package com.ghatana.requirements.api.rest.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for a requirement with suggestions and metadata.
 *
 * <p><b>Purpose:</b> Represents a requirement with all related AI data
 * (suggestions, embeddings, similar requirements) for REST API responses.
 *
 * <p><b>Thread Safety:</b> Immutable. Safe to serialize and share across threads.
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 *   GET /api/projects/{projectId}/requirements/{id}
 *
 *   {
 *     "id": "uuid",
 *     "projectId": "uuid",
 *     "text": "User can authenticate",
 *     "suggestions": [
 *       {
 *         "id": "sugg-1",
 *         "text": "Add OAuth2 support",
 *         "persona": "DEVELOPER",
 *         "relevanceScore": 0.87
 *       }
 *     ],
 *     "createdAt": "2025-01-15T10:30:00Z"
 *   }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Response DTO for requirement with AI data
 * @doc.layer product
 * @doc.pattern Data Transfer Object
 * @since 1.0.0
 */
public final class RequirementResponse {
  private final UUID id;
  private final UUID projectId;
  private final String text;
  private final String status;
  private final List<SuggestionResponse> suggestions;
  private final List<SimilarRequirementResponse> similarRequirements;
  private final Instant createdAt;
  private final Instant updatedAt;

  /**
   * Create a requirement response.
   *
   * @param id requirement ID
   * @param projectId project ID
   * @param text requirement text
   * @param status requirement status
   * @param suggestions list of suggestions
   * @param similarRequirements list of similar requirements
   * @param createdAt creation timestamp
   * @param updatedAt last update timestamp
   */
  public RequirementResponse(
      UUID id,
      UUID projectId,
      String text,
      String status,
      List<SuggestionResponse> suggestions,
      List<SimilarRequirementResponse> similarRequirements,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.projectId = projectId;
    this.text = text;
    this.status = status;
    this.suggestions = suggestions != null ? suggestions : List.of();
    this.similarRequirements = similarRequirements != null ? similarRequirements : List.of();
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public UUID getId() { return id; }
  public UUID getProjectId() { return projectId; }
  public String getText() { return text; }
  public String getStatus() { return status; }
  public List<SuggestionResponse> getSuggestions() { return suggestions; }
  public List<SimilarRequirementResponse> getSimilarRequirements() { return similarRequirements; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
}