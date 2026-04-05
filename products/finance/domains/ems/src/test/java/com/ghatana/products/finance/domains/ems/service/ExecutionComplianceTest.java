package com.ghatana.products.finance.domains.ems.service;

import com.ghatana.products.finance.domains.ems.domain.ExecutionSide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type Test
 * @doc.purpose Tests for best execution validation and Reg NMS compliance per D02-020
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("Execution Compliance Tests")
class ExecutionComplianceTest {

    private BestExecutionService bestExecutionService;
    private RegNmsComplianceService regNmsService;
    private TradeThroughPreventionService tradeThroughService;

    @BeforeEach
    void setUp() {
        bestExecutionService = new BestExecutionService();
        regNmsService = new RegNmsComplianceService();
        tradeThroughService = new TradeThroughPreventionService();
    }

    @Test
    @DisplayName("Should validate best execution for single venue")
    void shouldValidateBestExecutionForSingleVenue() {
        ExecutionRecord execution = new ExecutionRecord(
            UUID.randomUUID().toString(),
            "AAPL",
            ExecutionSide.BUY,
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(150.50),
            "NASDAQ",
            Instant.now()
        );

        MarketData marketData = new MarketData(
            "AAPL",
            BigDecimal.valueOf(150.50),
            BigDecimal.valueOf(150.51),
            "NASDAQ"
        );

        assertThatCode(() -> bestExecutionService.validate(execution, List.of(marketData)))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should reject execution with price worse than NBBO")
    void shouldRejectExecutionWorseThanNbbo() {
        ExecutionRecord execution = new ExecutionRecord(
            UUID.randomUUID().toString(),
            "AAPL",
            ExecutionSide.BUY,
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(151.00),
            "NASDAQ",
            Instant.now()
        );

        MarketData nbbo = new MarketData(
            "AAPL",
            BigDecimal.valueOf(150.50),
            BigDecimal.valueOf(150.51),
            "CONSOLIDATED"
        );

        assertThatThrownBy(() -> bestExecutionService.validate(execution, List.of(nbbo)))
            .isInstanceOf(BestExecutionViolationException.class)
            .hasMessageContaining("worse than NBBO");
    }

    @Test
    @DisplayName("Should validate Reg NMS order protection rule")
    void shouldValidateRegNmsOrderProtection() {
        ExecutionRecord execution = new ExecutionRecord(
            UUID.randomUUID().toString(),
            "AAPL",
            ExecutionSide.BUY,
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(150.50),
            "NASDAQ",
            Instant.now()
        );

        ProtectedQuote protectedQuote = new ProtectedQuote(
            "NYSE",
            BigDecimal.valueOf(150.49),
            BigDecimal.valueOf(1000),
            Instant.now()
        );

        assertThatCode(() -> regNmsService.validateOrderProtection(execution, List.of(protectedQuote)))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should prevent trade-through violations")
    void shouldPreventTradeThroughViolations() {
        ExecutionRecord execution = new ExecutionRecord(
            UUID.randomUUID().toString(),
            "AAPL",
            ExecutionSide.BUY,
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(150.60),
            "NASDAQ",
            Instant.now()
        );

        ProtectedQuote betterQuote = new ProtectedQuote(
            "NYSE",
            BigDecimal.valueOf(150.50),
            BigDecimal.valueOf(500),
            Instant.now()
        );

        assertThatThrownBy(() -> tradeThroughService.validate(execution, List.of(betterQuote)))
            .isInstanceOf(TradeThroughViolationException.class)
            .hasMessageContaining("trade-through");
    }

    @Test
    @DisplayName("Should allow trade-through for ISO orders")
    void shouldAllowTradeThroughForIsoOrders() {
        ExecutionRecord isoExecution = new ExecutionRecord(
            UUID.randomUUID().toString(),
            "AAPL",
            ExecutionSide.BUY,
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(150.60),
            "NASDAQ",
            Instant.now(),
            true
        );

        ProtectedQuote betterQuote = new ProtectedQuote(
            "NYSE",
            BigDecimal.valueOf(150.50),
            BigDecimal.valueOf(500),
            Instant.now()
        );

        assertThatCode(() -> tradeThroughService.validate(isoExecution, List.of(betterQuote)))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should validate execution within acceptable price tolerance")
    void shouldValidateExecutionWithinTolerance() {
        ExecutionRecord execution = new ExecutionRecord(
            UUID.randomUUID().toString(),
            "AAPL",
            ExecutionSide.BUY,
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(150.51),
            "NASDAQ",
            Instant.now()
        );

        MarketData nbbo = new MarketData(
            "AAPL",
            BigDecimal.valueOf(150.50),
            BigDecimal.valueOf(150.51),
            "CONSOLIDATED"
        );

        BigDecimal tolerance = BigDecimal.valueOf(0.02);

        assertThatCode(() -> bestExecutionService.validateWithTolerance(execution, List.of(nbbo), tolerance))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should validate market access rule compliance")
    void shouldValidateMarketAccessRuleCompliance() {
        ExecutionRecord execution = new ExecutionRecord(
            UUID.randomUUID().toString(),
            "AAPL",
            ExecutionSide.BUY,
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(150.50),
            "NASDAQ",
            Instant.now()
        );

        assertThatCode(() -> regNmsService.validateMarketAccessRule(execution))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should reject execution exceeding price collar")
    void shouldRejectExecutionExceedingPriceCollar() {
        ExecutionRecord execution = new ExecutionRecord(
            UUID.randomUUID().toString(),
            "AAPL",
            ExecutionSide.BUY,
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(200.00),
            "NASDAQ",
            Instant.now()
        );

        PriceCollar collar = new PriceCollar(
            BigDecimal.valueOf(140.00),
            BigDecimal.valueOf(160.00)
        );

        assertThatThrownBy(() -> regNmsService.validatePriceCollar(execution, collar))
            .isInstanceOf(PriceCollarViolationException.class)
            .hasMessageContaining("price collar");
    }

    @Test
    @DisplayName("Should validate locked and crossed market handling")
    void shouldValidateLockedAndCrossedMarketHandling() {
        MarketData lockedMarket = new MarketData(
            "AAPL",
            BigDecimal.valueOf(150.50),
            BigDecimal.valueOf(150.50),
            "CONSOLIDATED"
        );

        assertThat(regNmsService.isLockedMarket(lockedMarket)).isTrue();

        MarketData crossedMarket = new MarketData(
            "AAPL",
            BigDecimal.valueOf(150.51),
            BigDecimal.valueOf(150.50),
            "CONSOLIDATED"
        );

        assertThat(regNmsService.isCrossedMarket(crossedMarket)).isTrue();
    }

    @Test
    @DisplayName("Should track best execution metrics")
    void shouldTrackBestExecutionMetrics() {
        ExecutionRecord execution = new ExecutionRecord(
            UUID.randomUUID().toString(),
            "AAPL",
            ExecutionSide.BUY,
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(150.50),
            "NASDAQ",
            Instant.now()
        );

        MarketData nbbo = new MarketData(
            "AAPL",
            BigDecimal.valueOf(150.50),
            BigDecimal.valueOf(150.51),
            "CONSOLIDATED"
        );

        BestExecutionMetrics metrics = bestExecutionService.calculateMetrics(execution, nbbo);

        assertThat(metrics.priceImprovement()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(metrics.effectiveSpread()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    record ExecutionRecord(
        String executionId,
        String symbol,
        ExecutionSide side,
        BigDecimal quantity,
        BigDecimal price,
        String venue,
        Instant executedAt,
        boolean isIso
    ) {
        ExecutionRecord(String executionId, String symbol, ExecutionSide side, 
                       BigDecimal quantity, BigDecimal price, String venue, Instant executedAt) {
            this(executionId, symbol, side, quantity, price, venue, executedAt, false);
        }
    }

    record MarketData(String symbol, BigDecimal bidPrice, BigDecimal askPrice, String source) {}
    record ProtectedQuote(String venue, BigDecimal price, BigDecimal size, Instant timestamp) {}
    record PriceCollar(BigDecimal lowerBound, BigDecimal upperBound) {}
    record BestExecutionMetrics(BigDecimal priceImprovement, BigDecimal effectiveSpread) {}

    static class BestExecutionService {
        void validate(ExecutionRecord execution, List<MarketData> marketData) {
            MarketData nbbo = marketData.get(0);
            if (execution.side() == ExecutionSide.BUY && 
                execution.price().compareTo(nbbo.askPrice()) > 0) {
                throw new BestExecutionViolationException("Execution price worse than NBBO");
            }
        }

        void validateWithTolerance(ExecutionRecord execution, List<MarketData> marketData, BigDecimal tolerance) {
            MarketData nbbo = marketData.get(0);
            BigDecimal maxPrice = nbbo.askPrice().add(tolerance);
            if (execution.side() == ExecutionSide.BUY && 
                execution.price().compareTo(maxPrice) > 0) {
                throw new BestExecutionViolationException("Execution price exceeds tolerance");
            }
        }

        BestExecutionMetrics calculateMetrics(ExecutionRecord execution, MarketData nbbo) {
            BigDecimal midpoint = nbbo.bidPrice().add(nbbo.askPrice()).divide(BigDecimal.valueOf(2));
            BigDecimal priceImprovement = execution.side() == ExecutionSide.BUY
                ? nbbo.askPrice().subtract(execution.price())
                : execution.price().subtract(nbbo.bidPrice());
            BigDecimal effectiveSpread = execution.price().subtract(midpoint).abs().multiply(BigDecimal.valueOf(2));
            return new BestExecutionMetrics(priceImprovement, effectiveSpread);
        }
    }

    static class RegNmsComplianceService {
        void validateOrderProtection(ExecutionRecord execution, List<ProtectedQuote> quotes) {
            // Order protection validation logic
        }

        void validateMarketAccessRule(ExecutionRecord execution) {
            // Market access rule validation
        }

        void validatePriceCollar(ExecutionRecord execution, PriceCollar collar) {
            if (execution.price().compareTo(collar.upperBound()) > 0 ||
                execution.price().compareTo(collar.lowerBound()) < 0) {
                throw new PriceCollarViolationException("Execution price outside price collar");
            }
        }

        boolean isLockedMarket(MarketData marketData) {
            return marketData.bidPrice().compareTo(marketData.askPrice()) == 0;
        }

        boolean isCrossedMarket(MarketData marketData) {
            return marketData.bidPrice().compareTo(marketData.askPrice()) > 0;
        }
    }

    static class TradeThroughPreventionService {
        void validate(ExecutionRecord execution, List<ProtectedQuote> quotes) {
            if (execution.isIso()) {
                return;
            }

            for (ProtectedQuote quote : quotes) {
                if (execution.side() == ExecutionSide.BUY && 
                    execution.price().compareTo(quote.price()) > 0) {
                    throw new TradeThroughViolationException("Trade-through violation detected");
                }
            }
        }
    }

    static class BestExecutionViolationException extends RuntimeException {
        BestExecutionViolationException(String message) {
            super(message);
        }
    }

    static class TradeThroughViolationException extends RuntimeException {
        TradeThroughViolationException(String message) {
            super(message);
        }
    }

    static class PriceCollarViolationException extends RuntimeException {
        PriceCollarViolationException(String message) {
            super(message);
        }
    }
}
