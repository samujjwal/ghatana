package com.ghatana.flashit.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Request DTO for intelligence accumulation (cross-session learning).
 *
 * @doc.type record
 * @doc.purpose Carries user moment history for knowledge profile computation
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record IntelligenceAccumulationRequest(
        @JsonProperty("userId") String userId,
        @JsonProperty("moments") List<MomentData> moments,
        @JsonProperty("existingTopics") List<String> existingTopics,
        @JsonProperty("existingEntities") List<String> existingEntities,
        @JsonProperty("profileVersion") int profileVersion
) {
}
