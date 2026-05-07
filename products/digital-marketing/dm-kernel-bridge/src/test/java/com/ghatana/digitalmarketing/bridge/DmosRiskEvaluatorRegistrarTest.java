package com.ghatana.digitalmarketing.bridge;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.plugin.risk.RiskManagementPlugin.RiskModelId;
import com.ghatana.plugin.risk.impl.StandardRiskManagementPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DmosRiskEvaluatorRegistrar} (KERNEL-P1-2).
 */
@DisplayName("DmosRiskEvaluatorRegistrar")
class DmosRiskEvaluatorRegistrarTest extends EventloopTestBase {

    private StandardRiskManagementPlugin plugin;

    @BeforeEach
    void setUp() {
        plugin = new StandardRiskManagementPlugin();
        DmosRiskEvaluatorRegistrar.register(plugin);
    }

    @Test
    @DisplayName("register throws NullPointerException when plugin is null")
    void registerNullPluginThrows() {
        assertThatThrownBy(() -> DmosRiskEvaluatorRegistrar.register(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("CAMPAIGN_LAUNCH evaluator scores high risk for missing assets")
    void campaignLaunchHighRiskMissingAssets() {
        var score = runPromise(() -> plugin.calculateRisk("entity-1",
            DmosRiskEvaluatorRegistrar.CAMPAIGN_LAUNCH,
            Map.of("assetsComplete", "false")));
        assertThat(score.score()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("CAMPAIGN_LAUNCH evaluator scores low risk for complete assets")
    void campaignLaunchLowRiskCompleteAssets() {
        var score = runPromise(() -> plugin.calculateRisk("entity-1",
            DmosRiskEvaluatorRegistrar.CAMPAIGN_LAUNCH,
            Map.of("assetsComplete", "true", "audienceScore", "0.9", "budgetRemaining", "5000.0")));
        assertThat(score.score()).isLessThan(0.5);
    }

    @Test
    @DisplayName("BUDGET_APPROVAL evaluator scores very high risk for large amount")
    void budgetApprovalHighRiskLargeAmount() {
        var score = runPromise(() -> plugin.calculateRisk("entity-2",
            DmosRiskEvaluatorRegistrar.BUDGET_APPROVAL,
            Map.of("requestedAmount", "500000", "approverPresent", "false")));
        assertThat(score.score()).isGreaterThan(0.5);
    }

    @Test
    @DisplayName("BUDGET_APPROVAL evaluator scores low risk for small amount with approver")
    void budgetApprovalLowRiskSmallAmount() {
        var score = runPromise(() -> plugin.calculateRisk("entity-2",
            DmosRiskEvaluatorRegistrar.BUDGET_APPROVAL,
            Map.of("requestedAmount", "500", "approverPresent", "true")));
        assertThat(score.score()).isLessThan(0.4);
    }

    @Test
    @DisplayName("AD_SPEND_ANOMALY evaluator scores high risk for large spend ratio")
    void adSpendAnomalyHighRiskLargeRatio() {
        var score = runPromise(() -> plugin.calculateRisk("entity-3",
            DmosRiskEvaluatorRegistrar.AD_SPEND_ANOMALY,
            Map.of("spendRatio", "5.0")));
        assertThat(score.score()).isGreaterThan(0.5);
    }

    @Test
    @DisplayName("AI_EXECUTION evaluator scores high risk for low confidence and unverified model")
    void aiExecutionHighRisk() {
        var score = runPromise(() -> plugin.calculateRisk("entity-4",
            DmosRiskEvaluatorRegistrar.AI_EXECUTION,
            Map.of("confidenceScore", "0.1", "modelVerified", "false")));
        assertThat(score.score()).isGreaterThan(0.5);
    }

    @Test
    @DisplayName("AI_EXECUTION evaluator scores low risk for high confidence and verified model")
    void aiExecutionLowRisk() {
        var score = runPromise(() -> plugin.calculateRisk("entity-4",
            DmosRiskEvaluatorRegistrar.AI_EXECUTION,
            Map.of("confidenceScore", "0.95", "modelVerified", "true")));
        assertThat(score.score()).isLessThan(0.5);
    }

    @Test
    @DisplayName("DMOS model IDs use DMOS_ prefix and are distinct from platform defaults")
    void modelIdDistinctFromPlatformDefaults() {
        assertThat(DmosRiskEvaluatorRegistrar.CAMPAIGN_LAUNCH.getId()).startsWith("DMOS_");
        assertThat(DmosRiskEvaluatorRegistrar.BUDGET_APPROVAL.getId()).startsWith("DMOS_");
        assertThat(DmosRiskEvaluatorRegistrar.AD_SPEND_ANOMALY.getId()).startsWith("DMOS_");
        assertThat(DmosRiskEvaluatorRegistrar.AI_EXECUTION.getId()).startsWith("DMOS_");

        assertThat(DmosRiskEvaluatorRegistrar.CAMPAIGN_LAUNCH)
            .isNotEqualTo(RiskModelId.OPERATIONAL);
    }
}
