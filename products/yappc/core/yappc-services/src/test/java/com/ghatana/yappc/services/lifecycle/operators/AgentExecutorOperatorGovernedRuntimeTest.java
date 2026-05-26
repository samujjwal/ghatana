package com.ghatana.yappc.services.lifecycle.operators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.GeneratorMetadata;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.agent.spi.AgentRegistry;
import com.ghatana.platform.domain.event.Event;
import com.ghatana.platform.domain.event.GEvent;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.workflow.operator.OperatorResult;
import com.ghatana.yappc.infrastructure.datacloud.repository.AgentStateRepository;
import com.ghatana.yappc.agent.StepContext;
import com.ghatana.yappc.agent.StepContract;
import com.ghatana.yappc.agent.StepRequest;
import com.ghatana.yappc.agent.StepResult;
import com.ghatana.yappc.agent.ValidationResult;
import com.ghatana.yappc.agent.YAPPCAgentBase;
import com.ghatana.yappc.agent.YappcAgentRegistryAdapter;
import com.ghatana.yappc.agent.YappcAgentSystem;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AgentExecutorOperator governed runtime tests")
class AgentExecutorOperatorGovernedRuntimeTest extends EventloopTestBase {

    private static final String TENANT_ID = "tenant-1";
    private static final String RUN_ID = "run-001";
    private static final String STEP_NAME = "architecture.intake";

    @Test
    @DisplayName("persists agent execution state transitions through AgentStateRepository")
    void persistsAgentExecutionStateTransitions() {
        AgentRegistry platformRegistry = mock(AgentRegistry.class);
        when(platformRegistry.register(any(), any())).thenReturn(Promise.complete());
        YappcAgentRegistryAdapter registry = new YappcAgentRegistryAdapter(platformRegistry);
        CapturingAgent agent = new CapturingAgent(MemoryStore.noOp());
        runPromise(() -> registry.register(agent));

        YappcAgentSystem agentSystem = YappcAgentSystem.builder()
                .eventloop(eventloop())
                .memoryStore(MemoryStore.noOp())
                .aiRuntimeMode(YappcAgentSystem.AiRuntimeMode.STUB)
                .aepEventPublisher((eventType, tenantId, payload) -> Promise.complete())
                .sdlcRegistry(registry)
                .build();
        runPromise(agentSystem::initialize);

        UUID executionId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        AgentStateRepository stateRepository = mock(AgentStateRepository.class);
        when(stateRepository.create(eq(STEP_NAME), eq("architecture"), isNull(), any()))
                .thenReturn(Promise.of(executionId));
        when(stateRepository.markRunning(executionId)).thenReturn(Promise.complete());
        when(stateRepository.markSucceeded(eq(executionId), any())).thenReturn(Promise.complete());

        AgentExecutorOperator operator = new AgentExecutorOperator(agentSystem, stateRepository);

        OperatorResult result = runPromise(() -> operator.process(dispatchEvent(STEP_NAME)));

        assertThat(result.isSuccess()).isTrue();
        var inOrder = inOrder(stateRepository);
        inOrder.verify(stateRepository).create(eq(STEP_NAME), eq("architecture"), isNull(), any());
        inOrder.verify(stateRepository).markRunning(executionId);
        inOrder.verify(stateRepository).markSucceeded(eq(executionId), any());
    }

    @Test
    @DisplayName("marks agent execution failed when runtime emits error result")
    void marksAgentExecutionFailedWhenRuntimeEmitsError() {
        YappcAgentSystem agentSystem = YappcAgentSystem.builder()
                .eventloop(eventloop())
                .memoryStore(MemoryStore.noOp())
                .aiRuntimeMode(YappcAgentSystem.AiRuntimeMode.STUB)
                .aepEventPublisher((eventType, tenantId, payload) -> Promise.complete())
                .sdlcRegistry(new YappcAgentRegistryAdapter(mock(AgentRegistry.class)))
                .build();

        UUID executionId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        AgentStateRepository stateRepository = mock(AgentStateRepository.class);
        when(stateRepository.create(eq(STEP_NAME), eq("architecture"), isNull(), any()))
                .thenReturn(Promise.of(executionId));
        when(stateRepository.markRunning(executionId)).thenReturn(Promise.complete());
        when(stateRepository.markFailed(eq(executionId), any())).thenReturn(Promise.complete());

        AgentExecutorOperator operator = new AgentExecutorOperator(agentSystem, stateRepository);

        OperatorResult result = runPromise(() -> operator.process(dispatchEvent(STEP_NAME)));

        assertThat(result.isSuccess()).isTrue();
        Event output = result.getOutputEvents().get(0);
        assertThat(output.getPayload("status")).isEqualTo("error");
        verify(stateRepository).markFailed(eq(executionId), eq("agent_system_not_initialized"));
    }

