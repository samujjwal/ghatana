package com.ghatana.virtualorg.framework.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Priority escalation rule shared by task and interaction configuration.
 *
 * @doc.type record
 * @doc.purpose Shared priority escalation rule for Virtual-Org workload routing
 * @doc.layer core
 * @doc.pattern Value Object
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PriorityEscalationRule(
        @JsonProperty("name")
        String name,
        @JsonProperty("condition")
        String condition,
        @JsonProperty("from")
        String from,
        @JsonProperty("to")
        String to,
        @JsonProperty("notify")
        List<String> notifyList
        ) {
}