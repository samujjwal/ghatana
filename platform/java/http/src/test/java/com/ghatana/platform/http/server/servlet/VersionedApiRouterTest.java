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
@DisplayName("VersionedApiRouter — URI-prefix versioning and route delegation")
class VersionedApiRouterTest extends EventloopTestBase {

    // ── Factory/version registration ──────────────────────────────────────────

    @Test
    @DisplayName("create() returns a router with no versions registered")
    void createReturnsEmptyRouter() { 
        VersionedApiRouter router = VersionedApiRouter.create("/api");
        assertThat(router.getVersions()).isEmpty(); 
        assertThat(router.getLatestVersion()).isNull(); 
    }

    @Test
    @DisplayName("version() registers a version and updates getLatestVersion")
    void versionRegistersAndUpdatesLatest() { 
        VersionedApiRouter router = VersionedApiRouter.create("/api")
                .version("v1", s -> s.addRoute(HttpMethod.GET, "/items", 
                        req -> HttpResponse.ok200().build())); 

        assertThat(router.getVersions()).containsExactly("v1");
        assertThat(router.getLatestVersion()).isEqualTo("v1");
    }

    @Test
    @DisplayName("version() with multiple versions tracks last as latest")
    void multipleVersionsLastIsLatest() { 
        VersionedApiRouter router = VersionedApiRouter.create("/api")
                .version("v1", s -> {}) 
                .version("v2", s -> {}); 

        assertThat(router.getVersions()).containsExactly("v1", "v2"); 
        assertThat(router.getLatestVersion()).isEqualTo("v2");
    }

    @Test
    @DisplayName("version() throws NullPointerException for null version")
    void versionThrowsForNullVersion() { 
        VersionedApiRouter router = VersionedApiRouter.create("/api");
        assertThatThrownBy(() -> router.version(null, s -> {})) 
                .isInstanceOf(NullPointerException.class); 
    }

    // ── deprecate ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deprecate() marks a registered version as deprecated")
    void deprecateMarksVersion() { 
        VersionedApiRouter router = VersionedApiRouter.create("/api")
                .version("v1", s -> {}) 
                .deprecate("v1", "2026-12-31"); 

        assertThat(router.isDeprecated("v1")).isTrue();
    }

    @Test
    @DisplayName("deprecate() throws IllegalArgumentException for non-registered version")
    void deprecateThrowsForUnknownVersion() { 
        VersionedApiRouter router = VersionedApiRouter.create("/api");
        assertThatThrownBy(() -> router.deprecate("v99", "2026-01-01")) 
                .isInstanceOf(IllegalArgumentException.class); 
    }

    @Test
    @DisplayName("isDeprecated returns false for non-deprecated version")
    void isDeprecatedFalseForActiveVersion() { 
        VersionedApiRouter router = VersionedApiRouter.create("/api")
                .version("v2", s -> {}); 

        assertThat(router.isDeprecated("v2")).isFalse();
    }

    // ── registerRoutes ────────────────────────────────────────────────────────

    @Test
    @DisplayName("registerRoutes copies version routes onto target servlet")
    void registerRoutesPopulatesTargetServlet() { 
        VersionedApiRouter router = VersionedApiRouter.create("/api")
                .version("v1", s -> s.addRoute(HttpMethod.GET, "/ping", 
                        req -> HttpResponse.ok200().withBody("pong".getBytes()).build())) 
                .version("v2", s -> s.addRoute(HttpMethod.GET, "/ping", 
                        req -> HttpResponse.ok200().withBody("pong-v2".getBytes()).build())); 

        RoutingServlet target = new RoutingServlet(); 
        router.registerRoutes(target); 

        // Routes were registered — target has entries for both versions
        assertThat(target.getRouteCount()).isGreaterThan(0); 
    }

    @Test
    @DisplayName("registerRoutes throws NullPointerException for null target")
    void registerRoutesThrowsForNullTarget() { 
        VersionedApiRouter router = VersionedApiRouter.create("/api")
                .version("v1", s -> {}); 

        assertThatThrownBy(() -> router.registerRoutes(null)) 
                .isInstanceOf(NullPointerException.class); 
    }

    // ── create guard ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("create() throws NullPointerException for null basePath")
    void createThrowsForNullBasePath() { 
        assertThatThrownBy(() -> VersionedApiRouter.create(null)) 
                .isInstanceOf(NullPointerException.class); 
    }

    // ── getVersions() immutability ──────────────────────────────────────────── 

    @Test
    @DisplayName("getVersions returns an unmodifiable list")
    void getVersionsReturnsUnmodifiableList() { 
        VersionedApiRouter router = VersionedApiRouter.create("/api")
                .version("v1", s -> {}); 

        List<String> versions = router.getVersions(); 
        assertThatThrownBy(() -> versions.add("v99"))
                .isInstanceOf(UnsupportedOperationException.class); 
    }
}
