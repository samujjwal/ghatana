package com.ghatana.finance.ai;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * @doc.type class
 * @doc.purpose Produces structured explanations for finance fraud decisions from the extracted feature set
 * @doc.layer product
 * @doc.pattern Utils
 */
public final class FinanceFraudExplanationUtils {

    private FinanceFraudExplanationUtils() {
    }

    public static FraudDecisionExplanation explain(
        Map<String, Object> features,
        String fraudType,
        String riskLevel,
        boolean fraudulent,
        String inferenceSource
    ) {
        Objects.requireNonNull(features, "features cannot be null");
        Objects.requireNonNull(riskLevel, "riskLevel cannot be null");
        Objects.requireNonNull(inferenceSource, "inferenceSource cannot be null");

        if (!fraudulent && "LOW".equalsIgnoreCase(riskLevel)) {
            return new FraudDecisionExplanation(
                "Low fraud risk with no material anomaly indicators; scored by " + inferenceSource + ".",
                "balanced transaction signals",
                List.of()
            );
        }

        List<FraudDecisionExplanation.Factor> topFactors = Stream.of(
                factor(features, "counterparty_risk", 1.0, "elevated cross-border counterparty exposure"),
                factor(features, "merchant_risk", 1.0, "high-risk merchant profile"),
                factor(features, "payment_method_risk", 1.0, "high-risk payment rail"),
                factor(features, "location_mismatch_risk", 1.0, "location mismatch between origin and counterparty"),
                factor(features, "geolocation_risk", 1.0, "unusual geography for this transaction"),
                factor(features, "execution_channel_risk", 1.0, "high-risk execution channel"),
                factor(features, "price_deviation", 1.0, "price deviation from market baseline"),
                factor(features, "volume_anomaly", 20.0, "volume anomaly outside the normal range"),
                factor(features, "velocity_score", 15.0, "elevated transaction velocity"),
                factor(features, "amount_factor", 1.0, "transaction amount beyond the normal threshold"),
                factor(features, "time_risk", 1.0, "transaction timing outside the normal window")
            )
            .filter(Objects::nonNull)
            .sorted(Comparator.comparingDouble(FraudDecisionExplanation.Factor::contribution).reversed())
            .limit(3)
            .toList();

        if (topFactors.isEmpty()) {
            topFactors = features.entrySet().stream()
                .map(entry -> genericFactor(entry.getKey(), entry.getValue()))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(FraudDecisionExplanation.Factor::contribution).reversed())
                .limit(3)
                .toList();
        }

        String primaryReason = topFactors.isEmpty()
            ? (fraudType == null || fraudType.isBlank() ? "balanced transaction signals" : fraudType)
            : topFactors.get(0).rationale();
        String summary = buildSummary(riskLevel, fraudulent, inferenceSource, primaryReason, fraudType);

        return new FraudDecisionExplanation(summary, primaryReason, topFactors);
    }

    private static FraudDecisionExplanation.Factor factor(
        Map<String, Object> features,
        String key,
        double ceiling,
        String rationale
    ) {
        Object rawValue = features.get(key);
        double value = asDouble(rawValue);
        if (value <= 0.0) {
            return null;
        }
        double contribution = ceiling > 0.0 ? Math.min(value / ceiling, 1.0) : value;
        if (contribution <= 0.0) {
            return null;
        }
        return new FraudDecisionExplanation.Factor(key, contribution, rationale);
    }

    private static String buildSummary(
        String riskLevel,
        boolean fraudulent,
        String inferenceSource,
        String primaryReason,
        String fraudType
    ) {
        StringBuilder summary = new StringBuilder();
        summary.append(riskLevel).append(" fraud risk");
        if (fraudType != null && !fraudType.isBlank()) {
            summary.append(" (").append(fraudType).append(")");
        }
        summary.append(" driven by ").append(primaryReason);
        summary.append(" using ").append(inferenceSource).append(" scoring.");
        return summary.toString();
    }

    private static double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Double.parseDouble(stringValue);
            } catch (NumberFormatException ignored) {
                return 0.0;
            }
        }
        return 0.0;
    }

    private static FraudDecisionExplanation.Factor genericFactor(String key, Object value) {
        double numericValue = asDouble(value);
        if (numericValue <= 0.0) {
            return null;
        }
        double contribution = Math.min(numericValue, 1.0);
        return new FraudDecisionExplanation.Factor(key, contribution, humanizeKey(key));
    }

    private static String humanizeKey(String key) {
        return key.replace('_', ' ');
    }
}
