package com.ghatana.datacloud.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.*;

/**
 * Represents a typed node in a DAG pipeline with validated operator contracts.
 *
 * <p><b>Purpose</b><br>
 * Defines a strongly-typed pipeline node with operator validation, schema contracts,
 * and configuration validation. Each node has a specific type and operator class
 * that must implement the required interface.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * PipelineNode node = PipelineNode.builder()
 *     .id("transform-customer")
 *     .type(NodeType.TRANSFORM)
 *     .operator(CustomerTransformOperator.class.getName())
 *     .configuration(Map.of(
 *         "rules", List.of("normalize_email", "validate_phone"),
 *         "errorHandling", "FAIL_FAST"
 *     ))
 *     .inputSchema(RawCustomerData.class.getName())
 *     .outputSchema(ValidatedCustomerData.class.getName())
 *     .validationRules(List.of(
 *         ValidationRule.builder()
 *             .type("SCHEMA_VALIDATION")
 *             .severity("ERROR")
 *             .build()
 *     ))
 *     .build();
 * }</pre>
 *
 * @see PipelineDefinition
 * @see PipelineEdge
 * @doc.type class
 * @doc.purpose Typed pipeline node with validated operator contracts
 * @doc.layer product
 * @doc.pattern Domain Model (JPA Embeddable)
 */
@jakarta.persistence.Embeddable
public class PipelineNode {

    @Column(nullable = false, length = 255)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NodeType type;

    /**
     * Fully qualified class name of the operator implementation.
     * Must implement the appropriate operator interface for the node type.
     */
    @Column(name = "operator_class", nullable = false, length = 500)
    private String operator;

    /**
     * Node configuration as JSONB.
     * Typed configuration validated against operator schema.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> configuration = new HashMap<>();

    /**
     * Input schema class name for type validation.
     * Must match the output schema of upstream nodes.
     */
    @Column(name = "input_schema", length = 500)
    private String inputSchema;

    /**
     * Output schema class name for type validation.
     * Must match the input schema of downstream nodes.
     */
    @Column(name = "output_schema", length = 500)
    private String outputSchema;

    /**
     * Validation rules for this node.
     * Includes schema validation, data quality checks, business rules.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_rules", columnDefinition = "jsonb")
    private List<ValidationRule> validationRules = new ArrayList<>();

    /**
     * Error handling strategy.
     * Values: FAIL_FAST, CONTINUE, RETRY, SKIP
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "error_handling", length = 50)
    private ErrorHandlingStrategy errorHandling = ErrorHandlingStrategy.FAIL_FAST;

    /**
     * Retry configuration for transient failures.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "retry_config", columnDefinition = "jsonb")
    private RetryConfiguration retryConfig;

    /**
     * Resource requirements for this node.
     * CPU, memory, and other resource constraints.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resource_requirements", columnDefinition = "jsonb")
    private ResourceRequirements resourceRequirements;

    /**
     * Node timeout in seconds.
     */
    @Column(name = "timeout_seconds")
    private Integer timeoutSeconds;

    /**
     * Parallelism level for this node.
     */
    @Column(name = "parallelism")
    private Integer parallelism = 1;

    /**
     * Node description.
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Node metadata.
     * Additional key-value pairs for node configuration.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * Node type enumeration.
     */
    public enum NodeType {
        SOURCE,      // Data input nodes (connectors, file readers, etc.)
        TRANSFORM,   // Data transformation nodes (filters, mappers, aggregators)
        SINK,        // Data output nodes (database writers, file writers, etc.)
        CONDITION,   // Conditional routing nodes
        MERGE,       // Data merging nodes
        SPLIT,       // Data splitting nodes
        VALIDATE,    // Data validation nodes
        ENRICH,      // Data enrichment nodes
        CUSTOM       // Custom operator nodes
    }

    /**
     * Error handling strategy enumeration.
     */
    public enum ErrorHandlingStrategy {
        FAIL_FAST,   // Stop pipeline execution on error
        CONTINUE,    // Continue with next nodes, log error
        RETRY,       // Retry the node execution
        SKIP         // Skip this node and continue
    }

    /**
     * Validation rule for node execution.
     */
    public record ValidationRule(
        String type,
        String severity,
        Map<String, Object> configuration,
        String description
    ) {}

    /**
     * Retry configuration.
     */
    public record RetryConfiguration(
        int maxAttempts,
        long backoffMs,
        double backoffMultiplier,
        Set<Class<? extends Exception>> retryableExceptions
    ) {}

