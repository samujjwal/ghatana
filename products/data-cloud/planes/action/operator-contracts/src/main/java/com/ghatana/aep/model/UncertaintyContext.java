package com.ghatana.aep.model;

import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Captures AEP uncertainty dimensions for operator propagation and pattern confidence
 * @doc.layer product
 * @doc.pattern Contract
 */
public record UncertaintyContext(
        double eventDetectionConfidence,
        double attributeConfidence,
        double temporalConfidence,
        double sourceReliability,
        double patternConfidence,
        double modelConfidence,
        double retrievalConfidence,
        double inputCompleteness,
        double calibrationScore,
        Map<String, Object> evidence) {

    public UncertaintyContext {
        requireProbability(eventDetectionConfidence, "eventDetectionConfidence");
        requireProbability(attributeConfidence, "attributeConfidence");
        requireProbability(temporalConfidence, "temporalConfidence");
        requireProbability(sourceReliability, "sourceReliability");
        requireProbability(patternConfidence, "patternConfidence");
        requireProbability(modelConfidence, "modelConfidence");
        requireProbability(retrievalConfidence, "retrievalConfidence");
        requireProbability(inputCompleteness, "inputCompleteness");
        requireProbability(calibrationScore, "calibrationScore");
        evidence = Map.copyOf(evidence != null ? evidence : Map.of());
    }

    public static UncertaintyContext certain() {
        return new UncertaintyContext(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, Map.of());
    }

    private static void requireProbability(double value, String fieldName) {
        if (Double.isNaN(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(fieldName + " must be between 0.0 and 1.0");
        }
    }
}
