package com.ghatana.agent.registry.store;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity representing an agent specification in the database.
 * Includes versioning and soft delete support.
 */
@Entity
@Table(name = "agent_specs")
public class JpaAgentEntity {
    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", length = 4000)
    private String description;

    @Column(name = "version", nullable = false)
    private String version;

    @Column(name = "implementation_type")
    private String implementationType;

    @Column(name = "implementation_uri", length = 1024)
    private String implementationUri;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "deprecated")
    private boolean deprecated;

    @Column(name = "input_schema", columnDefinition = "TEXT")
    private String inputSchema;

    @Column(name = "output_schema", columnDefinition = "TEXT")
    private String outputSchema;

    @Column(name = "config_schema", columnDefinition = "TEXT")
    private String configSchema;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "agent_tags", joinColumns = @JoinColumn(name = "agent_id"))
    @Column(name = "tag")
    private List<String> tags;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public JpaAgentEntity() {
        this.tags = new ArrayList<>();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getImplementationType() { return implementationType; }
    public void setImplementationType(String implementationType) { this.implementationType = implementationType; }

    public String getImplementationUri() { return implementationUri; }
    public void setImplementationUri(String implementationUri) { this.implementationUri = implementationUri; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public boolean isDeprecated() { return deprecated; }
    public void setDeprecated(boolean deprecated) { this.deprecated = deprecated; }

    public String getInputSchema() { return inputSchema; }
    public void setInputSchema(String inputSchema) { this.inputSchema = inputSchema; }

    public String getOutputSchema() { return outputSchema; }
    public void setOutputSchema(String outputSchema) { this.outputSchema = outputSchema; }

    public String getConfigSchema() { return configSchema; }
    public void setConfigSchema(String configSchema) { this.configSchema = configSchema; }

    public List<String> getTags() {
        if (tags == null) {
            tags = new ArrayList<>();
        }
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags != null ? new ArrayList<>(tags) : new ArrayList<>();
    }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }

    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }

    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JpaAgentEntity that = (JpaAgentEntity) o;
        return deleted == that.deleted &&
               deprecated == that.deprecated &&
               Objects.equals(id, that.id) &&
               Objects.equals(name, that.name) &&
               Objects.equals(version, that.version) &&
               Objects.equals(implementationType, that.implementationType) &&
               Objects.equals(tenantId, that.tenantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, version, implementationType, tenantId, deprecated, deleted);
    }

    @Override
    public String toString() {
        return "JpaAgentEntity{" +
               "id='" + id + '\'' +
               ", name='" + name + '\'' +
               ", version='" + version + '\'' +
               ", implementationType='" + implementationType + '\'' +
               ", tenantId='" + tenantId + '\'' +
               ", deleted=" + deleted +
               '}';
    }

    public static final class Builder {
        private final JpaAgentEntity entity = new JpaAgentEntity();

        public Builder id(String id) {
            entity.setId(id);
            return this;
        }

        public Builder name(String name) {
            entity.setName(name);
            return this;
        }

        public Builder description(String description) {
            entity.setDescription(description);
            return this;
        }

        public Builder version(String version) {
            entity.setVersion(version);
            return this;
        }

        public Builder implementationType(String implementationType) {
            entity.setImplementationType(implementationType);
            return this;
        }

        public Builder implementationUri(String implementationUri) {
            entity.setImplementationUri(implementationUri);
            return this;
        }

        public Builder tenantId(String tenantId) {
            entity.setTenantId(tenantId);
            return this;
        }

        public Builder deprecated(boolean deprecated) {
            entity.setDeprecated(deprecated);
            return this;
        }

        public Builder inputSchema(String inputSchema) {
            entity.setInputSchema(inputSchema);
            return this;
        }

        public Builder outputSchema(String outputSchema) {
            entity.setOutputSchema(outputSchema);
            return this;
        }

        public Builder configSchema(String configSchema) {
            entity.setConfigSchema(configSchema);
            return this;
        }

        public Builder tags(List<String> tags) {
            entity.setTags(tags);
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            entity.setCreatedAt(createdAt);
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            entity.setUpdatedAt(updatedAt);
            return this;
        }

        public Builder deleted(boolean deleted) {
            entity.setDeleted(deleted);
            return this;
        }

        public Builder deletedAt(Instant deletedAt) {
            entity.setDeletedAt(deletedAt);
            return this;
        }

        public JpaAgentEntity build() {
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID().toString());
            }
            if (entity.getCreatedAt() == null) {
                entity.setCreatedAt(Instant.now());
            }
            if (entity.getUpdatedAt() == null) {
                entity.setUpdatedAt(Instant.now());
            }
            return entity;
        }
    }
}
