package com.ghatana.kernel.plugin;

import java.util.List;
import java.util.Map;

/**
 * Kernel workflow interface.
 * 
 * Workflows define business processes that can be executed by the kernel.
 */
public interface KernelWorkflow {
    
    /**
     * Get the workflow identifier.
     */
    String getWorkflowId();
    
    /**
     * Get the workflow version.
     */
    String getVersion();
    
    /**
     * Get the workflow description.
     */
    String getDescription();
    
    /**
     * Get the workflow steps.
     */
    List<WorkflowStep> getSteps();
    
    /**
     * Get the workflow parameters.
     */
    Map<String, Object> getParameters();
    
    /**
     * Initialize the workflow.
     */
    void initialize(PluginContext context);
    
    /**
     * Execute the workflow.
     * 
     * @param input the workflow input data
     * @param parameters the execution parameters
     * @return the workflow execution result
     */
    WorkflowResult execute(Object input, Map<String, Object> parameters);
    
    /**
     * Start the workflow.
     */
    void start();
    
    /**
     * Stop the workflow.
     */
    void stop();
    
    /**
     * Shutdown the workflow.
     */
    void shutdown();
    
    /**
     * Workflow step definition.
     */
    interface WorkflowStep {
        String getStepId();
        String getOperatorId();
        Map<String, Object> getStepParameters();
    }
    
    /**
     * Workflow execution result.
     */
    interface WorkflowResult {
        boolean isSuccess();
        Object getResult();
        String getErrorMessage();
        Map<String, Object> getMetadata();
    }
}
