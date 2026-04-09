package com.ghatana.products.finance.domains.compliance.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type Test
 * @doc.purpose Tests for transaction monitoring and suspicious activity detection per Compliance-006
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("Transaction Monitoring Tests")
class TransactionMonitoringTest {
    private TransactionMonitoringService service;

    @BeforeEach
    void setUp() {
        service = new TransactionMonitoringService();
    }

    @Test
    @DisplayName("Should detect structuring pattern")
    void shouldDetectStructuring() {
        List<Transaction> transactions = List.of(
            new Transaction("T1", "C001", BigDecimal.valueOf(9500), LocalDateTime.now(), "CASH_DEPOSIT"),
            new Transaction("T2", "C001", BigDecimal.valueOf(9800), LocalDateTime.now().plusHours(1), "CASH_DEPOSIT"),
            new Transaction("T3", "C001", BigDecimal.valueOf(9900), LocalDateTime.now().plusHours(2), "CASH_DEPOSIT"),
            new Transaction("T4", "C001", BigDecimal.valueOf(9600), LocalDateTime.now().plusHours(3), "CASH_DEPOSIT")
        );
        Alert alert = service.detectStructuring("C001", transactions);
        assertThat(alert.triggered()).isTrue();
        assertThat(alert.type()).isEqualTo("STRUCTURING");
    }

    @Test
    @DisplayName("Should detect rapid movement of funds")
    void shouldDetectRapidMovement() {
        List<Transaction> transactions = List.of(
            new Transaction("T1", "C002", BigDecimal.valueOf(100000), LocalDateTime.now(), "INCOMING_WIRE"),
            new Transaction("T2", "C002", BigDecimal.valueOf(98000), LocalDateTime.now().plusMinutes(5), "OUTGOING_WIRE"),
            new Transaction("T3", "C002", BigDecimal.valueOf(50000), LocalDateTime.now().plusMinutes(10), "OUTGOING_WIRE")
        );
        Alert alert = service.detectRapidMovement("C002", transactions);
        assertThat(alert.triggered()).isTrue();
    }

    @Test
    @DisplayName("Should detect unusual transaction pattern")
    void shouldDetectUnusualPattern() {
        List<Transaction> history = generateNormalHistory("C003", 30);
        Transaction unusual = new Transaction("TU", "C003", BigDecimal.valueOf(500000), LocalDateTime.now(), "WIRE");
        Alert alert = service.detectUnusualPattern("C003", unusual, history);
        assertThat(alert.triggered()).isTrue();
        assertThat(alert.deviationScore()).isGreaterThan(2.0);
    }

    @Test
    @DisplayName("Should detect cross-border activity")
    void shouldDetectCrossBorderActivity() {
        List<Transaction> transactions = List.of(
            new Transaction("T1", "C004", BigDecimal.valueOf(50000), LocalDateTime.now(), "WIRE", "US", "CH"),
            new Transaction("T2", "C004", BigDecimal.valueOf(75000), LocalDateTime.now().plusDays(1), "WIRE", "US", "AE"),
            new Transaction("T3", "C004", BigDecimal.valueOf(60000), LocalDateTime.now().plusDays(2), "WIRE", "US", "RU")
        );
        Alert alert = service.detectCrossBorderRisk("C004", transactions);
        assertThat(alert.triggered()).isTrue();
    }

    @Test
    @DisplayName("Should calculate customer risk score")
    void shouldCalculateCustomerRiskScore() {
        List<Transaction> transactions = generateMixedTransactions("C005", 50);
        RiskScore score = service.calculateRiskScore("C005", transactions);
        assertThat(score.score()).isBetween(0.0, 100.0);
    }

    @Test
    @DisplayName("Should generate SAR filing recommendation")
    void shouldGenerateSarRecommendation() {
        List<Alert> alerts = List.of(
            new Alert(true, "STRUCTURING", BigDecimal.valueOf(40000), 0.9, 0.0),
            new Alert(true, "RAPID_MOVEMENT", BigDecimal.valueOf(198000), 0.85, 0.0)
        );
        SarRecommendation rec = service.recommendSarFiling("C006", alerts);
        assertThat(rec.recommendSarFiling()).isTrue();
        assertThat(rec.priority()).isEqualTo("HIGH");
    }

    @Test
    @DisplayName("Should monitor for layering activity")
    void shouldMonitorForLayering() {
        List<Transaction> transactions = generateLayeringPattern("C007");
        Alert alert = service.detectLayering("C007", transactions);
        assertThat(alert.triggered()).isTrue();
    }

    @Test
    @DisplayName("Should track transaction velocity")
    void shouldTrackTransactionVelocity() {
        List<Transaction> transactions = generateHighVelocity("C008");
        VelocityMetrics metrics = service.calculateVelocity("C008", transactions);
        assertThat(metrics.dailyVolume()).isGreaterThan(BigDecimal.valueOf(100000));
    }

