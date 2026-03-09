package com.ghatana.core.pattern.learning;

import java.time.Instant;
import java.util.*;

/**
 * Tracks the evolution history of a pattern.
 *
 * @doc.type class
 * @doc.purpose Pattern evolution history tracking
 * @doc.layer core
 * @doc.pattern Learning
 */
public class PatternEvolutionHistory {

    private final List<EvolutionStep> evolutionSteps;
    private final Instant creationTime;

    public PatternEvolutionHistory() {
        this.evolutionSteps = new ArrayList<>();
        this.creationTime = Instant.now();
    }

    public List<EvolutionStep> getEvolutionSteps() { return new ArrayList<>(evolutionSteps); }
    public Instant getCreationTime() { return creationTime; }

    public void addEvolutionStep(EvolutionStep step) {
        evolutionSteps.add(step);
    }

    public int getEvolutionCount() { return evolutionSteps.size(); }

    public EvolutionStep getLastEvolution() {
        return evolutionSteps.isEmpty() ? null : evolutionSteps.get(evolutionSteps.size() - 1);
    }

    public double getFitnessImprovement() {
        if (evolutionSteps.size() < 2) {
            return 0.0;
        }
        
        double initialFitness = evolutionSteps.get(0).getFitnessScore();
        double currentFitness = evolutionSteps.get(evolutionSteps.size() - 1).getFitnessScore();
        
        return currentFitness - initialFitness;
    }

    /**
     * Represents a single evolution step.
     */
    public static class EvolutionStep {
        private final Instant timestamp;
        private final String evolutionType;
        private final double fitnessScore;
        private final Map<String, Object> changes;

        public EvolutionStep(String evolutionType, double fitnessScore, Map<String, Object> changes) {
            this.timestamp = Instant.now();
            this.evolutionType = evolutionType;
            this.fitnessScore = fitnessScore;
            this.changes = Map.copyOf(changes);
        }

        public Instant getTimestamp() { return timestamp; }
        public String getEvolutionType() { return evolutionType; }
        public double getFitnessScore() { return fitnessScore; }
        public Map<String, Object> getChanges() { return changes; }

        @Override
        public String toString() {
            return String.format("EvolutionStep{type=%s, fitness=%.3f, time=%s}",
                    evolutionType, fitnessScore, timestamp);
        }
    }
}
