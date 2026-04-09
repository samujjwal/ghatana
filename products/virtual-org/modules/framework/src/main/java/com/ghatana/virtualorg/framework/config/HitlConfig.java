package com.ghatana.virtualorg.framework.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Shared Human-in-the-Loop configuration.
 *
 * @doc.type record
 * @doc.purpose Shared human-in-the-loop configuration for Virtual-Org resources
 * @doc.layer core
 * @doc.pattern Value Object
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HitlConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("defaultApprovalTimeout")
        String defaultApprovalTimeout,
        @JsonProperty("escalationPolicy")
        String escalationPolicy,
        @JsonProperty("requireApprovalFor")
        List<String> requireApprovalFor,
        @JsonProperty("approvalChain")
        List<String> approvalChain
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }
}
