package com.ghatana.finance.extension;

import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDescriptor;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RiskManagementKernelExtension}.
 *
 * @doc.type test
 * @doc.purpose Unit tests for real-time risk management with VaR and position limits
 * @doc.layer test
 * @author Ghatana Kernel Team
 */
@DisplayName("RiskManagementKernelExtension Tests")
class RiskManagementKernelExtensionTest extends EventloopTestBase {

    private RiskManagementKernelExtension extension;

    @BeforeEach
    void setUp() {
        extension = new RiskManagementKernelExtension();
        extension.onModuleInitialized(null);
        extension.onModuleStarted(null);
    }

    @Test
    @DisplayName("Should return correct extension metadata")
    void shouldReturnCorrectExtensionMetadata() {
        assertEquals("risk-management-realtime", extension.getExtensionId());
        assertEquals("Real-time Risk Management", extension.getName());
        assertEquals(200, extension.getPriority());
        assertTrue(extension.isEnabledByDefault());
    }

    @Test
    @DisplayName("Should return valid descriptor")
    void shouldReturnValidDescriptor() {
        KernelDescriptor descriptor = extension.getDescriptor();

        assertNotNull(descriptor);
        assertEquals("risk-management-realtime", descriptor.getDescriptorId());
        assertEquals("Real-time Risk Management", descriptor.getName());
        assertEquals("1.0.0", descriptor.getVersion());
    }

    @Test
    @DisplayName("Should contribute risk management capability")
    void shouldContributeRiskManagementCapability() {
        Set<KernelCapability> capabilities = extension.getContributedCapabilities();

        assertEquals(1, capabilities.size());
        KernelCapability cap = capabilities.iterator().next();
        assertEquals("risk.management", cap.getCapabilityId());
        assertEquals(KernelCapability.CapabilityType.AI_ML, cap.getType());

        assertEquals("true", cap.getMetadata().get("real_time").toString());
        assertEquals("true", cap.getMetadata().get("supports_var").toString());
        assertEquals("true", cap.getMetadata().get("position_limits").toString());
        assertEquals("true", cap.getMetadata().get("portfolio_monitoring").toString());
    }

    @Test
    @DisplayName("Should calculate position risk correctly")
    void shouldCalculatePositionRiskCorrectly() {
        String positionId = "position-123";
        BigDecimal quantity = new BigDecimal("100");
        BigDecimal currentPrice = new BigDecimal("150.00");
        BigDecimal avgCost = new BigDecimal("145.00");

        var promise = extension.calculatePositionRisk(positionId, quantity, currentPrice, avgCost);
        RiskManagementKernelExtension.PositionRisk risk = runPromise(() -> promise);

        assertNotNull(risk);
        assertEquals(positionId, risk.getPositionId());
        assertEquals(quantity, risk.getQuantity());
        assertEquals(currentPrice, risk.getCurrentPrice());
        assertEquals(avgCost, risk.getAvgCost());

        // Unrealized P&L = 100 * (150 - 145) = 500
        BigDecimal expectedPnL = new BigDecimal("500.00");
        assertEquals(0, risk.getUnrealizedPnL().compareTo(expectedPnL), 0.01);

        // Notional = 100 * 150 = 15000
        BigDecimal expectedNotional = new BigDecimal("15000.00");
        assertEquals(0, risk.getNotionalExposure().compareTo(expectedNotional), 0.01);
    }

    @Test
    @DisplayName("Should calculate position risk with loss correctly")
    void shouldCalculatePositionRiskWithLossCorrectly() {
        BigDecimal quantity = new BigDecimal("100");
        BigDecimal currentPrice = new BigDecimal("140.00");
        BigDecimal avgCost = new BigDecimal("150.00");

        var promise = extension.calculatePositionRisk("position-loss", quantity, currentPrice, avgCost);
        RiskManagementKernelExtension.PositionRisk risk = runPromise(() -> promise);

        // Unrealized P&L = 100 * (140 - 150) = -1000
        assertTrue(risk.getUnrealizedPnL().compareTo(BigDecimal.ZERO) < 0);
    }

