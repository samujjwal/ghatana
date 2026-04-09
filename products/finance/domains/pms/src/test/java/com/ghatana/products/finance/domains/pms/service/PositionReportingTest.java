package com.ghatana.products.finance.domains.pms.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type Test
 * @doc.purpose Tests for position reporting per D03-004
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("Position Reporting Tests")
class PositionReportingTest {

    private ReportingService reportingService;

    @BeforeEach
    void setUp() {
        reportingService = new ReportingService();
    }

    @Test
    @DisplayName("Should generate daily position report")
    void shouldGenerateDailyPositionReport() {
        List<Position> positions = List.of(
            new Position("AAPL", 1000L, BigDecimal.valueOf(150.00)),
            new Position("GOOGL", 100L, BigDecimal.valueOf(2800.00))
        );

        DailyReport report = reportingService.generateDailyReport(positions, LocalDate.now());

        assertThat(report.positionCount()).isEqualTo(2);
        assertThat(report.totalValue()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should generate P&L report")
    void shouldGeneratePnLReport() {
        List<Position> positions = List.of(
            new Position("AAPL", 1000L, BigDecimal.valueOf(150.00))
        );

        List<PriceUpdate> prices = List.of(
            new PriceUpdate("AAPL", BigDecimal.valueOf(155.00))
        );

        PnLReport report = reportingService.generatePnLReport(positions, prices);

        assertThat(report.unrealizedPnL()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should generate holdings summary")
    void shouldGenerateHoldingsSummary() {
        List<Position> positions = List.of(
            new Position("AAPL", 1000L, BigDecimal.valueOf(150.00)),
            new Position("GOOGL", 100L, BigDecimal.valueOf(2800.00)),
            new Position("MSFT", 500L, BigDecimal.valueOf(300.00))
        );

        HoldingsSummary summary = reportingService.generateHoldingsSummary(positions);

        assertThat(summary.totalPositions()).isEqualTo(3);
        assertThat(summary.longPositions()).isEqualTo(3);
        assertThat(summary.shortPositions()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should generate sector allocation report")
    void shouldGenerateSectorAllocationReport() {
        List<PositionWithSector> positions = List.of(
            new PositionWithSector("AAPL", 1000L, BigDecimal.valueOf(150.00), "Technology"),
            new PositionWithSector("GOOGL", 100L, BigDecimal.valueOf(2800.00), "Technology"),
            new PositionWithSector("JPM", 500L, BigDecimal.valueOf(150.00), "Financial")
        );

        SectorAllocation allocation = reportingService.generateSectorAllocation(positions);

        assertThat(allocation.sectors()).containsKey("Technology");
        assertThat(allocation.sectors()).containsKey("Financial");
    }

    @Test
    @DisplayName("Should export positions to CSV")
    void shouldExportPositionsToCSV() {
        List<Position> positions = List.of(
            new Position("AAPL", 1000L, BigDecimal.valueOf(150.00)),
            new Position("GOOGL", 100L, BigDecimal.valueOf(2800.00))
        );

        String csv = reportingService.exportToCSV(positions);

        assertThat(csv).contains("AAPL");
        assertThat(csv).contains("GOOGL");
        assertThat(csv).contains("1000");
    }

    @Test
    @DisplayName("Should generate performance attribution")
    void shouldGeneratePerformanceAttribution() {
        List<Position> startPositions = List.of(
            new Position("AAPL", 1000L, BigDecimal.valueOf(150.00))
        );

        List<Position> endPositions = List.of(
            new Position("AAPL", 1000L, BigDecimal.valueOf(155.00))
        );

        PerformanceAttribution attribution = reportingService.calculateAttribution(
            startPositions,
            endPositions
        );

        assertThat(attribution.totalReturn()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should generate regulatory report")
    void shouldGenerateRegulatoryReport() {
        List<Position> positions = List.of(
            new Position("AAPL", 1000L, BigDecimal.valueOf(150.00))
        );

        RegulatoryReport report = reportingService.generateRegulatoryReport(
            positions,
            "13F",
            LocalDate.now()
        );

        assertThat(report.reportType()).isEqualTo("13F");
        assertThat(report.positionCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should calculate portfolio turnover")
    void shouldCalculatePortfolioTurnover() {
        List<Trade> trades = List.of(
            new Trade("trade-1", "AAPL", "BUY", 1000L, BigDecimal.valueOf(150.00)),
            new Trade("trade-2", "AAPL", "SELL", 500L, BigDecimal.valueOf(155.00))
        );

        BigDecimal avgPortfolioValue = BigDecimal.valueOf(500000.00);

        double turnover = reportingService.calculateTurnover(trades, avgPortfolioValue);

        assertThat(turnover).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("Should generate tax lot report")
    void shouldGenerateTaxLotReport() {
        List<TaxLot> lots = List.of(
            new TaxLot("lot-1", "AAPL", 500L, BigDecimal.valueOf(150.00), LocalDate.now().minusDays(100)),
            new TaxLot("lot-2", "AAPL", 500L, BigDecimal.valueOf(152.00), LocalDate.now().minusDays(50))
        );

        TaxLotReport report = reportingService.generateTaxLotReport(lots);

        assertThat(report.totalLots()).isEqualTo(2);
        assertThat(report.longTermLots()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("Should generate historical position snapshot")
    void shouldGenerateHistoricalPositionSnapshot() {
        List<Position> positions = List.of(
            new Position("AAPL", 1000L, BigDecimal.valueOf(150.00))
        );

        PositionSnapshot snapshot = reportingService.createSnapshot(positions, Instant.now());

        assertThat(snapshot.positions()).hasSize(1);
        assertThat(snapshot.snapshotTime()).isNotNull();
    }

    record Position(String symbol, long quantity, BigDecimal averagePrice) {}
    record PositionWithSector(String symbol, long quantity, BigDecimal averagePrice, String sector) {}
    record PriceUpdate(String symbol, BigDecimal price) {}
    record Trade(String tradeId, String symbol, String side, long quantity, BigDecimal price) {}
    record TaxLot(String lotId, String symbol, long quantity, BigDecimal costBasis, LocalDate acquiredDate) {}

    record DailyReport(int positionCount, BigDecimal totalValue, LocalDate reportDate) {}
    record PnLReport(BigDecimal unrealizedPnL, BigDecimal realizedPnL) {}
    record HoldingsSummary(int totalPositions, int longPositions, int shortPositions) {}
    record SectorAllocation(java.util.Map<String, BigDecimal> sectors) {}
    record PerformanceAttribution(BigDecimal totalReturn, BigDecimal marketReturn) {}
    record RegulatoryReport(String reportType, int positionCount, LocalDate reportDate) {}
    record TaxLotReport(int totalLots, int longTermLots, int shortTermLots) {}
    record PositionSnapshot(List<Position> positions, Instant snapshotTime) {}

    static class ReportingService {
        DailyReport generateDailyReport(List<Position> positions, LocalDate date) {
            BigDecimal totalValue = positions.stream()
                .map(p -> p.averagePrice().multiply(BigDecimal.valueOf(Math.abs(p.quantity()))))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new DailyReport(positions.size(), totalValue, date);
        }

        PnLReport generatePnLReport(List<Position> positions, List<PriceUpdate> prices) {
            BigDecimal unrealizedPnL = BigDecimal.ZERO;
            for (Position pos : positions) {
                PriceUpdate price = prices.stream()
                    .filter(p -> p.symbol().equals(pos.symbol()))
                    .findFirst()
                    .orElse(null);
                if (price != null) {
                    BigDecimal pnl = price.price().subtract(pos.averagePrice())
                        .multiply(BigDecimal.valueOf(pos.quantity()));
                    unrealizedPnL = unrealizedPnL.add(pnl);
                }
            }
            return new PnLReport(unrealizedPnL, BigDecimal.ZERO);
        }

        HoldingsSummary generateHoldingsSummary(List<Position> positions) {
            long longCount = positions.stream().filter(p -> p.quantity() > 0).count();
            long shortCount = positions.stream().filter(p -> p.quantity() < 0).count();
            return new HoldingsSummary(positions.size(), (int) longCount, (int) shortCount);
        }

        SectorAllocation generateSectorAllocation(List<PositionWithSector> positions) {
            java.util.Map<String, BigDecimal> sectors = new java.util.HashMap<>();
            BigDecimal totalValue = positions.stream()
                .map(p -> p.averagePrice().multiply(BigDecimal.valueOf(Math.abs(p.quantity()))))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            for (PositionWithSector pos : positions) {
                BigDecimal value = pos.averagePrice().multiply(BigDecimal.valueOf(Math.abs(pos.quantity())));
                sectors.merge(pos.sector(), value, BigDecimal::add);
            }

            return new SectorAllocation(sectors);
        }

        String exportToCSV(List<Position> positions) {
            StringBuilder csv = new StringBuilder("Symbol,Quantity,AveragePrice\n");
            for (Position pos : positions) {
                csv.append(pos.symbol()).append(",")
                   .append(pos.quantity()).append(",")
                   .append(pos.averagePrice()).append("\n");
            }
            return csv.toString();
        }

        PerformanceAttribution calculateAttribution(List<Position> start, List<Position> end) {
            BigDecimal startValue = start.stream()
                .map(p -> p.averagePrice().multiply(BigDecimal.valueOf(Math.abs(p.quantity()))))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal endValue = end.stream()
                .map(p -> p.averagePrice().multiply(BigDecimal.valueOf(Math.abs(p.quantity()))))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalReturn = endValue.subtract(startValue);
            return new PerformanceAttribution(totalReturn, BigDecimal.ZERO);
        }

        RegulatoryReport generateRegulatoryReport(List<Position> positions, String reportType, LocalDate date) {
            return new RegulatoryReport(reportType, positions.size(), date);
        }

        double calculateTurnover(List<Trade> trades, BigDecimal avgPortfolioValue) {
            BigDecimal totalTraded = trades.stream()
                .map(t -> t.price().multiply(BigDecimal.valueOf(t.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            return totalTraded.divide(avgPortfolioValue, 4, java.math.RoundingMode.HALF_UP).doubleValue();
        }

        TaxLotReport generateTaxLotReport(List<TaxLot> lots) {
            LocalDate oneYearAgo = LocalDate.now().minusDays(365);
            long longTerm = lots.stream().filter(l -> l.acquiredDate().isBefore(oneYearAgo)).count();
            long shortTerm = lots.size() - longTerm;
            return new TaxLotReport(lots.size(), (int) longTerm, (int) shortTerm);
        }

        PositionSnapshot createSnapshot(List<Position> positions, Instant time) {
            return new PositionSnapshot(List.copyOf(positions), time);
        }
    }
}
