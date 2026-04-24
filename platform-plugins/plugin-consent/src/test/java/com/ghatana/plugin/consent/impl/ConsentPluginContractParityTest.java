package com.ghatana.plugin.consent.impl;

import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginMetadata;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.plugin.PluginType;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.plugin.consent.ConsentPlugin;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * @doc.type class
 * @doc.purpose Contract parity tests across standard and durable consent implementations
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Consent plugin contract parity tests")
@ExtendWith(MockitoExtension.class)
class ConsentPluginContractParityTest extends EventloopTestBase {

    @Mock
    private PluginContext mockContext;

    @Test
    @DisplayName("manifest contract: both implementations expose valid metadata")
    void manifestContractIsValid() {
        PluginMetadata standard = new StandardConsentPlugin().metadata();
        PluginMetadata durable = new DurableConsentPlugin(testDataSource()).metadata();

        assertThat(standard.id()).isNotBlank();
        assertThat(standard.name()).isNotBlank();
        assertThat(standard.version()).isNotBlank();
        assertThat(standard.type()).isEqualTo(PluginType.GOVERNANCE);

        assertThat(durable.id()).isNotBlank();
        assertThat(durable.name()).isNotBlank();
        assertThat(durable.version()).isNotBlank();
        assertThat(durable.type()).isEqualTo(PluginType.GOVERNANCE);
    }

    @Test
    @DisplayName("lifecycle contract: both implementations follow initialize/start/stop/shutdown")
    void lifecycleParityAcrossImplementations() {
        StandardConsentPlugin standard = new StandardConsentPlugin();
        DurableConsentPlugin durable = new DurableConsentPlugin(testDataSource());
        durable.ensureSchema();

        assertThat(standard.getState()).isEqualTo(PluginState.UNLOADED);
        assertThat(durable.getState()).isEqualTo(PluginState.UNLOADED);

        runPromise(() -> standard.initialize(mockContext));
        runPromise(() -> durable.initialize(mockContext));
        assertThat(standard.getState()).isEqualTo(PluginState.INITIALIZED);
        assertThat(durable.getState()).isEqualTo(PluginState.INITIALIZED);

        runPromise(() -> standard.start());
        runPromise(() -> durable.start());
        assertThat(standard.getState()).isEqualTo(PluginState.RUNNING);
        assertThat(durable.getState()).isEqualTo(PluginState.STARTED);

        runPromise(() -> standard.stop());
        runPromise(() -> durable.stop());
        assertThat(standard.getState()).isEqualTo(PluginState.STOPPED);
        assertThat(durable.getState()).isEqualTo(PluginState.STOPPED);

        runPromise(() -> standard.shutdown());
        runPromise(() -> durable.shutdown());
        assertThat(standard.getState()).isEqualTo(PluginState.UNLOADED);
        assertThat(durable.getState()).isEqualTo(PluginState.UNLOADED);
    }

    @Test
    @DisplayName("behavior parity: both implementations support record/verify/history/revoke")
    void behaviorParityForCoreConsentContract() {
        StandardConsentPlugin standard = new StandardConsentPlugin();
        DurableConsentPlugin durable = new DurableConsentPlugin(testDataSource());
        durable.ensureSchema();

        runPromise(() -> standard.initialize(mockContext).then(v -> standard.start()));
        runPromise(() -> durable.initialize(mockContext).then(v -> durable.start()));

        ConsentPlugin.ConsentRecord standardGrant = runPromise(() ->
                standard.recordConsent("contract-subject", "contract-purpose", ConsentPlugin.ConsentAction.GRANT));
        ConsentPlugin.ConsentRecord durableGrant = runPromise(() ->
                durable.recordConsent("contract-subject", "contract-purpose", ConsentPlugin.ConsentAction.GRANT));

        assertThat(runPromise(() -> standard.verifyConsent("contract-subject", "contract-purpose"))).isTrue();
        assertThat(runPromise(() -> durable.verifyConsent("contract-subject", "contract-purpose"))).isTrue();

        assertThat(runPromise(() -> standard.getCurrentConsent("contract-subject", "contract-purpose")))
                .isEqualTo(ConsentPlugin.ConsentStatus.GRANTED);
        assertThat(runPromise(() -> durable.getCurrentConsent("contract-subject", "contract-purpose")))
                .isEqualTo(ConsentPlugin.ConsentStatus.GRANTED);

        assertThat(runPromise(() -> standard.getConsentHistory("contract-subject"))).isNotEmpty();
        assertThat(runPromise(() -> durable.getConsentHistory("contract-subject"))).isNotEmpty();

        runPromise(() -> standard.revokeConsent(standardGrant.consentId()));
        runPromise(() -> durable.revokeConsent(durableGrant.consentId()));

        assertThat(runPromise(() -> standard.verifyConsent("contract-subject", "contract-purpose"))).isFalse();
        assertThat(runPromise(() -> durable.verifyConsent("contract-subject", "contract-purpose"))).isFalse();
    }

    @Test
    @DisplayName("migration contract: durable ensureSchema is idempotent")
    void durableEnsureSchemaIsIdempotent() {
        DurableConsentPlugin durable = new DurableConsentPlugin(testDataSource());

        assertThatCode(() -> {
            durable.ensureSchema();
            durable.ensureSchema();
        }).doesNotThrowAnyException();
    }

    private JdbcDataSource testDataSource() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:consent_contract_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        return ds;
    }
}
