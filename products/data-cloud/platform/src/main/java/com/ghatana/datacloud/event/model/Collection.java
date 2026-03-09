package com.ghatana.datacloud.event.model;

import com.ghatana.datacloud.EntityRecord;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base collection class for schema definitions in EventCloud.
 *
 * <p>
 * Extends core {@link EntityRecord} with collection-specific fields for schema
 * management, permissions, and versioning.
 *
 * <p>
 * <b>Inheritance</b><br>
 * <pre>
 * DataRecord (core)
 *   └── EntityRecord (core) - mutable, versioned
 *         └── Collection (event/domain) - schema container
 *               └── EventType - event schema with governance
 * </pre>
 *
 * <p>
 * <b>Key Characteristics</b>:
 * <ul>
 * <li>Unique name (identifier per tenant)</li>
 * <li>RBAC permissions as JSONB</li>
 * <li>JSON Schema validation</li>
 * <li>Semantic versioning for schema evolution</li>
 * </ul>
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * EventType eventType = EventType.builder()
 *     .tenantId("tenant-123")
 *     .collectionName("event-types")
 *     .name("order.created")
 *     .label("Order Created")
 *     .description("Emitted when a new order is placed")
 *     .schemaVersion("1.0.0")
 *     .permission(Map.of(
 *         "read", List.of("analyst", "admin"),
 *         "write", List.of("admin")
 *     ))
 *     .build();
 * }</pre>
 *
 * @see EntityRecord
 * @see EventType
 * @doc.type class
 * @doc.purpose Base schema container with permissions and versioning
 * @doc.layer domain
 * @doc.pattern Domain Entity, Schema Registry
 */
@MappedSuperclass
@Getter
@Setter
@SuperBuilder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class Collection extends EntityRecord {

    // ==================== Identity ====================
    /**
     * Unique name/identifier for this collection within tenant.
     * <p>
     * Format: lowercase, dot-separated namespace (e.g., "order.created")
     */
    @NotBlank
    @Column(nullable = false, length = 255)
    @Size(max = 255)
    protected String name;

    /**
     * Human-readable label for display.
     */
    @Column(length = 255)
    @Size(max = 255)
    protected String label;

    /**
     * Description of this collection's purpose.
     */
    @Column(columnDefinition = "TEXT")
    protected String description;

    // ==================== Access Control ====================
    /**
     * RBAC permissions mapping actions to allowed roles.
     * <p>
     * Example: {"read": ["analyst", "admin"], "write": ["admin"]}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    protected Map<String, List<String>> permission = new HashMap<>();

    // ==================== Schema ====================
    /**
     * JSON Schema for validation.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_schema", columnDefinition = "jsonb")
    protected Map<String, Object> validationSchema;

    /**
     * Semantic version of this schema (MAJOR.MINOR.PATCH).
     */
    @Column(name = "schema_version", nullable = false, length = 50)
    @Builder.Default
    protected String schemaVersion = "1.0.0";

    // ==================== Accessors ====================
    /**
     * Gets validation schema, never null.
     *
     * @return validation schema map
     */
    public Map<String, Object> getValidationSchema() {
        if (validationSchema == null) {
            validationSchema = new HashMap<>();
        }
        return validationSchema;
    }

    /**
     * Gets permissions, never null.
     *
     * @return permission map
     */
    public Map<String, List<String>> getPermission() {
        if (permission == null) {
            permission = new HashMap<>();
        }
        return permission;
    }

    /**
     * Checks if role has permission for an action.
     *
     * @param role role to verify
     * @param action action to check
     * @return true if role has permission
     */
    public boolean hasPermission(String role, String action) {
        List<String> roles = getPermission().get(action);
        return roles != null && roles.contains(role);
    }

    /**
     * Gets display label (name if label not set).
     *
     * @return label or name
     */
    public String getDisplayName() {
        return label != null && !label.isBlank() ? label : name;
    }

    /**
     * Gets roles for a permission action.
     *
     * @param action the action to check (e.g., "read", "write")
     * @return list of roles with permission, empty if none
     */
    public List<String> getRolesForPermission(String action) {
        List<String> roles = getPermission().get(action);
        return roles != null ? new ArrayList<>(roles) : new ArrayList<>();
    }
}
