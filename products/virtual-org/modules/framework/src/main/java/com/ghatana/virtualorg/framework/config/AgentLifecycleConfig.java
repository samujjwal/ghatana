package com.ghatana.virtualorg.framework.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Agent lifecycle configuration POJO.
 *
 * <p>
 * <b>Purpose</b><br>
 * Represents agent lifecycle configuration loaded from YAML files. Defines the
 * complete lifecycle management including spawning, health monitoring, scaling,
 * state management, and termination policies.
 *
 * <p>
 * <b>Lifecycle Phases</b><br>
 * 1. <b>Spawning</b>: Agent creation and initialization 2. <b>Running</b>:
 * Active agent monitoring and management 3. <b>Scaling</b>: Horizontal/vertical
 * scaling decisions 4. <b>Maintenance</b>: Updates, configuration changes 5.
 * <b>Termination</b>: Graceful shutdown and cleanup
 *
 * <p>
 * <b>Usage Example (YAML)</b>
 * <pre>{@code
 * apiVersion: virtualorg.ghatana.com/v1
 * kind: AgentLifecycle
 * metadata:
 *   name: engineering-agent-lifecycle
 *   namespace: engineering
 * spec:
 *   spawning:
 *     strategy: on-demand
 *     warmPool:
 *       enabled: true
 *       size: 5
 *   health:
 *     checks:
 *       - type: heartbeat
 *         interval: 30s
 *       - type: task-completion
 *         threshold: 0.9
 *   scaling:
 *     enabled: true
 *     minInstances: 2
 *     maxInstances: 20
 *     metrics:
 *       - type: queue-depth
 *         targetValue: 10
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Agent lifecycle configuration data model
 * @doc.layer core
 * @doc.pattern Value Object
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentLifecycleConfig(
        @JsonProperty("apiVersion")
        String apiVersion,
        @JsonProperty("kind")
        String kind,
        @JsonProperty("metadata")
        ConfigMetadata metadata,
        @JsonProperty("spec")
        AgentLifecycleSpec spec
        ) {

    public boolean isValid() {
        return "virtualorg.ghatana.com/v1".equals(apiVersion)
                && "AgentLifecycle".equals(kind)
                && metadata != null
                && spec != null;
    }

    public String getName() {
        return metadata != null ? metadata.name() : null;
    }
}

/**
 * Agent lifecycle specification.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record AgentLifecycleSpec(
        @JsonProperty("displayName")
        String displayName,
        @JsonProperty("description")
        String description,
        @JsonProperty("agentSelector")
        AgentSelectorConfig agentSelector,
        @JsonProperty("spawning")
        SpawningConfig spawning,
        @JsonProperty("initialization")
        InitializationConfig initialization,
        @JsonProperty("health")
        HealthConfig health,
        @JsonProperty("scaling")
        ScalingConfig scaling,
        @JsonProperty("state")
        StateManagementConfig state,
        @JsonProperty("maintenance")
        MaintenanceConfig maintenance,
        @JsonProperty("termination")
        TerminationConfig termination,
        @JsonProperty("recovery")
        RecoveryConfig recovery,
        @JsonProperty("observability")
        LifecycleObservabilityConfig observability
        ) {

}

/**
 * Agent selector configuration - which agents this lifecycle applies to.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record AgentSelectorConfig(
        @JsonProperty("matchLabels")
        Map<String, String> matchLabels,
        @JsonProperty("matchExpressions")
        List<MatchExpressionConfig> matchExpressions,
        @JsonProperty("agentTypes")
        List<String> agentTypes,
        @JsonProperty("departments")
        List<String> departments,
        @JsonProperty("roles")
        List<String> roles
        ) {

}

/**
 * Label match expression configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record MatchExpressionConfig(
        @JsonProperty("key")
        String key,
        @JsonProperty("operator")
        String operator,
        @JsonProperty("values")
        List<String> values
        ) {

}

/**
 * Agent spawning configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record SpawningConfig(
        @JsonProperty("strategy")
        String strategy,
        @JsonProperty("onDemand")
        OnDemandSpawningConfig onDemand,
        @JsonProperty("scheduled")
        ScheduledSpawningConfig scheduled,
        @JsonProperty("warmPool")
        WarmPoolConfig warmPool,
        @JsonProperty("placement")
        PlacementConfig placement,
        @JsonProperty("quotas")
        SpawningQuotasConfig quotas
        ) {

    public String strategy() {
        return strategy != null ? strategy : "on-demand";
    }
}

/**
 * On-demand spawning configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record OnDemandSpawningConfig(
        @JsonProperty("triggers")
        List<SpawnTriggerConfig> triggers,
        @JsonProperty("timeout")
        String timeout,
        @JsonProperty("retryPolicy")
        RetryPolicyConfig retryPolicy
        ) {

    public String timeout() {
        return timeout != null ? timeout : "5m";
    }
}

/**
 * Spawn trigger configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record SpawnTriggerConfig(
        @JsonProperty("type")
        String type,
        @JsonProperty("event")
        String event,
        @JsonProperty("condition")
        String condition,
        @JsonProperty("priority")
        String priority
        ) {

    public String type() {
        return type != null ? type : "event";
    }
}

/**
 * Scheduled spawning configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ScheduledSpawningConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("schedule")
        String schedule,
        @JsonProperty("timezone")
        String timezone,
        @JsonProperty("count")
        Integer count
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : false;
    }

    public String timezone() {
        return timezone != null ? timezone : "UTC";
    }
}

/**
 * Warm pool configuration for pre-spawned agents.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record WarmPoolConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("size")
        Integer size,
        @JsonProperty("minReady")
        Integer minReady,
        @JsonProperty("maxIdle")
        String maxIdle,
        @JsonProperty("refreshInterval")
        String refreshInterval,
        @JsonProperty("preloadState")
        Boolean preloadState
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : false;
    }

    public Integer size() {
        return size != null ? size : 5;
    }

    public String maxIdle() {
        return maxIdle != null ? maxIdle : "10m";
    }

    public Boolean preloadState() {
        return preloadState != null ? preloadState : true;
    }
}

/**
 * Agent placement configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record PlacementConfig(
        @JsonProperty("strategy")
        String strategy,
        @JsonProperty("affinity")
        AffinityConfig affinity,
        @JsonProperty("antiAffinity")
        AntiAffinityConfig antiAffinity,
        @JsonProperty("constraints")
        List<PlacementConstraintConfig> constraints
        ) {

    public String strategy() {
        return strategy != null ? strategy : "spread";
    }
}

/**
 * Affinity configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record AffinityConfig(
        @JsonProperty("type")
        String type,
        @JsonProperty("labels")
        Map<String, String> labels,
        @JsonProperty("weight")
        Integer weight
        ) {

}

/**
 * Anti-affinity configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record AntiAffinityConfig(
        @JsonProperty("type")
        String type,
        @JsonProperty("labels")
        Map<String, String> labels,
        @JsonProperty("required")
        Boolean required
        ) {

}

/**
 * Placement constraint configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record PlacementConstraintConfig(
        @JsonProperty("type")
        String type,
        @JsonProperty("key")
        String key,
        @JsonProperty("operator")
        String operator,
        @JsonProperty("values")
        List<String> values
        ) {

}

/**
 * Spawning quotas configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record SpawningQuotasConfig(
        @JsonProperty("maxPerDepartment")
        Integer maxPerDepartment,
        @JsonProperty("maxPerTenant")
        Integer maxPerTenant,
        @JsonProperty("maxPerOrganization")
        Integer maxPerOrganization,
        @JsonProperty("burstLimit")
        Integer burstLimit,
        @JsonProperty("burstWindow")
        String burstWindow
        ) {

    public Integer burstLimit() {
        return burstLimit != null ? burstLimit : 10;
    }

    public String burstWindow() {
        return burstWindow != null ? burstWindow : "1m";
    }
}

/**
 * Agent initialization configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record InitializationConfig(
        @JsonProperty("steps")
        List<InitStepConfig> steps,
        @JsonProperty("timeout")
        String timeout,
        @JsonProperty("retryOnFailure")
        Boolean retryOnFailure,
        @JsonProperty("maxRetries")
        Integer maxRetries,
        @JsonProperty("parallelSteps")
        Boolean parallelSteps
        ) {

    public String timeout() {
        return timeout != null ? timeout : "2m";
    }

    public Boolean retryOnFailure() {
        return retryOnFailure != null ? retryOnFailure : true;
    }

    public Integer maxRetries() {
        return maxRetries != null ? maxRetries : 3;
    }
}

/**
 * Initialization step configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record InitStepConfig(
        @JsonProperty("name")
        String name,
        @JsonProperty("type")
        String type,
        @JsonProperty("action")
        String action,
        @JsonProperty("timeout")
        String timeout,
        @JsonProperty("required")
        Boolean required,
        @JsonProperty("order")
        Integer order,
        @JsonProperty("params")
        Map<String, Object> params
        ) {

    public Boolean required() {
        return required != null ? required : true;
    }

    public Integer order() {
        return order != null ? order : 0;
    }
}

/**
 * Agent health monitoring configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record HealthConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("checks")
        List<HealthCheckDefinition> checks,
        @JsonProperty("aggregation")
        HealthAggregationConfig aggregation,
        @JsonProperty("reporting")
        HealthReportingConfig reporting
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }
}

/**
 * Health check definition.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record HealthCheckDefinition(
        @JsonProperty("name")
        String name,
        @JsonProperty("type")
        String type,
        @JsonProperty("interval")
        String interval,
        @JsonProperty("timeout")
        String timeout,
        @JsonProperty("threshold")
        Double threshold,
        @JsonProperty("failureThreshold")
        Integer failureThreshold,
        @JsonProperty("successThreshold")
        Integer successThreshold,
        @JsonProperty("endpoint")
        String endpoint,
        @JsonProperty("expectedResponse")
        Object expectedResponse,
        @JsonProperty("severity")
        String severity
        ) {

    public String interval() {
        return interval != null ? interval : "30s";
    }

    public String timeout() {
        return timeout != null ? timeout : "10s";
    }

    public Integer failureThreshold() {
        return failureThreshold != null ? failureThreshold : 3;
    }

    public Integer successThreshold() {
        return successThreshold != null ? successThreshold : 1;
    }

    public String severity() {
        return severity != null ? severity : "critical";
    }
}

/**
 * Health aggregation configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record HealthAggregationConfig(
        @JsonProperty("strategy")
        String strategy,
        @JsonProperty("weights")
        Map<String, Double> weights,
        @JsonProperty("unhealthyThreshold")
        Double unhealthyThreshold,
        @JsonProperty("degradedThreshold")
        Double degradedThreshold
        ) {

    public String strategy() {
        return strategy != null ? strategy : "weighted";
    }

    public Double unhealthyThreshold() {
        return unhealthyThreshold != null ? unhealthyThreshold : 0.5;
    }

    public Double degradedThreshold() {
        return degradedThreshold != null ? degradedThreshold : 0.8;
    }
}

/**
 * Health reporting configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record HealthReportingConfig(
        @JsonProperty("interval")
        String interval,
        @JsonProperty("destination")
        String destination,
        @JsonProperty("includeMetrics")
        Boolean includeMetrics,
        @JsonProperty("alertOnUnhealthy")
        Boolean alertOnUnhealthy
        ) {

    public String interval() {
        return interval != null ? interval : "1m";
    }

    public Boolean includeMetrics() {
        return includeMetrics != null ? includeMetrics : true;
    }

    public Boolean alertOnUnhealthy() {
        return alertOnUnhealthy != null ? alertOnUnhealthy : true;
    }
}

/**
 * Agent scaling configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ScalingConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("minInstances")
        Integer minInstances,
        @JsonProperty("maxInstances")
        Integer maxInstances,
        @JsonProperty("desiredInstances")
        Integer desiredInstances,
        @JsonProperty("horizontal")
        HorizontalScalingConfig horizontal,
        @JsonProperty("vertical")
        VerticalScalingConfig vertical,
        @JsonProperty("cooldown")
        ScalingCooldownConfig cooldown,
        @JsonProperty("predictive")
        PredictiveScalingConfig predictive
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : false;
    }

    public Integer minInstances() {
        return minInstances != null ? minInstances : 1;
    }

    public Integer maxInstances() {
        return maxInstances != null ? maxInstances : 10;
    }
}

/**
 * Horizontal scaling configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record HorizontalScalingConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("metrics")
        List<ScalingMetricConfig> metrics,
        @JsonProperty("scaleUpPolicy")
        ScalePolicy scaleUpPolicy,
        @JsonProperty("scaleDownPolicy")
        ScalePolicy scaleDownPolicy
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }
}

/**
 * Scaling metric configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ScalingMetricConfig(
        @JsonProperty("name")
        String name,
        @JsonProperty("type")
        String type,
        @JsonProperty("targetValue")
        Double targetValue,
        @JsonProperty("targetAverageValue")
        Double targetAverageValue,
        @JsonProperty("source")
        String source,
        @JsonProperty("query")
        String query
        ) {

    public String type() {
        return type != null ? type : "average";
    }
}

/**
 * Scale policy configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ScalePolicy(
        @JsonProperty("type")
        String type,
        @JsonProperty("value")
        Integer value,
        @JsonProperty("periodSeconds")
        Integer periodSeconds,
        @JsonProperty("stabilizationWindowSeconds")
        Integer stabilizationWindowSeconds
        ) {

    public String type() {
        return type != null ? type : "percent";
    }

    public Integer periodSeconds() {
        return periodSeconds != null ? periodSeconds : 60;
    }
}

/**
 * Vertical scaling configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record VerticalScalingConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("resources")
        List<ResourceScalingConfig> resources,
        @JsonProperty("updateMode")
        String updateMode
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : false;
    }

    public String updateMode() {
        return updateMode != null ? updateMode : "auto";
    }
}

/**
 * Resource scaling configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ResourceScalingConfig(
        @JsonProperty("name")
        String name,
        @JsonProperty("min")
        String min,
        @JsonProperty("max")
        String max,
        @JsonProperty("target")
        String target
        ) {

}

/**
 * Scaling cooldown configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ScalingCooldownConfig(
        @JsonProperty("scaleUpCooldown")
        String scaleUpCooldown,
        @JsonProperty("scaleDownCooldown")
        String scaleDownCooldown,
        @JsonProperty("global")
        String global
        ) {

    public String scaleUpCooldown() {
        return scaleUpCooldown != null ? scaleUpCooldown : "3m";
    }

    public String scaleDownCooldown() {
        return scaleDownCooldown != null ? scaleDownCooldown : "5m";
    }
}

/**
 * Predictive scaling configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record PredictiveScalingConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("model")
        String model,
        @JsonProperty("lookAheadMinutes")
        Integer lookAheadMinutes,
        @JsonProperty("historyDays")
        Integer historyDays
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : false;
    }

    public Integer lookAheadMinutes() {
        return lookAheadMinutes != null ? lookAheadMinutes : 30;
    }
}

/**
 * Agent state management configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record StateManagementConfig(
        @JsonProperty("persistence")
        StatePersistenceConfig persistence,
        @JsonProperty("checkpointing")
        CheckpointingConfig checkpointing,
        @JsonProperty("memory")
        AgentMemoryManagementConfig memory,
        @JsonProperty("context")
        ContextManagementConfig context
        ) {

}

/**
 * State persistence configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record StatePersistenceConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("type")
        String type,
        @JsonProperty("destination")
        String destination,
        @JsonProperty("syncInterval")
        String syncInterval,
        @JsonProperty("compression")
        Boolean compression
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }

    public String type() {
        return type != null ? type : "hybrid";
    }

    public String syncInterval() {
        return syncInterval != null ? syncInterval : "30s";
    }
}

/**
 * State checkpointing configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record CheckpointingConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("interval")
        String interval,
        @JsonProperty("maxCheckpoints")
        Integer maxCheckpoints,
        @JsonProperty("onEvents")
        List<String> onEvents,
        @JsonProperty("retention")
        String retention
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }

    public String interval() {
        return interval != null ? interval : "5m";
    }

    public Integer maxCheckpoints() {
        return maxCheckpoints != null ? maxCheckpoints : 10;
    }
}

/**
 * Agent memory management configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record AgentMemoryManagementConfig(
        @JsonProperty("shortTerm")
        ShortTermMemoryConfig shortTerm,
        @JsonProperty("longTerm")
        LongTermMemoryConfig longTerm,
        @JsonProperty("working")
        WorkingMemoryConfig working,
        @JsonProperty("evictionPolicy")
        String evictionPolicy
        ) {

    public String evictionPolicy() {
        return evictionPolicy != null ? evictionPolicy : "lru";
    }
}

/**
 * Short-term memory configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ShortTermMemoryConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("maxItems")
        Integer maxItems,
        @JsonProperty("ttl")
        String ttl,
        @JsonProperty("contextWindow")
        Integer contextWindow
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }

    public Integer maxItems() {
        return maxItems != null ? maxItems : 100;
    }

    public String ttl() {
        return ttl != null ? ttl : "1h";
    }
}

/**
 * Long-term memory configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record LongTermMemoryConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("type")
        String type,
        @JsonProperty("maxSize")
        String maxSize,
        @JsonProperty("retention")
        String retention,
        @JsonProperty("indexing")
        Boolean indexing
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }

    public String type() {
        return type != null ? type : "vector";
    }
}

/**
 * Working memory configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record WorkingMemoryConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("maxSize")
        String maxSize,
        @JsonProperty("clearOnTaskComplete")
        Boolean clearOnTaskComplete
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }

    public Boolean clearOnTaskComplete() {
        return clearOnTaskComplete != null ? clearOnTaskComplete : false;
    }
}

/**
 * Context management configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ContextManagementConfig(
        @JsonProperty("maxContextLength")
        Integer maxContextLength,
        @JsonProperty("truncationStrategy")
        String truncationStrategy,
        @JsonProperty("preserveSystemPrompt")
        Boolean preserveSystemPrompt,
        @JsonProperty("preserveRecentMessages")
        Integer preserveRecentMessages
        ) {

    public Integer maxContextLength() {
        return maxContextLength != null ? maxContextLength : 8000;
    }

    public String truncationStrategy() {
        return truncationStrategy != null ? truncationStrategy : "sliding-window";
    }

    public Boolean preserveSystemPrompt() {
        return preserveSystemPrompt != null ? preserveSystemPrompt : true;
    }
}

/**
 * Agent maintenance configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record MaintenanceConfig(
        @JsonProperty("windows")
        List<MaintenanceWindowConfig> windows,
        @JsonProperty("updates")
        UpdateConfig updates,
        @JsonProperty("draining")
        DrainingConfig draining
        ) {

}

/**
 * Maintenance window configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record MaintenanceWindowConfig(
        @JsonProperty("name")
        String name,
        @JsonProperty("schedule")
        String schedule,
        @JsonProperty("timezone")
        String timezone,
        @JsonProperty("duration")
        String duration,
        @JsonProperty("allowedOperations")
        List<String> allowedOperations
        ) {

    public String timezone() {
        return timezone != null ? timezone : "UTC";
    }
}

/**
 * Agent update configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record UpdateConfig(
        @JsonProperty("strategy")
        String strategy,
        @JsonProperty("maxUnavailable")
        Integer maxUnavailable,
        @JsonProperty("maxSurge")
        Integer maxSurge,
        @JsonProperty("rollbackOnFailure")
        Boolean rollbackOnFailure,
        @JsonProperty("progressDeadlineSeconds")
        Integer progressDeadlineSeconds
        ) {

    public String strategy() {
        return strategy != null ? strategy : "rolling";
    }

    public Boolean rollbackOnFailure() {
        return rollbackOnFailure != null ? rollbackOnFailure : true;
    }
}

/**
 * Agent draining configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record DrainingConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("gracePeriod")
        String gracePeriod,
        @JsonProperty("completeActiveTasks")
        Boolean completeActiveTasks,
        @JsonProperty("redistributeTasks")
        Boolean redistributeTasks
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }

    public String gracePeriod() {
        return gracePeriod != null ? gracePeriod : "5m";
    }

    public Boolean completeActiveTasks() {
        return completeActiveTasks != null ? completeActiveTasks : true;
    }
}

/**
 * Agent termination configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record TerminationConfig(
        @JsonProperty("gracePeriod")
        String gracePeriod,
        @JsonProperty("preStopHooks")
        List<HookConfig> preStopHooks,
        @JsonProperty("postStopHooks")
        List<HookConfig> postStopHooks,
        @JsonProperty("cleanup")
        CleanupConfig cleanup,
        @JsonProperty("finalizers")
        List<String> finalizers
        ) {

    public String gracePeriod() {
        return gracePeriod != null ? gracePeriod : "30s";
    }
}

/**
 * Lifecycle hook configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record HookConfig(
        @JsonProperty("name")
        String name,
        @JsonProperty("type")
        String type,
        @JsonProperty("action")
        String action,
        @JsonProperty("timeout")
        String timeout,
        @JsonProperty("failurePolicy")
        String failurePolicy
        ) {

    public String timeout() {
        return timeout != null ? timeout : "30s";
    }

    public String failurePolicy() {
        return failurePolicy != null ? failurePolicy : "ignore";
    }
}

/**
 * Cleanup configuration on agent termination.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record CleanupConfig(
        @JsonProperty("deleteState")
        Boolean deleteState,
        @JsonProperty("deleteCheckpoints")
        Boolean deleteCheckpoints,
        @JsonProperty("deleteMemory")
        Boolean deleteMemory,
        @JsonProperty("archiveFirst")
        Boolean archiveFirst,
        @JsonProperty("archiveDestination")
        String archiveDestination
        ) {

    public Boolean deleteState() {
        return deleteState != null ? deleteState : false;
    }

    public Boolean archiveFirst() {
        return archiveFirst != null ? archiveFirst : true;
    }
}

/**
 * Agent recovery configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record RecoveryConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("strategy")
        String strategy,
        @JsonProperty("maxAttempts")
        Integer maxAttempts,
        @JsonProperty("backoffPolicy")
        BackoffPolicyConfig backoffPolicy,
        @JsonProperty("restoreFromCheckpoint")
        Boolean restoreFromCheckpoint,
        @JsonProperty("notifyOnRecovery")
        Boolean notifyOnRecovery
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }

    public String strategy() {
        return strategy != null ? strategy : "restart";
    }

    public Integer maxAttempts() {
        return maxAttempts != null ? maxAttempts : 5;
    }

    public Boolean restoreFromCheckpoint() {
        return restoreFromCheckpoint != null ? restoreFromCheckpoint : true;
    }
}

/**
 * Backoff policy configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record BackoffPolicyConfig(
        @JsonProperty("type")
        String type,
        @JsonProperty("initialDelay")
        String initialDelay,
        @JsonProperty("maxDelay")
        String maxDelay,
        @JsonProperty("multiplier")
        Double multiplier,
        @JsonProperty("jitter")
        Double jitter
        ) {

    public String type() {
        return type != null ? type : "exponential";
    }

    public String initialDelay() {
        return initialDelay != null ? initialDelay : "1s";
    }

    public String maxDelay() {
        return maxDelay != null ? maxDelay : "5m";
    }

    public Double multiplier() {
        return multiplier != null ? multiplier : 2.0;
    }
}

/**
 * Lifecycle observability configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record LifecycleObservabilityConfig(
        @JsonProperty("metrics")
        LifecycleMetricsConfig metrics,
        @JsonProperty("tracing")
        LifecycleTracingConfig tracing,
        @JsonProperty("logging")
        LifecycleLoggingConfig logging,
        @JsonProperty("events")
        LifecycleEventsConfig events
        ) {

}

/**
 * Lifecycle metrics configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record LifecycleMetricsConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("prefix")
        String prefix,
        @JsonProperty("labels")
        List<String> labels,
        @JsonProperty("histograms")
        List<String> histograms
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }
}

/**
 * Lifecycle tracing configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record LifecycleTracingConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("sampleRate")
        Double sampleRate,
        @JsonProperty("propagation")
        String propagation
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }

    public Double sampleRate() {
        return sampleRate != null ? sampleRate : 0.1;
    }
}

/**
 * Lifecycle logging configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record LifecycleLoggingConfig(
        @JsonProperty("level")
        String level,
        @JsonProperty("structured")
        Boolean structured,
        @JsonProperty("includeState")
        Boolean includeState,
        @JsonProperty("redactFields")
        List<String> redactFields
        ) {

    public String level() {
        return level != null ? level : "INFO";
    }

    public Boolean structured() {
        return structured != null ? structured : true;
    }
}

/**
 * Lifecycle events configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record LifecycleEventsConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("destination")
        String destination,
        @JsonProperty("events")
        List<String> events
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }
}
