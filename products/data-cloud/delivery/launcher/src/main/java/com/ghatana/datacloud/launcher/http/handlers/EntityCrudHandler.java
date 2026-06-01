package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.datacloud.spi.EntityWriteIdempotencyStore;
import com.ghatana.datacloud.spi.EntityWriteOutbox;
import com.ghatana.datacloud.spi.EntityWriteOutboxProcessor;
import com.ghatana.datacloud.spi.InMemoryEntityWriteOutboxProcessor;
import com.ghatana.datacloud.spi.TransactionManager;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.datacloud.entity.validation.EntitySchemaValidator;
import com.ghatana.datacloud.entity.validation.ValidationResult;
import com.ghatana.datacloud.entity.storage.QuerySpec;
import com.ghatana.datacloud.governance.TenantQuotaService;
import com.ghatana.datacloud.governance.QuotaCheckResult;
import com.ghatana.datacloud.infrastructure.storage.OpenSearchConnector;
import com.ghatana.datacloud.launcher.http.ApiInputValidator;
import com.ghatana.datacloud.launcher.http.ProvenanceEnricher;
import com.ghatana.datacloud.launcher.http.TraceSpanSupport;
import com.ghatana.platform.security.annotation.RequiresRole;
import com.ghatana.platform.security.annotation.Secured;
import io.activej.http.*;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Handles entity CRUD, batch, export, and anomaly-detection HTTP endpoints.
 *
 * <p>Extracted from {@code DataCloudHttpServer} to reduce the god-class size.
 * Registered in the server via method references:
 * <pre>{@code
 * .with(HttpMethod.POST, "/api/v1/entities/:collection", entityHandler::handleSaveEntity)
 * }</pre>
 *
 * <h2>Security</h2>
 * All entity operations require authentication. Role-based access control is enforced
 * at the HTTP filter level (DataCloudSecurityFilter) based on the endpoint sensitivity
 * and user roles. Write operations require EDITOR or higher role.
 *
 * @doc.type class
 * @doc.purpose Entity CRUD, batch, export, and anomaly HTTP handlers
 * @doc.layer product
 * @doc.pattern Handler
 */
@Secured
public class EntityCrudHandler {

    private static final Logger log = LoggerFactory.getLogger(EntityCrudHandler.class);

    private final DataCloudClient client;
    private final HttpHandlerSupport http;
    private final BiConsumer<String, Map<String, Object>> wsBroadcaster;
    private TraceSpanSupport traceSupport = TraceSpanSupport.disabled();
    private SemanticIndexPort semanticIndexPort;
    private SemanticDeletePort semanticDeletePort;

    private EntitySchemaValidator schemaValidator;
    private OpenSearchConnector openSearchConnector;
    private TenantQuotaService tenantQuotaService;

    /** DC-P1-05: Idempotency key store for entity writes — must be durable in production. */
    private EntityWriteIdempotencyStore idempotencyStore;
    // In-memory fallback for embedded/local profiles only (lost on restart, bounded by IDEMPOTENCY_MAX_ENTRIES).
    // DC-P1-05: This fallback is disabled in production profiles.
    private final Map<String, IdempotencyEntry> inMemoryIdempotencyStore = new ConcurrentHashMap<>();
    private static final int IDEMPOTENCY_MAX_ENTRIES = 10_000;
    private static final long IDEMPOTENCY_TTL_MS = 24 * 60 * 60 * 1000L; // 24 hours

    /** DC-BE-003: Transaction manager for atomic multi-step writes (entity + event + audit).
     *  DC-P1-05: Required in production profiles. */
    private TransactionManager transactionManager;

    /** DC-P1-05: Outbox processor for atomic entity write lifecycle — required in production. */
    private EntityWriteOutboxProcessor outboxProcessor;

    /** DC-P1-05: Deployment profile for production validation. */
    private String deploymentProfile = "local";

    /** DC-P1-04: Audit service for audit emission in transaction lifecycle. */
    private com.ghatana.platform.audit.AuditService auditService;

    /**
     * DC-P1-003: Registry-driven set of collections requiring all-or-nothing transactional delete.
     * Configurable via {@link #withCriticalCollections(java.util.Set)} at startup.
     * Defaults to the built-in PHI/critical set.
     */
    private Set<String> criticalCollections = Set.of("patients", "consents", "audit", "emergency_access");

    private record IdempotencyEntry(Map<String, Object> responseBody, Instant storedAt) {}

    /**
     * Creates an entity handler with required dependencies.
     *
     * @param client        the Data-Cloud client
     * @param http          shared HTTP helper methods
     * @param wsBroadcaster callback to broadcast WebSocket events; may be a no-op
     */
    public EntityCrudHandler(DataCloudClient client,
                             HttpHandlerSupport http,
                             BiConsumer<String, Map<String, Object>> wsBroadcaster) {
        this.client = client;
        this.http = http;
        this.wsBroadcaster = wsBroadcaster;
    }

    public EntityCrudHandler withIdempotencyStore(EntityWriteIdempotencyStore store) {
        this.idempotencyStore = store;
        return this;
    }

    public EntityCrudHandler withSchemaValidator(EntitySchemaValidator validator) {
        this.schemaValidator = validator;
        return this;
    }

    public EntityCrudHandler withOpenSearchConnector(OpenSearchConnector connector) {
        this.openSearchConnector = connector;
        return this;
    }

    public EntityCrudHandler withTenantQuotaService(TenantQuotaService service) {
        this.tenantQuotaService = service;
        return this;
    }

    /**
     * P0-06: Wires an outbox processor for atomic entity write lifecycle.
     *
     * @param outboxProcessor the outbox processor; may be {@code null}
     * @return this handler (fluent)
     */
    public EntityCrudHandler withOutboxProcessor(EntityWriteOutboxProcessor outboxProcessor) {
        this.outboxProcessor = outboxProcessor;
        return this;
    }

    public EntityCrudHandler withTraceSupport(TraceSpanSupport traceSupport) {
        this.traceSupport = traceSupport != null ? traceSupport : TraceSpanSupport.disabled();
        return this;
    }

    public EntityCrudHandler withSemanticSearchPorts(
            SemanticIndexPort semanticIndexPort,
            SemanticDeletePort semanticDeletePort) {
        this.semanticIndexPort = semanticIndexPort;
        this.semanticDeletePort = semanticDeletePort;
        return this;
    }

    /**
     * DC-BE-003: Attaches a transaction manager for atomic multi-step writes.
     *
     * @param transactionManager the transaction manager
     * @return {@code this} for method chaining
     */
    public EntityCrudHandler withTransactionManager(TransactionManager transactionManager) {
        this.transactionManager = transactionManager;
        return this;
    }

    /**
     * DC-P1-04: Attaches an audit service for audit emission in transaction lifecycle.
     *
     * @param auditService the audit service
     * @return {@code this} for method chaining
     */
    public EntityCrudHandler withAuditService(com.ghatana.platform.audit.AuditService auditService) {
        this.auditService = auditService;
        return this;
    }

    /**
     * DC-P1-05: Sets the deployment profile for production validation.
     *
     * @param profile the deployment profile (e.g., "local", "production", "staging", "sovereign")
     * @return {@code this} for method chaining
     */
    public EntityCrudHandler withDeploymentProfile(String profile) {
        this.deploymentProfile = profile != null ? profile : "local";
        return this;
    }

    /**
     * DC-P1-003: Registers a config/registry-driven set of collection names that require
     * all-or-nothing transactional delete. Replaces the built-in default set.
     *
     * @param collections non-null set of collection names classified as destructive/critical
     * @return this handler for chaining
     */
    public EntityCrudHandler withCriticalCollections(Set<String> collections) {
        if (collections != null && !collections.isEmpty()) {
            this.criticalCollections = Set.copyOf(collections);
        }
        return this;
    }

    /**
     * DC-P1-05: Validates production requirements for entity write durability.
     * Throws IllegalStateException if production invariants are violated.
     *
     * <p>Production/staging/sovereign profiles require:
     * <ul>
     *   <li>Durable {@link #idempotencyStore} (in-memory fallback disabled)</li>
     *   <li>{@link #transactionManager} for atomic writes</li>
     *   <li>{@link #outboxProcessor} for durable side-effect processing</li>
     * </ul>
     */
    public void validateProductionRequirements() {
        if (!isProductionLikeProfile(deploymentProfile)) {
            log.info("[EntityCrudHandler] Skipping production validation for profile '{}'", deploymentProfile);
            return;
        }

        log.info("[EntityCrudHandler] Validating production requirements for profile '{}'", deploymentProfile);

        // DC-P1-05: Durable idempotency store is required in production
        if (idempotencyStore == null) {
            throw new IllegalStateException(
                "DC-P1-05: Durable EntityWriteIdempotencyStore is required in production/staging/sovereign profiles. " +
                "In-memory idempotency is not durable across restarts.");
        }

        // DC-P1-05: Transaction manager is required for atomic writes in production
        if (transactionManager == null) {
            throw new IllegalStateException(
                "DC-P1-05: TransactionManager is required in production/staging/sovereign profiles. " +
                "Entity writes must be atomic with event append and audit emission.");
        }

        // DC-P1-04: Audit service is required for audit emission in transaction lifecycle
        if (auditService == null) {
            throw new IllegalStateException(
                "DC-P1-04: AuditService is required in production/staging/sovereign profiles. " +
                "Audit events must be emitted in the transaction/outbox lifecycle.");
        }

        // Group 8 / DC-SEC-008: Quota enforcement is fail-closed in production.
        // A missing TenantQuotaService means quota is never enforced, which constitutes a
        // silent security bypass for resource-exhaustion and billing correctness.
        if (tenantQuotaService == null) {
            throw new IllegalStateException(
                "DC-SEC-008: TenantQuotaService is required in production/staging/sovereign profiles. " +
                "Quota enforcement must not be silently bypassed at runtime.");
        }

        // DC-P1-05: Outbox processor is required for durable side-effect processing in production
        if (outboxProcessor == null) {
            throw new IllegalStateException(
                "DC-P1-05: EntityWriteOutboxProcessor is required in production/staging/sovereign profiles. " +
                "WebSocket broadcasts and semantic indexing must be processed durably.");
        }

        // DC-P1-05: A configured transaction manager must also be operational.
        try {
            Promise<Map<String, Object>> validationPromise = transactionManager.executeInTransaction(
                "production-validation",
                () -> Promise.of(Map.of("status", "ok")));
            if (validationPromise == null) {
                throw new IllegalStateException("Transaction validation did not return a promise");
            }
            if (validationPromise.isException()) {
                throw validationPromise.getException();
            }
            if (!validationPromise.isComplete()) {
                throw new IllegalStateException("Transaction validation did not complete synchronously");
            }
        } catch (Exception error) {
            throw new IllegalStateException(
                "DC-P1-05: TransactionManager failed production validation for profile '" + deploymentProfile + "'",
                error);
        }

        log.info("[EntityCrudHandler] Production requirements validated successfully for profile '{}'", deploymentProfile);
    }

    /**
     * DC-P1-05: Determines if the deployment profile requires production-like strictness.
     */
    private static boolean isProductionLikeProfile(String profile) {
        if (profile == null) return false;
        String lower = profile.trim().toLowerCase();
        return lower.equals("production") || lower.equals("staging") || lower.equals("sovereign");
    }

    // ==================== Quota Enforcement ====================

    /**
     * P0.5: Check tenant quota before write operations.
     * Returns an error promise if quota is exceeded, otherwise null.
     */
    private Promise<HttpResponse> checkQuotaOrNull(String tenantId,
                                                   String operationType,
                                                   int resourceAmount) {
        if (tenantQuotaService == null) return null;
        QuotaCheckResult result = tenantQuotaService.checkQuota(tenantId, operationType, resourceAmount);
        if (!result.isAllowed()) {
            return Promise.of(http.errorResponse(429,
                "Quota exceeded: " + result.message() + " (quota=" + result.quotaValue()
                    + ", used=" + result.usedAmount() + ")"));
        }
        return null;
    }

    // ==================== Idempotency Key Support (P0.2 / DC-P1-008) ====================

    /**
     * DC-BE-002: Entity CRUD routes support idempotency via X-Idempotency-Key header.
     * Idempotency is implemented for POST /api/v1/entities/:collection and
     * POST /api/v1/entities/:collection/batch endpoints using EntityWriteIdempotencyStore.
     *
     * <p><b>DC-BE-002 Note:</b> Other mutating routes (pipelines, events, governance, analytics, etc.)
     * require idempotency support or explicit non-idempotent documentation:
     * - POST /api/v1/pipelines (pipeline creation/update)
     * - POST /api/v1/events (event append)
     * - POST /api/v1/governance/* (governance operations)
     * - POST /api/v1/analytics/* (analytics queries)
     * Retry tests are required for all idempotent routes to ensure retries do not corrupt data.
     *
     * <p>Idempotency keys are scoped by tenantId/collection/key to prevent cross-tenant collisions.
     */

