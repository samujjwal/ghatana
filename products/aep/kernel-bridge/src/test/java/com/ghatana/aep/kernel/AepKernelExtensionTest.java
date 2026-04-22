package com.ghatana.aep.kernel;

import com.ghatana.kernel.adapter.aep.AepKernelAdapter;
import com.ghatana.kernel.adapter.aep.AepKernelAdapterImpl;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDescriptor;
import com.ghatana.kernel.module.KernelModule;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link AepKernelExtension}.
 *
 * @doc.type class
 * @doc.purpose Verify that the AEP bridge extension registers the adapter and exposes correct metadata
 * @doc.layer adapter
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("AepKernelExtension [GH-90000]")
class AepKernelExtensionTest extends EventloopTestBase {

    @Mock
    private KernelContext context;

    @Mock
    private KernelModule hostModule;

    private AepKernelAdapterImpl.AepClient stubClient;
    private AepKernelExtension extension;

    @BeforeEach
    void setUp() { // GH-90000
        stubClient = new StubAepClient(); // GH-90000
        extension = new AepKernelExtension(stubClient); // GH-90000
    }

    // ==================== Identity ====================

    @Test
    @DisplayName("extension ID is 'aep-kernel-bridge' [GH-90000]")
    void extensionIdIsCorrect() { // GH-90000
        assertThat(extension.getExtensionId()).isEqualTo("aep-kernel-bridge [GH-90000]");
    }

