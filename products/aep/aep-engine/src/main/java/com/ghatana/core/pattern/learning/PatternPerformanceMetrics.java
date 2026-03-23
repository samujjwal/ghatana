package com.ghatana.core.pattern.learning;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Performance metrics for learned patterns.
 *
 * @doc.type class
 * @doc.purpose Pattern performance metrics tracking
 * @doc.layer core
 * @doc.pattern Learning
 */
public class PatternPerformanceMetrics {

    private final AtomicLong totalExecutions = new AtomicLong(0);
    private final AtomicLong successfulMatches = new AtomicLong(0);
    private final AtomicLong falsePositives = new AtomicLong(0);
    private final AtomicLong falseNegatives = new AtomicLong(0);
    private final AtomicLong totalExecutionTime = new AtomicLong(0);
    
    private volatile double accuracy = 0.0;
    private volatile double precision = 0.0;
    private volatile double recall = 0.0;
    private volatile double f1Score = 0.0;
    private volatile double averageExecutionTime = 0.0;
    
    private final Instant creationTime;

    public PatternPerformanceMetrics() {
        this.creationTime = Instant.now();
    }

    public long getTotalExecutions() { return totalExecutions.get(); }
    public long getSuccessfulMatches() { return successfulMatches.get(); }
    public long getFalsePositives() { return falsePositives.get(); }
    public long getFalseNegatives() { return falseNegatives.get(); }
    public long getTotalExecutionTime() { return totalExecutionTime.get(); }
    
    public double getAccuracy() { return accuracy; }
    public double getPrecision() { return precision; }
    public double getRecall() { return recall; }
    public double getF1Score() { return f1Score; }
    public double getAverageExecutionTime() { return averageExecutionTime; }
    
    public Instant getCreationTime() { return creationTime; }

    public void recordExecution(long executionTimeMs, boolean matched, boolean isTruePositive, boolean isFalseNegative) {
        totalExecutions.incrementAndGet();
        totalExecutionTime.addAndGet(executionTimeMs);
        
        if (matched) {
            successfulMatches.incrementAndGet();
        }
        
        if (isTruePositive) {
            // True positive - correctly identified match
        } else if (isFalseNegative) {
            falseNegatives.incrementAndGet();
        } else if (matched && !isTruePositive) {
            falsePositives.incrementAndGet();
        }
        
        updateCalculatedMetrics();
    }

    public void recordMatch(long executionTimeMs) {
        recordExecution(executionTimeMs, true, true, false);
    }

    public void recordFalsePositive(long executionTimeMs) {
        recordExecution(executionTimeMs, true, false, false);
    }

    public void recordFalseNegative(long executionTimeMs) {
        recordExecution(executionTimeMs, false, false, true);
    }

    private void updateCalculatedMetrics() {
        long total = totalExecutions.get();
        long matches = successfulMatches.get();
        long fp = falsePositives.get();
        long fn = falseNegatives.get();
        
        // Average execution time
        if (total > 0) {
            averageExecutionTime = (double) totalExecutionTime.get() / total;
        }
        
        // Accuracy
        long truePositives = matches - fp;
        if (total > 0) {
            accuracy = (double) (truePositives + (total - matches - fn)) / total;
        }
        
        // Precision
        if (matches > 0) {
            precision = (double) truePositives / matches;
        }
        
        // Recall
        long actualPositives = truePositives + fn;
        if (actualPositives > 0) {
            recall = (double) truePositives / actualPositives;
        }
        
        // F1 Score
        if (precision + recall > 0) {
            f1Score = 2 * (precision * recall) / (precision + recall);
        }
    }

    @Override
    public String toString() {
        return String.format(
                "PatternPerformanceMetrics{executions=%d, accuracy=%.3f, precision=%.3f, recall=%.3f, f1=%.3f, avgTime=%.1fms}",
                getTotalExecutions(), getAccuracy(), getPrecision(), getRecall(), getF1Score(), getAverageExecutionTime()
        );
    }
}
