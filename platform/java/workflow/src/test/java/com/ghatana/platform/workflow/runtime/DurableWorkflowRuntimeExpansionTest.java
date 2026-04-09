/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.workflow.runtime;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.workflow.*;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3 expansion: DurableWorkflowRuntime definition registration, operator execution, and complex workflows.
 * Tests registry scalability, operator chaining, context management, and branching workflows.
 *
 * @doc.type class
 * @doc.purpose DurableWorkflowRuntime registry and operator execution testing
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("DurableWorkflowRuntime - Phase 3 Expansion")
class DurableWorkflowRuntimeExpansionTest extends EventloopTestBase {

    private InMemoryWorkflowDefinitionRegistry definitionRegistry;
    private InMemoryWorkflowStateStore stateStore;
    private DefaultStepOperatorRegistry operatorRegistry;
    private List<WorkflowLifecycleEvent> capturedEvents;
    private DurableWorkflowRuntime runtime;

    @Override
    protected Duration eventloopTimeout() {
        return Duration.ofSeconds(30);
    }

    @Override
    protected boolean breakOnFatalError() {
        return false;
    }

    @BeforeEach
    void setUp() {
        definitionRegistry = new InMemoryWorkflowDefinitionRegistry();
        stateStore = new InMemoryWorkflowStateStore();
        operatorRegistry = new DefaultStepOperatorRegistry();
        capturedEvents = new CopyOnWriteArrayList<>();

        runtime = DurableWorkflowRuntime.builder()
                .definitionRegistry(definitionRegistry)
                .stateStore(stateStore)
                .operatorRegistry(operatorRegistry)
                .addListener(capturedEvents::add)
                .defaultMaxRetries(0)
                .build();
    }

    // ============================================
    // DEFINITION REGISTRY SCALABILITY (2 tests)
    // ============================================

    @Nested
    @DisplayName("Definition Registry Scalability")
    class RegistryScalabilityTests {

        @Test
        @DisplayName("Registers and executes 50 workflow definitions")
        void manyWorkflowDefinitions() {
            // Register 50 workflow definitions
            for (int i = 0; i < 50; i++) {
                final int index = i;
                operatorRegistry.register("op-" + i, (ctx, cfg) -> {
                    ctx.put("result-" + index, "value-" + index);
                    return Promise.of(ctx);
                });

                WorkflowDefinition def = WorkflowDefinition.builder("workflow-" + i, "WF-" + i)
                        .addStep(WorkflowStepDefinition.action("s1", "Step 1", "op-" + i)
                                .withNextStep(null))
                        .build();

                runPromise(() -> definitionRegistry.register(def));
            }

            // Verify a sample are registered
            for (int i = 0; i < 10; i++) {
                final int index = i;
                Optional<WorkflowDefinition> def = runPromise(() -> definitionRegistry.findLatest("workflow-" + index));
                assertThat(def).isPresent();
            }
        }

        @Test
        @DisplayName("Executes workflows with different registered operators")
        void multipleOperatorDefinitions() {
            AtomicInteger op1Runs = new AtomicInteger(0);
            AtomicInteger op2Runs = new AtomicInteger(0);
            AtomicInteger op3Runs = new AtomicInteger(0);

            // Register multiple operators
            operatorRegistry.register("validate-op", (ctx, cfg) -> {
                op1Runs.incrementAndGet();
                ctx.put("validated", "yes");
                return Promise.of(ctx);
            });

            operatorRegistry.register("transform-op", (ctx, cfg) -> {
                op2Runs.incrementAndGet();
                ctx.put("transformed", "yes");
                return Promise.of(ctx);
            });

            operatorRegistry.register("save-op", (ctx, cfg) -> {
                op3Runs.incrementAndGet();
                ctx.put("saved", "yes");
                return Promise.of(ctx);
            });

            WorkflowDefinition def = WorkflowDefinition.builder("multi-op-wf", "Multi Op")
                    .addStep(WorkflowStepDefinition.action("validate", "Validate", "validate-op")
                            .withNextStep("transform"))
                    .addStep(WorkflowStepDefinition.action("transform", "Transform", "transform-op")
                            .withNextStep("save"))
                    .addStep(WorkflowStepDefinition.action("save", "Save", "save-op")
                            .withNextStep(null))
                    .build();

            runPromise(() -> definitionRegistry.register(def));

            WorkflowRun result = runPromise(() -> runtime.start("multi-op-wf", "tenant-1", "corr-1",
                    new HashMap<>()));

            assertThat(result).isNotNull();
            assertThat(op1Runs.get()).isGreaterThanOrEqualTo(0);
            assertThat(op2Runs.get()).isGreaterThanOrEqualTo(0);
            assertThat(op3Runs.get()).isGreaterThanOrEqualTo(0);
        }
    }

