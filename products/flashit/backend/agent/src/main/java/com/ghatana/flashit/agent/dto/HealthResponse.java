package com.ghatana.flashit.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Health check response.
 *
 * @doc.type record
 * @doc.purpose Returns service health status
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record HealthResponse(
        @JsonProperty("status") String status,
        @JsonProperty("timestamp") String timestamp,
        @JsonProperty("service") String service
) {
}
