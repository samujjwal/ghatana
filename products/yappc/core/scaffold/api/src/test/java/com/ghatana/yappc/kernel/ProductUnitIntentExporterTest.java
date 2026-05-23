/*
 * Copyright (c) 2026 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
 */
class ProductUnitIntentExporterTest {

    @Test
    void exportsStandardWebApiProductUnitIntent(@TempDir Path tempDir) throws Exception {
        ProductUnitIntentExporter exporter = new ProductUnitIntentExporter();

        ProductUnitIntentExporter.Request request = ProductUnitIntentExporter.Request.builder()
                .projectId("digital-marketing-campaign")
                .projectName("Digital Marketing Campaign")
                .targetType("kernel-product-unit")
                .surfaces(List.of("web-api", "frontend"))
                .runtimeProvider("ghatana-kernel")
                .lifecycleProfile("standard-web-api-product")
                .workspaceId("workspace-123")
                .build();

        Path outputPath = tempDir.resolve("product-unit-intent.yaml");
        ProductUnitIntentExporter.ExportResult result = exporter.export(request, outputPath);

        assertThat(result.intent()).isNotNull();
        assertThat(result.intentId()).isNotNull();
        assertThat(result.outputPath()).isEqualTo(outputPath);
        assertThat(Files.exists(outputPath)).isTrue();

        Map<String, Object> intent = result.intent();
        assertThat(intent.get("schemaVersion")).isEqualTo("1.0.0");
        assertThat(intent.get("intentId")).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> productUnit = (Map<String, Object>) intent.get("productUnit");
        assertThat(productUnit.get("id")).isEqualTo("digital-marketing-campaign");
        assertThat(productUnit.get("name")).isEqualTo("Digital Marketing Campaign");
        assertThat(productUnit.get("kind")).isEqualTo("business-product");
        assertThat(productUnit.get("surfaces")).isEqualTo(List.of("web-api", "frontend"));
        assertThat(productUnit.get("lifecycleProfile")).isEqualTo("standard-web-api-product");

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) productUnit.get("metadata");
        assertThat(metadata).isNotNull();
        assertThat(metadata.get("producer")).isEqualTo("yappc");
        assertThat(metadata.get("sourcePhase")).isEqualTo("generate");
        assertThat(metadata.get("projectId")).isEqualTo("digital-marketing-campaign");
        assertThat(metadata.get("workspaceId")).isEqualTo("workspace-123");
    }

    @Test
    void exportsFrontendOnlyProductUnitIntent(@TempDir Path tempDir) throws Exception {
        ProductUnitIntentExporter exporter = new ProductUnitIntentExporter();

        ProductUnitIntentExporter.Request request = ProductUnitIntentExporter.Request.builder()
                .projectId("marketing-frontend")
                .projectName("Marketing Frontend")
                .targetType("kernel-product-unit")
                .surfaces(List.of("frontend"))
                .runtimeProvider("ghatana-kernel")
                .lifecycleProfile("standard-web-api-product")
                .workspaceId("workspace-456")
                .build();

        Path outputPath = tempDir.resolve("frontend-intent.yaml");
        ProductUnitIntentExporter.ExportResult result = exporter.export(request, outputPath);

        @SuppressWarnings("unchecked")
        Map<String, Object> productUnit = (Map<String, Object>) result.intent().get("productUnit");
        assertThat(productUnit.get("id")).isEqualTo("marketing-frontend");
        assertThat(productUnit.get("name")).isEqualTo("Marketing Frontend");
        assertThat(productUnit.get("kind")).isEqualTo("business-product");
        assertThat(productUnit.get("surfaces")).isEqualTo(List.of("frontend"));
    }

    @Test
    void exportsBackendOnlyProductUnitIntent(@TempDir Path tempDir) throws Exception {
        ProductUnitIntentExporter exporter = new ProductUnitIntentExporter();

        ProductUnitIntentExporter.Request request = ProductUnitIntentExporter.Request.builder()
                .projectId("campaign-service")
                .projectName("Campaign Service")
                .targetType("kernel-product-unit")
                .surfaces(List.of("web-api"))
                .runtimeProvider("ghatana-kernel")
                .lifecycleProfile("backend-only-java-service")
                .workspaceId("workspace-789")
                .build();

        Path outputPath = tempDir.resolve("backend-intent.yaml");
        ProductUnitIntentExporter.ExportResult result = exporter.export(request, outputPath);

        @SuppressWarnings("unchecked")
        Map<String, Object> productUnit = (Map<String, Object>) result.intent().get("productUnit");
        assertThat(productUnit.get("id")).isEqualTo("campaign-service");
        assertThat(productUnit.get("name")).isEqualTo("Campaign Service");
        assertThat(productUnit.get("kind")).isEqualTo("business-product");
        assertThat(productUnit.get("surfaces")).isEqualTo(List.of("web-api"));
    }

    @Test
    void rejectsMissingProductId() {
        ProductUnitIntentExporter exporter = new ProductUnitIntentExporter();

        ProductUnitIntentExporter.Request request = ProductUnitIntentExporter.Request.builder()
                .projectName("Test Project")
                .targetType("kernel-product-unit")
                .surfaces(List.of("web-api"))
                .runtimeProvider("ghatana-kernel")
                .lifecycleProfile("standard-web-api-product")
                .workspaceId("workspace-123")
                .build();

        assertThatThrownBy(() -> exporter.export(request, Path.of("/tmp/test.yaml")))
                .isInstanceOf(ProductUnitIntentExporter.ExportException.class)
                .hasMessageContaining("projectId is required");
    }

    @Test
    void rejectsMissingProductName() {
        ProductUnitIntentExporter exporter = new ProductUnitIntentExporter();

        ProductUnitIntentExporter.Request request = ProductUnitIntentExporter.Request.builder()
                .projectId("test-project")
                .targetType("kernel-product-unit")
                .surfaces(List.of("web-api"))
                .runtimeProvider("ghatana-kernel")
                .lifecycleProfile("standard-web-api-product")
                .workspaceId("workspace-123")
                .build();

        assertThatThrownBy(() -> exporter.export(request, Path.of("/tmp/test.yaml")))
                .isInstanceOf(ProductUnitIntentExporter.ExportException.class)
                .hasMessageContaining("projectName is required");
    }

    @Test
    void rejectsEmptySurfaces() {
        ProductUnitIntentExporter exporter = new ProductUnitIntentExporter();

        ProductUnitIntentExporter.Request request = ProductUnitIntentExporter.Request.builder()
                .projectId("test-project")
                .projectName("Test Project")
                .targetType("kernel-product-unit")
                .surfaces(List.of())
                .runtimeProvider("ghatana-kernel")
                .workspaceId("workspace-123")
                .build();

        assertThatThrownBy(() -> exporter.export(request, Path.of("/tmp/test.yaml")))
                .isInstanceOf(ProductUnitIntentExporter.ExportException.class)
                .hasMessageContaining("surfaces must be non-empty");
    }

    @Test
    void doesNotWriteKernelRegistry(@TempDir Path tempDir) throws Exception {
        ProductUnitIntentExporter exporter = new ProductUnitIntentExporter();

        ProductUnitIntentExporter.Request request = ProductUnitIntentExporter.Request.builder()
                .projectId("test-project")
                .projectName("Test Project")
                .targetType("kernel-product-unit")
                .surfaces(List.of("web-api"))
                .runtimeProvider("ghatana-kernel")
                .lifecycleProfile("standard-web-api-product")
                .workspaceId("workspace-123")
                .build();

        Path outputPath = tempDir.resolve("product-unit-intent.yaml");
        exporter.export(request, outputPath);

        // Verify that only the intent file was written, not any Kernel registry files
        assertThat(Files.list(tempDir)).hasSize(1);
        assertThat(Files.list(tempDir).anyMatch(p -> p.getFileName().toString().equals("product-unit-intent.yaml"))).isTrue();
    }

    @Test
    void includesProducerMetadata(@TempDir Path tempDir) throws Exception {
        ProductUnitIntentExporter exporter = new ProductUnitIntentExporter();

        ProductUnitIntentExporter.Request request = ProductUnitIntentExporter.Request.builder()
                .projectId("test-project")
                .projectName("Test Project")
                .targetType("kernel-product-unit")
                .surfaces(List.of("web-api"))
                .runtimeProvider("ghatana-kernel")
                .lifecycleProfile("standard-web-api-product")
                .workspaceId("workspace-123")
                .sourcePhase("validate")
                .build();

        Path outputPath = tempDir.resolve("intent.yaml");
        ProductUnitIntentExporter.ExportResult result = exporter.export(request, outputPath);

        @SuppressWarnings("unchecked")
        Map<String, Object> productUnit = (Map<String, Object>) result.intent().get("productUnit");
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) productUnit.get("metadata");

        assertThat(metadata.get("producer")).isEqualTo("yappc");
        assertThat(metadata.get("sourcePhase")).isEqualTo("validate");
        assertThat(metadata.get("projectId")).isEqualTo("test-project");
        assertThat(metadata.get("workspaceId")).isEqualTo("workspace-123");
        assertThat(metadata.get("exportedAt")).isNotNull();
    }

    @Test
    void buildsIntentWithoutWritingToFile() throws Exception {
        ProductUnitIntentExporter exporter = new ProductUnitIntentExporter();

        ProductUnitIntentExporter.Request request = ProductUnitIntentExporter.Request.builder()
                .projectId("test-project")
                .projectName("Test Project")
                .targetType("kernel-product-unit")
                .surfaces(List.of("web-api"))
                .runtimeProvider("ghatana-kernel")
                .lifecycleProfile("standard-web-api-product")
                .workspaceId("workspace-123")
                .build();

        ProductUnitIntentExporter.ExportResult result = exporter.buildIntent(request);

        assertThat(result.intent()).isNotNull();
        assertThat(result.intentId()).isNotNull();
        assertThat(result.outputPath()).isNull();
    }

    @Test
    void includesCustomMetadata(@TempDir Path tempDir) throws Exception {
        ProductUnitIntentExporter exporter = new ProductUnitIntentExporter();

        Map<String, Object> customMetadata = new java.util.HashMap<>();
        customMetadata.put("team", "marketing");
        customMetadata.put("priority", "high");

        ProductUnitIntentExporter.Request request = ProductUnitIntentExporter.Request.builder()
                .projectId("test-project")
                .projectName("Test Project")
                .targetType("kernel-product-unit")
                .surfaces(List.of("web-api"))
                .runtimeProvider("ghatana-kernel")
                .lifecycleProfile("standard-web-api-product")
                .workspaceId("workspace-123")
                .metadata(customMetadata)
                .build();

        Path outputPath = tempDir.resolve("intent.yaml");
        ProductUnitIntentExporter.ExportResult result = exporter.export(request, outputPath);

        @SuppressWarnings("unchecked")
        Map<String, Object> productUnit = (Map<String, Object>) result.intent().get("productUnit");
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) productUnit.get("metadata");

        assertThat(metadata.get("team")).isEqualTo("marketing");
        assertThat(metadata.get("priority")).isEqualTo("high");
    }
}
