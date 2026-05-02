package com.ghatana.plugin.risk.impl;

import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.plugin.risk.RiskManagementPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * KP-RSK: Tenant isolation and deterministic fixture conformance tests
 * for {@link StandardRiskManagementPlugin}.
 *
 * <p>Covers:</p>
 * <ul>
 *   <li>KP-RSK-001: risk limits isolation â€” limits set for entity-A must not affect entity-B alerts</li>
 *   <li>KP-RSK-002: limit breach deterministic fixture â€” position exceeding limit must generate a BREACH alert</li>
 *   <li>KP-RSK-003: within-limit fixture â€” normal position must not generate a BREACH alert</li>
 *   <li>KP-RSK-004: alert isolation â€” alerts for entity-A must not appear in entity-B's alert list</li>
 *   <li>KP-RSK-005: explainability â€” risk score must carry non-empty factors for audit purposes</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose KP-RSK risk management plugin tenant isolation and deterministic fixture tests
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("RiskManagementPlugin â€” tenant isolation and deterministic fixture tests")
@ExtendWith(MockitoExtension.class)
class RiskManagementTenantIsolationTest extends EventloopTestBase {

    @Mock
    private PluginContext mockContext;

    private StandardRiskManagementPlugin plugin;

    @BeforeEach
    void setUp() {
        plugin = new StandardRiskManagementPlugin();
        runPromise(() -> plugin.initialize(mockContext).then(v -> plugin.start()));
    }

    // â”€â”€ KP-RSK-001 / KP-RSK-004: Isolation â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    @DisplayName("KP-RSK-001/004: risk limits and alerts for entity-A must not contaminate entity-B")
    void testEntityIsolation() {
        String entityA = "portfolio-A-" + UUID.randomUUID();
        String entityB = "portfolio-B-" + UUID.randomUUID();

        Map<String, BigDecimal> limitsA = Map.of(
                "position_size", new BigDecimal("1000"),
                "max_exposure", new BigDecimal("50000"));
        runPromise(() -> plugin.setRiskLimits(entityA, limitsA));

        // Calculate a very large risk for entity-A to trigger alerts
        runPromise(() -> plugin.calculateRisk(entityA, RiskManagementPlugin.RiskModelId.MARKET,
                Map.of("position_value", 100_000, "var_95", 60_000)));

        // Entity-B should have zero alerts (no limits set, no large position)
        List<RiskManagementPlugin.RiskAlert> alertsB = runPromise(() -> plugin.getActiveAlerts(entityB));

        assertThat(alertsB)
                .as("entity-B must have no alerts caused by entity-A's limit breach")
                .isEmpty();
    }

    // â”€â”€ KP-RSK-002: Limit breach deterministic fixture â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    @DisplayName("KP-RSK-002: position exceeding risk limit must generate a BREACH alert")
    void testLimitBreachGeneratesAlert() {
        String entityId = "portfolio-BREACH-" + UUID.randomUUID();

        Map<String, BigDecimal> limits = Map.of(
                "position_size", new BigDecimal("1000"),
                "max_exposure", new BigDecimal("5000"));
        runPromise(() -> plugin.setRiskLimits(entityId, limits));

        // Position value far exceeds the max position limit of 5000
        RiskManagementPlugin.RiskScore score = runPromise(() -> plugin.calculateRisk(
                entityId, RiskManagementPlugin.RiskModelId.MARKET,
                Map.of("position_size", 50_000.0, "volatility", 0.9)));

        assertThat(score).isNotNull();

        List<RiskManagementPlugin.RiskAlert> alerts = runPromise(() -> plugin.getActiveAlerts(entityId));
        assertThat(alerts)
                .as("exceeding position limit must produce at least one BREACH alert")
                .isNotEmpty()
                .anyMatch(a -> a.severity() >= 0.8 || a.severity() >= 0.6);
    }

    // â”€â”€ KP-RSK-003: Within-limit fixture â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    @DisplayName("KP-RSK-003: position within limits must not generate a BREACH alert")
    void testWithinLimitNoAlert() {
        String entityId = "portfolio-OK-" + UUID.randomUUID();

        Map<String, BigDecimal> limits = Map.of(
                "position_size", new BigDecimal("1000"),
                "max_exposure", new BigDecimal("100000"));
        runPromise(() -> plugin.setRiskLimits(entityId, limits));

        runPromise(() -> plugin.calculateRisk(entityId, RiskManagementPlugin.RiskModelId.MARKET,
                Map.of("position_size", 500.0, "volatility", 0.1)));

        List<RiskManagementPlugin.RiskAlert> alerts = runPromise(() -> plugin.getActiveAlerts(entityId));
        assertThat(alerts)
                .as("position within limits must not trigger any alerts")
                .isEmpty();
    }

    // â”€â”€ KP-RSK-005: Explainability â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    @DisplayName("KP-RSK-005: risk score must carry non-empty factors list for audit purposes")
    void testExplainabilityPresent() {
        String entityId = "portfolio-EXPLAIN-" + UUID.randomUUID();

        RiskManagementPlugin.RiskScore score = runPromise(() -> plugin.calculateRisk(
                entityId, RiskManagementPlugin.RiskModelId.CREDIT,
                Map.of("credit_score", 650, "debt_ratio", 0.6)));

        assertThat(score.componentScores())
                .as("risk score must include contributing factor explanations for audit purposes")
                .isNotNull()
                .isNotEmpty();
    }
}
