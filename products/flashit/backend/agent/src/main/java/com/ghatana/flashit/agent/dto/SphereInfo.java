package com.ghatana.flashit.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Sphere metadata used in classification requests.
 *
 * @doc.type record
 * @doc.purpose Represents available sphere info for classification context
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record SphereInfo(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("type") String type
) {
}
