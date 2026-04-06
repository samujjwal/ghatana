package com.ghatana.virtualorg.framework.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * KPI definition for department-level metrics.
 *
 * @doc.type record
 * @doc.purpose KPI definition value object for department configuration
 * @doc.layer core
 * @doc.pattern Value Object
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KpiDefinition(
        @JsonProperty("name")
        String name,
        @JsonProperty("displayName")
        String displayName,
        @JsonProperty("type")
        String type,
        @JsonProperty("unit")
        String unit,
        @JsonProperty("target")
        Double target,
        @JsonProperty("warningThreshold")
        Double warningThreshold,
        @JsonProperty("criticalThreshold")
        Double criticalThreshold
        ) {
}