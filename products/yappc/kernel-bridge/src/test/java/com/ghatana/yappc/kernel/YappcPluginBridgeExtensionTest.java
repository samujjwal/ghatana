package com.ghatana.yappc.kernel;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDescriptor;
import com.ghatana.kernel.module.KernelModule;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.plugin.PluginContext;
import com.ghatana.yappc.plugin.PluginMetadata;
import com.ghatana.yappc.plugin.PluginRegistry;
import com.ghatana.yappc.plugin.YAPPCPlugin;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link YappcPluginBridgeExtension}.
 *
 * @doc.type test
 * @doc.purpose Verify that the YAPPC bridge extension registers only narrow provider ports and exposes correct metadata
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

    @Mock
    private YappcProductUnitIntentProvider productUnitIntentProvider;

    @Mock
    private YappcSemanticArtifactEvidenceProvider semanticArtifactEvidenceProvider;

    @Mock
    private YappcArtifactGraphSummaryProvider artifactGraphSummaryProvider;

    @Mock
    private YappcResidualIslandReportProvider residualIslandReportProvider;

    @Mock
    private YappcRiskHotspotReportProvider riskHotspotReportProvider;

    private YappcPluginBridgeExtension extension;

    @BeforeEach
    void setUp() { 
        extension = new YappcPluginBridgeExtension(
            productUnitIntentProvider,
            semanticArtifactEvidenceProvider,
            artifactGraphSummaryProvider,
            residualIslandReportProvider,
            riskHotspotReportProvider
        );
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
    @DisplayName("contributes YAPPC intent and artifact-intelligence capabilities")
    void contributesTwoCapabilities() { 
        Set<KernelCapability> caps = extension.getContributedCapabilities(); 
        assertThat(caps).hasSize(2); 
    }

    @Test
    @DisplayName("contributes yappc.product-unit-intents capability")
    void contributesProductUnitIntentCapability() {
        Set<KernelCapability> caps = extension.getContributedCapabilities(); 
        assertThat(caps).anyMatch(c -> c.getCapabilityId().equals("yappc.product-unit-intents"));
    }

    @Test
    @DisplayName("contributes yappc.artifact-intelligence capability")
    void contributesArtifactIntelligenceCapability() {
        Set<KernelCapability> caps = extension.getContributedCapabilities(); 
        assertThat(caps).anyMatch(c -> c.getCapabilityId().equals("yappc.artifact-intelligence"));
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
    @DisplayName("onModuleInitialized registers only narrow YAPPC provider ports into context")
    void onModuleInitializedRegistersNarrowProviderPorts() {
        extension.onModuleInitialized(context); 

        verify(context).registerService(eq(YappcProductUnitIntentProvider.class), eq(productUnitIntentProvider));
        verify(context).registerService(eq(YappcSemanticArtifactEvidenceProvider.class), eq(semanticArtifactEvidenceProvider));
        verify(context).registerService(eq(YappcArtifactGraphSummaryProvider.class), eq(artifactGraphSummaryProvider));
        verify(context).registerService(eq(YappcResidualIslandReportProvider.class), eq(residualIslandReportProvider));
        verify(context).registerService(eq(YappcRiskHotspotReportProvider.class), eq(riskHotspotReportProvider));
        verify(context, never()).registerService(eq(PluginRegistry.class), any(PluginRegistry.class));
    }

    @Test
    @DisplayName("onModuleInitialized is idempotent; second call is no-op")
    void onModuleInitializedIsIdempotent() { 
        extension.onModuleInitialized(context); 
        extension.onModuleInitialized(context); 

        verify(context).registerService(eq(YappcProductUnitIntentProvider.class), eq(productUnitIntentProvider));
        verify(context).registerService(eq(YappcSemanticArtifactEvidenceProvider.class), eq(semanticArtifactEvidenceProvider));
        verify(context).registerService(eq(YappcArtifactGraphSummaryProvider.class), eq(artifactGraphSummaryProvider));
        verify(context).registerService(eq(YappcResidualIslandReportProvider.class), eq(residualIslandReportProvider));
        verify(context).registerService(eq(YappcRiskHotspotReportProvider.class), eq(riskHotspotReportProvider));
        verify(context, never()).registerService(eq(PluginRegistry.class), any(PluginRegistry.class));
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
    @DisplayName("null providers are rejected at construction")
    void nullProvidersAreRejected() {
        assertThatThrownBy(() -> new YappcPluginBridgeExtension(
            null,
            semanticArtifactEvidenceProvider,
            artifactGraphSummaryProvider,
            residualIslandReportProvider,
            riskHotspotReportProvider
        )).isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new YappcPluginBridgeExtension(
            productUnitIntentProvider,
            null,
            artifactGraphSummaryProvider,
            residualIslandReportProvider,
            riskHotspotReportProvider
        )).isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new YappcPluginBridgeExtension(
            productUnitIntentProvider,
            semanticArtifactEvidenceProvider,
            null,
            residualIslandReportProvider,
            riskHotspotReportProvider
        )).isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new YappcPluginBridgeExtension(
            productUnitIntentProvider,
            semanticArtifactEvidenceProvider,
            artifactGraphSummaryProvider,
            null,
            riskHotspotReportProvider
        )).isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new YappcPluginBridgeExtension(
            productUnitIntentProvider,
            semanticArtifactEvidenceProvider,
            artifactGraphSummaryProvider,
            residualIslandReportProvider,
            null
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("compatibility adapter converts PluginRegistry state into stable evidence maps")
    void compatibilityAdapterConvertsRegistryStateIntoStableEvidence() {
        PluginRegistry pluginRegistry = PluginRegistry.create(new StubPluginContext());
        YAPPCPlugin plugin = org.mockito.Mockito.mock(YAPPCPlugin.class);
        when(plugin.getMetadata()).thenReturn(PluginMetadata.builder().id("artifact-scanner").name("Artifact Scanner").build());
        when(plugin.initialize(any())).thenReturn(Promise.complete());
        runPromise(() -> pluginRegistry.registerPlugin(plugin));

        YappcPluginRegistryEvidenceAdapter adapter = new YappcPluginRegistryEvidenceAdapter(pluginRegistry);

        Map<String, Object> evidence = runPromise(() ->
            adapter.semanticArtifactEvidence("artifact-123", Map.of("correlationId", "corr-9"))
        );

        assertThat(evidence)
            .containsEntry("schemaVersion", "1.0.0")
            .containsEntry("kind", "semantic-artifact")
            .containsEntry("subjectId", "artifact-123")
            .containsEntry("source", "yappc-plugin-registry-adapter")
            .containsEntry("pluginCount", 1);
        assertThat(evidence.get("request")).isEqualTo(Map.of("correlationId", "corr-9"));
        assertThat(evidence.get("createdAt")).isInstanceOf(String.class);
    }

    @Test
    @DisplayName("kernel modules do not import YAPPC compiler scanner or plugin internals")
    void kernelModulesDoNotImportYappcInternals() throws IOException {
        Path kernelSource = repositoryRoot()
            .resolve("platform-kernel")
            .resolve("kernel-core")
            .resolve("src")
            .resolve("main")
            .resolve("java");

        try (Stream<Path> javaFiles = Files.walk(kernelSource)) {
            assertThat(javaFiles
                .filter(path -> path.toString().endsWith(".java"))
                .map(YappcPluginBridgeExtensionTest::readSource)
                .filter(source -> source.contains("com.ghatana.yappc"))
                .toList())
                .isEmpty();
        }
    }

    private static String readSource(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read " + path, exception);
        }
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("settings.gradle.kts"))
                    && Files.isDirectory(current.resolve("platform-kernel"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate repository root from " + Path.of("").toAbsolutePath());
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
