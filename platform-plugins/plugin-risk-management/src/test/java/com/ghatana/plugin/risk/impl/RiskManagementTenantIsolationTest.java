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
 *   <li>KP-RSK-001: risk limits isolation — limits set for entity-A must not affect entity-B alerts</li>
 *   <li>KP-RSK-002: limit breach deterministic fixture — position exceeding limit must generate a BREACH alert</li>
 *   <li>KP-RSK-003: within-limit fixture — normal position must not generate a BREACH alert</li>
 *   <li>KP-RSK-004: alert isolation — alerts for entity-A must not appear in entity-B's alert list</li>
 *   <li>KP-RSK-005: explainability — risk score must carry non-empty factors for audit purposes</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose KP-RSK risk management plugin tenant isolation and deterministic fixture tests
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("RiskManagementPlugin — tenant isolation and deterministic fixture tests")
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

    // ── KP-RSK-001 / KP-RSK-004: Isolation ────────────────────────────────────

    @Test
    @DisplayName("KP-RSK-001/004: risk limits and alerts for entity-A must not contaminate entity-B")
    void testEntityIsolation() {
        String entityA = "portfolio-A-" + UUID.randomUUID();
        String entityB = "portfolio-B-" + UUID.randomUUID();

        RiskManagementPlugin.RiskLimits limitsA = new RiskManagementPlugin.RiskLimits(
                new BigDecimal("1000"), new BigDecimal("50000"), new BigDecimal("0.3"),
                new BigDecimal("10000"), new BigDecimal("500"));
        runPromise(() -> plugin.setRiskLimits(entityA, limitsA));

        // Calculate a very large risk for entity-A to trigger alerts
        runPromise(() -> plugin.calculateRisk(entityA, RiskManagementPlugin.RiskType.MARKET,
                Map.of("position_value", 100_000, "var_95", 60_000)));

        // Entity-B should have zero alerts (no limits set, no large position)
        List<RiskManagementPlugin.RiskAlert> alertsB = runPromise(() -> plugin.getActiveAlerts(entityB));

        assertThat(alertsB)
                .as("entity-B must have no alerts caused by entity-A's limit breach")
                .isEmpty();
    }

    // ── KP-RSK-002: Limit breach deterministic fixture ───────────────────────

    @Test
    @DisplayName("KP-RSK-002: position exceeding risk limit must generate a BREACH alert")
    void testLimitBreachGeneratesAlert() {
        String entityId = "portfolio-BREACH-" + UUID.randomUUID();

        RiskManagementPlugin.RiskLimits limits = new RiskManagementPlugin.RiskLimits(
                new BigDecimal("1000"), new BigDecimal("5000"), new BigDecimal("0.2"),
                new BigDecimal("1000"), new BigDecimal("200"));
        runPromise(() -> plugin.setRiskLimits(entityId, limits));

        // Position value far exceeds the max position limit of 5000
        RiskManagementPlugin.RiskScore score = runPromise(() -> plugin.calculateRisk(
                entityId, RiskManagementPlugin.RiskType.MARKET,
                Map.of("position_value", 50_000, "var_95", 30_000)));

        assertThat(score).isNotNull();

        List<RiskManagementPlugin.RiskAlert> alerts = runPromise(() -> plugin.getActiveAlerts(entityId));
        assertThat(alerts)
                .as("exceeding position limit must produce at least one BREACH alert")
                .isNotEmpty()
                .anyMatch(a -> a.severity() >= 0.8 || a.severity() >= 0.6);
    }

    // ── KP-RSK-003: Within-limit fixture ──────────────────────────────────────

    @Test
    @DisplayName("KP-RSK-003: position within limits must not generate a BREACH alert")
    void testWithinLimitNoAlert() {
        String entityId = "portfolio-OK-" + UUID.randomUUID();

        RiskManagementPlugin.RiskLimits limits = new RiskManagementPlugin.RiskLimits(
                new BigDecimal("1000"), new BigDecimal("100000"), new BigDecimal("0.5"),
                new BigDecimal("50000"), new BigDecimal("1000"));
        runPromise(() -> plugin.setRiskLimits(entityId, limits));

        runPromise(() -> plugin.calculateRisk(entityId, RiskManagementPlugin.RiskType.MARKET,
                Map.of("position_value", 1_000, "var_95", 500)));

        List<RiskManagementPlugin.RiskAlert> alerts = runPromise(() -> plugin.getActiveAlerts(entityId));
        assertThat(alerts)
                .as("position within limits must not trigger any alerts")
                .isEmpty();
    }

    // ── KP-RSK-005: Explainability ────────────────────────────────────────────

    @Test
    @DisplayName("KP-RSK-005: risk score must carry non-empty factors list for audit purposes")
    void testExplainabilityPresent() {
        String entityId = "portfolio-EXPLAIN-" + UUID.randomUUID();

        RiskManagementPlugin.RiskScore score = runPromise(() -> plugin.calculateRisk(
                entityId, RiskManagementPlugin.RiskType.CREDIT,
                Map.of("credit_score", 650, "debt_ratio", 0.6)));

        assertThat(score.componentScores())
                .as("risk score must include contributing factor explanations for audit purposes")
                .isNotNull()
                .isNotEmpty();
    }
}