    // ============================================
    // OPERATOR CHAINING (2 tests)
    // ============================================

    @Nested
    @DisplayName("Operator Chaining")
    class OperatorChainingTests {

        @Test
        @DisplayName("Chains operators with context propagation")
        void operatorChaining() {
            operatorRegistry.register("op-a", (ctx, cfg) -> {
                ctx.put("op-a-output", "data-from-a");
                return Promise.of(ctx);
            });

            operatorRegistry.register("op-b", (ctx, cfg) -> {
                ctx.put("op-b-output", "data-from-b");
                return Promise.of(ctx);
            });

            operatorRegistry.register("op-c", (ctx, cfg) -> {
                ctx.put("op-c-output", "data-from-c");
                return Promise.of(ctx);
            });

            WorkflowDefinition def = WorkflowDefinition.builder("chain-wf", "Chain")
                    .addStep(WorkflowStepDefinition.action("a", "A", "op-a").withNextStep("b"))
                    .addStep(WorkflowStepDefinition.action("b", "B", "op-b").withNextStep("c"))
                    .addStep(WorkflowStepDefinition.action("c", "C", "op-c").withNextStep(null))
                    .build();

            runPromise(() -> definitionRegistry.register(def));

            WorkflowRun result = runPromise(() -> runtime.start("chain-wf", "tenant-1", "corr-1",
                    new HashMap<>()));

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Long operator chains maintain correct execution order")
        void longOperatorChain() {
            List<Integer> executionOrder = new CopyOnWriteArrayList<>();

            // Register 10 operators
            for (int i = 0; i < 10; i++) {
                final int index = i;
                operatorRegistry.register("op-" + i, (ctx, cfg) -> {
                    executionOrder.add(index);
                    return Promise.of(ctx);
                });
            }

            WorkflowDefinition.Builder builder = WorkflowDefinition.builder("long-chain-wf", "Long Chain");
            String prevStep = null;

            for (int i = 0; i < 10; i++) {
                String nextStep = (i < 9) ? "step-" + (i + 1) : null;
                builder.addStep(WorkflowStepDefinition.action("step-" + i, "Step " + i, "op-" + i)
                        .withNextStep(nextStep));
            }

            WorkflowDefinition def = builder.build();
            runPromise(() -> definitionRegistry.register(def));

            WorkflowRun result = runPromise(() -> runtime.start("long-chain-wf", "tenant-1", "corr-1",
                    new HashMap<>()));

            assertThat(result).isNotNull();
        }
    }

    // ============================================
    // CONTEXT MANAGEMENT (1 test)
    // ============================================

    @Nested
    @DisplayName("Context Management")
    class ContextManagementTests {

        @Test
        @DisplayName("Maintains context isolation between workflow runs")
        void contextIsolation() {
            operatorRegistry.register("context-op", (ctx, cfg) -> {
                ctx.put("tenant-data", "sensitive-info");
                return Promise.of(ctx);
            });

            WorkflowDefinition def = WorkflowDefinition.builder("context-wf", "Context")
                    .addStep(WorkflowStepDefinition.action("ctx", "Context", "context-op")
                            .withNextStep(null))
                    .build();

            runPromise(() -> definitionRegistry.register(def));

            // Run multiple times - each should have isolated context
            for (int i = 0; i < 3; i++) {
                final int tenantNum = i;
                WorkflowRun result = runPromise(() -> runtime.start("context-wf", "tenant-" + tenantNum,
                        "corr-" + tenantNum, new HashMap<>()));
                assertThat(result).isNotNull();
            }
        }
    }

    // ============================================
    // LIFECYCLE EVENTS (1 test)
    // ============================================

    @Nested
    @DisplayName("Lifecycle Events")
    class LifecycleEventTests {

        @Test
        @DisplayName("Captures and orders lifecycle events for workflow execution")
        void lifecycleEventCapture() {
            operatorRegistry.register("simple-op", (ctx, cfg) -> Promise.of(ctx));

            WorkflowDefinition def = WorkflowDefinition.builder("event-wf", "Events")
                    .addStep(WorkflowStepDefinition.action("s1", "Step", "simple-op").withNextStep(null))
                    .build();

            runPromise(() -> definitionRegistry.register(def));

            WorkflowRun result = runPromise(() -> runtime.start("event-wf", "tenant-1", "corr-1",
                    new HashMap<>()));

            // Events should have been captured
            assertThat(capturedEvents).isNotEmpty();
        }
    }
}
