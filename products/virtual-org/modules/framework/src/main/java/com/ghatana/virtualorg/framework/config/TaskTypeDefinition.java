package com.ghatana.virtualorg.framework.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Task type definition for department workload configuration.
 *
 * @doc.type record
 * @doc.purpose Task type value object for department configuration
 * @doc.layer core
 * @doc.pattern Value Object
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TaskTypeDefinition(
        @JsonProperty("name")
        String name,
        @JsonProperty("priority")
        String priority,
        @JsonProperty("slaHours")
        Integer slaHours,
        @JsonProperty("requiredCapabilities")
        List<String> requiredCapabilities
        ) {

    public String priority() {
        return priority != null ? priority : "normal";
    }
}