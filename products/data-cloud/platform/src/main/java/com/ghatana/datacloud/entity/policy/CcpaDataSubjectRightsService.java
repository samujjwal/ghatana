/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.entity.policy;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.DataCloudClient.Entity;
import com.ghatana.datacloud.DataCloudClient.Filter;
import com.ghatana.datacloud.DataCloudClient.Query;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implements California Consumer Privacy Act (CCPA) data-subject rights for
 * Data-Cloud entity collections.
 *
 * <h3>CCPA Rights Covered (Cal. Civ. Code §1798.100 et seq.)</h3>
 * <ul>
 *   <li><strong>Right to Know</strong> — {@link #accessRequest}: collects all
 *       entities where {@code _subjectId} matches the consumer identifier.</li>
 *   <li><strong>Right to Delete</strong> — {@link #deletionRequest}: erases all
 *       entities associated with the consumer (mirrors GDPR Art.17 erasure).</li>
 *   <li><strong>Right to Opt-Out</strong> — {@link #optOutRequest}: marks the
 *       consumer as opted-out for data-sale / sharing by writing an opt-out
 *       record ({@code _ccpaOptOut=true}) to the designated opt-out collection.</li>
 *   <li><strong>Right to Non-Discrimination</strong> — enforced organisationally;
 *       this service does not restrict service based on opt-out status.</li>
 *   <li><strong>Right to Correct</strong> — {@link #correctionRequest}: updates
 *       specified fields on all entities owned by the consumer.</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * CcpaDataSubjectRightsService svc = new CcpaDataSubjectRightsService(client);
 *
 * // Register the collections that may hold consumer PII
 * svc.registerCollection("t-1", "users");
 * svc.registerCollection("t-1", "orders");
 *
 * // Process a deletion request (Right to Delete)
 * CcpaReport report = runPromise(() -> svc.deletionRequest("t-1", "consumer-123"));
 * }</pre>
 *
 * <h3>Notes</h3>
 * <p>All entity records containing subject data MUST store the consumer
 * identifier in a field named {@value #SUBJECT_ID_FIELD}. This is a
 * platform convention enforced at ingestion time.
 *
 * @doc.type class
 * @doc.purpose CCPA data-subject rights: know, delete, opt-out, correct
 * @doc.layer product
 * @doc.pattern Service
 */
public final class CcpaDataSubjectRightsService {

    private static final Logger log = LoggerFactory.getLogger(CcpaDataSubjectRightsService.class);

    /** Field name that stores the data-subject / consumer identifier. */
    public static final String SUBJECT_ID_FIELD = "_subjectId";

    /** Field set to {@code "true"} on the opt-out record. */
    public static final String OPT_OUT_FIELD = "_ccpaOptOut";

    /** Collection used to persist opt-out records. One record per consumer. */
    public static final String OPT_OUT_COLLECTION = "_ccpa_opt_out";

    /** Page size for paginated bulk queries. */
    private static final int PAGE_SIZE = 500;

    private final DataCloudClient client;

    /**
     * (tenantId → list of registered collection names) mapping.
     * Only these collections are swept during deletion / access requests.
     */
    private final Map<String, List<String>> tenantCollections;

    /**
     * Constructs the service.
     *
     * @param client the DataCloud client used for all entity operations
     */
    public CcpaDataSubjectRightsService(DataCloudClient client) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.tenantCollections = new java.util.concurrent.ConcurrentHashMap<>();
    }

    // =========================================================================
    // Registration
    // =========================================================================

    /**
     * Registers a collection that may contain consumer PII for the given tenant.
     * Must be called before processing any rights requests.
     *
     * @param tenantId   the tenant identifier
     * @param collection the collection name
     */
    public void registerCollection(String tenantId, String collection) {
        tenantCollections
                .computeIfAbsent(tenantId, k -> new java.util.ArrayList<>())
                .add(collection);
        log.info("[CCPA] Registered collection '{}' for tenant '{}'", collection, tenantId);
    }

    // =========================================================================
    // Right to Know (§1798.100) — Access Request
    // =========================================================================

    /**
     * Fulfils a <em>Right to Know</em> request by collecting all entity data
     * belonging to the identified consumer across all registered collections.
     *
     * @param tenantId   the tenant identifier
     * @param consumerId the consumer's unique identifier (stored in {@value #SUBJECT_ID_FIELD})
     * @return Promise resolving to a {@link CcpaReport} with all matching entities
     */
    public Promise<CcpaReport> accessRequest(String tenantId, String consumerId) {
        validateInputs(tenantId, consumerId);
        List<String> collections = collectionsFor(tenantId);
        log.info("[CCPA][ACCESS] Starting access request for consumer '{}' in tenant '{}' ({} collections)",
                consumerId, tenantId, collections.size());

        List<Promise<List<Entity>>> promises = new ArrayList<>();
        for (String col : collections) {
            promises.add(queryAllBySubject(tenantId, col, consumerId));
        }

        return Promises.toList(promises).map(allLists -> {
            List<Entity> combined = new ArrayList<>();
            allLists.forEach(combined::addAll);
            log.info("[CCPA][ACCESS] Found {} records for consumer '{}'", combined.size(), consumerId);
            return new CcpaReport(CcpaRightType.ACCESS, tenantId, consumerId,
                    combined.size(), 0, combined);
        });
    }

    // =========================================================================
    // Right to Delete (§1798.105) — Deletion Request
    // =========================================================================

    /**
     * Fulfils a <em>Right to Delete</em> request by permanently erasing all
     * entity records containing the consumer's identifier across all registered
     * collections.
     *
     * <p>Deletions are irreversible. Ensure a data backup policy is in place
     * before invoking on production data.
     *
     * @param tenantId   the tenant identifier
     * @param consumerId the consumer's unique identifier
     * @return Promise resolving to a {@link CcpaReport} with deletion counts
     */
    public Promise<CcpaReport> deletionRequest(String tenantId, String consumerId) {
        validateInputs(tenantId, consumerId);
        List<String> collections = collectionsFor(tenantId);
        log.info("[CCPA][DELETE] Deletion request for consumer '{}' in tenant '{}' ({} collections)",
                consumerId, tenantId, collections.size());

        AtomicLong totalDeleted = new AtomicLong(0);
        List<Promise<Void>> deletions = new ArrayList<>();

        for (String col : collections) {
            deletions.add(
                    queryAllBySubject(tenantId, col, consumerId)
                            .then(entities -> {
                                List<Promise<Void>> perEntity = entities.stream()
                                        .map(e -> client.delete(tenantId, col, e.id())
                                                .whenResult(() -> totalDeleted.incrementAndGet()))
                                        .toList();
                                return Promises.all(perEntity);
                            })
            );
        }

        return Promises.all(deletions).map($ -> {
            log.info("[CCPA][DELETE] Erased {} records for consumer '{}' in tenant '{}'",
                    totalDeleted.get(), consumerId, tenantId);
            return new CcpaReport(CcpaRightType.DELETE, tenantId, consumerId,
                    0, totalDeleted.get(), List.of());
        });
    }

    // =========================================================================
    // Right to Opt-Out (§1798.120) — Data Sale Opt-Out
    // =========================================================================

    /**
     * Records a <em>Right to Opt-Out</em> of data sale/sharing for the consumer.
     *
     * <p>Writes (or updates) an opt-out record in the {@value #OPT_OUT_COLLECTION}
     * collection. Downstream data-sharing pipelines MUST check this record before
     * transmitting data to third parties.
     *
     * @param tenantId   the tenant identifier
     * @param consumerId the consumer's unique identifier
     * @return Promise resolving to a {@link CcpaReport} confirming the opt-out
     */
    public Promise<CcpaReport> optOutRequest(String tenantId, String consumerId) {
        validateInputs(tenantId, consumerId);
        log.info("[CCPA][OPT-OUT] Recording opt-out for consumer '{}' in tenant '{}'",
                consumerId, tenantId);

        Map<String, Object> optOutRecord = Map.of(
                "id",            consumerId,
                SUBJECT_ID_FIELD, consumerId,
                OPT_OUT_FIELD,   "true",
                "_optOutTimestamp", Instant.now().toString(),
                "_requestType",  "CCPA_OPT_OUT"
        );

        return client.save(tenantId, OPT_OUT_COLLECTION, optOutRecord)
                .map(saved -> {
                    log.info("[CCPA][OPT-OUT] Opt-out recorded for consumer '{}' (entity id: {})",
                            consumerId, saved.id());
                    return new CcpaReport(CcpaRightType.OPT_OUT, tenantId, consumerId,
                            1, 0, List.of(saved));
                });
    }

    /**
     * Checks whether the consumer has an active opt-out record.
     *
     * @param tenantId   the tenant identifier
     * @param consumerId the consumer's unique identifier
     * @return Promise resolving to {@code true} if the consumer has opted out
     */
    public Promise<Boolean> isOptedOut(String tenantId, String consumerId) {
        validateInputs(tenantId, consumerId);
        return client.findById(tenantId, OPT_OUT_COLLECTION, consumerId)
                .map(opt -> opt.isPresent()
                        && "true".equals(opt.get().data().get(OPT_OUT_FIELD)));
    }

    // =========================================================================
    // Right to Correct (§1798.106) — Correction Request
    // =========================================================================

    /**
     * Fulfils a <em>Right to Correct</em> request by updating specified fields
     * on all entity records owned by the consumer.
     *
     * <p>Only the supplied {@code corrections} fields are overwritten; all other
     * fields on each entity are preserved.
     *
     * @param tenantId    the tenant identifier
     * @param consumerId  the consumer's unique identifier
     * @param corrections a map of field names → corrected values
     * @return Promise resolving to a {@link CcpaReport} with update counts
     */
    public Promise<CcpaReport> correctionRequest(
            String tenantId, String consumerId, Map<String, Object> corrections) {
        validateInputs(tenantId, consumerId);
        Objects.requireNonNull(corrections, "corrections must not be null");
        if (corrections.isEmpty()) {
            return Promise.ofException(
                    new IllegalArgumentException("corrections map must not be empty"));
        }

        log.info("[CCPA][CORRECT] Correction request for consumer '{}' in tenant '{}', fields: {}",
                consumerId, tenantId, corrections.keySet());

        List<String> collections = collectionsFor(tenantId);
        AtomicLong totalUpdated = new AtomicLong(0);
        List<Promise<Void>> updates = new ArrayList<>();

        for (String col : collections) {
            updates.add(
                    queryAllBySubject(tenantId, col, consumerId)
                            .then(entities -> {
                                List<Promise<Void>> perEntity = entities.stream()
                                        .map(e -> {
                                            // Merge corrections into existing data
                                            Map<String, Object> updated =
                                                    new java.util.HashMap<>(e.data());
                                            updated.putAll(corrections);
                                            return client.save(tenantId, col, updated)
                                                    .toVoid()
                                                    .whenResult(() -> totalUpdated.incrementAndGet());
                                        })
                                        .toList();
                                return Promises.all(perEntity);
                            })
            );
        }

        return Promises.all(updates).map($ -> {
            log.info("[CCPA][CORRECT] Updated {} records for consumer '{}' in tenant '{}'",
                    totalUpdated.get(), consumerId, tenantId);
            return new CcpaReport(CcpaRightType.CORRECT, tenantId, consumerId,
                    0, totalUpdated.get(), List.of());
        });
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private Promise<List<Entity>> queryAllBySubject(
            String tenantId, String collection, String consumerId) {
        return queryPage(tenantId, collection, consumerId, new ArrayList<>());
    }

    private Promise<List<Entity>> queryPage(
            String tenantId, String collection, String consumerId,
            List<Entity> accumulator) {
        Query q = new Query.Builder()
                .filter(Filter.eq(SUBJECT_ID_FIELD, consumerId))
                .limit(PAGE_SIZE)
                .build();

        return client.query(tenantId, collection, q).then(page -> {
            accumulator.addAll(page);
            if (page.size() < PAGE_SIZE) {
                return Promise.of(accumulator);
            }
            // More pages remain — recurse
            return queryPage(tenantId, collection, consumerId, accumulator);
        });
    }

    private List<String> collectionsFor(String tenantId) {
        return tenantCollections.getOrDefault(tenantId, List.of());
    }

    private static void validateInputs(String tenantId, String consumerId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (consumerId == null || consumerId.isBlank()) {
            throw new IllegalArgumentException("consumerId must not be blank");
        }
    }

    // =========================================================================
    // Value objects
    // =========================================================================

    /**
     * Enumerates the CCPA rights implemented by this service.
     *
     * @doc.type enum
     * @doc.purpose CCPA right type discriminator
     * @doc.layer product
     * @doc.pattern ValueObject
     */
    public enum CcpaRightType {
        ACCESS, DELETE, OPT_OUT, CORRECT
    }

    /**
     * Immutable response object summarising a CCPA rights request.
     *
     * @param rightType     the right exercised
     * @param tenantId      the tenant context
     * @param consumerId    the consumer identifier
     * @param recordsFound  number of records found (for ACCESS/OPT_OUT)
     * @param recordsActed  number of records deleted or updated
     * @param entities      populated for ACCESS requests; empty otherwise
     *
     * @doc.type record
     * @doc.purpose Immutable CCPA processing result
     * @doc.layer product
     * @doc.pattern ValueObject
     */
    public record CcpaReport(
            CcpaRightType rightType,
            String        tenantId,
            String        consumerId,
            long          recordsFound,
            long          recordsActed,
            List<Entity>  entities
    ) {}
}
