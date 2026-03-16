/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.launcher.compliance;

import com.ghatana.aep.compliance.AepComplianceReport;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.DataCloudClient.Filter;
import com.ghatana.datacloud.DataCloudClient.Query;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * GDPR and CCPA data-subject rights service for the Agentic Event Processor.
 *
 * <p>Covers the following statutory rights across all Data-Cloud collections
 * maintained by AEP (patterns, pipelines, agent registrations, agent memory,
 * event logs, and audit trails):
 *
 * <table border="1" cellpadding="4">
 *   <caption>Supported data-subject rights</caption>
 *   <tr><th>Right</th><th>Regulation</th><th>Method</th></tr>
 *   <tr><td>Right of Access / Know</td><td>GDPR Art.15 / CCPA §1798.110</td>
 *       <td>{@link #accessRequest}</td></tr>
 *   <tr><td>Right to Erasure / Delete</td><td>GDPR Art.17 / CCPA §1798.105</td>
 *       <td>{@link #deletionRequest}</td></tr>
 *   <tr><td>Right to Correction</td><td>GDPR Art.16 / CCPA §1798.106</td>
 *       <td>{@link #correctionRequest}</td></tr>
 *   <tr><td>Right to Portability</td><td>GDPR Art.20</td>
 *       <td>{@link #portabilityRequest}</td></tr>
 *   <tr><td>Right to Opt-Out</td><td>CCPA §1798.120</td>
 *       <td>{@link #ccpaOptOut}</td></tr>
 * </table>
 *
 * <p>All personal data stored by AEP MUST include a {@value #SUBJECT_ID_FIELD}
 * field linking the record to the data subject's identifier. This invariant is
 * enforced at ingestion time by the {@code IngressAuthValidator}.
 *
 * @doc.type class
 * @doc.purpose GDPR Art.15–20 and CCPA §1798.100 data-subject rights for AEP
 * @doc.layer product
 * @doc.pattern Service
 */
public final class AepComplianceService {

    private static final Logger log = LoggerFactory.getLogger(AepComplianceService.class);

    /** Field name embedding the data-subject / consumer identifier in AEP entities. */
    public static final String SUBJECT_ID_FIELD = "_subjectId";

    /** Collection for CCPA opt-out records. */
    public static final String OPT_OUT_COLLECTION = "_aep_ccpa_opt_out";

    /** Page size used for bulk subject queries. */
    private static final int PAGE_SIZE = 500;

    /** AEP collections that may contain personal data. Populated via {@link #registerCollection}. */
    private final CopyOnWriteArrayList<String> registeredCollections = new CopyOnWriteArrayList<>(
            List.of(
                "aep_patterns",
                "aep_pipelines",
                "agent-registry",
                "dc_memory",
                "aep_audit"
            )
    );

    private final DataCloudClient client;

    /**
     * Creates a new compliance service.
     *
     * @param client Data-Cloud client for data-subject operations
     */
    public AepComplianceService(DataCloudClient client) {
        this.client = Objects.requireNonNull(client, "DataCloudClient must not be null");
    }

    /**
     * Registers an additional AEP-specific collection that may contain personal data.
     * This collection will be included in all access/erasure/portability operations.
     *
     * @param collection Data-Cloud collection name
     */
    public void registerCollection(String collection) {
        if (!registeredCollections.contains(collection)) {
            registeredCollections.add(collection);
            log.info("[compliance] registered collection '{}'", collection);
        }
    }

    // =========================================================================
    // Right of Access (GDPR Art.15 / CCPA §1798.110)
    // =========================================================================

    /**
     * Implements the Right of Access / Right to Know — collects all AEP entity records
     * associated with {@code subjectId} across all registered collections.
     *
     * @param tenantId  tenant scope
     * @param subjectId data-subject identifier
     * @return compliance report with all data found
     */
    public Promise<AepComplianceReport> accessRequest(String tenantId, String subjectId) {
        log.info("[compliance] GDPR access request for subjectId='{}' tenant='{}'", subjectId, tenantId);
        Instant start = Instant.now();

        List<Promise<Long>> collectionQueries = registeredCollections.stream()
                .map(collection -> countSubjectRecords(tenantId, collection, subjectId))
                .toList();

        return Promises.toList(collectionQueries)
                .map(counts -> {
                    Map<String, Long> breakdown = new HashMap<>();
                    for (int i = 0; i < registeredCollections.size(); i++) {
                        breakdown.put(registeredCollections.get(i), counts.get(i));
                    }
                    long total = counts.stream().mapToLong(Long::longValue).sum();
                    return new AepComplianceReport(
                            "GDPR_ACCESS",
                            tenantId, subjectId, true,
                            "Found " + total + " records for subject across " + registeredCollections.size() + " collections",
                            total, Map.copyOf(breakdown), List.of(), start, Instant.now()
                    );
                })
                .whenException(e -> log.error("[compliance] access request failed: {}", e.getMessage(), e));
    }

    // =========================================================================
    // Right to Erasure (GDPR Art.17 / CCPA §1798.105)
    // =========================================================================

    /**
     * Implements the Right to Erasure / Right to Delete — deletes all AEP entity records
     * associated with {@code subjectId} across all registered collections.
     *
     * <p>Deletion is executed in parallel across all collections. Non-fatal collection
     * errors are recorded as warnings to allow partial completion.
     *
     * @param tenantId  tenant scope
     * @param subjectId data-subject identifier
     * @return compliance report with counts of deleted records
     */
    public Promise<AepComplianceReport> deletionRequest(String tenantId, String subjectId) {
        log.info("[compliance] GDPR erasure request for subjectId='{}' tenant='{}'", subjectId, tenantId);
        Instant start = Instant.now();
        List<String> warnings = new CopyOnWriteArrayList<>();

        List<Promise<long[]>> deletionTasks = registeredCollections.stream()
                .map(collection -> deleteSubjectRecords(tenantId, collection, subjectId, warnings))
                .toList();

        return Promises.toList(deletionTasks)
                .map(results -> {
                    Map<String, Long> breakdown = new HashMap<>();
                    long total = 0;
                    for (int i = 0; i < registeredCollections.size(); i++) {
                        long count = results.get(i)[0];
                        breakdown.put(registeredCollections.get(i), count);
                        total += count;
                    }
                    return new AepComplianceReport(
                            "GDPR_ERASURE",
                            tenantId, subjectId, true,
                            "Erased " + total + " records for subject across " + registeredCollections.size() + " collections",
                            total, Map.copyOf(breakdown), List.copyOf(warnings), start, Instant.now()
                    );
                })
                .then(Promise::of, e -> {
                    log.error("[compliance] erasure request failed: {}", e.getMessage(), e);
                    return Promise.of(AepComplianceReport.failure("GDPR_ERASURE", tenantId, subjectId, e.getMessage()));
                });
    }

    // =========================================================================
    // Right to Correction (GDPR Art.16 / CCPA §1798.106)
    // =========================================================================

    /**
     * Implements the Right to Correction — applies {@code corrections} (field → new value)
     * to all entity records owned by {@code subjectId} in the given {@code collection}.
     *
     * @param tenantId    tenant scope
     * @param collection  target collection
     * @param subjectId   data-subject identifier
     * @param corrections map of field names to their corrected values
     * @return compliance report
     */
    public Promise<AepComplianceReport> correctionRequest(String tenantId, String collection,
                                                           String subjectId,
                                                           Map<String, Object> corrections) {
        log.info("[compliance] correction request for subjectId='{}' collection='{}' tenant='{}'",
                subjectId, collection, tenantId);
        Instant start = Instant.now();

        Query query = Query.builder()
                .filter(Filter.eq(SUBJECT_ID_FIELD, subjectId))
                .limit(PAGE_SIZE)
                .build();

        return client.query(tenantId, collection, query)
                .then(entities -> {
                    List<Promise<DataCloudClient.Entity>> updates = entities.stream()
                            .map(entity -> {
                                Map<String, Object> updated = new HashMap<>(entity.data());
                                updated.put("id", entity.id()); // preserve entity key for upsert
                                updated.putAll(corrections);
                                updated.put("_correctedAt", Instant.now().toString());
                                return client.save(tenantId, collection, updated);
                            })
                            .toList();
                    return Promises.toList(updates).map(saved ->
                            new AepComplianceReport(
                                    "GDPR_CORRECTION",
                                    tenantId, subjectId, true,
                                    "Corrected " + saved.size() + " records in collection '" + collection + "'",
                                    saved.size(), Map.of(collection, (long) saved.size()), List.of(),
                                    start, Instant.now()
                            )
                    );
                })
                .then(Promise::of, e -> {
                    log.error("[compliance] correction failed: {}", e.getMessage(), e);
                    return Promise.of(AepComplianceReport.failure("GDPR_CORRECTION", tenantId, subjectId, e.getMessage()));
                });
    }

    // =========================================================================
    // Right to Portability (GDPR Art.20)
    // =========================================================================

    /**
     * Implements the Right to Data Portability — collects all subject records into a
     * structured export map suitable for JSON serialization.
     *
     * @param tenantId  tenant scope
     * @param subjectId data-subject identifier
     * @return subject data export across all collections
     */
    public Promise<Map<String, Object>> portabilityRequest(String tenantId, String subjectId) {
        log.info("[compliance] portability request for subjectId='{}' tenant='{}'", subjectId, tenantId);

        Query query = Query.builder()
                .filter(Filter.eq(SUBJECT_ID_FIELD, subjectId))
                .limit(PAGE_SIZE)
                .build();

        List<Promise<Map.Entry<String, List<Map<String, Object>>>>> collectionExports =
                registeredCollections.stream()
                        .map(collection -> client.query(tenantId, collection, query)
                                .map(entities -> Map.entry(collection,
                                        entities.stream().map(DataCloudClient.Entity::data).toList())))
                        .toList();

        return Promises.toList(collectionExports)
                .map(entries -> {
                    Map<String, Object> export = new HashMap<>();
                    export.put("subjectId", subjectId);
                    export.put("tenantId", tenantId);
                    export.put("exportedAt", Instant.now().toString());
                    Map<String, List<Map<String, Object>>> data = new HashMap<>();
                    for (Map.Entry<String, List<Map<String, Object>>> entry : entries) {
                        data.put(entry.getKey(), entry.getValue());
                    }
                    export.put("collections", data);
                    return Map.copyOf(export);
                })
                .whenException(e -> log.error("[compliance] portability failed: {}", e.getMessage(), e));
    }

    // =========================================================================
    // CCPA Opt-Out (§1798.120)
    // =========================================================================

    /**
     * Records a CCPA opt-out for the given consumer — writes an opt-out marker
     * to the {@value #OPT_OUT_COLLECTION} collection so downstream systems
     * can honour the opt-out for data-sale / sharing operations.
     *
     * <p>The consumer ID is stored as the entity key (via {@code "id"} in the data map)
     * to enable idempotent upserts — re-submitting the same opt-out is safe.
     *
     * @param tenantId   tenant scope
     * @param consumerId CCPA consumer identifier
     * @return compliance report
     */
    public Promise<AepComplianceReport> ccpaOptOut(String tenantId, String consumerId) {
        log.info("[compliance] CCPA opt-out for consumerId='{}' tenant='{}'", consumerId, tenantId);
        Instant start = Instant.now();

        Map<String, Object> optOutRecord = new HashMap<>();
        optOutRecord.put("id",          consumerId); // entity key — enables idempotent upsert
        optOutRecord.put("_ccpaOptOut", true);
        optOutRecord.put("consumerId",  consumerId);
        optOutRecord.put("tenantId",    tenantId);
        optOutRecord.put("recordedAt",  Instant.now().toString());

        return client.save(tenantId, OPT_OUT_COLLECTION, optOutRecord)
                .map(entity -> new AepComplianceReport(
                        "CCPA_OPT_OUT",
                        tenantId, consumerId, true,
                        "CCPA opt-out recorded for consumer '" + consumerId + "'",
                        1L, Map.of(OPT_OUT_COLLECTION, 1L), List.of(), start, Instant.now()
                ))
                .then(Promise::of, e -> {
                    log.error("[compliance] CCPA opt-out failed: {}", e.getMessage(), e);
                    return Promise.of(AepComplianceReport.failure("CCPA_OPT_OUT", tenantId, consumerId, e.getMessage()));
                });
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private Promise<Long> countSubjectRecords(String tenantId, String collection, String subjectId) {
        Query query = Query.builder()
                .filter(Filter.eq(SUBJECT_ID_FIELD, subjectId))
                .limit(PAGE_SIZE)
                .build();
        return client.query(tenantId, collection, query)
                .map(entities -> (long) entities.size())
                .then(Promise::of, e -> {
                    log.warn("[compliance] count failed collection='{}': {}", collection, e.getMessage());
                    return Promise.of(0L);
                });
    }

    private Promise<long[]> deleteSubjectRecords(String tenantId, String collection,
                                                  String subjectId, List<String> warnings) {
        Query query = Query.builder()
                .filter(Filter.eq(SUBJECT_ID_FIELD, subjectId))
                .limit(PAGE_SIZE)
                .build();

        return client.query(tenantId, collection, query)
                .then(entities -> {
                    if (entities.isEmpty()) {
                        return Promise.of(new long[]{0L});
                    }
                    List<Promise<Void>> deletes = entities.stream()
                            .map(entity -> client.delete(tenantId, collection, entity.id()))
                            .toList();
                    // Use Promises.all() instead of toList() — Void promises resolve to null
                    // and List.of() rejects null elements, causing NPE in toList().
                    return Promises.all(deletes).map(ignored -> new long[]{(long) entities.size()});
                })
                .then(Promise::of, e -> {
                    String msg = "collection='" + collection + "': " + e.getMessage();
                    log.warn("[compliance] deletion failed {}", msg);
                    warnings.add(msg);
                    return Promise.of(new long[]{0L});
                });
    }
}
