package com.ghatana.yappc.kernel;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for KernelBackendRouteGenerator.
 *
 * @doc.type test
 * @doc.purpose Verifies YAPPC generates generic backend route skeletons with proper structure
 * @doc.layer product
 * @doc.pattern ContractTest
 */
class KernelBackendRouteGeneratorTest {

    @Test
    void generatesBackendRouteSkeletonsForRoutesWithApiEndpoints() {
        KernelProductContractImporter.KernelRouteContract contract = new KernelProductContractImporter.KernelRouteContract(
            "1.0.0",
            "sample-product",
            "1.0.0",
            List.of(
                new KernelProductContractImporter.ProductRoute(
                    "/dashboard",
                    "Dashboard",
                    "Workspace dashboard",
                    "care",
                    "member",
                    List.of("operator"),
                    List.of("core"),
                    List.of("view"),
                    List.of("summary"),
                    "stable",
                    "/api/v1/dashboard",
                    "sample.dashboard.view",
                    "sample-dashboard-view-001",
                    List.of("web"),
                    "sample.routes.dashboard.label",
                    "sample.routes.dashboard.description",
                    "web",
                    Map.of()
                )
            )
        );

        KernelProductContractImporter.ImportedKernelProduct imported = new KernelProductContractImporter.ImportedKernelProduct(
            "sample-product",
            contract.version(),
            contract.routes(),
            List.of(),
            List.of(),
            productUnitIntentRequest()
        );

        KernelBackendRouteGenerator generator = new KernelBackendRouteGenerator();
        List<GeneratedBackendRoute> routes = generator.generateRouteSkeletons(imported);

        assertThat(routes).hasSize(1);
        assertThat(routes.get(0).className()).isEqualTo("DashboardRoutes");
        assertThat(routes.get(0).filePath()).contains("DashboardRoutes.java");
        assertThat(routes.get(0).code()).contains("class DashboardRoutes");
        assertThat(routes.get(0).filePath()).contains("com/ghatana/sampleproduct/api/routes");
        assertThat(routes.get(0).code()).contains("SampleProductRouteSupport");
        assertThat(routes.get(0).code()).contains("SampleProductPolicyEvaluator");
        assertThat(routes.get(0).code()).contains("Objects.requireNonNull(policyEvaluator");
        assertThat(routes.get(0).code()).contains("policyEvaluator.canAccessResourceAsync");
        assertThat(routes.get(0).code()).contains("SampleProductRouteSupport.policyDenialResponse");
        assertThat(routes.get(0).code()).contains("ROUTE_NOT_IMPLEMENTED");
        assertThat(routes.get(0).code()).doesNotContain("TODO");
        assertThat(routes.get(0).code()).doesNotContain("new Object()");
    }

    @Test
    void skipsRoutesWithoutApiEndpoints() {
        KernelProductContractImporter.KernelRouteContract contract = new KernelProductContractImporter.KernelRouteContract(
            "1.0.0",
            "sample-product",
            "1.0.0",
            List.of(
                new KernelProductContractImporter.ProductRoute(
                    "/settings",
                    "Settings",
                    "User settings",
                    "profile",
                    "member",
                    List.of("operator"),
                    List.of("core"),
                    List.of("view"),
                    List.of("summary"),
                    "stable",
                    null,
                    null,
                    null,
                    List.of("web"),
                    "sample.routes.settings.label",
                    "sample.routes.settings.description",
                    "web",
                    Map.of()
                )
            )
        );

        KernelProductContractImporter.ImportedKernelProduct imported = new KernelProductContractImporter.ImportedKernelProduct(
            "sample-product",
            contract.version(),
            contract.routes(),
            List.of(),
            List.of(),
            productUnitIntentRequest()
        );

        KernelBackendRouteGenerator generator = new KernelBackendRouteGenerator();
        List<GeneratedBackendRoute> routes = generator.generateRouteSkeletons(imported);

        assertThat(routes).isEmpty();
    }

    @Test
    void infersHttpMethodFromEndpointPattern() {
        KernelProductContractImporter.KernelRouteContract contract = new KernelProductContractImporter.KernelRouteContract(
            "1.0.0",
            "sample-product",
            "1.0.0",
            List.of(
                new KernelProductContractImporter.ProductRoute(
                    "/records/create",
                    "Create Record",
                    "Create workspace record",
                    "care",
                    "member",
                    List.of("operator"),
                    List.of("core"),
                    List.of("create"),
                    List.of("summary"),
                    "stable",
                    "/api/v1/records/create",
                    "sample.records.create",
                    "sample-records-create-001",
                    List.of("web"),
                    "sample.routes.records.create.label",
                    "sample.routes.records.create.description",
                    "web",
                    Map.of()
                )
            )
        );

        KernelProductContractImporter.ImportedKernelProduct imported = new KernelProductContractImporter.ImportedKernelProduct(
            "sample-product",
            contract.version(),
            contract.routes(),
            List.of(),
            List.of(),
            productUnitIntentRequest()
        );

        KernelBackendRouteGenerator generator = new KernelBackendRouteGenerator();
        List<GeneratedBackendRoute> routes = generator.generateRouteSkeletons(imported);

        assertThat(routes.get(0).code()).contains("Method: POST");
    }

    @Test
    void handlesMetadataFallbackForApiEndpointAndPolicyId() {
        KernelProductContractImporter.KernelRouteContract contract = new KernelProductContractImporter.KernelRouteContract(
            "1.0.0",
            "sample-product",
            "1.0.0",
            List.of(
                new KernelProductContractImporter.ProductRoute(
                    "/documents",
                    "Documents",
                    "Workspace documents",
                    "care",
                    "member",
                    List.of("operator"),
                    List.of("core"),
                    List.of("view"),
                    List.of("summary"),
                    "stable",
                    null,
                    null,
                    null,
                    List.of("web"),
                    "sample.routes.documents.label",
                    "sample.routes.documents.description",
                    "web",
                    Map.of(
                        "apiEndpoint", "/api/v1/documents",
                        "policyId", "sample.documents.view"
                    )
                )
            )
        );

        KernelProductContractImporter.ImportedKernelProduct imported = new KernelProductContractImporter.ImportedKernelProduct(
            "sample-product",
            contract.version(),
            contract.routes(),
            List.of(),
            List.of(),
            productUnitIntentRequest()
        );

        KernelBackendRouteGenerator generator = new KernelBackendRouteGenerator();
        List<GeneratedBackendRoute> routes = generator.generateRouteSkeletons(imported);

        assertThat(routes).hasSize(1);
        assertThat(routes.get(0).code()).contains("/api/v1/documents");
        assertThat(routes.get(0).code()).contains("sample.documents.view");
    }

    private static ProductUnitIntentExporter.Request productUnitIntentRequest() {
        return ProductUnitIntentExporter.Request.builder()
                .projectId("sample-product")
                .projectName("Sample Product")
                .targetType("kernel-product-unit")
                .surfaces(List.of("backend-api", "web"))
                .runtimeProvider("ghatana-file-registry")
                .sourceProvider("ghatana-file-registry")
                .lifecycleProfile("mobile-plus-api-product")
                .tenantId("product-contract-import")
                .workspaceId("yappc-product-roundtrip")
                .sourcePhase("contract-import")
                .metadata(Map.of("sourceProduct", "sample-product"))
                .build();
    }
}
