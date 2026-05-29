package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.launcher.http.RouteSecurityRegistry;
import com.ghatana.datacloud.launcher.http.RouteSurfaceMapping;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Regression tests for runtime truth schema generation from canonical registries
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("SurfaceSchemaGenerator")
class SurfaceSchemaGeneratorTest {

    @Test
    @DisplayName("derives route truth snapshot from the canonical route registries")
    void derivesRouteTruthSnapshotFromCanonicalRouteRegistries() {
        SurfaceSchemaGenerator.SurfaceSchema schema = SurfaceSchemaGenerator.generateSchema();

        assertThat(schema.metadata.generators).contains("RouteSecurityRegistry", "RouteSurfaceMapping");
        assertThat(schema.routeTruth).isNotNull();
        assertThat(schema.routeTruth.generatedAt).isNotBlank();
        assertThat(schema.routeTruth.routeCount).isEqualTo(RouteSecurityRegistry.size());
        assertThat(schema.routeTruth.mappedRouteCount).isGreaterThan(0);
        assertThat(schema.routeTruth.unmappedRouteCount).isGreaterThan(0);
        assertThat(schema.routeTruth.routesByLegacyStatus).containsKeys("active", "compatibility-only");
        assertThat(schema.routeTruth.routesByRuntimeTruthSurface).containsKeys("action_plane", "data_cloud", "event_store");
        assertThat(schema.routeTruth.routesBySurfaceId).containsKey("runtime.truth.read");
        assertThat(schema.routeTruth.surfaceIds).containsExactlyInAnyOrderElementsOf(sortedSurfaceIds());
    }

    @Test
    @DisplayName("derives Action Plane capability families from canonical routes")
    void derivesActionPlaneCapabilityFamiliesFromCanonicalRoutes() {
        SurfaceSchemaGenerator.SurfaceSchema schema = SurfaceSchemaGenerator.generateSchema();

        assertThat(schema.aepCapabilities)
            .extracting(capability -> capability.id)
            .contains(
                "aep.eventlog.durable",
                "aep.pipelines",
                "aep.runs",
                "aep.reviews",
                "aep.learning",
                "aep.agents",
                "aep.reports"
            );

        assertThat(schema.aepCapabilities)
            .extracting(capability -> capability.id)
            .doesNotContain("aep.patterns", "aep.deployments");

        SurfaceSchemaGenerator.Capability pipelines = schema.aepCapabilities.stream()
            .filter(capability -> "aep.pipelines".equals(capability.id))
            .findFirst()
            .orElseThrow();

        assertThat(pipelines.metadata)
            .containsEntry("route_count", pipelines.metadata.get("route_count"))
            .containsKey("canonical_routes");
        assertThat(pipelines.metadata.get("canonical_routes"))
            .contains("GET /api/v1/action/pipelines")
            .contains("POST /api/v1/action/pipelines");
    }

    @Test
    @DisplayName("projects AV tool catalog entries into the schema with policy metadata")
    void projectsAudioVideoToolCatalogIntoSchema() {
        SurfaceSchemaGenerator.SurfaceSchema schema = SurfaceSchemaGenerator.generateSchema();

        assertThat(schema.aepCapabilities)
            .extracting(capability -> capability.id)
            .contains(
                "av.speech-to-text",
                "av.text-to-speech",
                "av.vision-analysis",
                "av.multimodal-inference"
            );

        SurfaceSchemaGenerator.Capability multimodal = schema.aepCapabilities.stream()
            .filter(capability -> "av.multimodal-inference".equals(capability.id))
            .findFirst()
            .orElseThrow();

        assertThat(multimodal.metadata)
            .containsEntry("required_roles", "OPERATOR")
            .containsEntry("consent_required", "true")
            .containsEntry("endpoint_health", "runtime-probe-required");
        assertThat(multimodal.metadata.get("policy_tags"))
            .contains("pii-risk")
            .contains("biometric-risk");
    }

    private static Set<String> sortedSurfaceIds() {
        return RouteSurfaceMapping.getAllSurfaceIds();
    }
}
