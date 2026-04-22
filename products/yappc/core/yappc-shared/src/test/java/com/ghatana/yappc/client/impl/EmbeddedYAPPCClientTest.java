package com.ghatana.yappc.client.impl;

import com.ghatana.yappc.client.*;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for EmbeddedYAPPCClient.

 * @doc.type class
 * @doc.purpose Handles embedded yappc client test operations
 * @doc.layer core
 * @doc.pattern Test
*/
class EmbeddedYAPPCClientTest extends EventloopTestBase {

    private YAPPCClient client;
    private YAPPCConfig config;

    @BeforeEach
    void setUp() { // GH-90000
        config = YAPPCConfig.builder() // GH-90000
            .aiProvider("ollama [GH-90000]")
            .storagePlugin("memory [GH-90000]")
            .build(); // GH-90000

        client = new EmbeddedYAPPCClient(config); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (client != null) { // GH-90000
            runPromise(() -> client.stop()); // GH-90000
        }
    }

    @Test
    void testStartAndStop() { // GH-90000
        assertFalse(client.isRunning()); // GH-90000

        runPromise(() -> client.start()); // GH-90000
        assertTrue(client.isRunning()); // GH-90000
        assertTrue(runPromise(() -> client.healthCheck())); // GH-90000

        HealthStatus health = runPromise(() -> client.checkHealth()); // GH-90000
        assertTrue(health.isHealthy()); // GH-90000
        assertEquals("UP", health.getStatus()); // GH-90000

        runPromise(() -> client.stop()); // GH-90000
        assertFalse(client.isRunning()); // GH-90000
        assertFalse(runPromise(() -> client.healthCheck())); // GH-90000
    }

    @Test
    void testRegisterTask() { // GH-90000
        runPromise(() -> client.start()); // GH-90000

        TaskDefinition task = TaskDefinition.builder() // GH-90000
            .id("test-task [GH-90000]")
            .name("Test Task [GH-90000]")
            .description("A test task [GH-90000]")
            .category("testing [GH-90000]")
            .build(); // GH-90000

        TaskRegistrationResult result = runPromise(() -> client.registerTask(task)); // GH-90000

        assertTrue(result.isSuccess()); // GH-90000
        assertEquals("test-task", result.getTaskId()); // GH-90000
    }

    @Test
    void testExecuteTask() { // GH-90000
        runPromise(() -> client.start()); // GH-90000

        TaskDefinition task = TaskDefinition.builder() // GH-90000
            .id("test-task [GH-90000]")
            .name("Test Task [GH-90000]")
            .build(); // GH-90000

        runPromise(() -> client.registerTask(task)); // GH-90000

        TaskResult<Map<String, Object>> result = runPromise(() -> client.<Map<String, Object>>executeTask( // GH-90000
            "test-task",
            Map.of("input", "test"), // GH-90000
            TaskContext.defaultContext() // GH-90000
        ));

        assertNotNull(result); // GH-90000
        assertEquals("test-task", result.getTaskId()); // GH-90000
        assertTrue(result.isSuccess()); // GH-90000
    }

    @Test
    void testListTasks() { // GH-90000
        runPromise(() -> client.start()); // GH-90000

        TaskDefinition task1 = TaskDefinition.builder() // GH-90000
            .id("task-1 [GH-90000]")
            .name("Task 1 [GH-90000]")
            .build(); // GH-90000

        TaskDefinition task2 = TaskDefinition.builder() // GH-90000
            .id("task-2 [GH-90000]")
            .name("Task 2 [GH-90000]")
            .build(); // GH-90000

        runPromise(() -> client.registerTask(task1)); // GH-90000
        runPromise(() -> client.registerTask(task2)); // GH-90000

        List<TaskDefinition> tasks = runPromise(() -> client.listTasks()); // GH-90000

        assertEquals(2, tasks.size()); // GH-90000
    }

