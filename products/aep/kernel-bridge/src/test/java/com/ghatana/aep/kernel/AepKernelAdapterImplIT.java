package com.ghatana.aep.kernel;

import com.ghatana.kernel.adapter.aep.AepKernelAdapter;
import com.ghatana.kernel.adapter.aep.AepKernelAdapter.AepEvent;
import com.ghatana.kernel.adapter.aep.AepKernelAdapter.AgentCommand;
import com.ghatana.kernel.adapter.aep.AepKernelAdapter.AgentDeployRequest;
import com.ghatana.kernel.adapter.aep.AepKernelAdapter.AgentDeployment;
import com.ghatana.kernel.adapter.aep.AepKernelAdapter.AgentStatus;
import com.ghatana.kernel.adapter.aep.AepKernelAdapter.CommandResult;
import com.ghatana.kernel.adapter.aep.AepKernelAdapter.PipelineCreateRequest;
import com.ghatana.kernel.adapter.aep.AepKernelAdapter.PipelineHandle;
import com.ghatana.kernel.adapter.aep.AepKernelAdapter.PipelineStatus;
import com.ghatana.kernel.adapter.aep.AepKernelAdapter.StreamCreateRequest;
import com.ghatana.kernel.adapter.aep.AepKernelAdapter.SubscriptionHandle;
import com.ghatana.kernel.adapter.aep.AepKernelAdapterImpl;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration scenarios for {@link AepKernelAdapterImpl}.
 *
 * <p>Tests the full adapter lifecycle: event publish/subscribe, stream lifecycle,
 * agent deploy/status/undeploy, command dispatch, and pipeline orchestration.
 * Uses a concrete recording {@link AepKernelAdapterImpl.AepClient} — no Mockito.</p>
 *
 * @doc.type class
 * @doc.purpose Verify AepKernelAdapterImpl integration scenarios end-to-end
 * @doc.layer adapter
 * @doc.pattern Test
 */
@DisplayName("AepKernelAdapterImpl — integration scenarios")
class AepKernelAdapterImplIT extends EventloopTestBase {

    private RecordingAepClient recordingClient;
    private AepKernelAdapterImpl adapter;

    @BeforeEach
    void setUp() {
        recordingClient = new RecordingAepClient();
        adapter = new AepKernelAdapterImpl(recordingClient);
    }

    // ==================== Event Stream ====================

    @Nested
    @DisplayName("Event publishing")
    class EventPublishing {

        @Test
        @DisplayName("publishes event and client receives all fields")
        void publishEventForwardsAllFields() {
            byte[] payload = "hello".getBytes();
            AepEvent event = new AepEvent("evt-1", "ORDER_PLACED", payload, Map.of("tenant", "t1"), 1_000L);

            runPromise(() -> adapter.publishEvent("stream-orders", event));

            assertThat(recordingClient.lastPublishedStreamId).isEqualTo("stream-orders");
            assertThat(recordingClient.lastPublishedEventId).isEqualTo("evt-1");
            assertThat(recordingClient.lastPublishedEventType).isEqualTo("ORDER_PLACED");
            assertThat(recordingClient.lastPublishedPayload).isEqualTo(payload);
            assertThat(recordingClient.lastPublishedHeaders).containsEntry("tenant", "t1");
            assertThat(recordingClient.lastPublishedTimestamp).isEqualTo(1_000L);
        }

        @Test
        @DisplayName("null streamId is rejected before client call")
        void nullStreamIdRejectedBeforeClientCall() {
            AepEvent event = new AepEvent("e", "T", new byte[0], Map.of(), 0L);
            assertThatThrownBy(() -> runPromise(() -> adapter.publishEvent(null, event)))
                .isInstanceOf(NullPointerException.class);
            assertThat(recordingClient.lastPublishedStreamId).isNull();
        }

