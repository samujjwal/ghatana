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

    // PHASE 3: Once pipeline.registry.v1.Pipeline proto is generated, replace
    // toWireBytes()/fromWireBytes() with the proto-based toProto()/fromProto() pair.
    // The commented-out implementations in the git history show the intended proto mapping.
    // The JSON wire format below is a fully-functional interim solution; it is tested,
    // structured, and safe to use until the protobuf contract is available.

    /**
     * Serializes this record to a structured map for protocol-agnostic transport.
     *
     * <p>Consumers that need the raw fields without proto or JSON encoding can use this
     * method. The format is stable: keys match field names, values are Java primitives
     * or ISO-8601 strings for {@link Instant} fields.
     *
     * <p>When {@code pipeline.registry.v1.Pipeline} proto becomes available, migrate
     * callers to {@code toProto()}.
     *
     * @return unmodifiable map of field name → value
     */
    public java.util.Map<String, Object> toFieldMap() {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("id", id != null ? id.toString() : null);
        map.put("tenantId", tenantId != null ? tenantId.toString() : null);
        map.put("name", name);
        map.put("description", description != null ? description : "");
        map.put("version", version);
        map.put("active", active);
        map.put("config", config);
        map.put("createdAt", createdAt != null ? createdAt.toString() : null);
        map.put("updatedAt", updatedAt != null ? updatedAt.toString() : null);
        map.put("createdBy", createdBy);
        map.put("updatedBy", updatedBy);
        map.put("tags", tags);
        map.put("versionControl", versionControl);
        return java.util.Collections.unmodifiableMap(map);
    }

    /**
     * Serializes this record to a JSON byte array for transport.
     *
     * <p>Replace with protobuf serialization once {@code pipeline.registry.v1.Pipeline}
     * proto is generated from the contract definitions.
     *
     * @return UTF-8 JSON bytes
     */
    public byte[] toWireBytes() {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            return mapper.writeValueAsBytes(this);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize PipelineRecord to wire bytes", e);
        }
    }

    /**
     * Deserializes a PipelineRecord from JSON bytes.
     *
     * <p>Replace with protobuf deserialization once {@code pipeline.registry.v1.Pipeline}
     * proto is generated from the contract definitions.
     *
     * @param wireBytes UTF-8 JSON bytes from {@link #toWireBytes()}
     * @return deserialized PipelineRecord
     */
    public static PipelineRecord fromWireBytes(byte[] wireBytes) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            return mapper.readValue(wireBytes, PipelineRecord.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize PipelineRecord from wire bytes", e);
        }
    }
}
