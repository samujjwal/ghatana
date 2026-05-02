package com.ghatana.plugin.risk.impl;

import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginMetadata;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.plugin.PluginType;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Contract tests for standard risk management plugin manifest and lifecycle
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Risk management plugin contract tests")
@ExtendWith(MockitoExtension.class)
class RiskManagementPluginContractTest extends EventloopTestBase {

    @Mock
    private PluginContext mockContext;

    @Test
    @DisplayName("manifest contract exposes expected metadata fields")
    void manifestContractIsValid() {
        PluginMetadata metadata = new StandardRiskManagementPlugin().metadata();

        assertThat(metadata.id()).isEqualTo("risk-management-plugin");
        assertThat(metadata.name()).isEqualTo("Risk Management Plugin");
        assertThat(metadata.version()).isEqualTo("1.2.0");
        assertThat(metadata.type()).isEqualTo(PluginType.CUSTOM);
    }

    @Test
    @DisplayName("manifest declares all required capabilities")
    void capabilitiesAreComplete() {
        PluginMetadata metadata = new StandardRiskManagementPlugin().metadata();

        assertThat(metadata.capabilities())
            .as("risk management plugin must declare all four canonical capabilities")
            .containsExactlyInAnyOrder(
                "risk:calculate",
                "risk:set-limits",
                "risk:get-alerts",
                "risk:generate-report"
            );
    }

    @Test
    @DisplayName("metadata() returns the same instance on repeated calls (no rebuild)")
    void metadataIsSingletonInstance() {
        StandardRiskManagementPlugin plugin = new StandardRiskManagementPlugin();
        assertThat(plugin.metadata()).isSameAs(plugin.metadata());
    }

    @Test
    @DisplayName("lifecycle contract follows initialize/start/stop/shutdown transitions")
    void lifecycleContractIsValid() {
        StandardRiskManagementPlugin plugin = new StandardRiskManagementPlugin();

        assertThat(plugin.getState()).isEqualTo(PluginState.UNLOADED);
        runPromise(() -> plugin.initialize(mockContext));
        assertThat(plugin.getState()).isEqualTo(PluginState.INITIALIZED);

        runPromise(() -> plugin.start());
        assertThat(plugin.getState()).isEqualTo(PluginState.STARTED);

        runPromise(() -> plugin.stop());
        assertThat(plugin.getState()).isEqualTo(PluginState.STOPPED);

        runPromise(() -> plugin.shutdown());
        assertThat(plugin.getState()).isEqualTo(PluginState.UNLOADED);
    }
}
