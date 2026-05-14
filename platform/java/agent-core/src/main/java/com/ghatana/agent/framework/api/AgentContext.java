package com.ghatana.agent.framework.api;

import com.ghatana.agent.framework.memory.MemoryStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * Execution context for agent operations.
 * Provides access to:
 * <ul>
 *   <li>Memory (episodic, semantic, procedural, preference)</li>
 *   <li>Configuration and environment variables</li>
 *   <li>Logging and observability</li>
 *   <li>Metrics and tracing</li>
 *   <li>Tenant/user/session information</li>
 * </ul>
 *
 * <p>Context instances are immutable and thread-safe.
 * Use {@link Builder} to create modified contexts for nested operations.
 *
 * @doc.type interface
 * @doc.purpose Agent execution context with memory and observability
 * @doc.layer framework
 * @doc.pattern Context Object
 */
public interface AgentContext {

    /**
     * Gets the unique ID for this agent execution turn.
     * @return Turn ID (never null)
     */
    @NotNull
    String getTurnId();

    /**
     * Gets the unique ID of the agent executing in this context.
     * @return Agent ID (never null)
     */
    @NotNull
    String getAgentId();

    /**
     * Gets the tenant ID for multi-tenant isolation.
     * @return Tenant ID (never null)
     */
    @NotNull
    String getTenantId();

    /**
     * Gets the user ID if this execution is user-initiated.
     * @return User ID, or null if system-initiated
     */
    @Nullable
    String getUserId();

    /**
     * Gets the session ID for conversation continuity.
     * @return Session ID, or null if no session
     */
    @Nullable
    String getSessionId();

    /**
     * Gets the working directory for file operations.
     * @return Working directory path, or null if not set
     */
    @Nullable
    String getWorkingDirectory();

    /**
     * Gets the repository ID for version control operations.
     * @return Repository ID, or null if not set
     */
    @Nullable
    String getRepoId();

    /**
     * Gets the timestamp when this turn started.
     * @return Turn start time (never null)
     */
    @NotNull
    Instant getStartTime();

    /**
     * Gets the memory store for this agent.
     * @return Memory store (never null)
     */
    @NotNull
    MemoryStore getMemoryStore();

    /**
     * Gets the logger for this agent.
     * @return Logger (never null)
     */
    @NotNull
    Logger getLogger();

    /**
     * Gets a configuration value.
     * @param key Configuration key
     * @return Configuration value, or null if not set
     */
    @Nullable
    Object getConfig(@NotNull String key);

    /**
     * Gets a configuration value with default.
     * @param key Configuration key
     * @param defaultValue Default value if not set
     * @param <T> Value type
     * @return Configuration value or default
     */
    @NotNull
    <T> T getConfigOrDefault(@NotNull String key, @NotNull T defaultValue);

    /**
     * Gets all configuration as immutable map.
     * @return Configuration map (never null)
     */
    @NotNull
    Map<String, Object> getAllConfig();

    /**
     * Records a metric for observability.
     * @param name Metric name
     * @param value Metric value
     */
    void recordMetric(@NotNull String name, double value);

    /**
     * Records a metric with tags.
     * @param name Metric name
     * @param value Metric value
     * @param tags Additional tags
     */
    void recordMetric(@NotNull String name, double value, @NotNull Map<String, String> tags);

    /**
     * Adds a tag to the current trace span.
     * @param key Tag key
     * @param value Tag value
     */
    void addTraceTag(@NotNull String key, @NotNull String value);

    /**
     * Gets remaining budget in USD for this execution.
     * Used for cost control.
     * @return Remaining budget, or null if no budget limit
     */
    @Nullable
    Double getRemainingBudget();

    /**
     * Deducts cost from the budget.
     * @param cost Cost to deduct in USD
     * @throws BudgetExceededException if cost exceeds remaining budget
     */
    void deductCost(double cost) throws BudgetExceededException;

    // ─────────────────────────────────────────────────────────────────────────
    // v2.0 additions — default methods for backward compatibility
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Gets the distributed tracing correlation ID.
     *
     * @return Trace ID, or null if not set
     * @since 2.0.0
     */
    @Nullable
    default String getTraceId() {
        return null;
    }

    /**
     * Returns the mutable metadata map that flows between pipeline stages.
     * Agents can write to this to communicate with downstream agents.
     *
     * @return Metadata map (never null)
     * @since 2.0.0
     */
    @NotNull
    default Map<String, Object> getMetadata() {
        return Collections.emptyMap();
    }

    /**
     * Sets a metadata value.
     *
     * @param key   metadata key
     * @param value metadata value (should be serialisable)
     * @since 2.0.0
     */
    default void setMetadata(@NotNull String key, @NotNull Object value) {
        // no-op default — overridden in DefaultAgentContext
    }

