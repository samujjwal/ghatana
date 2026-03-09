package com.ghatana.softwareorg.domain.persona;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for PersonaRoleService.
 *
 * Tests validate: - Role registration and retrieval - Role validation rules
 * (max 5 roles, incompatible combos) - Permission resolution from multiple
 * roles - Role inheritance and composition - Business rule enforcement
 *
 * @see PersonaRoleService
 */
@DisplayName("Persona Role Service Tests")
class PersonaRoleServiceTest {

    private PersonaRoleService service;

    @BeforeEach
    void setUp() {
        // GIVEN: Fresh service instance with default roles
        service = new PersonaRoleService();
    }

    /**
     * Verifies that all default roles are registered.
     *
     * GIVEN: Service initialized with default roles WHEN: getAllRoles is called
     * THEN: Returns 14 roles (4 base + 10 specialized)
     */
    @Test
    @DisplayName("Should register all 14 default roles")
    void shouldRegisterAllDefaultRoles() {
        // WHEN: Get all roles
        List<PersonaRoleDefinition> roles = service.getAllRoles();

        // THEN: 14 roles are registered
        assertThat(roles)
                .as("Should have 14 default roles")
                .hasSize(14);

        // THEN: Base roles are present
        assertThat(roles.stream().map(PersonaRoleDefinition::roleId))
                .as("Should contain base roles")
                .contains("admin", "tech-lead", "developer", "viewer");

        // THEN: Specialized roles are present
        assertThat(roles.stream().map(PersonaRoleDefinition::roleId))
                .as("Should contain specialized roles")
                .contains(
                        "fullstack-developer", "backend-developer", "frontend-developer",
                        "devops-engineer", "qa-engineer", "product-manager",
                        "designer", "data-analyst", "security-engineer", "architect"
                );
    }

    /**
     * Verifies that specific role can be retrieved by ID.
     *
     * GIVEN: Service with registered roles WHEN: getRoleDefinition is called
     * with valid ID THEN: Returns correct role definition
     */
    @Test
    @DisplayName("Should retrieve role by ID")
    void shouldRetrieveRoleById() {
        // WHEN: Get tech-lead role
        var roleOpt = service.getRoleDefinition("tech-lead");

        // THEN: Role is found
        assertThat(roleOpt)
                .as("Should find tech-lead role")
                .isPresent();

        // THEN: Role has correct properties
        PersonaRoleDefinition role = roleOpt.get();
        assertThat(role.roleId()).isEqualTo("tech-lead");
        assertThat(role.displayName()).isEqualTo("Tech Lead");
        assertThat(role.type()).isEqualTo(PersonaRoleDefinition.RoleType.BASE);
    }

    /**
     * Verifies that unknown role ID returns empty.
     *
     * GIVEN: Service with registered roles WHEN: getRoleDefinition is called
     * with unknown ID THEN: Returns empty Optional
     */
    @Test
    @DisplayName("Should return empty for unknown role ID")
    void shouldReturnEmptyForUnknownRoleId() {
        // WHEN: Get unknown role
        var roleOpt = service.getRoleDefinition("unknown-role");

        // THEN: Role is not found
        assertThat(roleOpt)
                .as("Should not find unknown role")
                .isEmpty();
    }

    /**
     * Verifies that roles can be filtered by type.
     *
     * GIVEN: Service with base and specialized roles WHEN: getRolesByType is
     * called THEN: Returns only roles of specified type
     */
    @Test
    @DisplayName("Should filter roles by type")
    void shouldFilterRolesByType() {
        // WHEN: Get base roles
        List<PersonaRoleDefinition> baseRoles = service.getRolesByType(
                PersonaRoleDefinition.RoleType.BASE
        );

        // THEN: Only 4 base roles returned
        assertThat(baseRoles)
                .as("Should have 4 base roles")
                .hasSize(4);
        assertThat(baseRoles.stream().map(PersonaRoleDefinition::roleId))
                .containsExactlyInAnyOrder("admin", "tech-lead", "developer", "viewer");

        // WHEN: Get specialized roles
        List<PersonaRoleDefinition> specializedRoles = service.getRolesByType(
                PersonaRoleDefinition.RoleType.SPECIALIZED
        );

        // THEN: 10 specialized roles returned
        assertThat(specializedRoles)
                .as("Should have 10 specialized roles")
                .hasSize(10);
    }

