package com.ghatana.virtualorg.framework.agent;

import com.ghatana.contracts.agent.v1.AgentInputProto;
import com.ghatana.contracts.agent.v1.AgentResultProto;
import com.ghatana.platform.domain.agent.registry.AgentExecutionContext;
import com.ghatana.platform.domain.agent.registry.AgentMetrics;
import com.ghatana.platform.domain.agent.registry.HealthStatus;
import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.virtualorg.framework.hierarchy.Authority;
import com.ghatana.virtualorg.framework.hierarchy.EscalationPath;
import com.ghatana.virtualorg.framework.hierarchy.Role;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Base implementation of OrganizationalAgent with common functionality.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides default implementations for organizational agent behavior including
 * role management, authority checking, and basic metrics. Subclasses only need
 * to implement the event handling logic specific to their role.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * public class EngineerAgent extends BaseOrganizationalAgent {
 *     public EngineerAgent(String id) {
 *         super(
 *             id,
 *             "1.0.0",
 *             Role.of("Engineer", Layer.INDIVIDUAL_CONTRIBUTOR),
 *             Authority.builder()
 *                 .addDecision("code_review")
 *                 .addDecision("merge_pr")
 *                 .build(),
 *             EscalationPath.of(
 *                 Role.of("Senior Engineer", Layer.INDIVIDUAL_CONTRIBUTOR),
 *                 Role.of("Architect Lead", Layer.MANAGEMENT)
 *             ),
 *             Set.of("code.review.requested", "pr.created"),
 *             Set.of("code.review.completed", "pr.merged")
 *         );
 *     }
 *
 *     @Override
 *     protected List<Event> doHandle(Event event, AgentExecutionContext context) {
 *         // Implement role-specific logic
 *         if (hasAuthority(event.getType())) {
 *             return processEvent(event, context);
 *         } else {
 *             return escalateEvent(event, context);
 *         }
 *     }
 * }
 * }</pre>
 *
 * <p>
 * <b>Architecture Role</b><br>
 * Part of virtual-org-framework product module. Provides base implementation
 * for all organizational agents. Subclasses define role-specific behavior.
 *
 * <p>
 * <b>Integration Points</b><br>
 * - Consumed by: Role-specific agent implementations - Depends on:
 * core:agent-runtime, hierarchy system - Events: Processes events based on role
 * and authority
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Thread-safe. Uses atomic counters for metrics. Immutable role, authority, and
 * escalation path.
 *
 * @see OrganizationalAgent
 * @see Role
 * @see Authority
 * @see EscalationPath
 * @doc.type class
 * @doc.purpose Base implementation for organizational agents
 * @doc.layer product
 * @doc.pattern Template Method
 */
public abstract class BaseOrganizationalAgent implements OrganizationalAgent {

    private final String id;
    private final String version;
    private final Role role;
    private final Authority authority;
    private final EscalationPath escalationPath;
    private final Set<String> supportedEventTypes;
    private final Set<String> outputEventTypes;

