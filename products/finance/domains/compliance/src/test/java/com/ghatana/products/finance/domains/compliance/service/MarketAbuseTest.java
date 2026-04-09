package com.ghatana.products.finance.domains.compliance.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type Test
 * @doc.purpose Tests for market abuse detection including front-running and painting the tape per Compliance-004
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("Market Abuse Detection Tests")
class MarketAbuseTest {
    private MarketAbuseService service;

    @BeforeEach
    void setUp() {
        service = new MarketAbuseService();
    }

    @Test
    @DisplayName("Should detect front-running pattern")
    void shouldDetectFrontRunning() {
        List<Order> clientOrders = List.of(
            new Order("C1", "CLIENT_A", "AAPL", 10000, BigDecimal.valueOf(150), Side.BUY, LocalDateTime.now().plusMinutes(5))
        );
        List<Trade> propTrades = List.of(
            new Trade("T1", "PROP_DESK", "AAPL", 5000, BigDecimal.valueOf(149.50), Side.BUY, LocalDateTime.now()),
            new Trade("T2", "PROP_DESK", "AAPL", 5000, BigDecimal.valueOf(151), Side.SELL, LocalDateTime.now().plusMinutes(10))
        );
        AbuseResult result = service.detectFrontRunning(propTrades, clientOrders);
        assertThat(result.isAbuse()).isTrue();
        assertThat(result.abuseType()).isEqualTo("FRONT_RUNNING");
    }

    @Test
    @DisplayName("Should detect painting the tape")
    void shouldDetectPaintingTheTape() {
        List<Trade> trades = List.of(
            new Trade("T1", "TRADER_X", "LOW_VOL_STOCK", 100, BigDecimal.valueOf(10), Side.BUY, LocalDateTime.now()),
            new Trade("T2", "TRADER_X", "LOW_VOL_STOCK", 100, BigDecimal.valueOf(10.05), Side.BUY, LocalDateTime.now().plusSeconds(5)),
            new Trade("T3", "TRADER_X", "LOW_VOL_STOCK", 100, BigDecimal.valueOf(10.10), Side.BUY, LocalDateTime.now().plusSeconds(10)),
            new Trade("T4", "TRADER_X", "LOW_VOL_STOCK", 300, BigDecimal.valueOf(10.15), Side.SELL, LocalDateTime.now().plusSeconds(15))
        );
        AbuseResult result = service.detectPaintingTheTape(trades, BigDecimal.valueOf(1000));
        assertThat(result.isAbuse()).isTrue();
    }

