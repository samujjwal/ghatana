/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.framework.config;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.*;

/**
 * Jackson-friendly YAML DTO for agent configuration deserialization.
 * This is the "wire format" that maps 1:1 to YAML structure, before
 * being materialized into the immutable {@link com.ghatana.agent.AgentConfig}
 * hierarchy via builder pattern.
 *
 * <p>Example YAML:
 * <pre>
 * agentId: fraud-detector
 * type: DETERMINISTIC
 * version: "2.1.0"
 * timeout: PT5S
 * confidenceThreshold: 0.85
 * maxRetries: 3
 * failureMode: CIRCUIT_BREAKER
 * metricsEnabled: true
 * labels:
 *   team: security
 *   domain: fraud
 * # Type-specific fields go into extraProperties via @JsonAnySetter
 * subtype: RULE_BASED
 * evaluateAllRules: true
 * rules:
 *   - name: high-amount
 *     field: amount
 *     operator: GT
 *     value: 10000
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Agent YAML deserialization DTO
 * @doc.layer platform
 * @doc.pattern DTO, Transfer Object
 *
 * @author Ghatana AI Platform
 * @since 3.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentConfigDto {

    // ═════════════════════════════════════════════════════════════════════════
    // Base AgentConfig fields
    // ═════════════════════════════════════════════════════════════════════════

    @JsonProperty("agentId")
    private String agentId;

    /** Optional template to inherit defaults from. */
    @JsonProperty("extends")
    private String extendsTemplate;

    @JsonProperty("type")
    private String type;

    @JsonProperty("version")
    private String version = "1.0.0";

    @JsonProperty("timeout")
    private String timeout;

    @JsonProperty("confidenceThreshold")
    private Double confidenceThreshold;

    @JsonProperty("maxRetries")
    private Integer maxRetries;

    @JsonProperty("retryBackoff")
    private String retryBackoff;

    @JsonProperty("maxRetryBackoff")
    private String maxRetryBackoff;

    @JsonProperty("failureMode")
    private String failureMode;

    @JsonProperty("circuitBreakerThreshold")
    private Integer circuitBreakerThreshold;

    @JsonProperty("circuitBreakerReset")
    private String circuitBreakerReset;

    @JsonProperty("metricsEnabled")
    private Boolean metricsEnabled;

    @JsonProperty("tracingEnabled")
    private Boolean tracingEnabled;

    @JsonProperty("tracingSampleRate")
    private Double tracingSampleRate;

    @JsonProperty("properties")
    private Map<String, Object> properties;

    @JsonProperty("labels")
    private Map<String, String> labels;

    @JsonProperty("requiredCapabilities")
    private Set<String> requiredCapabilities;

    // ═════════════════════════════════════════════════════════════════════════
    // Extra properties captured by @JsonAnySetter
    // These contain type-specific fields: subtype, rules, triggers, etc.
    // ═════════════════════════════════════════════════════════════════════════

    private final Map<String, Object> extraProperties = new LinkedHashMap<>();

    @JsonAnySetter
    public void setExtraProperty(String key, Object value) {
        extraProperties.put(key, value);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Accessors
    // ═════════════════════════════════════════════════════════════════════════

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

    @Nullable
    public String getExtendsTemplate() { return extendsTemplate; }
    public void setExtendsTemplate(String v) { this.extendsTemplate = v; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    @Nullable
    public String getTimeout() { return timeout; }
    public void setTimeout(String timeout) { this.timeout = timeout; }

    @Nullable
    public Double getConfidenceThreshold() { return confidenceThreshold; }
    public void setConfidenceThreshold(Double v) { this.confidenceThreshold = v; }

    @Nullable
    public Integer getMaxRetries() { return maxRetries; }
    public void setMaxRetries(Integer v) { this.maxRetries = v; }

    @Nullable
    public String getRetryBackoff() { return retryBackoff; }
    public void setRetryBackoff(String v) { this.retryBackoff = v; }

    @Nullable
    public String getMaxRetryBackoff() { return maxRetryBackoff; }
    public void setMaxRetryBackoff(String v) { this.maxRetryBackoff = v; }

    @Nullable
    public String getFailureMode() { return failureMode; }
    public void setFailureMode(String v) { this.failureMode = v; }

    @Nullable
    public Integer getCircuitBreakerThreshold() { return circuitBreakerThreshold; }
    public void setCircuitBreakerThreshold(Integer v) { this.circuitBreakerThreshold = v; }

    @Nullable
    public String getCircuitBreakerReset() { return circuitBreakerReset; }
    public void setCircuitBreakerReset(String v) { this.circuitBreakerReset = v; }

    @Nullable
    public Boolean getMetricsEnabled() { return metricsEnabled; }
    public void setMetricsEnabled(Boolean v) { this.metricsEnabled = v; }

    @Nullable
    public Boolean getTracingEnabled() { return tracingEnabled; }
    public void setTracingEnabled(Boolean v) { this.tracingEnabled = v; }

    @Nullable
    public Double getTracingSampleRate() { return tracingSampleRate; }
    public void setTracingSampleRate(Double v) { this.tracingSampleRate = v; }

    public Map<String, Object> getProperties() {
        return properties != null ? properties : Map.of();
    }
    public void setProperties(Map<String, Object> v) { this.properties = v; }

    public Map<String, String> getLabels() {
        return labels != null ? labels : Map.of();
    }
    public void setLabels(Map<String, String> v) { this.labels = v; }

    public Set<String> getRequiredCapabilities() {
        return requiredCapabilities != null ? requiredCapabilities : Set.of();
    }
    public void setRequiredCapabilities(Set<String> v) { this.requiredCapabilities = v; }

    public Map<String, Object> getExtraProperties() {
        return Collections.unmodifiableMap(extraProperties);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Convenience for extra property access
    // ═════════════════════════════════════════════════════════════════════════

    @Nullable
    public String getExtraString(String key) {
        Object v = extraProperties.get(key);
        return v != null ? v.toString() : null;
    }

    @Nullable
    public Boolean getExtraBoolean(String key) {
        Object v = extraProperties.get(key);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return Boolean.parseBoolean(s);
        return null;
    }

    @Nullable
    public Integer getExtraInt(String key) {
        Object v = extraProperties.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) return Integer.parseInt(s);
        return null;
    }

    @Nullable
    public Double getExtraDouble(String key) {
        Object v = extraProperties.get(key);
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) return Double.parseDouble(s);
        return null;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public <T> List<T> getExtraList(String key) {
        Object v = extraProperties.get(key);
        if (v instanceof List<?> list) return (List<T>) list;
        return null;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public Map<String, Object> getExtraMap(String key) {
        Object v = extraProperties.get(key);
        if (v instanceof Map<?, ?> map) return (Map<String, Object>) map;
        return null;
    }

    /**
     * Parses a duration string (ISO-8601 or seconds number).
     *
     * @param value duration string
     * @param fallback default if null
     * @return parsed Duration
     */
    public static Duration parseDuration(@Nullable String value, Duration fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Duration.parse(value);
        } catch (Exception e) {
            try {
                return Duration.ofSeconds(Long.parseLong(value));
            } catch (NumberFormatException nfe) {
                return fallback;
            }
        }
    }
}
