package com.ghatana.pattern.engine.evaluator;

import com.ghatana.pattern.engine.nfa.NFA;
import com.ghatana.pattern.engine.nfa.NFAState;
import com.ghatana.platform.domain.domain.event.GEvent;

import java.time.Instant;
import java.util.*;

/**
 * Probabilistic evaluator for Complex Event Processing with uncertainty handling.
 * 
 * Day 26 Implementation: NFA-based pattern matching with probability per state
 * Supports confidence scoring and uncertainty propagation through pattern matches
 */
public class ProbabilisticEvaluator {
    
    /**
     * Represents a state with associated probability during pattern matching.
     */
    public static class ProbabilisticState {
        private final NFAState state;
        private final double probability;
        private final Instant timestamp;
        
        public ProbabilisticState(NFAState state, double probability, Instant timestamp) {
            this.state = state;
            this.probability = Math.max(0.0, Math.min(1.0, probability)); // Clamp to [0,1]
            this.timestamp = timestamp;
        }
        
        public NFAState getState() { return state; }
        public double getProbability() { return probability; }
        public Instant getTimestamp() { return timestamp; }
        
        @Override
        public String toString() {
            return "ProbState{" + state.getId() + ", p=" + String.format("%.3f", probability) + "}";
        }
    }
    
    /**
     * Represents a pattern match result with confidence score.
     */
    public static class PatternMatch {
        private final String patternName;
        private final double confidence;
        private final List<GEvent> matchedEvents;
        private final Instant matchTime;
        
        public PatternMatch(String patternName, double confidence, List<GEvent> matchedEvents, Instant matchTime) {
            this.patternName = patternName;
            this.confidence = confidence;
            this.matchedEvents = List.copyOf(matchedEvents);
            this.matchTime = matchTime;
        }
        
        public String getPatternName() { return patternName; }
        public double getConfidence() { return confidence; }
        public List<GEvent> getMatchedEvents() { return matchedEvents; }
        public Instant getMatchTime() { return matchTime; }
        
        @Override
        public String toString() {
            return "PatternMatch{" + patternName + ", confidence=" + String.format("%.3f", confidence) + 
                   ", events=" + matchedEvents.size() + "}";
        }
    }
    
    private final NFA nfa;
    private final double confidenceThreshold;
    private Set<ProbabilisticState> currentStates;
    private final List<GEvent> eventHistory;
    
    public ProbabilisticEvaluator(NFA nfa, double confidenceThreshold) {
        this.nfa = nfa;
        this.confidenceThreshold = Math.max(0.0, Math.min(1.0, confidenceThreshold));
        this.currentStates = new HashSet<>();
        this.eventHistory = new ArrayList<>();
        
        // Initialize with start state at full probability
        ProbabilisticState startState = new ProbabilisticState(nfa.getStartState(), 1.0, Instant.now());
        this.currentStates = Set.of(startState);
    }
    
    /**
     * Process a new event and update probabilistic states.
     * 
     * @param event The incoming event
     * @return Optional pattern match if confidence threshold is exceeded
     */
    public Optional<PatternMatch> processEvent(GEvent event) {
        if (event == null) {
            return Optional.empty();
        }
        
        eventHistory.add(event);
        Instant eventTime = event.getTimestamp() != null ? event.getTimestamp() : Instant.now();
        
        // Convert probabilistic states to regular states for NFA step
        Set<NFAState> regularStates = new HashSet<>();
        Map<NFAState, Double> stateProbabilities = new HashMap<>();
        
        for (ProbabilisticState probState : currentStates) {
            regularStates.add(probState.getState());
            stateProbabilities.put(probState.getState(), probState.getProbability());
        }
        
        // Execute NFA step
        Set<NFAState> nextStates = nfa.step(regularStates, event.getType(), eventTime);
        
        // Update probabilistic states with uncertainty propagation
        Set<ProbabilisticState> newProbStates = new HashSet<>();
        
        for (NFAState nextState : nextStates) {
            // Calculate probability based on transition and event uncertainty
            double transitionProb = calculateTransitionProbability(nextState, event);
            double eventCertainty = getEventCertainty(event);
            
            // Find maximum incoming probability from previous states
            double maxIncomingProb = stateProbabilities.values().stream()
                    .mapToDouble(Double::doubleValue)
                    .max()
                    .orElse(0.0);
            
            // Combine probabilities: P(transition) * P(event) * P(previous_state)
            double combinedProb = transitionProb * eventCertainty * maxIncomingProb;
            
            if (combinedProb >= confidenceThreshold) {
                newProbStates.add(new ProbabilisticState(nextState, combinedProb, eventTime));
            }
        }
        
        // Update current states
        currentStates = newProbStates;
        
        // Check for accepting states and pattern matches
        return checkForPatternMatch(eventTime);
    }
    
