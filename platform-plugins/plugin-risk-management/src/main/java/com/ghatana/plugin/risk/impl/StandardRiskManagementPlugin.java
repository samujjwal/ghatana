package com.ghatana.plugin.risk.impl;

import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginMetadata;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.plugin.PluginType;
import com.ghatana.plugin.risk.RiskManagementPlugin;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Standard implementation of RiskManagementPlugin.
 *
 * <p>This implementation provides:</p>
 * <ul>
 *   <li>Risk calculation for multiple risk types</li>
 *   <li>Risk limit enforcement</li>
 *   <li>Risk alerting</li>
 *   <li>Portfolio and position risk tracking</li>
 * </ul>
 *
 * <p>Supports trading risk (Finance), clinical risk (PHR), and general use cases.</p>
 *
 * @doc.type class
 * @doc.purpose Standard risk management implementation
 * @doc.layer platform
 * @doc.pattern Plugin Implementation
 * @since 1.0.0
 */
public class StandardRiskManagementPlugin implements RiskManagementPlugin {

    private static final Logger LOG = LoggerFactory.getLogger(StandardRiskManagementPlugin.class);

    private final Map<String, RiskScore> scores = new ConcurrentHashMap<>();
    private final Map<String, RiskLimits> limits = new ConcurrentHashMap<>();
    private final Map<String, List<RiskAlert>> alerts = new ConcurrentHashMap<>();
    private final Map<String, List<RiskScore>> scoreHistory = new ConcurrentHashMap<>();

    private static final PluginMetadata METADATA = PluginMetadata.builder()
        .id("risk-management-plugin")
        .name("Risk Management Plugin")
        .version("1.0.0")
        .description("Cross-product risk management framework")
        .type(PluginType.CUSTOM)
        .author("Ghatana")
        .license("Apache-2.0")
        .capability("risk:calculate", "risk:set-limits", "risk:get-alerts", "risk:generate-report")
        .build();

    private PluginContext context;
    private PluginState state = PluginState.UNLOADED;

    @Override
    public PluginMetadata metadata() {
        return METADATA;
    }

    @Override
    public PluginState getState() {
        return state;
    }

    @Override
    public Promise<Void> initialize(PluginContext context) {
        this.context = context;
        this.state = PluginState.INITIALIZED;
        LOG.info("RiskManagementPlugin initialized");
        return Promise.complete();
    }

    @Override
    public Promise<Void> start() {
        this.state = PluginState.STARTED;
        LOG.info("RiskManagementPlugin started");
        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        this.state = PluginState.STOPPED;
        LOG.info("RiskManagementPlugin stopped");
        return Promise.complete();
    }

    @Override
    public Promise<Void> shutdown() {
        scores.clear();
        limits.clear();
        alerts.clear();
        scoreHistory.clear();
        this.state = PluginState.UNLOADED;
        LOG.info("RiskManagementPlugin shutdown");
        return Promise.complete();
    }

    @Override
    public Promise<RiskScore> calculateRisk(String entityId, RiskType type, Map<String, Object> factors) {
        Map<String, Double> componentScores = new HashMap<>();

        // Calculate risk based on type and factors
        double totalScore = 0.0;
        int componentCount = 0;

        for (Map.Entry<String, Object> entry : factors.entrySet()) {
            double score = evaluateFactor(entry.getKey(), entry.getValue(), type);
            componentScores.put(entry.getKey(), score);
            totalScore += score;
            componentCount++;
        }

        // Normalize to 0-1 scale
        double normalizedScore = componentCount > 0 ? totalScore / componentCount : 0.0;

        RiskScore.RiskLevel level = determineRiskLevel(normalizedScore);

        RiskScore riskScore = new RiskScore(
            entityId,
            type,
            normalizedScore,
            level,
            componentScores,
            Instant.now()
        );

        scores.put(entityId + ":" + type, riskScore);
        scoreHistory.computeIfAbsent(entityId, k -> new ArrayList<>()).add(riskScore);

        // Check against limits
        checkLimits(entityId, type, riskScore);

        LOG.info("Calculated {} risk for {}: score={:.2f}, level={}",
            type, entityId, normalizedScore, level);

        return Promise.of(riskScore);
    }

    @Override
    public Promise<Void> setRiskLimits(String entityId, RiskLimits riskLimits) {
        limits.put(entityId, riskLimits);
        LOG.info("Set risk limits for {}", entityId);
        return Promise.complete();
    }

    @Override
    public Promise<List<RiskAlert>> getActiveAlerts(String entityId) {
        List<RiskAlert> active = new ArrayList<>(alerts.getOrDefault(entityId, Collections.emptyList()));
        return Promise.of(active);
    }

    @Override
    public Promise<RiskReport> generateReport(String entityId, TimeRange range) {
        List<RiskScore> relevantScores = scoreHistory.getOrDefault(entityId, Collections.emptyList()).stream()
            .filter(s -> s.calculatedAt().isAfter(range.start()) && s.calculatedAt().isBefore(range.end()))
            .collect(Collectors.toList());

        List<RiskAlert> relevantAlerts = alerts.getOrDefault(entityId, Collections.emptyList()).stream()
            .filter(a -> a.triggeredAt().isAfter(range.start()) && a.triggeredAt().isBefore(range.end()))
            .collect(Collectors.toList());

        Map<String, Object> summary = buildSummary(relevantScores, relevantAlerts);

        RiskReport report = new RiskReport(
            entityId,
            range,
            relevantScores,
            relevantAlerts,
            summary,
            Instant.now()
        );

        return Promise.of(report);
    }

