package com.ghatana.products.yappc.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import com.ghatana.products.yappc.domain.Identifiable;

/**
 * Domain model representing a user-configurable dashboard in the YAPPC platform.
 *
 * <p>Dashboards provide customizable views of security metrics, scan results,
 * and compliance status for a workspace. Each dashboard belongs to a specific
 * workspace and contains widgets configured by the user.</p>
 *
 * @doc.type class
 * @doc.purpose Represents a customizable analytics dashboard for security metrics
 * @doc.layer product
 * @doc.pattern Entity
 */
@Entity
@Table(name = "dashboards")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Dashboard implements Identifiable<UUID> {

    /**
     * Unique identifier for the dashboard.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * The workspace this dashboard belongs to.
     */
    @NotNull(message = "Workspace ID is required")
    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    /**
     * Human-readable name for the dashboard.
     */
    @NotBlank(message = "Dashboard name is required")
    @Size(max = 255, message = "Dashboard name must not exceed 255 characters")
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * URL-friendly key identifier for the dashboard.
     */
    @Size(max = 100, message = "Key must not exceed 100 characters")
    @Column(name = "key", length = 100)
    private String key;

    /**
     * Display title for the dashboard.
     */
    @Size(max = 255, message = "Title must not exceed 255 characters")
    @Column(name = "title")
    private String title;

    /**
     * Persona type this dashboard is configured for.
     */
    @Size(max = 50, message = "Persona must not exceed 50 characters")
    @Column(name = "persona", length = 50)
    private String persona;

    /**
     * JSON-serialized dashboard configuration.
     */
    @Column(name = "config", columnDefinition = "jsonb")
    private String config;

    /**
     * JSON-serialized filter configuration.
     */
    @Column(name = "filters", columnDefinition = "jsonb")
    private String filters;

    /**
     * User who created this dashboard.
     */
    @Column(name = "created_by_id")
    private UUID createdById;

    /**
     * Optional description of the dashboard's purpose.
     */
    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    @Column(name = "description")
    private String description;

    /**
     * JSON-serialized widget configuration.
     */
    @Column(name = "widget_config", columnDefinition = "jsonb")
    private String widgetConfig;

    /**
     * Whether this is the default dashboard for the workspace.
     */
    @Column(name = "is_default")
    @Builder.Default
    private boolean isDefault = false;

    /**
     * Timestamp when the dashboard was created.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Timestamp when the dashboard was last updated.
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Version for optimistic locking.
     */
    @Column(name = "version")
    @Builder.Default
    private int version = 0;

    /**
     * Creates a new Dashboard with the minimum required fields.
     *
     * @param workspaceId the workspace ID
     * @param name        the dashboard name
     * @return a new Dashboard instance
     */
    public static Dashboard of(UUID workspaceId, String name) {
        Instant now = Instant.now();
        return Dashboard.builder()
                .workspaceId(Objects.requireNonNull(workspaceId, "workspaceId must not be null"))
                .name(Objects.requireNonNull(name, "name must not be null"))
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Dashboard dashboard = (Dashboard) o;
        return Objects.equals(id, dashboard.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
