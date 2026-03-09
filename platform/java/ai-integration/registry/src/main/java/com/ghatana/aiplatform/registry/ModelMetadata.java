package com.ghatana.aiplatform.registry;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Model metadata value object representing a versioned ML model.
 *
 * <p>
 * <b>Purpose</b><br>
 * Immutable representation of model metadata including version, framework,
 * deployment status, and training metrics. Used for model registry operations
 * and deployment tracking.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * ModelMetadata model = ModelMetadata.builder()
 *     .id(UUID.randomUUID())
 *     .tenantId("tenant-123")
 *     .name("pattern-recommender")
 *     .version("v1.2.0")
 *     .framework("tensorflow")
 *     .deploymentStatus(DeploymentStatus.PRODUCTION)
 *     .trainingMetrics(Map.of("accuracy", 0.92))
 *     .createdAt(Instant.now())
 *     .build();
 * }</pre>
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Immutable - thread-safe
 *
 * @doc.type class
 * @doc.purpose ML model metadata value object
 * @doc.layer platform
 * @doc.pattern Value Object
 */
public final class ModelMetadata {

    private final UUID id;
    private final String tenantId;
    private final String name;
    private final String version;
    private final String framework;
    private final DeploymentStatus deploymentStatus;
    private final Map<String, Object> metadata;
    private final Map<String, Double> trainingMetrics;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Optional<Instant> deployedAt;
    private final Optional<Instant> deprecatedAt;

    private ModelMetadata(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id must not be null");
        this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId must not be null");
        this.name = Objects.requireNonNull(builder.name, "name must not be null");
        this.version = Objects.requireNonNull(builder.version, "version must not be null");
        this.framework = builder.framework;
        this.deploymentStatus = Objects.requireNonNull(builder.deploymentStatus, "deploymentStatus must not be null");
        this.metadata = builder.metadata != null ? Map.copyOf(builder.metadata) : Map.of();
        this.trainingMetrics = builder.trainingMetrics != null ? Map.copyOf(builder.trainingMetrics) : Map.of();
        this.createdAt = Objects.requireNonNull(builder.createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(builder.updatedAt, "updatedAt must not be null");
        this.deployedAt = Optional.ofNullable(builder.deployedAt);
        this.deprecatedAt = Optional.ofNullable(builder.deprecatedAt);
    }

    public static Builder builder() {
        return new Builder();
    }

    public UUID getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getFramework() {
        return framework;
    }

    public DeploymentStatus getDeploymentStatus() {
        return deploymentStatus;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public Map<String, Double> getTrainingMetrics() {
        return trainingMetrics;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Optional<Instant> getDeployedAt() {
        return deployedAt;
    }

    public Optional<Instant> getDeprecatedAt() {
        return deprecatedAt;
    }

    /**
     * Create a new instance with updated deployment status.
     *
     * @param newStatus the new deployment status
     * @return new ModelMetadata instance with updated status
     */
    public ModelMetadata withStatus(DeploymentStatus newStatus) {
        return builder()
                .id(this.id)
                .tenantId(this.tenantId)
                .name(this.name)
                .version(this.version)
                .framework(this.framework)
                .deploymentStatus(newStatus)
                .metadata(this.metadata)
                .trainingMetrics(this.trainingMetrics)
                .createdAt(this.createdAt)
                .updatedAt(Instant.now())
                .deployedAt(newStatus == DeploymentStatus.PRODUCTION ? Instant.now() : this.deployedAt.orElse(null))
                .deprecatedAt(newStatus == DeploymentStatus.DEPRECATED ? Instant.now() : this.deprecatedAt.orElse(null))
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ModelMetadata that = (ModelMetadata) o;
        return Objects.equals(id, that.id)
                && Objects.equals(tenantId, that.tenantId)
                && Objects.equals(name, that.name)
                && Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tenantId, name, version);
    }

    @Override
    public String toString() {
        return "ModelMetadata{"
                + "id=" + id
                + ", tenantId='" + tenantId + '\''
                + ", name='" + name + '\''
                + ", version='" + version + '\''
                + ", framework='" + framework + '\''
                + ", deploymentStatus=" + deploymentStatus
                + ", createdAt=" + createdAt
                + ", updatedAt=" + updatedAt
                + '}';
    }

    public static final class Builder {

        private UUID id;
        private String tenantId;
        private String name;
        private String version;
        private String framework;
        private DeploymentStatus deploymentStatus = DeploymentStatus.DEVELOPMENT;
        private Map<String, Object> metadata;
        private Map<String, Double> trainingMetrics;
        private Instant createdAt = Instant.now();
        private Instant updatedAt = Instant.now();
        private Instant deployedAt;
        private Instant deprecatedAt;

        private Builder() {
        }

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

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder framework(String framework) {
            this.framework = framework;
            return this;
        }

        public Builder deploymentStatus(DeploymentStatus deploymentStatus) {
            this.deploymentStatus = deploymentStatus;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder trainingMetrics(Map<String, Double> trainingMetrics) {
            this.trainingMetrics = trainingMetrics;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Builder deployedAt(Instant deployedAt) {
            this.deployedAt = deployedAt;
            return this;
        }

        public Builder deprecatedAt(Instant deprecatedAt) {
            this.deprecatedAt = deprecatedAt;
            return this;
        }

        public ModelMetadata build() {
            if (id == null) {
                id = UUID.randomUUID();
            }
            return new ModelMetadata(this);
        }
    }
}
