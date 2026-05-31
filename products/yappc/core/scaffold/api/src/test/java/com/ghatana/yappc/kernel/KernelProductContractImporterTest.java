/*
 * Copyright (c) 2026 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.ghatana.yappc.kernel;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for KernelProductContractImporter.
 *
 * @doc.type test
 * @doc.purpose Verifies YAPPC imports generic Kernel product contracts and produces ProductUnit intent/artifact coverage
 * @doc.layer product
 * @doc.pattern ContractTest
 */
class KernelProductContractImporterTest {

    private static final String SAMPLE_ROUTE_CONTRACT =
            "products/yappc/core/scaffold/api/src/test/resources/kernel/sample-route-contract.json";
    private static final String SAMPLE_USECASE_BASELINE =
            "products/yappc/core/scaffold/api/src/test/resources/kernel/sample-usecase-baseline.json";

    @Test
    void importsCanonicalRouteAndUseCaseContracts() {
        KernelProductContractImporter.ImportedKernelProduct imported = importer().importProduct(
                repoFile(SAMPLE_ROUTE_CONTRACT),
                repoFile(SAMPLE_USECASE_BASELINE));

        assertThat(imported.product()).isEqualTo("sample-product");
        assertThat(imported.routes()).extracting(KernelProductContractImporter.ProductRoute::path)
                .contains("/dashboard", "/records");
        assertThat(imported.useCases()).extracting(KernelProductContractImporter.ProductUseCase::id)
                .contains("uc-operator-dashboard", "uc-operator-records");
        assertThat(imported.routes())
                .filteredOn(route -> "stable".equals(route.stability()))
                .allSatisfy(route -> {
                    assertThat(route.apiEndpoint()).as(route.path()).isNotBlank();
                    assertThat(route.policyId()).as(route.path()).startsWith("sample.");
                    assertThat(route.testId()).as(route.path()).startsWith("sample-");
                });
    }

    @Test
    void derivesKernelProductUnitIntentFromContract() throws Exception {
        KernelProductContractImporter.ImportedKernelProduct imported = importer().importProduct(
                repoFile(SAMPLE_ROUTE_CONTRACT),
                repoFile(SAMPLE_USECASE_BASELINE));

        ProductUnitIntentExporter.ExportResult result =
                new ProductUnitIntentExporter().buildIntent(imported.productUnitIntentRequest());

        @SuppressWarnings("unchecked")
        Map<String, Object> productUnit = (Map<String, Object>) result.intent().get("productUnit");
        assertThat(productUnit).containsEntry("id", "sample-product")
                .containsEntry("kind", "business-product")
                .containsEntry("lifecycleProfile", "mobile-plus-api-product");

        @SuppressWarnings("unchecked")
        var surfaces = (java.util.List<Map<String, Object>>) productUnit.get("surfaces");
        assertThat(surfaces).extracting(surface -> surface.get("type"))
                .containsExactly("backend-api", "web", "mobile");

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) productUnit.get("metadata");
        assertThat(metadata).containsEntry("sourceProduct", "sample-product")
                .containsEntry("producer", "yappc")
                .containsKey("routeCount")
                .containsKey("useCaseCount")
                .containsKey("generatedArtifactCount");
    }

    @Test
    void generatesArtifactCoverageForWebBackendMobileAndTests() {
        KernelProductContractImporter.ImportedKernelProduct imported = importer().importProduct(
                repoFile(SAMPLE_ROUTE_CONTRACT),
                repoFile(SAMPLE_USECASE_BASELINE));

        assertThat(imported.generatedArtifacts())
                .extracting(KernelProductContractImporter.GeneratedArtifact::surface)
                .contains("backend-api", "web", "mobile", "test");
        assertThat(imported.generatedArtifacts())
                .anySatisfy(artifact -> {
                    assertThat(artifact.artifactType()).isEqualTo("react-route");
                    assertThat(artifact.target()).isEqualTo("/dashboard");
                })
                .anySatisfy(artifact -> {
                    assertThat(artifact.artifactType()).isEqualTo("java-route-contract-test");
                    assertThat(artifact.target()).startsWith("/api/");
                })
                .anySatisfy(artifact -> {
                    assertThat(artifact.artifactType()).isEqualTo("react-native-screen");
                    assertThat(artifact.target()).isEqualTo("dashboard");
                });
    }

    private static KernelProductContractImporter importer() {
        return new KernelProductContractImporter();
    }

    private static Path repoFile(String relativePath) {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relativePath);
            if (java.nio.file.Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate repository file: " + relativePath);
    }
}
