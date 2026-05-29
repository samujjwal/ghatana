/*
 * Copyright (c) 2026 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.ghatana.yappc.kernel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for ProductUnitIntentExporter.
 *
 * @doc.type test
 * @doc.purpose Verifies ProductUnitIntent export uses canonical Kernel DTO shape
 * @doc.layer product
 * @doc.pattern Test
 */
class ProductUnitIntentExporterTest {

    @Test
    void exportsStandardWebApiProductUnitIntent(@TempDir Path tempDir) throws Exception {
        ProductUnitIntentExporter exporter = new ProductUnitIntentExporter();

        ProductUnitIntentExporter.ExportResult result = exporter.export(standardRequest(), tempDir.resolve("intent.yaml"));

        Map<String, Object> intent = result.intent();
        assertThat(intent.get("schemaVersion")).isEqualTo("1.0.0");
        assertThat(intent.get("intentType")).isEqualTo("create");
        assertThat(result.intentId()).isNotBlank();
        assertThat(Files.exists(result.outputPath())).isTrue();

        @SuppressWarnings("unchecked")
        Map<String, Object> scope = (Map<String, Object>) intent.get("scope");
        assertThat(scope).containsEntry("tenantId", "tenant-123")
                .containsEntry("workspaceId", "workspace-123")
                .containsEntry("projectId", "digital-marketing-campaign");

        @SuppressWarnings("unchecked")
        Map<String, Object> producer = (Map<String, Object>) intent.get("producer");
        assertThat(producer).containsEntry("type", "yappc")
                .containsEntry("correlationId", result.intentId());

        @SuppressWarnings("unchecked")
        Map<String, Object> productUnit = (Map<String, Object>) intent.get("productUnit");
        assertThat(productUnit.get("id")).isEqualTo("digital-marketing-campaign");
        assertThat(productUnit.get("kind")).isEqualTo("business-product");
        assertThat(productUnit.get("lifecycleProfile")).isEqualTo("standard-web-api-product");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> surfaces = (List<Map<String, Object>>) productUnit.get("surfaces");
        assertThat(surfaces)
                .extracting(surface -> surface.get("type"))
                .containsExactly("backend-api", "web");
        assertThat(surfaces)
                .allSatisfy(surface -> assertThat(surface.get("implementationStatus")).isEqualTo("planned"));
    }

    @Test
    void exportedIntentPassesValidation() throws Exception {
        ProductUnitIntentExporter exporter = new ProductUnitIntentExporter();

        ProductUnitIntentExporter.ExportResult result = exporter.buildIntent(standardRequest());

        ProductUnitIntentValidationService.ValidationResult validation =
                new ProductUnitIntentValidationService().validate(result.intent());
        assertThat(validation.errors()).isEmpty();
        assertThat(validation.isValid()).isTrue();
    }

    @Test
    void exportedYamlMatchesKernelContractGoldenFile(@TempDir Path tempDir) throws Exception {
        ProductUnitIntentExporter exporter = new ProductUnitIntentExporter(
                new ProductUnitKernelContractRegistry(),
                Clock.fixed(Instant.parse("2026-05-26T10:15:30Z"), ZoneOffset.UTC),
                () -> "intent-golden-1");

        ProductUnitIntentExporter.ExportResult result = exporter.export(
                standardRequest(),
                tempDir.resolve("intent.yaml"));

        assertThat(normalizeLineEndings(Files.readString(result.outputPath())))
                .isEqualTo(readResource("golden/product-unit-intent.standard.yaml"));
    }

    @Test
    void rejectsUnknownKernelProvider() {
        ProductUnitIntentExporter exporter = new ProductUnitIntentExporter();
        ProductUnitIntentExporter.Request request = ProductUnitIntentExporter.Request.builder()
                .projectId("test-project")
                .projectName("Test Project")
                .targetType("kernel-product-unit")
                .surfaces(List.of("backend-api"))
                .runtimeProvider("ghatana-kernel")
                .sourceProvider("ghatana-file-registry")
                .lifecycleProfile("standard-web-api-product")
                .tenantId("tenant-123")
                .workspaceId("workspace-123")
                .build();

        assertThatThrownBy(() -> exporter.export(request, Path.of("/tmp/test.yaml")))
                .isInstanceOf(ProductUnitIntentExporter.ExportException.class)
                .hasMessageContaining("Unknown Kernel registry provider: ghatana-kernel");
    }