    /**
     * Returns the memory plane object if the context is memory-aware.
     * The return type is {@code Object} to avoid a compile-time dependency
     * on the agent-memory module. Cast to {@code MemoryPlane} at the call site
     * or use {@code MemoryAwareContext.from(context)} for type-safe access.
     *
     * @return Memory plane object or null if not configured
     * @since 2.1.0
     */
    @Nullable
    default Object getMemoryPlane() {
        return null;
    }

    /**
     * Derives a child context for a sub-agent or nested invocation.
     * Inherits tenant, trace, and config; gets a new turnId and agentId.
     *
     * @param childAgentId the child agent's ID
     * @return a new child context
     * @since 2.0.0
     */
    @NotNull
    default AgentContext deriveChild(@NotNull String childAgentId) {
        return toBuilder()
                .turnId(UUID.randomUUID().toString())
                .agentId(childAgentId)
                .startTime(Instant.now())
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Governance typed context entries — v2.2 additions
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Gets the agent release ID for this execution.
     * Used for governance and version tracking.
     *
     * @return Agent release ID, or null if not set
     * @since 2.2.0
     */
    @Nullable
    default String getAgentReleaseId() {
        Object value = getConfig("agentReleaseId");
        return value != null ? value.toString() : null;
    }

    /**
     * Gets the agent release state for this execution.
     * Used for governance and release lifecycle tracking.
     *
     * @return Agent release state, or null if not set
     * @since 2.2.0
     */
    @Nullable
    default String getReleaseState() {
        Object value = getConfig("releaseState");
        return value != null ? value.toString() : null;
    }

    /**
     * Gets the learning contract for this execution.
     * Used for governance and learning policy enforcement.
     *
     * @return Learning contract, or null if not set
     * @since 2.2.0
     */
    @Nullable
    default com.ghatana.agent.learning.LearningContract getLearningContract() {
        Object value = getMetadata().get("learningContract");
        return value instanceof com.ghatana.agent.learning.LearningContract
                ? (com.ghatana.agent.learning.LearningContract) value
                : null;
    }

    /**
     * Gets the mastery decision for this execution.
     * Used for governance and mode selection.
     *
     * @return Mastery decision, or null if not set
     * @since 2.2.0
     */
    @Nullable
    default com.ghatana.agent.mastery.MasteryDecision getMasteryDecision() {
        Object value = getMetadata().get("masteryDecision");
        return value instanceof com.ghatana.agent.mastery.MasteryDecision
                ? (com.ghatana.agent.mastery.MasteryDecision) value
                : null;
    }

    /**
     * Gets the mode selection result for this execution.
     * Used for governance and execution strategy.
     *
     * @return Mode selection result, or null if not set
     * @since 2.2.0
     */
    @Nullable
    default com.ghatana.agent.runtime.mode.ModeSelectionResult getModeSelection() {
        Object value = getMetadata().get("modeSelection");
        return value instanceof com.ghatana.agent.runtime.mode.ModeSelectionResult
                ? (com.ghatana.agent.runtime.mode.ModeSelectionResult) value
                : null;
    }

    /**
     * Gets the version context for this execution.
     * Used for version compatibility and dependency tracking.
     *
     * @return Version context, or null if not set
     * @since 2.2.0
     */
    @Nullable
    default com.ghatana.agent.context.version.VersionContext getVersionContext() {
        Object value = getMetadata().get("versionContext");
        return value instanceof com.ghatana.agent.context.version.VersionContext
                ? (com.ghatana.agent.context.version.VersionContext) value
                : null;
    }

    /**
     * Gets the specification digest for this execution.
     * Used for configuration validation.
     *
     * @return Spec digest, or null if not set
     * @since 2.2.0
     */
    @Nullable
    default String getSpecDigest() {
        Object value = getConfig("specDigest");
        return value != null ? value.toString() : null;
    }

    /**
     * Gets the environment fingerprint from metadata.
     * Used for version context and dependency tracking.
     *
     * @return Environment fingerprint, or null if not set
     * @since 2.2.0
     */
    @Nullable
    default com.ghatana.agent.environment.EnvironmentFingerprint getEnvironmentFingerprint() {
        Object value = getMetadata().get("environmentFingerprint");
        return value instanceof com.ghatana.agent.environment.EnvironmentFingerprint
                ? (com.ghatana.agent.environment.EnvironmentFingerprint) value
                : null;
    }

    /**
     * Gets the mastery decision from metadata.
     * Used for governance and mode selection.
     *
     * @return Mastery item, or null if not set
     * @since 2.2.0
     */
    @Nullable
    default com.ghatana.agent.mastery.MasteryItem getMasteryItem() {
        Object value = getMetadata().get("masteryItem");
        return value instanceof com.ghatana.agent.mastery.MasteryItem
                ? (com.ghatana.agent.mastery.MasteryItem) value
                : null;
    }

    /**
     * Gets the execution mode from metadata.
     * Used for governance and mode selection.
     *
     * @return Execution mode, or null if not set
     * @since 2.2.0
     */
    @Nullable
    default com.ghatana.agent.runtime.mode.ExecutionMode getExecutionMode() {
        Object value = getMetadata().get("executionMode");
        return value instanceof com.ghatana.agent.runtime.mode.ExecutionMode
                ? (com.ghatana.agent.runtime.mode.ExecutionMode) value
                : null;
    }

    /**
     * Gets the approval proof from metadata.
     * Used for governance and approval validation.
     *
     * @return Approval proof, or null if not set
     * @since 2.3.0
     */
    @Nullable
    default com.ghatana.agent.approval.ApprovalProof getApprovalProof() {
        Object value = getMetadata().get("approvalProof");
        return value instanceof com.ghatana.agent.approval.ApprovalProof
                ? (com.ghatana.agent.approval.ApprovalProof) value
                : null;
    }

    /**
     * Gets the verification proof from metadata.
     * Used for governance and verification validation.
     *
     * @return Verification proof, or null if not set
     * @since 2.3.0
     */
    @Nullable
    default com.ghatana.agent.approval.VerificationProof getVerificationProof() {
        Object value = getMetadata().get("verificationProof");
        return value instanceof com.ghatana.agent.approval.VerificationProof
                ? (com.ghatana.agent.approval.VerificationProof) value
                : null;
    }

    /**
     * Gets the evaluation run proof from metadata.
     * Used for governance and evaluation validation.
     *
     * @return Evaluation run proof, or null if not set
     * @since 2.3.0
     */
    @Nullable
    default com.ghatana.agent.approval.EvaluationRunProof getEvaluationRunProof() {
        Object value = getMetadata().get("evaluationRunProof");
        return value instanceof com.ghatana.agent.approval.EvaluationRunProof
                ? (com.ghatana.agent.approval.EvaluationRunProof) value
                : null;
    }

    /**
     * Gets the policy decision proof from metadata.
     * Used for governance and policy validation.
     *
     * @return Policy decision proof, or null if not set
     * @since 2.3.0
     */
    @Nullable
    default com.ghatana.agent.approval.PolicyDecisionProof getPolicyDecisionProof() {
        Object value = getMetadata().get("policyDecisionProof");
        return value instanceof com.ghatana.agent.approval.PolicyDecisionProof
                ? (com.ghatana.agent.approval.PolicyDecisionProof) value
                : null;
    }

    /**
     * Creates a new AgentContext builder.
     * @return New builder
     */
    @NotNull
    Builder toBuilder();

    /**
     * Creates a new AgentContext builder.
     * @return New builder
     */
    @NotNull
    static Builder builder() {
        return new DefaultAgentContext.BuilderImpl();
    }

    /**
     * Creates a minimal no-op AgentContext suitable for testing or single-step evaluation.
     *
     * @return An empty context with no-op memory and default identifiers
     */
    @NotNull
    static AgentContext empty() {
        return builder()
                .agentId("test-agent")
                .turnId("test-turn")
                .tenantId("test-tenant")
                .memoryStore(MemoryStore.noOp())
                .build();
    }

    /**
     * Builder for AgentContext.
     */
    interface Builder {

        @NotNull
        Builder turnId(@NotNull String turnId);

        @NotNull
        Builder agentId(@NotNull String agentId);

        @NotNull
        Builder tenantId(@NotNull String tenantId);

        @NotNull
        Builder userId(@Nullable String userId);

        @NotNull
        Builder sessionId(@Nullable String sessionId);

        @NotNull
        Builder startTime(@NotNull Instant startTime);

        @NotNull
        Builder memoryStore(@NotNull MemoryStore memoryStore);

        @NotNull
        Builder logger(@NotNull Logger logger);

        @NotNull
        Builder config(@NotNull Map<String, Object> config);

        @NotNull
        Builder addConfig(@NotNull String key, @NotNull Object value);

        @NotNull
        Builder remainingBudget(@Nullable Double budget);

        @NotNull
        Builder traceId(@Nullable String traceId);

        @NotNull
        Builder metadata(@Nullable Map<String, Object> metadata);

        @NotNull
        AgentContext build();
    }

    /**
     * Exception thrown when budget is exceeded.
     */
    class BudgetExceededException extends Exception {
        private static final long serialVersionUID = 1L;

        private final double requestedCost;
        private final double remainingBudget;

        public BudgetExceededException(double requestedCost, double remainingBudget) {
            super(String.format("Cost %.2f exceeds remaining budget %.2f",
                requestedCost, remainingBudget));
            this.requestedCost = requestedCost;
            this.remainingBudget = remainingBudget;
        }

        public double getRequestedCost() {
            return requestedCost;
        }

        public double getRemainingBudget() {
            return remainingBudget;
        }
    }
}