    @Test
    @DisplayName("Should calculate portfolio risk correctly")
    void shouldCalculatePortfolioRiskCorrectly() {
        // Create some position risks first
        RiskManagementKernelExtension.PositionRisk risk1 = runPromise(() -> extension.calculatePositionRisk(
            "pos-1", new BigDecimal("100"), new BigDecimal("150"), new BigDecimal("145")
        ));

        RiskManagementKernelExtension.PositionRisk risk2 = runPromise(() -> extension.calculatePositionRisk(
            "pos-2", new BigDecimal("50"), new BigDecimal("200"), new BigDecimal("195")
        ));

        Map<String, RiskManagementKernelExtension.PositionRisk> positions = Map.of(
            "pos-1", risk1,
            "pos-2", risk2
        );

        var promise = extension.calculatePortfolioRisk("portfolio-1", positions);
        RiskManagementKernelExtension.PortfolioRisk portfolioRisk = runPromise(() -> promise);

        assertNotNull(portfolioRisk);
        assertEquals("portfolio-1", portfolioRisk.getPortfolioId());
        assertEquals(2, portfolioRisk.getPositionCount());

        // Total market value = (100 * 150) + (50 * 200) = 15000 + 10000 = 25000
        BigDecimal expectedMarketValue = new BigDecimal("25000.00");
        assertEquals(0, portfolioRisk.getTotalMarketValue().compareTo(expectedMarketValue), 0.01);

        // VaR should be calculated and positive
        assertTrue(portfolioRisk.getPortfolioVaR().compareTo(BigDecimal.ZERO) > 0);

        // Notional exposure should equal market value
        assertEquals(0, portfolioRisk.getTotalNotionalExposure().compareTo(expectedMarketValue), 0.01);
    }

    @Test
    @DisplayName("Should update and retrieve risk limits")
    void shouldUpdateAndRetrieveRiskLimits() {
        RiskManagementKernelExtension.RiskLimits newLimits = new RiskManagementKernelExtension.RiskLimits(
            new BigDecimal("5000000"),   // Max position notional
            new BigDecimal("25000000"),  // Max portfolio notional
            new BigDecimal("0.03"),      // Max VaR (3%)
            new BigDecimal("0.20"),     // Max concentration (20%)
            new BigDecimal("500000")    // Max single position loss
        );

        runPromise(() -> extension.updateRiskLimits(newLimits));
        RiskManagementKernelExtension.RiskLimits retrieved = extension.getRiskLimits();

        assertNotNull(retrieved);
        assertEquals(newLimits.getMaxPositionNotional(), retrieved.getMaxPositionNotional());
        assertEquals(newLimits.getMaxPortfolioNotional(), retrieved.getMaxPortfolioNotional());
        assertEquals(newLimits.getMaxPortfolioVaR(), retrieved.getMaxPortfolioVaR());
        assertEquals(newLimits.getMaxConcentration(), retrieved.getMaxConcentration());
        assertEquals(newLimits.getMaxPositionLoss(), retrieved.getMaxPositionLoss());
    }

    @Test
    @DisplayName("Should retrieve position risk by ID")
    void shouldRetrievePositionRiskById() {
        String positionId = "tracked-position";
        runPromise(() -> extension.calculatePositionRisk(positionId,
            new BigDecimal("100"), new BigDecimal("150"), new BigDecimal("145")));

        RiskManagementKernelExtension.PositionRisk retrieved = extension.getPositionRisk(positionId);

        assertNotNull(retrieved);
        assertEquals(positionId, retrieved.getPositionId());
    }

    @Test
    @DisplayName("Should return null for non-existent position risk")
    void shouldReturnNullForNonExistentPositionRisk() {
        RiskManagementKernelExtension.PositionRisk risk = extension.getPositionRisk("non-existent");
        assertNull(risk);
    }

    @Test
    @DisplayName("Should retrieve portfolio risk by ID")
    void shouldRetrievePortfolioRiskById() {
        String portfolioId = "tracked-portfolio";

        RiskManagementKernelExtension.PositionRisk risk = runPromise(() -> extension.calculatePositionRisk(
            "pos-1", new BigDecimal("100"), new BigDecimal("150"), new BigDecimal("145")
        ));

        runPromise(() -> extension.calculatePortfolioRisk(portfolioId, Map.of("pos-1", risk)));

        RiskManagementKernelExtension.PortfolioRisk retrieved = extension.getPortfolioRisk(portfolioId);
        assertNotNull(retrieved);
        assertEquals(portfolioId, retrieved.getPortfolioId());
    }

    @Test
    @DisplayName("Should handle empty portfolio correctly")
    void shouldHandleEmptyPortfolioCorrectly() {
        var promise = extension.calculatePortfolioRisk("empty-portfolio", Map.of());
        RiskManagementKernelExtension.PortfolioRisk risk = runPromise(() -> promise);

        assertNotNull(risk);
        assertEquals(BigDecimal.ZERO, risk.getTotalMarketValue());
        assertEquals(BigDecimal.ZERO, risk.getTotalUnrealizedPnL());
        assertEquals(BigDecimal.ZERO, risk.getTotalNotionalExposure());
        assertEquals(BigDecimal.ZERO, risk.getConcentrationRisk());
        assertEquals(0, risk.getPositionCount());
    }

