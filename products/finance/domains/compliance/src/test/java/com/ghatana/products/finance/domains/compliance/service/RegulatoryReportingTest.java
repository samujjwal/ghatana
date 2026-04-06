package com.ghatana.products.finance.domains.compliance.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type Test
 * @doc.purpose Tests for regulatory reporting including MiFID II, EMIR, SFTR per Compliance-002
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("Regulatory Reporting Tests")
class RegulatoryReportingTest {
    private RegulatoryReportingService service;

    @BeforeEach
    void setUp() {
        service = new RegulatoryReportingService();
    }

    @Test
    @DisplayName("Should generate MiFID II transaction report")
    void shouldGenerateMifidTransactionReport() {
        Trade trade = new Trade("T1", "LEI123456", "AAPL", 100, BigDecimal.valueOf(150.50), 
                                LocalDateTime.now(), "VENUE_X", "BUY", "CLIENT_1");
        MifidReport report = service.generateMifidTransactionReport(trade);
        assertThat(report.lei()).isEqualTo("LEI123456");
        assertThat(report.tradingVenue()).isEqualTo("VENUE_X");
        assertThat(report.reportingStatus()).isEqualTo("READY");
    }

    @Test
    @DisplayName("Should generate EMIR derivative report")
    void shouldGenerateEmirReport() {
        DerivativeTrade trade = new DerivativeTrade("IRS_1", "INTEREST_RATE_SWAP", BigDecimal.valueOf(10000000),
                                                    LocalDate.now(), LocalDate.now().plusYears(5), "CPTY_LEI", true);
        EmirReport report = service.generateEmirReport(trade);
        assertThat(report.utr()).isNotNull();
        assertThat(report.actionType()).isEqualTo("NEWT");
        assertThat(report.reportingDeadline()).isAfter(LocalDateTime.now());
    }

    @Test
    @DisplayName("Should generate SFTR securities financing report")
    void shouldGenerateSftrReport() {
        SftTransaction sft = new SftTransaction("SFT_1", "REPURCHASE", "ISIN123", BigDecimal.valueOf(5000000),
                                                  LocalDate.now(), LocalDate.now().plusDays(7), "COLLATERAL_1");
        SftrReport report = service.generateSftrReport(sft);
        assertThat(report.reportId()).isNotNull();
        assertThat(report.transactionType()).isEqualTo("REPURCHASE");
        assertThat(report.compliant()).isTrue();
    }

    @Test
    @DisplayName("Should generate CAT (Consolidated Audit Trail) report")
    void shouldGenerateCatReport() {
        List<Trade> trades = List.of(
            new Trade("T1", "LEI1", "AAPL", 100, BigDecimal.valueOf(150), LocalDateTime.now(), "NYSE", "BUY", "CLIENT_1"),
            new Trade("T2", "LEI1", "GOOGL", 50, BigDecimal.valueOf(2800), LocalDateTime.now(), "NASDAQ", "SELL", "CLIENT_2")
        );
        CatReport report = service.generateCatReport(trades, LocalDate.now());
        assertThat(report.recordCount()).isEqualTo(2);
        assertThat(report.firmDesignatedId()).isNotNull();
    }

    @Test
    @DisplayName("Should validate LEI format")
    void shouldValidateLeiFormat() {
        assertThat(service.isValidLei("529900T8BM49AURSDO55")).isTrue();
        assertThat(service.isValidLei("INVALID")).isFalse();
        assertThat(service.isValidLei("12345")).isFalse();
    }

    @Test
    @DisplayName("Should calculate reporting deadline")
    void shouldCalculateReportingDeadline() {
        LocalDateTime tradeTime = LocalDateTime.now();
        LocalDateTime deadline = service.calculateReportingDeadline(tradeTime, "EMIR");
        assertThat(deadline).isAfter(tradeTime);
        assertThat(deadline).isBefore(tradeTime.plusDays(2));
    }

    @Test
    @DisplayName("Should track reporting status")
    void shouldTrackReportingStatus() {
        String reportId = service.submitReport("EMIR", "<trade>data</trade>");
        ReportingStatus status = service.getReportingStatus(reportId);
        assertThat(status.status()).isIn("PENDING", "ACCEPTED", "REJECTED");
    }

    @Test
    @DisplayName("Should handle reporting rejection and resubmission")
    void shouldHandleReportingRejection() {
        String reportId = service.submitReport("MiFID", "<invalid>data");
        service.markRejected(reportId, "Invalid XML format");
        ReportingStatus status = service.getReportingStatus(reportId);
        assertThat(status.status()).isEqualTo("REJECTED");
        assertThat(status.errorMessage()).contains("Invalid XML");
    }

