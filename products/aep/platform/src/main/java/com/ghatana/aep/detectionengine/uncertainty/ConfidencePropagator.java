package com.ghatana.aep.detectionengine.uncertainty;

import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Propagates confidence scores through event sequences.
 * Handles confidence degradation and uncertainty accumulation in complex patterns.
 *
 * @doc.type class
 * @doc.purpose Manages confidence propagation through multi-event pattern chains
 * @doc.layer core
 * @doc.pattern Service
 */
public class ConfidencePropagator {
    private static final Logger log = LoggerFactory.getLogger(ConfidencePropagator.class);

    private final Eventloop eventloop;
    private final double propagationDecay;
    private final double minConfidenceThreshold;
    private final PropagationStrategy strategy;

    /**
     * Creates a ConfidencePropagator with specified decay and threshold.
     *
     * @param propagationDecay Decay factor per hop (0.0-1.0)
     * @param minConfidenceThreshold Minimum viable confidence (0.0-1.0)
     * @param strategy Strategy for propagation (MULTIPLICATIVE, MINIMUM, AVERAGE)
     */
    public ConfidencePropagator(
            double propagationDecay,
            double minConfidenceThreshold,
            PropagationStrategy strategy) {

        this(createEventloop(), propagationDecay, minConfidenceThreshold, strategy);
    }

    public ConfidencePropagator(
            Eventloop eventloop,
            double propagationDecay,
            double minConfidenceThreshold,
            PropagationStrategy strategy) {

        if (propagationDecay < 0.0 || propagationDecay > 1.0) {
            throw new IllegalArgumentException("propagationDecay must be between 0.0 and 1.0");
        }
        if (minConfidenceThreshold < 0.0 || minConfidenceThreshold > 1.0) {
            throw new IllegalArgumentException("minConfidenceThreshold must be between 0.0 and 1.0");
        }

        this.eventloop = eventloop;
        this.propagationDecay = propagationDecay;
        this.minConfidenceThreshold = minConfidenceThreshold;
        this.strategy = strategy;
    }

    private static Eventloop createEventloop() {
        return Eventloop.builder()
                .withCurrentThread()
                .build();
    }

    /**
     * Propagates confidence through a sequence of events.
     *
     * @param eventConfidences List of confidence scores for each event
     * @return Promise of propagated confidence
     */
    public Promise<ConfidencePropagationResult> propagateConfidence(
            List<EventConfidence> eventConfidences) {

        return Promise.ofBlocking(eventloop, () -> {
            log.debug("Propagating confidence through {} events", eventConfidences.size());

            if (eventConfidences.isEmpty()) {
                return ConfidencePropagationResult.empty();
            }

            double finalConfidence = calculatePropagatedConfidence(eventConfidences);
            boolean meetsThreshold = finalConfidence >= minConfidenceThreshold;

            // Calculate per-hop decay
            List<Double> confidenceChain = buildConfidenceChain(eventConfidences);

            log.debug("Final confidence: {:.3f}, meets threshold: {}",
                    finalConfidence, meetsThreshold);

            return new ConfidencePropagationResult(
                    finalConfidence,
                    meetsThreshold,
                    eventConfidences.size(),
                    confidenceChain,
                    strategy
            );
        });
    }

    /**
     * Combines multiple confidence scores from parallel paths.
     *
     * @param parallelPaths List of confidence scores from different paths
     * @return Promise of combined confidence
     */
    public Promise<Double> combineParallelConfidences(List<Double> parallelPaths) {
        return Promise.ofBlocking(eventloop, () -> {
            if (parallelPaths.isEmpty()) {
                return 0.0;
            }

            double combined = switch (strategy) {
                case MULTIPLICATIVE -> parallelPaths.stream()
                        .reduce(1.0, (a, b) -> a * b);
                case MINIMUM -> parallelPaths.stream()
                        .mapToDouble(Double::doubleValue)
                        .min()
                        .orElse(0.0);
                case AVERAGE -> parallelPaths.stream()
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(0.0);
            };

            log.debug("Combined {} parallel confidences: {:.3f}",
                    parallelPaths.size(), combined);

            return combined;
        });
    }

    /**
     * Calculates uncertainty bounds around a confidence estimate.
     *
     * @param baseConfidence Base confidence score
     * @param sampleSize Number of observations
     * @return Promise of confidence with uncertainty bounds
     */
    public Promise<ConfidenceWithUncertainty> calculateUncertaintyBounds(
            double baseConfidence,
            int sampleSize) {

        return Promise.ofBlocking(eventloop, () -> {
            if (sampleSize <= 0) {
                throw new IllegalArgumentException("sampleSize must be positive");
            }

            // Wilson score interval approximation
            double z = 1.96; // 95% confidence
            double center = baseConfidence;
            double margin = z * Math.sqrt((baseConfidence * (1 - baseConfidence)) / sampleSize);

            double lowerBound = Math.max(0.0, center - margin);
            double upperBound = Math.min(1.0, center + margin);

            log.debug("Confidence {:.3f} ± {:.3f} (n={})",
                    center, margin, sampleSize);

            return new ConfidenceWithUncertainty(center, lowerBound, upperBound, margin, sampleSize);
        });
    }

