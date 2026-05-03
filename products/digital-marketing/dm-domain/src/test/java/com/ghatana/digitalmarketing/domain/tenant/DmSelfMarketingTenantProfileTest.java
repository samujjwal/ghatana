package com.ghatana.digitalmarketing.domain.tenant;

import com.ghatana.digitalmarketing.domain.tenant.DmSelfMarketingTenantProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

@DisplayName("DmSelfMarketingTenantProfile domain entity")
class DmSelfMarketingTenantProfileTest {

    private DmSelfMarketingTenantProfile valid() {
        return DmSelfMarketingTenantProfile.builder()
            .id("prof-1").tenantId("t1").displayName("Acme Corp")
            .industry("TECH").timezone("UTC").defaultCurrency("USD")
            .maxActiveConnectors(5).maxCampaignsPerMonth(10)
            .maxMonthlyBudgetMicros(10_000_000L).killSwitchEnabled(false)
            .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    @Test @DisplayName("builder creates valid entity")
    void shouldBuildValid() {
        DmSelfMarketingTenantProfile p = valid();
        assertThat(p.getId()).isEqualTo("prof-1");
        assertThat(p.getDisplayName()).isEqualTo("Acme Corp");
    }

    @Test @DisplayName("builder rejects blank id")
    void shouldRejectBlankId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmSelfMarketingTenantProfile.builder().id("").tenantId("t").displayName("n")
                .createdAt(Instant.now()).updatedAt(Instant.now()).build());
    }

    @Test @DisplayName("isBudgetAllowed returns true under limit")
    void shouldAllowBudgetUnderLimit() {
        assertThat(valid().isBudgetAllowed(5_000_000L)).isTrue();
    }

    @Test @DisplayName("isBudgetAllowed returns false over limit")
    void shouldNotAllowBudgetOverLimit() {
        assertThat(valid().isBudgetAllowed(20_000_000L)).isFalse();
    }

    @Test @DisplayName("equals and hashCode are id-based")
    void shouldEqualById() {
        assertThat(valid()).isEqualTo(valid());
        assertThat(valid().hashCode()).isEqualTo(valid().hashCode());
    }

    @Test @DisplayName("equals returns false for null")
    void shouldNotEqualNull() { assertThat(valid()).isNotEqualTo(null); }

    @Test @DisplayName("equals returns false for different type")
    void shouldNotEqualDifferentType() { assertThat(valid()).isNotEqualTo("x"); }

    @Test @DisplayName("null tenantId throws")
    void shouldRejectNullTenantId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmSelfMarketingTenantProfile.builder().id("x").tenantId(null).displayName("n")
                .createdAt(Instant.now()).build());
    }

    @Test @DisplayName("blank displayName throws")
    void shouldRejectBlankDisplayName() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmSelfMarketingTenantProfile.builder().id("x").tenantId("t").displayName("")
                .createdAt(Instant.now()).build());
    }

    @Test @DisplayName("null createdAt throws")
    void shouldRejectNullCreatedAt() {
        assertThatNullPointerException().isThrownBy(() ->
            DmSelfMarketingTenantProfile.builder().id("x").tenantId("t").displayName("n")
                .createdAt(null).build());
    }

    @Test @DisplayName("all getters return expected values")
    void shouldExposeAllGetters() {
        DmSelfMarketingTenantProfile p = valid();
        assertThat(p.getTenantId()).isNotNull();
        assertThat(p.getDisplayName()).isNotNull();
        assertThat(p.getMaxMonthlyBudgetMicros()).isGreaterThan(0);
        assertThat(p.getCreatedAt()).isNotNull();
        assertThat(p.getUpdatedAt()).isNotNull();
        assertThat(p.toString()).isNotNull();
    }
}
