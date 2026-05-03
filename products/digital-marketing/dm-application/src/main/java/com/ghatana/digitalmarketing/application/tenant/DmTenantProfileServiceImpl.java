package com.ghatana.digitalmarketing.application.tenant;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.tenant.DmSelfMarketingTenantProfile;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link DmTenantProfileService}.
 *
 * @doc.type class
 * @doc.purpose Provisions and manages self-marketing tenant profiles (DMOS-F2-020)
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class DmTenantProfileServiceImpl implements DmTenantProfileService {

    private final DmTenantProfileRepository repository;
    private final DigitalMarketingKernelAdapter kernelAdapter;

    public DmTenantProfileServiceImpl(
            DmTenantProfileRepository repository,
            DigitalMarketingKernelAdapter kernelAdapter) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
    }

    @Override
    public Promise<DmSelfMarketingTenantProfile> provision(DmOperationContext ctx, ProvisionProfileCommand command) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "tenant-profiles", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to provision tenant profile"));
                }
                Instant now = Instant.now();
                DmSelfMarketingTenantProfile profile = DmSelfMarketingTenantProfile.builder()
                    .id(UUID.randomUUID().toString())
                    .tenantId(ctx.getTenantId().getValue())
                    .displayName(command.displayName())
                    .industry(command.industry())
                    .timezone(command.timezone())
                    .defaultCurrency(command.defaultCurrency())
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
                return repository.save(profile)
                    .then(saved -> kernelAdapter.recordAudit(
                        ctx, saved.getId(), "tenant-profile-provisioned",
                        Map.of("tenantId", (Object) ctx.getTenantId().getValue())
                    ).map(__ -> saved));
            });
    }

    @Override
    public Promise<DmSelfMarketingTenantProfile> update(DmOperationContext ctx, UpdateProfileCommand command) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "tenant-profiles", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to update tenant profile"));
                }
                return repository.findByTenantId(ctx.getTenantId().getValue())
                    .then(opt -> {
                        if (opt.isEmpty()) {
                            return Promise.ofException(new java.util.NoSuchElementException(
                                "No profile found for tenant: " + ctx.getTenantId().getValue()));
                        }
                        DmSelfMarketingTenantProfile existing = opt.get();
                        DmSelfMarketingTenantProfile updated = DmSelfMarketingTenantProfile.builder()
                            .id(existing.getId())
                            .tenantId(existing.getTenantId())
                            .displayName(command.displayName())
                            .industry(command.industry())
                            .timezone(command.timezone())
                            .defaultCurrency(command.defaultCurrency())
                            .killSwitchEnabled(command.killSwitchEnabled())
                            .maxActiveConnectors(command.maxActiveConnectors())
                            .maxCampaignsPerMonth(command.maxCampaignsPerMonth())
                            .maxMonthlyBudgetMicros(command.maxMonthlyBudgetMicros())
                            .createdAt(existing.getCreatedAt())
                            .updatedAt(Instant.now())
                            .build();
                        return repository.update(updated)
                            .then(saved -> kernelAdapter.recordAudit(
                                ctx, saved.getId(), "tenant-profile-updated",
                                Map.of("tenantId", (Object) ctx.getTenantId().getValue())
                            ).map(__ -> saved));
                    });
            });
    }

    @Override
    public Promise<Optional<DmSelfMarketingTenantProfile>> findByTenant(DmOperationContext ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");

        return repository.findByTenantId(ctx.getTenantId().getValue());
    }
}
