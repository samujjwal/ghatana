package com.ghatana.softwareorg.domain.hierarchy;

/**
 * Organizational hierarchy layers.
 *
 * <p>
 * <b>Purpose</b><br>
 * Defines the standard organizational layers used for authority
 * and escalation path determination. Aligns with the UI PersonaType
 * and virtual-org-framework Layer enum.
 *
 * <p>
 * <b>Layer Mapping</b><br>
 * - ORGANIZATION: Owner/CEO level (highest authority)
 * - EXECUTIVE: CTO, CPO, etc. (strategic decisions)
 * - MANAGEMENT: Leads, Managers (tactical decisions)
 * - OPERATIONS: Admin roles (cross-cutting support)
 * - CONTRIBUTOR: Individual contributors (execution)
 *
 * @doc.type enum
 * @doc.purpose Organizational hierarchy layer enumeration
 * @doc.layer product
 * @doc.pattern Value Object
 */
public enum HierarchyLayer {
    /**
     * Organization layer (Owner, CEO).
     * Highest authority, strategic direction, company-wide decisions.
     */
    ORGANIZATION("Organization", 4),

    /**
     * Executive layer (CTO, CPO, CFO).
     * Strategic decisions, department-wide authority.
     */
    EXECUTIVE("Executive", 3),

    /**
     * Management layer (Architect Lead, DevOps Lead, Product Manager).
     * Tactical decisions, team coordination, resource allocation.
     */
    MANAGEMENT("Management", 2),

    /**
     * Operations layer (Admin roles).
     * Cross-cutting support, system administration.
     * Same level as management but different scope.
     */
    OPERATIONS("Operations", 2),

    /**
     * Contributor layer (Engineers, QA, DevOps).
     * Execution, implementation, hands-on work.
     */
    CONTRIBUTOR("Contributor", 1);

    private final String displayName;
    private final int level;

    HierarchyLayer(String displayName, int level) {
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
     * @return level (1-4)
     */
    public int getLevel() {
        return level;
    }

    /**
     * Checks if this layer is leadership (organization, executive, or management).
     *
     * @return true if leadership layer
     */
    public boolean isLeadership() {
        return this == ORGANIZATION || this == EXECUTIVE || this == MANAGEMENT;
    }

    /**
     * Checks if this layer has higher authority than another layer.
     *
     * @param other the other layer to compare
     * @return true if this layer has higher authority
     */
    public boolean hasHigherAuthority(HierarchyLayer other) {
        return this.level > other.level;
    }

    /**
     * Checks if this layer can approve actions from another layer.
     *
     * @param other the layer requesting approval
     * @return true if this layer can approve
     */
    public boolean canApprove(HierarchyLayer other) {
        return this.level >= other.level;
    }

    /**
     * Maps a persona type string to HierarchyLayer.
     *
     * @param personaType the persona type (owner, executive, manager, admin, ic)
     * @return corresponding HierarchyLayer
     */
    public static HierarchyLayer fromPersonaType(String personaType) {
        if (personaType == null) {
            return CONTRIBUTOR;
        }
        return switch (personaType.toLowerCase()) {
            case "owner" -> ORGANIZATION;
            case "executive" -> EXECUTIVE;
            case "manager" -> MANAGEMENT;
            case "admin" -> OPERATIONS;
            case "ic" -> CONTRIBUTOR;
            default -> CONTRIBUTOR;
        };
    }
}