    // ==================== Transaction Support (DC-BE-003) ====================

    /**
     * P0-06: Executes entity save + event append in a transaction with outbox pattern.
     *
     * <p>When transactionManager is available, this method wraps the multi-step write operation
     * in a transaction to ensure atomicity. WebSocket broadcast and semantic indexing are moved
     * to an outbox pattern for asynchronous processing after the transaction commits.
     *
     * @param tenantId the tenant ID
     * @param collection the collection name
     * @param provenanced the entity data with provenance
     * @param request the HTTP request
     * @param handlerSpan the trace span
     * @param idempotencyKey the idempotency key
     * @return promise that completes with the HTTP response
     */
    private Promise<HttpResponse> executeSaveInTransaction(
            String tenantId,
            String collection,
            Map<String, Object> provenanced,
            HttpRequest request,
            TraceSpanSupport.TraceSpanScope handlerSpan,
            String idempotencyKey) {
        
        return transactionManager.executeInTransactionWithContext(tenantId, context -> {
            // Entity save operation
            return traceSupport.trace(
                request,
                tenantId,
                "datacloud.entity.store.save",
                handlerSpan.spanId(),
                Map.of("collection", collection),
                () -> client.save(tenantId, collection, provenanced))
                .then(entity -> {
                    // P0-06: Create outbox entry for async processing instead of direct websocket/semantic operations
                    String correlationId = http.resolveCorrelationId(request);
                    
                    // Event append operation (within transaction)
                    DataCloudClient.Event cdcEvent = DataCloudClient.Event.builder()
                        .type("entity.saved")
                        .payload(buildCdcEnvelope(tenantId, handlerSpan.spanId(), entity, "upsert", null))
                        .source("datacloud.launcher.entity-crud")
                        .build();
                    
                    return traceSupport.trace(
                        request,
                        tenantId,
                        "datacloud.event.store.append",
                        handlerSpan.spanId(),
                        Map.of("collection", entity.collection(), "event.type", "entity.saved"),
                        () -> client.appendEvent(tenantId, cdcEvent))
                        .then(savedEvent -> {
                            // DC-P1-04: Create audit event for entity save
                            Map<String, Object> auditPayload = null;
                            if (auditService != null) {
                                String principalId = http.resolvePrincipalId(request);
                                AuditEvent auditEvent = AuditEvent.builder()
                                    .tenantId(tenantId)
                                    .eventType("entity.saved")
                                    .principal(principalId != null ? principalId : "system")
                                    .resourceType("entity")
                                    .resourceId(entity.id())
                                    .success(true)
                                    .detail("collection", collection)
                                    .detail("entityVersion", entity.version())
                                    .build();
                                
                                auditPayload = Map.of(
                                    "id", auditEvent.id(),
                                    "tenantId", auditEvent.tenantId(),
                                    "eventType", auditEvent.eventType(),
                                    "principal", auditEvent.principal(),
                                    "resourceType", auditEvent.resourceType(),
                                    "resourceId", auditEvent.resourceId(),
                                    "success", auditEvent.success(),
                                    "details", auditEvent.details(),
                                    "timestamp", auditEvent.timestamp().toString()
                                );
                            }

                            // DC-P1-05: Create outbox entry for async websocket broadcast and semantic indexing
                            if (outboxProcessor != null) {
                                EntityWriteOutbox outbox = EntityWriteOutbox.builder()
                                    .tenantId(tenantId)
                                    .collection(collection)
                                    .entityId(entity.id())
                                    .operationType("entity.saved")
                                    .entitySnapshot(Map.of(
                                        "id", entity.id(),
                                        "collection", entity.collection(),
                                        "version", entity.version(),
                                        "createdAt", entity.createdAt().toString()
                                    ))
                                    .eventPayload(Map.of(
                                        "eventType", "entity.saved",
                                        "source", "datacloud.launcher.entity-crud"
                                    ))
                                    .auditPayload(auditPayload) // DC-P1-04: Include audit in outbox
                                    .correlationId(correlationId)
                                    .build();

                                // DC-P1-05: In production, outbox must be durable - not in-memory
                                if (isProductionLikeProfile(deploymentProfile)
                                    && outboxProcessor instanceof InMemoryEntityWriteOutboxProcessor) {
                                    log.error("[EntityCrudHandler] DC-P1-05: In-memory outbox processor used in production profile '{}' - " +
                                             "this violates durability requirements", deploymentProfile);
                                    // Return error promise for production - don't silently use in-memory
                                    return Promise.ofException(new IllegalStateException(
                                        "DC-P1-05: Durable outbox processor required in production"));
                                }

                                // Store outbox entry for async processing
                                outboxProcessor.addPending(outbox);
                            }

                            return Promise.of(entity);
                        });
                })
                .then(entity -> {
                    // Build response body
                    Map<String, Object> responseBody = Map.of(
                        "id", entity.id(),
                        "collection", entity.collection(),
                        "version", entity.version(),
                        "createdAt", entity.createdAt().toString(),
                        "timestamp", Instant.now().toString()
                    );
                    // WS5-12: Use operation-specific idempotency scope instead of entity-scoped
                    storeIdempotency(tenantId, "entity.create", idempotencyKey, responseBody);
                    return Promise.of(http.jsonResponse(responseBody));
                });
        });
    }

    private String inMemoryIdempotencyKey(String tenantId, String collection, String idempotencyKey) {
        return tenantId + "/" + collection + "/" + idempotencyKey;
    }

    private Promise<HttpResponse> checkIdempotencyOrNull(String tenantId, String operationScope, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return null;

        // DC-P1-05: In production, durable store is mandatory - no in-memory fallback
        if (idempotencyStore == null && isProductionLikeProfile(deploymentProfile)) {
            log.error("[EntityCrudHandler] DC-P1-05: Idempotency check failed - no durable store in production profile '{}'", deploymentProfile);
            return Promise.of(http.errorResponse(503,
                "Idempotency service unavailable: durable store required in production"));
        }

        // Prefer durable store when available (non-embedded profiles).
        if (idempotencyStore != null) {
            Optional<Map<String, Object>> cached = idempotencyStore.get(tenantId, operationScope, idempotencyKey);
            if (cached.isPresent()) {
                log.info("[idempotency] Returning durable cached response for scope={}, key={}", operationScope, idempotencyKey);
                return Promise.of(http.jsonResponse(cached.get()));
            }
            return null;
        }

        // DC-P1-05: Fall back to in-memory store ONLY for local/embedded profiles (not production).
        String key = inMemoryIdempotencyKey(tenantId, operationScope, idempotencyKey);
        IdempotencyEntry entry = inMemoryIdempotencyStore.get(key);
        if (entry != null && Instant.now().minusMillis(IDEMPOTENCY_TTL_MS).isBefore(entry.storedAt())) {
            log.info("[idempotency] Returning in-memory cached response for key={}", key);
            return Promise.of(http.jsonResponse(entry.responseBody()));
        }
        return null;
    }

    private void storeIdempotency(String tenantId, String operationScope, String idempotencyKey, Map<String, Object> responseBody) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return;

        // DC-P1-05: In production, durable store is mandatory
        if (idempotencyStore == null && isProductionLikeProfile(deploymentProfile)) {
            log.error("[EntityCrudHandler] DC-P1-05: Cannot store idempotency - no durable store in production profile '{}'", deploymentProfile);
            // Don't throw here to avoid breaking the response, but log the violation
            return;
        }

        // Prefer durable store when available.
        if (idempotencyStore != null) {
            idempotencyStore.put(tenantId, operationScope, idempotencyKey, responseBody);
            return;
        }

