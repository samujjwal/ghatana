package com.ghatana.flashit.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request DTO for NLP analysis (entities, sentiment, mood).
 *
 * <p>The specific analysis type is determined by the endpoint URL
 * (e.g., /extract-entities, /analyze-sentiment, /detect-mood).
 *
 * @doc.type record
 * @doc.purpose Carries text for natural language processing analysis
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record NLPRequest(
        @JsonProperty("momentId") String momentId,
        @JsonProperty("text") String text,
        @JsonProperty("userId") String userId
) {
}
