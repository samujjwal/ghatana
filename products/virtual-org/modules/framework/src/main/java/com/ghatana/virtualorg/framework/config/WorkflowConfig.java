package com.ghatana.virtualorg.framework.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Workflow configuration POJO.
 *
 * <p>
 * <b>Purpose</b><br>
 * Represents a workflow configuration loaded from YAML files. Maps to the
 * Virtual-Org workflow configuration schema.
 *
 * @doc.type record
 * @doc.purpose Workflow configuration data model
 * @doc.layer core
 * @doc.pattern Value Object
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkflowConfig(
        @JsonProperty("apiVersion")
        String apiVersion,
        @JsonProperty("kind")
        String kind,
        @JsonProperty("metadata")
        ConfigMetadata metadata,
        @JsonProperty("spec")
        WorkflowSpec spec
        ) {

    public boolean isValid() {
        return "virtualorg.ghatana.com/v1".equals(apiVersion)
                && "Workflow".equals(kind)
                && metadata != null
                && spec != null;
    }

    public String getName() {
        return metadata != null ? metadata.name() : null;
    }
}

/**
 * Workflow specification.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record WorkflowSpec(
        @JsonProperty("displayName")
        String displayName,
        @JsonProperty("description")
        String description,
        @JsonProperty("trigger")
        TriggerConfig trigger,
        @JsonProperty("context")
        Map<String, Object> context,
        @JsonProperty("steps")
        List<WorkflowStepConfig> steps,
        @JsonProperty("errorHandling")
        ErrorHandlingConfig errorHandling,
        @JsonProperty("approvals")
        List<ApprovalConfig> approvals,
        @JsonProperty("escalation")
        List<EscalationConfig> escalation,
        @JsonProperty("completion")
        CompletionConfig completion
        ) {

}

/**
 * Workflow trigger configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record TriggerConfig(
        @JsonProperty("type")
        String type,
        @JsonProperty("event")
        String event,
        @JsonProperty("cron")
        String cron,
        @JsonProperty("conditions")
        List<ConditionConfig> conditions
        ) {

}

/**
 * Workflow step configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record WorkflowStepConfig(
        @JsonProperty("id")
        String id,
        @JsonProperty("name")
        String name,
        @JsonProperty("department")
        String department,
        @JsonProperty("agent")
        String agent,
        @JsonProperty("action")
        String action,
        @JsonProperty("waitFor")
        List<String> waitFor,
        @JsonProperty("parallel")
        Boolean parallel,
        @JsonProperty("inputs")
        Map<String, Object> inputs,
        @JsonProperty("outputs")
        List<String> outputs,
        @JsonProperty("timeout")
        String timeout,
        @JsonProperty("condition")
        String condition,
        @JsonProperty("hitl")
        StepHitlConfig hitl,
        @JsonProperty("onTimeout")
        String onTimeout
        ) {

    public Boolean parallel() {
        return parallel != null ? parallel : false;
    }
}

/**
 * Step-level HITL configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record StepHitlConfig(
        @JsonProperty("required")
        Boolean required,
        @JsonProperty("approvers")
        List<String> approvers,
        @JsonProperty("timeout")
        String timeout
        ) {

    public Boolean required() {
        return required != null ? required : false;
    }
}

/**
 * Error handling configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ErrorHandlingConfig(
        @JsonProperty("strategy")
        String strategy,
        @JsonProperty("maxRetries")
        Integer maxRetries,
        @JsonProperty("retryDelay")
        String retryDelay,
        @JsonProperty("compensationSteps")
        List<CompensationStepConfig> compensationSteps
        ) {

    public String strategy() {
        return strategy != null ? strategy : "fail-fast";
    }

    public Integer maxRetries() {
        return maxRetries != null ? maxRetries : 3;
    }
}

/**
 * Compensation step configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record CompensationStepConfig(
        @JsonProperty("triggerOn")
        List<String> triggerOn,
        @JsonProperty("action")
        String action,
        @JsonProperty("agents")
        List<String> agents,
        @JsonProperty("message")
        String message
        ) {

}

/**
 * Approval configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ApprovalConfig(
        @JsonProperty("before")
        String before,
        @JsonProperty("approvers")
        List<String> approvers,
        @JsonProperty("timeout")
        String timeout,
        @JsonProperty("onTimeout")
        String onTimeout
        ) {

}

/**
 * Escalation configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record EscalationConfig(
        @JsonProperty("after")
        String after,
        @JsonProperty("if")
        String condition,
        @JsonProperty("action")
        String action,
        @JsonProperty("to")
        String to,
        @JsonProperty("message")
        String message
        ) {

}

/**
 * Completion configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record CompletionConfig(
        @JsonProperty("successCondition")
        String successCondition,
        @JsonProperty("artifacts")
        List<ArtifactConfig> artifacts
        ) {

}

/**
 * Artifact configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ArtifactConfig(
        @JsonProperty("name")
        String name,
        @JsonProperty("path")
        String path,
        @JsonProperty("retention")
        String retention
        ) {

}
