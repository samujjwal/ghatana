package com.ghatana.digitalmarketing.domain.agency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("DmAgencyProfile domain entity")
class DmAgencyProfileTest {

    private DmAgencyProfile valid() {
        Instant now = Instant.now();
        return DmAgencyProfile.builder()
            .id("agency-1").agencyTenantId("agency-tenant").displayName("Acme Agency")
            .managedTenantIds(List.of("client-1", "client-2"))
            .active(true).createdAt(now).updatedAt(now).build();
    }

    @Test @DisplayName("builder creates valid entity")
    void shouldBuildValid() {
        DmAgencyProfile p = valid();
        assertThat(p.getId()).isEqualTo("agency-1");
        assertThat(p.isActive()).isTrue();
        assertThat(p.getManagedTenantIds()).hasSize(2);
    }

    @Test @DisplayName("builder rejects blank id")
    void shouldRejectBlankId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmAgencyProfile.builder().id("").agencyTenantId("t").displayName("n")
                .managedTenantIds(List.of()).active(true)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build());
    }

    @Test @DisplayName("manages returns true for known tenantId")
    void shouldReturnTrueForManagedTenant() {
        assertThat(valid().manages("client-1")).isTrue();
    }

    @Test @DisplayName("manages returns false for unknown tenantId")
    void shouldReturnFalseForUnknownTenant() {
        assertThat(valid().manages("unknown")).isFalse();
    }

    @Test @DisplayName("managedTenantIds list is immutable")
    void shouldHaveImmutableList() {
        assertThat(valid().getManagedTenantIds()).isUnmodifiable();
    }

    @Test @DisplayName("equals and hashCode are id-based")
    void shouldEqualById() {
        assertThat(valid()).isEqualTo(valid());
        assertThat(valid().hashCode()).isEqualTo(valid().hashCode());
    }

    @Test @DisplayName("equals returns false for null")
    void shouldNotEqualNull() {
        assertThat(valid()).isNotEqualTo(null);
    }

    @Test @DisplayName("equals returns false for different type")
    void shouldNotEqualDifferentType() {
        assertThat(valid()).isNotEqualTo(42);
    }

    @Test @DisplayName("all getters return expected values")
    void shouldExposeAllGetters() {
        DmAgencyProfile p = valid();
        assertThat(p.getAgencyTenantId()).isEqualTo("agency-tenant");
        assertThat(p.getDisplayName()).isEqualTo("Acme Agency");
        assertThat(p.getUpdatedAt()).isNotNull();
        assertThat(p.toString()).contains("agency-1");
    }

    @Test @DisplayName("builder rejects null createdAt")
    void shouldRejectNullCreatedAt() {
        assertThatNullPointerException().isThrownBy(() ->
            DmAgencyProfile.builder().id("x").agencyTenantId("t").displayName("n")
                .managedTenantIds(List.of()).active(true).createdAt(null).build());
    }

    @Test @DisplayName("builder rejects null managedTenantIds")
    void shouldRejectNullManagedTenantIds() {
        assertThatNullPointerException().isThrownBy(() ->
            DmAgencyProfile.builder().id("x").agencyTenantId("t").displayName("n")
                .managedTenantIds(null).active(true).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("null id throws")
    void shouldRejectNullId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmAgencyProfile.builder().id(null).agencyTenantId("t").displayName("n")
                .managedTenantIds(java.util.List.of()).active(true)
                .createdAt(java.time.Instant.now()).build());
    }

    @Test @DisplayName("blank agencyTenantId throws")
    void shouldRejectBlankAgencyTenantId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmAgencyProfile.builder().id("x").agencyTenantId("").displayName("n")
                .managedTenantIds(java.util.List.of()).active(true)
                .createdAt(java.time.Instant.now()).build());
    }
}
