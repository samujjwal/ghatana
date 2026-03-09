package com.ghatana.aep.detectionengine.uncertainty;

import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Handles temporal uncertainty in event detection.
 * Manages timing jitter, event boundaries, and temporal windows.
 *
 * @doc.type class
 * @doc.purpose Quantifies and manages uncertainty from temporal variations in events
 * @doc.layer core
 * @doc.pattern Service
 */
public class TemporalUncertainty {
    private static final Logger log = LoggerFactory.getLogger(TemporalUncertainty.class);

    private final Eventloop eventloop;
    private final Duration jitterTolerance;
    private final Duration maxEventGap;
    private final double boundaryConfidence;

    /**
     * Creates a TemporalUncertainty handler with specified tolerances.
     *
     * @param jitterTolerance Acceptable timing jitter
     * @param maxEventGap Maximum gap between correlated events
     * @param boundaryConfidence Confidence for boundary detection (0.0-1.0)
     */
    public TemporalUncertainty(
            Duration jitterTolerance,
            Duration maxEventGap,
            double boundaryConfidence) {

        this(createEventloop(), jitterTolerance, maxEventGap, boundaryConfidence);
    }

    public TemporalUncertainty(
            Eventloop eventloop,
            Duration jitterTolerance,
            Duration maxEventGap,
            double boundaryConfidence) {

        if (jitterTolerance.isNegative()) {
            throw new IllegalArgumentException("jitterTolerance cannot be negative");
        }
        if (maxEventGap.isNegative()) {
            throw new IllegalArgumentException("maxEventGap cannot be negative");
        }
        if (boundaryConfidence < 0.0 || boundaryConfidence > 1.0) {
            throw new IllegalArgumentException("boundaryConfidence must be between 0.0 and 1.0");
        }

        this.eventloop = eventloop;
        this.jitterTolerance = jitterTolerance;
        this.maxEventGap = maxEventGap;
        this.boundaryConfidence = boundaryConfidence;
    }

    private static Eventloop createEventloop() {
        return Eventloop.builder()
                .withCurrentThread()
                .build();
    }

