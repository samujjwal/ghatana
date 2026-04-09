package com.ghatana.virtualorg.framework.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Shared retry policy configuration for action and lifecycle execution.
 *
 * @doc.type record
 * @doc.purpose Shared retry policy value object for Virtual-Org execution flows
 * @doc.layer core
 * @doc.pattern Value Object
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RetryPolicyConfig(
        @JsonProperty("maxRetries")
        Integer maxRetries,
        @JsonProperty("initialDelay")
        String initialDelay,
        @JsonProperty("maxDelay")
        String maxDelay,
        @JsonProperty("backoffMultiplier")
        Double backoffMultiplier,
        @JsonProperty("retryOn")
        List<String> retryOn,
        @JsonProperty("noRetryOn")
        List<String> noRetryOn
        ) {

    public Integer maxRetries() {
        return maxRetries != null ? maxRetries : 3;
    }

    public String initialDelay() {
        return initialDelay != null ? initialDelay : "1s";
    }

    public Double backoffMultiplier() {
        return backoffMultiplier != null ? backoffMultiplier : 2.0;
    }
}