    /**
     * Verifies that valid role combination is accepted.
     *
     * GIVEN: Valid role combination WHEN: validateRoleActivation is called
     * THEN: Validation succeeds
     */
    @Test
    @DisplayName("Should validate valid role combination")
    void shouldValidateValidRoleCombination() {
        // GIVEN: Valid roles (tech-lead + backend-developer)
        List<String> roleIds = List.of("tech-lead", "backend-developer");

        // WHEN: Validate
        var result = service.validateRoleActivation(roleIds);

        // THEN: Validation succeeds
        assertThat(result.isValid())
                .as("Valid combination should be accepted")
                .isTrue();
        assertThat(result.errorMessage())
                .as("No error message for valid combination")
                .isNull();
    }

    /**
     * Verifies that empty role list is rejected.
     *
     * GIVEN: Empty role list WHEN: validateRoleActivation is called THEN:
     * Validation fails with appropriate error
     */
    @Test
    @DisplayName("Should reject empty role list")
    void shouldRejectEmptyRoleList() {
        // GIVEN: Empty role list
        List<String> roleIds = List.of();

        // WHEN: Validate
        var result = service.validateRoleActivation(roleIds);

        // THEN: Validation fails
        assertThat(result.isValid())
                .as("Empty role list should be rejected")
                .isFalse();
        assertThat(result.errorMessage())
                .as("Error message should mention minimum requirement")
                .contains("At least one role must be activated");
    }

    /**
     * Verifies that null role list is rejected.
     *
     * GIVEN: Null role list WHEN: validateRoleActivation is called THEN:
     * Validation fails with appropriate error
     */
    @Test
    @DisplayName("Should reject null role list")
    void shouldRejectNullRoleList() {
        // GIVEN: Null role list
        List<String> roleIds = null;

        // WHEN: Validate
        var result = service.validateRoleActivation(roleIds);

        // THEN: Validation fails
        assertThat(result.isValid()).isFalse();
        assertThat(result.errorMessage()).contains("At least one role");
    }

    /**
     * Verifies that more than 5 roles is rejected.
     *
     * GIVEN: 6 roles WHEN: validateRoleActivation is called THEN: Validation
     * fails with max limit error
     */
    @Test
    @DisplayName("Should reject more than 5 roles")
    void shouldRejectMoreThanFiveRoles() {
        // GIVEN: 6 roles (exceeds limit)
        List<String> roleIds = List.of(
                "tech-lead", "backend-developer", "frontend-developer",
                "devops-engineer", "qa-engineer", "architect"
        );

        // WHEN: Validate
        var result = service.validateRoleActivation(roleIds);

        // THEN: Validation fails
        assertThat(result.isValid())
                .as("More than 5 roles should be rejected")
                .isFalse();
        assertThat(result.errorMessage())
                .as("Error message should mention maximum limit")
                .contains("Maximum 5 roles");
    }

    /**
     * Verifies that unknown role ID is rejected.
     *
     * GIVEN: Role list with unknown ID WHEN: validateRoleActivation is called
     * THEN: Validation fails with unknown role error
     */
    @Test
    @DisplayName("Should reject unknown role ID")
    void shouldRejectUnknownRoleId() {
        // GIVEN: Role list with unknown ID
        List<String> roleIds = List.of("tech-lead", "unknown-role");

        // WHEN: Validate
        var result = service.validateRoleActivation(roleIds);

        // THEN: Validation fails
        assertThat(result.isValid())
                .as("Unknown role should be rejected")
                .isFalse();
        assertThat(result.errorMessage())
                .as("Error message should mention unknown role")
                .contains("Unknown role: unknown-role");
    }

