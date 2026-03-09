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
    void setUp() {
        config = YAPPCConfig.builder()
            .aiProvider("ollama")
            .storagePlugin("memory")
            .build();
        
        client = new EmbeddedYAPPCClient(config);
    }
    
    @AfterEach
    void tearDown() {
        if (client != null) {
            runPromise(() -> client.stop());
        }
    }
    
    @Test
    void testStartAndStop() {
        runPromise(() -> client.start());
        
        HealthStatus health = runPromise(() -> client.checkHealth());
        assertTrue(health.isHealthy());
        assertEquals("UP", health.getStatus());
        
        runPromise(() -> client.stop());
    }
    
    @Test
    void testRegisterTask() {
        runPromise(() -> client.start());
        
        TaskDefinition task = TaskDefinition.builder()
            .id("test-task")
            .name("Test Task")
            .description("A test task")
            .category("testing")
            .build();
        
        TaskRegistrationResult result = runPromise(() -> client.registerTask(task));
        
        assertTrue(result.isSuccess());
        assertEquals("test-task", result.getTaskId());
    }
    
    @Test
    void testExecuteTask() {
        runPromise(() -> client.start());
        
        TaskDefinition task = TaskDefinition.builder()
            .id("test-task")
            .name("Test Task")
            .build();
        
        runPromise(() -> client.registerTask(task));
        
        TaskResult<Map<String, Object>> result = runPromise(() -> client.<Map<String, Object>>executeTask(
            "test-task",
            Map.of("input", "test"),
            TaskContext.defaultContext()
        ));
        
        assertNotNull(result);
        assertEquals("test-task", result.getTaskId());
        assertTrue(result.isSuccess());
    }
    
    @Test
    void testListTasks() {
        runPromise(() -> client.start());
        
        TaskDefinition task1 = TaskDefinition.builder()
            .id("task-1")
            .name("Task 1")
            .build();
        
        TaskDefinition task2 = TaskDefinition.builder()
            .id("task-2")
            .name("Task 2")
            .build();
        
        runPromise(() -> client.registerTask(task1));
        runPromise(() -> client.registerTask(task2));
        
        List<TaskDefinition> tasks = runPromise(() -> client.listTasks());
        
        assertEquals(2, tasks.size());
    }
    
    @Test
    void testInvokeAgent() {
        runPromise(() -> client.start());
        
        StepContext context = StepContext.builder()
            .projectId("test-project")
            .phase("planning")
            .build();
        
        @SuppressWarnings("unchecked")
        StepResult<Map<String, Object>> result = (StepResult<Map<String, Object>>) (StepResult<?>) runPromise(() -> client.invokeAgent(
            "planning",
            "create-architecture",
            Map.of("input", "test"),
            context
        ));
        
        assertNotNull(result);
        assertEquals("create-architecture", result.getStepName());
        assertEquals("planning", result.getPhase());
        assertTrue(result.isSuccess());
    }
    
    @Test
    void testCreateCanvas() {
        runPromise(() -> client.start());
        
        CreateCanvasRequest request = new CreateCanvasRequest(
            "Test Canvas",
            "A test canvas",
            null
        );
        
        CanvasResult result = runPromise(() -> client.createCanvas(request));
        
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("Test Canvas", result.getName());
    }
    
    @Test
    void testValidateCanvas() {
        runPromise(() -> client.start());
        
        CreateCanvasRequest createRequest = new CreateCanvasRequest(
            "Test Canvas",
            "A test canvas",
            null
        );
        
        CanvasResult canvasResult = runPromise(() -> client.createCanvas(createRequest));
        
        ValidationContext context = ValidationContext.forPhase("planning");
        ValidationReport report = runPromise(() -> client.validateCanvas(
            canvasResult.getCanvasId(),
            context
        ));
        
        assertNotNull(report);
        assertTrue(report.isValid());
    }
    
    @Test
    void testSearchKnowledge() {
        runPromise(() -> client.start());
        
        KnowledgeQuery query = new KnowledgeQuery("test query", 10, 0.5);
        SearchResults results = runPromise(() -> client.searchKnowledge(query));
        
        assertNotNull(results);
        assertEquals(0, results.getTotalCount());
    }
    
    @Test
    void testIngestKnowledge() {
        runPromise(() -> client.start());
        
        KnowledgeDocument document = new KnowledgeDocument(
            "doc-1",
            "Test Document",
            "This is a test document",
            Map.of("type", "test")
        );
        
        assertDoesNotThrow(() -> runPromise(() -> client.ingestKnowledge(document)));
    }
    
    @Test
    void testGetLifecycleState() {
        runPromise(() -> client.start());
        
        LifecycleState state = runPromise(() -> client.getLifecycleState("test-project"));
        
        assertNotNull(state);
        assertEquals("test-project", state.getProjectId());
        assertEquals("planning", state.getCurrentPhase());
    }
    
    @Test
    void testAdvancePhase() {
        runPromise(() -> client.start());
        
        AdvancePhaseRequest request = new AdvancePhaseRequest("implementation", false);
        PhaseResult result = runPromise(() -> client.advancePhase("test-project", request));
        
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("implementation", result.getNewPhase());
    }
    
    @Test
    void testGetConfiguration() {
        YAPPCConfig retrievedConfig = runPromise(() -> client.getConfiguration());
        
        assertNotNull(retrievedConfig);
        assertEquals("ollama", retrievedConfig.getAiProvider());
    }
    
    @Test
    void testGetMetrics() {
        runPromise(() -> client.start());
        
        Map<String, Object> metrics = runPromise(() -> client.getMetrics());
        
        assertNotNull(metrics);
        assertTrue(metrics.containsKey("started"));
        assertTrue((Boolean) metrics.get("started"));
    }
}