    // Metrics
    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong escalatedCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);

    /**
     * Constructor with auto-generated ID.
     *
     * @param version agent version
     * @param role organizational role
     * @param authority decision-making authority
     * @param escalationPath escalation path for decisions beyond authority
     * @param supportedEventTypes event types this agent can process
     * @param outputEventTypes event types this agent can produce
     */
    protected BaseOrganizationalAgent(
            String version,
            Role role,
            Authority authority,
            EscalationPath escalationPath,
            Set<String> supportedEventTypes,
            Set<String> outputEventTypes) {
        this(UUID.randomUUID().toString(), version, role, authority, escalationPath,
                supportedEventTypes, outputEventTypes);
    }

    /**
     * Constructor with explicit ID.
     *
     * @param id agent ID
     * @param version agent version
     * @param role organizational role
     * @param authority decision-making authority
     * @param escalationPath escalation path for decisions beyond authority
     * @param supportedEventTypes event types this agent can process
     * @param outputEventTypes event types this agent can produce
     */
    protected BaseOrganizationalAgent(
            String id,
            String version,
            Role role,
            Authority authority,
            EscalationPath escalationPath,
            Set<String> supportedEventTypes,
            Set<String> outputEventTypes) {
        this.id = id;
        this.version = version;
        this.role = role;
        this.authority = authority;
        this.escalationPath = escalationPath;
        this.supportedEventTypes = Collections.unmodifiableSet(new HashSet<>(supportedEventTypes));
        this.outputEventTypes = Collections.unmodifiableSet(new HashSet<>(outputEventTypes));
    }

    // ===== OrganizationalAgent Interface Implementation =====
    @Override
    public Role getRole() {
        return role;
    }

    @Override
    public Authority getAuthority() {
        return authority;
    }

    @Override
    public EscalationPath getEscalationPath() {
        return escalationPath;
    }

    @Override
    public List<Event> handle(Event event, AgentExecutionContext context) {
        try {
            processedCount.incrementAndGet();
            return doHandle(event, context);
        } catch (Exception e) {
            errorCount.incrementAndGet();
            throw new AgentExecutionException(
                    "Failed to handle event: " + event.getType(), e);
        }
    }

    @Override
    public AgentResultProto execute(AgentInputProto input) {
        processedCount.incrementAndGet();
        // Default implementation - subclasses can override
        return AgentResultProto.getDefaultInstance();
    }

    // ===== Agent Interface Implementation =====
    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public Set<String> getSupportedEventTypes() {
        return supportedEventTypes;
    }

    @Override
    public Set<String> getOutputEventTypes() {
        return outputEventTypes;
    }

    @Override
    public boolean isHealthy() {
        // Agent is healthy if error rate is below threshold
        long total = processedCount.get();
        long errors = errorCount.get();
        if (total == 0) {
            return true;
        }
        double errorRate = (double) errors / total;
        return errorRate < 0.1; // 10% error threshold
    }

    @Override
    public AgentMetrics getMetrics() {
        return new SimpleAgentMetrics(
                processedCount.get(),
                escalatedCount.get(),
                errorCount.get()
        );
    }

    // ===== Template Method =====
    /**
     * Template method for handling events. Subclasses implement role-specific
     * logic.
     *
     * @param event the event to handle
     * @param context the execution context
     * @return list of response events
     */
    protected abstract List<Event> doHandle(Event event, AgentExecutionContext context);

    // ===== Helper Methods =====
    /**
     * Records an escalation for metrics.
     */
    protected void recordEscalation() {
        escalatedCount.incrementAndGet();
    }

    /**
     * Gets the number of events processed.
     *
     * @return processed count
     */
    protected long getProcessedCount() {
        return processedCount.get();
    }

    /**
     * Gets the number of events escalated.
     *
     * @return escalated count
     */
    protected long getEscalatedCount() {
        return escalatedCount.get();
    }

    /**
     * Gets the number of errors encountered.
     *
     * @return error count
     */
    protected long getErrorCount() {
        return errorCount.get();
    }

    /**
     * Simple implementation of AgentMetrics.
     */
    private static class SimpleAgentMetrics implements AgentMetrics {

        private final long processedCount;
        private final long escalatedCount;
        private final long errorCount;

        SimpleAgentMetrics(long processedCount, long escalatedCount, long errorCount) {
            this.processedCount = processedCount;
            this.escalatedCount = escalatedCount;
            this.errorCount = errorCount;
        }

        @Override
        public long processedCount() {
            return processedCount;
        }

        @Override
        public long getEventsProcessed() {
            return processedCount;
        }

        @Override
        public long getErrorCount() {
            return errorCount;
        }

        @Override
        public double getAverageProcessingTimeMs() {
            return 0.0;
        }

        @Override
        public double getCurrentThroughput() {
            return 0.0;
        }

        @Override
        public double getPeakThroughput() {
            return 0.0;
        }

        @Override
        public java.time.Instant getLastProcessedAt() {
            return java.time.Instant.now();
        }

        @Override
        public long getMemoryUsageMb() {
            return 0L;
        }

        @Override
        public double getCpuUtilization() {
            return 0.0;
        }

        @Override
        public int getActiveThreads() {
            return 0;
        }

        @Override
        public java.util.Map<String, Object> getCustomMetrics() {
            return new java.util.HashMap<>();
        }

        @Override
        public HealthStatus getHealthStatus() {
            return errorCount > 0 ? HealthStatus.UNHEALTHY : HealthStatus.HEALTHY;
        }

        public long getEscalatedCount() {
            return escalatedCount;
        }
    }

    /**
     * Exception thrown when agent execution fails.
     */
    public static class AgentExecutionException extends RuntimeException {

        public AgentExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