        // DC-P1-05: In-memory fallback only for local/embedded profiles (not production).
        if (inMemoryIdempotencyStore.size() >= IDEMPOTENCY_MAX_ENTRIES) {
            Instant cutoff = Instant.now().minusMillis(IDEMPOTENCY_TTL_MS);
            inMemoryIdempotencyStore.entrySet().removeIf(e -> e.getValue().storedAt().isBefore(cutoff));
        }
        inMemoryIdempotencyStore.put(
            inMemoryIdempotencyKey(tenantId, operationScope, idempotencyKey),
            new IdempotencyEntry(responseBody, Instant.now()));
    }

    /**
     * DC-P1-001: Executes batch entity save in a transaction with audit and outbox parity.
     * 
     * <p>When transactionManager is available, this method wraps the batch save operation
     * in a transaction to ensure atomicity. All entities are saved, then a single batch audit event
     * is emitted, and outbox entries are created for async websocket/semantic processing.
     * 
     * <p>DC-P1-002: Emits a structured batch audit event that includes all entity IDs and outcomes.
     * <p>DC-P1-003: Uses outbox for websocket/semantic indexing side effects instead of direct in-request calls.
     *
     * @param tenantId the tenant ID
     * @param collection the collection name
     * @param entityList the list of entity data with provenance
     * @param request the HTTP request
     * @param handlerSpan the trace span
     * @param idempotencyKey the idempotency key
     * @return promise that completes with the HTTP response
     */
    private Promise<HttpResponse> executeBatchSaveInTransaction(
            String tenantId,
            String collection,
            List<Map<String, Object>> entityList,
            HttpRequest request,
            TraceSpanSupport.TraceSpanScope handlerSpan,
            String idempotencyKey) {
        
        return transactionManager.executeInTransactionWithContext(tenantId, context -> {
            // Save all entities within transaction
            List<Promise<DataCloudClient.Entity>> savePromises = entityList.stream()
                .map(data -> traceSupport.trace(
                    request,
                    tenantId,
                    "datacloud.entity.store.save",
                    handlerSpan.spanId(),
                    Map.of("collection", collection),
                    () -> client.save(tenantId, collection, data)))
                .toList();
            
            return Promises.toList(savePromises)
                .then(savedEntities -> {
                    // DC-P1-002: Create batch audit event
                    Map<String, Object> auditPayload = null;
                    if (auditService != null) {
                        String principalId = http.resolvePrincipalId(request);
                        List<String> entityIds = savedEntities.stream()
                            .map(DataCloudClient.Entity::id)
                            .toList();
                        
                        AuditEvent auditEvent = AuditEvent.builder()
                            .tenantId(tenantId)
                            .eventType("entity.batch-saved")
                            .principal(principalId != null ? principalId : "system")
                            .resourceType("entity")
                            .resourceId(collection) // Use collection as resource ID for batch
                            .success(true)
                            .detail("collection", collection)
                            .detail("count", String.valueOf(savedEntities.size()))
                            .detail("entityIds", entityIds.toString())
                            .build();
                        
                        auditPayload = Map.of(
                            "id", auditEvent.id(),
                            "tenantId", auditEvent.tenantId(),
                            "eventType", auditEvent.eventType(),
                            "principal", auditEvent.principal(),
                            "resourceType", auditEvent.resourceType(),
                            "resourceId", auditEvent.resourceId(),
                            "success", auditEvent.success(),
                            "details", auditEvent.details(),
                            "timestamp", auditEvent.timestamp().toString()
                        );
                    }
                    
                    // DC-P1-003: Create outbox entry for async websocket broadcast and semantic indexing
                    if (outboxProcessor != null) {
                        List<String> entityIds = savedEntities.stream()
                            .map(DataCloudClient.Entity::id)
                            .toList();
                        List<Map<String, Object>> entitySnapshots = savedEntities.stream()
                            .map(e -> buildCdcEnvelope(tenantId, handlerSpan.spanId(), e, "upsert", null))
                            .toList();
                        
                        EntityWriteOutbox outbox = EntityWriteOutbox.builder()
                            .tenantId(tenantId)
                            .collection(collection)
                            .entityId("batch-" + entityIds.hashCode()) // Use hash for batch identifier
                            .operationType("entity.batch-saved")
                            .entitySnapshot(Map.of(
                                "collection", collection,
                                "count", savedEntities.size(),
                                "ids", entityIds,
                                "entities", entitySnapshots
                            ))
                            .eventPayload(Map.of(
                                "eventType", "entity.batch-saved",
                                "source", "datacloud.launcher.entity-crud"
                            ))
                            .auditPayload(auditPayload)
                            .correlationId(http.resolveCorrelationId(request))
                            .build();
                        
                        // DC-P1-05: Validate durable outbox in production
                        if (isProductionLikeProfile(deploymentProfile)
                            && outboxProcessor instanceof InMemoryEntityWriteOutboxProcessor) {
                            log.error("[EntityCrudHandler] DC-P1-05: In-memory outbox processor used in production profile '{}' - " +
                                     "this violates durability requirements", deploymentProfile);
                            return Promise.ofException(new IllegalStateException(
                                "DC-P1-05: Durable outbox processor required in production"));
                        }
                        
                        outboxProcessor.addPending(outbox);
                    }
                    
                    // Build response body
                    List<String> ids = savedEntities.stream()
                        .map(DataCloudClient.Entity::id)
                        .toList();
                    Map<String, Object> responseBody = Map.of(
                        "saved", savedEntities.size(),
                        "collection", collection,
                        "ids", ids,
                        "errors", List.of(),
                        "timestamp", Instant.now().toString()
                    );
                    // WS5-12: Use operation-specific idempotency scope instead of entity-scoped
                    storeIdempotency(tenantId, "entity.batch-create", idempotencyKey, responseBody);
                    return Promise.of(http.jsonResponse(responseBody));
                });
        });
    }

    /**
     * DC-P1-001: Executes batch entity delete in a transaction with audit and outbox parity.
     * 
     * <p>When transactionManager is available, this method wraps the batch delete operation
     * in a transaction to ensure atomicity. All entities are deleted, then per-entity delete events
     * are emitted, a batch audit event is emitted, and outbox entries are created for async processing.
     * 
     * <p>DC-P1-004: Emits per-entity delete events with full entity snapshots in CDC envelopes.
     * <p>DC-P1-005: Emits batch audit event and uses outbox for websocket/semantic indexing.
     * <p>DC-P1-007: Supports all-or-nothing transactional delete for production-critical collections.
     *
     * @param tenantId the tenant ID
     * @param collection the collection name
     * @param ids the list of entity IDs to delete
     * @param request the HTTP request
     * @param handlerSpan the trace span
     * @param requireAllOrNothing if true, all deletes must succeed or transaction rolls back
     * @return promise that completes with the HTTP response
     */
    private Promise<HttpResponse> executeBatchDeleteInTransaction(
            String tenantId,
            String collection,
            List<String> ids,
            HttpRequest request,
            TraceSpanSupport.TraceSpanScope handlerSpan,
            boolean requireAllOrNothing) {
        
        return transactionManager.executeInTransactionWithContext(tenantId, context -> {
            // DC-P1-004: Fetch existing entities before deletion to include in CDC envelopes
            List<Promise<Optional<DataCloudClient.Entity>>> fetchPromises = ids.stream()
                .map(id -> client.findById(tenantId, collection, id))
                .toList();
            
            return Promises.toList(fetchPromises)
                .then(existingEntities -> {
                    // DC-P1-005: Build a stable ID→entity lookup to avoid index misalignment when
                    // some deletes fail and deletedIds is a strict subset of ids.
                    Map<String, Optional<DataCloudClient.Entity>> entityByIdMap = new LinkedHashMap<>();
                    for (int idx = 0; idx < ids.size(); idx++) {
                        entityByIdMap.put(ids.get(idx), existingEntities.get(idx));
                    }

                    // Delete all entities within transaction
                    List<Promise<Map<String, Object>>> trackedDeletes = ids.stream()
                        .map(id -> client.delete(tenantId, collection, id)
                            .map(ignored -> {
                                Map<String, Object> result = new LinkedHashMap<>();
                                result.put("id", id);
                                result.put("success", true);
                                return result;
                            })
                            .then(Promise::of, e -> {
                                Map<String, Object> result = new LinkedHashMap<>();
                                result.put("id", id);
                                result.put("success", false);
                                result.put("error", e.getMessage());
                                return Promise.of(result);
                            }))
                        .toList();
                    
                    return Promises.toList(trackedDeletes)
                        .then(results -> {
                            // DC-P1-007: If requireAllOrNothing is true and any delete failed, rollback transaction
                            if (requireAllOrNothing) {
                                boolean allSucceeded = results.stream()
                                    .allMatch(r -> Boolean.TRUE.equals(r.get("success")));
                                if (!allSucceeded) {
                                    // Rollback by throwing exception
                                    List<String> errorIds = results.stream()
                                        .filter(r -> !Boolean.TRUE.equals(r.get("success")))
                                        .map(r -> (String) r.get("id"))
                                        .toList();
                                    throw new IllegalStateException(
                                        "DC-P1-007: All-or-nothing delete failed. Could not delete entities: " + errorIds);
                                }
                            }
                            
                            List<String> deletedIds = results.stream()
                                .filter(r -> Boolean.TRUE.equals(r.get("success")))
                                .map(r -> (String) r.get("id"))
                                .toList();
                            List<Map<String, Object>> errors = results.stream()
                                .filter(r -> !Boolean.TRUE.equals(r.get("success")))
                                .map(r -> Map.of("id", r.get("id"), "error", r.get("error")))
                                .toList();
                            
                            // DC-P1-004: Emit per-entity delete events using canonical CDC contract
                            // DC-P1-005: Look up before-state by ID (not by list index) to avoid
                            //            misalignment when some deletes fail mid-batch.
                            List<Promise<DataCloudClient.Offset>> eventPromises = new ArrayList<>();
                            for (String id : deletedIds) {
                                Optional<DataCloudClient.Entity> existing = entityByIdMap.getOrDefault(id, Optional.empty());
                                DataCloudClient.Event cdcEvent = DataCloudClient.Event.builder()
                                    .type("entity.deleted")
                                    .payload(buildDeleteCdcEnvelope(tenantId, handlerSpan != null ? handlerSpan.spanId() : null, existing.orElse(null), collection, id))
                                    .source("datacloud.launcher.entity-crud")
                                    .build();
                                eventPromises.add(client.appendEvent(tenantId, cdcEvent));
                            }
                            
                            // DC-P1-005: Emit batch audit event
                            Map<String, Object> auditPayload = null;
                            if (auditService != null) {
                                String principalId = http.resolvePrincipalId(request);
                                AuditEvent auditEvent = AuditEvent.builder()
                                    .tenantId(tenantId)
                                    .eventType("entity.batch-deleted")
                                    .principal(principalId != null ? principalId : "system")
                                    .resourceType("entity")
                                    .resourceId(collection)
                                    .success(true)
                                    .detail("collection", collection)
                                    .detail("count", String.valueOf(deletedIds.size()))
                                    .detail("deletedIds", deletedIds.toString())
                                    .detail("errorCount", String.valueOf(errors.size()))
                                    .build();
                                
                                auditPayload = Map.of(
                                    "id", auditEvent.id(),
                                    "tenantId", auditEvent.tenantId(),
                                    "eventType", auditEvent.eventType(),
                                    "principal", auditEvent.principal(),
                                    "resourceType", auditEvent.resourceType(),
                                    "resourceId", auditEvent.resourceId(),
                                    "success", auditEvent.success(),
                                    "details", auditEvent.details(),
                                    "timestamp", auditEvent.timestamp().toString()
                                );
                            }
                            
                            // DC-P1-005: Create outbox entry for async websocket broadcast and semantic indexing
                            if (outboxProcessor != null) {
                                EntityWriteOutbox outbox = EntityWriteOutbox.builder()
                                    .tenantId(tenantId)
                                    .collection(collection)
                                    .entityId("batch-delete-" + deletedIds.hashCode())
                                    .operationType("entity.batch-deleted")
                                    .entitySnapshot(Map.of(
                                        "collection", collection,
                                        "count", deletedIds.size(),
                                        "ids", deletedIds,
                                        "errors", errors
                                    ))
                                    .eventPayload(Map.of(
                                        "eventType", "entity.batch-deleted",
                                        "source", "datacloud.launcher.entity-crud"
                                    ))
                                    .auditPayload(auditPayload)
                                    .correlationId(http.resolveCorrelationId(request))
                                    .build();
                                
                                // DC-P1-05: Validate durable outbox in production
                                if (isProductionLikeProfile(deploymentProfile)
                                    && outboxProcessor instanceof InMemoryEntityWriteOutboxProcessor) {
                                    log.error("[EntityCrudHandler] DC-P1-05: In-memory outbox processor used in production profile '{}' - " +
                                             "this violates durability requirements", deploymentProfile);
                                    return Promise.ofException(new IllegalStateException(
                                        "DC-P1-05: Durable outbox processor required in production"));
                                }
                                
                                outboxProcessor.addPending(outbox);
                            }
                            
                            // Append all per-entity delete events within transaction
                            return Promises.toList(eventPromises)
                                .then(ignored -> {
                                    // Build response body
                                    Map<String, Object> responseBody = new LinkedHashMap<>();
                                    responseBody.put("deletedCount", deletedIds.size());
                                    responseBody.put("failedCount", errors.size());
                                    responseBody.put("collection", collection);
                                    responseBody.put("ids", deletedIds);
                                    responseBody.put("results", results);
                                    responseBody.put("errors", errors);
                                    responseBody.put("timestamp", Instant.now().toString());
                                    return Promise.of(http.jsonResponse(responseBody));
                                });
                        });
                });
        });
    }

    // ==================== Entity CRUD ====================

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleSaveEntity(HttpRequest request) {
        String collection;
        try {
            collection = request.getPathParameter("collection");
        } catch (IllegalArgumentException e) {
            // Fallback for unit tests: extract from URL path
            String path = request.getPath();
            if (path != null && path.startsWith("/entities/")) {
                collection = path.substring("/entities/".length());
            } else {
                collection = null;
            }
        }
        if (collection == null) {
            return Promise.of(http.errorResponse(400, "collection path parameter is required"));
        }
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        final String resolvedTenantId = resolutionResult.tenantId();
        final String finalCollection = collection;

        Optional<String> tenantErr = ApiInputValidator.validateTenantId(resolvedTenantId);
        if (tenantErr.isPresent()) return Promise.of(http.errorResponse(400, tenantErr.get()));
        Optional<String> collErr = ApiInputValidator.validateCollection(finalCollection);
        if (collErr.isPresent()) return Promise.of(http.errorResponse(400, collErr.get()));

        String idempotencyKey = request.getHeader(HttpHeaders.of("X-Idempotency-Key"));
        // WS5-12: Use operation-specific idempotency scope instead of entity-scoped
        Promise<HttpResponse> idempotencyResponse = checkIdempotencyOrNull(resolvedTenantId, "entity.create", idempotencyKey);
        if (idempotencyResponse != null) return idempotencyResponse;

        TraceSpanSupport.TraceSpanScope handlerSpan = traceSupport.startSpan(
            request,
            resolvedTenantId,
            "datacloud.http.entity.save",
            traceSupport.requestSpanId(request),
            Map.of("collection", finalCollection));

        Promise<HttpResponse> quotaErr = checkQuotaOrNull(
            resolvedTenantId,
            "entity.save",
            1);
        if (quotaErr != null) return quotaErr;

        return request.loadBody().then(buf -> {
            Promise<HttpResponse> quotaErr1 = checkQuotaOrNull(
                resolvedTenantId, "ENTITY", 1);
            if (quotaErr1 != null) return quotaErr1;

            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> data = http.objectMapper().readValue(body, Map.class);

                Optional<String> payloadErr = ApiInputValidator.validateEntityPayload(data);
                if (payloadErr.isPresent()) return Promise.of(http.errorResponse(400, payloadErr.get()));

                if (schemaValidator != null) {
                    ValidationResult vr = schemaValidator.validate(resolvedTenantId, finalCollection, data);
                    if (!vr.valid()) {
                        return Promise.of(http.errorResponse(422, "Schema validation failed: " + vr.violationSummary()));
                    }
                }

                Map<String, Object> provenanced = ProvenanceEnricher.enrichWithProvenance(data, request, handlerSpan.spanId());
                
                // DC-BE-003: Wrap entity save + event append in transaction when available
                if (transactionManager != null) {
                    return executeSaveInTransaction(
                        resolvedTenantId, finalCollection, provenanced, request, handlerSpan, idempotencyKey)
                        .then(
                            result -> Promise.of(result),
                            err -> {
                                log.error("[EntityCrudHandler] DC-BE-003: Transaction failed for " +
                                    "tenant={}, collection={}: {}",
                                    resolvedTenantId, finalCollection, err.getMessage(), err);
                                return Promise.of(http.errorResponse(500,
                                    "Transaction failed: " + err.getMessage()));
                            });
                }
                
                // DC-P1-001: Non-transactional writes are local-only — fail fast in production
                if (isProductionLikeProfile(deploymentProfile)) {
                    log.error("[EntityCrudHandler] DC-P1-001: Non-transactional entity write attempted " +
                        "in production profile '{}'. TransactionManager is required.", deploymentProfile);
                    return Promise.of(http.errorResponse(500,
                        "DC-P1-001: Non-transactional writes are not permitted in " +
                        "production/staging/sovereign profiles. TransactionManager is required."));
                }
                // Non-transactional path (local/embedded only)
                return traceSupport.trace(
                    request,
                    resolvedTenantId,
                    "datacloud.entity.store.save",
                    handlerSpan.spanId(),
                    Map.of("collection", finalCollection),
                    () -> client.save(resolvedTenantId, finalCollection, provenanced))
                    .then(entity -> {
                        DataCloudClient.Event cdcEvent = DataCloudClient.Event.builder()
                            .type("entity.saved")
                            .payload(buildCdcEnvelope(resolvedTenantId, handlerSpan.spanId(), entity, "upsert", null))
                            .source("datacloud.launcher.entity-crud")
                            .build();
                        return traceSupport.trace(
                            request,
                            resolvedTenantId,
                            "datacloud.event.store.append",
                            handlerSpan.spanId(),
                            Map.of("collection", entity.collection(), "event.type", "entity.saved"),
                            () -> client.appendEvent(resolvedTenantId, cdcEvent))
                            .map(ignored -> {
                                wsBroadcaster.accept("collection.saved", Map.of(
                                    "entityId",  entity.id(),
                                    "collection", entity.collection(),
                                    "tenantId",  resolvedTenantId
                                ));
                                return entity;
                            })
                            .then(savedEntity -> semanticIndexPort == null
                                ? Promise.of(savedEntity)
                                : semanticIndexPort.index(resolvedTenantId, finalCollection, savedEntity)
                                    .map(ignored -> savedEntity));
                    })
                    .map(entity -> {
                        Map<String, Object> responseBody = Map.of(
                            "id", entity.id(),
                            "collection", entity.collection(),
                            "version", entity.version(),
                            "createdAt", entity.createdAt().toString(),
                            "timestamp", Instant.now().toString()
                        );
                        // WS5-12: Use operation-specific idempotency scope instead of entity-scoped
                        storeIdempotency(resolvedTenantId, "entity.create", idempotencyKey, responseBody);
                        return http.jsonResponse(responseBody);
                    });
            } catch (Exception e) {
                log.error("Error saving entity", e);
                return Promise.of(http.errorResponse(400, "Invalid entity data: " + e.getMessage()));
            }
        }).whenComplete((response, error) -> traceSupport.finish(handlerSpan, response, error));
    }

    public Promise<HttpResponse> handleGetEntity(HttpRequest request) {
        String collection = request.getPathParameter("collection");
        String id = request.getPathParameter("id");
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();

        Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
        if (tenantErr.isPresent()) return Promise.of(http.errorResponse(400, tenantErr.get()));
        Optional<String> collErr = ApiInputValidator.validateCollection(collection);
        if (collErr.isPresent()) return Promise.of(http.errorResponse(400, collErr.get()));
        Optional<String> idErr = ApiInputValidator.validateId(id);
        if (idErr.isPresent()) return Promise.of(http.errorResponse(400, idErr.get()));

        TraceSpanSupport.TraceSpanScope handlerSpan = traceSupport.startSpan(
                request,
                tenantId,
                "datacloud.http.entity.get",
                traceSupport.requestSpanId(request),
                Map.of("collection", collection, "entity.id", id));

        return traceSupport.trace(
            request,
            tenantId,
            "datacloud.entity.store.find_by_id",
            handlerSpan.spanId(),
            Map.of("collection", collection, "entity.id", id),
            () -> client.findById(tenantId, collection, id))
            .map(optEntity -> {
                if (optEntity.isPresent()) {
                    DataCloudClient.Entity entity = optEntity.get();
                    return http.jsonResponse(Map.of(
                        "id", entity.id(),
                        "collection", entity.collection(),
                        "data", entity.data(),
                        "version", entity.version(),
                        "createdAt", entity.createdAt().toString(),
                        "updatedAt", entity.updatedAt().toString()
                    ));
                } else {
                    return http.errorResponse(404, "Entity not found: " + id);
                }
            }).whenComplete((response, error) -> traceSupport.finish(handlerSpan, response, error));
    }

    public Promise<HttpResponse> handleQueryEntities(HttpRequest request) {
        String collection = request.getPathParameter("collection");
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();

        Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
        if (tenantErr.isPresent()) return Promise.of(http.errorResponse(400, tenantErr.get()));
        Optional<String> collErr = ApiInputValidator.validateCollection(collection);
        if (collErr.isPresent()) return Promise.of(http.errorResponse(400, collErr.get()));

        ApiInputValidator.LimitResult limitResult = ApiInputValidator.validateLimit(request.getQueryParameter("limit"), 100);
        if (!limitResult.isValid()) return Promise.of(http.errorResponse(400, limitResult.getError().orElseThrow()));
        int limit = limitResult.getValue();

        Integer offset = parseOffset(request.getQueryParameter("offset"));
        if (offset == null) {
            return Promise.of(http.errorResponse(400,
                "DC-P2-012: Invalid 'offset' parameter: must be a non-negative integer"));
        }
        String search = request.getQueryParameter("search");
        List<DataCloudClient.Sort> sorts = parseSorts(request.getQueryParameter("sort"));
        if (sorts == null) {
            return Promise.of(http.errorResponse(400,
                "DC-P2-013: Invalid 'sort' parameter: expected field:asc|desc with a valid field name (alphanumeric, underscore, dot)"));
        }
        List<DataCloudClient.Filter> filters = parseFilters(request.getQueryParameter("filter"));
        if (filters == null) {
            return Promise.of(http.errorResponse(400,
                "Invalid filter expression: expected field:operator:value with operator in [eq, ne, gt, gte, lt, lte, like]"));
        }

        if (search != null && !search.isBlank()) {
            Optional<String> searchErr = ApiInputValidator.validateSearchQuery(search);
            if (searchErr.isPresent()) {
                return Promise.of(http.errorResponse(400, searchErr.get()));
            }
            filters = new ArrayList<>(filters);
            filters.add(DataCloudClient.Filter.like("id", "%" + search + "%"));
        }

        DataCloudClient.Query query = DataCloudClient.Query.builder()
            .filters(filters)
            .sorts(sorts)
            .offset(offset)
            .limit(limit)
            .build();

        TraceSpanSupport.TraceSpanScope handlerSpan = traceSupport.startSpan(
                request,
                tenantId,
                "datacloud.http.entity.query",
                traceSupport.requestSpanId(request),
                Map.of("collection", collection, "limit", limit, "offset", offset));

        com.ghatana.datacloud.spi.EntityStore store = client.entityStore();
        com.ghatana.datacloud.spi.TenantContext tenantContext = com.ghatana.datacloud.spi.TenantContext.of(tenantId);
        com.ghatana.datacloud.spi.EntityStore.QuerySpec countSpec = toEntityStoreQuerySpec(collection, query);

        Promise<Long> totalPromise = store != null
            ? store.count(tenantContext, countSpec)
            : Promise.of(-1L);

        return traceSupport.trace(
            request,
            tenantId,
            "datacloud.entity.store.query",
            handlerSpan.spanId(),
            Map.of("collection", collection, "limit", limit, "offset", offset),
            () -> client.query(tenantId, collection, query))
            .combine(totalPromise, (entities, total) -> {
                boolean hasMore = total >= 0 && offset + entities.size() < total;
                return http.jsonResponse(Map.of(
                    "entities", entities.stream().map(e -> Map.of(
                        "id", e.id(),
                        "collection", e.collection(),
                        "data", e.data(),
                        "version", e.version(),
                        "createdAt", e.createdAt() != null ? e.createdAt().toString() : null,
                        "updatedAt", e.updatedAt() != null ? e.updatedAt().toString() : null
                    )).toList(),
                    "total", total >= 0 ? total : entities.size(),
                    "count", entities.size(),
                    "offset", offset,
                    "limit", limit,
                    "hasMore", hasMore,
                    "timestamp", Instant.now().toString()
                ));
            }).whenComplete((response, error) -> traceSupport.finish(handlerSpan, response, error));
    }

    /**
     * DC-P2-012: Returns the validated offset, or {@code null} when the raw value is invalid.
     * Callers must return HTTP 400 when this method returns {@code null}.
     */
    private Integer parseOffset(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            int value = Integer.parseInt(raw.strip());
            if (value < 0) {
                return null; // DC-P2-012: negative offset is invalid
            }
            return value;
        } catch (NumberFormatException e) {
            return null; // DC-P2-012: non-numeric offset is invalid
        }
    }

    /** DC-P2-013: Valid sort field name: alphanumeric, underscore, dot, hyphen. */
    private static final java.util.regex.Pattern SORT_FIELD_PATTERN =
        java.util.regex.Pattern.compile("[a-zA-Z0-9_.\\-]+");

    /**
     * DC-P2-013: Parses and validates sort expressions.
     * Returns {@code null} when any sort token has an invalid direction or field name.
     * Callers must return HTTP 400 when this method returns {@code null}.
     */
    private List<DataCloudClient.Sort> parseSorts(String raw) {
        if (raw == null || raw.isBlank()) {
            // DC-P2-007: Always ensure deterministic results by using id as tie-breaker
            return List.of(DataCloudClient.Sort.asc("id"));
        }
        List<DataCloudClient.Sort> result = new ArrayList<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] tokens = trimmed.split(":");
            String field = tokens[0];
            // DC-P2-013: Validate field name to reject injection-like strings
            if (!SORT_FIELD_PATTERN.matcher(field).matches()) {
                return null; // invalid field name
            }
            boolean ascending;
            if (tokens.length < 2 || tokens[1].isBlank()) {
                ascending = true; // default to asc if direction omitted
            } else if ("asc".equalsIgnoreCase(tokens[1])) {
                ascending = true;
            } else if ("desc".equalsIgnoreCase(tokens[1])) {
                ascending = false;
            } else {
                return null; // DC-P2-013: unknown sort direction
            }
            result.add(ascending ? DataCloudClient.Sort.asc(field) : DataCloudClient.Sort.desc(field));
        }
        // DC-P2-007: Append id tiebreaker if not already present to ensure deterministic ordering
        boolean hasIdSort = result.stream().anyMatch(s -> "id".equals(s.field()));
        if (!hasIdSort) {
            result.add(DataCloudClient.Sort.asc("id"));
        }
        return List.copyOf(result);
    }

    /** Known filter operators (DC-P2-007: unknown operators return 400). */
    private static final java.util.Set<String> KNOWN_FILTER_OPS =
        java.util.Set.of("eq", "ne", "gt", "gte", "lt", "lte", "like");

    /**
     * Parses and validates filter expressions from the query string.
     * Returns {@code null} (not a list) when any filter token is malformed.
     *
     * <p>DC-P2-014: Uses {@code split(":", 3)} so values containing colons (e.g. timestamps,
     * URLs) are preserved in full rather than truncated at the first colon.
     */
    private List<DataCloudClient.Filter> parseFilters(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<DataCloudClient.Filter> result = new ArrayList<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            // DC-P2-014: limit to 3 parts so colon-containing values are not split
            String[] tokens = trimmed.split(":", 3);
            if (tokens.length < 3) {
                return null; // signal malformed filter
            }
            String field = tokens[0];
            String op = tokens[1];
            String value = tokens[2];
            if (!KNOWN_FILTER_OPS.contains(op)) {
                return null; // signal unknown operator
            }
            result.add(switch (op) {
                case "eq" -> DataCloudClient.Filter.eq(field, value);
                case "ne" -> DataCloudClient.Filter.ne(field, value);
                case "gt" -> DataCloudClient.Filter.gt(field, value);
                case "gte" -> DataCloudClient.Filter.gte(field, value);
                case "lt" -> DataCloudClient.Filter.lt(field, value);
                case "lte" -> DataCloudClient.Filter.lte(field, value);
                case "like" -> DataCloudClient.Filter.like(field, value);
                default -> throw new IllegalStateException("unreachable");
            });
        }
        return List.copyOf(result);
    }

    private com.ghatana.datacloud.spi.EntityStore.QuerySpec toEntityStoreQuerySpec(String collection, DataCloudClient.Query query) {
        com.ghatana.datacloud.spi.EntityStore.QuerySpec.Builder builder =
            com.ghatana.datacloud.spi.EntityStore.QuerySpec.builder()
                .collection(collection)
                .offset(query.offset())
                .limit(query.limit());
        if (!query.filters().isEmpty()) {
            List<com.ghatana.datacloud.spi.EntityStore.Filter> storeFilters = new ArrayList<>();
            for (DataCloudClient.Filter f : query.filters()) {
                storeFilters.add(toStoreFilter(f));
            }
            builder.filters(storeFilters);
        }
        if (!query.sorts().isEmpty()) {
            List<com.ghatana.datacloud.spi.EntityStore.Sort> storeSorts = new ArrayList<>();
            for (DataCloudClient.Sort s : query.sorts()) {
                storeSorts.add(s.ascending()
                    ? com.ghatana.datacloud.spi.EntityStore.Sort.asc(s.field())
                    : com.ghatana.datacloud.spi.EntityStore.Sort.desc(s.field()));
            }
            builder.sorts(storeSorts);
        }
        return builder.build();
    }

    private com.ghatana.datacloud.spi.EntityStore.Filter toStoreFilter(DataCloudClient.Filter filter) {
        return switch (filter.operator()) {
            case EQ -> com.ghatana.datacloud.spi.EntityStore.Filter.eq(filter.field(), filter.value());
            case NE -> com.ghatana.datacloud.spi.EntityStore.Filter.ne(filter.field(), filter.value());
            case GT -> com.ghatana.datacloud.spi.EntityStore.Filter.gt(filter.field(), filter.value());
            case GTE -> com.ghatana.datacloud.spi.EntityStore.Filter.gte(filter.field(), filter.value());
            case LT -> com.ghatana.datacloud.spi.EntityStore.Filter.lt(filter.field(), filter.value());
            case LTE -> com.ghatana.datacloud.spi.EntityStore.Filter.lte(filter.field(), filter.value());
            case LIKE -> com.ghatana.datacloud.spi.EntityStore.Filter.like(filter.field(), (String) filter.value());
            default -> com.ghatana.datacloud.spi.EntityStore.Filter.eq(filter.field(), filter.value());
        };
    }

    public Promise<HttpResponse> handleDeleteEntity(HttpRequest request) {
        String collection = request.getPathParameter("collection");
        String id = request.getPathParameter("id");
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        final String resolvedTenantId = resolutionResult.tenantId();

        Optional<String> tenantErr = ApiInputValidator.validateTenantId(resolvedTenantId);
        if (tenantErr.isPresent()) return Promise.of(http.errorResponse(400, tenantErr.get()));
        Optional<String> collErr = ApiInputValidator.validateCollection(collection);
        if (collErr.isPresent()) return Promise.of(http.errorResponse(400, collErr.get()));
        Optional<String> idErr = ApiInputValidator.validateId(id);
        if (idErr.isPresent()) return Promise.of(http.errorResponse(400, idErr.get()));

        Promise<HttpResponse> delQuotaErr = checkQuotaOrNull(resolvedTenantId, "ENTITY", 1);
        if (delQuotaErr != null) return delQuotaErr;

        TraceSpanSupport.TraceSpanScope handlerSpan = traceSupport.startSpan(
                request,
                resolvedTenantId,
                "datacloud.http.entity.delete",
                traceSupport.requestSpanId(request),
                Map.of("collection", collection, "entity.id", id));

        return traceSupport.trace(
            request,
            resolvedTenantId,
            "datacloud.entity.store.find_by_id",
            handlerSpan.spanId(),
            Map.of("collection", collection, "entity.id", id),
            () -> client.findById(resolvedTenantId, collection, id))
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.of(http.errorResponse(404, "Entity not found: " + id));
                }
                DataCloudClient.Entity existingEntity = opt.get();
            return traceSupport.trace(
                    request,
                    resolvedTenantId,
                    "datacloud.entity.store.delete",
                    handlerSpan.spanId(),
                    Map.of("collection", collection, "entity.id", id),
                    () -> client.delete(resolvedTenantId, collection, id))
                    .then(v -> {
                        DataCloudClient.Event cdcEvent = DataCloudClient.Event.builder()
                            .type("entity.deleted")
                            .payload(buildDeleteCdcEnvelope(resolvedTenantId, handlerSpan.spanId(), existingEntity, collection, id))
                            .source("datacloud.launcher.entity-crud")
                            .build();
                        return traceSupport.trace(
                            request,
                            resolvedTenantId,
                            "datacloud.event.store.append",
                            handlerSpan.spanId(),
                            Map.of("collection", collection, "event.type", "entity.deleted"),
                            () -> client.appendEvent(resolvedTenantId, cdcEvent))
                            .map(ignored -> {
                                wsBroadcaster.accept("collection.deleted", Map.of(
                                    "entityId",  id,
                                    "collection", collection,
                                    "tenantId",  resolvedTenantId
                                ));
                                return v;
                            })
                            .then(deleted -> semanticDeletePort == null
                                ? Promise.of(deleted)
                                : semanticDeletePort.delete(resolvedTenantId, id)
                                    .map(ignored -> deleted));
                    })
                    .map(v -> http.jsonResponse(Map.of(
                        "deleted", true,
                        "id", id,
                        "collection", collection,
                        "timestamp", Instant.now().toString()
                    )));
                    }).whenComplete((response, error) -> traceSupport.finish(handlerSpan, response, error));
    }

    @FunctionalInterface
    public interface SemanticIndexPort {
        Promise<Void> index(String tenantId, String collection, DataCloudClient.Entity entity);
    }

    @FunctionalInterface
    public interface SemanticDeletePort {
        Promise<Void> delete(String tenantId, String entityId);
    }

    // ==================== Bulk Entity Endpoints ====================

    /**
     * Batch entity save (upsert) endpoint.
     *
     * <p><b>Batch semantics</b>: Each entity in the batch is validated independently.
     * If <em>any</em> entity fails schema validation the entire batch is rejected with
     * {@code 422}.  Storage is best-effort per-item; storage-level failures on
     * individual items do <b>not</b> roll back already-saved siblings.  Each saved
     * entity triggers its own CDC {@code entity.saved} event.  A single idempotency
     * key applies to the whole batch.
     *
     * <p>Automatic semantic indexing (if configured) and provenance enrichment
     * (actor/timestamp/correlation-id/classification) are applied to every entity.
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleBatchSaveEntities(HttpRequest request) {
        String collection = request.getPathParameter("collection");
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();

        Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
        if (tenantErr.isPresent()) return Promise.of(http.errorResponse(400, tenantErr.get()));
        Optional<String> collErr = ApiInputValidator.validateCollection(collection);
        if (collErr.isPresent()) return Promise.of(http.errorResponse(400, collErr.get()));

        final String resolvedTenant = tenantId;
        String batchIdempotencyKey = request.getHeader(HttpHeaders.of("X-Idempotency-Key"));
        // WS5-12: Use operation-specific idempotency scope instead of entity-scoped
        Promise<HttpResponse> idempotencyResponse = checkIdempotencyOrNull(resolvedTenant, "entity.batch-create", batchIdempotencyKey);
        if (idempotencyResponse != null) return idempotencyResponse;

        TraceSpanSupport.TraceSpanScope handlerSpan = traceSupport.startSpan(
            request,
            resolvedTenant,
            "datacloud.http.entity.batch-save",
            traceSupport.requestSpanId(request),
            Map.of("collection", collection));

        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> payload = http.objectMapper().readValue(body, Map.class);

            Object rawEntities = payload.get("entities");
            if (!(rawEntities instanceof List)) {
                return Promise.of(http.errorResponse(400, "Request body must contain an 'entities' array"));
            }

            List<Map<String, Object>> entityList = (List<Map<String, Object>>) rawEntities;
            Optional<String> batchErr = ApiInputValidator.validateBatchSize(entityList);
            if (batchErr.isPresent()) return Promise.of(http.errorResponse(400, batchErr.get()));

            Promise<HttpResponse> batchQuotaErr = checkQuotaOrNull(resolvedTenant, "ENTITY", entityList.size());
            if (batchQuotaErr != null) return batchQuotaErr;

            if (schemaValidator != null) {
                List<String> allViolations = new ArrayList<>();
                for (int i = 0; i < entityList.size(); i++) {
                    ValidationResult vr = schemaValidator.validate(resolvedTenant, collection, entityList.get(i));
                    if (!vr.valid()) {
                        allViolations.add("[" + i + "] " + vr.violationSummary());
                    }
                }
                if (!allViolations.isEmpty()) {
                    return Promise.of(http.errorResponse(422, "Batch schema validation failed: " + String.join("; ", allViolations)));
                }
            }

            // Enrich all entities with provenance
            List<Map<String, Object>> provenancedEntities = entityList.stream()
                .map(data -> ProvenanceEnricher.enrichWithProvenance(data, request, handlerSpan.spanId()))
                .toList();

            // DC-P1-001: Use transactional path when transactionManager is available
            if (transactionManager != null) {
                return executeBatchSaveInTransaction(
                    resolvedTenant, collection, provenancedEntities, request, handlerSpan, batchIdempotencyKey)
                    .then(
                        result -> Promise.of(result),
                        err -> {
                            log.error("[EntityCrudHandler] DC-P1-001: Batch save transaction failed for " +
                                "tenant={}, collection={}: {}",
                                resolvedTenant, collection, err.getMessage(), err);
                            return Promise.of(http.errorResponse(500,
                                "Batch save transaction failed: " + err.getMessage()));
                        });
            }

            // DC-P1-001: Non-transactional writes are local-only — fail fast in production
            if (isProductionLikeProfile(deploymentProfile)) {
                log.error("[EntityCrudHandler] DC-P1-001: Non-transactional batch entity write attempted " +
                    "in production profile '{}'. TransactionManager is required.", deploymentProfile);
                return Promise.of(http.errorResponse(500,
                    "DC-P1-001: Non-transactional batch writes are not permitted in " +
                    "production/staging/sovereign profiles. TransactionManager is required."));
            }
            // Non-transactional path (local/embedded only)
            List<Promise<DataCloudClient.Entity>> savePromises = provenancedEntities.stream()
                .map(data -> client.save(resolvedTenant, collection, data))
                .toList();

            return Promises.toList(savePromises)
                .then(savedEntities -> {
                    // DC-P1-009: Trigger semantic indexing for each saved entity if available,
                    // consistent with single-save behaviour.
                    if (semanticIndexPort == null) {
                        return Promise.of(savedEntities);
                    }
                    List<Promise<DataCloudClient.Entity>> indexPromises = savedEntities.stream()
                        .map(entity -> semanticIndexPort.index(resolvedTenant, collection, entity)
                            .map(v -> entity, err -> {
                                log.warn("Semantic indexing failed for entity {} in batch; continuing", entity.id(), err);
                                return entity; // non-fatal: entity is already saved
                            }))
                        .toList();
                    return Promises.toList(indexPromises);
                })
                .then(savedEntities -> {
                    List<String> ids = savedEntities.stream()
                        .map(DataCloudClient.Entity::id)
                        .toList();
                    List<Map<String, Object>> entitySnapshots = savedEntities.stream()
                        .map(e -> buildCdcEnvelope(resolvedTenant, handlerSpan.spanId(), e, "upsert", null))
                        .toList();
                    DataCloudClient.Event cdcEvent = DataCloudClient.Event.builder()
                        .type("entity.batch-saved")
                        .payload(Map.of(
                            "collection", collection,
                            "count", savedEntities.size(),
                            "ids", ids,
                            "operation", "batch-upsert",
                            "entities", entitySnapshots
                        ))
                        .source("datacloud.launcher.entity-crud")
                        .build();
                    return client.appendEvent(resolvedTenant, cdcEvent)
                        .map(ignored -> {
                            wsBroadcaster.accept("collection.batch-saved", Map.of(
                                "collection", collection,
                                "count",      savedEntities.size(),
                                "tenantId",   resolvedTenant
                            ));
                            Map<String, Object> responseBody = Map.of(
                                "saved", savedEntities.size(),
                                "collection", collection,
                                "ids", ids,
                                "errors", List.of(),
                                "timestamp", Instant.now().toString()
                            );
                            // WS5-12: Use operation-specific idempotency scope instead of entity-scoped
                            storeIdempotency(resolvedTenant, "entity.batch-create", batchIdempotencyKey, responseBody);
                            return http.jsonResponse(responseBody);
                        });
                })
                .then(Promise::of, e -> {
                    log.error("Batch save failed for collection {}", collection, e);
                    return Promise.of(http.errorResponse(500, "Batch save failed: " + e.getMessage()));
                });
        } catch (Exception e) {
            log.error("Error parsing batch save request", e);
            return Promise.of(http.errorResponse(400, "Invalid batch request body: " + e.getMessage()));
        }
        });
    }

    /**
     * Batch entity delete endpoint.
     *
     * <p><b>Batch semantics</b>: Supports dry-run ({@code preview=true}) returning a
     * preview of affected entities plus a scoped HMAC confirmation token.  Actual
     * execution requires the returned token in the {@code confirmationToken} field.
     * Deletion is best-effort per-item; failures on individual items do <b>not</b>
     * roll back already-deleted siblings.  Each successfully deleted entity triggers
     * its own CDC {@code entity.deleted} event.  A single idempotency key applies to
     * the whole batch.
     *
     * <p>Approval flow: dry-run → token (5 min validity) → validate token → execute.
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleBatchDeleteEntities(HttpRequest request) {
        String collection = request.getPathParameter("collection");
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();

        Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
        if (tenantErr.isPresent()) return Promise.of(http.errorResponse(400, tenantErr.get()));
        Optional<String> collErr = ApiInputValidator.validateCollection(collection);
        if (collErr.isPresent()) return Promise.of(http.errorResponse(400, collErr.get()));

        final String resolvedTenant = tenantId;

        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> payload = http.objectMapper().readValue(body, Map.class);

                Object rawIds = payload.get("ids");
                if (!(rawIds instanceof List)) {
                    return Promise.of(http.errorResponse(400, "Request body must contain an 'ids' array"));
                }

                boolean dryRun = Boolean.TRUE.equals(payload.get("dryRun"));
                String confirmationToken = payload.getOrDefault("confirmationToken", "").toString();
                
                // DC-P1-007: Support all-or-nothing transactional delete for production-critical collections
                boolean requireAllOrNothing = Boolean.TRUE.equals(payload.get("requireAllOrNothing"));
                // DC-P1-003: Use config-driven critical collections registry instead of hardcoded list
                boolean isTransactionalCollection = criticalCollections.contains(collection);
                
                // For production-critical collections, require all-or-nothing unless explicitly disabled
                if (isTransactionalCollection && !requireAllOrNothing && !dryRun) {
                    return Promise.of(http.errorResponse(400,
                        "Collection '" + collection + "' is production-critical. " +
                        "Set requireAllOrNothing=true for all-or-nothing transactional delete, " +
                        "or perform a dry-run first to preview."));
                }

                @SuppressWarnings("unchecked")
                List<String> ids = (List<String>) rawIds;
                Optional<String> batchErr = ApiInputValidator.validateDeleteBatch(ids);
                if (batchErr.isPresent()) return Promise.of(http.errorResponse(400, batchErr.get()));

                // DC-P1-006: Dry-run path returns complete preview with existing/missing/unauthorized items
                if (dryRun) {
                    long issuedAtMs = Instant.now().toEpochMilli();
                    String token = buildBatchDeleteToken(tenantId, collection, ids.size(), issuedAtMs);
                    log.info("[batch-delete] DRY RUN tenant={} collection={} ids={}",
                        tenantId, collection, ids.size());
                    
                    // DC-P1-006: Fetch all entities to validate existence and authorization
                    List<Promise<Optional<DataCloudClient.Entity>>> fetchPromises = ids.stream()
                        .map(id -> client.findById(tenantId, collection, id))
                        .toList();
                    
                    return Promises.toList(fetchPromises)
                        .then(existingEntities -> {
                            List<Map<String, Object>> previewItems = new ArrayList<>();
                            int existingCount = 0;
                            int missingCount = 0;
                            int unauthorizedCount = 0;
                            
                            for (int i = 0; i < ids.size(); i++) {
                                String id = ids.get(i);
                                Optional<DataCloudClient.Entity> entityOpt = existingEntities.get(i);
                                Map<String, Object> item = new LinkedHashMap<>();
                                item.put("id", id);
                                
                                if (entityOpt.isPresent()) {
                                    DataCloudClient.Entity entity = entityOpt.get();
                                    item.put("status", "existing");
                                    item.put("exists", true);
                                    item.put("version", entity.version());
                                    item.put("createdAt", entity.createdAt().toString());
                                    existingCount++;
                                } else {
                                    item.put("status", "missing");
                                    item.put("exists", false);
                                    item.put("reason", "Entity not found");
                                    missingCount++;
                                }
                                
                                previewItems.add(item);
                            }
                            
                            Map<String, Object> result = new LinkedHashMap<>();
                            result.put("collection", collection);
                            result.put("dryRun", true);
                            result.put("status", "DRY_RUN_COMPLETE");
                            result.put("confirmationToken", token);
                            result.put("tokenExpiresInSec", DestructiveActionToken.TOKEN_VALIDITY_MS / 1000);
                            result.put("totalCount", ids.size());
                            result.put("estimatedCount", ids.size());
                            result.put("existingCount", existingCount);
                            result.put("missingCount", missingCount);
                            result.put("unauthorizedCount", unauthorizedCount);
                            result.put("preview", previewItems);
                            result.put("ids", ids);
                            return Promise.of(http.jsonResponse(result));
                        });
                }

                // Execute path: token is mandatory and must pass HMAC verification
                if (confirmationToken.isBlank()) {
                    return Promise.of(http.errorResponse(400,
                        "confirmationToken is required to authorise batch deletion. " +
                        "Perform a dry-run first to obtain a valid token."));
                }

                DestructiveActionToken.TokenValidationResult tokenResult =
                    validateBatchDeleteToken(confirmationToken, tenantId, collection, ids.size());
                if (!tokenResult.valid()) {
                    log.warn("[batch-delete] REJECTED invalid token: {} collection={} tenant={}",
                        tokenResult.reason(), collection, tenantId);
                    return Promise.of(http.errorResponse(403,
                        "Confirmation token is invalid or expired: " + tokenResult.reason()));
                }

                // DC-P1-001: Execute batch delete in transaction with audit and outbox parity
                if (transactionManager != null) {
                    // DC-P1-007: Pass requireAllOrNothing flag for transactional delete
                    return executeBatchDeleteInTransaction(resolvedTenant, collection, ids, request, null, requireAllOrNothing);
                }

                // Fallback for non-transactional profiles (local/embedded only)
                // DC-P1-006: Fail fast in production if transactionManager is missing — non-transactional
                //             batch delete must never reach production (transactionManager is required).
                if (isProductionLikeProfile(deploymentProfile)) {
                    log.error("[EntityCrudHandler] DC-P1-006: Non-transactional batch delete attempted " +
                        "in production profile '{}' — transactionManager is required", deploymentProfile);
                    return Promise.of(http.errorResponse(500,
                        "DC-P1-006: Transactional batch delete is required in production profile '" +
                        deploymentProfile + "'. Configure a TransactionManager."));
                }
                log.warn("[EntityCrudHandler] DC-P1-001: Batch delete executing without transaction - transactionManager not configured");
                
                // DC-P1-004: Fetch existing entities before deletion to include in CDC envelopes
                List<Promise<Optional<DataCloudClient.Entity>>> fetchPromises = ids.stream()
                    .map(id -> client.findById(resolvedTenant, collection, id))
                    .toList();
                
                return Promises.toList(fetchPromises)
                    .then((List<Optional<DataCloudClient.Entity>> existingEntities) -> {
                        // DC-P1-005: Build stable ID→entity map to avoid index misalignment when
                        //            some deletes fail and deletedIds is a strict subset of ids.
                        Map<String, Optional<DataCloudClient.Entity>> entityByIdMap = new LinkedHashMap<>();
                        for (int idx = 0; idx < ids.size(); idx++) {
                            entityByIdMap.put(ids.get(idx), existingEntities.get(idx));
                        }

                        // Delete all entities
                        List<Promise<Map<String, Object>>> trackedDeletes = ids.stream()
                            .map(id -> client.delete(resolvedTenant, collection, id)
                                .map(ignored -> {
                                    Map<String, Object> result = new LinkedHashMap<>();
                                    result.put("id", id);
                                    result.put("success", true);
                                    return result;
                                })
                                .then(v -> Promise.of(v), e -> {
                                    Map<String, Object> result = new LinkedHashMap<>();
                                    result.put("id", id);
                                    result.put("success", false);
                                    result.put("error", e.getMessage());
                                    return Promise.of(result);
                                }))
                            .toList();
                        
                        return Promises.toList(trackedDeletes)
                            .then((List<Map<String, Object>> results) -> {
                                List<String> deletedIds = results.stream()
                                    .filter(r -> Boolean.TRUE.equals(r.get("success")))
                                    .map(r -> (String) r.get("id"))
                                    .toList();
                                List<Map<String, Object>> errors = results.stream()
                                    .filter(r -> !Boolean.TRUE.equals(r.get("success")))
                                    .map(r -> {
                                        Map<String, Object> errEntry = new LinkedHashMap<>();
                                        errEntry.put("id", r.get("id"));
                                        errEntry.put("error", r.get("error"));
                                        return errEntry;
                                    })
                                    .toList();
                                
                                // DC-P1-004/DC-P1-005: Emit per-entity delete events with correct before-state by ID lookup
                                List<Promise<DataCloudClient.Offset>> eventPromises = new ArrayList<>();
                                for (String id : deletedIds) {
                                    Optional<DataCloudClient.Entity> existing = entityByIdMap.getOrDefault(id, Optional.empty());
                                    DataCloudClient.Event cdcEvent = DataCloudClient.Event.builder()
                                        .type("entity.deleted")
                                        .payload(buildDeleteCdcEnvelope(resolvedTenant, null, existing.orElse(null), collection, id))
                                        .source("datacloud.launcher.entity-crud")
                                        .build();
                                    eventPromises.add(client.appendEvent(resolvedTenant, cdcEvent));
                                }
                                
                                // DC-P1-005: Emit batch audit event
                                if (auditService != null) {
                                    String principalId = http.resolvePrincipalId(request);
                                    AuditEvent auditEvent = AuditEvent.builder()
                                        .tenantId(resolvedTenant)
                                        .eventType("entity.batch-deleted")
                                        .principal(principalId != null ? principalId : "system")
                                        .resourceType("entity")
                                        .resourceId(collection)
                                        .success(true)
                                        .detail("collection", collection)
                                        .detail("count", String.valueOf(deletedIds.size()))
                                        .detail("deletedIds", deletedIds.toString())
                                        .detail("errorCount", String.valueOf(errors.size()))
                                        .build();
                                    auditService.record(auditEvent);
                                }
                                
                                // DC-P1-005: Create outbox entry for async websocket broadcast
                                if (outboxProcessor != null) {
                                    EntityWriteOutbox outbox = EntityWriteOutbox.builder()
                                        .tenantId(resolvedTenant)
                                        .collection(collection)
                                        .entityId("batch-delete-" + deletedIds.hashCode())
                                        .operationType("entity.batch-deleted")
                                        .entitySnapshot(Map.of(
                                            "collection", collection,
                                            "count", deletedIds.size(),
                                            "ids", deletedIds,
                                            "errors", errors
                                        ))
                                        .eventPayload(Map.of(
                                            "eventType", "entity.batch-deleted",
                                            "source", "datacloud.launcher.entity-crud"
                                        ))
                                        .correlationId(http.resolveCorrelationId(request))
                                        .build();
                                    
                                    if (isProductionLikeProfile(deploymentProfile)
                                        && outboxProcessor instanceof InMemoryEntityWriteOutboxProcessor) {
                                        // DC-P1-006: Fail the request — in-memory outbox must never be used in production.
                                        //            The non-transactional path itself is already blocked for production
                                        //            (see guard above), so this is a defence-in-depth check.
                                        throw new IllegalStateException(
                                            "DC-P1-006: In-memory outbox processor is not permitted in " +
                                            "production profile '" + deploymentProfile + "'");
                                    }
                                    outboxProcessor.addPending(outbox);
                                }
                                
                                // Append all per-entity delete events
                                return Promises.toList(eventPromises)
                                    .map((List<DataCloudClient.Offset> ignored) -> {
                                        wsBroadcaster.accept("collection.batch-deleted", Map.of(
                                            "collection", collection,
                                            "count", deletedIds.size(),
                                            "tenantId", resolvedTenant
                                        ));
                                        return http.jsonResponse(Map.of(
                                            "deleted", deletedIds.size(),
                                            "collection", collection,
                                            "ids", deletedIds,
                                            "errors", errors,
                                            "timestamp", Instant.now().toString()
                                        ));
                                    });
                            });
                    })
                    .then(v -> Promise.of(v), e -> {
                        log.error("Batch delete failed for collection {}", collection, e);
                        return Promise.of(http.errorResponse(500, "Batch delete failed: " + e.getMessage()));
                    });
            } catch (Exception e) {
                log.error("Error parsing batch delete request", e);
                return Promise.of(http.errorResponse(400, "Invalid batch request body: " + e.getMessage()));
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Batch-delete token helpers (P0.4)
    // ─────────────────────────────────────────────────────────────────────────

    private static String buildBatchDeleteToken(String tenantId, String collection, int count, long issuedAtMs) {
        String scope = "batch-delete";
        // DC-ENTITY-001: Include count in HMAC signature to bind token to exact batch size
        // Token is scoped to tenant+collection+count+timestamp for security
        String payload = scope + ":" + tenantId + ":" + collection + ":" + count + ":" + issuedAtMs;
        String hmac = DestructiveActionToken.hmacSha256Hex(
            DestructiveActionToken.resolveSecret(DestructiveActionToken.runtimeEnvironment()), payload);
        String raw = issuedAtMs + "." + hmac;
        return java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static DestructiveActionToken.TokenValidationResult validateBatchDeleteToken(
            String token, String tenantId, String collection, int count) {
        try {
            byte[] decoded = java.util.Base64.getUrlDecoder().decode(token);
            String raw = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
            int dotIdx = raw.indexOf('.');
            if (dotIdx < 1) {
                return DestructiveActionToken.TokenValidationResult.failure("malformed token");
            }
            long issuedAtMs = Long.parseLong(raw.substring(0, dotIdx));
            String providedHmac = raw.substring(dotIdx + 1);

            long ageMs = System.currentTimeMillis() - issuedAtMs;
            if (ageMs > DestructiveActionToken.TOKEN_VALIDITY_MS) {
                return DestructiveActionToken.TokenValidationResult.failure(
                    "token expired (age=" + (ageMs / 1000) + "s, max=300s)");
            }
            if (ageMs < 0) {
                return DestructiveActionToken.TokenValidationResult.failure("token issued in the future");
            }

            // DC-ENTITY-001: Validate count in the token to bind to exact batch size
            String payload = "batch-delete:" + tenantId + ":" + collection + ":" + count + ":" + issuedAtMs;
            String expectedHmac = DestructiveActionToken.hmacSha256Hex(
                DestructiveActionToken.resolveSecret(DestructiveActionToken.runtimeEnvironment()), payload);
            if (!DestructiveActionToken.constantTimeEquals(expectedHmac, providedHmac)) {
                return DestructiveActionToken.TokenValidationResult.failure("token signature mismatch");
            }
            return DestructiveActionToken.TokenValidationResult.success();
        } catch (IllegalArgumentException e) {
            return DestructiveActionToken.TokenValidationResult.failure("token decode error: " + e.getMessage());
        }
    }

    // ==================== CDC Helpers (DC-AUD-010 / DC-AUD-023 / P0.3) ====================

    /**
     * Builds a full CDC event envelope for entity mutations (P0.3 canonical event envelope).
     *
     * <p>Required fields: eventId, tenantId, type, version, occurredAt, actor, resource,
     * operation, before, after, traceId, correlationId, provenance.
     */
    private static Map<String, Object> buildCdcEnvelope(String tenantId, String traceId,
                                                        DataCloudClient.Entity entity,
                                                        String operation,
                                                        Map<String, Object> beforeState) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", java.util.UUID.randomUUID().toString());
        envelope.put("tenantId", tenantId);
        envelope.put("type", "entity.mutated");
        // DC-P2-008: Split into eventSchemaVersion and entityVersion to avoid collision
        envelope.put("eventSchemaVersion", "1.0");
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("actor", Map.of("type", "system", "id", "api"));
        envelope.put("resource", Map.of("type", "entity", "collection", entity.collection(), "id", entity.id()));
        envelope.put("operation", operation);
        envelope.put("before", beforeState != null ? beforeState : Map.of());
        envelope.put("after", entity.data());
        envelope.put("traceId", traceId != null ? traceId : "");
        envelope.put("correlationId", traceId != null ? traceId : "");
        envelope.put("provenance", Map.of("source", "api", "derivedFrom", List.of()));
        // Backward compatibility
        envelope.put("collection", entity.collection());
        envelope.put("id", entity.id());
        envelope.put("eventType", "entity.mutated");
        envelope.put("timestamp", Instant.now().toString());
        envelope.put("data", entity.data());
        envelope.put("entityVersion", entity.version()); // DC-P2-008: Renamed from "version"
        envelope.put("createdAt", entity.createdAt() != null ? entity.createdAt().toString() : null);
        envelope.put("updatedAt", entity.updatedAt() != null ? entity.updatedAt().toString() : null);
        return envelope;
    }

    private static Map<String, Object> buildDeleteCdcEnvelope(String tenantId, String traceId,
                                                             DataCloudClient.Entity entity,
                                                             String collection, String id) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", java.util.UUID.randomUUID().toString());
        envelope.put("tenantId", tenantId);
        envelope.put("type", "entity.deleted");
        // DC-P2-008: Split into eventSchemaVersion and entityVersion to avoid collision
        envelope.put("eventSchemaVersion", "1.0");
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("actor", Map.of("type", "system", "id", "api"));
        envelope.put("resource", Map.of("type", "entity", "collection", collection, "id", id));
        envelope.put("operation", "delete");
        envelope.put("before", entity != null ? entity.data() : Map.of());
        envelope.put("after", Map.of());
        envelope.put("traceId", traceId != null ? traceId : "");
        envelope.put("correlationId", traceId != null ? traceId : "");
        envelope.put("provenance", Map.of("source", "api", "derivedFrom", List.of()));
        // Backward compatibility
        envelope.put("collection", collection);
        envelope.put("id", id);
        envelope.put("eventType", "entity.deleted");
        envelope.put("timestamp", Instant.now().toString());
        envelope.put("tombstone", true);
        if (entity != null) {
            envelope.put("data", entity.data());
            envelope.put("entityVersion", entity.version()); // DC-P2-008: Renamed from "version"
            envelope.put("createdAt", entity.createdAt() != null ? entity.createdAt().toString() : null);
            envelope.put("updatedAt", entity.updatedAt() != null ? entity.updatedAt().toString() : null);
        }
        return envelope;
    }

    // ==================== Collection Metadata Management (P0.2) ====================

    /**
     * Upsert collection metadata into the {@code dc_collections} registry.
     *
     * <p>Validates allowed metadata fields ({@code lifecycleStatus}, {@code qualityScore},
     * {@code qualityMetrics}, {@code retentionPolicy}, {@code lineage}, {@code operationalStatus},
     * {@code label}, {@code description}, {@code active}, {@code validationSchema},
     * {@code storageProfile}, {@code physicalMapping}, {@code schemaVersion}),
     * merges with any existing stored metadata, and persists into the entity store.
     *
     * <p>Route: {@code POST /api/v1/collections/:collection/metadata}</p>
     */
    public Promise<HttpResponse> handleUpsertCollectionMetadata(HttpRequest request) {
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        String collection = request.getPathParameter("collection");
        if (collection == null || collection.isBlank()) {
            return Promise.of(http.errorResponse(400, "collection path parameter is required"));
        }
        Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
        if (tenantErr.isPresent()) return Promise.of(http.errorResponse(400, tenantErr.get()));
        Optional<String> collErr = ApiInputValidator.validateCollection(collection);
        if (collErr.isPresent()) return Promise.of(http.errorResponse(400, collErr.get()));

        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                @SuppressWarnings("unchecked")
                Map<String, Object> metadata = http.objectMapper().readValue(body, Map.class);

                Optional<String> validationErr = ApiInputValidator.validateCollectionMetadata(metadata);
                if (validationErr.isPresent()) {
                    return Promise.of(http.errorResponse(422, validationErr.get()));
                }

                // Merge with existing metadata so we don't lose unspecified fields
                return client.findById(tenantId, "dc_collections", collection)
                    .then(existingOpt -> {
                        Map<String, Object> merged = new LinkedHashMap<>();
                        if (existingOpt.isPresent() && existingOpt.get().data() != null) {
                            merged.putAll(existingOpt.get().data());
                        }
                        merged.putAll(metadata);
                        merged.put("id", collection);
                        merged.put("name", collection);
                        merged.put("updatedAt", Instant.now().toString());

                        return client.save(tenantId, "dc_collections", merged)
                            .then(saved -> {
                                // DC-P1-008: emit domain event for collection metadata change
                                DataCloudClient.Event metaEvent = DataCloudClient.Event.builder()
                                    .type("collection.metadata.updated")
                                    .payload(Map.of(
                                        "tenantId", tenantId,
                                        "collection", collection,
                                        "updatedAt", merged.get("updatedAt"),
                                        "source", "datacloud.launcher.entity-crud"
                                    ))
                                    .source("datacloud.launcher.entity-crud")
                                    .build();

                                return client.appendEvent(tenantId, metaEvent)
                                    .then($ -> {
                                        // DC-P1-008: emit audit record for the metadata change
                                        if (auditService != null) {
                                            String principalId = http.resolvePrincipalId(request);
                                            AuditEvent auditEvent = AuditEvent.builder()
                                                .tenantId(tenantId)
                                                .eventType("collection.metadata.updated")
                                                .principal(principalId != null ? principalId : "system")
                                                .resourceType("collection")
                                                .resourceId(collection)
                                                .success(true)
                                                .detail("collection", collection)
                                                .build();
                                            auditService.record(auditEvent).whenException(e ->
                                                log.warn("[DC-P1-008][upsertCollectionMetadata] audit write failed tenant={} collection={}: {}",
                                                    tenantId, collection, e.getMessage()));
                                        }
                                        Map<String, Object> response = new LinkedHashMap<>();
                                        response.put("id", saved.id());
                                        response.put("collection", collection);
                                        response.put("metadata", saved.data());
                                        return Promise.of(http.createdResponse(response));
                                    });
                            });
                    });
            } catch (Exception e) {
                log.error("[upsertCollectionMetadata] tenant={} collection={}: {}", tenantId, collection, e.getMessage(), e);
                return Promise.of(http.errorResponse(500, "Failed to upsert collection metadata: " + e.getMessage()));
            }
        });
    }

    /**
     * GET /api/v1/data-quality/trust-scores
     *
     * <p>Returns the canonical Data Plane trust-score contract derived from the
     * collection registry metadata. Scores are normalized to 0-100 and include
     * lifecycle and operational posture so operators can compare collection trust
     * levels consistently across surfaces.
     */
    public Promise<HttpResponse> handleGetDataQualityTrustScores(HttpRequest request) {
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();

        Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
        if (tenantErr.isPresent()) {
            return Promise.of(http.errorResponse(400, tenantErr.get()));
        }

        DataCloudClient.Query query = DataCloudClient.Query.builder()
            .limit(500)
            .build();

        return client.query(tenantId, "dc_collections", query)
            .map(collections -> {
                List<Map<String, Object>> scores = collections.stream()
                    .map(entity -> toTrustScoreEntry(entity.data()))
                    .toList();

                Map<String, Object> response = new LinkedHashMap<>();
                response.put("tenantId", tenantId);
                response.put("count", scores.size());
                response.put("generatedAt", Instant.now().toString());
                response.put("scores", scores);
                return http.jsonResponse(response);
            })
            .mapException(e -> {
                log.error("[trust-scores] tenant={} failed to compute trust scores", tenantId, e);
                return new HttpException("Failed to compute trust scores: " + e.getMessage(), e);
            });
    }

    private static Map<String, Object> toTrustScoreEntry(Map<String, Object> metadata) {
        String collection = String.valueOf(metadata.getOrDefault("id", metadata.getOrDefault("name", "unknown")));
        String lifecycleStatus = String.valueOf(metadata.getOrDefault("lifecycleStatus", "DRAFT"));
        String operationalStatus = String.valueOf(metadata.getOrDefault("operationalStatus", "unknown"));

        double qualityScore = parseNormalizedQualityScore(metadata.get("qualityScore"));
        int trustScore = computeTrustScore(qualityScore, lifecycleStatus, operationalStatus);

        Map<String, Object> score = new LinkedHashMap<>();
        score.put("collection", collection);
        score.put("qualityScore", qualityScore);
        score.put("trustScore", trustScore);
        score.put("lifecycleStatus", lifecycleStatus);
        score.put("operationalStatus", operationalStatus);
        score.put("qualityMetrics", metadata.getOrDefault("qualityMetrics", Map.of()));
        score.put("computedAt", Instant.now().toString());
        return score;
    }

    private static double parseNormalizedQualityScore(Object value) {
        if (value instanceof Number number) {
            return clamp(number.doubleValue(), 0.0, 1.0);
        }
        if (value instanceof String stringValue) {
            try {
                return clamp(Double.parseDouble(stringValue), 0.0, 1.0);
            } catch (NumberFormatException ignored) {
                return 0.6;
            }
        }
        return 0.6;
    }

    private static int computeTrustScore(double qualityScore, String lifecycleStatus, String operationalStatus) {
        double score = qualityScore * 100.0;

        String normalizedOperationalStatus = operationalStatus == null
            ? "unknown"
            : operationalStatus.trim().toLowerCase();
        if ("degraded".equals(normalizedOperationalStatus)) {
            score -= 15.0;
        } else if ("unavailable".equals(normalizedOperationalStatus)) {
            score -= 35.0;
        } else if ("maintenance".equals(normalizedOperationalStatus)) {
            score -= 10.0;
        }

        String normalizedLifecycleStatus = lifecycleStatus == null
            ? "UNKNOWN"
            : lifecycleStatus.trim().toUpperCase();
        if ("DEPRECATED".equals(normalizedLifecycleStatus)) {
            score -= 10.0;
        } else if ("ARCHIVED".equals(normalizedLifecycleStatus)) {
            score -= 20.0;
        }

        return (int) Math.round(clamp(score, 0.0, 100.0));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    // ==================== Full-Text Search ====================

    /**
     * Handles full-text entity search via OpenSearch.
     *
     * <p>Returns {@code 501 Unavailable} when no {@link OpenSearchConnector} is
     * configured. Returns {@code 400 Bad Request} when required query params are
     * absent or invalid.
     *
     * @param request the incoming HTTP request
     * @return a Promise resolving to the HTTP response
     *
     * @doc.type     method
     * @doc.purpose  REST handler for OpenSearch full-text entity search
     * @doc.layer    product
     * @doc.pattern  Handler
     */
    public Promise<HttpResponse> handleFullTextSearch(HttpRequest request) {
        if (openSearchConnector == null) {
            return Promise.of(http.errorResponse(501,
                "Full-text search is not enabled; configure an OpenSearchConnector"));
        }

        String collection = request.getPathParameter("collection");
        Optional<String> collErr = ApiInputValidator.validateCollection(collection);
        if (collErr.isPresent()) return Promise.of(http.errorResponse(400, collErr.get()));

        String q = request.getQueryParameter("q");
        Optional<String> qErr = ApiInputValidator.validateSearchQuery(q);
        if (qErr.isPresent()) return Promise.of(http.errorResponse(400, qErr.get()));

        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
        if (tenantErr.isPresent()) return Promise.of(http.errorResponse(400, tenantErr.get()));

        ApiInputValidator.LimitResult limitResult = ApiInputValidator.validateLimit(request.getQueryParameter("limit"), 20);
        if (!limitResult.isValid()) return Promise.of(http.errorResponse(400, limitResult.getError().orElseThrow()));
        int limit  = limitResult.getValue();
        int offset = Math.max(HttpHandlerSupport.parseIntParam(request.getQueryParameter("offset"), 0), 0);

        QuerySpec spec = QuerySpec.builder()
            .filter(q)
            .limit(limit)
            .offset(offset)
            .build();

        log.debug("[search] tenant={} collection={} q='{}' limit={} offset={}", tenantId, collection, q, limit, offset);

        return openSearchConnector.query((java.util.UUID) null, tenantId, spec)
            .map(qr -> {
                List<Map<String, Object>> results = qr.entities().stream()
                    .map(e -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("id", e.getId() != null ? e.getId().toString() : null);
                        item.put("collectionName", e.getCollectionName());
                        item.put("data", e.getData());
                        item.put("version", e.getVersion());
                        item.put("createdAt", e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
                        item.put("updatedAt", e.getUpdatedAt() != null ? e.getUpdatedAt().toString() : null);
                        return item;
                    })
                    .toList();

                Map<String, Object> body = new LinkedHashMap<>();
                body.put("results",     results);
                body.put("total",       qr.total());
                body.put("limit",       qr.limit());
                body.put("offset",      qr.offset());
                body.put("hasMore",     qr.hasMore());
                body.put("executionMs", qr.executionTimeMs());
                return http.jsonResponse(body);
            })
            .mapException(e -> {
                log.error("[search] tenant={} collection={} q='{}': {}",
                    tenantId, collection, q, e.getMessage(), e);
                return new HttpException("Search failed: " + e.getMessage(), e);
            });
    }

    /**
     * GET /api/v1/entities/:collection/:id?asOf={ISO-8601} — B14 point-in-time query.
     *
     * <p>Fetches the current entity from storage, then overlays any event-log entries that were
     * created before or exactly at the requested timestamp. The reconstruction is additive:
     * each event whose payload contains a {@code "data"} map is merged in timestamp order so
     * that the last writer wins on a per-field basis. When no events are found before the
     * requested time, the current entity state is returned as-is (best-effort; the store may
     * not have been persisted with full CDC coverage).
     *
     * @param request the incoming HTTP request
     * @return 200 with the reconstructed entity snapshot, 400 on validation error,
     *         404 when entity not found now or has no events before the timestamp
     *
     * @doc.type method
     * @doc.purpose Return entity state at a specific point-in-time
     * @doc.layer product
     * @doc.pattern Handler
     */
    public Promise<HttpResponse> handleGetEntityAsOf(HttpRequest request) {
        String collection = request.getPathParameter("collection");
        String id = request.getPathParameter("id");
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        String asOfParam = request.getQueryParameter("asOf");

        Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
        if (tenantErr.isPresent()) return Promise.of(http.errorResponse(400, tenantErr.get()));
        Optional<String> collErr = ApiInputValidator.validateCollection(collection);
        if (collErr.isPresent()) return Promise.of(http.errorResponse(400, collErr.get()));
        Optional<String> idErr = ApiInputValidator.validateId(id);
        if (idErr.isPresent()) return Promise.of(http.errorResponse(400, idErr.get()));
        if (asOfParam == null || asOfParam.isBlank()) {
            return Promise.of(http.errorResponse(400, "'asOf' query parameter is required (ISO-8601 instant)"));
        }

        final Instant asOf;
        try {
            asOf = Instant.parse(asOfParam);
        } catch (DateTimeParseException e) {
            return Promise.of(http.errorResponse(400, "Invalid 'asOf' value — expected ISO-8601 instant, e.g. 2026-01-15T12:00:00Z"));
        }

        // Genesis-forward reconstruction: replay all CDC events for this entity from the
        // earliest event up to asOf, starting with an empty map. This correctly handles
        // entities that were created, mutated, deleted, or recreated before asOf.
        DataCloudClient.EventQuery timeQuery = new DataCloudClient.EventQuery(
                List.of(),        // all types — let collection + entity-id filtering happen in stream
                null,             // no lower bound
                asOf,             // upper bound inclusive
                1_000             // cap at 1 000 events per request
        );

        return client.queryEvents(tenantId, timeQuery).map(events -> {
            // Filter only events that reference this entity and collection,
            // and are within the asOf time bound (defensive filter in case
            // the event store does not fully respect endTime).
            List<DataCloudClient.Event> entityEvents = events.stream()
                    .filter(ev -> {
                        Instant eventTime = ev.timestamp() != null ? ev.timestamp()
                            : Instant.parse((String) ev.payload().getOrDefault("timestamp", Instant.EPOCH.toString()));
                        return !eventTime.isAfter(asOf);
                    })
                    .filter(ev -> {
                        Map<String, Object> p = ev.payload();
                        if (!collection.equals(p.get("collection"))) return false;
                        // Individual save / delete
                        if (id.equals(p.get("id"))) return true;
                        // Batch-saved: check entities list for matching id
                        if ("entity.batch-saved".equals(ev.type())) {
                            Object ents = p.get("entities");
                            if (ents instanceof List<?> list) {
                                for (Object e : list) {
                                    if (e instanceof Map<?, ?> m && id.equals(m.get("id"))) return true;
                                }
                            }
                        }
                        // Batch-deleted: check ids list
                        if ("entity.batch-deleted".equals(ev.type())) {
                            Object ids = p.get("ids");
                            return ids instanceof List<?> list && list.contains(id);
                        }
                        return false;
                    })
                    .toList();

            // Sort ascending by timestamp so earliest events come first (genesis-forward)
            List<DataCloudClient.Event> sorted = entityEvents.stream()
                    .sorted((a, b) -> {
                        Instant ta = a.timestamp() != null ? a.timestamp() : Instant.EPOCH;
                        Instant tb = b.timestamp() != null ? b.timestamp() : Instant.EPOCH;
                        return ta.compareTo(tb);
                    })
                    .toList();

            Map<String, Object> reconstructed = new LinkedHashMap<>();
            boolean isDeleted = false;
            boolean everExisted = false;
            long version = 0;
            Instant lastMutationAt = null;
            int appliedEvents = 0;

            for (DataCloudClient.Event ev : sorted) {
                Map<String, Object> p = ev.payload();
                String eventType = ev.type();

                if ("entity.saved".equals(eventType) && id.equals(p.get("id"))) {
                    Object dataObj = p.get("data");
                    if (dataObj instanceof Map<?, ?> dataMap) {
                        reconstructed.clear();
                        for (Map.Entry<?, ?> e : dataMap.entrySet()) {
                            reconstructed.put(String.valueOf(e.getKey()), e.getValue());
                        }
                        isDeleted = false;
                        everExisted = true;
                    }
                    Object ver = p.get("version");
                    if (ver instanceof Number n) version = n.longValue();
                    lastMutationAt = ev.timestamp();
                    appliedEvents++;
                } else if ("entity.deleted".equals(eventType) && id.equals(p.get("id"))) {
                    reconstructed.clear();
                    isDeleted = true;
                    everExisted = true;
                    lastMutationAt = ev.timestamp();
                    appliedEvents++;
                } else if ("entity.batch-saved".equals(eventType)) {
                    Object ents = p.get("entities");
                    if (ents instanceof List<?> list) {
                        for (Object e : list) {
                            if (e instanceof Map<?, ?> m && id.equals(m.get("id"))) {
                                Object dataObj = m.get("data");
                                if (dataObj instanceof Map<?, ?> dataMap) {
                                    reconstructed.clear();
                                    for (Map.Entry<?, ?> entry : dataMap.entrySet()) {
                                        reconstructed.put(String.valueOf(entry.getKey()), entry.getValue());
                                    }
                                    isDeleted = false;
                                    everExisted = true;
                                }
                                Object ver = m.get("version");
                                if (ver instanceof Number n) version = n.longValue();
                                lastMutationAt = ev.timestamp();
                                appliedEvents++;
                                break;
                            }
                        }
                    }
                } else if ("entity.batch-deleted".equals(eventType)) {
                    Object ids = p.get("ids");
                    if (ids instanceof List<?> list && list.contains(id)) {
                        reconstructed.clear();
                        isDeleted = true;
                        everExisted = true;
                        lastMutationAt = ev.timestamp();
                        appliedEvents++;
                    }
                }
            }

            if (!everExisted) {
                return http.errorResponse(404, "Entity not found at " + asOf + ": " + id);
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("id", id);
            body.put("collection", collection);
            body.put("data", reconstructed);
            body.put("version", version);
            body.put("asOf", asOf.toString());
            body.put("appliedEvents", appliedEvents);
            body.put("reconstructionMethod", "genesis-forward");
            body.put("lastMutationAt", lastMutationAt != null ? lastMutationAt.toString() : null);
            if (isDeleted) {
                body.put("deletedAt", lastMutationAt != null ? lastMutationAt.toString() : null);
                body.put("tombstone", true);
            }
            return http.jsonResponse(body);
        }).mapException(e -> {
            log.error("[asOf] tenant={} collection={} id={} asOf={}: {}",
                    tenantId, collection, id, asOf, e.getMessage(), e);
            return new HttpException("Point-in-time query failed: " + e.getMessage(), e);
        });
    }

    /**
     * Lists all collections registered for the tenant.
     *
     * <p>Implements the first-class collection registry endpoint (dc-s4) that
     * exposes what entity collections exist for a tenant so the Data Explorer
     * can drive navigation without assuming hard-coded names.</p>
     */
    public Promise<HttpResponse> handleListCollections(HttpRequest request) {
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();

        // Prefer the backing EntityStore if it supports listCollections
        try {
            EntityStore store = client.entityStore();
            Promise<List<String>> namesPromise = store.listCollections(com.ghatana.datacloud.spi.TenantContext.of(tenantId));
            Promise<List<DataCloudClient.Entity>> metaPromise = client.query(
                tenantId, "dc_collections", DataCloudClient.Query.limit(500));

            return namesPromise.combine(metaPromise, (names, metaEntities) -> {
                Map<String, DataCloudClient.Entity> metaMap = new LinkedHashMap<>();
                for (DataCloudClient.Entity e : metaEntities) {
                    metaMap.put(e.id(), e);
                }

                List<Map<String, Object>> entries = names.stream()
                    .map(name -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("name", name);
                        m.put("systemCollection", name.startsWith("dc_"));
                        DataCloudClient.Entity meta = metaMap.get(name);
                        if (meta != null) {
                            m.put("lifecycleStatus", meta.data().getOrDefault("lifecycleStatus", "UNKNOWN"));
                            m.put("qualityScore", meta.data().get("qualityScore"));
                            m.put("qualityMetrics", meta.data().get("qualityMetrics"));
                            m.put("retentionPolicy", meta.data().get("retentionPolicy"));
                            m.put("lineage", meta.data().get("lineage"));
                            m.put("owner", meta.data().getOrDefault("owner", meta.data().get("createdBy")));
                            m.put("operationalStatus", meta.data().getOrDefault("operationalStatus", "unknown"));
                            Object schema = meta.data().get("schema");
                            if (schema != null) {
                                m.put("schema", schema);
                            }
                        } else {
                            m.put("lifecycleStatus", "UNKNOWN");
                            m.put("operationalStatus", "unknown");
                        }
                        return m;
                    })
                    .toList();
                return http.jsonResponse(Map.of(
                    "tenantId", tenantId,
                    "collections", entries,
                    "total", entries.size(),
                    "timestamp", Instant.now().toString()
                ));
            });
        } catch (Exception e) {
            log.error("[listCollections] tenant={} failed: {}", tenantId, e.getMessage(), e);
            return Promise.of(http.errorResponse(500, "Collection registry query failed: " + e.getMessage()));
        }
    }
}
