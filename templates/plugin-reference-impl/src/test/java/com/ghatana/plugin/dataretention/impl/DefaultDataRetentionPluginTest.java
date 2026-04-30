package com.ghatana.plugin.dataretention.impl;

import com.ghatana.kernel.test.EventloopTestBase;
import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.plugin.dataretention.DataRetentionPlugin;
import com.ghatana.plugin.dataretention.DataRetentionPlugin.ApprovalOutcome;
import com.ghatana.plugin.dataretention.DataRetentionPlugin.DataAccessedEvent;
import com.ghatana.plugin.dataretention.DataRetentionPlugin.DataCreatedEvent;
import com.ghatana.plugin.dataretention.DataRetentionPlugin.DataRecord;
import com.ghatana.plugin.dataretention.DataRetentionPlugin.Decision;
import com.ghatana.plugin.dataretention.DataRetentionPlugin.RetentionDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * DefaultDataRetentionPluginTest — reference plugin test.
 *
 * <p>Shows the canonical test patterns for all three plugin patterns:</p>
 * <ul>
 *   <li>Pattern 1: pure policy evaluation — assert deterministic outcomes</li>
 *   <li>Pattern 2: event-driven — fire events and assert side-effects</li>
 *   <li>Pattern 3: human approval — verify escalation flow</li>
 *   <li>Tenant isolation — verify state does not bleed across tenants</li>
 * </ul>
 *
 * <h2>Key conventions</h2>
 * <ul>
 *   <li>Extend {@code EventloopTestBase} — required for all ActiveJ async tests.</li>
 *   <li>Use {@code runPromise(() -> ...)} to execute ActiveJ Promises in tests.</li>
 *   <li>Use {@code lenient().when()} for stubs that are not needed in every test.</li>
 *   <li>Never call {@code .getResult()} on a Promise directly.</li>
 * </ul>
 */
@DisplayName("DefaultDataRetentionPlugin")
@ExtendWith(MockitoExtension.class)
class DefaultDataRetentionPluginTest extends EventloopTestBase {

    @Mock
    private PluginContext mockContext;

    private DefaultDataRetentionPlugin plugin;

