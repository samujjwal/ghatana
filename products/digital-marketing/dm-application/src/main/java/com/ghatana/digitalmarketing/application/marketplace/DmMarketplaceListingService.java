package com.ghatana.digitalmarketing.application.marketplace;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.marketplace.DmMarketplaceListing;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Application service for managing marketplace listings.
 *
 * @doc.type interface
 * @doc.purpose Use-case boundary for marketplace listing management (DMOS-F5-001)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface DmMarketplaceListingService {

    Promise<DmMarketplaceListing> create(DmOperationContext ctx, CreateMarketplaceListingCommand cmd);

    Promise<DmMarketplaceListing> publish(DmOperationContext ctx, String listingId);

    Promise<DmMarketplaceListing> unpublish(DmOperationContext ctx, String listingId);

    Promise<Optional<DmMarketplaceListing>> findById(DmOperationContext ctx, String listingId);

    Promise<List<DmMarketplaceListing>> listByTenant(DmOperationContext ctx);

    Promise<List<DmMarketplaceListing>> listPublished(DmOperationContext ctx);

    record CreateMarketplaceListingCommand(
            String itemType,
            String itemId,
            String title,
            String description,
            long priceMicros,
            String currency
    ) {
        public CreateMarketplaceListingCommand {
            if (title == null || title.isBlank()) throw new IllegalArgumentException("title must not be blank");
            if (itemType == null || itemType.isBlank()) throw new IllegalArgumentException("itemType must not be blank");
            if (itemId == null || itemId.isBlank()) throw new IllegalArgumentException("itemId must not be blank");
            if (currency == null || currency.isBlank()) throw new IllegalArgumentException("currency must not be blank");
            if (priceMicros < 0) throw new IllegalArgumentException("priceMicros must not be negative");
        }
    }
}
