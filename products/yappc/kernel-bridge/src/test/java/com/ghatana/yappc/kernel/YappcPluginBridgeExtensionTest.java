package com.ghatana.yappc.kernel;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDescriptor;
import com.ghatana.kernel.module.KernelModule;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.plugin.PluginContext;
import com.ghatana.yappc.plugin.PluginRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link YappcPluginBridgeExtension}.
 *
 * @doc.type class
 * @doc.purpose Verify that the YAPPC bridge extension registers the registry and exposes correct metadata
 * @doc.layer adapter
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("YappcPluginBridgeExtension [GH-90000]")
class YappcPluginBridgeExtensionTest extends EventloopTestBase {

    @Mock
    private KernelContext context;

    @Mock
    private KernelModule hostModule;

    private PluginRegistry pluginRegistry;
    private YappcPluginBridgeExtension extension;

    @BeforeEach
    void setUp() { // GH-90000
        pluginRegistry = PluginRegistry.create(new StubPluginContext()); // GH-90000
        extension = new YappcPluginBridgeExtension(pluginRegistry); // GH-90000
    }

    // ==================== Identity ====================

    @Test
    @DisplayName("extension ID is 'yappc-plugin-bridge' [GH-90000]")
    void extensionIdIsCorrect() { // GH-90000
        assertThat(extension.getExtensionId()).isEqualTo("yappc-plugin-bridge [GH-90000]");
    }

    @Test
    @DisplayName("extension name is human-readable [GH-90000]")
    void extensionNameIsHumanReadable() { // GH-90000
        assertThat(extension.getName()).isEqualTo("YAPPC Plugin Bridge [GH-90000]");
    }

    @Test
    @DisplayName("extension version follows semver [GH-90000]")
    void extensionVersionIsSemver() { // GH-90000
        assertThat(extension.getVersion()).matches("\\d+\\.\\d+\\.\\d+.* [GH-90000]");
    }

    // ==================== Descriptor ====================

    @Test
    @DisplayName("descriptor type is EXTENSION [GH-90000]")
    void descriptorTypeIsExtension() { // GH-90000
        KernelDescriptor descriptor = extension.getDescriptor(); // GH-90000
        assertThat(descriptor.getType()).isEqualTo(KernelDescriptor.DescriptorType.EXTENSION); // GH-90000
    }

    @Test
    @DisplayName("descriptor ID matches extension ID [GH-90000]")
    void descriptorIdMatchesExtensionId() { // GH-90000
        assertThat(extension.getDescriptor().getDescriptorId()).isEqualTo(extension.getExtensionId()); // GH-90000
    }

    // ==================== Capabilities ====================

    @Test
    @DisplayName("contributes YAPPC registry and validator capabilities [GH-90000]")
    void contributesTwoCapabilities() { // GH-90000
        Set<KernelCapability> caps = extension.getContributedCapabilities(); // GH-90000
        assertThat(caps).hasSize(2); // GH-90000
    }

    @Test
    @DisplayName("contributes yappc.plugin-registry capability [GH-90000]")
    void contributesRegistryCapability() { // GH-90000
        Set<KernelCapability> caps = extension.getContributedCapabilities(); // GH-90000
        assertThat(caps).anyMatch(c -> c.getCapabilityId().equals("yappc.plugin-registry [GH-90000]"));
    }

    @Test
    @DisplayName("contributes yappc.code-validators capability [GH-90000]")
    void contributesValidatorCapability() { // GH-90000
        Set<KernelCapability> caps = extension.getContributedCapabilities(); // GH-90000
        assertThat(caps).anyMatch(c -> c.getCapabilityId().equals("yappc.code-validators [GH-90000]"));
    }

    // ==================== Compatibility ====================

    @Test
    @DisplayName("is compatible with any non-null module [GH-90000]")
    void isCompatibleWithAnyModule() { // GH-90000
        assertThat(extension.isCompatible(hostModule)).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("returns false for null host module [GH-90000]")
    void returnsFalseForNullModule() { // GH-90000
        assertThat(extension.isCompatible(null)).isFalse(); // GH-90000
    }

    // ==================== Lifecycle ====================

    @Test
    @DisplayName("onModuleInitialized registers PluginRegistry into context [GH-90000]")
    void onModuleInitializedRegistersRegistry() { // GH-90000
        extension.onModuleInitialized(context); // GH-90000

        verify(context).registerService(eq(PluginRegistry.class), eq(pluginRegistry)); // GH-90000
    }

    @Test
    @DisplayName("onModuleInitialized is idempotent — second call is no-op [GH-90000]")
    void onModuleInitializedIsIdempotent() { // GH-90000
        extension.onModuleInitialized(context); // GH-90000
        extension.onModuleInitialized(context); // GH-90000

        verify(context).registerService(eq(PluginRegistry.class), eq(pluginRegistry)); // GH-90000
    }

    @Test
    @DisplayName("full lifecycle runs without error [GH-90000]")
    void fullLifecycleRunsWithoutError() { // GH-90000
        extension.onModuleInitialized(context); // GH-90000
        extension.onModuleStarted(context); // GH-90000
        extension.onModuleStopped(context); // GH-90000
    }

    // ==================== Construction guard ====================

    @Test
    @DisplayName("null registry is rejected at construction [GH-90000]")
    void nullRegistryIsRejected() { // GH-90000
        assertThatThrownBy(() -> new YappcPluginBridgeExtension(null)) // GH-90000
            .isInstanceOf(NullPointerException.class); // GH-90000
    }

    // ==================== Helpers ====================

    private static class StubPluginContext implements PluginContext {

        @Override
        public Map<String, Object> getConfiguration() { // GH-90000
            return Map.of(); // GH-90000
        }

        @Override
        public Object getConfigValue(String key) { // GH-90000
            return null;
        }

        @Override
        public <T> T getConfigValue(String key, T defaultValue) { // GH-90000
            return defaultValue;
        }

        @Override
        public String getYappcVersion() { // GH-90000
            return "1.0.0-stub";
        }

        @Override
        public String getPluginDirectory() { // GH-90000
            return "/tmp/yappc-plugins-stub";
        }
    }
}
