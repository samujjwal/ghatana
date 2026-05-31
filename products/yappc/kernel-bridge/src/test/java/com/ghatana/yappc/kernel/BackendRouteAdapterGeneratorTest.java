package com.ghatana.yappc.kernel;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G10-004: Product Backend Route Adapter Generator Tests
 *
 * Tests for generating product backend route adapter skeletons from Kernel route contract.
 *
 * @doc.type class
 * @doc.purpose Test product backend route adapter generator functionality
 * @doc.layer integration
 * @doc.pattern Unit Test
 */
@DisplayName("Product Backend Route Adapter Generator Tests")
class BackendRouteAdapterGeneratorTest extends EventloopTestBase {

    private BackendRouteAdapterGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new BackendRouteAdapterGenerator();
    }

    @AfterEach
    void tearDown() {
        generator = null;
    }

    @Test
    @DisplayName("GIVEN product route WHEN generating adapter skeleton THEN produces valid code")
    void productRoute_whenGeneratingAdapterSkeleton_producesValidCode() {
        ProductIntelligenceArtifactImporter.ProductRoute route = new ProductIntelligenceArtifactImporter.ProductRoute();
        route.setProductId("sample-product");
        route.setId("dashboard");
        route.setPath("/dashboard");
        route.setStability("stable");
        route.setVisibility("public");
        route.setRoles(List.of("user", "reviewer"));
        route.setRequiredFeatures(List.of());

        BackendRouteAdapterGenerator.BackendRouteAdapterSkeleton skeleton = runPromise(() ->
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
        ProductIntelligenceArtifactImporter.ProductRoute route = new ProductIntelligenceArtifactImporter.ProductRoute();
        route.setProductId("sample-product");
        route.setId("records");
        route.setPath("/records");
        route.setStability("stable");
        route.setVisibility("public");
        route.setRoles(List.of("user"));
        route.setRequiredFeatures(List.of("regulatory-access", "domain-records"));

        BackendRouteAdapterGenerator.BackendRouteAdapterSkeleton skeleton = runPromise(() ->
            generator.generateAdapterSkeleton(route)
        );

        assertThat(skeleton.getAdapterCode()).contains("regulatory-access");
        assertThat(skeleton.getAdapterCode()).contains("domain-records");
    }
}
