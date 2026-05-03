package com.ghatana.digitalmarketing.application.marketplace;

import com.ghatana.digitalmarketing.domain.marketplace.MarketplaceListing;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import io.activej.promise.Promise;

import java.util.Optional;

/**
 * Repository interface for MarketplaceListing entities (DMOS-P3-004).
 *
 * @doc.type interface
 * @doc.purpose Repository interface for marketplace listing operations
 * @doc.layer application
 */
public interface MarketplaceListingRepository {

    /**
     * Save a marketplace listing.
     */
    Promise<MarketplaceListing> save(MarketplaceListing listing);

    /**
     * Find a marketplace listing by ID.
     */
    Promise<Optional<MarketplaceListing>> findById(String listingId);

    /**
     * Find all published marketplace listings.
     */
    Promise<java.util.List<MarketplaceListing>> findPublished();

    /**
     * Find listings by author tenant.
     */
    Promise<java.util.List<MarketplaceListing>> findByAuthor(DmTenantId authorTenantId);

    /**
     * Update a marketplace listing.
     */
    Promise<MarketplaceListing> update(MarketplaceListing listing);

    /**
     * Delete a marketplace listing.
     */
    Promise<Void> delete(String listingId);

    /**
     * Increment download count.
     */
    Promise<Void> incrementDownloadCount(String listingId);
}
