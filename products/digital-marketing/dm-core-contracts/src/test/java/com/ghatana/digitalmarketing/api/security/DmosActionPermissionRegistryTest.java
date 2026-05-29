package com.ghatana.digitalmarketing.api.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

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
    @DisplayName("normalizes roles with underscores and spaces")
    void normalizesRolesWithUnderscoresAndSpaces() {
        assertThat(DmosActionPermissionRegistry.isActionAllowed(Set.of("brand_manager"), "create-campaign")).isTrue();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(Set.of("brand manager"), "create-campaign")).isTrue();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(Set.of("BRAND_MANAGER"), "create-campaign")).isTrue();
    }

    @Test
    @DisplayName("normalizes actions with underscores and spaces")
    void normalizesActionsWithUnderscoresAndSpaces() {
        assertThat(DmosActionPermissionRegistry.isActionAllowed(Set.of("brand-manager"), "create_campaign")).isTrue();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(Set.of("brand-manager"), "create campaign")).isTrue();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(Set.of("brand-manager"), "CREATE_CAMPAIGN")).isTrue();
    }

    @Test
    @DisplayName("handles null roles set")
    void handlesNullRolesSet() {
        assertThat(DmosActionPermissionRegistry.isActionAllowed(null, "view-dashboard")).isFalse();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(null, "manage-agency")).isFalse();
    }

    @Test
    @DisplayName("handles empty roles set")
    void handlesEmptyRolesSet() {
        assertThat(DmosActionPermissionRegistry.isActionAllowed(Set.of(), "view-dashboard")).isFalse();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(Set.of(), "manage-agency")).isFalse();
    }

    @Test
    @DisplayName("handles unknown roles in set")
    void handlesUnknownRolesInSet() {
        assertThat(DmosActionPermissionRegistry.isActionAllowed(Set.of("unknown-role"), "view-dashboard")).isFalse();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(Set.of("viewer", "unknown-role"), "view-dashboard")).isTrue();
    }

    @Test
    @DisplayName("uses highest role when multiple roles provided")
    void usesHighestRoleWhenMultipleRolesProvided() {
        assertThat(DmosActionPermissionRegistry.isActionAllowed(Set.of("viewer", "admin"), "manage-agency")).isTrue();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(Set.of("viewer", "brand-manager"), "approve-budget")).isFalse();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(Set.of("marketing-director", "exec-sponsor"), "approve-budget")).isTrue();
    }

    @Test
    @DisplayName("throws for unknown actions")
    void throwsForUnknownActions() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> DmosActionPermissionRegistry.isActionAllowed(Set.of("admin"), "unknown-action"))
            .withMessageContaining("Unknown DMOS action");
    }

    @Test
    @DisplayName("throws for null action")
    void throwsForNullAction() {
        assertThatNullPointerException()
            .isThrownBy(() -> DmosActionPermissionRegistry.isActionAllowed(Set.of("admin"), null))
            .withMessageContaining("action must not be null");
    }

    @Test
    @DisplayName("handles mixed case roles and actions")
    void handlesMixedCaseRolesAndActions() {
        assertThat(DmosActionPermissionRegistry.isActionAllowed(Set.of("ADMIN"), "manage-agency")).isTrue();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(Set.of("Admin"), "MANAGE_AGENCY")).isTrue();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(Set.of("admin"), "MANAGE_AGENCY")).isTrue();
    }

    @Test
    @DisplayName("trims whitespace from roles and actions")
    void trimsWhitespaceFromRolesAndActions() {
        assertThat(DmosActionPermissionRegistry.isActionAllowed(Set.of(" admin "), " view-dashboard ")).isTrue();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(Set.of("  brand-manager  "), "  create-campaign  ")).isTrue();
    }
}
