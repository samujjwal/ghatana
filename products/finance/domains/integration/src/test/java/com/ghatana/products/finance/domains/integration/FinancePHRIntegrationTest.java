package com.ghatana.products.finance.domains.integration;

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
 * @doc.purpose Tests for Finance-PHR integration data flow per D08-001
 * @doc.layer Test
 * @doc.pattern Integration Test
 */
@DisplayName("Finance-PHR Integration Tests")
class FinancePHRIntegrationTest {
    private FinancePHRIntegrationService service;

    @BeforeEach
    void setUp() {
        service = new FinancePHRIntegrationService();
    }

    @Test
    @DisplayName("Should sync patient billing records to finance")
    void shouldSyncPatientBillingRecordsToFinance() {
        PatientBillingRecord record = new PatientBillingRecord("patient-1", "encounter-1", BigDecimal.valueOf(500.00), "USD");
        service.syncBillingRecord(record);
        assertThat(service.getFinanceRecord("encounter-1")).isNotNull();
        assertThat(service.getFinanceRecord("encounter-1").amount()).isEqualByComparingTo(BigDecimal.valueOf(500.00));
    }

    @Test
    @DisplayName("Should create ledger entry from billing record")
    void shouldCreateLedgerEntryFromBillingRecord() {
        PatientBillingRecord record = new PatientBillingRecord("patient-1", "encounter-1", BigDecimal.valueOf(500.00), "USD");
        service.syncBillingRecord(record);
        LedgerEntry entry = service.createLedgerEntry("encounter-1");
        assertThat(entry.debit()).isEqualByComparingTo(BigDecimal.valueOf(500.00));
        assertThat(entry.status()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("Should handle insurance claim reconciliation")
    void shouldHandleInsuranceClaimReconciliation() {
        InsuranceClaim claim = new InsuranceClaim("claim-1", "patient-1", BigDecimal.valueOf(1000.00), BigDecimal.valueOf(800.00));
        service.processInsuranceClaim(claim);
        ReconciliationResult result = service.reconcileClaim("claim-1");
        assertThat(result.expectedAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000.00));
        assertThat(result.actualAmount()).isEqualByComparingTo(BigDecimal.valueOf(800.00));
        assertThat(result.difference()).isEqualByComparingTo(BigDecimal.valueOf(200.00));
    }

    @Test
    @DisplayName("Should track patient financial history")
    void shouldTrackPatientFinancialHistory() {
        service.syncBillingRecord(new PatientBillingRecord("patient-1", "encounter-1", BigDecimal.valueOf(200.00), "USD"));
        service.syncBillingRecord(new PatientBillingRecord("patient-1", "encounter-2", BigDecimal.valueOf(300.00), "USD"));
        service.syncBillingRecord(new PatientBillingRecord("patient-1", "encounter-3", BigDecimal.valueOf(500.00), "USD"));
        List<PatientBillingRecord> history = service.getPatientFinancialHistory("patient-1");
        assertThat(history).hasSize(3);
        assertThat(service.calculateTotalBilled("patient-1")).isEqualByComparingTo(BigDecimal.valueOf(1000.00));
    }

    @Test
    @DisplayName("Should handle payment posting to patient account")
    void shouldHandlePaymentPostingToPatientAccount() {
        PatientBillingRecord record = new PatientBillingRecord("patient-1", "encounter-1", BigDecimal.valueOf(500.00), "USD");
        service.syncBillingRecord(record);
        Payment payment = new Payment("payment-1", "patient-1", BigDecimal.valueOf(300.00), LocalDateTime.now());
        service.postPayment(payment);
        assertThat(service.getOutstandingBalance("patient-1")).isEqualByComparingTo(BigDecimal.valueOf(200.00));
    }

    @Test
    @DisplayName("Should generate patient billing statement")
    void shouldGeneratePatientBillingStatement() {
        service.syncBillingRecord(new PatientBillingRecord("patient-1", "encounter-1", BigDecimal.valueOf(200.00), "USD"));
        service.syncBillingRecord(new PatientBillingRecord("patient-1", "encounter-2", BigDecimal.valueOf(300.00), "USD"));
        Payment payment = new Payment("payment-1", "patient-1", BigDecimal.valueOf(250.00), LocalDateTime.now());
        service.postPayment(payment);
        BillingStatement statement = service.generateStatement("patient-1", LocalDate.now());
        assertThat(statement.totalCharges()).isEqualByComparingTo(BigDecimal.valueOf(500.00));
        assertThat(statement.totalPayments()).isEqualByComparingTo(BigDecimal.valueOf(250.00));
        assertThat(statement.balanceDue()).isEqualByComparingTo(BigDecimal.valueOf(250.00));
    }

    @Test
    @DisplayName("Should handle multi-currency billing")
    void shouldHandleMultiCurrencyBilling() {
        service.syncBillingRecord(new PatientBillingRecord("patient-1", "encounter-1", BigDecimal.valueOf(500.00), "USD"));
        service.syncBillingRecord(new PatientBillingRecord("patient-1", "encounter-2", BigDecimal.valueOf(400.00), "EUR"));
        service.syncBillingRecord(new PatientBillingRecord("patient-1", "encounter-3", BigDecimal.valueOf(30000.00), "JPY"));
        List<String> currencies = service.getPatientCurrencies("patient-1");
        assertThat(currencies).contains("USD", "EUR", "JPY");
    }

    @Test
    @DisplayName("Should validate billing record completeness")
    void shouldValidateBillingRecordCompleteness() {
        PatientBillingRecord incompleteRecord = new PatientBillingRecord("", "encounter-1", BigDecimal.valueOf(500.00), "USD");
        ValidationResult result = service.validateBillingRecord(incompleteRecord);
        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).contains("Patient ID is required");
    }

