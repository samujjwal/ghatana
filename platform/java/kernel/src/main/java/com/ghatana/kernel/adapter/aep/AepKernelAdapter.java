package com.ghatana.kernel.adapter.aep;

import io.activej.promise.Promise;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Adapter interface for AEP (Agent Execution Platform) integration.
 *
 * <p>Wraps AEP CompletableFuture-based SPI with ActiveJ Promise.
 * Provides event stream processing, agent management, and pipeline operations.</p>
 *
 * @doc.type interface
 * @doc.purpose Bridge kernel SPI to AEP platform with Promise wrapping
 * @doc.layer adapter
 * @doc.pattern Adapter
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public interface AepKernelAdapter {

    // ==================== Event Stream Operations ====================

    /**
     * Publishes an event to AEP event stream.
     *
     * @param streamId the stream identifier
     * @param event the event to publish
     * @return Promise completing when event is published
     */
    Promise<Void> publishEvent(String streamId, AepEvent event);

    /**
     * Subscribes to an event stream.
     *
     * @param streamId the stream identifier
     * @param handler the event handler
     * @return Promise containing subscription handle
     */
    Promise<SubscriptionHandle> subscribe(String streamId, EventHandler handler);

    /**
     * Creates a new event stream.
     *
     * @param request the stream creation request
     * @return Promise completing when stream is created
     */
    Promise<Void> createStream(StreamCreateRequest request);

    /**
     * Deletes an event stream.
     *
     * @param streamId the stream identifier
     * @return Promise completing when stream is deleted
     */
    Promise<Void> deleteStream(String streamId);

    // ==================== Agent Operations ====================

    /**
     * Deploys an agent to AEP.
     *
     * @param request the agent deployment request
     * @return Promise containing deployment result
     */
    Promise<AgentDeployment> deployAgent(AgentDeployRequest request);

    /**
     * Undeploys an agent from AEP.
     *
     * @param agentId the agent identifier
     * @return Promise completing when agent is undeployed
     */
    Promise<Void> undeployAgent(String agentId);

    /**
     * Gets agent status.
     *
     * @param agentId the agent identifier
     * @return Promise containing agent status
     */
    Promise<AgentStatus> getAgentStatus(String agentId);

    /**
     * Lists all deployed agents.
     *
     * @return Promise containing list of agent deployments
     */
    Promise<List<AgentDeployment>> listAgents();

    /**
     * Sends a command to an agent.
     *
     * @param agentId the agent identifier
     * @param command the command to send
     * @return Promise containing command result
     */
    Promise<CommandResult> sendCommand(String agentId, AgentCommand command);

    // ==================== Pipeline Operations ====================

    /**
     * Creates a data processing pipeline.
     *
     * @param request the pipeline creation request
     * @return Promise containing pipeline handle
     */
    Promise<PipelineHandle> createPipeline(PipelineCreateRequest request);

    /**
     * Starts a pipeline.
     *
     * @param pipelineId the pipeline identifier
     * @return Promise completing when pipeline starts
     */
    Promise<Void> startPipeline(String pipelineId);

    /**
     * Stops a pipeline.
     *
     * @param pipelineId the pipeline identifier
     * @return Promise completing when pipeline stops
     */
    Promise<Void> stopPipeline(String pipelineId);

    /**
     * Gets pipeline status.
     *
     * @param pipelineId the pipeline identifier
     * @return Promise containing pipeline status
     */
    Promise<PipelineStatus> getPipelineStatus(String pipelineId);

    // ==================== Utility Methods ====================

    /**
     * Wraps a CompletableFuture with ActiveJ Promise.
     *
     * @param future the CompletableFuture to wrap
     * @param <T> the result type
     * @return Promise wrapping the future
     */
    default <T> Promise<T> wrapFuture(CompletableFuture<T> future) {
        return Promise.ofFuture(future);
    }

    // ==================== Request/Result Types ====================

    class AepEvent {
        private final String eventId;
        private final String eventType;
        private final byte[] payload;
        private final Map<String, String> headers;
        private final long timestamp;

        public AepEvent(String eventId, String eventType, byte[] payload, Map<String, String> headers, long timestamp) {
            this.eventId = eventId;
            this.eventType = eventType;
            this.payload = payload;
            this.headers = headers != null ? headers : Map.of();
            this.timestamp = timestamp;
        }

        public String getEventId() { return eventId; }
        public String getEventType() { return eventType; }
        public byte[] getPayload() { return payload; }
        public Map<String, String> getHeaders() { return headers; }
        public long getTimestamp() { return timestamp; }
    }

    interface EventHandler {
        Promise<Void> handle(AepEvent event);
    }

    interface SubscriptionHandle {
        String getId();
        Promise<Void> unsubscribe();
    }

    class StreamCreateRequest {
        private final String streamId;
        private final String streamType;
        private final Map<String, String> config;
        private final int partitionCount;
        private final Duration retention;

        public StreamCreateRequest(String streamId, String streamType, Map<String, String> config,
                                    int partitionCount, Duration retention) {
            this.streamId = streamId;
            this.streamType = streamType;
            this.config = config != null ? config : Map.of();
            this.partitionCount = partitionCount;
            this.retention = retention != null ? retention : Duration.ofDays(7);
        }

        public String getStreamId() { return streamId; }
        public String getStreamType() { return streamType; }
        public Map<String, String> getConfig() { return config; }
        public int getPartitionCount() { return partitionCount; }
        public Duration getRetention() { return retention; }
    }

    class AgentDeployRequest {
        private final String agentId;
        private final String agentType;
        private final String version;
        private final Map<String, Object> config;
        private final int instanceCount;

        public AgentDeployRequest(String agentId, String agentType, String version,
                                  Map<String, Object> config, int instanceCount) {
            this.agentId = agentId;
            this.agentType = agentType;
            this.version = version;
            this.config = config != null ? config : Map.of();
            this.instanceCount = instanceCount;
        }

        public String getAgentId() { return agentId; }
        public String getAgentType() { return agentType; }
        public String getVersion() { return version; }
        public Map<String, Object> getConfig() { return config; }
        public int getInstanceCount() { return instanceCount; }
    }

    class AgentDeployment {
        private final String agentId;
        private final String status;
        private final long deployedAt;
        private final String endpoint;

        public AgentDeployment(String agentId, String status, long deployedAt, String endpoint) {
            this.agentId = agentId;
            this.status = status;
            this.deployedAt = deployedAt;
            this.endpoint = endpoint;
        }

        public String getAgentId() { return agentId; }
        public String getStatus() { return status; }
        public long getDeployedAt() { return deployedAt; }
        public String getEndpoint() { return endpoint; }
    }

    class AgentStatus {
        private final String agentId;
        private final String state;
        private final int activeInstances;
        private final long lastHeartbeat;
        private final Map<String, Object> metrics;

        public AgentStatus(String agentId, String state, int activeInstances, long lastHeartbeat, Map<String, Object> metrics) {
            this.agentId = agentId;
            this.state = state;
            this.activeInstances = activeInstances;
            this.lastHeartbeat = lastHeartbeat;
            this.metrics = metrics != null ? metrics : Map.of();
        }

        public String getAgentId() { return agentId; }
        public String getState() { return state; }
        public int getActiveInstances() { return activeInstances; }
        public long getLastHeartbeat() { return lastHeartbeat; }
        public Map<String, Object> getMetrics() { return metrics; }
    }

    class AgentCommand {
        private final String commandId;
        private final String commandType;
        private final Map<String, Object> parameters;

        public AgentCommand(String commandId, String commandType, Map<String, Object> parameters) {
            this.commandId = commandId;
            this.commandType = commandType;
            this.parameters = parameters != null ? parameters : Map.of();
        }

        public String getCommandId() { return commandId; }
        public String getCommandType() { return commandType; }
        public Map<String, Object> getParameters() { return parameters; }
    }

    class CommandResult {
        private final String commandId;
        private final boolean success;
        private final String message;
        private final Map<String, Object> data;

        public CommandResult(String commandId, boolean success, String message, Map<String, Object> data) {
            this.commandId = commandId;
            this.success = success;
            this.message = message;
            this.data = data != null ? data : Map.of();
        }

        public String getCommandId() { return commandId; }
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Map<String, Object> getData() { return data; }
    }

    class PipelineCreateRequest {
        private final String pipelineId;
        private final String pipelineType;
        private final List<PipelineStage> stages;
        private final Map<String, String> config;

        public PipelineCreateRequest(String pipelineId, String pipelineType, List<PipelineStage> stages, Map<String, String> config) {
            this.pipelineId = pipelineId;
            this.pipelineType = pipelineType;
            this.stages = stages != null ? stages : List.of();
            this.config = config != null ? config : Map.of();
        }

        public String getPipelineId() { return pipelineId; }
        public String getPipelineType() { return pipelineType; }
        public List<PipelineStage> getStages() { return stages; }
        public Map<String, String> getConfig() { return config; }
    }

    class PipelineStage {
        private final String stageId;
        private final String stageType;
        private final Map<String, Object> config;

        public PipelineStage(String stageId, String stageType, Map<String, Object> config) {
            this.stageId = stageId;
            this.stageType = stageType;
            this.config = config != null ? config : Map.of();
        }

        public String getStageId() { return stageId; }
        public String getStageType() { return stageType; }
        public Map<String, Object> getConfig() { return config; }
    }

    interface PipelineHandle {
        String getId();
        Promise<Void> destroy();
    }

    class PipelineStatus {
        private final String pipelineId;
        private final String state;
        private final long eventsProcessed;
        private final long errors;
        private final Map<String, Object> metrics;

        public PipelineStatus(String pipelineId, String state, long eventsProcessed, long errors, Map<String, Object> metrics) {
            this.pipelineId = pipelineId;
            this.state = state;
            this.eventsProcessed = eventsProcessed;
            this.errors = errors;
            this.metrics = metrics != null ? metrics : Map.of();
        }

        public String getPipelineId() { return pipelineId; }
        public String getState() { return state; }
        public long getEventsProcessed() { return eventsProcessed; }
        public long getErrors() { return errors; }
        public Map<String, Object> getMetrics() { return metrics; }
    }
}