    @Test
    @DisplayName("dispatches registered YAPPC agent through governed WorkflowStepOperatorAdapter")
    void dispatchesRegisteredAgentThroughGovernedRuntime() {
        AgentRegistry platformRegistry = mock(AgentRegistry.class);
        when(platformRegistry.register(any(), any())).thenReturn(Promise.complete());
        YappcAgentRegistryAdapter registry = new YappcAgentRegistryAdapter(platformRegistry);
        CapturingAgent agent = new CapturingAgent(MemoryStore.noOp());
        runPromise(() -> registry.register(agent));

        YappcAgentSystem agentSystem = YappcAgentSystem.builder()
                .eventloop(eventloop())
                .memoryStore(MemoryStore.noOp())
                .aiRuntimeMode(YappcAgentSystem.AiRuntimeMode.STUB)
                .aepEventPublisher((eventType, tenantId, payload) -> Promise.complete())
                .sdlcRegistry(registry)
                .build();
        runPromise(agentSystem::initialize);

        AgentExecutorOperator operator = new AgentExecutorOperator(agentSystem);

        OperatorResult result = runPromise(() -> operator.process(dispatchEvent(STEP_NAME)));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutputEvents()).hasSize(1);

        Event output = result.getOutputEvents().get(0);
        assertThat(output.getType()).isEqualTo(AgentExecutorOperator.EVENT_RESULT_PRODUCED);
        assertThat(output.getPayload("status")).isEqualTo("success");
        assertThat(output.getPayload("capabilityRef")).isEqualTo(STEP_NAME);
        assertThat(output.getPayload("operatorContract")).isEqualTo("EventOperatorCapability");
        assertThat(output.getPayload("policy")).isEqualTo("yappc-agent-runtime-policy");
        assertThat(output.getPayload("approval")).isEqualTo("required-for-destructive-actions");
        assertThat(output.getPayload("idempotencyKey")).isEqualTo(RUN_ID + ":" + STEP_NAME);
        assertThat(output.getPayload("audit")).isEqualTo("agent.dispatch.governed");
        assertThat(output.getPayload("outputValidation")).isEqualTo("operator-result-validated");

