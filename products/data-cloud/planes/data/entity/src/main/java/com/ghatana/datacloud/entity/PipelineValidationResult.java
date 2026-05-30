package com.ghatana.datacloud.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.*;

/**
 * Represents the comprehensive validation result for a typed DAG pipeline.
 *
 * <p><b>Purpose</b><br>
 * Stores detailed validation results including DAG structure validation,
 * schema compatibility checks, operator validation, and performance constraints.
 * Provides actionable error messages and warnings for pipeline debugging.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * PipelineValidationResult result = PipelineValidationResult.builder()
 *     .valid(false)
 *     .validationTimestamp(Instant.now())
 *     .dagValidation(DAGValidationResult.builder()
 *         .hasCycles(true)
 *         .orphanedNodes(List.of("node-5"))
 *         .build())
 *     .schemaValidation(SchemaValidationResult.builder()
 *         .incompatibleSchemas(List.of(
 *             SchemaMismatch.builder()
 *                 .fromNode("extract")
 *                 .toNode("transform")
 *                 .expectedSchema("CustomerInput")
 *                 .actualSchema("RawCustomer")
 *                 .build()
 *         ))
 *         .build())
 *     .errors(List.of("Pipeline contains cycles"))
 *     .warnings(List.of("Node 'transform' has no timeout configured"))
 *     .build();
 * }</pre>
 *
 * @see PipelineDefinition
 * @see PipelineNode
 * @see PipelineEdge
 * @doc.type class
 * @doc.purpose Comprehensive validation result for typed DAG pipelines
 * @doc.layer product
 * @doc.pattern Domain Model (JPA Embeddable)
 */
@jakarta.persistence.Embeddable
public class PipelineValidationResult {

    @Column(nullable = false)
    private Boolean valid;

    @Column(name = "validation_timestamp", nullable = false)
    private Instant validationTimestamp;

    /**
     * DAG structure validation results.
     * Checks for cycles, connectivity, orphaned nodes.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dag_validation", columnDefinition = "jsonb")
    private DAGValidationResult dagValidation;

    /**
     * Schema compatibility validation results.
     * Validates input/output schema compatibility between connected nodes.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "schema_validation", columnDefinition = "jsonb")
    private SchemaValidationResult schemaValidation;

    /**
     * Operator validation results.
     * Validates operator class existence, interface compliance, configuration.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "operator_validation", columnDefinition = "jsonb")
    private OperatorValidationResult operatorValidation;

    /**
     * Performance constraint validation results.
     * Validates resource requirements, capacity limits, performance expectations.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "performance_validation", columnDefinition = "jsonb")
    private PerformanceValidationResult performanceValidation;

    /**
     * Security validation results.
     * Validates access controls, data privacy, security policies.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "security_validation", columnDefinition = "jsonb")
    private SecurityValidationResult securityValidation;

    /**
     * Validation errors that prevent pipeline execution.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<ValidationError> errors = new ArrayList<>();

    /**
     * Validation warnings that should be addressed but don't prevent execution.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<ValidationWarning> warnings = new ArrayList<>();

    /**
     * Validation recommendations for optimization.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recommendations", columnDefinition = "jsonb")
    private List<ValidationRecommendation> recommendations = new ArrayList<>();

    /**
     * Validation metrics.
     * Performance metrics for the validation process itself.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_metrics", columnDefinition = "jsonb")
    private Map<String, Object> validationMetrics = new HashMap<>();

    /**
     * DAG validation result.
     */
    public record DAGValidationResult(
        boolean hasCycles,
        boolean isConnected,
        List<String> orphanedNodes,
        List<String> sourceNodes,
        List<String> sinkNodes,
        List<List<String>> paths,
        int maxDepth
    ) {}

    /**
     * Schema validation result.
     */
    public record SchemaValidationResult(
        boolean schemasCompatible,
        List<SchemaMismatch> incompatibleSchemas,
        List<String> missingSchemas,
        List<String> invalidSchemas
    ) {}

    /**
     * Schema mismatch details.
     */
    public record SchemaMismatch(
        String fromNode,
        String toNode,
        String expectedSchema,
        String actualSchema,
        String mismatchType,
        String description
    ) {}

    /**
     * Operator validation result.
     */
    public record OperatorValidationResult(
        boolean operatorsValid,
        List<String> missingOperators,
        List<String> invalidOperators,
        List<OperatorConfigError> configErrors
    ) {}

    /**
     * Operator configuration error.
     */
    public record OperatorConfigError(
        String nodeId,
        String operator,
        String errorType,
        String message,
        List<String> invalidFields
    ) {}

    /**
     * Performance validation result.
     */
    public record PerformanceValidationResult(
        boolean performanceConstraintsMet,
        List<PerformanceViolation> violations,
        Map<String, Object> estimatedResources,
        Map<String, Object> bottlenecks
    ) {}

    /**
     * Performance violation.
     */
    public record PerformanceViolation(
        String nodeId,
        String violationType,
        String description,
        Object actualValue,
        Object limitValue
    ) {}

    /**
     * Security validation result.
     */
    public record SecurityValidationResult(
        boolean securityConstraintsMet,
        List<SecurityViolation> violations,
        List<String> dataPrivacyIssues,
        List<String> accessControlIssues
    ) {}

    /**
     * Security violation.
     */
    public record SecurityViolation(
        String nodeId,
        String violationType,
        String severity,
        String description,
        List<String> recommendations
    ) {}

    /**
     * Validation error.
     */
    public record ValidationError(
        String code,
        String message,
        String severity,
        String category,
        List<String> affectedNodes,
        Map<String, Object> details
    ) {}

