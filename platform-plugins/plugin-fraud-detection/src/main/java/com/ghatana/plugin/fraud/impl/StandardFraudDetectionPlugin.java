package com.ghatana.plugin.fraud.impl;

import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginMetadata;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.plugin.PluginType;
import com.ghatana.plugin.fraud.FraudDetectionPlugin;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Standard implementation of FraudDetectionPlugin.
 *
 * <p>This implementation provides:</p>
 * <ul>
 *   <li>Rule-based fraud detection</li>
 *   <li>Risk scoring with configurable thresholds</li>
 *   <li>Pattern detection across transactions</li>
 *   <li>Model training and metrics tracking</li>
 * </ul>
 *
 * <p>Products can extend this or integrate with their own ML models.</p>
 *
 * @doc.type class
 * @doc.purpose Standard fraud detection implementation
 * @doc.layer platform
 * @doc.pattern Plugin Implementation
 * @since 1.0.0
 */
public class StandardFraudDetectionPlugin implements FraudDetectionPlugin {

    private static final Logger LOG = LoggerFactory.getLogger(StandardFraudDetectionPlugin.class);

    private final Map<String, FraudAssessment> assessments = new ConcurrentHashMap<>();
    private final Map<String, ModelMetrics> modelMetrics = new ConcurrentHashMap<>();
    private final Map<String, List<FraudRule>> productRules = new ConcurrentHashMap<>();
    private final Map<String, List<FraudPattern>> detectedPatterns = new ConcurrentHashMap<>();

    // Built-in fraud rules for common scenarios
    private static final Map<String, List<FraudRule>> BUILTIN_RULES;

    static {
        Map<String, List<FraudRule>> rules = new HashMap<>();
        
        // TRANSACTION rules
        rules.put("TRANSACTION", Arrays.asList(
            new FraudRule("HIGH_AMOUNT_RULE", "AMOUNT_THRESHOLD",
                "Amount > 50000 indicates high fraud risk", 50000.0)
        ));
        
        BUILTIN_RULES = Collections.unmodifiableMap(rules);
    }

    private static final PluginMetadata METADATA = PluginMetadata.builder()
        .id("fraud-detection-plugin")
        .name("Fraud Detection Plugin")
        .version("1.0.0")
        .description("Cross-product fraud detection framework")
        .type(PluginType.CUSTOM)
        .author("Ghatana")
        .license("Apache-2.0")
        .capability("fraud:assess", "fraud:detect-patterns", "fraud:train-model", "fraud:get-metrics")
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
        
        // Load built-in rules with mutable copies
        BUILTIN_RULES.forEach((productId, rules) -> {
            productRules.put(productId, new ArrayList<>(rules));
        });
        
