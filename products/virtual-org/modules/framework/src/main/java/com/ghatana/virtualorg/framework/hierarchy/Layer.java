package com.ghatana.virtualorg.framework.hierarchy;

/**
 * Organizational layers in a hierarchical structure.
 *
 * <p><b>Purpose</b><br>
 * Defines the three standard organizational layers used for authority
 * and escalation path determination. Enables role-based decision making
 * and hierarchical workflows.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * Layer layer = Layer.EXECUTIVE;
 * boolean isLeadership = layer.isLeadership(); // true
 *
 * if (layer == Layer.INDIVIDUAL_CONTRIBUTOR) {
 *     // Handle IC-specific logic
 * }
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * Part of virtual-org-framework organizational hierarchy system.
 * Used by Role, Authority, and EscalationPath for decision-making.
 *
 * <p><b>Thread Safety</b><br>
 * Immutable enum - thread-safe.
 *
 * @see Role
 * @see Authority
 * @see EscalationPath
 * @doc.type enum
 * @doc.purpose Organizational layer enumeration
 * @doc.layer product
 * @doc.pattern Value Object
 */
public enum Layer {
    /**
     * Executive layer (CEO, CTO, CPO).
     * Highest authority, strategic decisions, company direction.
     */
    EXECUTIVE("Executive", 3),
    
    /**
     * Management layer (Architect Lead, DevOps Lead, Product Manager).
     * Tactical decisions, team coordination, resource allocation.
     */
    MANAGEMENT("Management", 2),
    
    /**
     * Individual contributor layer (Engineers, QA, DevOps).
     * Execution, implementation, hands-on work.
     */
    INDIVIDUAL_CONTRIBUTOR("Individual Contributor", 1);
    
    private final String displayName;
    private final int level;
    
    /**
     * Constructor for Layer enum.
     *
     * @param displayName human-readable name
     * @param level hierarchy level (1=IC, 2=Management, 3=Executive)
     */
    Layer(String displayName, int level) {
        this.displayName = displayName;
        this.level = level;
    }
    
    /**
     * Gets the display name for this layer.
     *
     * @return human-readable name
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Gets the hierarchy level (higher = more authority).
     *
     * @return level (1-3)
     */
    public int getLevel() {
        return level;
    }
    
    /**
     * Checks if this layer is leadership (executive or management).
     *
     * @return true if executive or management
     */
    public boolean isLeadership() {
        return this == EXECUTIVE || this == MANAGEMENT;
    }
    
    /**
     * Checks if this layer has higher authority than another layer.
     *
     * @param other the other layer to compare
     * @return true if this layer has higher authority
     */
    public boolean hasHigherAuthority(Layer other) {
        return this.level > other.level;
    }
}
