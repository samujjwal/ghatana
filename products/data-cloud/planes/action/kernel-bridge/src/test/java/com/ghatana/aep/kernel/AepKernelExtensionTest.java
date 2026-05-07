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
@ExtendWith(MockitoExtension.class) 
@DisplayName("AepKernelExtension")
class AepKernelExtensionTest extends EventloopTestBase {

    @Mock
    private KernelContext context;

    @Mock
    private KernelModule hostModule;

    private AepKernelAdapterImpl.AepClient stubClient;
    private AepKernelExtension extension;

    @BeforeEach
    void setUp() { 
        stubClient = new StubAepClient(); 
        extension = new AepKernelExtension(stubClient); 
    }

    // ==================== Identity ====================

    @Test
    @DisplayName("extension ID is 'aep-kernel-bridge'")
    void extensionIdIsCorrect() { 
        assertThat(extension.getExtensionId()).isEqualTo("aep-kernel-bridge");
    }

    @Test
    @DisplayName("extension name is human-readable")
    void extensionNameIsHumanReadable() { 
        assertThat(extension.getName()).isEqualTo("AEP Kernel Bridge");
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
    @DisplayName("contributes three AEP capabilities")
    void contributesThreeCapabilities() { 
        Set<KernelCapability> caps = extension.getContributedCapabilities(); 
        assertThat(caps).hasSize(3); 
    }

    @Test
    @DisplayName("contributes aep.event-streaming capability")
    void contributesEventStreamingCapability() { 
        Set<KernelCapability> caps = extension.getContributedCapabilities(); 
        assertThat(caps).anyMatch(c -> c.getCapabilityId().equals("aep.event-streaming"));
    }

    @Test
    @DisplayName("contributes aep.agent-runtime capability")
    void contributesAgentRuntimeCapability() { 
        Set<KernelCapability> caps = extension.getContributedCapabilities(); 
        assertThat(caps).anyMatch(c -> c.getCapabilityId().equals("aep.agent-runtime"));
    }

    @Test
    @DisplayName("contributes aep.pipeline-orchestration capability")
    void contributesPipelineCapability() { 
        Set<KernelCapability> caps = extension.getContributedCapabilities(); 
        assertThat(caps).anyMatch(c -> c.getCapabilityId().equals("aep.pipeline-orchestration"));
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
    @DisplayName("onModuleInitialized registers AepKernelAdapter into context")
    void onModuleInitializedRegistersAdapter() { 
        extension.onModuleInitialized(context); 

        verify(context).registerService(eq(AepKernelAdapter.class), any(AepKernelAdapter.class)); 
    }

    @Test
    @DisplayName("onModuleInitialized is idempotent — second call is no-op")
    void onModuleInitializedIsIdempotent() { 
        extension.onModuleInitialized(context); 
        extension.onModuleInitialized(context); 

        verify(context).registerService(eq(AepKernelAdapter.class), any(AepKernelAdapter.class)); 
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
    @DisplayName("null client is rejected at construction")
    void nullClientIsRejected() { 
        assertThatThrownBy(() -> new AepKernelExtension(null)) 
            .isInstanceOf(NullPointerException.class); 
    }

    // ==================== Helpers ====================

    private static class StubAepClient implements AepKernelAdapterImpl.AepClient {

        @Override
        public CompletableFuture<Void> publishEvent(String streamId, String eventId, String eventType, 
                                                     byte[] payload, Map<String, String> headers, long timestamp) {
            return CompletableFuture.completedFuture(null); 
        }

        @Override
        public CompletableFuture<AepKernelAdapterImpl.InnerSubscription> subscribe( 
                String streamId, AepKernelAdapterImpl.InnerEventHandler handler) {
            return CompletableFuture.completedFuture(new AepKernelAdapterImpl.InnerSubscription() { 
                @Override
                public String getId() { return "stub-sub"; } 

                @Override
                public CompletableFuture<Void> unsubscribe() { 
                    return CompletableFuture.completedFuture(null); 
                }
            });
        }

        @Override
        public CompletableFuture<Void> createStream(String streamId, String streamType, 
                                                     Map<String, String> config, int partitionCount,
                                                     Duration retention) {
            return CompletableFuture.completedFuture(null); 
        }

        @Override
        public CompletableFuture<Void> deleteStream(String streamId) { 
            return CompletableFuture.completedFuture(null); 
        }

        @Override
        public CompletableFuture<AepKernelAdapterImpl.DeployResult> deployAgent( 
                String agentId, String agentType, String version,
                Map<String, Object> config, int instanceCount) {
            return CompletableFuture.completedFuture(new AepKernelAdapterImpl.DeployResult("stub-endpoint"));
        }

        @Override
        public CompletableFuture<Void> undeployAgent(String agentId) { 
            return CompletableFuture.completedFuture(null); 
        }

        @Override
        public CompletableFuture<AepKernelAdapterImpl.InnerAgentStatus> getAgentStatus(String agentId) { 
            return CompletableFuture.completedFuture( 
                new AepKernelAdapterImpl.InnerAgentStatus("RUNNING", 1, 0L, Map.of())); 
        }

        @Override
        public CompletableFuture<List<AepKernelAdapter.AgentDeployment>> listAgents() { 
            return CompletableFuture.completedFuture(List.of()); 
        }

        @Override
        public CompletableFuture<AepKernelAdapterImpl.InnerCommandResult> sendCommand( 
                String agentId, String commandId, String commandType, Map<String, Object> parameters) {
            return CompletableFuture.completedFuture( 
                new AepKernelAdapterImpl.InnerCommandResult(true, "ok", Map.of())); 
        }

        @Override
        public CompletableFuture<Object> createPipeline(String pipelineId, String pipelineType, 
                                                         List<AepKernelAdapter.PipelineStage> stages,
                                                         Map<String, String> config) {
            return CompletableFuture.completedFuture("stub-pipeline");
        }

        @Override
        public CompletableFuture<Void> startPipeline(Object pipeline) { 
            return CompletableFuture.completedFuture(null); 
        }

        @Override
        public CompletableFuture<Void> stopPipeline(Object pipeline) { 
            return CompletableFuture.completedFuture(null); 
        }

        @Override
        public CompletableFuture<AepKernelAdapterImpl.InnerPipelineStatus> getPipelineStatus(Object pipeline) { 
            return CompletableFuture.completedFuture( 
                new AepKernelAdapterImpl.InnerPipelineStatus("RUNNING", 0L, 0L, Map.of())); 
        }
    }
}
