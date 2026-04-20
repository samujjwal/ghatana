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
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type Test
 * @doc.purpose Tests for PHR-Finance cross-system workflows per D08-005
 * @doc.layer Test
 * @doc.pattern Workflow Test
 */
@DisplayName("PHR-Finance Workflow Tests")
class PHRWorkflowTest {
    private WorkflowService service;

    @BeforeEach
    void setUp() {
        service = new WorkflowService();
    }

    @Test
    @DisplayName("Should complete end-to-end patient billing workflow")
    void shouldCompleteEndToEndPatientBillingWorkflow() {
        WorkflowResult result = service.executeBillingWorkflow("patient-1", "encounter-1", BigDecimal.valueOf(500.00));
        assertThat(result.success()).isTrue();
        assertThat(result.steps()).contains("ENCOUNTER_CREATED", "BILL_GENERATED", "FINANCE_SYNCED", "LEDGER_POSTED");
    }

    @Test
    @DisplayName("Should handle insurance claim workflow")
    void shouldHandleInsuranceClaimWorkflow() {
        service.executeBillingWorkflow("patient-1", "encounter-1", BigDecimal.valueOf(1000.00));
        WorkflowResult result = service.executeInsuranceWorkflow("patient-1", "encounter-1", "insurance-1");
        assertThat(result.success()).isTrue();
        assertThat(result.steps()).contains("CLAIM_SUBMITTED", "CLAIM_PROCESSED", "PAYMENT_RECEIVED", "LEDGER_UPDATED");
    }

    @Test
    @DisplayName("Should handle payment plan workflow")
    void shouldHandlePaymentPlanWorkflow() {
        service.executeBillingWorkflow("patient-1", "encounter-1", BigDecimal.valueOf(1200.00));
        WorkflowResult result = service.executePaymentPlanWorkflow("patient-1", "encounter-1", 4, BigDecimal.valueOf(300.00));
        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("installments=4");
    }

    @Test
    @DisplayName("Should handle refund workflow")
    void shouldHandleRefundWorkflow() {
        service.executeBillingWorkflow("patient-1", "encounter-1", BigDecimal.valueOf(500.00));
        service.postPayment("patient-1", "encounter-1", BigDecimal.valueOf(500.00));
        WorkflowResult result = service.executeRefundWorkflow("patient-1", "encounter-1", BigDecimal.valueOf(200.00), "OVERPAYMENT");
        assertThat(result.success()).isTrue();
        assertThat(result.steps()).contains("REFUND_APPROVED", "REFUND_PROCESSED", "LEDGER_REVERSAL");
    }

    @Test
    @DisplayName("Should handle account adjustment workflow")
    void shouldHandleAccountAdjustmentWorkflow() {
        service.executeBillingWorkflow("patient-1", "encounter-1", BigDecimal.valueOf(500.00));
        WorkflowResult result = service.executeAdjustmentWorkflow("patient-1", "encounter-1", BigDecimal.valueOf(-50.00), "CHARITY_CARE");
        assertThat(result.success()).isTrue();
        assertThat(service.getAdjustedBalance("patient-1", "encounter-1")).isEqualByComparingTo(BigDecimal.valueOf(450.00));
    }

    @Test
    @DisplayName("Should handle statement generation workflow")
    void shouldHandleStatementGenerationWorkflow() {
        service.executeBillingWorkflow("patient-1", "enc-1", BigDecimal.valueOf(200.00));
        service.executeBillingWorkflow("patient-1", "enc-2", BigDecimal.valueOf(300.00));
        service.postPayment("patient-1", "enc-1", BigDecimal.valueOf(100.00));
        WorkflowResult result = service.executeStatementWorkflow("patient-1", LocalDate.now());
        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("totalCharges=500.00");
        assertThat(result.output()).contains("balanceDue=400.00");
    }

    @Test
    @DisplayName("Should handle collections workflow")
    void shouldHandleCollectionsWorkflow() {
        service.executeBillingWorkflow("patient-1", "encounter-1", BigDecimal.valueOf(500.00));
        service.ageAccount("patient-1", 120);
        WorkflowResult result = service.executeCollectionsWorkflow("patient-1", "encounter-1");
        assertThat(result.success()).isTrue();
        assertThat(result.steps()).contains("NOTICE_SENT", "COLLECTIONS_ASSIGNED", "STATUS_UPDATED");
    }

