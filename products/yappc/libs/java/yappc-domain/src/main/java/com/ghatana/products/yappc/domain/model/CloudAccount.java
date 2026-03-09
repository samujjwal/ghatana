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
 * Domain model representing a cloud provider account in the YAPPC platform.
 *
 * <p>A CloudAccount links a workspace to a cloud provider (AWS, Azure, GCP)
 * and stores the necessary credentials and configuration for accessing
 * cloud resources.</p>
 *
 * @doc.type class
 * @doc.purpose Represents a connected cloud provider account for security monitoring
 * @doc.layer product
 * @doc.pattern Entity
 */
@Entity
@Table(name = "cloud_accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class CloudAccount {

    /**
     * Unique identifier for the cloud account.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * The workspace this cloud account belongs to.
     */
    @NotNull(message = "Workspace ID is required")
    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    /**
     * The cloud provider (AWS, AZURE, GCP).
     */
    @NotNull(message = "Cloud provider is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false)
    private CloudProvider provider;

    /**
     * The cloud provider's account ID.
     */
    @NotBlank(message = "Account ID is required")
    @Size(max = 100, message = "Account ID must not exceed 100 characters")
    @Column(name = "account_id", nullable = false)
    private String accountId;

    /**
     * Human-readable name for the account.
     */
    @NotBlank(message = "Account name is required")
    @Size(max = 255, message = "Account name must not exceed 255 characters")
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * Primary region for this account.
     */
    @Size(max = 50, message = "Region must not exceed 50 characters")
    @Column(name = "region")
    private String region;

    /**
     * External ID for cross-account role assumption (AWS).
     */
    @Size(max = 200, message = "External ID must not exceed 200 characters")
    @Column(name = "external_id")
    private String externalId;

    /**
     * Role ARN for cross-account access (AWS).
     */
    @Size(max = 500, message = "Role ARN must not exceed 500 characters")
    @Column(name = "role_arn")
    private String roleArn;

    /**
     * Whether this account is currently enabled for scanning.
     */
    @Column(name = "enabled")
    @Builder.Default
    private boolean enabled = true;

    /**
     * Status of the last connection test.
     */
    @Size(max = 50, message = "Connection status must not exceed 50 characters")
    @Column(name = "connection_status")
    @Builder.Default
    private String connectionStatus = "PENDING";

    /**
     * Timestamp of the last successful connection.
     */
    @Column(name = "last_connected_at")
    private Instant lastConnectedAt;

    /**
     * Timestamp when the account was created.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Timestamp when the account was last updated.
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
     * Creates a new CloudAccount with the minimum required fields.
     *
     * @param workspaceId the workspace ID
     * @param provider    the cloud provider
     * @param accountId   the cloud provider account ID
     * @param name        the account name
     * @return a new CloudAccount instance
     */
    public static CloudAccount of(UUID workspaceId, CloudProvider provider, String accountId, String name) {
        Instant now = Instant.now();
        return CloudAccount.builder()
                .workspaceId(Objects.requireNonNull(workspaceId, "workspaceId must not be null"))
                .provider(Objects.requireNonNull(provider, "provider must not be null"))
                .accountId(Objects.requireNonNull(accountId, "accountId must not be null"))
                .name(Objects.requireNonNull(name, "name must not be null"))
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * Marks the account as connected.
     *
     * @return this CloudAccount for fluent chaining
     */
    public CloudAccount markConnected() {
        this.connectionStatus = "CONNECTED";
        this.lastConnectedAt = Instant.now();
        this.updatedAt = Instant.now();
        return this;
    }

    /**
     * Marks the account as disconnected.
     *
     * @return this CloudAccount for fluent chaining
     */
    public CloudAccount markDisconnected() {
        this.connectionStatus = "DISCONNECTED";
        this.updatedAt = Instant.now();
        return this;
    }

    /**
     * Disables the account.
     *
     * @return this CloudAccount for fluent chaining
     */
    public CloudAccount disable() {
        this.enabled = false;
        this.updatedAt = Instant.now();
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CloudAccount that = (CloudAccount) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
