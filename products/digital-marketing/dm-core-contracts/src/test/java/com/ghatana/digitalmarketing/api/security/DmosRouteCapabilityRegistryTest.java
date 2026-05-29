package com.ghatana.digitalmarketing.api.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DmosRouteCapabilityRegistry")
class DmosRouteCapabilityRegistryTest {

    @Test
    @DisplayName("returns capability for known routes")
    void returnsCapabilityForKnownRoutes() {
        assertThat(DmosRouteCapabilityRegistry.capabilityForPath("/v1/workspaces/ws-1/campaigns")).isEqualTo("dmos.campaigns");
        assertThat(DmosRouteCapabilityRegistry.capabilityForPath("/v1/workspaces/ws-1/budget")).isEqualTo("dmos.budget");
        assertThat(DmosRouteCapabilityRegistry.capabilityForPath("/v1/workspaces/ws-1/strategy")).isEqualTo("dmos.strategy");
        assertThat(DmosRouteCapabilityRegistry.capabilityForPath("/v1/workspaces/ws-1/attribution")).isEqualTo("dmos.reporting");
    }

    @Test
    @DisplayName("returns null for routes without capability")
    void returnsNullForRoutesWithoutCapability() {
        assertThat(DmosRouteCapabilityRegistry.capabilityForPath("/v1/workspaces/ws-1/dashboard")).isNull();
        assertThat(DmosRouteCapabilityRegistry.capabilityForPath("/v1/workspaces/ws-1/approvals")).isNull();
        assertThat(DmosRouteCapabilityRegistry.capabilityForPath("/v1/workspaces/ws-1/ai-actions")).isNull();
    }

    @Test
    @DisplayName("returns null for unknown routes")
    void returnsNullForUnknownRoutes() {
        assertThat(DmosRouteCapabilityRegistry.capabilityForPath("/v1/unknown/route")).isNull();
        assertThat(DmosRouteCapabilityRegistry.capabilityForPath("/v1/workspaces/ws-1/unknown")).isNull();
    }

    @Test
    @DisplayName("returns null for null path")
    void returnsNullForNullPath() {
        assertThat(DmosRouteCapabilityRegistry.capabilityForPath(null)).isNull();
    }

    @Test
    @DisplayName("returns null for blank path")
    void returnsNullForBlankPath() {
        assertThat(DmosRouteCapabilityRegistry.capabilityForPath("")).isNull();
        assertThat(DmosRouteCapabilityRegistry.capabilityForPath("   ")).isNull();
    }

    @Test
    @DisplayName("handles path parameters correctly")
    void handlesPathParametersCorrectly() {
        assertThat(DmosRouteCapabilityRegistry.capabilityForPath("/v1/workspaces/ws-123/campaigns")).isEqualTo("dmos.campaigns");
        assertThat(DmosRouteCapabilityRegistry.capabilityForPath("/v1/workspaces/abc-456/campaigns/camp-789")).isEqualTo("dmos.campaigns");
        assertThat(DmosRouteCapabilityRegistry.capabilityForPath("/v1/workspaces/ws-1/strategy/str-2")).isEqualTo("dmos.strategy");
    }

    @Test
    @DisplayName("trims query parameters from path")
    void trimsQueryParametersFromPath() {
        assertThat(DmosRouteCapabilityRegistry.capabilityForPath("/v1/workspaces/ws-1/campaigns?filter=active")).isEqualTo("dmos.campaigns");
        assertThat(DmosRouteCapabilityRegistry.capabilityForPath("/v1/workspaces/ws-1/budget?tenant=t1&workspace=ws1")).isEqualTo("dmos.budget");
    }

    @Test
    @DisplayName("rejects paths with empty path parameters")
    void rejectsPathsWithEmptyPathParameters() {
        assertThat(DmosRouteCapabilityRegistry.capabilityForPath("/v1/workspaces//campaigns")).isNull();
        assertThat(DmosRouteCapabilityRegistry.capabilityForPath("/v1/workspaces/ws-1//campaigns")).isNull();
    }

    @Test
    @DisplayName("handles routes without leading slash")
    void handlesRoutesWithoutLeadingSlash() {
        assertThat(DmosRouteCapabilityRegistry.capabilityForPath("v1/workspaces/ws-1/campaigns")).isEqualTo("dmos.campaigns");
    }

    @Test
    @DisplayName("returns capability for connector readiness route")
    void returnsCapabilityForConnectorReadinessRoute() {
        assertThat(DmosRouteCapabilityRegistry.capabilityForPath("/v1/workspaces/ws-1/connectors/google-ads/conn-1/readiness")).isEqualTo("dmos.connectors");
    }

    @Test
    @DisplayName("returns capability for release readiness route")
    void returnsCapabilityForReleaseReadinessRoute() {
        assertThat(DmosRouteCapabilityRegistry.capabilityForPath("/v1/workspaces/ws-1/release-readiness")).isEqualTo("dmos.release_readiness");
    }
}
