# Detailed Implementation Plan
## Data Cloud & AEP Hardening Roadmap

**Date:** April 17, 2026  
**Status:** Post-Audit Action Plan  
**Classification:** Engineering Implementation Guide

---

## Executive Summary

This document provides granular, task-by-task implementation guidance for hardening Data Cloud and AEP into production-ready systems. Each task includes:
- Specific files to modify
- Implementation steps
- Acceptance criteria
- Estimated effort
- Dependencies

**Continuation Status (2026-04-17):** Remaining-task execution in this repo has also included a live runtime truthfulness sweep in `products/audio-video/modules/intelligence/ai-voice/apps/desktop`, because several user-visible desktop command paths were still returning fabricated success or placeholder metadata outside the original Data Cloud/AEP plan scope. Completed hardening in that slice now includes fail-closed training/conversion/preview behavior, real cloned-voice discovery from persisted metadata, truthful model availability state, computed clone similarity scoring, and removal of fake pending/quality/model-library status paths where no real runtime metric existed.

---

## Phase 0: Correctness Blockers + Fake Completeness + Hardening Gaps
**Timeline:** Weeks 1-4  
**Goal:** Eliminate data loss risks, security vulnerabilities, and false product claims

---

### Task 0.1: AEP Durable Agent Registry
**Priority:** P0 - Critical  
**Owner:** AEP Backend Team  
**Effort:** 3-4 days
**Implementation Status (2026-04-17):** Code complete in current repo shape. Launcher now uses the existing `products:data-cloud:agent-registry` durable registry implementation with production fail-closed startup. This pass added restart-equivalent durability coverage in `AepLauncherTest`: persisted registry metadata survives registry recreation against the same Data Cloud backend, while live `TypedAgent` instances remain process-local and must re-register by design.

#### Problem
`AepLauncher` uses `InMemoryAgentRegistry` which loses all agent registrations on restart.

#### Files to Modify
1. `@/Users/samujjwal/Development/ghatana/products/aep/server/src/main/java/com/ghatana/aep/server/AepLauncher.java:181`
2. `@/Users/samujjwal/Development/ghatana/products/aep/aep-registry/src/main/java/com/ghatana/agent/registry/InMemoryAgentRegistry.java`
3. `@/Users/samujjwal/Development/ghatana/products/aep/aep-registry/src/main/java/com/ghatana/agent/registry/AgentRegistry.java` (interface)
4. Create: `@/Users/samujjwal/Development/ghatana/products/aep/aep-registry/src/main/java/com/ghatana/agent/registry/DataCloudAgentRegistry.java`

#### Implementation Steps

**Step 1: Define Data Cloud Agent Store Contract**
```java
// In data-cloud/spi (if not exists)
public interface AgentStore {
    Promise<AgentRecord> save(String tenantId, AgentRecord agent);
    Promise<Optional<AgentRecord>> findById(String tenantId, String agentId);
    Promise<List<AgentRecord>> listByTenant(String tenantId);
    Promise<Void> delete(String tenantId, String agentId);
}
```

**Step 2: Implement DataCloudAgentRegistry**
```java
package com.ghatana.agent.registry;

import com.ghatana.datacloud.DataCloudClient;
import io.activej.promise.Promise;
import java.util.*;

public class DataCloudAgentRegistry implements AgentRegistry {
    private final DataCloudClient dataCloud;
    private static final String COLLECTION = "aep_agents";
    
    public DataCloudAgentRegistry(DataCloudClient dataCloud) {
        this.dataCloud = Objects.requireNonNull(dataCloud);
    }
    
    @Override
    public Promise<AgentRegistration> register(String tenantId, AgentDefinition definition) {
        AgentRecord record = AgentRecord.from(definition);
        return dataCloud.save(tenantId, COLLECTION, record.toMap())
            .map(saved -> AgentRegistration.from(saved));
    }
    
    @Override
    public Promise<List<AgentRegistration>> listAgents(String tenantId) {
        return dataCloud.query(tenantId, COLLECTION, Query.builder().build())
            .map(entities -> entities.stream()
                .map(AgentRegistration::from)
                .toList());
    }
    // ... remaining methods
}
```

**Step 3: Update AepLauncher to Use Durable Registry**
```java
// Replace line 181 in AepLauncher.java
// OLD: AepGrpcServer grpcServer = new AepGrpcServer(new InMemoryAgentRegistry());
// NEW:
DataCloudClient agentDataCloud = createAgentDataCloudClient();
if (agentDataCloud == null) {
    throw new IllegalStateException("Agent registry requires Data Cloud connection. " +
        "Set DATACLOUD_URL or run with --embedded flag.");
}
AepGrpcServer grpcServer = new AepGrpcServer(
    new DataCloudAgentRegistry(agentDataCloud));
```

**Step 4: Add Fail-Closed Configuration**
```java
// In AepLauncher.createAgentDataCloudClient()
private static DataCloudClient createAgentDataCloudClient() {
    String dataCloudUrl = System.getenv("DATACLOUD_URL");
    if (dataCloudUrl == null && isProduction()) {
        throw new IllegalStateException(
            "DATACLOUD_URL required in production mode");
    }
    // ... existing logic
}
```

#### Acceptance Criteria
- [x] Durable agent registry metadata survives registry recreation on the same Data Cloud backend
- [x] Integration test: register agent metadata → recreate registry → persisted metadata remains queryable via registry stats while live agent resolution stays process-local by design
- [x] Fail-closed: AEP server refuses to start without Data Cloud connection in production
- [ ] Performance: agent list query < 100ms for 1000 agents

#### Dependencies
- Task 0.3 (Durable Data Cloud client)
- Data Cloud entity store operational

---

### Task 0.2: AEP Durable Human Review Queue
**Priority:** P0 - Critical  
**Owner:** AEP Backend Team  
**Effort:** 4-5 days
**Implementation Status (2026-04-17):** Code complete in current repo shape. Added `DataCloudHumanReviewQueue`, wired launcher bootstrap to prefer it when a Data Cloud client is available, verified pending review durability across queue/server restart, and added explicit `expiresAt` support with automatic transition of elapsed pending reviews to `EXPIRED` during queue reads and mutations.

#### Problem
`InMemoryHumanReviewQueue` loses all pending reviews on restart.

#### Files to Modify
1. `@/Users/samujjwal/Development/ghatana/products/aep/server/src/main/java/com/ghatana/aep/server/AepLauncher.java:204-227`
2. `@/Users/samujjwal/Development/ghatana/products/aep/aep-agent-runtime/src/main/java/com/ghatana/agent/learning/review/InMemoryHumanReviewQueue.java`
3. Create: `@/Users/samujjwal/Development/ghatana/products/aep/aep-agent-runtime/src/main/java/com/ghatana/agent/learning/review/DataCloudHumanReviewQueue.java`

#### Implementation Steps

**Step 1: Define Review Queue Schema**
```java
// Entity schema for Data Cloud
public class ReviewItemRecord {
    public static final String COLLECTION = "aep_review_queue";
    
    String reviewId;        // UUID
    String tenantId;        // Tenant isolation
    String skillId;         // Agent/skill reference
    String itemType;        // REVIEW | ESCALATION | APPROVAL
    String status;          // PENDING | APPROVED | REJECTED | EXPIRED
    double confidenceScore; // 0.0 - 1.0
    Map<String, Object> payload; // Review context
    Instant createdAt;
    Instant expiresAt;      // TTL support
    String assignedTo;      // User ID or null
}
```

**Step 2: Implement DataCloudHumanReviewQueue**
```java
public class DataCloudHumanReviewQueue implements HumanReviewQueue {
    private final DataCloudClient dataCloud;
    private final ReviewNotificationSpi notificationSpi;
    private static final String COLLECTION = "aep_review_queue";
    
    public DataCloudHumanReviewQueue(DataCloudClient dataCloud, 
                                      ReviewNotificationSpi notificationSpi) {
        this.dataCloud = Objects.requireNonNull(dataCloud);
        this.notificationSpi = notificationSpi;
    }
    
    @Override
    public Promise<String> enqueue(String tenantId, ReviewItem item) {
        String reviewId = UUID.randomUUID().toString();
        Map<String, Object> record = Map.of(
            "reviewId", reviewId,
            "tenantId", tenantId,
            "skillId", item.getSkillId(),
            "itemType", item.getItemType().name(),
            "status", "PENDING",
            "confidenceScore", item.getConfidenceScore(),
            "payload", item.getPayload(),
            "createdAt", Instant.now().toString(),
            "expiresAt", item.getExpiresAt().toString()
        );
        
        return dataCloud.save(tenantId, COLLECTION, record)
            .map(saved -> {
                notificationSpi.onItemEnqueued(item);
                return reviewId;
            });
    }
    
    @Override
    public Promise<List<ReviewItem>> pendingForTenant(String tenantId) {
        // Query with status = PENDING
        Query query = Query.builder()
            .filter(Filter.eq("status", "PENDING"))
            .sort(Sort.desc("createdAt"))
            .build();
            
        return dataCloud.query(tenantId, COLLECTION, query)
            .map(entities -> entities.stream()
                .map(this::toReviewItem)
                .toList());
    }
    
    @Override
    public Promise<Void> approve(String tenantId, String reviewId, String userId) {
        return updateStatus(tenantId, reviewId, "APPROVED", userId)
            .then(item -> {
                notificationSpi.onItemApproved(item);
                return Promise.complete();
            });
    }
    
    private Promise<ReviewItem> updateStatus(String tenantId, String reviewId, 
                                              String newStatus, String userId) {
        return dataCloud.findById(tenantId, COLLECTION, reviewId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.ofException(
                        new NotFoundException("Review not found: " + reviewId));
                }
                Map<String, Object> record = new HashMap<>(opt.get().data());
                record.put("status", newStatus);
                record.put("decidedBy", userId);
                record.put("decidedAt", Instant.now().toString());
                
                return dataCloud.save(tenantId, COLLECTION, record)
                    .map(saved -> toReviewItem(saved));
            });
    }
}
```

**Step 3: Update AepLauncher**
```java
// In createHumanReviewQueue() method
private static HumanReviewQueue createHumanReviewQueue(
        AtomicReference<AepHttpServer> httpServerRef) {
    
    DataCloudClient dataCloud = createAgentDataCloudClient();
    if (dataCloud == null && isProduction()) {
        throw new IllegalStateException(
            "HITL review queue requires Data Cloud in production");
    }
    
    ReviewNotificationSpi spi = createSseNotificationSpi(httpServerRef);
    
    if (dataCloud != null) {
        log.info("Creating DataCloudHumanReviewQueue with durable persistence");
        return new DataCloudHumanReviewQueue(dataCloud, spi);
    } else {
        log.warn("Creating InMemoryHumanReviewQueue for dev/testing only");
        return new InMemoryHumanReviewQueue(spi);
    }
}
```

