package com.ghatana.softwareorg.domain.persona;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for PersonaRoleDefinition.
 *
 * Tests validate: - Role creation with valid data - Role validation rules -
 * Permission and capability checks - Role inheritance checks - Immutability of
 * role definitions
 *
 * @see PersonaRoleDefinition
 */
@DisplayName("Persona Role Definition Tests")
class PersonaRoleDefinitionTest {

    /**
     * Verifies that role is created with valid data.
     *
     * GIVEN: Valid role parameters WHEN: PersonaRoleDefinition is constructed
     * THEN: Role is created with correct properties
     */
    @Test
    @DisplayName("Should create role with valid data")
    void shouldCreateRoleWithValidData() {
        // GIVEN: Valid role parameters
        String roleId = "tech-lead";
        String displayName = "Tech Lead";
        String description = "Technical leadership role";
        PersonaRoleDefinition.RoleType type = PersonaRoleDefinition.RoleType.BASE;
        Set<String> permissions = Set.of("code.approve", "architecture.review");
        Set<String> capabilities = Set.of("approveCodeReviews", "reviewArchitecture");
        Set<String> parentRoles = Set.of();

        // WHEN: Create role
        PersonaRoleDefinition role = new PersonaRoleDefinition(
                roleId, displayName, description, type,
                permissions, capabilities, parentRoles
        );

        // THEN: Role properties are set correctly
        assertThat(role.roleId())
                .as("Role ID should match input")
                .isEqualTo("tech-lead");
        assertThat(role.displayName())
                .as("Display name should match input")
                .isEqualTo("Tech Lead");
        assertThat(role.description())
                .as("Description should match input")
                .isEqualTo("Technical leadership role");
        assertThat(role.type())
                .as("Type should be BASE")
                .isEqualTo(PersonaRoleDefinition.RoleType.BASE);
        assertThat(role.permissions())
                .as("Permissions should contain expected values")
                .containsExactlyInAnyOrder("code.approve", "architecture.review");
        assertThat(role.capabilities())
                .as("Capabilities should contain expected values")
                .containsExactlyInAnyOrder("approveCodeReviews", "reviewArchitecture");
    }

