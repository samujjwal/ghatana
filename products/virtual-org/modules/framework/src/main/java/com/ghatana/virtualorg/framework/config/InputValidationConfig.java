package com.ghatana.virtualorg.framework.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Shared input validation rules for action and task inputs.
 *
 * @doc.type record
 * @doc.purpose Shared validation rules value object for Virtual-Org inputs
 * @doc.layer core
 * @doc.pattern Value Object
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InputValidationConfig(
        @JsonProperty("pattern")
        String pattern,
        @JsonProperty("minLength")
        Integer minLength,
        @JsonProperty("maxLength")
        Integer maxLength,
        @JsonProperty("minimum")
        Double minimum,
        @JsonProperty("maximum")
        Double maximum,
        @JsonProperty("enum")
        List<String> enumValues,
        @JsonProperty("format")
        String format,
        @JsonProperty("customValidator")
        String customValidator
        ) {
}