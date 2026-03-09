package com.ghatana.products.yappc.domain.model;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Audit log entity representing system audit trail.
 *
 * <p>
 * <b>Purpose</b><br>
 * AuditLog records all significant system actions for security monitoring,
 * compliance, and forensic analysis.
 *
 * <p>
 * <b>Audited Actions</b><br>
 * - User authentication (login, logout, failed attempts) - Resource
 * modifications (create, update, delete) - Permission changes (role
 * assignments, access grants) - Configuration changes (system settings) -
 * Sensitive data access (PII, credentials) - Security events (incidents,
 * alerts, policy violations)
 *
 * <p>
 * <b>Audit Trail Requirements</b><br>
 * Supports regulatory compliance requirements: - SOC 2: Audit logging for all
 * system changes - ISO 27001: Security event logging - PCI DSS: Access logging
 * for cardholder data - GDPR: Data access and modification logs - HIPAA: PHI
 * access audit trails
 *
 * <p>
 * <b>JSONB Storage</b><br>
 * - old_value: State before change (for updates) - new_value: State after
 * change (for creates/updates) - metadata: Request context (IP, user agent,
 * session)
 *
 * @see Workspace
 * @see User
 * @doc.type class
 * @doc.purpose System audit trail entity
 * @doc.layer product
 * @doc.pattern Entity
 */
@Entity
@Table(name = "audit_log", indexes = {
    @Index(name = "idx_audit_log_workspace_timestamp",
            columnList = "workspace_id, timestamp"),
    @Index(name = "idx_audit_log_user_action",
            columnList = "user_id, action"),
    @Index(name = "idx_audit_log_resource",
            columnList = "resource_type, resource_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "resource_type", nullable = false, length = 100)
    private String resourceType;

    @Column(name = "resource_id", length = 100)
    private String resourceId;

    @Type(JsonBinaryType.class)
    @Column(name = "old_value", columnDefinition = "jsonb")
    private Map<String, Object> oldValue;

    @Type(JsonBinaryType.class)
    @Column(name = "new_value", columnDefinition = "jsonb")
    private Map<String, Object> newValue;

    @Type(JsonBinaryType.class)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}