    /**
     * Resource requirements.
     */
    public record ResourceRequirements(
        int cpuCores,
        long memoryMB,
        String storageType,
        long storageMB,
        Map<String, String> labels
    ) {}

    // ============ Getters & Setters ============

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public NodeType getType() {
        return type;
    }

    public void setType(NodeType type) {
        this.type = type;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public Map<String, Object> getConfiguration() {
        return configuration;
    }

    public void setConfiguration(Map<String, Object> configuration) {
        this.configuration = configuration;
    }

    public String getInputSchema() {
        return inputSchema;
    }

    public void setInputSchema(String inputSchema) {
        this.inputSchema = inputSchema;
    }

    public String getOutputSchema() {
        return outputSchema;
    }

    public void setOutputSchema(String outputSchema) {
        this.outputSchema = outputSchema;
    }

    public List<ValidationRule> getValidationRules() {
        return validationRules;
    }

    public void setValidationRules(List<ValidationRule> validationRules) {
        this.validationRules = validationRules;
    }

    public ErrorHandlingStrategy getErrorHandling() {
        return errorHandling;
    }

    public void setErrorHandling(ErrorHandlingStrategy errorHandling) {
        this.errorHandling = errorHandling;
    }

    public RetryConfiguration getRetryConfig() {
        return retryConfig;
    }

    public void setRetryConfig(RetryConfiguration retryConfig) {
        this.retryConfig = retryConfig;
    }

    public ResourceRequirements getResourceRequirements() {
        return resourceRequirements;
    }

    public void setResourceRequirements(ResourceRequirements resourceRequirements) {
        this.resourceRequirements = resourceRequirements;
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(Integer timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public Integer getParallelism() {
        return parallelism;
    }

    public void setParallelism(Integer parallelism) {
        this.parallelism = parallelism;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    // ============ Builder Pattern ============

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private NodeType type;
        private String operator;
        private Map<String, Object> configuration = new HashMap<>();
        private String inputSchema;
        private String outputSchema;
        private List<ValidationRule> validationRules = new ArrayList<>();
        private ErrorHandlingStrategy errorHandling = ErrorHandlingStrategy.FAIL_FAST;
        private RetryConfiguration retryConfig;
        private ResourceRequirements resourceRequirements;
        private Integer timeoutSeconds;
        private Integer parallelism = 1;
        private String description;
        private Map<String, Object> metadata = new HashMap<>();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder type(NodeType type) {
            this.type = type;
            return this;
        }

        public Builder operator(String operator) {
            this.operator = operator;
            return this;
        }

        public Builder configuration(Map<String, Object> configuration) {
            this.configuration = configuration;
            return this;
        }

        public Builder inputSchema(String inputSchema) {
            this.inputSchema = inputSchema;
            return this;
        }

        public Builder outputSchema(String outputSchema) {
            this.outputSchema = outputSchema;
            return this;
        }

        public Builder validationRules(List<ValidationRule> validationRules) {
            this.validationRules = validationRules;
            return this;
        }

        public Builder errorHandling(ErrorHandlingStrategy errorHandling) {
            this.errorHandling = errorHandling;
            return this;
        }

        public Builder retryConfig(RetryConfiguration retryConfig) {
            this.retryConfig = retryConfig;
            return this;
        }

        public Builder resourceRequirements(ResourceRequirements resourceRequirements) {
            this.resourceRequirements = resourceRequirements;
            return this;
        }

        public Builder timeoutSeconds(Integer timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
            return this;
        }

        public Builder parallelism(Integer parallelism) {
            this.parallelism = parallelism;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public PipelineNode build() {
            PipelineNode node = new PipelineNode();
            node.id = this.id;
            node.type = this.type;
            node.operator = this.operator;
            node.configuration = this.configuration;
            node.inputSchema = this.inputSchema;
            node.outputSchema = this.outputSchema;
            node.validationRules = this.validationRules;
            node.errorHandling = this.errorHandling;
            node.retryConfig = this.retryConfig;
            node.resourceRequirements = this.resourceRequirements;
            node.timeoutSeconds = this.timeoutSeconds;
            node.parallelism = this.parallelism;
            node.description = this.description;
            node.metadata = this.metadata;
            return node;
        }
    }

    @Override
    public String toString() {
        return "PipelineNode{" +
                "id='" + id + '\'' +
                ", type=" + type +
                ", operator='" + operator + '\'' +
                ", parallelism=" + parallelism +
                ", timeoutSeconds=" + timeoutSeconds +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PipelineNode that = (PipelineNode) o;
        return Objects.equals(id, that.id) &&
                type == that.type &&
                Objects.equals(operator, that.operator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, operator);
    }
}
