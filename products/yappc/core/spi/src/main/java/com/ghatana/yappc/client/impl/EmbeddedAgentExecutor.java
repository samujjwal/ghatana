package com.ghatana.yappc.client.impl;

import com.ghatana.yappc.client.*;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;

/**
 * Embedded agent executor for in-process SDLC agent execution.
 *
 * @author YAPPC Team
 * @version 1.0.0
 * @since 1.0.0
 
 * @doc.type class
 * @doc.purpose Handles embedded agent executor operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
final class EmbeddedAgentExecutor {
    
    private static final Logger logger = LoggerFactory.getLogger(EmbeddedAgentExecutor.class);
    
    private final YAPPCConfig config;
    private volatile boolean initialized = false;
    
    EmbeddedAgentExecutor(YAPPCConfig config) {
        this.config = config;
    }
    
    void initialize() {
        logger.info("Initializing embedded agent executor");
        initialized = true;
    }
    
    void shutdown() {
        logger.info("Shutting down embedded agent executor");
        initialized = false;
    }
    
    boolean isHealthy() {
        return initialized;
    }
    
    <I, O> Promise<StepResult<O>> execute(String phase, String stepName, I input, StepContext context) {
        return Promise.ofCallback(cb -> {
            Instant startTime = Instant.now();
            
            try {
                logger.debug("Executing agent step: {}/{} for project: {}", 
                    phase, stepName, context.getProjectId());
                
                @SuppressWarnings("unchecked")
                O output = (O) executeAgentLogic(phase, stepName, input, context);
                
                StepResult<O> stepResult = StepResult.<O>builder()
                    .stepName(stepName)
                    .phase(phase)
                    .output(output)
                    .status(TaskStatus.SUCCESS)
                    .startTime(startTime)
                    .endTime(Instant.now())
                    .metadata(Map.of(
                        "project", context.getProjectId(),
                        "phase", phase
                    ))
                    .build();
                
                cb.set(stepResult);
            } catch (Exception e) {
                logger.error("Agent execution failed: {}/{}", phase, stepName, e);
                
                StepResult<O> errorResult = StepResult.<O>builder()
                    .stepName(stepName)
                    .phase(phase)
                    .status(TaskStatus.FAILED)
                    .startTime(startTime)
                    .endTime(Instant.now())
                    .error(e)
                    .build();
                
                cb.set(errorResult);
            }
        });
    }
    
    private Object executeAgentLogic(String phase, String stepName, Object input, StepContext context) {
        return Map.of(
            "phase", phase,
            "step", stepName,
            "status", "completed",
            "message", "Agent step executed successfully"
        );
    }
}