    @Test
    void testInvokeAgent() { // GH-90000
        runPromise(() -> client.start()); // GH-90000

        StepContext context = StepContext.builder() // GH-90000
            .projectId("test-project [GH-90000]")
            .phase("planning [GH-90000]")
            .build(); // GH-90000

        @SuppressWarnings("unchecked [GH-90000]")
        StepResult<Map<String, Object>> result = (StepResult<Map<String, Object>>) (StepResult<?>) runPromise(() -> client.invokeAgent( // GH-90000
            "planning",
            "create-architecture",
            Map.of("input", "test"), // GH-90000
            context
        ));

        assertNotNull(result); // GH-90000
        assertEquals("create-architecture", result.getStepName()); // GH-90000
        assertEquals("planning", result.getPhase()); // GH-90000
        assertTrue(result.isSuccess()); // GH-90000
    }

    @Test
    void testCreateCanvas() { // GH-90000
        runPromise(() -> client.start()); // GH-90000

        CreateCanvasRequest request = new CreateCanvasRequest( // GH-90000
            "Test Canvas",
            "A test canvas",
            null
        );

        CanvasResult result = runPromise(() -> client.createCanvas(request)); // GH-90000

        assertNotNull(result); // GH-90000
        assertTrue(result.isSuccess()); // GH-90000
        assertEquals("Test Canvas", result.getName()); // GH-90000
    }

    @Test
    void testValidateCanvas() { // GH-90000
        runPromise(() -> client.start()); // GH-90000

        CreateCanvasRequest createRequest = new CreateCanvasRequest( // GH-90000
            "Test Canvas",
            "A test canvas",
            null
        );

        CanvasResult canvasResult = runPromise(() -> client.createCanvas(createRequest)); // GH-90000

        ValidationContext context = ValidationContext.forPhase("planning [GH-90000]");
        ValidationReport report = runPromise(() -> client.validateCanvas( // GH-90000
            canvasResult.getCanvasId(), // GH-90000
            context
        ));

        assertNotNull(report); // GH-90000
        assertTrue(report.isValid()); // GH-90000
    }

    @Test
    void testSearchKnowledge() { // GH-90000
        runPromise(() -> client.start()); // GH-90000

        KnowledgeQuery query = new KnowledgeQuery("test query", 10, 0.5); // GH-90000
        SearchResults results = runPromise(() -> client.searchKnowledge(query)); // GH-90000

        assertNotNull(results); // GH-90000
        assertEquals(0, results.getTotalCount()); // GH-90000
    }

    @Test
    void testIngestKnowledge() { // GH-90000
        runPromise(() -> client.start()); // GH-90000

        KnowledgeDocument document = new KnowledgeDocument( // GH-90000
            "doc-1",
            "Test Document",
            "This is a test document",
            Map.of("type", "test") // GH-90000
        );

        assertDoesNotThrow(() -> runPromise(() -> client.ingestKnowledge(document))); // GH-90000
    }

    @Test
    void testGetLifecycleState() { // GH-90000
        runPromise(() -> client.start()); // GH-90000

        LifecycleState state = runPromise(() -> client.getLifecycleState("test-project [GH-90000]"));

        assertNotNull(state); // GH-90000
        assertEquals("test-project", state.getProjectId()); // GH-90000
        assertEquals("planning", state.getCurrentPhase()); // GH-90000
    }

    @Test
    void testAdvancePhase() { // GH-90000
        runPromise(() -> client.start()); // GH-90000

        AdvancePhaseRequest request = new AdvancePhaseRequest("implementation", false); // GH-90000
        PhaseResult result = runPromise(() -> client.advancePhase("test-project", request)); // GH-90000

        assertNotNull(result); // GH-90000
        assertTrue(result.isSuccess()); // GH-90000
        assertEquals("implementation", result.getNewPhase()); // GH-90000
    }

    @Test
    void testGetConfiguration() { // GH-90000
        YAPPCConfig retrievedConfig = runPromise(() -> client.getConfiguration()); // GH-90000

        assertNotNull(retrievedConfig); // GH-90000
        assertEquals("ollama", retrievedConfig.getAiProvider()); // GH-90000
    }

    @Test
    void testGetMetrics() { // GH-90000
        runPromise(() -> client.start()); // GH-90000

        Map<String, Object> metrics = runPromise(() -> client.getMetrics()); // GH-90000

        assertNotNull(metrics); // GH-90000
        assertTrue(metrics.containsKey("started [GH-90000]"));
        assertTrue((Boolean) metrics.get("started [GH-90000]"));
    }
}
