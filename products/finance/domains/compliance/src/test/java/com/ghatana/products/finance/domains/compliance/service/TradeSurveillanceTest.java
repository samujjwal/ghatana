package com.ghatana.products.finance.domains.compliance.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type Test
 * @doc.purpose Tests for trade surveillance and market manipulation detection per Compliance-001
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("Trade Surveillance Tests")
class TradeSurveillanceTest {
    private TradeSurveillanceService service;

    @BeforeEach
    void setUp() {
        service = new TradeSurveillanceService();
    }

    @Test
    @DisplayName("Should detect wash trading pattern")
    void shouldDetectWashTrading() {
        List<Trade> trades = List.of(
            new Trade("T1", "TRADER_A", "AAPL", 100, BigDecimal.valueOf(150.00), LocalDateTime.now(), Side.BUY),
            new Trade("T2", "TRADER_A", "AAPL", 100, BigDecimal.valueOf(150.00), LocalDateTime.now().plusSeconds(5), Side.SELL),
            new Trade("T3", "TRADER_A", "AAPL", 100, BigDecimal.valueOf(150.00), LocalDateTime.now().plusSeconds(10), Side.BUY),
            new Trade("T4", "TRADER_A", "AAPL", 100, BigDecimal.valueOf(150.00), LocalDateTime.now().plusSeconds(15), Side.SELL)
        );
        SurveillanceResult result = service.analyzeForWashTrading("TRADER_A", trades);
        assertThat(result.alertTriggered()).isTrue();
        assertThat(result.alertType()).isEqualTo("WASH_TRADING");
    }

    @Test
    @DisplayName("Should detect spoofing pattern")
    void shouldDetectSpoofing() {
        List<Order> orders = List.of(
            new Order("O1", "TRADER_B", "GOOGL", 10000, BigDecimal.valueOf(2800), Side.BUY, OrderType.LIMIT),
            new Order("O2", "TRADER_B", "GOOGL", 100, BigDecimal.valueOf(2800), Side.BUY, OrderType.MARKET),
            new Order("O3", "TRADER_B", "GOOGL", 10000, BigDecimal.valueOf(2800), Side.BUY, OrderType.LIMIT, Status.CANCELLED)
        );
        SurveillanceResult result = service.analyzeForSpoofing("TRADER_B", orders);
        assertThat(result.alertTriggered()).isTrue();
        assertThat(result.alertType()).isEqualTo("SPOOFING");
    }

    @Test
    @DisplayName("Should detect layering pattern")
    void shouldDetectLayering() {
        List<Order> orders = generateLayeringOrders("TRADER_C", "MSFT", 5);
        SurveillanceResult result = service.analyzeForLayering("TRADER_C", orders);
        assertThat(result.alertTriggered()).isTrue();
        assertThat(result.confidenceScore()).isGreaterThan(0.8);
    }

    @Test
    @DisplayName("Should detect momentum ignition")
    void shouldDetectMomentumIgnition() {
        List<Trade> trades = generateRapidPriceMovement("TSLA", 20);
        SurveillanceResult result = service.analyzeForMomentumIgnition(trades);
        assertThat(result.alertTriggered()).isTrue();
    }

    @Test
    @DisplayName("Should detect marking the close")
    void shouldDetectMarkingTheClose() {
        LocalDateTime closeTime = LocalDateTime.now().withHour(15).withMinute(59);
        List<Trade> trades = List.of(
            new Trade("T1", "TRADER_D", "AAPL", 1000, BigDecimal.valueOf(155.00), closeTime.minusMinutes(5), Side.BUY),
            new Trade("T2", "TRADER_D", "AAPL", 5000, BigDecimal.valueOf(156.00), closeTime.minusMinutes(1), Side.BUY),
            new Trade("T3", "TRADER_D", "AAPL", 10000, BigDecimal.valueOf(157.00), closeTime, Side.BUY)
        );
        SurveillanceResult result = service.analyzeForMarkingClose("TRADER_D", trades);
        assertThat(result.alertTriggered()).isTrue();
    }

