package com.ghatana.digitalmarketing.application.marketplace;

import com.ghatana.digitalmarketing.domain.marketplace.MarketplaceListing;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;

/**
 * Service implementation for managing marketplace listings (DMOS-P3-004).
 *
 * @doc.type class
 * @doc.purpose Service implementation for marketplace listing operations
 * @doc.layer application
 */
public final class MarketplaceListingServiceImpl implements MarketplaceListingService {

    private static final Logger logger = LoggerFactory.getLogger(MarketplaceListingServiceImpl.class);

    private final MarketplaceListingRepository repository;

    public MarketplaceListingServiceImpl(MarketplaceListingRepository repository) {
        this.repository = repository;
    }

    @Override
    public Promise<MarketplaceListing> createListing(DmTenantId authorTenantId, String name, String description, String version) {
        String listingId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        MarketplaceListing listing = MarketplaceListing.builder()
            .listingId(listingId)
            .name(name)
            .description(description)
            .authorTenantId(authorTenantId.getValue())
            .version(version)
            .status("DRAFT")
            .rating(0.0)
            .downloadCount(0)
            .createdAt(now)
            .updatedAt(now)
            .build();

        logger.info("Creating marketplace listing: {} for tenant: {}", name, authorTenantId.getValue());
        return repository.save(listing);
    }

    @Override
    public Promise<MarketplaceListing> getListing(String listingId) {
        return repository.findById(listingId)
            .then(listingOpt -> listingOpt.orElseThrow(() -> new IllegalArgumentException("Marketplace listing not found: " + listingId)));
    }

    @Override
    public Promise<java.util.List<MarketplaceListing>> getPublishedListings() {
        logger.info("Fetching published marketplace listings");
        return repository.findPublished();
    }

    @Override
    public Promise<java.util.List<MarketplaceListing>> getListingsByAuthor(DmTenantId authorTenantId) {
        logger.info("Fetching marketplace listings for author: {}", authorTenantId.getValue());
        return repository.findByAuthor(authorTenantId);
    }

    @Override
    public Promise<MarketplaceListing> submitForReview(String listingId) {
        return getListing(listingId)
            .then(listing -> {
                MarketplaceListing updated = MarketplaceListing.builder()
                    .listingId(listing.getListingId())
                    .name(listing.getName())
                    .description(listing.getDescription())
                    .authorTenantId(listing.getAuthorTenantId())
                    .version(listing.getVersion())
                    .status("PENDING_REVIEW")
                    .rating(listing.getRating())
                    .downloadCount(listing.getDownloadCount())
                    .createdAt(listing.getCreatedAt())
                    .updatedAt(Instant.now())
                    .build();

                logger.info("Submitted marketplace listing for review: {}", listingId);
                return repository.update(updated);
            });
    }

    @Override
    public Promise<MarketplaceListing> approveListing(String listingId) {
        return getListing(listingId)
            .then(listing -> {
                MarketplaceListing updated = MarketplaceListing.builder()
                    .listingId(listing.getListingId())
                    .name(listing.getName())
                    .description(listing.getDescription())
                    .authorTenantId(listing.getAuthorTenantId())
                    .version(listing.getVersion())
                    .status("PUBLISHED")
                    .rating(listing.getRating())
                    .downloadCount(listing.getDownloadCount())
                    .createdAt(listing.getCreatedAt())
                    .updatedAt(Instant.now())
                    .build();

                logger.info("Approved marketplace listing: {}", listingId);
                return repository.update(updated);
            });
    }

    @Override
    public Promise<MarketplaceListing> rejectListing(String listingId) {
        return getListing(listingId)
            .then(listing -> {
                MarketplaceListing updated = MarketplaceListing.builder()
                    .listingId(listing.getListingId())
                    .name(listing.getName())
                    .description(listing.getDescription())
                    .authorTenantId(listing.getAuthorTenantId())
                    .version(listing.getVersion())
                    .status("REJECTED")
                    .rating(listing.getRating())
                    .downloadCount(listing.getDownloadCount())
                    .createdAt(listing.getCreatedAt())
                    .updatedAt(Instant.now())
                    .build();

                logger.info("Rejected marketplace listing: {}", listingId);
                return repository.update(updated);
            });
    }

    @Override
    public Promise<MarketplaceListing> updateListing(String listingId, String name, String description, String version) {
        return getListing(listingId)
            .then(listing -> {
                MarketplaceListing updated = MarketplaceListing.builder()
                    .listingId(listing.getListingId())
                    .name(name)
                    .description(description)
                    .authorTenantId(listing.getAuthorTenantId())
                    .version(version)
                    .status(listing.getStatus())
                    .rating(listing.getRating())
                    .downloadCount(listing.getDownloadCount())
                    .createdAt(listing.getCreatedAt())
                    .updatedAt(Instant.now())
                    .build();

                logger.info("Updated marketplace listing: {}", listingId);
                return repository.update(updated);
            });
    }

    @Override
    public Promise<Void> installListing(DmTenantId tenantId, String listingId) {
        logger.info("Installing marketplace listing: {} for tenant: {}", listingId, tenantId.getValue());
        return recordDownload(listingId);
    }

    @Override
    public Promise<Void> uninstallListing(DmTenantId tenantId, String listingId) {
        logger.info("Uninstalling marketplace listing: {} for tenant: {}", listingId, tenantId.getValue());
        return Promise.of(null);
    }

    @Override
    public Promise<Void> recordDownload(String listingId) {
        return repository.incrementDownloadCount(listingId);
    }
}
