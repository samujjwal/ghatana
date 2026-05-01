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
@DisplayName("KP-013 — DurableConsentPlugin cross-product isolation tests")
@ExtendWith(MockitoExtension.class) 
class DurableConsentPluginCrossProductTest extends EventloopTestBase {

    @Mock
    private PluginContext mockContext;

    private DurableConsentPlugin plugin;

    @BeforeEach
    void setUp() { 
        JdbcDataSource ds = new JdbcDataSource(); 
        ds.setURL("jdbc:h2:mem:consent_xp_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1"); 
        plugin = new DurableConsentPlugin(ds); 
        plugin.ensureSchema(); 
        runPromise(() -> plugin.initialize(mockContext)); 
        runPromise(() -> plugin.start()); 
    }

    @AfterEach
    void tearDown() { 
        runPromise(() -> plugin.stop()); 
        runPromise(() -> plugin.shutdown()); 
    }

    // -------------------------------------------------------------------------
    // Cross-product isolation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("PHR consent grant must NOT satisfy a Finance purpose consent check")
    void testPhrConsentDoesNotSatisfyFinancePurpose() { 
        runPromise(() -> plugin.recordConsent( 
                "patient-001", "phr.data-sharing", ConsentPlugin.ConsentAction.GRANT));

        boolean phrConsent = runPromise(() -> 
                plugin.verifyConsent("patient-001", "phr.data-sharing")); 
        boolean financeConsent = runPromise(() -> 
                plugin.verifyConsent("patient-001", "finance.transactions")); 

        assertThat(phrConsent).isTrue(); 
        assertThat(financeConsent).isFalse(); 
    }

    @Test
    @DisplayName("Finance consent grant must NOT satisfy a PHR medical-records purpose check")
    void testFinanceConsentDoesNotSatisfyPhrPurpose() { 
        runPromise(() -> plugin.recordConsent( 
                "customer-001", "finance.transactions", ConsentPlugin.ConsentAction.GRANT));

        boolean financeConsent = runPromise(() -> 
                plugin.verifyConsent("customer-001", "finance.transactions")); 
        boolean phrConsent = runPromise(() -> 
                plugin.verifyConsent("customer-001", "phr.medical-records")); 

        assertThat(financeConsent).isTrue(); 
        assertThat(phrConsent).isFalse(); 
    }

    @Test
    @DisplayName("Revoking a PHR consent must not affect a separately granted Finance consent")
    void testRevocationIsPurposeScoped() { 
        runPromise(() -> plugin.recordConsent( 
                "multi-consent-user", "phr.data-sharing", ConsentPlugin.ConsentAction.GRANT));
        ConsentPlugin.ConsentRecord financeGrant = runPromise(() -> 
                plugin.recordConsent("multi-consent-user", "finance.transactions", 
                        ConsentPlugin.ConsentAction.GRANT));

        // Revoke PHR consent by withdrawing
        runPromise(() -> plugin.recordConsent( 
                "multi-consent-user", "phr.data-sharing", ConsentPlugin.ConsentAction.WITHDRAW));

        boolean phrAfterRevoke = runPromise(() -> 
                plugin.verifyConsent("multi-consent-user", "phr.data-sharing")); 
        boolean financeAfterRevoke = runPromise(() -> 
                plugin.verifyConsent("multi-consent-user", "finance.transactions")); 

        assertThat(phrAfterRevoke).isFalse(); 
        assertThat(financeAfterRevoke).isTrue(); 
    }

    @Test
    @DisplayName("Consent history is subject-scoped — different subjects have independent histories")
    void testSubjectIsolation() { 
        runPromise(() -> plugin.recordConsent("subj-a", "shared.purpose", ConsentPlugin.ConsentAction.GRANT)); 
        runPromise(() -> plugin.recordConsent("subj-a", "shared.purpose", ConsentPlugin.ConsentAction.WITHDRAW)); 
        runPromise(() -> plugin.recordConsent("subj-b", "shared.purpose", ConsentPlugin.ConsentAction.GRANT)); 

        List<ConsentPlugin.ConsentRecord> histA =
                runPromise(() -> plugin.getConsentHistory("subj-a"));
        List<ConsentPlugin.ConsentRecord> histB =
                runPromise(() -> plugin.getConsentHistory("subj-b"));

        assertThat(histA).hasSize(2); 
        assertThat(histA).allMatch(r -> "subj-a".equals(r.subjectId())); 

        assertThat(histB).hasSize(1); 
        assertThat(histB).allMatch(r -> "subj-b".equals(r.subjectId())); 
    }

    @Test
    @DisplayName("revokeConsent by ID must update status to REVOKED")
    void testRevokeConsentById() { 
        ConsentPlugin.ConsentRecord record = runPromise(() -> 
                plugin.recordConsent("subj-revoke", "phr.imaging", ConsentPlugin.ConsentAction.GRANT)); 

        runPromise(() -> plugin.revokeConsent(record.consentId())); 

        ConsentPlugin.ConsentStatus status =
                runPromise(() -> plugin.getCurrentConsent("subj-revoke", "phr.imaging")); 

        assertThat(status).isEqualTo(ConsentPlugin.ConsentStatus.REVOKED); 
    }

    @Test
    @DisplayName("NOT_REQUESTED status must be returned for a subject-purpose pair with no history")
    void testNotRequestedStatus() { 
        ConsentPlugin.ConsentStatus status =
                runPromise(() -> plugin.getCurrentConsent("unknown-subject", "any.purpose")); 

        assertThat(status).isEqualTo(ConsentPlugin.ConsentStatus.NOT_REQUESTED); 
    }
}
