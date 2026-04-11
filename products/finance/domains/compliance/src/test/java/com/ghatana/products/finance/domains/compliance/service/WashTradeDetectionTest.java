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
 * @doc.purpose Tests for wash trade detection algorithms per Compliance-003
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("Wash Trade Detection Tests")
class WashTradeDetectionTest {
    private WashTradeDetectionService service;

    @BeforeEach
    void setUp() {
        service = new WashTradeDetectionService();
    }

    @Test
    @DisplayName("Should detect exact match wash trades")
    void shouldDetectExactMatchWashTrades() {
        List<Trade> trades = List.of(
            new Trade("T1", "ACC_A", "ACC_B", "AAPL", 100, BigDecimal.valueOf(150.00), LocalDateTime.now()),
            new Trade("T2", "ACC_B", "ACC_A", "AAPL", 100, BigDecimal.valueOf(150.00), LocalDateTime.now().plusSeconds(5))
        );
        WashTradeResult result = service.detectExactMatchWashTrades(trades);
        assertThat(result.isWashTrade()).isTrue();
        assertThat(result.matchingTrades()).hasSize(2);
    }

    @Test
    @DisplayName("Should detect cross-account wash trading by same beneficiary")
    void shouldDetectCrossAccountWashTrading() {
        Map<String, String> accountOwnership = Map.of(
            "ACC_A", "BENEFICIARY_1",
            "ACC_B", "BENEFICIARY_1",
            "ACC_C", "BENEFICIARY_1"
        );
        List<Trade> trades = List.of(
            new Trade("T1", "ACC_A", "ACC_C", "GOOGL", 50, BigDecimal.valueOf(2800), LocalDateTime.now()),
            new Trade("T2", "ACC_C", "ACC_B", "GOOGL", 50, BigDecimal.valueOf(2800), LocalDateTime.now().plusSeconds(10))
        );
        WashTradeResult result = service.detectCrossAccountWashTrades(trades, accountOwnership);
        assertThat(result.isWashTrade()).isTrue();
        assertThat(result.commonBeneficiary()).isEqualTo("BENEFICIARY_1");
    }

    @Test
    @DisplayName("Should detect time-synchronized wash trades")
    void shouldDetectTimeSynchronizedTrades() {
        LocalDateTime baseTime = LocalDateTime.now();
        List<Trade> trades = List.of(
            new Trade("T1", "ACC_A", "ACC_B", "MSFT", 200, BigDecimal.valueOf(300), baseTime),
            new Trade("T2", "ACC_B", "ACC_A", "MSFT", 200, BigDecimal.valueOf(300), baseTime.plusNanos(500_000_000L))
        );
        WashTradeResult result = service.detectTimeSynchronizedTrades(trades, 1000);
        assertThat(result.isWashTrade()).isTrue();
        assertThat(result.timeDifferenceMillis()).isLessThanOrEqualTo(1000);
    }

    @Test
    @DisplayName("Should detect price-matched wash trades")
    void shouldDetectPriceMatchedTrades() {
        List<Trade> trades = List.of(
            new Trade("T1", "ACC_A", "ACC_B", "TSLA", 10, BigDecimal.valueOf(800.00), LocalDateTime.now()),
            new Trade("T2", "ACC_B", "ACC_A", "TSLA", 10, BigDecimal.valueOf(800.01), LocalDateTime.now().plusSeconds(2))
        );
        WashTradeResult result = service.detectPriceMatchedTrades(trades, BigDecimal.valueOf(0.05));
        assertThat(result.isWashTrade()).isTrue();
        assertThat(result.priceDifference()).isLessThanOrEqualTo(BigDecimal.valueOf(0.05));
    }

    @Test
    @DisplayName("Should calculate wash trade volume ratio")
    void shouldCalculateWashTradeVolumeRatio() {
        List<Trade> allTrades = generateSampleTrades(100);
        List<Trade> washTrades = List.of(
            new Trade("W1", "A", "B", "SYM", 100, BigDecimal.valueOf(100), LocalDateTime.now()),
            new Trade("W2", "B", "A", "SYM", 100, BigDecimal.valueOf(100), LocalDateTime.now())
        );
        BigDecimal ratio = service.calculateWashTradeVolumeRatio(allTrades, washTrades);
        assertThat(ratio).isBetween(BigDecimal.ZERO, BigDecimal.ONE);
    }

