package com.ghatana.virtualorg.framework.workflow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WorkflowEngine.
 *
 * @doc.type test
 * @doc.purpose WorkflowEngine validation
 */
@DisplayName("Workflow Engine Tests")
class WorkflowEngineTest {
    
    private WorkflowEngine engine;
    private WorkflowDefinition definition;
    
    @BeforeEach
    void setup() {
        engine = new WorkflowEngine();
        definition = WorkflowDefinition.builder()
            .id("test-workflow")
            .name("Test Workflow")
            .version("1.0.0")
            .description("Test workflow for unit tests")
            .addStep(WorkflowDefinition.WorkflowStep.of("step1", "First step", "Engineer"))
            .addStep(WorkflowDefinition.WorkflowStep.of("step2", "Second step", "Senior Engineer"))
            .build();
    }
    
    @Test
    @DisplayName("Create workflow engine")
    void testCreateEngine() {
        assertNotNull(engine);
        WorkflowEngine.ExecutionMetrics metrics = engine.getMetrics();
        assertEquals(0, metrics.getTotalExecutions());
    }
    
    @Test
    @DisplayName("Execute workflow")
    void testExecuteWorkflow() {
        WorkflowEngine.WorkflowExecution execution = engine.execute(definition, java.util.Map.of());
        assertNotNull(execution);
        assertEquals("RUNNING", execution.getStatus());
        assertEquals(0, execution.getCurrentStepIndex());
    }
    
    @Test
    @DisplayName("Get execution by ID")
    void testGetExecution() {
        WorkflowEngine.WorkflowExecution execution = engine.execute(definition, java.util.Map.of());
        WorkflowEngine.WorkflowExecution retrieved = engine.getExecution(execution.getId());
        assertNotNull(retrieved);
        assertEquals(execution.getId(), retrieved.getId());
    }
    
    @Test
    @DisplayName("Mark execution as completed")
    void testMarkCompleted() {
        WorkflowEngine.WorkflowExecution execution = engine.execute(definition, java.util.Map.of());
        engine.markCompleted(execution.getId());
        assertEquals("COMPLETED", execution.getStatus());
        assertNotNull(execution.getCompletedAt());
    }
    
    @Test
    @DisplayName("Mark execution as failed")
    void testMarkFailed() {
        WorkflowEngine.WorkflowExecution execution = engine.execute(definition, java.util.Map.of());
        engine.markFailed(execution.getId(), "Test failure");
        assertEquals("FAILED", execution.getStatus());
        assertEquals("Test failure", execution.getFailureReason());
    }
    
    @Test
    @DisplayName("Get execution metrics")
    void testGetMetrics() {
        engine.execute(definition, java.util.Map.of());
        engine.execute(definition, java.util.Map.of());
        
        WorkflowEngine.ExecutionMetrics metrics = engine.getMetrics();
        assertEquals(2, metrics.getTotalExecutions());
        assertEquals(2, metrics.getActiveExecutions());
    }
    
    @Test
    @DisplayName("Advance workflow step")
    void testAdvanceStep() {
        WorkflowEngine.WorkflowExecution execution = engine.execute(definition, java.util.Map.of());
        assertEquals(0, execution.getCurrentStepIndex());
        
        execution.advanceStep();
        assertEquals(1, execution.getCurrentStepIndex());
        
        execution.advanceStep();
        assertEquals(1, execution.getCurrentStepIndex()); // Should not exceed max
    }
    
    @Test
    @DisplayName("Calculate execution duration")
    void testExecutionDuration() throws InterruptedException {
        WorkflowEngine.WorkflowExecution execution = engine.execute(definition, java.util.Map.of());
        Thread.sleep(100);
        engine.markCompleted(execution.getId());
        
        long duration = execution.getDurationMs();
        assertTrue(duration >= 100);
    }
}
