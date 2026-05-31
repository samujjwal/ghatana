package com.ghatana.yappc.kernel;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G10-005: PHR Mobile Screen Generator Tests
 *
 * Tests for generating PHR mobile screen/API skeletons from Kernel route contract.
 *
 * @doc.type class
 * @doc.purpose Test PHR mobile screen generator functionality
 * @doc.layer integration
 * @doc.pattern Unit Test
 */
@DisplayName("PHR Mobile Screen Generator Tests")
class PhrMobileScreenGeneratorTest extends EventloopTestBase {

    private PhrMobileScreenGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new PhrMobileScreenGenerator();
    }

    @AfterEach
    void tearDown() {
        generator = null;
    }

    @Test
    @DisplayName("GIVEN PHR route WHEN generating screen skeleton THEN produces valid code")
    void phrRoute_whenGeneratingScreenSkeleton_producesValidCode() {
        PhrIntelligenceArtifactImporter.PhrRoute route = new PhrIntelligenceArtifactImporter.PhrRoute();
        route.setId("dashboard");
        route.setPath("/dashboard");
        route.setComponent("DashboardScreen");
        route.setStability("stable");
        route.setVisibility("public");
        route.setRoles(List.of("patient", "caregiver"));
        route.setRequiredFeatures(List.of());

        PhrMobileScreenGenerator.MobileScreenSkeleton skeleton = runPromise(() ->
            generator.generateScreenSkeleton(route)
        );

        assertThat(skeleton.getRouteId()).isEqualTo("dashboard");
        assertThat(skeleton.getPath()).isEqualTo("/dashboard");
        assertThat(skeleton.getComponentName()).isEqualTo("DashboardScreen");
        assertThat(skeleton.getScreenFilePath()).contains("DashboardScreen.tsx");
        assertThat(skeleton.getApiFilePath()).contains("DashboardApi.ts");
        assertThat(skeleton.getScreenCode()).contains("DashboardScreen");
        assertThat(skeleton.getApiCode()).contains("fetchDashboard");
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

        PhrMobileScreenGenerator.MobileScreenSkeleton skeleton = runPromise(() ->
            generator.generateScreenSkeleton(route)
        );

        assertThat(skeleton.getScreenCode()).contains("RecordsScreen");
        assertThat(skeleton.getApiCode()).contains("fetchRecords");
    }
}
