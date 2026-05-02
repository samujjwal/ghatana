package com.ghatana.plugin.compliance.impl;

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
 * @doc.purpose Contract tests for standard compliance plugin manifest and lifecycle
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Compliance plugin contract tests")
@ExtendWith(MockitoExtension.class)
class CompliancePluginContractTest extends EventloopTestBase {

    @Mock
    private PluginContext mockContext;

    @Test
    @DisplayName("manifest contract exposes expected metadata fields")
    void manifestContractIsValid() {
        PluginMetadata metadata = new StandardCompliancePlugin().metadata();

        assertThat(metadata.id()).isEqualTo("com.ghatana.plugin.compliance");
        assertThat(metadata.name()).isEqualTo("Compliance Plugin");
        assertThat(metadata.version()).isEqualTo("1.2.0");
        assertThat(metadata.type()).isEqualTo(PluginType.GOVERNANCE);
    }

    @Test
    @DisplayName("lifecycle contract follows initialize/start/stop/shutdown transitions")
    void lifecycleContractIsValid() {
        StandardCompliancePlugin plugin = new StandardCompliancePlugin();

        assertThat(plugin.getState()).isEqualTo(PluginState.UNLOADED);
        runPromise(() -> plugin.initialize(mockContext));
        assertThat(plugin.getState()).isEqualTo(PluginState.INITIALIZED);

        runPromise(() -> plugin.start());
        assertThat(plugin.getState()).isEqualTo(PluginState.RUNNING);

        runPromise(() -> plugin.stop());
        assertThat(plugin.getState()).isEqualTo(PluginState.STOPPED);

        runPromise(() -> plugin.shutdown());
        assertThat(plugin.getState()).isEqualTo(PluginState.UNLOADED);
    }
}
