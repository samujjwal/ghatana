package com.ghatana.digitalmarketing.application.tenant;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.tenant.DmSelfMarketingTenantProfile;
import io.activej.promise.Promise;

import java.util.Objects;
import java.util.Optional;

/**
 * Application service for self-marketing tenant profile management.
 *
 * @doc.type interface
 * @doc.purpose Provides CRUD for tenant profile and limit configuration (DMOS-F2-020)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface DmTenantProfileService {

    Promise<DmSelfMarketingTenantProfile> provision(DmOperationContext ctx, ProvisionProfileCommand command);

    Promise<DmSelfMarketingTenantProfile> update(DmOperationContext ctx, UpdateProfileCommand command);

    Promise<Optional<DmSelfMarketingTenantProfile>> findByTenant(DmOperationContext ctx);

    /**
     * Command to provision a tenant profile.
     */
    record ProvisionProfileCommand(
        String displayName,
        String industry,
        String timezone,
        String defaultCurrency
    ) {
        public ProvisionProfileCommand {
            Objects.requireNonNull(displayName, "displayName must not be null");
            if (displayName.isBlank()) throw new IllegalArgumentException("displayName must not be blank");
        }
    }

    /**
     * Command to update an existing tenant profile.
     */
    record UpdateProfileCommand(
        String displayName,
        String industry,
        String timezone,
        String defaultCurrency,
        boolean killSwitchEnabled,
        int maxActiveConnectors,
        int maxCampaignsPerMonth,
        long maxMonthlyBudgetMicros
    ) {
        public UpdateProfileCommand {
            Objects.requireNonNull(displayName, "displayName must not be null");
            if (displayName.isBlank()) throw new IllegalArgumentException("displayName must not be blank");
            if (maxMonthlyBudgetMicros < 0) throw new IllegalArgumentException("maxMonthlyBudgetMicros must be non-negative");
        }
    }
}
