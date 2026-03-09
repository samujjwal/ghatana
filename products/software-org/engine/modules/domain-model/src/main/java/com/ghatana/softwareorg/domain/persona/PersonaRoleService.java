package com.ghatana.softwareorg.domain.persona;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Domain service for persona role management.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides authoritative role definitions, permission resolution, and role
 * validation for software-org personas. This is DOMAIN LOGIC that determines
 * what roles exist and what they can do.
 *
 * <p>
 * <b>Service Functions</b><br>
 * - getRoleDefinition: Retrieve canonical role definition - getAllRoles: List
 * all available roles - validateRoleActivation: Check if user can activate role
 * combination - resolveEffectivePermissions: Compute permissions from active
 * roles
 *
 * <p>
 * <b>Integration with Node.js Backend</b><br>
 * Node.js backend should call this service via: - HTTP REST endpoint: GET
 * /api/v1/personas/roles - gRPC service: PersonaRoleService.getRoles()
 *
 * Node.js backend stores WHICH roles user activates, but queries THIS service
 * to know WHAT those roles mean.
 *
 * <p>
 * <b>Boundary Compliance</b><br>
 * This is JAVA domain logic. Node.js backend: - ✅ CAN query role definitions
 * (read-only via REST/gRPC) - ❌ CANNOT modify role definitions - ✅ CAN validate
 * role combinations before saving preferences - ✅ CAN resolve effective
 * permissions for authorization checks
 *
 * <p>
 * <b>EventCloud Integration</b><br>
 * Emits events when roles are queried or validated: - persona.role.queried -
 * persona.role.validated - persona.permissions.resolved
 *
 * @doc.type class
 * @doc.purpose Domain service for persona role management
 * @doc.layer product
 * @doc.pattern Service
 */
public class PersonaRoleService {

    private final Map<String, PersonaRoleDefinition> roleRegistry;

    /**
     * Create role service with default role definitions
     */
    public PersonaRoleService() {
        this.roleRegistry = new HashMap<>();
        registerDefaultRoles();
    }

    /**
     * Register default base roles
     */
    private void registerDefaultRoles() {
        registerRole(PersonaRoleDefinition.createAdmin());
        registerRole(PersonaRoleDefinition.createTechLead());
        registerRole(PersonaRoleDefinition.createDeveloper());
        registerRole(PersonaRoleDefinition.createViewer());

        // Register specialized roles
        registerRole(createFullStackDevRole());
        registerRole(createBackendDevRole());
        registerRole(createFrontendDevRole());
        registerRole(createDevOpsEngineerRole());
        registerRole(createQAEngineerRole());
        registerRole(createProductManagerRole());
        registerRole(createDesignerRole());
        registerRole(createDataAnalystRole());
        registerRole(createSecurityEngineerRole());
        registerRole(createArchitectRole());
    }

    /**
     * Register a role definition
     *
     * @param role Role definition to register
     */
    public void registerRole(PersonaRoleDefinition role) {
        roleRegistry.put(role.roleId(), role);
    }

    /**
     * Get role definition by ID
     *
     * @param roleId Role identifier
     * @return Role definition or empty if not found
     */
    public Optional<PersonaRoleDefinition> getRoleDefinition(String roleId) {
        return Optional.ofNullable(roleRegistry.get(roleId));
    }

    /**
     * Get all registered roles
     *
     * @return List of all role definitions
     */
    public List<PersonaRoleDefinition> getAllRoles() {
        return List.copyOf(roleRegistry.values());
    }

    /**
     * Get roles by type
     *
     * @param type Role type filter
     * @return List of roles matching type
     */
    public List<PersonaRoleDefinition> getRolesByType(PersonaRoleDefinition.RoleType type) {
        return roleRegistry.values().stream()
                .filter(role -> role.type() == type)
                .collect(Collectors.toList());
    }

