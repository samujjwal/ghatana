package com.ghatana.plugin.risk.impl;

import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginMetadata;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.plugin.PluginType;
import com.ghatana.plugin.observability.PluginObservability;
import com.ghatana.plugin.risk.RiskManagementPlugin;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Standard in-memory implementation of {@link RiskManagementPlugin}.
 *
 * <p>Factor evaluation is model-driven: callers register a {@link FactorEvaluator}
 * per {@link RiskModelId}. Built-in evaluators are provided for the standard
 * generic model IDs ({@code VOLATILITY}, {@code COUNTERPARTY}, {@code OPERATIONAL},
 * {@code ANOMALY}, {@code COMPLIANCE}). Products register their own evaluators
 * for domain-specific models without touching this class.</p>
 *
 * @doc.type class
 * @doc.purpose Standard in-memory risk management implementation with pluggable model evaluators
 * @doc.layer platform
 * @doc.pattern Plugin Implementation
 * @since 1.2.0
 */
public class StandardRiskManagementPlugin extends PluginObservability implements RiskManagementPlugin {

    private static final Logger LOG = LoggerFactory.getLogger(StandardRiskManagementPlugin.class);

    /** Pluggable factor evaluator for a risk model. */
    @FunctionalInterface
    public interface FactorEvaluator {
        /**
         * Evaluates a single factor key/value pair.
         *
         * @param key   the factor name
         * @param value the factor value (Number, Boolean, or String)
         * @return a normalized risk contribution in the range {@code [0.0, 1.0]}
         */
        double evaluate(String key, Object value);
    }

    private final Map<String, RiskScore> scores = new ConcurrentHashMap<>();
    private final Map<String, Map<String, BigDecimal>> limits = new ConcurrentHashMap<>();
    private final Map<String, List<RiskAlert>> alerts = new ConcurrentHashMap<>();
    private final Map<String, List<RiskScore>> scoreHistory = new ConcurrentHashMap<>();
    private final Map<String, FactorEvaluator> evaluators = new ConcurrentHashMap<>();

    private static final PluginMetadata METADATA = PluginMetadata.builder()
        .id("risk-management-plugin")
        .name("Risk Management Plugin")
        .version("1.2.0")
        .description("Domain-agnostic risk management framework with pluggable model evaluators")
        .type(PluginType.CUSTOM)
        .author("Ghatana")
        .license("Apache-2.0")
        .capability("risk:calculate", "risk:set-limits", "risk:get-alerts", "risk:generate-report")
        .build();

    private PluginContext context;
    private PluginState state = PluginState.UNLOADED;

    /** Constructs the plugin and registers built-in generic evaluators. */
    public StandardRiskManagementPlugin() {
        super("risk-management-plugin");
        registerBuiltinEvaluators();
    }

    /**
     * Registers a factor evaluator for a risk model.
     *
     * <p>Must be called before {@link #start()} to be effective for production use.
     * May be called any time during testing.</p>
     *
     * @param modelId   the risk model identifier
     * @param evaluator the factor evaluator
     */
    public void registerEvaluator(RiskModelId modelId, FactorEvaluator evaluator) {
        evaluators.put(modelId.getId(), Objects.requireNonNull(evaluator, "evaluator cannot be null"));
        LOG.debug("Registered evaluator for risk model: {}", modelId.getId());
    }

    // ── Plugin lifecycle ──────────────────────────────────────────────────────

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
        LOG.info("RiskManagementPlugin initialized with {} model evaluators", evaluators.size());
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

    // ── Risk operations ───────────────────────────────────────────────────────

