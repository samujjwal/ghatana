package com.ghatana.pipeline.registry.model;

import com.ghatana.platform.domain.auth.TenantId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a pipeline configuration in the system.
 * 
 * <p>Supports both legacy string config and new structured config for backward compatibility.
 * During migration period, both formats are supported with structured config taking priority.
 *
 * <p>Note: This is a domain model (POJO), not a JPA entity.
 * Use repository layer to map to/from database records using JDBI or similar.
 *
 * @doc.type class
 * @doc.purpose Pipeline domain model with structured and legacy config support
 * @doc.layer product
 * @doc.pattern DomainModel, Entity
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Pipeline {
    private String id;
    private TenantId tenantId;

    @Size(min = 1, max = 255, message = "Name must be between 1 and 255 characters")
    @NotBlank(message = "Name is required")
    private String name;

    @Size(max = 2000, message = "Description cannot exceed 2000 characters")
    private String description;

    private int version;
    private boolean active;

    /**
     * Legacy configuration as JSON string.
     */
    private String config;
    
    /**
     * Structured pipeline configuration (NEW - preferred).
     */
    private PipelineConfig structuredConfig;

    /**
     * Pipeline stages/steps (NEW - preferred).
     */
    @Builder.Default
    private List<PipelineStage> stages = new ArrayList<>();

    /**
     * Additional metadata key-value pairs.
     */
    @Builder.Default
    private Map<String, String> metadata = new HashMap<>();

    @NotBlank(message = "Updated by is required")
    private String updatedBy;

    @Builder.Default
    @Size(max = 100, message = "Tag cannot exceed 100 characters")
    private List<@NotBlank(message = "Tag cannot be blank") String> tags = new ArrayList<>();
    
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private long versionControl;

    /**
     * Check if this pipeline has structured configuration.
     *
     * @return true if structured config is present
     */
    public boolean hasStructuredConfig() {
        return structuredConfig != null;
    }

    /**
     * Check if this pipeline has stages defined.
     *
     * @return true if stages are present
     */
    public boolean hasStages() {
        return stages != null && !stages.isEmpty();
    }

    /**
     * Get configuration - prefers structured, falls back to legacy.
     *
     * @return configuration (structured or legacy)
     */
    public Object getEffectiveConfig() {
        return hasStructuredConfig() ? structuredConfig : config;
    }

    /**
     * Add a stage to the pipeline.
     *
     * @param stage the stage to add
     */
    public void addStage(PipelineStage stage) {
        if (stages == null) {
            stages = new ArrayList<>();
        }
        stages.add(stage);
    }

    /**
     * Add metadata entry.
     *
     * @param key the metadata key
     * @param value the metadata value
     */
    public void addMetadata(String key, String value) {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        metadata.put(key, value);
    }

    /**
     * Creates a new instance with the specified ID.
     *
     * @param id the ID to set
     * @return a new instance with the specified ID
     */
    public static Pipeline withId(String id) {
        Pipeline pipeline = new Pipeline();
        pipeline.setId(id);
        return pipeline;
    }

    /**
     * Creates a new instance with the specified name and tenant ID.
     *
     * @param name     the name to set
     * @param tenantId the tenant ID to set
     * @return a new instance with the specified name and tenant ID
     */
    public static Pipeline create(String name, TenantId tenantId) {
        Pipeline pipeline = new Pipeline();
        pipeline.setName(name);
        pipeline.setTenantId(tenantId);
        pipeline.setActive(true);
        pipeline.setCreatedAt(Instant.now());
        pipeline.setUpdatedAt(Instant.now());
        return pipeline;
    }

    /**
     * Creates a new version of this pipeline.
     *
     * @return a new version of this pipeline
     */
    public Pipeline newVersion() {
        Pipeline newPipeline = new Pipeline();
        newPipeline.setTenantId(this.tenantId);
        newPipeline.setName(this.name);
        newPipeline.setDescription(this.description);
        newPipeline.setVersion(this.version + 1);
        newPipeline.setActive(true);

        // Copy both legacy and structured config
        newPipeline.setConfig(this.config);
        newPipeline.setStructuredConfig(this.structuredConfig);
        newPipeline.setStages(this.stages != null ? new ArrayList<>(this.stages) : new ArrayList<>());
        newPipeline.setMetadata(this.metadata != null ? new HashMap<>(this.metadata) : new HashMap<>());

        newPipeline.setCreatedAt(Instant.now());
        newPipeline.setUpdatedAt(Instant.now());
        newPipeline.setCreatedBy(this.createdBy);
        newPipeline.setUpdatedBy(this.updatedBy);
        newPipeline.setTags(new ArrayList<>(this.tags));
        return newPipeline;
    }

    /**
     * Updates this pipeline with the values from the specified pipeline.
     *
     * @param update the pipeline containing the new values
     */
    public void updateFrom(Pipeline update) {
        if (update.getDescription() != null) {
            this.description = update.getDescription();
        }

        // Update config - prefer structured, keep legacy for compatibility
        if (update.hasStructuredConfig()) {
            this.structuredConfig = update.getStructuredConfig();
        }
        if (update.getConfig() != null) {
            this.config = update.getConfig();
        }

        // Update stages
        if (update.hasStages()) {
            this.stages = new ArrayList<>(update.getStages());
        }

        // Update metadata
        if (update.getMetadata() != null && !update.getMetadata().isEmpty()) {
            if (this.metadata == null) {
                this.metadata = new HashMap<>();
            }
            this.metadata.putAll(update.getMetadata());
        }

        if (update.getTags() != null) {
            this.tags = new ArrayList<>(update.getTags());
        }

        this.active = update.isActive();
        this.updatedAt = Instant.now();
        this.updatedBy = update.getUpdatedBy();
    }
}

