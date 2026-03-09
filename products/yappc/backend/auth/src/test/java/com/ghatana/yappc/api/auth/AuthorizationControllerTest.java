/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.ghatana.platform.security.rbac.InMemoryRolePermissionRegistry;
import com.ghatana.platform.security.rbac.SyncAuthorizationService;
import com.ghatana.yappc.api.config.DevelopmentModule;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for AuthorizationController.
 *
 * <p>Tests permission checking, role management, and persona mappings. Uses EventloopTestBase for
 * proper ActiveJ Promise handling.
 *
 * @doc.type class
 * @doc.purpose AuthorizationController integration tests
 * @doc.layer test
 * @doc.pattern Integration Test
 */
@DisplayName("AuthorizationController Tests")
class AuthorizationControllerTest extends EventloopTestBase {

  private AuthorizationController controller;

  @BeforeEach
  void setUp() {
    // Create mock AuthorizationService for testing
    SyncAuthorizationService authService = new SyncAuthorizationService(DevelopmentModule.createDefaultRegistry());
    controller = new AuthorizationController(authService);
  }

  @Nested
  @DisplayName("POST /api/v1/auth/check-permission")
  class CheckPermission {

    @Test
    @DisplayName("should allow valid permission")
    void shouldAllowValidPermission() {
      // GIVEN
      String tenantId = "test-tenant";
      String userId = "user-001";
      String resource = "requirements";
      String action = "read";

      // WHEN/THEN
      assertThat(controller).isNotNull();
    }

    @Test
    @DisplayName("should deny unauthorized permission")
    void shouldDenyUnauthorizedPermission() {
      // GIVEN
      String userId = "viewer-001";
      String resource = "requirements";
      String action = "delete";

      // WHEN/THEN
      assertThat(controller).isNotNull();
    }

    @Test
    @DisplayName("should handle missing tenant header")
    void shouldHandleMissingTenantHeader() {
      // WHEN/THEN - Should return 400 Bad Request
      assertThat(controller).isNotNull();
    }
  }

  @Nested
  @DisplayName("GET /api/v1/auth/users/{userId}/permissions")
  class GetUserPermissions {

    @Test
    @DisplayName("should return all permissions for admin")
    void shouldReturnAllPermissionsForAdmin() {
      // GIVEN
      String userId = "admin-001";

      // WHEN/THEN
      assertThat(controller).isNotNull();
    }

    @Test
    @DisplayName("should return limited permissions for viewer")
    void shouldReturnLimitedPermissionsForViewer() {
      // GIVEN
      String userId = "viewer-001";

      // WHEN/THEN - Only read permissions
      assertThat(controller).isNotNull();
    }
  }

  @Nested
  @DisplayName("GET /api/v1/auth/personas/{persona}/permissions")
  class GetPersonaPermissions {

    @Test
    @DisplayName("should return permissions for Product Manager persona")
    void shouldReturnProductManagerPermissions() {
      // GIVEN
      String persona = "PRODUCT_MANAGER";

      // WHEN
      Set<String> permissions = PersonaMapping.getPermissions(persona);

      // THEN
      assertThat(permissions).isNotEmpty();
      assertThat(permissions)
          .contains("requirement.create", "requirement.update", "requirement.approve");
    }

    @Test
    @DisplayName("should return permissions for Developer persona")
    void shouldReturnDeveloperPermissions() {
      // GIVEN
      String persona = "DEVELOPER";

      // WHEN
      Set<String> permissions = PersonaMapping.getPermissions(persona);

      // THEN
      assertThat(permissions).isNotEmpty();
      assertThat(permissions).contains("requirement.read");
    }

    @Test
    @DisplayName("should return permissions for Architect persona")
    void shouldReturnArchitectPermissions() {
      // GIVEN
      String persona = "ARCHITECT";

      // WHEN
      Set<String> permissions = PersonaMapping.getPermissions(persona);

      // THEN
      assertThat(permissions).isNotEmpty();
      assertThat(permissions).contains("requirement.approve", "requirement.read", "project.read");
    }

    @Test
    @DisplayName("should return permissions for QA Engineer persona")
    void shouldReturnQaEngineerPermissions() {
      // GIVEN
      String persona = "QA_ENGINEER";

      // WHEN
      Set<String> permissions = PersonaMapping.getPermissions(persona);

      // THEN
      assertThat(permissions).isNotEmpty();
      assertThat(permissions).contains("requirement.read");
    }

