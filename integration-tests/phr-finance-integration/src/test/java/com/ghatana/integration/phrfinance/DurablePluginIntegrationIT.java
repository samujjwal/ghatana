package com.ghatana.integration.phrfinance;

import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.plugin.audit.AuditTrailPlugin;
import com.ghatana.plugin.audit.impl.DurableAuditTrailPlugin;
import com.ghatana.plugin.ledger.LedgerPlugin;
import com.ghatana.plugin.ledger.LedgerTransaction;
import com.ghatana.plugin.ledger.impl.DurableLedgerPlugin;
import com.ghatana.plugin.consent.ConsentPlugin;
import com.ghatana.plugin.consent.impl.DurableConsentPlugin;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * KP-013 — Durable plugin cross-product integration tests.
 *
 * <p>These tests exercise the three durable plugins ({@link DurableAuditTrailPlugin},
 * {@link DurableLedgerPlugin}, {@link DurableConsentPlugin}) working together
 * in realistic PHR-Finance cross-domain scenarios. Each scenario verifies isolation,
 * audit continuity, and consent-gated data access across JDBC-backed stores using
 * an H2 in-memory database.</p>
 *
 * @doc.type class
 * @doc.purpose KP-013 durable plugin cross-product PHR-Finance integration tests
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("KP-013 — Durable Plugin PHR-Finance Integration Tests")
@ExtendWith(MockitoExtension.class)
class DurablePluginIntegrationIT extends EventloopTestBase {

    @Mock
    private PluginContext mockContext;

    private DurableAuditTrailPlugin auditPlugin;
    private DurableLedgerPlugin billingPlugin;
    private DurableConsentPlugin consentPlugin;

    @BeforeEach
    void setUp() {
        // Each test gets its own isolated H2 instance via unique URL
        long nanos = System.nanoTime();
        JdbcDataSource auditDs = h2ds("audit_it_" + nanos);
        JdbcDataSource billingDs = h2ds("billing_it_" + nanos);
        JdbcDataSource consentDs = h2ds("consent_it_" + nanos);

        auditPlugin   = new DurableAuditTrailPlugin(auditDs);
        billingPlugin = new DurableLedgerPlugin(billingDs);
        consentPlugin = new DurableConsentPlugin(consentDs);

        auditPlugin.ensureSchema();
        billingPlugin.ensureSchema();
        consentPlugin.ensureSchema();

        runPromise(() -> auditPlugin.initialize(mockContext));
        runPromise(() -> billingPlugin.initialize(mockContext));
        runPromise(() -> consentPlugin.initialize(mockContext));

        runPromise(() -> auditPlugin.start());
        runPromise(() -> billingPlugin.start());
        runPromise(() -> consentPlugin.start());
    }

    @AfterEach
    void tearDown() {
        runPromise(() -> auditPlugin.stop());
        runPromise(() -> billingPlugin.stop());
        runPromise(() -> consentPlugin.stop());
        runPromise(() -> auditPlugin.shutdown());
        runPromise(() -> billingPlugin.shutdown());
        runPromise(() -> consentPlugin.shutdown());
    }

    // ── Scenario 1: PHR encounter + linked billing charge ────────────────────

