package com.ghatana.virtualorg.framework.hierarchy;

/**
 * Represents an organizational role with name and layer.
 *
 * <p><b>Purpose</b><br>
 * Immutable value object representing a position within an organizational hierarchy.
 * Combines role name (e.g., "Engineer") with organizational layer (e.g., IC).
 * Used to define agent roles and determine authority levels.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * Role engineer = Role.of("Engineer", Layer.INDIVIDUAL_CONTRIBUTOR);
 * Role cto = Role.of("CTO", Layer.EXECUTIVE);
 *
 * if (engineer.getLayer().isLeadership()) {
 *     // Handle leadership role
 * }
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * Part of virtual-org-framework organizational hierarchy system.
 * Used by OrganizationalAgent to define agent roles.
 *
 * <p><b>Thread Safety</b><br>
 * Immutable record - thread-safe.
 *
 * @param name the role name (e.g., "Engineer", "CTO") - never null or empty
 * @param layer the organizational layer - never null
 * @see Layer
 * @see Authority
 * @see EscalationPath
 * @doc.type record
 * @doc.purpose Organizational role value object
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record Role(String name, Layer layer) {
    
    /**
     * Compact constructor with validation.
     *
     * @throws IllegalArgumentException if name is null/empty or layer is null
     */
    public Role {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Role name cannot be null or empty");
        }
        if (layer == null) {
            throw new IllegalArgumentException("Layer cannot be null");
        }
    }
    
    /**
     * Creates a new Role with validation.
     *
     * @param name the role name (not null, not empty)
     * @param layer the organizational layer (not null)
     * @return the role
     * @throws IllegalArgumentException if validation fails
     */
    public static Role of(String name, Layer layer) {
        return new Role(name, layer);
    }
    
    /**
     * Gets the display name for this role.
     *
     * @return formatted string like "Engineer (Individual Contributor)"
     */
    public String getDisplayName() {
        return name + " (" + layer.getDisplayName() + ")";
    }
    
    /**
     * Checks if this role is in a leadership layer.
     *
     * @return true if layer is executive or management
     */
    public boolean isLeadership() {
        return layer.isLeadership();
    }
    
    /**
     * Checks if this role has higher authority than another role.
     *
     * @param other the other role to compare
     * @return true if this role's layer has higher authority
     */
    public boolean hasHigherAuthority(Role other) {
        return this.layer.hasHigherAuthority(other.layer);
    }
}