    private double evaluateFactor(String key, Object value, RiskType type) {
        // Type-specific factor evaluation
        return switch (type) {
            case MARKET -> evaluateMarketFactor(key, value);
            case CREDIT -> evaluateCreditFactor(key, value);
            case OPERATIONAL -> evaluateOperationalFactor(key, value);
            case CLINICAL -> evaluateClinicalFactor(key, value);
            case FRAUD -> evaluateFraudFactor(key, value);
            case COMPLIANCE -> evaluateComplianceFactor(key, value);
        };
    }

    private double evaluateMarketFactor(String key, Object value) {
        return switch (key) {
            case "volatility" -> toDouble(value) * 0.3;
            case "position_size" -> Math.min(toDouble(value) / 1000000, 1.0); // Normalize to 1M
            case "concentration" -> toDouble(value);
            case "liquidity" -> 1.0 - toDouble(value); // Lower liquidity = higher risk
            default -> 0.5;
        };
    }

    private double evaluateCreditFactor(String key, Object value) {
        return switch (key) {
            case "credit_score" -> 1.0 - (toDouble(value) / 850); // Inverse of credit score
            case "debt_ratio" -> toDouble(value);
            case "payment_history" -> 1.0 - toDouble(value); // Lower history = higher risk
            default -> 0.5;
        };
    }

    private double evaluateOperationalFactor(String key, Object value) {
        return switch (key) {
            case "system_downtime" -> toDouble(value) / 100; // Percentage
            case "error_rate" -> toDouble(value);
            case "staff_turnover" -> toDouble(value) / 100; // Percentage
            default -> 0.5;
        };
    }

    private double evaluateClinicalFactor(String key, Object value) {
        return switch (key) {
            case "patient_age" -> toDouble(value) / 100; // Normalize
            case "severity_score" -> toDouble(value) / 10; // Assume 10 max
            case "comorbidity_count" -> Math.min(toDouble(value) / 5, 1.0);
            case "medication_interactions" -> toDouble(value);
            default -> 0.5;
        };
    }

    private double evaluateFraudFactor(String key, Object value) {
        return switch (key) {
            case "velocity" -> Math.min(toDouble(value) / 100, 1.0);
            case "anomaly_score" -> toDouble(value);
            case "geographic_risk" -> toDouble(value);
            default -> 0.5;
        };
    }

    private double evaluateComplianceFactor(String key, Object value) {
        return switch (key) {
            case "violation_count" -> Math.min(toDouble(value) / 10, 1.0);
            case "audit_findings" -> toDouble(value) / 5;
            case "training_overdue" -> toDouble(value) / 100; // Percentage
            default -> 0.5;
        };
    }

    private double toDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0.0;
    }

    private RiskScore.RiskLevel determineRiskLevel(double score) {
        if (score >= 0.8) return RiskScore.RiskLevel.CRITICAL;
        if (score >= 0.6) return RiskScore.RiskLevel.HIGH;
        if (score >= 0.3) return RiskScore.RiskLevel.MEDIUM;
        return RiskScore.RiskLevel.LOW;
    }

    private void checkLimits(String entityId, RiskType type, RiskScore score) {
        RiskLimits entityLimits = limits.get(entityId);
        if (entityLimits == null) return;

        // Check various limits based on risk type
        boolean isHighOrCritical = score.level() == RiskScore.RiskLevel.HIGH
                || score.level() == RiskScore.RiskLevel.CRITICAL;
        if (type == RiskType.MARKET && isHighOrCritical) {
            RiskAlert alert = new RiskAlert(
                UUID.randomUUID().toString(),
                entityId,
                "HIGH_MARKET_RISK",
                "Market risk exceeds threshold: " + String.format("%.2f", score.score()),
                score.score(),
                Instant.now()
            );
            alerts.computeIfAbsent(entityId, k -> new ArrayList<>()).add(alert);
            LOG.warn("Risk limit exceeded for {}: {}", entityId, alert.message());
        }
    }

    private Map<String, Object> buildSummary(List<RiskScore> scores, List<RiskAlert> alerts) {
        Map<String, Object> summary = new HashMap<>();

        double avgScore = scores.stream().mapToDouble(RiskScore::score).average().orElse(0.0);
        long criticalCount = scores.stream().filter(s -> s.level() == RiskScore.RiskLevel.CRITICAL).count();
        long highCount = scores.stream().filter(s -> s.level() == RiskScore.RiskLevel.HIGH).count();

        summary.put("averageRiskScore", avgScore);
        summary.put("criticalRiskCount", criticalCount);
        summary.put("highRiskCount", highCount);
        summary.put("activeAlertCount", alerts.size());

        return summary;
    }

    @Override
    public String toString() {
        return "StandardRiskManagementPlugin{scores=" + scores.size() +
               ", limits=" + limits.size() + ", alerts=" + alerts.size() + "}";
    }
}
