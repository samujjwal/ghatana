package com.ghatana.yappc.ai.requirements.domain.project;

import java.util.List;

/**
 * Project template for quick project creation.
 *
 * <p><b>Purpose</b><br>
 * Provides pre-configured project templates with default settings and
 * requirement templates to accelerate project setup. Reduces initial configuration.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * ProjectTemplate template = ProjectTemplate.webApplication();
 * Project project = Project.builder()
 *     .withTemplate(template)
 *     .build();
 * }</pre>
 *
 * <p><b>Built-in Templates</b><br>
 * - webApplication(): Standard web application with auth, storage, performance
 * - mobileApplication(): Mobile app with offline sync, notifications
 * - apiService(): Backend API service with caching, security
 *
 * <p><b>Thread Safety</b><br>
 * Immutable record - safe for concurrent use
 *
 * @doc.type record
 * @doc.purpose Project template container
 * @doc.layer product
 * @doc.pattern Value Object, Factory
 */
public record ProjectTemplate(
    /**
     * Unique template identifier.
     */
    String templateId,

    /**
     * Human-readable template name.
     */
    String name,

    /**
     * Template description.
     */
    String description,

    /**
     * Default project settings from template.
     */
    ProjectSettings defaultSettings,

    /**
     * Default requirement templates included in this project.
     */
    List<RequirementTemplate> requirementTemplates) {

  /**
   * Create ProjectTemplate with defensive copying.
   *
   * @param templateId template identifier
   * @param name template name
   * @param description template description
   * @param defaultSettings default settings
   * @param requirementTemplates requirement templates
   */
  public ProjectTemplate {
    // Defensive copy
    requirementTemplates = List.copyOf(requirementTemplates);
  }

  /**
   * Create web application project template.
   *
   * <p>Includes common requirements for web applications:
   * - User Authentication
   * - Data Storage
   * - Performance
   *
   * @return web application template
   */
  public static ProjectTemplate webApplication() {
    return new ProjectTemplate(
        "web-app",
        "Web Application",
        "Standard web application project with authentication and data storage",
        ProjectSettings.defaults(),
        List.of(
            new RequirementTemplate("User Authentication", "FUNCTIONAL"),
            new RequirementTemplate("Data Storage and Retrieval", "NON_FUNCTIONAL"),
            new RequirementTemplate("API Performance", "NON_FUNCTIONAL"),
            new RequirementTemplate("Security and Authorization", "CONSTRAINT")));
  }

  /**
   * Create mobile application project template.
   *
   * @return mobile application template
   */
  public static ProjectTemplate mobileApplication() {
    return new ProjectTemplate(
        "mobile-app",
        "Mobile Application",
        "Mobile app project with offline sync and push notifications",
        ProjectSettings.builder().enableAISuggestions(true).build(),
        List.of(
            new RequirementTemplate("User Authentication", "FUNCTIONAL"),
            new RequirementTemplate("Offline Sync", "FUNCTIONAL"),
            new RequirementTemplate("Push Notifications", "FUNCTIONAL"),
            new RequirementTemplate("Battery Optimization", "NON_FUNCTIONAL"),
            new RequirementTemplate("Network Resilience", "CONSTRAINT")));
  }

  /**
   * Create API service project template.
   *
   * @return API service template
   */
  public static ProjectTemplate apiService() {
    return new ProjectTemplate(
        "api-service",
        "API Service",
        "Backend API service with authentication, caching, and security",
        ProjectSettings.builder()
            .requireApprovalForRequirements(true)
            .enableAISuggestions(true)
            .build(),
        List.of(
            new RequirementTemplate("API Authentication", "FUNCTIONAL"),
            new RequirementTemplate("Request Validation", "FUNCTIONAL"),
            new RequirementTemplate("Response Caching", "NON_FUNCTIONAL"),
            new RequirementTemplate("Rate Limiting", "NON_FUNCTIONAL"),
            new RequirementTemplate("API Security", "CONSTRAINT"),
            new RequirementTemplate("API Documentation", "CONSTRAINT")));
  }

  /**
   * Requirement template reference.
   *
   * <p>Simple reference to a requirement template included in project.
   * Actual Requirement objects will be created from these templates.
   */
  public record RequirementTemplate(String title, String type) {
    /**
     * Create requirement template.
     *
     * @param title requirement title
     * @param type requirement type
     */
    public RequirementTemplate {}
  }

  /**
   * Get requirement template by title.
   *
   * @param title title to search for
   * @return requirement template or null if not found
   */
  public RequirementTemplate getRequirementTemplate(String title) {
    return requirementTemplates.stream()
        .filter(t -> t.title().equals(title))
        .findFirst()
        .orElse(null);
  }

  /**
   * Check if this template includes a specific requirement template.
   *
   * @param title template title to check
   * @return true if template includes the requirement
   */
  public boolean hasRequirementTemplate(String title) {
    return requirementTemplates.stream().anyMatch(t -> t.title().equals(title));
  }
}