package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.launcher.DataCloudLauncherSettings;
import com.ghatana.datacloud.launcher.audit.AuditSummaryProvider;
import com.ghatana.datacloud.launcher.http.ApiResponse;
import com.ghatana.datacloud.launcher.http.TraceSpanSupport;
import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.audit.AuditService;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.Period;
import java.util.Base64;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * HTTP handler for data lifecycle and governance operations (DC-E5).
 *
 * <p>Implements GDPR / data-retention compliance primitives as first-class API
 * endpoints, enabling tenant-controlled policy enforcement over the Data-Cloud
 * entity and event planes.
 *
 * <h2>Routes</h2>
 * <pre>
 *   POST /api/v1/governance/retention/classify    — classify collection for retention tier
 *   GET  /api/v1/governance/retention/policy      — get retention policy for a collection
 *   POST /api/v1/governance/retention/purge       — trigger deletion workflow (CRITICAL)
 *   POST /api/v1/governance/privacy/redact        — redact PII fields from an entity
 *   GET  /api/v1/governance/privacy/pii-fields    — list registered PII-tagged fields
 *   GET  /api/v1/governance/privacy/verify        — verify redaction status for an entity
 *   GET  /api/v1/governance/compliance/summary    — tenant compliance dashboard
 * </pre>
 *
 * <h2>Security Classification</h2>
 * All governance routes are classified {@link com.ghatana.datacloud.launcher.http.EndpointSensitivity#CRITICAL}
 * and require policy engine approval.  All operations are fully audited.
 *
 * <h2>Privacy by Design</h2>
 * <ul>
 *   <li>Redact operations are idempotent and append-only (original data is overwritten,
 *       not stored alongside the redacted version).</li>
 *   <li>Purge operations require a confirmation token in the request body and leave
 *       a tombstone audit record after completion.</li>
 *   <li>PII field mappings are tenant-scoped and encrypted at rest.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Data lifecycle and governance HTTP handler (DC-E5)
 * @doc.layer product
 * @doc.pattern Handler, GovernanceService
 */
public class DataLifecycleHandler {

    private static final Logger log = LoggerFactory.getLogger(DataLifecycleHandler.class);

    /** Well-known retention tiers with associated default duration. */
    private static final Map<String, Period> RETENTION_TIERS = Map.of(
        "transient",   Period.ofDays(7),
        "short-term",  Period.ofDays(90),
        "standard",    Period.ofYears(1),
        "compliance",  Period.ofYears(7),
        "permanent",   Period.ofDays(Integer.MAX_VALUE / 365)
    );

    /** Fields that are always treated as PII regardless of tenant classification. */
    private static final Set<String> GLOBAL_PII_FIELDS = Set.of(
        "email", "phone", "ssn", "passport_number", "date_of_birth",
        "full_name", "ip_address", "credit_card", "bank_account"
    );

    /** Validity window for HMAC purge confirmation tokens (5 minutes). */
    private static final long PURGE_TOKEN_VALIDITY_MS = 5L * 60 * 1000;

    /** HMAC-SHA256 algorithm identifier. */
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String GOVERNANCE_POLICY_COLLECTION = "_governance_retention_policies";
    private static final String GOVERNANCE_PURGE_TOMBSTONE_COLLECTION = "_governance_purge_tombstones";
    private static final String POLICY_STATUS_CLASSIFIED = "CLASSIFIED";
    private static final String POLICY_STATUS_DEFAULT = "DEFAULT";
    private static final String MISSING_TENANT_ERROR = "MISSING_TENANT";
    private static final String PURGE_TOKEN_SECRET_REQUIRED = "PURGE_TOKEN_SECRET_REQUIRED";
    private static final String PURGE_TOKEN_SECRET_ENV = "DATACLOUD_PURGE_TOKEN_SECRET";
    private static final String DATACLOUD_PROFILE_ENV = "DATACLOUD_PROFILE";

    private static final String REDACTED_VALUE = "[REDACTED]";
    private static final int PURGE_QUERY_LIMIT = EntityStore.QuerySpec.MAX_LIMIT;

    /**
     * Ephemeral per-process fallback secret used only in local/embedded-style profiles.
     */
    private static final byte[] EPHEMERAL_PURGE_TOKEN_SECRET =
        UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);

    private final DataCloudClient client;
    private final ObjectMapper objectMapper;
    private final HttpHandlerSupport http;
    private final AuditService auditService; // nullable
    private TraceSpanSupport traceSupport = TraceSpanSupport.disabled();

    /**
     * Creates a governance handler.
     *
     * @param client       data-cloud client; must not be null
     * @param objectMapper Jackson mapper; must not be null
     * @param http         shared HTTP support; must not be null
     * @param auditService optional audit service; when null audit emissions are skipped
     */
    public DataLifecycleHandler(DataCloudClient client,
                                ObjectMapper objectMapper,
                                HttpHandlerSupport http,
                                AuditService auditService) {
        this.client       = client;
        this.objectMapper = objectMapper;
        this.http         = http;
        this.auditService = auditService;
    }

    public DataLifecycleHandler withTraceSupport(TraceSpanSupport traceSupport) {
        this.traceSupport = traceSupport != null ? traceSupport : TraceSpanSupport.disabled();
        return this;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Route handlers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * {@code POST /api/v1/governance/retention/classify}
     *
     * <p>Assigns a retention tier to a collection.  The tier determines the
     * default purge schedule and the data-residency policy applied to all entities
     * in the collection.
     *
     * <p>Request body:
     * <pre>{@code
     * {
     *   "collection": "user_profiles",
     *   "tier":       "compliance",      // transient | short-term | standard | compliance | permanent
     *   "reason":     "GDPR Article 17"
     * }
     * }</pre>
     */
    public Promise<HttpResponse> handleClassifyRetention(HttpRequest request) {
        String requestId = resolveRequestId(request);
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(missingTenantResponse(requestId));
        }

        TraceSpanSupport.TraceSpanScope handlerSpan = traceSupport.startSpan(
                request,
                tenantId,
                "datacloud.http.governance.retention.classify",
                traceSupport.requestSpanId(request),
                Map.of("request.id", requestId));

        return request.loadBody(1024 * 16)
            .then(body -> {
                Map<String, Object> input = parseBody(body.getString(StandardCharsets.UTF_8));
                String collection = sanitise((String) input.getOrDefault("collection", ""));
                String tier       = sanitise((String) input.getOrDefault("tier", "standard"));
                String reason     = sanitise((String) input.getOrDefault("reason", "unspecified"));

                if (collection.isBlank()) {
                    return Promise.of(http.envelopeResponse(
                        ApiResponse.error("MISSING_COLLECTION", "collection is required", tenantId, requestId),
                        objectMapper));
                }

                if (!RETENTION_TIERS.containsKey(tier)) {
                    return Promise.of(http.envelopeResponse(
                        ApiResponse.error("INVALID_TIER",
                            "Valid tiers: " + RETENTION_TIERS.keySet(),
                            tenantId, requestId),
                        objectMapper));
                }

                Period retentionPeriod = RETENTION_TIERS.get(tier);
                long retentionDays = retentionPeriod.getDays()
                    + retentionPeriod.getMonths() * 30L
                    + retentionPeriod.getYears() * 365L;
                Instant expiresAt = tier.equals("permanent")
                    ? null
                    : Instant.now().plusSeconds(retentionDays * 86_400L);
                List<String> piiFields = resolveRequestedPiiFields(input.get("piiFields"), collection);

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("collection",     collection);
                result.put("tier",           tier);
                result.put("retentionDays",  retentionDays);
                if (expiresAt != null) {
                    result.put("expiresAt", expiresAt.toString());
                }
                result.put("classifiedAt",   Instant.now().toString());
                result.put("classifiedBy",   tenantId);
                result.put("reason",         reason);
                result.put("piiFields",      piiFields);
                result.put("status",         POLICY_STATUS_CLASSIFIED);

                TenantContext tenantContext = buildTenantContext(tenantId, requestId);
                return saveRetentionPolicy(tenantContext, collection, result)
                    .map(savedPolicy -> {
                        emitAudit(tenantId, requestId, "RETENTION_CLASSIFY", collection,
                              Map.of("tier", tier, "reason", reason, "piiFieldCount", piiFields.size()));

                        log.info("[DC-E5] retention classified collection={} tier={} tenant={}", collection, tier, tenantId);
                        return http.envelopeResponse(
                            ApiResponse.success(savedPolicy, tenantId, requestId), objectMapper);
                    });
            }).whenComplete((response, error) -> traceSupport.finish(handlerSpan, response, error));
    }

    /**
     * {@code GET /api/v1/governance/retention/policy?collection=X}
     *
     * <p>Returns the current retention policy for a collection, including the
     * effective tier, expiry schedule, and any active holds.
     */
    public Promise<HttpResponse> handleGetRetentionPolicy(HttpRequest request) {
        String requestId  = resolveRequestId(request);
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(missingTenantResponse(requestId));
        }
        String collection = sanitise(request.getQueryParameter("collection"));
        TraceSpanSupport.TraceSpanScope handlerSpan = traceSupport.startSpan(
                request,
                tenantId,
                "datacloud.http.governance.retention.policy",
                traceSupport.requestSpanId(request),
                collection == null || collection.isBlank() ? Map.of("request.id", requestId) : Map.of("request.id", requestId, "collection", collection));

        if (collection == null || collection.isBlank()) {
            return Promise.of(http.envelopeResponse(
                ApiResponse.error("MISSING_COLLECTION", "collection query parameter is required",
                    tenantId, requestId),
                objectMapper)).whenComplete((response, error) -> traceSupport.finish(handlerSpan, response, error));
        }

        TenantContext tenantContext = buildTenantContext(tenantId, requestId);
        return loadRetentionPolicy(tenantContext, collection)
            .map(stored -> stored.orElseGet(() -> defaultRetentionPolicy(collection)))
            .map(policy -> http.envelopeResponse(
                ApiResponse.success(policy, tenantId, requestId), objectMapper))
            .whenComplete((response, error) -> traceSupport.finish(handlerSpan, response, error));
    }

    /**
     * {@code POST /api/v1/governance/retention/purge}
     *
     * <p>Initiates a data purge for entities in a collection that have exceeded
     * their retention period.  Requires a {@code confirmationToken} matching the
     * tenant ID + collection to prevent accidental deletions.
     *
     * <p>This is a CRITICAL operation; policy engine approval is required before
     * the handler is entered (enforced by {@link com.ghatana.datacloud.launcher.http.DataCloudSecurityFilter}).
     *
     * <p>Request body:
     * <pre>{@code
     * {
     *   "collection":         "user_profiles",
     *   "confirmationToken":  "sha256(tenantId+collection+date)",
     *   "dryRun":             true   // if true, returns count without deleting
     * }
     * }</pre>
     */
    public Promise<HttpResponse> handlePurge(HttpRequest request) {
        String requestId = resolveRequestId(request);
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(missingTenantResponse(requestId));
        }
        TenantContext tenantContext = buildTenantContext(tenantId, requestId);
        TraceSpanSupport.TraceSpanScope handlerSpan = traceSupport.startSpan(
                request,
                tenantId,
                "datacloud.http.governance.retention.purge",
                traceSupport.requestSpanId(request),
                Map.of("request.id", requestId));

        return request.loadBody(1024 * 8)
            .then(body -> {
                Map<String, Object> input = parseBody(body.getString(StandardCharsets.UTF_8));
                String collection        = sanitise((String) input.getOrDefault("collection", ""));
                String confirmationToken = (String) input.getOrDefault("confirmationToken", "");
                boolean dryRun           = Boolean.TRUE.equals(input.get("dryRun"));

                if (collection.isBlank()) {
                    return Promise.of(http.envelopeResponse(
                        ApiResponse.error("MISSING_COLLECTION", "collection is required", tenantId, requestId),
                        objectMapper));
                }

                TokenSecretRequirement tokenSecretRequirement = validatePurgeTokenSecretConfiguration(runtimeEnvironment());
                if (!tokenSecretRequirement.available()) {
                    return Promise.of(http.envelopeResponse(
                        ApiResponse.error(
                            PURGE_TOKEN_SECRET_REQUIRED,
                            tokenSecretRequirement.message(),
                            Map.of("profile", tokenSecretRequirement.profile()),
                            tenantId,
                            requestId),
                        objectMapper));
                }

                EntityStore entityStore = requireEntityStore();
                Promise<Optional<Map<String, Object>>> policyPromise = loadRetentionPolicy(tenantContext, collection);

                if (dryRun) {
                    return policyPromise.then(policy -> entityStore.query(tenantContext, buildCollectionQuery(collection))
                        .map(queryResult -> {
                            List<EntityStore.Entity> candidates = findPurgeCandidates(policy.orElse(null), queryResult.entities());
                            long issuedAtMs = Instant.now().toEpochMilli();
                            String token = buildPurgeToken(tenantId, collection, issuedAtMs);
                            log.info("[DC-E5] purge DRY RUN collection={} tenant={} candidates={}",
                                collection, tenantId, candidates.size());

                            Map<String, Object> result = new LinkedHashMap<>();
                            result.put("collection", collection);
                            result.put("dryRun", true);
                            result.put("status", "DRY_RUN_COMPLETE");
                            result.put("confirmationToken", token);
                            result.put("tokenExpiresInSec", PURGE_TOKEN_VALIDITY_MS / 1000);
                            result.put("estimatedRows", candidates.size());
                            result.put("sampleEntityIds", candidates.stream()
                                .map(entity -> entity.id().value())
                                .limit(10)
                                .toList());
                            result.put("requestId", requestId);

                            emitAudit(tenantId, requestId, "RETENTION_PURGE_DRY_RUN",
                                collection, Map.of("dryRun", true, "estimatedRows", candidates.size()));
                            return http.envelopeResponse(
                                ApiResponse.success(result, tenantId, requestId), objectMapper);
                        }));
                }

                // Execute path: token is mandatory and must pass HMAC verification
                if (confirmationToken.isBlank()) {
                    return Promise.of(http.envelopeResponse(
                        ApiResponse.error("MISSING_CONFIRMATION",
                            "confirmationToken is required to authorise data deletion. " +
                            "Perform a dry-run first to obtain a valid token.",
                            tenantId, requestId),
                        objectMapper));
                }

                TokenValidationResult tokenResult = validatePurgeToken(confirmationToken, tenantId, collection);
                if (!tokenResult.valid()) {
                    log.warn("[DC-E5] purge REJECTED invalid token: {} collection={} tenant={}",
                             tokenResult.reason(), collection, tenantId);
                    emitAudit(tenantId, requestId, "RETENTION_PURGE_REJECTED",
                              collection, Map.of(
                                  "reason", tokenResult.reason(),
                                  "confirmationTokenHash", sha256Hex(confirmationToken)));
                    return Promise.of(http.envelopeResponse(
                        ApiResponse.error("INVALID_CONFIRMATION_TOKEN",
                            "Confirmation token is invalid or expired: " + tokenResult.reason(),
                            tenantId, requestId),
                        objectMapper));
                }

                return policyPromise.then(policy -> entityStore.query(tenantContext, buildCollectionQuery(collection))
                    .then(queryResult -> {
                        List<EntityStore.Entity> candidates = findPurgeCandidates(policy.orElse(null), queryResult.entities());
                        List<EntityStore.EntityId> entityIds = candidates.stream()
                            .map(EntityStore.Entity::id)
                            .toList();

                        if (entityIds.isEmpty()) {
                            emitGovernanceEvent(
                                tenantContext,
                                requestId,
                                "RETENTION_PURGE",
                                collection,
                                Map.of("deletedCount", 0, "status", "NO_MATCHES"));
                            return Promise.of(http.envelopeResponse(
                                ApiResponse.success(Map.of(
                                    "collection", collection,
                                    "dryRun", false,
                                    "status", "PURGE_COMPLETED",
                                    "deletedRows", 0,
                                    "deletedEntityIds", List.of(),
                                    "requestId", requestId,
                                    "completedAt", Instant.now().toString()
                                ), tenantId, requestId), objectMapper));
                        }

                        return entityStore.deleteBatch(tenantContext, entityIds)
                            .then(batchResult -> {
                                log.info("[DC-E5] purge COMPLETED collection={} tenant={} deleted={}",
                                    collection, tenantId, batchResult.successCount());
                                emitAudit(tenantId, requestId, "RETENTION_PURGE",
                                    collection, Map.of(
                                        "dryRun", false,
                                        "deletedCount", batchResult.successCount(),
                                        "requestedCount", entityIds.size(),
                                        "confirmationTokenHash", sha256Hex(confirmationToken)));
                                emitGovernanceEvent(
                                    tenantContext,
                                    requestId,
                                    "RETENTION_PURGE",
                                    collection,
                                    Map.of(
                                        "deletedCount", batchResult.successCount(),
                                        "requestedCount", entityIds.size(),
                                        "failedCount", batchResult.failureCount()));

                                return savePurgeTombstone(
                                    tenantContext,
                                    collection,
                                    candidates.stream().map(entity -> entity.id().value()).toList(),
                                    batchResult.successCount(),
                                    entityIds.size(),
                                    sha256Hex(confirmationToken),
                                    requestId
                                ).map(ignored -> batchResult);
                            })
                            .map(batchResult -> {

                                Map<String, Object> result = new LinkedHashMap<>();
                                result.put("collection", collection);
                                result.put("dryRun", false);
                                result.put("status", "PURGE_COMPLETED");
                                result.put("deletedRows", batchResult.successCount());
                                result.put("requestedRows", entityIds.size());
                                result.put("failedRows", batchResult.failureCount());
                                result.put("deletedEntityIds", candidates.stream()
                                    .map(entity -> entity.id().value())
                                    .toList());
                                result.put("completedAt", Instant.now().toString());
                                result.put("requestId", requestId);
                                return http.envelopeResponse(
                                    ApiResponse.success(result, tenantId, requestId), objectMapper);
                            });
                    }));
                }).whenComplete((response, error) -> traceSupport.finish(handlerSpan, response, error));
    }

    /**
     * {@code POST /api/v1/governance/privacy/redact}
     *
     * <p>Redacts PII fields in an entity, replacing their values with a
     * standardised redaction placeholder.  The operation is idempotent.
     *
     * <p>Request body:
     * <pre>{@code
     * {
     *   "collection": "user_profiles",
     *   "entityId":   "ent-123",
     *   "fields":     ["email", "phone"],   // if omitted, all known PII fields are redacted
     *   "reason":     "GDPR Article 17 request from user"
     * }
     * }</pre>
     */
    public Promise<HttpResponse> handleRedact(HttpRequest request) {
        String requestId = resolveRequestId(request);
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(missingTenantResponse(requestId));
        }
        TenantContext tenantContext = buildTenantContext(tenantId, requestId);
        TraceSpanSupport.TraceSpanScope handlerSpan = traceSupport.startSpan(
                request,
                tenantId,
                "datacloud.http.governance.privacy.redact",
                traceSupport.requestSpanId(request),
                Map.of("request.id", requestId));

        return request.loadBody(1024 * 8)
            .then(body -> {
                Map<String, Object> input = parseBody(body.getString(StandardCharsets.UTF_8));
                String collection = sanitise((String) input.getOrDefault("collection", ""));
                String entityId   = sanitise((String) input.getOrDefault("entityId", ""));
                String reason     = sanitise((String) input.getOrDefault("reason", "unspecified"));

                @SuppressWarnings("unchecked")
                List<String> requestedFields = (List<String>) input.getOrDefault("fields", List.of());
                Set<String> fieldsToRedact = requestedFields.isEmpty()
                    ? GLOBAL_PII_FIELDS
                    : Set.copyOf(requestedFields);

                if (collection.isBlank() || entityId.isBlank()) {
                    return Promise.of(http.envelopeResponse(
                        ApiResponse.error("MISSING_REQUIRED",
                            "collection and entityId are required",
                            tenantId, requestId),
                        objectMapper));
                }

                EntityStore entityStore = requireEntityStore();
                EntityStore.EntityId storeEntityId = EntityStore.EntityId.of(entityId);
                return entityStore.findById(tenantContext, storeEntityId)
                    .then(entityOpt -> {
                        if (entityOpt.isEmpty()) {
                            return Promise.of(http.envelopeResponse(
                                ApiResponse.error("ENTITY_NOT_FOUND",
                                    "No entity found for entityId=" + entityId,
                                    tenantId, requestId),
                                objectMapper));
                        }

                        EntityStore.Entity existingEntity = entityOpt.get();
                        Map<String, Object> redactedData = new HashMap<>(existingEntity.data());
                        List<String> changedFields = new ArrayList<>();
                        Map<String, String> previousValueHashes = new LinkedHashMap<>();

                        for (String field : fieldsToRedact) {
                            Object currentValue = redactedData.get(field);
                            if (currentValue == null || REDACTED_VALUE.equals(currentValue)) {
                                continue;
                            }
                            previousValueHashes.put(field, sha256Hex(String.valueOf(currentValue)));
                            redactedData.put(field, REDACTED_VALUE);
                            changedFields.add(field);
                        }

                        if (changedFields.isEmpty()) {
                            Map<String, Object> result = Map.of(
                                "collection", collection,
                                "entityId", entityId,
                                "redactedFields", List.of(),
                                "requestedFields", fieldsToRedact.stream().sorted().toList(),
                                "reason", reason,
                                "status", "NO_OP",
                                "redactedAt", Instant.now().toString()
                            );
                            emitAudit(tenantId, requestId, "PII_REDACT", collection,
                                Map.of("entityId", entityId, "fieldCount", 0, "reason", reason, "status", "NO_OP"));
                            return Promise.of(http.envelopeResponse(
                                ApiResponse.success(result, tenantId, requestId), objectMapper));
                        }

                        EntityStore.Entity updatedEntity = new EntityStore.Entity(
                            existingEntity.id(),
                            existingEntity.collection(),
                            redactedData,
                            existingEntity.metadata().withUpdate("governance-redact")
                        );

                        return entityStore.save(tenantContext, updatedEntity)
                            .map(savedEntity -> {
                                emitAudit(tenantId, requestId, "PII_REDACT", collection,
                                    Map.of(
                                        "entityId", entityId,
                                        "fieldCount", changedFields.size(),
                                        "fields", changedFields,
                                        "previousValueHashes", previousValueHashes,
                                        "reason", reason));
                                emitGovernanceEvent(
                                    tenantContext,
                                    requestId,
                                    "PII_REDACT",
                                    collection,
                                    Map.of(
                                        "entityId", entityId,
                                        "fields", changedFields,
                                        "reason", reason));

                                log.info("[DC-E5] PII redact collection={} entityId={} fields={} tenant={}",
                                    collection, entityId, changedFields.size(), tenantId);

                                Map<String, Object> result = new LinkedHashMap<>();
                                result.put("collection", collection);
                                result.put("entityId", savedEntity.id().value());
                                result.put("redactedFields", changedFields.stream().sorted().toList());
                                result.put("requestedFields", fieldsToRedact.stream().sorted().toList());
                                result.put("reason", reason);
                                result.put("status", "REDACTED");
                                result.put("redactedAt", Instant.now().toString());
                                return http.envelopeResponse(
                                    ApiResponse.success(result, tenantId, requestId), objectMapper);
                            });
                    });
                }).whenComplete((response, error) -> traceSupport.finish(handlerSpan, response, error));
    }

    /**
     * {@code GET /api/v1/governance/privacy/pii-fields}
     *
     * <p>Returns the registered PII field mappings: globally-defined fields
     * plus any tenant-specific additions.
     */
    public Promise<HttpResponse> handleListPiiFields(HttpRequest request) {
        String requestId = resolveRequestId(request);
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(missingTenantResponse(requestId));
        }
        String collection = sanitise(request.getQueryParameter("collection"));
        TraceSpanSupport.TraceSpanScope handlerSpan = traceSupport.startSpan(
                request,
                tenantId,
                "datacloud.http.governance.privacy.list_pii_fields",
                traceSupport.requestSpanId(request),
                Map.of("request.id", requestId));

        TenantContext tenantContext = buildTenantContext(tenantId, requestId);
        return loadRetentionPolicies(tenantContext)
            .map(policies -> {
                Set<String> tenantFields = policies.stream()
                    .flatMap(policy -> readPolicyPiiFields(policy).stream())
                    .filter(field -> !GLOBAL_PII_FIELDS.contains(field))
                    .collect(Collectors.toCollection(java.util.TreeSet::new));

                Set<String> effectiveFields = new java.util.TreeSet<>(GLOBAL_PII_FIELDS);
                effectiveFields.addAll(tenantFields);

                List<String> autoDetectedFields = collection == null || collection.isBlank()
                    ? List.of()
                    : derivePiiFields(collection);
                effectiveFields.addAll(autoDetectedFields);

                Map<String, Object> data = new LinkedHashMap<>();
                data.put("globalFields", GLOBAL_PII_FIELDS.stream().sorted().toList());
                data.put("tenantFields", List.copyOf(tenantFields));
                data.put("autoDetectedFields", autoDetectedFields);
                if (collection != null && !collection.isBlank()) {
                    data.put("collection", collection);
                }
                data.put("effectiveCount", effectiveFields.size());
                return http.envelopeResponse(
                    ApiResponse.success(data, tenantId, requestId), objectMapper);
            })
            .whenComplete((response, error) -> traceSupport.finish(handlerSpan, response, error));
    }

    /**
     * {@code GET /api/v1/governance/privacy/verify?collection=X&entityId=Y}
     *
     * <p>Verifies whether the expected PII fields on an entity have already been redacted.
     */
    public Promise<HttpResponse> handleVerifyRedaction(HttpRequest request) {
        String requestId = resolveRequestId(request);
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(missingTenantResponse(requestId));
        }

        String collection = sanitise(request.getQueryParameter("collection"));
        String entityId = sanitise(request.getQueryParameter("entityId"));
        String fieldsParam = sanitise(request.getQueryParameter("fields"));
        TraceSpanSupport.TraceSpanScope handlerSpan = traceSupport.startSpan(
            request,
            tenantId,
            "datacloud.http.governance.privacy.verify",
            traceSupport.requestSpanId(request),
            Map.of("request.id", requestId));

        if (collection.isBlank() || entityId.isBlank()) {
            return Promise.of(http.envelopeResponse(
                ApiResponse.error("MISSING_REQUIRED", "collection and entityId are required", tenantId, requestId),
                objectMapper)).whenComplete((response, error) -> traceSupport.finish(handlerSpan, response, error));
        }

        TenantContext tenantContext = buildTenantContext(tenantId, requestId);
        return loadRetentionPolicy(tenantContext, collection)
            .then(policy -> requireEntityStore().findById(tenantContext, EntityStore.EntityId.of(entityId))
                .map(entityOpt -> {
                    if (entityOpt.isEmpty()) {
                        return http.envelopeResponse(
                            ApiResponse.error("ENTITY_NOT_FOUND", "No entity found for entityId=" + entityId, tenantId, requestId),
                            objectMapper);
                    }

                    EntityStore.Entity entity = entityOpt.get();
                    List<String> fieldsToVerify = resolveVerificationFields(fieldsParam, collection, policy.orElse(null));
                    List<String> verifiedFields = fieldsToVerify.stream()
                        .filter(field -> REDACTED_VALUE.equals(entity.data().get(field)))
                        .sorted()
                        .toList();
                    List<String> pendingFields = fieldsToVerify.stream()
                        .filter(field -> entity.data().containsKey(field) && !REDACTED_VALUE.equals(entity.data().get(field)))
                        .sorted()
                        .toList();
                    List<String> absentFields = fieldsToVerify.stream()
                        .filter(field -> !entity.data().containsKey(field))
                        .sorted()
                        .toList();

                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("collection", collection);
                    data.put("entityId", entityId);
                    data.put("status", pendingFields.isEmpty() ? "VERIFIED" : "NOT_REDACTED");
                    data.put("verifiedFields", verifiedFields);
                    data.put("pendingFields", pendingFields);
                    data.put("absentFields", absentFields);
                    data.put("requestedFields", fieldsToVerify);
                    data.put("verifiedAt", Instant.now().toString());
                    return http.envelopeResponse(
                        ApiResponse.success(data, tenantId, requestId), objectMapper);
                }))
            .whenComplete((response, error) -> traceSupport.finish(handlerSpan, response, error));
    }

    /**
     * {@code GET /api/v1/governance/compliance/summary}
     *
     * <p>Returns a high-level compliance posture summary for the tenant:
     * collections pending classification, unredacted PII count estimate,
     * active legal holds, and upcoming retention expirations.
     */
    public Promise<HttpResponse> handleComplianceSummary(HttpRequest request) {
        String requestId = resolveRequestId(request);
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(missingTenantResponse(requestId));
        }
        TraceSpanSupport.TraceSpanScope handlerSpan = traceSupport.startSpan(
                request,
                tenantId,
                "datacloud.http.governance.compliance.summary",
                traceSupport.requestSpanId(request),
                Map.of("request.id", requestId));

        TenantContext tenantContext = buildTenantContext(tenantId, requestId);
        return loadRetentionPolicies(tenantContext)
            .then(policies -> {
                Map<String, Object> summary = buildBaseComplianceSummary(tenantId, policies);
                if (auditService instanceof AuditSummaryProvider auditSummaryProvider) {
                    return auditSummaryProvider.summarize(tenantId, Instant.now().minusSeconds(30L * 24 * 60 * 60), 500)
                        .map(auditSummary -> enrichComplianceSummary(summary, auditSummary))
                        .map(enrichedSummary -> http.envelopeResponse(
                            ApiResponse.success(enrichedSummary, tenantId, requestId), objectMapper));
                }

                return Promise.of(http.envelopeResponse(
                    ApiResponse.success(summary, tenantId, requestId), objectMapper));
            })
            .whenComplete((response, error) -> traceSupport.finish(handlerSpan, response, error));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Derives likely PII fields for a collection by name convention.
     * Production would read from the tenant PII registry.
     */
    private static List<String> derivePiiFields(String collection) {
        if (collection.contains("user") || collection.contains("person") || collection.contains("customer")) {
            return GLOBAL_PII_FIELDS.stream().sorted().toList();
        }
        return List.of();
    }

    private Map<String, Object> buildBaseComplianceSummary(String tenantId, List<Map<String, Object>> policies) {
        long collectionsClassified = policies.size();
        long collectionsTotal = collectionsClassified;
        long expiringIn30Days = policies.stream()
            .map(policy -> policy.get("expiresAt"))
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .map(this::parseInstantSafely)
            .filter(instant -> instant != null && !instant.isBefore(Instant.now())
                && !instant.isAfter(Instant.now().plusSeconds(30L * 24 * 60 * 60)))
            .count();
        Set<String> tenantSpecificPiiFields = policies.stream()
            .flatMap(policy -> readPolicyPiiFields(policy).stream())
            .collect(Collectors.toCollection(java.util.TreeSet::new));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("tenantId", tenantId);
        summary.put("collectionsTotal", collectionsTotal);
        summary.put("collectionsClassified", collectionsClassified);
        summary.put("collectionsUnclassified", 0L);
        summary.put("piiFieldsRegistered", tenantSpecificPiiFields.size());
        summary.put("legalHoldsActive", 0);
        summary.put("retentionExpirationsIn30Days", expiringIn30Days);
        summary.put("lastAuditAt", Instant.EPOCH.toString());
        summary.put("auditEventsIn30Days", 0L);
        summary.put("authFailuresIn30Days", 0L);
        summary.put("redactionsIn30Days", 0L);
        summary.put("purgesIn30Days", 0L);
        summary.put("recentAuditEvents", List.of());
        summary.put("complianceStatus", collectionsClassified == 0 ? "NEEDS_CLASSIFICATION" : "COMPLIANT");
        summary.put("generatedAt", Instant.now().toString());
        return summary;
    }

    private Map<String, Object> enrichComplianceSummary(
            Map<String, Object> baseSummary,
            AuditSummaryProvider.AuditSummary auditSummary) {
        Map<String, Long> eventCounts = auditSummary.eventCounts();
        long totalAuditEvents = eventCounts.values().stream().mapToLong(Long::longValue).sum();
        long authFailures = eventCounts.getOrDefault("AUTH_FAILURE", 0L);
        long redactions = eventCounts.getOrDefault("PII_REDACT", 0L);
        long purges = eventCounts.getOrDefault("RETENTION_PURGE", 0L)
            + eventCounts.getOrDefault("RETENTION_PURGE_DRY_RUN", 0L)
            + eventCounts.getOrDefault("RETENTION_PURGE_REJECTED", 0L);

        baseSummary.put("lastAuditAt", auditSummary.lastAuditAt().toString());
        baseSummary.put("auditEventsIn30Days", totalAuditEvents);
        baseSummary.put("authFailuresIn30Days", authFailures);
        baseSummary.put("redactionsIn30Days", redactions);
        baseSummary.put("purgesIn30Days", purges);
        baseSummary.put("recentAuditEvents", auditSummary.recentEvents());
        if (authFailures > 0) {
            baseSummary.put("complianceStatus", "REVIEW_REQUIRED");
        }
        return baseSummary;
    }

    private Instant parseInstantSafely(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private EntityStore requireEntityStore() {
        EntityStore entityStore = client.entityStore();
        if (entityStore == null) {
            throw new IllegalStateException("EntityStore is not configured for governance operations.");
        }
        return entityStore;
    }

    private TenantContext buildTenantContext(String tenantId, String requestId) {
        return TenantContext.of(tenantId).withMetadata("requestId", requestId);
    }

    private EntityStore.QuerySpec buildCollectionQuery(String collection) {
        return EntityStore.QuerySpec.builder()
            .collection(collection)
            .limit(PURGE_QUERY_LIMIT)
            .build();
    }

    private List<EntityStore.Entity> findPurgeCandidates(Map<String, Object> policy,
                                                         List<EntityStore.Entity> entities) {
        Instant now = Instant.now();
        Instant policyCutoff = resolvePolicyCutoff(policy, now);
        return entities.stream()
            .filter(entity -> isEntityExpired(entity, policyCutoff, now))
            .toList();
    }

    private Promise<Map<String, Object>> saveRetentionPolicy(TenantContext tenantContext,
                                                             String collection,
                                                             Map<String, Object> policy) {
        EntityStore.Entity entity = EntityStore.Entity.builder()
            .id(policyId(collection))
            .collection(GOVERNANCE_POLICY_COLLECTION)
            .data(policy)
            .build();
        return requireEntityStore().save(tenantContext, entity)
            .map(saved -> new LinkedHashMap<>(saved.data()));
    }

    private Promise<Void> savePurgeTombstone(TenantContext tenantContext,
                                             String collection,
                                             List<String> entityIds,
                                             int deletedCount,
                                             int requestedCount,
                                             String confirmationTokenHash,
                                             String requestId) {
        Map<String, Object> tombstone = new LinkedHashMap<>();
        tombstone.put("collection", collection);
        tombstone.put("entityIds", entityIds);
        tombstone.put("deletedCount", deletedCount);
        tombstone.put("requestedCount", requestedCount);
        tombstone.put("confirmationTokenHash", confirmationTokenHash);
        tombstone.put("requestId", requestId);
        tombstone.put("purgedAt", Instant.now().toString());
        tombstone.put("status", "PURGED");

        EntityStore.Entity entity = EntityStore.Entity.builder()
            .collection(GOVERNANCE_PURGE_TOMBSTONE_COLLECTION)
            .data(tombstone)
            .build();
        return requireEntityStore().save(tenantContext, entity).map(ignored -> null);
    }

    private Promise<Optional<Map<String, Object>>> loadRetentionPolicy(TenantContext tenantContext,
                                                                       String collection) {
        return requireEntityStore().findById(tenantContext, EntityStore.EntityId.of(policyId(collection)))
            .map(found -> found
                .filter(entity -> GOVERNANCE_POLICY_COLLECTION.equals(entity.collection()))
                .map(entity -> new LinkedHashMap<>(entity.data())));
    }

    private Promise<List<Map<String, Object>>> loadRetentionPolicies(TenantContext tenantContext) {
        return requireEntityStore().query(tenantContext, EntityStore.QuerySpec.builder()
                .collection(GOVERNANCE_POLICY_COLLECTION)
                .limit(PURGE_QUERY_LIMIT)
                .build())
            .map(result -> result.entities().stream()
                .map(entity -> new LinkedHashMap<>(entity.data()))
                .collect(Collectors.toList()));
    }

    private Map<String, Object> defaultRetentionPolicy(String collection) {
        return Map.of(
            "collection", collection,
            "tier", "standard",
            "retentionDays", 365,
            "legalHolds", List.of(),
            "piiFields", derivePiiFields(collection),
            "lastClassifiedAt", Instant.EPOCH.toString(),
            "status", POLICY_STATUS_DEFAULT
        );
    }

    private HttpResponse missingTenantResponse(String requestId) {
        return http.envelopeResponse(
            ApiResponse.error(
                MISSING_TENANT_ERROR,
                "X-Tenant-Id header or tenantId query parameter is required",
                "unknown",
                requestId),
            objectMapper);
    }

    private List<String> resolveRequestedPiiFields(Object rawPiiFields, String collection) {
        if (!(rawPiiFields instanceof List<?> piiFields) || piiFields.isEmpty()) {
            return derivePiiFields(collection);
        }

        return piiFields.stream()
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .map(DataLifecycleHandler::sanitise)
            .filter(field -> !field.isBlank())
            .distinct()
            .sorted()
            .toList();
    }

    private List<String> readPolicyPiiFields(Map<String, Object> policy) {
        Object raw = policy.get("piiFields");
        if (!(raw instanceof List<?> fields)) {
            return List.of();
        }

        return fields.stream()
            .filter(Objects::nonNull)
            .map(Object::toString)
            .map(DataLifecycleHandler::sanitise)
            .filter(field -> !field.isBlank())
            .distinct()
            .sorted()
            .toList();
    }

    private List<String> resolveVerificationFields(String fieldsParam,
                                                   String collection,
                                                   Map<String, Object> policy) {
        if (fieldsParam != null && !fieldsParam.isBlank()) {
            return List.of(fieldsParam.split(",")).stream()
                .map(DataLifecycleHandler::sanitise)
                .filter(field -> !field.isBlank())
                .distinct()
                .sorted()
                .toList();
        }

        java.util.TreeSet<String> fields = new java.util.TreeSet<>(GLOBAL_PII_FIELDS);
        fields.addAll(derivePiiFields(collection));
        if (policy != null) {
            fields.addAll(readPolicyPiiFields(policy));
        }
        return List.copyOf(fields);
    }

    private Instant resolvePolicyCutoff(Map<String, Object> policy, Instant now) {
        if (policy == null) {
            return null;
        }

        Object retentionDaysValue = policy.get("retentionDays");
        if (!(retentionDaysValue instanceof Number retentionDaysNumber)) {
            return null;
        }

        long retentionDays = retentionDaysNumber.longValue();
        if (retentionDays <= 0 || retentionDays >= (Integer.MAX_VALUE / 365L)) {
            return null;
        }
        return now.minusSeconds(retentionDays * 86_400L);
    }

    private boolean isEntityExpired(EntityStore.Entity entity, Instant policyCutoff, Instant now) {
        Instant explicitExpiry = extractExplicitExpiry(entity);
        if (explicitExpiry != null) {
            return !explicitExpiry.isAfter(now);
        }
        if (policyCutoff == null) {
            return false;
        }
        Instant updatedAt = entity.metadata().updatedAt();
        return !updatedAt.isAfter(policyCutoff);
    }

    private Instant extractExplicitExpiry(EntityStore.Entity entity) {
        for (String field : List.of("retentionExpiresAt", "expiresAt", "ttlExpiresAt")) {
            Object value = entity.data().get(field);
            if (value instanceof String timestamp && !timestamp.isBlank()) {
                try {
                    return Instant.parse(timestamp);
                } catch (RuntimeException ignored) {
                    // Ignore malformed timestamps and continue with policy-based expiry.
                }
            }
        }
        return null;
    }

    private void emitGovernanceEvent(TenantContext tenantContext,
                                     String requestId,
                                     String eventType,
                                     String collection,
                                     Map<String, Object> payload) {
        EventLogStore eventLogStore = client.eventLogStore();
        if (eventLogStore == null) {
            return;
        }

        Map<String, Object> eventPayload = new LinkedHashMap<>(payload);
        eventPayload.put("collection", collection);
        eventPayload.put("requestId", requestId);
        eventPayload.put("timestamp", Instant.now().toString());

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(eventPayload);
            com.ghatana.platform.domain.eventstore.TenantContext eventTenantContext =
                com.ghatana.platform.domain.eventstore.TenantContext.of(
                    tenantContext.tenantId(),
                    tenantContext.metadata());
            EventLogStore.EventEntry eventEntry = EventLogStore.EventEntry.builder()
                .eventType(eventType)
                .eventVersion("1.0.0")
                .timestamp(Instant.now())
                .payload(ByteBuffer.wrap(bytes))
                .contentType("application/json")
                .headers(Map.of(
                    "tenantId", tenantContext.tenantId(),
                    "collection", collection,
                    "requestId", requestId))
                .build();
            eventLogStore.append(eventTenantContext, eventEntry)
                .whenException(error -> log.warn("[DC-E5] governance event emit failed: {}", error.getMessage()));
        } catch (Exception exception) {
            log.warn("[DC-E5] governance event serialization failed: {}", exception.getMessage());
        }
    }

    private void emitAudit(String tenantId, String requestId, String eventType,
                           String resourceId, Map<String, Object> details) {
        if (auditService == null) return;
        AuditEvent.Builder builder = AuditEvent.builder()
            .tenantId(tenantId)
            .eventType(eventType)
            .resourceType("GOVERNANCE")
            .resourceId(resourceId)
            .success(true)
            .detail("requestId", requestId);
        details.forEach(builder::detail);
        auditService.record(builder.build())
            .whenException(e -> log.warn("[DC-E5] audit emit failed: {}", e.getMessage()));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseBody(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static String sanitise(String input) {
        if (input == null) return "";
        return input.replaceAll("[\\x00-\\x1F`\\\\<>]", "").strip();
    }

    private static String resolveRequestId(HttpRequest request) {
        String rid = request.getHeader(io.activej.http.HttpHeaders.of("X-Request-ID"));
        return (rid != null && !rid.isBlank()) ? rid : UUID.randomUUID().toString();
    }

    private static String policyId(String collection) {
        return "retention-policy:" + collection;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HMAC purge token helpers (P2.1.2)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds a time-limited HMAC-SHA256 purge confirmation token.
     *
     * <p>Format: {@code Base64Url( epochMs + "." + HMAC-SHA256(secret, tenantId+":"+collection+":"+epochMs) )}
     */
    static String buildPurgeToken(String tenantId, String collection, long issuedAtMs) {
        return buildPurgeToken(tenantId, collection, issuedAtMs, runtimeEnvironment());
    }

    static String buildPurgeToken(String tenantId, String collection, long issuedAtMs, Map<String, String> env) {
        String payload = tenantId + ":" + collection + ":" + issuedAtMs;
        String hmac = hmacSha256Hex(resolvePurgeTokenSecret(env), payload);
        String raw = issuedAtMs + "." + hmac;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Validates a purge confirmation token against tenant + collection and expiry window.
     */
    static TokenValidationResult validatePurgeToken(String token, String tenantId, String collection) {
        return validatePurgeToken(token, tenantId, collection, runtimeEnvironment());
    }

    static TokenValidationResult validatePurgeToken(String token, String tenantId, String collection, Map<String, String> env) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(token);
            String raw = new String(decoded, StandardCharsets.UTF_8);
            int dotIdx = raw.indexOf('.');
            if (dotIdx < 1) {
                return TokenValidationResult.failure("malformed token");
            }
            long issuedAtMs = Long.parseLong(raw.substring(0, dotIdx));
            String providedHmac = raw.substring(dotIdx + 1);

            long ageMs = Instant.now().toEpochMilli() - issuedAtMs;
            if (ageMs > PURGE_TOKEN_VALIDITY_MS) {
                return TokenValidationResult.failure("token expired (age=" + (ageMs / 1000) + "s, max=300s)");
            }
            if (ageMs < 0) {
                return TokenValidationResult.failure("token issued in the future");
            }

            String expectedHmac = hmacSha256Hex(resolvePurgeTokenSecret(env),
                    tenantId + ":" + collection + ":" + issuedAtMs);
            if (!constantTimeEquals(expectedHmac, providedHmac)) {
                return TokenValidationResult.failure("token signature mismatch");
            }
            return TokenValidationResult.success();
        } catch (IllegalArgumentException e) {
            return TokenValidationResult.failure("token decode error: " + e.getMessage());
        }
    }

    static TokenSecretRequirement validatePurgeTokenSecretConfiguration(Map<String, String> env) {
        if (resolveConfiguredPurgeTokenSecret(env).isPresent()) {
            return TokenSecretRequirement.available(resolveProfileName(env));
        }
        if (allowsEphemeralPurgeTokenSecret(env)) {
            return TokenSecretRequirement.available(resolveProfileName(env));
        }
        return TokenSecretRequirement.unavailable(
            resolveProfileName(env),
            PURGE_TOKEN_SECRET_ENV + " must be configured for purge operations outside local or sovereign profiles"
        );
    }

    private static byte[] resolvePurgeTokenSecret(Map<String, String> env) {
        return resolveConfiguredPurgeTokenSecret(env)
            .orElse(EPHEMERAL_PURGE_TOKEN_SECRET);
    }

    private static Optional<byte[]> resolveConfiguredPurgeTokenSecret(Map<String, String> env) {
        String configured = env.get(PURGE_TOKEN_SECRET_ENV);
        if (configured == null || configured.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(configured.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean allowsEphemeralPurgeTokenSecret(Map<String, String> env) {
        return DataCloudLauncherSettings.isEmbeddedProfile(
            DataCloudLauncherSettings.resolveProfile(new String[0], env));
    }

    private static String resolveProfileName(Map<String, String> env) {
        String rawProfile = env.get(DATACLOUD_PROFILE_ENV);
        return (rawProfile == null || rawProfile.isBlank()) ? "local" : rawProfile;
    }

    private static Map<String, String> runtimeEnvironment() {
        Map<String, String> env = new HashMap<>(System.getenv());
        putSystemPropertyOverride(env, DATACLOUD_PROFILE_ENV);
        putSystemPropertyOverride(env, PURGE_TOKEN_SECRET_ENV);
        return Map.copyOf(env);
    }

    private static void putSystemPropertyOverride(Map<String, String> env, String key) {
        String value = System.getProperty(key);
        if (value != null) {
            env.put(key, value);
        }
    }

    private static String hmacSha256Hex(byte[] secret, String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            byte[] result = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(result);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Constant-time string comparison to prevent timing attacks. */
    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }

    /** Result of a purge token validation. */
    record TokenValidationResult(boolean valid, String reason) {
        static TokenValidationResult success()                { return new TokenValidationResult(true, null); }
        static TokenValidationResult failure(String reason)   { return new TokenValidationResult(false, reason); }
    }

    record TokenSecretRequirement(boolean available, String profile, String message) {
        static TokenSecretRequirement available(String profile) {
            return new TokenSecretRequirement(true, profile, null);
        }

        static TokenSecretRequirement unavailable(String profile, String message) {
            return new TokenSecretRequirement(false, profile, message);
        }
    }
}
