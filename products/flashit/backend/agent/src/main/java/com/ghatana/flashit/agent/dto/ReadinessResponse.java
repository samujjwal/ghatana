package com.ghatana.flashit.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Readiness check response.
 *
 * @doc.type record
 * @doc.purpose Returns service readiness status with available agents
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ReadinessResponse(
        @JsonProperty("ready") boolean ready,
        @JsonProperty("agents") List<String> agents,
        @JsonProperty("openAiConfigured") boolean openAiConfigured
) {
}
