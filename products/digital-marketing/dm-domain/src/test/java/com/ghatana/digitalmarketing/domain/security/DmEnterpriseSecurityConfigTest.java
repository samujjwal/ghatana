package com.ghatana.digitalmarketing.domain.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("DmEnterpriseSecurityConfig domain entity")
class DmEnterpriseSecurityConfigTest {

    private DmEnterpriseSecurityConfig valid() {
        Instant now = Instant.now();
        return DmEnterpriseSecurityConfig.builder()
            .id("sec-1").tenantId("t1")
            .mfaRequired(true).ipAllowlistEnabled(false).allowedIpCidrs(List.of())
            .auditLogEnabled(true).sessionTimeoutMinutes(60)
            .createdAt(now).updatedAt(now).build();
    }

    @Test @DisplayName("builder creates valid entity with defaults")
    void shouldBuildValid() {
        DmEnterpriseSecurityConfig c = valid();
        assertThat(c.getId()).isEqualTo("sec-1");
        assertThat(c.isMfaRequired()).isTrue();
        assertThat(c.getSessionTimeoutMinutes()).isEqualTo(60);
    }

    @Test @DisplayName("builder rejects blank id")
    void shouldRejectBlankId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmEnterpriseSecurityConfig.builder().id("").tenantId("t")
                .mfaRequired(false).ipAllowlistEnabled(false).allowedIpCidrs(List.of())
                .auditLogEnabled(false).sessionTimeoutMinutes(60)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build());
    }

    @Test @DisplayName("builder rejects blank tenantId")
    void shouldRejectBlankTenantId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmEnterpriseSecurityConfig.builder().id("x").tenantId("")
                .mfaRequired(false).ipAllowlistEnabled(false).allowedIpCidrs(List.of())
                .auditLogEnabled(false).sessionTimeoutMinutes(60)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build());
    }

    @Test @DisplayName("allowedIpCidrs list is immutable")
    void shouldHaveImmutableCidrs() {
        assertThat(valid().getAllowedIpCidrs()).isUnmodifiable();
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
        assertThat(valid()).isNotEqualTo("x");
    }

    @Test @DisplayName("all getters return expected values")
    void shouldExposeAllGetters() {
        DmEnterpriseSecurityConfig c = valid();
        assertThat(c.getTenantId()).isEqualTo("t1");
        assertThat(c.isIpAllowlistEnabled()).isFalse();
        assertThat(c.isAuditLogEnabled()).isTrue();
        assertThat(c.getAllowedIpCidrs()).isEmpty();
        assertThat(c.getUpdatedAt()).isNotNull();
        assertThat(c.toString()).contains("sec-1");
    }

    @Test @DisplayName("builder rejects null createdAt")
    void shouldRejectNullCreatedAt() {
        assertThatNullPointerException().isThrownBy(() ->
            DmEnterpriseSecurityConfig.builder().id("x").tenantId("t")
                .allowedIpCidrs(List.of()).createdAt(null).build());
    }

    @Test @DisplayName("builder rejects null allowedIpCidrs")
    void shouldRejectNullAllowedIpCidrs() {
        assertThatNullPointerException().isThrownBy(() ->
            DmEnterpriseSecurityConfig.builder().id("x").tenantId("t")
                .allowedIpCidrs(null).createdAt(Instant.now()).build());
    }
}
