package com.ghatana.dataexploration.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents a group of correlated event types discovered through pattern mining.
 * 
 * Day 28 Implementation: Correlated event groups for pattern exploration.
 */
public class CorrelatedEventGroup {
    
    private final Set<String> eventTypes;
    private final double confidence;
    private final double support;
    private final Map<String, Double> correlationMatrix;
    private final List<CorrelationRule> rules;
    private final String groupId;
    
    public CorrelatedEventGroup(Set<String> eventTypes, double confidence, double support,
                               Map<String, Double> correlationMatrix, List<CorrelationRule> rules,
                               String groupId) {
        this.eventTypes = eventTypes;
        this.confidence = confidence;
        this.support = support;
        this.correlationMatrix = correlationMatrix;
        this.rules = rules;
        this.groupId = groupId;
    }
    
    public Set<String> getEventTypes() {
        return eventTypes;
    }
    
    public double getConfidence() {
        return confidence;
    }
    
    public double getSupport() {
        return support;
    }
    
    public Map<String, Double> getCorrelationMatrix() {
        return correlationMatrix;
    }
    
    public List<CorrelationRule> getRules() {
        return rules;
    }
    
    public String getGroupId() {
        return groupId;
    }
    
    public int getSize() {
        return eventTypes.size();
    }
    
    public boolean contains(String eventType) {
        return eventTypes.contains(eventType);
    }
    
    public static class CorrelationRule {
        private final String antecedent;
        private final String consequent;
        private final double confidence;
        private final double lift;
        
        public CorrelationRule(String antecedent, String consequent, double confidence, double lift) {
            this.antecedent = antecedent;
            this.consequent = consequent;
            this.confidence = confidence;
            this.lift = lift;
        }
        
        public String getAntecedent() {
            return antecedent;
        }
        
        public String getConsequent() {
            return consequent;
        }
        
        public double getConfidence() {
            return confidence;
        }
        
        public double getLift() {
            return lift;
        }
        
        @Override
        public String toString() {
            return String.format("%s → %s (conf=%.3f, lift=%.3f)", antecedent, consequent, confidence, lift);
        }
    }
}