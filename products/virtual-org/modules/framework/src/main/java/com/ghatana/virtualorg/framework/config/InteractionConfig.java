package com.ghatana.virtualorg.framework.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Interaction configuration POJO.
 *
 * <p>
 * <b>Purpose</b><br>
 * Represents interaction configuration loaded from YAML files. Defines how
 * agents and departments communicate, collaborate, and hand off work. This
 * configuration governs all inter-entity communication patterns.
 *
 * <p>
 * <b>Interaction Types</b><br>
 * - <b>handoff</b>: Work transfer between entities - <b>collaboration</b>:
 * Joint work on shared tasks - <b>delegation</b>: Assigning work to
 * subordinates - <b>escalation</b>: Raising issues to higher authority -
 * <b>consultation</b>: Seeking advice without transfer - <b>notification</b>:
 * Information broadcast
 *
 * <p>
 * <b>Usage Example (YAML)</b>
 * <pre>{@code
 * apiVersion: virtualorg.ghatana.com/v1
 * kind: Interaction
 * metadata:
 *   name: eng-qa-handoff
 *   namespace: engineering
 * spec:
 *   type: handoff
 *   displayName: "Engineering to QA Handoff"
 *   source:
 *     department: engineering
 *     agents: [senior-engineer, tech-lead]
 *   target:
 *     department: qa
 *     agents: [qa-engineer]
 *   protocol:
 *     format: structured
 *     acknowledgment: required
 *   sla:
 *     responseTime: 15m
 *     completionTime: 4h
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Interaction configuration data model
 * @doc.layer core
 * @doc.pattern Value Object
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InteractionConfig(
        @JsonProperty("apiVersion")
        String apiVersion,
        @JsonProperty("kind")
        String kind,
        @JsonProperty("metadata")
        ConfigMetadata metadata,
        @JsonProperty("spec")
        InteractionSpec spec
        ) {

    public boolean isValid() {
        return "virtualorg.ghatana.com/v1".equals(apiVersion)
                && "Interaction".equals(kind)
                && metadata != null
                && spec != null
                && spec.type() != null;
    }

    public String getName() {
        return metadata != null ? metadata.name() : null;
    }

    public String getInteractionType() {
        return spec != null ? spec.type() : null;
    }
}

/**
 * Interaction specification.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record InteractionSpec(
        @JsonProperty("displayName")
        String displayName,
        @JsonProperty("description")
        String description,
        @JsonProperty("type")
        String type,
        @JsonProperty("category")
        String category,
        @JsonProperty("bidirectional")
        Boolean bidirectional,
        @JsonProperty("source")
        InteractionEndpointConfig source,
        @JsonProperty("target")
        InteractionEndpointConfig target,
        @JsonProperty("trigger")
        InteractionTriggerConfig trigger,
        @JsonProperty("protocol")
        InteractionProtocolConfig protocol,
        @JsonProperty("data")
        InteractionDataConfig data,
        @JsonProperty("sla")
        InteractionSlaConfig sla,
        @JsonProperty("rules")
        InteractionRulesConfig rules,
        @JsonProperty("state")
        InteractionStateConfig state,
        @JsonProperty("metrics")
        InteractionMetricsConfig metrics
        ) {

    public String type() {
        return type != null ? type : "handoff";
    }

    public Boolean bidirectional() {
        return bidirectional != null ? bidirectional : false;
    }
}

/**
 * Interaction endpoint configuration (source or target).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record InteractionEndpointConfig(
        @JsonProperty("type")
        String type,
        @JsonProperty("department")
        String department,
        @JsonProperty("departments")
        List<String> departments,
        @JsonProperty("agent")
        String agent,
        @JsonProperty("agents")
        List<String> agents,
        @JsonProperty("roles")
        List<String> roles,
        @JsonProperty("selector")
        EndpointSelectorConfig selector,
        @JsonProperty("constraints")
        List<EndpointConstraintConfig> constraints
        ) {

    public String type() {
        return type != null ? type : "agent";
    }
}

/**
 * Endpoint selector for dynamic resolution.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record EndpointSelectorConfig(
        @JsonProperty("strategy")
        String strategy,
        @JsonProperty("matchLabels")
        Map<String, String> matchLabels,
        @JsonProperty("matchCapabilities")
        List<String> matchCapabilities,
        @JsonProperty("expression")
        String expression
        ) {

    public String strategy() {
        return strategy != null ? strategy : "match-all";
    }
}

/**
 * Endpoint constraint configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record EndpointConstraintConfig(
        @JsonProperty("type")
        String type,
        @JsonProperty("field")
        String field,
        @JsonProperty("operator")
        String operator,
        @JsonProperty("value")
        Object value
        ) {

}

/**
 * Interaction trigger configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record InteractionTriggerConfig(
        @JsonProperty("type")
        String type,
        @JsonProperty("events")
        List<String> events,
        @JsonProperty("conditions")
        List<TriggerConditionConfig> conditions,
        @JsonProperty("schedule")
        String schedule,
        @JsonProperty("manual")
        ManualTriggerConfig manual
        ) {

    public String type() {
        return type != null ? type : "event";
    }
}

/**
 * Trigger condition configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record TriggerConditionConfig(
        @JsonProperty("field")
        String field,
        @JsonProperty("operator")
        String operator,
        @JsonProperty("value")
        Object value,
        @JsonProperty("expression")
        String expression
        ) {

}

/**
 * Manual trigger configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ManualTriggerConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("requiredRoles")
        List<String> requiredRoles,
        @JsonProperty("confirmation")
        Boolean confirmation
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }

    public Boolean confirmation() {
        return confirmation != null ? confirmation : true;
    }
}

/**
 * Interaction protocol configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record InteractionProtocolConfig(
        @JsonProperty("format")
        String format,
        @JsonProperty("acknowledgment")
        String acknowledgment,
        @JsonProperty("confirmation")
        ConfirmationConfig confirmation,
        @JsonProperty("timeout")
        String timeout,
        @JsonProperty("retries")
        Integer retries,
        @JsonProperty("ordering")
        String ordering,
        @JsonProperty("priority")
        InteractionPriorityConfig priority,
        @JsonProperty("encryption")
        Boolean encryption
        ) {

    public String format() {
        return format != null ? format : "structured";
    }

    public String acknowledgment() {
        return acknowledgment != null ? acknowledgment : "required";
    }

    public String timeout() {
        return timeout != null ? timeout : "5m";
    }

    public Integer retries() {
        return retries != null ? retries : 3;
    }

    public String ordering() {
        return ordering != null ? ordering : "fifo";
    }
}

/**
 * Confirmation configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ConfirmationConfig(
        @JsonProperty("required")
        Boolean required,
        @JsonProperty("timeout")
        String timeout,
        @JsonProperty("autoConfirmOn")
        List<String> autoConfirmOn,
        @JsonProperty("rejectPolicy")
        String rejectPolicy
        ) {

    public Boolean required() {
        return required != null ? required : false;
    }

    public String timeout() {
        return timeout != null ? timeout : "1h";
    }

    public String rejectPolicy() {
        return rejectPolicy != null ? rejectPolicy : "return-to-source";
    }
}

/**
 * Interaction priority configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record InteractionPriorityConfig(
        @JsonProperty("default")
        String defaultPriority,
        @JsonProperty("inheritFromTask")
        Boolean inheritFromTask,
        @JsonProperty("escalationRules")
        List<PriorityEscalationRule> escalationRules
        ) {

    public String defaultPriority() {
        return defaultPriority != null ? defaultPriority : "normal";
    }

    public Boolean inheritFromTask() {
        return inheritFromTask != null ? inheritFromTask : true;
    }
}

/**
 * Interaction data configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record InteractionDataConfig(
        @JsonProperty("payload")
        PayloadConfig payload,
        @JsonProperty("context")
        ContextTransferConfig context,
        @JsonProperty("attachments")
        AttachmentConfig attachments,
        @JsonProperty("transformation")
        List<DataTransformationConfig> transformation,
        @JsonProperty("validation")
        DataValidationConfig validation
        ) {

}

/**
 * Payload configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record PayloadConfig(
        @JsonProperty("schemaRef")
        String schemaRef,
        @JsonProperty("schema")
        Map<String, Object> schema,
        @JsonProperty("requiredFields")
        List<String> requiredFields,
        @JsonProperty("sensitiveFields")
        List<String> sensitiveFields,
        @JsonProperty("maxSize")
        String maxSize
        ) {

}

/**
 * Context transfer configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ContextTransferConfig(
        @JsonProperty("include")
        List<String> include,
        @JsonProperty("exclude")
        List<String> exclude,
        @JsonProperty("transformations")
        Map<String, String> transformations,
        @JsonProperty("preserveHistory")
        Boolean preserveHistory,
        @JsonProperty("historyDepth")
        Integer historyDepth
        ) {

    public Boolean preserveHistory() {
        return preserveHistory != null ? preserveHistory : true;
    }

    public Integer historyDepth() {
        return historyDepth != null ? historyDepth : 10;
    }
}

/**
 * Attachment configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record AttachmentConfig(
        @JsonProperty("allowed")
        Boolean allowed,
        @JsonProperty("maxCount")
        Integer maxCount,
        @JsonProperty("maxSize")
        String maxSize,
        @JsonProperty("allowedTypes")
        List<String> allowedTypes,
        @JsonProperty("scanning")
        Boolean scanning
        ) {

    public Boolean allowed() {
        return allowed != null ? allowed : true;
    }

    public Integer maxCount() {
        return maxCount != null ? maxCount : 10;
    }
}

/**
 * Data transformation configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record DataTransformationConfig(
        @JsonProperty("name")
        String name,
        @JsonProperty("type")
        String type,
        @JsonProperty("sourceField")
        String sourceField,
        @JsonProperty("targetField")
        String targetField,
        @JsonProperty("expression")
        String expression,
        @JsonProperty("template")
        String template
        ) {

}

/**
 * Data validation configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record DataValidationConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("strict")
        Boolean strict,
        @JsonProperty("rules")
        List<DataValidationRuleConfig> rules,
        @JsonProperty("onInvalid")
        String onInvalid
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }

    public Boolean strict() {
        return strict != null ? strict : false;
    }

    public String onInvalid() {
        return onInvalid != null ? onInvalid : "reject";
    }
}

/**
 * Data validation rule.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record DataValidationRuleConfig(
        @JsonProperty("name")
        String name,
        @JsonProperty("field")
        String field,
        @JsonProperty("rule")
        String rule,
        @JsonProperty("message")
        String message
        ) {

}

/**
 * Interaction SLA configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record InteractionSlaConfig(
        @JsonProperty("responseTime")
        String responseTime,
        @JsonProperty("acknowledgmentTime")
        String acknowledgmentTime,
        @JsonProperty("completionTime")
        String completionTime,
        @JsonProperty("byPriority")
        Map<String, InteractionSlaTierConfig> byPriority,
        @JsonProperty("tracking")
        SlaTrackingConfig tracking,
        @JsonProperty("breachActions")
        List<InteractionBreachActionConfig> breachActions
        ) {

    public String responseTime() {
        return responseTime != null ? responseTime : "15m";
    }

    public String acknowledgmentTime() {
        return acknowledgmentTime != null ? acknowledgmentTime : "5m";
    }
}

/**
 * Interaction SLA tier configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record InteractionSlaTierConfig(
        @JsonProperty("responseTime")
        String responseTime,
        @JsonProperty("acknowledgmentTime")
        String acknowledgmentTime,
        @JsonProperty("completionTime")
        String completionTime
        ) {

}

/**
 * Interaction breach action configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record InteractionBreachActionConfig(
        @JsonProperty("name")
        String name,
        @JsonProperty("type")
        String type,
        @JsonProperty("threshold")
        String threshold,
        @JsonProperty("action")
        String action,
        @JsonProperty("notify")
        List<String> notifyList,
        @JsonProperty("escalateTo")
        String escalateTo
        ) {

}

/**
 * Interaction rules configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record InteractionRulesConfig(
        @JsonProperty("preconditions")
        List<InteractionConditionConfig> preconditions,
        @JsonProperty("postconditions")
        List<InteractionConditionConfig> postconditions,
        @JsonProperty("guards")
        List<InteractionGuardConfig> guards,
        @JsonProperty("routing")
        InteractionRoutingConfig routing,
        @JsonProperty("fallback")
        InteractionFallbackConfig fallback
        ) {

}

/**
 * Interaction condition configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record InteractionConditionConfig(
        @JsonProperty("name")
        String name,
        @JsonProperty("expression")
        String expression,
        @JsonProperty("errorMessage")
        String errorMessage,
        @JsonProperty("severity")
        String severity
        ) {

    public String severity() {
        return severity != null ? severity : "error";
    }
}

/**
 * Interaction guard configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record InteractionGuardConfig(
        @JsonProperty("name")
        String name,
        @JsonProperty("type")
        String type,
        @JsonProperty("condition")
        String condition,
        @JsonProperty("action")
        String action,
        @JsonProperty("bypassRoles")
        List<String> bypassRoles
        ) {

    public String action() {
        return action != null ? action : "block";
    }
}

/**
 * Interaction routing configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record InteractionRoutingConfig(
        @JsonProperty("strategy")
        String strategy,
        @JsonProperty("rules")
        List<InteractionRoutingRuleConfig> rules,
        @JsonProperty("loadBalancing")
        LoadBalancingConfig loadBalancing
        ) {

    public String strategy() {
        return strategy != null ? strategy : "direct";
    }
}

/**
 * Interaction routing rule.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record InteractionRoutingRuleConfig(
        @JsonProperty("name")
        String name,
        @JsonProperty("condition")
        String condition,
        @JsonProperty("target")
        InteractionEndpointConfig target,
        @JsonProperty("priority")
        Integer priority
        ) {

    public Integer priority() {
        return priority != null ? priority : 0;
    }
}

/**
 * Interaction fallback configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record InteractionFallbackConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("strategy")
        String strategy,
        @JsonProperty("targets")
        List<InteractionEndpointConfig> targets,
        @JsonProperty("timeout")
        String timeout,
        @JsonProperty("notifyOnFallback")
        Boolean notifyOnFallback
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }

    public String strategy() {
        return strategy != null ? strategy : "escalate";
    }
}

/**
 * Interaction state configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record InteractionStateConfig(
        @JsonProperty("statuses")
        List<InteractionStatusConfig> statuses,
        @JsonProperty("transitions")
        List<InteractionTransitionConfig> transitions,
        @JsonProperty("persistence")
        InteractionPersistenceConfig persistence,
        @JsonProperty("history")
        InteractionHistoryConfig history
        ) {

}

/**
 * Interaction status configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record InteractionStatusConfig(
        @JsonProperty("name")
        String name,
        @JsonProperty("displayName")
        String displayName,
        @JsonProperty("type")
        String type,
        @JsonProperty("terminal")
        Boolean terminal,
        @JsonProperty("slaCountsHere")
        Boolean slaCountsHere
        ) {

    public Boolean terminal() {
        return terminal != null ? terminal : false;
    }

    public Boolean slaCountsHere() {
        return slaCountsHere != null ? slaCountsHere : true;
    }
}

/**
 * Interaction transition configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record InteractionTransitionConfig(
        @JsonProperty("from")
        String from,
        @JsonProperty("to")
        String to,
        @JsonProperty("name")
        String name,
        @JsonProperty("trigger")
        String trigger,
        @JsonProperty("condition")
        String condition,
        @JsonProperty("hooks")
        List<TransitionHookConfig> hooks
        ) {

}

/**
 * Transition hook configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record TransitionHookConfig(
        @JsonProperty("name")
        String name,
        @JsonProperty("type")
        String type,
        @JsonProperty("action")
        String action,
        @JsonProperty("async")
        Boolean async
        ) {

    public Boolean async() {
        return async != null ? async : false;
    }
}

/**
 * Interaction persistence configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record InteractionPersistenceConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("destination")
        String destination,
        @JsonProperty("retention")
        String retention,
        @JsonProperty("includePayload")
        Boolean includePayload
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }

    public String retention() {
        return retention != null ? retention : "90d";
    }

    public Boolean includePayload() {
        return includePayload != null ? includePayload : true;
    }
}

/**
 * Interaction history configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record InteractionHistoryConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("maxItems")
        Integer maxItems,
        @JsonProperty("includeDetails")
        Boolean includeDetails
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }

    public Integer maxItems() {
        return maxItems != null ? maxItems : 100;
    }
}

/**
 * Interaction metrics configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record InteractionMetricsConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("counters")
        List<String> counters,
        @JsonProperty("histograms")
        List<InteractionHistogramConfig> histograms,
        @JsonProperty("labels")
        List<String> labels,
        @JsonProperty("slo")
        InteractionSloConfig slo
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }
}

/**
 * Interaction histogram configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record InteractionHistogramConfig(
        @JsonProperty("name")
        String name,
        @JsonProperty("field")
        String field,
        @JsonProperty("buckets")
        List<Double> buckets
        ) {

}

/**
 * Interaction SLO configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record InteractionSloConfig(
        @JsonProperty("successRate")
        Double successRate,
        @JsonProperty("latencyP99")
        String latencyP99,
        @JsonProperty("alertOnBreach")
        Boolean alertOnBreach
        ) {

    public Double successRate() {
        return successRate != null ? successRate : 0.99;
    }

    public Boolean alertOnBreach() {
        return alertOnBreach != null ? alertOnBreach : true;
    }
}
