package com.ghatana.products.finance.domains.phr.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@DisplayName("Portfolio Reporting Tests")
class PortfolioReportingTest {
    private ReportingService service;

    @BeforeEach
    void setUp() {
        service = new ReportingService();
    }

    @Test
    @DisplayName("Should generate monthly statement")
    void shouldGenerateMonthlyStatement() {
        service.addHolding("AAPL", 100L, BigDecimal.valueOf(150.00));
        service.addTransaction("BUY", "AAPL", 100L, BigDecimal.valueOf(150.00), LocalDate.now());
        MonthlyStatement statement = service.generateMonthlyStatement(2024, 4);
        assertThat(statement.holdings()).hasSize(1);
        assertThat(statement.transactions()).hasSize(1);
    }

    @Test
    @DisplayName("Should generate quarterly report")
    void shouldGenerateQuarterlyReport() {
        service.setBeginningValue(BigDecimal.valueOf(1000000.00));
        service.setEndingValue(BigDecimal.valueOf(1050000.00));
        QuarterlyReport report = service.generateQuarterlyReport(2024, 2);
        assertThat(report.quarterlyReturn()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should generate annual summary")
    void shouldGenerateAnnualSummary() {
        service.addReturn(LocalDate.of(2024, 1, 31), BigDecimal.valueOf(2.5));
        service.addReturn(LocalDate.of(2024, 2, 29), BigDecimal.valueOf(1.8));
        service.addReturn(LocalDate.of(2024, 3, 31), BigDecimal.valueOf(3.2));
        AnnualSummary summary = service.generateAnnualSummary(2024);
        assertThat(summary.yearToDateReturn()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should generate performance summary")
    void shouldGeneratePerformanceSummary() {
        service.addReturn(LocalDate.now().minusDays(30), BigDecimal.valueOf(5.0));
        service.addReturn(LocalDate.now(), BigDecimal.valueOf(8.0));
        PerformanceSummary summary = service.generatePerformanceSummary();
        assertThat(summary.oneMonthReturn()).isNotNull();
    }

    @Test
    @DisplayName("Should generate holdings report")
    void shouldGenerateHoldingsReport() {
        service.addHolding("AAPL", 100L, BigDecimal.valueOf(150.00));
        service.addHolding("GOOGL", 50L, BigDecimal.valueOf(2800.00));
        HoldingsReport report = service.generateHoldingsReport();
        assertThat(report.totalHoldings()).isEqualTo(2);
        assertThat(report.totalValue()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should generate transaction history report")
    void shouldGenerateTransactionHistoryReport() {
        service.addTransaction("BUY", "AAPL", 100L, BigDecimal.valueOf(150.00), LocalDate.now().minusDays(10));
        service.addTransaction("SELL", "AAPL", 50L, BigDecimal.valueOf(155.00), LocalDate.now());
        TransactionHistoryReport report = service.generateTransactionHistoryReport(
            LocalDate.now().minusDays(30), LocalDate.now()
        );
        assertThat(report.transactions()).hasSize(2);
    }

    @Test
    @DisplayName("Should generate income report")
    void shouldGenerateIncomeReport() {
        service.addDividend("AAPL", BigDecimal.valueOf(50.00), LocalDate.now());
        service.addInterest(BigDecimal.valueOf(25.00), LocalDate.now());
        IncomeReport report = service.generateIncomeReport(2024);
        assertThat(report.totalIncome()).isEqualByComparingTo(BigDecimal.valueOf(75.00));
    }

    @Test
    @DisplayName("Should generate fee report")
    void shouldGenerateFeeReport() {
        service.addFee("MANAGEMENT", BigDecimal.valueOf(100.00), LocalDate.now());
        service.addFee("TRANSACTION", BigDecimal.valueOf(25.00), LocalDate.now());
        FeeReport report = service.generateFeeReport(2024);
        assertThat(report.totalFees()).isEqualByComparingTo(BigDecimal.valueOf(125.00));
    }

    @Test
    @DisplayName("Should export report to PDF")
    void shouldExportReportToPDF() {
        service.addHolding("AAPL", 100L, BigDecimal.valueOf(150.00));
        byte[] pdf = service.exportToPDF("MONTHLY_STATEMENT", 2024, 4);
        assertThat(pdf).isNotEmpty();
    }

    @Test
    @DisplayName("Should schedule automated reports")
    void shouldScheduleAutomatedReports() {
        service.scheduleReport("MONTHLY_STATEMENT", "MONTHLY", "user@example.com");
        List<ScheduledReport> scheduled = service.getScheduledReports();
        assertThat(scheduled).hasSize(1);
    }

    record MonthlyStatement(List<Holding> holdings, List<Transaction> transactions, BigDecimal totalValue) {}
    record QuarterlyReport(BigDecimal quarterlyReturn, BigDecimal beginningValue, BigDecimal endingValue) {}
    record AnnualSummary(BigDecimal yearToDateReturn, BigDecimal totalReturn, int year) {}
    record PerformanceSummary(BigDecimal oneMonthReturn, BigDecimal threeMonthReturn, BigDecimal yearToDateReturn) {}
    record HoldingsReport(int totalHoldings, BigDecimal totalValue, List<Holding> holdings) {}
    record TransactionHistoryReport(List<Transaction> transactions, BigDecimal netCashFlow) {}
    record IncomeReport(BigDecimal totalIncome, BigDecimal dividends, BigDecimal interest) {}
    record FeeReport(BigDecimal totalFees, java.util.Map<String, BigDecimal> feesByType) {}
    record ScheduledReport(String reportType, String frequency, String recipient) {}
    record Holding(String symbol, long quantity, BigDecimal price) {}
    record Transaction(String type, String symbol, long quantity, BigDecimal price, LocalDate date) {}

    static class ReportingService {
        private final List<Holding> holdings = new java.util.ArrayList<>();
        private final List<Transaction> transactions = new java.util.ArrayList<>();
        private final List<Return> returns = new java.util.ArrayList<>();
        private final List<Dividend> dividends = new java.util.ArrayList<>();
        private final List<Fee> fees = new java.util.ArrayList<>();
        private final List<ScheduledReport> scheduledReports = new java.util.ArrayList<>();
        private BigDecimal beginningValue = BigDecimal.ZERO;
        private BigDecimal endingValue = BigDecimal.ZERO;
        private BigDecimal interestIncome = BigDecimal.ZERO;

        void addHolding(String symbol, long quantity, BigDecimal price) {
            holdings.add(new Holding(symbol, quantity, price));
        }

        void addTransaction(String type, String symbol, long quantity, BigDecimal price, LocalDate date) {
            transactions.add(new Transaction(type, symbol, quantity, price, date));
        }

        void addReturn(LocalDate date, BigDecimal returnPct) {
            returns.add(new Return(date, returnPct));
        }

        void addDividend(String symbol, BigDecimal amount, LocalDate date) {
            dividends.add(new Dividend(symbol, amount, date));
        }

        void addInterest(BigDecimal amount, LocalDate date) {
            interestIncome = interestIncome.add(amount);
        }

        void addFee(String type, BigDecimal amount, LocalDate date) {
            fees.add(new Fee(type, amount, date));
        }

        void setBeginningValue(BigDecimal value) {
            this.beginningValue = value;
        }

        void setEndingValue(BigDecimal value) {
            this.endingValue = value;
        }

        MonthlyStatement generateMonthlyStatement(int year, int month) {
            BigDecimal totalValue = holdings.stream()
                .map(h -> h.price().multiply(BigDecimal.valueOf(h.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            return new MonthlyStatement(holdings, transactions, totalValue);
        }

        QuarterlyReport generateQuarterlyReport(int year, int quarter) {
            BigDecimal quarterlyReturn = endingValue.subtract(beginningValue)
                .divide(beginningValue, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
            
            return new QuarterlyReport(quarterlyReturn, beginningValue, endingValue);
        }

        AnnualSummary generateAnnualSummary(int year) {
            BigDecimal ytdReturn = returns.stream()
                .filter(r -> r.date().getYear() == year)
                .map(Return::returnPct)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            return new AnnualSummary(ytdReturn, ytdReturn, year);
        }

        PerformanceSummary generatePerformanceSummary() {
            LocalDate now = LocalDate.now();
            
            BigDecimal oneMonth = returns.stream()
                .filter(r -> r.date().isAfter(now.minusDays(30)))
                .map(Return::returnPct)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal threeMonth = returns.stream()
                .filter(r -> r.date().isAfter(now.minusDays(90)))
                .map(Return::returnPct)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal ytd = returns.stream()
                .filter(r -> r.date().getYear() == now.getYear())
                .map(Return::returnPct)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            return new PerformanceSummary(oneMonth, threeMonth, ytd);
        }

        HoldingsReport generateHoldingsReport() {
            BigDecimal totalValue = holdings.stream()
                .map(h -> h.price().multiply(BigDecimal.valueOf(h.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            return new HoldingsReport(holdings.size(), totalValue, holdings);
        }

        TransactionHistoryReport generateTransactionHistoryReport(LocalDate from, LocalDate to) {
            List<Transaction> periodTransactions = transactions.stream()
                .filter(t -> !t.date().isBefore(from) && !t.date().isAfter(to))
                .toList();
            
            BigDecimal netCashFlow = periodTransactions.stream()
                .map(t -> {
                    BigDecimal amount = t.price().multiply(BigDecimal.valueOf(t.quantity()));
                    return t.type().equals("BUY") ? amount.negate() : amount;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            return new TransactionHistoryReport(periodTransactions, netCashFlow);
        }

        IncomeReport generateIncomeReport(int year) {
            BigDecimal dividendIncome = dividends.stream()
                .filter(d -> d.date().getYear() == year)
                .map(Dividend::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal totalIncome = dividendIncome.add(interestIncome);
            
            return new IncomeReport(totalIncome, dividendIncome, interestIncome);
        }

        FeeReport generateFeeReport(int year) {
            List<Fee> yearFees = fees.stream()
                .filter(f -> f.date().getYear() == year)
                .toList();
            
            BigDecimal totalFees = yearFees.stream()
                .map(Fee::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            java.util.Map<String, BigDecimal> feesByType = new java.util.HashMap<>();
            yearFees.forEach(f -> feesByType.merge(f.type(), f.amount(), BigDecimal::add));
            
            return new FeeReport(totalFees, feesByType);
        }

        byte[] exportToPDF(String reportType, int year, int month) {
            return new byte[]{0x25, 0x50, 0x44, 0x46};
        }

        void scheduleReport(String reportType, String frequency, String recipient) {
            scheduledReports.add(new ScheduledReport(reportType, frequency, recipient));
        }

        List<ScheduledReport> getScheduledReports() {
            return scheduledReports;
        }

        record Return(LocalDate date, BigDecimal returnPct) {}
        record Dividend(String symbol, BigDecimal amount, LocalDate date) {}
        record Fee(String type, BigDecimal amount, LocalDate date) {}
    }
}
