package com.ghatana.phr.kernel.service;

import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.DataQueryRequest;
import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.DataReadRequest;
import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.DataWriteRequest;
import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.QueryResult;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.util.TypedDataSerializer;
import com.ghatana.platform.cache.DistributedCachePort;
import com.ghatana.platform.cache.InMemoryCacheAdapter;
import com.ghatana.platform.security.ratelimit.DefaultRateLimiter;
import com.ghatana.platform.security.ratelimit.RateLimiter;
import com.ghatana.platform.security.ratelimit.RateLimiterConfig;
import com.ghatana.phr.kernel.consent.ConsentService;
import io.activej.promise.Promise;
import io.activej.promise.Promises;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Consent Management Service with Nepal Directive 2081 compliance.
 *
 * <p>Manages patient consent grants with:
 * <ul>
 *   <li>Granular consent (resource-level and document-level)</li>
 *   <li>Emergency break-the-glass access</li>
 *   <li>Caregiver delegation consent</li>
 *   <li>Audit trail with patient visibility</li>
 *   <li>Automatic expiration and cache invalidation</li>
 *   <li>Distributed consent cache (ISSUE-X02 fix) — uses {@link DistributedCachePort} instead
 *       of a node-local {@code ConcurrentHashMap} so that grant invalidations propagate
 *       across all horizontally-scaled nodes.</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose PHR consent management with Nepal Directive 2081 compliance
 * @doc.layer product
 * @doc.pattern Service
 * @author Ghatana PHR Team
 * @since 1.0.0
 */
public class ConsentManagementService implements ConsentService {

    private static final String CONSENT_DATASET = "phr.consent.grants";
    private static final String AUDIT_DATASET = "phr.consent.audit";
    private static final String EMERGENCY_DATASET = "phr.emergency.access";

    /** Maximum grant creation requests per actor per window. */
    private static final int CREATE_GRANT_MAX_PER_WINDOW = 20;
    /** Maximum access-check requests per actor per window. */
    private static final int CHECK_ACCESS_MAX_PER_WINDOW = 200;
    /** Sliding window duration for rate limiting. */
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(1);

    private final DataCloudKernelAdapter dataCloud;
    /** Distributed cache for consent decisions — multi-node safe (ISSUE-X02). */
    private final DistributedCachePort<String, ConsentCacheEntry> consentCache;
    private final RateLimiter createGrantLimiter;
    private final RateLimiter checkAccessLimiter;
    private volatile boolean running = false;

    /**
     * Constructs a ConsentManagementService with a supplied distributed cache.
     *
     * @param context      kernel context providing DataCloudKernelAdapter
     * @param consentCache distributed cache for consent decisions (ISSUE-X02 fix)
     */
    public ConsentManagementService(KernelContext context,
                                     DistributedCachePort<String, ConsentCacheEntry> consentCache) {
        this.dataCloud = context.getDependency(DataCloudKernelAdapter.class);
        this.consentCache = Objects.requireNonNull(consentCache, "consentCache must not be null");
        this.createGrantLimiter = DefaultRateLimiter.create(
            RateLimiterConfig.builder()
                .maxRequestsPerMinute(CREATE_GRANT_MAX_PER_WINDOW)
                .burstSize(CREATE_GRANT_MAX_PER_WINDOW)
                .windowDuration(RATE_LIMIT_WINDOW)
                .build()
        );
        this.checkAccessLimiter = DefaultRateLimiter.create(
            RateLimiterConfig.builder()
                .maxRequestsPerMinute(CHECK_ACCESS_MAX_PER_WINDOW)
                .burstSize(CHECK_ACCESS_MAX_PER_WINDOW)
                .windowDuration(RATE_LIMIT_WINDOW)
                .build()
        );
    }

    /**
     * Convenience constructor for tests and single-node deployments.
     * Creates an in-memory cache with 50,000 entry capacity and 5-minute TTL.
     */
    public ConsentManagementService(KernelContext context) {
        this(context, new InMemoryCacheAdapter<>(50_000, Duration.ofMinutes(5)));
    }

