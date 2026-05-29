package com.ghatana.yappc.kernel;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G10-004: PHR Backend Route Adapter Generator Tests
 *
 * Tests for generating PHR backend route adapter skeletons from Kernel route contract.
 *
 * @doc.type class
 * @doc.purpose Test PHR backend route adapter generator functionality
 * @doc.layer integration
 * @doc.pattern Unit Test
 */
@DisplayName("PHR Backend Route Adapter Generator Tests")
class PhrBackendRouteAdapterGeneratorTest extends EventloopTestBase {

    private PhrBackendRouteAdapterGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new PhrBackendRouteAdapterGenerator();
    }

    @AfterEach
    void tearDown() {
        generator = null;
    }

    @Test
    @DisplayName("GIVEN PHR route WHEN generating adapter skeleton THEN produces valid code")
    void phrRoute_whenGeneratingAdapterSkeleton_producesValidCode() {
        PhrIntelligenceArtifactImporter.PhrRoute route = new PhrIntelligenceArtifactImporter.PhrRoute();
        route.setId("dashboard");
        route.setPath("/dashboard");
        route.setStability("stable");
        route.setVisibility("public");
        route.setRoles(List.of("patient", "caregiver"));
        route.setRequiredFeatures(List.of());

        PhrBackendRouteAdapterGenerator.BackendRouteAdapterSkeleton skeleton = runPromise(() ->
            generator.generateAdapterSkeleton(route)
        );

        assertThat(skeleton.getRouteId()).isEqualTo("dashboard");
        assertThat(skeleton.getPath()).isEqualTo("/dashboard");
        assertThat(skeleton.getAdapterFilePath()).contains("DashboardRouteAdapter.java");
        assertThat(skeleton.getHandlerFilePath()).contains("DashboardHandler.java");
        assertThat(skeleton.getAdapterCode()).contains("DashboardRouteAdapter");
        assertThat(skeleton.getHandlerCode()).contains("DashboardHandler");
    }

    @Test
    @DisplayName("GIVEN route with required features WHEN generating THEN includes feature checks")
    void routeWithFeatures_whenGenerating_includesFeatureChecks() {
        PhrIntelligenceArtifactImporter.PhrRoute route = new PhrIntelligenceArtifactImporter.PhrRoute();
        route.setId("records");
        route.setPath("/records");
        route.setStability("stable");
        route.setVisibility("public");
        route.setRoles(List.of("patient"));
        route.setRequiredFeatures(List.of("phi-access", "fhir-data"));

        PhrBackendRouteAdapterGenerator.BackendRouteAdapterSkeleton skeleton = runPromise(() ->
            generator.generateAdapterSkeleton(route)
        );

        assertThat(skeleton.getAdapterCode()).contains("phi-access");
        assertThat(skeleton.getAdapterCode()).contains("fhir-data");
    }
}
