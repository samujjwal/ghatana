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
@DisplayName("StandardConsentPlugin Tests")
@ExtendWith(MockitoExtension.class)
class StandardConsentPluginTest extends EventloopTestBase {

    @Mock
    private PluginContext mockContext;

    private StandardConsentPlugin consentPlugin;

    @BeforeEach
    void setUp() {
        consentPlugin = new StandardConsentPlugin();
    }

    @Test
    @DisplayName("Should initialize consent plugin")
    void testInitialize() {
        assertThat(consentPlugin.getState()).isEqualTo(PluginState.UNLOADED);
        Promise<Void> result = consentPlugin.initialize(mockContext);
        runPromise(() -> result);
        assertThat(consentPlugin.getState()).isEqualTo(PluginState.INITIALIZED);
    }

    @Test
    @DisplayName("Should start consent plugin")
    void testStart() {
        runPromise(() -> consentPlugin.initialize(mockContext));
        Promise<Void> result = consentPlugin.start();
        runPromise(() -> result);
        assertThat(consentPlugin.getState()).isEqualTo(PluginState.RUNNING);
    }

    @Test
    @DisplayName("Should return correct metadata")
    void testMetadata() {
        var metadata = consentPlugin.metadata();
        assertThat(metadata.name()).isEqualTo("Consent Plugin");
        assertThat(metadata.version()).isEqualTo("1.0.0");
    }

    @Test
    @DisplayName("Should record grant consent")
    void testRecordConsent_Grant() {
        runPromise(() -> consentPlugin.initialize(mockContext)
                .then(v -> consentPlugin.start()));

        Promise<ConsentPlugin.ConsentRecord> result = consentPlugin.recordConsent(
            "subject1", "marketing", ConsentPlugin.ConsentAction.GRANT);
        ConsentPlugin.ConsentRecord record = runPromise(() -> result);

        assertThat(record.subjectId()).isEqualTo("subject1");
        assertThat(record.purpose()).isEqualTo("marketing");
        assertThat(record.status()).isEqualTo(ConsentPlugin.ConsentStatus.GRANTED);
        assertThat(record.consentId()).isNotNull();
    }

    @Test
    @DisplayName("Should record deny consent")
    void testRecordConsent_Deny() {
        runPromise(() -> consentPlugin.initialize(mockContext)
                .then(v -> consentPlugin.start()));

        Promise<ConsentPlugin.ConsentRecord> result = consentPlugin.recordConsent(
            "subject2", "analytics", ConsentPlugin.ConsentAction.DENY);
        ConsentPlugin.ConsentRecord record = runPromise(() -> result);

        assertThat(record.status()).isEqualTo(ConsentPlugin.ConsentStatus.DENIED);
    }

    @Test
    @DisplayName("Should verify granted consent")
    void testVerifyConsent_Granted() {
        runPromise(() -> consentPlugin.initialize(mockContext)
                .then(v -> consentPlugin.start())
                .then(v -> consentPlugin.recordConsent("subject3", "email",
                    ConsentPlugin.ConsentAction.GRANT)));

        Promise<Boolean> result = consentPlugin.verifyConsent("subject3", "email");
        Boolean hasConsent = runPromise(() -> result);

        assertThat(hasConsent).isTrue();
    }

    @Test
    @DisplayName("Should deny non-existent consent")
    void testVerifyConsent_NotFound() {
        runPromise(() -> consentPlugin.initialize(mockContext)
                .then(v -> consentPlugin.start()));

        Promise<Boolean> result = consentPlugin.verifyConsent("subject4", "sms");
        Boolean hasConsent = runPromise(() -> result);

        assertThat(hasConsent).isFalse();
    }

    @Test
    @DisplayName("Should revoke consent")
    void testRevokeConsent() {
        runPromise(() -> consentPlugin.initialize(mockContext)
                .then(v -> consentPlugin.start()));

        Promise<ConsentPlugin.ConsentRecord> recordResult = consentPlugin.recordConsent(
            "subject5", "newsletter", ConsentPlugin.ConsentAction.GRANT);
        ConsentPlugin.ConsentRecord record = runPromise(() -> recordResult);

        Promise<Void> revokeResult = consentPlugin.revokeConsent(record.consentId());
        runPromise(() -> revokeResult);

        // Verify revoked
        Promise<Boolean> verifyResult = consentPlugin.verifyConsent("subject5", "newsletter");
        Boolean hasConsent = runPromise(() -> verifyResult);

        assertThat(hasConsent).isFalse();
    }

    @Test
    @DisplayName("Should get consent history for subject")
    void testGetConsentHistory() {
        runPromise(() -> consentPlugin.initialize(mockContext)
                .then(v -> consentPlugin.start())
                .then(v -> consentPlugin.recordConsent("subject6", "marketing",
                    ConsentPlugin.ConsentAction.GRANT))
                .then(v -> consentPlugin.recordConsent("subject6", "analytics",
                    ConsentPlugin.ConsentAction.DENY)));

        Promise<List<ConsentPlugin.ConsentRecord>> result =
                consentPlugin.getConsentHistory("subject6");
        List<ConsentPlugin.ConsentRecord> history = runPromise(() -> result);

        assertThat(history).hasSize(2);
    }

    @Test
    @DisplayName("Should get current consent status")
    void testGetCurrentConsent() {
        runPromise(() -> consentPlugin.initialize(mockContext)
                .then(v -> consentPlugin.start())
                .then(v -> consentPlugin.recordConsent("subject7", "sms",
                    ConsentPlugin.ConsentAction.GRANT)));

        Promise<ConsentPlugin.ConsentStatus> result =
                consentPlugin.getCurrentConsent("subject7", "sms");
        ConsentPlugin.ConsentStatus status = runPromise(() -> result);

        assertThat(status).isEqualTo(ConsentPlugin.ConsentStatus.GRANTED);
    }

    @Test
    @DisplayName("Should shutdown consent plugin")
    void testShutdown() {
        runPromise(() -> consentPlugin.initialize(mockContext)
                .then(v -> consentPlugin.start()));

        Promise<Void> result = consentPlugin.shutdown();
        runPromise(() -> result);

        assertThat(consentPlugin.getState()).isEqualTo(PluginState.UNLOADED);
    }
}
