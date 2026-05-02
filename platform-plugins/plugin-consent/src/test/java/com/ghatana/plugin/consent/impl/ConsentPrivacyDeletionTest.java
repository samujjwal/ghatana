package com.ghatana.plugin.consent.impl;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.plugin.consent.ConsentPlugin;
import com.ghatana.plugin.consent.ConsentPlugin.ConsentAction;
import com.ghatana.plugin.consent.ConsentPlugin.ConsentStatus;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Privacy deletion (right-to-erasure) contract tests for consent plugins.
 * <p>
 * Verifies that {@link ConsentPlugin#deleteAllForSubject} correctly removes all
 * consent records and that subsequent queries reflect the erasure.
 *
 * @doc.type class
 * @doc.purpose Right-to-erasure contract tests for StandardConsentPlugin and DurableConsentPlugin
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Consent Privacy Deletion Tests")
class ConsentPrivacyDeletionTest extends EventloopTestBase {

    @Nested
    @DisplayName("StandardConsentPlugin")
    class StandardImpl {

        private StandardConsentPlugin plugin;

        @BeforeEach
        void setUp() {
            plugin = new StandardConsentPlugin();
        }

        @Test
        @DisplayName("deleteAllForSubject removes all consent records and verifyConsent returns false")
        void deleteAllRemovesConsentAndVerifyReturnsFalse() {
            runPromise(() -> plugin.recordConsent("subject-1", "marketing", ConsentAction.GRANT));
            runPromise(() -> plugin.recordConsent("subject-1", "healthcare", ConsentAction.GRANT));

            boolean beforeDeletion = runPromise(() -> plugin.verifyConsent("subject-1", "marketing"));
            assertThat(beforeDeletion).isTrue();

            int deleted = runPromise(() -> plugin.deleteAllForSubject("subject-1"));
            assertThat(deleted).isEqualTo(2);

            boolean afterDeletion = runPromise(() -> plugin.verifyConsent("subject-1", "marketing"));
            assertThat(afterDeletion).isFalse();

            List<ConsentPlugin.ConsentRecord> history = runPromise(() -> plugin.getConsentHistory("subject-1"));
            assertThat(history).isEmpty();
        }

        @Test
        @DisplayName("deleteAllForSubject does not affect other subjects' consent")
        void deleteDoesNotAffectOtherSubjects() {
            runPromise(() -> plugin.recordConsent("subject-a", "marketing", ConsentAction.GRANT));
            runPromise(() -> plugin.recordConsent("subject-b", "marketing", ConsentAction.GRANT));

            runPromise(() -> plugin.deleteAllForSubject("subject-a"));

            boolean subjectBConsent = runPromise(() -> plugin.verifyConsent("subject-b", "marketing"));
            assertThat(subjectBConsent).isTrue();
        }

        @Test
        @DisplayName("deleteAllForSubject returns 0 for unknown subject without error")
        void deleteUnknownSubjectReturnsZero() {
            int deleted = runPromise(() -> plugin.deleteAllForSubject("ghost-subject"));
            assertThat(deleted).isEqualTo(0);
        }

        @Test
        @DisplayName("deleteAllForSubject then re-grant creates fresh consent")
        void deleteAndRegrantCreatesNewConsent() {
            runPromise(() -> plugin.recordConsent("subject-1", "marketing", ConsentAction.GRANT));
            runPromise(() -> plugin.deleteAllForSubject("subject-1"));

            // Re-grant after erasure must work cleanly
            runPromise(() -> plugin.recordConsent("subject-1", "marketing", ConsentAction.GRANT));

            boolean afterRegrant = runPromise(() -> plugin.verifyConsent("subject-1", "marketing"));
            assertThat(afterRegrant).isTrue();
        }
    }

    @Nested
    @DisplayName("DurableConsentPlugin")
    class DurableImpl {

        private DurableConsentPlugin plugin;

        @BeforeEach
        void setUp() {
            JdbcDataSource ds = new JdbcDataSource();
            ds.setURL("jdbc:h2:mem:consent_privacy_test_" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
            ds.setUser("sa");
            ds.setPassword("");
            plugin = new DurableConsentPlugin(ds);
            plugin.ensureSchema();
        }

        @Test
        @DisplayName("deleteAllForSubject removes all consent records and verifyConsent returns false")
        void deleteAllRemovesConsentAndVerifyReturnsFalse() {
            runPromise(() -> plugin.recordConsent("subject-1", "marketing", ConsentAction.GRANT));
            runPromise(() -> plugin.recordConsent("subject-1", "domain-a-dataset", ConsentAction.GRANT));

            boolean beforeDeletion = runPromise(() -> plugin.verifyConsent("subject-1", "marketing"));
            assertThat(beforeDeletion).isTrue();

            Integer deleted = runPromise(() -> plugin.deleteAllForSubject("subject-1"));
            assertThat(deleted).isEqualTo(2);

            boolean afterDeletion = runPromise(() -> plugin.verifyConsent("subject-1", "marketing"));
            assertThat(afterDeletion).isFalse();

            List<ConsentPlugin.ConsentRecord> history = runPromise(() -> plugin.getConsentHistory("subject-1"));
            assertThat(history).isEmpty();
        }

        @Test
        @DisplayName("deleteAllForSubject does not affect other subjects' consent")
        void deleteDoesNotAffectOtherSubjects() {
            runPromise(() -> plugin.recordConsent("subject-a", "marketing", ConsentAction.GRANT));
            runPromise(() -> plugin.recordConsent("subject-b", "marketing", ConsentAction.GRANT));

            runPromise(() -> plugin.deleteAllForSubject("subject-a"));

            boolean subjectBConsent = runPromise(() -> plugin.verifyConsent("subject-b", "marketing"));
            assertThat(subjectBConsent).isTrue();
        }

        @Test
        @DisplayName("deleteAllForSubject returns 0 for unknown subject without error")
        void deleteUnknownSubjectReturnsZero() {
            Integer deleted = runPromise(() -> plugin.deleteAllForSubject("ghost-subject"));
            assertThat(deleted).isEqualTo(0);
        }

        @Test
        @DisplayName("getCurrentConsent returns NOT_REQUESTED after erasure")
        void currentConsentIsNotRequestedAfterErasure() {
            runPromise(() -> plugin.recordConsent("subject-1", "marketing", ConsentAction.GRANT));
            runPromise(() -> plugin.deleteAllForSubject("subject-1"));

            ConsentStatus status = runPromise(() -> plugin.getCurrentConsent("subject-1", "marketing"));
            assertThat(status).isEqualTo(ConsentStatus.NOT_REQUESTED);
        }

        @Test
        @DisplayName("deleteAllForSubject then re-grant creates fresh consent")
        void deleteAndRegrantCreatesNewConsent() {
            runPromise(() -> plugin.recordConsent("subject-1", "marketing", ConsentAction.GRANT));
            runPromise(() -> plugin.deleteAllForSubject("subject-1"));

            runPromise(() -> plugin.recordConsent("subject-1", "marketing", ConsentAction.GRANT));

            boolean afterRegrant = runPromise(() -> plugin.verifyConsent("subject-1", "marketing"));
            assertThat(afterRegrant).isTrue();
        }
    }
}