    /**
     * Validation warning.
     */
    public record ValidationWarning(
        String code,
        String message,
        String category,
        List<String> affectedNodes,
        Map<String, Object> details
    ) {}

    /**
     * Validation recommendation.
     */
    public record ValidationRecommendation(
        String type,
        String message,
        String priority,
        List<String> affectedNodes,
        Map<String, Object> suggestedChanges
    ) {}

    // ============ Getters & Setters ============

    public Boolean getValid() {
        return valid;
    }

    public void setValid(Boolean valid) {
        this.valid = valid;
    }

    public Instant getValidationTimestamp() {
        return validationTimestamp;
    }

    public void setValidationTimestamp(Instant validationTimestamp) {
        this.validationTimestamp = validationTimestamp;
    }

    public DAGValidationResult getDagValidation() {
        return dagValidation;
    }

    public void setDagValidation(DAGValidationResult dagValidation) {
        this.dagValidation = dagValidation;
    }

    public SchemaValidationResult getSchemaValidation() {
        return schemaValidation;
    }

    public void setSchemaValidation(SchemaValidationResult schemaValidation) {
        this.schemaValidation = schemaValidation;
    }

    public OperatorValidationResult getOperatorValidation() {
        return operatorValidation;
    }

    public void setOperatorValidation(OperatorValidationResult operatorValidation) {
        this.operatorValidation = operatorValidation;
    }

    public PerformanceValidationResult getPerformanceValidation() {
        return performanceValidation;
    }

    public void setPerformanceValidation(PerformanceValidationResult performanceValidation) {
        this.performanceValidation = performanceValidation;
    }

    public SecurityValidationResult getSecurityValidation() {
        return securityValidation;
    }

    public void setSecurityValidation(SecurityValidationResult securityValidation) {
        this.securityValidation = securityValidation;
    }

    public List<ValidationError> getErrors() {
        return errors;
    }

    public void setErrors(List<ValidationError> errors) {
        this.errors = errors;
    }

    public List<ValidationWarning> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<ValidationWarning> warnings) {
        this.warnings = warnings;
    }

    public List<ValidationRecommendation> getRecommendations() {
        return recommendations;
    }

    public void setRecommendations(List<ValidationRecommendation> recommendations) {
        this.recommendations = recommendations;
    }

    public Map<String, Object> getValidationMetrics() {
        return validationMetrics;
    }

    public void setValidationMetrics(Map<String, Object> validationMetrics) {
        this.validationMetrics = validationMetrics;
    }

    // ============ Builder Pattern ============

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Boolean valid;
        private Instant validationTimestamp;
        private DAGValidationResult dagValidation;
        private SchemaValidationResult schemaValidation;
        private OperatorValidationResult operatorValidation;
        private PerformanceValidationResult performanceValidation;
        private SecurityValidationResult securityValidation;
        private List<ValidationError> errors = new ArrayList<>();
        private List<ValidationWarning> warnings = new ArrayList<>();
        private List<ValidationRecommendation> recommendations = new ArrayList<>();
        private Map<String, Object> validationMetrics = new HashMap<>();

        public Builder valid(Boolean valid) {
            this.valid = valid;
            return this;
        }

        public Builder validationTimestamp(Instant validationTimestamp) {
            this.validationTimestamp = validationTimestamp;
            return this;
        }

        public Builder dagValidation(DAGValidationResult dagValidation) {
            this.dagValidation = dagValidation;
            return this;
        }

        public Builder schemaValidation(SchemaValidationResult schemaValidation) {
            this.schemaValidation = schemaValidation;
            return this;
        }

        public Builder operatorValidation(OperatorValidationResult operatorValidation) {
            this.operatorValidation = operatorValidation;
            return this;
        }

        public Builder performanceValidation(PerformanceValidationResult performanceValidation) {
            this.performanceValidation = performanceValidation;
            return this;
        }

        public Builder securityValidation(SecurityValidationResult securityValidation) {
            this.securityValidation = securityValidation;
            return this;
        }

        public Builder errors(List<ValidationError> errors) {
            this.errors = errors;
            return this;
        }

        public Builder warnings(List<ValidationWarning> warnings) {
            this.warnings = warnings;
            return this;
        }

        public Builder recommendations(List<ValidationRecommendation> recommendations) {
            this.recommendations = recommendations;
            return this;
        }

        public Builder validationMetrics(Map<String, Object> validationMetrics) {
            this.validationMetrics = validationMetrics;
            return this;
        }

        public PipelineValidationResult build() {
            PipelineValidationResult result = new PipelineValidationResult();
            result.valid = this.valid;
            result.validationTimestamp = this.validationTimestamp;
            result.dagValidation = this.dagValidation;
            result.schemaValidation = this.schemaValidation;
            result.operatorValidation = this.operatorValidation;
            result.performanceValidation = this.performanceValidation;
            result.securityValidation = this.securityValidation;
            result.errors = this.errors;
            result.warnings = this.warnings;
            result.recommendations = this.recommendations;
            result.validationMetrics = this.validationMetrics;
            return result;
        }
    }

    @Override
    public String toString() {
        return "PipelineValidationResult{" +
                "valid=" + valid +
                ", validationTimestamp=" + validationTimestamp +
                ", errorCount=" + (errors != null ? errors.size() : 0) +
                ", warningCount=" + (warnings != null ? warnings.size() : 0) +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PipelineValidationResult that = (PipelineValidationResult) o;
        return Objects.equals(valid, that.valid) &&
                Objects.equals(validationTimestamp, that.validationTimestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(valid, validationTimestamp);
    }
}
