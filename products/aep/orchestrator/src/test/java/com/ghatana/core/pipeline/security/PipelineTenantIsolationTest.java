/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
 *
 * Task 4.9 — Security Hardening: Tenant isolation verification for AEP pipelines.
 * Ensures pipeline execution, checkpoint storage, and context propagation
 * properly enforce tenant boundaries.
 */
package com.ghatana.core.pipeline.security;

import static org.assertj.core.api.Assertions.*;

import com.ghatana.core.operator.catalog.UnifiedOperatorCatalog;
import com.ghatana.core.pipeline.PipelineExecutionContext;
import com.ghatana.orchestrator.store.InMemoryCheckpointStore;
import com.ghatana.orchestrator.store.PipelineCheckpoint;
import com.ghatana.orchestrator.store.PipelineCheckpointStatus;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.governance.security.TenantContext;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;

/**
 * Verifies multi-tenant isolation guarantees across AEP pipeline infrastructure.
 * Tests cover:
 * <ul>
 *   <li>Checkpoint store: data never leaks between tenants</li>
 *   <li>Execution context: tenantId is mandatory and properly scoped</li>
 *   <li>TenantContext + Principal: scope() / clear() lifecycle</li> // GH-90000
 *   <li>Idempotency keys: per-tenant, not global</li>
 *   <li>Concurrent multi-tenant access: no cross-contamination under load</li>
 * </ul>
 */
@DisplayName("Pipeline Tenant Isolation [GH-90000]")
class PipelineTenantIsolationTest {

    private static final String TENANT_A = "tenant-alpha";
    private static final String TENANT_B = "tenant-beta";
    private static final String TENANT_C = "tenant-gamma";
    private static final String PIPELINE_ID = "fraud-detection";

    private InMemoryCheckpointStore checkpointStore;

