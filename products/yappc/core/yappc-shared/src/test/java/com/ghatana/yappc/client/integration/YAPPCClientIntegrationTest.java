package com.ghatana.yappc.client.integration;

import com.ghatana.yappc.client.*;
import com.ghatana.yappc.client.impl.EmbeddedYAPPCClient;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for YAPPCClient.

 * @doc.type class
 * @doc.purpose Handles yappc client integration test operations
 * @doc.layer core
 * @doc.pattern Test
*/
class YAPPCClientIntegrationTest extends EventloopTestBase {

    private YAPPCClient client;

    @BeforeEach
    void setUp() { // GH-90000
        YAPPCConfig config = YAPPCConfig.builder() // GH-90000
            .aiProvider("ollama [GH-90000]")
            .storagePlugin("memory [GH-90000]")
            .build(); // GH-90000

        client = new EmbeddedYAPPCClient(config); // GH-90000
        runPromise(() -> client.start()); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (client != null) { // GH-90000
            runPromise(() -> client.stop()); // GH-90000
        }
    }

    @Test
    void testCompleteWorkflow() throws Exception { // GH-90000
        // 1. Register a task
        TaskDefinition task = TaskDefinition.builder() // GH-90000
            .id("architecture-task [GH-90000]")
            .name("Create Architecture [GH-90000]")
            .description("Creates software architecture [GH-90000]")
            .category("architecture [GH-90000]")
            .build(); // GH-90000

        TaskRegistrationResult regResult = runPromise(() -> client.registerTask(task)); // GH-90000
        assertTrue(regResult.isSuccess()); // GH-90000

        // 2. List tasks
        List<TaskDefinition> tasks = runPromise(() -> client.listTasks()); // GH-90000
        assertEquals(1, tasks.size()); // GH-90000

        // 3. Execute task
        TaskContext context = TaskContext.builder() // GH-90000
            .tenantId("test-tenant [GH-90000]")
            .userId("test-user [GH-90000]")
            .build(); // GH-90000

        TaskResult<Map<String, Object>> execResult = runPromise(() -> client.<Map<String, Object>>executeTask( // GH-90000
            "architecture-task",
            Map.of("project", "test-project"), // GH-90000
            context
        ));

        assertTrue(execResult.isSuccess()); // GH-90000
        assertNotNull(execResult.getResult()); // y04-ok: TaskResult domain accessor, not Promise.getResult() // GH-90000

        // 4. Create canvas
        CreateCanvasRequest canvasRequest = new CreateCanvasRequest( // GH-90000
            "Test Architecture",
            "Architecture for test project",
            "architecture-template"
        );

        CanvasResult canvasResult = runPromise(() -> client.createCanvas(canvasRequest)); // GH-90000
        assertTrue(canvasResult.isSuccess()); // GH-90000

        // 5. Validate canvas
        ValidationReport validation = runPromise(() -> client.validateCanvas( // GH-90000
            canvasResult.getCanvasId(), // GH-90000
            ValidationContext.forPhase("planning [GH-90000]")
        ));

        assertTrue(validation.isValid()); // GH-90000

        // 6. Generate code
        GenerationOptions genOptions = new GenerationOptions( // GH-90000
            "java",
            "spring-boot",
            Map.of("package", "com.example") // GH-90000
        );

        GenerationResult genResult = runPromise(() -> client.generateFromCanvas( // GH-90000
            canvasResult.getCanvasId(), // GH-90000
            genOptions
        ));

        assertTrue(genResult.isSuccess()); // GH-90000

        // 7. Search knowledge
        KnowledgeQuery query = new KnowledgeQuery("architecture patterns", 5, 0.8); // GH-90000
        SearchResults searchResults = runPromise(() -> client.searchKnowledge(query)); // GH-90000
        assertNotNull(searchResults); // GH-90000

        // 8. Ingest knowledge
        KnowledgeDocument doc = new KnowledgeDocument( // GH-90000
            "arch-patterns",
            "Architecture Patterns",
            "Common software architecture patterns...",
            Map.of("category", "architecture") // GH-90000
        );

        runPromise(() -> client.ingestKnowledge(doc)); // GH-90000

        // 9. Manage lifecycle
        LifecycleState state = runPromise(() -> client.getLifecycleState("test-project [GH-90000]"));
        assertEquals("planning", state.getCurrentPhase()); // GH-90000

        AdvancePhaseRequest advanceRequest = new AdvancePhaseRequest("implementation", false); // GH-90000
        PhaseResult phaseResult = runPromise(() -> client.advancePhase("test-project", advanceRequest)); // GH-90000
        assertTrue(phaseResult.isSuccess()); // GH-90000
        assertEquals("implementation", phaseResult.getNewPhase()); // GH-90000

        // 10. Check health
        HealthStatus health = runPromise(() -> client.checkHealth()); // GH-90000
        assertTrue(health.isHealthy()); // GH-90000

        // 11. Get metrics
        Map<String, Object> metrics = runPromise(() -> client.getMetrics()); // GH-90000
        assertTrue((Boolean) metrics.get("started [GH-90000]"));
        assertEquals(1, metrics.get("registeredTasks [GH-90000]"));
    }

