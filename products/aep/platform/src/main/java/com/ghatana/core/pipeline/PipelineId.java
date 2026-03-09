package com.ghatana.core.pipeline;

import java.util.Objects;

/**
 * Unique identifier for a processing pipeline.
 *
 * @param value the string representation of the pipeline ID
 */
public record PipelineId(String value) {

    public PipelineId {
        Objects.requireNonNull(value, "PipelineId value required");
    }

    /**
     * Creates a pipeline ID from tenant, namespace, name and version components.
     */
    public static PipelineId of(String tenantId, String namespace, String name, String version) {
        return new PipelineId(tenantId + ":" + namespace + ":" + name + ":" + version);
    }

    /**
     * Extracts the name component from the pipeline ID.
     * <p>Assumes format: tenantId:namespace:name:version</p>
     *
     * @return the name component, or the full value if format is unexpected
     */
    public String getName() {
        String[] parts = value.split(":");
        return parts.length >= 3 ? parts[2] : value;
    }
}
