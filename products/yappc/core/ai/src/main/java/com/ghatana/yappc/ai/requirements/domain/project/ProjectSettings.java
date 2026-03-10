package com.ghatana.yappc.ai.requirements.domain.project;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Project-specific configuration settings.
 *
 * <p><b>Purpose</b><br>
 * Holds all project-level configuration that affects requirement management,
 * approvals, and AI suggestions. Provides sensible defaults and builder pattern.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * ProjectSettings settings = new ProjectSettings(
 *     true,  // Require approval
 *     true,  // Enable AI suggestions
 *     "FUNCTIONAL",
 *     List.of("FUNCTIONAL", "NON_FUNCTIONAL", "CONSTRAINT"),
 *     Map.of()
 * );
 * 
 * // Or use defaults
 * ProjectSettings defaults = ProjectSettings.defaults();
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Immutable record - safe for concurrent use
 *
 * @doc.type record
 * @doc.purpose Project configuration holder
 * @doc.layer product
 * @doc.pattern Value Object, Configuration
 */
public record ProjectSettings(
    /**
     * Whether requirement changes require workflow approval.
     */
    boolean requireApprovalForRequirements,

    /**
     * Whether AI-powered suggestions are enabled for this project.
     */
    boolean enableAISuggestions,

    /**
     * Default requirement type when creating new requirements.
     */
    String defaultRequirementType,

    /**
     * List of allowed requirement types for this project.
     */
    List<String> allowedRequirementTypes,

    /**
     * Custom fields for project-specific configuration.
     */
    Map<String, Object> customFields) {

  /**
   * Create ProjectSettings with defensive copying for collections.
   *
   * @param requireApprovalForRequirements approval requirement flag
   * @param enableAISuggestions AI suggestions enabled flag
   * @param defaultRequirementType default requirement type
   * @param allowedRequirementTypes allowed types list
   * @param customFields custom configuration map
   */
  public ProjectSettings {
    // Defensive copy for immutability
    allowedRequirementTypes = List.copyOf(allowedRequirementTypes);
    customFields = Map.copyOf(customFields);
  }

  /**
   * Create default project settings with reasonable defaults.
   *
   * <p>Defaults:<br>
   * - No approval required<br>
   * - AI suggestions enabled<br>
   * - Default type: FUNCTIONAL<br>
   * - Allowed types: FUNCTIONAL, NON_FUNCTIONAL, CONSTRAINT
   *
   * @return default ProjectSettings instance
   */
  public static ProjectSettings defaults() {
    return new ProjectSettings(
        false, // no approval by default
        true, // AI suggestions enabled
        "FUNCTIONAL",
        List.of("FUNCTIONAL", "NON_FUNCTIONAL", "CONSTRAINT"),
        Map.of());
  }

  /**
   * Create builder for fluent ProjectSettings construction.
   *
   * @return new ProjectSettingsBuilder
   */
  public static ProjectSettingsBuilder builder() {
    return new ProjectSettingsBuilder();
  }

  /**
   * Builder for ProjectSettings with method chaining.
   */
  public static class ProjectSettingsBuilder {
    private boolean requireApprovalForRequirements = false;
    private boolean enableAISuggestions = true;
    private String defaultRequirementType = "FUNCTIONAL";
    private List<String> allowedRequirementTypes =
        List.of("FUNCTIONAL", "NON_FUNCTIONAL", "CONSTRAINT");
    private Map<String, Object> customFields = new HashMap<>();

    /**
     * Set whether approval is required.
     *
     * @param require whether approval required
     * @return this builder
     */
    public ProjectSettingsBuilder requireApprovalForRequirements(boolean require) {
      this.requireApprovalForRequirements = require;
      return this;
    }

    /**
     * Set whether AI suggestions are enabled.
     *
     * @param enable whether AI suggestions enabled
     * @return this builder
     */
    public ProjectSettingsBuilder enableAISuggestions(boolean enable) {
      this.enableAISuggestions = enable;
      return this;
    }

    /**
     * Set default requirement type.
     *
     * @param type default type
     * @return this builder
     */
    public ProjectSettingsBuilder defaultRequirementType(String type) {
      this.defaultRequirementType = type;
      return this;
    }

    /**
     * Set allowed requirement types.
     *
     * @param types list of allowed types
     * @return this builder
     */
    public ProjectSettingsBuilder allowedRequirementTypes(List<String> types) {
      this.allowedRequirementTypes = types;
      return this;
    }

    /**
     * Set custom field.
     *
     * @param key field key
     * @param value field value
     * @return this builder
     */
    public ProjectSettingsBuilder customField(String key, Object value) {
      this.customFields.put(key, value);
      return this;
    }

    /**
     * Build ProjectSettings instance.
     *
     * @return constructed ProjectSettings
     */
    public ProjectSettings build() {
      return new ProjectSettings(
          requireApprovalForRequirements,
          enableAISuggestions,
          defaultRequirementType,
          allowedRequirementTypes,
          customFields);
    }
  }

  /**
   * Check if a requirement type is allowed in this project.
   *
   * @param type requirement type to check
   * @return true if type is allowed
   */
  public boolean isAllowedRequirementType(String type) {
    return allowedRequirementTypes.contains(type);
  }

  /**
   * Get custom field value.
   *
   * @param key field key
   * @return field value or null if not found
   */
  public Object getCustomField(String key) {
    return customFields.get(key);
  }

  /**
   * Get unmodifiable view of custom fields.
   *
   * @return unmodifiable map of custom fields
   */
  public Map<String, Object> getCustomFieldsView() {
    return Collections.unmodifiableMap(customFields);
  }
}