package com.ghatana.requirements.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Request DTO for creating a new requirement.
 *
 * <p><b>Purpose:</b> Encapsulates request parameters for requirement creation.
 * Handles validation and transformation to domain model.
 *
 * <p><b>Thread Safety:</b> Immutable value object. Safe to share across threads.
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 *   POST /api/projects/{projectId}/requirements
 *   Content-Type: application/json
 *
 *   {
 *     "text": "User can authenticate with OAuth2",
 *     "priority": "HIGH"
 *   }
 *
 *   // In controller
 *   CreateRequirementRequest req = new CreateRequirementRequest("...", "HIGH");
 *   Requirement created = requirementService.create(projectId, req.toDomain());
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Request DTO for requirement creation
 * @doc.layer product
 * @doc.pattern Data Transfer Object
 * @since 1.0.0
 */
public final class CreateRequirementRequest {
  private final String text;
  private final String priority;

  /**
   * Create a requirement creation request.
   *
   * @param text the requirement text (non-null, non-empty)
   * @param priority optional priority level (HIGH, MEDIUM, LOW)
   */
  @JsonCreator
  public CreateRequirementRequest(
      @JsonProperty("text") String text,
      @JsonProperty("priority") String priority) {
    this.text = Objects.requireNonNull(text, "text cannot be null");
    if (text.trim().isEmpty()) {
      throw new IllegalArgumentException("text cannot be empty");
    }
    this.priority = priority != null ? priority : "MEDIUM";
  }

  /**
 * Get the requirement text. */
  @JsonProperty("text")
  public String text() {
    return text;
  }

  /**
 * Get the priority level. */
  @JsonProperty("priority")
  public String priority() {
    return priority;
  }

  @Override
  public String toString() {
    return String.format("CreateRequirementRequest{text=%s, priority=%s}", text, priority);
  }
}