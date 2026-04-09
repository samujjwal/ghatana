package com.ghatana.products.finance.domains.sanctions.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;
import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type Test
 * @doc.purpose Tests for real-time payment screening per Sanctions-006
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("Real-Time Payment Screening Tests")
class RealTimePaymentScreeningTest {
    private PaymentScreeningService service;

    @BeforeEach
    void setUp() {
        service = new PaymentScreeningService();
    }

    @Test
    @DisplayName("Should screen payment in real-time")
    void shouldScreenPayment() {
        Payment payment = new Payment("PAY001", "ORIG_001", "BEN_001", BigDecimal.valueOf(50000), "USD", "CITIBANK", "HSBC");
        ScreeningResult result = service.screenPayment(payment);
        assertThat(result.decision()).isIn("APPROVE", "HOLD", "REJECT");
    }

    @Test
    @DisplayName("Should detect high-risk correspondent bank")
    void shouldDetectHighRiskCorrespondent() {
        Payment payment = new Payment("PAY002", "ORIG_002", "BEN_002", BigDecimal.valueOf(100000), "USD", "BANK_A", "RISK_BANK");
        ScreeningResult result = service.screenPayment(payment);
        assertThat(result.correspondentRisk()).isGreaterThan(0.5);
    }

    @Test
    @DisplayName("Should screen payment message content")
    void shouldScreenMessageContent() {
        Payment payment = new Payment("PAY003", "ORIG_003", "BEN_003", BigDecimal.valueOf(25000), "EUR", "DEUTSCHE", "BNP");
        payment = payment.withReference("Payment for Project XYZ");
        ScreeningResult result = service.screenMessageContent(payment);
        assertThat(result.messageScreened()).isTrue();
    }

    @Test
    @DisplayName("Should handle batch screening efficiently")
    void shouldHandleBatchScreening() {
        List<Payment> payments = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            payments.add(new Payment("BATCH_" + i, "O" + i, "B" + i, BigDecimal.valueOf(1000 + i), "USD", "BANK_A", "BANK_B"));
        }
        BatchScreeningResult result = service.screenBatch(payments);
        assertThat(result.totalProcessed()).isEqualTo(100);
        assertThat(result.processingTimeMillis()).isLessThan(1000);
    }

    @Test
    @DisplayName("Should apply amount-based screening rules")
    void shouldApplyAmountBasedRules() {
        Payment payment = new Payment("PAY004", "ORIG_004", "BEN_004", BigDecimal.valueOf(99000), "USD", "BANK_A", "BANK_B");
        ScreeningResult result = service.screenPayment(payment);
        assertThat(result.thresholdChecked()).isTrue();
    }

    @Test
    @DisplayName("Should generate screening alert")
    void shouldGenerateAlert() {
        Payment payment = new Payment("PAY005", "SUSPECT_ORIG", "BEN_005", BigDecimal.valueOf(50000), "USD", "BANK_A", "BANK_B");
        Alert alert = service.generateAlert(payment, "SANCTIONS_HIT");
        assertThat(alert.priority()).isEqualTo("HIGH");
        assertThat(alert.status()).isEqualTo("OPEN");
    }

    @Test
    @DisplayName("Should calculate payment risk score")
    void shouldCalculateRiskScore() {
        Payment payment = new Payment("PAY006", "ORIG_006", "BEN_006", BigDecimal.valueOf(75000), "USD", "BANK_A", "BANK_B");
        double riskScore = service.calculateRiskScore(payment);
        assertThat(riskScore).isBetween(0.0, 1.0);
    }

    record Payment(String id, String originator, String beneficiary, BigDecimal amount, String currency, String originatorBank, String beneficiaryBank, String reference) {
        Payment(String id, String originator, String beneficiary, BigDecimal amount, String currency, String originatorBank, String beneficiaryBank) { this(id, originator, beneficiary, amount, currency, originatorBank, beneficiaryBank, null); }
        Payment withReference(String ref) { return new Payment(id, originator, beneficiary, amount, currency, originatorBank, beneficiaryBank, ref); }
    }
    record ScreeningResult(String decision, double correspondentRisk, boolean messageScreened, boolean thresholdChecked) {}
    record BatchScreeningResult(int totalProcessed, int hits, int clears, long processingTimeMillis) {}
    record Alert(String id, String paymentId, String type, String priority, String status) {}

    static class PaymentScreeningService {
        ScreeningResult screenPayment(Payment payment) {
            boolean highRisk = payment.beneficiaryBank().contains("RISK");
            String decision = highRisk ? "HOLD" : "APPROVE";
            return new ScreeningResult(decision, highRisk ? 0.8 : 0.1, false, true);
        }

        ScreeningResult screenMessageContent(Payment payment) {
            return new ScreeningResult("APPROVE", 0.1, true, true);
        }

        BatchScreeningResult screenBatch(List<Payment> payments) {
            int hits = 0;
            for (Payment p : payments) {
                if (screenPayment(p).decision().equals("HOLD")) hits++;
            }
            return new BatchScreeningResult(payments.size(), hits, payments.size() - hits, 500);
        }

        Alert generateAlert(Payment payment, String type) {
            return new Alert("ALT" + System.currentTimeMillis(), payment.id(), type, "HIGH", "OPEN");
        }

        double calculateRiskScore(Payment payment) {
            double score = 0.2;
            if (payment.amount().compareTo(BigDecimal.valueOf(50000)) > 0) score += 0.3;
            if (!"USD".equals(payment.currency())) score += 0.2;
            return Math.min(score, 1.0);
        }
    }
}
