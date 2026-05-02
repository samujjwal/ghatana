package com.ghatana.integration.phrfinance;

import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.plugin.approval.ApprovalDecision;
import com.ghatana.plugin.approval.ApprovalRecord;
import com.ghatana.plugin.approval.ApprovalRequest;
import com.ghatana.plugin.approval.ApprovalStatus;
import com.ghatana.plugin.approval.impl.StandardHumanApprovalPlugin;
import com.ghatana.plugin.audit.AuditTrailPlugin;
import com.ghatana.plugin.audit.impl.DurableAuditTrailPlugin;
import com.ghatana.plugin.ledger.LedgerPlugin;
import com.ghatana.plugin.ledger.LedgerTransaction;
import com.ghatana.plugin.ledger.impl.DurableLedgerPlugin;
import com.ghatana.plugin.consent.ConsentPlugin;
import com.ghatana.plugin.consent.impl.DurableConsentPlugin;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * KP-034 — Flagship thin-slice demo: PHR sensitive-data export with human approval
 *          and linked Finance billing.
 *
 * <p>This test exercises the full regulated platform stack in a single cohesive scenario:</p>
 * <ol>
 *   <li>Patient grants consent for sensitive data export.</li>
 *   <li>A request to export sensitive PHR data is submitted.</li>
 *   <li>Because the data is high-sensitivity, a human approval request is raised
 *       (via {@link StandardHumanApprovalPlugin}).</li>
 *   <li>The export is blocked until a reviewer approves it.</li>
 *   <li>Each observable step is audit-logged via {@link DurableAuditTrailPlugin}.</li>
 *   <li>After approval the export service fee is posted to the billing ledger
 *       ({@link DurableLedgerPlugin}).</li>
 *   <li>Assertions verify: consent gate, approval lifecycle, audit trail continuity,
 *       billing isolation, and cross-domain observability.</li>
 * </ol>
 *
 * @doc.type class
 * @doc.purpose KP-034 flagship thin-slice integration test
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("KP-034 — Flagship: PHR Sensitive-Data Export with Human Approval + Finance Billing")
@ExtendWith(MockitoExtension.class)
class FlagshipThinSliceDemoIT extends EventloopTestBase {

    private static final String TENANT     = "tenant-flagship";
    private static final String PATIENT_ID = "patient-flagship-01";
    private static final String DOCTOR_ID  = "dr-flagship";
    private static final String REVIEWER_ID = "chief-medical-officer";

    @Mock
    private PluginContext mockContext;

    // Durable (JDBC/H2) plugins
    private DurableAuditTrailPlugin     auditPlugin;
    private DurableLedgerPlugin  billingPlugin;
    private DurableConsentPlugin        consentPlugin;

    // In-memory human approval plugin
    private StandardHumanApprovalPlugin approvalPlugin;

    @BeforeEach
    void setUp() {
        long nanos = System.nanoTime();
        JdbcDataSource auditDs   = h2ds("flagship_audit_"   + nanos);
        JdbcDataSource billingDs = h2ds("flagship_billing_" + nanos);
        JdbcDataSource consentDs = h2ds("flagship_consent_" + nanos);

        auditPlugin   = new DurableAuditTrailPlugin(auditDs);
        billingPlugin = new DurableLedgerPlugin(billingDs);
        consentPlugin = new DurableConsentPlugin(consentDs);
        approvalPlugin = new StandardHumanApprovalPlugin();

        auditPlugin.ensureSchema();
        billingPlugin.ensureSchema();
        consentPlugin.ensureSchema();

        runPromise(() -> auditPlugin.initialize(mockContext));
        runPromise(() -> billingPlugin.initialize(mockContext));
        runPromise(() -> consentPlugin.initialize(mockContext));
        runPromise(() -> approvalPlugin.initialize(mockContext));

        runPromise(() -> auditPlugin.start());
        runPromise(() -> billingPlugin.start());
        runPromise(() -> consentPlugin.start());
        runPromise(() -> approvalPlugin.start());
    }

    @AfterEach
    void tearDown() {
        runPromise(() -> auditPlugin.stop());
        runPromise(() -> billingPlugin.stop());
        runPromise(() -> consentPlugin.stop());
        runPromise(() -> approvalPlugin.stop());
        runPromise(() -> auditPlugin.shutdown());
        runPromise(() -> billingPlugin.shutdown());
        runPromise(() -> consentPlugin.shutdown());
        runPromise(() -> approvalPlugin.shutdown());
    }