    /**
     * Calculate transition probability based on pattern complexity and constraints.
     */
    private double calculateTransitionProbability(NFAState state, @SuppressWarnings("unused") GEvent event) {
        // Base probability - can be enhanced with machine learning models
        double baseProb = 0.9;
        
        // Adjust based on state type
        switch (state.getType()) {
            case START -> baseProb = 1.0;
            case END -> baseProb = 0.95; // Slightly lower for accepting states
            case INTERMEDIATE -> baseProb = 0.9;
            case NEGATION -> baseProb = 0.8; // More uncertain for negation
            case TIMED -> baseProb = 0.85; // Time constraints add uncertainty
            case REPEAT -> baseProb = 0.75; // Repeat patterns more complex
        }
        
        // Additional factors could include:
        // - Event attribute confidence
        // - Historical pattern match rates
        // - Data quality metrics
        // - Domain-specific uncertainty models
        
        return baseProb;
    }
    
    /**
     * Extract event certainty from event metadata or attributes.
     */
    private double getEventCertainty(GEvent event) {
        // Check if event has uncertainty in headers or payload
        String certaintyHeader = event.getHeader("certainty");
        if (certaintyHeader != null) {
            try {
                double certainty = Double.parseDouble(certaintyHeader);
                return Math.max(0.0, Math.min(1.0, certainty));
            } catch (NumberFormatException e) {
                // Fall back to payload check
            }
        }
        
        // Check payload for certainty
        Object certaintyPayload = event.getPayload("certainty");
        if (certaintyPayload instanceof Number) {
            return Math.max(0.0, Math.min(1.0, ((Number) certaintyPayload).doubleValue()));
        }
        
        // Default certainty for events without explicit uncertainty
        return 0.95;
    }
    
    /**
     * Check current states for pattern matches above confidence threshold.
     */
    private Optional<PatternMatch> checkForPatternMatch(Instant eventTime) {
        double maxConfidence = 0.0;
        ProbabilisticState bestMatch = null;
        
        for (ProbabilisticState probState : currentStates) {
            if (nfa.getAcceptingStates().contains(probState.getState())) {
                if (probState.getProbability() > maxConfidence) {
                    maxConfidence = probState.getProbability();
                    bestMatch = probState;
                }
            }
        }
        
        if (bestMatch != null && maxConfidence >= confidenceThreshold) {
            PatternMatch match = new PatternMatch(
                nfa.getPatternName(),
                maxConfidence,
                new ArrayList<>(eventHistory), // Copy current event history
                eventTime
            );
            
            // Reset for next pattern matching cycle
            reset();
            
            return Optional.of(match);
        }
        
        return Optional.empty();
    }
    
    /**
     * Reset evaluator state for next pattern matching cycle.
     */
    public void reset() {
        ProbabilisticState startState = new ProbabilisticState(nfa.getStartState(), 1.0, Instant.now());
        this.currentStates = Set.of(startState);
        this.eventHistory.clear();
    }
    
    /**
     * Get current probabilistic states for debugging/monitoring.
     */
    public Set<ProbabilisticState> getCurrentStates() {
        return Collections.unmodifiableSet(currentStates);
    }
    
    /**
     * Get confidence threshold.
     */
    public double getConfidenceThreshold() {
        return confidenceThreshold;
    }
    
    /**
     * Get current event history.
     */
    public List<GEvent> getEventHistory() {
        return Collections.unmodifiableList(eventHistory);
    }
}