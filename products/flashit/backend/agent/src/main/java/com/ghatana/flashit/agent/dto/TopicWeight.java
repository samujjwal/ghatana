package com.ghatana.flashit.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A topic with its computed weight/importance.
 *
 * @doc.type record
 * @doc.purpose Represents a ranked topic in the user's knowledge profile
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record TopicWeight(
        @JsonProperty("topic") String topic,
        @JsonProperty("weight") double weight,
        @JsonProperty("momentCount") int momentCount,
        @JsonProperty("trend") String trend
) {
}
