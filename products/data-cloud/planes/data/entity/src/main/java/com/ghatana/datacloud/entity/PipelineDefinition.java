package com.ghatana.datacloud.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.*;

/**
 * Represents a typed DAG (Directed Acyclic Graph) pipeline definition with validated contracts.
 *
 * <p><b>Purpose</b><br>
 * Defines a strongly-typed pipeline with DAG structure, validated node/edge contracts,
 * and schema enforcement. Replaces freeform pipeline definitions with typed, validated
 * DAG contracts that ensure correctness at definition time.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * PipelineDefinition pipeline = PipelineDefinition.builder()
 *     .tenantId("tenant-123")
 *     .name("customer-etl")
 *     .description("Customer data ETL pipeline")
 *     .inputSchema(CustomerInputSchema.class.getName())
 *     .outputSchema(CustomerOutputSchema.class.getName())
 *     .nodes(List.of(
 *         PipelineNode.builder()
 *             .id("extract")
 *             .type(NodeType.SOURCE)
 *             .operator(DataSourceOperator.class.getName())
 *             .configuration(Map.of("source", "postgres"))
 *             .build(),
 *         PipelineNode.builder()
 *             .id("transform")
 *             .type(NodeType.TRANSFORM)
 *             .operator(DataTransformOperator.class.getName())
 *             .configuration(Map.of("rules", List.of("normalize", "validate")))
 *             .build(),
 *         PipelineNode.builder()
 *             .id("load")
 *             .type(NodeType.SINK)
 *             .operator(DataSinkOperator.class.getName())
 *             .configuration(Map.of("target", "datawarehouse"))
 *             .build()
 *     ))
 *     .edges(List.of(
 *         PipelineEdge.builder().from("extract").to("transform").build(),
 *         PipelineEdge.builder().from("transform").to("load").build()
 *     ))
 *     .build();
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - Domain model in hexagonal architecture (domain layer)
 * - Persisted via JPA/Hibernate through core/database
 * - Consumed by PipelineService for pipeline management
 * - Validated by PipelineValidator for DAG correctness
 * - Executed by PipelineExecutor for runtime processing
 *
 * <p><b>Thread Safety</b><br>
 * Mutable JPA entity - not thread-safe. Use within transaction boundaries only.
 * Instances should not be shared across threads.
 *
 * <p><b>Multi-Tenancy</b><br>
 * Tenant-scoped via tenantId field. All queries MUST filter by tenant to prevent
 * cross-tenant data access. Enforced at repository layer.
 *
 * @see PipelineNode
 * @see PipelineEdge
 * @see com.ghatana.datacloud.application.PipelineService
 * @doc.type class
 * @doc.purpose Typed DAG pipeline definition with validated contracts
 * @doc.layer product
 * @doc.pattern Domain Model (JPA Entity)
 */
