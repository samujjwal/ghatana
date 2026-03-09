package com.ghatana.pipeline.registry.model;

import com.ghatana.platform.domain.auth.TenantId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Connector instance model representing a configured data connector.
 *
 * <p>Purpose: Represents a tenant-scoped connector instance with configuration,
 * status, and binding information. Supports various connector types (HTTP,
 * Kafka, SQS) with configurable ingress/egress direction.</p>
 *
 * @doc.type class
 * @doc.purpose Domain model for configured connector instances
 * @doc.layer product
 * @doc.pattern ValueObject
 * @since 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectorInstance {

    private String id;
    private TenantId tenantId;
    private String name;
    private String templateId;
    private String type;
    private String direction;
    private String status;
    private Map<String, Object> config;
    private String schemaId;
    private List<String> eventTypeIds;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private Map<String, Object> metadata;
}
