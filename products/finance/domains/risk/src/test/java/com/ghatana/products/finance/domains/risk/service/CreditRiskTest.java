package com.ghatana.products.finance.domains.risk.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type Test
 * @doc.purpose Tests for credit risk calculations including counterparty exposure and margin per Risk-003
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("Credit Risk Tests")
class CreditRiskTest {
    private CreditRiskService service;

    @BeforeEach
    void setUp() {
        service = new CreditRiskService();
    }

    @Test
    @DisplayName("Should calculate counterparty exposure")
    void shouldCalculateCounterpartyExposure() {
        List<Trade> trades = List.of(
            new Trade("T1", "CPTY_A", BigDecimal.valueOf(100000), BigDecimal.valueOf(0.95)),
            new Trade("T2", "CPTY_A", BigDecimal.valueOf(50000), BigDecimal.valueOf(0.98))
        );
        BigDecimal exposure = service.calculateCounterpartyExposure("CPTY_A", trades);
        assertThat(exposure).isEqualByComparingTo(BigDecimal.valueOf(1.93));
    }

    @Test
    @DisplayName("Should calculate potential future exposure (PFE)")
    void shouldCalculatePotentialFutureExposure() {
        BigDecimal currentExposure = BigDecimal.valueOf(1000000);
        BigDecimal volatility = BigDecimal.valueOf(0.15);
        int timeHorizon = 252;
        BigDecimal pfe = service.calculatePFE(currentExposure, volatility, timeHorizon, 0.99);
        assertThat(pfe).isGreaterThan(currentExposure);
    }

    @Test
    @DisplayName("Should calculate credit valuation adjustment (CVA)")
    void shouldCalculateCva() {
        BigDecimal exposure = BigDecimal.valueOf(1000000);
        BigDecimal pd = BigDecimal.valueOf(0.02);
        BigDecimal lgd = BigDecimal.valueOf(0.6);
        BigDecimal cva = service.calculateCVA(exposure, pd, lgd);
        assertThat(cva).isPositive();
        assertThat(cva).isLessThan(exposure.multiply(lgd).multiply(pd).multiply(BigDecimal.valueOf(5)));
    }

    @Test
    @DisplayName("Should calculate initial margin for derivatives")
    void shouldCalculateInitialMargin() {
        DerivativeTrade trade = new DerivativeTrade("IRS_1", "InterestRateSwap", BigDecimal.valueOf(10000000), BigDecimal.valueOf(0.15));
        BigDecimal margin = service.calculateInitialMargin(trade, MarginMethod.STANDARDIZED);
        assertThat(margin).isPositive();
        assertThat(margin).isLessThan(trade.notional().multiply(BigDecimal.valueOf(0.3)));
    }

    @Test
    @DisplayName("Should calculate variation margin")
    void shouldCalculateVariationMargin() {
        BigDecimal currentMtM = BigDecimal.valueOf(500000);
        BigDecimal previousMtM = BigDecimal.valueOf(450000);
        BigDecimal vm = service.calculateVariationMargin(currentMtM, previousMtM);
        assertThat(vm).isEqualByComparingTo(BigDecimal.valueOf(50000));
    }

