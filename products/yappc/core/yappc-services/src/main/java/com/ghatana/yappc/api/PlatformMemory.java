/**
 * Platform Memory DTOs
 * 
 * Canonical schema for Data Cloud+AEP platform memory.
 * Defines the structure for memory records and operations.
 * 
 * @doc.type class
 * @doc.purpose Platform memory schema
 * @doc.layer product
 * @doc.pattern DTO
 */

package com.ghatana.yappc.api;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Canonical platform memory schema.
 */
public final class PlatformMemory {

    private final String memoryId;
    private final String projectId;
    private final String workspaceId;
    private final String tenantId;
    private final MemoryRecord record;
    private final MemoryMetadata metadata;
    private final Instant createdAt;
    private final Instant updatedAt;

    public PlatformMemory(
            @NotNull String memoryId,
            @NotNull String projectId,
            @NotNull String workspaceId,
            @NotNull String tenantId,
            @NotNull MemoryRecord record,
            @NotNull MemoryMetadata metadata,
            @NotNull Instant createdAt,
            @NotNull Instant updatedAt
    ) {
        this.memoryId = memoryId;
        this.projectId = projectId;
        this.workspaceId = workspaceId;
        this.tenantId = tenantId;
        this.record = record;
        this.metadata = metadata;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String memoryId() {
        return memoryId;
    }

    public String projectId() {
        return projectId;
    }

    public String workspaceId() {
        return workspaceId;
    }

    public String tenantId() {
        return tenantId;
    }

    public MemoryRecord record() {
        return record;
    }

    public MemoryMetadata metadata() {
        return metadata;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    /**
     * Memory record.
     */
    public record MemoryRecord(
            String memoryType,
            String key,
            Object value,
            MemoryValue valueMetadata,
            List<MemoryReference> references
    ) {}

    /**
     * Memory value metadata.
     */
    public record MemoryValue(
            String valueType,
            long sizeBytes,
            String encoding,
            String checksum,
            Instant expiresAt
    ) {}

    /**
     * Memory reference.
     */
    public record MemoryReference(
            String referenceId,
            String referenceType,
            String referenceLocation
    ) {}

    /**
     * Memory metadata.
     */
    public record MemoryMetadata(
            String sessionId,
            String userId,
            String lifecyclePhase,
            Set<String> tags,
            Map<String, String> customMetadata
    ) {}

    /**
     * Memory operation.
     */
    public record MemoryOperation(
            String operationId,
            String operationType,
            String memoryId,
            String key,
            Object oldValue,
            Object newValue,
            Instant timestamp
    ) {
        public enum OperationType {
            CREATE,
            UPDATE,
            DELETE,
            READ
        }
    }
}