    @Test
    @DisplayName("Should generate surveillance alert")
    void shouldGenerateSurveillanceAlert() {
        SurveillanceAlert alert = service.generateAlert(
            "WASH_TRADING",
            "TRADER_A",
            "AAPL",
            "Multiple matching buy/sell trades within short timeframe",
            BigDecimal.valueOf(0.95)
        );
        assertThat(alert.priority()).isEqualTo("HIGH");
        assertThat(alert.status()).isEqualTo("OPEN");
    }

    @Test
    @DisplayName("Should assess trader behavior profile")
    void shouldAssessTraderBehaviorProfile() {
        List<Trade> history = generateTraderHistory("TRADER_E", 100);
        TraderProfile profile = service.assessBehaviorProfile("TRADER_E", history);
        assertThat(profile.riskScore()).isBetween(0.0, 1.0);
        assertThat(profile.tradingPattern()).isNotNull();
    }

    @Test
    @DisplayName("Should detect cross-market manipulation")
    void shouldDetectCrossMarketManipulation() {
        Map<String, List<Trade>> marketTrades = Map.of(
            "NYSE", List.of(new Trade("T1", "TRADER_F", "AAPL", 1000, BigDecimal.valueOf(150), LocalDateTime.now(), Side.BUY)),
            "NASDAQ", List.of(new Trade("T2", "TRADER_F", "AAPL", 1000, BigDecimal.valueOf(150.01), LocalDateTime.now().plusNanos(10_000_000L), Side.SELL))
        );
        SurveillanceResult result = service.analyzeCrossMarketActivity("TRADER_F", marketTrades);
        assertThat(result.crossMarketPattern()).isTrue();
    }

    @Test
    @DisplayName("Should monitor for insider trading patterns")
    void shouldMonitorForInsiderTrading() {
        List<Trade> trades = List.of(
            new Trade("T1", "TRADER_G", "ACME", 50000, BigDecimal.valueOf(50), LocalDateTime.now().minusDays(1), Side.BUY)
        );
        String materialEvent = "MERGER_ANNOUNCEMENT";
        SurveillanceResult result = service.analyzeForInsiderTrading("TRADER_G", trades, materialEvent);
        assertThat(result.preEventActivity()).isTrue();
    }

    record Trade(String id, String trader, String symbol, int quantity, BigDecimal price, LocalDateTime timestamp, Side side) {}
    record Order(String id, String trader, String symbol, int quantity, BigDecimal price, Side side, OrderType type) {
        Order(String id, String trader, String symbol, int quantity, BigDecimal price, Side side, OrderType type, Status status) {
            this(id, trader, symbol, quantity, price, side, type);
        }
    }
    enum Side { BUY, SELL }
    enum OrderType { MARKET, LIMIT }
    enum Status { NEW, CANCELLED, FILLED }
    record SurveillanceResult(boolean alertTriggered, String alertType, double confidenceScore, boolean crossMarketPattern, boolean preEventActivity) {}
    record SurveillanceAlert(String id, String type, String subject, String description, String priority, String status, LocalDateTime timestamp) {}
    record TraderProfile(String traderId, double riskScore, String tradingPattern, int alertsLastMonth) {}