**Step 4: Add Review Expiration Handling**
```java
// Current repo implementation performs expiry normalization inside the queue.
// When a pending review is read or acted on and expiresAt < now, the queue
// persists the item as EXPIRED before returning the result.
```

#### Acceptance Criteria
- [x] Pending reviews survive AEP restart
- [x] SSE notifications still work with durable queue
- [x] Approve/reject operations durable and auditable
- [x] Expired reviews auto-transition to EXPIRED status
- [ ] Performance: enqueue < 50ms, list pending < 100ms

---

### Task 0.3: AEP Durable Pipeline Repository
**Priority:** P0 - Critical  
**Owner:** AEP Backend Team  
**Effort:** 3-4 days
**Implementation Status (2026-04-17):** Core durability was already present in current repo shape via `products/aep/server/.../DataCloudPipelineStore` plus `AepHttpServer` wiring when Data Cloud is configured. This pass completed the missing Data Cloud-backed pipeline version metadata/snapshot persistence and added restart-level regression coverage for published version history.

#### Problem
`InMemoryPipelineRepository` loses all pipeline definitions on restart.

#### Files to Modify
1. `@/Users/samujjwal/Development/ghatana/products/aep/server/src/main/java/com/ghatana/aep/server/http/AepHttpServer.java:106`
2. `@/Users/samujjwal/Development/ghatana/products/aep/orchestrator/src/main/java/com/ghatana/pipeline/registry/repository/InMemoryPipelineRepository.java`
3. Create: `@/Users/samujjwal/Development/ghatana/products/aep/orchestrator/src/main/java/com/ghatana/pipeline/registry/repository/DataCloudPipelineRepository.java`

#### Implementation Steps

**Step 1: Define Pipeline Storage Schema**
```java
public class PipelineRecord {
    public static final String COLLECTION = "aep_pipelines";
    
    String pipelineId;
    String tenantId;
    String name;
    String description;
    String status; // DRAFT | ACTIVE | PAUSED | ARCHIVED
    List<Node> nodes;
    List<Edge> edges;
    Map<String, Object> config;
    String version; // For optimistic locking
    Instant createdAt;
    Instant updatedAt;
    String createdBy;
}
```

**Step 2: Implement DataCloudPipelineRepository**
```java
public class DataCloudPipelineRepository implements PipelineRepository {
    private final DataCloudClient dataCloud;
    private static final String COLLECTION = "aep_pipelines";
    
    @Override
    public Promise<PipelineRegistration> save(String tenantId, Pipeline pipeline) {
        String pipelineId = pipeline.getId() != null ? 
            pipeline.getId() : UUID.randomUUID().toString();
        
        Map<String, Object> record = pipelineToMap(pipeline, pipelineId);
        
        return dataCloud.save(tenantId, COLLECTION, record)
            .map(saved -> mapToRegistration(saved));
    }
    
    @Override
    public Promise<Optional<Pipeline>> findById(String tenantId, String pipelineId) {
        return dataCloud.findById(tenantId, COLLECTION, pipelineId)
            .map(opt -> opt.map(this::mapToPipeline));
    }
    
    @Override
    public Promise<List<Pipeline>> findByTenant(String tenantId) {
        return dataCloud.query(tenantId, COLLECTION, Query.all())
            .map(result -> result.entities().stream()
                .map(this::mapToPipeline)
                .toList());
    }
    
    @Override
    public Promise<Boolean> delete(String tenantId, String pipelineId) {
        return dataCloud.delete(tenantId, COLLECTION, pipelineId)
            .map(v -> true);
    }
}
```

**Step 3: Update AepHttpServer Constructor**
```java
// In full constructor (line ~283)
public AepHttpServer(AepEngine engine, int port,
                     @Nullable DataCloudClient agentDataCloud,
                     @Nullable HumanReviewQueue humanReviewQueue,
                     MetricsCollector metricsCollector,
                     // ... other params
                     ) {
    // ... 
    
    // Replace: this.pipelineRepository = new InMemoryPipelineRepository();
    // With:
    if (agentDataCloud != null) {
        this.pipelineRepository = new DataCloudPipelineRepository(agentDataCloud);
        this.durablePipelines = true;
    } else {
        log.warn("Using InMemoryPipelineRepository - pipelines will be lost on restart");
        this.pipelineRepository = new InMemoryPipelineRepository();
        this.durablePipelines = false;
    }
    
    // ... rest of initialization
}
```

**Step 4: Add Pipeline Migration Support**
```java
// For existing in-memory pipelines, provide export/import
public class PipelineMigrationUtil {
    public static Promise<Void> migrateToDataCloud(
            InMemoryPipelineRepository source,
            DataCloudPipelineRepository target,
            String tenantId) {
        return source.findByTenant(tenantId)
            .then(pipelines -> {
                Promise<?>[] saves = pipelines.stream()
                    .map(p -> target.save(tenantId, p))
                    .toArray(Promise[]::new);
                return Promise.all(saves);
            });
    }
}
```

#### Acceptance Criteria
- [x] Pipeline definitions survive AEP restart
- [x] Pipeline version metadata and immutable snapshots persist in Data Cloud mode
- [x] Integration test: create → restart → list/history → assert present
- [ ] Migration utility for existing in-memory pipelines
- [ ] Performance: save < 50ms, list < 100ms for 100 pipelines

---

### Task 0.4: Data Cloud Mandatory Tenant Enforcement
**Priority:** P0 - Critical  
**Owner:** Data Cloud Backend Team  
**Effort:** 2 days
**Implementation Status (2026-04-17):** Completed on the strict launcher HTTP boundary. This pass hardened `HttpHandlerSupport` so tenant IDs are sanitized and format-validated centrally, and added strict-mode enforcement in `RequestObservationFilter` so protected API routes return `401` for missing tenant context and `400` for malformed tenant IDs before handler execution. Dedicated regression tests now cover missing, malformed, and valid tenant flows.

#### Problem
Missing tenant context silently resolves to "default", enabling cross-tenant contamination.

#### Files to Modify
1. `@/Users/samujjwal/Development/ghatana/products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/HttpHandlerSupport.java:224`
2. `@/Users/samujjwal/Development/ghatana/products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudSecurityFilter.java`
3. Create test: `@/Users/samujjwal/Development/ghatana/products/data-cloud/launcher/src/test/java/com/ghatana/datacloud/launcher/http/TenantIsolationEnforcementTest.java`

#### Implementation Steps

**Step 1: Update HttpHandlerSupport.resolveTenantId**
```java
public String requireTenantIdOrFail(HttpRequest request) {
    // Check header first
    String tenantId = request.getHeader(X_TENANT_ID_HEADER);
    
    // Then check query parameter
    if (tenantId == null || tenantId.isBlank()) {
        tenantId = request.getQueryParameter(TENANT_ID_PARAM);
    }
    
    // STRICT: No silent fallback
    if (tenantId == null || tenantId.isBlank()) {
        log.warn("Rejecting request without explicit tenant identification");
        return null; // Caller must handle null and return 401/403
    }
    
    // Validate tenant format (optional but recommended)
    if (!isValidTenantId(tenantId)) {
        log.warn("Rejecting request with invalid tenant ID format: {}", tenantId);
        return null;
    }
    
    return sanitizeTenantId(tenantId);
}

private boolean isValidTenantId(String tenantId) {
    // Tenant IDs must be 3-64 alphanumeric with hyphens
    return tenantId.matches("^[a-zA-Z0-9-]{3,64}$");
}
```

**Step 2: Update All Handlers to Reject Missing Tenant**
```java
// Example: EntityCrudHandler.handleCreate
public Promise<HttpResponse> handleCreate(HttpRequest request) {
    String tenantId = http.requireTenantIdOrFail(request);
    if (tenantId == null) {
        return Promise.of(HttpResponse.ofCode(401)
            .withJson(Map.of(
                "error", "MISSING_TENANT",
                "message", "X-Tenant-ID header or tenantId parameter required"
            )));
    }
    // ... continue with tenantId guaranteed non-null
}
```

**Step 3: Add Tenant Validation Middleware**
```java
@Component
public class TenantValidationMiddleware implements HttpMiddleware {
    @Override
    public Promise<HttpResponse> handle(HttpRequest request, HttpHandler next) {
        // Skip for health endpoints
        if (isHealthEndpoint(request)) {
            return next.handle(request);
        }
        
        String tenantId = extractTenantId(request);
        if (tenantId == null) {
            return Promise.of(HttpResponse.ofCode(401)
                .withJson(Map.of(
                    "error", "UNAUTHORIZED",
                    "message", "Tenant identification required"
                )));
        }
        
        // Validate tenant exists (if tenant registry configured)
        return validateTenantExists(tenantId)
            .then(valid -> {
                if (!valid) {
                    return Promise.of(HttpResponse.ofCode(403)
                        .withJson(Map.of(
                            "error", "INVALID_TENANT",
                            "message", "Tenant not found or inactive"
                        )));
                }
                return next.handle(request);
            });
    }
}
```

**Step 4: Update Test Fixtures**
```java
// In all tests, explicitly set tenant header
@Test
void shouldCreateEntity() {
    HttpRequest request = HttpRequest.post("/api/v1/entities/test")
        .withHeader("X-Tenant-ID", "test-tenant-001") // Required
        .withJson(Map.of("name", "test"));
    
    // ... test logic
}
```

#### Acceptance Criteria
- [x] Requests without tenant ID return 401 UNAUTHORIZED in strict tenant resolution mode
- [x] Requests with invalid tenant format return 400 BAD REQUEST in strict tenant resolution mode
- [x] No code path allows "default" tenant fallback
- [ ] All existing tests updated with explicit tenant
- [x] New strict-boundary regression test: missing tenant → 401, malformed tenant → 400, valid tenant → success

---

### Task 0.5: Data Cloud Real Governance Purge
**Priority:** P0 - Critical  
**Owner:** Data Cloud Backend Team  
**Effort:** 5-6 days
**Implementation Status (2026-04-17):** Code complete in current repo shape. `DataLifecycleHandler.handlePurge(...)` already executes real deletion via `EntityStore.deleteBatch(...)`, emits governance/audit events, and has purge-token security coverage; this task should now be treated as verification/performance hardening, not missing feature work.

#### Problem
Purge endpoint returns `DRY_RUN_COMPLETE` with `estimatedRows = 0`; no actual deletion occurs.