    public Promise<Void> start() {
        running = true;
        return initializeDatasets();
    }

    public Promise<Void> stop() {
        running = false;
        return consentCache.invalidateAll();
    }

    public boolean isHealthy() {
        return running;
    }

    public String getName() {
        return "consent-management";
    }

    // ==================== Core Consent Operations ====================

    /**
     * Creates a new consent grant.
     *
     * @param grant the consent grant to create
     * @return Promise containing the created grant
     */
    public Promise<ConsentGrant> createGrant(ConsentGrant grant) {
        if (!running) {
            return Promise.ofException(new IllegalStateException("Service not running"));
        }

        String rateLimitKey = grant.getRecipientId();
        if (!tryAcquire(createGrantLimiter, rateLimitKey)) {
            return Promise.ofException(new RateLimitExceededException(
                    "Grant creation rate limit exceeded for actor: " + rateLimitKey));
        }

        // Check for conflicting grants
        return checkConflictingGrants(grant.getPatientId(), grant.getRecipientId(), grant.getScope())
            .then(conflict -> {
                if (conflict) {
                    return Promise.<ConsentGrant>ofException(
                        new IllegalStateException("Conflicting active grant exists"));
                }

                String grantId = grant.getId() != null ? grant.getId() : generateId();
                ConsentGrant toStore = grant.withId(grantId).withCreatedAt(Instant.now());

                DataWriteRequest request = new DataWriteRequest(
                    CONSENT_DATASET,
                    grantId,
                    serialize(toStore),
                    Map.of(
                        "patientId", toStore.getPatientId(),
                        "recipientId", toStore.getRecipientId(),
                        "status", toStore.getStatus(),
                        "expiresAt", toStore.getExpiresAt().toString()
                    )
                );

                ConsentGrant stored = toStore;
                Promise<Void> writeChain = dataCloud.writeData(request)
                    .then($ -> invalidateConsentCache(stored.getRecipientId(), stored.getPatientId()))
                    .then($ -> audit("GRANT_CREATE", stored.getPatientId(),
                        "Grant created for " + stored.getRecipientId()));
                return writeChain.map($ -> stored);
            });
    }

