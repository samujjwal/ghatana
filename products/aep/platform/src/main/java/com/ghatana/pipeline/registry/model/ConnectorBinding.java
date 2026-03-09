package com.ghatana.pipeline.registry.model;

import com.ghatana.platform.domain.auth.TenantId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Connector binding model linking schemas to connectors.
 *
 * <p>Purpose: Represents the binding between an event schema and a connector,
 * defining how data flows through header/payload mappings and encoding.
 * Supports configurable transformations and enable/disable state.</p>
 *
 * @doc.type class
 * @doc.purpose Domain model for schema-to-connector bindings
 * @doc.layer product
 * @doc.pattern ValueObject
 * @since 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectorBinding {

    private String id;
    private TenantId tenantId;
    private String schemaId;
    private String connectorId;
    private String direction;
    private String encoding;
    private List<HeaderMapping> headerMappings;
    private PayloadMapping payloadMapping;
    private boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String updatedBy;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HeaderMapping {
        private String source;
        private String target;
        private String transform;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PayloadMapping {
        private String rootPath;
        private List<String> excludeFields;
        private Map<String, String> fieldTransforms;
    }

    public static ConnectorBinding create(
            TenantId tenantId,
            String schemaId,
            String connectorId,
            String direction,
            String encoding,
            List<HeaderMapping> headerMappings,
            PayloadMapping payloadMapping,
            String createdBy) {
        Instant now = Instant.now();
        return ConnectorBinding.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .schemaId(schemaId)
                .connectorId(connectorId)
                .direction(direction)
                .encoding(encoding)
                .headerMappings(headerMappings)
                .payloadMapping(payloadMapping)
                .enabled(true)
                .createdAt(now)
                .updatedAt(now)
                .createdBy(createdBy)
                .updatedBy(createdBy)
                .build();
    }

    public void touchUpdated(String updatedBy) {
        this.updatedAt = Instant.now();
        this.updatedBy = updatedBy;
    }
}