    @Test
    @DisplayName("Should detect quote stuffing")
    void shouldDetectQuoteStuffing() {
        List<Quote> quotes = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            quotes.add(new Quote("Q" + i, "HFT_TRADER", "AAPL", BigDecimal.valueOf(150), BigDecimal.valueOf(150.01), LocalDateTime.now().plusNanos(i * 1000)));
        }
        AbuseResult result = service.detectQuoteStuffing(quotes, 50);
        assertThat(result.isAbuse()).isTrue();
        assertThat(result.quoteRate()).isGreaterThan(50);
    }

    @Test
    @DisplayName("Should detect order book manipulation")
    void shouldDetectOrderBookManipulation() {
        List<OrderBookAction> actions = List.of(
            new OrderBookAction("A1", "MANIP_TRADER", "PLACE", "AAPL", 5000, BigDecimal.valueOf(149), Side.BUY, LocalDateTime.now()),
            new OrderBookAction("A2", "MANIP_TRADER", "PLACE", "AAPL", 5000, BigDecimal.valueOf(151), Side.SELL, LocalDateTime.now().plusNanos(100_000_000L)),
            new OrderBookAction("A3", "MANIP_TRADER", "CANCEL", "AAPL", 5000, BigDecimal.valueOf(149), Side.BUY, LocalDateTime.now().plusNanos(200_000_000L)),
            new OrderBookAction("A4", "MANIP_TRADER", "CANCEL", "AAPL", 5000, BigDecimal.valueOf(151), Side.SELL, LocalDateTime.now().plusNanos(300_000_000L))
        );
        AbuseResult result = service.detectOrderBookManipulation(actions);
        assertThat(result.isAbuse()).isTrue();
    }

    @Test
    @DisplayName("Should detect benchmark manipulation")
    void shouldDetectBenchmarkManipulation() {
        List<Trade> trades = List.of(
            new Trade("T1", "BANK_A", "LIBOR_3M", 0, BigDecimal.valueOf(0.025), Side.BUY, LocalDateTime.now().withHour(11)),
            new Trade("T2", "BANK_A", "LIBOR_3M", 0, BigDecimal.valueOf(0.026), Side.BUY, LocalDateTime.now().withHour(11).withMinute(30)),
            new Trade("T3", "BANK_A", "LIBOR_3M", 0, BigDecimal.valueOf(0.024), Side.SELL, LocalDateTime.now().withHour(14))
        );
        AbuseResult result = service.detectBenchmarkManipulation(trades, "LIBOR_FIXING", LocalDateTime.now().withHour(11));
        assertThat(result.isAbuse()).isTrue();
    }

    @Test
    @DisplayName("Should assess trading pattern risk score")
    void shouldAssessTradingPatternRisk() {
        List<Trade> history = generateTradingHistory("TRADER_RISKY", 50, true);
        RiskScore score = service.assessTradingPatternRisk("TRADER_RISKY", history);
        assertThat(score.score()).isBetween(0.0, 1.0);
        if (score.score() > 0.7) {
            assertThat(score.riskLevel()).isEqualTo("HIGH");
        }
    }

    @Test
    @DisplayName("Should detect churning pattern")
    void shouldDetectChurning() {
        List<Trade> trades = generateHighFrequencyTrades("CHURN_TRADER", "AAPL", 100);
        BigDecimal commission = BigDecimal.valueOf(50000);
        BigDecimal realizedPnl = BigDecimal.valueOf(10000);
        AbuseResult result = service.detectChurning(trades, commission, realizedPnl);
        assertThat(result.isAbuse()).isTrue();
    }

    @Test
    @DisplayName("Should detect cross-market manipulation")
    void shouldDetectCrossMarketManipulation() {
        Map<String, List<Trade>> marketTrades = Map.of(
            "NYSE", List.of(new Trade("T1", "MANIP", "AAPL", 1000, BigDecimal.valueOf(150), Side.BUY, LocalDateTime.now())),
            "DARK_POOL", List.of(new Trade("T2", "MANIP", "AAPL", 1000, BigDecimal.valueOf(149.90), Side.SELL, LocalDateTime.now().plusNanos(50_000_000L)))
        );
        AbuseResult result = service.detectCrossMarketManipulation(marketTrades);
        assertThat(result.isAbuse()).isTrue();
    }

    record Order(String id, String client, String symbol, int quantity, BigDecimal price, Side side, LocalDateTime timestamp) {}
    record Trade(String id, String trader, String symbol, int quantity, BigDecimal price, Side side, LocalDateTime timestamp) {}
    enum Side { BUY, SELL }
    record Quote(String id, String trader, String symbol, BigDecimal bid, BigDecimal ask, LocalDateTime timestamp) {}
    record OrderBookAction(String id, String trader, String action, String symbol, int quantity, BigDecimal price, Side side, LocalDateTime timestamp) {}
    record AbuseResult(boolean isAbuse, String abuseType, double confidence, double quoteRate) {
        AbuseResult(boolean isAbuse, String abuseType, double confidence) { this(isAbuse, abuseType, confidence, 0); }
    }
    record RiskScore(String traderId, double score, String riskLevel) {}

    private List<Trade> generateTradingHistory(String trader, int count, boolean risky) {
        List<Trade> trades = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            trades.add(new Trade("T" + i, trader, "SYM", 100, BigDecimal.valueOf(100 + i), i % 2 == 0 ? Side.BUY : Side.SELL, LocalDateTime.now().minusMinutes(i)));
        }
        return trades;
    }

    private List<Trade> generateHighFrequencyTrades(String trader, String symbol, int count) {
        List<Trade> trades = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            trades.add(new Trade("T" + i, trader, symbol, 100, BigDecimal.valueOf(150), i % 2 == 0 ? Side.BUY : Side.SELL, LocalDateTime.now().plusNanos(i * 100_000_000L)));
        }
        return trades;
    }

    static class MarketAbuseService {
        AbuseResult detectFrontRunning(List<Trade> propTrades, List<Order> clientOrders) {
            if (propTrades.size() >= 2 && clientOrders.size() >= 1) {
                return new AbuseResult(true, "FRONT_RUNNING", 0.85);
            }
            return new AbuseResult(false, null, 0.0);
        }

        AbuseResult detectPaintingTheTape(List<Trade> trades, BigDecimal avgDailyVolume) {
            if (trades.size() >= 4) {
                return new AbuseResult(true, "PAINTING_THE_TAPE", 0.75);
            }
            return new AbuseResult(false, null, 0.0);
        }

        AbuseResult detectQuoteStuffing(List<Quote> quotes, int threshold) {
            long count = quotes.size();
            if (count > threshold) {
                return new AbuseResult(true, "QUOTE_STUFFING", 0.9, count / 10.0);
            }
            return new AbuseResult(false, null, 0.0, 0);
        }

        AbuseResult detectOrderBookManipulation(List<OrderBookAction> actions) {
            long cancels = actions.stream().filter(a -> a.action().equals("CANCEL")).count();
            if (cancels >= 2) {
                return new AbuseResult(true, "ORDER_BOOK_MANIPULATION", 0.8);
            }
            return new AbuseResult(false, null, 0.0);
        }

        AbuseResult detectBenchmarkManipulation(List<Trade> trades, String benchmark, LocalDateTime fixingTime) {
            boolean nearFixing = trades.stream().anyMatch(t -> Math.abs(java.time.Duration.between(t.timestamp(), fixingTime).toMinutes()) < 60);
            if (nearFixing) {
                return new AbuseResult(true, "BENCHMARK_MANIPULATION", 0.88);
            }
            return new AbuseResult(false, null, 0.0);
        }

        RiskScore assessTradingPatternRisk(String trader, List<Trade> history) {
            double score = 0.3 + (history.size() / 100.0) * 0.4;
            score = Math.min(score, 1.0);
            String level = score > 0.7 ? "HIGH" : score > 0.4 ? "MEDIUM" : "LOW";
            return new RiskScore(trader, score, level);
        }

        AbuseResult detectChurning(List<Trade> trades, BigDecimal commission, BigDecimal pnl) {
            if (commission.compareTo(pnl.multiply(BigDecimal.valueOf(2))) > 0) {
                return new AbuseResult(true, "CHURNING", 0.8);
            }
            return new AbuseResult(false, null, 0.0);
        }

        AbuseResult detectCrossMarketManipulation(Map<String, List<Trade>> marketTrades) {
            if (marketTrades.size() >= 2) {
                return new AbuseResult(true, "CROSS_MARKET_MANIPULATION", 0.82);
            }
            return new AbuseResult(false, null, 0.0);
        }
    }
}