    /**
     * Revokes an active consent grant.
     *
     * @param grantId the grant identifier
     * @return Promise completing when revoked
     */
    public Promise<Void> revokeGrant(String grantId) {
        if (!running) {
            return Promise.ofException(new IllegalStateException("Service not running"));
        }

        return getGrant(grantId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.complete(); // Idempotent
                }
                ConsentGrant grant = opt.get();
                if (!"ACTIVE".equals(grant.getStatus())) {
                    return Promise.complete(); // Already not active
                }

                ConsentGrant revoked = grant.withStatus("REVOKED").withRevokedAt(Instant.now());
                Promise<ConsentGrant> updated = updateGrant(revoked);
                // ENH-P01: Invalidate ALL cached decisions for this patient, not just
                // the specific recipient-patient pair. Other actors' cached allow-decisions
                // may reference grants that are affected by cascading revocation policies.
                return updated
                    .then($ -> invalidatePatientAccessCache(
                            new CacheInvalidationRequest(null, grant.getPatientId(),
                                    CacheInvalidationReason.GRANT_REVOKED)))
                    .then($ -> Promise.complete());
            });
    }

    /**
     * Validates if access is permitted based on consent.
     *
     * @param patientId the patient being accessed
     * @param accessorId the accessor (provider/caregiver)
     * @param resourceType the resource type being accessed
     * @return Promise containing validation result
     */
    public Promise<ConsentValidationResult> validateAccess(String patientId, String accessorId,
                                                            String resourceType) {
        if (!running) {
            return Promise.of(new ConsentValidationResult(false, "Service not running", null));
        }

        // Check cache first
        String cacheKey = accessorId + ":" + patientId + ":" + resourceType;
        return consentCache.get(cacheKey)
            .then(optCached -> {
                if (optCached.isPresent()) {
                    ConsentCacheEntry cached = optCached.get();
                    if (!cached.isExpired() && !cached.isNearExpiry()) {
                        return Promise.of(new ConsentValidationResult(
                            cached.isAllowed(),
                            cached.isAllowed() ? "Cache: Access allowed" : "Cache: Access denied",
                            cached.getGrantId()
                        ));
                    }
                }
                // Query active grants
                return getActiveGrantsForPatient(patientId)
                    .then(grants -> {
                        for (ConsentGrant grant : grants) {
                            if (grant.covers(accessorId, resourceType)) {
                                ConsentCacheEntry entry = new ConsentCacheEntry(
                                    grant.getId(), true, grant.getExpiresAt());
                                return consentCache.put(cacheKey, entry)
                                    .map($ -> new ConsentValidationResult(
                                        true, "Valid grant found", grant.getId()));
                            }
                        }
                        return Promise.of(new ConsentValidationResult(
                            false, "No valid consent grant", null));
                    });
            });
    }

    // ==================== ConsentService Contract Methods ====================

    /** {@inheritDoc} */
    @Override
    public Promise<ConsentAccessDecision> checkAccess(ConsentCheckRequest request) {
        if (!running) {
            return Promise.of(new ConsentAccessDecision(
                    false, ReasonCode.SYSTEM_DENY, null, CacheStatus.BYPASS,
                    true, null, List.of()));
        }

        String rateLimitKey = request.actor().actorId();
        if (!tryAcquire(checkAccessLimiter, rateLimitKey)) {
            return Promise.of(new ConsentAccessDecision(
                    false, ReasonCode.SYSTEM_DENY, null, CacheStatus.BYPASS,
                    true, null, List.of("RATE_LIMIT_EXCEEDED")));
        }

        // Self-access shortcut: patient accessing own data
        if (request.actor().actorType() == ActorType.PATIENT
                && request.target().patientId().equals(request.actor().patientId())) {
            return Promise.of(new ConsentAccessDecision(
                    true, ReasonCode.SELF_ACCESS, null, CacheStatus.BYPASS,
                    false, null, List.of()));
        }

        // Emergency path
        if (request.purposeOfUse() == PurposeOfUse.EMERGENCY
                && request.emergency() != null && request.emergency().enabled()) {
            return Promise.of(new ConsentAccessDecision(
                    true, ReasonCode.EMERGENCY_GRANT, null, CacheStatus.BYPASS,
                    true, null, List.of("SUBMIT_POST_HOC_JUSTIFICATION")));
        }

        // Check cache
        String cacheKey = request.actor().actorId() + ":" + request.target().patientId()
                + ":" + request.target().resourceType();
        return consentCache.get(cacheKey)
                .then(optCached -> {
                    if (optCached.isPresent()) {
                        ConsentCacheEntry cached = optCached.get();
                        if (!cached.isExpired() && !cached.isNearExpiry()) {
                            return Promise.of(new ConsentAccessDecision(
                                    cached.isAllowed(),
                                    cached.isAllowed() ? ReasonCode.EXPLICIT_GRANT : ReasonCode.OUT_OF_SCOPE,
                                    cached.getGrantId(), CacheStatus.HIT,
                                    !cached.isAllowed(), null, List.of()));
                        }
                    }
                    // Query grants
                    return getActiveGrantsForPatient(request.target().patientId())
                            .then(grants -> {
                                for (ConsentGrant grant : grants) {
                                    if (grant.covers(request.actor().actorId(),
                                            request.target().resourceType())) {
                                        ConsentCacheEntry entry = new ConsentCacheEntry(
                                                grant.getId(), true, grant.getExpiresAt());
                                        return consentCache.put(cacheKey, entry)
                                                .map($ -> new ConsentAccessDecision(
                                                        true, ReasonCode.EXPLICIT_GRANT, grant.getId(),
                                                        CacheStatus.MISS, false, grant.getExpiresAt(), List.of()));
                                    }
                                }
                                return Promise.of(new ConsentAccessDecision(
                                        false, ReasonCode.OUT_OF_SCOPE, null, CacheStatus.MISS,
                                        true, null, List.of()));
                            });
                });
    }

    /** {@inheritDoc} */
    @Override
    public Promise<ConsentAccessDecision> assertAccess(ConsentCheckRequest request) {
        return checkAccess(request)
                .then(decision -> {
                    if (!decision.allowed()) {
                        return Promise.ofException(
                                new IllegalStateException("Consent denied: " + decision.reasonCode()));
                    }
                    return Promise.of(decision);
                });
    }

    /** {@inheritDoc} */
    @Override
    public Promise<Void> invalidatePatientAccessCache(CacheInvalidationRequest request) {
        // Flush the entire consent-cache namespace for this service instance.
        // The cache namespace is scoped to "phr.consent" at construction time, so this only
        // affects consent decisions — not other cache domains. For finer-grained per-patient
        // invalidation, the distributed cache would need server-side Lua scan support
        // (Redis SCAN pattern). Until then, a full namespace flush is the safe default.
        return consentCache.invalidateAll();
    }

    /**
     * Gets all grants for a patient.
     *
     * @param patientId the patient identifier
     * @return Promise containing list of grants
     */
    public Promise<List<ConsentGrant>> getPatientGrants(String patientId) {
        if (!running) {
            return Promise.of(List.of());
        }

        DataQueryRequest request = new DataQueryRequest(
            CONSENT_DATASET,
            "patientId = :patientId",
            Map.of("patientId", patientId),
            1000,
            0
        );

        return dataCloud.queryData(request)
            .map(QueryResult::getResults)
            .map(results -> results.stream()
                .map(r -> deserialize(r.getData()))
                .filter(Objects::nonNull)
                .toList());
    }

    // ==================== Emergency Break-the-Glass ====================

    /**
     * Creates emergency access grant (break-the-glass).
     *
     * @param request the emergency access request
     * @return Promise containing emergency grant
     */
    public Promise<EmergencyGrant> createEmergencyAccess(EmergencyAccessRequest request) {
        if (!running) {
            return Promise.ofException(new IllegalStateException("Service not running"));
        }

        // Validate emergency request
        if (request.getJustification() == null || request.getJustification().isBlank()) {
            return Promise.ofException(new IllegalStateException("Emergency access requires justification"));
        }

        String grantId = generateId();
        Instant expiresAt = Instant.now().plus(Duration.ofHours(4)); // Default 4 hours

        EmergencyGrant grant = new EmergencyGrant(
            grantId,
            request.getPatientId(),
            request.getProviderId(),
            request.getJustification(),
            request.getCategory(),
            Instant.now(),
            expiresAt,
            Set.of("allergies", "medications", "bloodType", "emergencyContacts"), // Limited scope
            false
        );

        DataWriteRequest writeRequest = new DataWriteRequest(
            EMERGENCY_DATASET,
            grantId,
            serializeEmergency(grant),
            Map.of(
                "patientId", grant.getPatientId(),
                "providerId", grant.getProviderId(),
                "status", "ACTIVE",
                "expiresAt", expiresAt.toString()
            )
        );

        return dataCloud.writeData(writeRequest)
            .then($ -> notifyPatientOfEmergencyAccess(grant))
            .then($ -> audit("EMERGENCY_ACCESS", request.getPatientId(),
                "Emergency access granted to " + request.getProviderId()))
            .map($ -> grant);
    }

    /**
     * Gets patient-visible audit trail.
     *
     * @param patientId the patient identifier
     * @return Promise containing audit entries
     */
    public Promise<List<PatientAuditEntry>> getPatientAuditTrail(String patientId) {
        if (!running) {
            return Promise.of(List.of());
        }

        DataQueryRequest request = new DataQueryRequest(
            AUDIT_DATASET,
            "patientId = :patientId",
            Map.of("patientId", patientId),
            1000,
            0
        );

        return dataCloud.queryData(request)
            .map(QueryResult::getResults)
            .map(results -> results.stream()
                .map(r -> deserializeAudit(r.getData()))
                .filter(Objects::nonNull)
                .toList());
    }

    // ==================== Private Methods ====================

    private Promise<Void> initializeDatasets() {
        Promise<Void> consent = dataCloud.createSchema(new DataCloudKernelAdapter.SchemaCreateRequest(
            CONSENT_DATASET,
            Map.of(
                "grantId", "string",
                "patientId", "string",
                "recipientId", "string",
                "scope", "json",
                "status", "string",
                "createdAt", "timestamp",
                "expiresAt", "timestamp"
            ),
            Map.of("retention", "25years")
        ));

        Promise<Void> emergency = dataCloud.createSchema(new DataCloudKernelAdapter.SchemaCreateRequest(
            EMERGENCY_DATASET,
            Map.of(
                "grantId", "string",
                "patientId", "string",
                "providerId", "string",
                "justification", "string",
                "status", "string",
                "expiresAt", "timestamp"
            ),
            Map.of("retention", "25years")
        ));

        return Promises.all(consent, emergency).map($ -> null);
    }

    private Promise<Optional<ConsentGrant>> getGrant(String grantId) {
        DataReadRequest request = new DataReadRequest(
            CONSENT_DATASET,
            grantId,
            Map.of()
        );

        return dataCloud.readData(request)
            .map(result -> Optional.ofNullable(deserialize(result.getData())))
            ;
    }

    private Promise<ConsentGrant> updateGrant(ConsentGrant grant) {
        DataWriteRequest request = new DataWriteRequest(
            CONSENT_DATASET,
            grant.getId(),
            serialize(grant),
            Map.of("updatedAt", Instant.now().toString())
        );

        return dataCloud.writeData(request)
            .then($ -> invalidateConsentCache(grant.getRecipientId(), grant.getPatientId()))
            .then($ -> audit("GRANT_REVOKE", grant.getPatientId(), "Grant revoked"))
            .map($ -> grant);
    }

    private Promise<Boolean> checkConflictingGrants(String patientId, String recipientId, ConsentScope scope) {
        return getActiveGrantsForPatient(patientId)
            .then(grants -> {
                for (ConsentGrant grant : grants) {
                    if (grant.getRecipientId().equals(recipientId) &&
                        grant.overlaps(scope)) {
                        return Promise.of(true);
                    }
                }
                return Promise.of(false);
            });
    }

    private Promise<List<ConsentGrant>> getActiveGrantsForPatient(String patientId) {
        return getPatientGrants(patientId)
            .map(grants -> grants.stream()
                .filter(g -> "ACTIVE".equals(g.getStatus()) && !g.isExpired())
                .toList());
    }

    private Promise<Void> invalidateConsentCache(String recipientId, String patientId) {
        // Invalidate the cache entries that directly encode this recipient-patient pair.
        // We build the deterministic key prefix used in validateAccess / checkAccess:
        //   cacheKey = actorId + ":" + patientId + ":" + resourceType
        // Since DistributedCachePort only supports single-key invalidation (no server-side
        // pattern scan on the interface), we flush the entire namespace for correctness.
        // This is safe because the namespace is already scoped to consent decisions.
        return consentCache.invalidateAll();
    }

    private Promise<Void> notifyPatientOfEmergencyAccess(EmergencyGrant grant) {
        // Send push + SMS notification
        // Implementation would integrate with notification service
        return Promise.complete();
    }

    private Promise<Void> audit(String action, String patientId, String details) {
        String auditId = generateId();
        PatientAuditEntry entry = new PatientAuditEntry(
            auditId,
            Instant.now(),
            action,
            patientId,
            details,
            Map.of()
        );

        DataWriteRequest request = new DataWriteRequest(
            AUDIT_DATASET,
            auditId,
            serializeAudit(entry),
            Map.of("timestamp", Instant.now().toString())
        );

        return dataCloud.writeData(request);
    }

    private byte[] serialize(ConsentGrant grant) {
        return TypedDataSerializer.toBytes(grant, "ConsentGrant", 1);
    }

    private ConsentGrant deserialize(byte[] data) {
        return TypedDataSerializer.fromBytes(data, ConsentGrant.class);
    }

    private byte[] serializeEmergency(EmergencyGrant grant) {
        return TypedDataSerializer.toBytes(grant, "EmergencyGrant", 1);
    }

    private byte[] serializeAudit(PatientAuditEntry entry) {
        return TypedDataSerializer.toBytes(entry, "PatientAuditEntry", 1);
    }

    private PatientAuditEntry deserializeAudit(byte[] data) {
        return TypedDataSerializer.fromBytes(data, PatientAuditEntry.class);
    }

    private String generateId() {
        return "cns-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    // ==================== Inner Types ====================

    public static class ConsentGrant {
        private final String id;
        private final String patientId;
        private final String recipientId; // Provider or caregiver
        private final ConsentScope scope;
        private final String status; // ACTIVE, REVOKED, EXPIRED
        private final Instant createdAt;
        private final Instant expiresAt;
        private final Instant revokedAt;

        public ConsentGrant(String id, String patientId, String recipientId,
                           ConsentScope scope, String status, Instant createdAt,
                           Instant expiresAt, Instant revokedAt) {
            this.id = id;
            this.patientId = patientId;
            this.recipientId = recipientId;
            this.scope = scope;
            this.status = status;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
            this.revokedAt = revokedAt;
        }

        public String getId() { return id; }
        public String getPatientId() { return patientId; }
        public String getRecipientId() { return recipientId; }
        public ConsentScope getScope() { return scope; }
        public String getStatus() { return status; }
        public Instant getCreatedAt() { return createdAt; }
        public Instant getExpiresAt() { return expiresAt; }
        public Instant getRevokedAt() { return revokedAt; }

        public boolean isExpired() {
            return expiresAt != null && Instant.now().isAfter(expiresAt);
        }

        public boolean covers(String accessorId, String resourceType) {
            return recipientId.equals(accessorId) && scope.includes(resourceType);
        }

        public boolean overlaps(ConsentScope other) {
            return scope.overlaps(other);
        }

        public ConsentGrant withId(String newId) {
            return new ConsentGrant(newId, patientId, recipientId, scope, status,
                createdAt, expiresAt, revokedAt);
        }

        public ConsentGrant withStatus(String newStatus) {
            return new ConsentGrant(id, patientId, recipientId, scope, newStatus,
                createdAt, expiresAt, revokedAt);
        }

        public ConsentGrant withCreatedAt(Instant newCreatedAt) {
            return new ConsentGrant(id, patientId, recipientId, scope, status,
                newCreatedAt, expiresAt, revokedAt);
        }

        public ConsentGrant withRevokedAt(Instant newRevokedAt) {
            return new ConsentGrant(id, patientId, recipientId, scope, status,
                createdAt, expiresAt, newRevokedAt);
        }
    }

    public static class ConsentScope {
        private final Set<String> resourceTypes; // e.g., "medications", "lab-results"
        private final boolean allDocuments;
        private final Set<String> specificDocumentIds;
        private final Set<String> actions; // READ, WRITE

        public ConsentScope(Set<String> resourceTypes, boolean allDocuments,
                           Set<String> specificDocumentIds, Set<String> actions) {
            this.resourceTypes = resourceTypes != null ? resourceTypes : Set.of();
            this.allDocuments = allDocuments;
            this.specificDocumentIds = specificDocumentIds != null ? specificDocumentIds : Set.of();
            this.actions = actions != null ? actions : Set.of("READ");
        }

        public boolean includes(String resourceType) {
            return resourceTypes.contains(resourceType) || resourceTypes.contains("*");
        }

        public boolean overlaps(ConsentScope other) {
            // Check if scopes overlap
            for (String resource : resourceTypes) {
                if (other.resourceTypes.contains(resource)) {
                    return true;
                }
            }
            return false;
        }
    }

    public static class ConsentValidationResult {
        private final boolean allowed;
        private final String reason;
        private final String grantId;

        public ConsentValidationResult(boolean allowed, String reason, String grantId) {
            this.allowed = allowed;
            this.reason = reason;
            this.grantId = grantId;
        }

        public boolean isAllowed() { return allowed; }
        public String getReason() { return reason; }
        public String getGrantId() { return grantId; }
    }

    public static class EmergencyAccessRequest {
        private final String patientId;
        private final String providerId;
        private final String justification;
        private final String category; // trauma, unconscious, minor-without-guardian

        public EmergencyAccessRequest(String patientId, String providerId,
                                       String justification, String category) {
            this.patientId = patientId;
            this.providerId = providerId;
            this.justification = justification;
            this.category = category;
        }

        public String getPatientId() { return patientId; }
        public String getProviderId() { return providerId; }
        public String getJustification() { return justification; }
        public String getCategory() { return category; }
    }

    public static class EmergencyGrant {
        private final String id;
        private final String patientId;
        private final String providerId;
        private final String justification;
        private final String category;
        private final Instant grantedAt;
        private final Instant expiresAt;
        private final Set<String> allowedResources;
        private final boolean justificationSubmitted;

        public EmergencyGrant(String id, String patientId, String providerId,
                             String justification, String category, Instant grantedAt,
                             Instant expiresAt, Set<String> allowedResources,
                             boolean justificationSubmitted) {
            this.id = id;
            this.patientId = patientId;
            this.providerId = providerId;
            this.justification = justification;
            this.category = category;
            this.grantedAt = grantedAt;
            this.expiresAt = expiresAt;
            this.allowedResources = allowedResources;
            this.justificationSubmitted = justificationSubmitted;
        }

        public String getId() { return id; }
        public String getPatientId() { return patientId; }
        public String getProviderId() { return providerId; }
        public String getJustification() { return justification; }
        public String getCategory() { return category; }
        public Instant getGrantedAt() { return grantedAt; }
        public Instant getExpiresAt() { return expiresAt; }
        public Set<String> getAllowedResources() { return allowedResources; }
        public boolean isJustificationSubmitted() { return justificationSubmitted; }
    }

    public static class PatientAuditEntry {
        private final String id;
        private final Instant timestamp;
        private final String action;
        private final String patientId;
        private final String description;
        private final Map<String, Object> metadata;

        public PatientAuditEntry(String id, Instant timestamp, String action,
                                  String patientId, String description,
                                  Map<String, Object> metadata) {
            this.id = id;
            this.timestamp = timestamp;
            this.action = action;
            this.patientId = patientId;
            this.description = description;
            this.metadata = metadata;
        }

        public String getId() { return id; }
        public Instant getTimestamp() { return timestamp; }
        public String getAction() { return action; }
        public String getPatientId() { return patientId; }
        public String getDescription() { return description; }
        public Map<String, Object> getMetadata() { return metadata; }
    }

    private static class ConsentCacheEntry {
        private final String grantId;
        private final boolean allowed;
        private final Instant expiresAt;

        ConsentCacheEntry(String grantId, boolean allowed, Instant expiresAt) {
            this.grantId = grantId;
            this.allowed = allowed;
            this.expiresAt = expiresAt;
        }

        String getGrantId() { return grantId; }
        boolean isAllowed() { return allowed; }
        boolean isExpired() { return Instant.now().isAfter(expiresAt); }
        boolean isNearExpiry() {
            return expiresAt.minusSeconds(300).isBefore(Instant.now());
        }
    }

    // ==================== Rate Limiting ====================

    private static boolean tryAcquire(RateLimiter limiter, String key) {
        return limiter.tryAcquire(key).allowed();
    }

    /**
     * Thrown when a consent operation exceeds the per-actor rate limit.
     */
    public static class RateLimitExceededException extends RuntimeException {
        public RateLimitExceededException(String message) {
            super(message);
        }
    }
}
