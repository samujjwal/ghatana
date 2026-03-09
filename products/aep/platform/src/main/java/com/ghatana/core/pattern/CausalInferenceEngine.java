package com.ghatana.core.pattern;

import com.ghatana.platform.domain.domain.event.Event;
import java.util.*;

/**
 * Causal inference engine for detecting causal relationships between events.
 *
 * @see AdvancedCorrelationEngine
 * @doc.type class
 * @doc.purpose Causal relationship inference
 * @doc.layer core
 * @doc.pattern Inference
 */
public class CausalInferenceEngine {

    private final CorrelationEngineConfig.CausalInferenceConfig config;

    public CausalInferenceEngine(CorrelationEngineConfig.CausalInferenceConfig config) {
        this.config = config;
    }

    /**
     * Infer causal relationships from correlated events.
     */
    public List<CausalRelationship> inferCausality(List<Event> events) {
        List<CausalRelationship> relationships = new ArrayList<>();
        
        // Simple temporal causality inference
        List<Event> sortedEvents = events.stream()
                .sorted(Comparator.comparing(Event::getTimestamp))
                .toList();
        
        for (int i = 0; i < sortedEvents.size(); i++) {
            for (int j = i + 1; j < sortedEvents.size(); j++) {
                Event cause = sortedEvents.get(i);
                Event effect = sortedEvents.get(j);
                
                if (isCausalCandidate(cause, effect)) {
                    double confidence = calculateCausalConfidence(cause, effect, sortedEvents);
                    if (confidence >= config.getConfidenceThreshold()) {
                        relationships.add(new CausalRelationship(cause, effect, confidence));
                    }
                }
            }
        }
        
        return relationships;
    }

    private boolean isCausalCandidate(Event cause, Event effect) {
        // Temporal precedence
        if (cause.getTimestampMillis() >= effect.getTimestampMillis()) {
            return false;
        }
        
        // Time window constraint
        long timeDiff = effect.getTimestampMillis() - cause.getTimestampMillis();
        return timeDiff <= 300000; // Within 5 minutes
    }

    private double calculateCausalConfidence(Event cause, Event effect, List<Event> allEvents) {
        // Simplified confidence calculation
        double temporalScore = calculateTemporalScore(cause, effect, allEvents);
        double semanticScore = calculateSemanticScore(cause, effect);
        
        return (temporalScore + semanticScore) / 2.0;
    }

    private double calculateTemporalScore(Event cause, Event effect, List<Event> allEvents) {
        // Count how often cause precedes effect
        long causeCount = allEvents.stream()
                .filter(e -> e.getType().equals(cause.getType()))
                .count();
        
        long effectAfterCause = allEvents.stream()
                .filter(e -> e.getType().equals(effect.getType()))
                .filter(e -> e.getTimestampMillis() > cause.getTimestampMillis())
                .count();
        
        return causeCount > 0 ? (double) effectAfterCause / causeCount : 0.0;
    }

    private double calculateSemanticScore(Event cause, Event effect) {
        // Simple semantic similarity based on event types
        String causeType = cause.getType();
        String effectType = effect.getType();
        
        // Predefined causal patterns
        if (isKnownCausalPattern(causeType, effectType)) {
            return 0.8;
        }
        
        return 0.3; // Default low confidence
    }

    private boolean isKnownCausalPattern(String causeType, String effectType) {
        // Known causal patterns
        return (causeType.equals("user.login") && effectType.equals("user.action")) ||
               (causeType.equals("click") && effectType.equals("purchase")) ||
               (causeType.equals("error") && effectType.equals("system.restart"));
    }

    /**
     * Causal relationship representation.
     */
    public static class CausalRelationship {
        private final Event cause;
        private final Event effect;
        private final double confidence;

        public CausalRelationship(Event cause, Event effect, double confidence) {
            this.cause = cause;
            this.effect = effect;
            this.confidence = confidence;
        }

        public Event getCause() { return cause; }
        public Event getEffect() { return effect; }
        public double getConfidence() { return confidence; }

        @Override
        public String toString() {
            return String.format("CausalRelationship{cause=%s, effect=%s, confidence=%.2f}",
                    cause.getType(), effect.getType(), confidence);
        }
    }
}