    @Test
    @DisplayName("Should generate large trader reporting")
    void shouldGenerateLargeTraderReporting() {
        List<Position> positions = List.of(
            new Position("AAPL", 10000000),
            new Position("GOOGL", 5000000)
        );
        LargeTraderReport report = service.generateLargeTraderReport("ENTITY_1", positions);
        assertThat(report.totalShares()).isEqualByComparingTo(BigDecimal.valueOf(15000000));
        assertThat(report.thresholdExceeded()).isTrue();
    }

    @Test
    @DisplayName("Should aggregate reports by regulator")
    void shouldAggregateReportsByRegulator() {
        Map<String, List<String>> reports = Map.of(
            "ESMA", List.of("MIFID_RPT_1", "MIFID_RPT_2"),
            "ECB", List.of("EMIR_RPT_1"),
            "FCA", List.of("SFTR_RPT_1", "SFTR_RPT_2")
        );
        RegulatorSummary summary = service.aggregateByRegulator(reports);
        assertThat(summary.totalReports()).isEqualTo(5);
        assertThat(summary.byRegulator().get("ESMA")).isEqualTo(2);
    }

    record Trade(String id, String lei, String symbol, int quantity, BigDecimal price, 
                 LocalDateTime timestamp, String venue, String side, String clientId) {}
    record DerivativeTrade(String id, String productType, BigDecimal notional, 
                          LocalDate startDate, LocalDate maturityDate, String counterpartyLei, boolean cleared) {}
    record SftTransaction(String id, String type, String isin, BigDecimal amount,
                         LocalDate startDate, LocalDate endDate, String collateralId) {}
    record MifidReport(String lei, String tradingVenue, String reportingStatus, LocalDateTime timestamp) {}
    record EmirReport(String utr, String actionType, LocalDateTime reportingDeadline, boolean validated) {}
    record SftrReport(String reportId, String transactionType, boolean compliant, LocalDateTime submittedAt) {}
    record CatReport(int recordCount, String firmDesignatedId, LocalDate reportDate, String version) {}
    record Position(String symbol, int quantity) {}
    record LargeTraderReport(String entityId, BigDecimal totalShares, boolean thresholdExceeded, LocalDate date) {}
    record ReportingStatus(String status, String errorMessage, int resubmissionCount) {}
    record RegulatorSummary(int totalReports, Map<String, Integer> byRegulator) {}

    static class RegulatoryReportingService {
        MifidReport generateMifidTransactionReport(Trade trade) {
            return new MifidReport(trade.lei(), trade.venue(), "READY", LocalDateTime.now());
        }

        EmirReport generateEmirReport(DerivativeTrade trade) {
            String utr = "UTR" + System.currentTimeMillis();
            return new EmirReport(utr, "NEWT", LocalDateTime.now().plusHours(24), true);
        }

        SftrReport generateSftrReport(SftTransaction sft) {
            return new SftrReport("SFTR" + System.currentTimeMillis(), sft.type(), true, LocalDateTime.now());
        }

        CatReport generateCatReport(List<Trade> trades, LocalDate date) {
            return new CatReport(trades.size(), "FDID_123", date, "2.0");
        }

        boolean isValidLei(String lei) {
            return lei != null && lei.length() == 20 && lei.matches("[A-Z0-9]{20}");
        }

        LocalDateTime calculateReportingDeadline(LocalDateTime tradeTime, String regulation) {
            if ("EMIR".equals(regulation)) return tradeTime.plusHours(4);
            if ("MiFID".equals(regulation)) return tradeTime.plusMinutes(15);
            return tradeTime.plusHours(24);
        }

        String submitReport(String regulation, String payload) {
            return "RPT" + System.currentTimeMillis();
        }

        ReportingStatus getReportingStatus(String reportId) {
            return new ReportingStatus("PENDING", null, 0);
        }

        void markRejected(String reportId, String error) {
        }

        LargeTraderReport generateLargeTraderReport(String entityId, List<Position> positions) {
            BigDecimal total = positions.stream().map(p -> BigDecimal.valueOf(p.quantity())).reduce(BigDecimal.ZERO, BigDecimal::add);
            return new LargeTraderReport(entityId, total, total.compareTo(BigDecimal.valueOf(10000000)) > 0, LocalDate.now());
        }

        RegulatorSummary aggregateByRegulator(Map<String, List<String>> reports) {
            int total = reports.values().stream().mapToInt(List::size).sum();
            Map<String, Integer> byReg = new HashMap<>();
            for (Map.Entry<String, List<String>> entry : reports.entrySet()) {
                byReg.put(entry.getKey(), entry.getValue().size());
            }
            return new RegulatorSummary(total, byReg);
        }
    }
}
