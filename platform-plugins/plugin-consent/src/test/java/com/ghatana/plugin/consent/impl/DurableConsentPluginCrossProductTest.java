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
 *   <li>A consent grant for purpose A does NOT satisfy a purpose B query.</li>
 *   <li>A consent grant for purpose B does NOT satisfy a purpose A query.</li>
 *   <li>Revocation is reflected immediately across both purpose domains.</li>
 *   <li>History is subject-scoped — different subjects have independent histories.</li>
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
    @DisplayName("purpose-A consent grant must NOT satisfy a purpose-B consent check")
    void testPurposeAConsentDoesNotSatisfyPurposeBCheck() { 
        runPromise(() -> plugin.recordConsent( 
                "entity-001", "domain-a.data-sharing", ConsentPlugin.ConsentAction.GRANT));

        boolean domainAConsent = runPromise(() -> 
                plugin.verifyConsent("entity-001", "domain-a.data-sharing")); 
        boolean domainBConsent = runPromise(() -> 
                plugin.verifyConsent("entity-001", "domain-b.transactions")); 

        assertThat(domainAConsent).isTrue(); 
        assertThat(domainBConsent).isFalse(); 
    }

    @Test
    @DisplayName("purpose-B consent grant must NOT satisfy a purpose-A consent check")
    void testPurposeBConsentDoesNotSatisfyPurposeACheck() { 
        runPromise(() -> plugin.recordConsent( 
                "entity-002", "domain-b.transactions", ConsentPlugin.ConsentAction.GRANT));

        boolean domainBConsent = runPromise(() -> 
                plugin.verifyConsent("entity-002", "domain-b.transactions")); 
        boolean domainAConsent = runPromise(() -> 
                plugin.verifyConsent("entity-002", "domain-a.medical-records")); 

        assertThat(domainBConsent).isTrue(); 
        assertThat(domainAConsent).isFalse(); 
    }

    @Test
    @DisplayName("Revoking a consent must not affect a separately granted consent for another purpose")
    void testRevocationIsPurposeScoped() { 
        runPromise(() -> plugin.recordConsent( 
                "multi-consent-user", "domain-a.data-sharing", ConsentPlugin.ConsentAction.GRANT));
        ConsentPlugin.ConsentRecord domainBGrant = runPromise(() -> 
                plugin.recordConsent("multi-consent-user", "domain-b.transactions", 
                        ConsentPlugin.ConsentAction.GRANT));

        // Revoke domain-a consent by withdrawing
        runPromise(() -> plugin.recordConsent( 
                "multi-consent-user", "domain-a.data-sharing", ConsentPlugin.ConsentAction.WITHDRAW));

        boolean domainAAfterRevoke = runPromise(() -> 
                plugin.verifyConsent("multi-consent-user", "domain-a.data-sharing")); 
        boolean domainBAfterRevoke = runPromise(() -> 
                plugin.verifyConsent("multi-consent-user", "domain-b.transactions")); 

        assertThat(domainAAfterRevoke).isFalse(); 
        assertThat(domainBAfterRevoke).isTrue(); 
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
                plugin.recordConsent("subj-revoke", "domain-a.imaging", ConsentPlugin.ConsentAction.GRANT)); 

        runPromise(() -> plugin.revokeConsent(record.consentId())); 

        ConsentPlugin.ConsentStatus status =
                runPromise(() -> plugin.getCurrentConsent("subj-revoke", "domain-a.imaging")); 

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
