package com.ghatana.requirements.api.rest.dto;

import java.util.UUID;

/**
 * Response DTO for a similar requirement from vector search.
 *
 * <p><b>Purpose:</b> Represents a requirement that is semantically similar
 * to a query requirement, returned from vector similarity search.
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 *   GET /api/requirements/{id}/similar?limit=5
 *
 *   [
 *     {
 *       "requirementId": "req-456",
 *       "text": "Add OAuth2 support",
 *       "similarityScore": 0.92
 *     },
 *     {
 *       "requirementId": "req-789",
 *       "text": "User authentication",
 *       "similarityScore": 0.88
 *     }
 *   ]
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Response DTO for similar requirement
 * @doc.layer product
 * @doc.pattern Data Transfer Object
 * @since 1.0.0
 */
public final class SimilarRequirementResponse {
  private final UUID requirementId;
  private final String text;
  private final float similarityScore;

  /**
   * Create a similar requirement response.
   *
   * @param requirementId ID of similar requirement
   * @param text requirement text
   * @param similarityScore cosine similarity score [-1, 1]
   */
  public SimilarRequirementResponse(UUID requirementId, String text, float similarityScore) {
    this.requirementId = requirementId;
    this.text = text;
    this.similarityScore = similarityScore;
  }

  public UUID getRequirementId() { return requirementId; }
  public String getText() { return text; }
  public float getSimilarityScore() { return similarityScore; }
}