    /**
     * Verifies that null roleId is rejected.
     *
     * GIVEN: Null roleId WHEN: PersonaRoleDefinition is constructed THEN:
     * IllegalArgumentException is thrown
     */
    @Test
    @DisplayName("Should reject null role ID")
    void shouldRejectNullRoleId() {
        // GIVEN: Null roleId
        // WHEN/THEN: Constructor throws IllegalArgumentException
        assertThatThrownBy(() -> new PersonaRoleDefinition(
                null, "Tech Lead", "Description",
                PersonaRoleDefinition.RoleType.BASE,
                Set.of(), Set.of(), Set.of()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("roleId cannot be null");
    }

    /**
     * Verifies that blank roleId is rejected.
     *
     * GIVEN: Blank roleId WHEN: PersonaRoleDefinition is constructed THEN:
     * IllegalArgumentException is thrown
     */
    @Test
    @DisplayName("Should reject blank role ID")
    void shouldRejectBlankRoleId() {
        // GIVEN: Blank roleId
        // WHEN/THEN: Constructor throws IllegalArgumentException
        assertThatThrownBy(() -> new PersonaRoleDefinition(
                "   ", "Tech Lead", "Description",
                PersonaRoleDefinition.RoleType.BASE,
                Set.of(), Set.of(), Set.of()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("roleId cannot be null or blank");
    }

    /**
     * Verifies that null displayName is rejected.
     *
     * GIVEN: Null displayName WHEN: PersonaRoleDefinition is constructed THEN:
     * IllegalArgumentException is thrown
     */
    @Test
    @DisplayName("Should reject null display name")
    void shouldRejectNullDisplayName() {
        // GIVEN: Null displayName
        // WHEN/THEN: Constructor throws IllegalArgumentException
        assertThatThrownBy(() -> new PersonaRoleDefinition(
                "tech-lead", null, "Description",
                PersonaRoleDefinition.RoleType.BASE,
                Set.of(), Set.of(), Set.of()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("displayName cannot be null");
    }

    /**
     * Verifies that null type is rejected.
     *
     * GIVEN: Null type WHEN: PersonaRoleDefinition is constructed THEN:
     * IllegalArgumentException is thrown
     */
    @Test
    @DisplayName("Should reject null type")
    void shouldRejectNullType() {
        // GIVEN: Null type
        // WHEN/THEN: Constructor throws IllegalArgumentException
        assertThatThrownBy(() -> new PersonaRoleDefinition(
                "tech-lead", "Tech Lead", "Description",
                null,
                Set.of(), Set.of(), Set.of()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type cannot be null");
    }

    /**
     * Verifies that hasPermission correctly checks permissions.
     *
     * GIVEN: Role with specific permissions WHEN: hasPermission is called THEN:
     * Returns true for granted permissions, false otherwise
     */
    @Test
    @DisplayName("Should correctly check permissions")
    void shouldCorrectlyCheckPermissions() {
        // GIVEN: Role with code.approve permission
        PersonaRoleDefinition role = new PersonaRoleDefinition(
                "tech-lead", "Tech Lead", "Description",
                PersonaRoleDefinition.RoleType.BASE,
                Set.of("code.approve", "architecture.review"),
                Set.of(), Set.of()
        );

        // WHEN/THEN: hasPermission returns correct values
        assertThat(role.hasPermission("code.approve"))
                .as("Should have code.approve permission")
                .isTrue();
        assertThat(role.hasPermission("deployment.production"))
                .as("Should not have deployment.production permission")
                .isFalse();
    }

    /**
     * Verifies that hasCapability correctly checks capabilities.
     *
     * GIVEN: Role with specific capabilities WHEN: hasCapability is called
     * THEN: Returns true for granted capabilities, false otherwise
     */
    @Test
    @DisplayName("Should correctly check capabilities")
    void shouldCorrectlyCheckCapabilities() {
        // GIVEN: Role with approveCodeReviews capability
        PersonaRoleDefinition role = new PersonaRoleDefinition(
                "tech-lead", "Tech Lead", "Description",
                PersonaRoleDefinition.RoleType.BASE,
                Set.of(),
                Set.of("approveCodeReviews", "reviewArchitecture"),
                Set.of()
        );

        // WHEN/THEN: hasCapability returns correct values
        assertThat(role.hasCapability("approveCodeReviews"))
                .as("Should have approveCodeReviews capability")
                .isTrue();
        assertThat(role.hasCapability("deployProduction"))
                .as("Should not have deployProduction capability")
                .isFalse();
    }

    /**
     * Verifies that inheritsFrom correctly checks parent roles.
     *
     * GIVEN: Role with parent roles WHEN: inheritsFrom is called THEN: Returns
     * true for parent roles, false otherwise
     */
    @Test
    @DisplayName("Should correctly check parent roles")
    void shouldCorrectlyCheckParentRoles() {
        // GIVEN: Role inheriting from developer
        PersonaRoleDefinition role = new PersonaRoleDefinition(
                "backend-developer", "Backend Developer", "Description",
                PersonaRoleDefinition.RoleType.SPECIALIZED,
                Set.of(), Set.of(),
                Set.of("developer")
        );

        // WHEN/THEN: inheritsFrom returns correct values
        assertThat(role.inheritsFrom("developer"))
                .as("Should inherit from developer")
                .isTrue();
        assertThat(role.inheritsFrom("admin"))
                .as("Should not inherit from admin")
                .isFalse();
    }

    /**
     * Verifies that Admin role is created correctly.
     *
     * GIVEN: createAdmin factory method WHEN: Admin role is created THEN: Role
     * has correct properties and permissions
     */
    @Test
    @DisplayName("Should create Admin role with all permissions")
    void shouldCreateAdminRoleWithAllPermissions() {
        // GIVEN/WHEN: Create Admin role
        PersonaRoleDefinition admin = PersonaRoleDefinition.createAdmin();

        // THEN: Admin has correct properties
        assertThat(admin.roleId()).isEqualTo("admin");
        assertThat(admin.displayName()).isEqualTo("Administrator");
        assertThat(admin.type()).isEqualTo(PersonaRoleDefinition.RoleType.BASE);

        // THEN: Admin has all critical permissions
        assertThat(admin.hasPermission("workspace.manage")).isTrue();
        assertThat(admin.hasPermission("team.manage")).isTrue();
        assertThat(admin.hasPermission("code.approve")).isTrue();
        assertThat(admin.hasPermission("deployment.production")).isTrue();

        // THEN: Admin has all critical capabilities
        assertThat(admin.hasCapability("viewAllProjects")).isTrue();
        assertThat(admin.hasCapability("deployProduction")).isTrue();
        assertThat(admin.hasCapability("manageTeam")).isTrue();
    }

    /**
     * Verifies that TechLead role is created correctly.
     *
     * GIVEN: createTechLead factory method WHEN: TechLead role is created THEN:
     * Role has correct properties and permissions
     */
    @Test
    @DisplayName("Should create TechLead role with leadership permissions")
    void shouldCreateTechLeadRoleWithLeadershipPermissions() {
        // GIVEN/WHEN: Create TechLead role
        PersonaRoleDefinition techLead = PersonaRoleDefinition.createTechLead();

        // THEN: TechLead has correct properties
        assertThat(techLead.roleId()).isEqualTo("tech-lead");
        assertThat(techLead.displayName()).isEqualTo("Tech Lead");
        assertThat(techLead.type()).isEqualTo(PersonaRoleDefinition.RoleType.BASE);

        // THEN: TechLead has leadership permissions
        assertThat(techLead.hasPermission("code.approve")).isTrue();
        assertThat(techLead.hasPermission("architecture.review")).isTrue();
        assertThat(techLead.hasPermission("deployment.staging")).isTrue();

        // THEN: TechLead does NOT have production deployment
        assertThat(techLead.hasPermission("deployment.production")).isFalse();
    }

    /**
     * Verifies that role sets are immutable.
     *
     * GIVEN: Role created with mutable sets WHEN: Attempting to modify returned
     * sets THEN: UnsupportedOperationException is thrown
     */
    @Test
    @DisplayName("Should enforce immutability of permission sets")
    void shouldEnforceImmutabilityOfPermissionSets() {
        // GIVEN: Role with permissions
        PersonaRoleDefinition role = PersonaRoleDefinition.createDeveloper();

        // WHEN/THEN: Attempting to modify permissions throws exception
        assertThatThrownBy(() -> role.permissions().add("new.permission"))
                .isInstanceOf(UnsupportedOperationException.class);

        // WHEN/THEN: Attempting to modify capabilities throws exception
        assertThatThrownBy(() -> role.capabilities().add("newCapability"))
                .isInstanceOf(UnsupportedOperationException.class);

        // WHEN/THEN: Attempting to modify parent roles throws exception
        assertThatThrownBy(() -> role.parentRoles().add("newParent"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
