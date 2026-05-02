package com.ghatana.digitalmarketing.application.brand;

import com.ghatana.digitalmarketing.application.offer.ProductOfferRepository;
import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.brand.BrandProfile;
import com.ghatana.digitalmarketing.domain.offer.ProductOffer;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

/**
 * Production implementation of {@link BrandCatalogService}.
 *
 * @doc.type class
 * @doc.purpose DMOS brand plus offer catalog lifecycle implementation
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class BrandCatalogServiceImpl implements BrandCatalogService {

    private final DigitalMarketingKernelAdapter kernelAdapter;
    private final BrandProfileRepository brandRepository;
    private final ProductOfferRepository offerRepository;

    public BrandCatalogServiceImpl(
            DigitalMarketingKernelAdapter kernelAdapter,
            BrandProfileRepository brandRepository,
            ProductOfferRepository offerRepository) {
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
        this.brandRepository = Objects.requireNonNull(brandRepository, "brandRepository must not be null");
        this.offerRepository = Objects.requireNonNull(offerRepository, "offerRepository must not be null");
    }

    @Override
    public Promise<BrandProfile> upsertBrandProfile(DmOperationContext ctx, UpsertBrandProfileCommand command) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "brands/*", "write")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(new SecurityException("Actor not authorized to update brand profile"));
                }
                return brandRepository.findByWorkspace(ctx.getWorkspaceId())
                    .then(existing -> {
                        Instant now = Instant.now();
                        BrandProfile profile = existing
                            .map(value -> BrandProfile.builder()
                                .id(value.getId())
                                .workspaceId(value.getWorkspaceId())
                                .displayName(command.displayName())
                                .voiceTone(command.voiceTone())
                                .brandColors(command.brandColors())
                                .targetGeographies(command.targetGeographies())
                                .createdAt(value.getCreatedAt())
                                .updatedAt(now)
                                .createdBy(value.getCreatedBy())
                                .build())
                            .orElseGet(() -> BrandProfile.builder()
                                .id(UUID.randomUUID().toString())
                                .workspaceId(ctx.getWorkspaceId())
                                .displayName(command.displayName())
                                .voiceTone(command.voiceTone())
                                .brandColors(command.brandColors())
                                .targetGeographies(command.targetGeographies())
                                .createdAt(now)
                                .updatedAt(now)
                                .createdBy(ctx.getActor().getPrincipalId())
                                .build());

                        return brandRepository.save(profile)
                            .then(saved -> kernelAdapter.recordAudit(
                                ctx,
                                "brands/" + saved.getId(),
                                "upsert",
                                Map.of("displayName", saved.getDisplayName())
                            ).map(__ -> saved));
                    });
            });
    }

    @Override
    public Promise<BrandProfile> getBrandProfile(DmOperationContext ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");

        return kernelAdapter.isAuthorized(ctx, "brands/*", "read")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(new SecurityException("Actor not authorized to read brand profile"));
                }
                return brandRepository.findByWorkspace(ctx.getWorkspaceId())
                    .then(existing -> existing
                        .map(Promise::of)
                        .orElseGet(() -> Promise.ofException(new NoSuchElementException("Brand profile not found"))));
            });
    }

    @Override
    public Promise<ProductOffer> createOffer(DmOperationContext ctx, CreateOfferCommand command) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "offers/*", "write")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(new SecurityException("Actor not authorized to create offer"));
                }

                Instant now = Instant.now();
                ProductOffer offer = ProductOffer.builder()
                    .id(UUID.randomUUID().toString())
                    .workspaceId(ctx.getWorkspaceId())
                    .offerName(command.offerName())
                    .offerDescription(command.offerDescription())
                    .basePrice(command.basePrice())
                    .currencyCode(command.currencyCode())
                    .active(true)
                    .createdAt(now)
                    .updatedAt(now)
                    .createdBy(ctx.getActor().getPrincipalId())
                    .build();

                return offerRepository.save(offer)
                    .then(saved -> kernelAdapter.recordAudit(
                        ctx,
                        "offers/" + saved.getId(),
                        "create",
                        Map.of("offerName", saved.getOfferName())
                    ).map(__ -> saved));
            });
    }

    @Override
    public Promise<ProductOffer> updateOffer(DmOperationContext ctx, String offerId, UpdateOfferCommand command) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(offerId, "offerId must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "offers/" + offerId, "write")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(new SecurityException("Actor not authorized to update offer"));
                }
                return offerRepository.findById(ctx.getWorkspaceId(), offerId)
                    .then(existing -> {
                        if (existing.isEmpty()) {
                            return Promise.ofException(new NoSuchElementException("Offer not found: " + offerId));
                        }

                        ProductOffer updated = existing.get().toBuilder()
                            .offerName(command.offerName())
                            .offerDescription(command.offerDescription())
                            .basePrice(command.basePrice())
                            .currencyCode(command.currencyCode())
                            .active(command.active())
                            .updatedAt(Instant.now())
                            .build();

                        return offerRepository.save(updated)
                            .then(saved -> kernelAdapter.recordAudit(
                                ctx,
                                "offers/" + saved.getId(),
                                "update",
                                Map.of("active", Boolean.toString(saved.isActive()))
                            ).map(__ -> saved));
                    });
            });
    }

    @Override
    public Promise<List<ProductOffer>> listOffers(DmOperationContext ctx, boolean includeInactive) {
        Objects.requireNonNull(ctx, "ctx must not be null");

        return kernelAdapter.isAuthorized(ctx, "offers/*", "read")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(new SecurityException("Actor not authorized to list offers"));
                }
                return offerRepository.listByWorkspace(ctx.getWorkspaceId(), includeInactive);
            });
    }
}
