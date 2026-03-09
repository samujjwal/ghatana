package com.ghatana.products.yappc.domain.model;

import com.ghatana.products.yappc.domain.enums.CloudProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/**
 * Domain model representing a cloud resource in the YAPPC platform.
 *
 * <p>CloudResource represents an infrastructure component in a connected
 * cloud account (e.g., EC2 instance, S3 bucket, Lambda function).
 * It tracks resource metadata, security posture, and compliance status.</p>
 *
 * @doc.type class
 * @doc.purpose Represents a cloud infrastructure resource for security monitoring
 * @doc.layer product
 * @doc.pattern Entity
 */
@Entity
@Table(name = "cloud_resources")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class CloudResource {

    /**
     * Unique identifier for the resource.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * The workspace this resource belongs to.
     */
    @NotNull(message = "Workspace ID is required")
    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    /**
     * The cloud account this resource belongs to.
     */
    @NotNull(message = "Cloud account ID is required")
    @Column(name = "cloud_account_id", nullable = false)
    private UUID cloudAccountId;

    /**
     * The cloud provider.
     */
    @NotNull(message = "Cloud provider is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false)
    private CloudProvider provider;

    /**
     * The resource type (e.g., ec2:instance, s3:bucket).
     */
    @NotBlank(message = "Resource type is required")
    @Size(max = 100, message = "Resource type must not exceed 100 characters")
    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    /**
     * The cloud provider's resource identifier (ARN, resource ID, etc.).
     */
    @NotBlank(message = "Resource identifier is required")
    @Size(max = 500, message = "Resource identifier must not exceed 500 characters")
    @Column(name = "identifier", nullable = false)
    private String identifier;

    /**
     * Human-readable name of the resource.
     */
    @Size(max = 255, message = "Name must not exceed 255 characters")
    @Column(name = "name")
    private String name;

    /**
     * Region where the resource is located.
     */
    @Size(max = 50, message = "Region must not exceed 50 characters")
    @Column(name = "region")
    private String region;

    /**
     * JSON-serialized resource tags.
     */
    @Column(name = "tags", columnDefinition = "jsonb")
    private String tags;

    /**
     * JSON-serialized resource configuration.
     */
    @Column(name = "configuration", columnDefinition = "jsonb")
    private String configuration;

    /**
     * Security risk score (0-100).
     */
    @Column(name = "risk_score")
    @Builder.Default
    private int riskScore = 0;

    /**
     * Whether the resource is publicly accessible.
     */
    @Column(name = "is_public")
    @Builder.Default
    private boolean isPublic = false;

    /**
     * Timestamp when the resource was last synced.
     */
    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    /**
     * Timestamp when the resource was created.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Timestamp when the resource was last updated.
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
     * Creates a new CloudResource with the minimum required fields.
     *
     * @param workspaceId    the workspace ID
     * @param cloudAccountId the cloud account ID
     * @param provider       the cloud provider
     * @param resourceType   the resource type
     * @param identifier     the resource identifier
     * @return a new CloudResource instance
     */
    public static CloudResource of(UUID workspaceId, UUID cloudAccountId, CloudProvider provider, String resourceType, String identifier) {
        Instant now = Instant.now();
        return CloudResource.builder()
                .workspaceId(Objects.requireNonNull(workspaceId, "workspaceId must not be null"))
                .cloudAccountId(Objects.requireNonNull(cloudAccountId, "cloudAccountId must not be null"))
                .provider(Objects.requireNonNull(provider, "provider must not be null"))
                .resourceType(Objects.requireNonNull(resourceType, "resourceType must not be null"))
                .identifier(Objects.requireNonNull(identifier, "identifier must not be null"))
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * Records a sync operation.
     *
     * @return this CloudResource for fluent chaining
     */
    public CloudResource recordSync() {
        this.lastSyncedAt = Instant.now();
        this.updatedAt = Instant.now();
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CloudResource that = (CloudResource) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
