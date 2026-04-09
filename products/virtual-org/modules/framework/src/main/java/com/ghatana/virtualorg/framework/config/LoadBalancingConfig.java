package com.ghatana.virtualorg.framework.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Load-balancing strategy shared by task assignment and interaction routing.
 *
 * @doc.type record
 * @doc.purpose Shared load-balancing configuration for Virtual-Org routing
 * @doc.layer core
 * @doc.pattern Value Object
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LoadBalancingConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("algorithm")
        String algorithm,
        @JsonProperty("maxConcurrentTasks")
        Integer maxConcurrentTasks,
        @JsonProperty("weightByCapacity")
        Boolean weightByCapacity
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }

    public String algorithm() {
        return algorithm != null ? algorithm : "least-loaded";
    }
}
