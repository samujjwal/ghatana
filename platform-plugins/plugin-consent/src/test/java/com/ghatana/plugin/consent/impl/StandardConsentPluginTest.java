package com.ghatana.plugin.consent.impl;

import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.plugin.consent.ConsentPlugin;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Comprehensive tests for StandardConsentPlugin
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("StandardConsentPlugin Tests [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class StandardConsentPluginTest extends EventloopTestBase {

    @Mock
    private PluginContext mockContext;

    private StandardConsentPlugin consentPlugin;

    @BeforeEach
    void setUp() { // GH-90000
        consentPlugin = new StandardConsentPlugin(); // GH-90000
    }

    @Test
    @DisplayName("Should initialize consent plugin [GH-90000]")
    void testInitialize() { // GH-90000
        assertThat(consentPlugin.getState()).isEqualTo(PluginState.UNLOADED); // GH-90000
        Promise<Void> result = consentPlugin.initialize(mockContext); // GH-90000
        runPromise(() -> result); // GH-90000
        assertThat(consentPlugin.getState()).isEqualTo(PluginState.INITIALIZED); // GH-90000
    }

    @Test
    @DisplayName("Should start consent plugin [GH-90000]")
    void testStart() { // GH-90000
        runPromise(() -> consentPlugin.initialize(mockContext)); // GH-90000
        Promise<Void> result = consentPlugin.start(); // GH-90000
        runPromise(() -> result); // GH-90000
        assertThat(consentPlugin.getState()).isEqualTo(PluginState.RUNNING); // GH-90000
    }

    @Test
    @DisplayName("Should return correct metadata [GH-90000]")
    void testMetadata() { // GH-90000
        var metadata = consentPlugin.metadata(); // GH-90000
        assertThat(metadata.name()).isEqualTo("Consent Plugin [GH-90000]");
        assertThat(metadata.version()).isEqualTo("1.0.0 [GH-90000]");
    }

    @Test
    @DisplayName("Should record grant consent [GH-90000]")
    void testRecordConsent_Grant() { // GH-90000
        runPromise(() -> consentPlugin.initialize(mockContext) // GH-90000
                .then(v -> consentPlugin.start())); // GH-90000

        Promise<ConsentPlugin.ConsentRecord> result = consentPlugin.recordConsent( // GH-90000
            "subject1", "marketing", ConsentPlugin.ConsentAction.GRANT);
        ConsentPlugin.ConsentRecord record = runPromise(() -> result); // GH-90000

        assertThat(record.subjectId()).isEqualTo("subject1 [GH-90000]");
        assertThat(record.purpose()).isEqualTo("marketing [GH-90000]");
        assertThat(record.status()).isEqualTo(ConsentPlugin.ConsentStatus.GRANTED); // GH-90000
        assertThat(record.consentId()).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Should record deny consent [GH-90000]")
    void testRecordConsent_Deny() { // GH-90000
        runPromise(() -> consentPlugin.initialize(mockContext) // GH-90000
                .then(v -> consentPlugin.start())); // GH-90000

        Promise<ConsentPlugin.ConsentRecord> result = consentPlugin.recordConsent( // GH-90000
            "subject2", "analytics", ConsentPlugin.ConsentAction.DENY);
        ConsentPlugin.ConsentRecord record = runPromise(() -> result); // GH-90000

        assertThat(record.status()).isEqualTo(ConsentPlugin.ConsentStatus.DENIED); // GH-90000
    }

    @Test
    @DisplayName("Should verify granted consent [GH-90000]")
    void testVerifyConsent_Granted() { // GH-90000
        runPromise(() -> consentPlugin.initialize(mockContext) // GH-90000
                .then(v -> consentPlugin.start()) // GH-90000
                .then(v -> consentPlugin.recordConsent("subject3", "email", // GH-90000
                    ConsentPlugin.ConsentAction.GRANT)));

        Promise<Boolean> result = consentPlugin.verifyConsent("subject3", "email"); // GH-90000
        Boolean hasConsent = runPromise(() -> result); // GH-90000

        assertThat(hasConsent).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("Should deny non-existent consent [GH-90000]")
    void testVerifyConsent_NotFound() { // GH-90000
        runPromise(() -> consentPlugin.initialize(mockContext) // GH-90000
                .then(v -> consentPlugin.start())); // GH-90000

        Promise<Boolean> result = consentPlugin.verifyConsent("subject4", "sms"); // GH-90000
        Boolean hasConsent = runPromise(() -> result); // GH-90000

        assertThat(hasConsent).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("Should revoke consent [GH-90000]")
    void testRevokeConsent() { // GH-90000
        runPromise(() -> consentPlugin.initialize(mockContext) // GH-90000
                .then(v -> consentPlugin.start())); // GH-90000

        Promise<ConsentPlugin.ConsentRecord> recordResult = consentPlugin.recordConsent( // GH-90000
            "subject5", "newsletter", ConsentPlugin.ConsentAction.GRANT);
        ConsentPlugin.ConsentRecord record = runPromise(() -> recordResult); // GH-90000

        Promise<Void> revokeResult = consentPlugin.revokeConsent(record.consentId()); // GH-90000
        runPromise(() -> revokeResult); // GH-90000

        // Verify revoked
        Promise<Boolean> verifyResult = consentPlugin.verifyConsent("subject5", "newsletter"); // GH-90000
        Boolean hasConsent = runPromise(() -> verifyResult); // GH-90000

        assertThat(hasConsent).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("Should get consent history for subject [GH-90000]")
    void testGetConsentHistory() { // GH-90000
        runPromise(() -> consentPlugin.initialize(mockContext) // GH-90000
                .then(v -> consentPlugin.start()) // GH-90000
                .then(v -> consentPlugin.recordConsent("subject6", "marketing", // GH-90000
                    ConsentPlugin.ConsentAction.GRANT))
                .then(v -> consentPlugin.recordConsent("subject6", "analytics", // GH-90000
                    ConsentPlugin.ConsentAction.DENY)));

        Promise<List<ConsentPlugin.ConsentRecord>> result =
                consentPlugin.getConsentHistory("subject6 [GH-90000]");
        List<ConsentPlugin.ConsentRecord> history = runPromise(() -> result); // GH-90000

        assertThat(history).hasSize(2); // GH-90000
    }

    @Test
    @DisplayName("Should get current consent status [GH-90000]")
    void testGetCurrentConsent() { // GH-90000
        runPromise(() -> consentPlugin.initialize(mockContext) // GH-90000
                .then(v -> consentPlugin.start()) // GH-90000
                .then(v -> consentPlugin.recordConsent("subject7", "sms", // GH-90000
                    ConsentPlugin.ConsentAction.GRANT)));

        Promise<ConsentPlugin.ConsentStatus> result =
                consentPlugin.getCurrentConsent("subject7", "sms"); // GH-90000
        ConsentPlugin.ConsentStatus status = runPromise(() -> result); // GH-90000

        assertThat(status).isEqualTo(ConsentPlugin.ConsentStatus.GRANTED); // GH-90000
    }

    @Test
    @DisplayName("Should shutdown consent plugin [GH-90000]")
    void testShutdown() { // GH-90000
        runPromise(() -> consentPlugin.initialize(mockContext) // GH-90000
                .then(v -> consentPlugin.start())); // GH-90000

        Promise<Void> result = consentPlugin.shutdown(); // GH-90000
        runPromise(() -> result); // GH-90000

        assertThat(consentPlugin.getState()).isEqualTo(PluginState.UNLOADED); // GH-90000
    }
}
