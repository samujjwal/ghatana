package com.ghatana.digitalmarketing.api.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DmosActionPermissionRegistry")
class DmosActionPermissionRegistryTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    @DisplayName("allows admin for every route-contract action")
    void shouldAllowAdminForEveryRouteContractAction() throws Exception {
        for (String action : routeContractActions()) {
            assertThat(DmosActionPermissionRegistry.isActionAllowed(Set.of("admin"), action))
                .as("action %s should be allowed for admin", action)
                .isTrue();
        }
    }

    @Test
    @DisplayName("enforces stricter actions for lower roles")
    void shouldEnforceStrictActionBoundariesForLowerRoles() {
        assertThat(DmosActionPermissionRegistry.isActionAllowed(Set.of("viewer"), "approve")).isFalse();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(Set.of("viewer"), "reject")).isFalse();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(Set.of("brand-manager"), "approve-budget")).isFalse();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(Set.of("marketing-director"), "approve-budget")).isFalse();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(Set.of("exec-sponsor"), "approve-budget")).isTrue();
    }

    @Test
    @DisplayName("supports normalized role formats")
    void shouldSupportNormalizedRoleFormats() {
        assertThat(DmosActionPermissionRegistry.isActionAllowed(Set.of("Marketing Director"), "approve-strategy")).isTrue();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(Set.of("EXEC_SPONSOR"), "approve-budget")).isTrue();
    }

    @Test
    @DisplayName("throws for unknown actions")
    void shouldThrowForUnknownAction() {
        assertThatThrownBy(() -> DmosActionPermissionRegistry.isActionAllowed(Set.of("admin"), "unknown-action"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown DMOS action");
    }

    private Set<String> routeContractActions() throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("contracts/dmos-route-capabilities.json")) {
            if (stream == null) {
                throw new IllegalStateException("Missing route-capability contract resource");
            }

            JsonNode root = OBJECT_MAPPER.readTree(stream);
            Set<String> actions = new LinkedHashSet<>();
            for (JsonNode route : root.withArray("routes")) {
                for (JsonNode action : route.withArray("actions")) {
                    actions.add(action.asText());
                }
            }
            return actions;
        }
    }
}
