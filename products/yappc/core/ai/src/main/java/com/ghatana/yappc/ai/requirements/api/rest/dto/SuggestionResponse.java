package com.ghatana.yappc.ai.requirements.api.rest.dto;

import java.time.Instant;

/**
 * Response DTO for an AI suggestion.
 *
 * <p><b>Purpose:</b> Represents a suggestion in REST API responses with
 * all metadata needed for UI rendering and interaction.
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 *   GET /api/requirements/{id}/suggestions
 *
 *   [
 *     {
 *       "id": "sugg-123",
 *       "text": "Add two-factor authentication",
 *       "persona": "DEVELOPER",
 *       "relevanceScore": 0.85,
 *       "priorityScore": 0.75,
 *       "status": "PENDING",
 *       "createdAt": "2025-01-15T10:30:00Z"
 *     }
 *   ]
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Response DTO for AI suggestion
 * @doc.layer product
 * @doc.pattern Data Transfer Object
 * @since 1.0.0
 */
public final class SuggestionResponse {
  private final String id;
  private final String text;
  private final String persona;
  private final float relevanceScore;
  private final float priorityScore;
  private final String status;
  private final Instant createdAt;

  /**
   * Create a suggestion response.
   *
   * @param id suggestion ID
   * @param text suggested requirement text
   * @param persona persona that generated (DEVELOPER, ARCHITECT, etc.)
   * @param relevanceScore relevance score [0, 1]
   * @param priorityScore priority score [0, 1]
   * @param status suggestion status (PENDING, APPROVED, etc.)
   * @param createdAt creation timestamp
   */
  public SuggestionResponse(
      String id,
      String text,
      String persona,
      float relevanceScore,
      float priorityScore,
      String status,
      Instant createdAt) {
    this.id = id;
    this.text = text;
    this.persona = persona;
    this.relevanceScore = relevanceScore;
    this.priorityScore = priorityScore;
    this.status = status;
    this.createdAt = createdAt;
  }

  public String getId() { return id; }
  public String getText() { return text; }
  public String getPersona() { return persona; }
  public float getRelevanceScore() { return relevanceScore; }
  public float getPriorityScore() { return priorityScore; }
  public float getRankScore() { return (relevanceScore + priorityScore) / 2.0f; }
  public String getStatus() { return status; }
  public Instant getCreatedAt() { return createdAt; }
}