        @Test
        @DisplayName("null event is rejected before client call")
        void nullEventRejectedBeforeClientCall() {
            assertThatThrownBy(() -> runPromise(() -> adapter.publishEvent("s", null)))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("client failure propagates as failed Promise")
        void clientFailurePropagatesAsFailedPromise() {
            recordingClient.failNext = true;
            AepEvent event = new AepEvent("e", "T", new byte[0], Map.of(), 0L);
            assertThatThrownBy(() -> runPromise(() -> adapter.publishEvent("stream", event)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("simulated client failure");
        }
    }

    // ==================== Subscription ====================

    @Nested
    @DisplayName("Event subscription")
    class EventSubscription {

        @Test
        @DisplayName("subscribe returns a non-null handle with unique ID")
        void subscribeReturnsHandleWithUniqueId() {
            SubscriptionHandle h1 = runPromise(() -> adapter.subscribe("stream-a", e -> Promise.complete()));
            SubscriptionHandle h2 = runPromise(() -> adapter.subscribe("stream-b", e -> Promise.complete()));

            assertThat(h1.getId()).isNotBlank();
            assertThat(h2.getId()).isNotBlank();
            assertThat(h1.getId()).isNotEqualTo(h2.getId());
        }

        @Test
        @DisplayName("unsubscribe completes without error")
        void unsubscribeCompletesCleanly() {
            SubscriptionHandle handle = runPromise(() -> adapter.subscribe("stream-x", e -> Promise.complete()));
            runPromise(handle::unsubscribe);
            // no exception → pass
        }

        @Test
        @DisplayName("null handler is rejected before client call")
        void nullHandlerRejectedBeforeClientCall() {
            assertThatThrownBy(() -> runPromise(() -> adapter.subscribe("s", null)))
                .isInstanceOf(NullPointerException.class);
        }
    }

    // ==================== Stream Lifecycle ====================

    @Nested
    @DisplayName("Stream lifecycle")
    class StreamLifecycle {

        @Test
        @DisplayName("createStream forwards all request fields to client")
        void createStreamForwardsAllFields() {
            StreamCreateRequest req = new StreamCreateRequest(
                "events-v2", "PERSISTENT", Map.of("compression", "zstd"), 4, Duration.ofDays(30));

            runPromise(() -> adapter.createStream(req));

            assertThat(recordingClient.lastCreatedStreamId).isEqualTo("events-v2");
            assertThat(recordingClient.lastCreatedPartitions).isEqualTo(4);
            assertThat(recordingClient.lastCreatedRetention).isEqualTo(Duration.ofDays(30));
        }

        @Test
        @DisplayName("deleteStream calls client with correct stream ID")
        void deleteStreamCallsClientWithCorrectId() {
            runPromise(() -> adapter.deleteStream("events-old"));

            assertThat(recordingClient.lastDeletedStreamId).isEqualTo("events-old");
        }

        @Test
        @DisplayName("null streamId on deleteStream is rejected before client call")
        void nullStreamIdDeleteRejected() {
            assertThatThrownBy(() -> runPromise(() -> adapter.deleteStream(null)))
                .isInstanceOf(NullPointerException.class);
            assertThat(recordingClient.lastDeletedStreamId).isNull();
        }
    }

    // ==================== Agent Lifecycle ====================

    @Nested
    @DisplayName("Agent lifecycle")
    class AgentLifecycle {

        @Test
        @DisplayName("deployAgent records deployment and returns DEPLOYED status")
        void deployAgentReturnsDeployedStatus() {
            AgentDeployRequest req = new AgentDeployRequest("agent-42", "ML_CLASSIFIER", "2.1.0",
                Map.of("model", "gpt-turbo"), 2);

            AgentDeployment deployment = runPromise(() -> adapter.deployAgent(req));

            assertThat(deployment.getAgentId()).isEqualTo("agent-42");
            assertThat(deployment.getStatus()).isEqualTo("DEPLOYED");
            assertThat(deployment.getEndpoint()).isEqualTo("https://aep.internal/agent-42");
        }

        @Test
        @DisplayName("listAgents includes all locally deployed agents")
        void listAgentsIncludesDeployedAgents() {
            AgentDeployRequest req1 = new AgentDeployRequest("a1", "TYPE_A", "1.0", Map.of(), 1);
            AgentDeployRequest req2 = new AgentDeployRequest("a2", "TYPE_B", "1.0", Map.of(), 1);

            runPromise(() -> adapter.deployAgent(req1));
            runPromise(() -> adapter.deployAgent(req2));

            List<AgentDeployment> agents = runPromise(adapter::listAgents);
            assertThat(agents).extracting(AgentDeployment::getAgentId)
                .containsExactlyInAnyOrder("a1", "a2");
        }

        @Test
        @DisplayName("getAgentStatus maps client state correctly")
        void getAgentStatusMapsClientState() {
            AgentStatus status = runPromise(() -> adapter.getAgentStatus("agent-99"));

            assertThat(status.getAgentId()).isEqualTo("agent-99");
            assertThat(status.getState()).isEqualTo("RUNNING");
            assertThat(status.getActiveInstances()).isEqualTo(1);
        }

        @Test
        @DisplayName("undeployAgent removes agent from local registry")
        void undeployAgentRemovesFromRegistry() {
            AgentDeployRequest req = new AgentDeployRequest("a-tmp", "TYPE_X", "1.0", Map.of(), 1);
            runPromise(() -> adapter.deployAgent(req));
            runPromise(() -> adapter.undeployAgent("a-tmp"));

            List<AgentDeployment> agents = runPromise(adapter::listAgents);
            assertThat(agents).extracting(AgentDeployment::getAgentId).doesNotContain("a-tmp");
        }

        @Test
        @DisplayName("sendCommand propagates success result from client")
        void sendCommandPropagatesSuccessResult() {
            AgentCommand command = new AgentCommand("cmd-1", "SCALE_UP", Map.of("instances", 3));

            CommandResult result = runPromise(() -> adapter.sendCommand("agent-42", command));

            assertThat(result.getCommandId()).isEqualTo("cmd-1");
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).isEqualTo("ok");
        }
    }

    // ==================== Pipeline Orchestration ====================

    @Nested
    @DisplayName("Pipeline orchestration")
    class PipelineOrchestration {

        @Test
        @DisplayName("createPipeline returns a handle with a non-blank ID")
        void createPipelineReturnsHandleWithId() {
            PipelineCreateRequest req = new PipelineCreateRequest(
                "pipe-1", "STREAM", List.of(), Map.of());

            PipelineHandle handle = runPromise(() -> adapter.createPipeline(req));

            assertThat(handle.getId()).isNotBlank();
        }

        @Test
        @DisplayName("startPipeline completes for a known pipeline ID")
        void startPipelineCompletesForKnownPipeline() {
            PipelineCreateRequest req = new PipelineCreateRequest(
                "pipe-2", "BATCH", List.of(), Map.of());
            PipelineHandle handle = runPromise(() -> adapter.createPipeline(req));

            runPromise(() -> adapter.startPipeline(handle.getId()));
            // no exception → pass
        }

        @Test
        @DisplayName("stopPipeline completes for a known pipeline ID")
        void stopPipelineCompletesForKnownPipeline() {
            PipelineCreateRequest req = new PipelineCreateRequest(
                "pipe-3", "BATCH", List.of(), Map.of());
            PipelineHandle handle = runPromise(() -> adapter.createPipeline(req));

            runPromise(() -> adapter.startPipeline(handle.getId()));
            runPromise(() -> adapter.stopPipeline(handle.getId()));
            // no exception → pass
        }

        @Test
        @DisplayName("startPipeline with unknown ID fails with IllegalStateException")
        void startPipelineUnknownIdFails() {
            assertThatThrownBy(() -> runPromise(() -> adapter.startPipeline("does-not-exist")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does-not-exist");
        }

        @Test
        @DisplayName("stopPipeline with unknown ID fails with IllegalStateException")
        void stopPipelineUnknownIdFails() {
            assertThatThrownBy(() -> runPromise(() -> adapter.stopPipeline("ghost-pipeline")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ghost-pipeline");
        }

        @Test
        @DisplayName("getPipelineStatus maps client state correctly")
        void getPipelineStatusMapsClientState() {
            PipelineCreateRequest req = new PipelineCreateRequest(
                "pipe-status", "STREAM", List.of(), Map.of());
            PipelineHandle handle = runPromise(() -> adapter.createPipeline(req));

            PipelineStatus status = runPromise(() -> adapter.getPipelineStatus(handle.getId()));

            assertThat(status.getState()).isEqualTo("RUNNING");
        }

        @Test
        @DisplayName("getPipelineStatus with unknown ID fails with IllegalStateException")
        void getPipelineStatusUnknownIdFails() {
            assertThatThrownBy(() -> runPromise(() -> adapter.getPipelineStatus("ghost")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ghost");
        }
    }

    // ==================== Recording stub client ====================

    /**
     * Concrete recording stub — captures last invocation per operation.
     * Never uses Mockito; models only what the adapter under test exercises.
     */
    private static final class RecordingAepClient implements AepKernelAdapterImpl.AepClient {

        // publish tracking
        String lastPublishedStreamId;
        String lastPublishedEventId;
        String lastPublishedEventType;
        byte[] lastPublishedPayload;
        Map<String, String> lastPublishedHeaders;
        long lastPublishedTimestamp;

        // stream tracking
        String lastCreatedStreamId;
        int lastCreatedPartitions;
        Duration lastCreatedRetention;
        String lastDeletedStreamId;

        // failure injection
        boolean failNext;

        @Override
        public CompletableFuture<Void> publishEvent(String streamId, String eventId, String eventType,
                                                     byte[] payload, Map<String, String> headers,
                                                     long timestamp) {
            if (failNext) {
                failNext = false;
                return CompletableFuture.failedFuture(
                    new RuntimeException("simulated client failure"));
            }
            lastPublishedStreamId = streamId;
            lastPublishedEventId = eventId;
            lastPublishedEventType = eventType;
            lastPublishedPayload = payload;
            lastPublishedHeaders = headers;
            lastPublishedTimestamp = timestamp;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<AepKernelAdapterImpl.InnerSubscription> subscribe(
                String streamId, AepKernelAdapterImpl.InnerEventHandler handler) {
            return CompletableFuture.completedFuture(new AepKernelAdapterImpl.InnerSubscription() {
                @Override
                public String getId() { return "inner-sub-" + streamId; }

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
            lastCreatedStreamId = streamId;
            lastCreatedPartitions = partitionCount;
            lastCreatedRetention = retention;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> deleteStream(String streamId) {
            lastDeletedStreamId = streamId;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<AepKernelAdapterImpl.DeployResult> deployAgent(
                String agentId, String agentType, String version,
                Map<String, Object> config, int instanceCount) {
            return CompletableFuture.completedFuture(
                new AepKernelAdapterImpl.DeployResult("https://aep.internal/" + agentId));
        }

        @Override
        public CompletableFuture<Void> undeployAgent(String agentId) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<AepKernelAdapterImpl.InnerAgentStatus> getAgentStatus(String agentId) {
            return CompletableFuture.completedFuture(
                new AepKernelAdapterImpl.InnerAgentStatus("RUNNING", 1, System.currentTimeMillis(), Map.of()));
        }

        @Override
        public CompletableFuture<List<AepKernelAdapter.AgentDeployment>> listAgents() {
            return CompletableFuture.completedFuture(Collections.emptyList());
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
            return CompletableFuture.completedFuture("inner-pipeline-" + pipelineId);
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
