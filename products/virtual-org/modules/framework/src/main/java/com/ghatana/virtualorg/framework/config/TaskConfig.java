package com.ghatana.virtualorg.framework.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Task configuration POJO.
 *
 * <p>
 * <b>Purpose</b><br>
 * Represents a comprehensive task configuration loaded from YAML files. Defines
 * task types, lifecycle, assignment rules, execution parameters, and SLA
 * requirements. This configuration drives all task-related behavior in the
 * virtual organization.
 *
 * <p>
 * <b>Task Lifecycle</b><br>
 * 1. <b>Created</b>: Task instantiated from definition 2. <b>Queued</b>:
 * Waiting for assignment 3. <b>Assigned</b>: Assigned to an agent 4. <b>In
 * Progress</b>: Agent actively working 5. <b>Blocked</b>: Waiting for
 * dependencies 6. <b>Review</b>: Awaiting review/approval 7. <b>Completed</b>:
 * Successfully finished 8. <b>Failed</b>: Execution failed 9. <b>Cancelled</b>:
 * Explicitly cancelled
 *
 * <p>
 * <b>Usage Example (YAML)</b>
 * <pre>{@code
 * apiVersion: virtualorg.ghatana.com/v1
 * kind: TaskDefinition
 * metadata:
 *   name: code-implementation
 *   namespace: engineering
 * spec:
 *   displayName: "Code Implementation Task"
 *   category: development
 *   priority:
 *     default: medium
 *     escalationRules:
 *       - condition: "age > 4h"
 *         to: high
 *   assignment:
 *     strategy: skill-match
 *     requiredCapabilities: [java, spring-boot]
 *   sla:
 *     responseTime: 15m
 *     completionTime: 8h
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Task configuration data model
 * @doc.layer core
 * @doc.pattern Value Object
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TaskConfig(
        @JsonProperty("apiVersion")
        String apiVersion,
        @JsonProperty("kind")
        String kind,
        @JsonProperty("metadata")
        ConfigMetadata metadata,
        @JsonProperty("spec")
        TaskSpec spec
        ) {

    public boolean isValid() {
        return "virtualorg.ghatana.com/v1".equals(apiVersion)
                && "TaskDefinition".equals(kind)
                && metadata != null
                && spec != null;
    }

    public String getName() {
        return metadata != null ? metadata.name() : null;
    }
}

/**
 * Task specification containing all task configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record TaskSpec(
        @JsonProperty("displayName")
        String displayName,
        @JsonProperty("description")
        String description,
        @JsonProperty("category")
        String category,
        @JsonProperty("type")
        String type,
        @JsonProperty("version")
        String version,
        @JsonProperty("deprecated")
        Boolean deprecated,
        @JsonProperty("priority")
        TaskPriorityConfig priority,
        @JsonProperty("assignment")
        TaskAssignmentRulesConfig assignment,
        @JsonProperty("execution")
        TaskExecutionConfig execution,
        @JsonProperty("dependencies")
        TaskDependencyConfig dependencies,
        @JsonProperty("inputs")
        List<TaskInputConfig> inputs,
        @JsonProperty("outputs")
        List<TaskOutputConfig> outputs,
        @JsonProperty("sla")
        TaskSlaConfig sla,
        @JsonProperty("lifecycle")
        TaskLifecycleConfig lifecycle,
        @JsonProperty("subtasks")
        SubtaskConfig subtasks,
        @JsonProperty("approval")
        TaskApprovalConfig approval,
        @JsonProperty("notifications")
        TaskNotificationConfig notifications,
        @JsonProperty("metrics")
        TaskMetricsConfig metrics
        ) {

    public String type() {
        return type != null ? type : "standard";
    }

    public Boolean deprecated() {
        return deprecated != null ? deprecated : false;
    }
}

/**
 * Task priority configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record TaskPriorityConfig(
        @JsonProperty("default")
        String defaultPriority,
        @JsonProperty("allowOverride")
        Boolean allowOverride,
        @JsonProperty("overrideRoles")
        List<String> overrideRoles,
        @JsonProperty("escalationRules")
        List<PriorityEscalationRule> escalationRules,
        @JsonProperty("factors")
        List<PriorityFactorConfig> factors
        ) {

    public String defaultPriority() {
        return defaultPriority != null ? defaultPriority : "medium";
    }

    public Boolean allowOverride() {
        return allowOverride != null ? allowOverride : true;
    }
}

/**
 * Priority escalation rule.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record PriorityEscalationRule(
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

/**
 * Priority factor for dynamic priority calculation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record PriorityFactorConfig(
        @JsonProperty("name")
        String name,
        @JsonProperty("weight")
        Double weight,
        @JsonProperty("source")
        String source,
        @JsonProperty("mapping")
        Map<String, Integer> mapping
        ) {

}

/**
 * Task assignment rules configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record TaskAssignmentRulesConfig(
        @JsonProperty("strategy")
        String strategy,
        @JsonProperty("customStrategyClass")
        String customStrategyClass,
        @JsonProperty("requiredCapabilities")
        List<String> requiredCapabilities,
        @JsonProperty("preferredCapabilities")
        List<String> preferredCapabilities,
        @JsonProperty("requiredRoles")
        List<String> requiredRoles,
        @JsonProperty("departmentRestriction")
        List<String> departmentRestriction,
        @JsonProperty("agentRestriction")
        List<String> agentRestriction,
        @JsonProperty("loadBalancing")
        LoadBalancingConfig loadBalancing,
        @JsonProperty("affinity")
        TaskAffinityConfig affinity,
        @JsonProperty("fallback")
        AssignmentFallbackConfig fallback,
        @JsonProperty("timeout")
        String timeout
        ) {

    public String strategy() {
        return strategy != null ? strategy : "round-robin";
    }

    public String timeout() {
        return timeout != null ? timeout : "5m";
    }
}

/**
 * Load balancing configuration for task assignment.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record LoadBalancingConfig(
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

/**
 * Task affinity configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record TaskAffinityConfig(
        @JsonProperty("sameAgent")
        SameAgentAffinityConfig sameAgent,
        @JsonProperty("sameDepartment")
        Boolean sameDepartment,
        @JsonProperty("context")
        List<String> context
        ) {

    public Boolean sameDepartment() {
        return sameDepartment != null ? sameDepartment : false;
    }
}

/**
 * Same agent affinity configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record SameAgentAffinityConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("affinityKey")
        String affinityKey,
        @JsonProperty("window")
        String window
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : false;
    }

    public String window() {
        return window != null ? window : "1h";
    }
}

/**
 * Assignment fallback configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record AssignmentFallbackConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("strategy")
        String strategy,
        @JsonProperty("notifyOnFallback")
        Boolean notifyOnFallback,
        @JsonProperty("escalateTo")
        String escalateTo
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }

    public String strategy() {
        return strategy != null ? strategy : "any-available";
    }
}

/**
 * Task execution configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record TaskExecutionConfig(
        @JsonProperty("timeout")
        String timeout,
        @JsonProperty("retryPolicy")
        TaskRetryPolicyConfig retryPolicy,
        @JsonProperty("isolation")
        String isolation,
        @JsonProperty("resourceLimits")
        ResourceLimitsConfig resourceLimits,
        @JsonProperty("checkpointing")
        TaskCheckpointingConfig checkpointing,
        @JsonProperty("preExecution")
        List<TaskHookConfig> preExecution,
        @JsonProperty("postExecution")
        List<TaskHookConfig> postExecution,
        @JsonProperty("onFailure")
        List<TaskHookConfig> onFailure
        ) {

    public String timeout() {
        return timeout != null ? timeout : "1h";
    }

    public String isolation() {
        return isolation != null ? isolation : "shared";
    }
}

/**
 * Task retry policy configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record TaskRetryPolicyConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("maxRetries")
        Integer maxRetries,
        @JsonProperty("backoff")
        String backoff,
        @JsonProperty("initialDelay")
        String initialDelay,
        @JsonProperty("maxDelay")
        String maxDelay,
        @JsonProperty("retryableErrors")
        List<String> retryableErrors,
        @JsonProperty("nonRetryableErrors")
        List<String> nonRetryableErrors
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }

    public Integer maxRetries() {
        return maxRetries != null ? maxRetries : 3;
    }

    public String backoff() {
        return backoff != null ? backoff : "exponential";
    }
}

/**
 * Resource limits configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ResourceLimitsConfig(
        @JsonProperty("maxTokens")
        Integer maxTokens,
        @JsonProperty("maxApiCalls")
        Integer maxApiCalls,
        @JsonProperty("maxCost")
        Double maxCost,
        @JsonProperty("maxMemory")
        String maxMemory,
        @JsonProperty("maxTime")
        String maxTime
        ) {

}

/**
 * Task checkpointing configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record TaskCheckpointingConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("interval")
        String interval,
        @JsonProperty("onMilestones")
        List<String> onMilestones,
        @JsonProperty("resumable")
        Boolean resumable
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }

    public Boolean resumable() {
        return resumable != null ? resumable : true;
    }
}

/**
 * Task execution hook configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record TaskHookConfig(
        @JsonProperty("name")
        String name,
        @JsonProperty("type")
        String type,
        @JsonProperty("action")
        String action,
        @JsonProperty("condition")
        String condition,
        @JsonProperty("timeout")
        String timeout,
        @JsonProperty("failurePolicy")
        String failurePolicy
        ) {

    public String failurePolicy() {
        return failurePolicy != null ? failurePolicy : "continue";
    }
}

/**
 * Task dependency configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record TaskDependencyConfig(
        @JsonProperty("required")
        List<DependencyDefinition> required,
        @JsonProperty("optional")
        List<DependencyDefinition> optional,
        @JsonProperty("produces")
        List<String> produces,
        @JsonProperty("consumes")
        List<String> consumes,
        @JsonProperty("blocking")
        Boolean blocking,
        @JsonProperty("timeout")
        String timeout
        ) {

    public Boolean blocking() {
        return blocking != null ? blocking : true;
    }

    public String timeout() {
        return timeout != null ? timeout : "30m";
    }
}

/**
 * Task dependency definition.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record DependencyDefinition(
        @JsonProperty("taskType")
        String taskType,
        @JsonProperty("taskId")
        String taskId,
        @JsonProperty("status")
        List<String> status,
        @JsonProperty("dataMapping")
        Map<String, String> dataMapping
        ) {

}

/**
 * Task input configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record TaskInputConfig(
        @JsonProperty("name")
        String name,
        @JsonProperty("type")
        String type,
        @JsonProperty("description")
        String description,
        @JsonProperty("required")
        Boolean required,
        @JsonProperty("default")
        Object defaultValue,
        @JsonProperty("validation")
        InputValidationConfig validation,
        @JsonProperty("source")
        String source,
        @JsonProperty("sensitive")
        Boolean sensitive
        ) {

    public Boolean required() {
        return required != null ? required : false;
    }

    public String type() {
        return type != null ? type : "string";
    }

    public Boolean sensitive() {
        return sensitive != null ? sensitive : false;
    }
}

/**
 * Task output configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record TaskOutputConfig(
        @JsonProperty("name")
        String name,
        @JsonProperty("type")
        String type,
        @JsonProperty("description")
        String description,
        @JsonProperty("schema")
        Map<String, Object> schema,
        @JsonProperty("validation")
        OutputValidationConfig validation,
        @JsonProperty("retention")
        String retention,
        @JsonProperty("sensitive")
        Boolean sensitive
        ) {

    public String type() {
        return type != null ? type : "string";
    }

    public Boolean sensitive() {
        return sensitive != null ? sensitive : false;
    }
}

/**
 * Output validation configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record OutputValidationConfig(
        @JsonProperty("required")
        Boolean required,
        @JsonProperty("schemaRef")
        String schemaRef,
        @JsonProperty("customValidator")
        String customValidator
        ) {

    public Boolean required() {
        return required != null ? required : false;
    }
}

/**
 * Task SLA configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record TaskSlaConfig(
        @JsonProperty("responseTime")
        String responseTime,
        @JsonProperty("completionTime")
        String completionTime,
        @JsonProperty("byPriority")
        Map<String, TaskSlaByPriorityConfig> byPriority,
        @JsonProperty("breachActions")
        List<SlaBreachActionConfig> breachActions,
        @JsonProperty("tracking")
        SlaTrackingConfig tracking
        ) {

    public String responseTime() {
        return responseTime != null ? responseTime : "15m";
    }

    public String completionTime() {
        return completionTime != null ? completionTime : "8h";
    }
}

/**
 * SLA configuration by priority level.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record TaskSlaByPriorityConfig(
        @JsonProperty("responseTime")
        String responseTime,
        @JsonProperty("completionTime")
        String completionTime
        ) {

}

/**
 * SLA breach action configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record SlaBreachActionConfig(
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
 * SLA tracking configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record SlaTrackingConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("excludeWeekends")
        Boolean excludeWeekends,
        @JsonProperty("businessHours")
        BusinessHoursConfig businessHours,
        @JsonProperty("pauseOnBlocked")
        Boolean pauseOnBlocked
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }

    public Boolean pauseOnBlocked() {
        return pauseOnBlocked != null ? pauseOnBlocked : true;
    }
}

/**
 * Business hours configuration for SLA.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record BusinessHoursConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("start")
        String start,
        @JsonProperty("end")
        String end,
        @JsonProperty("timezone")
        String timezone,
        @JsonProperty("days")
        List<String> days
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : false;
    }

    public String start() {
        return start != null ? start : "09:00";
    }

    public String end() {
        return end != null ? end : "17:00";
    }

    public String timezone() {
        return timezone != null ? timezone : "UTC";
    }
}

/**
 * Task lifecycle configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record TaskLifecycleConfig(
        @JsonProperty("statuses")
        List<TaskStatusConfig> statuses,
        @JsonProperty("transitions")
        List<TaskTransitionConfig> transitions,
        @JsonProperty("customStateMachine")
        String customStateMachine,
        @JsonProperty("autoTransitions")
        List<AutoTransitionConfig> autoTransitions
        ) {

}

/**
 * Task status configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record TaskStatusConfig(
        @JsonProperty("name")
        String name,
        @JsonProperty("displayName")
        String displayName,
        @JsonProperty("type")
        String type,
        @JsonProperty("color")
        String color,
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
 * Task transition configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record TaskTransitionConfig(
        @JsonProperty("from")
        String from,
        @JsonProperty("to")
        String to,
        @JsonProperty("name")
        String name,
        @JsonProperty("requiredRoles")
        List<String> requiredRoles,
        @JsonProperty("condition")
        String condition,
        @JsonProperty("hooks")
        List<TaskHookConfig> hooks,
        @JsonProperty("requiresComment")
        Boolean requiresComment
        ) {

    public Boolean requiresComment() {
        return requiresComment != null ? requiresComment : false;
    }
}

/**
 * Automatic transition configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record AutoTransitionConfig(
        @JsonProperty("name")
        String name,
        @JsonProperty("from")
        String from,
        @JsonProperty("to")
        String to,
        @JsonProperty("condition")
        String condition,
        @JsonProperty("delay")
        String delay
        ) {

}

/**
 * Subtask configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record SubtaskConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("maxDepth")
        Integer maxDepth,
        @JsonProperty("inheritPriority")
        Boolean inheritPriority,
        @JsonProperty("inheritAssignment")
        Boolean inheritAssignment,
        @JsonProperty("propagateStatus")
        Boolean propagateStatus,
        @JsonProperty("templates")
        List<SubtaskTemplateConfig> templates
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }

    public Integer maxDepth() {
        return maxDepth != null ? maxDepth : 3;
    }

    public Boolean inheritPriority() {
        return inheritPriority != null ? inheritPriority : true;
    }
}

/**
 * Subtask template configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record SubtaskTemplateConfig(
        @JsonProperty("name")
        String name,
        @JsonProperty("taskType")
        String taskType,
        @JsonProperty("defaultInputs")
        Map<String, Object> defaultInputs,
        @JsonProperty("auto")
        Boolean auto
        ) {

    public Boolean auto() {
        return auto != null ? auto : false;
    }
}

/**
 * Task approval configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record TaskApprovalConfig(
        @JsonProperty("required")
        Boolean required,
        @JsonProperty("stages")
        List<ApprovalStageConfig> stages,
        @JsonProperty("timeout")
        String timeout,
        @JsonProperty("onTimeout")
        String onTimeout,
        @JsonProperty("autoApprove")
        AutoApproveConfig autoApprove
        ) {

    public Boolean required() {
        return required != null ? required : false;
    }

    public String timeout() {
        return timeout != null ? timeout : "24h";
    }

    public String onTimeout() {
        return onTimeout != null ? onTimeout : "escalate";
    }
}

/**
 * Approval stage configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ApprovalStageConfig(
        @JsonProperty("name")
        String name,
        @JsonProperty("order")
        Integer order,
        @JsonProperty("approvers")
        List<String> approvers,
        @JsonProperty("approverRoles")
        List<String> approverRoles,
        @JsonProperty("minApprovals")
        Integer minApprovals,
        @JsonProperty("unanimous")
        Boolean unanimous
        ) {

    public Integer minApprovals() {
        return minApprovals != null ? minApprovals : 1;
    }

    public Boolean unanimous() {
        return unanimous != null ? unanimous : false;
    }
}

/**
 * Auto-approve configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record AutoApproveConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("conditions")
        List<String> conditions,
        @JsonProperty("maxValue")
        Double maxValue
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : false;
    }
}

/**
 * Task notification configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record TaskNotificationConfig(
        @JsonProperty("onCreated")
        List<TaskNotificationRuleConfig> onCreated,
        @JsonProperty("onAssigned")
        List<TaskNotificationRuleConfig> onAssigned,
        @JsonProperty("onCompleted")
        List<TaskNotificationRuleConfig> onCompleted,
        @JsonProperty("onFailed")
        List<TaskNotificationRuleConfig> onFailed,
        @JsonProperty("onSlaWarning")
        List<TaskNotificationRuleConfig> onSlaWarning,
        @JsonProperty("onSlaBreach")
        List<TaskNotificationRuleConfig> onSlaBreach,
        @JsonProperty("custom")
        List<CustomNotificationConfig> custom
        ) {

}

/**
 * Task notification rule configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record TaskNotificationRuleConfig(
        @JsonProperty("channel")
        String channel,
        @JsonProperty("recipients")
        List<String> recipients,
        @JsonProperty("template")
        String template,
        @JsonProperty("condition")
        String condition
        ) {

}

/**
 * Custom notification configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record CustomNotificationConfig(
        @JsonProperty("name")
        String name,
        @JsonProperty("trigger")
        String trigger,
        @JsonProperty("condition")
        String condition,
        @JsonProperty("channel")
        String channel,
        @JsonProperty("recipients")
        List<String> recipients,
        @JsonProperty("template")
        String template
        ) {

}

/**
 * Task metrics configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record TaskMetricsConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("counters")
        List<String> counters,
        @JsonProperty("timers")
        List<TaskTimerConfig> timers,
        @JsonProperty("custom")
        List<CustomMetricConfig> custom,
        @JsonProperty("labels")
        List<String> labels
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }
}

/**
 * Task timer metric configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record TaskTimerConfig(
        @JsonProperty("name")
        String name,
        @JsonProperty("from")
        String from,
        @JsonProperty("to")
        String to,
        @JsonProperty("slo")
        String slo
        ) {

}