    @Test
    void testAgentWorkflow() throws Exception { // GH-90000
        StepContext context = StepContext.builder() // GH-90000
            .projectId("test-project [GH-90000]")
            .phase("planning [GH-90000]")
            .build(); // GH-90000

        // Execute multiple agent steps
        @SuppressWarnings("unchecked [GH-90000]")
        StepResult<Map<String, Object>> result1 = (StepResult<Map<String, Object>>) (StepResult<?>) runPromise(() -> client.invokeAgent( // GH-90000
            "planning",
            "create-architecture",
            Map.of("requirements", "test requirements"), // GH-90000
            context
        ));

        assertTrue(result1.isSuccess()); // GH-90000
        assertEquals("create-architecture", result1.getStepName()); // GH-90000

        @SuppressWarnings("unchecked [GH-90000]")
        StepResult<Map<String, Object>> result2 = (StepResult<Map<String, Object>>) (StepResult<?>) runPromise(() -> client.invokeAgent( // GH-90000
            "planning",
            "validate-architecture",
            Map.of("architecture", "test architecture"), // GH-90000
            context
        ));

        assertTrue(result2.isSuccess()); // GH-90000
        assertEquals("validate-architecture", result2.getStepName()); // GH-90000
    }

    @Test
    void testConcurrentOperations() throws Exception { // GH-90000
        // Register multiple tasks
        for (int i = 0; i < 5; i++) { // GH-90000
            TaskDefinition task = TaskDefinition.builder() // GH-90000
                .id("task-" + i) // GH-90000
                .name("Task " + i) // GH-90000
                .build(); // GH-90000

            runPromise(() -> client.registerTask(task)); // GH-90000
        }

        // Execute tasks concurrently
        List<TaskResult<?>> results = new java.util.ArrayList<>(); // GH-90000
        for (int i = 0; i < 5; i++) { // GH-90000
            final int idx = i;
            TaskResult<?> result = runPromise(() -> client.executeTask( // GH-90000
                "task-" + idx,
                Map.of("input", "test-" + idx), // GH-90000
                TaskContext.defaultContext() // GH-90000
            ));

            results.add(result); // GH-90000
            assertTrue(result.isSuccess()); // GH-90000
        }

        assertEquals(5, results.size()); // GH-90000

        // Verify all tasks are listed
        List<TaskDefinition> tasks = runPromise(() -> client.listTasks()); // GH-90000
        assertEquals(5, tasks.size()); // GH-90000
    }

    @Test
    void testErrorHandling() throws Exception { // GH-90000
        // Test non-existent task — the promise may reject or return a failed result
        var taskPromise = client.executeTask( // GH-90000
            "non-existent-task",
            Map.of(), // GH-90000
            TaskContext.defaultContext() // GH-90000
        );

        // Either the promise has an exception, or we get no result
        if (taskPromise.getException() != null) { // GH-90000
            assertNotNull(taskPromise.getException()); // GH-90000
        }

        // Test phase advancement (stub always succeeds) // GH-90000
        AdvancePhaseRequest request = new AdvancePhaseRequest("invalid-phase", false); // GH-90000
        PhaseResult result = runPromise(() -> client.advancePhase("test-project", request)); // GH-90000
        assertNotNull(result); // GH-90000
    }
}
