package com.ghatana.virtualorg.framework.agent;

import com.ghatana.contracts.agent.v1.AgentInputProto;
import com.ghatana.contracts.agent.v1.AgentResultProto;
import com.ghatana.platform.domain.agent.registry.AgentExecutionContext;
import com.ghatana.platform.domain.agent.registry.AgentMetrics;
import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.virtualorg.framework.hierarchy.Authority;
import com.ghatana.virtualorg.framework.hierarchy.EscalationPath;
import com.ghatana.virtualorg.framework.hierarchy.Role;

import java.util.List;
import java.util.Set;

/**
 * Base interface for agents within an organizational structure.
 *
 * <p><b>Purpose</b><br>
 * Extends core Agent with organizational concepts (role, hierarchy, authority).
 * Enables agents to participate in organizational workflows and decision-making.
 * Provides authority checking and escalation path management for decisions
 * beyond an agent's scope.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * public class EngineerAgent implements OrganizationalAgent {
 *     private final Role role = Role.of("Engineer", Layer.INDIVIDUAL_CONTRIBUTOR);
 *     private final Authority authority = Authority.builder()
 *         .addDecision("code_review")
 *         .addDecision("merge_pr")
 *         .build();
 *     private final EscalationPath escalation = EscalationPath.of(
 *         Role.of("Senior Engineer", Layer.INDIVIDUAL_CONTRIBUTOR),
 *         Role.of("Architect Lead", Layer.MANAGEMENT)
 *     );
 *
 *     @Override
 *     public Role getRole() {
 *         return role;
 *     }
 *
 *     @Override
 *     public Authority getAuthority() {
 *         return authority;
 *     }
 *
 *     @Override
 *     public Promise<Event> handle(Event event) {
 *         // Process event based on role and authority
 *         if (hasAuthority(event.getDecisionType())) {
 *             return processDecision(event);
 *         } else {
 *             return escalateDecision(event);
 *         }
 *     }
 * }
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * Part of virtual-org-framework product module. Extends core:agent-runtime
 * to add organizational context. Used by all role-based agents in the
 * software organization and other domain implementations.
 *
 * <p><b>Integration Points</b><br>
 * - Consumed by: WorkflowEngine, organizational workflows
 * - Depends on: core:agent-runtime, hierarchy system
 * - Events: Emits decision events, escalation events
 *
 * <p><b>Thread Safety</b><br>
 * Implementations must be thread-safe as agents may be accessed concurrently
 * by multiple workflows and event streams.
 *
 * @see Role
 * @see Authority
 * @see EscalationPath
 * @doc.type interface
 * @doc.purpose Base agent interface with organizational context
 * @doc.layer product
 * @doc.pattern Port
 */
public interface OrganizationalAgent {
    
    /**
     * Gets the organizational role of this agent.
     *
     * @return the role (never null)
     */
    Role getRole();
    
    /**
     * Gets the decision-making authority of this agent.
     *
     * @return the authority (never null)
     */
    Authority getAuthority();
    
    /**
     * Gets the escalation path for decisions beyond this agent's authority.
     *
     * @return the escalation path (may be empty)
     */
    EscalationPath getEscalationPath();
    
    /**
     * Checks if this agent has authority for a given decision.
     *
     * @param decisionType the type of decision
     * @return true if agent has authority
     */
    default boolean hasAuthority(String decisionType) {
        return getAuthority().canDecide(decisionType);
    }
    
    /**
     * Checks if this agent is in a leadership layer.
     *
     * @return true if role is in executive or management layer
     */
    default boolean isLeadership() {
        return getRole().isLeadership();
    }
    
    /**
     * Handles an event based on this agent's role and authority.
     *
     * <p>Implementations should:
     * 1. Check if agent has authority for the decision
     * 2. If yes, process the decision
     * 3. If no, escalate to next authority level
     *
     * @param event the event to handle
     * @param context the agent execution context
     * @return list of response events
     */
    List<Event> handle(Event event, AgentExecutionContext context);

    // --- Methods previously inherited from deprecated domain Agent ---

    String getId();

    String getVersion();

    Set<String> getSupportedEventTypes();

    Set<String> getOutputEventTypes();

    AgentResultProto execute(AgentInputProto input);

    boolean isHealthy();

    AgentMetrics getMetrics();
}
