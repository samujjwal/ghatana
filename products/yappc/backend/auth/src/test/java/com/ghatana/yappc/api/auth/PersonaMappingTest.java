/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.ghatana.platform.security.rbac.Permission;
import com.ghatana.platform.domain.auth.Role;
import com.ghatana.yappc.api.auth.PersonaMapping.PersonaType;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Tests for PersonaMapping.
 *
 * <p>Validates that all 21 personas map correctly to roles and permissions. Ensures consistency and
 * completeness of permission sets.
 *
 * @doc.type class
 * @doc.purpose Unit tests for persona mapping
 * @doc.layer api
 * @doc.pattern Test
 */
@DisplayName("PersonaMapping Tests")
class PersonaMappingTest {

  @Test
  @DisplayName("Should map workspace admin to ADMIN role")
  void shouldMapWorkspaceAdminToAdminRole() {
    // WHEN
    Role role = PersonaMapping.getDefaultRole(PersonaType.WORKSPACE_ADMIN);

    // THEN
    assertThat(role).isEqualTo(Role.ADMIN);
  }

  @Test
  @DisplayName("Should map executive to ADMIN role")
  void shouldMapExecutiveToAdminRole() {
    // WHEN
    Role role = PersonaMapping.getDefaultRole(PersonaType.EXECUTIVE);

    // THEN
    assertThat(role).isEqualTo(Role.ADMIN);
  }

  @Test
  @DisplayName("Should map stakeholder to VIEWER role")
  void shouldMapStakeholderToViewerRole() {
    // WHEN
    Role role = PersonaMapping.getDefaultRole(PersonaType.STAKEHOLDER);

    // THEN
    assertThat(role).isEqualTo(Role.VIEWER);
  }

  @Test
  @DisplayName("Should map most personas to EDITOR role")
  void shouldMapMostPersonasToEditorRole() {
    // GIVEN
    PersonaType[] editorPersonas = {
      PersonaType.DEVELOPER,
      PersonaType.TECH_LEAD,
      PersonaType.PRODUCT_MANAGER,
      PersonaType.ARCHITECT
    };

    // WHEN / THEN
    for (PersonaType persona : editorPersonas) {
      Role role = PersonaMapping.getDefaultRole(persona);
      assertThat(role).as("Persona %s should map to EDITOR role", persona).isEqualTo(Role.EDITOR);
    }
  }

  @Test
  @DisplayName("Should grant workspace admin full permissions")
  void shouldGrantWorkspaceAdminFullPermissions() {
    // WHEN
    Set<String> permissions = PersonaMapping.getPersonaPermissions(PersonaType.WORKSPACE_ADMIN);

    // THEN
    assertThat(permissions)
        .containsAll(
            Set.of(
                Permission.WORKSPACE_CREATE,
                Permission.WORKSPACE_READ,
                Permission.WORKSPACE_UPDATE,
                Permission.WORKSPACE_MANAGE_MEMBERS,
                Permission.PROJECT_CREATE,
                Permission.PROJECT_READ,
                Permission.PROJECT_UPDATE,
                Permission.REQUIREMENT_CREATE,
                Permission.REQUIREMENT_READ,
                Permission.REQUIREMENT_UPDATE,
                Permission.REQUIREMENT_APPROVE,
                Permission.ROLE_ASSIGN,
                Permission.USER_MANAGE));
  }

  @Test
  @DisplayName("Should grant product manager requirement lifecycle permissions")
  void shouldGrantProductManagerRequirementPermissions() {
    // WHEN
    Set<String> permissions = PersonaMapping.getPersonaPermissions(PersonaType.PRODUCT_MANAGER);

    // THEN
    assertThat(permissions)
        .contains(
            Permission.REQUIREMENT_CREATE,
            Permission.REQUIREMENT_READ,
            Permission.REQUIREMENT_UPDATE,
            Permission.REQUIREMENT_DELETE,
            Permission.REQUIREMENT_APPROVE);
  }

  @Test
  @DisplayName("Should grant developer read-only requirement permissions")
  void shouldGrantDeveloperReadOnlyRequirementPermissions() {
    // WHEN
    Set<String> permissions = PersonaMapping.getPersonaPermissions(PersonaType.DEVELOPER);

    // THEN
    assertThat(permissions)
        .contains(Permission.REQUIREMENT_READ)
        .doesNotContain(
            Permission.REQUIREMENT_CREATE,
            Permission.REQUIREMENT_UPDATE,
            Permission.REQUIREMENT_DELETE,
            Permission.REQUIREMENT_APPROVE);
  }

  @Test
  @DisplayName("Should grant tech lead approval permissions")
  void shouldGrantTechLeadApprovalPermissions() {
    // WHEN
    Set<String> permissions = PersonaMapping.getPersonaPermissions(PersonaType.TECH_LEAD);

    // THEN
    assertThat(permissions)
        .contains(
            Permission.REQUIREMENT_APPROVE,
            Permission.REQUIREMENT_UPDATE,
            Permission.REQUIREMENT_READ);
  }