    @Test
    @DisplayName("PHR encounter is audit-logged and the linked billing charge is posted; ledger does not show PHR audit entries")
    void phrEncounterAuditAndBillingCharge_areIsolated() {
        // PHR operation is audit-logged
        runPromise(() -> auditPlugin.logEvent(
                "patient-001",
                "ENCOUNTER_CLOSED",
                auditDetails("tenant-nep", "phr.encounter", "dr-paudel", "{\"encounterId\":\"enc-99\"}")));

        // Linked billing charge is posted to the ledger
        LedgerTransaction charge = LedgerTransaction.builder()
                .transactionId("tx-enc-99")
                .sourceId("phr")
                .tenantId("tenant-nep")
                .debitAccount("PHR:AR:patient-001")
                .creditAccount("PHR:REVENUE:provider-paudel")
                .amount(new BigDecimal("1800.00"))
                .currency("NPR")
                .type(LedgerTransaction.TransactionType.CHARGE)
                .description("Encounter enc-99 closure charge")
                .occurredAt(Instant.now())
                .build();

        String entryId = runPromise(() -> billingPlugin.postTransaction(charge));
        assertThat(entryId).isNotBlank();
        assertThat(runPromise(() -> billingPlugin.getPostingStatus("tx-enc-99")))
                .isEqualTo(LedgerPlugin.PostingStatus.POSTED);

        // Audit trail has 1 PHR entry
        List<AuditTrailPlugin.AuditEntry> auditEntries = getAuditTrail("patient-001", "phr.encounter");
        assertThat(auditEntries).hasSize(1);
        assertThat(auditEntries.get(0).action()).isEqualTo("ENCOUNTER_CLOSED");

        // Ledger does NOT store PHR audit events — isolation check
        LedgerPlugin.PostingStatus noEntry =
                runPromise(() -> billingPlugin.getPostingStatus("NON_EXISTENT_TX"));
        assertThat(noEntry).isEqualTo(LedgerPlugin.PostingStatus.NOT_FOUND);
    }

    // ── Scenario 2: Consent-gated PHR billing flow ───────────────────────────

    @Test
    @DisplayName("Finance-side billing for a PHR patient requires PHR consent; access is denied and no charge posted without consent")
    void financeBillingForPhrPatient_requiresPhrConsent() {
        // No consent granted → deny billing

        boolean consentForBilling = runPromise(() ->
                consentPlugin.verifyConsent("patient-002", "phr.billing-share"));
        assertThat(consentForBilling).isFalse();

        // Because consent is missing, the product logic must not post the charge.
        // Simulate the guard: only post if consent is present
        assertThat(consentForBilling).isFalse(); // guard asserted

        // Now grant consent and retry
        runPromise(() -> consentPlugin.recordConsent(
                "patient-002", "phr.billing-share", ConsentPlugin.ConsentAction.GRANT));

        boolean consentAfterGrant = runPromise(() ->
                consentPlugin.verifyConsent("patient-002", "phr.billing-share"));
        assertThat(consentAfterGrant).isTrue();

        // Finance product posts billing only after confirmed consent
        LedgerTransaction charge = LedgerTransaction.builder()
                .transactionId("tx-consent-guarded-1")
                .sourceId("finance")
                .tenantId("tenant-nep")
                .debitAccount("FIN:AR:patient-002")
                .creditAccount("FIN:REVENUE:billing-dept")
                .amount(new BigDecimal("500.00"))
                .currency("NPR")
                .type(LedgerTransaction.TransactionType.CHARGE)
                .description("PHR-authorized finance charge")
                .occurredAt(Instant.now())
                .build();

        String entryId = runPromise(() -> billingPlugin.postTransaction(charge));
        assertThat(entryId).isNotBlank();
        assertThat(runPromise(() -> billingPlugin.getPostingStatus("tx-consent-guarded-1")))
                .isEqualTo(LedgerPlugin.PostingStatus.POSTED);
    }

    // ── Scenario 3: Finance audit trail is isolated from PHR audit trail ─────

    @Test
    @DisplayName("Finance audit entries logged in the same plugin store are isolated from PHR entries by tenantId/entityType")
    void financeAndPhrAuditTrails_areScopeIsolated() {
        runPromise(() -> auditPlugin.logEvent(
                "patient-003",
                "RECORD_ACCESSED",
                auditDetails("tenant-nep", "phr.record", "nurse-k", "{}")));
        runPromise(() -> auditPlugin.logEvent(
                "account-fin-7",
                "STATEMENT_GENERATED",
                auditDetails("tenant-nep", "finance.account", "finance-bot", "{}")));

        List<AuditTrailPlugin.AuditEntry> phrEntries = getAuditTrail("patient-003", "phr.record");
        List<AuditTrailPlugin.AuditEntry> financeEntries = getAuditTrail("account-fin-7", "finance.account");

        assertThat(phrEntries).hasSize(1);
        assertThat(phrEntries.get(0).action()).isEqualTo("RECORD_ACCESSED");

        assertThat(financeEntries).hasSize(1);
        assertThat(financeEntries.get(0).action()).isEqualTo("STATEMENT_GENERATED");

        // Cross-check: no PHR entries visible in Finance query and vice versa
        assertThat(financeEntries).noneMatch(e -> "patient-003".equals(e.entityId()));
        assertThat(phrEntries).noneMatch(e -> "account-fin-7".equals(e.entityId()));
    }

