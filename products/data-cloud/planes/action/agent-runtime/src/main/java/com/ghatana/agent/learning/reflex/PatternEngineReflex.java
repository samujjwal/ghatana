package com.ghatana.agent.learning.reflex;

import com.ghatana.agent.learning.SkillVersion;
import io.activej.promise.Promise;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Executes high-confidence policies directly without invoking the LLM.
 * Bypasses the REASON phase when confidence exceeds 0.95.
 *
 * @doc.type class
 * @doc.purpose High-confidence fast-paths bypassing LLMs
 * @doc.layer platform
 * @doc.pattern Engine
 */
public class PatternEngineReflex {

    private final Map<String, SkillVersion> fastPaths = new ConcurrentHashMap<>();
    private static final double CONFIDENCE_THRESHOLD = 0.95;

    public void registerPolicy(String workflowPattern, SkillVersion skill) {
        if (skill.getConfidence() >= CONFIDENCE_THRESHOLD) {
            fastPaths.put(workflowPattern, skill);
        }
    }

    public Promise<Boolean> canBypassLlm(String eventType) {
        return Promise.of(fastPaths.containsKey(eventType));
    }

    public Promise<SkillVersion> executeReflex(String eventType) {
        if (!fastPaths.containsKey(eventType)) {
            return Promise.ofException(new IllegalArgumentException("No high-confidence reflex for " + eventType));
        }
        return Promise.of(fastPaths.get(eventType));
    }
}
