package com.ghatana.virtualorg.framework.hierarchy;

import java.util.List;
import java.util.Optional;

/**
 * Defines the escalation path for decisions beyond an agent's authority.
 *
 * <p><b>Purpose</b><br>
 * Specifies the chain of roles to escalate decisions to when an agent
 * lacks authority. Supports multi-level escalation through organizational
 * hierarchy. Enables automatic routing of decisions to appropriate authority.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * EscalationPath path = EscalationPath.of(
 *     Role.of("Senior Engineer", Layer.INDIVIDUAL_CONTRIBUTOR),
 *     Role.of("Architect Lead", Layer.MANAGEMENT),
 *     Role.of("CTO", Layer.EXECUTIVE)
 * );
 *
 * Role nextLevel = path.getNext().orElse(null); // Senior Engineer
 * EscalationPath remaining = path.getRemaining(); // Architect Lead, CTO
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * Part of virtual-org-framework organizational hierarchy system.
 * Used by OrganizationalAgent to escalate decisions beyond authority.
 *
 * <p><b>Thread Safety</b><br>
 * Immutable record - thread-safe.
 *
 * @param roles ordered list of roles in escalation chain (never null)
 * @see Role
 * @see Authority
 * @doc.type record
 * @doc.purpose Escalation path value object
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record EscalationPath(List<Role> roles) {
    
    /**
     * Compact constructor with defensive copy.
     *
     * @param roles ordered list of roles (copied to prevent external modification)
     */
    public EscalationPath {
        roles = List.copyOf(roles);
    }
    
    /**
     * Creates an escalation path from roles.
     *
     * @param roles the roles in order
     * @return the escalation path
     */
    public static EscalationPath of(Role... roles) {
        return new EscalationPath(List.of(roles));
    }
    
    /**
     * Creates an escalation path from a list of roles.
     *
     * @param roleList the list of roles
     * @return the escalation path
     */
    public static EscalationPath of(List<Role> roleList) {
        return new EscalationPath(roleList);
    }
    
    /**
     * Gets the next role in the escalation chain.
     *
     * @return Optional containing the next role, or empty if none
     */
    public Optional<Role> getNext() {
        return roles.isEmpty() ? Optional.empty() : Optional.of(roles.get(0));
    }
    
    /**
     * Gets the remaining escalation path after the first role.
     *
     * @return new EscalationPath with remaining roles, or empty path if none
     */
    public EscalationPath getRemaining() {
        if (roles.size() <= 1) {
            return new EscalationPath(List.of());
        }
        return new EscalationPath(roles.subList(1, roles.size()));
    }
    
    /**
     * Checks if escalation path is empty (no escalation roles).
     *
     * @return true if no escalation roles
     */
    public boolean isEmpty() {
        return roles.isEmpty();
    }
    
    /**
     * Gets the number of escalation levels.
     *
     * @return number of roles in path
     */
    public int getDepth() {
        return roles.size();
    }
    
    /**
     * Gets the final authority in the escalation chain.
     *
     * @return Optional containing the final role, or empty if path is empty
     */
    public Optional<Role> getFinalAuthority() {
        return roles.isEmpty() ? Optional.empty() : Optional.of(roles.get(roles.size() - 1));
    }
}
