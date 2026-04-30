package com.ghatana.yappc.domain.service;

import com.ghatana.platform.observability.NoopMetricsCollector;
import com.ghatana.yappc.domain.agent.*;
import com.ghatana.yappc.domain.task.*;
import com.ghatana.yappc.service.CapabilityMatcher;
import com.ghatana.yappc.service.TaskOrchestrator;
import com.ghatana.yappc.service.TaskServiceImpl;
import com.ghatana.yappc.service.TaskValidator;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TaskServiceImpl")
class TaskServiceImplTest extends EventloopTestBase {

    @Test
    void shouldExecuteTaskWithDependenciesAndEnrichInputs() { // GH-90000
        // GIVEN
        String capability = "sdlc.execute";

        AgentRegistry agentRegistry = new AgentRegistry(new NoopMetricsCollector()); // GH-90000
        agentRegistry.register(new EchoAgent(capability)); // GH-90000

        TaskDefinition dep = TaskDefinition.builder() // GH-90000
                .id("dep-task")
                .name("Dependency Task")
                .description("Produces an intermediate output")
                .domain("testing")
                .phase(SDLCPhase.IMPLEMENTATION) // GH-90000
                .requiredCapabilities(List.of(capability)) // GH-90000
                .parameters(Map.of()) // GH-90000
                .complexity(TaskComplexity.SIMPLE) // GH-90000
                .dependencies(List.of()) // GH-90000
                .metadata(Map.of()) // GH-90000
                .build(); // GH-90000

        TaskDefinition root = TaskDefinition.builder() // GH-90000
                .id("root-task")
                .name("Root Task")
                .description("Consumes dependency output")
                .domain("testing")
                .phase(SDLCPhase.IMPLEMENTATION) // GH-90000
                .requiredCapabilities(List.of(capability)) // GH-90000
                .parameters(Map.of()) // GH-90000
                .complexity(TaskComplexity.SIMPLE) // GH-90000
                .dependencies(List.of(TaskDependency.required(dep.id(), "Needs dep output"))) // GH-90000
                .metadata(Map.of()) // GH-90000
                .build(); // GH-90000

        TaskRegistry registry = new TaskRegistry(List.of()); // GH-90000
        registry.register(dep); // GH-90000
        registry.register(root); // GH-90000

        TaskOrchestrator orchestrator = new TaskOrchestrator(agentRegistry, new CapabilityMatcher()); // GH-90000
        TaskServiceImpl taskService = new TaskServiceImpl(registry, orchestrator, new TaskValidator()); // GH-90000

        TaskExecutionContext context = TaskExecutionContext.builder() // GH-90000
                .userId("u1")
                .tenantId("t1")
                .traceId("trace-1")
                .metadata(Map.of()) // GH-90000
                .build(); // GH-90000

        Map<String, Object> input = Map.of( // GH-90000
                "depOutput", "@" + dep.id(), // GH-90000
                "note", "hello"
        );

        // WHEN
        TaskResult<Map<String, Object>> result = runPromise(() -> // GH-90000
            taskService.<Map<String, Object>, Map<String, Object>>executeTask(root.id(), input, context) // GH-90000
        );

        // THEN
        assertThat(result.status()).isEqualTo(TaskExecutionStatus.COMPLETED); // GH-90000
        assertThat(result.output()).isNotNull(); // GH-90000

        @SuppressWarnings("unchecked")
        Map<String, Object> returnedInput = (Map<String, Object>) result.output().get("input");
        assertThat(returnedInput).isNotNull(); // GH-90000
        assertThat(returnedInput.get("note")).isEqualTo("hello");

        Object depOutput = returnedInput.get("depOutput");
        assertThat(depOutput).isInstanceOf(Map.class); // GH-90000
        assertThat(((Map<?, ?>) depOutput).get("taskId")).isEqualTo(dep.id());
    }

