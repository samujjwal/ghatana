package com.ghatana.yappc.kernel;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G10-003: Product Web Route Generator Tests
 *
 * Tests for generating Product web route/page skeletons from Kernel route contract.
 *
 * @doc.type class
 * @doc.purpose Test Product web route generator functionality
 * @doc.layer integration
 * @doc.pattern Unit Test
 */
@DisplayName("Product Web Route Generator Tests")
class WebRouteGeneratorTest extends EventloopTestBase {

    private WebRouteGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new WebRouteGenerator();
    }

    @AfterEach
    void tearDown() {
        generator = null;
    }

    @Test
    @DisplayName("GIVEN product route WHEN generating skeleton THEN produces valid code")
    void productRoute_whenGeneratingSkeleton_producesValidCode() {
        ProductIntelligenceArtifactImporter.ProductRoute route = new ProductIntelligenceArtifactImporter.ProductRoute();
        route.setProductId("sample-product");
        route.setId("dashboard");
        route.setPath("/dashboard");
        route.setComponent("DashboardScreen");
        route.setStability("stable");
        route.setVisibility("public");
        route.setRoles(List.of("user", "reviewer"));
        route.setRequiredFeatures(List.of());

        WebRouteGenerator.WebRouteSkeleton skeleton = runPromise(() ->
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
        ProductIntelligenceArtifactImporter.ProductRoute route = new ProductIntelligenceArtifactImporter.ProductRoute();
        route.setProductId("sample-product");
        route.setId("records");
        route.setPath("/records");
        route.setComponent("RecordsScreen");
        route.setStability("stable");
        route.setVisibility("public");
        route.setRoles(List.of("user"));
        route.setRequiredFeatures(List.of("regulatory-access", "domain-records"));

        WebRouteGenerator.WebRouteSkeleton skeleton = runPromise(() ->
            generator.generateRouteSkeleton(route)
        );

        assertThat(skeleton.getRouteCode()).contains("regulatory-access");
        assertThat(skeleton.getRouteCode()).contains("domain-records");
    }
}
