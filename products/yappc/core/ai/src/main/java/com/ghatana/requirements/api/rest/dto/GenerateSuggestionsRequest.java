package com.ghatana.requirements.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Request payload for generating AI suggestions for a requirement.
 *
 * <p><b>Purpose:</b> Allows clients to provide custom feature descriptions,
 * persona filters, and ranking options when requesting AI-generated
 * requirement suggestions.</p>
 *
 * @doc.type class
 * @doc.purpose Request DTO for AI suggestion generation
 * @doc.layer product
 * @doc.pattern Data Transfer Object
 */
public final class GenerateSuggestionsRequest {
  private final String featureDescription;
  private final List<String> personas;
  private final Float minRelevance;
  private final Integer limit;

  /**
   * Create a suggestion generation request.
   *
   * @param featureDescription optional feature description override
   * @param personas optional persona filters
   * @param minRelevance optional minimum relevance score
   * @param limit optional limit override
   */
  @JsonCreator
  public GenerateSuggestionsRequest(
      @JsonProperty("featureDescription") String featureDescription,
      @JsonProperty("personas") List<String> personas,
      @JsonProperty("minRelevance") Float minRelevance,
      @JsonProperty("limit") Integer limit) {
    this.featureDescription = featureDescription;
    this.personas = personas != null ? List.copyOf(personas) : List.of();
    this.minRelevance = minRelevance;
    this.limit = limit;
  }

  public String featureDescription() {
    return featureDescription;
  }

  public List<String> personas() {
    return personas;
  }

  public Float minRelevance() {
    return minRelevance;
  }

  public Integer limit() {
    return limit;
  }

  /**
   * Convenience factory for an empty request.
   *
   * @return request with no overrides
   */
  public static GenerateSuggestionsRequest empty() {
    return new GenerateSuggestionsRequest(null, List.of(), null, null);
  }
}
