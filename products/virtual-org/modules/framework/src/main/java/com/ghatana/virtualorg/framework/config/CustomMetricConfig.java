package com.ghatana.virtualorg.framework.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Custom metric definition shared by Virtual-Org observability configs.
 *
 * @doc.type record
 * @doc.purpose Shared custom metric definition for Virtual-Org observability
 * @doc.layer core
 * @doc.pattern Value Object
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CustomMetricConfig(
        @JsonProperty("name")
        String name,
        @JsonProperty("type")
        String type,
        @JsonProperty("description")
        String description,
        @JsonProperty("labels")
        List<String> labels
        ) {
}