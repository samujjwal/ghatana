package com.ghatana.yappc.client;

import io.activej.promise.Promise;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Unified YAPPC Client for library integration.
 * 
 * <p>Enables software organizations to:
 * <ul>
 *   <li>Execute SDLC tasks programmatically</li>
 *   <li>Integrate with existing CI/CD pipelines</li>
 *   <li>Embed YAPPC capabilities in their tools</li>
 * </ul>
 * 
 * <p>Example usage:
 * <pre>{@code
 * // Embedded usage
 * YAPPCClient yappc = YAPPCClientFactory.embedded(
 *     YAPPCConfig.builder()
 *         .storagePlugin("postgres")
 *         .aiProvider("ollama")
 *         .build()
 * );
 * 
 * // Start the client
 * yappc.start().getResult();
 * 
 * // Execute a task
 * TaskResult<ArchitectureResult> result = yappc.executeTask(
 *     "create-architecture",
 *     new ArchitectureRequest("Design a microservices system"),
 *     TaskContext.defaultContext()
 * ).getResult();
 * 
 * // Invoke an SDLC agent
 * StepResult<IntakeOutput> intake = yappc.invokeAgent(
 *     "architecture", "intake",
 *     new IntakeInput("Build a REST API"),
 *     StepContext.forTenant("tenant-123")
 * ).getResult();
 * }</pre>
 * 
 * @author YAPPC Team
 * @version 1.0.0
 * @since 1.0.0
 
 * @doc.type interface
 * @doc.purpose Defines the contract for yappc client
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public interface YAPPCClient extends AutoCloseable {
    
    // ========== Lifecycle ==========
    
    /**
     * Starts the client and initializes all services.
     * 
     * @return a Promise that completes when the client is started
     */
    Promise<Void> start();
    
    /**
     * Stops the client gracefully.
     * 
     * @return a Promise that completes when the client is stopped
     */
    Promise<Void> stop();
    
    // ========== Task Execution ==========
    
    /**
     * Executes a task by ID with the given input and context.
     * 
     * @param <R> the result type
     * @param taskId the unique task identifier
     * @param input the task input
     * @param ctx the execution context
     * @return a Promise containing the task result
     */
    <R> Promise<TaskResult<R>> executeTask(String taskId, Object input, TaskContext ctx);
    
    /**
     * Lists all available tasks.
     * 
     * @return a Promise containing the list of task definitions
     */
    Promise<List<TaskDefinition>> listTasks();
    
    /**
     * Lists tasks by category.
     * 
     * @param category the task category
     * @return a Promise containing the list of task definitions
     */
    default Promise<List<TaskDefinition>> listTasksByCategory(String category) {
        return listTasks().map(tasks -> tasks.stream()
            .filter(t -> category.equals(t.getCategory()))
            .toList());
    }
    
    /**
     * Registers a new task definition at runtime.
     * 
     * @param definition the task definition
     * @return a Promise containing the registration result
     */
    Promise<TaskRegistrationResult> registerTask(TaskDefinition definition);
    
    // ========== SDLC Agent Invocation ==========
    
    /**
     * Invokes an SDLC agent by phase and step name.
     * 
     * @param <I> the input type
     * @param <O> the output type
     * @param phase the SDLC phase (e.g., "architecture", "implementation")
     * @param stepName the workflow step name (e.g., "intake", "design")
     * @param input the step input
     * @param ctx the step context
     * @return a Promise containing the step result
     */
    <I, O> Promise<StepResult<O>> invokeAgent(String phase, String stepName, I input, StepContext ctx);
    
    /**
     * Lists all available workflow steps.
     * 
     * @return the set of step names
     */
    default Set<String> listSteps() {
        return Set.of();
    }
    
    /**
     * Lists workflow steps for a specific phase.
     * 
     * @param phase the SDLC phase (e.g., "architecture", "implementation")
     * @return the set of step names
     */
    default Set<String> listStepsByPhase(String phase) {
        return Set.of();
    }
    
    // ========== Canvas Operations ==========
    
    /**
     * Creates a new canvas.
     * 
     * @param request the canvas creation request
     * @return a Promise containing the canvas result
     */
    Promise<CanvasResult> createCanvas(CreateCanvasRequest request);
    
    /**
     * Validates a canvas against defined rules.
     * 
     * @param canvasId the canvas identifier
     * @param ctx the validation context
     * @return a Promise containing the validation report
     */
    Promise<ValidationReport> validateCanvas(String canvasId, ValidationContext ctx);
    
    /**
     * Generates code or artifacts from a canvas.
     * 
     * @param canvasId the canvas identifier
     * @param opts the generation options
     * @return a Promise containing the generation result
     */
    Promise<GenerationResult> generateFromCanvas(String canvasId, GenerationOptions opts);
    
    // ========== Knowledge Graph ==========
    
    /**
     * Searches the knowledge graph.
     * 
     * @param query the search query
     * @return a Promise containing the search results
     */
    Promise<SearchResults> searchKnowledge(KnowledgeQuery query);
    
    /**
     * Ingests a document into the knowledge graph.
     * 
     * @param document the knowledge document
     * @return a Promise that completes when ingestion is done
     */
    Promise<Void> ingestKnowledge(KnowledgeDocument document);
    
    // ========== Lifecycle Management ==========
    
    /**
     * Gets the current lifecycle state for a project.
     * 
     * @param projectId the project identifier
     * @return a Promise containing the lifecycle state
     */
    Promise<LifecycleState> getLifecycleState(String projectId);
    
    /**
     * Advances a project to the next phase.
     * 
     * @param projectId the project identifier
     * @param request the phase advancement request
     * @return a Promise containing the phase result
     */
    Promise<PhaseResult> advancePhase(String projectId, AdvancePhaseRequest request);
    
    // ========== Health & Configuration ==========
    
    /**
     * Performs a detailed health check on the YAPPC system.
     * 
     * @return a Promise containing the health status
     */
    Promise<HealthStatus> checkHealth();
    
    /**
     * Simple health check returning basic status only.
     * 
     * @return a Promise containing the health status
     */
    default Promise<HealthStatus> healthCheck() {
        return checkHealth();
    }
    
    /**
     * Gets system metrics.
     * 
     * @return a Promise containing metrics as key-value pairs
     */
    Promise<Map<String, Object>> getMetrics();
    
    /**
     * Gets the current YAPPC configuration.
     * 
     * @return a Promise containing the configuration
     */
    Promise<YAPPCConfig> getConfiguration();
    
    /**
     * Closes the client and releases all resources.
     * 
     * @throws Exception if an error occurs during closure
     */
    @Override
    void close() throws Exception;
}
