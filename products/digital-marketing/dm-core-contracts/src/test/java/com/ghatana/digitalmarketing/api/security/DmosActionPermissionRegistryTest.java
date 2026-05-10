package com.ghatana.digitalmarketing.api.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DmosActionPermissionRegistry")
class DmosActionPermissionRegistryTest {

    @Test
    @DisplayName("allows admin actions")
    void allowsAdminActions() {
        assertThat(DmosActionPermissionRegistry.isActionAllowed(Set.of("admin"), "manage-agency")).isTrue();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(Set.of("admin"), "approve-budget")).isTrue();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(Set.of("admin"), "view-dashboard")).isTrue();
    }

    @Test
    @DisplayName("enforces minimum role hierarchy")
    void enforcesRoleHierarchy() {
        assertThat(DmosActionPermissionRegistry.isActionAllowed(Set.of("viewer"), "view-dashboard")).isTrue();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(Set.of("viewer"), "approve")).isFalse();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(Set.of("brand-manager"), "approve")).isTrue();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(Set.of("marketing-director"), "approve-budget")).isFalse();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(Set.of("exec-sponsor"), "approve-budget")).isTrue();
    }

    @Test
    @DisplayName("normalizes role and action formats")
    void normalizesRoleAndActionFormats() {
        assertThat(DmosActionPermissionRegistry.isActionAllowed(Set.of("Brand Manager"), "create_campaign")).isTrue();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(Set.of("brand_manager"), "Create Campaign")).isTrue();
    }

    @Test
    @DisplayName("throws for unknown actions")
    void throwsForUnknownActions() {
        assertThatThrownBy(() -> DmosActionPermissionRegistry.isActionAllowed(Set.of("admin"), "unknown-action"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown DMOS action");
    }
}
