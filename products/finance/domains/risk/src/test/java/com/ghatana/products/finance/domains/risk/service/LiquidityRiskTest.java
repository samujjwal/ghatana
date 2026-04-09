package com.ghatana.products.finance.domains.risk.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type Test
 * @doc.purpose Tests for liquidity risk calculations per Risk-006
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("Liquidity Risk Tests")
class LiquidityRiskTest {
    private LiquidityRiskService service;

    @BeforeEach
    void setUp() {
        service = new LiquidityRiskService();
    }

    @Test
    @DisplayName("Should calculate liquidity coverage ratio (LCR)")
    void shouldCalculateLcr() {
        BigDecimal hqla = BigDecimal.valueOf(100000000);
        BigDecimal netCashOutflows = BigDecimal.valueOf(80000000);
        BigDecimal lcr = service.calculateLcr(hqla, netCashOutflows);
        assertThat(lcr).isEqualByComparingTo(BigDecimal.valueOf(1.25));
        assertThat(lcr).isGreaterThanOrEqualTo(BigDecimal.ONE);
    }

    @Test
    @DisplayName("Should calculate net stable funding ratio (NSFR)")
    void shouldCalculateNsfr() {
        BigDecimal availableStableFunding = BigDecimal.valueOf(150000000);
        BigDecimal requiredStableFunding = BigDecimal.valueOf(120000000);
        BigDecimal nsfr = service.calculateNsfr(availableStableFunding, requiredStableFunding);
        assertThat(nsfr).isGreaterThanOrEqualTo(BigDecimal.valueOf(1.0));
    }

    @Test
    @DisplayName("Should calculate liquidity gap")
    void shouldCalculateLiquidityGap() {
        Map<Integer, BigDecimal> inflows = Map.of(
            1, BigDecimal.valueOf(50000000),
            7, BigDecimal.valueOf(30000000),
            30, BigDecimal.valueOf(80000000)
        );
        Map<Integer, BigDecimal> outflows = Map.of(
            1, BigDecimal.valueOf(40000000),
            7, BigDecimal.valueOf(35000000),
            30, BigDecimal.valueOf(70000000)
        );
        Map<Integer, BigDecimal> gaps = service.calculateLiquidityGap(inflows, outflows);
        assertThat(gaps.get(1)).isPositive();
    }

    @Test
    @DisplayName("Should assess asset liquidity")
    void shouldAssessAssetLiquidity() {
        List<Asset> assets = List.of(
            new Asset("Cash", BigDecimal.valueOf(50000000), LiquidityLevel.HIGH, BigDecimal.ZERO),
            new Asset("GovtBonds", BigDecimal.valueOf(80000000), LiquidityLevel.HIGH, BigDecimal.valueOf(0.02)),
            new Asset("CorporateBonds", BigDecimal.valueOf(60000000), LiquidityLevel.MEDIUM, BigDecimal.valueOf(0.05)),
            new Asset("Equities", BigDecimal.valueOf(40000000), LiquidityLevel.MEDIUM, BigDecimal.valueOf(0.08)),
            new Asset("RealEstate", BigDecimal.valueOf(30000000), LiquidityLevel.LOW, BigDecimal.valueOf(0.20))
        );
        LiquidityAssessment assessment = service.assessAssetLiquidity(assets);
        assertThat(assessment.weightedLiquidityScore()).isBetween(BigDecimal.ZERO, BigDecimal.ONE);
    }

    @Test
    @DisplayName("Should calculate funding concentration")
    void shouldCalculateFundingConcentration() {
        Map<String, BigDecimal> fundingSources = Map.of(
            "RetailDeposits", BigDecimal.valueOf(100000000),
            "WholesaleFunding", BigDecimal.valueOf(60000000),
            "Interbank", BigDecimal.valueOf(40000000),
            "Repo", BigDecimal.valueOf(50000000)
        );
        FundingConcentrationResult result = service.calculateFundingConcentration(fundingSources);
        assertThat(result.concentrationRatio()).isBetween(BigDecimal.ZERO, BigDecimal.ONE);
        assertThat(result.diversificationIndex()).isPositive();
    }

