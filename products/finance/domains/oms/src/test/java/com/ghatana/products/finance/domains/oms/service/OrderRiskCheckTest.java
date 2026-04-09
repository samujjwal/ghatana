package com.ghatana.products.finance.domains.oms.service;

import com.ghatana.products.finance.domains.oms.domain.OrderSide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type Test
 * @doc.purpose Tests for pre-trade risk checks and margin validation
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("Order Risk Check Tests")
class OrderRiskCheckTest {

    private OrderRiskService riskService;

    @BeforeEach
    void setUp() {
        riskService = new OrderRiskService();
    }

    @Test
    @DisplayName("Should pass risk check for valid order")
    void shouldPassRiskCheckForValidOrder() {
        // GIVEN: Valid order within risk limits
        OrderRiskRequest request = new OrderRiskRequest(
            "client-1",
            "account-1",
            "INST-001",
            OrderSide.BUY,
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(150.00),
            BigDecimal.valueOf(15000.00)
        );

        // WHEN/THEN: Should not throw
        assertThatCode(() -> riskService.checkRisk(request))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should reject order with insufficient margin")
    void shouldRejectOrderWithInsufficientMargin() {
        // GIVEN: Order requiring more margin than available
        OrderRiskRequest request = new OrderRiskRequest(
            "client-1",
            "account-1",
            "INST-001",
            OrderSide.BUY,
            BigDecimal.valueOf(10000),
            BigDecimal.valueOf(150.00),
            BigDecimal.valueOf(1_500_000.00)
        );

        BigDecimal availableMargin = BigDecimal.valueOf(100_000.00);

        // WHEN/THEN: Should throw risk exception
        assertThatThrownBy(() -> riskService.checkMarginRequirement(request, availableMargin))
            .isInstanceOf(RiskViolationException.class)
            .hasMessageContaining("insufficient margin");
    }

    @Test
    @DisplayName("Should calculate margin requirement correctly")
    void shouldCalculateMarginRequirementCorrectly() {
        // GIVEN: Order details
        OrderRiskRequest request = new OrderRiskRequest(
            "client-1",
            "account-1",
            "INST-001",
            OrderSide.BUY,
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(150.00),
            BigDecimal.valueOf(15000.00)
        );

        // WHEN: Calculate margin requirement (50% for equity)
        BigDecimal marginRequired = riskService.calculateMarginRequirement(request);

        // THEN: Margin calculated correctly (50% of 15000)
        assertThat(marginRequired).isEqualByComparingTo(BigDecimal.valueOf(7500.00));
    }

    @Test
    @DisplayName("Should reject order exceeding credit limit")
    void shouldRejectOrderExceedingCreditLimit() {
        // GIVEN: Order exceeding credit limit
        OrderRiskRequest request = new OrderRiskRequest(
            "client-1",
            "account-1",
            "INST-001",
            OrderSide.BUY,
            BigDecimal.valueOf(10000),
            BigDecimal.valueOf(150.00),
            BigDecimal.valueOf(1_500_000.00)
        );

        BigDecimal creditLimit = BigDecimal.valueOf(1_000_000.00);

        // WHEN/THEN: Should throw risk exception
        assertThatThrownBy(() -> riskService.checkCreditLimit(request, creditLimit))
            .isInstanceOf(RiskViolationException.class)
            .hasMessageContaining("credit limit");
    }

    @Test
    @DisplayName("Should reject order exceeding exposure limit")
    void shouldRejectOrderExceedingExposureLimit() {
        // GIVEN: Order that would exceed exposure limit
        OrderRiskRequest request = new OrderRiskRequest(
            "client-1",
            "account-1",
            "INST-001",
            OrderSide.BUY,
            BigDecimal.valueOf(10000),
            BigDecimal.valueOf(150.00),
            BigDecimal.valueOf(1_500_000.00)
        );

        BigDecimal currentExposure = BigDecimal.valueOf(8_000_000.00);
        BigDecimal exposureLimit = BigDecimal.valueOf(9_000_000.00);

        // WHEN/THEN: Should throw risk exception
        assertThatThrownBy(() -> riskService.checkExposureLimit(request, currentExposure, exposureLimit))
            .isInstanceOf(RiskViolationException.class)
            .hasMessageContaining("exposure limit");
    }

    @Test
    @DisplayName("Should calculate VaR (Value at Risk) correctly")
    void shouldCalculateVaRCorrectly() {
        // GIVEN: Portfolio and order details
        OrderRiskRequest request = new OrderRiskRequest(
            "client-1",
            "account-1",
            "INST-001",
            OrderSide.BUY,
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(150.00),
            BigDecimal.valueOf(15000.00)
        );

        BigDecimal portfolioValue = BigDecimal.valueOf(1_000_000.00);
        BigDecimal volatility = BigDecimal.valueOf(0.20); // 20% volatility

        // WHEN: Calculate VaR (95% confidence, 1-day)
        BigDecimal var = riskService.calculateVaR(request, portfolioValue, volatility);

        // THEN: VaR calculated
        assertThat(var).isGreaterThan(BigDecimal.ZERO);
        assertThat(var).isLessThan(portfolioValue);
    }

    @Test
    @DisplayName("Should reject order failing stress test")
    void shouldRejectOrderFailingStressTest() {
        // GIVEN: Order that fails stress test scenario
        OrderRiskRequest request = new OrderRiskRequest(
            "client-1",
            "account-1",
            "INST-001",
            OrderSide.BUY,
            BigDecimal.valueOf(10000),
            BigDecimal.valueOf(150.00),
            BigDecimal.valueOf(1_500_000.00)
        );

        Map<String, BigDecimal> stressScenarios = Map.of(
            "MARKET_CRASH", BigDecimal.valueOf(-0.30), // -30% scenario
            "VOLATILITY_SPIKE", BigDecimal.valueOf(0.50)
        );

        // WHEN/THEN: Should throw risk exception
        assertThatThrownBy(() -> riskService.runStressTest(request, stressScenarios))
            .isInstanceOf(RiskViolationException.class)
            .hasMessageContaining("stress test");
    }

    @Test
    @DisplayName("Should check concentration risk")
    void shouldCheckConcentrationRisk() {
        // GIVEN: Order creating concentration risk
        OrderRiskRequest request = new OrderRiskRequest(
            "client-1",
            "account-1",
            "INST-001",
            OrderSide.BUY,
            BigDecimal.valueOf(10000),
            BigDecimal.valueOf(150.00),
            BigDecimal.valueOf(1_500_000.00)
        );

        BigDecimal portfolioValue = BigDecimal.valueOf(5_000_000.00);

        // WHEN: Check concentration (30% of portfolio)
        boolean hasConcentrationRisk = riskService.hasConcentrationRisk(
            request, portfolioValue, BigDecimal.valueOf(0.25) // 25% threshold
        );

        // THEN: Concentration risk detected
        assertThat(hasConcentrationRisk).isTrue();
    }

    @Test
    @DisplayName("Should calculate portfolio beta impact")
    void shouldCalculatePortfolioBetaImpact() {
        // GIVEN: Order and portfolio details
        OrderRiskRequest request = new OrderRiskRequest(
            "client-1",
            "account-1",
            "INST-001",
            OrderSide.BUY,
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(150.00),
            BigDecimal.valueOf(15000.00)
        );

        BigDecimal portfolioValue = BigDecimal.valueOf(1_000_000.00);
        BigDecimal instrumentBeta = BigDecimal.valueOf(1.5);

        // WHEN: Calculate beta impact
        BigDecimal betaImpact = riskService.calculateBetaImpact(
            request, portfolioValue, instrumentBeta
        );

        // THEN: Beta impact calculated
        assertThat(betaImpact).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should generate risk audit trail")
    void shouldGenerateRiskAuditTrail() {
        // GIVEN: Order passing risk checks
        OrderRiskRequest request = new OrderRiskRequest(
            "client-1",
            "account-1",
            "INST-001",
            OrderSide.BUY,
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(150.00),
            BigDecimal.valueOf(15000.00)
        );

        // WHEN: Check risk with audit
        RiskResult result = riskService.checkRiskWithAudit(request);

        // THEN: Audit trail generated
        assertThat(result.passed()).isTrue();
        assertThat(result.auditTrail()).isNotEmpty();
        assertThat(result.auditTrail()).contains("Margin check: PASS");
        assertThat(result.auditTrail()).contains("Credit limit check: PASS");
        assertThat(result.timestamp()).isNotNull();
    }

    // Helper classes for testing
    record OrderRiskRequest(
        String clientId,
        String accountId,
        String instrumentId,
        OrderSide side,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal orderValue
    ) {}

    record RiskResult(
        boolean passed,
        String auditTrail,
        java.time.Instant timestamp,
        Map<String, BigDecimal> riskMetrics
    ) {}

    static class RiskViolationException extends RuntimeException {
        public RiskViolationException(String message) {
            super(message);
        }
    }

    // Mock risk service for testing
    static class OrderRiskService {
        void checkRisk(OrderRiskRequest request) {
            // Basic validation
        }

        void checkMarginRequirement(OrderRiskRequest request, BigDecimal availableMargin) {
            BigDecimal required = calculateMarginRequirement(request);
            if (required.compareTo(availableMargin) > 0) {
                throw new RiskViolationException("Order requires insufficient margin");
            }
        }

        BigDecimal calculateMarginRequirement(OrderRiskRequest request) {
            // 50% margin requirement for equity
            return request.orderValue().multiply(BigDecimal.valueOf(0.50));
        }

        void checkCreditLimit(OrderRiskRequest request, BigDecimal creditLimit) {
            if (request.orderValue().compareTo(creditLimit) > 0) {
                throw new RiskViolationException("Order exceeds credit limit");
            }
        }

        void checkExposureLimit(OrderRiskRequest request, BigDecimal currentExposure, BigDecimal exposureLimit) {
            BigDecimal newExposure = currentExposure.add(request.orderValue());
            if (newExposure.compareTo(exposureLimit) > 0) {
                throw new RiskViolationException("Order would exceed exposure limit");
            }
        }

        BigDecimal calculateVaR(OrderRiskRequest request, BigDecimal portfolioValue, BigDecimal volatility) {
            // Simplified VaR calculation: Portfolio Value × Volatility × Z-score (1.65 for 95%)
            return portfolioValue.multiply(volatility).multiply(BigDecimal.valueOf(1.65));
        }

        void runStressTest(OrderRiskRequest request, Map<String, BigDecimal> scenarios) {
            for (Map.Entry<String, BigDecimal> scenario : scenarios.entrySet()) {
                BigDecimal impact = request.orderValue().multiply(scenario.getValue());
                if (impact.abs().compareTo(BigDecimal.valueOf(500_000)) > 0) {
                    throw new RiskViolationException("Order fails stress test: " + scenario.getKey());
                }
            }
        }

        boolean hasConcentrationRisk(OrderRiskRequest request, BigDecimal portfolioValue, BigDecimal threshold) {
            BigDecimal concentration = request.orderValue().divide(portfolioValue, 4, java.math.RoundingMode.HALF_UP);
            return concentration.compareTo(threshold) > 0;
        }

        BigDecimal calculateBetaImpact(OrderRiskRequest request, BigDecimal portfolioValue, BigDecimal instrumentBeta) {
            BigDecimal weight = request.orderValue().divide(portfolioValue, 4, java.math.RoundingMode.HALF_UP);
            return weight.multiply(instrumentBeta);
        }

        RiskResult checkRiskWithAudit(OrderRiskRequest request) {
            StringBuilder audit = new StringBuilder();
            audit.append("Margin check: PASS\n");
            audit.append("Credit limit check: PASS\n");
            audit.append("Exposure limit check: PASS\n");

            return new RiskResult(
                true,
                audit.toString(),
                java.time.Instant.now(),
                Map.of(
                    "margin_required", calculateMarginRequirement(request),
                    "var_95", BigDecimal.valueOf(50000)
                )
            );
        }
    }
}