#### Files to Modify
1. `@/Users/samujjwal/Development/ghatana/products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/DataLifecycleHandler.java:290-450`
2. `@/Users/samujjwal/Development/ghatana/products/data-cloud/spi/src/main/java/com/ghatana/datacloud/spi/EntityStore.java` (add batch delete)
3. Create: `@/Users/samujjwal/Development/ghatana/products/data-cloud/platform-launcher/src/main/java/com/ghatana/datacloud/governance/PurgeExecutor.java`

#### Implementation Steps

**Step 1: Extend EntityStore SPI for Batch Delete**
```java
// In EntityStore interface
/**
 * Batch delete entities by ID. Returns count deleted.
 */
Promise<Long> deleteBatch(TenantContext tenant, List<EntityId> ids);

/**
 * Query with time-based filter for retention policies.
 */
Promise<QueryResult> queryByUpdatedBefore(
    TenantContext tenant, 
    String collection,
    Instant cutoffDate,
    int limit
);
```

**Step 2: Implement Real Purge in DataLifecycleHandler**
```java
public Promise<HttpResponse> handlePurge(HttpRequest request) {
    // ... existing validation logic (token, tenant, etc.)
    
    if (dryRun) {
        return executeDryRun(tenantContext, collection, requestId, tenantId);
    }
    
    // REAL PURGE EXECUTION
    return executeRealPurge(tenantContext, collection, confirmationToken, 
                           requestId, tenantId);
}

private Promise<HttpResponse> executeRealPurge(
        TenantContext tenantContext,
        String collection,
        String confirmationToken,
        String requestId,
        String tenantId) {
    
    EntityStore entityStore = requireEntityStore();
    
    // Load retention policy to determine cutoff
    return loadRetentionPolicy(tenantContext, collection)
        .then(policy -> {
            Instant cutoff = calculateCutoff(policy);
            
            // Query expired entities
            return entityStore.queryByUpdatedBefore(
                tenantContext, collection, cutoff, PURGE_QUERY_LIMIT);
        })
        .then(queryResult -> {
            List<EntityId> expiredIds = queryResult.entities().stream()
                .map(Entity::id)
                .toList();
            
            if (expiredIds.isEmpty()) {
                return emitPurgeEvent(tenantContext, requestId, collection, 0)
                    .map(v -> buildEmptyPurgeResponse(collection, requestId));
            }
            
            // Execute batch delete
            return entityStore.deleteBatch(tenantContext, expiredIds)
                .then(deletedCount -> {
                    // Emit governance event for audit
                    return emitPurgeEvent(tenantContext, requestId, 
                                         collection, deletedCount)
                        .map(v -> buildPurgeResponse(collection, deletedCount, 
                                                    expiredIds, requestId));
                });
        });
}

private Promise<Void> emitPurgeEvent(TenantContext tenant, String requestId,
                                      String collection, long deletedCount) {
    Map<String, Object> event = Map.of(
        "eventType", "RETENTION_PURGE",
        "requestId", requestId,
        "collection", collection,
        "deletedCount", deletedCount,
        "timestamp", Instant.now().toString(),
        "confirmationTokenHash", hashToken(confirmationToken) // Don't store raw token
    );
    
    return eventLogStore.append(tenant, event);
}
```

**Step 3: Implement H2 Batch Delete**
```java
// In H2SovereignEntityStore
@Override
public Promise<Long> deleteBatch(TenantContext tenant, List<EntityId> ids) {
    if (ids.isEmpty()) {
        return Promise.of(0L);
    }
    
    return Promise.ofBlocking(() -> {
        String sql = "DELETE FROM entities WHERE tenant_id = ? AND id IN (" +
            String.join(",", Collections.nCopies(ids.size(), "?")) + ")";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, tenant.tenantId());
            for (int i = 0; i < ids.size(); i++) {
                stmt.setString(i + 2, ids.get(i).value());
            }
            
            long deleted = stmt.executeUpdate();
            
            // Emit tombstone events for audit
            for (EntityId id : ids) {
                emitTombstone(tenant, id);
            }
            
            return deleted;
        }
    });
}
```

**Step 4: Add Purge Verification**
```java
public class PurgeVerificationService {
    /**
     * Verify that purged entities are actually gone.
     */
    public Promise<Boolean> verifyPurge(TenantContext tenant, 
                                         String collection,
                                         List<EntityId> purgedIds) {
        Promise<?>[] checks = purgedIds.stream()
            .map(id -> entityStore.findById(tenant, id)
                .map(opt -> opt.isEmpty()))
            .toArray(Promise[]::new);
            
        return Promise.all(checks)
            .map(results -> Arrays.stream(results)
                .allMatch(r -> (Boolean) r));
    }
}
```

**Step 5: Implement Retention Policy Background Job**
```java
@Component
public class RetentionPolicyEnforcementJob {
    @Scheduled(fixedDelay = 3600000) // Hourly
    public void enforceRetentionPolicies() {
        // For each tenant with classified collections
        // Check for expired data
        // Auto-purge if policy requires (with notification)
    }
}
```

#### Acceptance Criteria
- [x] Purge with dryRun=false actually deletes entities
- [x] Deleted entities cannot be retrieved via GET
- [x] Purge audit event emitted with deleted count
- [x] Confirmation token hash stored in audit (not raw token)
- [ ] Batch delete handles 1000+ entities efficiently
- [ ] Tombstone records retained for compliance
- [ ] Integration test: create → classify short retention → age data → purge → verify gone

---

### Task 0.6: Data Cloud Real PII Redaction
**Priority:** P0 - Critical  
**Owner:** Data Cloud Backend Team  
**Effort:** 4-5 days
**Implementation Status (2026-04-17):** Code complete in current repo shape. `DataLifecycleHandler.handleRedact(...)` already mutates stored entity fields durably, launcher governance tests assert `[REDACTED]` persistence, and `PIIDetectionService` hashing/tokenization has been hardened to deterministic SHA-256-based output.

#### Problem
Redact endpoint returns `status: REDACTED` without actually mutating entity fields.

#### Files to Modify
1. `@/Users/samujjwal/Development/ghatana/products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/DataLifecycleHandler.java:321`
2. `@/Users/samujjwal/Development/ghatana/products/data-cloud/platform-launcher/src/main/java/com/ghatana/datacloud/storage/H2SovereignEntityStore.java`
3. Create: `@/Users/samujjwal/Development/ghatana/products/data-cloud/platform-api/src/main/java/com/ghatana/datacloud/governance/RedactionService.java`

#### Implementation Steps

**Step 1: Define Redaction Request Schema**
```java
public class RedactionRequest {
    String entityId;
    String collection;
    List<String> fieldsToRedact; // Specific fields or auto-detect PII
    boolean autoDetectPii;      // Use PII classification
    String redactionReason;     // GDPR Article 17, etc.
    String authorizedBy;        // User ID performing redaction
}

public class RedactionResult {
    String entityId;
    String collection;
    List<String> redactedFields;
    List<String> previousValuesHash; // Hashed for audit, not actual values
    Instant redactedAt;
    String redactedBy;
}
```

**Step 2: Implement RedactionService**
```java
@Service
public class RedactionService {
    private final EntityStore entityStore;
    private final AuditService auditService;
    private static final String REDACTED_VALUE = "[REDACTED]";
    
    public Promise<RedactionResult> redactEntity(
            TenantContext tenant,
            String collection,
            String entityId,
            List<String> fieldsToRedact,
            String reason,
            String authorizedBy) {
        
        // 1. Load entity
        return entityStore.findById(tenant, EntityId.of(entityId))
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.ofException(
                        new NotFoundException("Entity not found: " + entityId));
                }
                
                Entity entity = opt.get();
                
                // 2. Verify entity is in specified collection
                if (!entity.collection().equals(collection)) {
                    return Promise.ofException(
                        new ValidationException("Entity not in collection: " + collection));
                }
                
                // 3. Determine fields to redact
                List<String> actualFields = determineFieldsToRedact(
                    entity, fieldsToRedact);
                
                if (actualFields.isEmpty()) {
                    return Promise.ofException(
                        new ValidationException("No fields to redact"));
                }
                
                // 4. Create redacted version
                Map<String, Object> originalData = new HashMap<>(entity.data());
                Map<String, Object> redactedData = new HashMap<>(entity.data());
                List<String> previousHashes = new ArrayList<>();
                
                for (String field : actualFields) {
                    Object previousValue = redactedData.get(field);
                    if (previousValue != null) {
                        // Store hash of previous value for audit
                        previousHashes.add(hashValue(previousValue));
                        // Replace with redacted marker
                        redactedData.put(field, REDACTED_VALUE);
                    }
                }
                
                // 5. Save redacted entity
                Entity redactedEntity = Entity.builder()
                    .id(entity.id())
                    .collection(entity.collection())
                    .data(redactedData)
                    .metadata(entity.metadata().withUpdatedAt(Instant.now()))
                    .build();
                
                return entityStore.save(tenant, redactedEntity)
                    .then(saved -> {
                        // 6. Emit redaction event
                        RedactionResult result = new RedactionResult(
                            entityId,
                            collection,
                            actualFields,
                            previousHashes,
                            Instant.now(),
                            authorizedBy
                        );
                        
                        return emitRedactionEvent(tenant, result, reason)
                            .map(v -> result);
                    });
            });
    }
    
    private String hashValue(Object value) {
        // One-way hash for audit trail (not reversible)
        return Hashing.sha256().hashString(value.toString(), UTF_8).toString();
    }
}
```

**Step 3: Update DataLifecycleHandler**
```java
public Promise<HttpResponse> handleRedact(HttpRequest request) {
    String tenantId = http.requireTenantIdOrFail(request);
    if (tenantId == null) {
        return Promise.of(missingTenantResponse(requestId));
    }
    
    return request.loadBody(1024 * 8)
        .then(body -> {
            Map<String, Object> input = parseBody(body.getString(UTF_8));
            
            String collection = sanitise((String) input.get("collection"));
            String entityId = sanitise((String) input.get("entityId"));
            List<String> fields = (List<String>) input.get("fields");
            boolean autoDetect = Boolean.TRUE.equals(input.get("autoDetectPii"));
            String reason = (String) input.get("reason");
            String authorizedBy = (String) input.get("authorizedBy");
            
            // Validation
            if (collection.isBlank() || entityId.isBlank()) {
                return Promise.of(validationErrorResponse());
            }
            
            TenantContext tenant = TenantContext.of(tenantId);
            
            // Execute real redaction
            return redactionService.redactEntity(
                    tenant, collection, entityId, fields, reason, authorizedBy)
                .map(result -> {
                    Map<String, Object> response = Map.of(
                        "entityId", result.getEntityId(),
                        "collection", result.getCollection(),
                        "redactedFields", result.getRedactedFields(),
                        "redactedAt", result.getRedactedAt().toString(),
                        "redactedBy", result.getRedactedBy(),
                        "status", "REDACTED",
                        "verificationToken", generateVerificationToken(result)
                    );
                    
                    return http.envelopeResponse(
                        ApiResponse.success(response, tenantId, requestId), 
                        objectMapper);
                })
                .whenException(e -> handleRedactionError(e, tenantId, requestId));
        });
}
```

