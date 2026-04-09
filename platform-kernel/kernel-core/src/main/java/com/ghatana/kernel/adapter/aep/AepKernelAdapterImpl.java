package com.ghatana.kernel.adapter.aep;

import com.ghatana.platform.core.client.AsyncClient;
import io.activej.promise.Promise;
import io.activej.promise.Promises;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Concrete implementation of AepKernelAdapter.
 *
 * <p>Provides real AEP (Agent Execution Platform) integration with:
 * <ul>
 *   <li>Promise-based async operations wrapping CompletableFuture</li>
 *   <li>Event stream publishing and subscription</li>
 *   <li>Agent lifecycle management (deploy, undeploy, status)</li>
 *   <li>Pipeline orchestration for data processing</li>
 *   <li>Command and control interface for agents</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Concrete AEP adapter for event streams and agent management
 * @doc.layer adapter
 * @doc.pattern Adapter, Facade
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public class AepKernelAdapterImpl implements AepKernelAdapter {

    private final AepClient aepClient;
    private final Map<String, SubscriptionImpl> activeSubscriptions = new ConcurrentHashMap<>();
    private final Map<String, PipelineImpl> activePipelines = new ConcurrentHashMap<>();
    private final Map<String, AgentDeployment> deployedAgents = new ConcurrentHashMap<>();
    private final AtomicLong subscriptionCounter = new AtomicLong(0);
    private final AtomicLong pipelineCounter = new AtomicLong(0);

    /**
     * Creates a new AEP adapter with the given client.
     *
     * @param aepClient the AEP client for event/agent operations
     */
    public AepKernelAdapterImpl(AepClient aepClient) {
        this.aepClient = Objects.requireNonNull(aepClient, "aepClient cannot be null");
    }

    @Override
    public Promise<Void> publishEvent(String streamId, AepEvent event) {
        Objects.requireNonNull(streamId, "streamId cannot be null");
        Objects.requireNonNull(event, "event cannot be null");

        CompletableFuture<Void> future = aepClient.publishEvent(
            streamId,
            event.getEventId(),
            event.getEventType(),
            event.getPayload(),
            event.getHeaders(),
            event.getTimestamp()
        );

        return wrapFuture(future);
    }

    @Override
    public Promise<SubscriptionHandle> subscribe(String streamId, EventHandler handler) {
        Objects.requireNonNull(streamId, "streamId cannot be null");
        Objects.requireNonNull(handler, "handler cannot be null");

        String subscriptionId = "sub-" + subscriptionCounter.incrementAndGet();

        CompletableFuture<SubscriptionHandle> future = aepClient.subscribe(streamId, (eventId, eventType, payload, headers, timestamp) -> {
            AepEvent event = new AepEvent(eventId, eventType, payload, headers, timestamp);
            return handler.handle(event);
        }).thenApply(innerSub -> {
            SubscriptionImpl handle = new SubscriptionImpl(subscriptionId, innerSub, streamId, handler);
            activeSubscriptions.put(subscriptionId, handle);
            return handle;
        });

        return wrapFuture(future);
    }

    @Override
    public Promise<Void> createStream(StreamCreateRequest request) {
        Objects.requireNonNull(request, "request cannot be null");

        CompletableFuture<Void> future = aepClient.createStream(
            request.getStreamId(),
            request.getStreamType(),
            request.getConfig(),
            request.getPartitionCount(),
            request.getRetention()
        );

        return wrapFuture(future);
    }

    @Override
    public Promise<Void> deleteStream(String streamId) {
        Objects.requireNonNull(streamId, "streamId cannot be null");

        CompletableFuture<Void> future = aepClient.deleteStream(streamId);
        return wrapFuture(future);
    }

    @Override
    public Promise<AgentDeployment> deployAgent(AgentDeployRequest request) {
        Objects.requireNonNull(request, "request cannot be null");

        CompletableFuture<AgentDeployment> future = aepClient.deployAgent(
            request.getAgentId(),
            request.getAgentType(),
            request.getVersion(),
            request.getConfig(),
            request.getInstanceCount()
        ).thenApply(result -> {
            AgentDeployment deployment = new AgentDeployment(
                request.getAgentId(),
                "DEPLOYED",
                System.currentTimeMillis(),
                result.getEndpoint()
            );
            deployedAgents.put(request.getAgentId(), deployment);
            return deployment;
        });

        return wrapFuture(future);
    }

    @Override
    public Promise<Void> undeployAgent(String agentId) {
        Objects.requireNonNull(agentId, "agentId cannot be null");

        CompletableFuture<Void> future = aepClient.undeployAgent(agentId)
            .thenRun(() -> deployedAgents.remove(agentId));

        return wrapFuture(future);
    }

    @Override
    public Promise<AgentStatus> getAgentStatus(String agentId) {
        Objects.requireNonNull(agentId, "agentId cannot be null");

        CompletableFuture<AgentStatus> future = aepClient.getAgentStatus(agentId)
            .thenApply(status -> new AgentStatus(
                agentId,
                status.getState(),
                status.getActiveInstances(),
                status.getLastHeartbeat(),
                status.getMetrics()
            ));

        return wrapFuture(future);
    }

    @Override
    public Promise<List<AgentDeployment>> listAgents() {
        CompletableFuture<List<AgentDeployment>> future = aepClient.listAgents()
            .thenApply(list -> new ArrayList<>(deployedAgents.values()));

        return wrapFuture(future);
    }

    @Override
    public Promise<CommandResult> sendCommand(String agentId, AgentCommand command) {
        Objects.requireNonNull(agentId, "agentId cannot be null");
        Objects.requireNonNull(command, "command cannot be null");

        CompletableFuture<CommandResult> future = aepClient.sendCommand(
            agentId,
            command.getCommandId(),
            command.getCommandType(),
            command.getParameters()
        ).thenApply(result -> new CommandResult(
            command.getCommandId(),
            result.isSuccess(),
            result.getMessage(),
            result.getData()
        ));

        return wrapFuture(future);
    }

    @Override
    public Promise<PipelineHandle> createPipeline(PipelineCreateRequest request) {
        Objects.requireNonNull(request, "request cannot be null");

        String pipelineId = "pipeline-" + pipelineCounter.incrementAndGet();

        CompletableFuture<PipelineHandle> future = aepClient.createPipeline(
            request.getPipelineId(),
            request.getPipelineType(),
            request.getStages(),
            request.getConfig()
        ).thenApply(innerPipeline -> {
            PipelineImpl handle = new PipelineImpl(pipelineId, innerPipeline);
            activePipelines.put(pipelineId, handle);
            return handle;
        });

        return wrapFuture(future);
    }

    @Override
    public Promise<Void> startPipeline(String pipelineId) {
        Objects.requireNonNull(pipelineId, "pipelineId cannot be null");

        PipelineImpl pipeline = activePipelines.get(pipelineId);
        if (pipeline == null) {
            return Promise.ofException(new IllegalStateException("Pipeline not found: " + pipelineId));
        }

        CompletableFuture<Void> future = aepClient.startPipeline(pipeline.getInnerPipeline());
        return wrapFuture(future);
    }

    @Override
    public Promise<Void> stopPipeline(String pipelineId) {
        Objects.requireNonNull(pipelineId, "pipelineId cannot be null");

        PipelineImpl pipeline = activePipelines.get(pipelineId);
        if (pipeline == null) {
            return Promise.ofException(new IllegalStateException("Pipeline not found: " + pipelineId));
        }

        CompletableFuture<Void> future = aepClient.stopPipeline(pipeline.getInnerPipeline());
        return wrapFuture(future);
    }

    @Override
    public Promise<PipelineStatus> getPipelineStatus(String pipelineId) {
        Objects.requireNonNull(pipelineId, "pipelineId cannot be null");

        PipelineImpl pipeline = activePipelines.get(pipelineId);
        if (pipeline == null) {
            return Promise.ofException(new IllegalStateException("Pipeline not found: " + pipelineId));
        }

        CompletableFuture<PipelineStatus> future = aepClient.getPipelineStatus(pipeline.getInnerPipeline())
            .thenApply(status -> new PipelineStatus(
                pipelineId,
                status.getState(),
                status.getEventsProcessed(),
                status.getErrors(),
                status.getMetrics()
            ));

        return wrapFuture(future);
    }

    @Override
    public <T> Promise<T> wrapFuture(CompletableFuture<T> future) {
        return Promise.ofFuture(future);
    }

    // ==================== Additional Utility Methods ====================

    /**
     * Gets all active subscriptions.
     *
     * @return list of active subscription handles
     */
    public List<SubscriptionHandle> getActiveSubscriptions() {
        return new ArrayList<>(activeSubscriptions.values());
    }

    /**
     * Unsubscribes from all active subscriptions.
     *
     * @return Promise completing when all are unsubscribed
     */
    public Promise<Void> unsubscribeAll() {
        List<Promise<Void>> promises = new ArrayList<>();
        for (SubscriptionImpl sub : activeSubscriptions.values()) {
            promises.add(sub.unsubscribe());
        }
        return Promises.all(promises);
    }

    /**
     * Destroys all active pipelines.
     *
     * @return Promise completing when all are destroyed
     */
    public Promise<Void> destroyAllPipelines() {
        List<Promise<Void>> promises = new ArrayList<>();
        for (PipelineImpl pipeline : activePipelines.values()) {
            promises.add(pipeline.destroy());
        }
        return Promises.all(promises).whenResult($ -> activePipelines.clear());
    }

    // ==================== Inner Types ====================

    /**
     * AEP client interface (to be implemented by AEP platform).
     */
    public interface AepClient extends AsyncClient {
        @Override
        default Promise<Void> start() {
            return Promise.complete();
        }

        @Override
        default Promise<Void> stop() {
            return Promise.complete();
        }

        @Override
        default Promise<Boolean> healthCheck() {
            return Promise.of(true);
        }

        @Override
        default boolean isRunning() {
            return true;
        }

        CompletableFuture<Void> publishEvent(String streamId, String eventId, String eventType,
                                              byte[] payload, Map<String, String> headers, long timestamp);
        CompletableFuture<InnerSubscription> subscribe(String streamId, InnerEventHandler handler);
        CompletableFuture<Void> createStream(String streamId, String streamType, Map<String, String> config,
                                              int partitionCount, Duration retention);
        CompletableFuture<Void> deleteStream(String streamId);
        CompletableFuture<DeployResult> deployAgent(String agentId, String agentType, String version,
                                                     Map<String, Object> config, int instanceCount);
        CompletableFuture<Void> undeployAgent(String agentId);
        CompletableFuture<InnerAgentStatus> getAgentStatus(String agentId);
        CompletableFuture<List<AgentDeployment>> listAgents();
        CompletableFuture<InnerCommandResult> sendCommand(String agentId, String commandId,
                                                           String commandType, Map<String, Object> parameters);
        CompletableFuture<Object> createPipeline(String pipelineId, String pipelineType,
                                                  List<PipelineStage> stages, Map<String, String> config);
        CompletableFuture<Void> startPipeline(Object pipeline);
        CompletableFuture<Void> stopPipeline(Object pipeline);
        CompletableFuture<InnerPipelineStatus> getPipelineStatus(Object pipeline);
    }

    public interface InnerSubscription {
        String getId();
        CompletableFuture<Void> unsubscribe();
    }

    public interface InnerEventHandler {
        Promise<Void> handle(String eventId, String eventType, byte[] payload,
                              Map<String, String> headers, long timestamp);
    }

    public static class DeployResult {
        private final String endpoint;

        public DeployResult(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getEndpoint() { return endpoint; }
    }

    public static class InnerAgentStatus {
        private final String state;
        private final int activeInstances;
        private final long lastHeartbeat;
        private final Map<String, Object> metrics;

        public InnerAgentStatus(String state, int activeInstances, long lastHeartbeat,
                                 Map<String, Object> metrics) {
            this.state = state;
            this.activeInstances = activeInstances;
            this.lastHeartbeat = lastHeartbeat;
            this.metrics = metrics;
        }

        public String getState() { return state; }
        public int getActiveInstances() { return activeInstances; }
        public long getLastHeartbeat() { return lastHeartbeat; }
        public Map<String, Object> getMetrics() { return metrics; }
    }

    public static class InnerCommandResult {
        private final boolean success;
        private final String message;
        private final Map<String, Object> data;

        public InnerCommandResult(boolean success, String message, Map<String, Object> data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Map<String, Object> getData() { return data; }
    }

    public static class InnerPipelineStatus {
        private final String state;
        private final long eventsProcessed;
        private final long errors;
        private final Map<String, Object> metrics;

        public InnerPipelineStatus(String state, long eventsProcessed, long errors,
                                    Map<String, Object> metrics) {
            this.state = state;
            this.eventsProcessed = eventsProcessed;
            this.errors = errors;
            this.metrics = metrics;
        }

        public String getState() { return state; }
        public long getEventsProcessed() { return eventsProcessed; }
        public long getErrors() { return errors; }
        public Map<String, Object> getMetrics() { return metrics; }
    }

    private class SubscriptionImpl implements SubscriptionHandle {
        private final String id;
        private final InnerSubscription innerSubscription;
        private final String streamId;
        private final EventHandler handler;

        SubscriptionImpl(String id, InnerSubscription innerSubscription, String streamId, EventHandler handler) {
            this.id = id;
            this.innerSubscription = innerSubscription;
            this.streamId = streamId;
            this.handler = handler;
        }

        @Override
        public String getId() { return id; }

        @Override
        public Promise<Void> unsubscribe() {
            activeSubscriptions.remove(id);
            CompletableFuture<Void> future = innerSubscription.unsubscribe();
            return wrapFuture(future);
        }
    }

    private class PipelineImpl implements PipelineHandle {
        private final String id;
        private final Object innerPipeline;

        PipelineImpl(String id, Object innerPipeline) {
            this.id = id;
            this.innerPipeline = innerPipeline;
        }

        @Override
        public String getId() { return id; }

        @Override
        public Promise<Void> destroy() {
            activePipelines.remove(id);
            CompletableFuture<Void> future = aepClient.stopPipeline(innerPipeline);
            return wrapFuture(future);
        }

        Object getInnerPipeline() { return innerPipeline; }
    }
}