        assertThat(agent.capturedInput.get()).containsEntry("agentId", STEP_NAME);
        assertThat(agent.capturedInput.get()).containsEntry("tenantId", TENANT_ID);
        assertThat(agent.capturedInput.get()).containsEntry("correlationId", RUN_ID);
        assertThat(agent.capturedContext.get().tenantId()).isEqualTo(TENANT_ID);
        assertThat(agent.capturedContext.get().runId()).isEqualTo(RUN_ID);
        assertThat(agent.capturedContext.get().phase()).isEqualTo("architecture");
    }

    @Test
    @DisplayName("returns auditable error event when runtime is not initialized")
    void emitsAuditableErrorWhenRuntimeIsNotInitialized() {
        YappcAgentSystem agentSystem = YappcAgentSystem.builder()
                .eventloop(eventloop())
                .memoryStore(MemoryStore.noOp())
                .aiRuntimeMode(YappcAgentSystem.AiRuntimeMode.STUB)
                .aepEventPublisher((eventType, tenantId, payload) -> Promise.complete())
                .sdlcRegistry(new YappcAgentRegistryAdapter(mock(AgentRegistry.class)))
                .build();

        AgentExecutorOperator operator = new AgentExecutorOperator(agentSystem);

        OperatorResult result = runPromise(() -> operator.process(dispatchEvent(STEP_NAME)));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutputEvents()).hasSize(1);
        Event output = result.getOutputEvents().get(0);
        assertThat(output.getPayload("status")).isEqualTo("error");
        assertThat(output.getPayload("_error")).isEqualTo("agent_system_not_initialized");
    }

    @Test
    @DisplayName("returns auditable error event when capabilityRef cannot resolve")
    void emitsAuditableErrorWhenCapabilityCannotResolve() {
        YappcAgentSystem agentSystem = YappcAgentSystem.builder()
                .eventloop(eventloop())
                .memoryStore(MemoryStore.noOp())
                .aiRuntimeMode(YappcAgentSystem.AiRuntimeMode.STUB)
                .aepEventPublisher((eventType, tenantId, payload) -> Promise.complete())
                .sdlcRegistry(new YappcAgentRegistryAdapter(mock(AgentRegistry.class)))
                .build();
        runPromise(agentSystem::initialize);

        AgentExecutorOperator operator = new AgentExecutorOperator(agentSystem);

        OperatorResult result = runPromise(() -> operator.process(dispatchEvent("architecture.missing")));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutputEvents()).hasSize(1);
        Event output = result.getOutputEvents().get(0);
        assertThat(output.getPayload("status")).isEqualTo("error");
        assertThat(output.getPayload("_error")).isEqualTo("agent_not_found");
        assertThat(output.getPayload("agentId")).isEqualTo("architecture.missing");
    }

    private static Event dispatchEvent(String agentId) {
        return GEvent.builder()
                .typeTenantVersion(TENANT_ID, AgentExecutorOperator.EVENT_DISPATCH_VALIDATED, "v1")
                .addPayload("agentId", agentId)
                .addPayload("fromStage", "intent")
                .addPayload("toStage", "architecture")
                .addPayload("tenantId", TENANT_ID)
                .addPayload("correlationId", RUN_ID)
                .build();
    }

    static final class CapturingAgent extends YAPPCAgentBase<Map<String, Object>, Map<String, Object>> {
        private static final StepContract CONTRACT = new StepContract(
                STEP_NAME,
                "#/definitions/AgentDispatchInput",
                "#/definitions/AgentDispatchOutput",
                List.of("EventOperatorCapability"),
                Map.of("runtime", "governed"));

        private final MemoryStore memoryStore;
        private final AtomicReference<Map<String, Object>> capturedInput = new AtomicReference<>();
        private final AtomicReference<StepContext> capturedContext = new AtomicReference<>();

        CapturingAgent(MemoryStore memoryStore) {
            super("agent-architecture-intake", STEP_NAME, CONTRACT, new NoopGenerator(), defaultEventPublisher());
            this.memoryStore = memoryStore;
        }

        @Override
        protected MemoryStore getMemoryStore() {
            return memoryStore;
        }

        @Override
        public ValidationResult validateInput(@NotNull Map<String, Object> input) {
            capturedInput.set(input);
            return input.containsKey("correlationId")
                    ? ValidationResult.success()
                    : ValidationResult.fail("correlationId missing");
        }

        @Override
        public Promise<StepResult<Map<String, Object>>> execute(
                @NotNull Map<String, Object> input,
                @NotNull StepContext context) {
            capturedInput.set(input);
            capturedContext.set(context);
            Instant started = Instant.now();
            return Promise.of(StepResult.success(
                    Map.of("handled", true),
                    Map.of("runtime", "governed"),
                    started,
                    Instant.now()));
        }
    }

    private static final class NoopGenerator
            implements OutputGenerator<StepRequest<Map<String, Object>>, StepResult<Map<String, Object>>> {
        @Override
        public @NotNull Promise<StepResult<Map<String, Object>>> generate(
                @NotNull StepRequest<Map<String, Object>> input,
                @NotNull AgentContext context) {
            Instant started = Instant.now();
            return Promise.of(StepResult.success(Map.of(), Map.of(), started, Instant.now()));
        }

        @Override
        public @NotNull Promise<Double> estimateCost(
                @NotNull StepRequest<Map<String, Object>> input,
                @NotNull AgentContext context) {
            return Promise.of(0.0);
        }

        @Override
        public @NotNull GeneratorMetadata getMetadata() {
            return GeneratorMetadata.builder()
                    .name("NoopGenerator")
                    .type("test")
                    .description("No-op generator for governed runtime tests")
                    .version("1.0.0")
                    .build();
        }
    }
}
