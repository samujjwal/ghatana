package com.ghatana.plugin.fraud.impl;

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
 * @doc.purpose Contract tests for standard fraud detection plugin manifest and lifecycle
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Fraud detection plugin contract tests")
@ExtendWith(MockitoExtension.class)
class FraudDetectionPluginContractTest extends EventloopTestBase {

    @Mock
    private PluginContext mockContext;

    @Test
    @DisplayName("manifest contract exposes expected metadata fields")
    void manifestContractIsValid() {
        PluginMetadata metadata = new StandardFraudDetectionPlugin().metadata();

        assertThat(metadata.id()).isEqualTo("fraud-detection-plugin");
        assertThat(metadata.name()).isEqualTo("Fraud Detection Plugin");
        assertThat(metadata.version()).isEqualTo("1.0.0");
        assertThat(metadata.type()).isEqualTo(PluginType.CUSTOM);
    }

    @Test
    @DisplayName("manifest declares all required capabilities")
    void capabilitiesAreComplete() {
        PluginMetadata metadata = new StandardFraudDetectionPlugin().metadata();

        assertThat(metadata.capabilities())
            .as("fraud detection plugin must declare all four canonical capabilities")
            .containsExactlyInAnyOrder(
                "fraud:assess",
                "fraud:detect-patterns",
                "fraud:train-model",
                "fraud:get-metrics"
            );
    }

    @Test
    @DisplayName("metadata() returns the same instance on repeated calls (no rebuild)")
    void metadataIsSingletonInstance() {
        StandardFraudDetectionPlugin plugin = new StandardFraudDetectionPlugin();
        assertThat(plugin.metadata()).isSameAs(plugin.metadata());
    }

    @Test
    @DisplayName("lifecycle contract follows initialize/start/stop/shutdown transitions")
    void lifecycleContractIsValid() {
        StandardFraudDetectionPlugin plugin = new StandardFraudDetectionPlugin();

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