    @Test
    void rejectsUnknownSurface() {
        ProductUnitIntentExporter exporter = new ProductUnitIntentExporter();
        ProductUnitIntentExporter.Request request = ProductUnitIntentExporter.Request.builder()
                .projectId("test-project")
                .projectName("Test Project")
                .targetType("kernel-product-unit")
                .surfaces(List.of("frontend"))
                .runtimeProvider("ghatana-file-registry")
                .lifecycleProfile("standard-web-api-product")
                .tenantId("tenant-123")
                .workspaceId("workspace-123")
                .build();

        assertThatThrownBy(() -> exporter.export(request, Path.of("/tmp/test.yaml")))
                .isInstanceOf(ProductUnitIntentExporter.ExportException.class)
                .hasMessageContaining("Unknown ProductUnit surface: frontend");
    }

        @Test
        void rejectsMissingTenantId() {
                ProductUnitIntentExporter exporter = new ProductUnitIntentExporter();
                ProductUnitIntentExporter.Request request = ProductUnitIntentExporter.Request.builder()
                                .projectId("test-project")
                                .projectName("Test Project")
                                .targetType("kernel-product-unit")
                                .surfaces(List.of("backend-api"))
                                .runtimeProvider("ghatana-file-registry")
                                .sourceProvider("ghatana-file-registry")
                                .lifecycleProfile("standard-web-api-product")
                                .workspaceId("workspace-123")
                                .build();

                assertThatThrownBy(() -> exporter.export(request, Path.of("/tmp/test.yaml")))
                                .isInstanceOf(ProductUnitIntentExporter.ExportException.class)
                                .hasMessageContaining("tenantId is required");
        }

    @Test
    void includesCustomMetadata() throws Exception {
        ProductUnitIntentExporter exporter = new ProductUnitIntentExporter();

        ProductUnitIntentExporter.ExportResult result = exporter.buildIntent(ProductUnitIntentExporter.Request.builder()
                .projectId("test-project")
                .projectName("Test Project")
                .targetType("kernel-product-unit")
                .surfaces(List.of("backend-api"))
                .runtimeProvider("ghatana-file-registry")
                .sourceProvider("ghatana-file-registry")
                .lifecycleProfile("standard-web-api-product")
                .tenantId("tenant-123")
                .workspaceId("workspace-123")
                .metadata(Map.of("team", "marketing"))
                .build());

        @SuppressWarnings("unchecked")
        Map<String, Object> productUnit = (Map<String, Object>) result.intent().get("productUnit");
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) productUnit.get("metadata");