    @Test
    @DisplayName("extension name is human-readable [GH-90000]")
    void extensionNameIsHumanReadable() { // GH-90000
        assertThat(extension.getName()).isEqualTo("AEP Kernel Bridge [GH-90000]");
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
    @DisplayName("contributes three AEP capabilities [GH-90000]")
    void contributesThreeCapabilities() { // GH-90000
        Set<KernelCapability> caps = extension.getContributedCapabilities(); // GH-90000
        assertThat(caps).hasSize(3); // GH-90000
    }

    @Test
    @DisplayName("contributes aep.event-streaming capability [GH-90000]")
    void contributesEventStreamingCapability() { // GH-90000
        Set<KernelCapability> caps = extension.getContributedCapabilities(); // GH-90000
        assertThat(caps).anyMatch(c -> c.getCapabilityId().equals("aep.event-streaming [GH-90000]"));
    }

    @Test
    @DisplayName("contributes aep.agent-runtime capability [GH-90000]")
    void contributesAgentRuntimeCapability() { // GH-90000
        Set<KernelCapability> caps = extension.getContributedCapabilities(); // GH-90000
        assertThat(caps).anyMatch(c -> c.getCapabilityId().equals("aep.agent-runtime [GH-90000]"));
    }

    @Test
    @DisplayName("contributes aep.pipeline-orchestration capability [GH-90000]")
    void contributesPipelineCapability() { // GH-90000
        Set<KernelCapability> caps = extension.getContributedCapabilities(); // GH-90000
        assertThat(caps).anyMatch(c -> c.getCapabilityId().equals("aep.pipeline-orchestration [GH-90000]"));
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
    @DisplayName("onModuleInitialized registers AepKernelAdapter into context [GH-90000]")
    void onModuleInitializedRegistersAdapter() { // GH-90000
        extension.onModuleInitialized(context); // GH-90000

        verify(context).registerService(eq(AepKernelAdapter.class), any(AepKernelAdapter.class)); // GH-90000
    }

    @Test
    @DisplayName("onModuleInitialized is idempotent — second call is no-op [GH-90000]")
    void onModuleInitializedIsIdempotent() { // GH-90000
        extension.onModuleInitialized(context); // GH-90000
        extension.onModuleInitialized(context); // GH-90000

        verify(context).registerService(eq(AepKernelAdapter.class), any(AepKernelAdapter.class)); // GH-90000
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
    @DisplayName("null client is rejected at construction [GH-90000]")
    void nullClientIsRejected() { // GH-90000
        assertThatThrownBy(() -> new AepKernelExtension(null)) // GH-90000
            .isInstanceOf(NullPointerException.class); // GH-90000
    }

    // ==================== Helpers ====================

    private static class StubAepClient implements AepKernelAdapterImpl.AepClient {

        @Override
        public CompletableFuture<Void> publishEvent(String streamId, String eventId, String eventType, // GH-90000
                                                     byte[] payload, Map<String, String> headers, long timestamp) {
            return CompletableFuture.completedFuture(null); // GH-90000
        }

        @Override
        public CompletableFuture<AepKernelAdapterImpl.InnerSubscription> subscribe( // GH-90000
                String streamId, AepKernelAdapterImpl.InnerEventHandler handler) {
            return CompletableFuture.completedFuture(new AepKernelAdapterImpl.InnerSubscription() { // GH-90000
                @Override
                public String getId() { return "stub-sub"; } // GH-90000

                @Override
                public CompletableFuture<Void> unsubscribe() { // GH-90000
                    return CompletableFuture.completedFuture(null); // GH-90000
                }
            });
        }

        @Override
        public CompletableFuture<Void> createStream(String streamId, String streamType, // GH-90000
                                                     Map<String, String> config, int partitionCount,
                                                     Duration retention) {
            return CompletableFuture.completedFuture(null); // GH-90000
        }

        @Override
        public CompletableFuture<Void> deleteStream(String streamId) { // GH-90000
            return CompletableFuture.completedFuture(null); // GH-90000
        }

        @Override
        public CompletableFuture<AepKernelAdapterImpl.DeployResult> deployAgent( // GH-90000
                String agentId, String agentType, String version,
                Map<String, Object> config, int instanceCount) {
            return CompletableFuture.completedFuture(new AepKernelAdapterImpl.DeployResult("stub-endpoint [GH-90000]"));
        }

        @Override
        public CompletableFuture<Void> undeployAgent(String agentId) { // GH-90000
            return CompletableFuture.completedFuture(null); // GH-90000
        }

        @Override
        public CompletableFuture<AepKernelAdapterImpl.InnerAgentStatus> getAgentStatus(String agentId) { // GH-90000
            return CompletableFuture.completedFuture( // GH-90000
                new AepKernelAdapterImpl.InnerAgentStatus("RUNNING", 1, 0L, Map.of())); // GH-90000
        }

        @Override
        public CompletableFuture<List<AepKernelAdapter.AgentDeployment>> listAgents() { // GH-90000
            return CompletableFuture.completedFuture(List.of()); // GH-90000
        }

        @Override
        public CompletableFuture<AepKernelAdapterImpl.InnerCommandResult> sendCommand( // GH-90000
                String agentId, String commandId, String commandType, Map<String, Object> parameters) {
            return CompletableFuture.completedFuture( // GH-90000
                new AepKernelAdapterImpl.InnerCommandResult(true, "ok", Map.of())); // GH-90000
        }

        @Override
        public CompletableFuture<Object> createPipeline(String pipelineId, String pipelineType, // GH-90000
                                                         List<AepKernelAdapter.PipelineStage> stages,
                                                         Map<String, String> config) {
            return CompletableFuture.completedFuture("stub-pipeline [GH-90000]");
        }

        @Override
        public CompletableFuture<Void> startPipeline(Object pipeline) { // GH-90000
            return CompletableFuture.completedFuture(null); // GH-90000
        }

        @Override
        public CompletableFuture<Void> stopPipeline(Object pipeline) { // GH-90000
            return CompletableFuture.completedFuture(null); // GH-90000
        }

        @Override
        public CompletableFuture<AepKernelAdapterImpl.InnerPipelineStatus> getPipelineStatus(Object pipeline) { // GH-90000
            return CompletableFuture.completedFuture( // GH-90000
                new AepKernelAdapterImpl.InnerPipelineStatus("RUNNING", 0L, 0L, Map.of())); // GH-90000
        }
    }
}
