package com.ghatana.phr.kernel.service;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghatana.kernel.context.KernelContext;

import com.ghatana.platform.cache.DistributedCachePort;
import com.ghatana.platform.security.ratelimit.DefaultRateLimiter;
import com.ghatana.platform.security.ratelimit.RateLimiter;
import com.ghatana.platform.security.ratelimit.RateLimiterConfig;
import com.ghatana.phr.kernel.consent.ConsentService;
import io.activej.promise.Promise;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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
public class ConsentManagementService extends PhrServiceBase implements ConsentService {

    private static final String CONSENT_DATASET = "phr.consent.grants";
    private static final String EMERGENCY_DATASET = "phr.emergency.access";

    /** Maximum grant creation requests per actor per window. */
    private static final int CREATE_GRANT_MAX_PER_WINDOW = 20;
    /** Maximum access-check requests per actor per window. */
    private static final int CHECK_ACCESS_MAX_PER_WINDOW = 200;
    /** Sliding window duration for rate limiting. */
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(1);
    private static final Duration EMERGENCY_ACCESS_WINDOW = Duration.ofHours(4);

    /** Distributed cache for consent decisions — multi-node safe (ISSUE-X02). */
    private final DistributedCachePort<String, ConsentCacheEntry> consentCache;
    private final RateLimiter createGrantLimiter;
    private final RateLimiter checkAccessLimiter;
    private final PhrNotificationSender notificationSender;

    /**
     * Constructs a ConsentManagementService with a supplied distributed cache.
     *
     * @param context      kernel context providing DataCloudKernelAdapter
     * @param consentCache distributed cache for consent decisions (ISSUE-X02 fix)
     */
    public ConsentManagementService(KernelContext context,
                                     DistributedCachePort<String, ConsentCacheEntry> consentCache) {
        this(context, consentCache, PhrNotificationSenders.fromContext(context));
    }

