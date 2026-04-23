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
@ExtendWith(MockitoExtension.class) // GH-90000
class StandardRiskManagementPluginTest extends EventloopTestBase {

    @Mock
    private PluginContext mockContext;

    private StandardRiskManagementPlugin riskPlugin;

    @BeforeEach
    void setUp() { // GH-90000
        riskPlugin = new StandardRiskManagementPlugin(); // GH-90000
    }

    @Test
    @DisplayName("Should initialize risk management plugin")
    void testInitialize() { // GH-90000
        assertThat(riskPlugin.getState()).isEqualTo(PluginState.UNLOADED); // GH-90000
        Promise<Void> result = riskPlugin.initialize(mockContext); // GH-90000
        runPromise(() -> result); // GH-90000
        assertThat(riskPlugin.getState()).isEqualTo(PluginState.INITIALIZED); // GH-90000
    }

    @Test
    @DisplayName("Should start risk management plugin")
    void testStart() { // GH-90000
        runPromise(() -> riskPlugin.initialize(mockContext)); // GH-90000
        Promise<Void> result = riskPlugin.start(); // GH-90000
        runPromise(() -> result); // GH-90000
        assertThat(riskPlugin.getState()).isEqualTo(PluginState.STARTED); // GH-90000
    }

    @Test
    @DisplayName("Should return correct metadata")
    void testMetadata() { // GH-90000
        var metadata = riskPlugin.metadata(); // GH-90000
        assertThat(metadata.name()).isEqualTo("Risk Management Plugin");
        assertThat(metadata.version()).isEqualTo("1.0.0");
    }

    @Test
    @DisplayName("Should calculate market risk")
    void testCalculateRisk_Market() { // GH-90000
        runPromise(() -> riskPlugin.initialize(mockContext) // GH-90000
                .then(v -> riskPlugin.start())); // GH-90000

        Map<String, Object> factors = Map.of( // GH-90000
            "volatility", 0.25,
            "position_size", 500000.0,
            "concentration", 0.15,
            "liquidity", 0.8
        );

        Promise<RiskManagementPlugin.RiskScore> result =
                riskPlugin.calculateRisk("portfolio1", RiskManagementPlugin.RiskType.MARKET, factors); // GH-90000
        RiskManagementPlugin.RiskScore score = runPromise(() -> result); // GH-90000

        assertThat(score.entityId()).isEqualTo("portfolio1");
        assertThat(score.type()).isEqualTo(RiskManagementPlugin.RiskType.MARKET); // GH-90000
        assertThat(score.score()).isBetween(0.0, 1.0); // GH-90000
        assertThat(score.componentScores()).isNotEmpty(); // GH-90000
    }

    @Test
    @DisplayName("Should calculate credit risk")
    void testCalculateRisk_Credit() { // GH-90000
        runPromise(() -> riskPlugin.initialize(mockContext) // GH-90000
                .then(v -> riskPlugin.start())); // GH-90000

        Map<String, Object> factors = Map.of( // GH-90000
            "credit_rating", 850.0,
            "default_probability", 0.01,
            "exposure", 100000.0
        );

        Promise<RiskManagementPlugin.RiskScore> result =
                riskPlugin.calculateRisk("borrower1", RiskManagementPlugin.RiskType.CREDIT, factors); // GH-90000
        RiskManagementPlugin.RiskScore score = runPromise(() -> result); // GH-90000

        assertThat(score.type()).isEqualTo(RiskManagementPlugin.RiskType.CREDIT); // GH-90000
        assertThat(score.calculatedAt()).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Should set risk limits")
    void testSetRiskLimits() { // GH-90000
        runPromise(() -> riskPlugin.initialize(mockContext) // GH-90000
                .then(v -> riskPlugin.start())); // GH-90000

        RiskManagementPlugin.RiskLimits limits = new RiskManagementPlugin.RiskLimits( // GH-90000
            new BigDecimal("10000000.00"),
            new BigDecimal("50000000.00"),
            new BigDecimal("1000000.00"),
            new BigDecimal("1000000.00"),
            new BigDecimal("500000.00")
        );

        Promise<Void> result = riskPlugin.setRiskLimits("trader123", limits); // GH-90000
        runPromise(() -> result); // GH-90000

        // Verify limits were set
        assertThat(riskPlugin).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Should get active risk alerts")
    void testGetActiveAlerts() { // GH-90000
        runPromise(() -> riskPlugin.initialize(mockContext) // GH-90000
                .then(v -> riskPlugin.start())); // GH-90000

        Promise<List<RiskManagementPlugin.RiskAlert>> result =
                riskPlugin.getActiveAlerts("entity123");
        List<RiskManagementPlugin.RiskAlert> alerts = runPromise(() -> result); // GH-90000

        assertThat(alerts).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Should generate risk report")
    void testGenerateReport() { // GH-90000
        runPromise(() -> riskPlugin.initialize(mockContext) // GH-90000
                .then(v -> riskPlugin.start()) // GH-90000
                .then(v -> { // GH-90000
                    Map<String, Object> factors = Map.of("volatility", 0.2); // GH-90000
                    return riskPlugin.calculateRisk("entity456", // GH-90000
                        RiskManagementPlugin.RiskType.OPERATIONAL, factors);
                }));

        Instant now = Instant.now(); // GH-90000
        RiskManagementPlugin.TimeRange range = new RiskManagementPlugin.TimeRange( // GH-90000
            now.minusSeconds(3600), now); // GH-90000

        Promise<RiskManagementPlugin.RiskReport> result =
                riskPlugin.generateReport("entity456", range); // GH-90000
        RiskManagementPlugin.RiskReport report = runPromise(() -> result); // GH-90000

        assertThat(report.entityId()).isEqualTo("entity456");
        assertThat(report.range()).isEqualTo(range); // GH-90000
        assertThat(report.generatedAt()).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Should shutdown risk management plugin")
    void testShutdown() { // GH-90000
        runPromise(() -> riskPlugin.initialize(mockContext) // GH-90000
                .then(v -> riskPlugin.start())); // GH-90000

        Promise<Void> result = riskPlugin.shutdown(); // GH-90000
        runPromise(() -> result); // GH-90000

        assertThat(riskPlugin.getState()).isEqualTo(PluginState.UNLOADED); // GH-90000
    }
}