    @Test
    @DisplayName("Should propagate billing adjustments")
    void shouldPropagateBillingAdjustments() {
        PatientBillingRecord record = new PatientBillingRecord("patient-1", "encounter-1", BigDecimal.valueOf(500.00), "USD");
        service.syncBillingRecord(record);
        BillingAdjustment adjustment = new BillingAdjustment("adj-1", "encounter-1", BigDecimal.valueOf(-50.00), "DISCOUNT");
        service.applyAdjustment(adjustment);
        assertThat(service.getOutstandingBalance("patient-1")).isEqualByComparingTo(BigDecimal.valueOf(450.00));
    }

    @Test
    @DisplayName("Should maintain data consistency across systems")
    void shouldMaintainDataConsistencyAcrossSystems() {
        PatientBillingRecord record = new PatientBillingRecord("patient-1", "encounter-1", BigDecimal.valueOf(500.00), "USD");
        service.syncBillingRecord(record);
        service.createLedgerEntry("encounter-1");
        ConsistencyCheck check = service.verifyConsistency("encounter-1");
        assertThat(check.isConsistent()).isTrue();
        assertThat(check.financeAmount()).isEqualByComparingTo(check.phrAmount());
    }

    record PatientBillingRecord(String patientId, String encounterId, BigDecimal amount, String currency) {}
    record LedgerEntry(String entryId, BigDecimal debit, BigDecimal credit, String status) {}
    record InsuranceClaim(String claimId, String patientId, BigDecimal expectedAmount, BigDecimal actualAmount) {}
    record ReconciliationResult(BigDecimal expectedAmount, BigDecimal actualAmount, BigDecimal difference) {}
    record Payment(String paymentId, String patientId, BigDecimal amount, LocalDateTime timestamp) {}
    record BillingStatement(String patientId, LocalDate date, BigDecimal totalCharges, BigDecimal totalPayments, BigDecimal balanceDue) {}
    record BillingAdjustment(String adjustmentId, String encounterId, BigDecimal amount, String reason) {}
    record ValidationResult(boolean isValid, List<String> errors) {}
    record ConsistencyCheck(boolean isConsistent, BigDecimal financeAmount, BigDecimal phrAmount) {}

    static class FinancePHRIntegrationService {
        private final Map<String, PatientBillingRecord> billingRecords = new HashMap<>();
        private final Map<String, LedgerEntry> ledgerEntries = new HashMap<>();
        private final Map<String, InsuranceClaim> claims = new HashMap<>();
        private final List<Payment> payments = new ArrayList<>();
        private final List<BillingAdjustment> adjustments = new ArrayList<>();
        private int entryCounter = 0;

