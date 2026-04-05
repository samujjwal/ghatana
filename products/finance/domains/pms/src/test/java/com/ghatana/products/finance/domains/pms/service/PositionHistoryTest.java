package com.ghatana.products.finance.domains.pms.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@DisplayName("Position History Tests")
class PositionHistoryTest {
    private HistoryService service;

    @BeforeEach
    void setUp() {
        service = new HistoryService();
    }

    @Test
    @DisplayName("Should track position changes over time")
    void shouldTrackPositionChangesOverTime() {
        service.recordSnapshot("AAPL", 100L, BigDecimal.valueOf(150.00), Instant.now());
        service.recordSnapshot("AAPL", 150L, BigDecimal.valueOf(151.00), Instant.now().plusSeconds(3600));
        assertThat(service.getHistory("AAPL")).hasSize(2);
    }

    @Test
    @DisplayName("Should retrieve position at specific point in time")
    void shouldRetrievePositionAtSpecificPointInTime() {
        Instant t1 = Instant.now();
        service.recordSnapshot("AAPL", 100L, BigDecimal.valueOf(150.00), t1);
        service.recordSnapshot("AAPL", 150L, BigDecimal.valueOf(151.00), t1.plusSeconds(3600));
        PositionSnapshot snapshot = service.getSnapshotAt("AAPL", t1.plusSeconds(1800));
        assertThat(snapshot.quantity()).isEqualTo(100L);
    }

    @Test
    @DisplayName("Should calculate position changes")
    void shouldCalculatePositionChanges() {
        Instant t1 = Instant.now();
        service.recordSnapshot("AAPL", 100L, BigDecimal.valueOf(150.00), t1);
        service.recordSnapshot("AAPL", 150L, BigDecimal.valueOf(151.00), t1.plusSeconds(3600));
        PositionChange change = service.calculateChange("AAPL", t1, t1.plusSeconds(3600));
        assertThat(change.quantityDelta()).isEqualTo(50L);
    }

    @Test
    @DisplayName("Should track intraday position movements")
    void shouldTrackIntradayPositionMovements() {
        Instant start = Instant.now();
        for (int i = 0; i < 10; i++) {
            service.recordSnapshot("AAPL", 100L + i * 10, BigDecimal.valueOf(150.00), start.plusSeconds(i * 600));
        }
        assertThat(service.getIntradayHistory("AAPL")).hasSize(10);
    }

    @Test
    @DisplayName("Should generate position timeline")
    void shouldGeneratePositionTimeline() {
        service.recordSnapshot("AAPL", 100L, BigDecimal.valueOf(150.00), Instant.now());
        service.recordSnapshot("AAPL", 150L, BigDecimal.valueOf(151.00), Instant.now().plusSeconds(3600));
        Timeline timeline = service.generateTimeline("AAPL");
        assertThat(timeline.events()).hasSize(2);
    }

    @Test
    @DisplayName("Should support historical queries")
    void shouldSupportHistoricalQueries() {
        Instant t1 = Instant.now().minusSeconds(86400);
        service.recordSnapshot("AAPL", 100L, BigDecimal.valueOf(150.00), t1);
        List<PositionSnapshot> history = service.getHistoryBetween("AAPL", t1.minusSeconds(3600), Instant.now());
        assertThat(history).hasSize(1);
    }