    /**
     * Adjusts confidence based on evidence quality.
     *
     * @param baseConfidence Initial confidence
     * @param evidenceQuality Quality score (0.0-1.0)
     * @return Promise of adjusted confidence
     */
    public Promise<Double> adjustForEvidenceQuality(
            double baseConfidence,
            double evidenceQuality) {

        return Promise.ofBlocking(eventloop, () -> {
            if (evidenceQuality < 0.0 || evidenceQuality > 1.0) {
                throw new IllegalArgumentException("evidenceQuality must be between 0.0 and 1.0");
            }

            // Confidence degrades with poor evidence quality
            double adjusted = baseConfidence * evidenceQuality;

            log.debug("Adjusted confidence {:.3f} -> {:.3f} (quality: {:.2f})",
                    baseConfidence, adjusted, evidenceQuality);

            return adjusted;
        });
    }

    /**
     * Evaluates if a confidence chain is reliable enough for decision-making.
     *
     * @param result Propagation result to evaluate
     * @return Promise of reliability assessment
     */
    public Promise<ReliabilityAssessment> assessReliability(
            ConfidencePropagationResult result) {

        return Promise.ofBlocking(eventloop, () -> {
            boolean meetsMinimum = result.finalConfidence() >= minConfidenceThreshold;

            // Check for confidence cliff (sudden drop)
            boolean hasCliff = detectConfidenceCliff(result.confidenceChain());

            // Check for consistent degradation
            double avgDecay = calculateAverageDecay(result.confidenceChain());
            boolean excessiveDecay = avgDecay > propagationDecay * 10;

            boolean reliable = meetsMinimum && !hasCliff && !excessiveDecay;

            String reason = buildReliabilityReason(meetsMinimum, hasCliff, excessiveDecay);

            log.debug("Reliability assessment: {} ({}) - finalConfidence: {}, minThreshold: {}, hasCliff: {}, excessiveDecay: {}, avgDecay: {}", 
                reliable, reason, result.finalConfidence(), minConfidenceThreshold, hasCliff, excessiveDecay, avgDecay);

            return new ReliabilityAssessment(
                    reliable,
                    result.finalConfidence(),
                    meetsMinimum,
                    hasCliff,
                    excessiveDecay,
                    reason
            );
        });
    }

    // Helper methods

    private double calculatePropagatedConfidence(List<EventConfidence> events) {
        return switch (strategy) {
            case MULTIPLICATIVE -> {
                double product = 1.0;
                for (int i = 0; i < events.size(); i++) {
                    double decay = Math.pow(1.0 - propagationDecay, i);
                    product *= events.get(i).confidence() * decay;
                }
                yield product;
            }
            case MINIMUM -> events.stream()
                    .mapToDouble(EventConfidence::confidence)
                    .min()
                    .orElse(0.0);
            case AVERAGE -> events.stream()
                    .mapToDouble(EventConfidence::confidence)
                    .average()
                    .orElse(0.0);
        };
    }

    private List<Double> buildConfidenceChain(List<EventConfidence> events) {
        List<Double> chain = new ArrayList<>();
        double cumulative = 1.0;

        for (int i = 0; i < events.size(); i++) {
            double decay = Math.pow(1.0 - propagationDecay, i);
            double eventConfidence = events.get(i).confidence();
            cumulative *= eventConfidence * decay;
            chain.add(cumulative);
        }

        return chain;
    }

    private boolean detectConfidenceCliff(List<Double> chain) {
        if (chain.size() < 2) {
            return false;
        }

        for (int i = 1; i < chain.size(); i++) {
            double drop = chain.get(i - 1) - chain.get(i);
            double dropPercent = drop / chain.get(i - 1);
            if (dropPercent > 0.5) { // >50% drop
                return true;
            }
        }

        return false;
    }

    private double calculateAverageDecay(List<Double> chain) {
        if (chain.size() < 2) {
            return 0.0;
        }

        double totalDecay = 0.0;
        for (int i = 1; i < chain.size(); i++) {
            double decay = (chain.get(i - 1) - chain.get(i)) / chain.get(i - 1);
            totalDecay += decay;
        }

        return totalDecay / (chain.size() - 1);
    }

    private String buildReliabilityReason(boolean meetsMinimum, boolean hasCliff, boolean excessiveDecay) {
        if (!meetsMinimum) {
            return "Confidence below minimum threshold";
        }
        if (hasCliff) {
            return "Sudden confidence drop detected";
        }
        if (excessiveDecay) {
            return "Excessive confidence decay";
        }
        return "All reliability checks passed";
    }

    // Enums and value objects

    /**
     * Strategy for propagating confidence through sequences.
     */
    public enum PropagationStrategy {
        MULTIPLICATIVE,  // Multiply confidences with decay
        MINIMUM,         // Take minimum confidence
        AVERAGE          // Average all confidences
    }

    /**
     * Event with associated confidence score.
     */
    public record EventConfidence(String eventId, double confidence) {
        public EventConfidence {
            if (confidence < 0.0 || confidence > 1.0) {
                throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
            }
        }
    }

    /**
     * Result of confidence propagation.
     */
    public record ConfidencePropagationResult(
            double finalConfidence,
            boolean meetsThreshold,
            int eventCount,
            List<Double> confidenceChain,
            PropagationStrategy strategyUsed
    ) {
        public static ConfidencePropagationResult empty() {
            return new ConfidencePropagationResult(0.0, false, 0, List.of(), PropagationStrategy.MULTIPLICATIVE);
        }
    }

    /**
     * Confidence score with uncertainty bounds.
     */
    public record ConfidenceWithUncertainty(
            double confidence,
            double lowerBound,
            double upperBound,
            double margin,
            int sampleSize
    ) {}

    /**
     * Reliability assessment of a confidence chain.
     */
    public record ReliabilityAssessment(
            boolean reliable,
            double finalConfidence,
            boolean meetsMinimum,
            boolean hasConfidenceCliff,
            boolean hasExcessiveDecay,
            String reason
    ) {}
}