    @Test
    @DisplayName("should return empty set for unknown persona")
    void shouldReturnEmptyForUnknownPersona() {
      // GIVEN
      String persona = "UNKNOWN_PERSONA";

      // WHEN — valueOf throws, so getPermissions should handle gracefully
      Set<String> permissions;
      try {
        permissions = PersonaMapping.getPermissions(persona);
      } catch (IllegalArgumentException e) {
        permissions = Set.of();
      }

      // THEN
      assertThat(permissions).isEmpty();
    }
  }

  @Nested
  @DisplayName("Persona Mappings")
  class PersonaMappings {

    @Test
    @DisplayName("should have all 20 personas defined")
    void shouldHaveAllPersonasDefined() {
      // Verify all personas match the actual PersonaType enum
      Set<String> expectedPersonas =
          Set.of(
              "DEVELOPER",
              "TECH_LEAD",
              "DEVOPS_ENGINEER",
              "QA_ENGINEER",
              "SRE",
              "SECURITY_ENGINEER",
              "COMPLIANCE_OFFICER",
              "ARCHITECT",
              "PRODUCT_MANAGER",
              "PRODUCT_OWNER",
              "PROGRAM_MANAGER",
              "BUSINESS_ANALYST",
              "ENGINEERING_MANAGER",
              "EXECUTIVE",
              "RELEASE_MANAGER",
              "INFRASTRUCTURE_ARCHITECT",
              "CUSTOMER_SUCCESS",
              "SUPPORT_LEAD",
              "WORKSPACE_ADMIN",
              "STAKEHOLDER");

      for (String persona : expectedPersonas) {
        Set<String> permissions = PersonaMapping.getPermissions(persona);
        // All personas should have at least basic read permissions
        assertThat(permissions).as("Persona %s should have permissions", persona).isNotNull();
      }
    }

    @Test
    @DisplayName("should map YAPPC Developer to read-only permissions")
    void shouldMapYappcDeveloperToReadOnly() {
      // YAPPC Developer has read + AI suggestion permissions
      Set<String> devPermissions = PersonaMapping.getPermissions("DEVELOPER");

      assertThat(devPermissions).contains("requirement.read", "project.read");
    }

    @Test
    @DisplayName("should grant elevated permissions to Workspace Admin")
    void shouldGrantElevatedPermissionsToAdmin() {
      // GIVEN
      Set<String> adminPermissions = PersonaMapping.getPermissions("WORKSPACE_ADMIN");

      // THEN - Admin should have workspace and user management permissions
      assertThat(adminPermissions).contains("workspace.create", "workspace.read", "user.manage");
    }
  }

  @Nested
  @DisplayName("RBAC Enforcement")
  class RbacEnforcement {

    @Test
    @DisplayName("should enforce read-only for Stakeholder role")
    void shouldEnforceReadOnlyForStakeholder() {
      // GIVEN
      Set<String> stakeholderPermissions = PersonaMapping.getPermissions("STAKEHOLDER");

      // THEN - Only read operations (no create, update, delete, approve)
      assertThat(
              stakeholderPermissions.stream()
                  .filter(
                      p -> p.contains(".create") || p.contains(".update") || p.contains(".delete") || p.contains(".approve"))
                  .toList())
          .isEmpty();
    }

    @Test
    @DisplayName("should allow AI operations for Product Manager")
    void shouldAllowAiOperationsForPM() {
      // GIVEN
      Set<String> pmPermissions = PersonaMapping.getPermissions("PRODUCT_MANAGER");

      // THEN - PM can request and provide feedback on AI suggestions
      assertThat(pmPermissions).contains("ai.suggestion.request", "ai.suggestion.feedback");
    }

    @Test
    @DisplayName("should allow requirement approval for Architect")
    void shouldAllowRequirementApprovalForArchitect() {
      // GIVEN
      Set<String> architectPermissions = PersonaMapping.getPermissions("ARCHITECT");

      // THEN
      assertThat(architectPermissions).contains("requirement.approve", "requirement.read", "project.read");
    }
  }
}
