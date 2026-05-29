package com.ghatana.digitalmarketing.domain.marketplace;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

@DisplayName("DmMarketplaceListing domain entity")
class DmMarketplaceListingTest {

    private DmMarketplaceListing valid() {
        return DmMarketplaceListing.builder()
            .id("ml-1").tenantId("t1").itemType("PLAYBOOK").itemId("pb-1")
            .title("Starter Playbook").description("Good for SMEs")
            .priceMicros(5_000_000L).currency("USD")
            .status(DmMarketplaceListingStatus.DRAFT)
            .createdAt(Instant.now()).build();
    }

    @Test @DisplayName("builder creates valid entity")
    void shouldBuildValid() {
        DmMarketplaceListing m = valid();
        assertThat(m.getId()).isEqualTo("ml-1");
        assertThat(m.getStatus()).isEqualTo(DmMarketplaceListingStatus.DRAFT);
    }

    @Test @DisplayName("builder rejects blank id")
    void shouldRejectBlankId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmMarketplaceListing.builder().id("").tenantId("t").itemType("t").itemId("i")
                .title("n").status(DmMarketplaceListingStatus.DRAFT).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("builder rejects blank title")
    void shouldRejectBlankTitle() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmMarketplaceListing.builder().id("x").tenantId("t").itemType("t").itemId("i")
                .title("").status(DmMarketplaceListingStatus.DRAFT).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("publish from DRAFT succeeds")
    void shouldPublish() {
        DmMarketplaceListing published = valid().publish();
        assertThat(published.getStatus()).isEqualTo(DmMarketplaceListingStatus.PUBLISHED);
        assertThat(published.getPublishedAt()).isNotNull();
    }

    @Test @DisplayName("publish from non-DRAFT fails")
    void shouldNotPublishTwice() {
        assertThatIllegalStateException().isThrownBy(() -> valid().publish().publish());
    }

    @Test @DisplayName("unpublish from PUBLISHED succeeds")
    void shouldUnpublish() {
        DmMarketplaceListing unpublished = valid().publish().unpublish();
        assertThat(unpublished.getStatus()).isEqualTo(DmMarketplaceListingStatus.UNLISTED);
    }

    @Test @DisplayName("unpublish from non-PUBLISHED fails")
    void shouldNotUnpublishFromDraft() {
        assertThatIllegalStateException().isThrownBy(() -> valid().unpublish());
    }

    @Test @DisplayName("equals and hashCode are id-based")
    void shouldEqualById() {
        assertThat(valid()).isEqualTo(valid());
    }

    @Test @DisplayName("null id throws")
    void shouldRejectNullId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmMarketplaceListing.builder().id(null).tenantId("t").itemType("t").itemId("i").title("n")
                .status(DmMarketplaceListingStatus.DRAFT).createdAt(java.time.Instant.now()).build());
    }

    @Test @DisplayName("blank tenantId throws")
    void shouldRejectBlankTenantId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmMarketplaceListing.builder().id("x").tenantId("").itemType("t").itemId("i").title("n")
                .status(DmMarketplaceListingStatus.DRAFT).createdAt(java.time.Instant.now()).build());
    }

    @Test @DisplayName("null tenantId throws")
    void shouldRejectNullTenantId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmMarketplaceListing.builder().id("x").tenantId(null).itemType("t").itemId("i").title("n")
                .status(DmMarketplaceListingStatus.DRAFT).createdAt(java.time.Instant.now()).build());
    }
}
