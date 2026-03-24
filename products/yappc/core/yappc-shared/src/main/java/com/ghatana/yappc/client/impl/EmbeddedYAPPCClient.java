package com.ghatana.yappc.client.impl;

import com.ghatana.yappc.client.*;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Embedded implementation of YAPPCClient that runs YAPPC services in-process.
 *
 * @author YAPPC Team
 * @version 1.0.0
 * @since 1.0.0
 
 * @doc.type class
 * @doc.purpose Handles embedded yappc client operations
 * @doc.layer core
 * @doc.pattern Implementation
*/
public final class EmbeddedYAPPCClient implements YAPPCClient {
    
    private static final Logger logger = LoggerFactory.getLogger(EmbeddedYAPPCClient.class);
    
    private final YAPPCConfig config;
    private final Map<String, TaskDefinition> registeredTasks;
    private final EmbeddedTaskExecutor taskExecutor;
    private final EmbeddedAgentExecutor agentExecutor;
    private final EmbeddedCanvasService canvasService;
    private final EmbeddedKnowledgeService knowledgeService;
    private final EmbeddedLifecycleService lifecycleService;
    private volatile boolean started = false;
    
    public EmbeddedYAPPCClient(YAPPCConfig config) {
        this.config = config;
        this.registeredTasks = new ConcurrentHashMap<>();
        this.taskExecutor = new EmbeddedTaskExecutor(config);
        this.agentExecutor = new EmbeddedAgentExecutor(config);
        this.canvasService = new EmbeddedCanvasService(config);
        this.knowledgeService = new EmbeddedKnowledgeService(config);
        this.lifecycleService = new EmbeddedLifecycleService(config);
    }
    
    @Override
    public Promise<Void> start() {
        return Promise.ofCallback(cb -> {
            if (started) {
                cb.set(null);
                return;
            }
            
            logger.info("Starting embedded YAPPC client...");
            
            try {
                taskExecutor.initialize();
                agentExecutor.initialize();
                canvasService.initialize();
                knowledgeService.initialize();
                lifecycleService.initialize();
                
                started = true;
                logger.info("Embedded YAPPC client started successfully");
                cb.set(null);
            } catch (Exception e) {
                logger.error("Failed to start embedded YAPPC client", e);
                cb.setException(e);
            }
        });
    }
    
    @Override
    public Promise<Void> stop() {
        return Promise.ofCallback(cb -> {
            if (!started) {
                cb.set(null);
                return;
            }
            
            logger.info("Stopping embedded YAPPC client...");
            
            try {
                lifecycleService.shutdown();
                knowledgeService.shutdown();
                canvasService.shutdown();
                agentExecutor.shutdown();
                taskExecutor.shutdown();
                
                started = false;
                logger.info("Embedded YAPPC client stopped successfully");
                cb.set(null);
            } catch (Exception e) {
                logger.error("Failed to stop embedded YAPPC client", e);
                cb.setException(e);
            }
        });
    }
    
