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

package com.ghatana.yappc.kernelvisibility;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for LocalKernelManifestTruthSource.
 */
@DisplayName("LocalKernelManifestTruthSource")
class LocalKernelManifestTruthSourceTest extends EventloopTestBase {

    @TempDir
    Path tempDir;

    private Path kernelOutputRoot;
    private LocalKernelManifestTruthSource truthSource;

    @BeforeEach
    void setUp() {
        kernelOutputRoot = tempDir.resolve(".kernel");
        KernelLifecycleEventIngestService ingestService =
                                KernelLifecycleEventIngestService.forLocalDevelopment(kernelOutputRoot);
                truthSource = LocalKernelManifestTruthSource.forLocalDevelopment(ingestService);
    }

    @Test
    @DisplayName("returns lifecycle data for known ProductUnit")
    void returnsLifecycleDataForKnownProductUnit() throws Exception {
        Path productPath = kernelOutputRoot.resolve("out/products/digital-marketing");
        Files.createDirectories(productPath);
        Files.writeString(productPath.resolve("lifecycle-result.json"),
                """
                {"status":"succeeded","currentPhase":"deploy","timestamp":"2026-05-13T12:00:00Z"}
                """);
        Files.writeString(productPath.resolve("lifecycle-health-snapshot.json"),
                """
                {"status":"healthy","lastChecked":"2026-05-13T12:00:00Z"}
                """);

        Map<String, Object> data = runPromise(
                () -> truthSource.getProductUnitLifecycleData("digital-marketing"));

        assertThat(data).isNotNull();
        assertThat(data).isNotEmpty();
        assertThat(data.get("productUnitId")).isEqualTo("digital-marketing");
    }

    @Test
    @DisplayName("returns empty map with not_found status for missing ProductUnit")
    void returnsEmptyMapForMissingProductUnit() {
        Map<String, Object> data = runPromise(
                () -> truthSource.getProductUnitLifecycleData("nonexistent-product"));

        assertThat(data).isNotNull();
        assertThat(data.get("productUnitId")).isEqualTo("nonexistent-product");
        assertThat(data.get("status")).isEqualTo("not_found");
    }

    @Test
    @DisplayName("lists all ProductUnit IDs with manifest data")
    void listsAllProductUnitIds() throws Exception {
        Path product1Path = kernelOutputRoot.resolve("out/products/product-alpha");
        Path product2Path = kernelOutputRoot.resolve("out/products/product-beta");
        Files.createDirectories(product1Path);
        Files.createDirectories(product2Path);
        Files.writeString(product1Path.resolve("lifecycle-health-snapshot.json"),
                "{\"status\":\"healthy\"}");
        Files.writeString(product2Path.resolve("lifecycle-health-snapshot.json"),
                "{\"status\":\"degraded\"}");

        List<String> ids = runPromise(() -> truthSource.listProductUnitIds());

        assertThat(ids).containsExactlyInAnyOrder("product-alpha", "product-beta");
    }

    @Test
    @DisplayName("hasLifecycleData returns true when manifest files exist")
    void hasLifecycleDataReturnsTrueWhenDataExists() throws Exception {
        Path productPath = kernelOutputRoot.resolve("out/products/existing-product");
        Files.createDirectories(productPath);
        Files.writeString(productPath.resolve("lifecycle-health-snapshot.json"),
                "{\"status\":\"healthy\"}");

        Boolean hasData = runPromise(
                () -> truthSource.hasLifecycleData("existing-product"));

        assertThat(hasData).isTrue();
    }

    @Test
    @DisplayName("hasLifecycleData returns false when no manifest files exist")
    void hasLifecycleDataReturnsFalseWhenNoData() {
        Boolean hasData = runPromise(
                () -> truthSource.hasLifecycleData("phantom-product"));

        assertThat(hasData).isFalse();
    }

    @Test
    @DisplayName("listAllProductUnitLifecycleData returns empty list when no products exist")
    void listAllProductUnitLifecycleDataReturnsEmptyListWhenNoProducts() {
        List<Map<String, Object>> dataList = runPromise(
                () -> truthSource.listAllProductUnitLifecycleData());

        assertThat(dataList).isNotNull();
        assertThat(dataList).isEmpty();
    }
}
