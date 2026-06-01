package com.ghatana.phr.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contract tests for {@link PhrRouteContractMountTable}.
 *
 * @doc.type class
 * @doc.purpose Verifies backend mount generation from the canonical PHR route contract
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PHR route contract mount table")
class PhrRouteContractMountTableTest {

    @Test
    @DisplayName("loads stable backend mounts from the canonical route contract")
    void loadsStableBackendMountsFromCanonicalRouteContract() {
        List<PhrRouteContractMountTable.MountSpec> mounts = PhrRouteContractMountTable.loadStableMounts();
        List<String> paths = mounts.stream().map(PhrRouteContractMountTable.MountSpec::path).toList();

        assertThat(paths)
            .contains(
                "/api/v1/dashboard",
                "/api/v1/records/documents/*",
                "/api/v1/records/imaging/*",
                "/api/v1/records/*",
                "/api/v1/clinical/*",
                "/api/v1/admin/*",
                "/api/v1/hie/*",
                "/api/v1/route-entitlements"
            )
            .doesNotContain(
                "/api/v1/provider/*",
                "/api/v1/caregiver/*",
                "/api/v1/fchv/*"
            );
    }

    @Test
    @DisplayName("fails closed when a stable contract endpoint has no backend mount target")
    void failsClosedForUnmappedStableEndpoint() throws Exception {
        Path fixture = Files.createTempFile("phr-route-contract-", ".json");
        Files.writeString(fixture, """
            {
              "routes": [
                {
                  "path": "/unknown",
                  "label": "Unknown",
                  "minimumRole": "patient",
                  "stability": "stable",
                  "apiEndpoint": "/api/v1/unknown",
                  "surface": ["backend"]
                }
              ]
            }
            """);

        assertThatThrownBy(() -> PhrRouteContractMountTable.loadStableMounts(fixture))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("/api/v1/unknown");
    }

    @Test
    @DisplayName("skips stable web-only routes unless they use the entitlement endpoint")
    void skipsStableWebOnlyRoutesExceptEntitlements() throws Exception {
        Path fixture = Files.createTempFile("phr-route-contract-web-only-", ".json");
        Files.writeString(fixture, """
            {
              "routes": [
                {
                  "path": "/client-only",
                  "label": "Client Only",
                  "minimumRole": "patient",
                  "stability": "stable",
                  "apiEndpoint": "/api/v1/client-only",
                  "surface": ["web"]
                },
                {
                  "path": "/forbidden",
                  "label": "Forbidden",
                  "minimumRole": "patient",
                  "stability": "stable",
                  "apiEndpoint": "/api/v1/route-entitlements",
                  "surface": ["web"]
                }
              ]
            }
            """);

        List<String> paths = PhrRouteContractMountTable.loadStableMounts(fixture).stream()
            .map(PhrRouteContractMountTable.MountSpec::path)
            .toList();

        assertThat(paths).containsExactly("/api/v1/route-entitlements");
    }
}