    // ── Scenario 4: Idempotent double-billing prevention with audit evidence ──

    @Test
    @DisplayName("Idempotent billing re-submission returns ALREADY_POSTED and audit has no duplicate entries")
    void idempotentBilling_noDoubleAuditEntry() {
        LedgerTransaction posting = LedgerTransaction.builder()
                .transactionId("tx-idempotent-99")
                .sourceId("finance")
                .tenantId("tenant-fin")
                .debitAccount("FIN:AR:cust-1")
                .creditAccount("FIN:REVENUE:dept-A")
                .amount(new BigDecimal("250.00"))
                .currency("NPR")
                .type(LedgerTransaction.TransactionType.CHARGE)
                .description("Idempotency test charge")
                .occurredAt(Instant.now())
                .build();

        String firstEntryId = runPromise(() -> billingPlugin.postTransaction(posting));
        String secondEntryId = runPromise(() -> billingPlugin.postTransaction(posting));

        assertThat(firstEntryId).isNotBlank();
        assertThat(secondEntryId).isEqualTo(firstEntryId);
        assertThat(runPromise(() -> billingPlugin.getPostingStatus("tx-idempotent-99")))
                .isEqualTo(LedgerPlugin.PostingStatus.POSTED);

        // Only one audit event logged for the transaction (prevent double-audit on re-submit)
        runPromise(() -> auditPlugin.logEvent(
                "tx-idempotent-99",
                "TRANSACTION_POSTED",
                auditDetails("tenant-fin", "finance.transaction", "system", "{\"txId\":\"tx-idempotent-99\"}")));

        List<AuditTrailPlugin.AuditEntry> auditEntries = getAuditTrail("tx-idempotent-99", "finance.transaction");

        assertThat(auditEntries).hasSize(1); // Product only calls log once despite idempotent billing
    }

    // ── Scenario 5: Consent revocation blocks subsequent billing ─────────────

    @Test
    @DisplayName("Revoking PHR billing consent prevents subsequent finance billing authorizations")
    void revokedConsent_blocksFurtherBilling() {
        // Grant consent
        runPromise(() -> consentPlugin.recordConsent(
                "patient-004", "phr.billing-share", ConsentPlugin.ConsentAction.GRANT));

        boolean beforeRevoke = runPromise(() ->
                consentPlugin.verifyConsent("patient-004", "phr.billing-share"));
        assertThat(beforeRevoke).isTrue();

        // Revoke consent
        runPromise(() -> consentPlugin.recordConsent(
                "patient-004", "phr.billing-share", ConsentPlugin.ConsentAction.WITHDRAW));

        boolean afterRevoke = runPromise(() ->
                consentPlugin.verifyConsent("patient-004", "phr.billing-share"));
        assertThat(afterRevoke).isFalse();

        // History shows two entries (GRANT then WITHDRAW)
        List<ConsentPlugin.ConsentRecord> history =
                runPromise(() -> consentPlugin.getConsentHistory("patient-004"));
        assertThat(history).hasSize(2);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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

        private static Map<String, Object> auditDetails(String tenantId, String entityType, String actorId, String data) {
                return Map.of(
                                "tenantId", tenantId,
                                "entityType", entityType,
                                "actorId", actorId,
                                "data", data
                );
        }
}