    @Override
    public Promise<TaskRegistrationResult> registerTask(TaskDefinition task) {
        return Promise.ofCallback(cb -> {
            try {
                if (registeredTasks.containsKey(task.getId())) {
                    cb.set(TaskRegistrationResult.failure(
                        task.getId(), 
                        "Task already registered: " + task.getId()
                    ));
                    return;
                }
                
                registeredTasks.put(task.getId(), task);
                logger.info("Registered task: {} ({})", task.getName(), task.getId());
                cb.set(TaskRegistrationResult.success(task.getId()));
            } catch (Exception e) {
                logger.error("Failed to register task: {}", task.getId(), e);
                cb.setException(e);
            }
        });
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public <R> Promise<TaskResult<R>> executeTask(String taskId, Object request, TaskContext context) {
        return Promise.ofCallback(cb -> {
            try {
                TaskDefinition task = registeredTasks.get(taskId);
                if (task == null) {
                    cb.setException(new IllegalArgumentException("Task not found: " + taskId));
                    return;
                }
                
                logger.info("Executing task: {} for tenant: {}", taskId, context.getTenantId());
                taskExecutor.execute(task, request, context)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            cb.setException(error);
                        } else {
                            cb.set((TaskResult<R>) result);
                        }
                    });
            } catch (Exception e) {
                logger.error("Failed to execute task: {}", taskId, e);
                cb.setException(e);
            }
        });
    }
    
    @Override
    public Promise<List<TaskDefinition>> listTasks() {
        return Promise.of(List.copyOf(registeredTasks.values()));
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public <I, O> Promise<StepResult<O>> invokeAgent(String phase, String stepName, I input, StepContext context) {
        return Promise.ofCallback(cb -> {
            try {
                logger.info("Invoking agent: {}/{} for project: {}", phase, stepName, context.getProjectId());
                agentExecutor.execute(phase, stepName, input, context)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            cb.setException(error);
                        } else {
                            cb.set((StepResult<O>) result);
                        }
                    });
            } catch (Exception e) {
                logger.error("Failed to invoke agent: {}/{}", phase, stepName, e);
                cb.setException(e);
            }
        });
    }
    
    @Override
    public Set<String> listStepsByPhase(String phase) {
        // Return empty set for now - can be enhanced with actual step registry
        return Set.of();
    }
    
    @Override
    public Promise<CanvasResult> createCanvas(CreateCanvasRequest request) {
        return Promise.ofCallback(cb -> {
            try {
                logger.info("Creating canvas: {}", request.getName());
                canvasService.createCanvas(request)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            cb.setException(error);
                        } else {
                            cb.set(result);
                        }
                    });
            } catch (Exception e) {
                logger.error("Failed to create canvas: {}", request.getName(), e);
                cb.setException(e);
            }
        });
    }
    
    @Override
    public Promise<ValidationReport> validateCanvas(String canvasId, ValidationContext context) {
        return Promise.ofCallback(cb -> {
            try {
                logger.info("Validating canvas: {} for phase: {}", canvasId, context.getPhase());
                canvasService.validateCanvas(canvasId, context)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            cb.setException(error);
                        } else {
                            cb.set(result);
                        }
                    });
            } catch (Exception e) {
                logger.error("Failed to validate canvas: {}", canvasId, e);
                cb.setException(e);
            }
        });
    }
    
    @Override
    public Promise<GenerationResult> generateFromCanvas(String canvasId, GenerationOptions options) {
        return Promise.ofCallback(cb -> {
            try {
                logger.info("Generating code from canvas: {} for language: {}", canvasId, options.getLanguage());
                canvasService.generateFromCanvas(canvasId, options)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            cb.setException(error);
                        } else {
                            cb.set(result);
                        }
                    });
            } catch (Exception e) {
                logger.error("Failed to generate from canvas: {}", canvasId, e);
                cb.setException(e);
            }
        });
    }
    
    @Override
    public Promise<SearchResults> searchKnowledge(KnowledgeQuery query) {
        return Promise.ofCallback(cb -> {
            try {
                logger.info("Searching knowledge: {}", query.getQuery());
                knowledgeService.search(query)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            cb.setException(error);
                        } else {
                            cb.set(result);
                        }
                    });
            } catch (Exception e) {
                logger.error("Failed to search knowledge: {}", query.getQuery(), e);
                cb.setException(e);
            }
        });
    }
    
    @Override
    public Promise<Void> ingestKnowledge(KnowledgeDocument document) {
        return Promise.ofCallback(cb -> {
            try {
                logger.info("Ingesting knowledge document: {}", document.getId());
                knowledgeService.ingest(document)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            cb.setException(error);
                        } else {
                            cb.set(null);
                        }
                    });
            } catch (Exception e) {
                logger.error("Failed to ingest knowledge: {}", document.getId(), e);
                cb.setException(e);
            }
        });
    }
    
    @Override
    public Promise<LifecycleState> getLifecycleState(String projectId) {
        return Promise.ofCallback(cb -> {
            try {
                logger.info("Getting lifecycle state for project: {}", projectId);
                lifecycleService.getState(projectId)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            cb.setException(error);
                        } else {
                            cb.set(result);
                        }
                    });
            } catch (Exception e) {
                logger.error("Failed to get lifecycle state: {}", projectId, e);
                cb.setException(e);
            }
        });
    }
    
    @Override
    public Promise<PhaseResult> advancePhase(String projectId, AdvancePhaseRequest request) {
        return Promise.ofCallback(cb -> {
            try {
                logger.info("Advancing phase for project: {} to: {}", projectId, request.getTargetPhase());
                lifecycleService.advancePhase(projectId, request)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            cb.setException(error);
                        } else {
                            cb.set(result);
                        }
                    });
            } catch (Exception e) {
                logger.error("Failed to advance phase: {}", projectId, e);
                cb.setException(e);
            }
        });
    }
    
    @Override
    public Promise<HealthStatus> checkHealth() {
        return Promise.ofCallback(cb -> {
            try {
                boolean allHealthy = taskExecutor.isHealthy() 
                    && agentExecutor.isHealthy()
                    && canvasService.isHealthy()
                    && knowledgeService.isHealthy()
                    && lifecycleService.isHealthy();
                
                HealthStatus status = HealthStatus.builder()
                    .healthy(allHealthy)
                    .status(allHealthy ? "UP" : "DEGRADED")
                    .components(Map.of(
                        "taskExecutor", new HealthStatus.ComponentHealth(
                            taskExecutor.isHealthy(), "UP", "Task executor operational"),
                        "agentExecutor", new HealthStatus.ComponentHealth(
                            agentExecutor.isHealthy(), "UP", "Agent executor operational"),
                        "canvasService", new HealthStatus.ComponentHealth(
                            canvasService.isHealthy(), "UP", "Canvas service operational"),
                        "knowledgeService", new HealthStatus.ComponentHealth(
                            knowledgeService.isHealthy(), "UP", "Knowledge service operational"),
                        "lifecycleService", new HealthStatus.ComponentHealth(
                            lifecycleService.isHealthy(), "UP", "Lifecycle service operational")
                    ))
                    .build();
                
                cb.set(status);
            } catch (Exception e) {
                logger.error("Failed to check health", e);
                cb.setException(e);
            }
        });
    }
    
    @Override
    public Promise<YAPPCConfig> getConfiguration() {
        return Promise.of(config);
    }
    
    @Override
    public Promise<HealthStatus> healthCheck() {
        return Promise.of(HealthStatus.builder()
            .healthy(started)
            .status(started ? "UP" : "DOWN")
            .build());
    }
    
    @Override
    public Promise<Map<String, Object>> getMetrics() {
        return Promise.of(Map.of(
            "registeredTasks", registeredTasks.size(),
            "started", started,
            "uptime", System.currentTimeMillis()
        ));
    }
    
    @Override
    public void close() throws Exception {
        // Cleanup resources
        started = false;
        // Additional cleanup can be added here as needed
    }
}