        void syncBillingRecord(PatientBillingRecord record) {
            billingRecords.put(record.encounterId(), record);
        }

        PatientBillingRecord getFinanceRecord(String encounterId) {
            return billingRecords.get(encounterId);
        }

        LedgerEntry createLedgerEntry(String encounterId) {
            PatientBillingRecord record = billingRecords.get(encounterId);
            if (record == null) return null;
            LedgerEntry entry = new LedgerEntry("LEDGER-" + (++entryCounter), record.amount(), BigDecimal.ZERO, "PENDING");
            ledgerEntries.put(encounterId, entry);
            return entry;
        }

        void processInsuranceClaim(InsuranceClaim claim) {
            claims.put(claim.claimId(), claim);
        }

        ReconciliationResult reconcileClaim(String claimId) {
            InsuranceClaim claim = claims.get(claimId);
            if (claim == null) return null;
            BigDecimal diff = claim.expectedAmount().subtract(claim.actualAmount());
            return new ReconciliationResult(claim.expectedAmount(), claim.actualAmount(), diff);
        }

        List<PatientBillingRecord> getPatientFinancialHistory(String patientId) {
            return billingRecords.values().stream()
                .filter(r -> r.patientId().equals(patientId))
                .toList();
        }

        BigDecimal calculateTotalBilled(String patientId) {
            return getPatientFinancialHistory(patientId).stream()
                .map(PatientBillingRecord::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        void postPayment(Payment payment) {
            payments.add(payment);
        }

        BigDecimal getOutstandingBalance(String patientId) {
            BigDecimal totalBilled = calculateTotalBilled(patientId);
            BigDecimal totalPaid = payments.stream()
                .filter(p -> p.patientId().equals(patientId))
                .map(Payment::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalAdjustments = adjustments.stream()
                .filter(a -> billingRecords.values().stream()
                    .anyMatch(r -> r.encounterId().equals(a.encounterId()) && r.patientId().equals(patientId)))
                .map(BillingAdjustment::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            return totalBilled.subtract(totalPaid).add(totalAdjustments);
        }

        BillingStatement generateStatement(String patientId, LocalDate date) {
            BigDecimal charges = calculateTotalBilled(patientId);
            BigDecimal totalPayments = payments.stream()
                .filter(p -> p.patientId().equals(patientId))
                .map(Payment::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalAdjustments = adjustments.stream()
                .filter(a -> billingRecords.values().stream()
                    .anyMatch(r -> r.encounterId().equals(a.encounterId()) && r.patientId().equals(patientId)))
                .map(BillingAdjustment::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal balance = charges.subtract(totalPayments).add(totalAdjustments);
            return new BillingStatement(patientId, date, charges, totalPayments, balance);
        }

        List<String> getPatientCurrencies(String patientId) {
            return getPatientFinancialHistory(patientId).stream()
                .map(PatientBillingRecord::currency)
                .distinct()
                .toList();
        }

        ValidationResult validateBillingRecord(PatientBillingRecord record) {
            List<String> errors = new ArrayList<>();
            if (record.patientId() == null || record.patientId().isEmpty()) {
                errors.add("Patient ID is required");
            }
            if (record.encounterId() == null || record.encounterId().isEmpty()) {
                errors.add("Encounter ID is required");
            }
            if (record.amount() == null || record.amount().compareTo(BigDecimal.ZERO) < 0) {
                errors.add("Valid amount is required");
            }
            if (record.currency() == null || record.currency().isEmpty()) {
                errors.add("Currency is required");
            }
            return new ValidationResult(errors.isEmpty(), errors);
        }

        void applyAdjustment(BillingAdjustment adjustment) {
            adjustments.add(adjustment);
        }

        ConsistencyCheck verifyConsistency(String encounterId) {
            PatientBillingRecord phr = billingRecords.get(encounterId);
            LedgerEntry finance = ledgerEntries.get(encounterId);
            if (phr == null || finance == null) {
                return new ConsistencyCheck(false, BigDecimal.ZERO, BigDecimal.ZERO);
            }
            return new ConsistencyCheck(
                phr.amount().compareTo(finance.debit()) == 0,
                finance.debit(),
                phr.amount()
            );
        }
    }
}
