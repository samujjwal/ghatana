package com.ghatana.aep.versioning;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AepApiVersionRegistry} — AEP-010.
 *
 * @doc.type class
 * @doc.purpose Unit tests for AEP API version registry and negotiation
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AepApiVersionRegistry [GH-90000]")
class AepApiVersionRegistryTest {

    private static final LocalDate FUTURE_DATE = LocalDate.now().plusMonths(3); // GH-90000

    private AepApiVersionRegistry buildStandard() { // GH-90000
        return AepApiVersionRegistry.builder() // GH-90000
                .register(AepApiVersionRegistry.ApiVersion.current("v3 [GH-90000]"))
                .register(AepApiVersionRegistry.ApiVersion.deprecated("v2", FUTURE_DATE)) // GH-90000
                .register(AepApiVersionRegistry.ApiVersion.sunset("v1 [GH-90000]"))
                .build(); // GH-90000
    }

    // ─── currentVersion ───────────────────────────────────────────────────────

    @Test
    @DisplayName("currentVersion: returns the registered CURRENT version [GH-90000]")
    void currentVersion_returnsCurrentVersion() { // GH-90000
        AepApiVersionRegistry registry = buildStandard(); // GH-90000
        assertThat(registry.currentVersion()).isEqualTo("v3 [GH-90000]");
    }