    @BeforeEach
    void setUp() {
        plugin = new DefaultDataRetentionPlugin();
        // Lenient: not every test needs the initialized plugin — lifecycle tests use raw plugin
        lenient().when(mockContext.pluginId()).thenReturn("data-retention");
        runPromise(() -> plugin.initialize(mockContext).then(v -> plugin.start()));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Pattern 1 — Policy Evaluation
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Pattern 1: Policy Evaluation")
    class PolicyEvaluation {

        @Test
        @DisplayName("COMPLIANT when record is within retention period")
        void compliantWithinPeriod() {
            DataRecord record = newRecord("PHI", Instant.now().minus(Duration.ofDays(100)), false);

            RetentionDecision decision = runPromise(() ->
                plugin.evaluateRetentionPolicy("tenant-a", record));

            assertThat(decision.decision()).isEqualTo(Decision.COMPLIANT);
            assertThat(decision.expiresAt()).isPresent();
        }

        @Test
        @DisplayName("EXPIRED when record exceeds retention period")
        void expiredBeyondPeriod() {
            // PHI retention is 7 years — simulate 8-year-old record
            Instant eightYearsAgo = Instant.now().minus(Duration.ofDays(365 * 8));
            DataRecord record = newRecord("PHI", eightYearsAgo, false);

            RetentionDecision decision = runPromise(() ->
                plugin.evaluateRetentionPolicy("tenant-a", record));

            assertThat(decision.decision()).isEqualTo(Decision.EXPIRED);
        }

        @Test
        @DisplayName("LEGAL_HOLD supersedes expiry when hold is active")
        void legalHoldSupersedesExpiry() {
            // Even if record would be EXPIRED, legal hold takes precedence
            Instant eightYearsAgo = Instant.now().minus(Duration.ofDays(365 * 8));
            DataRecord record = newRecord("PHI", eightYearsAgo, true /* legalHold */);

            RetentionDecision decision = runPromise(() ->
                plugin.evaluateRetentionPolicy("tenant-a", record));

            assertThat(decision.decision()).isEqualTo(Decision.LEGAL_HOLD);
            assertThat(decision.expiresAt()).isEmpty();
        }

        @Test
        @DisplayName("Returns correct retention period for known classification")
        void retentionPeriodForKnownClassification() {
            Duration period = runPromise(() ->
                plugin.getRetentionPeriod("tenant-a", "FINANCIAL"));

            assertThat(period).isEqualTo(Duration.ofDays(365 * 7));
        }

        @Test
        @DisplayName("Returns default 1-year period for unknown classification")
        void retentionPeriodDefaultsForUnknown() {
            Duration period = runPromise(() ->
                plugin.getRetentionPeriod("tenant-a", "UNKNOWN_TYPE"));

            assertThat(period).isEqualTo(Duration.ofDays(365));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Pattern 2 — Event-Driven Processing
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Pattern 2: Event-Driven Processing")
    class EventDrivenProcessing {

        @Test
        @DisplayName("handleDataCreated stores record — idempotent on re-delivery")
        void handleDataCreatedIsIdempotent() {
            DataRecord record = newRecord("OPERATIONAL", Instant.now().minus(Duration.ofDays(30)), false);
            DataCreatedEvent event = new DataCreatedEvent(
                UUID.randomUUID().toString(), "tenant-b", record, "actor-1", Instant.now());

            // Deliver twice (at-least-once)
            runPromise(() -> plugin.handleDataCreated(event));
            runPromise(() -> plugin.handleDataCreated(event));

            List<DataRecord> expiring = runPromise(() ->
                plugin.findExpiring("tenant-b", Duration.ofDays(365 * 4)));

            // Stored exactly once despite two deliveries
            assertThat(expiring.stream().filter(r -> r.recordId().equals(record.recordId()))).hasSize(1);
        }

        @Test
        @DisplayName("findExpiring returns records within look-ahead window")
        void findExpiringReturnsCorrectRecords() {
            // Record created 3 years ago with ANALYTICS (1-year retention) → already expired
            DataRecord expired = newRecord("ANALYTICS",
                Instant.now().minus(Duration.ofDays(365 * 3)), false);
            DataRecord fresh = newRecord("PHI", Instant.now().minus(Duration.ofDays(30)), false);

            runPromise(() -> plugin.handleDataCreated(
                new DataCreatedEvent(UUID.randomUUID().toString(), "tenant-c", expired, "sys", Instant.now())));
            runPromise(() -> plugin.handleDataCreated(
                new DataCreatedEvent(UUID.randomUUID().toString(), "tenant-c", fresh, "sys", Instant.now())));

            List<DataRecord> expiring = runPromise(() ->
                plugin.findExpiring("tenant-c", Duration.ofDays(1)));

            assertThat(expiring).extracting(DataRecord::recordId).contains(expired.recordId());
            assertThat(expiring).extracting(DataRecord::recordId).doesNotContain(fresh.recordId());
        }

        @Test
        @DisplayName("handleDataAccessed does not throw for unknown record")
        void handleDataAccessedSafeForUnknownRecord() {
            DataAccessedEvent event = new DataAccessedEvent(
                UUID.randomUUID().toString(), "tenant-d", "unknown-record",
                "user-x", "READ", Instant.now());

            // Should complete without error
            runPromise(() -> plugin.handleDataAccessed(event));
        }

        @Test
        @DisplayName("Records on legal hold are excluded from expiring list")
        void legalHoldExcludedFromExpiring() {
            // ANALYTICS record created 3 years ago but on legal hold
            DataRecord onHold = newRecord("ANALYTICS",
                Instant.now().minus(Duration.ofDays(365 * 3)), true /* legalHold */);

            runPromise(() -> plugin.handleDataCreated(
                new DataCreatedEvent(UUID.randomUUID().toString(), "tenant-e", onHold, "sys", Instant.now())));

            List<DataRecord> expiring = runPromise(() ->
                plugin.findExpiring("tenant-e", Duration.ofDays(1)));

            assertThat(expiring).extracting(DataRecord::recordId).doesNotContain(onHold.recordId());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Pattern 3 — Human Approval Integration
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Pattern 3: Human Approval Integration")
    class HumanApprovalIntegration {

        @Test
        @DisplayName("requestRetentionException returns an approval outcome")
        void requestRetentionExceptionReturnsOutcome() {
            DataRecord record = newRecord("PHI", Instant.now().minus(Duration.ofDays(365 * 8)), false);

            ApprovalOutcome outcome = runPromise(() ->
                plugin.requestRetentionException("tenant-f", record,
                    "Legal hold for ongoing litigation", "legal-team@company.com"));

            assertThat(outcome).isNotNull();
            assertThat(outcome).isIn(ApprovalOutcome.APPROVED, ApprovalOutcome.REJECTED, ApprovalOutcome.TIMEOUT);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Tenant Isolation
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Tenant Isolation")
    class TenantIsolation {

        @Test
        @DisplayName("Records stored for tenant-A are not visible to tenant-B")
        void recordsAreTenantScoped() {
            DataRecord recordA = newRecord("PHI", Instant.now().minus(Duration.ofDays(365 * 8)), false);
            runPromise(() -> plugin.handleDataCreated(
                new DataCreatedEvent(UUID.randomUUID().toString(), "iso-tenant-a", recordA, "sys", Instant.now())));

            // Tenant B asks for expiring records — should see nothing from tenant A
            List<DataRecord> expiringB = runPromise(() ->
                plugin.findExpiring("iso-tenant-b", Duration.ofDays(1)));

            assertThat(expiringB).extracting(DataRecord::recordId)
                .doesNotContain(recordA.recordId());
        }

        @Test
        @DisplayName("Policy evaluation is tenant-scoped — classification period is per-tenant")
        void policyEvaluationIsTenantScoped() {
            DataRecord record = newRecord("ANALYTICS", Instant.now().minus(Duration.ofDays(400)), false);

            // Both tenants evaluate the same record — default ANALYTICS is 1 year (365 days)
            // Record is 400 days old → EXPIRED for both (default retention)
            RetentionDecision decisionA = runPromise(() ->
                plugin.evaluateRetentionPolicy("iso-tenant-a", record));
            RetentionDecision decisionB = runPromise(() ->
                plugin.evaluateRetentionPolicy("iso-tenant-b", record));

            // Both expired but through independent evaluation paths
            assertThat(decisionA.decision()).isEqualTo(Decision.EXPIRED);
            assertThat(decisionB.decision()).isEqualTo(Decision.EXPIRED);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private DataRecord newRecord(String classification, Instant createdAt, boolean legalHold) {
        return new DataRecord(
            UUID.randomUUID().toString(),
            "test-tenant",
            classification,
            createdAt,
            createdAt,
            legalHold,
            Map.of()
        );
    }
}
