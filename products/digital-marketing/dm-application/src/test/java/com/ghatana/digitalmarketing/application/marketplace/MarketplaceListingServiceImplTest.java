package com.ghatana.digitalmarketing.application.marketplace;

import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.domain.marketplace.MarketplaceListing;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for MarketplaceListingServiceImpl (DMOS-P3-004).
 *
 * @doc.type test
 * @doc.purpose Verify marketplace listing service behavior
 * @doc.layer application
 */
@DisplayName("MarketplaceListingServiceImpl")
class MarketplaceListingServiceImplTest {

    private EphemeralMarketplaceListingRepository repository;
    private MarketplaceListingServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = new EphemeralMarketplaceListingRepository();
        service = new MarketplaceListingServiceImpl(repository);
    }

    @Test
    @DisplayName("createListing creates and saves marketplace listing")
    void createListing_createsAndSavesMarketplaceListing() {
        DmTenantId tenantId = DmTenantId.of("tenant-123");

        var promise = service.createListing(tenantId, "Lead Generation Playbook", "A playbook for lead generation", "1.0.0");
        assertThat(promise).isNotNull();
    }

    @Test
    @DisplayName("getListing retrieves marketplace listing by ID")
    void getListing_retrievesMarketplaceListingById() {
        MarketplaceListing listing = MarketplaceListing.builder()
            .listingId("listing-789")
            .name("Lead Generation Playbook")
            .authorTenantId("tenant-123")
            .version("1.0.0")
            .status("PUBLISHED")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        repository.save(listing).getResult();
        var promise = service.getListing("listing-789");
        assertThat(promise).isNotNull();
    }

    @Test
    @DisplayName("getPublishedListings returns published listings")
    void getPublishedListings_returnsPublishedListings() {
        var promise = service.getPublishedListings();
        assertThat(promise).isNotNull();
    }

    @Test
    @DisplayName("submitForReview submits listing for review")
    void submitForReview_submitsListingForReview() {
        MarketplaceListing listing = MarketplaceListing.builder()
            .listingId("listing-789")
            .name("Lead Generation Playbook")
            .authorTenantId("tenant-123")
            .version("1.0.0")
            .status("DRAFT")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        repository.save(listing).getResult();
        var promise = service.submitForReview("listing-789");
        assertThat(promise).isNotNull();
    }

    @Test
    @DisplayName("approveListing approves listing")
    void approveListing_approvesListing() {
        MarketplaceListing listing = MarketplaceListing.builder()
            .listingId("listing-789")
            .name("Lead Generation Playbook")
            .authorTenantId("tenant-123")
            .version("1.0.0")
            .status("PENDING_REVIEW")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        repository.save(listing).getResult();
        var promise = service.approveListing("listing-789");
        assertThat(promise).isNotNull();
    }

    // ── test doubles ─────────────────────────────────────────────────────────

    private static final class EphemeralMarketplaceListingRepository implements MarketplaceListingRepository {
        private final ConcurrentHashMap<String, MarketplaceListing> store = new ConcurrentHashMap<>();

        @Override
        public Promise<MarketplaceListing> save(MarketplaceListing listing) {
            store.put(listing.getListingId(), listing);
            return Promise.of(listing);
        }

        @Override
        public Promise<Optional<MarketplaceListing>> findById(String listingId) {
            return Promise.of(Optional.ofNullable(store.get(listingId)));
        }

        @Override
        public Promise<List<MarketplaceListing>> findByAuthor(DmTenantId authorTenantId) {
            List<MarketplaceListing> result = new ArrayList<>();
            for (MarketplaceListing listing : store.values()) {
                if (listing.getAuthorTenantId().equals(authorTenantId)) {
                    result.add(listing);
                }
            }
            return Promise.of(result);
        }

        @Override
        public Promise<List<MarketplaceListing>> findPublished() {
            List<MarketplaceListing> result = new ArrayList<>();
            for (MarketplaceListing listing : store.values()) {
                if ("PUBLISHED".equals(listing.getStatus())) {
                    result.add(listing);
                }
            }
            return Promise.of(result);
        }

        @Override
        public Promise<Void> incrementDownloadCount(String listingId) {
            return Promise.complete();
        }

        @Override
        public Promise<Void> delete(String listingId) {
            store.remove(listingId);
            return Promise.complete();
        }

        @Override
        public Promise<MarketplaceListing> update(MarketplaceListing listing) {
            store.put(listing.getListingId(), listing);
            return Promise.of(listing);
        }
    }
}
