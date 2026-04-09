package com.ghatana.virtualorg.framework.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Shared condition expression for Virtual-Org rules and subscriptions.
 *
 * @doc.type record
 * @doc.purpose Shared condition value object for Virtual-Org rule evaluation
 * @doc.layer core
 * @doc.pattern Value Object
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConditionConfig(
        @JsonProperty("field")
        String field,
        @JsonProperty("operator")
        String operator,
        @JsonProperty("value")
        Object value,
        @JsonProperty("values")
        List<Object> values
        ) {
}
