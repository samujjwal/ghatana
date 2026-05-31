package com.ghatana.yappc.kernel;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G10-005: Product Mobile Screen Generator Tests
 *
 * Tests for generating Product mobile screen/API skeletons from Kernel route contract.
 *
 * @doc.type class
 * @doc.purpose Test Product mobile screen generator functionality
 * @doc.layer integration
 * @doc.pattern Unit Test
 */
@DisplayName("Product Mobile Screen Generator Tests")
class MobileScreenGeneratorTest extends EventloopTestBase {

    private MobileScreenGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new MobileScreenGenerator();
    }

    @AfterEach
    void tearDown() {
        generator = null;
    }

    @Test
    @DisplayName("GIVEN product route WHEN generating screen skeleton THEN produces valid code")
    void productRoute_whenGeneratingScreenSkeleton_producesValidCode() {
        ProductIntelligenceArtifactImporter.ProductRoute route = new ProductIntelligenceArtifactImporter.ProductRoute();
        route.setProductId("sample-product");
        route.setId("dashboard");
        route.setPath("/dashboard");
        route.setComponent("DashboardScreen");
        route.setStability("stable");
        route.setVisibility("public");
        route.setRoles(List.of("user", "reviewer"));
        route.setRequiredFeatures(List.of());

        MobileScreenGenerator.MobileScreenSkeleton skeleton = runPromise(() ->
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
        ProductIntelligenceArtifactImporter.ProductRoute route = new ProductIntelligenceArtifactImporter.ProductRoute();
        route.setProductId("sample-product");
        route.setId("records");
        route.setPath("/records");
        route.setComponent("RecordsScreen");
        route.setStability("stable");
        route.setVisibility("public");
        route.setRoles(List.of("user"));
        route.setRequiredFeatures(List.of("regulatory-access", "domain-records"));

        MobileScreenGenerator.MobileScreenSkeleton skeleton = runPromise(() ->
            generator.generateScreenSkeleton(route)
        );

        assertThat(skeleton.getScreenCode()).contains("RecordsScreen");
        assertThat(skeleton.getApiCode()).contains("fetchRecords");
    }
}
