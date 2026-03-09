package com.ghatana.yappc.client.impl;

import com.ghatana.yappc.client.*;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Embedded lifecycle service for in-process project lifecycle management.
 *
 * @author YAPPC Team
 * @version 1.0.0
 * @since 1.0.0
 
 * @doc.type class
 * @doc.purpose Handles embedded lifecycle service operations
 * @doc.layer core
 * @doc.pattern Service
* @doc.gaa.lifecycle perceive
*/
final class EmbeddedLifecycleService {
    
    private static final Logger logger = LoggerFactory.getLogger(EmbeddedLifecycleService.class);
    
    private final YAPPCConfig config;
    private final Map<String, LifecycleState> projectStates;
    private volatile boolean initialized = false;
    
    EmbeddedLifecycleService(YAPPCConfig config) {
        this.config = config;
        this.projectStates = new ConcurrentHashMap<>();
    }
    
    void initialize() {
        logger.info("Initializing embedded lifecycle service");
        initialized = true;
    }
    
    void shutdown() {
        logger.info("Shutting down embedded lifecycle service");
        projectStates.clear();
        initialized = false;
    }
    
    boolean isHealthy() {
        return initialized;
    }
    
    Promise<LifecycleState> getState(String projectId) {
        return Promise.ofCallback(cb -> {
            try {
                LifecycleState state = projectStates.computeIfAbsent(
                    projectId,
                    id -> new LifecycleState(id, "planning", "active")
                );
                
                logger.debug("Retrieved lifecycle state for project: {}", projectId);
                cb.set(state);
            } catch (Exception e) {
                logger.error("Failed to get lifecycle state: {}", projectId, e);
                cb.setException(e);
            }
        });
    }
    
    Promise<PhaseResult> advancePhase(String projectId, AdvancePhaseRequest request) {
        return Promise.ofCallback(cb -> {
            try {
                LifecycleState newState = new LifecycleState(
                    projectId,
                    request.getTargetPhase(),
                    "active"
                );
                
                projectStates.put(projectId, newState);
                
                logger.info("Advanced project {} to phase: {}", projectId, request.getTargetPhase());
                
                PhaseResult result = new PhaseResult(projectId, request.getTargetPhase(), true);
                cb.set(result);
            } catch (Exception e) {
                logger.error("Failed to advance phase for project: {}", projectId, e);
                cb.setException(e);
            }
        });
    }
}