    @Test
    @DisplayName("Should calculate concentration risk correctly")
    void shouldCalculateConcentrationRiskCorrectly() {
        // Create positions with varying sizes
        RiskManagementKernelExtension.PositionRisk largePosition = runPromise(() -> extension.calculatePositionRisk(
            "large", new BigDecimal("1000"), new BigDecimal("100"), new BigDecimal("95")
        ));

        RiskManagementKernelExtension.PositionRisk smallPosition = runPromise(() -> extension.calculatePositionRisk(
            "small", new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("95")
        ));

        Map<String, RiskManagementKernelExtension.PositionRisk> positions = Map.of(
            "large", largePosition,
            "small", smallPosition
        );

        var promise = extension.calculatePortfolioRisk("concentrated-portfolio", positions);
        RiskManagementKernelExtension.PortfolioRisk portfolioRisk = runPromise(() -> promise);

        // Concentration = largest position / total = 100000 / 110000 = ~0.909
        assertTrue(portfolioRisk.getConcentrationRisk().compareTo(new BigDecimal("0.8")) > 0);
        assertTrue(portfolioRisk.getConcentrationRisk().compareTo(BigDecimal.ONE) <= 0);
    }

    @Test
    @DisplayName("Should reject operations when not started")
    void shouldRejectOperationsWhenNotStarted() {
        extension.onModuleStopped(null);

        assertThrows(Exception.class, () -> runPromise(() ->
            extension.calculatePositionRisk("test", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE)));
    }

    @Test
    @DisplayName("Should check compatibility with event processing module")
    void shouldCheckCompatibilityWithEventProcessingModule() {
        assertTrue(extension.isCompatible(createModuleWithCapability("event.processing")));
        assertFalse(extension.isCompatible(createModuleWithCapability("data.storage")));
    }

    @Test
    @DisplayName("Position risk should track calculation timestamp")
    void positionRiskShouldTrackCalculationTimestamp() {
        var promise = extension.calculatePositionRisk("timestamp-test",
            new BigDecimal("100"), new BigDecimal("150"), new BigDecimal("145"));
        RiskManagementKernelExtension.PositionRisk risk = runPromise(() -> promise);

        assertNotNull(risk.getCalculatedAt());
        // Should be recent
        assertTrue(risk.getCalculatedAt().isAfter(java.time.Instant.now().minusSeconds(5)));
    }

    @Test
    @DisplayName("Portfolio risk should track calculation timestamp")
    void portfolioRiskShouldTrackCalculationTimestamp() {
        var promise = extension.calculatePortfolioRisk("timestamp-portfolio", Map.of());
        RiskManagementKernelExtension.PortfolioRisk risk = runPromise(() -> promise);

        assertNotNull(risk.getCalculatedAt());
    }

    @Test
    @DisplayName("Should handle negative quantities (short positions)")
    void shouldHandleNegativeQuantities() {
        // Short position
        var promise = extension.calculatePositionRisk("short-position",
            new BigDecimal("-100"), new BigDecimal("150"), new BigDecimal("145"));
        RiskManagementKernelExtension.PositionRisk risk = runPromise(() -> promise);

        assertNotNull(risk);
        // Delta should reflect short position
        assertTrue(risk.getDelta().compareTo(BigDecimal.ZERO) < 0);
        // Notional exposure should still be positive
        assertTrue(risk.getNotionalExposure().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("Risk limits should have default values")
    void riskLimitsShouldHaveDefaultValues() {
        RiskManagementKernelExtension.RiskLimits limits = extension.getRiskLimits();

        assertNotNull(limits);
        assertTrue(limits.getMaxPositionNotional().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(limits.getMaxPortfolioNotional().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(limits.getMaxPortfolioVaR().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(limits.getMaxConcentration().compareTo(BigDecimal.ZERO) > 0);
    }

    // ==================== Test Helpers ====================

    private com.ghatana.kernel.module.KernelModule createModuleWithCapability(String capabilityId) {
        return new com.ghatana.kernel.module.KernelModule() {
            @Override public String getModuleId() { return "test-module"; }
            @Override public String getVersion() { return "1.0.0"; }
            @Override public Set<com.ghatana.kernel.descriptor.KernelCapability> getCapabilities() {
                return Set.of(new com.ghatana.kernel.descriptor.KernelCapability(capabilityId, "Test", "Test capability", com.ghatana.kernel.descriptor.KernelCapability.CapabilityType.BUSINESS_LOGIC, java.util.Map.of()));
            }
            @Override public Set<com.ghatana.kernel.descriptor.KernelDependency> getDependencies() { return Set.of(); }
            @Override public void initialize(com.ghatana.kernel.context.KernelContext ctx) {}
            @Override public io.activej.promise.Promise<Void> start() { return io.activej.promise.Promise.complete(); }
            @Override public io.activej.promise.Promise<Void> stop() { return io.activej.promise.Promise.complete(); }
            @Override public com.ghatana.platform.health.HealthStatus getHealthStatus() {
                return com.ghatana.platform.health.HealthStatus.healthy();
            }
        };
    }
}