  @Test
  @DisplayName("Should restrict stakeholder to read-only")
  void shouldRestrictStakeholderToReadOnly() {
    // WHEN
    Set<String> permissions = PersonaMapping.getPersonaPermissions(PersonaType.STAKEHOLDER);

    // THEN
    assertThat(permissions)
        .containsOnly(Permission.PROJECT_READ, Permission.REQUIREMENT_READ)
        .doesNotContain(
            Permission.REQUIREMENT_CREATE,
            Permission.REQUIREMENT_UPDATE,
            Permission.REQUIREMENT_DELETE,
            Permission.REQUIREMENT_APPROVE);
  }

  @Test
  @DisplayName("Should allow security engineer to approve requirements")
  void shouldAllowSecurityEngineerApproval() {
    // WHEN
    Set<String> permissions =
        PersonaMapping.getPersonaPermissions(PersonaType.SECURITY_ENGINEER);

    // THEN
    assertThat(permissions)
        .contains(
            Permission.REQUIREMENT_APPROVE, Permission.REQUIREMENT_READ, Permission.PROJECT_READ);
  }

  @Test
  @DisplayName("Should grant AI permissions to appropriate personas")
  void shouldGrantAiPermissionsToAppropriatePersonas() {
    // GIVEN
    PersonaType[] aiEnabledPersonas = {
      PersonaType.PRODUCT_MANAGER,
      PersonaType.BUSINESS_ANALYST,
      PersonaType.TECH_LEAD,
      PersonaType.DEVELOPER
    };

    // WHEN / THEN
    for (PersonaType persona : aiEnabledPersonas) {
      Set<String> permissions = PersonaMapping.getPersonaPermissions(persona);
      assertThat(permissions)
          .as("Persona %s should have AI_SUGGESTION_REQUEST", persona)
          .contains(Permission.AI_SUGGESTION_REQUEST);
    }
  }

  @ParameterizedTest
  @EnumSource(PersonaType.class)
  @DisplayName("Should return non-empty permission set for all personas")
  void shouldReturnNonEmptyPermissionSetForAllPersonas(PersonaType persona) {
    // WHEN
    Set<String> permissions = PersonaMapping.getPersonaPermissions(persona);

    // THEN
    assertThat(permissions)
        .as("Persona %s should have at least one permission", persona)
        .isNotEmpty();
  }

  @ParameterizedTest
  @EnumSource(PersonaType.class)
  @DisplayName("Should correctly check hasPermission for all personas")
  void shouldCorrectlyCheckHasPermissionForAllPersonas(PersonaType persona) {
    // GIVEN
    Set<String> permissions = PersonaMapping.getPersonaPermissions(persona);

    // WHEN / THEN
    for (String permission : permissions) {
      boolean hasPermission = PersonaMapping.hasPermission(persona, permission);
      assertThat(hasPermission)
          .as("Persona %s should have permission %s", persona, permission)
          .isTrue();
    }
  }

  @Test
  @DisplayName("Should return false for permission not granted to persona")
  void shouldReturnFalseForPermissionNotGrantedToPersona() {
    // WHEN
    boolean hasPermission =
        PersonaMapping.hasPermission(PersonaType.DEVELOPER, Permission.REQUIREMENT_APPROVE);

    // THEN
    assertThat(hasPermission).isFalse();
  }

  @Test
  @DisplayName("Should ensure governance personas have approval rights")
  void shouldEnsureGovernancePersonasHaveApprovalRights() {
    // GIVEN
    PersonaType[] governancePersonas = {
      PersonaType.ARCHITECT, PersonaType.SECURITY_ENGINEER, PersonaType.COMPLIANCE_OFFICER
    };

    // WHEN / THEN
    for (PersonaType persona : governancePersonas) {
      Set<String> permissions = PersonaMapping.getPersonaPermissions(persona);
      assertThat(permissions)
          .as("Governance persona %s should have approval rights", persona)
          .contains(Permission.REQUIREMENT_APPROVE);
    }
  }

  @Test
  @DisplayName("Should ensure operational personas have read access")
  void shouldEnsureOperationalPersonasHaveReadAccess() {
    // GIVEN
    PersonaType[] operationalPersonas = {
      PersonaType.DEVOPS_ENGINEER, PersonaType.SRE, PersonaType.INFRASTRUCTURE_ARCHITECT
    };

    // WHEN / THEN
    for (PersonaType persona : operationalPersonas) {
      Set<String> permissions = PersonaMapping.getPersonaPermissions(persona);
      assertThat(permissions)
          .as("Operational persona %s should have read access", persona)
          .contains(Permission.PROJECT_READ, Permission.REQUIREMENT_READ);
    }
  }
}
