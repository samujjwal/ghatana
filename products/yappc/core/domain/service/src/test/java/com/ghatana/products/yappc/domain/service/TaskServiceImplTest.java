package com.ghatana.products.yappc.domain.service;

import com.ghatana.agent.AgentCapabilities;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.platform.observability.NoopMetricsCollector;
import com.ghatana.products.yappc.domain.agent.*;
import com.ghatana.products.yappc.domain.task.*;
import com.ghatana.products.yappc.service.CapabilityMatcher;
import com.ghatana.products.yappc.service.TaskOrchestrator;
import com.ghatana.products.yappc.service.TaskServiceImpl;
import com.ghatana.products.yappc.service.TaskValidator;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TaskServiceImpl")
class TaskServiceImplTest extends EventloopTestBase {

    @Test
    void shouldExecuteTaskWithDependenciesAndEnrichInputs() {
        // GIVEN
        String capability = "sdlc.execute";

        AgentRegistry agentRegistry = new AgentRegistry(new NoopMetricsCollector());
        agentRegistry.register(new EchoAgent(capability));

        TaskDefinition dep = TaskDefinition.builder()
                .id("dep-task")
                .name("Dependency Task")
                .description("Produces an intermediate output")
                .domain("testing")
                .phase(SDLCPhase.IMPLEMENTATION)
                .requiredCapabilities(List.of(capability))
                .parameters(Map.of())
                .complexity(TaskComplexity.SIMPLE)
                .dependencies(List.of())
                .metadata(Map.of())
                .build();

        TaskDefinition root = TaskDefinition.builder()
                .id("root-task")
                .name("Root Task")
                .description("Consumes dependency output")
                .domain("testing")
                .phase(SDLCPhase.IMPLEMENTATION)
                .requiredCapabilities(List.of(capability))
                .parameters(Map.of())
                .complexity(TaskComplexity.SIMPLE)
                .dependencies(List.of(TaskDependency.required(dep.id(), "Needs dep output")))
                .metadata(Map.of())
                .build();

        TaskRegistry registry = new TaskRegistry(List.of());
        registry.register(dep);
        registry.register(root);

        TaskOrchestrator orchestrator = new TaskOrchestrator(agentRegistry, new CapabilityMatcher());
        TaskServiceImpl taskService = new TaskServiceImpl(registry, orchestrator, new TaskValidator());

        TaskExecutionContext context = TaskExecutionContext.builder()
                .userId("u1")
                .tenantId("t1")
                .traceId("trace-1")
                .metadata(Map.of())
                .build();

        Map<String, Object> input = Map.of(
                "depOutput", "@" + dep.id(),
                "note", "hello"
        );

        // WHEN
        TaskResult<Map<String, Object>> result = runPromise(() ->
                taskService.<Map<String, Object>, Map<String, Object>>executeTask(root.id(), input, context)
                        .map(r -> (TaskResult<Map<String, Object>>) r)
        );

        // THEN
        assertThat(result.status()).isEqualTo(TaskExecutionStatus.COMPLETED);
        assertThat(result.output()).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> returnedInput = (Map<String, Object>) result.output().get("input");
        assertThat(returnedInput).isNotNull();
        assertThat(returnedInput.get("note")).isEqualTo("hello");

        Object depOutput = returnedInput.get("depOutput");
        assertThat(depOutput).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) depOutput).get("taskId")).isEqualTo(dep.id());
    }

    /**
     * Simple deterministic agent for testing task orchestration.
     *
     * @doc.type class
     * @doc.purpose Test agent that echoes input and taskId
     * @doc.layer product
     * @doc.pattern Test Double
     */
    private static final class EchoAgent implements AIAgent<Object, Object> {
        private final AgentMetadata metadata;

        private EchoAgent(@NotNull String capability) {
            this.metadata = new AgentMetadata(
                    AgentName.COPILOT_AGENT,
                    "test",
                    "Echo agent for tests",
                    List.of(capability),
                    List.of("test"),
                    10L,
                    null
            );
        }

        @Override
        @NotNull
        public String getId() {
            return metadata.name().name();
        }

        @Override
        @NotNull
        public AgentCapabilities getCapabilities() {
            // TaskOrchestrator uses AgentRegistry metadata capability matching, not this.
            return new AgentCapabilities(
                    metadata.name().getDisplayName(),
                    "test",
                    "test",
                    Set.of(),
                    Set.of()
            );
        }

        @Override
        @NotNull
        public Promise<Void> initialize(@NotNull AgentContext context) {
            return Promise.complete();
        }

        @Override
        @NotNull
        public Promise<Void> start() {
            return Promise.complete();
        }

        @Override
        @NotNull
        public <T, R> Promise<R> process(@NotNull T task, @NotNull AgentContext context) {
            String taskId = String.valueOf(context.getConfig("taskId"));
            Map<String, Object> data = Map.of(
                    "taskId", taskId,
                    "input", task
            );

            AgentResult.AgentMetrics metrics = AgentResult.AgentMetrics.builder()
                    .latencyMs(1)
                    .modelVersion("test")
                    .build();
            AgentResult.AgentTrace trace = new AgentResult.AgentTrace(
                    metadata.name().getDisplayName(),
                    "req-1",
                    Instant.now(),
                    Map.of()
            );

            @SuppressWarnings("unchecked")
            R out = (R) AgentResult.success(data, metrics, trace);
            return Promise.of(out);
        }

        @Override
        @NotNull
        public Promise<Void> shutdown() {
            return Promise.complete();
        }

        @Override
        @NotNull
        public Promise<AgentResult<Object>> execute(@NotNull Object input, @NotNull AIAgentContext context) {
            return Promise.ofException(new UnsupportedOperationException("Not used by TaskAgentAdapter"));
        }

        @Override
        public long getLatencySLA() {
            return metadata.latencySLA();
        }

        @Override
        @NotNull
        public Promise<AgentHealth> healthCheck() {
            return Promise.of(AgentHealth.healthy(1));
        }

        @Override
        @NotNull
        public AgentMetadata getMetadata() {
            return metadata;
        }

        @Override
        public void validateInput(@NotNull Object input) {
            // no-op for test
        }
    }
}