    @Test
    @DisplayName("Should calculate contingent liquidity need")
    void shouldCalculateContingentLiquidityNeed() {
        List<ContingentExposure> exposures = List.of(
            new ContingentExposure("CreditLine", BigDecimal.valueOf(50000000), BigDecimal.valueOf(0.3)),
            new ContingentExposure("Guarantees", BigDecimal.valueOf(30000000), BigDecimal.valueOf(0.1)),
            new ContingentExposure("LettersOfCredit", BigDecimal.valueOf(20000000), BigDecimal.valueOf(0.5))
        );
        BigDecimal contingentNeed = service.calculateContingentLiquidityNeed(exposures);
        assertThat(contingentNeed).isPositive();
    }

    @Test
    @DisplayName("Should stress test liquidity under adverse conditions")
    void shouldStressTestLiquidity() {
        LiquidityPosition position = new LiquidityPosition(
            BigDecimal.valueOf(100000000),
            BigDecimal.valueOf(80000000),
            BigDecimal.valueOf(20000000)
        );
        LiquidityStressScenario scenario = new LiquidityStressScenario(
            "DEPOSIT_RUN",
            BigDecimal.valueOf(0.3),
            BigDecimal.valueOf(0.5)
        );
        LiquidityStressResult result = service.stressTestLiquidity(position, scenario);
        assertThat(result.survivalDays()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should calculate cost to close liquidity gap")
    void shouldCalculateCostToClose() {
        BigDecimal gap = BigDecimal.valueOf(50000000);
        BigDecimal fundingCost = BigDecimal.valueOf(0.05);
        BigDecimal cost = service.calculateCostToClose(gap, fundingCost, 30);
        assertThat(cost).isPositive();
        assertThat(cost).isLessThan(gap.multiply(fundingCost));
    }

    @Test
    @DisplayName("Should monitor intraday liquidity position")
    void shouldMonitorIntradayLiquidity() {
        List<IntradayPosition> positions = List.of(
            new IntradayPosition("09:00", BigDecimal.valueOf(50000000)),
            new IntradayPosition("12:00", BigDecimal.valueOf(35000000)),
            new IntradayPosition("15:00", BigDecimal.valueOf(60000000))
        );
        IntradayLiquidityProfile profile = service.analyzeIntradayLiquidity(positions);
        assertThat(profile.minimumPosition()).isEqualByComparingTo(BigDecimal.valueOf(35000000));
        assertThat(profile.peakRequirement()).isPositive();
    }

    record Asset(String name, BigDecimal value, LiquidityLevel liquidity, BigDecimal haircut) {}
    record LiquidityAssessment(BigDecimal weightedLiquidityScore, Map<LiquidityLevel, BigDecimal> byCategory) {}
    record FundingConcentrationResult(BigDecimal concentrationRatio, BigDecimal diversificationIndex) {}
    record ContingentExposure(String type, BigDecimal amount, BigDecimal drawProbability) {}
    record LiquidityPosition(BigDecimal liquidAssets, BigDecimal shortTermLiabilities, BigDecimal buffer) {}
    record LiquidityStressScenario(String name, BigDecimal depositRunoff, BigDecimal wholesaleFreeze) {}
    record LiquidityStressResult(int survivalDays, BigDecimal stressedOutflows, boolean survives) {}
    record IntradayPosition(String time, BigDecimal availableLiquidity) {}
    record IntradayLiquidityProfile(BigDecimal minimumPosition, BigDecimal peakRequirement, BigDecimal averagePosition) {}
    enum LiquidityLevel { HIGH, MEDIUM, LOW }

    static class LiquidityRiskService {
        BigDecimal calculateLcr(BigDecimal hqla, BigDecimal netCashOutflows) {
            return hqla.divide(netCashOutflows, 4, java.math.RoundingMode.HALF_UP);
        }

        BigDecimal calculateNsfr(BigDecimal availableStableFunding, BigDecimal requiredStableFunding) {
            return availableStableFunding.divide(requiredStableFunding, 4, java.math.RoundingMode.HALF_UP);
        }

        Map<Integer, BigDecimal> calculateLiquidityGap(Map<Integer, BigDecimal> inflows, Map<Integer, BigDecimal> outflows) {
            Map<Integer, BigDecimal> gaps = new HashMap<>();
            for (Integer bucket : inflows.keySet()) {
                BigDecimal in = inflows.getOrDefault(bucket, BigDecimal.ZERO);
                BigDecimal out = outflows.getOrDefault(bucket, BigDecimal.ZERO);
                gaps.put(bucket, in.subtract(out));
            }
            return gaps;
        }

        LiquidityAssessment assessAssetLiquidity(List<Asset> assets) {
            BigDecimal total = assets.stream().map(Asset::value).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal weighted = BigDecimal.ZERO;
            Map<LiquidityLevel, BigDecimal> byCategory = new HashMap<>();
            for (Asset asset : assets) {
                BigDecimal weight = asset.value().divide(total, 4, java.math.RoundingMode.HALF_UP);
                int liquidityScore = asset.liquidity() == LiquidityLevel.HIGH ? 3 : asset.liquidity() == LiquidityLevel.MEDIUM ? 2 : 1;
                weighted = weighted.add(weight.multiply(BigDecimal.valueOf(liquidityScore)));
                byCategory.merge(asset.liquidity(), asset.value(), BigDecimal::add);
            }
            return new LiquidityAssessment(weighted.divide(BigDecimal.valueOf(3), 4, java.math.RoundingMode.HALF_UP), byCategory);
        }

        FundingConcentrationResult calculateFundingConcentration(Map<String, BigDecimal> fundingSources) {
            BigDecimal total = fundingSources.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal hhi = BigDecimal.ZERO;
            for (BigDecimal amount : fundingSources.values()) {
                BigDecimal share = amount.divide(total, 4, java.math.RoundingMode.HALF_UP);
                hhi = hhi.add(share.multiply(share));
            }
            return new FundingConcentrationResult(hhi, BigDecimal.ONE.divide(hhi, 2, java.math.RoundingMode.HALF_UP));
        }

        BigDecimal calculateContingentLiquidityNeed(List<ContingentExposure> exposures) {
            return exposures.stream()
                .map(e -> e.amount().multiply(e.drawProbability()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        LiquidityStressResult stressTestLiquidity(LiquidityPosition position, LiquidityStressScenario scenario) {
            BigDecimal stressedDeposits = position.shortTermLiabilities().multiply(scenario.depositRunoff());
            BigDecimal stressedWholesale = position.shortTermLiabilities().multiply(scenario.wholesaleFreeze());
            BigDecimal totalOutflows = stressedDeposits.add(stressedWholesale);
            BigDecimal available = position.liquidAssets().add(position.buffer());
            int survivalDays = available.divide(totalOutflows.divide(BigDecimal.valueOf(30), 2, java.math.RoundingMode.HALF_UP), 0, java.math.RoundingMode.DOWN).intValue();
            return new LiquidityStressResult(survivalDays, totalOutflows, survivalDays >= 30);
        }

        BigDecimal calculateCostToClose(BigDecimal gap, BigDecimal fundingCost, int days) {
            return gap.multiply(fundingCost).multiply(BigDecimal.valueOf(days)).divide(BigDecimal.valueOf(360), 2, java.math.RoundingMode.HALF_UP);
        }

        IntradayLiquidityProfile analyzeIntradayLiquidity(List<IntradayPosition> positions) {
            BigDecimal min = positions.stream().map(IntradayPosition::availableLiquidity).min(java.util.Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
            BigDecimal max = positions.stream().map(IntradayPosition::availableLiquidity).max(java.util.Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
            BigDecimal avg = positions.stream().map(IntradayPosition::availableLiquidity).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(positions.size()), 2, java.math.RoundingMode.HALF_UP);
            return new IntradayLiquidityProfile(min, max, avg);
        }
    }
}
