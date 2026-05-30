package com.ghatana.datacloud.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.*;

/**
 * Represents a typed edge in a DAG pipeline with validated data flow contracts.
 *
 * <p><b>Purpose</b><br>
 * Defines a strongly-typed pipeline edge with schema compatibility validation,
 * data flow constraints, and transformation rules. Edges connect nodes and
 * ensure type-safe data flow through the pipeline.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * PipelineEdge edge = PipelineEdge.builder()
 *     .from("extract-customer")
 *     .to("transform-customer")
 *     .condition("status == 'ACTIVE'")
 *     .transformationRules(List.of(
 *         TransformationRule.builder()
 *             .type("FIELD_MAPPING")
 *             .configuration(Map.of("source", "raw_name", "target", "customer_name"))
 *             .build()
 *     ))
 *     .schemaValidation(true)
 *     .build();
 * }</pre>
 *
 * @see PipelineDefinition
 * @see PipelineNode
 * @doc.type class
 * @doc.purpose Typed pipeline edge with validated data flow contracts
 * @doc.layer product
 * @doc.pattern Domain Model (JPA Embeddable)
 */
@jakarta.persistence.Embeddable
public class PipelineEdge {

    @Column(name = "from_node", nullable = false, length = 255)
    private String from;

    @Column(name = "to_node", nullable = false, length = 255)
    private String to;

    /**
     * Edge condition for conditional routing.
     * Expression evaluated to determine if data should flow through this edge.
     */
    @Column(name = "condition", columnDefinition = "TEXT")
    private String condition;

    /**
     * Transformation rules applied to data flowing through this edge.
     * Field mappings, type conversions, data enrichment.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "transformation_rules", columnDefinition = "jsonb")
    private List<TransformationRule> transformationRules = new ArrayList<>();

    /**
     * Schema validation flag.
     * If true, validates that source node output schema matches target node input schema.
     */
    @Column(name = "schema_validation", nullable = false)
    private Boolean schemaValidation = true;

    /**
     * Data quality rules for this edge.
     * Completeness, validity, consistency checks.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "quality_rules", columnDefinition = "jsonb")
    private List<DataQualityRule> qualityRules = new ArrayList<>();

    /**
     * Edge priority for routing decisions.
     * Higher values indicate higher priority when multiple edges exist.
     */
    @Column(name = "priority")
    private Integer priority = 0;

    /**
     * Edge capacity limits.
     * Maximum records per batch, maximum throughput, etc.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "capacity_limits", columnDefinition = "jsonb")
    private CapacityLimits capacityLimits;

    /**
     * Error handling strategy for edge failures.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "error_handling", length = 50)
    private EdgeErrorHandlingStrategy errorHandling = EdgeErrorHandlingStrategy.FAIL_FAST;

    /**
     * Edge metadata.
     * Additional key-value pairs for edge configuration.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * Edge description.
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Edge error handling strategy enumeration.
     */
    public enum EdgeErrorHandlingStrategy {
        FAIL_FAST,     // Stop pipeline execution on edge error
        DROP_RECORD,   // Drop problematic records and continue
        ROUTE_TO_DLQ,  // Send to dead letter queue
        RETRY          // Retry the edge operation
    }

    /**
     * Transformation rule for edge data processing.
     */
    public record TransformationRule(
        String type,
        Map<String, Object> configuration,
        String description,
        boolean required
    ) {}

    /**
     * Data quality rule for edge validation.
     */
    public record DataQualityRule(
        String type,
        String severity,
        Map<String, Object> configuration,
        String description
    ) {}

    /**
     * Capacity limits for edge processing.
     */
    public record CapacityLimits(
        Integer maxBatchSize,
        Long maxRecordsPerSecond,
        Long maxBytesPerSecond,
        Integer maxConcurrentBatches
    ) {}

    // ============ Getters & Setters ============

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public List<TransformationRule> getTransformationRules() {
        return transformationRules;
    }

    public void setTransformationRules(List<TransformationRule> transformationRules) {
        this.transformationRules = transformationRules;
    }

    public Boolean getSchemaValidation() {
        return schemaValidation;
    }

    public void setSchemaValidation(Boolean schemaValidation) {
        this.schemaValidation = schemaValidation;
    }

    public List<DataQualityRule> getQualityRules() {
        return qualityRules;
    }

    public void setQualityRules(List<DataQualityRule> qualityRules) {
        this.qualityRules = qualityRules;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public CapacityLimits getCapacityLimits() {
        return capacityLimits;
    }

    public void setCapacityLimits(CapacityLimits capacityLimits) {
        this.capacityLimits = capacityLimits;
    }

    public EdgeErrorHandlingStrategy getErrorHandling() {
        return errorHandling;
    }

    public void setErrorHandling(EdgeErrorHandlingStrategy errorHandling) {
        this.errorHandling = errorHandling;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    // ============ Builder Pattern ============

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String from;
        private String to;
        private String condition;
        private List<TransformationRule> transformationRules = new ArrayList<>();
        private Boolean schemaValidation = true;
        private List<DataQualityRule> qualityRules = new ArrayList<>();
        private Integer priority = 0;
        private CapacityLimits capacityLimits;
        private EdgeErrorHandlingStrategy errorHandling = EdgeErrorHandlingStrategy.FAIL_FAST;
        private Map<String, Object> metadata = new HashMap<>();
        private String description;

        public Builder from(String from) {
            this.from = from;
            return this;
        }

        public Builder to(String to) {
            this.to = to;
            return this;
        }

        public Builder condition(String condition) {
            this.condition = condition;
            return this;
        }

        public Builder transformationRules(List<TransformationRule> transformationRules) {
            this.transformationRules = transformationRules;
            return this;
        }

        public Builder schemaValidation(Boolean schemaValidation) {
            this.schemaValidation = schemaValidation;
            return this;
        }

        public Builder qualityRules(List<DataQualityRule> qualityRules) {
            this.qualityRules = qualityRules;
            return this;
        }

        public Builder priority(Integer priority) {
            this.priority = priority;
            return this;
        }

        public Builder capacityLimits(CapacityLimits capacityLimits) {
            this.capacityLimits = capacityLimits;
            return this;
        }

        public Builder errorHandling(EdgeErrorHandlingStrategy errorHandling) {
            this.errorHandling = errorHandling;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public PipelineEdge build() {
            PipelineEdge edge = new PipelineEdge();
            edge.from = this.from;
            edge.to = this.to;
            edge.condition = this.condition;
            edge.transformationRules = this.transformationRules;
            edge.schemaValidation = this.schemaValidation;
            edge.qualityRules = this.qualityRules;
            edge.priority = this.priority;
            edge.capacityLimits = this.capacityLimits;
            edge.errorHandling = this.errorHandling;
            edge.metadata = this.metadata;
            edge.description = this.description;
            return edge;
        }
    }

    @Override
    public String toString() {
        return "PipelineEdge{" +
                "from='" + from + '\'' +
                ", to='" + to + '\'' +
                ", priority=" + priority +
                ", schemaValidation=" + schemaValidation +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PipelineEdge that = (PipelineEdge) o;
        return Objects.equals(from, that.from) &&
                Objects.equals(to, that.to);
    }

    @Override
    public int hashCode() {
        return Objects.hash(from, to);
    }
}