**Step 4: Implement PII Field Detection**
```java
public class PiiFieldDetector {
    private static final Set<String> PII_FIELD_PATTERNS = Set.of(
        "email", "e-mail", "mail",
        "phone", "mobile", "cell",
        "ssn", "social", "social_security",
        "passport", "license",
        "dob", "birthdate", "birth_date",
        "address", "street", "zip", "postal",
        "credit_card", "card_number", "ccv",
        "ip_address", "ip", "client_ip"
    );
    
    public List<String> detectPiiFields(Map<String, Object> data) {
        return data.keySet().stream()
            .filter(this::isPiiField)
            .toList();
    }
    
    private boolean isPiiField(String fieldName) {
        String normalized = fieldName.toLowerCase().replaceAll("[^a-z0-9]", "_");
        return PII_FIELD_PATTERNS.stream()
            .anyMatch(pattern -> normalized.contains(pattern));
    }
}
```

**Step 5: Add Redaction Verification**
```java
public class RedactionVerificationService {
    /**
     * Verify redaction was applied correctly.
     */
    public Promise<Boolean> verifyRedaction(TenantContext tenant,
                                            String collection,
                                            String entityId,
                                            List<String> expectedRedactedFields) {
        return entityStore.findById(tenant, EntityId.of(entityId))
            .map(opt -> {
                if (opt.isEmpty()) return false;
                Entity entity = opt.get();
                
                for (String field : expectedRedactedFields) {
                    Object value = entity.data().get(field);
                    if (!"[REDACTED]".equals(value)) {
                        return false;
                    }
                }
                return true;
            });
    }
}
```

#### Acceptance Criteria
- [x] Redaction actually replaces field values with [REDACTED]
- [x] Redacted values cannot be retrieved via API
- [x] Audit event contains hashed previous values (not plaintext)
- [ ] PII auto-detection identifies common PII fields
- [ ] Verification endpoint confirms redaction applied
- [x] Integration test: create entity with PII → redact → verify fields redacted → verify audit event

---

### Task 0.7: Data Cloud Auth Enforcement
**Priority:** P0 - Critical  
**Owner:** Data Cloud Backend Team  
**Effort:** 3 days
**Implementation Status (2026-04-17):** Complete in current repo shape. Launcher bootstrap fails closed when non-local profiles are started without auth, shared-secret JWT and API key flows are wired, this pass added canonical JWKS-backed JWT validation through `platform:java:security`, and `DataCloudSecurityFilter` now rejects JWT requests whose tenant claim conflicts with the requested tenant header/query parameter.

#### Problem
API key resolver is optional; production mode does not enforce authentication.

#### Files to Modify
1. `@/Users/samujjwal/Development/ghatana/products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/bootstrap/DataCloudHttpLauncherBootstrap.java`
2. `@/Users/samujjwal/Development/ghatana/products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudHttpServer.java:874`
3. `@/Users/samujjwal/Development/ghatana/products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudSecurityFilter.java`

#### Implementation Steps

**Step 1: Add Mandatory Auth Check in Bootstrap**
```java
public class DataCloudHttpLauncherBootstrap {
    public static void start(DataCloudClient client, Logger log) {
        DataCloudProfile profile = resolveProfile();
        
        ApiKeyResolver apiKeyResolver = configureApiKeyResolver();
        
        // MANDATORY in non-local profiles
        if (profile != DataCloudProfile.LOCAL && apiKeyResolver == null) {
            throw new IllegalStateException(
                "Authentication required in " + profile + " mode. " +
                "Configure DATACLOUD_API_KEY or DATACLOUD_AUTH_JWKS_URL");
        }
        
        DataCloudHttpServer server = new DataCloudHttpServer.Builder()
            .withApiKeyResolver(apiKeyResolver) // Now required
            .withProfile(profile)
            .build();
            
        server.start();
    }
    
    private static ApiKeyResolver configureApiKeyResolver() {
        // 1. Try JWT validation
        String jwksUrl = System.getenv("DATACLOUD_AUTH_JWKS_URL");
        if (jwksUrl != null) {
            return new JwtApiKeyResolver(jwksUrl);
        }
        
        // 2. Try shared secret
        String apiKey = System.getenv("DATACLOUD_API_KEY");
        if (apiKey != null) {
            return new SharedSecretResolver(apiKey);
        }
        
        // 3. No auth configured
        return null;
    }
}
```

**Step 2: Update Security Filter to Reject Unauthenticated**
```java
public class DataCloudSecurityFilter implements HttpServletFilter {
    @Override
    public Promise<HttpResponse> doFilter(HttpRequest request, HttpHandler next) {
        // Skip auth for health endpoints
        if (isPublicEndpoint(request)) {
            return next.handle(request);
        }
        
        String apiKey = extractApiKey(request);
        if (apiKey == null) {
            return Promise.of(HttpResponse.ofCode(401)
                .withJson(Map.of(
                    "error", "UNAUTHORIZED",
                    "message", "API key or JWT required"
                )));
        }
        
        return apiKeyResolver.validate(apiKey)
            .then(valid -> {
                if (!valid) {
                    return Promise.of(HttpResponse.ofCode(403)
                        .withJson(Map.of(
                            "error", "FORBIDDEN",
                            "message", "Invalid or expired credentials"
                        )));
                }
                
                // Extract tenant from token if not explicitly provided
                String tokenTenant = apiKeyResolver.extractTenant(apiKey);
                request = request.withHeader("X-Token-Tenant", tokenTenant);
                
                return next.handle(request);
            });
    }
    
    private String extractApiKey(HttpRequest request) {
        // Check Authorization header: Bearer <token>
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        
        // Check X-API-Key header
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey != null) return apiKey;
        
        // Check query parameter
        return request.getQueryParameter("apiKey");
    }
}
```

**Step 3: Implement JWT Validation**
```java
public class JwtApiKeyResolver implements ApiKeyResolver {
    private final JwkProvider jwkProvider;
    private final String expectedIssuer;
    private final String expectedAudience;
    
    @Override
    public Promise<Boolean> validate(String token) {
        return Promise.ofBlocking(() -> {
            try {
                DecodedJWT jwt = JWT.decode(token);
                Jwk jwk = jwkProvider.get(jwt.getKeyId());
                
                Algorithm algorithm = Algorithm.RSA256(
                    (RSAPublicKey) jwk.getPublicKey(), 
                    null);
                
                algorithm.verify(jwt);
                
                // Verify claims
                if (!expectedIssuer.equals(jwt.getIssuer())) {
                    return false;
                }
                if (!jwt.getAudience().contains(expectedAudience)) {
                    return false;
                }
                
                return !jwt.getExpiresAt().before(new Date());
            } catch (JWTVerificationException e) {
                return false;
            }
        });
    }
    
    @Override
    public String extractTenant(String token) {
        DecodedJWT jwt = JWT.decode(token);
        return jwt.getClaim("tenantId").asString();
    }
}
```

**Step 4: Add Configuration Validation**
```java
public class AuthConfigurationValidator {
    public void validate(DataCloudProfile profile) {
        if (profile == DataCloudProfile.LOCAL) {
            log.info("LOCAL profile: auth optional");
            return;
        }
        
        boolean hasAuth = System.getenv("DATACLOUD_AUTH_JWKS_URL") != null ||
                         System.getenv("DATACLOUD_API_KEY") != null;
                         
        if (!hasAuth) {
            throw new IllegalStateException(
                "Authentication not configured for profile: " + profile + "\n" +
                "Set one of: DATACLOUD_AUTH_JWKS_URL, DATACLOUD_API_KEY");
        }
    }
}
```

#### Acceptance Criteria
- [x] Non-local profiles fail startup without auth configuration
- [x] Requests without valid credentials return 401/403
- [x] JWT validation with JWKS endpoint works
- [x] Shared secret auth works for simple deployments
- [x] Tenant extracted from JWT and cross-checked with request
- [x] Health endpoints remain accessible without auth
- [x] Integration test: no auth → 401, valid JWT → success, invalid JWT → 401

---

### Task 0.8: Data Cloud Workflow Execution Backend
**Priority:** P0 - Critical  
**Owner:** Data Cloud Backend Team  
**Effort:** 6-7 days
**Implementation Status (2026-04-17):** Complete in current repo shape. `WorkflowExecutionHandler` already exposes execute/list/get/cancel/log endpoints backed by runtime plugin execution persistence, and this pass added explicit cancellation regression coverage so execute/list/detail/cancel flows plus restart persistence are now directly verified in launcher tests.

#### Problem
UI workflow execution is stubbed — `getExecutions()` returns empty list, `cancelExecution()` throws.

#### Files to Modify
1. Create: `@/Users/samujjwal/Development/ghatana/products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/workflow/WorkflowExecutionService.java`
2. Create: `@/Users/samujjwal/Development/ghatana/products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/WorkflowExecutionHandler.java`
3. Update: `@/Users/samujjwal/Development/ghatana/products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudHttpServer.java` (add routes)

#### Implementation Steps

**Step 1: Define Execution Data Model**
```java
public class WorkflowExecution {
    String executionId;      // UUID
    String workflowId;     // Parent workflow
    String tenantId;
    String status;         // PENDING | RUNNING | COMPLETED | FAILED | CANCELLED
    Map<String, Object> inputs;
    Map<String, Object> outputs;
    List<NodeExecution> nodeExecutions;
    Instant createdAt;
    Instant startedAt;
    Instant completedAt;
    String errorMessage;
    String triggeredBy;    // user ID or "schedule" or "event"
}

public class NodeExecution {
    String nodeId;
    String status;         // PENDING | RUNNING | COMPLETED | FAILED | SKIPPED
    Map<String, Object> inputs;
    Map<String, Object> outputs;
    Instant startedAt;
    Instant completedAt;
    String errorMessage;
}
```