    @Override
    public Promise<RiskScore> calculateRisk(String entityId, RiskModelId modelId, Map<String, Object> factors) {
        FactorEvaluator evaluator = evaluators.get(modelId.getId());
        if (evaluator == null) {
            return Promise.ofException(new IllegalArgumentException(
                "No evaluator registered for risk model: " + modelId.getId()));
        }

        Map<String, Double> componentScores = new LinkedHashMap<>();
        double totalScore = 0.0;
        int componentCount = 0;

        for (Map.Entry<String, Object> entry : factors.entrySet()) {
            double score = evaluator.evaluate(entry.getKey(), entry.getValue());
            componentScores.put(entry.getKey(), score);
            totalScore += score;
            componentCount++;
        }

        double normalizedScore = componentCount > 0 ? totalScore / componentCount : 0.0;
        RiskScore.RiskLevel level = determineRiskLevel(normalizedScore);

        RiskScore riskScore = new RiskScore(entityId, modelId, normalizedScore, level,
                Collections.unmodifiableMap(componentScores), Instant.now());

        scores.put(entityId + ":" + modelId.getId(), riskScore);
        scoreHistory.computeIfAbsent(entityId, k -> new ArrayList<>()).add(riskScore);

        checkLimits(entityId, modelId, riskScore, factors);

        LOG.info("Calculated {} risk for {}: score={}, level={}", modelId, entityId, normalizedScore, level);
        return Promise.of(riskScore);
    }

    @Override
    public Promise<Void> setRiskLimits(String entityId, Map<String, BigDecimal> entityLimits) {
        limits.put(entityId, Map.copyOf(entityLimits));
        LOG.info("Set {} risk limits for {}", entityLimits.size(), entityId);
        return Promise.complete();
    }

    @Override
    public Promise<List<RiskAlert>> getActiveAlerts(String entityId) {
        return Promise.of(new ArrayList<>(alerts.getOrDefault(entityId, Collections.emptyList())));
    }

