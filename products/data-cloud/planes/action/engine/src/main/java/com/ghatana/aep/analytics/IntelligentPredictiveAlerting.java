package com.ghatana.aep.analytics;

import java.time.Instant;
import java.util.Objects;

/**
 * Predictive alerting contract.
 *
 * @doc.type interface
 * @doc.purpose Represent predictive alerts emitted from trending resource metrics
 * @doc.layer product
 * @doc.pattern Service
 */
public interface IntelligentPredictiveAlerting {

    /**
     * Predictive alert result.
     */
    record AlertResult(
            String alertId,
            String metricName,
            String severity,
            String message,
            Instant predictedTime) {
        public AlertResult {
            Objects.requireNonNull(alertId, "alertId");
            Objects.requireNonNull(metricName, "metricName");
            Objects.requireNonNull(severity, "severity");
            Objects.requireNonNull(message, "message");
            Objects.requireNonNull(predictedTime, "predictedTime");
        }
    }
}