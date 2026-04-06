package com.ghatana.kernel.ai;

import java.util.List;
import java.util.Map;

/**
 * Orchestrator for AI agent execution and coordination.
 *
 * <p>Manages agent lifecycle, execution, and coordination for AI-native
 * workflows. Supports agent registration, discovery, and orchestrated execution.</p>
 *
 * @doc.type interface
 * @doc.purpose AI agent orchestration and coordination
 * @doc.layer core
 * @doc.pattern Orchestrator
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public interface AgentOrchestrator {

    /**
     * Executes an agent with a request.
     *
     * @param agent the agent to execute
     * @param request the agent request
     * @return agent response
     */
    AgentResponse executeAgent(KernelAgent agent, AgentRequest request);

    /**
     * Registers an agent with the orchestrator.
     *
     * @param agent the agent to register
     */
    void registerAgent(KernelAgent agent);

    /**
     * Unregisters an agent.
     *
     * @param agentId the agent identifier
     */
    void unregisterAgent(String agentId);

    /**
     * Gets all available agents.
     *
     * @return list of registered agents
     */
    List<KernelAgent> getAvailableAgents();

    /**
     * Executes an agent workflow with multiple agents.
     *
     * @param agents the agents to execute in sequence
     * @param request the initial request
     * @return workflow result
     */
    WorkflowResult executeAgentWorkflow(List<KernelAgent> agents, AgentRequest request);

    /**
     * Gets an agent by ID.
     *
     * @param agentId the agent identifier
     * @return the agent or null if not found
     */
    KernelAgent getAgent(String agentId);

    /**
     * Executes an agent asynchronously.
     *
     * @param agent the agent to execute
     * @param request the agent request
     * @return promise with agent response
     */
    io.activej.promise.Promise<AgentResponse> executeAgentAsync(KernelAgent agent, AgentRequest request);

    /**
     * Represents a kernel agent.
     */
    interface KernelAgent {
        String getAgentId();
        String getName();
        String getDescription();
        AgentResponse execute(AgentRequest request);
        AgentCapabilities getCapabilities();
    }

    /**
     * Agent capabilities definition.
     */
    interface AgentCapabilities {
        List<String> getSupportedOperations();
        Map<String, Object> getMetadata();
        boolean supportsOperation(String operation);
    }

    /**
     * Agent request.
     */
    class AgentRequest {
        private final String requestId;
        private final String operation;
        private final Object payload;
        private final Map<String, Object> context;

        public AgentRequest(String requestId, String operation, Object payload, Map<String, Object> context) {
            this.requestId = requestId;
            this.operation = operation;
            this.payload = payload;
            this.context = context;
        }

        public String getRequestId() { return requestId; }
        public String getOperation() { return operation; }
        public Object getPayload() { return payload; }
        public Map<String, Object> getContext() { return context; }
    }

    /**
     * Agent response.
     */
    class AgentResponse {
        private final String requestId;
        private final boolean success;
        private final Object result;
        private final String error;
        private final double confidence;
        private final Map<String, Object> metadata;

        private AgentResponse(Builder builder) {
            this.requestId = builder.requestId;
            this.success = builder.success;
            this.result = builder.result;
            this.error = builder.error;
            this.confidence = builder.confidence;
            this.metadata = builder.metadata;
        }

        public String getRequestId() { return requestId; }
        public boolean isSuccess() { return success; }
        public Object getResult() { return result; }
        public String getError() { return error; }
        public double getConfidence() { return confidence; }
        public Map<String, Object> getMetadata() { return metadata; }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String requestId;
            private boolean success;
            private Object result;
            private String error;
            private double confidence = 1.0;
            private Map<String, Object> metadata;

            public Builder requestId(String requestId) {
                this.requestId = requestId;
                return this;
            }

            public Builder success(boolean success) {
                this.success = success;
                return this;
            }

            public Builder result(Object result) {
                this.result = result;
                return this;
            }

            public Builder error(String error) {
                this.error = error;
                return this;
            }

            public Builder confidence(double confidence) {
                this.confidence = confidence;
                return this;
            }

            public Builder metadata(Map<String, Object> metadata) {
                this.metadata = metadata;
                return this;
            }

            public AgentResponse build() {
                return new AgentResponse(this);
            }
        }
    }

    /**
     * Workflow execution result.
     */
    class WorkflowResult {
        private final boolean success;
        private final List<AgentResponse> responses;
        private final String error;

        public WorkflowResult(boolean success, List<AgentResponse> responses, String error) {
            this.success = success;
            this.responses = responses;
            this.error = error;
        }

        public boolean isSuccess() { return success; }
        public List<AgentResponse> getResponses() { return responses; }
        public String getError() { return error; }
    }
}