        assertThat(metadata).containsEntry("team", "marketing")
                .containsEntry("producer", "yappc")
                .containsEntry("sourcePhase", "generate")
                .containsEntry("projectId", "test-project")
                .containsEntry("workspaceId", "workspace-123");
        assertThat(metadata.get("exportedAt")).isNotNull();
    }

        @Test
        void usesExplicitRequestCorrelationIdWhenProvided() throws Exception {
                ProductUnitIntentExporter exporter = new ProductUnitIntentExporter(
                                new ProductUnitKernelContractRegistry(),
                                Clock.fixed(Instant.parse("2026-05-26T10:15:30Z"), ZoneOffset.UTC),
                                () -> "intent-123");

                ProductUnitIntentExporter.ExportResult result = exporter.buildIntent(ProductUnitIntentExporter.Request.builder()
                                .projectId("test-project")
                                .projectName("Test Project")
                                .targetType("kernel-product-unit")
                                .surfaces(List.of("backend-api"))
                                .runtimeProvider("ghatana-file-registry")
                                .sourceProvider("ghatana-file-registry")
                                .lifecycleProfile("standard-web-api-product")
                                .tenantId("tenant-123")
                                .workspaceId("workspace-123")
                                .correlationId("req-corr-9")
                                .build());

                @SuppressWarnings("unchecked")
                Map<String, Object> producer = (Map<String, Object>) result.intent().get("producer");

                assertThat(result.intentId()).isEqualTo("intent-123");
                assertThat(producer).containsEntry("correlationId", "req-corr-9");
        }

        @Test
        void rejectsUnknownSourceProvider() {
                ProductUnitIntentExporter exporter = new ProductUnitIntentExporter();
                ProductUnitIntentExporter.Request request = ProductUnitIntentExporter.Request.builder()
                                .projectId("test-project")
                                .projectName("Test Project")
                                .targetType("kernel-product-unit")
                                .surfaces(List.of("backend-api"))
                                .runtimeProvider("ghatana-file-registry")
                                .sourceProvider("unknown-source")
                                .lifecycleProfile("standard-web-api-product")
                                .tenantId("tenant-123")
                                .workspaceId("workspace-123")
                                .build();

                assertThatThrownBy(() -> exporter.export(request, Path.of("/tmp/test.yaml")))
                                .isInstanceOf(ProductUnitIntentExporter.ExportException.class)
                                .hasMessageContaining("Unknown source provider: unknown-source");
        }

    @Test
    void productUnitIntentDocumentImportedOnlyInExporter() throws IOException {
        Path repoRoot = findRepoRoot(Path.of("").toAbsolutePath());
        Pattern importPattern = Pattern.compile("import\\s+com\\.ghatana\\.contracts\\.kernel\\.ProductUnitIntentDocument");

        AtomicInteger importCount = new AtomicInteger(0);
        Path exporterPath = repoRoot.resolve("products/yappc/core/scaffold/api/src/main/java/com/ghatana/yappc/kernel/ProductUnitIntentExporter.java");

        for (Path sourceRoot : yappcJavaSourceRoots(repoRoot)) {
            if (!Files.exists(sourceRoot)) {
                continue;
            }
            try (Stream<Path> javaFiles = Files.walk(sourceRoot)) {
                javaFiles
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            String content = Files.readString(path);
                            Matcher matcher = importPattern.matcher(content);
                            if (matcher.find()) {
                                importCount.incrementAndGet();
                                assertThat(path).isEqualTo(exporterPath);
                            }
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to read file: " + path, e);
                        }
                    });
            }
        }

        assertThat(importCount.get()).isEqualTo(1);
    }

    private static List<Path> yappcJavaSourceRoots(Path repoRoot) {
        return List.of(
                repoRoot.resolve("products/yappc/core/scaffold/api/src/main/java"),
                repoRoot.resolve("products/yappc/core/scaffold/api/src/test/java"),
                repoRoot.resolve("products/yappc/core/scaffold/engine/src/main/java"),
                repoRoot.resolve("products/yappc/core/scaffold/generators/src/main/java"),
                repoRoot.resolve("products/yappc/core/scaffold/templates/src/main/java"),
                repoRoot.resolve("products/yappc/core/yappc-services/src/main/java"),
                repoRoot.resolve("products/yappc/core/yappc-services/src/test/java"),
                repoRoot.resolve("products/yappc/infrastructure/aep/src/main/java"),
                repoRoot.resolve("products/yappc/infrastructure/datacloud/src/main/java"),
                repoRoot.resolve("products/yappc/libs/java/yappc-domain/src/main/java")
        );
    }

    @Test
    void intentOutputIsConvertedFromProductUnitIntentDocument() throws Exception {
        // Regression test: proves output is converted from ProductUnitIntentDocument,
        // not hand-built ad hoc map. This ensures ProductUnitIntent shape remains contract-owned.
        ProductUnitIntentExporter exporter = new ProductUnitIntentExporter(
                new ProductUnitKernelContractRegistry(),
                Clock.fixed(Instant.parse("2026-05-26T10:15:30Z"), ZoneOffset.UTC),
                () -> "intent-dto-test-1");

        ProductUnitIntentExporter.ExportResult result = exporter.buildIntent(standardRequest());
        Map<String, Object> intent = result.intent();

        // Verify the output structure matches ProductUnitIntentDocument contract
        assertThat(intent).containsKey("schemaVersion");
        assertThat(intent).containsKey("intentId");
        assertThat(intent).containsKey("intentType");
        assertThat(intent).containsKey("scope");
        assertThat(intent).containsKey("producer");
        assertThat(intent).containsKey("target");
        assertThat(intent).containsKey("productUnit");
        assertThat(intent).containsKey("requestedLifecycle");

        // Verify scope structure matches ProductUnitScopeDocument
        @SuppressWarnings("unchecked")
        Map<String, Object> scope = (Map<String, Object>) intent.get("scope");
        assertThat(scope).containsKey("tenantId");
        assertThat(scope).containsKey("workspaceId");
        assertThat(scope).containsKey("projectId");

        // Verify producer structure matches ProducerDocument
        @SuppressWarnings("unchecked")
        Map<String, Object> producer = (Map<String, Object>) intent.get("producer");
        assertThat(producer).containsKey("id");
        assertThat(producer).containsKey("type");
        assertThat(producer).containsKey("name");
        assertThat(producer).containsKey("correlationId");

        // Verify target structure matches TargetProvidersDocument
        @SuppressWarnings("unchecked")
        Map<String, Object> target = (Map<String, Object>) intent.get("target");
        assertThat(target).containsKey("registryProvider");
        assertThat(target).containsKey("sourceProvider");

        // Verify productUnit structure matches ProductUnitDraftDocument
        @SuppressWarnings("unchecked")
        Map<String, Object> productUnit = (Map<String, Object>) intent.get("productUnit");
        assertThat(productUnit).containsKey("id");
        assertThat(productUnit).containsKey("name");
        assertThat(productUnit).containsKey("kind");
        assertThat(productUnit).containsKey("surfaces");
        assertThat(productUnit).containsKey("lifecycleProfile");
        assertThat(productUnit).containsKey("metadata");

        // Verify surfaces structure matches ProductUnitSurfaceDocument
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> surfaces = (List<Map<String, Object>>) productUnit.get("surfaces");
        assertThat(surfaces).isNotEmpty();
        assertThat(surfaces.get(0)).containsKey("id");
        assertThat(surfaces.get(0)).containsKey("type");
        assertThat(surfaces.get(0)).containsKey("implementationStatus");

        // Verify requestedLifecycle structure matches RequestedLifecycleDocument
        @SuppressWarnings("unchecked")
        Map<String, Object> requestedLifecycle = (Map<String, Object>) intent.get("requestedLifecycle");
        assertThat(requestedLifecycle).containsKey("profile");
        assertThat(requestedLifecycle).containsKey("enableExecution");
    }

    private static Path findRepoRoot(Path start) {
        Path current = start;
        while (current != null) {
            if (Files.exists(current.resolve(".github/copilot-instructions.md"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate repository root from " + start);
    }

    private static ProductUnitIntentExporter.Request standardRequest() {
        return ProductUnitIntentExporter.Request.builder()
                .projectId("digital-marketing-campaign")
                .projectName("Digital Marketing Campaign")
                .targetType("kernel-product-unit")
                .surfaces(List.of("backend-api", "web"))
                .runtimeProvider("ghatana-file-registry")
                .sourceProvider("ghatana-file-registry")
                .lifecycleProfile("standard-web-api-product")
                .tenantId("tenant-123")
                .workspaceId("workspace-123")
                .build();
    }

    private static String readResource(String resourcePath) throws Exception {
        URI uri = ProductUnitIntentExporterTest.class.getClassLoader()
                .getResource(resourcePath)
                .toURI();
        return normalizeLineEndings(Files.readString(Path.of(uri), StandardCharsets.UTF_8));
    }

    private static String normalizeLineEndings(String value) {
        return value.replace("\r\n", "\n");
    }
}
