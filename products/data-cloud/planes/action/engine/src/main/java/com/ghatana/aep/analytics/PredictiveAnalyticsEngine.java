package com.ghatana.aep.analytics;

import java.time.Instant;
import java.util.Objects;

/**
 * Predictive analytics contract for simple trend-based forecasting summaries.
 *
 * @doc.type interface
 * @doc.purpose Predict future behavior confidence from observed metric trends
 * @doc.layer product
 * @doc.pattern Service
 */
public interface PredictiveAnalyticsEngine {

    /**
     * Prediction summary for a metric or event family.
     */
    record PredictionSummary(
            String eventType,
            Instant generatedAt,
            double predictedValue,
            double confidence,
            int observations,
            long horizonSeconds) {
        public PredictionSummary {
            Objects.requireNonNull(eventType, "eventType");
            Objects.requireNonNull(generatedAt, "generatedAt");
        }
    }
}