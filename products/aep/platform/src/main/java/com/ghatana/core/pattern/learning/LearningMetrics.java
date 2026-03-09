package com.ghatana.core.pattern.learning;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics for the real-time pattern learning engine.
 *
 * @doc.type class
 * @doc.purpose Learning engine performance metrics
 * @doc.layer core
 * @doc.pattern Metrics
 */
public class LearningMetrics {

    private final AtomicLong eventsProcessed = new AtomicLong(0);
    private final AtomicLong patternsDiscovered = new AtomicLong(0);
    private final AtomicLong patternsOptimized = new AtomicLong(0);
    private final AtomicLong patternsEvolved = new AtomicLong(0);
    private final AtomicLong learningCycles = new AtomicLong(0);
    private final AtomicLong optimizationCycles = new AtomicLong(0);
    private final AtomicLong evolutionCycles = new AtomicLong(0);
    private final AtomicLong errors = new AtomicLong(0);
    
    private final Instant startTime;

    public LearningMetrics() {
        this.startTime = Instant.now();
    }

    public long getEventsProcessed() { return eventsProcessed.get(); }
    public long getPatternsDiscovered() { return patternsDiscovered.get(); }
    public long getPatternsOptimized() { return patternsOptimized.get(); }
    public long getPatternsEvolved() { return patternsEvolved.get(); }
    public long getLearningCycles() { return learningCycles.get(); }
    public long getOptimizationCycles() { return optimizationCycles.get(); }
    public long getEvolutionCycles() { return evolutionCycles.get(); }
    public long getErrors() { return errors.get(); }
    public Instant getStartTime() { return startTime; }

    public void incrementEventsProcessed() { eventsProcessed.incrementAndGet(); }
    public void incrementPatternsDiscovered() { patternsDiscovered.incrementAndGet(); }
    public void incrementPatternsOptimized() { patternsOptimized.incrementAndGet(); }
    public void incrementPatternsEvolved() { patternsEvolved.incrementAndGet(); }
    public void incrementLearningCycles() { learningCycles.incrementAndGet(); }
    public void incrementOptimizationCycles() { optimizationCycles.incrementAndGet(); }
    public void incrementEvolutionCycles() { evolutionCycles.incrementAndGet(); }
    public void incrementErrors() { errors.incrementAndGet(); }

    public double getEventsPerSecond() {
        long duration = System.currentTimeMillis() - startTime.toEpochMilli();
        return duration > 0 ? (eventsProcessed.get() * 1000.0) / duration : 0.0;
    }

    public double getDiscoveryRate() {
        long totalEvents = eventsProcessed.get();
        return totalEvents > 0 ? (double) patternsDiscovered.get() / totalEvents : 0.0;
    }

    public double getErrorRate() {
        long totalEvents = eventsProcessed.get();
        return totalEvents > 0 ? (double) errors.get() / totalEvents : 0.0;
    }

    @Override
    public String toString() {
        return String.format(
                "LearningMetrics{events=%d, patterns=%d, cycles=%d, errors=%d, rate=%.2f/s}",
                eventsProcessed.get(), patternsDiscovered.get(), learningCycles.get(),
                errors.get(), getEventsPerSecond()
        );
    }
}
