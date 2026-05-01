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
@ExtendWith(MockitoExtension.class) 
@DisplayName("YappcPluginBridgeExtension")
class YappcPluginBridgeExtensionTest extends EventloopTestBase {

    @Mock
    private KernelContext context;

    @Mock
    private KernelModule hostModule;

    private PluginRegistry pluginRegistry;
    private YappcPluginBridgeExtension extension;

    @BeforeEach
    void setUp() { 
        pluginRegistry = PluginRegistry.create(new StubPluginContext()); 
        extension = new YappcPluginBridgeExtension(pluginRegistry); 
    }

    // ==================== Identity ====================

    @Test
    @DisplayName("extension ID is 'yappc-plugin-bridge'")
    void extensionIdIsCorrect() { 
        assertThat(extension.getExtensionId()).isEqualTo("yappc-plugin-bridge");
    }

    @Test
    @DisplayName("extension name is human-readable")
    void extensionNameIsHumanReadable() { 
        assertThat(extension.getName()).isEqualTo("YAPPC Plugin Bridge");
    }

    @Test
    @DisplayName("extension version follows semver")
    void extensionVersionIsSemver() { 
        assertThat(extension.getVersion()).matches("\\d+\\.\\d+\\.\\d+.*");
    }

    // ==================== Descriptor ====================

    @Test
    @DisplayName("descriptor type is EXTENSION")
    void descriptorTypeIsExtension() { 
        KernelDescriptor descriptor = extension.getDescriptor(); 
        assertThat(descriptor.getType()).isEqualTo(KernelDescriptor.DescriptorType.EXTENSION); 
    }

    @Test
    @DisplayName("descriptor ID matches extension ID")
    void descriptorIdMatchesExtensionId() { 
        assertThat(extension.getDescriptor().getDescriptorId()).isEqualTo(extension.getExtensionId()); 
    }

    // ==================== Capabilities ====================

    @Test
    @DisplayName("contributes YAPPC registry and validator capabilities")
    void contributesTwoCapabilities() { 
        Set<KernelCapability> caps = extension.getContributedCapabilities(); 
        assertThat(caps).hasSize(2); 
    }

    @Test
    @DisplayName("contributes yappc.plugin-registry capability")
    void contributesRegistryCapability() { 
        Set<KernelCapability> caps = extension.getContributedCapabilities(); 
        assertThat(caps).anyMatch(c -> c.getCapabilityId().equals("yappc.plugin-registry"));
    }

    @Test
    @DisplayName("contributes yappc.code-validators capability")
    void contributesValidatorCapability() { 
        Set<KernelCapability> caps = extension.getContributedCapabilities(); 
        assertThat(caps).anyMatch(c -> c.getCapabilityId().equals("yappc.code-validators"));
    }

    // ==================== Compatibility ====================

    @Test
    @DisplayName("is compatible with any non-null module")
    void isCompatibleWithAnyModule() { 
        assertThat(extension.isCompatible(hostModule)).isTrue(); 
    }

    @Test
    @DisplayName("returns false for null host module")
    void returnsFalseForNullModule() { 
        assertThat(extension.isCompatible(null)).isFalse(); 
    }

    // ==================== Lifecycle ====================

    @Test
    @DisplayName("onModuleInitialized registers PluginRegistry into context")
    void onModuleInitializedRegistersRegistry() { 
        extension.onModuleInitialized(context); 

        verify(context).registerService(eq(PluginRegistry.class), eq(pluginRegistry)); 
    }

    @Test
    @DisplayName("onModuleInitialized is idempotent — second call is no-op")
    void onModuleInitializedIsIdempotent() { 
        extension.onModuleInitialized(context); 
        extension.onModuleInitialized(context); 

        verify(context).registerService(eq(PluginRegistry.class), eq(pluginRegistry)); 
    }

    @Test
    @DisplayName("full lifecycle runs without error")
    void fullLifecycleRunsWithoutError() { 
        extension.onModuleInitialized(context); 
        extension.onModuleStarted(context); 
        extension.onModuleStopped(context); 
    }

    // ==================== Construction guard ====================

    @Test
    @DisplayName("null registry is rejected at construction")
    void nullRegistryIsRejected() { 
        assertThatThrownBy(() -> new YappcPluginBridgeExtension(null)) 
            .isInstanceOf(NullPointerException.class); 
    }

    // ==================== Helpers ====================

    private static class StubPluginContext implements PluginContext {

        @Override
        public Map<String, Object> getConfiguration() { 
            return Map.of(); 
        }

        @Override
        public Object getConfigValue(String key) { 
            return null;
        }

        @Override
        public <T> T getConfigValue(String key, T defaultValue) { 
            return defaultValue;
        }

        @Override
        public String getYappcVersion() { 
            return "1.0.0-stub";
        }

        @Override
        public String getPluginDirectory() { 
            return "/tmp/yappc-plugins-stub";
        }
    }
}
