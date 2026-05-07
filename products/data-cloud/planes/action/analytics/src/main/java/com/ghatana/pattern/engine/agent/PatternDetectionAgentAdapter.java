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
import com.ghatana.platform.domain.event.GEvent;
import com.ghatana.core.operator.OperatorResult;
import com.ghatana.platform.types.time.GTimeInterval;
import com.ghatana.platform.types.time.GTimeUnit;
import com.ghatana.platform.types.time.GTimeValue;
import com.ghatana.platform.types.time.GTimestamp;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;

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

                    if (result.isSuccess()) {
                        for (Event outputEvent : result.getOutputEvents()) {
                            Map<String, Object> output = outputEvent.toPayloadMap();
                            if (!output.isEmpty()) {
                                double confidence = confidenceFrom(output);
                            detections.add(new AepEngine.Detection(
                                patternId,
                                aepEvent.type(),
                                    confidence,
                                output,
                                Instant.now()
                            ));
                        }
                        }
                    }

                    return detections;
                })
                .then(
                    Promise::of,
                    e -> {
                    logger.warn("PatternDetectionAgent failed for tenant={}, event={}: {}",
                        tenantId, aepEvent.type(), e.getMessage());
                        return Promise.of(List.of());
                    }
                );
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
        GTimestamp timestamp = GTimestamp.of(aepEvent.timestamp());
        EventTime eventTime = EventTime.builder()
            .occurrenceTime(GTimeInterval.between(timestamp, timestamp))
            .detectionTimePoint(timestamp)
            .validDuration(new GTimeValue(Long.MAX_VALUE, GTimeUnit.MILLISECONDS))
            .boundingInterval(GTimeInterval.between(timestamp, timestamp))
            .granularity(1)
            .build();

        return GEvent.builder()
            .id(EventId.create(
                aepEvent.idempotencyKey().orElseGet(() -> UUID.randomUUID().toString()),
                aepEvent.type(),
                aepEvent.version(),
                tenantId))
            .time(eventTime)
            .stats(EventStats.builder()
                .withProcessingTimeNanos(0)
                .withSizeInBytes(aepEvent.payload().toString().length())
                .withFieldCount(aepEvent.payload().size())
                .withTagCount(aepEvent.headers().size())
                .build())
            .relations(EventRelations.empty())
            .headers(new LinkedHashMap<>(aepEvent.headers()))
            .payload(new LinkedHashMap<>(aepEvent.payload()))
            .intervalBased(false)
            .build();
    }

    private double confidenceFrom(Map<String, Object> output) {
        Object value = output.get("confidence");
        if (value instanceof Number number) {
            return Math.max(0.0, Math.min(1.0, number.doubleValue()));
        }
        return 1.0;
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
