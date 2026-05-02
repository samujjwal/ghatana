package com.ghatana.digitalmarketing.domain.offer;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@DisplayName("ProductOffer")
class ProductOfferTest {

    @Test
    @DisplayName("builds valid offer and supports update/deactivate")
    void shouldBuildAndMutate() {
        Instant now = Instant.now();
        ProductOffer offer = ProductOffer.builder()
            .id("offer-1")
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .offerName("Starter SEO")
            .offerDescription("Baseline SEO package")
            .basePrice(new BigDecimal("299.00"))
            .currencyCode("USD")
            .active(true)
            .createdAt(now)
            .updatedAt(now)
            .createdBy("user-1")
            .build();

        ProductOffer updated = offer.update("Starter SEO+", "Expanded", new BigDecimal("399.00"), "USD");
        ProductOffer deactivated = updated.deactivate();

        assertThat(offer.getId()).isEqualTo("offer-1");
        assertThat(offer.getWorkspaceId()).isEqualTo(DmWorkspaceId.of("ws-1"));
        assertThat(offer.getOfferDescription()).isEqualTo("Baseline SEO package");
        assertThat(offer.getCurrencyCode()).isEqualTo("USD");
        assertThat(offer.getCreatedAt()).isEqualTo(now);
        assertThat(offer.getUpdatedAt()).isEqualTo(now);
        assertThat(offer.getCreatedBy()).isEqualTo("user-1");
        assertThat(updated.getOfferName()).isEqualTo("Starter SEO+");
        assertThat(updated.getBasePrice()).isEqualByComparingTo("399.00");
        assertThat(deactivated.isActive()).isFalse();
    }

    @Test
    @DisplayName("rejects negative price")
    void shouldRejectNegativePrice() {
        Instant now = Instant.now();
        assertThatIllegalArgumentException().isThrownBy(() -> ProductOffer.builder()
            .id("offer-1")
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .offerName("Starter")
            .basePrice(new BigDecimal("-1.00"))
            .currencyCode("USD")
            .active(true)
            .createdAt(now)
            .updatedAt(now)
            .createdBy("user-1")
            .build());

        assertThatIllegalArgumentException().isThrownBy(() -> ProductOffer.builder()
            .id(" ")
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .offerName("Starter")
            .basePrice(new BigDecimal("10.00"))
            .currencyCode("USD")
            .active(true)
            .createdAt(now)
            .updatedAt(now)
            .createdBy("user-1")
            .build());

        assertThatIllegalArgumentException().isThrownBy(() -> ProductOffer.builder()
            .id("offer-1")
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .offerName("Starter")
            .basePrice(new BigDecimal("10.00"))
            .currencyCode(" ")
            .active(true)
            .createdAt(now)
            .updatedAt(now)
            .createdBy("user-1")
            .build());
    }
}
