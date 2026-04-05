package com.ghatana.products.finance.domains.phr.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@DisplayName("Portfolio Transactions Tests")
class PortfolioTransactionsTest {
    private TransactionService service;

    @BeforeEach
    void setUp() {
        service = new TransactionService();
    }

    @Test
    @DisplayName("Should record buy transaction")
    void shouldRecordBuyTransaction() {
        Transaction txn = new Transaction("txn-1", "BUY", "AAPL", 100L, BigDecimal.valueOf(150.00), LocalDate.now());
        service.recordTransaction(txn);
        assertThat(service.getTransactions()).hasSize(1);
    }

    @Test
    @DisplayName("Should record sell transaction")
    void shouldRecordSellTransaction() {
        Transaction txn = new Transaction("txn-1", "SELL", "AAPL", 50L, BigDecimal.valueOf(155.00), LocalDate.now());
        service.recordTransaction(txn);
        assertThat(service.getTransactions()).hasSize(1);
    }

    @Test
    @DisplayName("Should calculate transaction costs")
    void shouldCalculateTransactionCosts() {
        Transaction txn = new Transaction("txn-1", "BUY", "AAPL", 100L, BigDecimal.valueOf(150.00), LocalDate.now());
        BigDecimal cost = service.calculateTransactionCost(txn, BigDecimal.valueOf(0.001));
        assertThat(cost).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should track cash flows")
    void shouldTrackCashFlows() {
        service.recordTransaction(new Transaction("txn-1", "BUY", "AAPL", 100L, BigDecimal.valueOf(150.00), LocalDate.now()));
        service.recordTransaction(new Transaction("txn-2", "SELL", "AAPL", 50L, BigDecimal.valueOf(155.00), LocalDate.now()));
        BigDecimal netCashFlow = service.calculateNetCashFlow();
        assertThat(netCashFlow).isNotNull();
    }

    @Test
    @DisplayName("Should filter transactions by date range")
    void shouldFilterTransactionsByDateRange() {
        service.recordTransaction(new Transaction("txn-1", "BUY", "AAPL", 100L, BigDecimal.valueOf(150.00), LocalDate.now().minusDays(10)));
        service.recordTransaction(new Transaction("txn-2", "BUY", "GOOGL", 50L, BigDecimal.valueOf(2800.00), LocalDate.now()));
        List<Transaction> recent = service.getTransactionsBetween(LocalDate.now().minusDays(5), LocalDate.now());
        assertThat(recent).hasSize(1);
    }

    @Test
    @DisplayName("Should filter transactions by symbol")
    void shouldFilterTransactionsBySymbol() {
        service.recordTransaction(new Transaction("txn-1", "BUY", "AAPL", 100L, BigDecimal.valueOf(150.00), LocalDate.now()));
        service.recordTransaction(new Transaction("txn-2", "BUY", "GOOGL", 50L, BigDecimal.valueOf(2800.00), LocalDate.now()));
        List<Transaction> appleTransactions = service.getTransactionsBySymbol("AAPL");
        assertThat(appleTransactions).hasSize(1);
    }

    @Test
    @DisplayName("Should calculate turnover ratio")
    void shouldCalculateTurnoverRatio() {
        service.recordTransaction(new Transaction("txn-1", "BUY", "AAPL", 100L, BigDecimal.valueOf(150.00), LocalDate.now()));
        service.recordTransaction(new Transaction("txn-2", "SELL", "AAPL", 50L, BigDecimal.valueOf(155.00), LocalDate.now()));
        BigDecimal avgPortfolioValue = BigDecimal.valueOf(100000.00);
        double turnover = service.calculateTurnoverRatio(avgPortfolioValue);
        assertThat(turnover).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("Should generate transaction report")
    void shouldGenerateTransactionReport() {
        service.recordTransaction(new Transaction("txn-1", "BUY", "AAPL", 100L, BigDecimal.valueOf(150.00), LocalDate.now()));
        service.recordTransaction(new Transaction("txn-2", "SELL", "AAPL", 50L, BigDecimal.valueOf(155.00), LocalDate.now()));
        TransactionReport report = service.generateReport(LocalDate.now().minusDays(30), LocalDate.now());
        assertThat(report.totalTransactions()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should export transactions to CSV")
    void shouldExportTransactionsToCSV() {
        service.recordTransaction(new Transaction("txn-1", "BUY", "AAPL", 100L, BigDecimal.valueOf(150.00), LocalDate.now()));
        String csv = service.exportToCSV();
        assertThat(csv).contains("AAPL").contains("BUY");
    }

    @Test
    @DisplayName("Should validate transaction data")
    void shouldValidateTransactionData() {
        Transaction txn = new Transaction("txn-1", "BUY", "AAPL", 100L, BigDecimal.valueOf(150.00), LocalDate.now());
        boolean valid = service.validateTransaction(txn);
        assertThat(valid).isTrue();
    }

    record Transaction(String txnId, String type, String symbol, long quantity, BigDecimal price, LocalDate date) {}
    record TransactionReport(int totalTransactions, BigDecimal totalValue) {}

    static class TransactionService {
        private final List<Transaction> transactions = new java.util.ArrayList<>();

        void recordTransaction(Transaction txn) {
            transactions.add(txn);
        }

        List<Transaction> getTransactions() {
            return transactions;
        }

        BigDecimal calculateTransactionCost(Transaction txn, BigDecimal feeRate) {
            BigDecimal value = txn.price().multiply(BigDecimal.valueOf(txn.quantity()));
            return value.multiply(feeRate);
        }

        BigDecimal calculateNetCashFlow() {
            BigDecimal inflow = transactions.stream()
                .filter(t -> t.type().equals("SELL"))
                .map(t -> t.price().multiply(BigDecimal.valueOf(t.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal outflow = transactions.stream()
                .filter(t -> t.type().equals("BUY"))
                .map(t -> t.price().multiply(BigDecimal.valueOf(t.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            return inflow.subtract(outflow);
        }

        List<Transaction> getTransactionsBetween(LocalDate from, LocalDate to) {
            return transactions.stream()
                .filter(t -> !t.date().isBefore(from) && !t.date().isAfter(to))
                .toList();
        }

        List<Transaction> getTransactionsBySymbol(String symbol) {
            return transactions.stream()
                .filter(t -> t.symbol().equals(symbol))
                .toList();
        }

        double calculateTurnoverRatio(BigDecimal avgPortfolioValue) {
            BigDecimal totalTraded = transactions.stream()
                .map(t -> t.price().multiply(BigDecimal.valueOf(t.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            return totalTraded.divide(avgPortfolioValue, 4, java.math.RoundingMode.HALF_UP).doubleValue();
        }

        TransactionReport generateReport(LocalDate from, LocalDate to) {
            List<Transaction> periodTransactions = getTransactionsBetween(from, to);
            BigDecimal totalValue = periodTransactions.stream()
                .map(t -> t.price().multiply(BigDecimal.valueOf(t.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new TransactionReport(periodTransactions.size(), totalValue);
        }

        String exportToCSV() {
            return transactions.stream()
                .map(t -> String.format("%s,%s,%s,%d,%s,%s", 
                    t.txnId(), t.type(), t.symbol(), t.quantity(), t.price(), t.date()))
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
        }

        boolean validateTransaction(Transaction txn) {
            return txn.quantity() > 0 && txn.price().compareTo(BigDecimal.ZERO) > 0;
        }
    }
}
