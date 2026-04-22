package com.ghatana.platform.http.server.servlet;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpMethod;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Unit tests for VersionedApiRouter versioning, deprecation, and route delegation
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("VersionedApiRouter — URI-prefix versioning and route delegation [GH-90000]")
class VersionedApiRouterTest extends EventloopTestBase {

    // ── Factory/version registration ──────────────────────────────────────────

    @Test
    @DisplayName("create() returns a router with no versions registered [GH-90000]")
    void createReturnsEmptyRouter() { // GH-90000
        VersionedApiRouter router = VersionedApiRouter.create("/api [GH-90000]");
        assertThat(router.getVersions()).isEmpty(); // GH-90000
        assertThat(router.getLatestVersion()).isNull(); // GH-90000
    }

    @Test
    @DisplayName("version() registers a version and updates getLatestVersion [GH-90000]")
    void versionRegistersAndUpdatesLatest() { // GH-90000
        VersionedApiRouter router = VersionedApiRouter.create("/api [GH-90000]")
                .version("v1", s -> s.addRoute(HttpMethod.GET, "/items", // GH-90000
                        req -> HttpResponse.ok200().build())); // GH-90000

        assertThat(router.getVersions()).containsExactly("v1 [GH-90000]");
        assertThat(router.getLatestVersion()).isEqualTo("v1 [GH-90000]");
    }

    @Test
    @DisplayName("version() with multiple versions tracks last as latest [GH-90000]")
    void multipleVersionsLastIsLatest() { // GH-90000
        VersionedApiRouter router = VersionedApiRouter.create("/api [GH-90000]")
                .version("v1", s -> {}) // GH-90000
                .version("v2", s -> {}); // GH-90000

        assertThat(router.getVersions()).containsExactly("v1", "v2"); // GH-90000
        assertThat(router.getLatestVersion()).isEqualTo("v2 [GH-90000]");
    }

    @Test
    @DisplayName("version() throws NullPointerException for null version [GH-90000]")
    void versionThrowsForNullVersion() { // GH-90000
        VersionedApiRouter router = VersionedApiRouter.create("/api [GH-90000]");
        assertThatThrownBy(() -> router.version(null, s -> {})) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    // ── deprecate ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deprecate() marks a registered version as deprecated [GH-90000]")
    void deprecateMarksVersion() { // GH-90000
        VersionedApiRouter router = VersionedApiRouter.create("/api [GH-90000]")
                .version("v1", s -> {}) // GH-90000
                .deprecate("v1", "2026-12-31"); // GH-90000

        assertThat(router.isDeprecated("v1 [GH-90000]")).isTrue();
    }

    @Test
    @DisplayName("deprecate() throws IllegalArgumentException for non-registered version [GH-90000]")
    void deprecateThrowsForUnknownVersion() { // GH-90000
        VersionedApiRouter router = VersionedApiRouter.create("/api [GH-90000]");
        assertThatThrownBy(() -> router.deprecate("v99", "2026-01-01")) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    @Test
    @DisplayName("isDeprecated returns false for non-deprecated version [GH-90000]")
    void isDeprecatedFalseForActiveVersion() { // GH-90000
        VersionedApiRouter router = VersionedApiRouter.create("/api [GH-90000]")
                .version("v2", s -> {}); // GH-90000

        assertThat(router.isDeprecated("v2 [GH-90000]")).isFalse();
    }

    // ── registerRoutes ────────────────────────────────────────────────────────

    @Test
    @DisplayName("registerRoutes copies version routes onto target servlet [GH-90000]")
    void registerRoutesPopulatesTargetServlet() { // GH-90000
        VersionedApiRouter router = VersionedApiRouter.create("/api [GH-90000]")
                .version("v1", s -> s.addRoute(HttpMethod.GET, "/ping", // GH-90000
                        req -> HttpResponse.ok200().withBody("pong".getBytes()).build())) // GH-90000
                .version("v2", s -> s.addRoute(HttpMethod.GET, "/ping", // GH-90000
                        req -> HttpResponse.ok200().withBody("pong-v2".getBytes()).build())); // GH-90000

        RoutingServlet target = new RoutingServlet(); // GH-90000
        router.registerRoutes(target); // GH-90000

        // Routes were registered — target has entries for both versions
        assertThat(target.getRouteCount()).isGreaterThan(0); // GH-90000
    }

    @Test
    @DisplayName("registerRoutes throws NullPointerException for null target [GH-90000]")
    void registerRoutesThrowsForNullTarget() { // GH-90000
        VersionedApiRouter router = VersionedApiRouter.create("/api [GH-90000]")
                .version("v1", s -> {}); // GH-90000

        assertThatThrownBy(() -> router.registerRoutes(null)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    // ── create guard ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("create() throws NullPointerException for null basePath [GH-90000]")
    void createThrowsForNullBasePath() { // GH-90000
        assertThatThrownBy(() -> VersionedApiRouter.create(null)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    // ── getVersions() immutability ──────────────────────────────────────────── // GH-90000

    @Test
    @DisplayName("getVersions returns an unmodifiable list [GH-90000]")
    void getVersionsReturnsUnmodifiableList() { // GH-90000
        VersionedApiRouter router = VersionedApiRouter.create("/api [GH-90000]")
                .version("v1", s -> {}); // GH-90000

        List<String> versions = router.getVersions(); // GH-90000
        assertThatThrownBy(() -> versions.add("v99 [GH-90000]"))
                .isInstanceOf(UnsupportedOperationException.class); // GH-90000
    }
}