**Step 2: Implement Execution Service**
```java
@Service
public class WorkflowExecutionService {
    private final DataCloudClient dataCloud;
    private final PipelineRegistry pipelineRegistry;
    private final WorkflowEngine workflowEngine;
    private static final String EXECUTION_COLLECTION = "workflow_executions";
    
    /**
     * Start a new workflow execution.
     */
    public Promise<WorkflowExecution> executeWorkflow(
            String tenantId, 
            String workflowId,
            Map<String, Object> inputs,
            String triggeredBy) {
        
        String executionId = UUID.randomUUID().toString();
        
        // 1. Load workflow definition
        return pipelineRegistry.findById(tenantId, workflowId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.ofException(
                        new NotFoundException("Workflow not found: " + workflowId));
                }
                
                Workflow workflow = opt.get();
                
                // 2. Create execution record
                WorkflowExecution execution = WorkflowExecution.builder()
                    .executionId(executionId)
                    .workflowId(workflowId)
                    .tenantId(tenantId)
                    .status("PENDING")
                    .inputs(inputs)
                    .nodeExecutions(initializeNodeExecutions(workflow.getNodes()))
                    .createdAt(Instant.now())
                    .triggeredBy(triggeredBy)
                    .build();
                
                // 3. Persist execution
                return saveExecution(execution)
                    .then(saved -> {
                        // 4. Submit to workflow engine
                        workflowEngine.submit(execution, workflow);
                        return Promise.of(saved);
                    });
            });
    }
    
    /**
     * List executions for a workflow.
     */
    public Promise<List<WorkflowExecution>> listExecutions(
            String tenantId, 
            String workflowId,
            int limit,
            int offset) {
        
        Query query = Query.builder()
            .filter(Filter.and(
                Filter.eq("tenantId", tenantId),
                Filter.eq("workflowId", workflowId)
            ))
            .sort(Sort.desc("createdAt"))
            .limit(limit)
            .offset(offset)
            .build();
            
        return dataCloud.query(tenantId, EXECUTION_COLLECTION, query)
            .map(result -> result.entities().stream()
                .map(this::mapToExecution)
                .toList());
    }
    
    /**
     * Get single execution.
     */
    public Promise<Optional<WorkflowExecution>> getExecution(
            String tenantId, 
            String workflowId,
            String executionId) {
        
        return dataCloud.findById(tenantId, EXECUTION_COLLECTION, executionId)
            .map(opt -> opt.map(this::mapToExecution));
    }
    
    /**
     * Cancel a running execution.
     */
    public Promise<WorkflowExecution> cancelExecution(
            String tenantId,
            String workflowId, 
            String executionId,
            String cancelledBy) {
        
        return getExecution(tenantId, workflowId, executionId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.ofException(
                        new NotFoundException("Execution not found: " + executionId));
                }
                
                WorkflowExecution execution = opt.get();
                
                // Can only cancel PENDING or RUNNING
                if (!Set.of("PENDING", "RUNNING").contains(execution.getStatus())) {
                    return Promise.ofException(
                        new IllegalStateException(
                            "Cannot cancel execution with status: " + execution.getStatus()));
                }
                
                // Signal cancellation to engine
                return workflowEngine.cancel(executionId)
                    .then(v -> {
                        // Update status
                        execution.setStatus("CANCELLED");
                        execution.setCompletedAt(Instant.now());
                        execution.setErrorMessage("Cancelled by user: " + cancelledBy);
                        
                        return saveExecution(execution);
                    });
            });
    }
    
    private Promise<WorkflowExecution> saveExecution(WorkflowExecution execution) {
        Map<String, Object> record = executionToMap(execution);
        return dataCloud.save(execution.getTenantId(), EXECUTION_COLLECTION, record)
            .map(saved -> mapToExecution(saved));
    }
}
```

**Step 3: Implement Workflow Engine**
```java
@Component
public class WorkflowEngine {
    private final ExecutorService executor;
    private final Map<String, ExecutionContext> activeExecutions = new ConcurrentHashMap<>();
    
    public void submit(WorkflowExecution execution, Workflow workflow) {
        ExecutionContext context = new ExecutionContext(execution, workflow);
        activeExecutions.put(execution.getExecutionId(), context);
        
        executor.submit(() -> runExecution(context));
    }
    
    private void runExecution(ExecutionContext context) {
        WorkflowExecution execution = context.getExecution();
        Workflow workflow = context.getWorkflow();
        
        try {
            updateStatus(execution, "RUNNING");
            
            // Execute nodes in topological order
            List<String> nodeOrder = topologicalSort(workflow.getNodes(), workflow.getEdges());
            
            for (String nodeId : nodeOrder) {
                if (context.isCancelled()) {
                    throw new CancellationException("Execution cancelled");
                }
                
                Node node = workflow.getNode(nodeId);
                executeNode(context, node);
            }
            
            updateStatus(execution, "COMPLETED");
            
        } catch (Exception e) {
            updateStatus(execution, "FAILED", e.getMessage());
        } finally {
            activeExecutions.remove(execution.getExecutionId());
        }
    }
    
    private void executeNode(ExecutionContext context, Node node) {
        NodeExecution nodeExec = context.getNodeExecution(node.getId());
        nodeExec.setStatus("RUNNING");
        nodeExec.setStartedAt(Instant.now());
        
        try {
            NodeExecutor executor = NodeExecutorFactory.getExecutor(node.getType());
            Map<String, Object> outputs = executor.execute(node, context.getInputsForNode(node));
            
            nodeExec.setOutputs(outputs);
            nodeExec.setStatus("COMPLETED");
            nodeExec.setCompletedAt(Instant.now());
            
        } catch (Exception e) {
            nodeExec.setStatus("FAILED");
            nodeExec.setErrorMessage(e.getMessage());
            throw e; // Propagate to fail workflow
        }
    }
    
    public Promise<Void> cancel(String executionId) {
        ExecutionContext context = activeExecutions.get(executionId);
        if (context != null) {
            context.cancel();
        }
        return Promise.complete();
    }
}
```

**Step 4: Create HTTP Handler**
```java
public class WorkflowExecutionHandler {
    private final WorkflowExecutionService executionService;
    private final HttpHandlerSupport http;
    private final ObjectMapper objectMapper;
    
    /**
     * POST /api/v1/pipelines/:id/execute
     */
    public Promise<HttpResponse> handleExecute(HttpRequest request, String pipelineId) {
        String tenantId = http.requireTenantIdOrFail(request);
        String requestId = generateRequestId();
        
        return request.loadBody()
            .then(body -> {
                Map<String, Object> inputs = parseJson(body);
                String triggeredBy = request.getHeader("X-User-ID"); // From auth token
                
                return executionService.executeWorkflow(tenantId, pipelineId, inputs, triggeredBy)
                    .map(execution -> {
                        Map<String, Object> response = Map.of(
                            "executionId", execution.getExecutionId(),
                            "workflowId", execution.getWorkflowId(),
                            "status", execution.getStatus(),
                            "createdAt", execution.getCreatedAt().toString()
                        );
                        
                        return http.envelopeResponse(
                            ApiResponse.success(response, tenantId, requestId),
                            objectMapper);
                    });
            });
    }
    
    /**
     * GET /api/v1/pipelines/:id/executions
     */
    public Promise<HttpResponse> handleListExecutions(HttpRequest request, String pipelineId) {
        String tenantId = http.requireTenantIdOrFail(request);
        String requestId = generateRequestId();
        
        int limit = parseIntOrDefault(request.getQueryParameter("limit"), 50);
        int offset = parseIntOrDefault(request.getQueryParameter("offset"), 0);
        
        return executionService.listExecutions(tenantId, pipelineId, limit, offset)
            .map(executions -> {
                List<Map<String, Object>> items = executions.stream()
                    .map(this::executionToMap)
                    .toList();
                    
                Map<String, Object> response = Map.of(
                    "items", items,
                    "total", items.size(), // TODO: get actual count
                    "page", (offset / limit) + 1,
                    "pageSize", limit,
                    "hasMore", items.size() == limit
                );
                
                return http.envelopeResponse(
                    ApiResponse.success(response, tenantId, requestId),
                    objectMapper);
            });
    }
    
    /**
     * GET /api/v1/pipelines/:pipelineId/executions/:executionId
     */
    public Promise<HttpResponse> handleGetExecution(HttpRequest request, 
                                                     String pipelineId,
                                                     String executionId) {
        String tenantId = http.requireTenantIdOrFail(request);
        String requestId = generateRequestId();
        
        return executionService.getExecution(tenantId, pipelineId, executionId)
            .map(opt -> {
                if (opt.isEmpty()) {
                    return HttpResponse.ofCode(404)
                        .withJson(Map.of("error", "EXECUTION_NOT_FOUND"));
                }
                
                return http.envelopeResponse(
                    ApiResponse.success(executionToMap(opt.get()), tenantId, requestId),
                    objectMapper);
            });
    }
    
    /**
     * POST /api/v1/pipelines/:pipelineId/executions/:executionId/cancel
     */
    public Promise<HttpResponse> handleCancel(HttpRequest request,
                                               String pipelineId,
                                               String executionId) {
        String tenantId = http.requireTenantIdOrFail(request);
        String cancelledBy = request.getHeader("X-User-ID");
        String requestId = generateRequestId();
        
        return executionService.cancelExecution(tenantId, pipelineId, executionId, cancelledBy)
            .map(execution -> http.envelopeResponse(
                ApiResponse.success(executionToMap(execution), tenantId, requestId),
                objectMapper))
            .whenException(e -> handleCancellationError(e, requestId));
    }
}
```

**Step 5: Wire Routes in HTTP Server**
```java
// In DataCloudHttpServer.configureRoutes()
router.post("/api/v1/pipelines/:id/execute", 
    req -> executionHandler.handleExecute(req, req.getPathParameter("id")));
    
router.get("/api/v1/pipelines/:id/executions",
    req -> executionHandler.handleListExecutions(req, req.getPathParameter("id")));
    
router.get("/api/v1/pipelines/:pipelineId/executions/:executionId",
    req -> executionHandler.handleGetExecution(req, 
        req.getPathParameter("pipelineId"),
        req.getPathParameter("executionId")));
        
router.post("/api/v1/pipelines/:pipelineId/executions/:executionId/cancel",
    req -> executionHandler.handleCancel(req,
        req.getPathParameter("pipelineId"),
        req.getPathParameter("executionId")));
```

#### Acceptance Criteria
- [x] POST /pipelines/:id/execute returns execution ID immediately
- [x] GET /pipelines/:id/executions returns real executions, not empty list
- [x] GET /pipelines/:id/executions/:id returns execution details with node statuses
- [x] POST /pipelines/:id/executions/:id/cancel stops running execution
- [x] Execution status transitions: PENDING → RUNNING → COMPLETED/FAILED/CANCELLED
- [x] Node executions recorded with inputs/outputs/errors
- [x] Executions survive Data Cloud restart
- [x] Integration test: create workflow → execute → list → get → cancel → verify states

---

## Phase 1: Proof/Tests + Workflow Completion + Architecture Fixes
**Timeline:** Weeks 5-8  
**Goal:** Validated correctness, aligned contracts, truthful health checks

---

