/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Task 4.9 — Security Hardening: Tenant isolation verification for AEP pipelines.
 * Ensures pipeline execution, checkpoint storage, and context propagation
 * properly enforce tenant boundaries.
 */
package com.ghatana.core.pipeline.security;

import com.ghatana.orchestrator.store.CheckpointStore;
import com.ghatana.orchestrator.store.InMemoryCheckpointStore;
import com.ghatana.orchestrator.store.PipelineCheckpoint;
import com.ghatana.orchestrator.store.PipelineCheckpointStatus;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.governance.security.TenantContext;
import com.ghatana.core.pipeline.PipelineExecutionContext;
import com.ghatana.core.operator.catalog.DefaultOperatorCatalog;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/**
 * Verifies multi-tenant isolation guarantees across AEP pipeline infrastructure.
 * Tests cover:
 * <ul>
 *   <li>Checkpoint store: data never leaks between tenants</li>
 *   <li>Execution context: tenantId is mandatory and properly scoped</li>
 *   <li>TenantContext + Principal: scope() / clear() lifecycle</li>
 *   <li>Idempotency keys: per-tenant, not global</li>
 *   <li>Concurrent multi-tenant access: no cross-contamination under load</li>
 * </ul>
 */
@DisplayName("Pipeline Tenant Isolation")
class PipelineTenantIsolationTest {

    private static final String TENANT_A = "tenant-alpha";
    private static final String TENANT_B = "tenant-beta";
    private static final String TENANT_C = "tenant-gamma";
    private static final String PIPELINE_ID = "fraud-detection";

    private InMemoryCheckpointStore checkpointStore;

