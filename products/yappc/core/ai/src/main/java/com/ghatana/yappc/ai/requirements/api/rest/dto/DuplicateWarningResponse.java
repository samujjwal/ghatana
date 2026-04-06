package com.ghatana.yappc.ai.requirements.api.rest.dto;

import java.util.UUID;

/**
 * @doc.type class
 * @doc.purpose Response DTO describing a likely duplicate requirement discovered during write flows
 * @doc.layer product
 * @doc.pattern Data Transfer Object
 */
public final class DuplicateWarningResponse {
  private final UUID requirementId;
  private final String text;
  private final float similarityScore;

  public DuplicateWarningResponse(UUID requirementId, String text, float similarityScore) {
    this.requirementId = requirementId;
    this.text = text;
    this.similarityScore = similarityScore;
  }

  public UUID getRequirementId() { return requirementId; }
  public String getText() { return text; }
  public float getSimilarityScore() { return similarityScore; }
}