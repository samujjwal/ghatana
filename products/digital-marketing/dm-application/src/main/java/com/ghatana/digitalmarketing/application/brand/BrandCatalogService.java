package com.ghatana.digitalmarketing.application.brand;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.brand.BrandProfile;
import com.ghatana.digitalmarketing.domain.offer.ProductOffer;
import io.activej.promise.Promise;

import java.math.BigDecimal;
import java.util.List;

/**
 * Application service for brand profile and product offer catalog lifecycle.
 *
 * @doc.type interface
 * @doc.purpose DMOS F1 brand profile and offer catalog application service
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public interface BrandCatalogService {

    Promise<BrandProfile> upsertBrandProfile(DmOperationContext ctx, UpsertBrandProfileCommand command);

    Promise<BrandProfile> getBrandProfile(DmOperationContext ctx);

    Promise<ProductOffer> createOffer(DmOperationContext ctx, CreateOfferCommand command);

    Promise<ProductOffer> updateOffer(DmOperationContext ctx, String offerId, UpdateOfferCommand command);

    Promise<List<ProductOffer>> listOffers(DmOperationContext ctx, boolean includeInactive);

    record UpsertBrandProfileCommand(
        String displayName,
        String voiceTone,
        List<String> brandColors,
        List<String> targetGeographies
    ) {
        public UpsertBrandProfileCommand {
            if (displayName == null || displayName.isBlank()) {
                throw new IllegalArgumentException("displayName must not be blank");
            }
            brandColors = brandColors != null ? List.copyOf(brandColors) : List.of();
            targetGeographies = targetGeographies != null ? List.copyOf(targetGeographies) : List.of();
        }
    }

    record CreateOfferCommand(
        String offerName,
        String offerDescription,
        BigDecimal basePrice,
        String currencyCode
    ) {
        public CreateOfferCommand {
            if (offerName == null || offerName.isBlank()) {
                throw new IllegalArgumentException("offerName must not be blank");
            }
            if (basePrice == null || basePrice.signum() < 0) {
                throw new IllegalArgumentException("basePrice must be non-negative");
            }
            if (currencyCode == null || currencyCode.isBlank()) {
                throw new IllegalArgumentException("currencyCode must not be blank");
            }
        }
    }

    record UpdateOfferCommand(
        String offerName,
        String offerDescription,
        BigDecimal basePrice,
        String currencyCode,
        boolean active
    ) {
        public UpdateOfferCommand {
            if (offerName == null || offerName.isBlank()) {
                throw new IllegalArgumentException("offerName must not be blank");
            }
            if (basePrice == null || basePrice.signum() < 0) {
                throw new IllegalArgumentException("basePrice must be non-negative");
            }
            if (currencyCode == null || currencyCode.isBlank()) {
                throw new IllegalArgumentException("currencyCode must not be blank");
            }
        }
    }
}