### Task 1.1: Align E2E Mocks to Backend Truth
**Priority:** P1 - High  
**Owner:** Data Cloud Frontend Team  
**Effort:** 3 days
**Implementation Status (2026-04-17):** Complete in current repo shape. The canonical collections client already targets `/api/v1/entities/dc_collections`, previous passes aligned the remaining Playwright collection mocks/empty-state overrides, and this pass updated the lingering frontend contract test labels to the canonical entity route so test reporting matches the real backend contract.

#### Problem
E2E tests mock `/api/v1/collections` but backend uses `/api/v1/entities/dc_collections`.

#### Files to Modify
1. `@/Users/samujjwal/Development/ghatana/products/data-cloud/ui/e2e/helpers/api-mocks.ts`
2. `@/Users/samujjwal/Development/ghatana/products/data-cloud/ui/e2e/fixtures/test-data.ts`
3. `@/Users/samujjwal/Development/ghatana/products/data-cloud/ui/src/lib/api/collections.ts`

#### Implementation Steps

**Step 1: Update Mock Routes**
```typescript
// In api-mocks.ts
// OLD: await page.route('**/api/v1/collections', ...)
// NEW: Match actual backend routes

export async function mockCollectionsAPI(page: Page) {
  // Match the actual backend route
  await page.route('**/api/v1/entities/dc_collections', async (route) => {
    // ... implementation
  });
  
  // Also support the old route with redirect warning for transition
  await page.route('**/api/v1/collections', async (route) => {
    console.warn('DEPRECATED: /api/v1/collections is deprecated, use /api/v1/entities/dc_collections');
    await route.fulfill({
      status: 301,
      headers: { 'Location': '/api/v1/entities/dc_collections' }
    });
  });
}
```

**Step 2: Update Collections Service**
```typescript
// In collections.ts
// Align to actual backend contract

export const collectionsApi = {
  // Use the actual backend route
  list: async (params?: CollectionQueryParams): Promise<PaginatedResponse<Collection>> => {
    const response = await apiClient.get<BackendCollectionListResponse>(
      '/entities/dc_collections', // Actual backend route
      { params }
    );
    return transformResponse(response);
  },
  
  // ... other methods
};
```

**Step 3: Contract Verification Test**
```typescript
// Create contract-test.spec.ts
import { test, expect } from '@playwright/test';
import { openApiSpec } from '../contracts/openapi';

test('E2E mocks match OpenAPI contract', async () => {
  const mockRoutes = extractMockRoutes();
  const contractRoutes = extractContractRoutes(openApiSpec);
  
  for (const mockRoute of mockRoutes) {
    expect(contractRoutes).toContain(mockRoute);
  }
});

test('Backend routes match OpenAPI contract', async ({ request }) => {
  // Test each backend route exists and matches contract
  const routes = ['/entities/dc_collections', '/pipelines', '/events'];
  
  for (const route of routes) {
    const response = await request.get(route);
    expect(response.status()).not.toBe(404);
    
    // Validate response schema
    const body = await response.json();
    expect(() => contractValidator.validate(route, body)).not.toThrow();
  }
});
```

#### Acceptance Criteria
- [x] All E2E mocks and contract-test route labels use actual backend routes
- [ ] E2E tests pass against real backend (not just mocks)
- [ ] Contract drift detection test in CI
- [ ] Deprecation warnings for old routes
- [ ] Single source of truth: OpenAPI spec drives mocks

---

### Task 1.2: Dependency-Truthful Health Checks
**Priority:** P1 - High  
**Owner:** Data Cloud Backend Team  
**Effort:** 3 days
**Implementation Status (2026-04-17):** Code complete in current repo shape. `HealthHandler` now distinguishes liveness from readiness, surfaces configured subsystem snapshots, marks unconfigured optional dependencies as `NOT_CONFIGURED`, and returns `503` on readiness when critical dependencies are down.

#### Problem
Health/readiness return UP/READY without probing configured dependencies.

#### Files to Modify
1. `@/Users/samujjwal/Development/ghatana/products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/HealthHandler.java`
2. Create: `@/Users/samujjwal/Development/ghatana/products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/health/DependencyHealthProbe.java`

#### Implementation Steps

**Step 1: Define Health Check Interface**
```java
public interface HealthProbe {
    String getName();
    HealthStatus check();
    boolean isRequired(); // Degraded if false and down
}

public enum HealthStatus {
    UP,      // Healthy
    DEGRADED, // Optional dependency down
    DOWN,    // Required dependency down
    UNKNOWN  // Not configured
}

public class HealthResult {
    String name;
    HealthStatus status;
    String message;
    long responseTimeMs;
    Map<String, Object> details;
}
```

**Step 2: Implement Probes**
```java
@Component
public class EntityStoreHealthProbe implements HealthProbe {
    private final DataCloudClient dataCloud;
    
    @Override
    public HealthStatus check() {
        long start = System.currentTimeMillis();
        try {
            // Perform lightweight operation
            dataCloud.findById("health-check-tenant", "_health", "ping")
                .getResult(); // ActiveJ Promise
                
            return HealthStatus.UP;
        } catch (Exception e) {
            return HealthStatus.DOWN;
        }
    }
    
    @Override
    public boolean isRequired() {
        return true; // Entity store is required
    }
}

@Component
public class EventLogStoreHealthProbe implements HealthProbe {
    // Similar implementation
}

@Component
public class OptionalDependencyProbe implements HealthProbe {
    private final OptionalService service;
    
    @Override
    public HealthStatus check() {
        if (service == null || !service.isConfigured()) {
            return HealthStatus.UNKNOWN;
        }
        
        try {
            service.ping();
            return HealthStatus.UP;
        } catch (Exception e) {
            return HealthStatus.DEGRADED; // Optional, so degraded not down
        }
    }
    
    @Override
    public boolean isRequired() {
        return false; // Optional service
    }
}
```

**Step 3: Update Health Handler**
```java
public class HealthHandler {
    private final List<HealthProbe> probes;
    private final DataCloudConfig config;
    
    /**
     * GET /health
     * Shallow check - is the process alive
     */
    public Promise<HttpResponse> handleHealth(HttpRequest request) {
        return Promise.of(HttpResponse.ok()
            .withJson(Map.of(
                "status", "UP",
                "timestamp", Instant.now().toString()
            )));
    }
    
    /**
     * GET /ready
     * Deep check - are required dependencies healthy
     */
    public Promise<HttpResponse> handleReady(HttpRequest request) {
        List<HealthResult> results = probes.stream()
            .map(probe -> {
                long start = System.currentTimeMillis();
                HealthStatus status = probe.check();
                return new HealthResult(
                    probe.getName(),
                    status,
                    status == HealthStatus.UP ? "Healthy" : "Check failed",
                    System.currentTimeMillis() - start,
                    Map.of()
                );
            })
            .toList();
            
        boolean anyRequiredDown = results.stream()
            .filter(r -> r.getStatus() == HealthStatus.DOWN)
            .anyMatch(r -> {
                HealthProbe probe = findProbe(r.getName());
                return probe.isRequired();
            });
            
        String overallStatus = anyRequiredDown ? "DOWN" : "READY";
        int statusCode = anyRequiredDown ? 503 : 200;
        
        Map<String, Object> response = Map.of(
            "status", overallStatus,
            "timestamp", Instant.now().toString(),
            "profile", config.profile(),
            "dependencies", results.stream()
                .map(r -> Map.of(
                    "name", r.getName(),
                    "status", r.getStatus().toString(),
                    "responseTimeMs", r.getResponseTimeMs(),
                    "message", r.getMessage()
                ))
                .toList()
        );
        
        return Promise.of(HttpResponse.ofCode(statusCode)
            .withJson(response));
    }
    
    /**
     * GET /health/deep
     * Comprehensive check with all details
     */
    public Promise<HttpResponse> handleDeepHealth(HttpRequest request) {
        // Detailed health with subsystem information
        Map<String, Object> subsystems = new HashMap<>();
        
        // Entity store
        subsystems.put("entityStore", checkEntityStore());
        
        // Event log
        subsystems.put("eventLog", checkEventLog());
        
        // Optional: AI service
        subsystems.put("aiAssist", checkAiService());
        
        // Build response
        boolean allRequiredUp = subsystems.values().stream()
            .map(s -> (Map<String, Object>) s)
            .filter(s -> Boolean.TRUE.equals(s.get("required")))
            .allMatch(s -> "UP".equals(s.get("status")));
            
        return Promise.of(HttpResponse.ofCode(allRequiredUp ? 200 : 503)
            .withJson(Map.of(
                "status", allRequiredUp ? "HEALTHY" : "DEGRADED",
                "timestamp", Instant.now().toString(),
                "subsystems", subsystems
            )));
    }
}
```

**Step 4: Add Startup Health Check**
```java
@Component
public class StartupHealthCheck {
    public void validate() {
        // Fail fast during startup if required dependencies unavailable
        List<HealthProbe> requiredProbes = probes.stream()
            .filter(HealthProbe::isRequired)
            .toList();
            
        for (HealthProbe probe : requiredProbes) {
            HealthStatus status = probe.check();
            if (status != HealthStatus.UP) {
                throw new IllegalStateException(
                    "Required dependency unavailable: " + probe.getName());
            }
        }
    }
}
```

#### Acceptance Criteria
- [x] /ready returns 503 when required dependencies down
- [x] /ready returns 200 only when all required dependencies up
- [x] /health/deep shows UNKNOWN for unconfigured optional services
- [x] Response includes response time for each dependency
- [ ] Startup fails fast if required dependencies unavailable
- [x] Kubernetes liveness probe uses /health (tolerant)
- [x] Kubernetes readiness probe uses /ready (strict)

---

### Task 1.3: AEP Durable Data Cloud Client
**Priority:** P1 - High  
**Owner:** AEP Backend Team  
**Effort:** 2 days
**Implementation Status (2026-04-17):** Code complete in current repo shape. `AepLauncher` now creates durable production/sovereign Data Cloud clients, keeps embedded mode to non-production profiles, and fails closed on production client bootstrap failures.

#### Problem
AEP creates embedded in-memory Data Cloud client; needs durable external connection.

#### Files to Modify
1. `@/Users/samujjwal/Development/ghatana/products/aep/server/src/main/java/com/ghatana/aep/server/AepLauncher.java:302-310`

#### Implementation Steps

**Step 1: Support External Data Cloud**
```java
private static DataCloudClient createAgentDataCloudClient() {
    String dataCloudUrl = System.getenv("DATACLOUD_URL");
    
    if (dataCloudUrl != null) {
        // Connect to external Data Cloud
        log.info("Connecting to external Data Cloud at {}", dataCloudUrl);
        
        DataCloudConfig config = DataCloudConfig.builder()
            .profile(DataCloudProfile.PRODUCTION)
            .endpoint(dataCloudUrl)
            .apiKey(System.getenv("DATACLOUD_API_KEY"))
            .build();
            
        return DataCloud.create(config);
    }
    
    // Fall back to embedded only in non-production
    if (isProduction()) {
        throw new IllegalStateException(
            "DATACLOUD_URL required in production. " +
            "In-memory Data Cloud not suitable for production.");
    }
    
    log.warn("Creating embedded DataCloudClient for development");
    return DataCloud.embedded();
}
```

