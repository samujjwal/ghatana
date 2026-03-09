package com.ghatana.softwareorg.domain.persona;

import java.util.Set;
import java.util.Collections;
import java.util.HashSet;

/**
 * Domain model for persona role definition.
 *
 * <p>
 * <b>Purpose</b><br>
 * Defines canonical role structure with permissions, capabilities, and
 * metadata. This is DOMAIN LOGIC - role definitions are not user preferences.
 *
 * <p>
 * <b>Role Hierarchy</b><br>
 * - Base roles: Admin, TechLead, Developer, Viewer - Specialized roles:
 * FullStackDev, BackendDev, FrontendDev, DevOps, etc.
 *
 * <p>
 * <b>Permission Model</b><br>
 * Roles define capabilities (what they can do), not UI preferences. Examples:
 * canApproveCode, canDeployProduction, canAccessAnalytics
 *
 * <p>
 * <b>Boundary Compliance</b><br>
 * This is JAVA domain logic. Node.js backend: - ✅ CAN query role definitions
 * (read-only) - ❌ CANNOT create/modify roles (that's domain logic) - ✅ CAN
 * store which roles user activates (preferences)
 *
 * @doc.type record
 * @doc.purpose Canonical role definition with permissions
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record PersonaRoleDefinition(
        String roleId,
        String displayName,
        String description,
        RoleType type,
        Set<String> permissions,
        Set<String> capabilities,
        Set<String> parentRoles
        ) {

    /**
     * Role type classification
     */
    public enum RoleType {
        /**
         * Base roles: Admin, TechLead, Developer, Viewer
         */
        BASE,
        /**
         * Specialized technical roles: Frontend, Backend, DevOps
         */
        SPECIALIZED,
        /**
         * Custom user-defined roles (not in core set)
         */
        CUSTOM
    }

    /**
     * Compact constructor with validation
     */
    public PersonaRoleDefinition       {
        if (roleId == null || roleId.isBlank()) {
            throw new IllegalArgumentException("roleId cannot be null or blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName cannot be null or blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("type cannot be null");
        }

        // Defensive copies for mutable sets
        permissions = permissions != null
                ? Collections.unmodifiableSet(new HashSet<>(permissions))
                : Collections.emptySet();
        capabilities = capabilities != null
                ? Collections.unmodifiableSet(new HashSet<>(capabilities))
                : Collections.emptySet();
        parentRoles = parentRoles != null
                ? Collections.unmodifiableSet(new HashSet<>(parentRoles))
                : Collections.emptySet();
    }

    /**
     * Check if role has specific permission
     *
     * @param permission Permission to check
     * @return true if role has permission
     */
    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    /**
     * Check if role has specific capability
     *
     * @param capability Capability to check
     * @return true if role has capability
     */
    public boolean hasCapability(String capability) {
        return capabilities.contains(capability);
    }

    /**
     * Check if role inherits from parent
     *
     * @param parentRoleId Parent role ID to check
     * @return true if role inherits from parent
     */
    public boolean inheritsFrom(String parentRoleId) {
        return parentRoles.contains(parentRoleId);
    }

    /**
     * Create base Admin role
     */
    public static PersonaRoleDefinition createAdmin() {
        return new PersonaRoleDefinition(
                "admin",
                "Administrator",
                "Full system access with all permissions",
                RoleType.BASE,
                Set.of(
                        "workspace.manage",
                        "team.manage",
                        "project.manage",
                        "code.approve",
                        "deployment.production",
                        "analytics.full",
                        "settings.modify",
                        "user.manage"
                ),
                Set.of(
                        "viewAllProjects",
                        "approveCodeReviews",
                        "deployProduction",
                        "manageTeam",
                        "viewAnalytics"
                ),
                Set.of()
        );
    }

    /**
     * Create base TechLead role
     */
    public static PersonaRoleDefinition createTechLead() {
        return new PersonaRoleDefinition(
                "tech-lead",
                "Tech Lead",
                "Technical leadership with code approval and architecture decisions",
                RoleType.BASE,
                Set.of(
                        "code.approve",
                        "architecture.review",
                        "deployment.staging",
                        "analytics.team",
                        "project.plan"
                ),
                Set.of(
                        "viewTeamProjects",
                        "approveCodeReviews",
                        "reviewArchitecture",
                        "deployStaging",
                        "viewTeamAnalytics"
                ),
                Set.of()
        );
    }

    /**
     * Create base Developer role
     */
    public static PersonaRoleDefinition createDeveloper() {
        return new PersonaRoleDefinition(
                "developer",
                "Developer",
                "Standard development access with code submission",
                RoleType.BASE,
                Set.of(
                        "code.write",
                        "code.review",
                        "deployment.dev",
                        "project.view"
                ),
                Set.of(
                        "viewAssignedProjects",
                        "submitCode",
                        "reviewCode",
                        "runTests"
                ),
                Set.of()
        );
    }

    /**
     * Create base Viewer role
     */
    public static PersonaRoleDefinition createViewer() {
        return new PersonaRoleDefinition(
                "viewer",
                "Viewer",
                "Read-only access to project information",
                RoleType.BASE,
                Set.of(
                        "project.view",
                        "analytics.basic"
                ),
                Set.of(
                        "viewPublicProjects",
                        "viewBasicAnalytics"
                ),
                Set.of()
        );
    }
}