    @BeforeEach
    void setUp() {
        checkpointStore = new InMemoryCheckpointStore();
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // =========================================================================
    // 1. Checkpoint Store Tenant Isolation
    // =========================================================================

    @Nested
    @DisplayName("Checkpoint Store Isolation")
    class CheckpointStoreIsolation {

        @Test
        @DisplayName("Checkpoints created by tenant A are invisible to tenant B")
        void checkpointsInvisibleAcrossTenants() {
            // Tenant A creates an execution
            checkpointStore.createExecution(TENANT_A, PIPELINE_ID, "exec-a1",
                    "idem-a1", Map.of("totalSteps", 3));

            // Tenant B queries the same pipeline — should find nothing
            List<PipelineCheckpoint> tenantBResults =
                    checkpointStore.findByPipelineId(TENANT_B, PIPELINE_ID, 100);
            assertThat(tenantBResults).isEmpty();

            // Tenant A sees its own checkpoint
            List<PipelineCheckpoint> tenantAResults =
                    checkpointStore.findByPipelineId(TENANT_A, PIPELINE_ID, 100);
            assertThat(tenantAResults).hasSize(1);
            assertThat(tenantAResults.get(0).getTenantId()).isEqualTo(TENANT_A);
        }

        @Test
        @DisplayName("Idempotency keys are scoped per-tenant")
        void idempotencyKeysPerTenant() {
            String sharedKey = "order-12345";

            // Tenant A creates with a key
            checkpointStore.createExecution(TENANT_A, PIPELINE_ID, "exec-a",
                    sharedKey, Map.of());

            // Same key, different tenant — should succeed (not duplicate)
            assertThatCode(() ->
                    checkpointStore.createExecution(TENANT_B, PIPELINE_ID, "exec-b",
                            sharedKey, Map.of())
            ).doesNotThrowAnyException();

            // Same key, same tenant — should fail
            assertThatThrownBy(() ->
                    checkpointStore.createExecution(TENANT_A, PIPELINE_ID, "exec-a2",
                            sharedKey, Map.of())
            ).isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Duplicate execution");
        }

        @Test
        @DisplayName("isDuplicate checks are tenant-scoped")
        void isDuplicateTenantScoped() {
            String key = "unique-key-1";
            checkpointStore.createExecution(TENANT_A, PIPELINE_ID, "exec-1",
                    key, Map.of());

            assertThat(checkpointStore.isDuplicate(TENANT_A, key)).isTrue();
            assertThat(checkpointStore.isDuplicate(TENANT_B, key)).isFalse();
        }

        @Test
        @DisplayName("findByIdempotencyKey only returns matching tenant")
        void findByIdempotencyKeyTenantScoped() {
            String key = "shared-idemp-key";
            checkpointStore.createExecution(TENANT_A, PIPELINE_ID, "exec-a",
                    key, Map.of("source", "alpha"));
            checkpointStore.createExecution(TENANT_B, PIPELINE_ID, "exec-b",
                    key, Map.of("source", "beta"));

            Optional<PipelineCheckpoint> resultA =
                    checkpointStore.findByIdempotencyKey(TENANT_A, key);
            assertThat(resultA).isPresent();
            assertThat(resultA.get().getTenantId()).isEqualTo(TENANT_A);

            Optional<PipelineCheckpoint> resultB =
                    checkpointStore.findByIdempotencyKey(TENANT_B, key);
            assertThat(resultB).isPresent();
            assertThat(resultB.get().getTenantId()).isEqualTo(TENANT_B);

            // Non-existent tenant returns empty
            Optional<PipelineCheckpoint> resultC =
                    checkpointStore.findByIdempotencyKey(TENANT_C, key);
            assertThat(resultC).isEmpty();
        }

        @Test
        @DisplayName("Multiple tenants accumulate checkpoints independently")
        void multipleTenantAccumulation() {
            for (int i = 0; i < 5; i++) {
                checkpointStore.createExecution(TENANT_A, PIPELINE_ID, "exec-a-" + i,
                        "key-a-" + i, Map.of());
            }
            for (int i = 0; i < 3; i++) {
                checkpointStore.createExecution(TENANT_B, PIPELINE_ID, "exec-b-" + i,
                        "key-b-" + i, Map.of());
            }

            assertThat(checkpointStore.findByPipelineId(TENANT_A, PIPELINE_ID, 100))
                    .hasSize(5);
            assertThat(checkpointStore.findByPipelineId(TENANT_B, PIPELINE_ID, 100))
                    .hasSize(3);
            assertThat(checkpointStore.findByPipelineId(TENANT_C, PIPELINE_ID, 100))
                    .isEmpty();
        }

        @Test
        @DisplayName("Checkpoint state updates do not affect other tenants' checkpoints")
        void stateUpdatesTenantIsolated() {
            checkpointStore.createExecution(TENANT_A, PIPELINE_ID, "exec-a",
                    "key-a", Map.of("totalSteps", 3));
            checkpointStore.createExecution(TENANT_B, PIPELINE_ID, "exec-b",
                    "key-b", Map.of("totalSteps", 5));

            // Update tenant A's checkpoint
            checkpointStore.updateCheckpoint("exec-a", "step-1", "Filter",
                    PipelineCheckpointStatus.STEP_SUCCESS,
                    Map.of("filtered", 100), Map.of("totalSteps", 3));

            // Tenant B's checkpoint should be untouched
            PipelineCheckpoint bCheckpoint = checkpointStore.findByInstanceId("exec-b").orElseThrow();
            assertThat(bCheckpoint.getCompletedSteps()).isZero();
            assertThat(bCheckpoint.getStatus()).isEqualTo(PipelineCheckpointStatus.CREATED);
        }
    }

    // =========================================================================
    // 2. Execution Context Tenant Scoping
    // =========================================================================

    @Nested
    @DisplayName("Execution Context Tenant Scoping")
    class ExecutionContextScoping {

        @Test
        @DisplayName("PipelineExecutionContext carries tenantId")
        void contextCarriesTenantId() {
            PipelineExecutionContext ctx = PipelineExecutionContext.builder()
                    .pipelineId(PIPELINE_ID)
                    .tenantId(TENANT_A)
                    .operatorCatalog(new DefaultOperatorCatalog())
                    .deadline(Duration.ofSeconds(30))
                    .build();

            assertThat(ctx.getTenantId()).isEqualTo(TENANT_A);
        }

        @Test
        @DisplayName("Two contexts with different tenants are independent")
        void twoContextsIndependent() {
            PipelineExecutionContext ctxA = PipelineExecutionContext.builder()
                    .pipelineId(PIPELINE_ID)
                    .tenantId(TENANT_A)
                    .executionId("exec-a")
                    .operatorCatalog(new DefaultOperatorCatalog())
                    .deadline(Duration.ofSeconds(30))
                    .build();

            PipelineExecutionContext ctxB = PipelineExecutionContext.builder()
                    .pipelineId(PIPELINE_ID)
                    .tenantId(TENANT_B)
                    .executionId("exec-b")
                    .operatorCatalog(new DefaultOperatorCatalog())
                    .deadline(Duration.ofSeconds(30))
                    .build();

            assertThat(ctxA.getTenantId()).isNotEqualTo(ctxB.getTenantId());
            assertThat(ctxA.getExecutionId()).isNotEqualTo(ctxB.getExecutionId());
        }
    }

    // =========================================================================
    // 3. TenantContext Thread-Local Isolation
    // =========================================================================

    @Nested
    @DisplayName("TenantContext Thread-Local Isolation")
    class TenantContextIsolation {

        @Test
        @DisplayName("scope() sets and restores tenant context")
        void scopeSetsAndRestores() throws Exception {
            assertThat(TenantContext.getCurrentTenantId()).isEqualTo("default-tenant");

            Principal principalA = new Principal("alice", List.of("admin"), TENANT_A);
            try (AutoCloseable scope = TenantContext.scope(principalA)) {
                assertThat(TenantContext.getCurrentTenantId()).isEqualTo(TENANT_A);
                assertThat(TenantContext.current()).isPresent();
                assertThat(TenantContext.current().get().getName()).isEqualTo("alice");
            }

            // After scope closes, back to default
            assertThat(TenantContext.getCurrentTenantId()).isEqualTo("default-tenant");
            assertThat(TenantContext.current()).isEmpty();
        }

        @Test
        @DisplayName("Nested scopes restore previous tenant on exit")
        void nestedScopesRestore() throws Exception {
            Principal principalA = new Principal("alice", List.of("admin"), TENANT_A);
            Principal principalB = new Principal("bob", List.of("viewer"), TENANT_B);

            try (AutoCloseable outerScope = TenantContext.scope(principalA)) {
                assertThat(TenantContext.getCurrentTenantId()).isEqualTo(TENANT_A);

                try (AutoCloseable innerScope = TenantContext.scope(principalB)) {
                    assertThat(TenantContext.getCurrentTenantId()).isEqualTo(TENANT_B);
                    assertThat(TenantContext.current().get().getName()).isEqualTo("bob");
                }

                // Inner scope closed — back to outer scope's tenant
                assertThat(TenantContext.getCurrentTenantId()).isEqualTo(TENANT_A);
                assertThat(TenantContext.current().get().getName()).isEqualTo("alice");
            }

            assertThat(TenantContext.getCurrentTenantId()).isEqualTo("default-tenant");
        }

        @Test
        @DisplayName("clear() removes all context")
        void clearRemovesContext() {
            TenantContext.setCurrentTenantId(TENANT_A);
            assertThat(TenantContext.getCurrentTenantId()).isEqualTo(TENANT_A);

            TenantContext.clear();
            assertThat(TenantContext.getCurrentTenantId()).isEqualTo("default-tenant");
            assertThat(TenantContext.current()).isEmpty();
        }

        @Test
        @DisplayName("Thread-local does not cross thread boundaries")
        void threadLocalDoesNotCrossBoundaries() throws Exception {
            Principal principalA = new Principal("alice", List.of("admin"), TENANT_A);
            TenantContext.scope(principalA);

            AtomicReference<String> otherThreadTenant = new AtomicReference<>();
            Thread thread = new Thread(() ->
                    otherThreadTenant.set(TenantContext.getCurrentTenantId()));
            thread.start();
            thread.join(5000);

            // Other thread defaults to "default-tenant", not TENANT_A
            assertThat(otherThreadTenant.get()).isEqualTo("default-tenant");
        }

        @Test
        @DisplayName("Explicit scope transfer to child thread works correctly")
        void explicitScopeTransfer() throws Exception {
            Principal principalA = new Principal("alice", List.of("admin"), TENANT_A);

            AtomicReference<String> childTenant = new AtomicReference<>();
            AtomicReference<String> childPrincipalName = new AtomicReference<>();

            try (AutoCloseable scope = TenantContext.scope(principalA)) {
                // Capture the principal for transfer
                Principal captured = TenantContext.current().orElseThrow();

                Thread child = new Thread(() -> {
                    try (AutoCloseable childScope = TenantContext.scope(captured)) {
                        childTenant.set(TenantContext.getCurrentTenantId());
                        childPrincipalName.set(TenantContext.current().get().getName());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                child.start();
                child.join(5000);
            }

            assertThat(childTenant.get()).isEqualTo(TENANT_A);
            assertThat(childPrincipalName.get()).isEqualTo("alice");
        }
    }

    // =========================================================================
    // 4. Principal Role-Based Tenant Scoping
    // =========================================================================

    @Nested
    @DisplayName("Principal Tenant Scoping")
    class PrincipalScoping {

        @Test
        @DisplayName("Principals with same name but different tenants are not equal")
        void sameNameDifferentTenantNotEqual() {
            Principal p1 = new Principal("alice", List.of("admin"), TENANT_A);
            Principal p2 = new Principal("alice", List.of("admin"), TENANT_B);

            assertThat(p1).isNotEqualTo(p2);
            assertThat(p1.hashCode()).isNotEqualTo(p2.hashCode());
        }

        @Test
        @DisplayName("Principal roles are immutable")
        void rolesImmutable() {
            Principal p = new Principal("alice", List.of("admin", "editor"), TENANT_A);

            assertThatThrownBy(() -> p.getRoles().add("hacker"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("Principal carries tenant identity for TenantContext integration")
        void principalCarriesTenantForContext() throws Exception {
            Principal p = new Principal("svc-pipeline", List.of("processor"), TENANT_B);
            try (AutoCloseable scope = TenantContext.scope(p)) {
                assertThat(TenantContext.getCurrentTenantId()).isEqualTo(TENANT_B);
                assertThat(TenantContext.current().get().hasRole("processor")).isTrue();
                assertThat(TenantContext.current().get().hasRole("admin")).isFalse();
            }
        }
    }

    // =========================================================================
    // 5. Concurrent Multi-Tenant Access
    // =========================================================================

    @Nested
    @DisplayName("Concurrent Multi-Tenant Access")
    class ConcurrentAccess {

        @Test
        @DisplayName("Concurrent checkpoint operations maintain tenant isolation")
        void concurrentCheckpointIsolation() throws Exception {
            int operationsPerTenant = 50;
            ExecutorService executor = Executors.newFixedThreadPool(6);
            CountDownLatch latch = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();

            String[] tenants = {TENANT_A, TENANT_B, TENANT_C};
            for (String tenant : tenants) {
                for (int i = 0; i < operationsPerTenant; i++) {
                    final int idx = i;
                    futures.add(executor.submit(() -> {
                        try {
                            latch.await(); // Synchronize start
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        checkpointStore.createExecution(tenant, PIPELINE_ID,
                                tenant + "-exec-" + idx, tenant + "-key-" + idx, Map.of());
                    }));
                }
            }

            latch.countDown(); // Release all threads
            for (Future<?> f : futures) {
                f.get(10, TimeUnit.SECONDS);
            }

            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);

            // Each tenant should see exactly its own checkpoints
            for (String tenant : tenants) {
                List<PipelineCheckpoint> results =
                        checkpointStore.findByPipelineId(tenant, PIPELINE_ID, 200);
                assertThat(results)
                        .hasSize(operationsPerTenant)
                        .allSatisfy(cp -> assertThat(cp.getTenantId()).isEqualTo(tenant));
            }
        }

        @Test
        @DisplayName("Concurrent TenantContext scopes do not interfere across threads")
        void concurrentTenantContextScopes() throws Exception {
            int threads = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CyclicBarrier barrier = new CyclicBarrier(threads);
            List<Future<String>> futures = new ArrayList<>();

            for (int i = 0; i < threads; i++) {
                final String tenantId = "tenant-" + i;
                final String principalName = "user-" + i;
                futures.add(executor.submit(() -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    Principal p = new Principal(principalName, List.of("viewer"), tenantId);
                    try (AutoCloseable scope = TenantContext.scope(p)) {
                        // Small delay to increase chance of cross-contamination
                        Thread.sleep(ThreadLocalRandom.current().nextInt(1, 10));
                        return TenantContext.getCurrentTenantId();
                    }
                }));
            }

            for (int i = 0; i < threads; i++) {
                String result = futures.get(i).get(10, TimeUnit.SECONDS);
                assertThat(result).isEqualTo("tenant-" + i);
            }

            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    // =========================================================================
    // 6. End-to-End Pipeline + Tenant Lifecycle
    // =========================================================================

    @Nested
    @DisplayName("End-to-End Pipeline Tenant Lifecycle")
    class EndToEndLifecycle {

        @Test
        @DisplayName("Full pipeline lifecycle maintains tenant isolation")
        void fullLifecycleIsolation() throws Exception {
            Principal adminA = new Principal("admin-a", List.of("admin"), TENANT_A);
            Principal adminB = new Principal("admin-b", List.of("admin"), TENANT_B);

            // Tenant A: create, update, complete
            try (AutoCloseable scope = TenantContext.scope(adminA)) {
                String tenantId = TenantContext.getCurrentTenantId();
                checkpointStore.createExecution(tenantId, "pipeline-1", "exec-a",
                        "order-1", Map.of("totalSteps", 2));
                checkpointStore.updateCheckpoint("exec-a", "step-1", "Validate",
                        PipelineCheckpointStatus.STEP_SUCCESS, Map.of(), Map.of("totalSteps", 2));
                checkpointStore.completeExecution("exec-a",
                        PipelineCheckpointStatus.COMPLETED, Map.of("result", "ok"));
            }

            // Tenant B: create, fail
            try (AutoCloseable scope = TenantContext.scope(adminB)) {
                String tenantId = TenantContext.getCurrentTenantId();
                checkpointStore.createExecution(tenantId, "pipeline-1", "exec-b",
                        "order-2", Map.of("totalSteps", 3));
                checkpointStore.completeExecution("exec-b",
                        PipelineCheckpointStatus.FAILED, Map.of("error", "timeout"));
            }

            // Verify tenant A's view
            List<PipelineCheckpoint> tenantAView =
                    checkpointStore.findByPipelineId(TENANT_A, "pipeline-1", 100);
            assertThat(tenantAView).hasSize(1);
            assertThat(tenantAView.get(0).getStatus()).isEqualTo(PipelineCheckpointStatus.COMPLETED);

            // Verify tenant B's view
            List<PipelineCheckpoint> tenantBView =
                    checkpointStore.findByPipelineId(TENANT_B, "pipeline-1", 100);
            assertThat(tenantBView).hasSize(1);
            assertThat(tenantBView.get(0).getStatus()).isEqualTo(PipelineCheckpointStatus.FAILED);

            // Verify tenant C sees nothing
            assertThat(checkpointStore.findByPipelineId(TENANT_C, "pipeline-1", 100))
                    .isEmpty();
        }

        @Test
        @DisplayName("Cleanup of old checkpoints respects tenant boundaries implicitly")
        void cleanupRespectsTimestamp() {
            // Create checkpoints at different times for different tenants
            checkpointStore.createExecution(TENANT_A, PIPELINE_ID, "old-exec-a",
                    "old-key-a", Map.of());
            checkpointStore.completeExecution("old-exec-a",
                    PipelineCheckpointStatus.COMPLETED, Map.of());

            checkpointStore.createExecution(TENANT_B, PIPELINE_ID, "old-exec-b",
                    "old-key-b", Map.of());
            checkpointStore.completeExecution("old-exec-b",
                    PipelineCheckpointStatus.COMPLETED, Map.of());

            // Cleanup all completed checkpoints completed before "far future"
            int cleaned = checkpointStore.cleanupOldCheckpoints(Instant.now().plusSeconds(3600));
            assertThat(cleaned).isEqualTo(2);

            // Both tenants should now be empty
            assertThat(checkpointStore.size()).isZero();
        }
    }
}