    @Test
    @DisplayName("Should check margin sufficiency")
    void shouldCheckMarginSufficiency() {
        BigDecimal requiredMargin = BigDecimal.valueOf(1000000);
        BigDecimal postedMargin = BigDecimal.valueOf(1200000);
        MarginCheckResult result = service.checkMarginSufficiency(requiredMargin, postedMargin);
        assertThat(result.isSufficient()).isTrue();
        assertThat(result.shortfall()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should detect margin shortfall")
    void shouldDetectMarginShortfall() {
        BigDecimal requiredMargin = BigDecimal.valueOf(1000000);
        BigDecimal postedMargin = BigDecimal.valueOf(900000);
        MarginCheckResult result = service.checkMarginSufficiency(requiredMargin, postedMargin);
        assertThat(result.isSufficient()).isFalse();
        assertThat(result.shortfall()).isEqualByComparingTo(BigDecimal.valueOf(100000));
    }

    @Test
    @DisplayName("Should calculate collateral requirements")
    void shouldCalculateCollateralRequirements() {
        BigDecimal exposure = BigDecimal.valueOf(2000000);
        BigDecimal threshold = BigDecimal.valueOf(500000);
        BigDecimal minimumTransfer = BigDecimal.valueOf(100000);
        BigDecimal collateral = service.calculateCollateralRequirement(exposure, threshold, minimumTransfer);
        assertThat(collateral).isEqualByComparingTo(BigDecimal.valueOf(1500000));
    }

    @Test
    @DisplayName("Should calculate wrong-way risk")
    void shouldCalculateWrongWayRisk() {
        BigDecimal baseExposure = BigDecimal.valueOf(1000000);
        BigDecimal correlation = BigDecimal.valueOf(0.7);
        BigDecimal wwrExposure = service.calculateWrongWayRisk(baseExposure, correlation);
        assertThat(wwrExposure).isGreaterThan(baseExposure);
    }

    @Test
    @DisplayName("Should aggregate credit risk across counterparties")
    void shouldAggregateCreditRisk() {
        Map<String, BigDecimal> exposures = Map.of(
            "CPTY_A", BigDecimal.valueOf(1000000),
            "CPTY_B", BigDecimal.valueOf(2000000),
            "CPTY_C", BigDecimal.valueOf(500000)
        );
        Map<String, BigDecimal> pds = Map.of(
            "CPTY_A", BigDecimal.valueOf(0.01),
            "CPTY_B", BigDecimal.valueOf(0.02),
            "CPTY_C", BigDecimal.valueOf(0.015)
        );
        BigDecimal totalCreditRisk = service.aggregateCreditRisk(exposures, pds, BigDecimal.valueOf(0.6));
        assertThat(totalCreditRisk).isPositive();
    }

    record Trade(String id, String counterparty, BigDecimal notional, BigDecimal marketValue) {}
    record DerivativeTrade(String id, String type, BigDecimal notional, BigDecimal volatility) {}
    record MarginCheckResult(boolean isSufficient, BigDecimal shortfall) {}
    enum MarginMethod { STANDARDIZED, SIMM, CEM }

    static class CreditRiskService {
        BigDecimal calculateCounterpartyExposure(String counterparty, List<Trade> trades) {
            return trades.stream()
                .filter(t -> t.counterparty().equals(counterparty))
                .map(Trade::marketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        BigDecimal calculatePFE(BigDecimal currentExposure, BigDecimal volatility, int timeHorizon, double confidence) {
            double multiplier = 1.645;
            if (confidence >= 0.99) multiplier = 2.326;
            return currentExposure.multiply(BigDecimal.valueOf(1 + multiplier * volatility.doubleValue() * Math.sqrt(timeHorizon / 252.0)));
        }

        BigDecimal calculateCVA(BigDecimal exposure, BigDecimal pd, BigDecimal lgd) {
            return exposure.multiply(pd).multiply(lgd).multiply(BigDecimal.valueOf(0.5));
        }

        BigDecimal calculateInitialMargin(DerivativeTrade trade, MarginMethod method) {
            if (method == MarginMethod.STANDARDIZED) {
                return trade.notional().multiply(trade.volatility()).multiply(BigDecimal.valueOf(0.15));
            }
            return trade.notional().multiply(BigDecimal.valueOf(0.1));
        }

        BigDecimal calculateVariationMargin(BigDecimal currentMtM, BigDecimal previousMtM) {
            return currentMtM.subtract(previousMtM);
        }

        MarginCheckResult checkMarginSufficiency(BigDecimal required, BigDecimal posted) {
            if (posted.compareTo(required) >= 0) {
                return new MarginCheckResult(true, BigDecimal.ZERO);
            }
            return new MarginCheckResult(false, required.subtract(posted));
        }

        BigDecimal calculateCollateralRequirement(BigDecimal exposure, BigDecimal threshold, BigDecimal minimumTransfer) {
            BigDecimal required = exposure.subtract(threshold);
            return required.compareTo(minimumTransfer) >= 0 ? required : BigDecimal.ZERO;
        }

        BigDecimal calculateWrongWayRisk(BigDecimal baseExposure, BigDecimal correlation) {
            return baseExposure.multiply(BigDecimal.valueOf(1 + correlation.doubleValue() * 0.5));
        }

        BigDecimal aggregateCreditRisk(Map<String, BigDecimal> exposures, Map<String, BigDecimal> pds, BigDecimal lgd) {
            BigDecimal total = BigDecimal.ZERO;
            for (String cpty : exposures.keySet()) {
                BigDecimal exposure = exposures.get(cpty);
                BigDecimal pd = pds.getOrDefault(cpty, BigDecimal.valueOf(0.01));
                total = total.add(exposure.multiply(pd).multiply(lgd));
            }
            return total;
        }
    }
}