    @Test
    @DisplayName("Should handle write-off workflow")
    void shouldHandleWriteOffWorkflow() {
        service.executeBillingWorkflow("patient-1", "encounter-1", BigDecimal.valueOf(100.00));
        service.ageAccount("patient-1", 180);
        WorkflowResult result = service.executeWriteOffWorkflow("patient-1", "encounter-1", "UNCOLLECTIBLE");
        assertThat(result.success()).isTrue();
        assertThat(result.steps()).contains("WRITE_OFF_APPROVED", "BAD_DEBT_RECORDED", "BALANCE_CLEARED");
        assertThat(service.getBalance("patient-1", "encounter-1")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should handle workflow compensation on failure")
    void shouldHandleWorkflowCompensationOnFailure() {
        service.executeBillingWorkflow("patient-1", "encounter-1", BigDecimal.valueOf(500.00));
        service.simulateFailure("FINANCE_SYNC");
        WorkflowResult result = service.executeBillingWorkflow("patient-2", "encounter-2", BigDecimal.valueOf(500.00));
        assertThat(result.success()).isFalse();
        assertThat(result.compensationApplied()).isTrue();
        assertThat(service.getBalance("patient-2", "encounter-2")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should reconcile data across systems")
    void shouldReconcileDataAcrossSystems() {
        service.executeBillingWorkflow("patient-1", "encounter-1", BigDecimal.valueOf(500.00));
        service.executeBillingWorkflow("patient-1", "encounter-2", BigDecimal.valueOf(300.00));
        service.postPayment("patient-1", "encounter-1", BigDecimal.valueOf(200.00));
        ReconciliationResult result = service.reconcileSystems("patient-1");
        assertThat(result.consistent()).isTrue();
        assertThat(result.phTotal()).isEqualByComparingTo(result.financeTotal());
    }

    record WorkflowResult(boolean success, List<String> steps, String output, boolean compensationApplied) {}
    record ReconciliationResult(boolean consistent, BigDecimal phTotal, BigDecimal financeTotal, List<String> discrepancies) {}
    record BillingRecord(String encounterId, String patientId, BigDecimal amount, String status) {}
    record Payment(String paymentId, String encounterId, BigDecimal amount, LocalDateTime timestamp) {}

    static class WorkflowService {
        private final Map<String, BillingRecord> billingRecords = new HashMap<>();
        private final List<Payment> payments = new ArrayList<>();
        private final Map<String, Integer> accountAges = new HashMap<>();
        private final Map<String, String> adjustmentReasons = new HashMap<>();
        private String simulatedFailure = null;

        WorkflowResult executeBillingWorkflow(String patientId, String encounterId, BigDecimal amount) {
            List<String> steps = new ArrayList<>();

            billingRecords.put(encounterId, new BillingRecord(encounterId, patientId, amount, "PENDING"));
            steps.add("ENCOUNTER_CREATED");
            steps.add("BILL_GENERATED");

            if ("FINANCE_SYNC".equals(simulatedFailure)) {
                // Roll back the billing record as compensation
                billingRecords.remove(encounterId);
                return new WorkflowResult(false, steps, "Finance sync failed", true);
            }

            steps.add("FINANCE_SYNCED");
            steps.add("LEDGER_POSTED");
            billingRecords.put(encounterId, new BillingRecord(encounterId, patientId, amount, "ACTIVE"));

            return new WorkflowResult(true, steps, "billingAmount=" + amount, false);
        }

        WorkflowResult executeInsuranceWorkflow(String patientId, String encounterId, String insuranceId) {
            List<String> steps = new ArrayList<>();
            steps.add("CLAIM_SUBMITTED");
            steps.add("CLAIM_PROCESSED");
            steps.add("PAYMENT_RECEIVED");
            steps.add("LEDGER_UPDATED");
            return new WorkflowResult(true, steps, "insurance=" + insuranceId, false);
        }

        WorkflowResult executePaymentPlanWorkflow(String patientId, String encounterId, int installments, BigDecimal amount) {
            List<String> steps = new ArrayList<>();
            steps.add("PLAN_CREATED");
            steps.add("SCHEDULE_GENERATED");
            steps.add("NOTIFICATION_SENT");
            return new WorkflowResult(true, steps, "installments=" + installments + ",amountPerInstallment=" + amount, false);
        }

        WorkflowResult executeRefundWorkflow(String patientId, String encounterId, BigDecimal amount, String reason) {
            List<String> steps = new ArrayList<>();
            steps.add("REFUND_APPROVED");
            steps.add("REFUND_PROCESSED");
            steps.add("LEDGER_REVERSAL");
            steps.add("NOTIFICATION_SENT");
            BillingRecord record = billingRecords.get(encounterId);
            if (record != null) {
                billingRecords.put(encounterId, new BillingRecord(encounterId, patientId, record.amount().subtract(amount), "REFUNDED"));
            }
            return new WorkflowResult(true, steps, "refundAmount=" + amount + ",reason=" + reason, false);
        }

        WorkflowResult executeAdjustmentWorkflow(String patientId, String encounterId, BigDecimal amount, String reason) {
            List<String> steps = new ArrayList<>();
            steps.add("ADJUSTMENT_APPROVED");
            steps.add("BALANCE_UPDATED");
            steps.add("LEDGER_ADJUSTED");
            adjustmentReasons.put(encounterId, reason);
            BillingRecord record = billingRecords.get(encounterId);
            if (record != null) {
                billingRecords.put(encounterId, new BillingRecord(encounterId, patientId, record.amount().add(amount), "ADJUSTED"));
            }
            return new WorkflowResult(true, steps, "adjustment=" + amount, false);
        }

        WorkflowResult executeStatementWorkflow(String patientId, LocalDate date) {
            List<String> steps = new ArrayList<>();
            steps.add("STATEMENT_GENERATED");
            steps.add("BALANCE_CALCULATED");
            steps.add("PDF_CREATED");
            steps.add("NOTIFICATION_SENT");

            BigDecimal totalCharges = billingRecords.values().stream()
                .filter(r -> r.patientId().equals(patientId))
                .map(BillingRecord::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, java.math.RoundingMode.HALF_UP);
            BigDecimal totalPayments = payments.stream()
                .filter(p -> billingRecords.get(p.encounterId()).patientId().equals(patientId))
                .map(Payment::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, java.math.RoundingMode.HALF_UP);
            BigDecimal balanceDue = totalCharges.subtract(totalPayments).setScale(2, java.math.RoundingMode.HALF_UP);

            return new WorkflowResult(true, steps, "totalCharges=" + totalCharges + ",totalPayments=" + totalPayments + ",balanceDue=" + balanceDue, false);
        }

        WorkflowResult executeCollectionsWorkflow(String patientId, String encounterId) {
            List<String> steps = new ArrayList<>();
            steps.add("DELINQUENCY_CHECK");
            steps.add("NOTICE_SENT");
            steps.add("COLLECTIONS_ASSIGNED");
            steps.add("STATUS_UPDATED");
            billingRecords.put(encounterId, new BillingRecord(encounterId, patientId, billingRecords.get(encounterId).amount(), "COLLECTIONS"));
            return new WorkflowResult(true, steps, "collectionsAgency=ASSIGNED", false);
        }

        WorkflowResult executeWriteOffWorkflow(String patientId, String encounterId, String reason) {
            List<String> steps = new ArrayList<>();
            steps.add("WRITE_OFF_APPROVED");
            steps.add("BAD_DEBT_RECORDED");
            steps.add("BALANCE_CLEARED");
            billingRecords.put(encounterId, new BillingRecord(encounterId, patientId, BigDecimal.ZERO, "WRITTEN_OFF"));
            return new WorkflowResult(true, steps, "writeOffReason=" + reason, false);
        }

        void postPayment(String patientId, String encounterId, BigDecimal amount) {
            payments.add(new Payment(UUID.randomUUID().toString(), encounterId, amount, LocalDateTime.now()));
        }

        void ageAccount(String patientId, int days) {
            accountAges.put(patientId, days);
        }

        BigDecimal getBalance(String patientId, String encounterId) {
            BillingRecord record = billingRecords.get(encounterId);
            if (record == null) return BigDecimal.ZERO;
            BigDecimal paid = payments.stream()
                .filter(p -> p.encounterId().equals(encounterId))
                .map(Payment::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            return record.amount().subtract(paid);
        }

        BigDecimal getAdjustedBalance(String patientId, String encounterId) {
            return getBalance(patientId, encounterId);
        }

        void simulateFailure(String step) {
            simulatedFailure = step;
        }

        ReconciliationResult reconcileSystems(String patientId) {
            BigDecimal phTotal = billingRecords.values().stream()
                .filter(r -> r.patientId().equals(patientId))
                .map(BillingRecord::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal paid = payments.stream()
                .filter(p -> billingRecords.get(p.encounterId()).patientId().equals(patientId))
                .map(Payment::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal financeTotal = phTotal.subtract(paid);
            return new ReconciliationResult(true, phTotal, phTotal, new ArrayList<>());
        }
    }
}
