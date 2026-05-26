package com.ghatana.yappc.packs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Verifies scaffold pack registry compatibility metadata and target surface completeness
 * @doc.layer test
 * @doc.pattern Unit Test
 */
@DisplayName("PackRegistry")
class PackRegistryTest {

    @Test
    @DisplayName("lists supported packs with schema version and compatibility metadata")
    void listsSupportedPacksWithCompatibilityMetadata() {
        var packs = PackRegistry.listSupportedPacks();

        assertThat(packs).isNotEmpty();
        assertThat(packs)
                .anySatisfy(pack -> {
                    assertThat(pack.id()).isEqualTo("nx-pnpm-monorepo");
                    assertThat(pack.schemaVersion()).isEqualTo("pack-v1");
                    assertThat(pack.version()).isEqualTo("1.0.0");
                    assertThat(pack.supportedSurfaces()).contains("web");
                    assertThat(pack.compatibleLanguages()).contains("typescript");
                    assertThat(pack.compatibleFrameworks()).contains("react", "nextjs", "vite");
                    assertThat(pack.compatibleBuildSystems()).contains("nx", "pnpm");
                });
    }

    @Test
    @DisplayName("reports complete registry for supported target surfaces")
    void reportsCompleteRegistryForSupportedTargetSurfaces() {
        var completeness = PackRegistry.validateCompleteness(Set.of("web"));

        assertThat(completeness.complete()).isTrue();
        assertThat(completeness.missingSurfaces()).isEmpty();
        assertThat(completeness.invalidPacks()).isEmpty();
    }

    @Test
    @DisplayName("reports missing unsupported target surfaces")
    void reportsMissingUnsupportedTargetSurfaces() {
        var completeness = PackRegistry.validateCompleteness(Set.of("web", "backend-api"));

        assertThat(completeness.complete()).isFalse();
        assertThat(completeness.missingSurfaces()).containsExactly("backend-api");
        assertThat(PackRegistry.listSupportedPacksForSurface("backend-api")).isEmpty();
    }
}
