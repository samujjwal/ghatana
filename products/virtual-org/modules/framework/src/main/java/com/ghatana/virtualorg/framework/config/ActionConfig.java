package com.ghatana.virtualorg.framework.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Action configuration POJO.
 *
 * <p>
 * <b>Purpose</b><br>
 * Represents an action configuration loaded from YAML files. Actions are
 * discrete units of work that agents can perform. This configuration defines
 * the complete behavior, inputs, outputs, validation, and execution rules.
 *
 * <p>
 * <b>Action Types</b><br>
 * - <b>ai</b>: AI-powered actions (LLM calls, reasoning, generation) -
 * <b>tool</b>: External tool invocations (APIs, services) - <b>workflow</b>:
 * Nested workflow execution - <b>human</b>: Human-in-the-loop actions requiring
 * approval - <b>composite</b>: Actions composed of multiple sub-actions -
 * <b>conditional</b>: Actions with branching logic - <b>loop</b>: Iterative
 * actions over collections
 *
 * <p>
 * <b>Usage Example (YAML)</b>
 * <pre>{@code
 * apiVersion: virtualorg.ghatana.com/v1
 * kind: Action
 * metadata:
 *   name: code-review
 *   namespace: engineering
 * spec:
 *   displayName: "Code Review"
 *   type: ai
 *   description: "AI-powered code review action"
 *   inputs:
 *     - name: code
 *       type: string
 *       required: true
 *   outputs:
 *     - name: review_comments
 *       type: object
 *   execution:
 *     timeout: "30m"
 *     retryPolicy:
 *       maxRetries: 3
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Action configuration data model
 * @doc.layer core
 * @doc.pattern Value Object
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ActionConfig(
        @JsonProperty("apiVersion")
        String apiVersion,
        @JsonProperty("kind")
        String kind,
        @JsonProperty("metadata")
        ConfigMetadata metadata,
        @JsonProperty("spec")
        ActionSpec spec
        ) {

    public boolean isValid() {
        return "virtualorg.ghatana.com/v1".equals(apiVersion)
                && "Action".equals(kind)
                && metadata != null
                && spec != null
                && spec.type() != null;
    }

    public String getName() {
        return metadata != null ? metadata.name() : null;
    }

    public String getActionType() {
        return spec != null ? spec.type() : null;
    }
}

/**
 * Action specification containing all action details.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ActionSpec(
        @JsonProperty("displayName")
        String displayName,
        @JsonProperty("type")
        String type,
        @JsonProperty("description")
        String description,
        @JsonProperty("category")
        String category,
        @JsonProperty("version")
        String version,
        @JsonProperty("deprecated")
        Boolean deprecated,
        @JsonProperty("deprecationMessage")
        String deprecationMessage,
        @JsonProperty("inputs")
        List<ActionInputConfig> inputs,
        @JsonProperty("outputs")
        List<ActionOutputConfig> outputs,
        @JsonProperty("validation")
        ActionValidationConfig validation,
        @JsonProperty("execution")
        ActionExecutionConfig execution,
        @JsonProperty("ai")
        ActionAiConfig ai,
        @JsonProperty("tool")
        ActionToolConfig tool,
        @JsonProperty("workflow")
        ActionWorkflowConfig workflow,
        @JsonProperty("composite")
        ActionCompositeConfig composite,
        @JsonProperty("conditional")
        ActionConditionalConfig conditional,
        @JsonProperty("loop")
        ActionLoopConfig loop,
        @JsonProperty("permissions")
        ActionPermissionsConfig permissions,
        @JsonProperty("audit")
        ActionAuditConfig audit,
        @JsonProperty("metrics")
        ActionMetricsConfig metrics
        ) {

    public String type() {
        return type != null ? type : "ai";
    }

    public Boolean deprecated() {
        return deprecated != null ? deprecated : false;
    }

    public String version() {
        return version != null ? version : "1.0.0";
    }
}

/**
 * Action input parameter configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ActionInputConfig(
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
        @JsonProperty("sensitive")
        Boolean sensitive,
        @JsonProperty("source")
        String source
        ) {

    public Boolean required() {
        return required != null ? required : false;
    }

    public Boolean sensitive() {
        return sensitive != null ? sensitive : false;
    }

    public String type() {
        return type != null ? type : "string";
    }
}

/**
 * Input validation rules.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record InputValidationConfig(
        @JsonProperty("pattern")
        String pattern,
        @JsonProperty("minLength")
        Integer minLength,
        @JsonProperty("maxLength")
        Integer maxLength,
        @JsonProperty("minimum")
        Double minimum,
        @JsonProperty("maximum")
        Double maximum,
        @JsonProperty("enum")
        List<String> enumValues,
        @JsonProperty("format")
        String format,
        @JsonProperty("customValidator")
        String customValidator
        ) {

}

/**
 * Action output parameter configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ActionOutputConfig(
        @JsonProperty("name")
        String name,
        @JsonProperty("type")
        String type,
        @JsonProperty("description")
        String description,
        @JsonProperty("schema")
        Map<String, Object> schema,
        @JsonProperty("sensitive")
        Boolean sensitive,
        @JsonProperty("retention")
        String retention
        ) {

    public String type() {
        return type != null ? type : "string";
    }

    public Boolean sensitive() {
        return sensitive != null ? sensitive : false;
    }
}

/**
 * Action-level validation configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ActionValidationConfig(
        @JsonProperty("preConditions")
        List<ConditionConfig> preConditions,
        @JsonProperty("postConditions")
        List<ConditionConfig> postConditions,
        @JsonProperty("invariants")
        List<String> invariants,
        @JsonProperty("customValidatorClass")
        String customValidatorClass
        ) {

}

/**
 * Action execution configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ActionExecutionConfig(
        @JsonProperty("timeout")
        String timeout,
        @JsonProperty("retryPolicy")
        RetryPolicyConfig retryPolicy,
        @JsonProperty("circuitBreaker")
        CircuitBreakerConfig circuitBreaker,
        @JsonProperty("rateLimit")
        RateLimitConfig rateLimit,
        @JsonProperty("isolation")
        String isolation,
        @JsonProperty("priority")
        String priority,
        @JsonProperty("async")
        Boolean async,
        @JsonProperty("idempotent")
        Boolean idempotent,
        @JsonProperty("caching")
        CachingConfig caching
        ) {

    public String timeout() {
        return timeout != null ? timeout : "5m";
    }

    public Boolean async() {
        return async != null ? async : false;
    }

    public Boolean idempotent() {
        return idempotent != null ? idempotent : false;
    }
}

/**
 * Retry policy configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record RetryPolicyConfig(
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

/**
 * Circuit breaker configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record CircuitBreakerConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("failureThreshold")
        Integer failureThreshold,
        @JsonProperty("resetTimeout")
        String resetTimeout,
        @JsonProperty("halfOpenRequests")
        Integer halfOpenRequests
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : false;
    }

    public Integer failureThreshold() {
        return failureThreshold != null ? failureThreshold : 5;
    }

    public String resetTimeout() {
        return resetTimeout != null ? resetTimeout : "30s";
    }
}

/**
 * Rate limit configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record RateLimitConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("requestsPerSecond")
        Integer requestsPerSecond,
        @JsonProperty("burstSize")
        Integer burstSize,
        @JsonProperty("scope")
        String scope
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : false;
    }

    public String scope() {
        return scope != null ? scope : "global";
    }
}

/**
 * Caching configuration for action results.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record CachingConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("ttl")
        String ttl,
        @JsonProperty("keyTemplate")
        String keyTemplate,
        @JsonProperty("invalidateOn")
        List<String> invalidateOn
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : false;
    }

    public String ttl() {
        return ttl != null ? ttl : "1h";
    }
}

/**
 * AI-specific action configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ActionAiConfig(
        @JsonProperty("provider")
        String provider,
        @JsonProperty("model")
        String model,
        @JsonProperty("promptTemplate")
        String promptTemplate,
        @JsonProperty("systemPrompt")
        String systemPrompt,
        @JsonProperty("responseFormat")
        String responseFormat,
        @JsonProperty("maxTokens")
        Integer maxTokens,
        @JsonProperty("temperature")
        Double temperature,
        @JsonProperty("topP")
        Double topP,
        @JsonProperty("stopSequences")
        List<String> stopSequences,
        @JsonProperty("structuredOutput")
        StructuredOutputConfig structuredOutput,
        @JsonProperty("reasoning")
        ReasoningConfig reasoning,
        @JsonProperty("tools")
        List<String> tools
        ) {

    public String provider() {
        return provider != null ? provider : "openai";
    }

    public Double temperature() {
        return temperature != null ? temperature : 0.7;
    }
}

/**
 * Structured output configuration for AI actions.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record StructuredOutputConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("schema")
        Map<String, Object> schema,
        @JsonProperty("schemaRef")
        String schemaRef,
        @JsonProperty("strict")
        Boolean strict
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : false;
    }

    public Boolean strict() {
        return strict != null ? strict : true;
    }
}

/**
 * AI reasoning configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ReasoningConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("strategy")
        String strategy,
        @JsonProperty("maxSteps")
        Integer maxSteps,
        @JsonProperty("showThinking")
        Boolean showThinking
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : false;
    }

    public String strategy() {
        return strategy != null ? strategy : "chain-of-thought";
    }

    public Integer maxSteps() {
        return maxSteps != null ? maxSteps : 5;
    }
}

/**
 * Tool-specific action configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ActionToolConfig(
        @JsonProperty("toolId")
        String toolId,
        @JsonProperty("endpoint")
        String endpoint,
        @JsonProperty("method")
        String method,
        @JsonProperty("headers")
        Map<String, String> headers,
        @JsonProperty("authentication")
        ToolAuthConfig authentication,
        @JsonProperty("inputMapping")
        Map<String, String> inputMapping,
        @JsonProperty("outputMapping")
        Map<String, String> outputMapping,
        @JsonProperty("healthCheck")
        HealthCheckConfig healthCheck
        ) {

    public String method() {
        return method != null ? method : "POST";
    }
}

/**
 * Tool authentication configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ToolAuthConfig(
        @JsonProperty("type")
        String type,
        @JsonProperty("secretRef")
        String secretRef,
        @JsonProperty("headerName")
        String headerName,
        @JsonProperty("tokenPrefix")
        String tokenPrefix
        ) {

    public String type() {
        return type != null ? type : "bearer";
    }
}

/**
 * Health check configuration for tools.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record HealthCheckConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("endpoint")
        String endpoint,
        @JsonProperty("interval")
        String interval,
        @JsonProperty("timeout")
        String timeout
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }

    public String interval() {
        return interval != null ? interval : "30s";
    }
}

/**
 * Workflow-type action configuration (nested workflow).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ActionWorkflowConfig(
        @JsonProperty("workflowRef")
        String workflowRef,
        @JsonProperty("inputMapping")
        Map<String, String> inputMapping,
        @JsonProperty("outputMapping")
        Map<String, String> outputMapping,
        @JsonProperty("waitForCompletion")
        Boolean waitForCompletion,
        @JsonProperty("onComplete")
        String onComplete
        ) {

    public Boolean waitForCompletion() {
        return waitForCompletion != null ? waitForCompletion : true;
    }
}

/**
 * Composite action configuration (multiple sub-actions).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ActionCompositeConfig(
        @JsonProperty("strategy")
        String strategy,
        @JsonProperty("actions")
        List<SubActionConfig> actions,
        @JsonProperty("aggregation")
        AggregationConfig aggregation,
        @JsonProperty("failureHandling")
        String failureHandling
        ) {

    public String strategy() {
        return strategy != null ? strategy : "sequential";
    }

    public String failureHandling() {
        return failureHandling != null ? failureHandling : "fail-fast";
    }
}

/**
 * Sub-action reference within composite action.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record SubActionConfig(
        @JsonProperty("actionRef")
        String actionRef,
        @JsonProperty("name")
        String name,
        @JsonProperty("inputMapping")
        Map<String, String> inputMapping,
        @JsonProperty("outputMapping")
        Map<String, String> outputMapping,
        @JsonProperty("condition")
        String condition,
        @JsonProperty("optional")
        Boolean optional
        ) {

    public Boolean optional() {
        return optional != null ? optional : false;
    }
}

/**
 * Aggregation configuration for composite results.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record AggregationConfig(
        @JsonProperty("strategy")
        String strategy,
        @JsonProperty("outputName")
        String outputName,
        @JsonProperty("customAggregator")
        String customAggregator
        ) {

    public String strategy() {
        return strategy != null ? strategy : "merge";
    }
}

/**
 * Conditional action configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ActionConditionalConfig(
        @JsonProperty("condition")
        String condition,
        @JsonProperty("then")
        SubActionConfig thenAction,
        @JsonProperty("else")
        SubActionConfig elseAction,
        @JsonProperty("branches")
        List<BranchConfig> branches
        ) {

}

/**
 * Branch configuration for multi-way conditionals.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record BranchConfig(
        @JsonProperty("condition")
        String condition,
        @JsonProperty("action")
        SubActionConfig action,
        @JsonProperty("name")
        String name
        ) {

}

/**
 * Loop action configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ActionLoopConfig(
        @JsonProperty("type")
        String type,
        @JsonProperty("collection")
        String collection,
        @JsonProperty("itemVariable")
        String itemVariable,
        @JsonProperty("indexVariable")
        String indexVariable,
        @JsonProperty("maxIterations")
        Integer maxIterations,
        @JsonProperty("parallelism")
        Integer parallelism,
        @JsonProperty("condition")
        String condition,
        @JsonProperty("action")
        SubActionConfig action,
        @JsonProperty("aggregation")
        AggregationConfig aggregation
        ) {

    public String type() {
        return type != null ? type : "foreach";
    }

    public String itemVariable() {
        return itemVariable != null ? itemVariable : "item";
    }

    public Integer maxIterations() {
        return maxIterations != null ? maxIterations : 1000;
    }

    public Integer parallelism() {
        return parallelism != null ? parallelism : 1;
    }
}

/**
 * Action permissions configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ActionPermissionsConfig(
        @JsonProperty("requiredRoles")
        List<String> requiredRoles,
        @JsonProperty("requiredPermissions")
        List<String> requiredPermissions,
        @JsonProperty("requiredCapabilities")
        List<String> requiredCapabilities,
        @JsonProperty("denyRoles")
        List<String> denyRoles
        ) {

}

/**
 * Action audit configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ActionAuditConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("logLevel")
        String logLevel,
        @JsonProperty("includeInputs")
        Boolean includeInputs,
        @JsonProperty("includeOutputs")
        Boolean includeOutputs,
        @JsonProperty("redactFields")
        List<String> redactFields,
        @JsonProperty("retentionDays")
        Integer retentionDays
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }

    public String logLevel() {
        return logLevel != null ? logLevel : "INFO";
    }

    public Boolean includeInputs() {
        return includeInputs != null ? includeInputs : true;
    }

    public Boolean includeOutputs() {
        return includeOutputs != null ? includeOutputs : true;
    }
}

/**
 * Action metrics configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ActionMetricsConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("customMetrics")
        List<CustomMetricConfig> customMetrics,
        @JsonProperty("sla")
        ActionSlaConfig sla
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }
}

/**
 * Custom metric definition for actions.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record CustomMetricConfig(
        @JsonProperty("name")
        String name,
        @JsonProperty("type")
        String type,
        @JsonProperty("description")
        String description,
        @JsonProperty("labels")
        List<String> labels
        ) {

}

/**
 * SLA configuration for actions.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ActionSlaConfig(
        @JsonProperty("targetDuration")
        String targetDuration,
        @JsonProperty("successRateTarget")
        Double successRateTarget,
        @JsonProperty("alertOnBreach")
        Boolean alertOnBreach
        ) {

    public Double successRateTarget() {
        return successRateTarget != null ? successRateTarget : 0.99;
    }

    public Boolean alertOnBreach() {
        return alertOnBreach != null ? alertOnBreach : true;
    }
}
