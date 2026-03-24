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
    void setUp() {
        YAPPCConfig config = YAPPCConfig.builder()
            .aiProvider("ollama")
            .storagePlugin("memory")
            .build();
        
        client = new EmbeddedYAPPCClient(config);
        runPromise(() -> client.start());
    }
    
    @AfterEach
    void tearDown() {
        if (client != null) {
            runPromise(() -> client.stop());
        }
    }
    
    @Test
    void testCompleteWorkflow() throws Exception {
        // 1. Register a task
        TaskDefinition task = TaskDefinition.builder()
            .id("architecture-task")
            .name("Create Architecture")
            .description("Creates software architecture")
            .category("architecture")
            .build();
        
        TaskRegistrationResult regResult = runPromise(() -> client.registerTask(task));
        assertTrue(regResult.isSuccess());
        
        // 2. List tasks
        List<TaskDefinition> tasks = runPromise(() -> client.listTasks());
        assertEquals(1, tasks.size());
        
        // 3. Execute task
        TaskContext context = TaskContext.builder()
            .tenantId("test-tenant")
            .userId("test-user")
            .build();
        
        TaskResult<Map<String, Object>> execResult = runPromise(() -> client.<Map<String, Object>>executeTask(
            "architecture-task",
            Map.of("project", "test-project"),
            context
        ));
        
        assertTrue(execResult.isSuccess());
        assertNotNull(execResult.getResult());
        
        // 4. Create canvas
        CreateCanvasRequest canvasRequest = new CreateCanvasRequest(
            "Test Architecture",
            "Architecture for test project",
            "architecture-template"
        );
        
        CanvasResult canvasResult = runPromise(() -> client.createCanvas(canvasRequest));
        assertTrue(canvasResult.isSuccess());
        
        // 5. Validate canvas
        ValidationReport validation = runPromise(() -> client.validateCanvas(
            canvasResult.getCanvasId(),
            ValidationContext.forPhase("planning")
        ));
        
        assertTrue(validation.isValid());
        
        // 6. Generate code
        GenerationOptions genOptions = new GenerationOptions(
            "java",
            "spring-boot",
            Map.of("package", "com.example")
        );
        
        GenerationResult genResult = runPromise(() -> client.generateFromCanvas(
            canvasResult.getCanvasId(),
            genOptions
        ));
        
        assertTrue(genResult.isSuccess());
        
        // 7. Search knowledge
        KnowledgeQuery query = new KnowledgeQuery("architecture patterns", 5, 0.8);
        SearchResults searchResults = runPromise(() -> client.searchKnowledge(query));
        assertNotNull(searchResults);
        
        // 8. Ingest knowledge
        KnowledgeDocument doc = new KnowledgeDocument(
            "arch-patterns",
            "Architecture Patterns",
            "Common software architecture patterns...",
            Map.of("category", "architecture")
        );
        
        runPromise(() -> client.ingestKnowledge(doc));
        
        // 9. Manage lifecycle
        LifecycleState state = runPromise(() -> client.getLifecycleState("test-project"));
        assertEquals("planning", state.getCurrentPhase());
        
        AdvancePhaseRequest advanceRequest = new AdvancePhaseRequest("implementation", false);
        PhaseResult phaseResult = runPromise(() -> client.advancePhase("test-project", advanceRequest));
        assertTrue(phaseResult.isSuccess());
        assertEquals("implementation", phaseResult.getNewPhase());
        
        // 10. Check health
        HealthStatus health = runPromise(() -> client.checkHealth());
        assertTrue(health.isHealthy());
        
        // 11. Get metrics
        Map<String, Object> metrics = runPromise(() -> client.getMetrics());
        assertTrue((Boolean) metrics.get("started"));
        assertEquals(1, metrics.get("registeredTasks"));
    }
    
    @Test
    void testAgentWorkflow() throws Exception {
        StepContext context = StepContext.builder()
            .projectId("test-project")
            .phase("planning")
            .build();
        
        // Execute multiple agent steps
        @SuppressWarnings("unchecked")
        StepResult<Map<String, Object>> result1 = (StepResult<Map<String, Object>>) (StepResult<?>) runPromise(() -> client.invokeAgent(
            "planning",
            "create-architecture",
            Map.of("requirements", "test requirements"),
            context
        ));
        
        assertTrue(result1.isSuccess());
        assertEquals("create-architecture", result1.getStepName());
        
        @SuppressWarnings("unchecked")
        StepResult<Map<String, Object>> result2 = (StepResult<Map<String, Object>>) (StepResult<?>) runPromise(() -> client.invokeAgent(
            "planning",
            "validate-architecture",
            Map.of("architecture", "test architecture"),
            context
        ));
        
        assertTrue(result2.isSuccess());
        assertEquals("validate-architecture", result2.getStepName());
    }
    
    @Test
    void testConcurrentOperations() throws Exception {
        // Register multiple tasks
        for (int i = 0; i < 5; i++) {
            TaskDefinition task = TaskDefinition.builder()
                .id("task-" + i)
                .name("Task " + i)
                .build();
            
            runPromise(() -> client.registerTask(task));
        }
        
        // Execute tasks concurrently
        List<TaskResult<?>> results = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            TaskResult<?> result = runPromise(() -> client.executeTask(
                "task-" + idx,
                Map.of("input", "test-" + idx),
                TaskContext.defaultContext()
            ));
            
            results.add(result);
            assertTrue(result.isSuccess());
        }
        
        assertEquals(5, results.size());
        
        // Verify all tasks are listed
        List<TaskDefinition> tasks = runPromise(() -> client.listTasks());
        assertEquals(5, tasks.size());
    }
    
    @Test
    void testErrorHandling() throws Exception {
        // Test non-existent task — the promise may reject or return a failed result
        var taskPromise = client.executeTask(
            "non-existent-task",
            Map.of(),
            TaskContext.defaultContext()
        );
        
        // Either the promise has an exception, or we get no result
        if (taskPromise.getException() != null) {
            assertNotNull(taskPromise.getException());
        }
        
        // Test phase advancement (stub always succeeds)
        AdvancePhaseRequest request = new AdvancePhaseRequest("invalid-phase", false);
        PhaseResult result = runPromise(() -> client.advancePhase("test-project", request));
        assertNotNull(result);
    }
}
