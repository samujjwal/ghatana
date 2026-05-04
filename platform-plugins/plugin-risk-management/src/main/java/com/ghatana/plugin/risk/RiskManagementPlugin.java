package com.ghatana.plugin.risk;

import com.ghatana.platform.plugin.Plugin;
import io.activej.promise.Promise;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Risk Management Plugin — generic, model-driven risk framework.
 *
 * <p>Supports any risk domain through registered {@link RiskModelId} types.
 * Products supply their own evaluator implementations; the kernel plugin
 * itself is domain-agnostic.</p>
 *
 * @doc.type interface
 * @doc.purpose Generic risk management plugin interface — model-driven, domain-agnostic
 * @doc.layer platform
 * @doc.pattern Plugin
 * @since 1.2.0
 */
public interface RiskManagementPlugin extends Plugin {

    /**
     * Calculates risk for an entity using the specified risk model.
     *
     * @param entityId the entity identifier
     * @param modelId  the risk model identifier (domain-supplied)
     * @param factors  the input risk factors as key-value pairs
     * @return Promise containing the risk score
     */
    Promise<RiskScore> calculateRisk(String entityId, RiskModelId modelId, Map<String, Object> factors);

    /**
     * Sets risk limits for an entity.
     *
     * @param entityId the entity identifier
     * @param limits   named limit values (e.g. {@code "max-exposure" -> 1_000_000})
     * @return Promise completing when limits are set
     */
    Promise<Void> setRiskLimits(String entityId, Map<String, BigDecimal> limits);

    /**
     * Gets active risk alerts for an entity.
     *
     * @param entityId the entity identifier
     * @return Promise containing active alerts
     */
    Promise<List<RiskAlert>> getActiveAlerts(String entityId);

    /**
     * Generates a risk report for a time range.
     *
     * @param entityId the entity identifier
     * @param range    the time range
     * @return Promise containing the risk report
     */
    Promise<RiskReport> generateReport(String entityId, TimeRange range);

    /**
     * Typed identifier for a risk model.
     *
     * <p>Products register evaluators under a {@code RiskModelId};
     * the kernel uses the id as a lookup key, not a hardcoded enum.</p>
     *
     * <p>Well-known generic IDs are provided as constants but any string is valid.</p>
     */
    final class RiskModelId {
        /** Generic volatility and exposure risk. */
        public static final RiskModelId VOLATILITY  = new RiskModelId("VOLATILITY");
        /** Counterparty and obligation risk. */
        public static final RiskModelId COUNTERPARTY = new RiskModelId("COUNTERPARTY");
        /** Operational-process and infrastructure risk. */
        public static final RiskModelId OPERATIONAL = new RiskModelId("OPERATIONAL");
        /** Anomaly and abuse risk. */
        public static final RiskModelId ANOMALY     = new RiskModelId("ANOMALY");
        /** Regulatory-compliance risk. */
        public static final RiskModelId COMPLIANCE  = new RiskModelId("COMPLIANCE");

        private final String id;

        public RiskModelId(String id) {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("RiskModelId must not be blank");
            this.id = id.toUpperCase(java.util.Locale.ROOT);
        }

        public String getId() { return id; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RiskModelId that)) return false;
            return id.equals(that.id);
        }

        @Override
        public int hashCode() { return Objects.hash(id); }

        @Override
        public String toString() { return id; }
    }

    /**
     * Risk score produced by a model evaluation.
     */
    record RiskScore(
        String entityId,
        RiskModelId modelId,
        double score,
        RiskLevel level,
        Map<String, Double> componentScores,
        Instant calculatedAt
    ) {
        /** Qualitative risk level derived from a normalized score. */
        public enum RiskLevel {
            LOW, MEDIUM, HIGH, CRITICAL
        }
    }

    /**
     * Risk alert triggered when a score or limit is breached.
     */
    record RiskAlert(
        String alertId,
        String entityId,
        String alertType,
        String message,
        double severity,
        Instant triggeredAt
    ) {}

    /**
     * Risk report covering a time range.
     */
    record RiskReport(
        String entityId,
        TimeRange range,
        List<RiskScore> scores,
        List<RiskAlert> alerts,
        Map<String, Object> summary,
        Instant generatedAt
    ) {}

    /**
     * Inclusive time range for report and history queries.
     */
    record TimeRange(Instant start, Instant end) {}
}

