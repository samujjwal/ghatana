package com.ghatana.pipeline.registry.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resource requirements for pipeline execution.
 *
 * <p>Defines CPU, memory, and concurrency requirements to ensure
 * proper resource allocation and scheduling.
 *
 * @doc.type class
 * @doc.purpose Resource requirements specification
 * @doc.layer product
 * @doc.pattern ValueObject
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceRequirements {

    /**
     * CPU cores required (e.g., 0.5, 1.0, 2.0).
     */
    @Builder.Default
    private double cpuCores = 1.0;

    /**
     * Memory required in megabytes.
     */
    @Builder.Default
    private long memoryMb = 512;

    /**
     * Maximum number of concurrent executions allowed.
     */
    @Builder.Default
    private int maxConcurrency = 10;

    /**
     * Validate that resource requirements are sensible.
     *
     * @return true if valid
     * @throws IllegalArgumentException if invalid
     */
    public boolean validate() {
        if (cpuCores <= 0) {
            throw new IllegalArgumentException("CPU cores must be positive");
        }
        if (memoryMb <= 0) {
            throw new IllegalArgumentException("Memory must be positive");
        }
        if (maxConcurrency <= 0) {
            throw new IllegalArgumentException("Max concurrency must be positive");
        }
        return true;
    }
}

