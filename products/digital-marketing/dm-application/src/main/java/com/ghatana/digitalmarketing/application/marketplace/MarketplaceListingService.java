package com.ghatana.digitalmarketing.application.marketplace;

import com.ghatana.digitalmarketing.domain.marketplace.MarketplaceListing;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import io.activej.promise.Promise;

/**
 * Service for managing marketplace listings (DMOS-P3-004).
 *
 * @doc.type interface
 * @doc.purpose Service interface for marketplace listing operations
 * @doc.layer application
 */
public interface MarketplaceListingService {

    /**
     * Create a new marketplace listing (playbook publishing).
     */
    Promise<MarketplaceListing> createListing(DmTenantId authorTenantId, String name, String description, String version);

    /**
     * Get a marketplace listing by ID.
     */
    Promise<MarketplaceListing> getListing(String listingId);

    /**
     * Get all published marketplace listings (marketplace listing).
     */
    Promise<java.util.List<MarketplaceListing>> getPublishedListings();

    /**
     * Get listings by author tenant.
     */
    Promise<java.util.List<MarketplaceListing>> getListingsByAuthor(DmTenantId authorTenantId);

    /**
     * Submit a listing for review (review/approval).
     */
    Promise<MarketplaceListing> submitForReview(String listingId);

    /**
     * Approve a listing (review/approval).
     */
    Promise<MarketplaceListing> approveListing(String listingId);

    /**
     * Reject a listing (review/approval).
     */
    Promise<MarketplaceListing> rejectListing(String listingId);

    /**
     * Update a listing (versioning).
     */
    Promise<MarketplaceListing> updateListing(String listingId, String name, String description, String version);

    /**
     * Install a listing for a tenant (tenant install/uninstall).
     */
    Promise<Void> installListing(DmTenantId tenantId, String listingId);

    /**
     * Uninstall a listing for a tenant (tenant install/uninstall).
     */
    Promise<Void> uninstallListing(DmTenantId tenantId, String listingId);

    /**
     * Record a download.
     */
    Promise<Void> recordDownload(String listingId);
}
