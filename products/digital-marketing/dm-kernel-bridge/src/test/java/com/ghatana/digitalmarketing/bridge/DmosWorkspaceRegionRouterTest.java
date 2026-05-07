package com.ghatana.digitalmarketing.bridge;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DmosWorkspaceRegionRouter} — KERNEL-P2-2.
 */
@DisplayName("DmosWorkspaceRegionRouter")
class DmosWorkspaceRegionRouterTest extends EventloopTestBase {

    private static final URI US_EAST = URI.create("https://api-us-east-1.dmos.example.com");
    private static final URI EU_WEST = URI.create("https://api-eu-west-1.dmos.example.com");
    private static final URI AP_SE   = URI.create("https://api-ap-se-1.dmos.example.com");

    private DmosWorkspaceRegionRouter router;
    private DmWorkspaceId workspace;

    @BeforeEach
    void setUp() {
        router = DmosWorkspaceRegionRouter.builder()
                .primaryRegion("us-east-1")
                .addRegion("us-east-1", US_EAST)
                .addRegion("eu-west-1", EU_WEST)
                .addRegion("ap-southeast-1", AP_SE)
                .build();

        workspace = DmWorkspaceId.of("ws-001");
    }

    @Test
    @DisplayName("routes to preferred region when hint matches registered region")
    void routesToPreferredRegion() {
        URI result = runPromise(() -> router.resolveEndpoint(workspace, "eu-west-1"));
        assertThat(result).isEqualTo(EU_WEST);
    }

    @Test
    @DisplayName("falls back to primary region when hint is null")
    void fallsBackToPrimaryWhenHintNull() {
        URI result = runPromise(() -> router.resolveEndpoint(workspace, null));
        assertThat(result).isEqualTo(US_EAST);
    }

    @Test
    @DisplayName("falls back to primary region when hint is blank")
    void fallsBackToPrimaryWhenHintBlank() {
        URI result = runPromise(() -> router.resolveEndpoint(workspace, "  "));
        assertThat(result).isEqualTo(US_EAST);
    }

    @Test
    @DisplayName("falls back to primary region when hint is unknown")
    void fallsBackToPrimaryWhenHintUnknown() {
        URI result = runPromise(() -> router.resolveEndpoint(workspace, "sa-east-1"));
        assertThat(result).isEqualTo(US_EAST);
    }

    @Test
    @DisplayName("routes to primary region when hint matches it explicitly")
    void routesToPrimaryWhenHintMatchesPrimary() {
        URI result = runPromise(() -> router.resolveEndpoint(workspace, "us-east-1"));
        assertThat(result).isEqualTo(US_EAST);
    }

    @Test
    @DisplayName("routes to Asia-Pacific endpoint correctly")
    void routesToApRegion() {
        URI result = runPromise(() -> router.resolveEndpoint(workspace, "ap-southeast-1"));
        assertThat(result).isEqualTo(AP_SE);
    }

    @Test
    @DisplayName("rejects null workspaceId")
    void rejectsNullWorkspaceId() {
        assertThatNullPointerException()
                .isThrownBy(() -> runPromise(() -> router.resolveEndpoint(null, "eu-west-1")))
                .withMessageContaining("workspaceId");
    }

    @Test
    @DisplayName("builder rejects primaryRegion not registered in addRegion")
    void builderRejectsPrimaryNotRegistered() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DmosWorkspaceRegionRouter.builder()
                        .primaryRegion("us-west-2")
                        .addRegion("us-east-1", US_EAST)
                        .build())
                .withMessageContaining("us-west-2");
    }

    @Test
    @DisplayName("builder rejects blank regionCode")
    void builderRejectsBlankRegionCode() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DmosWorkspaceRegionRouter.builder()
                        .addRegion("  ", US_EAST));
    }

    @Test
    @DisplayName("registeredRegions returns all regions in alphabetical order")
    void registeredRegionsSortedAlphabetically() {
        List<String> regions = router.registeredRegions();
        assertThat(regions).containsExactly("ap-southeast-1", "eu-west-1", "us-east-1");
    }

    @Test
    @DisplayName("isRegionRegistered correctly identifies known and unknown regions")
    void isRegionRegisteredReturnsCorrectly() {
        assertThat(router.isRegionRegistered("eu-west-1")).isTrue();
        assertThat(router.isRegionRegistered("us-west-2")).isFalse();
    }

    @Test
    @DisplayName("getPrimaryRegion returns the configured primary")
    void getPrimaryRegionReturnsConfiguredPrimary() {
        assertThat(router.getPrimaryRegion()).isEqualTo("us-east-1");
    }
}
