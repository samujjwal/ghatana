/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.pattern.engine.agent;

import com.ghatana.aep.AepEngine;
import com.ghatana.pattern.api.model.DetectionPlan;
import com.ghatana.pattern.engine.evaluator.ProbabilisticEvaluator;
import com.ghatana.pattern.engine.nfa.NFA;
import com.ghatana.platform.domain.event.Event;
import com.ghatana.platform.domain.event.EventId;
import com.ghatana.platform.domain.event.EventTime;
import com.ghatana.platform.domain.event.EventStats;
import com.ghatana.platform.domain.event.EventRelations;
import com.ghatana.core.operator.OperatorResult;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapter that wraps {@link PatternDetectionAgent} to implement {@link AepEngine.PatternDetector}.
 * <p>
 * This adapter bridges the gap between:
 * <ul>
 *   <li>PatternDetectionAgent (uses platform domain Event)</li>
 *   <li>AepEngine.PatternDetector (uses AepEngine.Event)</li>
 * </ul>
 * <p>
 * It converts AepEngine.Event to platform Event, invokes PatternDetectionAgent, and converts
 * OperatorResult back to AepEngine.Detection.
 *
 * @doc.type class
 * @doc.purpose Adapter for integrating PatternDetectionAgent with AepEngine
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class PatternDetectionAgentAdapter implements AepEngine.PatternDetector {

    private static final Logger logger = LoggerFactory.getLogger(PatternDetectionAgentAdapter.class);

    private final PatternDetectionAgent agent;
    private final String patternId;

    /**
     * Creates an adapter for the given PatternDetectionAgent.
     *
     * @param agent the PatternDetectionAgent to wrap
     * @param patternId the pattern ID to use for detections from this agent
     */
    public PatternDetectionAgentAdapter(PatternDetectionAgent agent, String patternId) {
        this.agent = agent;
        this.patternId = patternId;
    }

    @Override
    public Promise<List<AepEngine.Detection>> detect(String tenantId, AepEngine.Event aepEvent,
                                                       List<AepEngine.Pattern> patterns) {
        try {
            // Convert AepEngine.Event to platform Event
            Event platformEvent = convertToPlatformEvent(tenantId, aepEvent);

            // Invoke PatternDetectionAgent
            return agent.process(platformEvent)
                .map(result -> {
                    List<AepEngine.Detection> detections = new ArrayList<>();
                    
                    // Convert OperatorResult to AepEngine.Detection
                    if (result.isSuccess() && result.hasOutput()) {
                        Map<String, Object> output = result.getOutput();
                        if (output != null && !output.isEmpty()) {
                            detections.add(new AepEngine.Detection(
                                patternId,
                                aepEvent.type(),
                                result.getConfidence(),
                                output,
                                Instant.now()
                            ));
                        }
                    }
                    
                    return detections;
                })
                .whenException(e -> {
                    logger.warn("PatternDetectionAgent failed for tenant={}, event={}: {}",
                        tenantId, aepEvent.type(), e.getMessage());
                    return Promise.of(List.of());
                });
        } catch (Exception e) {
            logger.error("Error in PatternDetectionAgentAdapter for tenant={}, event={}: {}",
                tenantId, aepEvent.type(), e.getMessage(), e);
            return Promise.of(List.of());
        }
    }

    /**
     * Converts AepEngine.Event to platform Event.
     */
    private Event convertToPlatformEvent(String tenantId, AepEngine.Event aepEvent) {
        return new Event() {
            @Override
            public EventId getId() {
                return new EventId(tenantId, aepEvent.type(), aepEvent.version(), UUID.randomUUID().toString());
            }

            @Override
            public EventTime getTime() {
                return new EventTime() {
                    @Override
                    public Instant getDetectionTimePoint() {
                        return aepEvent.timestamp();
                    }

                    @Override
                    public Instant getOccurrenceTime() {
                        return aepEvent.timestamp();
                    }
                };
            }

            @Override
            public Location getLocation() {
                return null;
            }

            @Override
            public EventStats getStats() {
                return new EventStats() {
                    @Override
                    public long getProcessingTimeMs() {
                        return 0;
                    }

                    @Override
                    public long getSizeBytes() {
                        return 0;
                    }
                };
            }

            @Override
            public EventRelations getRelations() {
                return new EventRelations(List.of(), List.of(), Map.of());
            }

            @Override
            public String getHeader(String name) {
                return aepEvent.headers().get(name);
            }

            @Override
            public Object getPayload(String name) {
                return aepEvent.payload().get(name);
            }

            @Override
            public Map<String, Object> toPayloadMap() {
                return Map.copyOf(aepEvent.payload());
            }

            @Override
            public boolean isIntervalBased() {
                return false;
            }
        };
    }

    /**
     * Creates a PatternDetectionAgentAdapter from a PatternDetectionAgent.
     *
     * @param agent the PatternDetectionAgent to wrap
     * @param patternId the pattern ID to use
     * @return a new PatternDetectionAgentAdapter
     */
    public static PatternDetectionAgentAdapter wrap(PatternDetectionAgent agent, String patternId) {
        return new PatternDetectionAgentAdapter(agent, patternId);
    }
}
