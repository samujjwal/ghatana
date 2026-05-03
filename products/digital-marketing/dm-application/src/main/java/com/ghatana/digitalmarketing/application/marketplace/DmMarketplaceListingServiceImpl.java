package com.ghatana.digitalmarketing.application.marketplace;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.marketplace.DmMarketplaceListing;
import com.ghatana.digitalmarketing.domain.marketplace.DmMarketplaceListingStatus;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link DmMarketplaceListingService}.
 *
 * @doc.type class
 * @doc.purpose Manages marketplace listing lifecycle (DMOS-F5-001)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class DmMarketplaceListingServiceImpl implements DmMarketplaceListingService {

    private final DmMarketplaceListingRepository repository;
    private final DigitalMarketingKernelAdapter kernelAdapter;

    public DmMarketplaceListingServiceImpl(
            DmMarketplaceListingRepository repository,
            DigitalMarketingKernelAdapter kernelAdapter) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
    }

    @Override
    public Promise<DmMarketplaceListing> create(DmOperationContext ctx, CreateMarketplaceListingCommand cmd) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(cmd, "cmd must not be null");
        return kernelAdapter.isAuthorized(ctx, "marketplace-listing", "create")
            .then(allowed -> {
                if (!allowed) return Promise.ofException(new SecurityException("Not authorized to create marketplace listing"));
                Instant now = Instant.now();
                DmMarketplaceListing listing = DmMarketplaceListing.builder()
                    .id(UUID.randomUUID().toString())
                    .tenantId(ctx.getTenantId().getValue())
                    .itemType(cmd.itemType())
                    .itemId(cmd.itemId())
                    .title(cmd.title())
                    .description(cmd.description())
                    .priceMicros(cmd.priceMicros())
                    .currency(cmd.currency())
                    .status(DmMarketplaceListingStatus.DRAFT)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
                return repository.save(listing);
            })
            .then(saved -> kernelAdapter.recordAudit(ctx, saved.getId(), "marketplace-listing.create",
                Map.of("itemType", (Object) saved.getItemType()))
                .map(__ -> saved));
    }

    @Override
    public Promise<DmMarketplaceListing> publish(DmOperationContext ctx, String listingId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(listingId, "listingId must not be null");
        return kernelAdapter.isAuthorized(ctx, "marketplace-listing", "publish")
            .then(allowed -> {
                if (!allowed) return Promise.ofException(new SecurityException("Not authorized to publish marketplace listing"));
                return repository.findById(listingId);
            })
            .then(opt -> {
                DmMarketplaceListing existing = opt.orElseThrow(() ->
                    new NoSuchElementException("Listing not found: " + listingId));
                if (!existing.getTenantId().equals(ctx.getTenantId().getValue()))
                    return Promise.ofException(new SecurityException("Listing does not belong to tenant"));
                return repository.update(existing.publish());
            })
            .then(updated -> kernelAdapter.recordAudit(ctx, updated.getId(), "marketplace-listing.publish",
                Map.of("status", (Object) updated.getStatus().name()))
                .map(__ -> updated));
    }

    @Override
    public Promise<DmMarketplaceListing> unpublish(DmOperationContext ctx, String listingId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(listingId, "listingId must not be null");
        return kernelAdapter.isAuthorized(ctx, "marketplace-listing", "unpublish")
            .then(allowed -> {
                if (!allowed) return Promise.ofException(new SecurityException("Not authorized to unpublish marketplace listing"));
                return repository.findById(listingId);
            })
            .then(opt -> {
                DmMarketplaceListing existing = opt.orElseThrow(() ->
                    new NoSuchElementException("Listing not found: " + listingId));
                if (!existing.getTenantId().equals(ctx.getTenantId().getValue()))
                    return Promise.ofException(new SecurityException("Listing does not belong to tenant"));
                return repository.update(existing.unpublish());
            })
            .then(updated -> kernelAdapter.recordAudit(ctx, updated.getId(), "marketplace-listing.unpublish",
                Map.of("status", (Object) updated.getStatus().name()))
                .map(__ -> updated));
    }

    @Override
    public Promise<Optional<DmMarketplaceListing>> findById(DmOperationContext ctx, String listingId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(listingId, "listingId must not be null");
        return repository.findById(listingId);
    }

    @Override
    public Promise<List<DmMarketplaceListing>> listByTenant(DmOperationContext ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return repository.listByTenant(ctx.getTenantId().getValue());
    }

    @Override
    public Promise<List<DmMarketplaceListing>> listPublished(DmOperationContext ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return repository.listByStatus(DmMarketplaceListingStatus.PUBLISHED);
    }
}
