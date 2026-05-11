/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

import com.ghatana.agent.AgentType;

import java.util.List;
import java.util.Map;

/**
 * @doc.type class
 * @doc.purpose Creates minimal canonical evaluation packs for each agent type
 * @doc.layer agent-core
 * @doc.pattern Factory
 */
/**
 * Creates minimal canonical evaluation packs for each agent type.
 */
public final class AgentTypeEvalPackFactory {

    public List<LearningReplayCase> defaultCases(AgentType type) {
        return switch (type) {
            case DETERMINISTIC -> List.of(caseOf("determinism-repeatability", "deterministic", true));
            case PROBABILISTIC -> List.of(caseOf("confidence-calibration", "confidenceModel", true));
            case STREAM_PROCESSOR -> List.of(caseOf("checkpoint-backpressure", "checkpointing", true));
            case PLANNING -> List.of(caseOf("plan-compensation", "compensationPolicy", true));
            case HYBRID -> List.of(caseOf("routing-fallback", "fallbackCondition", true));
            case ADAPTIVE -> List.of(caseOf("drift-control", "driftControls", true));
            case COMPOSITE -> List.of(caseOf("aggregation-disagreement", "disagreementHandling", true));
            case REACTIVE -> List.of(caseOf("trigger-idempotence", "idempotent", true));
            case CUSTOM -> List.of(caseOf("custom-owner-hook", "validationHook", true));
        };
    }

    private static LearningReplayCase caseOf(String id, String key, Object expectedValue) {
        return new LearningReplayCase(id, Map.of(), Map.of(key, expectedValue), 1.0);
    }
}