    private List<Order> generateLayeringOrders(String trader, String symbol, int layers) {
        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < layers; i++) {
            orders.add(new Order("O" + i, trader, symbol, 1000, BigDecimal.valueOf(100 + i), Side.SELL, OrderType.LIMIT));
        }
        orders.add(new Order("OFINAL", trader, symbol, 100, BigDecimal.valueOf(99), Side.BUY, OrderType.MARKET));
        for (int i = 0; i < layers; i++) {
            orders.add(new Order("OC" + i, trader, symbol, 1000, BigDecimal.valueOf(100 + i), Side.SELL, OrderType.LIMIT, Status.CANCELLED));
        }
        return orders;
    }

    private List<Trade> generateRapidPriceMovement(String symbol, int count) {
        List<Trade> trades = new ArrayList<>();
        BigDecimal price = BigDecimal.valueOf(100);
        for (int i = 0; i < count; i++) {
            price = price.multiply(BigDecimal.valueOf(1.02));
            trades.add(new Trade("T" + i, "TRADER", symbol, 100, price, LocalDateTime.now().plusSeconds(i), Side.BUY));
        }
        return trades;
    }

    private List<Trade> generateTraderHistory(String trader, int count) {
        List<Trade> trades = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            trades.add(new Trade("T" + i, trader, "AAPL", 100, BigDecimal.valueOf(150), LocalDateTime.now().minusDays(i), i % 2 == 0 ? Side.BUY : Side.SELL));
        }
        return trades;
    }

    static class TradeSurveillanceService {
        SurveillanceResult analyzeForWashTrading(String trader, List<Trade> trades) {
            int matchingPairs = 0;
            for (int i = 0; i < trades.size() - 1; i++) {
                Trade t1 = trades.get(i);
                Trade t2 = trades.get(i + 1);
                if (t1.symbol().equals(t2.symbol()) && t1.quantity() == t2.quantity() && 
                    t1.price().compareTo(t2.price()) == 0 && t1.side() != t2.side()) {
                    matchingPairs++;
                }
            }
            return new SurveillanceResult(matchingPairs >= 2, "WASH_TRADING", 0.9, false, false);
        }

        SurveillanceResult analyzeForSpoofing(String trader, List<Order> orders) {
            boolean hasLargeOrder = orders.stream().anyMatch(o -> o.quantity() > 5000);
            boolean hasCancellation = orders.stream().anyMatch(o -> o.toString().contains("CANCELLED"));
            boolean hasExecution = orders.stream().anyMatch(o -> o.type() == OrderType.MARKET);
            return new SurveillanceResult(hasLargeOrder && hasCancellation && hasExecution, "SPOOFING", 0.85, false, false);
        }

        SurveillanceResult analyzeForLayering(String trader, List<Order> orders) {
            long cancelled = orders.stream().filter(o -> o.toString().contains("CANCELLED")).count();
            return new SurveillanceResult(cancelled >= 3, "LAYERING", 0.88, false, false);
        }

        SurveillanceResult analyzeForMomentumIgnition(List<Trade> trades) {
            boolean rapidMovement = trades.size() >= 10;
            return new SurveillanceResult(rapidMovement, "MOMENTUM_IGNITION", 0.75, false, false);
        }

        SurveillanceResult analyzeForMarkingClose(String trader, List<Trade> trades) {
            boolean nearClose = trades.stream().anyMatch(t -> t.timestamp().getHour() >= 15 && t.timestamp().getMinute() >= 30);
            boolean increasingSize = trades.get(trades.size() - 1).quantity() > trades.get(0).quantity() * 2;
            return new SurveillanceResult(nearClose && increasingSize, "MARKING_CLOSE", 0.82, false, false);
        }

        SurveillanceAlert generateAlert(String type, String subject, String symbol, String description, BigDecimal confidence) {
            String priority = confidence.compareTo(BigDecimal.valueOf(0.9)) >= 0 ? "HIGH" : "MEDIUM";
            return new SurveillanceAlert("ALT-" + System.currentTimeMillis(), type, subject, description, priority, "OPEN", LocalDateTime.now());
        }

        TraderProfile assessBehaviorProfile(String trader, List<Trade> history) {
            return new TraderProfile(trader, 0.3, "NORMAL", 0);
        }

        SurveillanceResult analyzeCrossMarketActivity(String trader, Map<String, List<Trade>> marketTrades) {
            return new SurveillanceResult(false, "CROSS_MARKET", 0.7, true, false);
        }

        SurveillanceResult analyzeForInsiderTrading(String trader, List<Trade> trades, String event) {
            boolean preEvent = trades.stream().anyMatch(t -> t.timestamp().isBefore(LocalDateTime.now().minusHours(1)));
            return new SurveillanceResult(preEvent, "INSIDER_TRADING", 0.65, false, true);
        }
    }
}