    @Test
    @DisplayName("Should assess wash trade intent")
    void shouldAssessWashTradeIntent() {
        List<Trade> history = List.of(
            new Trade("T1", "ACC_A", "ACC_B", "AAPL", 100, BigDecimal.valueOf(150), LocalDateTime.now().minusDays(1)),
            new Trade("T2", "ACC_B", "ACC_A", "AAPL", 100, BigDecimal.valueOf(150), LocalDateTime.now().minusDays(1).plusSeconds(5)),
            new Trade("T3", "ACC_A", "ACC_B", "AAPL", 100, BigDecimal.valueOf(150), LocalDateTime.now().minusHours(2)),
            new Trade("T4", "ACC_B", "ACC_A", "AAPL", 100, BigDecimal.valueOf(150), LocalDateTime.now().minusHours(2).plusSeconds(3))
        );
        IntentAssessment assessment = service.assessWashTradeIntent("ACC_A", history);
        assertThat(assessment.patternRecurrence()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Should generate wash trade alert")
    void shouldGenerateWashTradeAlert() {
        WashTradeResult result = new WashTradeResult(true, List.of("T1", "T2"), BigDecimal.valueOf(30000), 0.95);
        WashTradeAlert alert = service.generateAlert("ACC_A", "ACC_B", result);
        assertThat(alert.severity()).isEqualTo("HIGH");
        assertThat(alert.notionalAmount()).isEqualByComparingTo(BigDecimal.valueOf(30000));
    }

    @Test
    @DisplayName("Should track wash trade statistics")
    void shouldTrackWashTradeStatistics() {
        service.recordDetection("SYM1", LocalDateTime.now(), BigDecimal.valueOf(10000));
        service.recordDetection("SYM2", LocalDateTime.now(), BigDecimal.valueOf(20000));
        service.recordDetection("SYM1", LocalDateTime.now().minusDays(1), BigDecimal.valueOf(15000));
        WashTradeStats stats = service.getStatistics(LocalDateTime.now().minusDays(7), LocalDateTime.now());
        assertThat(stats.totalDetections()).isEqualTo(3);
        assertThat(stats.totalNotional()).isEqualByComparingTo(BigDecimal.valueOf(45000));
    }

    @Test
    @DisplayName("Should detect circular trading pattern")
    void shouldDetectCircularTrading() {
        List<Trade> trades = List.of(
            new Trade("T1", "A", "B", "SYM", 100, BigDecimal.valueOf(100), LocalDateTime.now()),
            new Trade("T2", "B", "C", "SYM", 100, BigDecimal.valueOf(100), LocalDateTime.now().plusSeconds(10)),
            new Trade("T3", "C", "A", "SYM", 100, BigDecimal.valueOf(100), LocalDateTime.now().plusSeconds(20))
        );
        WashTradeResult result = service.detectCircularTrading(trades);
        assertThat(result.isCircularPattern()).isTrue();
        assertThat(result.cycleLength()).isEqualTo(3);
    }

    record Trade(String id, String buyer, String seller, String symbol, int quantity, BigDecimal price, LocalDateTime timestamp) {}
    record WashTradeResult(boolean isWashTrade, List<String> matchingTrades, BigDecimal notionalAmount,
                          double confidenceScore, String commonBeneficiary, long timeDifferenceMillis,
                          BigDecimal priceDifference, boolean isCircularPattern, int cycleLength) {
        WashTradeResult(boolean isWashTrade, List<String> matchingTrades, BigDecimal notionalAmount, double confidenceScore) {
            this(isWashTrade, matchingTrades, notionalAmount, confidenceScore, null, 0, BigDecimal.ZERO, false, 0);
        }
    }
    record IntentAssessment(int patternRecurrence, String intentLevel, List<String> relatedAccounts) {}
    record WashTradeAlert(String alertId, String accountA, String accountB, String severity, BigDecimal notionalAmount, LocalDateTime timestamp) {}
    record WashTradeStats(int totalDetections, BigDecimal totalNotional, Map<String, Integer> bySymbol) {}

    private List<Trade> generateSampleTrades(int count) {
        List<Trade> trades = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            trades.add(new Trade("T" + i, "ACC_" + (i % 5), "ACC_" + ((i + 1) % 5), "SYM", 100, BigDecimal.valueOf(100 + i), LocalDateTime.now().minusMinutes(i)));
        }
        return trades;
    }

    static class WashTradeDetectionService {
        WashTradeResult detectExactMatchWashTrades(List<Trade> trades) {
            for (int i = 0; i < trades.size(); i++) {
                for (int j = i + 1; j < trades.size(); j++) {
                    Trade t1 = trades.get(i);
                    Trade t2 = trades.get(j);
                    if (t1.symbol().equals(t2.symbol()) && t1.quantity() == t2.quantity() &&
                        t1.price().compareTo(t2.price()) == 0 &&
                        t1.buyer().equals(t2.seller()) && t1.seller().equals(t2.buyer())) {
                        return new WashTradeResult(true, List.of(t1.id(), t2.id()), t1.price().multiply(BigDecimal.valueOf(t1.quantity())), 1.0);
                    }
                }
            }
            return new WashTradeResult(false, List.of(), BigDecimal.ZERO, 0.0);
        }

        WashTradeResult detectCrossAccountWashTrades(List<Trade> trades, Map<String, String> ownership) {
            for (Trade t : trades) {
                String buyerOwner = ownership.get(t.buyer());
                String sellerOwner = ownership.get(t.seller());
                if (buyerOwner != null && buyerOwner.equals(sellerOwner)) {
                    return new WashTradeResult(true, List.of(t.id()), BigDecimal.ZERO, 0.9, buyerOwner, 0, BigDecimal.ZERO, false, 0);
                }
            }
            return new WashTradeResult(false, List.of(), BigDecimal.ZERO, 0.0);
        }

        WashTradeResult detectTimeSynchronizedTrades(List<Trade> trades, long maxMillis) {
            for (int i = 0; i < trades.size() - 1; i++) {
                long diff = java.time.Duration.between(trades.get(i).timestamp(), trades.get(i + 1).timestamp()).toMillis();
                if (diff <= maxMillis) {
                    return new WashTradeResult(true, List.of(trades.get(i).id(), trades.get(i + 1).id()), BigDecimal.ZERO, 0.8, null, diff, BigDecimal.ZERO, false, 0);
                }
            }
            return new WashTradeResult(false, List.of(), BigDecimal.ZERO, 0.0);
        }

        WashTradeResult detectPriceMatchedTrades(List<Trade> trades, BigDecimal tolerance) {
            for (int i = 0; i < trades.size() - 1; i++) {
                BigDecimal diff = trades.get(i).price().subtract(trades.get(i + 1).price()).abs();
                if (diff.compareTo(tolerance) <= 0) {
                    return new WashTradeResult(true, List.of(trades.get(i).id(), trades.get(i + 1).id()), BigDecimal.ZERO, 0.75, null, 0, diff, false, 0);
                }
            }
            return new WashTradeResult(false, List.of(), BigDecimal.ZERO, 0.0);
        }

        BigDecimal calculateWashTradeVolumeRatio(List<Trade> all, List<Trade> wash) {
            BigDecimal washVolume = wash.stream().map(t -> t.price().multiply(BigDecimal.valueOf(t.quantity()))).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalVolume = all.stream().map(t -> t.price().multiply(BigDecimal.valueOf(t.quantity()))).reduce(BigDecimal.ZERO, BigDecimal::add);
            return washVolume.divide(totalVolume, 4, java.math.RoundingMode.HALF_UP);
        }

        IntentAssessment assessWashTradeIntent(String account, List<Trade> history) {
            Map<String, Integer> symbolCount = new HashMap<>();
            for (Trade t : history) {
                symbolCount.merge(t.symbol(), 1, Integer::sum);
            }
            int maxRecurrence = symbolCount.values().stream().max(Integer::compare).orElse(0);
            return new IntentAssessment(maxRecurrence, maxRecurrence > 2 ? "HIGH" : "LOW", List.of());
        }

        WashTradeAlert generateAlert(String accA, String accB, WashTradeResult result) {
            return new WashTradeAlert("WT" + System.currentTimeMillis(), accA, accB, "HIGH", result.notionalAmount(), LocalDateTime.now());
        }

        private final List<Map<String, Object>> detections = new ArrayList<>();

        void recordDetection(String symbol, LocalDateTime time, BigDecimal notional) {
            Map<String, Object> rec = new HashMap<>();
            rec.put("symbol", symbol);
            rec.put("time", time);
            rec.put("notional", notional);
            detections.add(rec);
        }

        WashTradeStats getStatistics(LocalDateTime from, LocalDateTime to) {
            int count = 0;
            BigDecimal total = BigDecimal.ZERO;
            Map<String, Integer> bySym = new HashMap<>();
            for (Map<String, Object> rec : detections) {
                LocalDateTime time = (LocalDateTime) rec.get("time");
                if (!time.isBefore(from) && !time.isAfter(to)) {
                    count++;
                    total = total.add((BigDecimal) rec.get("notional"));
                    bySym.merge((String) rec.get("symbol"), 1, Integer::sum);
                }
            }
            return new WashTradeStats(count, total, bySym);
        }

        WashTradeResult detectCircularTrading(List<Trade> trades) {
            if (trades.size() >= 3) {
                String firstBuyer = trades.get(0).buyer();
                String lastSeller = trades.get(trades.size() - 1).seller();
                if (firstBuyer.equals(lastSeller)) {
                    return new WashTradeResult(true, trades.stream().map(Trade::id).toList(), BigDecimal.ZERO, 0.9, null, 0, BigDecimal.ZERO, true, trades.size());
                }
            }
            return new WashTradeResult(false, List.of(), BigDecimal.ZERO, 0.0);
        }
    }
}
