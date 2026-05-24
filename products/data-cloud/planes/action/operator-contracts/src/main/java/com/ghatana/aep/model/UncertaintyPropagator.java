package com.ghatana.aep.model;

import com.ghatana.aep.operator.contract.OperatorKind;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic uncertainty propagation rules for AEP operators.
 *
 * @doc.type class
 * @doc.purpose Applies standard and agent uncertainty propagation semantics for operator results
 * @doc.layer product
 * @doc.pattern Policy
 */
public final class UncertaintyPropagator {

    private UncertaintyPropagator() {}

    public static UncertaintyContext propagate(
            OperatorKind operatorKind,
            List<UncertaintyContext> inputs,
            UncertaintyAdjustment adjustment) {
        Objects.requireNonNull(operatorKind, "operatorKind");
        List<UncertaintyContext> contexts = List.copyOf(inputs != null ? inputs : List.of());
        if (contexts.isEmpty()) {
            return UncertaintyContext.certain();
        }
        UncertaintyAdjustment effectiveAdjustment = adjustment == null ? UncertaintyAdjustment.none() : adjustment;
        double penalty = effectiveAdjustment.multiplier();

        return switch (operatorKind) {
            case AND -> aggregate(contexts, minPattern(contexts) * averagePattern(contexts) * penalty, "AND");
            case OR -> aggregate(contexts, maxPattern(contexts) * penalty, "OR");
            case SEQ -> aggregate(contexts, minPattern(contexts) * averageTemporal(contexts) * penalty, "SEQ");
            case NOT, ABSENCE -> aggregate(contexts, averageSource(contexts) * averageTemporal(contexts) * penalty, "ABSENCE");
            case WITHIN, WINDOW -> aggregate(contexts, averagePattern(contexts) * averageTemporal(contexts) * penalty, operatorKind.name());
            case TIMES, REPEAT -> aggregate(contexts, averagePattern(contexts) * averageEventDetection(contexts) * penalty, operatorKind.name());
            case AGENT_PREDICATE, AGENT_ENRICH, AGENT_EXTRACT, AGENT_PATTERN_SYNTHESIS,
                 AGENT_EXPLANATION, AGENT_REVIEW, AGENT_ACTION, AGENT_REFLECTION ->
                aggregate(contexts, averageAgentConfidence(contexts) * penalty, operatorKind.name());
            default -> aggregate(contexts, averagePattern(contexts) * penalty, operatorKind.name());
        };
    }

    private static UncertaintyContext aggregate(
            List<UncertaintyContext> contexts,
            double patternConfidence,
            String rule) {
        return new UncertaintyContext(
            average(contexts, UncertaintyContext::eventDetectionConfidence),
            average(contexts, UncertaintyContext::attributeConfidence),
            average(contexts, UncertaintyContext::temporalConfidence),
            average(contexts, UncertaintyContext::sourceReliability),
            clamp(patternConfidence),
            average(contexts, UncertaintyContext::modelConfidence),
            average(contexts, UncertaintyContext::retrievalConfidence),
            average(contexts, UncertaintyContext::inputCompleteness),
            average(contexts, UncertaintyContext::calibrationScore),
            Map.of("propagationRule", rule));
    }

    private static double averageAgentConfidence(List<UncertaintyContext> contexts) {
        return average(contexts, context -> min(
            context.modelConfidence(),
            context.retrievalConfidence(),
            context.inputCompleteness(),
            context.calibrationScore()));
    }

    private static double averagePattern(List<UncertaintyContext> contexts) {
        return average(contexts, UncertaintyContext::patternConfidence);
    }

    private static double averageTemporal(List<UncertaintyContext> contexts) {
        return average(contexts, UncertaintyContext::temporalConfidence);
    }

    private static double averageSource(List<UncertaintyContext> contexts) {
        return average(contexts, UncertaintyContext::sourceReliability);
    }

    private static double averageEventDetection(List<UncertaintyContext> contexts) {
        return average(contexts, UncertaintyContext::eventDetectionConfidence);
    }

    private static double minPattern(List<UncertaintyContext> contexts) {
        return contexts.stream().mapToDouble(UncertaintyContext::patternConfidence).min().orElse(1.0);
    }

    private static double maxPattern(List<UncertaintyContext> contexts) {
        return contexts.stream().mapToDouble(UncertaintyContext::patternConfidence).max().orElse(1.0);
    }

    private static double average(List<UncertaintyContext> contexts, ConfidenceSelector selector) {
        return clamp(contexts.stream().mapToDouble(selector::select).average().orElse(1.0));
    }

    private static double min(double first, double... rest) {
        double value = first;
        for (double candidate : rest) {
            value = Math.min(value, candidate);
        }
        return value;
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    @FunctionalInterface
    private interface ConfidenceSelector {
        double select(UncertaintyContext context);
    }
}