    @Test
    @DisplayName("Should calculate average position")
    void shouldCalculateAveragePosition() {
        Instant t1 = Instant.now();
        service.recordSnapshot("AAPL", 100L, BigDecimal.valueOf(150.00), t1);
        service.recordSnapshot("AAPL", 200L, BigDecimal.valueOf(152.00), t1.plusSeconds(3600));
        BigDecimal avgPrice = service.calculateAveragePrice("AAPL", t1, t1.plusSeconds(3600));
        assertThat(avgPrice).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should track position turnover")
    void shouldTrackPositionTurnover() {
        service.recordSnapshot("AAPL", 100L, BigDecimal.valueOf(150.00), Instant.now());
        service.recordSnapshot("AAPL", 50L, BigDecimal.valueOf(151.00), Instant.now().plusSeconds(3600));
        service.recordSnapshot("AAPL", 150L, BigDecimal.valueOf(152.00), Instant.now().plusSeconds(7200));
        double turnover = service.calculateTurnover("AAPL");
        assertThat(turnover).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("Should export position history")
    void shouldExportPositionHistory() {
        service.recordSnapshot("AAPL", 100L, BigDecimal.valueOf(150.00), Instant.now());
        String export = service.exportHistory("AAPL", "CSV");
        assertThat(export).contains("AAPL").contains("100");
    }

    @Test
    @DisplayName("Should archive old snapshots")
    void shouldArchiveOldSnapshots() {
        Instant old = Instant.now().minusSeconds(86400 * 365);
        service.recordSnapshot("AAPL", 100L, BigDecimal.valueOf(150.00), old);
        int archived = service.archiveOldSnapshots(90);
        assertThat(archived).isGreaterThan(0);
    }

    record PositionSnapshot(String symbol, long quantity, BigDecimal price, Instant timestamp) {}
    record PositionChange(long quantityDelta, BigDecimal priceDelta) {}
    record Timeline(List<PositionSnapshot> events) {}

    static class HistoryService {
        private final List<PositionSnapshot> snapshots = new java.util.ArrayList<>();

        void recordSnapshot(String symbol, long quantity, BigDecimal price, Instant timestamp) {
            snapshots.add(new PositionSnapshot(symbol, quantity, price, timestamp));
        }

        List<PositionSnapshot> getHistory(String symbol) {
            return snapshots.stream().filter(s -> s.symbol().equals(symbol)).toList();
        }

        PositionSnapshot getSnapshotAt(String symbol, Instant time) {
            return snapshots.stream()
                .filter(s -> s.symbol().equals(symbol) && !s.timestamp().isAfter(time))
                .max((a, b) -> a.timestamp().compareTo(b.timestamp()))
                .orElse(null);
        }

        PositionChange calculateChange(String symbol, Instant start, Instant end) {
            PositionSnapshot startSnapshot = getSnapshotAt(symbol, start);
            PositionSnapshot endSnapshot = getSnapshotAt(symbol, end);
            if (startSnapshot != null && endSnapshot != null) {
                return new PositionChange(
                    endSnapshot.quantity() - startSnapshot.quantity(),
                    endSnapshot.price().subtract(startSnapshot.price())
                );
            }
            return new PositionChange(0L, BigDecimal.ZERO);
        }

        List<PositionSnapshot> getIntradayHistory(String symbol) {
            Instant startOfDay = Instant.now().minusSeconds(86400);
            return snapshots.stream()
                .filter(s -> s.symbol().equals(symbol) && s.timestamp().isAfter(startOfDay))
                .toList();
        }

        Timeline generateTimeline(String symbol) {
            return new Timeline(getHistory(symbol));
        }

        List<PositionSnapshot> getHistoryBetween(String symbol, Instant start, Instant end) {
            return snapshots.stream()
                .filter(s -> s.symbol().equals(symbol) 
                    && !s.timestamp().isBefore(start) 
                    && !s.timestamp().isAfter(end))
                .toList();
        }

        BigDecimal calculateAveragePrice(String symbol, Instant start, Instant end) {
            List<PositionSnapshot> history = getHistoryBetween(symbol, start, end);
            if (history.isEmpty()) return BigDecimal.ZERO;
            return history.stream()
                .map(PositionSnapshot::price)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(history.size()), 2, java.math.RoundingMode.HALF_UP);
        }

        double calculateTurnover(String symbol) {
            List<PositionSnapshot> history = getHistory(symbol);
            if (history.size() < 2) return 0.0;
            long totalChange = 0L;
            for (int i = 1; i < history.size(); i++) {
                totalChange += Math.abs(history.get(i).quantity() - history.get(i-1).quantity());
            }
            long avgPosition = history.stream().mapToLong(PositionSnapshot::quantity).sum() / history.size();
            return avgPosition > 0 ? (double) totalChange / avgPosition : 0.0;
        }

        String exportHistory(String symbol, String format) {
            List<PositionSnapshot> history = getHistory(symbol);
            return history.stream()
                .map(s -> String.format("%s,%d,%s,%s", s.symbol(), s.quantity(), s.price(), s.timestamp()))
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
        }

        int archiveOldSnapshots(int daysToKeep) {
            Instant cutoff = Instant.now().minusSeconds(daysToKeep * 86400L);
            int count = (int) snapshots.stream().filter(s -> s.timestamp().isBefore(cutoff)).count();
            snapshots.removeIf(s -> s.timestamp().isBefore(cutoff));
            return count;
        }
    }
}