@jakarta.persistence.Entity
@Table(
    name = "pipeline_definitions",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "name"}),
    indexes = {
        @Index(name = "idx_pipeline_tenant", columnList = "tenant_id"),
        @Index(name = "idx_pipeline_active", columnList = "tenant_id, active"),
        @Index(name = "idx_pipeline_status", columnList = "tenant_id, status"),
        @Index(name = "idx_pipeline_created_at", columnList = "created_at DESC")
    }
)
public class PipelineDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull(message = "Tenant ID is required")
    @Column(name = DataCloudColumnNames.TENANT_ID, nullable = false, length = 255)
    private String tenantId;

    @NotBlank(message = "Pipeline name is required")
    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 255)
    private String label;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Input schema class name for type validation.
     * Must be a valid Java class on the classpath.
     */
    @Column(name = "input_schema", nullable = false, length = 500)
    private String inputSchema;

    /**
     * Output schema class name for type validation.
     * Must be a valid Java class on the classpath.
     */
    @Column(name = "output_schema", nullable = false, length = 500)
    private String outputSchema;

    /**
     * Pipeline nodes as JSONB array.
     * Each node has type, operator, configuration, and validation rules.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<PipelineNode> nodes = new ArrayList<>();

    /**
     * Pipeline edges as JSONB array.
     * Defines the DAG structure and data flow.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<PipelineEdge> edges = new ArrayList<>();

    /**
     * Pipeline execution configuration.
     * Includes parallelism, retry policies, timeout settings.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "execution_config", columnDefinition = "jsonb")
    private Map<String, Object> executionConfig = new HashMap<>();

    /**
     * Pipeline validation configuration.
     * Includes schema validation, data quality rules, constraints.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_config", columnDefinition = "jsonb")
    private Map<String, Object> validationConfig = new HashMap<>();

    /**
     * Pipeline status.
     * Values: DRAFT, VALIDATED, ACTIVE, PAUSED, DEPRECATED, ARCHIVED
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PipelineStatus status = PipelineStatus.DRAFT;

    /**
     * DAG validation results.
     * Stores the last validation outcome with timestamp.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_result", columnDefinition = "jsonb")
    private PipelineValidationResult validationResult;

    /**
     * Pipeline version for change tracking.
     * Incremented on each significant change.
     */
    @Column(nullable = false)
    private Integer version = 1;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = DataCloudColumnNames.CREATED_AT, nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = DataCloudColumnNames.UPDATED_AT, nullable = false)
    private Instant updatedAt;

    @Column(name = DataCloudColumnNames.CREATED_BY, length = 255)
    private String createdBy;

    @Column(name = DataCloudColumnNames.UPDATED_BY, length = 255)
    private String updatedBy;

    /**
     * Pipeline owner (team, user, or service).
     */
    @Column(name = "owner", length = 255)
    private String owner;

    /**
     * Pipeline tags for categorization and discovery.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> tags = new ArrayList<>();

    /**
     * Pipeline execution statistics.
     * Tracks runtime performance metrics.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "execution_stats", columnDefinition = "jsonb")
    private Map<String, Object> executionStats;

    /**
     * Pipeline status enumeration.
     */
    public enum PipelineStatus {
        DRAFT,
        VALIDATED,
        ACTIVE,
        PAUSED,
        DEPRECATED,
        ARCHIVED
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // ============ Getters & Setters ============

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public List<PipelineNode> getNodes() {
        return nodes;
    }

    public void setNodes(List<PipelineNode> nodes) {
        this.nodes = nodes;
    }

    public List<PipelineEdge> getEdges() {
        return edges;
    }

    public void setEdges(List<PipelineEdge> edges) {
        this.edges = edges;
    }

    public Map<String, Object> getExecutionConfig() {
        return executionConfig;
    }

    public void setExecutionConfig(Map<String, Object> executionConfig) {
        this.executionConfig = executionConfig;
    }

    public Map<String, Object> getValidationConfig() {
        return validationConfig;
    }

    public void setValidationConfig(Map<String, Object> validationConfig) {
        this.validationConfig = validationConfig;
    }

    public PipelineStatus getStatus() {
        return status;
    }

    public void setStatus(PipelineStatus status) {
        this.status = status;
    }

    public PipelineValidationResult getValidationResult() {
        return validationResult;
    }

    public void setValidationResult(PipelineValidationResult validationResult) {
        this.validationResult = validationResult;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public Map<String, Object> getExecutionStats() {
        return executionStats;
    }

    public void setExecutionStats(Map<String, Object> executionStats) {
        this.executionStats = executionStats;
    }

    // ============ Builder Pattern ============

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private String tenantId;
        private String name;
        private String label;
        private String description;
        private String inputSchema;
        private String outputSchema;
        private List<PipelineNode> nodes = new ArrayList<>();
        private List<PipelineEdge> edges = new ArrayList<>();
        private Map<String, Object> executionConfig = new HashMap<>();
        private Map<String, Object> validationConfig = new HashMap<>();
        private PipelineStatus status = PipelineStatus.DRAFT;
        private PipelineValidationResult validationResult;
        private Integer version = 1;
        private Boolean active = true;
        private String createdBy;
        private String updatedBy;
        private String owner;
        private List<String> tags = new ArrayList<>();
        private Map<String, Object> executionStats;

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder label(String label) {
            this.label = label;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
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

        public Builder nodes(List<PipelineNode> nodes) {
            this.nodes = nodes;
            return this;
        }

        public Builder edges(List<PipelineEdge> edges) {
            this.edges = edges;
            return this;
        }

        public Builder executionConfig(Map<String, Object> executionConfig) {
            this.executionConfig = executionConfig;
            return this;
        }

        public Builder validationConfig(Map<String, Object> validationConfig) {
            this.validationConfig = validationConfig;
            return this;
        }

        public Builder status(PipelineStatus status) {
            this.status = status;
            return this;
        }

        public Builder validationResult(PipelineValidationResult validationResult) {
            this.validationResult = validationResult;
            return this;
        }

        public Builder version(Integer version) {
            this.version = version;
            return this;
        }

        public Builder active(Boolean active) {
            this.active = active;
            return this;
        }

        public Builder createdBy(String createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public Builder updatedBy(String updatedBy) {
            this.updatedBy = updatedBy;
            return this;
        }

        public Builder owner(String owner) {
            this.owner = owner;
            return this;
        }

        public Builder tags(List<String> tags) {
            this.tags = tags;
            return this;
        }

        public Builder executionStats(Map<String, Object> executionStats) {
            this.executionStats = executionStats;
            return this;
        }

        public PipelineDefinition build() {
            PipelineDefinition pipeline = new PipelineDefinition();
            pipeline.id = this.id;
            pipeline.tenantId = this.tenantId;
            pipeline.name = this.name;
            pipeline.label = this.label;
            pipeline.description = this.description;
            pipeline.inputSchema = this.inputSchema;
            pipeline.outputSchema = this.outputSchema;
            pipeline.nodes = this.nodes;
            pipeline.edges = this.edges;
            pipeline.executionConfig = this.executionConfig;
            pipeline.validationConfig = this.validationConfig;
            pipeline.status = this.status;
            pipeline.validationResult = this.validationResult;
            pipeline.version = this.version;
            pipeline.active = this.active;
            pipeline.createdBy = this.createdBy;
            pipeline.updatedBy = this.updatedBy;
            pipeline.owner = this.owner;
            pipeline.tags = this.tags;
            pipeline.executionStats = this.executionStats;
            return pipeline;
        }
    }

    @Override
    public String toString() {
        return "PipelineDefinition{" +
                "id=" + id +
                ", tenantId='" + tenantId + '\'' +
                ", name='" + name + '\'' +
                ", nodeCount=" + (nodes != null ? nodes.size() : 0) +
                ", edgeCount=" + (edges != null ? edges.size() : 0) +
                ", status=" + status +
                ", version=" + version +
                ", createdAt=" + createdAt +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PipelineDefinition that = (PipelineDefinition) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(tenantId, that.tenantId) &&
                Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tenantId, name);
    }
}
