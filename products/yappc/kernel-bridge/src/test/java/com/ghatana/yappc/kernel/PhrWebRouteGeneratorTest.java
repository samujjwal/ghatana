package com.ghatana.yappc.kernel;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G10-003: PHR Web Route Generator Tests
 *
 * Tests for generating PHR web route/page skeletons from Kernel route contract.
 *
 * @doc.type class
 * @doc.purpose Test PHR web route generator functionality
 * @doc.layer integration
 * @doc.pattern Unit Test
 */
@DisplayName("PHR Web Route Generator Tests")
class PhrWebRouteGeneratorTest extends EventloopTestBase {

    private PhrWebRouteGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new PhrWebRouteGenerator();
    }

    @AfterEach
    void tearDown() {
        generator = null;
    }

    @Test
    @DisplayName("GIVEN PHR route WHEN generating skeleton THEN produces valid code")
    void phrRoute_whenGeneratingSkeleton_producesValidCode() {
        PhrIntelligenceArtifactImporter.PhrRoute route = new PhrIntelligenceArtifactImporter.PhrRoute();
        route.setId("dashboard");
        route.setPath("/dashboard");
        route.setComponent("DashboardScreen");
        route.setStability("stable");
        route.setVisibility("public");
        route.setRoles(List.of("patient", "caregiver"));
        route.setRequiredFeatures(List.of());

        PhrWebRouteGenerator.WebRouteSkeleton skeleton = runPromise(() ->
            generator.generateRouteSkeleton(route)
        );

        assertThat(skeleton.getRouteId()).isEqualTo("dashboard");
        assertThat(skeleton.getPath()).isEqualTo("/dashboard");
        assertThat(skeleton.getComponentName()).isEqualTo("DashboardScreen");
        assertThat(skeleton.getRouteFilePath()).contains("dashboard.tsx");
        assertThat(skeleton.getPageFilePath()).contains("DashboardScreen.tsx");
        assertThat(skeleton.getRouteCode()).contains("DashboardScreen");
        assertThat(skeleton.getPageCode()).contains("DashboardScreen");
    }

    @Test
    @DisplayName("GIVEN route with required features WHEN generating THEN includes feature checks")
    void routeWithFeatures_whenGenerating_includesFeatureChecks() {
        PhrIntelligenceArtifactImporter.PhrRoute route = new PhrIntelligenceArtifactImporter.PhrRoute();
        route.setId("records");
        route.setPath("/records");
        route.setComponent("RecordsScreen");
        route.setStability("stable");
        route.setVisibility("public");
        route.setRoles(List.of("patient"));
        route.setRequiredFeatures(List.of("phi-access", "fhir-data"));

        PhrWebRouteGenerator.WebRouteSkeleton skeleton = runPromise(() ->
            generator.generateRouteSkeleton(route)
        );

        assertThat(skeleton.getRouteCode()).contains("phi-access");
        assertThat(skeleton.getRouteCode()).contains("fhir-data");
    }
}