**Step 2: Add Configuration Validation**
```java
private static void validateDataCloudConfiguration() {
    if (isProduction()) {
        String dataCloudUrl = System.getenv("DATACLOUD_URL");
        if (dataCloudUrl == null) {
            throw new IllegalStateException(
                "Production AEP requires external Data Cloud.\n" +
                "Set DATACLOUD_URL (e.g., https://datacloud.internal:8082)");
        }
        
        // Validate URL format
        try {
            new URL(dataCloudUrl);
        } catch (MalformedURLException e) {
            throw new IllegalStateException(
                "Invalid DATACLOUD_URL: " + dataCloudUrl);
        }
    }
}
```

#### Acceptance Criteria
- [x] Production AEP requires DATACLOUD_URL
- [x] AEP connects to external durable Data Cloud in production
- [x] Embedded mode only allowed in LOCAL/DEV profiles
- [x] Fail-fast validation on startup
- [x] Health check verifies Data Cloud connectivity

---

## Phase 2: UX Simplification + Operational Readiness + Performance
**Timeline:** Weeks 9-12  
**Goal:** Honest UI, operational tooling, proven scalability

---

### Task 2.1: Remove Hardcoded Insights from UI
**Priority:** P2 - Medium  
**Owner:** Data Cloud Frontend Team  
**Effort:** 2 days
**Implementation Status (2026-04-17):** Complete in current repo shape. `InsightsPage` is backed by real query, cost, workflow, capability, and analytics suggestion services, and this pass removed the remaining static trend decorations so the page no longer implies synthetic deltas as live telemetry.

#### Problem
UI shows hardcoded AI insights like "Query optimization available" as if they were live.

#### Files to Modify
1. `@/Users/samujjwal/Development/ghatana/products/data-cloud/ui/src/pages/InsightsPage.tsx:140+`

#### Implementation Steps

**Step 1: Create Backend-Driven Insight Component**
```typescript
// components/insights/BackendInsightCard.tsx
interface Insight {
  key: string;
  title: string;
  description: string;
  type: 'optimization' | 'warning' | 'insight';
  severity: 'low' | 'medium' | 'high';
  createdAt: string;
  expiresAt?: string;
}

export function BackendInsightCard({ insight }: { insight: Insight }) {
  return (
    <div className={`insight-card insight-${insight.type}`}>
      <h4>{insight.title}</h4>
      <p>{insight.description}</p>
      <span className="timestamp">
        Generated: {new Date(insight.createdAt).toLocaleString()}
      </span>
    </div>
  );
}
```

**Step 2: Update Insights Page**
```typescript
// pages/InsightsPage.tsx
export function InsightsPage() {
  const { data: insights, isLoading } = useQuery({
    queryKey: ['insights'],
    queryFn: () => brainService.getInsights(), // Real backend call
  });
  
  const { data: capabilities } = useCapabilityRegistry();
  const insightsAvailable = capabilities?.includes('insights');
  
  if (!insightsAvailable) {
    return (
      <div className="insights-unavailable">
        <p>AI Insights not available in current deployment.</p>
        <p>Configure OpenAI API key or Ollama host to enable.</p>
      </div>
    );
  }
  
  if (insights?.length === 0) {
    return (
      <div className="no-insights">
        <p>No AI insights currently available.</p>
        <p>Insights are generated periodically based on data patterns.</p>
      </div>
    );
  }
  
  return (
    <div className="insights-grid">
      {insights?.map(insight => (
        <BackendInsightCard key={insight.key} insight={insight} />
      ))}
    </div>
  );
}
```

**Step 3: Remove Hardcoded Data**
```typescript
// DELETE from InsightsPage.tsx:
// - REMOVE: hardcoded "Query optimization available" card
// - REMOVE: hardcoded "Data freshness alert" card  
// - REMOVE: hardcoded "Pattern detected" card
// - REMOVE: Mock trend data: trend={{ value: 12, direction: 'up' }}
```

#### Acceptance Criteria
- [x] No hardcoded insight text in UI
- [x] Empty state shown when no insights from backend
- [x] Capability-gated: insights section renders an explicit unavailable state when backend capability is unavailable
- [x] All insight data fetched from canonical brain / analytics / cost / workflow services
- [x] Timestamps show when insight was generated

---

### Task 2.2: Capability-Gated UI States
**Priority:** P2 - Medium  
**Owner:** Data Cloud Frontend Team  
**Effort:** 3 days
**Implementation Status (2026-04-17):** Implemented via the runtime capability registry rather than a single global guard abstraction. The UI now uses `capabilities.service.ts` for normalized runtime truth, and key surfaces such as `InsightsPage` and `SqlWorkspacePage` render explicit unavailable/degraded states when optional backend capabilities are absent.

#### Problem
UI shows features that may not be wired in backend.

#### Implementation Steps

**Step 1: Extend Capability Registry Hook**
```typescript
// hooks/useCapabilityRegistry.ts
export function useCapabilityRegistry() {
  return useQuery({
    queryKey: ['capabilities'],
    queryFn: async () => {
      const response = await apiClient.get('/api/v1/capabilities');
      return {
        available: new Set(response.capabilities),
        degraded: new Set(response.degraded || []),
        unavailable: new Set(response.unavailable || []),
        all: response.capabilities,
      };
    },
    staleTime: 30000, // Cache for 30 seconds
  });
}

export function useCapability(capability: string): CapabilityState {
  const { data } = useCapabilityRegistry();
  
  if (data?.available.has(capability)) {
    return { status: 'available', enabled: true };
  }
  if (data?.degraded.has(capability)) {
    return { status: 'degraded', enabled: true };
  }
  if (data?.unavailable.has(capability)) {
    return { status: 'unavailable', enabled: false };
  }
  return { status: 'unknown', enabled: false };
}
```

**Step 2: Create CapabilityGuard Component**
```typescript
// components/CapabilityGuard.tsx
interface CapabilityGuardProps {
  capability: string;
  children: React.ReactNode;
  fallback?: React.ReactNode;
}

export function CapabilityGuard({ 
  capability, 
  children, 
  fallback = <DisabledFeature message="Feature not available" /> 
}: CapabilityGuardProps) {
  const { status, enabled } = useCapability(capability);
  
  if (!enabled) {
    return fallback;
  }
  
  return (
    <div className={status === 'degraded' ? 'feature-degraded' : ''}>
      {status === 'degraded' && (
        <DegradedBanner capability={capability} />
      )}
      {children}
    </div>
  );
}
```

**Step 3: Apply to Feature Pages**
```typescript
// pages/WorkflowsPage.tsx
export function WorkflowsPage() {
  return (
    <CapabilityGuard 
      capability="workflow_execution"
      fallback={
        <DisabledFeature 
          title="Workflow Execution"
          message="Workflow execution is not enabled in this deployment."
          action={{ label: "View Documentation", href: "/docs/workflows" }}
        />
      }
    >
      <WorkflowList />
      <WorkflowBuilder />
    </CapabilityGuard>
  );
}

// pages/PluginsPage.tsx  
export function PluginsPage() {
  return (
    <CapabilityGuard
      capability="plugin_marketplace"
      fallback={
        <DisabledFeature
          title="Plugin Marketplace"
          message="Only bundled plugins are available."
        >
          <BundledPluginList />
        </DisabledFeature>
      }
    >
      <PluginMarketplace />
    </CapabilityGuard>
  );
}
```

#### Acceptance Criteria
- [ ] All optional features guarded by capability check
- [x] Features show "unavailable" state when backend not wired
- [x] Degraded features show warning but remain usable
- [x] Capability registry polled on app start and periodically
- [x] Loading state while capabilities being fetched

---

### Task 2.3: Load Testing Framework
**Priority:** P2 - Medium  
**Owner:** Data Cloud Platform Team  
**Effort:** 4 days
**Implementation Status (2026-04-17):** Code complete in current repo shape. The repo now contains focused JMH CRUD benchmarks plus a durable multi-tenant Testcontainers load suite that emits JSON metrics under `products/data-cloud/build/reports/load-tests/` and is documented in the Data Cloud runbook.

#### Implementation Steps

**Step 1: Create Load Test Suite**
```java
// src/test/java/com/ghatana/datacloud/performance/LoadTest.java
public class EntityCrudLoadTest {
    
    @Test
    void shouldHandle1000ConcurrentWrites() throws Exception {
        int concurrentUsers = 1000;
        int requestsPerUser = 10;
        
        DataCloudClient client = createTestClient();
        
        ExecutorService executor = Executors.newFixedThreadPool(concurrentUsers);
        CountDownLatch latch = new CountDownLatch(concurrentUsers);
        
        List<Future<List<Long>>> futures = new ArrayList<>();
        
        for (int i = 0; i < concurrentUsers; i++) {
            final int userId = i;
            futures.add(executor.submit(() -> {
                latch.countDown();
                latch.await(); // Synchronize start
                
                List<Long> latencies = new ArrayList<>();
                for (int j = 0; j < requestsPerUser; j++) {
                    long start = System.nanoTime();
                    
                    client.save("load-test-tenant", "test_entities", 
                        Map.of("user", userId, "request", j))
                        .getResult();
                        
                    latencies.add((System.nanoTime() - start) / 1_000_000); // ms
                }
                return latencies;
            }));
        }
        
        // Collect results
        List<Long> allLatencies = futures.stream()
            .map(f -> {
                try {
                    return f.get(30, TimeUnit.SECONDS);
                } catch (Exception e) {
                    fail("Request failed: " + e.getMessage());
                    return List.<Long>of();
                }
            })
            .flatMap(List::stream)
            .toList();
            
        // Assert performance
        double avgLatency = allLatencies.stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0);
            
        long p99Latency = percentile(allLatencies, 99);
        
        assertThat(avgLatency).isLessThan(50); // 50ms average
        assertThat(p99Latency).isLessThan(200); // 200ms p99
        
        // Assert throughput
        double throughput = (concurrentUsers * requestsPerUser) / 
            (allLatencies.stream().mapToLong(Long::longValue).sum() / 1000.0);
            
        assertThat(throughput).isGreaterThan(100); // 100+ requests/second
    }
}
```

