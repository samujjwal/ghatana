package com.ghatana.flashit.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Agent information for discovery and status.
 *
 * @doc.type record
 * @doc.purpose Describes an available agent with its capabilities
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record AgentInfo(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("status") String status,
        @JsonProperty("capabilities") List<String> capabilities
) {
}
