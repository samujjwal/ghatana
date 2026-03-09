package com.ghatana.virtualorg.framework.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Virtual-Org agent configuration POJO loaded from YAML.
 *
 * <p>
 * <b>Purpose</b><br>
 * Represents an agent configuration loaded from YAML files. Maps to the
 * Virtual-Org agent configuration schema.
 *
 * @doc.type record
 * @doc.purpose Virtual-Org agent configuration data model
 * @doc.layer core
 * @doc.pattern Value Object
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VirtualOrgAgentConfig(
        @JsonProperty("apiVersion")
        String apiVersion,
        @JsonProperty("kind")
        String kind,
        @JsonProperty("metadata")
        ConfigMetadata metadata,
        @JsonProperty("spec")
        AgentSpec spec
        ) {

    public boolean isValid() {
        return "virtualorg.ghatana.com/v1".equals(apiVersion)
                && "Agent".equals(kind)
                && metadata != null
                && spec != null;
    }

    public String getName() {
        return metadata != null ? metadata.name() : null;
    }

    // Accessor methods to expose nested spec properties
    public String getTemplate() {
        return spec != null ? spec.template() : null;
    }

    public String getDisplayName() {
        return spec != null ? spec.displayName() : null;
    }

    public String getDescription() {
        return spec != null ? spec.description() : null;
    }

    public String getDepartment() {
        return spec != null ? spec.department() : null;
    }

    public String getRoleName() {
        return spec != null && spec.role() != null ? spec.role().name() : null;
    }

    public String getRoleLevel() {
        return spec != null && spec.role() != null ? spec.role().level() : "individual";
    }

    public String getRoleTitle() {
        return spec != null && spec.role() != null ? spec.role().title() : null;
    }

    public boolean isAiEnabled() {
        return spec != null && spec.ai() != null && spec.ai().enabled();
    }

    public String getAiProvider() {
        return spec != null && spec.ai() != null ? spec.ai().provider() : "openai";
    }

    public String getAiModel() {
        return spec != null && spec.ai() != null ? spec.ai().model() : null;
    }

    public String getSystemPrompt() {
        return spec != null && spec.ai() != null ? spec.ai().systemPrompt() : null;
    }

    public List<String> getPrimaryCapabilities() {
        return spec != null && spec.capabilities() != null ?
            spec.capabilities().primary() : List.of();
    }

    public List<String> getSecondaryCapabilities() {
        return spec != null && spec.capabilities() != null ?
            spec.capabilities().secondary() : List.of();
    }
}

/**
 * Agent specification.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record AgentSpec(
        @JsonProperty("template")
        String template,
        @JsonProperty("displayName")
        String displayName,
        @JsonProperty("description")
        String description,
        @JsonProperty("role")
        RoleConfig role,
        @JsonProperty("department")
        String department,
        @JsonProperty("reportingTo")
        String reportingTo,
        @JsonProperty("directReports")
        List<String> directReports,
        @JsonProperty("authority")
        AuthorityConfig authority,
        @JsonProperty("capabilities")
        CapabilitiesConfig capabilities,
        @JsonProperty("ai")
        AiConfig ai,
        @JsonProperty("workload")
        WorkloadConfig workload,
        @JsonProperty("availability")
        AvailabilityConfig availability,
        @JsonProperty("hitl")
        HitlConfig hitl,
        @JsonProperty("subscriptions")
        List<EventSubscription> subscriptions
        ) {

}

/**
 * Agent role configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record RoleConfig(
        @JsonProperty("name")
        String name,
        @JsonProperty("level")
        String level,
        @JsonProperty("title")
        String title
        ) {

    public String level() {
        return level != null ? level : "individual";
    }
}

/**
 * Agent authority configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record AuthorityConfig(
        @JsonProperty("decisionScope")
        String decisionScope,
        @JsonProperty("budgetLimit")
        Double budgetLimit,
        @JsonProperty("canApprove")
        List<String> canApprove,
        @JsonProperty("canEscalateTo")
        List<String> canEscalateTo
        ) {

    public String decisionScope() {
        return decisionScope != null ? decisionScope : "individual";
    }
}

/**
 * Agent capabilities configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record CapabilitiesConfig(
        @JsonProperty("primary")
        List<String> primary,
        @JsonProperty("secondary")
        List<String> secondary
        ) {

}

/**
 * Agent AI configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record AiConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("provider")
        String provider,
        @JsonProperty("model")
        String model,
        @JsonProperty("systemPrompt")
        String systemPrompt,
        @JsonProperty("responseConfig")
        ResponseConfig responseConfig,
        @JsonProperty("tools")
        List<ToolConfig> tools,
        @JsonProperty("memory")
        MemoryConfig memory
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : false;
    }

    public String provider() {
        return provider != null ? provider : "openai";
    }
}

/**
 * AI response configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ResponseConfig(
        @JsonProperty("maxTokens")
        Integer maxTokens,
        @JsonProperty("temperature")
        Double temperature,
        @JsonProperty("topP")
        Double topP
        ) {

    public Integer maxTokens() {
        return maxTokens != null ? maxTokens : 2000;
    }

    public Double temperature() {
        return temperature != null ? temperature : 0.7;
    }

    public Double topP() {
        return topP != null ? topP : 0.9;
    }
}

/**
 * AI tool configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ToolConfig(
        @JsonProperty("name")
        String name,
        @JsonProperty("enabled")
        Boolean enabled
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }
}

/**
 * Agent memory configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record MemoryConfig(
        @JsonProperty("type")
        String type,
        @JsonProperty("contextWindow")
        Integer contextWindow
        ) {

    public String type() {
        return type != null ? type : "short-term";
    }

    public Integer contextWindow() {
        return contextWindow != null ? contextWindow : 20;
    }
}

/**
 * Agent workload configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record WorkloadConfig(
        @JsonProperty("maxConcurrentTasks")
        Integer maxConcurrentTasks,
        @JsonProperty("taskPrioritization")
        String taskPrioritization
        ) {

    public Integer maxConcurrentTasks() {
        return maxConcurrentTasks != null ? maxConcurrentTasks : 5;
    }

    public String taskPrioritization() {
        return taskPrioritization != null ? taskPrioritization : "fifo";
    }
}

/**
 * Agent availability configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record AvailabilityConfig(
        @JsonProperty("schedule")
        ScheduleConfig schedule,
        @JsonProperty("responseTime")
        ResponseTimeConfig responseTime
        ) {

}

/**
 * Schedule configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ScheduleConfig(
        @JsonProperty("type")
        String type
        ) {

    public String type() {
        return type != null ? type : "always";
    }
}

/**
 * Response time configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ResponseTimeConfig(
        @JsonProperty("target")
        String target,
        @JsonProperty("maximum")
        String maximum
        ) {

}

/**
 * Event subscription configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record EventSubscription(
        @JsonProperty("event")
        String event,
        @JsonProperty("action")
        String action,
        @JsonProperty("conditions")
        List<ConditionConfig> conditions
        ) {

}

/**
 * Condition configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ConditionConfig(
        @JsonProperty("field")
        String field,
        @JsonProperty("operator")
        String operator,
        @JsonProperty("value")
        Object value,
        @JsonProperty("values")
        List<Object> values
        ) {

}