    /**
     * Validate if role combination is allowed
     *
     * <p>
     * Business rules: - User can activate multiple compatible roles - Admin
     * role cannot be combined with Viewer - Maximum 5 active roles per user
     *
     * @param roleIds List of role IDs to validate
     * @return Validation result with error message if invalid
     */
    public ValidationResult validateRoleActivation(List<String> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return ValidationResult.invalid("At least one role must be activated");
        }

        if (roleIds.size() > 5) {
            return ValidationResult.invalid("Maximum 5 roles can be activated");
        }

        // Check all roles exist
        for (String roleId : roleIds) {
            if (!roleRegistry.containsKey(roleId)) {
                return ValidationResult.invalid("Unknown role: " + roleId);
            }
        }

        // Check incompatible combinations
        boolean hasAdmin = roleIds.contains("admin");
        boolean hasViewer = roleIds.contains("viewer");

        if (hasAdmin && hasViewer) {
            return ValidationResult.invalid("Admin and Viewer roles are incompatible");
        }

        return ValidationResult.valid();
    }

    /**
     * Resolve effective permissions from active roles
     *
     * <p>
     * Combines permissions from all active roles, including inherited
     * permissions from parent roles.
     *
     * @param roleIds List of active role IDs
     * @return Set of all effective permissions
     */
    public EffectivePermissions resolveEffectivePermissions(List<String> roleIds) {
        var permissions = new HashMap<String, Boolean>();
        var capabilities = new HashMap<String, Boolean>();

        for (String roleId : roleIds) {
            getRoleDefinition(roleId).ifPresent(role -> {
                role.permissions().forEach(perm -> permissions.put(perm, true));
                role.capabilities().forEach(cap -> capabilities.put(cap, true));

                // Resolve parent role permissions
                role.parentRoles().forEach(parentId -> {
                    getRoleDefinition(parentId).ifPresent(parent -> {
                        parent.permissions().forEach(perm -> permissions.put(perm, true));
                        parent.capabilities().forEach(cap -> capabilities.put(cap, true));
                    });
                });
            });
        }

        return new EffectivePermissions(
                Map.copyOf(permissions),
                Map.copyOf(capabilities)
        );
    }

    // Specialized role factory methods
    private PersonaRoleDefinition createFullStackDevRole() {
        return new PersonaRoleDefinition(
                "fullstack-developer",
                "Full-Stack Developer",
                "Full-stack development with frontend and backend expertise",
                PersonaRoleDefinition.RoleType.SPECIALIZED,
                Set.of("code.write", "code.review", "deployment.dev", "project.view"),
                Set.of("viewAssignedProjects", "submitCode", "reviewCode", "runTests", "debugProduction"),
                Set.of("developer")
        );
    }

    private PersonaRoleDefinition createBackendDevRole() {
        return new PersonaRoleDefinition(
                "backend-developer",
                "Backend Developer",
                "Backend services, APIs, and database development",
                PersonaRoleDefinition.RoleType.SPECIALIZED,
                Set.of("code.write", "code.review", "database.read", "api.test"),
                Set.of("viewAssignedProjects", "submitCode", "reviewCode", "queryDatabase"),
                Set.of("developer")
        );
    }

    private PersonaRoleDefinition createFrontendDevRole() {
        return new PersonaRoleDefinition(
                "frontend-developer",
                "Frontend Developer",
                "UI/UX implementation and frontend architecture",
                PersonaRoleDefinition.RoleType.SPECIALIZED,
                Set.of("code.write", "code.review", "design.view", "analytics.user"),
                Set.of("viewAssignedProjects", "submitCode", "reviewCode", "viewDesigns"),
                Set.of("developer")
        );
    }

    private PersonaRoleDefinition createDevOpsEngineerRole() {
        return new PersonaRoleDefinition(
                "devops-engineer",
                "DevOps Engineer",
                "Infrastructure, deployment, and monitoring",
                PersonaRoleDefinition.RoleType.SPECIALIZED,
                Set.of("deployment.staging", "deployment.production", "monitoring.full", "infrastructure.manage"),
                Set.of("deployStaging", "deployProduction", "manageInfrastructure", "viewLogs"),
                Set.of("developer")
        );
    }

    private PersonaRoleDefinition createQAEngineerRole() {
        return new PersonaRoleDefinition(
                "qa-engineer",
                "QA Engineer",
                "Quality assurance, testing, and bug tracking",
                PersonaRoleDefinition.RoleType.SPECIALIZED,
                Set.of("test.execute", "bug.report", "test.plan", "project.view"),
                Set.of("viewAssignedProjects", "executTests", "reportBugs", "createTestPlans"),
                Set.of("developer")
        );
    }

    private PersonaRoleDefinition createProductManagerRole() {
        return new PersonaRoleDefinition(
                "product-manager",
                "Product Manager",
                "Product strategy, roadmap, and requirements",
                PersonaRoleDefinition.RoleType.SPECIALIZED,
                Set.of("product.plan", "requirements.define", "analytics.product", "project.view"),
                Set.of("viewAllProjects", "defineRequirements", "viewProductAnalytics", "manageProdBacklog"),
                Set.of("viewer")
        );
    }

    private PersonaRoleDefinition createDesignerRole() {
        return new PersonaRoleDefinition(
                "designer",
                "UX/UI Designer",
                "User experience and interface design",
                PersonaRoleDefinition.RoleType.SPECIALIZED,
                Set.of("design.create", "design.review", "prototype.create", "user.research"),
                Set.of("viewAssignedProjects", "createDesigns", "createPrototypes", "conductResearch"),
                Set.of("viewer")
        );
    }

    private PersonaRoleDefinition createDataAnalystRole() {
        return new PersonaRoleDefinition(
                "data-analyst",
                "Data Analyst",
                "Data analysis, reporting, and insights",
                PersonaRoleDefinition.RoleType.SPECIALIZED,
                Set.of("analytics.full", "database.read", "report.create", "dashboard.create"),
                Set.of("viewAllProjects", "queryDatabase", "createReports", "createDashboards"),
                Set.of("viewer")
        );
    }

    private PersonaRoleDefinition createSecurityEngineerRole() {
        return new PersonaRoleDefinition(
                "security-engineer",
                "Security Engineer",
                "Security audits, vulnerability management, compliance",
                PersonaRoleDefinition.RoleType.SPECIALIZED,
                Set.of("security.audit", "vulnerability.scan", "compliance.check", "code.review"),
                Set.of("viewAllProjects", "auditSecurity", "scanVulnerabilities", "reviewSecurityCode"),
                Set.of("developer")
        );
    }

    private PersonaRoleDefinition createArchitectRole() {
        return new PersonaRoleDefinition(
                "architect",
                "Software Architect",
                "System architecture, design patterns, technical decisions",
                PersonaRoleDefinition.RoleType.SPECIALIZED,
                Set.of("architecture.review", "architecture.design", "code.approve", "project.view"),
                Set.of("viewAllProjects", "reviewArchitecture", "designArchitecture", "approveCodeReviews"),
                Set.of("tech-lead")
        );
    }

    /**
     * Validation result for role activation
     */
    public record ValidationResult(boolean isValid, String errorMessage) {
        

    

    public static ValidationResult valid() {
        return new ValidationResult(true, null);
    }

    public static ValidationResult invalid(String message) {
        return new ValidationResult(false, message);
    }
}

/**
 * Effective permissions resolved from active roles
 */
public record EffectivePermissions(
        Map<String, Boolean> permissions,
        Map<String, Boolean> capabilities
        ) {

    public boolean hasPermission(String permission) {
        return permissions.getOrDefault(permission, false);
    }

    public boolean hasCapability(String capability) {
        return capabilities.getOrDefault(capability, false);
    }
}
}
