package com.ghatana.agent.framework.config;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * A named, reusable configuration template that agent configs can extend.
 *
 * <p>Templates define platform-level defaults (retry policy, timeout, memory config,
 * observation level) that products override selectively. This enables a configuration
 * inheritance chain:
 *
 * <pre>{@code
 * # templates/platform-defaults.yaml
 * templateId: platform-defaults
 * timeout: PT5S
 * maxRetries: 3
 * retryBackoff: PT0.1S
 * metricsEnabled: true
 * tracingEnabled: true
 * tracingSampleRate: 0.1
 * failureMode: CIRCUIT_BREAKER
 * labels:
 *   platform: ghatana
 *
 * # agents/fraud-detector.yaml
 * extends: platform-defaults
 * agentId: fraud-detector
 * type: DETERMINISTIC
 * timeout: PT10S          # override
 * confidenceThreshold: 0.95
 * labels:
 *   team: security        # merged with platform labels
 * }</pre>
 *
 * <p>Merge semantics:
 * <ul>
 *   <li>Scalar fields: child overrides parent</li>
 *   <li>Maps (labels, properties): shallow merge (child keys override parent keys)</li>
 *   <li>Lists/Sets: child replaces parent entirely</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Reusable agent configuration template
 * @doc.layer platform
 * @doc.pattern Template / Prototype
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentConfigTemplate {

    @JsonProperty("templateId")
    private String templateId;

    @JsonProperty("description")
    private String description;

    // All fields mirror AgentConfigDto (nullable = "not set")
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

    private final Map<String, Object> extraDefaults = new LinkedHashMap<>();

    @JsonAnySetter
    public void setExtraDefault(String key, Object value) {
        extraDefaults.put(key, value);
    }

    // =========================================================================
    // Accessors
    // =========================================================================

    @NotNull
    public String getTemplateId() { return templateId; }
    public void setTemplateId(String v) { this.templateId = v; }

    @Nullable
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }

    @Nullable public String getTimeout() { return timeout; }
    @Nullable public Double getConfidenceThreshold() { return confidenceThreshold; }
    @Nullable public Integer getMaxRetries() { return maxRetries; }
    @Nullable public String getRetryBackoff() { return retryBackoff; }
    @Nullable public String getMaxRetryBackoff() { return maxRetryBackoff; }
    @Nullable public String getFailureMode() { return failureMode; }
    @Nullable public Integer getCircuitBreakerThreshold() { return circuitBreakerThreshold; }
    @Nullable public String getCircuitBreakerReset() { return circuitBreakerReset; }
    @Nullable public Boolean getMetricsEnabled() { return metricsEnabled; }
    @Nullable public Boolean getTracingEnabled() { return tracingEnabled; }
    @Nullable public Double getTracingSampleRate() { return tracingSampleRate; }
    @Nullable public Map<String, Object> getProperties() { return properties; }
    @Nullable public Map<String, String> getLabels() { return labels; }
    @Nullable public Set<String> getRequiredCapabilities() { return requiredCapabilities; }
    @NotNull public Map<String, Object> getExtraDefaults() { return Collections.unmodifiableMap(extraDefaults); }

    // =========================================================================
    // Merge Logic
    // =========================================================================

    /**
     * Applies this template's defaults to the given DTO. Only sets fields that
     * are {@code null} in the DTO (i.e., child values take precedence).
     *
     * <p>For maps (labels, properties), performs a shallow merge where child keys
     * override template keys.
     *
     * @param dto the agent configuration DTO to apply defaults to
     */
    public void applyTo(@NotNull AgentConfigDto dto) {
        if (dto.getTimeout() == null && timeout != null) dto.setTimeout(timeout);
        if (dto.getConfidenceThreshold() == null && confidenceThreshold != null)
            dto.setConfidenceThreshold(confidenceThreshold);
        if (dto.getMaxRetries() == null && maxRetries != null) dto.setMaxRetries(maxRetries);
        if (dto.getRetryBackoff() == null && retryBackoff != null) dto.setRetryBackoff(retryBackoff);
        if (dto.getMaxRetryBackoff() == null && maxRetryBackoff != null) dto.setMaxRetryBackoff(maxRetryBackoff);
        if (dto.getFailureMode() == null && failureMode != null) dto.setFailureMode(failureMode);
        if (dto.getCircuitBreakerThreshold() == null && circuitBreakerThreshold != null)
            dto.setCircuitBreakerThreshold(circuitBreakerThreshold);
        if (dto.getCircuitBreakerReset() == null && circuitBreakerReset != null)
            dto.setCircuitBreakerReset(circuitBreakerReset);
        if (dto.getMetricsEnabled() == null && metricsEnabled != null) dto.setMetricsEnabled(metricsEnabled);
        if (dto.getTracingEnabled() == null && tracingEnabled != null) dto.setTracingEnabled(tracingEnabled);
        if (dto.getTracingSampleRate() == null && tracingSampleRate != null)
            dto.setTracingSampleRate(tracingSampleRate);

        // Shallow merge for maps: template fills gaps, child overrides
        if (labels != null && !labels.isEmpty()) {
            Map<String, String> merged = new LinkedHashMap<>(labels);
            merged.putAll(dto.getLabels()); // child wins
            dto.setLabels(merged);
        }

        if (properties != null && !properties.isEmpty()) {
            Map<String, Object> merged = new LinkedHashMap<>(properties);
            merged.putAll(dto.getProperties()); // child wins
            dto.setProperties(merged);
        }

        if (requiredCapabilities != null && dto.getRequiredCapabilities().isEmpty()) {
            dto.setRequiredCapabilities(requiredCapabilities);
        }

        // Apply extra defaults to the DTO's extra properties
        for (var entry : extraDefaults.entrySet()) {
            if (dto.getExtraProperties().get(entry.getKey()) == null) {
                dto.setExtraProperty(entry.getKey(), entry.getValue());
            }
        }
    }
}
