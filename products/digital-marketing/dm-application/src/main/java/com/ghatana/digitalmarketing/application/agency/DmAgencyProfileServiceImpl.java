package com.ghatana.digitalmarketing.application.agency;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.agency.DmAgencyProfile;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link DmAgencyProfileService}.
 *
 * @doc.type class
 * @doc.purpose Manages agency profiles and client tenant relationships (DMOS-F4-003)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class DmAgencyProfileServiceImpl implements DmAgencyProfileService {

    private final DmAgencyProfileRepository repository;
    private final DigitalMarketingKernelAdapter kernelAdapter;

    public DmAgencyProfileServiceImpl(
            DmAgencyProfileRepository repository,
            DigitalMarketingKernelAdapter kernelAdapter) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
    }

    @Override
    public Promise<DmAgencyProfile> create(DmOperationContext ctx, CreateAgencyProfileCommand cmd) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(cmd, "cmd must not be null");
        return kernelAdapter.isAuthorized(ctx, "agency-profile", "create")
            .then(allowed -> {
                if (!allowed) return Promise.ofException(new SecurityException("Not authorized to create agency profile"));
                Instant now = Instant.now();
                DmAgencyProfile profile = DmAgencyProfile.builder()
                    .id(UUID.randomUUID().toString())
                    .agencyTenantId(ctx.getTenantId().getValue())
                    .displayName(cmd.displayName())
                    .managedTenantIds(List.of())
                    .active(true)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
                return repository.save(profile);
            })
            .then(saved -> kernelAdapter.recordAudit(ctx, saved.getId(), "agency.create",
                Map.of("agencyTenantId", (Object) saved.getAgencyTenantId()))
                .map(__ -> saved));
    }

    @Override
    public Promise<DmAgencyProfile> addManagedTenant(DmOperationContext ctx, String agencyProfileId, String managedTenantId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(agencyProfileId, "agencyProfileId must not be null");
        Objects.requireNonNull(managedTenantId, "managedTenantId must not be null");
        return kernelAdapter.isAuthorized(ctx, "agency-profile", "update")
            .then(allowed -> {
                if (!allowed) return Promise.ofException(new SecurityException("Not authorized to update agency profile"));
                return repository.findById(agencyProfileId);
            })
            .then(opt -> {
                DmAgencyProfile existing = opt.orElseThrow(() ->
                    new NoSuchElementException("Agency profile not found: " + agencyProfileId));
                List<String> updated = new ArrayList<>(existing.getManagedTenantIds());
                if (!updated.contains(managedTenantId)) updated.add(managedTenantId);
                DmAgencyProfile profile = DmAgencyProfile.builder()
                    .id(existing.getId())
                    .agencyTenantId(existing.getAgencyTenantId())
                    .displayName(existing.getDisplayName())
                    .managedTenantIds(updated)
                    .active(existing.isActive())
                    .createdAt(existing.getCreatedAt())
                    .updatedAt(Instant.now())
                    .build();
                return repository.update(profile);
            })
            .then(saved -> kernelAdapter.recordAudit(ctx, saved.getId(), "agency.addManagedTenant",
                Map.of("managedTenantId", (Object) managedTenantId))
                .map(__ -> saved));
    }

    @Override
    public Promise<DmAgencyProfile> removeManagedTenant(DmOperationContext ctx, String agencyProfileId, String managedTenantId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(agencyProfileId, "agencyProfileId must not be null");
        Objects.requireNonNull(managedTenantId, "managedTenantId must not be null");
        return kernelAdapter.isAuthorized(ctx, "agency-profile", "update")
            .then(allowed -> {
                if (!allowed) return Promise.ofException(new SecurityException("Not authorized to update agency profile"));
                return repository.findById(agencyProfileId);
            })
            .then(opt -> {
                DmAgencyProfile existing = opt.orElseThrow(() ->
                    new NoSuchElementException("Agency profile not found: " + agencyProfileId));
                List<String> updated = new ArrayList<>(existing.getManagedTenantIds());
                updated.remove(managedTenantId);
                DmAgencyProfile profile = DmAgencyProfile.builder()
                    .id(existing.getId())
                    .agencyTenantId(existing.getAgencyTenantId())
                    .displayName(existing.getDisplayName())
                    .managedTenantIds(updated)
                    .active(existing.isActive())
                    .createdAt(existing.getCreatedAt())
                    .updatedAt(Instant.now())
                    .build();
                return repository.update(profile);
            })
            .then(saved -> kernelAdapter.recordAudit(ctx, saved.getId(), "agency.removeManagedTenant",
                Map.of("managedTenantId", (Object) managedTenantId))
                .map(__ -> saved));
    }

    @Override
    public Promise<Optional<DmAgencyProfile>> findById(DmOperationContext ctx, String agencyProfileId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(agencyProfileId, "agencyProfileId must not be null");
        return repository.findById(agencyProfileId);
    }

    @Override
    public Promise<List<DmAgencyProfile>> listAll(DmOperationContext ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return repository.listAll();
    }
}
