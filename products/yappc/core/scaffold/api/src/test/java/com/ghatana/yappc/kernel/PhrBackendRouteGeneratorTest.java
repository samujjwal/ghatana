/**
 * Tests for PHR Backend Route Generator (YAPPC-T05)
 */

package com.ghatana.yappc.kernel;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for PhrBackendRouteGenerator.
 *
 * @doc.type test
 * @doc.purpose Verifies YAPPC generates PHR backend route skeletons with proper structure
 * @doc.layer product
 * @doc.pattern ContractTest
 */
class PhrBackendRouteGeneratorTest {

    @Test
    void generatesBackendRouteSkeletonsForRoutesWithApiEndpoints() {
        PhrProductContractImporter.PhrRouteContract contract = new PhrProductContractImporter.PhrRouteContract(
            "1.0.0",
            "phr",
            "1.0.0",
            List.of(
                new PhrProductContractImporter.PhrRoute(
                    "/dashboard",
                    "Dashboard",
                    "Patient dashboard",
                    "care",
                    "patient",
                    List.of("web"),
                    List.of("patient"),
                    List.of("core"),
                    List.of("view"),
                    List.of("summary"),
                    "stable",
                    "/api/v1/dashboard",
                    "phr.dashboard.view",
                    "phr-dashboard-view-001",
                    List.of("web"),
                    "phr.routes.dashboard.label",
                    "phr.routes.dashboard.description",
                    "web",
                    Map.of()
                )
            )
        );

        PhrProductContractImporter.ImportedPhrProduct imported = new PhrProductContractImporter.ImportedPhrProduct(
            "phr",
            contract.routes(),
            List.of(),
            List.of()
        );

        PhrBackendRouteGenerator generator = new PhrBackendRouteGenerator();
        List<PhrBackendRouteGenerator.GeneratedBackendRoute> routes = generator.generateRouteSkeletons(imported);

        assertThat(routes).hasSize(1);
        assertThat(routes.get(0).className()).isEqualTo("DashboardRoutes");
        assertThat(routes.get(0).filePath()).contains("DashboardRoutes.java");
        assertThat(routes.get(0).code()).contains("class DashboardRoutes");
        assertThat(routes.get(0).code()).contains("PhrRouteSupport");
        assertThat(routes.get(0).code()).contains("PhrPolicyEvaluator");
    }

    @Test
    void skipsRoutesWithoutApiEndpoints() {
        PhrProductContractImporter.PhrRouteContract contract = new PhrProductContractImporter.PhrRouteContract(
            "1.0.0",
            "phr",
            "1.0.0",
            List.of(
                new PhrProductContractImporter.PhrRoute(
                    "/settings",
                    "Settings",
                    "User settings",
                    "profile",
                    "patient",
                    List.of("web"),
                    List.of("patient"),
                    List.of("core"),
                    List.of("view"),
                    List.of("summary"),
                    "stable",
                    null,
                    null,
                    null,
                    List.of("web"),
                    "phr.routes.settings.label",
                    "phr.routes.settings.description",
                    "web",
                    Map.of()
                )
            )
        );

        PhrProductContractImporter.ImportedPhrProduct imported = new PhrProductContractImporter.ImportedPhrProduct(
            "phr",
            contract.routes(),
            List.of(),
            List.of()
        );

        PhrBackendRouteGenerator generator = new PhrBackendRouteGenerator();
        List<PhrBackendRouteGenerator.GeneratedBackendRoute> routes = generator.generateRouteSkeletons(imported);

        assertThat(routes).isEmpty();
    }

    @Test
    void infersHttpMethodFromEndpointPattern() {
        PhrProductContractImporter.PhrRouteContract contract = new PhrProductContractImporter.PhrRouteContract(
            "1.0.0",
            "phr",
            "1.0.0",
            List.of(
                new PhrProductContractImporter.PhrRoute(
                    "/records/create",
                    "Create Record",
                    "Create patient record",
                    "care",
                    "clinician",
                    List.of("web"),
                    List.of("clinician"),
                    List.of("core"),
                    List.of("create"),
                    List.of("summary"),
                    "stable",
                    "/api/v1/records/create",
                    "phr.records.create",
                    "phr-records-create-001",
                    List.of("web"),
                    "phr.routes.records.create.label",
                    "phr.routes.records.create.description",
                    "web",
                    Map.of()
                )
            )
        );

        PhrProductContractImporter.ImportedPhrProduct imported = new PhrProductContractImporter.ImportedPhrProduct(
            "phr",
            contract.routes(),
            List.of(),
            List.of()
        );

        PhrBackendRouteGenerator generator = new PhrBackendRouteGenerator();
        List<PhrBackendRouteGenerator.GeneratedBackendRoute> routes = generator.generateRouteSkeletons(imported);

        assertThat(routes.get(0).code()).contains("Method: POST");
    }

    @Test
    void handlesMetadataFallbackForApiEndpointAndPolicyId() {
        PhrProductContractImporter.PhrRouteContract contract = new PhrProductContractImporter.PhrRouteContract(
            "1.0.0",
            "phr",
            "1.0.0",
            List.of(
                new PhrProductContractImporter.PhrRoute(
                    "/documents",
                    "Documents",
                    "Patient documents",
                    "care",
                    "patient",
                    List.of("web"),
                    List.of("patient"),
                    List.of("core"),
                    List.of("view"),
                    List.of("summary"),
                    "stable",
                    null,
                    null,
                    null,
                    List.of("web"),
                    "phr.routes.documents.label",
                    "phr.routes.documents.description",
                    "web",
                    Map.of(
                        "apiEndpoint", "/api/v1/documents",
                        "policyId", "phr.documents.view"
                    )
                )
            )
        );

        PhrProductContractImporter.ImportedPhrProduct imported = new PhrProductContractImporter.ImportedPhrProduct(
            "phr",
            contract.routes(),
            List.of(),
            List.of()
        );

        PhrBackendRouteGenerator generator = new PhrBackendRouteGenerator();
        List<PhrBackendRouteGenerator.GeneratedBackendRoute> routes = generator.generateRouteSkeletons(imported);

        assertThat(routes).hasSize(1);
        assertThat(routes.get(0).code()).contains("/api/v1/documents");
        assertThat(routes.get(0).code()).contains("phr.documents.view");
    }
}
