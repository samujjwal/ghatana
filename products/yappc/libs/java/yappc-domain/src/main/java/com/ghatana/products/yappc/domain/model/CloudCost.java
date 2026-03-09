package com.ghatana.products.yappc.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain model representing cloud cost data in the YAPPC platform.
 *
 * <p>CloudCost tracks spending data from connected cloud accounts,
 * enabling cost monitoring, budget alerts, and optimization recommendations.</p>
 *
 * @doc.type class
 * @doc.purpose Represents cloud spending data for cost monitoring and optimization
 * @doc.layer product
 * @doc.pattern Entity
 */
@Entity
@Table(name = "cloud_costs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class CloudCost {

    /**
     * Unique identifier for the cost record.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * The workspace this cost record belongs to.
     */
    @NotNull(message = "Workspace ID is required")
    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    /**
     * The cloud account this cost is associated with.
     */
    @NotNull(message = "Cloud account ID is required")
    @Column(name = "cloud_account_id", nullable = false)
    private UUID cloudAccountId;

    /**
     * The cost amount.
     */
    @NotNull(message = "Amount is required")
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /**
     * Currency code (e.g., USD, EUR).
     */
    @NotNull(message = "Currency is required")
    @Size(max = 3, message = "Currency code must be 3 characters")
    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "USD";

    /**
     * The service generating the cost (e.g., EC2, S3, Lambda).
     */
    @Size(max = 100, message = "Service name must not exceed 100 characters")
    @Column(name = "service_name")
    private String serviceName;

    /**
     * Region where the cost was incurred.
     */
    @Size(max = 50, message = "Region must not exceed 50 characters")
    @Column(name = "region")
    private String region;

    /**
     * Date the cost was incurred.
     */
    @NotNull(message = "Cost date is required")
    @Column(name = "cost_date", nullable = false)
    private LocalDate costDate;

    /**
     * Timestamp when the record was created.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Version for optimistic locking.
     */
    @Column(name = "version")
    @Builder.Default
    private int version = 0;

    /**
     * Creates a new CloudCost with the minimum required fields.
     *
     * @param workspaceId    the workspace ID
     * @param cloudAccountId the cloud account ID
     * @param amount         the cost amount
     * @param costDate       the date of the cost
     * @return a new CloudCost instance
     */
    public static CloudCost of(UUID workspaceId, UUID cloudAccountId, BigDecimal amount, LocalDate costDate) {
        return CloudCost.builder()
                .workspaceId(Objects.requireNonNull(workspaceId, "workspaceId must not be null"))
                .cloudAccountId(Objects.requireNonNull(cloudAccountId, "cloudAccountId must not be null"))
                .amount(Objects.requireNonNull(amount, "amount must not be null"))
                .costDate(Objects.requireNonNull(costDate, "costDate must not be null"))
                .createdAt(Instant.now())
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CloudCost cloudCost = (CloudCost) o;
        return Objects.equals(id, cloudCost.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
