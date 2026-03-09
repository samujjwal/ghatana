package com.ghatana.yappc.client.impl;

import com.ghatana.yappc.client.*;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;

/**
 * Embedded task executor for in-process task execution.
 *
 * @author YAPPC Team
 * @version 1.0.0
 * @since 1.0.0
 
 * @doc.type class
 * @doc.purpose Handles embedded task executor operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
final class EmbeddedTaskExecutor {
    
    private static final Logger logger = LoggerFactory.getLogger(EmbeddedTaskExecutor.class);
    
    private final YAPPCConfig config;
    private volatile boolean initialized = false;
    
    EmbeddedTaskExecutor(YAPPCConfig config) {
        this.config = config;
    }
    
    void initialize() {
        logger.info("Initializing embedded task executor");
        initialized = true;
    }
    
    void shutdown() {
        logger.info("Shutting down embedded task executor");
        initialized = false;
    }
    
    boolean isHealthy() {
        return initialized;
    }
    
    <R> Promise<TaskResult<R>> execute(TaskDefinition task, Object request, TaskContext context) {
        return Promise.ofCallback(cb -> {
            Instant startTime = Instant.now();
            
            try {
                logger.debug("Executing task: {} for tenant: {}", task.getId(), context.getTenantId());
                
                @SuppressWarnings("unchecked")
                R result = (R) executeTaskLogic(task, request, context);
                
                TaskResult<R> taskResult = TaskResult.<R>builder()
                    .taskId(task.getId())
                    .result(result)
                    .status(TaskStatus.SUCCESS)
                    .startTime(startTime)
                    .endTime(Instant.now())
                    .metadata(Map.of(
                        "tenant", context.getTenantId(),
                        "user", context.getUserId()
                    ))
                    .build();
                
                cb.set(taskResult);
            } catch (Exception e) {
                logger.error("Task execution failed: {}", task.getId(), e);
                
                TaskResult<R> errorResult = TaskResult.<R>builder()
                    .taskId(task.getId())
                    .status(TaskStatus.FAILED)
                    .startTime(startTime)
                    .endTime(Instant.now())
                    .error(e)
                    .build();
                
                cb.set(errorResult);
            }
        });
    }
    
    private Object executeTaskLogic(TaskDefinition task, Object request, TaskContext context) {
        return Map.of(
            "taskId", task.getId(),
            "status", "completed",
            "message", "Task executed successfully"
        );
    }
}