**Step 2: Create CI Load Test Job**
```yaml
# .github/workflows/load-test.yml
name: Load Tests

on:
  schedule:
    - cron: '0 2 * * *' # Nightly
  workflow_dispatch:

jobs:
  load-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Start Data Cloud
        run: |
          ./gradlew :products:data-cloud:launcher:run &
          sleep 30 # Wait for startup
          
      - name: Run Load Tests
        run: |
          ./gradlew :products:data-cloud:launcher:test \
            --tests "com.ghatana.datacloud.performance.*" \
            -Dtest.profile=load
            
      - name: Publish Results
        uses: actions/upload-artifact@v3
        with:
          name: load-test-results
          path: build/reports/load-tests/
```

#### Acceptance Criteria
- [ ] 1000 concurrent entity writes test
- [ ] 10,000 events/second streaming test
- [ ] p99 latency < 200ms for entity CRUD
- [ ] Results published to CI artifacts
- [ ] Performance regression detection

---

## Phase 3: Differentiation + Innovation + Frontrunner Positioning
**Timeline:** Weeks 13-20  
**Goal:** Unique capabilities that define market category

---

### Task 3.1: Context Layer API
**Priority:** P3 - Strategic  
**Owner:** Data Cloud Platform Team  
**Effort:** 10-12 days
**Implementation Status (2026-04-17):** Code complete in current repo shape. `ContextLayerHandler` exposes the tenant-scoped context API and snapshot endpoint, the HTTP server wires the routes, and the typed frontend client plus integration tests are already in place.

#### Vision
Expose unified context surface for AI agents: entity schema + lineage + governance + freshness in single query.

#### Implementation Steps

**Step 1: Define Context API Schema**
```java
public class ContextQuery {
    String entityType;        // e.g., "customer"
    String entityId;          // Optional: specific instance
    List<String> aspects;     // schema, lineage, governance, relationships, freshness
    int depth;                // Relationship traversal depth
}

public class ContextResponse {
    String entityType;
    Map<String, Object> schema;           // Field definitions, types
    List<LineageEdge> lineage;            // Where this data came from
    GovernanceInfo governance;            // Classification, retention
    List<Relationship> relationships;     // Connected entities
    FreshnessInfo freshness;              // Last update, staleness
    List<ContextInsight> insights;        // AI-generated context
}
```

**Step 2: Implement Context Service**
```java
@Service
public class ContextService {
    private final EntityStore entityStore;
    private final LineageService lineageService;
    private final GovernanceService governanceService;
    private final KnowledgeGraphService knowledgeGraph;
    
    public Promise<ContextResponse> getContext(
            TenantContext tenant, 
            ContextQuery query) {
        
        ContextResponse.Builder response = ContextResponse.builder()
            .entityType(query.getEntityType());
            
        // Parallel fetch of context aspects
        List<Promise<?>> fetches = new ArrayList<>();
        
        if (query.getAspects().contains("schema")) {
            fetches.add(
                fetchSchema(tenant, query.getEntityType())
                    .map(response::schema)
            );
        }
        
        if (query.getAspects().contains("lineage")) {
            fetches.add(
                lineageService.getLineage(tenant, query.getEntityType())
                    .map(response::lineage)
            );
        }
        
        if (query.getAspects().contains("governance")) {
            fetches.add(
                governanceService.getClassification(tenant, query.getEntityType())
                    .map(response::governance)
            );
        }
        
        if (query.getAspects().contains("relationships")) {
            fetches.add(
                knowledgeGraph.getRelationships(
                    tenant, query.getEntityType(), query.getEntityId(), query.getDepth())
                    .map(response::relationships)
            );
        }
        
        if (query.getAspects().contains("freshness")) {
            fetches.add(
                fetchFreshness(tenant, query.getEntityType())
                    .map(response::freshness)
            );
        }
        
        return Promise.all(fetches.toArray(new Promise[0]))
            .map(v -> response.build());
    }
}
```

**Step 3: Create HTTP Endpoint**
```java
// GET /api/v1/context?entityType=customer&aspects=schema,lineage,governance
public Promise<HttpResponse> handleContextQuery(HttpRequest request) {
    String tenantId = requireTenantId(request);
    String entityType = request.getQueryParameter("entityType");
    List<String> aspects = Arrays.asList(
        request.getQueryParameter("aspects").split(","));
    
    ContextQuery query = ContextQuery.builder()
        .entityType(entityType)
        .aspects(aspects)
        .depth(parseIntOrDefault(request.getQueryParameter("depth"), 1))
        .build();
        
    return contextService.getContext(TenantContext.of(tenantId), query)
        .map(context -> HttpResponse.ok().withJson(context));
}
```

**Step 4: MCP Server Implementation**
```java
// Expose as Model Context Protocol server
@Component
public class ContextMcpServer implements McpServer {
    
    @Tool(description = "Get comprehensive context about an entity type")
    public ContextResponse getEntityContext(
            @Param(description = "Entity type, e.g., 'customer', 'order'") String entityType,
            @Param(description = "Aspects to include: schema, lineage, governance, relationships, freshness") 
            List<String> aspects) {
        
        return contextService.getContext(
            currentTenant(),
            ContextQuery.builder()
                .entityType(entityType)
                .aspects(aspects)
                .build()
        ).getResult();
    }
    
    @Tool(description = "Find related entities")
    public List<Relationship> findRelatedEntities(
            @Param String entityType,
            @Param String entityId,
            @Param int depth) {
        
        return knowledgeGraph.getRelationships(
            currentTenant(), entityType, entityId, depth).getResult();
    }
    
    @Tool(description = "Check data freshness")
    public FreshnessInfo checkFreshness(@Param String entityType) {
        return contextService.getFreshness(currentTenant(), entityType).getResult();
    }
}
```

#### Acceptance Criteria
- [ ] Single API returns unified context (schema+lineage+governance+relationships)
- [ ] MCP server exposes context tools to AI agents
- [ ] < 100ms response for standard context query
- [ ] Relationship traversal up to 3 levels
- [ ] Context automatically updated on entity changes
- [ ] Documentation: "Context-Native Data Fabric" positioning

---

### Task 3.2: Voice Query Gateway (Hardening)
**Priority:** P3 - Strategic  
**Owner:** Data Cloud Platform Team  
**Effort:** 6-8 days
**Implementation Status (2026-04-17):** Code complete in current repo shape. `VoiceGatewayHandler` has already been hardened with tenant-scoped rate limiting, context-layer grounding, and dedicated regression coverage; this item should now be treated as operational follow-through rather than missing implementation.

#### Problem
VoiceGatewayHandler exists but real voice integration unclear.

#### Implementation Steps

**Step 1: Implement Real STT Adapter**
```java
public class WhisperSttAdapter implements SpeechToTextAdapter {
    private final OpenAIClient openAiClient;
    
    @Override
    public Promise<String> transcribe(AudioStream audio) {
        return Promise.ofBlocking(() -> {
            // Stream audio to Whisper API
            TranscriptionRequest request = TranscriptionRequest.builder()
                .model("whisper-1")
                .language("en")
                .build();
                
            TranscriptionResult result = openAiClient.transcribe(audio, request);
            return result.getText();
        });
    }
}
```

**Step 2: Implement Intent Resolution**
```java
public class VoiceIntentResolver {
    
    public Promise<VoiceIntent> resolveIntent(String transcript) {
        // Use LLM to extract intent from transcript
        String prompt = """
            Parse the following voice query into a structured intent.
            
            Query: "%s"
            
            Available intents:
            - QUERY: "show me", "find", "list", "get"
            - CREATE: "create", "add", "new"
            - UPDATE: "update", "change", "set"
            - DELETE: "delete", "remove", "drop"
            
            Respond in JSON:
            {
              "intent": "QUERY|CREATE|UPDATE|DELETE",
              "entityType": "the entity being queried",
              "filters": {"field": "value"},
              "aggregations": ["count", "sum"],
              "timeRange": "today|this week|last month"
            }
            """.formatted(transcript);
            
        return llmClient.complete(prompt)
            .map(response -> parseIntentJson(response));
    }
}
```

**Step 3: Voice-to-Query Pipeline**
```java
public class VoiceQueryPipeline {
    
    public Promise<QueryResult> processVoiceQuery(
            TenantContext tenant,
            AudioStream audio) {
        
        // 1. Transcribe
        return sttAdapter.transcribe(audio)
            
            // 2. Resolve intent
            .then(transcript -> intentResolver.resolveIntent(transcript))
            
            // 3. Convert to structured query
            .then(intent -> intentToQueryConverter.convert(intent))
            
            // 4. Execute query
            .then(query -> entityStore.query(tenant, query))
            
            // 5. Format for voice response
            .then(results -> voiceResponseFormatter.format(results));
    }
}
```

#### Acceptance Criteria
- [ ] Real-time voice transcription < 2 seconds
- [ ] Intent resolution accuracy > 90% for common queries
- [ ] Voice query executes and returns results
- [ ] Voice response synthesis (TTS)
- [ ] Demo: voice query over entity data

---

## Appendix: Implementation Priorities Summary

### Week-by-Week Breakdown

| Week | Focus | Key Deliverables |
|------|-------|------------------|
| 1 | AEP Durability | InMemory→DataCloud replacements for AgentRegistry, HumanReviewQueue |
| 2 | Data Cloud Security | Mandatory tenant, auth enforcement, purge implementation |
| 3 | Workflow Execution | Backend execution service, node execution engine |
| 4 | Redaction + Testing | Real PII redaction, integration test suite |
| 5 | Contract Alignment | E2E mocks aligned, drift detection in CI |
| 6 | Health Checks | Dependency-probing health, K8s probes |
| 7 | AEP Hardening | Durable Data Cloud client, integration tests |
| 8 | UI Alignment | Backend-driven insights, capability gating |
| 9-10 | Performance | Load testing, optimization |
| 11-12 | Operations | Monitoring, alerting, runbooks |
| 13-16 | Context API | Context layer, MCP server |
| 17-20 | Voice + Innovation | Voice query hardening, differentiation features |

### Critical Path Dependencies

```
Tenant Enforcement (0.4)
    ↓
Auth Enforcement (0.7)
    ↓
Workflow Execution (0.8) → UI Alignment (2.1)
    ↓
Load Testing (2.3) → Performance Optimization
```

### Success Metrics by Phase

**Phase 0:**
- Zero data loss scenarios (all InMemory→durable replacements)
- 100% test pass rate with real backends
- Security scan: zero critical vulnerabilities

**Phase 1:**
- Contract drift: zero (mocks match backend)
- Health check accuracy: 100%
- Integration test coverage: > 80%

**Phase 2:**
- UI truthfulness: 100% (no hardcoded data)
- Load test: 10k events/sec sustained
- p99 latency: < 200ms

**Phase 3:**
- Context API response time: < 100ms
- Voice query accuracy: > 90%
- MCP server: functional with Claude/Cursor

---

**End of Implementation Plan**