    /**
     * Verifies that Admin + Viewer combination is rejected.
     *
     * GIVEN: Admin + Viewer roles (incompatible) WHEN: validateRoleActivation
     * is called THEN: Validation fails with incompatibility error
     */
    @Test
    @DisplayName("Should reject incompatible Admin + Viewer combination")
    void shouldRejectIncompatibleAdminViewerCombination() {
        // GIVEN: Admin + Viewer (incompatible)
        List<String> roleIds = List.of("admin", "viewer");

        // WHEN: Validate
        var result = service.validateRoleActivation(roleIds);

        // THEN: Validation fails
        assertThat(result.isValid())
                .as("Admin + Viewer should be incompatible")
                .isFalse();
        assertThat(result.errorMessage())
                .as("Error message should mention incompatibility")
                .contains("incompatible");
    }

    /**
     * Verifies that single role activation is valid.
     *
     * GIVEN: Single role WHEN: validateRoleActivation is called THEN:
     * Validation succeeds
     */
    @Test
    @DisplayName("Should accept single role activation")
    void shouldAcceptSingleRoleActivation() {
        // GIVEN: Single role
        List<String> roleIds = List.of("developer");

        // WHEN: Validate
        var result = service.validateRoleActivation(roleIds);

        // THEN: Validation succeeds
        assertThat(result.isValid()).isTrue();
    }

    /**
     * Verifies that permissions are resolved from single role.
     *
     * GIVEN: Single tech-lead role WHEN: resolveEffectivePermissions is called
     * THEN: Returns tech-lead permissions
     */
    @Test
    @DisplayName("Should resolve permissions from single role")
    void shouldResolvePermissionsFromSingleRole() {
        // GIVEN: Tech-lead role
        List<String> roleIds = List.of("tech-lead");

        // WHEN: Resolve permissions
        var permissions = service.resolveEffectivePermissions(roleIds);

        // THEN: Has tech-lead permissions
        assertThat(permissions.hasPermission("code.approve"))
                .as("Tech lead should have code.approve")
                .isTrue();
        assertThat(permissions.hasPermission("architecture.review"))
                .as("Tech lead should have architecture.review")
                .isTrue();
        assertThat(permissions.hasPermission("deployment.staging"))
                .as("Tech lead should have deployment.staging")
                .isTrue();

        // THEN: Does not have admin permissions
        assertThat(permissions.hasPermission("deployment.production"))
                .as("Tech lead should NOT have deployment.production")
                .isFalse();
    }

    /**
     * Verifies that permissions are union of multiple roles.
     *
     * GIVEN: Multiple roles (tech-lead + backend-developer) WHEN:
     * resolveEffectivePermissions is called THEN: Returns union of all
     * permissions
     */
    @Test
    @DisplayName("Should resolve union of permissions from multiple roles")
    void shouldResolveUnionOfPermissionsFromMultipleRoles() {
        // GIVEN: Tech-lead + backend-developer
        List<String> roleIds = List.of("tech-lead", "backend-developer");

        // WHEN: Resolve permissions
        var permissions = service.resolveEffectivePermissions(roleIds);

        // THEN: Has permissions from tech-lead
        assertThat(permissions.hasPermission("code.approve"))
                .as("Should have code.approve from tech-lead")
                .isTrue();
        assertThat(permissions.hasPermission("architecture.review"))
                .as("Should have architecture.review from tech-lead")
                .isTrue();

        // THEN: Has permissions from backend-developer
        assertThat(permissions.hasPermission("database.read"))
                .as("Should have database.read from backend-developer")
                .isTrue();
        assertThat(permissions.hasPermission("api.test"))
                .as("Should have api.test from backend-developer")
                .isTrue();

        // THEN: Has permissions from parent role (developer)
        assertThat(permissions.hasPermission("code.write"))
                .as("Should have code.write from parent developer role")
                .isTrue();
        assertThat(permissions.hasPermission("code.review"))
                .as("Should have code.review from parent developer role")
                .isTrue();
    }