    /**
     * Calculates timing uncertainty for an event pair.
     *
     * @param event1Time First event timestamp
     * @param event2Time Second event timestamp
     * @return Promise of timing uncertainty
     */
    public Promise<TimingUncertainty> calculateTimingUncertainty(
            Instant event1Time,
            Instant event2Time) {

        try {
            Duration gap = Duration.between(event1Time, event2Time).abs();

            // Calculate jitter impact
            double jitterImpact = calculateJitterImpact(gap);

            // Determine if gap is acceptable
            boolean withinTolerance = gap.compareTo(maxEventGap) <= 0;

            // Calculate confidence adjustment based on gap
            double confidenceAdjustment = calculateConfidenceAdjustment(gap);

            log.debug("Timing uncertainty: gap={}ms, jitter={:.2f}, within tolerance={}",
                    gap.toMillis(), jitterImpact, withinTolerance);

            return Promise.of(new TimingUncertainty(
                    gap,
                    jitterImpact,
                    withinTolerance,
                    confidenceAdjustment
            ));
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }

    /**
     * Detects temporal boundaries in event sequences.
     * Identifies where patterns start/end based on timing gaps.
     *
     * @param eventTimes List of event timestamps
     * @return Promise of detected boundaries
     */
    public Promise<List<BoundaryDetection>> detectTemporalBoundaries(
            List<Instant> eventTimes) {

        try {
            log.debug("Detecting temporal boundaries in {} events", eventTimes.size());

            if (eventTimes.size() < 2) {
                return Promise.of(List.of());
            }

            List<Instant> sorted = new ArrayList<>(eventTimes);
            sorted.sort(Instant::compareTo);

            List<BoundaryDetection> boundaries = new ArrayList<>();

            for (int i = 1; i < sorted.size(); i++) {
                Duration gap = Duration.between(sorted.get(i - 1), sorted.get(i));

                if (gap.compareTo(maxEventGap) > 0) {
                    // Significant gap detected - likely boundary
                    double confidence = calculateBoundaryConfidence(gap);

                    boundaries.add(new BoundaryDetection(
                            i,
                            sorted.get(i - 1),
                            sorted.get(i),
                            gap,
                            confidence
                    ));

                    log.debug("Boundary detected at index {}: gap={}ms, confidence={:.2f}",
                            i, gap.toMillis(), confidence);
                }
            }

            return Promise.of(boundaries);
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }

    /**
     * Adjusts confidence based on temporal window quality.
     *
     * @param windowStart Window start time
     * @param windowEnd Window end time
     * @param baseConfidence Base confidence before adjustment
     * @return Promise of adjusted confidence
     */
    public Promise<Double> adjustForTemporalWindow(
            Instant windowStart,
            Instant windowEnd,
            double baseConfidence) {

        return Promise.ofBlocking(eventloop, () -> {
            Duration windowSize = Duration.between(windowStart, windowEnd);

            if (windowSize.isNegative()) {
                throw new IllegalArgumentException("Window end must be after start");
            }

            // Smaller windows = higher confidence (less chance of false positives)
            // Larger windows = lower confidence (more uncertainty)
            double windowFactor = calculateWindowFactor(windowSize);
            double adjusted = baseConfidence * windowFactor;

            log.debug("Window adjustment: {}ms window, factor={:.2f}, {:.3f} -> {:.3f}",
                    windowSize.toMillis(), windowFactor, baseConfidence, adjusted);

            return adjusted;
        });
    }

    /**
     * Estimates optimal time window for pattern detection.
     *
     * @param historicalGaps Historical gaps between events
     * @return Promise of recommended window size
     */
    public Promise<WindowRecommendation> estimateOptimalWindow(
            List<Duration> historicalGaps) {

        try {
            if (historicalGaps.isEmpty()) {
                Duration defaultWindow = maxEventGap.multipliedBy(2);
                return Promise.of(new WindowRecommendation(
                        defaultWindow,
                        0.5,
                        "Default window (no historical data)"
                ));
            }

            // Calculate statistics
            DoubleSummaryStatistics stats = historicalGaps.stream()
                    .mapToDouble(Duration::toMillis)
                    .summaryStatistics();

            double mean = stats.getAverage();
            double max = stats.getMax();

            // Recommend window = mean + 2*stddev (captures ~95% of cases)
            double stddev = calculateStdDev(historicalGaps, mean);
            long recommendedMs = (long) (mean + 2 * stddev);
            Duration recommended = Duration.ofMillis(Math.max(recommendedMs, (long) max));

            // Confidence based on data consistency
            double confidence = calculateWindowConfidence(historicalGaps, stddev, mean);

            log.info("Optimal window: {}ms (mean={:.0f}, stddev={:.0f}, confidence={:.2f})",
                    recommended.toMillis(), mean, stddev, confidence);

            return Promise.of(new WindowRecommendation(
                    recommended,
                    confidence,
                    String.format("Based on %d historical samples", historicalGaps.size())
            ));
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }

    /**
     * Checks if two events are temporally correlated.
     *
     * @param event1Time First event time
     * @param event2Time Second event time
     * @return Promise of correlation result
     */
    public Promise<TemporalCorrelation> checkTemporalCorrelation(
            Instant event1Time,
            Instant event2Time) {

        try {
            Duration gap = Duration.between(event1Time, event2Time).abs();

            boolean correlated = gap.compareTo(maxEventGap) <= 0;
            double strength = calculateCorrelationStrength(gap);

            log.debug("Temporal correlation: gap={}ms, correlated={}, strength={:.2f}",
                    gap.toMillis(), correlated, strength);

            return Promise.of(new TemporalCorrelation(
                    correlated,
                    strength,
                    gap
            ));
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }

    // Helper methods

    private double calculateJitterImpact(Duration gap) {
        if (gap.compareTo(jitterTolerance) <= 0) {
            return 0.0; // Within tolerance, no impact
        }

        // Linear increase in impact beyond tolerance
        long excessMs = gap.minus(jitterTolerance).toMillis();
        long toleranceMs = jitterTolerance.toMillis();

        return Math.min(1.0, (double) excessMs / toleranceMs);
    }

    private double calculateConfidenceAdjustment(Duration gap) {
        if (gap.compareTo(jitterTolerance) <= 0) {
            return 1.0; // No adjustment needed
        }

        if (gap.compareTo(maxEventGap) > 0) {
            return 0.0; // Beyond max gap
        }

        // Linear decay between jitter tolerance and max gap
        long gapMs = gap.toMillis();
        long jitterMs = jitterTolerance.toMillis();
        long maxMs = maxEventGap.toMillis();

        return 1.0 - ((double) (gapMs - jitterMs) / (maxMs - jitterMs));
    }

    private double calculateBoundaryConfidence(Duration gap) {
        // Confidence increases with gap size, using logarithmic scale to differentiate large gaps
        double gapRatio = (double) gap.toMillis() / maxEventGap.toMillis();
        
        // Use logarithmic scaling for better differentiation at larger gaps
        // This ensures that gaps of 3s and 10s have different confidences
        double scaledRatio = Math.log1p(gapRatio) / Math.log1p(5.0); // Normalize to log scale
        
        return Math.min(1.0, boundaryConfidence * (0.5 + scaledRatio * 0.5));
    }

    private double calculateWindowFactor(Duration windowSize) {
        // Optimal window is maxEventGap
        long windowMs = windowSize.toMillis();
        long optimalMs = maxEventGap.toMillis();

        if (windowMs <= optimalMs) {
            return 1.0; // Ideal or smaller
        }

        // Decay factor for larger windows
        double ratio = (double) windowMs / optimalMs;
        return 1.0 / Math.sqrt(ratio); // Square root decay
    }

    private double calculateStdDev(List<Duration> gaps, double mean) {
        double sumSquares = gaps.stream()
                .mapToDouble(Duration::toMillis)
                .map(ms -> Math.pow(ms - mean, 2))
                .sum();

        return Math.sqrt(sumSquares / gaps.size());
    }

    private double calculateWindowConfidence(List<Duration> gaps, double stddev, double mean) {
        if (mean == 0.0) {
            return 0.5;
        }

        // Coefficient of variation: lower = more consistent = higher confidence
        double cv = stddev / mean;

        return Math.max(0.0, Math.min(1.0, 1.0 - cv));
    }

    private double calculateCorrelationStrength(Duration gap) {
        if (gap.compareTo(jitterTolerance) <= 0) {
            return 1.0; // Perfect correlation
        }

        if (gap.compareTo(maxEventGap) > 0) {
            return 0.0; // No correlation
        }

        // Linear decay
        long gapMs = gap.toMillis();
        long jitterMs = jitterTolerance.toMillis();
        long maxMs = maxEventGap.toMillis();

        return 1.0 - ((double) (gapMs - jitterMs) / (maxMs - jitterMs));
    }

    // Value objects

    /**
     * Timing uncertainty between events.
     */
    public record TimingUncertainty(
            Duration gap,
            double jitterImpact,
            boolean withinTolerance,
            double confidenceAdjustment
    ) {}

    /**
     * Detected temporal boundary.
     */
    public record BoundaryDetection(
            int index,
            Instant beforeTime,
            Instant afterTime,
            Duration gap,
            double confidence
    ) {}

    /**
     * Window size recommendation.
     */
    public record WindowRecommendation(
            Duration recommendedWindow,
            double confidence,
            String rationale
    ) {}

    /**
     * Temporal correlation between events.
     */
    public record TemporalCorrelation(
            boolean correlated,
            double strength,
            Duration gap
    ) {}
}
