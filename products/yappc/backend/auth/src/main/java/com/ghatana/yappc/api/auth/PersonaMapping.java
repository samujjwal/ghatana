/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.auth;

import com.ghatana.platform.security.rbac.Permission;
import com.ghatana.platform.domain.auth.Role;
import java.util.Set;

/**
 * Maps YAPPC's 21 personas to platform Roles and Permissions.
 *
 * <p><b>Purpose</b><br>
 * Bridges YAPPC's persona system (UI behavior) with platform RBAC (permissions). Defines which
 * permissions each persona has based on their role.
 *
 * <p><b>Persona Categories</b><br>
 * - Execution: developer, tech-lead, devops-engineer, qa-engineer, sre - Governance:
 * security-engineer, compliance-officer, architect - Strategic: product-manager, product-owner,
 * program-manager, business-analyst, engineering-manager, executive - Operations: release-manager,
 * infrastructure-architect, customer-success, support-lead - Administrative: workspace-admin,
 * stakeholder
 *
 * <p><b>Usage</b><br>
 *
 * <pre>{@code
 * PersonaType persona = PersonaType.PRODUCT_MANAGER;
 * Role role = PersonaMapping.getDefaultRole(persona);
 * Set<Permission> permissions = PersonaMapping.getPersonaPermissions(persona);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Persona to Role/Permission mapping
 * @doc.layer api
 * @doc.pattern Strategy, Mapping
 */
public class PersonaMapping {

  /** Persona types from frontend. */
  public enum PersonaType {
    // Execution
    DEVELOPER,
    TECH_LEAD,
    DEVOPS_ENGINEER,
    QA_ENGINEER,
    SRE,
    // Governance
    SECURITY_ENGINEER,
    COMPLIANCE_OFFICER,
    ARCHITECT,
    // Strategic
    PRODUCT_MANAGER,
    PRODUCT_OWNER,
    PROGRAM_MANAGER,
    BUSINESS_ANALYST,
    ENGINEERING_MANAGER,
    EXECUTIVE,
    // Operations
    RELEASE_MANAGER,
    INFRASTRUCTURE_ARCHITECT,
    CUSTOMER_SUCCESS,
    SUPPORT_LEAD,
    // Administrative
    WORKSPACE_ADMIN,
    STAKEHOLDER
  }

  /**
   * Gets the default role for a persona.
   *
   * <p>Maps personas to platform roles: - Workspace Admin, Executive → ADMIN role - Most personas →
   * MEMBER role - Stakeholder → VIEWER role
   *
   * @param persona the persona type
   * @return the default role
   */
  public static Role getDefaultRole(PersonaType persona) {
    return switch (persona) {
      case WORKSPACE_ADMIN -> Role.ADMIN;
      case EXECUTIVE -> Role.ADMIN;
      case STAKEHOLDER -> Role.VIEWER;
      default -> Role.EDITOR; // Use EDITOR instead of MEMBER
    };
  }

  /**
   * Gets permissions for a persona.
   *
   * <p>Defines what each persona can do in the system. Combines role-based permissions with
   * persona-specific needs.
   *
   * @param persona the persona type
   * @return set of permissions
   */
  public static Set<String> getPersonaPermissions(PersonaType persona) {
    return switch (persona) {
        // Administrative - Full access
      case WORKSPACE_ADMIN ->
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
              Permission.USER_MANAGE);

        // Executive - Read all, approve critical
      case EXECUTIVE ->
          Set.of(
              Permission.WORKSPACE_READ,
              Permission.PROJECT_READ,
              Permission.REQUIREMENT_READ,
              Permission.REQUIREMENT_APPROVE);

        // Product/Requirements personas - Full requirement lifecycle
      case PRODUCT_MANAGER, PRODUCT_OWNER ->
          Set.of(
              Permission.PROJECT_READ,
              Permission.PROJECT_UPDATE,
              Permission.REQUIREMENT_CREATE,
              Permission.REQUIREMENT_READ,
              Permission.REQUIREMENT_UPDATE,
              Permission.REQUIREMENT_DELETE,
              Permission.REQUIREMENT_APPROVE,
              Permission.AI_SUGGESTION_REQUEST,
              Permission.AI_SUGGESTION_FEEDBACK);

        // Business Analyst - Requirements quality focus
      case BUSINESS_ANALYST ->
          Set.of(
              Permission.PROJECT_READ,
              Permission.REQUIREMENT_CREATE,
              Permission.REQUIREMENT_READ,
              Permission.REQUIREMENT_UPDATE,
              Permission.AI_SUGGESTION_REQUEST,
              Permission.AI_SUGGESTION_FEEDBACK);

        // Tech Lead, Architect - Technical approval & architecture
      case TECH_LEAD, ARCHITECT ->
          Set.of(
              Permission.PROJECT_READ,
              Permission.PROJECT_UPDATE,
              Permission.REQUIREMENT_READ,
              Permission.REQUIREMENT_UPDATE,
              Permission.REQUIREMENT_APPROVE,
              Permission.AI_SUGGESTION_REQUEST);

        // Developer - Execute requirements
      case DEVELOPER ->
          Set.of(
              Permission.PROJECT_READ,
              Permission.REQUIREMENT_READ,
              Permission.AI_SUGGESTION_REQUEST);

        // QA Engineer - Test & validate
      case QA_ENGINEER ->
          Set.of(
              Permission.PROJECT_READ,
              Permission.REQUIREMENT_READ,
              Permission.AI_SUGGESTION_REQUEST);

        // Security, Compliance - Review & approve security
      case SECURITY_ENGINEER, COMPLIANCE_OFFICER ->
          Set.of(
              Permission.PROJECT_READ,
              Permission.REQUIREMENT_READ,
              Permission.REQUIREMENT_APPROVE,
              Permission.AI_SUGGESTION_REQUEST);

        // DevOps, SRE, Infrastructure - Operational view
      case DEVOPS_ENGINEER, SRE, INFRASTRUCTURE_ARCHITECT ->
          Set.of(Permission.PROJECT_READ, Permission.REQUIREMENT_READ);

        // Manager - Team & delivery view
      case ENGINEERING_MANAGER, PROGRAM_MANAGER, RELEASE_MANAGER ->
          Set.of(
              Permission.PROJECT_READ, Permission.REQUIREMENT_READ, Permission.REQUIREMENT_APPROVE);

        // Support - Read-only for context
      case CUSTOMER_SUCCESS, SUPPORT_LEAD ->
          Set.of(Permission.PROJECT_READ, Permission.REQUIREMENT_READ);

        // Stakeholder - Read-only
      case STAKEHOLDER -> Set.of(Permission.PROJECT_READ, Permission.REQUIREMENT_READ);
    };
  }

  /**
   * Checks if a persona can perform an action.
   *
   * @param persona the persona type
   * @param permission the required permission
   * @return true if persona has permission
   */
  public static boolean hasPermission(PersonaType persona, String permission) {
    return getPersonaPermissions(persona).contains(permission);
  }

  /**
   * Get permissions for a persona by string name (convenience method).
   *
   * @param personaName the persona name as string
   * @return set of permissions
   */
  public static Set<String> getPermissions(String personaName) {
    PersonaType persona = PersonaType.valueOf(personaName);
    return getPersonaPermissions(persona);
  }
}