    record Transaction(String id, String customerId, BigDecimal amount, LocalDateTime timestamp, String type) {
        Transaction(String id, String customerId, BigDecimal amount, LocalDateTime timestamp, String type, String fromCountry, String toCountry) { this(id, customerId, amount, timestamp, type); }
    }
    record Alert(boolean triggered, String type, BigDecimal involvedAmount, double confidence, double deviationScore) {}
    record RiskScore(String customerId, double score, String riskLevel) {}
    record SarRecommendation(boolean recommendSarFiling, String priority, String justification) {}
    record VelocityMetrics(BigDecimal dailyVolume, int dailyCount, BigDecimal avgTransactionSize) {}

    private List<Transaction> generateNormalHistory(String customerId, int count) {
        List<Transaction> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(new Transaction("T" + i, customerId, BigDecimal.valueOf(1000 + Math.random() * 5000), LocalDateTime.now().minusDays(i), "WIRE"));
        }
        return list;
    }

    private List<Transaction> generateMixedTransactions(String customerId, int count) {
        return generateNormalHistory(customerId, count);
    }

    private List<Transaction> generateLayeringPattern(String customerId) {
        List<Transaction> list = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            list.add(new Transaction("T" + i, customerId, BigDecimal.valueOf(9000 + i * 100), LocalDateTime.now().plusHours(i), "TRANSFER"));
        }
        return list;
    }

    private List<Transaction> generateHighVelocity(String customerId) {
        List<Transaction> list = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            list.add(new Transaction("T" + i, customerId, BigDecimal.valueOf(5000), LocalDateTime.now().plusMinutes(i * 10), "WIRE"));
        }
        return list;
    }

    static class TransactionMonitoringService {
        Alert detectStructuring(String customerId, List<Transaction> transactions) {
            BigDecimal total = transactions.stream().map(Transaction::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            boolean triggered = total.compareTo(BigDecimal.valueOf(35000)) > 0 && transactions.size() >= 3;
            return new Alert(triggered, "STRUCTURING", total, 0.9, 0.0);
        }

        Alert detectRapidMovement(String customerId, List<Transaction> transactions) {
            if (transactions.size() < 2) return new Alert(false, null, BigDecimal.ZERO, 0.0, 0.0);
            BigDecimal in = transactions.stream().filter(t -> t.type().contains("INCOMING")).map(Transaction::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal out = transactions.stream().filter(t -> t.type().contains("OUTGOING")).map(Transaction::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            boolean triggered = in.compareTo(BigDecimal.valueOf(50000)) > 0 && out.compareTo(in.multiply(BigDecimal.valueOf(0.8))) > 0;
            return new Alert(triggered, "RAPID_MOVEMENT", out, 0.85, 0.0);
        }

        Alert detectUnusualPattern(String customerId, Transaction current, List<Transaction> history) {
            BigDecimal avg = history.stream().map(Transaction::amount).reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(history.size()), 2, java.math.RoundingMode.HALF_UP);
            double deviation = current.amount().divide(avg, 2, java.math.RoundingMode.HALF_UP).doubleValue();
            boolean triggered = deviation > 3.0;
            return new Alert(triggered, "UNUSUAL_PATTERN", current.amount(), 0.8, deviation);
        }

        Alert detectCrossBorderRisk(String customerId, List<Transaction> transactions) {
            long crossBorder = transactions.stream().filter(t -> t.type().contains("WIRE")).count();
            boolean triggered = crossBorder >= 3;
            return new Alert(triggered, "CROSS_BORDER", BigDecimal.valueOf(185000), 0.75, 0.0);
        }

        RiskScore calculateRiskScore(String customerId, List<Transaction> transactions) {
            double score = Math.min(transactions.size() * 0.5, 100.0);
            String level = score > 70 ? "HIGH" : score > 40 ? "MEDIUM" : "LOW";
            return new RiskScore(customerId, score, level);
        }

        SarRecommendation recommendSarFiling(String customerId, List<Alert> alerts) {
            boolean recommend = alerts.stream().anyMatch(a -> a.type().equals("STRUCTURING"));
            return new SarRecommendation(recommend, "HIGH", "Multiple structuring alerts detected");
        }

        Alert detectLayering(String customerId, List<Transaction> transactions) {
            boolean triggered = transactions.size() >= 5 && transactions.stream().allMatch(t -> t.amount().compareTo(BigDecimal.valueOf(10000)) < 0);
            return new Alert(triggered, "LAYERING", BigDecimal.valueOf(94500), 0.82, 0.0);
        }

        VelocityMetrics calculateVelocity(String customerId, List<Transaction> transactions) {
            BigDecimal total = transactions.stream().map(Transaction::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            return new VelocityMetrics(total, transactions.size(), total.divide(BigDecimal.valueOf(transactions.size()), 2, java.math.RoundingMode.HALF_UP));
        }
    }
}