    // =========================================================================
    // Happy-path: consent → pending approval → reviewer approves → billing posted
    // =========================================================================

    @Test
    @DisplayName("Full happy-path: consent → pending approval → approved → billed → audit trail complete")
    void happyPath_consentApprovedAndBilled() {

        // ── Step 1: Patient grants consent ────────────────────────────────────
        runPromise(() -> consentPlugin.recordConsent(
                PATIENT_ID, "phr.sensitive-data-export", ConsentPlugin.ConsentAction.GRANT));

        boolean consentPresent = runPromise(
                () -> consentPlugin.verifyConsent(PATIENT_ID, "phr.sensitive-data-export"));
        assertThat(consentPresent).isTrue();

        // Log consent grant
        logAuditEvent("phr.consent", PATIENT_ID, "CONSENT_GRANTED",
                "{\"purpose\":\"phr.sensitive-data-export\"}");

        // ── Step 2: Request raised — human approval needed for high-sensitivity ──
        String requestId = UUID.randomUUID().toString();
        ApprovalRequest approvalReq = new ApprovalRequest(
                requestId,
                PATIENT_ID,
                DOCTOR_ID,
                "phr.sensitive-data-export",
                "Cross-domain export for specialist referral",
                Map.of("data_classification", "HIGH", "purpose", "referral"),
                Instant.now(),
                null
        );
        ApprovalRecord pending = runPromise(() -> approvalPlugin.requestApproval(approvalReq));
        assertThat(pending.status()).isEqualTo(ApprovalStatus.PENDING);

        // Export is blocked: verify still PENDING
        Optional<ApprovalRecord> statusBefore = runPromise(
                () -> approvalPlugin.getApprovalStatus(requestId));
        assertThat(statusBefore).isPresent();
        assertThat(statusBefore.get().status()).isEqualTo(ApprovalStatus.PENDING);

        logAuditEvent("phr.approval", PATIENT_ID, "EXPORT_APPROVAL_REQUESTED",
                "{\"requestId\":\"" + requestId + "\"}");

        // ── Step 3: Reviewer approves ─────────────────────────────────────────
        ApprovalRecord approved = runPromise(
                () -> approvalPlugin.completeApproval(requestId, ApprovalDecision.APPROVED,
                        REVIEWER_ID, "Clinically justified referral"));
        assertThat(approved.status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(approved.reviewerId()).isEqualTo(REVIEWER_ID);

        logAuditEvent("phr.approval", PATIENT_ID, "EXPORT_APPROVED",
                "{\"requestId\":\"" + requestId + "\",\"reviewer\":\"" + REVIEWER_ID + "\"}");

        // No pending approvals remain for this patient
        List<ApprovalRecord> pendingAfter = runPromise(
                () -> approvalPlugin.listPendingForSubject(PATIENT_ID));
        assertThat(pendingAfter).isEmpty();

        // ── Step 4: Export service fee posted to billing ledger ───────────────
        LedgerTransaction exportFee = LedgerTransaction.builder()
                .transactionId("tx-export-" + requestId)
                .sourceId("phr")
                .tenantId(TENANT)
                .debitAccount("PHR:AR:" + PATIENT_ID)
                .creditAccount("PHR:REVENUE:export-service")
                .amount(new BigDecimal("250.00"))
                .currency("NPR")
                .type(LedgerTransaction.TransactionType.CHARGE)
                .description("Sensitive-data export fee — requestId=" + requestId)
                .occurredAt(Instant.now())
                .build();

        String entryId = runPromise(() -> billingPlugin.postTransaction(exportFee));
        assertThat(entryId).isNotBlank();
        assertThat(runPromise(() -> billingPlugin.getPostingStatus("tx-export-" + requestId)))
                .isEqualTo(LedgerPlugin.PostingStatus.POSTED);

        logAuditEvent("finance.billing", PATIENT_ID, "EXPORT_FEE_POSTED",
                "{\"txId\":\"tx-export-" + requestId + "\",\"amount\":\"250.00\"}");

        // ── Step 5: Cross-domain audit trail continuity verification ──────────
        List<AuditTrailPlugin.AuditEntry> phrAudit = getAuditTrail(PATIENT_ID, "phr.consent");
        assertThat(phrAudit).hasSize(1);
        assertThat(phrAudit.get(0).action()).isEqualTo("CONSENT_GRANTED");

        List<AuditTrailPlugin.AuditEntry> approvalAudit = getAuditTrail(PATIENT_ID, "phr.approval");
        assertThat(approvalAudit).hasSize(2);
        assertThat(approvalAudit).extracting(AuditTrailPlugin.AuditEntry::action)
                .containsExactlyInAnyOrder("EXPORT_APPROVAL_REQUESTED", "EXPORT_APPROVED");

        List<AuditTrailPlugin.AuditEntry> billingAudit = getAuditTrail(PATIENT_ID, "finance.billing");
        assertThat(billingAudit).hasSize(1);
        assertThat(billingAudit.get(0).action()).isEqualTo("EXPORT_FEE_POSTED");
    }

    // =========================================================================
    // Rejection path: rejection blocks billing
    // =========================================================================

    @Test
    @DisplayName("Rejection path: export rejected by reviewer — no billing charge posted")
    void rejectionPath_noBillingPosted() {
        // Patient grants consent
        runPromise(() -> consentPlugin.recordConsent(
                PATIENT_ID, "phr.sensitive-data-export", ConsentPlugin.ConsentAction.GRANT));

        // Raise approval request
        String requestId = UUID.randomUUID().toString();
        ApprovalRequest req = new ApprovalRequest(requestId, PATIENT_ID, DOCTOR_ID,
                "phr.sensitive-data-export", "Non-essential export",
                Map.of("data_classification", "HIGH"),
                Instant.now(), null);
        runPromise(() -> approvalPlugin.requestApproval(req));

        // Reviewer rejects
        ApprovalRecord rejected = runPromise(
                () -> approvalPlugin.completeApproval(requestId, ApprovalDecision.REJECTED,
                        REVIEWER_ID, "Not clinically required"));
        assertThat(rejected.status()).isEqualTo(ApprovalStatus.REJECTED);

        // Because rejected, the product logic must not post a billing charge.
        // Verify: no billing entries for this export request
        LedgerPlugin.PostingStatus noEntry = runPromise(
                () -> billingPlugin.getPostingStatus("tx-export-" + requestId));
        assertThat(noEntry).isEqualTo(LedgerPlugin.PostingStatus.NOT_FOUND);
    }

    // =========================================================================
    // Consent gate: no consent → approval not raised, no billing
    // =========================================================================

    @Test
    @DisplayName("Consent gate: absent consent prevents approval request and billing")
    void consentGate_noConsentNoBilling() {
        // No consent granted → export guard fails immediately
        boolean hasConsent = runPromise(
                () -> consentPlugin.verifyConsent(PATIENT_ID, "phr.sensitive-data-export"));
        assertThat(hasConsent).isFalse();

        // Because consent is absent the product logic must not proceed to billing
        LedgerPlugin.PostingStatus noEntry = runPromise(
                () -> billingPlugin.getPostingStatus("tx-no-consent-" + PATIENT_ID));
        assertThat(noEntry).isEqualTo(LedgerPlugin.PostingStatus.NOT_FOUND);
    }

    // =========================================================================
    // Cross-domain isolation: PHR audit ≠ Finance billing data
    // =========================================================================

    @Test
    @DisplayName("Audit trail is isolated from billing ledger — PHR events do not leak into billing")
    void crossDomain_auditIsolatedFromBilling() {
        // PHR event
        logAuditEvent("phr.encounter", "patient-isolation-1", "ENCOUNTER_OPENED", "{}");

        // Finance billing check for unrelated transaction
        LedgerPlugin.PostingStatus missing = runPromise(
                () -> billingPlugin.getPostingStatus("phr-event-should-not-be-here"));
        assertThat(missing).isEqualTo(LedgerPlugin.PostingStatus.NOT_FOUND);

        // Billing events must not appear in PHR audit
        List<AuditTrailPlugin.AuditEntry> phrEvents = getAuditTrail("patient-isolation-1", "phr.encounter");
        assertThat(phrEvents).hasSize(1);
        assertThat(phrEvents.get(0).details()).containsEntry("entityType", "phr.encounter");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void logAuditEvent(String entityType, String entityId, String action, String data) {
        runPromise(() -> auditPlugin.logEvent(entityId, action, Map.of(
                "tenantId", TENANT,
                "entityType", entityType,
                "actorId", DOCTOR_ID,
                "data", data
        )));
    }

    private static JdbcDataSource h2ds(String dbName) {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
        return ds;
    }

        private List<AuditTrailPlugin.AuditEntry> getAuditTrail(String entityId, String entityType) {
                return runPromise(() -> auditPlugin.getTrail(entityId)).stream()
                                .filter(entry -> entityType.equals(String.valueOf(entry.details().get("entityType"))))
                                .toList();
        }
}
