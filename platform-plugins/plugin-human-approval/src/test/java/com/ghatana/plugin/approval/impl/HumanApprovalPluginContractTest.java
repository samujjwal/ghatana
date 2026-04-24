package com.ghatana.plugin.approval.impl;

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
 * @doc.purpose Contract tests for standard human approval plugin manifest and lifecycle
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Human approval plugin contract tests")
@ExtendWith(MockitoExtension.class)
class HumanApprovalPluginContractTest extends EventloopTestBase {

    @Mock
    private PluginContext mockContext;

    @Test
    @DisplayName("manifest contract exposes expected metadata fields")
    void manifestContractIsValid() {
        PluginMetadata metadata = new StandardHumanApprovalPlugin().metadata();

        assertThat(metadata.id()).isEqualTo("com.ghatana.plugin.human-approval");
        assertThat(metadata.name()).isEqualTo("Human Approval Plugin");
        assertThat(metadata.version()).isEqualTo("1.0.0");
        assertThat(metadata.type()).isEqualTo(PluginType.GOVERNANCE);
    }

    @Test
    @DisplayName("lifecycle contract follows initialize/start/stop/shutdown transitions")
    void lifecycleContractIsValid() {
        StandardHumanApprovalPlugin plugin = new StandardHumanApprovalPlugin();

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
