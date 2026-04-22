package com.ghatana.plugin.consent.impl;

import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.plugin.consent.ConsentPlugin;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * KP-013: Cross-product consent isolation contract tests.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>A consent grant for PHR data-sharing does NOT satisfy a Finance purpose query.</li>
 *   <li>A consent grant for Finance transactions does NOT satisfy a PHR medical-records query.</li>
 *   <li>Revocation is reflected immediately across both product domains.</li>
 *   <li>History is tenant-scoped per subjectId — different subjects have independent histories.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose KP-013 cross-product consent isolation contract tests
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("KP-013 — DurableConsentPlugin cross-product isolation tests [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class DurableConsentPluginCrossProductTest extends EventloopTestBase {

    @Mock
    private PluginContext mockContext;

    private DurableConsentPlugin plugin;

    @BeforeEach
    void setUp() { // GH-90000
        JdbcDataSource ds = new JdbcDataSource(); // GH-90000
        ds.setURL("jdbc:h2:mem:consent_xp_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1"); // GH-90000
        plugin = new DurableConsentPlugin(ds); // GH-90000
        plugin.ensureSchema(); // GH-90000
        runPromise(() -> plugin.initialize(mockContext)); // GH-90000
        runPromise(() -> plugin.start()); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        runPromise(() -> plugin.stop()); // GH-90000
        runPromise(() -> plugin.shutdown()); // GH-90000
    }

    // -------------------------------------------------------------------------
    // Cross-product isolation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("PHR consent grant must NOT satisfy a Finance purpose consent check [GH-90000]")
    void testPhrConsentDoesNotSatisfyFinancePurpose() { // GH-90000
        runPromise(() -> plugin.recordConsent( // GH-90000
                "patient-001", "phr.data-sharing", ConsentPlugin.ConsentAction.GRANT));

        boolean phrConsent = runPromise(() -> // GH-90000
                plugin.verifyConsent("patient-001", "phr.data-sharing")); // GH-90000
        boolean financeConsent = runPromise(() -> // GH-90000
                plugin.verifyConsent("patient-001", "finance.transactions")); // GH-90000

        assertThat(phrConsent).isTrue(); // GH-90000
        assertThat(financeConsent).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("Finance consent grant must NOT satisfy a PHR medical-records purpose check [GH-90000]")
    void testFinanceConsentDoesNotSatisfyPhrPurpose() { // GH-90000
        runPromise(() -> plugin.recordConsent( // GH-90000
                "customer-001", "finance.transactions", ConsentPlugin.ConsentAction.GRANT));

        boolean financeConsent = runPromise(() -> // GH-90000
                plugin.verifyConsent("customer-001", "finance.transactions")); // GH-90000
        boolean phrConsent = runPromise(() -> // GH-90000
                plugin.verifyConsent("customer-001", "phr.medical-records")); // GH-90000

        assertThat(financeConsent).isTrue(); // GH-90000
        assertThat(phrConsent).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("Revoking a PHR consent must not affect a separately granted Finance consent [GH-90000]")
    void testRevocationIsPurposeScoped() { // GH-90000
        runPromise(() -> plugin.recordConsent( // GH-90000
                "multi-consent-user", "phr.data-sharing", ConsentPlugin.ConsentAction.GRANT));
        ConsentPlugin.ConsentRecord financeGrant = runPromise(() -> // GH-90000
                plugin.recordConsent("multi-consent-user", "finance.transactions", // GH-90000
                        ConsentPlugin.ConsentAction.GRANT));

        // Revoke PHR consent by withdrawing
        runPromise(() -> plugin.recordConsent( // GH-90000
                "multi-consent-user", "phr.data-sharing", ConsentPlugin.ConsentAction.WITHDRAW));

        boolean phrAfterRevoke = runPromise(() -> // GH-90000
                plugin.verifyConsent("multi-consent-user", "phr.data-sharing")); // GH-90000
        boolean financeAfterRevoke = runPromise(() -> // GH-90000
                plugin.verifyConsent("multi-consent-user", "finance.transactions")); // GH-90000

        assertThat(phrAfterRevoke).isFalse(); // GH-90000
        assertThat(financeAfterRevoke).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("Consent history is subject-scoped — different subjects have independent histories [GH-90000]")
    void testSubjectIsolation() { // GH-90000
        runPromise(() -> plugin.recordConsent("subj-a", "shared.purpose", ConsentPlugin.ConsentAction.GRANT)); // GH-90000
        runPromise(() -> plugin.recordConsent("subj-a", "shared.purpose", ConsentPlugin.ConsentAction.WITHDRAW)); // GH-90000
        runPromise(() -> plugin.recordConsent("subj-b", "shared.purpose", ConsentPlugin.ConsentAction.GRANT)); // GH-90000

        List<ConsentPlugin.ConsentRecord> histA =
                runPromise(() -> plugin.getConsentHistory("subj-a [GH-90000]"));
        List<ConsentPlugin.ConsentRecord> histB =
                runPromise(() -> plugin.getConsentHistory("subj-b [GH-90000]"));

        assertThat(histA).hasSize(2); // GH-90000
        assertThat(histA).allMatch(r -> "subj-a".equals(r.subjectId())); // GH-90000

        assertThat(histB).hasSize(1); // GH-90000
        assertThat(histB).allMatch(r -> "subj-b".equals(r.subjectId())); // GH-90000
    }

    @Test
    @DisplayName("revokeConsent by ID must update status to REVOKED [GH-90000]")
    void testRevokeConsentById() { // GH-90000
        ConsentPlugin.ConsentRecord record = runPromise(() -> // GH-90000
                plugin.recordConsent("subj-revoke", "phr.imaging", ConsentPlugin.ConsentAction.GRANT)); // GH-90000

        runPromise(() -> plugin.revokeConsent(record.consentId())); // GH-90000

        ConsentPlugin.ConsentStatus status =
                runPromise(() -> plugin.getCurrentConsent("subj-revoke", "phr.imaging")); // GH-90000

        assertThat(status).isEqualTo(ConsentPlugin.ConsentStatus.REVOKED); // GH-90000
    }

    @Test
    @DisplayName("NOT_REQUESTED status must be returned for a subject-purpose pair with no history [GH-90000]")
    void testNotRequestedStatus() { // GH-90000
        ConsentPlugin.ConsentStatus status =
                runPromise(() -> plugin.getCurrentConsent("unknown-subject", "any.purpose")); // GH-90000

        assertThat(status).isEqualTo(ConsentPlugin.ConsentStatus.NOT_REQUESTED); // GH-90000
    }
}
