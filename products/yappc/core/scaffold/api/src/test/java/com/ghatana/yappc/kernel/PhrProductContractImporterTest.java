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
 * Tests for PhrProductContractImporter.
 *
 * @doc.type test
 * @doc.purpose Verifies YAPPC imports real PHR contracts and produces Kernel ProductUnit intent/artifact coverage
 * @doc.layer product
 * @doc.pattern ContractTest
 */
class PhrProductContractImporterTest {

    private static final String PHR_ROUTE_CONTRACT = "products/phr/config/phr-route-contract.json";
    private static final String PHR_USECASE_BASELINE = "products/phr/config/phr-usecase-baseline.json";

    @Test
    void importsCanonicalPhrRouteAndUseCaseContracts() {
        PhrProductContractImporter.ImportedPhrProduct imported = importer().importProduct(
                repoFile(PHR_ROUTE_CONTRACT),
                repoFile(PHR_USECASE_BASELINE));

        assertThat(imported.product()).isEqualTo("phr");
        assertThat(imported.routes()).extracting(PhrProductContractImporter.PhrRoute::path)
                .contains("/dashboard", "/records", "/documents", "/emergency");
        assertThat(imported.useCases()).extracting(PhrProductContractImporter.PhrUseCase::id)
                .contains("uc-patient-dashboard", "uc-patient-records");
        assertThat(imported.routes())
                .filteredOn(route -> "stable".equals(route.stability()))
                .allSatisfy(route -> {
                    assertThat(route.apiEndpoint()).as(route.path()).isNotBlank();
                    assertThat(route.policyId()).as(route.path()).startsWith("phr.");
                    assertThat(route.testId()).as(route.path()).startsWith("phr-");
                });
    }

    @Test
    void derivesKernelProductUnitIntentFromPhrContract() throws Exception {
        PhrProductContractImporter.ImportedPhrProduct imported = importer().importProduct(
                repoFile(PHR_ROUTE_CONTRACT),
                repoFile(PHR_USECASE_BASELINE));

        ProductUnitIntentExporter.ExportResult result =
                new ProductUnitIntentExporter().buildIntent(imported.productUnitIntentRequest());

        @SuppressWarnings("unchecked")
        Map<String, Object> productUnit = (Map<String, Object>) result.intent().get("productUnit");
        assertThat(productUnit).containsEntry("id", "phr")
                .containsEntry("kind", "business-product")
                .containsEntry("lifecycleProfile", "mobile-plus-api-product");

        @SuppressWarnings("unchecked")
        var surfaces = (java.util.List<Map<String, Object>>) productUnit.get("surfaces");
        assertThat(surfaces).extracting(surface -> surface.get("type"))
                .containsExactly("backend-api", "web", "mobile");

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) productUnit.get("metadata");
        assertThat(metadata).containsEntry("sourceProduct", "phr")
                .containsEntry("producer", "yappc")
                .containsKey("routeCount")
                .containsKey("useCaseCount")
                .containsKey("generatedArtifactCount");
    }

    @Test
    void generatesArtifactCoverageForWebBackendMobileAndTests() {
        PhrProductContractImporter.ImportedPhrProduct imported = importer().importProduct(
                repoFile(PHR_ROUTE_CONTRACT),
                repoFile(PHR_USECASE_BASELINE));

        assertThat(imported.generatedArtifacts())
                .extracting(PhrProductContractImporter.GeneratedArtifact::surface)
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

    private static PhrProductContractImporter importer() {
        return new PhrProductContractImporter();
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