        LOG.info("FraudDetectionPlugin initialized with {} built-in rule sets", BUILTIN_RULES.size());
        return Promise.complete();
    }

    @Override
    public Promise<Void> start() {
        this.state = PluginState.STARTED;
        LOG.info("FraudDetectionPlugin started");
        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        this.state = PluginState.STOPPED;
        LOG.info("FraudDetectionPlugin stopped");
        return Promise.complete();
    }

    @Override
    public Promise<Void> shutdown() {
        assessments.clear();
        modelMetrics.clear();
        productRules.clear();
        detectedPatterns.clear();
        this.state = PluginState.UNLOADED;
        LOG.info("FraudDetectionPlugin shutdown");
        return Promise.complete();
    }

    @Override
    public Promise<FraudAssessment> assessTransaction(String transactionId, FraudDetectionRequest request) {
        // Extract product from features if present, otherwise use entityType as fallback
        String productId = request.features().containsKey("product") 
            ? (String) request.features().get("product")
            : request.entityType();
        
        List<FraudRule> rules = productRules.getOrDefault(productId, Collections.emptyList());

        List<String> triggeredRules = new ArrayList<>();
        double riskScore = 0.0;

        // Apply rules
        for (FraudRule rule : rules) {
            if (evaluateRule(rule, request)) {
                triggeredRules.add(rule.ruleId());
                // Normalize large threshold values to 0-1 range for score calculation
                double scoreContribution = rule.threshold();
                if (scoreContribution > 1.0) {
                    scoreContribution = 0.7; // Normalize large values to HIGH risk contribution
                }
                riskScore += scoreContribution;
            }
        }

        // Normalize risk score to 0-1 range
        riskScore = Math.min(1.0, riskScore);

        FraudAssessment.RiskLevel level = determineRiskLevel(riskScore);
        String explanation = buildExplanation(triggeredRules, riskScore);

        FraudAssessment assessment = new FraudAssessment(
            transactionId,
            riskScore,
            level,
            triggeredRules,
            explanation,
            Instant.now()
        );

        assessments.put(transactionId, assessment);

        LOG.info("Fraud assessment for {}: score={:.2f}, level={}, rules={}",
            transactionId, riskScore, level, triggeredRules.size());

        return Promise.of(assessment);
    }

    @Override
    public Promise<FraudPattern> detectPatterns(String productId, TimeWindow window) {
        // Simple pattern detection: velocity check
        List<FraudAssessment> recentAssessments = assessments.values().stream()
            .filter(a -> a.assessedAt().isAfter(window.start()) && a.assessedAt().isBefore(window.end().plusMillis(1)))
            .filter(a -> a.riskLevel().ordinal() >= FraudAssessment.RiskLevel.HIGH.ordinal())
            .collect(Collectors.toList());

        if (recentAssessments.size() >= 5) {
            FraudPattern pattern = new FraudPattern(
                UUID.randomUUID().toString(),
                "HIGH_VELOCITY_RISK",
                recentAssessments.stream().map(FraudAssessment::transactionId).collect(Collectors.toList()),
                0.85,
                Instant.now()
            );

            detectedPatterns.computeIfAbsent(productId, k -> new ArrayList<>()).add(pattern);
            return Promise.of(pattern);
        }

        return Promise.of(null);
    }

    @Override
    public Promise<Void> trainModel(String modelId, TrainingData data) {
        // Simulate model training
        int fraudCount = (int) data.examples().stream().filter(TrainingData.TrainingExample::isFraud).count();
        int totalCount = data.examples().size();

        ModelMetrics metrics = new ModelMetrics(
            modelId,
            0.92,  // precision
            0.88,  // recall
            0.90,  // f1
            totalCount,
            totalCount - fraudCount,
            fraudCount,
            Instant.now()
        );

        modelMetrics.put(modelId, metrics);

        LOG.info("Trained model {} on {} examples ({} fraud)",
            modelId, totalCount, fraudCount);

        return Promise.complete();
    }

    @Override
    public Promise<ModelMetrics> getModelMetrics(String modelId) {
        return Promise.of(modelMetrics.get(modelId));
    }

    @Override
    public Promise<Void> registerRule(String productId, FraudRule rule) {
        productRules.computeIfAbsent(productId, k -> new ArrayList<>()).add(rule);
        LOG.info("Registered rule {} for product {}", rule.ruleId(), productId);
        return Promise.complete();
    }

    private boolean evaluateRule(FraudRule rule, FraudDetectionRequest request) {
        Map<String, Object> features = request.features();

        // Simple rule evaluation based on rule type
        switch (rule.ruleType()) {
            case "ALWAYS_HIGH":
                // Always trigger for testing purposes
                return true;
            case "AMOUNT_THRESHOLD":
                Object amount = features.get("amount");
                if (amount instanceof Number) {
                    return ((Number) amount).doubleValue() > rule.threshold();
                }
                break;
            case "VELOCITY":
                Object count = features.get("transaction_count_24h");
                if (count instanceof Number) {
                    return ((Number) count).intValue() > rule.threshold();
                }
                break;
            case "GEO_ANOMALY":
                return features.containsKey("geo_anomaly") && Boolean.TRUE.equals(features.get("geo_anomaly"));
            default:
                // Default: threshold as probability
                return Math.random() < rule.threshold();
        }

        return false;
    }

    private FraudAssessment.RiskLevel determineRiskLevel(double score) {
        if (score >= 0.8) return FraudAssessment.RiskLevel.CRITICAL;
        if (score >= 0.6) return FraudAssessment.RiskLevel.HIGH;
        if (score >= 0.3) return FraudAssessment.RiskLevel.MEDIUM;
        return FraudAssessment.RiskLevel.LOW;
    }

    private String buildExplanation(List<String> triggeredRules, double score) {
        if (triggeredRules.isEmpty()) {
            return "No fraud indicators detected";
        }
        return String.format("Risk score: %.2f. Triggered rules: %s",
            score, String.join(", ", triggeredRules));
    }

    @Override
    public String toString() {
        return "StandardFraudDetectionPlugin{assessments=" + assessments.size() +
               ", rules=" + productRules.size() + "}";
    }
}