    ConsentManagementService(
            KernelContext context,
            DistributedCachePort<String, ConsentCacheEntry> consentCache,
            PhrNotificationSender notificationSender) {
        super(context);
        this.consentCache = Objects.requireNonNull(consentCache, "consentCache must not be null");
        this.notificationSender = Objects.requireNonNull(notificationSender, "notificationSender must not be null");
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

    @Override
    public String getName() {
        return "consent-management";
    }

    @Override
    protected Promise<Void> initializeDatasets() {
        Promise<Void> consent = createSchema(
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
        );

        Promise<Void> emergency = createSchema(
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
        );

        return consent.then($ -> emergency);
    }

    @Override
    protected Promise<Void> onStop() {
        return consentCache.invalidateAll();
    }

    // ==================== Core Consent Operations ====================

    /**
     * Creates a new consent grant.
     *
     * @param grant the consent grant to create
     * @return Promise containing the created grant
     */
    public Promise<ConsentGrant> createGrant(ConsentGrant grant) {
        ensureRunning();

        String patientId = PhrInputSanitizationUtils.requireSafeIdentifier(grant.getPatientId(), "patientId");
        String recipientId = PhrInputSanitizationUtils.requireSafeIdentifier(grant.getRecipientId(), "recipientId");
        ConsentScope sanitizedScope = sanitizeScope(grant.getScope());
        Instant expiresAt = requireFutureExpiry(grant.getExpiresAt(), "expiresAt");
        String status = PhrInputSanitizationUtils.requireAllowedValue(
            grant.getStatus(),
            "status",
            Set.of("ACTIVE", "REVOKED", "EXPIRED")
        );

        String rateLimitKey = recipientId;
        if (!tryAcquire(createGrantLimiter, rateLimitKey)) {
            return Promise.ofException(new RateLimitExceededException(
                    "Grant creation rate limit exceeded for actor: " + rateLimitKey));
        }

        // Check for conflicting grants
        return checkConflictingGrants(patientId, recipientId, sanitizedScope)
            .then(conflict -> {
                if (conflict) {
                    return Promise.<ConsentGrant>ofException(
                        new IllegalStateException("Conflicting active grant exists"));
                }

                String grantId = grant.getId() != null ? grant.getId() : generateId("cns");
                ConsentGrant toStore = new ConsentGrant(
                    grantId,
                    patientId,
                    recipientId,
                    sanitizedScope,
                    status,
                    Instant.now(),
                    expiresAt,
                    grant.getRevokedAt(),
                    grant.getIdempotencyKey()
                );

                return createRecord(
                    CONSENT_DATASET,
                    grantId,
                    toStore,
                    mutationMetadata(Map.of(
                        "patientId", toStore.getPatientId(),
                        "recipientId", toStore.getRecipientId(),
                        "status", toStore.getStatus(),
                        "expiresAt", toStore.getExpiresAt().toString()
                    ), toStore.getRecipientId()),
                    "ConsentGrant",
                    1
                ).then(stored -> invalidateConsentCache(stored.getRecipientId(), stored.getPatientId())
                    .then($ -> notifyConsentChange(stored, PhrNotificationSender.ConsentChangeType.GRANT_CREATED))
                    .then($ -> audit("GRANT_CREATE", stored.getPatientId(),
                        "Grant created for " + stored.getRecipientId()))
                    .map($ -> stored));
            });
    }

    /**
     * Revokes an active consent grant.
     *
     * @param grantId the grant identifier
     * @return Promise completing when revoked
     */
    public Promise<Void> revokeGrant(String grantId) {
        ensureRunning();

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
                return updateGrantInternal(revoked)
                    .then($ -> invalidatePatientAccessCache(
                            new CacheInvalidationRequest(null, grant.getPatientId(),
                                    CacheInvalidationReason.GRANT_REVOKED)))
            .then($ -> notifyConsentChange(revoked, PhrNotificationSender.ConsentChangeType.GRANT_REVOKED))
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
        return validateAccess(patientId, accessorId, resourceType, "READ");
    }

    /**
     * Validates if access is permitted based on consent resource and action scope.
     *
     * @param patientId the patient being accessed
     * @param accessorId the accessor (provider/caregiver)
     * @param resourceType the resource type being accessed
     * @param action the action being performed
     * @return Promise containing validation result
     */
    public Promise<ConsentValidationResult> validateAccess(String patientId, String accessorId,
                                                            String resourceType, String action) {
        ensureRunning();

        // Check cache first
        String sanitizedPatientId = PhrInputSanitizationUtils.requireSafeIdentifier(patientId, "patientId");
        String sanitizedAccessorId = PhrInputSanitizationUtils.requireSafeIdentifier(accessorId, "accessorId");
        String sanitizedResourceType = PhrInputSanitizationUtils.requireSafeCode(resourceType, "resourceType");
        String sanitizedAction = PhrInputSanitizationUtils.requireSafeCode(action, "action").toUpperCase();
        String cacheKey = sanitizedAccessorId + ":" + sanitizedPatientId + ":" + sanitizedResourceType + ":" + sanitizedAction;
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
                return getActiveGrantsForPatient(sanitizedPatientId)
                    .then(grants -> {
                        for (ConsentGrant grant : grants) {
                            if (grant.covers(sanitizedAccessorId, sanitizedResourceType, sanitizedAction)) {
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
                    false, null, List.of()));
        }

        SanitizedAccessRequest sanitized;
        try {
            sanitized = sanitizeAccessRequest(request);
        } catch (IllegalArgumentException ex) {
            return Promise.of(new ConsentAccessDecision(
                    false, ReasonCode.SYSTEM_DENY, null, CacheStatus.BYPASS,
                    true, null, List.of("MALFORMED_REQUEST")));
        }

        String rateLimitKey = sanitized.actorId();
        if (!tryAcquire(checkAccessLimiter, rateLimitKey)) {
            return Promise.of(new ConsentAccessDecision(
                    false, ReasonCode.SYSTEM_DENY, null, CacheStatus.BYPASS,
                    true, null, List.of("RATE_LIMIT_EXCEEDED")));
        }

        // Self-access shortcut: patient accessing own data
        if (request.actor().actorType() == ActorType.PATIENT
                && sanitized.targetPatientId().equals(sanitized.actorPatientId())) {
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
        String cacheKey = sanitized.tenantId() + ":" + sanitized.actorId() + ":"
                + sanitized.targetPatientId() + ":" + sanitized.resourceType();
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
                    return getActiveGrantsForPatient(sanitized.targetPatientId())
                            .then(grants -> {
                                for (ConsentGrant grant : grants) {
                                    if (grant.covers(sanitized.actorId(),
                                            sanitized.resourceType())) {
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

    /** {@inheritDoc} */
    @Override
    public Promise<ConsentRevokeResult> revokeConsent(ConsentRevokeRequest request) {
        ensureRunning();

        // Find the consent grant by target resource identifier (consentId)
        return getGrant(request.target().resourceId())
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.of(new ConsentRevokeResult(false, request.target().resourceId()));
                }
                ConsentGrant grant = opt.get();
                if (!"ACTIVE".equals(grant.getStatus())) {
                    return Promise.of(new ConsentRevokeResult(false, grant.getId()));
                }

                ConsentGrant revoked = grant.withStatus("REVOKED").withRevokedAt(Instant.now());
                return updateGrantInternal(revoked)
                    .then($ -> invalidatePatientAccessCache(
                            new CacheInvalidationRequest(request.tenantId(), request.target().patientId(),
                                    CacheInvalidationReason.GRANT_REVOKED)))
                    .then($ -> notifyConsentChange(revoked, PhrNotificationSender.ConsentChangeType.GRANT_REVOKED))
                    .then($ -> audit("CONSENT_REVOKE", request.target().patientId(),
                        "Consent revoked by " + request.actor().actorId()))
                    .map($ -> new ConsentRevokeResult(true, grant.getId()));
            });
    }

    /**
     * Gets all grants for a patient.
     *
     * @param patientId the patient identifier
     * @return Promise containing list of grants
     */
    public Promise<Optional<ConsentGrant>> getGrantByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Promise.of(Optional.empty());
        }
        return queryRecords(
            CONSENT_DATASET,
            "idempotencyKey = :idempotencyKey AND status = :status",
            Map.of(
                "idempotencyKey", idempotencyKey,
                "status", "ACTIVE"
            ),
            10,
            0,
            ConsentGrant.class
        )
            .then(grants -> {
                if (grants.isEmpty()) {
                    return Promise.of(Optional.empty());
                }
                // Return the most recent grant with this idempotency key
                return Promise.of(Optional.of(grants.get(0)));
            });
    }

    public Promise<List<ConsentGrant>> getPatientGrants(String patientId) {
        ensureRunning();

        return queryRecords(
            CONSENT_DATASET,
            "patientId = :patientId",
            Map.of("patientId", patientId),
            1000,
            0,
            ConsentGrant.class
        );
    }

    /**
     * Gets active, unexpired grants issued to a recipient.
     *
     * @param recipientId the provider or caregiver identifier receiving delegated access
     * @return active grants for the recipient; never null
     */
    public Promise<List<ConsentGrant>> getActiveGrantsForRecipient(String recipientId) {
        ensureRunning();

        String sanitizedRecipientId = PhrInputSanitizationUtils.requireSafeIdentifier(recipientId, "recipientId");
        return queryRecords(
            CONSENT_DATASET,
            "recipientId = :recipientId AND status = :status",
            Map.of("recipientId", sanitizedRecipientId, "status", "ACTIVE"),
            1000,
            0,
            ConsentGrant.class
        ).map(grants -> grants.stream()
            .filter(grant -> !grant.isExpired())
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
        ensureRunning();

        // Validate emergency request
        String patientId = PhrInputSanitizationUtils.requireSafeIdentifier(request.getPatientId(), "patientId");
        String providerId = PhrInputSanitizationUtils.requireSafeIdentifier(request.getProviderId(), "providerId");
        String justification = PhrInputSanitizationUtils.sanitizeRequiredText(request.getJustification(), "justification", 2000);
        String category = PhrInputSanitizationUtils.requireSafeCode(request.getCategory(), "category");

        Instant grantedAt = Instant.now();
        Instant expiresAt = grantedAt.plus(EMERGENCY_ACCESS_WINDOW);

        EmergencyGrant grant = new EmergencyGrant(
            generateId("emg"),
            patientId,
            providerId,
            justification,
            category,
            grantedAt,
            expiresAt,
            Set.of("allergies", "medications", "bloodType", "emergencyContacts"), // Limited scope
            false
        );

        return createRecord(
            EMERGENCY_DATASET,
            grant.getId(),
            grant,
            mutationMetadata(Map.of(
                "patientId", patientId,
                "providerId", providerId,
                "status", "ACTIVE",
                "expiresAt", expiresAt.toString()
            ), providerId),
            "EmergencyGrant",
            1
        ).then(stored -> notifyPatientOfEmergencyAccess(stored)
            .then($ -> audit("EMERGENCY_ACCESS", patientId,
                "Emergency access granted to " + providerId))
            .map($ -> stored));
    }

    /**
     * Gets patient-visible audit trail.
     *
     * @param patientId the patient identifier
     * @return Promise containing audit entries
     */
    public Promise<List<PatientAuditEntry>> getPatientAuditTrail(String patientId) {
        ensureRunning();

        return queryRecords(
            AUDIT_DATASET,
            "patientId = :patientId",
            Map.of("patientId", patientId),
            1000,
            0,
            PatientAuditEntry.class
        );
    }

    private static final String AUDIT_DATASET = "phr.consent.audit";

    // ==================== Private Methods ====================

    private Promise<Optional<ConsentGrant>> getGrant(String grantId) {
        return readRecord(CONSENT_DATASET, grantId, ConsentGrant.class);
    }

    private Promise<ConsentGrant> updateGrantInternal(ConsentGrant grant) {
        return updateRecord(
            CONSENT_DATASET,
            grant.getId(),
            grant,
            mutationMetadata(Map.of(
                "patientId", grant.getPatientId(),
                "recipientId", grant.getRecipientId(),
                "status", grant.getStatus()
            ), grant.getRecipientId()),
            "ConsentGrant",
            1
        );
    }

    private Promise<Boolean> checkConflictingGrants(String patientId, String recipientId, ConsentScope scope) {
        return getActiveGrantsForPatient(patientId)
            .then(grants -> {
                for (ConsentGrant grant : grants) {
                    if (grant.getRecipientId().equals(recipientId) &&
                        grant.overlaps(scope)) {
                        return Promise.of(Boolean.TRUE);
                    }
                }
                return Promise.of(false);
            });
    }

    private Promise<List<ConsentGrant>> getActiveGrantsForPatient(String patientId) {
        return queryRecords(
            CONSENT_DATASET,
            "patientId = :patientId",
            Map.of("patientId", patientId),
            1000,
            0,
            ConsentGrant.class
        ).map(grants -> grants.stream()
            .filter(g -> "ACTIVE".equals(g.getStatus())
                && g.getExpiresAt() != null
                && !g.isExpired())
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
        return notificationSender.notifyConsentChange(new PhrNotificationSender.ConsentChangeNotification(
            grant.getPatientId(),
            grant.getProviderId(),
            grant.getId(),
            PhrNotificationSender.ConsentChangeType.EMERGENCY_ACCESS_GRANTED,
            PhrNotificationSender.DEFAULT_CHANNELS,
            PhrTraceContext.newCorrelationId("phr_emergency_access_granted"),
            "phr_emergency_access_granted"
        ));
    }

    private Promise<Void> notifyConsentChange(
            ConsentGrant grant,
            PhrNotificationSender.ConsentChangeType changeType) {
        return notificationSender.notifyConsentChange(new PhrNotificationSender.ConsentChangeNotification(
            grant.getPatientId(),
            grant.getRecipientId(),
            grant.getId(),
            changeType,
            PhrNotificationSender.DEFAULT_CHANNELS,
            PhrTraceContext.newCorrelationId("phr_consent_change"),
            "phr_consent_change"
        ));
    }

    private ConsentScope sanitizeScope(ConsentScope scope) {
        if (scope == null) {
            throw new IllegalArgumentException("scope is required");
        }
        Set<String> resourceTypes = scope.resourceTypes.stream()
            .map(resourceType -> PhrInputSanitizationUtils.requireSafeCode(resourceType, "scope.resourceTypes"))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<String> documentIds = scope.specificDocumentIds.stream()
            .map(documentId -> PhrInputSanitizationUtils.requireSafeIdentifier(documentId, "scope.documentIds"))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<String> actions = scope.actions.stream()
            .map(action -> PhrInputSanitizationUtils.requireSafeCode(action, "scope.actions"))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new ConsentScope(resourceTypes, scope.allDocuments, documentIds, actions, scope.fieldLevelAccess);
    }

    private static Instant requireFutureExpiry(Instant expiresAt, String fieldName) {
        if (expiresAt == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        if (!expiresAt.isAfter(Instant.now())) {
            throw new IllegalArgumentException(fieldName + " must be in the future");
        }
        return expiresAt;
    }

    private static SanitizedAccessRequest sanitizeAccessRequest(ConsentCheckRequest request) {
        String tenantId = PhrInputSanitizationUtils.requireSafeIdentifier(request.tenantId(), "tenantId");
        String actorId = PhrInputSanitizationUtils.requireSafeIdentifier(request.actor().actorId(), "actorId");
        String actorPatientId = request.actor().patientId() == null
            ? null
            : PhrInputSanitizationUtils.requireSafeIdentifier(request.actor().patientId(), "actor.patientId");
        String targetPatientId = PhrInputSanitizationUtils.requireSafeIdentifier(
            request.target().patientId(),
            "target.patientId"
        );
        String resourceType = PhrInputSanitizationUtils.requireSafeCode(
            request.target().resourceType(),
            "target.resourceType"
        );
        return new SanitizedAccessRequest(tenantId, actorId, actorPatientId, targetPatientId, resourceType);
    }

    private record SanitizedAccessRequest(
        String tenantId,
        String actorId,
        String actorPatientId,
        String targetPatientId,
        String resourceType
    ) {}

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
        private final String idempotencyKey;

        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public ConsentGrant(
            @JsonProperty("id") String id,
            @JsonProperty("patientId") String patientId,
            @JsonProperty("recipientId") String recipientId,
            @JsonProperty("scope") ConsentScope scope,
            @JsonProperty("status") String status,
            @JsonProperty("createdAt") Instant createdAt,
            @JsonProperty("expiresAt") Instant expiresAt,
            @JsonProperty("revokedAt") Instant revokedAt,
            @JsonProperty("idempotencyKey") String idempotencyKey) {
            this.id = id;
            this.patientId = patientId;
            this.recipientId = recipientId;
            this.scope = scope;
            this.status = status;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
            this.revokedAt = revokedAt;
            this.idempotencyKey = idempotencyKey;
        }

        public String getId() { return id; }
        public String getPatientId() { return patientId; }
        public String getRecipientId() { return recipientId; }
        public ConsentScope getScope() { return scope; }
        public String getStatus() { return status; }
        public Instant getCreatedAt() { return createdAt; }
        public Instant getExpiresAt() { return expiresAt; }
        public Instant getRevokedAt() { return revokedAt; }
        public String getGrantId() { return id; }
        public String getIdempotencyKey() { return idempotencyKey; }

        public boolean isExpired() {
            return expiresAt != null && Instant.now().isAfter(expiresAt);
        }

        public boolean covers(String accessorId, String resourceType) {
            return recipientId.equals(accessorId) && scope.includes(resourceType);
        }

        public boolean covers(String accessorId, String resourceType, String action) {
            return recipientId.equals(accessorId) && scope.allowsAction(resourceType, action);
        }

        public boolean overlaps(ConsentScope other) {
            return scope.overlaps(other);
        }

        public ConsentGrant withId(String newId) {
            return new ConsentGrant(newId, patientId, recipientId, scope, status,
                createdAt, expiresAt, revokedAt, idempotencyKey);
        }

        public ConsentGrant withStatus(String newStatus) {
            return new ConsentGrant(id, patientId, recipientId, scope, newStatus,
                createdAt, expiresAt, revokedAt, idempotencyKey);
        }

        public ConsentGrant withCreatedAt(Instant newCreatedAt) {
            return new ConsentGrant(id, patientId, recipientId, scope, status,
                newCreatedAt, expiresAt, revokedAt, idempotencyKey);
        }

        public ConsentGrant withRevokedAt(Instant newRevokedAt) {
            return new ConsentGrant(id, patientId, recipientId, scope, status,
                createdAt, expiresAt, newRevokedAt, idempotencyKey);
        }

        public ConsentGrant withIdempotencyKey(String newIdempotencyKey) {
            return new ConsentGrant(id, patientId, recipientId, scope, status,
                createdAt, expiresAt, revokedAt, newIdempotencyKey);
        }
    }

    public static class ConsentScope {
        private final Set<String> resourceTypes; // e.g., "medications", "lab-results"
        private final boolean allDocuments;
        private final Set<String> specificDocumentIds;
        private final Set<String> actions; // READ, WRITE
        private final Map<String, Set<String>> fieldLevelAccess; // resourceType -> allowed fields

        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public ConsentScope(
                @JsonProperty("resourceTypes") Set<String> resourceTypes,
                @JsonProperty("allDocuments") boolean allDocuments,
                @JsonProperty("specificDocumentIds") Set<String> specificDocumentIds,
                @JsonProperty("actions") Set<String> actions,
                @JsonProperty("fieldLevelAccess") Map<String, Set<String>> fieldLevelAccess) {
            this.resourceTypes = resourceTypes != null ? resourceTypes : Set.of();
            this.allDocuments = allDocuments;
            this.specificDocumentIds = specificDocumentIds != null ? specificDocumentIds : Set.of();
            this.actions = actions != null ? actions : Set.of("READ");
            this.fieldLevelAccess = fieldLevelAccess != null ? fieldLevelAccess : Map.of();
        }

        public Set<String> getResourceTypes() { return resourceTypes; }

        public boolean isAllDocuments() { return allDocuments; }

        public Set<String> getSpecificDocumentIds() { return specificDocumentIds; }

        public Set<String> getActions() { return actions; }

        public Map<String, Set<String>> getFieldLevelAccess() { return fieldLevelAccess; }

        public boolean includes(String resourceType) {
            return resourceTypes.contains(resourceType) || resourceTypes.contains("*");
        }

        /**
         * Checks if a specific field within a resource type is allowed access.
         * If no field-level restrictions are defined for the resource type,
         * the resource-level grant allows the requested field.
         *
         * @param resourceType the resource type
         * @param field the field name
         * @return true if the field is accessible
         */
        public boolean allowsField(String resourceType, String field) {
            if (field == null || field.isBlank()) {
                return true; // No field specified, allow access
            }

            Set<String> allowedFields = fieldLevelAccess.get(resourceType);
            if (allowedFields == null || allowedFields.isEmpty()) {
                // No field-level restrictions for this resource type
                return true;
            }

            // If field-level restrictions exist, check if field is allowed
            return allowedFields.contains(field) || allowedFields.contains("*");
        }

        /**
         * Checks if the scope allows a specific action on a resource type.
         *
         * @param resourceType the resource type
         * @param action the action (READ, WRITE)
         * @return true if the action is allowed
         */
        public boolean allowsAction(String resourceType, String action) {
            if (!includes(resourceType)) {
                return false;
            }
            return actions.contains(action) || actions.contains("*");
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

    public static class ConsentCacheEntry {
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
        boolean isExpired() { return expiresAt == null || Instant.now().isAfter(expiresAt); }
        boolean isNearExpiry() {
            if (expiresAt == null) {
                return true;
            }
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
    public static class RateLimitExceededException extends com.ghatana.platform.core.exception.RateLimitExceededException {
        public RateLimitExceededException(String message) {
            super(message);
        }
    }
}
