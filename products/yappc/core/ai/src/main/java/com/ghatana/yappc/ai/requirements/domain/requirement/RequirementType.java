package com.ghatana.yappc.ai.requirements.domain.requirement;

/**
 * Requirement classification types.
 *
 * <p><b>Purpose</b><br>
 * Categorizes requirements by their nature and role in system design.
 * Different types have different handling and approval workflows.
 *
 * <p><b>Types</b><br>
 * - FUNCTIONAL: What the system must do
 * - NON_FUNCTIONAL: System quality attributes (performance, security, etc.)
 * - CONSTRAINT: Technical or business constraints
 * - ASSUMPTION: Assumptions made about the system
 * - DEPENDENCY: External dependencies
 * - RISK: Identified risks to manage
 *
 * @doc.type enum
 * @doc.purpose Requirement classification
 * @doc.layer product
 * @doc.pattern Value Object
 */
public enum RequirementType {
  /**
 * Functional requirement describing system behavior */
  FUNCTIONAL("Functional Requirement"),
  /**
 * Non-functional requirement (quality, performance, security) */
  NON_FUNCTIONAL("Non-Functional Requirement"),
  /**
 * Technical or business constraint */
  CONSTRAINT("Constraint"),
  /**
 * Assumption about the system */
  ASSUMPTION("Assumption"),
  /**
 * External dependency */
  DEPENDENCY("Dependency"),
  /**
 * Risk to manage */
  RISK("Risk");

  private final String displayName;

  /**
   * Create requirement type.
   *
   * @param displayName human-readable display name
   */
  RequirementType(String displayName) {
    this.displayName = displayName;
  }

  /**
   * Get display name.
   *
   * @return human-readable name
   */
  public String getDisplayName() {
    return displayName;
  }
}