package com.ghatana.plugin.risk;

import com.ghatana.platform.plugin.Plugin;
import io.activej.promise.Promise;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Risk Management Plugin - Generic risk calculation framework.
 *
 * <p>Supports multiple risk types:</p>
 * <ul>
 *   <li>Trading risk (Finance)</li>
 *   <li>Clinical risk (PHR)</li>
 *   <li>Credit risk (general)</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Risk management plugin interface
 * @doc.layer platform
 * @doc.pattern Plugin
 * @since 1.0.0
 */
public interface RiskManagementPlugin extends Plugin {

    /**
     * Calculates risk for an entity.
     *
     * @param entityId the entity identifier
     * @param type the risk type
     * @param factors the risk factors
     * @return Promise containing the risk score
     */
    Promise<RiskScore> calculateRisk(String entityId, RiskType type, Map<String, Object> factors);

    /**
     * Sets risk limits for an entity.
     *
     * @param entityId the entity identifier
     * @param limits the risk limits
     * @return Promise completing when limits are set
     */
    Promise<Void> setRiskLimits(String entityId, RiskLimits limits);

    /**
     * Gets active risk alerts for an entity.
     *
     * @param entityId the entity identifier
     * @return Promise containing active alerts
     */
    Promise<List<RiskAlert>> getActiveAlerts(String entityId);

    /**
     * Generates a risk report.
     *
     * @param entityId the entity identifier
     * @param range the time range
     * @return Promise containing the risk report
     */
    Promise<RiskReport> generateReport(String entityId, TimeRange range);

    /**
     * Risk types.
     */
    enum RiskType {
        MARKET,
        CREDIT,
        OPERATIONAL,
        CLINICAL,
        FRAUD,
        COMPLIANCE
    }

    /**
     * Risk score.
     */
    record RiskScore(
        String entityId,
        RiskType type,
        double score,
        RiskLevel level,
        Map<String, Double> componentScores,
        Instant calculatedAt
    ) {
        public enum RiskLevel {
            LOW, MEDIUM, HIGH, CRITICAL
        }
    }

    /**
     * Risk limits.
     */
    record RiskLimits(
        BigDecimal maxPositionNotional,
        BigDecimal maxPortfolioNotional,
        BigDecimal maxPortfolioVaR,
        BigDecimal maxConcentration,
        BigDecimal maxPositionLoss
    ) {}

    /**
     * Risk alert.
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
     * Risk report.
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
     * Time range.
     */
    record TimeRange(Instant start, Instant end) {}
}
