/*
 * Copyright (c) 2026 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.ghatana.yappc.kernel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

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
    void rejectsUnknownKernelProvider() {
        ProductUnitIntentExporter exporter = new ProductUnitIntentExporter();
        ProductUnitIntentExporter.Request request = ProductUnitIntentExporter.Request.builder()
                .projectId("test-project")
                .projectName("Test Project")
                .targetType("kernel-product-unit")
                .surfaces(List.of("backend-api"))
                .runtimeProvider("ghatana-kernel")
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
    void includesCustomMetadata() throws Exception {
        ProductUnitIntentExporter exporter = new ProductUnitIntentExporter();

        ProductUnitIntentExporter.ExportResult result = exporter.buildIntent(ProductUnitIntentExporter.Request.builder()
                .projectId("test-project")
                .projectName("Test Project")
                .targetType("kernel-product-unit")
                .surfaces(List.of("backend-api"))
                .runtimeProvider("ghatana-file-registry")
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

    private static ProductUnitIntentExporter.Request standardRequest() {
        return ProductUnitIntentExporter.Request.builder()
                .projectId("digital-marketing-campaign")
                .projectName("Digital Marketing Campaign")
                .targetType("kernel-product-unit")
                .surfaces(List.of("backend-api", "web"))
                .runtimeProvider("ghatana-file-registry")
                .lifecycleProfile("standard-web-api-product")
                .tenantId("tenant-123")
                .workspaceId("workspace-123")
                .build();
    }
}