    @Override
    public Promise<RiskReport> generateReport(String entityId, TimeRange range) {
        List<RiskScore> relevantScores = scoreHistory.getOrDefault(entityId, Collections.emptyList()).stream()
            .filter(s -> !s.calculatedAt().isBefore(range.start()) && !s.calculatedAt().isAfter(range.end()))
            .collect(Collectors.toList());

        List<RiskAlert> relevantAlerts = alerts.getOrDefault(entityId, Collections.emptyList()).stream()
            .filter(a -> !a.triggeredAt().isBefore(range.start()) && !a.triggeredAt().isAfter(range.end()))
            .collect(Collectors.toList());

        Map<String, Object> summary = buildSummary(relevantScores, relevantAlerts);
        return Promise.of(new RiskReport(entityId, range, relevantScores, relevantAlerts, summary, Instant.now()));
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private void registerBuiltinEvaluators() {
        evaluators.put(RiskModelId.VOLATILITY.getId(), this::evaluateVolatilityFactor);
        evaluators.put(RiskModelId.COUNTERPARTY.getId(), this::evaluateCounterpartyFactor);
        evaluators.put(RiskModelId.OPERATIONAL.getId(), this::evaluateOperationalFactor);
        evaluators.put(RiskModelId.ANOMALY.getId(), this::evaluateAnomalyFactor);
        evaluators.put(RiskModelId.COMPLIANCE.getId(), this::evaluateComplianceFactor);
    }

    private double evaluateVolatilityFactor(String key, Object value) {
        return switch (key) {
            case "variance"        -> toDouble(value) * 0.3;
            case "exposure_size"   -> Math.min(toDouble(value) / 1_000_000, 1.0);
            case "concentration"   -> toDouble(value);
            case "liquidity"       -> 1.0 - toDouble(value);
            default                -> 0.5;
        };
    }

    private double evaluateCounterpartyFactor(String key, Object value) {
        return switch (key) {
            case "trust_score"    -> 1.0 - (toDouble(value) / 850);
            case "obligation_ratio" -> toDouble(value);
            case "fulfillment_history" -> 1.0 - toDouble(value);
            default                -> 0.5;
        };
    }

    private double evaluateOperationalFactor(String key, Object value) {
        return switch (key) {
            case "system_downtime" -> toDouble(value) / 100;
            case "error_rate"      -> toDouble(value);
            case "staff_turnover"  -> toDouble(value) / 100;
            default                -> 0.5;
        };
    }

    private double evaluateAnomalyFactor(String key, Object value) {
        return switch (key) {
            case "velocity"        -> Math.min(toDouble(value) / 100, 1.0);
            case "anomaly_score"   -> toDouble(value);
            case "geographic_risk" -> toDouble(value);
            default                -> 0.5;
        };
    }

    private double evaluateComplianceFactor(String key, Object value) {
        return switch (key) {
            case "violation_count"  -> Math.min(toDouble(value) / 10, 1.0);
            case "audit_findings"   -> toDouble(value) / 5;
            case "training_overdue" -> toDouble(value) / 100;
            default                 -> 0.5;
        };
    }

    private double toDouble(Object value) {
        if (value instanceof Number num) return num.doubleValue();
        if (value instanceof Boolean bool) return bool ? 1.0 : 0.0;
        if (value instanceof String str) {
            try { return Double.parseDouble(str); } catch (NumberFormatException ignored) {}
        }
        return 0.0;
    }

    private RiskScore.RiskLevel determineRiskLevel(double score) {
        if (score >= 0.8) return RiskScore.RiskLevel.CRITICAL;
        if (score >= 0.6) return RiskScore.RiskLevel.HIGH;
        if (score >= 0.3) return RiskScore.RiskLevel.MEDIUM;
        return RiskScore.RiskLevel.LOW;
    }

    private void checkLimits(String entityId, RiskModelId modelId, RiskScore score, Map<String, Object> factors) {
        Map<String, BigDecimal> entityLimits = limits.get(entityId);
        if (entityLimits == null || entityLimits.isEmpty()) return;

        List<RiskAlert> newAlerts = new ArrayList<>();

        for (Map.Entry<String, Object> fe : factors.entrySet()) {
            if (!(fe.getValue() instanceof Number numVal)) continue;
            BigDecimal limitValue = entityLimits.get(fe.getKey());
            if (limitValue != null && BigDecimal.valueOf(numVal.doubleValue()).compareTo(limitValue) > 0) {
                newAlerts.add(new RiskAlert(
                    UUID.randomUUID().toString(),
                    entityId,
                    "LIMIT_BREACH",
                    "Factor '" + fe.getKey() + "' exceeds limit " + limitValue + " for model " + modelId.getId(),
                    0.85,
                    Instant.now()
                ));
            }
        }

        if (score.level() == RiskScore.RiskLevel.HIGH || score.level() == RiskScore.RiskLevel.CRITICAL) {
            newAlerts.add(new RiskAlert(
                UUID.randomUUID().toString(),
                entityId,
                "HIGH_RISK_SCORE",
                modelId.getId() + " risk level is " + score.level() + " for entity " + entityId
                    + " (score=" + String.format("%.2f", score.score()) + ")",
                score.level() == RiskScore.RiskLevel.CRITICAL ? 1.0 : 0.75,
                Instant.now()
            ));
        }

        if (!newAlerts.isEmpty()) {
            alerts.computeIfAbsent(entityId, k -> new ArrayList<>()).addAll(newAlerts);
            LOG.warn("Generated {} risk alert(s) for entity {} model {}", newAlerts.size(), entityId, modelId);
        }
    }

    private Map<String, Object> buildSummary(List<RiskScore> riskScores, List<RiskAlert> riskAlerts) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("averageRiskScore", riskScores.isEmpty() ? 0.0 : riskScores.stream().mapToDouble(RiskScore::score).average().orElse(0.0));
        summary.put("criticalRiskCount", riskScores.stream()
            .filter(s -> s.level() == RiskScore.RiskLevel.CRITICAL).count());
        summary.put("highRiskCount", riskScores.stream()
            .filter(s -> s.level() == RiskScore.RiskLevel.HIGH).count());
        summary.put("activeAlertCount", riskAlerts.size());
        return Collections.unmodifiableMap(summary);
    }

    @Override
    public String toString() {
        return "StandardRiskManagementPlugin{scores=" + scores.size() +
               ", limits=" + limits.size() + ", alerts=" + alerts.size() + "}";
    }
}
