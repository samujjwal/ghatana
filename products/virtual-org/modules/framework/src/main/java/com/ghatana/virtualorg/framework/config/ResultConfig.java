package com.ghatana.virtualorg.framework.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Result processing configuration POJO.
 *
 * <p>
 * <b>Purpose</b><br>
 * Represents result processing configuration loaded from YAML files. Defines
 * how agent outputs, task results, and workflow completions are processed,
 * validated, transformed, routed, and stored.
 *
 * <p>
 * <b>Processing Pipeline</b><br>
 * Results flow through a configurable pipeline: 1. <b>Validation</b>: Schema
 * validation, business rules 2. <b>Transformation</b>: Format conversion,
 * enrichment 3. <b>Routing</b>: Destination determination based on rules 4.
 * <b>Aggregation</b>: Combining multiple results 5. <b>Storage</b>: Persistence
 * and archival 6. <b>Notification</b>: Alerting stakeholders
 *
 * <p>
 * <b>Usage Example (YAML)</b>
 * <pre>{@code
 * apiVersion: virtualorg.ghatana.com/v1
 * kind: ResultProcessor
 * metadata:
 *   name: code-review-results
 *   namespace: engineering
 * spec:
 *   source:
 *     type: action
 *     actionRef: code-review
 *   pipeline:
 *     validation:
 *       enabled: true
 *       schema: code-review-output-schema
 *     transformation:
 *       - type: enrich
 *         with: repository-metadata
 *     routing:
 *       rules:
 *         - condition: "result.severity == 'critical'"
 *           destination: escalation-queue
 *   storage:
 *     type: event-cloud
 *     retention: 90d
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Result processing configuration data model
 * @doc.layer core
 * @doc.pattern Value Object
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResultConfig(
        @JsonProperty("apiVersion")
        String apiVersion,
        @JsonProperty("kind")
        String kind,
        @JsonProperty("metadata")
        ConfigMetadata metadata,
        @JsonProperty("spec")
        ResultSpec spec
        ) {

    public boolean isValid() {
        return "virtualorg.ghatana.com/v1".equals(apiVersion)
                && "ResultProcessor".equals(kind)
                && metadata != null
                && spec != null;
    }

    public String getName() {
        return metadata != null ? metadata.name() : null;
    }
}

/**
 * Result processing specification.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ResultSpec(
        @JsonProperty("displayName")
        String displayName,
        @JsonProperty("description")
        String description,
        @JsonProperty("source")
        ResultSourceConfig source,
        @JsonProperty("pipeline")
        ResultPipelineConfig pipeline,
        @JsonProperty("routing")
        ResultRoutingConfig routing,
        @JsonProperty("aggregation")
        ResultAggregationConfig aggregation,
        @JsonProperty("storage")
        ResultStorageConfig storage,
        @JsonProperty("notification")
        ResultNotificationConfig notification,
        @JsonProperty("errorHandling")
        ResultErrorHandlingConfig errorHandling,
        @JsonProperty("metrics")
        ResultMetricsConfig metrics
        ) {

}

/**
 * Result source configuration - where results come from.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ResultSourceConfig(
        @JsonProperty("type")
        String type,
        @JsonProperty("actionRef")
        String actionRef,
        @JsonProperty("workflowRef")
        String workflowRef,
        @JsonProperty("agentRef")
        String agentRef,
        @JsonProperty("taskType")
        String taskType,
        @JsonProperty("eventType")
        String eventType,
        @JsonProperty("filter")
        ResultFilterConfig filter
        ) {

    public String type() {
        return type != null ? type : "action";
    }
}

/**
 * Result filter configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ResultFilterConfig(
        @JsonProperty("conditions")
        List<ConditionConfig> conditions,
        @JsonProperty("includeStatuses")
        List<String> includeStatuses,
        @JsonProperty("excludeStatuses")
        List<String> excludeStatuses,
        @JsonProperty("minConfidence")
        Double minConfidence,
        @JsonProperty("customFilter")
        String customFilter
        ) {

}

/**
 * Result processing pipeline configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ResultPipelineConfig(
        @JsonProperty("validation")
        PipelineValidationConfig validation,
        @JsonProperty("transformation")
        List<TransformationConfig> transformation,
        @JsonProperty("enrichment")
        List<EnrichmentConfig> enrichment,
        @JsonProperty("normalization")
        NormalizationConfig normalization,
        @JsonProperty("classification")
        ClassificationConfig classification
        ) {

}

/**
 * Pipeline validation configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record PipelineValidationConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("schemaRef")
        String schemaRef,
        @JsonProperty("schema")
        Map<String, Object> schema,
        @JsonProperty("rules")
        List<ValidationRuleConfig> rules,
        @JsonProperty("onInvalid")
        String onInvalid,
        @JsonProperty("quarantineDestination")
        String quarantineDestination
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }

    public String onInvalid() {
        return onInvalid != null ? onInvalid : "reject";
    }
}

/**
 * Validation rule configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ValidationRuleConfig(
        @JsonProperty("name")
        String name,
        @JsonProperty("field")
        String field,
        @JsonProperty("rule")
        String rule,
        @JsonProperty("message")
        String message,
        @JsonProperty("severity")
        String severity
        ) {

    public String severity() {
        return severity != null ? severity : "error";
    }
}

/**
 * Result transformation configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record TransformationConfig(
        @JsonProperty("name")
        String name,
        @JsonProperty("type")
        String type,
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("mapping")
        Map<String, String> mapping,
        @JsonProperty("template")
        String template,
        @JsonProperty("expression")
        String expression,
        @JsonProperty("customTransformerClass")
        String customTransformerClass,
        @JsonProperty("order")
        Integer order
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }

    public String type() {
        return type != null ? type : "mapping";
    }

    public Integer order() {
        return order != null ? order : 0;
    }
}

/**
 * Result enrichment configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record EnrichmentConfig(
        @JsonProperty("name")
        String name,
        @JsonProperty("type")
        String type,
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("source")
        String source,
        @JsonProperty("sourceEndpoint")
        String sourceEndpoint,
        @JsonProperty("fields")
        List<EnrichmentFieldConfig> fields,
        @JsonProperty("caching")
        EnrichmentCachingConfig caching,
        @JsonProperty("fallback")
        Map<String, Object> fallback
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }

    public String type() {
        return type != null ? type : "lookup";
    }
}

/**
 * Enrichment field mapping.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record EnrichmentFieldConfig(
        @JsonProperty("sourceField")
        String sourceField,
        @JsonProperty("targetField")
        String targetField,
        @JsonProperty("lookupKey")
        String lookupKey
        ) {

}

/**
 * Enrichment caching configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record EnrichmentCachingConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("ttl")
        String ttl,
        @JsonProperty("maxSize")
        Integer maxSize
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }

    public String ttl() {
        return ttl != null ? ttl : "1h";
    }
}

/**
 * Result normalization configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record NormalizationConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("targetFormat")
        String targetFormat,
        @JsonProperty("dateFormat")
        String dateFormat,
        @JsonProperty("timezone")
        String timezone,
        @JsonProperty("nullHandling")
        String nullHandling,
        @JsonProperty("trimStrings")
        Boolean trimStrings,
        @JsonProperty("lowercase")
        List<String> lowercase,
        @JsonProperty("uppercase")
        List<String> uppercase
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }

    public String dateFormat() {
        return dateFormat != null ? dateFormat : "ISO8601";
    }

    public String timezone() {
        return timezone != null ? timezone : "UTC";
    }
}

/**
 * Result classification configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ClassificationConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("type")
        String type,
        @JsonProperty("categories")
        List<CategoryConfig> categories,
        @JsonProperty("aiModel")
        String aiModel,
        @JsonProperty("confidenceThreshold")
        Double confidenceThreshold,
        @JsonProperty("defaultCategory")
        String defaultCategory
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : false;
    }

    public Double confidenceThreshold() {
        return confidenceThreshold != null ? confidenceThreshold : 0.8;
    }
}

/**
 * Classification category configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record CategoryConfig(
        @JsonProperty("name")
        String name,
        @JsonProperty("description")
        String description,
        @JsonProperty("rules")
        List<String> rules,
        @JsonProperty("priority")
        Integer priority
        ) {

}

/**
 * Result routing configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ResultRoutingConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("defaultDestination")
        String defaultDestination,
        @JsonProperty("rules")
        List<RoutingRuleConfig> rules,
        @JsonProperty("deadLetterDestination")
        String deadLetterDestination,
        @JsonProperty("multicast")
        Boolean multicast
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }

    public Boolean multicast() {
        return multicast != null ? multicast : false;
    }
}

/**
 * Routing rule configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record RoutingRuleConfig(
        @JsonProperty("name")
        String name,
        @JsonProperty("condition")
        String condition,
        @JsonProperty("destination")
        String destination,
        @JsonProperty("destinationType")
        String destinationType,
        @JsonProperty("priority")
        Integer priority,
        @JsonProperty("transform")
        String transform,
        @JsonProperty("metadata")
        Map<String, String> metadata
        ) {

    public String destinationType() {
        return destinationType != null ? destinationType : "queue";
    }

    public Integer priority() {
        return priority != null ? priority : 0;
    }
}

/**
 * Result aggregation configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ResultAggregationConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("type")
        String type,
        @JsonProperty("groupBy")
        List<String> groupBy,
        @JsonProperty("window")
        AggregationWindowConfig window,
        @JsonProperty("operations")
        List<AggregationOperationConfig> operations,
        @JsonProperty("outputDestination")
        String outputDestination
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : false;
    }

    public String type() {
        return type != null ? type : "batch";
    }
}

/**
 * Aggregation window configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record AggregationWindowConfig(
        @JsonProperty("type")
        String type,
        @JsonProperty("size")
        String size,
        @JsonProperty("slide")
        String slide,
        @JsonProperty("maxCount")
        Integer maxCount,
        @JsonProperty("flushOnComplete")
        Boolean flushOnComplete
        ) {

    public String type() {
        return type != null ? type : "time";
    }

    public String size() {
        return size != null ? size : "5m";
    }

    public Boolean flushOnComplete() {
        return flushOnComplete != null ? flushOnComplete : true;
    }
}

/**
 * Aggregation operation configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record AggregationOperationConfig(
        @JsonProperty("name")
        String name,
        @JsonProperty("type")
        String type,
        @JsonProperty("field")
        String field,
        @JsonProperty("outputField")
        String outputField,
        @JsonProperty("customAggregator")
        String customAggregator
        ) {

}

/**
 * Result storage configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ResultStorageConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("type")
        String type,
        @JsonProperty("destination")
        String destination,
        @JsonProperty("retention")
        RetentionConfig retention,
        @JsonProperty("partitioning")
        PartitioningConfig partitioning,
        @JsonProperty("compression")
        CompressionConfig compression,
        @JsonProperty("encryption")
        EncryptionConfig encryption,
        @JsonProperty("indexing")
        IndexingConfig indexing
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }

    public String type() {
        return type != null ? type : "event-cloud";
    }
}

/**
 * Data retention configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record RetentionConfig(
        @JsonProperty("duration")
        String duration,
        @JsonProperty("policy")
        String policy,
        @JsonProperty("archiveAfter")
        String archiveAfter,
        @JsonProperty("archiveDestination")
        String archiveDestination,
        @JsonProperty("deleteAfter")
        String deleteAfter
        ) {

    public String duration() {
        return duration != null ? duration : "90d";
    }

    public String policy() {
        return policy != null ? policy : "time-based";
    }
}

/**
 * Data partitioning configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record PartitioningConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("strategy")
        String strategy,
        @JsonProperty("keys")
        List<String> keys,
        @JsonProperty("timePartition")
        String timePartition
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }

    public String strategy() {
        return strategy != null ? strategy : "time";
    }

    public String timePartition() {
        return timePartition != null ? timePartition : "daily";
    }
}

/**
 * Data compression configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record CompressionConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("algorithm")
        String algorithm,
        @JsonProperty("level")
        Integer level
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }

    public String algorithm() {
        return algorithm != null ? algorithm : "zstd";
    }
}

/**
 * Data encryption configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record EncryptionConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("algorithm")
        String algorithm,
        @JsonProperty("keyRef")
        String keyRef,
        @JsonProperty("rotationPeriod")
        String rotationPeriod
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : false;
    }

    public String algorithm() {
        return algorithm != null ? algorithm : "AES-256-GCM";
    }
}

/**
 * Data indexing configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record IndexingConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("fields")
        List<IndexFieldConfig> fields,
        @JsonProperty("fullTextSearch")
        FullTextSearchConfig fullTextSearch
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }
}

/**
 * Index field configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record IndexFieldConfig(
        @JsonProperty("field")
        String field,
        @JsonProperty("type")
        String type,
        @JsonProperty("sortable")
        Boolean sortable,
        @JsonProperty("filterable")
        Boolean filterable
        ) {

    public Boolean sortable() {
        return sortable != null ? sortable : false;
    }

    public Boolean filterable() {
        return filterable != null ? filterable : true;
    }
}

/**
 * Full-text search configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record FullTextSearchConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("fields")
        List<String> fields,
        @JsonProperty("analyzer")
        String analyzer
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : false;
    }
}

/**
 * Result notification configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ResultNotificationConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("channels")
        List<NotificationChannelConfig> channels,
        @JsonProperty("rules")
        List<NotificationRuleConfig> rules,
        @JsonProperty("throttling")
        ThrottlingConfig throttling
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : false;
    }
}

/**
 * Notification channel configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record NotificationChannelConfig(
        @JsonProperty("name")
        String name,
        @JsonProperty("type")
        String type,
        @JsonProperty("endpoint")
        String endpoint,
        @JsonProperty("template")
        String template,
        @JsonProperty("credentials")
        String credentials
        ) {

}

/**
 * Notification rule configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record NotificationRuleConfig(
        @JsonProperty("name")
        String name,
        @JsonProperty("condition")
        String condition,
        @JsonProperty("channels")
        List<String> channels,
        @JsonProperty("priority")
        String priority,
        @JsonProperty("template")
        String template,
        @JsonProperty("recipients")
        List<String> recipients
        ) {

    public String priority() {
        return priority != null ? priority : "normal";
    }
}

/**
 * Notification throttling configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ThrottlingConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("maxPerHour")
        Integer maxPerHour,
        @JsonProperty("groupBy")
        List<String> groupBy,
        @JsonProperty("digest")
        DigestConfig digest
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }

    public Integer maxPerHour() {
        return maxPerHour != null ? maxPerHour : 60;
    }
}

/**
 * Notification digest configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record DigestConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("interval")
        String interval,
        @JsonProperty("maxItems")
        Integer maxItems
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }

    public String interval() {
        return interval != null ? interval : "15m";
    }
}

/**
 * Result error handling configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ResultErrorHandlingConfig(
        @JsonProperty("strategy")
        String strategy,
        @JsonProperty("maxRetries")
        Integer maxRetries,
        @JsonProperty("retryDelay")
        String retryDelay,
        @JsonProperty("deadLetterQueue")
        String deadLetterQueue,
        @JsonProperty("alertOnFailure")
        Boolean alertOnFailure,
        @JsonProperty("fallback")
        FallbackConfig fallback
        ) {

    public String strategy() {
        return strategy != null ? strategy : "retry-then-dlq";
    }

    public Integer maxRetries() {
        return maxRetries != null ? maxRetries : 3;
    }

    public Boolean alertOnFailure() {
        return alertOnFailure != null ? alertOnFailure : true;
    }
}

/**
 * Fallback configuration for result processing failures.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record FallbackConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("type")
        String type,
        @JsonProperty("value")
        Object value,
        @JsonProperty("handlerClass")
        String handlerClass
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : false;
    }
}

/**
 * Result metrics configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ResultMetricsConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("counters")
        List<String> counters,
        @JsonProperty("histograms")
        List<HistogramMetricConfig> histograms,
        @JsonProperty("gauges")
        List<String> gauges,
        @JsonProperty("labels")
        List<String> labels
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }
}

/**
 * Histogram metric configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record HistogramMetricConfig(
        @JsonProperty("name")
        String name,
        @JsonProperty("field")
        String field,
        @JsonProperty("buckets")
        List<Double> buckets
        ) {

}