    @BeforeEach
    void setUp() { // GH-90000
        checkpointStore = new InMemoryCheckpointStore(); // GH-90000
        TenantContext.clear(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        TenantContext.clear(); // GH-90000
    }

    // =========================================================================
    // 1. Checkpoint Store Tenant Isolation
    // =========================================================================

    @Nested
    @DisplayName("Checkpoint Store Isolation [GH-90000]")
    class CheckpointStoreIsolation {

        @Test
        @DisplayName("Checkpoints created by tenant A are invisible to tenant B [GH-90000]")
        void checkpointsInvisibleAcrossTenants() { // GH-90000
            // Tenant A creates an execution
            checkpointStore.createExecution(TENANT_A, PIPELINE_ID, "exec-a1", "idem-a1", Map.of("totalSteps", 3)); // GH-90000

            // Tenant B queries the same pipeline — should find nothing
            List<PipelineCheckpoint> tenantBResults = checkpointStore.findByPipelineId(TENANT_B, PIPELINE_ID, 100); // GH-90000
            assertThat(tenantBResults).isEmpty(); // GH-90000

            // Tenant A sees its own checkpoint
            List<PipelineCheckpoint> tenantAResults = checkpointStore.findByPipelineId(TENANT_A, PIPELINE_ID, 100); // GH-90000
            assertThat(tenantAResults).hasSize(1); // GH-90000
            assertThat(tenantAResults.get(0).getTenantId()).isEqualTo(TENANT_A); // GH-90000
        }

        @Test
        @DisplayName("Idempotency keys are scoped per-tenant [GH-90000]")
        void idempotencyKeysPerTenant() { // GH-90000
            String sharedKey = "order-12345";

            // Tenant A creates with a key
            checkpointStore.createExecution(TENANT_A, PIPELINE_ID, "exec-a", sharedKey, Map.of()); // GH-90000

            // Same key, different tenant — should succeed (not duplicate) // GH-90000
            assertThatCode(() -> checkpointStore.createExecution(TENANT_B, PIPELINE_ID, "exec-b", sharedKey, Map.of())) // GH-90000
                    .doesNotThrowAnyException(); // GH-90000

            // Same key, same tenant — should fail
            assertThatThrownBy(() -> // GH-90000
                            checkpointStore.createExecution(TENANT_A, PIPELINE_ID, "exec-a2", sharedKey, Map.of())) // GH-90000
                    .isInstanceOf(RuntimeException.class) // GH-90000
                    .hasMessageContaining("Duplicate execution [GH-90000]");
        }

        @Test
        @DisplayName("isDuplicate checks are tenant-scoped [GH-90000]")
        void isDuplicateTenantScoped() { // GH-90000
            String key = "unique-key-1";
            checkpointStore.createExecution(TENANT_A, PIPELINE_ID, "exec-1", key, Map.of()); // GH-90000

            assertThat(checkpointStore.isDuplicate(TENANT_A, key)).isTrue(); // GH-90000
            assertThat(checkpointStore.isDuplicate(TENANT_B, key)).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("findByIdempotencyKey only returns matching tenant [GH-90000]")
        void findByIdempotencyKeyTenantScoped() { // GH-90000
            String key = "shared-idemp-key";
            checkpointStore.createExecution(TENANT_A, PIPELINE_ID, "exec-a", key, Map.of("source", "alpha")); // GH-90000
            checkpointStore.createExecution(TENANT_B, PIPELINE_ID, "exec-b", key, Map.of("source", "beta")); // GH-90000

            Optional<PipelineCheckpoint> resultA = checkpointStore.findByIdempotencyKey(TENANT_A, key); // GH-90000
            assertThat(resultA).isPresent(); // GH-90000
            assertThat(resultA.get().getTenantId()).isEqualTo(TENANT_A); // GH-90000

            Optional<PipelineCheckpoint> resultB = checkpointStore.findByIdempotencyKey(TENANT_B, key); // GH-90000
            assertThat(resultB).isPresent(); // GH-90000
            assertThat(resultB.get().getTenantId()).isEqualTo(TENANT_B); // GH-90000

            // Non-existent tenant returns empty
            Optional<PipelineCheckpoint> resultC = checkpointStore.findByIdempotencyKey(TENANT_C, key); // GH-90000
            assertThat(resultC).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("Multiple tenants accumulate checkpoints independently [GH-90000]")
        void multipleTenantAccumulation() { // GH-90000
            for (int i = 0; i < 5; i++) { // GH-90000
                checkpointStore.createExecution(TENANT_A, PIPELINE_ID, "exec-a-" + i, "key-a-" + i, Map.of()); // GH-90000
            }
            for (int i = 0; i < 3; i++) { // GH-90000
                checkpointStore.createExecution(TENANT_B, PIPELINE_ID, "exec-b-" + i, "key-b-" + i, Map.of()); // GH-90000
            }

            assertThat(checkpointStore.findByPipelineId(TENANT_A, PIPELINE_ID, 100)) // GH-90000
                    .hasSize(5); // GH-90000
            assertThat(checkpointStore.findByPipelineId(TENANT_B, PIPELINE_ID, 100)) // GH-90000
                    .hasSize(3); // GH-90000
            assertThat(checkpointStore.findByPipelineId(TENANT_C, PIPELINE_ID, 100)) // GH-90000
                    .isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("Checkpoint state updates do not affect other tenants' checkpoints [GH-90000]")
        void stateUpdatesTenantIsolated() { // GH-90000
            checkpointStore.createExecution(TENANT_A, PIPELINE_ID, "exec-a", "key-a", Map.of("totalSteps", 3)); // GH-90000
            checkpointStore.createExecution(TENANT_B, PIPELINE_ID, "exec-b", "key-b", Map.of("totalSteps", 5)); // GH-90000

            // Update tenant A's checkpoint
            checkpointStore.updateCheckpoint( // GH-90000
                    "exec-a",
                    "step-1",
                    "Filter",
                    PipelineCheckpointStatus.STEP_SUCCESS,
                    Map.of("filtered", 100), // GH-90000
                    Map.of("totalSteps", 3)); // GH-90000

            // Tenant B's checkpoint should be untouched
            PipelineCheckpoint bCheckpoint =
                    checkpointStore.findByInstanceId("exec-b [GH-90000]").orElseThrow();
            assertThat(bCheckpoint.getCompletedSteps()).isZero(); // GH-90000
            assertThat(bCheckpoint.getStatus()).isEqualTo(PipelineCheckpointStatus.CREATED); // GH-90000
        }
    }

    // =========================================================================
    // 2. Execution Context Tenant Scoping
    // =========================================================================

    @Nested
    @DisplayName("Execution Context Tenant Scoping [GH-90000]")
    class ExecutionContextScoping {

        @Test
        @DisplayName("PipelineExecutionContext carries tenantId [GH-90000]")
        void contextCarriesTenantId() { // GH-90000
            PipelineExecutionContext ctx = PipelineExecutionContext.builder() // GH-90000
                    .pipelineId(PIPELINE_ID) // GH-90000
                    .tenantId(TENANT_A) // GH-90000
                    .operatorCatalog(new UnifiedOperatorCatalog()) // GH-90000
                    .deadline(Duration.ofSeconds(30)) // GH-90000
                    .build(); // GH-90000

            assertThat(ctx.getTenantId()).isEqualTo(TENANT_A); // GH-90000
        }

        @Test
        @DisplayName("Two contexts with different tenants are independent [GH-90000]")
        void twoContextsIndependent() { // GH-90000
            PipelineExecutionContext ctxA = PipelineExecutionContext.builder() // GH-90000
                    .pipelineId(PIPELINE_ID) // GH-90000
                    .tenantId(TENANT_A) // GH-90000
                    .executionId("exec-a [GH-90000]")
                    .operatorCatalog(new UnifiedOperatorCatalog()) // GH-90000
                    .deadline(Duration.ofSeconds(30)) // GH-90000
                    .build(); // GH-90000

            PipelineExecutionContext ctxB = PipelineExecutionContext.builder() // GH-90000
                    .pipelineId(PIPELINE_ID) // GH-90000
                    .tenantId(TENANT_B) // GH-90000
                    .executionId("exec-b [GH-90000]")
                    .operatorCatalog(new UnifiedOperatorCatalog()) // GH-90000
                    .deadline(Duration.ofSeconds(30)) // GH-90000
                    .build(); // GH-90000

            assertThat(ctxA.getTenantId()).isNotEqualTo(ctxB.getTenantId()); // GH-90000
            assertThat(ctxA.getExecutionId()).isNotEqualTo(ctxB.getExecutionId()); // GH-90000
        }
    }

    // =========================================================================
    // 3. TenantContext Thread-Local Isolation
    // =========================================================================

    @Nested
    @DisplayName("TenantContext Thread-Local Isolation [GH-90000]")
    class TenantContextIsolation {

        @Test
        @DisplayName("scope() sets and restores tenant context [GH-90000]")
        void scopeSetsAndRestores() throws Exception { // GH-90000
            assertThat(TenantContext.getCurrentTenantId()).isEqualTo("default-tenant [GH-90000]");

            Principal principalA = new Principal("alice", List.of("admin [GH-90000]"), TENANT_A);
            try (AutoCloseable scope = TenantContext.scope(principalA)) { // GH-90000
                assertThat(TenantContext.getCurrentTenantId()).isEqualTo(TENANT_A); // GH-90000
                assertThat(TenantContext.current()).isPresent(); // GH-90000
                assertThat(TenantContext.current().get().getName()).isEqualTo("alice [GH-90000]");
            }

            // After scope closes, back to default
            assertThat(TenantContext.getCurrentTenantId()).isEqualTo("default-tenant [GH-90000]");
            assertThat(TenantContext.current()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("Nested scopes restore previous tenant on exit [GH-90000]")
        void nestedScopesRestore() throws Exception { // GH-90000
            Principal principalA = new Principal("alice", List.of("admin [GH-90000]"), TENANT_A);
            Principal principalB = new Principal("bob", List.of("viewer [GH-90000]"), TENANT_B);

            try (AutoCloseable outerScope = TenantContext.scope(principalA)) { // GH-90000
                assertThat(TenantContext.getCurrentTenantId()).isEqualTo(TENANT_A); // GH-90000

                try (AutoCloseable innerScope = TenantContext.scope(principalB)) { // GH-90000
                    assertThat(TenantContext.getCurrentTenantId()).isEqualTo(TENANT_B); // GH-90000
                    assertThat(TenantContext.current().get().getName()).isEqualTo("bob [GH-90000]");
                }

                // Inner scope closed — back to outer scope's tenant
                assertThat(TenantContext.getCurrentTenantId()).isEqualTo(TENANT_A); // GH-90000
                assertThat(TenantContext.current().get().getName()).isEqualTo("alice [GH-90000]");
            }

            assertThat(TenantContext.getCurrentTenantId()).isEqualTo("default-tenant [GH-90000]");
        }

        @Test
        @DisplayName("clear() removes all context [GH-90000]")
        void clearRemovesContext() { // GH-90000
            TenantContext.setCurrentTenantId(TENANT_A); // GH-90000
            assertThat(TenantContext.getCurrentTenantId()).isEqualTo(TENANT_A); // GH-90000

            TenantContext.clear(); // GH-90000
            assertThat(TenantContext.getCurrentTenantId()).isEqualTo("default-tenant [GH-90000]");
            assertThat(TenantContext.current()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("Thread-local does not cross thread boundaries [GH-90000]")
        void threadLocalDoesNotCrossBoundaries() throws Exception { // GH-90000
            Principal principalA = new Principal("alice", List.of("admin [GH-90000]"), TENANT_A);
            TenantContext.scope(principalA); // GH-90000

            AtomicReference<String> otherThreadTenant = new AtomicReference<>(); // GH-90000
            Thread thread = new Thread(() -> otherThreadTenant.set(TenantContext.getCurrentTenantId())); // GH-90000
            thread.start(); // GH-90000
            thread.join(5000); // GH-90000

            // Other thread defaults to "default-tenant", not TENANT_A
            assertThat(otherThreadTenant.get()).isEqualTo("default-tenant [GH-90000]");
        }

        @Test
        @DisplayName("Explicit scope transfer to child thread works correctly [GH-90000]")
        void explicitScopeTransfer() throws Exception { // GH-90000
            Principal principalA = new Principal("alice", List.of("admin [GH-90000]"), TENANT_A);

            AtomicReference<String> childTenant = new AtomicReference<>(); // GH-90000
            AtomicReference<String> childPrincipalName = new AtomicReference<>(); // GH-90000

            try (AutoCloseable scope = TenantContext.scope(principalA)) { // GH-90000
                // Capture the principal for transfer
                Principal captured = TenantContext.current().orElseThrow(); // GH-90000

                Thread child = new Thread(() -> { // GH-90000
                    try (AutoCloseable childScope = TenantContext.scope(captured)) { // GH-90000
                        childTenant.set(TenantContext.getCurrentTenantId()); // GH-90000
                        childPrincipalName.set(TenantContext.current().get().getName()); // GH-90000
                    } catch (Exception e) { // GH-90000
                        throw new RuntimeException(e); // GH-90000
                    }
                });
                child.start(); // GH-90000
                child.join(5000); // GH-90000
            }

            assertThat(childTenant.get()).isEqualTo(TENANT_A); // GH-90000
            assertThat(childPrincipalName.get()).isEqualTo("alice [GH-90000]");
        }
    }

    // =========================================================================
    // 4. Principal Role-Based Tenant Scoping
    // =========================================================================

    @Nested
    @DisplayName("Principal Tenant Scoping [GH-90000]")
    class PrincipalScoping {

        @Test
        @DisplayName("Principals with same name but different tenants are not equal [GH-90000]")
        void sameNameDifferentTenantNotEqual() { // GH-90000
            Principal p1 = new Principal("alice", List.of("admin [GH-90000]"), TENANT_A);
            Principal p2 = new Principal("alice", List.of("admin [GH-90000]"), TENANT_B);

            assertThat(p1).isNotEqualTo(p2); // GH-90000
            assertThat(p1.hashCode()).isNotEqualTo(p2.hashCode()); // GH-90000
        }

        @Test
        @DisplayName("Principal roles are immutable [GH-90000]")
        void rolesImmutable() { // GH-90000
            Principal p = new Principal("alice", List.of("admin", "editor"), TENANT_A); // GH-90000

            assertThatThrownBy(() -> p.getRoles().add("hacker [GH-90000]")).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("Principal carries tenant identity for TenantContext integration [GH-90000]")
        void principalCarriesTenantForContext() throws Exception { // GH-90000
            Principal p = new Principal("svc-pipeline", List.of("processor [GH-90000]"), TENANT_B);
            try (AutoCloseable scope = TenantContext.scope(p)) { // GH-90000
                assertThat(TenantContext.getCurrentTenantId()).isEqualTo(TENANT_B); // GH-90000
                assertThat(TenantContext.current().get().hasRole("processor [GH-90000]")).isTrue();
                assertThat(TenantContext.current().get().hasRole("admin [GH-90000]")).isFalse();
            }
        }
    }

    // =========================================================================
    // 5. Concurrent Multi-Tenant Access
    // =========================================================================

    @Nested
    @DisplayName("Concurrent Multi-Tenant Access [GH-90000]")
    class ConcurrentAccess {

        @Test
        @DisplayName("Concurrent checkpoint operations maintain tenant isolation [GH-90000]")
        void concurrentCheckpointIsolation() throws Exception { // GH-90000
            int operationsPerTenant = 50;
            ExecutorService executor = Executors.newFixedThreadPool(6); // GH-90000
            CountDownLatch latch = new CountDownLatch(1); // GH-90000
            List<Future<?>> futures = new ArrayList<>(); // GH-90000

            String[] tenants = {TENANT_A, TENANT_B, TENANT_C};
            for (String tenant : tenants) { // GH-90000
                for (int i = 0; i < operationsPerTenant; i++) { // GH-90000
                    final int idx = i;
                    futures.add(executor.submit(() -> { // GH-90000
                        try {
                            latch.await(); // Synchronize start // GH-90000
                        } catch (InterruptedException e) { // GH-90000
                            Thread.currentThread().interrupt(); // GH-90000
                            return;
                        }
                        checkpointStore.createExecution( // GH-90000
                                tenant, PIPELINE_ID, tenant + "-exec-" + idx, tenant + "-key-" + idx, Map.of()); // GH-90000
                    }));
                }
            }

            latch.countDown(); // Release all threads // GH-90000
            for (Future<?> f : futures) { // GH-90000
                f.get(10, TimeUnit.SECONDS); // GH-90000
            }

            executor.shutdown(); // GH-90000
            executor.awaitTermination(5, TimeUnit.SECONDS); // GH-90000

            // Each tenant should see exactly its own checkpoints
            for (String tenant : tenants) { // GH-90000
                List<PipelineCheckpoint> results = checkpointStore.findByPipelineId(tenant, PIPELINE_ID, 200); // GH-90000
                assertThat(results).hasSize(operationsPerTenant).allSatisfy(cp -> assertThat(cp.getTenantId()) // GH-90000
                        .isEqualTo(tenant)); // GH-90000
            }
        }

        @Test
        @DisplayName("Concurrent TenantContext scopes do not interfere across threads [GH-90000]")
        void concurrentTenantContextScopes() throws Exception { // GH-90000
            int threads = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threads); // GH-90000
            CyclicBarrier barrier = new CyclicBarrier(threads); // GH-90000
            List<Future<String>> futures = new ArrayList<>(); // GH-90000

            for (int i = 0; i < threads; i++) { // GH-90000
                final String tenantId = "tenant-" + i;
                final String principalName = "user-" + i;
                futures.add(executor.submit(() -> { // GH-90000
                    barrier.await(5, TimeUnit.SECONDS); // GH-90000
                    Principal p = new Principal(principalName, List.of("viewer [GH-90000]"), tenantId);
                    try (AutoCloseable scope = TenantContext.scope(p)) { // GH-90000
                        // Small delay to increase chance of cross-contamination
                        Thread.sleep(ThreadLocalRandom.current().nextInt(1, 10)); // GH-90000
                        return TenantContext.getCurrentTenantId(); // GH-90000
                    }
                }));
            }

            for (int i = 0; i < threads; i++) { // GH-90000
                String result = futures.get(i).get(10, TimeUnit.SECONDS); // GH-90000
                assertThat(result).isEqualTo("tenant-" + i); // GH-90000
            }

            executor.shutdown(); // GH-90000
            executor.awaitTermination(5, TimeUnit.SECONDS); // GH-90000
        }
    }

    // =========================================================================
    // 6. End-to-End Pipeline + Tenant Lifecycle
    // =========================================================================

    @Nested
    @DisplayName("End-to-End Pipeline Tenant Lifecycle [GH-90000]")
    class EndToEndLifecycle {

        @Test
        @DisplayName("Full pipeline lifecycle maintains tenant isolation [GH-90000]")
        void fullLifecycleIsolation() throws Exception { // GH-90000
            Principal adminA = new Principal("admin-a", List.of("admin [GH-90000]"), TENANT_A);
            Principal adminB = new Principal("admin-b", List.of("admin [GH-90000]"), TENANT_B);

            // Tenant A: create, update, complete
            try (AutoCloseable scope = TenantContext.scope(adminA)) { // GH-90000
                String tenantId = TenantContext.getCurrentTenantId(); // GH-90000
                checkpointStore.createExecution(tenantId, "pipeline-1", "exec-a", "order-1", Map.of("totalSteps", 2)); // GH-90000
                checkpointStore.updateCheckpoint( // GH-90000
                        "exec-a",
                        "step-1",
                        "Validate",
                        PipelineCheckpointStatus.STEP_SUCCESS,
                        Map.of(), // GH-90000
                        Map.of("totalSteps", 2)); // GH-90000
                checkpointStore.completeExecution("exec-a", PipelineCheckpointStatus.COMPLETED, Map.of("result", "ok")); // GH-90000
            }

            // Tenant B: create, fail
            try (AutoCloseable scope = TenantContext.scope(adminB)) { // GH-90000
                String tenantId = TenantContext.getCurrentTenantId(); // GH-90000
                checkpointStore.createExecution(tenantId, "pipeline-1", "exec-b", "order-2", Map.of("totalSteps", 3)); // GH-90000
                checkpointStore.completeExecution( // GH-90000
                        "exec-b", PipelineCheckpointStatus.FAILED, Map.of("error", "timeout")); // GH-90000
            }

            // Verify tenant A's view
            List<PipelineCheckpoint> tenantAView = checkpointStore.findByPipelineId(TENANT_A, "pipeline-1", 100); // GH-90000
            assertThat(tenantAView).hasSize(1); // GH-90000
            assertThat(tenantAView.get(0).getStatus()).isEqualTo(PipelineCheckpointStatus.COMPLETED); // GH-90000

            // Verify tenant B's view
            List<PipelineCheckpoint> tenantBView = checkpointStore.findByPipelineId(TENANT_B, "pipeline-1", 100); // GH-90000
            assertThat(tenantBView).hasSize(1); // GH-90000
            assertThat(tenantBView.get(0).getStatus()).isEqualTo(PipelineCheckpointStatus.FAILED); // GH-90000

            // Verify tenant C sees nothing
            assertThat(checkpointStore.findByPipelineId(TENANT_C, "pipeline-1", 100)) // GH-90000
                    .isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("Cleanup of old checkpoints respects tenant boundaries implicitly [GH-90000]")
        void cleanupRespectsTimestamp() { // GH-90000
            // Create checkpoints at different times for different tenants
            checkpointStore.createExecution(TENANT_A, PIPELINE_ID, "old-exec-a", "old-key-a", Map.of()); // GH-90000
            checkpointStore.completeExecution("old-exec-a", PipelineCheckpointStatus.COMPLETED, Map.of()); // GH-90000

            checkpointStore.createExecution(TENANT_B, PIPELINE_ID, "old-exec-b", "old-key-b", Map.of()); // GH-90000
            checkpointStore.completeExecution("old-exec-b", PipelineCheckpointStatus.COMPLETED, Map.of()); // GH-90000

            // Cleanup all completed checkpoints completed before "far future"
            int cleaned = checkpointStore.cleanupOldCheckpoints(Instant.now().plusSeconds(3600)); // GH-90000
            assertThat(cleaned).isEqualTo(2); // GH-90000

            // Both tenants should now be empty
            assertThat(checkpointStore.size()).isZero(); // GH-90000
        }
    }
}
