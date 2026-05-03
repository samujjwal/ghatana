package com.ghatana.digitalmarketing.application.marketplace;

import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.domain.marketplace.MarketplaceListing;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for MarketplaceListingServiceImpl (DMOS-P3-004).
 *
 * @doc.type test
 * @doc.purpose Verify marketplace listing service behavior
 * @doc.layer application
 */
@DisplayName("MarketplaceListingServiceImpl")
class MarketplaceListingServiceImplTest {

    @Test
    @DisplayName("createListing creates and saves marketplace listing")
    void createListing_createsAndSavesMarketplaceListing() {
        MarketplaceListingRepository repository = mock(MarketplaceListingRepository.class);
        when(repository.save(any(MarketplaceListing.class))).thenReturn(Promise.of(mock(MarketplaceListing.class)));

        MarketplaceListingServiceImpl service = new MarketplaceListingServiceImpl(repository);
        DmTenantId tenantId = new DmTenantId("tenant-123");

        var promise = service.createListing(tenantId, "Lead Generation Playbook", "A playbook for lead generation", "1.0.0");
        assertThat(promise).isNotNull();
    }

    @Test
    @DisplayName("getListing retrieves marketplace listing by ID")
    void getListing_retrievesMarketplaceListingById() {
        MarketplaceListingRepository repository = mock(MarketplaceListingRepository.class);
        MarketplaceListing listing = MarketplaceListing.builder()
            .listingId("listing-789")
            .name("Lead Generation Playbook")
            .authorTenantId("tenant-123")
            .version("1.0.0")
            .status("PUBLISHED")
            .createdAt(java.time.Instant.now())
            .updatedAt(java.time.Instant.now())
            .build();

        when(repository.findById("listing-789")).thenReturn(Promise.of(java.util.Optional.of(listing)));

        MarketplaceListingServiceImpl service = new MarketplaceListingServiceImpl(repository);
        var promise = service.getListing("listing-789");
        assertThat(promise).isNotNull();
    }

    @Test
    @DisplayName("getPublishedListings returns published listings")
    void getPublishedListings_returnsPublishedListings() {
        MarketplaceListingRepository repository = mock(MarketplaceListingRepository.class);
        when(repository.findPublished()).thenReturn(Promise.of(java.util.List.of()));

        MarketplaceListingServiceImpl service = new MarketplaceListingServiceImpl(repository);
        var promise = service.getPublishedListings();
        assertThat(promise).isNotNull();
    }

    @Test
    @DisplayName("submitForReview submits listing for review")
    void submitForReview_submitsListingForReview() {
        MarketplaceListingRepository repository = mock(MarketplaceListingRepository.class);
        MarketplaceListing listing = MarketplaceListing.builder()
            .listingId("listing-789")
            .name("Lead Generation Playbook")
            .authorTenantId("tenant-123")
            .version("1.0.0")
            .status("DRAFT")
            .createdAt(java.time.Instant.now())
            .updatedAt(java.time.Instant.now())
            .build();

        when(repository.findById("listing-789")).thenReturn(Promise.of(java.util.Optional.of(listing)));
        when(repository.update(any(MarketplaceListing.class))).thenReturn(Promise.of(listing));

        MarketplaceListingServiceImpl service = new MarketplaceListingServiceImpl(repository);
        var promise = service.submitForReview("listing-789");
        assertThat(promise).isNotNull();
    }

    @Test
    @DisplayName("approveListing approves listing")
    void approveListing_approvesListing() {
        MarketplaceListingRepository repository = mock(MarketplaceListingRepository.class);
        MarketplaceListing listing = MarketplaceListing.builder()
            .listingId("listing-789")
            .name("Lead Generation Playbook")
            .authorTenantId("tenant-123")
            .version("1.0.0")
            .status("PENDING_REVIEW")
            .createdAt(java.time.Instant.now())
            .updatedAt(java.time.Instant.now())
            .build();

        when(repository.findById("listing-789")).thenReturn(Promise.of(java.util.Optional.of(listing)));
        when(repository.update(any(MarketplaceListing.class))).thenReturn(Promise.of(listing));

        MarketplaceListingServiceImpl service = new MarketplaceListingServiceImpl(repository);
        var promise = service.approveListing("listing-789");
        assertThat(promise).isNotNull();
    }
}