    @Test
    @DisplayName("build: throws if no CURRENT version is registered [GH-90000]")
    void build_noCurrent_throwsISE() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
                AepApiVersionRegistry.builder() // GH-90000
                        .register(AepApiVersionRegistry.ApiVersion.deprecated("v1", FUTURE_DATE)) // GH-90000
                        .build()) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("CURRENT [GH-90000]");
    }

    // ─── negotiate ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("negotiate() [GH-90000]")
    class Negotiate {

        @Test
        @DisplayName("current version is returned as-is [GH-90000]")
        void negotiate_currentVersion_returnedAsIs() { // GH-90000
            AepApiVersionRegistry registry = buildStandard(); // GH-90000
            assertThat(registry.negotiate("v3 [GH-90000]")).contains("v3 [GH-90000]");
        }

        @Test
        @DisplayName("deprecated version is returned (with a warning) [GH-90000]")
        void negotiate_deprecatedVersion_returned() { // GH-90000
            AepApiVersionRegistry registry = buildStandard(); // GH-90000
            assertThat(registry.negotiate("v2 [GH-90000]")).contains("v2 [GH-90000]");
        }

        @Test
        @DisplayName("sunset version → redirected to current [GH-90000]")
        void negotiate_sunsetVersion_redirectedToCurrent() { // GH-90000
            AepApiVersionRegistry registry = buildStandard(); // GH-90000
            assertThat(registry.negotiate("v1 [GH-90000]")).contains("v3 [GH-90000]");
        }

        @Test
        @DisplayName("unknown version → redirected to current [GH-90000]")
        void negotiate_unknownVersion_redirectedToCurrent() { // GH-90000
            AepApiVersionRegistry registry = buildStandard(); // GH-90000
            assertThat(registry.negotiate("v99 [GH-90000]")).contains("v3 [GH-90000]");
        }

        @Test
        @DisplayName("null requestedVersion throws NullPointerException [GH-90000]")
        void negotiate_nullVersion_throwsNPE() { // GH-90000
            AepApiVersionRegistry registry = buildStandard(); // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> registry.negotiate(null)); // GH-90000
        }
    }

    // ─── isDeprecated ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("isDeprecated: returns true for deprecated version [GH-90000]")
    void isDeprecated_trueForDeprecated() { // GH-90000
        AepApiVersionRegistry registry = buildStandard(); // GH-90000
        assertThat(registry.isDeprecated("v2 [GH-90000]")).isTrue();
    }

    @Test
    @DisplayName("isDeprecated: returns false for current version [GH-90000]")
    void isDeprecated_falseForCurrent() { // GH-90000
        AepApiVersionRegistry registry = buildStandard(); // GH-90000
        assertThat(registry.isDeprecated("v3 [GH-90000]")).isFalse();
    }

    @Test
    @DisplayName("isDeprecated: returns false for unknown version [GH-90000]")
    void isDeprecated_falseForUnknown() { // GH-90000
        AepApiVersionRegistry registry = buildStandard(); // GH-90000
        assertThat(registry.isDeprecated("v99 [GH-90000]")).isFalse();
    }

    // ─── isSunset ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("isSunset: returns true for sunset version [GH-90000]")
    void isSunset_trueForSunset() { // GH-90000
        AepApiVersionRegistry registry = buildStandard(); // GH-90000
        assertThat(registry.isSunset("v1 [GH-90000]")).isTrue();
    }

    @Test
    @DisplayName("isSunset: returns false for current version [GH-90000]")
    void isSunset_falseForCurrent() { // GH-90000
        AepApiVersionRegistry registry = buildStandard(); // GH-90000
        assertThat(registry.isSunset("v3 [GH-90000]")).isFalse();
    }

    // ─── sunsetDate ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("sunsetDate: returns date for deprecated version [GH-90000]")
    void sunsetDate_presentForDeprecated() { // GH-90000
        AepApiVersionRegistry registry = buildStandard(); // GH-90000
        assertThat(registry.sunsetDate("v2 [GH-90000]")).isPresent().contains(FUTURE_DATE);
    }

    @Test
    @DisplayName("sunsetDate: empty for current version [GH-90000]")
    void sunsetDate_emptyForCurrent() { // GH-90000
        AepApiVersionRegistry registry = buildStandard(); // GH-90000
        assertThat(registry.sunsetDate("v3 [GH-90000]")).isEmpty();
    }

    @Test
    @DisplayName("sunsetDate: empty for unknown version [GH-90000]")
    void sunsetDate_emptyForUnknown() { // GH-90000
        AepApiVersionRegistry registry = buildStandard(); // GH-90000
        assertThat(registry.sunsetDate("vUnknown [GH-90000]")).isEmpty();
    }

    // ─── registeredVersions ───────────────────────────────────────────────────

    @Test
    @DisplayName("registeredVersions: contains all registered IDs [GH-90000]")
    void registeredVersions_containsAll() { // GH-90000
        AepApiVersionRegistry registry = buildStandard(); // GH-90000
        assertThat(registry.registeredVersions()).containsExactlyInAnyOrder("v1", "v2", "v3"); // GH-90000
    }

    // ─── ApiVersion factories ─────────────────────────────────────────────────

    @Test
    @DisplayName("ApiVersion.current: has CURRENT status and no sunset date [GH-90000]")
    void apiVersion_current_properties() { // GH-90000
        var v = AepApiVersionRegistry.ApiVersion.current("v5 [GH-90000]");
        assertThat(v.status()).isEqualTo(AepApiVersionRegistry.ApiVersion.Status.CURRENT); // GH-90000
        assertThat(v.sunsetDate()).isEmpty(); // GH-90000
        assertThat(v.versionId()).isEqualTo("v5 [GH-90000]");
    }

    @Test
    @DisplayName("ApiVersion.deprecated: has DEPRECATED status and a sunset date [GH-90000]")
    void apiVersion_deprecated_properties() { // GH-90000
        var v = AepApiVersionRegistry.ApiVersion.deprecated("v4", FUTURE_DATE); // GH-90000
        assertThat(v.status()).isEqualTo(AepApiVersionRegistry.ApiVersion.Status.DEPRECATED); // GH-90000
        assertThat(v.sunsetDate()).contains(FUTURE_DATE); // GH-90000
    }

    @Test
    @DisplayName("ApiVersion.sunset: has SUNSET status and no sunset date [GH-90000]")
    void apiVersion_sunset_properties() { // GH-90000
        var v = AepApiVersionRegistry.ApiVersion.sunset("v0 [GH-90000]");
        assertThat(v.status()).isEqualTo(AepApiVersionRegistry.ApiVersion.Status.SUNSET); // GH-90000
        assertThat(v.sunsetDate()).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("ApiVersion.current: null versionId throws NPE [GH-90000]")
    void apiVersion_current_nullId_throwsNPE() { // GH-90000
        assertThatNullPointerException() // GH-90000
                .isThrownBy(() -> AepApiVersionRegistry.ApiVersion.current(null)); // GH-90000
    }
}
