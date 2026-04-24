package com.ghatana.plugin.audit.impl;

import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginMetadata;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.plugin.PluginType;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.plugin.audit.AuditTrailPlugin;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Cross-implementation contract and parity tests for audit trail plugins.
 *
 * @doc.type class
 * @doc.purpose Verify manifest/lifecycle contract compatibility and behavior parity
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("AuditTrailPlugin Contract and Parity")
@ExtendWith(MockitoExtension.class)
class AuditTrailPluginContractParityTest extends EventloopTestBase {

    @Mock
    private PluginContext mockContext;

    @Test
    @DisplayName("manifest contract: both implementations expose valid plugin metadata")
    void manifestContractIsValid() {
        PluginMetadata standard = new StandardAuditTrailPlugin().metadata();
        PluginMetadata durable = new DurableAuditTrailPlugin(testDataSource()).metadata();

        assertThat(standard.id()).isNotBlank();
        assertThat(standard.name()).isNotBlank();
        assertThat(standard.version()).isNotBlank();
        assertThat(standard.type()).isEqualTo(PluginType.CUSTOM);
        assertThat(standard.properties())
            .containsEntry("variant", "standard-in-memory")
            .containsEntry("durability", "non-durable");
        assertThat(standard.properties().get("unsupportedExportFormats"))
            .isEqualTo(List.of("PDF"));

        assertThat(durable.id()).isNotBlank();
        assertThat(durable.name()).isNotBlank();
        assertThat(durable.version()).isNotBlank();
        assertThat(durable.type()).isEqualTo(PluginType.CUSTOM);
        assertThat(durable.properties())
            .containsEntry("variant", "durable-jdbc")
            .containsEntry("durability", "durable");
        assertThat(durable.properties().get("unsupportedExportFormats"))
            .isEqualTo(List.of("PDF"));

        assertThat(standard.properties().get("supportedExportFormats"))
            .isEqualTo(durable.properties().get("supportedExportFormats"));
    }

    @Test
    @DisplayName("lifecycle contract: both implementations follow initialize/start/stop/shutdown transitions")
    void lifecycleParityAcrossImplementations() {
        StandardAuditTrailPlugin standard = new StandardAuditTrailPlugin();
        DurableAuditTrailPlugin durable = new DurableAuditTrailPlugin(testDataSource());
        durable.ensureSchema();

        assertThat(standard.getState()).isEqualTo(PluginState.UNLOADED);
        assertThat(durable.getState()).isEqualTo(PluginState.UNLOADED);

        runPromise(() -> standard.initialize(mockContext));
        runPromise(() -> durable.initialize(mockContext));
        assertThat(standard.getState()).isEqualTo(PluginState.INITIALIZED);
        assertThat(durable.getState()).isEqualTo(PluginState.INITIALIZED);

        runPromise(() -> standard.start());
        runPromise(() -> durable.start());
        assertThat(standard.getState()).isEqualTo(PluginState.STARTED);
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
    @DisplayName("behavior parity: both implementations log/get/verify and export JSON")
    void behaviorParityForCoreAuditContract() {
        StandardAuditTrailPlugin standard = new StandardAuditTrailPlugin();
        DurableAuditTrailPlugin durable = new DurableAuditTrailPlugin(testDataSource());
        durable.ensureSchema();

        runPromise(() -> standard.initialize(mockContext).then(v -> standard.start()));
        runPromise(() -> durable.initialize(mockContext).then(v -> durable.start()));

        Map<String, Object> details = Map.of("actorId", "contract-user", "scope", "contract");

        runPromise(() -> standard.logEvent("entity-contract", "CREATE", details));
        runPromise(() -> durable.logEvent("entity-contract", "CREATE", details));

        List<AuditTrailPlugin.AuditEntry> standardTrail = runPromise(() -> standard.getTrail("entity-contract"));
        List<AuditTrailPlugin.AuditEntry> durableTrail = runPromise(() -> durable.getTrail("entity-contract"));

        assertThat(standardTrail).hasSize(1);
        assertThat(durableTrail).hasSize(1);
        assertThat(standardTrail.get(0).action()).isEqualTo("CREATE");
        assertThat(durableTrail.get(0).action()).isEqualTo("CREATE");

        AuditTrailPlugin.VerificationResult standardVerify = runPromise(() -> standard.verifyIntegrity("entity-contract"));
        AuditTrailPlugin.VerificationResult durableVerify = runPromise(() -> durable.verifyIntegrity("entity-contract"));

        assertThat(standardVerify.valid()).isTrue();
        assertThat(durableVerify.valid()).isTrue();

        ByteArrayOutputStream standardOut = new ByteArrayOutputStream();
        ByteArrayOutputStream durableOut = new ByteArrayOutputStream();
        runPromise(() -> standard.exportTrail("entity-contract", AuditTrailPlugin.ExportFormat.JSON, standardOut));
        runPromise(() -> durable.exportTrail("entity-contract", AuditTrailPlugin.ExportFormat.JSON, durableOut));

        String standardJson = standardOut.toString(StandardCharsets.UTF_8);
        String durableJson = durableOut.toString(StandardCharsets.UTF_8);

        assertThat(standardJson).contains("entity-contract", "CREATE");
        assertThat(durableJson).contains("entity-contract", "CREATE");
    }

    @Test
    @DisplayName("migration contract: durable ensureSchema is idempotent")
    void durableEnsureSchemaIsIdempotent() {
        DurableAuditTrailPlugin durable = new DurableAuditTrailPlugin(testDataSource());

        assertThatCode(() -> {
            durable.ensureSchema();
            durable.ensureSchema();
        }).doesNotThrowAnyException();
    }

    private JdbcDataSource testDataSource() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:audit_contract_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        return ds;
    }
}