    /**
     * Verifies that capabilities are resolved correctly.
     *
     * GIVEN: Roles with capabilities WHEN: resolveEffectivePermissions is
     * called THEN: Returns correct capabilities
     */
    @Test
    @DisplayName("Should resolve capabilities from roles")
    void shouldResolveCapabilitiesFromRoles() {
        // GIVEN: Tech-lead role
        List<String> roleIds = List.of("tech-lead");

        // WHEN: Resolve permissions
        var permissions = service.resolveEffectivePermissions(roleIds);

        // THEN: Has tech-lead capabilities
        assertThat(permissions.hasCapability("approveCodeReviews"))
                .as("Should have approveCodeReviews capability")
                .isTrue();
        assertThat(permissions.hasCapability("reviewArchitecture"))
                .as("Should have reviewArchitecture capability")
                .isTrue();
        assertThat(permissions.hasCapability("deployStaging"))
                .as("Should have deployStaging capability")
                .isTrue();

        // THEN: Does not have admin capabilities
        assertThat(permissions.hasCapability("deployProduction"))
                .as("Should NOT have deployProduction capability")
                .isFalse();
    }

    /**
     * Verifies that inherited permissions are resolved correctly.
     *
     * GIVEN: Specialized role with parent role WHEN:
     * resolveEffectivePermissions is called THEN: Returns permissions from role
     * and parent
     */
    @Test
    @DisplayName("Should resolve inherited permissions from parent roles")
    void shouldResolveInheritedPermissionsFromParentRoles() {
        // GIVEN: Backend-developer (inherits from developer)
        List<String> roleIds = List.of("backend-developer");

        // WHEN: Resolve permissions
        var permissions = service.resolveEffectivePermissions(roleIds);

        // THEN: Has own permissions
        assertThat(permissions.hasPermission("database.read"))
                .as("Should have database.read from backend-developer")
                .isTrue();

        // THEN: Has inherited permissions from developer
        assertThat(permissions.hasPermission("code.write"))
                .as("Should inherit code.write from developer")
                .isTrue();
        assertThat(permissions.hasPermission("code.review"))
                .as("Should inherit code.review from developer")
                .isTrue();
        assertThat(permissions.hasPermission("deployment.dev"))
                .as("Should inherit deployment.dev from developer")
                .isTrue();
    }

    /**
     * Verifies that architect inherits from tech-lead.
     *
     * GIVEN: Architect role (inherits from tech-lead) WHEN:
     * resolveEffectivePermissions is called THEN: Returns permissions from
     * architect and tech-lead
     */
    @Test
    @DisplayName("Should resolve architect inheriting from tech-lead")
    void shouldResolveArchitectInheritingFromTechLead() {
        // GIVEN: Architect (inherits from tech-lead)
        List<String> roleIds = List.of("architect");

        // WHEN: Resolve permissions
        var permissions = service.resolveEffectivePermissions(roleIds);

        // THEN: Has own permissions
        assertThat(permissions.hasPermission("architecture.design"))
                .as("Should have architecture.design from architect")
                .isTrue();

        // THEN: Has inherited permissions from tech-lead
        assertThat(permissions.hasPermission("code.approve"))
                .as("Should inherit code.approve from tech-lead")
                .isTrue();
        assertThat(permissions.hasPermission("architecture.review"))
                .as("Should inherit architecture.review from tech-lead")
                .isTrue();
        assertThat(permissions.hasPermission("deployment.staging"))
                .as("Should inherit deployment.staging from tech-lead")
                .isTrue();
    }
}