        @Test
        void shouldFilterTaskHistoryByTenantId() { // GH-90000
        String capability = "sdlc.execute";

        AgentRegistry agentRegistry = new AgentRegistry(new NoopMetricsCollector()); // GH-90000
        agentRegistry.register(new EchoAgent(capability)); // GH-90000

        TaskDefinition task = TaskDefinition.builder() // GH-90000
            .id("tenant-task")
            .name("Tenant Task")
            .description("Tenant-scoped history validation")
            .domain("testing")
            .phase(SDLCPhase.IMPLEMENTATION) // GH-90000
            .requiredCapabilities(List.of(capability)) // GH-90000
            .parameters(Map.of()) // GH-90000
            .complexity(TaskComplexity.SIMPLE) // GH-90000
            .dependencies(List.of()) // GH-90000
            .metadata(Map.of()) // GH-90000
            .build(); // GH-90000

        TaskRegistry registry = new TaskRegistry(List.of()); // GH-90000
        registry.register(task); // GH-90000

        TaskOrchestrator orchestrator = new TaskOrchestrator(agentRegistry, new CapabilityMatcher()); // GH-90000
        TaskServiceImpl taskService = new TaskServiceImpl(registry, orchestrator, new TaskValidator()); // GH-90000

        TaskExecutionContext tenantAContext = TaskExecutionContext.builder() // GH-90000
            .userId("shared-user")
            .tenantId("tenant-a")
            .traceId("trace-a")
            .metadata(Map.of()) // GH-90000
            .build(); // GH-90000

        TaskExecutionContext tenantBContext = TaskExecutionContext.builder() // GH-90000
            .userId("shared-user")
            .tenantId("tenant-b")
            .traceId("trace-b")
            .metadata(Map.of()) // GH-90000
            .build(); // GH-90000

        runPromise(() -> taskService.executeTask(task.id(), Map.of("k", "v-a"), tenantAContext)); // GH-90000
        runPromise(() -> taskService.executeTask(task.id(), Map.of("k", "v-b"), tenantBContext)); // GH-90000

        TaskHistoryFilter tenantAFilter = TaskHistoryFilter.builder() // GH-90000
            .tenantId("tenant-a")
            .userId("shared-user")
            .limit(50) // GH-90000
            .build(); // GH-90000

        List<TaskExecution> filtered = runPromise(() -> taskService.getTaskHistory(tenantAFilter)); // GH-90000

        assertThat(filtered).isNotEmpty(); // GH-90000
        assertThat(filtered).allMatch(execution -> "tenant-a".equals(execution.metadata().get("tenantId")));
        assertThat(filtered).noneMatch(execution -> "tenant-b".equals(execution.metadata().get("tenantId")));
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

        private EchoAgent(@NotNull String capability) { // GH-90000
            this.metadata = new AgentMetadata( // GH-90000
                    AgentName.COPILOT_AGENT,
                    "test",
                    "Echo agent for tests",
                    List.of(capability), // GH-90000
                    List.of("test"),
                    10L,
                    null
            );
        }

        @Override
        @NotNull
        public String getId() { // GH-90000
            return metadata.name().name(); // GH-90000
        }

        @Override
        @NotNull
        public Promise<AgentResult<Object>> execute(@NotNull Object input, @NotNull AIAgentContext context) { // GH-90000
            // Echo agent: return input wrapped in AgentResult
            AgentResult.AgentMetrics metrics = new AgentResult.AgentMetrics(10L, 0, "test", 1.0, 0.0); // GH-90000
            AgentResult.AgentTrace trace = AgentResult.AgentTrace.of("echo", "test"); // GH-90000
            return Promise.of(AgentResult.success(Map.of("input", input, "taskId", context.metadata().get("taskId")), metrics, trace));
        }

        @Override
        public long getLatencySLA() { // GH-90000
            return metadata.latencySLA(); // GH-90000
        }

        @Override
        @NotNull
        public Promise<AgentHealth> healthCheck() { // GH-90000
            return Promise.of(AgentHealth.healthy(1)); // GH-90000
        }

        @Override
        @NotNull
        public AgentMetadata getMetadata() { // GH-90000
            return metadata;
        }

        @Override
        public void validateInput(@NotNull Object input) { // GH-90000
            // no-op for test
        }
    }
}
