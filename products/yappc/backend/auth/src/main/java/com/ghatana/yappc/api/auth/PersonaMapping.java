/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.auth;

import com.ghatana.platform.domain.auth.Role;
import java.util.Set;

/**
 * Maps YAPPC's 21 personas to platform Roles and Permissions.
 *
 * <p><b>Source of truth</b><br>
 * All persona definitions are loaded at startup by {@link PersonaLoader} from
 * {@code personas/personas.yaml}. This class is the stable Java API facade—its
 * implementation delegates entirely to the YAML-driven {@link PersonaLoader}.
 *
 * <p><b>Persona Categories</b><br>
 * - Execution: developer, tech-lead, devops-engineer, qa-engineer, sre
 * - Governance: security-engineer, compliance-officer, architect
 * - Strategic: product-manager, product-owner, program-manager, business-analyst,
 *              engineering-manager, executive
 * - Operations: release-manager, infrastructure-architect, customer-success, support-lead
 * - Administrative: workspace-admin, stakeholder
 *
 * <p><b>Usage</b><br>
 *
 * <pre>{@code
 * PersonaType persona = PersonaType.PRODUCT_MANAGER;
 * Role role = PersonaMapping.getDefaultRole(persona);
 * Set<String> permissions = PersonaMapping.getPersonaPermissions(persona);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Persona to Role/Permission mapping — delegates to PersonaLoader (YAML-backed)
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
   * <p>Delegates to {@link PersonaLoader} which reads the role from {@code personas.yaml}.
   * The fallback chain is: YAML role → {@code Role.EDITOR} if the persona is unknown.
   *
   * @param persona the persona type
   * @return the default role
   */
  public static Role getDefaultRole(PersonaType persona) {
    PersonaDefinition def = PersonaLoader.get(persona.name());
    if (def == null) {
      return Role.EDITOR;
    }
    return switch (def.getRole().toUpperCase()) {
      case "ADMIN"  -> Role.ADMIN;
      case "VIEWER" -> Role.VIEWER;
      default       -> Role.EDITOR;
    };
  }

  /**
   * Gets permissions for a persona.
   *
   * <p>Delegates to {@link PersonaLoader} which reads permissions from {@code personas.yaml}.
   * Returns an empty set if the persona is unknown.
   *
   * @param persona the persona type
   * @return set of permissions
   */
  public static Set<String> getPersonaPermissions(PersonaType persona) {
    PersonaDefinition def = PersonaLoader.get(persona.name());
    if (def == null) {
      return Set.of();
    }
    return Set.copyOf(def.getPermissions());
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
