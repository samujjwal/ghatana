package com.ghatana.finance.integration;

import com.ghatana.platform.audit.AuditBusPort;
import com.ghatana.platform.audit.AuditEvent;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type Service
 * @doc.purpose Integration test suite for healthcare billing encounter close -> finance ledger posting
 * @doc.layer Integration Testing
 * @doc.pattern Port-Adapter
 */
public class HealthcareBillingToLedgerIntegrationTestSuiteService {

    public interface PhrBillingPort {
        String createEncounter(String patientId, String providerId, String currency, double amount) throws Exception;
        String closeEncounter(String encounterId) throws Exception;
    }

    public interface FinanceLedgerPort {
        boolean hasEntry(String ledgerEntryId) throws Exception;
        boolean isBalanced(String ledgerEntryId) throws Exception;
        String getPostingType(String ledgerEntryId) throws Exception;
    }

    private final javax.sql.DataSource dataSource;
    private final PhrBillingPort phrBilling;
    private final FinanceLedgerPort financeLedger;
    private final AuditBusPort audit;
    private final Executor executor;
    private final Counter passedCounter;
    private final Counter failedCounter;

    public HealthcareBillingToLedgerIntegrationTestSuiteService(
        javax.sql.DataSource dataSource,
        PhrBillingPort phrBilling,
        FinanceLedgerPort financeLedger,
        AuditBusPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource cannot be null");
        this.phrBilling = Objects.requireNonNull(phrBilling, "phrBilling cannot be null");
        this.financeLedger = Objects.requireNonNull(financeLedger, "financeLedger cannot be null");
        this.audit = Objects.requireNonNull(audit, "audit cannot be null");
        this.executor = Objects.requireNonNull(executor, "executor cannot be null");
        this.passedCounter = Counter.builder("integration.healthcare.billing_to_ledger.passed").register(registry);
        this.failedCounter = Counter.builder("integration.healthcare.billing_to_ledger.failed").register(registry);
    }

    /**
     * Runs an end-to-end scenario: create encounter in PHR, close encounter,
     * verify ledger posting exists and is balanced in Finance.
     */
    public Promise<TestResult> runEndToEndScenario(String patientId, String providerId, double amount, String currency) {
        return Promise.ofBlocking(executor, () -> {
            String runId = UUID.randomUUID().toString();
            long startedAt = System.currentTimeMillis();
            writeRun(runId, "RUNNING", null);

            try {
                String encounterId = phrBilling.createEncounter(patientId, providerId, currency, amount);
                String ledgerEntryId = phrBilling.closeEncounter(encounterId);

                if (!financeLedger.hasEntry(ledgerEntryId)) {
                    throw new IllegalStateException("Ledger entry not found: " + ledgerEntryId);
                }
                if (!financeLedger.isBalanced(ledgerEntryId)) {
                    throw new IllegalStateException("Ledger is not balanced for entry: " + ledgerEntryId);
                }
                String postingType = financeLedger.getPostingType(ledgerEntryId);
                if (!"CHARGE".equals(postingType)) {
                    throw new IllegalStateException("Expected CHARGE posting type, got: " + postingType);
                }

                passedCounter.increment();
                writeRun(runId, "PASSED", null);
                audit.emit(AuditEvent.builder()
                    .tenantId("integration")
                    .eventType("integration.billing_to_ledger.passed")
                    .principal("integration-suite")
                    .resourceType("patient")
                    .resourceId(patientId)
                    .success(true)
                    .timestamp(Instant.now())
                    .detail("encounterId", encounterId)
                    .detail("ledgerEntryId", ledgerEntryId)
                    .build());

                return new TestResult(runId, true, encounterId, ledgerEntryId, null,
                    System.currentTimeMillis() - startedAt);
            } catch (Exception exception) {
                failedCounter.increment();
                writeRun(runId, "FAILED", exception.getMessage());
                audit.emit(AuditEvent.builder()
                    .tenantId("integration")
                    .eventType("integration.billing_to_ledger.failed")
                    .principal("integration-suite")
                    .resourceType("patient")
                    .resourceId(patientId)
                    .success(false)
                    .timestamp(Instant.now())
                    .detail("error", exception.getMessage())
                    .build());
                return new TestResult(runId, false, null, null, exception.getMessage(),
                    System.currentTimeMillis() - startedAt);
            }
        });
    }

    private void writeRun(String runId, String status, String errorMessage) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 INSERT INTO phr_finance_integration_runs
                     (run_id, suite_name, status, error_message, recorded_at)
                 VALUES (?, ?, ?, ?, ?)
                 """)) {
            statement.setString(1, runId);
            statement.setString(2, "HealthcareBillingToLedger");
            statement.setString(3, status);
            statement.setString(4, errorMessage);
            statement.setLong(5, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException ignored) {
            // Integration telemetry persistence must not fail the main integration scenario.
        }
    }

    public record TestResult(
        String runId,
        boolean passed,
        String encounterId,
        String ledgerEntryId,
        String failure,
        long durationMs
    ) {}
}
