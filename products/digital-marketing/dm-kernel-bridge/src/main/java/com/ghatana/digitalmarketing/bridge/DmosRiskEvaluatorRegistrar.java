package com.ghatana.digitalmarketing.bridge;

import com.ghatana.plugin.risk.RiskManagementPlugin.RiskModelId;
import com.ghatana.plugin.risk.impl.StandardRiskManagementPlugin;
import com.ghatana.plugin.risk.impl.StandardRiskManagementPlugin.FactorEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

/**
 * Registers DMOS-specific risk factor evaluators with the platform
 * {@link StandardRiskManagementPlugin}.
 *
 * <p>Must be called <em>before</em> {@code plugin.start()} to ensure all
 * evaluators are available at runtime. Each evaluator maps domain-specific
 * factor keys to a normalised risk score in the range {@code [0.0, 1.0]}.
 *
 * @doc.type class
 * @doc.purpose Registers DMOS risk evaluators with StandardRiskManagementPlugin (KERNEL-P1-2)
 * @doc.layer product
 * @doc.pattern Registry
 */
public final class DmosRiskEvaluatorRegistrar {

    private static final Logger LOG = LoggerFactory.getLogger(DmosRiskEvaluatorRegistrar.class);

    /** Risk model for campaign launch pre-flight checks. */
    public static final RiskModelId CAMPAIGN_LAUNCH = new RiskModelId("DMOS_CAMPAIGN_LAUNCH");

    /** Risk model for budget approval workflows. */
    public static final RiskModelId BUDGET_APPROVAL = new RiskModelId("DMOS_BUDGET_APPROVAL");

    /** Risk model for ad-spend anomaly detection. */
    public static final RiskModelId AD_SPEND_ANOMALY = new RiskModelId("DMOS_AD_SPEND_ANOMALY");

    /** Risk model for AI-driven action execution. */
    public static final RiskModelId AI_EXECUTION = new RiskModelId("DMOS_AI_EXECUTION");

    private DmosRiskEvaluatorRegistrar() {
        // utility class
    }

    /**
     * Registers all DMOS evaluators on the provided plugin instance.
     *
     * @param plugin the risk management plugin to configure
     */
    public static void register(StandardRiskManagementPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin must not be null");

        plugin.registerEvaluator(CAMPAIGN_LAUNCH, campaignLaunchEvaluator());
        plugin.registerEvaluator(BUDGET_APPROVAL, budgetApprovalEvaluator());
        plugin.registerEvaluator(AD_SPEND_ANOMALY, adSpendAnomalyEvaluator());
        plugin.registerEvaluator(AI_EXECUTION, aiExecutionEvaluator());

        LOG.info("[DMOS] Risk evaluators registered: CAMPAIGN_LAUNCH, BUDGET_APPROVAL, AD_SPEND_ANOMALY, AI_EXECUTION");
    }

    // -------------------------------------------------------------------------
    // Evaluator factories
    // -------------------------------------------------------------------------

    /**
     * Campaign-launch risk: penalises missing creative assets and low audience score.
     */
    private static FactorEvaluator campaignLaunchEvaluator() {
        return (key, value) -> switch (key) {
            case "audienceScore"   -> 1.0 - clamp(toDouble(value));
            case "assetsComplete"  -> "false".equalsIgnoreCase(String.valueOf(value)) ? 0.7 : 0.0;
            case "budgetRemaining" -> toDouble(value) < 100.0 ? 0.8 : 0.1;
            default                -> 0.0;
        };
    }

    /**
     * Budget-approval risk: high risk for large amounts or missing approver.
     */
    private static FactorEvaluator budgetApprovalEvaluator() {
        return (key, value) -> switch (key) {
            case "requestedAmount" -> {
                double amount = toDouble(value);
                yield amount > 100_000.0 ? 0.9 : amount > 10_000.0 ? 0.5 : 0.1;
            }
            case "approverPresent" -> "false".equalsIgnoreCase(String.valueOf(value)) ? 0.8 : 0.0;
            case "policyViolation" -> "true".equalsIgnoreCase(String.valueOf(value)) ? 1.0 : 0.0;
            default                -> 0.0;
        };
    }

    /**
     * Ad-spend anomaly risk: detects spend ratio spikes relative to baseline.
     */
    private static FactorEvaluator adSpendAnomalyEvaluator() {
        return (key, value) -> switch (key) {
            case "spendRatio"      -> {
                double ratio = toDouble(value);
                yield ratio > 3.0 ? 1.0 : ratio > 1.5 ? 0.6 : 0.0;
            }
            case "velocityChange"  -> {
                double change = toDouble(value);
                yield change > 200.0 ? 0.9 : change > 50.0 ? 0.4 : 0.0;
            }
            default                -> 0.0;
        };
    }

    /**
     * AI-execution risk: penalises low confidence scores and unverified models.
     */
    private static FactorEvaluator aiExecutionEvaluator() {
        return (key, value) -> switch (key) {
            case "confidenceScore" -> 1.0 - clamp(toDouble(value));
            case "modelVerified"   -> "false".equalsIgnoreCase(String.valueOf(value)) ? 0.75 : 0.0;
            case "dryRun"          -> "true".equalsIgnoreCase(String.valueOf(value)) ? 0.0 : 0.2;
            default                -> 0.0;
        };
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static double toDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
