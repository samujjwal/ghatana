package com.ghatana.pipeline.registry.store;

import com.ghatana.platform.domain.auth.TenantId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain model representing a persisted pipeline definition.
 *
 * <p>Purpose: POJO for pipeline persistence via JDBI or JPA. Contains all
 * pipeline metadata including versioning, tenant isolation, config content,
 * and audit timestamps. Supports protobuf serialization for gRPC.</p>
 *
 * @doc.type class
 * @doc.purpose Persistence model for pipeline definitions
 * @doc.layer product
 * @doc.pattern Entity
 * @since 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineRecord {

    private UUID id;
    private TenantId tenantId;
    private String name;
    private String description;
    private int version;
    private boolean active;
    private String config;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String updatedBy;
    private String tags;  // Comma-separated tags
    private Long versionControl;

    // PHASE 3 TODO: Temporarily disabled - needs pipeline.registry.v1.Pipeline proto
    // /**
    //  * Converts this record to a protobuf message.
    //  */
    // public com.ghatana.pipeline.registry.v1.Pipeline toProto() {
    //     return com.ghatana.pipeline.registry.v1.Pipeline.newBuilder()
    //             .setId(id != null ? id.toString() : "")
    //             .setTenantId(tenantId != null ? tenantId.toString() : "")
    //             .setName(name)
    //             .setDescription(description != null ? description : "")
    //             .setVersion(version)
    //             .setIsActive(active)
    //             .setConfig(config)
    //             .setCreatedAt(
    //                     com.google.protobuf.Timestamp.newBuilder()
    //                             .setSeconds(createdAt.getEpochSecond())
    //                             .setNanos(createdAt.getNano()))
    //             .setUpdatedAt(
    //                     com.google.protobuf.Timestamp.newBuilder()
    //                             .setSeconds(updatedAt.getEpochSecond())
    //                             .setNanos(updatedAt.getNano()))
    //             .setCreatedBy(createdBy)
    //             .setUpdatedBy(updatedBy)
    //             .addAllTags(tags != null ? java.util.List.of(tags.split(",")) : java.util.Collections.emptyList())
    //             .build();
    // }

    // PHASE 3 TODO: Temporarily disabled - needs pipeline.registry.v1.Pipeline proto
    // /**
    //  * Creates a new record from a protobuf message.
    //  */
    // public static PipelineRecord fromProto(com.ghatana.pipeline.registry.v1.Pipeline proto) {
    //     return PipelineRecord.builder()
    //             .id(proto.getId().isEmpty() ? null : UUID.fromString(proto.getId()))
    //             .tenantId(proto.getTenantId().isEmpty() ? null : TenantId.of(proto.getTenantId()))
    //             .name(proto.getName())
    //             .description(proto.getDescription())
    //             .version(proto.getVersion())
    //             .active(proto.getIsActive())
    //             .config(proto.getConfig())
    //             .createdAt(
    //                     Instant.ofEpochSecond(
    //                             proto.getCreatedAt().getSeconds(),
    //                             proto.getCreatedAt().getNanos()))
    //             .updatedAt(
    //                     Instant.ofEpochSecond(
    //                             proto.getUpdatedAt().getSeconds(),
    //                             proto.getUpdatedAt().getNanos()))
    //             .createdBy(proto.getCreatedBy())
    //             .updatedBy(proto.getUpdatedBy())
    //             .tags(String.join(\",\", proto.getTagsList()))
    //             .build();
    // }
}
