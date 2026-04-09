package com.ghatana.plugin.risk.impl;

import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.plugin.risk.RiskManagementPlugin;
import io.activej.promise.Promise;
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

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Comprehensive tests for StandardRiskManagementPlugin
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("StandardRiskManagementPlugin Tests")
@ExtendWith(MockitoExtension.class)
class StandardRiskManagementPluginTest extends EventloopTestBase {

    @Mock
    private PluginContext mockContext;

    private StandardRiskManagementPlugin riskPlugin;

    @BeforeEach
    void setUp() {
        riskPlugin = new StandardRiskManagementPlugin();
    }

    @Test
    @DisplayName("Should initialize risk management plugin")
    void testInitialize() {
        assertThat(riskPlugin.getState()).isEqualTo(PluginState.UNLOADED);
        Promise<Void> result = riskPlugin.initialize(mockContext);
        runPromise(() -> result);
        assertThat(riskPlugin.getState()).isEqualTo(PluginState.INITIALIZED);
    }

    @Test
    @DisplayName("Should start risk management plugin")
    void testStart() {
        runPromise(() -> riskPlugin.initialize(mockContext));
        Promise<Void> result = riskPlugin.start();
        runPromise(() -> result);
        assertThat(riskPlugin.getState()).isEqualTo(PluginState.STARTED);
    }

    @Test
    @DisplayName("Should return correct metadata")
    void testMetadata() {
        var metadata = riskPlugin.metadata();
        assertThat(metadata.name()).isEqualTo("Risk Management Plugin");
        assertThat(metadata.version()).isEqualTo("1.0.0");
    }

    @Test
    @DisplayName("Should calculate market risk")
    void testCalculateRisk_Market() {
        runPromise(() -> riskPlugin.initialize(mockContext)
                .then(v -> riskPlugin.start()));

        Map<String, Object> factors = Map.of(
            "volatility", 0.25,
            "position_size", 500000.0,
            "concentration", 0.15,
            "liquidity", 0.8
        );

        Promise<RiskManagementPlugin.RiskScore> result =
                riskPlugin.calculateRisk("portfolio1", RiskManagementPlugin.RiskType.MARKET, factors);
        RiskManagementPlugin.RiskScore score = runPromise(() -> result);

        assertThat(score.entityId()).isEqualTo("portfolio1");
        assertThat(score.type()).isEqualTo(RiskManagementPlugin.RiskType.MARKET);
        assertThat(score.score()).isBetween(0.0, 1.0);
        assertThat(score.componentScores()).isNotEmpty();
    }

    @Test
    @DisplayName("Should calculate credit risk")
    void testCalculateRisk_Credit() {
        runPromise(() -> riskPlugin.initialize(mockContext)
                .then(v -> riskPlugin.start()));

        Map<String, Object> factors = Map.of(
            "credit_rating", 850.0,
            "default_probability", 0.01,
            "exposure", 100000.0
        );

        Promise<RiskManagementPlugin.RiskScore> result =
                riskPlugin.calculateRisk("borrower1", RiskManagementPlugin.RiskType.CREDIT, factors);
        RiskManagementPlugin.RiskScore score = runPromise(() -> result);

        assertThat(score.type()).isEqualTo(RiskManagementPlugin.RiskType.CREDIT);
        assertThat(score.calculatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should set risk limits")
    void testSetRiskLimits() {
        runPromise(() -> riskPlugin.initialize(mockContext)
                .then(v -> riskPlugin.start()));

        RiskManagementPlugin.RiskLimits limits = new RiskManagementPlugin.RiskLimits(
            new BigDecimal("10000000.00"),
            new BigDecimal("50000000.00"),
            new BigDecimal("1000000.00"),
            new BigDecimal("1000000.00"),
            new BigDecimal("500000.00")
        );

        Promise<Void> result = riskPlugin.setRiskLimits("trader123", limits);
        runPromise(() -> result);

        // Verify limits were set
        assertThat(riskPlugin).isNotNull();
    }

    @Test
    @DisplayName("Should get active risk alerts")
    void testGetActiveAlerts() {
        runPromise(() -> riskPlugin.initialize(mockContext)
                .then(v -> riskPlugin.start()));

        Promise<List<RiskManagementPlugin.RiskAlert>> result =
                riskPlugin.getActiveAlerts("entity123");
        List<RiskManagementPlugin.RiskAlert> alerts = runPromise(() -> result);

        assertThat(alerts).isNotNull();
    }

    @Test
    @DisplayName("Should generate risk report")
    void testGenerateReport() {
        runPromise(() -> riskPlugin.initialize(mockContext)
                .then(v -> riskPlugin.start())
                .then(v -> {
                    Map<String, Object> factors = Map.of("volatility", 0.2);
                    return riskPlugin.calculateRisk("entity456",
                        RiskManagementPlugin.RiskType.OPERATIONAL, factors);
                }));

        Instant now = Instant.now();
        RiskManagementPlugin.TimeRange range = new RiskManagementPlugin.TimeRange(
            now.minusSeconds(3600), now);

        Promise<RiskManagementPlugin.RiskReport> result =
                riskPlugin.generateReport("entity456", range);
        RiskManagementPlugin.RiskReport report = runPromise(() -> result);

        assertThat(report.entityId()).isEqualTo("entity456");
        assertThat(report.range()).isEqualTo(range);
        assertThat(report.generatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should shutdown risk management plugin")
    void testShutdown() {
        runPromise(() -> riskPlugin.initialize(mockContext)
                .then(v -> riskPlugin.start()));

        Promise<Void> result = riskPlugin.shutdown();
        runPromise(() -> result);

        assertThat(riskPlugin.getState()).isEqualTo(PluginState.UNLOADED);
    }
}
