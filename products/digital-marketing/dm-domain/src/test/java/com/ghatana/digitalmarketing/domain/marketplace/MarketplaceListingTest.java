package com.ghatana.digitalmarketing.domain.marketplace;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for MarketplaceListing (DMOS-P3-004).
 *
 * @doc.type test
 * @doc.purpose Verify marketplace listing domain entity behavior
 * @doc.layer domain
 */
@DisplayName("MarketplaceListing")
class MarketplaceListingTest {

    @Test
    @DisplayName("builder creates valid marketplace listing")
    void builder_createsValidMarketplaceListing() {
        Instant now = Instant.now();

        MarketplaceListing listing = MarketplaceListing.builder()
            .listingId("listing-789")
            .name("Lead Generation Playbook")
            .description("A playbook for lead generation campaigns")
            .authorTenantId("tenant-123")
            .version("1.0.0")
            .status("DRAFT")
            .rating(4.5)
            .downloadCount(100)
            .createdAt(now)
            .updatedAt(now)
            .build();

        assertThat(listing.getListingId()).isEqualTo("listing-789");
        assertThat(listing.getName()).isEqualTo("Lead Generation Playbook");
        assertThat(listing.getAuthorTenantId()).isEqualTo("tenant-123");
        assertThat(listing.getVersion()).isEqualTo("1.0.0");
        assertThat(listing.getStatus()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("builder creates published listing")
    void builder_createsPublishedListing() {
        MarketplaceListing listing = MarketplaceListing.builder()
            .listingId("listing-789")
            .name("Lead Generation Playbook")
            .authorTenantId("tenant-123")
            .version("1.0.0")
            .status("PUBLISHED")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        assertThat(listing.getStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("builder handles null optional fields")
    void builder_handlesNullOptionalFields() {
        MarketplaceListing listing = MarketplaceListing.builder()
            .listingId("listing-789")
            .name("Lead Generation Playbook")
            .authorTenantId("tenant-123")
            .version("1.0.0")
            .status("DRAFT")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        assertThat(listing.getDescription()).isNull();
        assertThat(listing.getRating()).isEqualTo(0.0);
        assertThat(listing.getDownloadCount()).isEqualTo(0);
    }
}
