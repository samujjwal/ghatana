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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.common.function.SupplierEx;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Ingests Kernel lifecycle events or local pilot manifests into YAPPC read model.
 *
 * <p>This service reads Kernel lifecycle outputs from local filesystem for development
 * and normalizes them into a YAPPC read model for health visualization. Future production
 * implementation will use Data Cloud/event stream backing.
 *
 * <p><b>Initial Implementation (Local Filesystem)</b></p>
 * <ul>
 *   <li>Reads <code>.kernel/out/products/&lt;productId&gt;/**</code> for local/dev</li>
 *   <li>Parses public lifecycle-plan/result/artifact/deployment/health JSON files</li>
 *   <li>Normalizes into read model for health visualization</li>
 * </ul>
 *
 * <p><b>Future Implementation (Data Cloud/Event Stream)</b></p>
 * <ul>
 *   <li>Subscribe to Kernel lifecycle events via Event Cloud</li>
 *   <li>Query Data Cloud for historical lifecycle runs</li>
 *   <li>Real-time updates to YAPPC read model</li>
 * </ul>
 *
 * <p><b>Important Constraints</b></p>
 * <ul>
 *   <li>Does NOT parse stdout/stderr logs for lifecycle status</li>
 *   <li>Does NOT assume product is Digital Marketing only (product-neutral)</li>
 *   <li>Consumes only public Kernel contracts and manifests</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Ingests Kernel lifecycle events into YAPPC read model for health visualization
 * @doc.layer product
 * @doc.pattern Service
 */
public final class KernelLifecycleEventIngestService {

    private static final Logger log = LoggerFactory.getLogger(KernelLifecycleEventIngestService.class);

    private final ObjectMapper objectMapper;
    private final Path kernelOutputRoot;
    private static final AtomicLong THREAD_COUNTER = new AtomicLong(0);
    private static final Executor BLOCKING_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r);
        thread.setName("kernel-visibility-blocking-" + THREAD_COUNTER.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });

    /**
     * Constructs a new KernelLifecycleEventIngestService with default kernel output root.
     */
    public KernelLifecycleEventIngestService() {
        this(Path.of(".kernel"));
    }

    /**
     * Constructs a new KernelLifecycleEventIngestService with custom kernel output root.
     *
     * @param kernelOutputRoot the root directory for Kernel output files
     */
    public KernelLifecycleEventIngestService(@NotNull Path kernelOutputRoot) {
        this.objectMapper = new ObjectMapper();
        this.kernelOutputRoot = kernelOutputRoot;
    }

    /**
     * Ingests lifecycle data for a specific ProductUnit from local Kernel output.
     *
     * @param productUnitId the ProductUnit ID to ingest data for
     * @return promise resolving to the ingested lifecycle data map
     */
    public Promise<Map<String, Object>> ingestProductUnitLifecycle(@NotNull String productUnitId) {
        return Promise.ofBlocking(BLOCKING_EXECUTOR, () -> {
            Path productUnitPath = kernelOutputRoot.resolve("out/products").resolve(productUnitId);
            
            if (!Files.exists(productUnitPath)) {
                log.warn("Kernel output directory does not exist for ProductUnit: {}", productUnitId);
                return Map.of("productUnitId", productUnitId, "status", "not_found");
            }

            Map<String, Object> lifecycleData = new HashMap<>();
            lifecycleData.put("productUnitId", productUnitId);
            lifecycleData.put("status", "found");

            // Read lifecycle-plan.json
            readJsonFile(productUnitPath.resolve("lifecycle-plan.json"))
                    .ifPresent(data -> lifecycleData.put("lifecyclePlan", data));

            // Read lifecycle-result.json
            readJsonFile(productUnitPath.resolve("lifecycle-result.json"))
                    .ifPresent(data -> lifecycleData.put("lifecycleResult", data));

            // Read artifacts.json
            readJsonFile(productUnitPath.resolve("artifacts.json"))
                    .ifPresent(data -> lifecycleData.put("artifacts", data));

            // Read deployment.json
            readJsonFile(productUnitPath.resolve("deployment.json"))
                    .ifPresent(data -> lifecycleData.put("deployment", data));

            // Read health-snapshot.json
            readJsonFile(productUnitPath.resolve("health-snapshot.json"))
                    .ifPresent(data -> lifecycleData.put("healthSnapshot", data));

            // Read gates.json
            readJsonFile(productUnitPath.resolve("gates.json"))
                    .ifPresent(data -> lifecycleData.put("gates", data));

            log.info("Ingested lifecycle data for ProductUnit: {}", productUnitId);
            return lifecycleData;
        });
    }

    /**
     * Ingests lifecycle data for all ProductUnits from local Kernel output.
     *
     * @return promise resolving to a list of lifecycle data maps for all ProductUnits
     */
    public Promise<java.util.List<Map<String, Object>>> ingestAllProductUnitLifecycles() {
        return Promise.ofBlocking(BLOCKING_EXECUTOR, () -> {
            Path productsPath = kernelOutputRoot.resolve("out/products");
            
            if (!Files.exists(productsPath)) {
                log.warn("Kernel products output directory does not exist: {}", productsPath);
                return java.util.List.of();
            }

            java.util.List<Map<String, Object>> allLifecycleData = new java.util.ArrayList<>();

            try (var stream = Files.list(productsPath)) {
                stream.filter(Files::isDirectory)
                      .forEach(productUnitDir -> {
                          String productUnitId = productUnitDir.getFileName().toString();
                          try {
                              // Read files synchronously within blocking context
                              Map<String, Object> data = new HashMap<>();
                              data.put("productUnitId", productUnitId);
                              data.put("status", "found");
                              
                              // Read health-snapshot.json if exists
                              Path healthSnapshotPath = productUnitDir.resolve("health-snapshot.json");
                              if (Files.exists(healthSnapshotPath)) {
                                  readJsonFile(healthSnapshotPath).ifPresent(snapshot -> data.put("healthSnapshot", snapshot));
                              }
                              
                              // Read lifecycle-result.json if exists
                              Path lifecycleResultPath = productUnitDir.resolve("lifecycle-result.json");
                              if (Files.exists(lifecycleResultPath)) {
                                  readJsonFile(lifecycleResultPath).ifPresent(result -> data.put("lifecycleResult", result));
                              }
                              
                              // Read deployment.json if exists
                              Path deploymentPath = productUnitDir.resolve("deployment.json");
                              if (Files.exists(deploymentPath)) {
                                  readJsonFile(deploymentPath).ifPresent(deployment -> data.put("deployment", deployment));
                              }
                              
                              allLifecycleData.add(data);
                          } catch (Exception e) {
                              log.error("Failed to ingest lifecycle data for ProductUnit: {}", productUnitId, e);
                              Map<String, Object> errorData = new HashMap<>();
                              errorData.put("productUnitId", productUnitId);
                              errorData.put("status", "error");
                              errorData.put("error", e.getMessage());
                              allLifecycleData.add(errorData);
                          }
                      });
            } catch (Exception e) {
                log.error("Failed to list ProductUnits in Kernel output directory", e);
            }

            log.info("Ingested lifecycle data for {} ProductUnits", allLifecycleData.size());
            return allLifecycleData;
        });
    }

    /**
     * Checks if Kernel output data exists for a ProductUnit.
     *
     * @param productUnitId the ProductUnit ID to check
     * @return promise resolving to true if data exists, false otherwise
     */
    public Promise<Boolean> hasProductUnitLifecycleData(@NotNull String productUnitId) {
        return Promise.ofBlocking(BLOCKING_EXECUTOR, () -> {
            Path productUnitPath = kernelOutputRoot.resolve("out/products").resolve(productUnitId);
            return Files.exists(productUnitPath) && Files.isDirectory(productUnitPath);
        });
    }

    /**
     * Reads a JSON file and returns its contents as a Map.
     *
     * @param filePath the path to the JSON file
     * @return an Optional containing the parsed Map, or empty if the file doesn't exist or can't be parsed
     */
    @SuppressWarnings("unchecked")
    private java.util.Optional<Map<String, Object>> readJsonFile(@NotNull Path filePath) {
        try {
            if (!Files.exists(filePath)) {
                return java.util.Optional.empty();
            }

            String content = Files.readString(filePath);
            Map<String, Object> data = objectMapper.readValue(content, Map.class);
            return java.util.Optional.ofNullable(data);
        } catch (Exception e) {
            log.warn("Failed to read JSON file: {}", filePath, e);
            return java.util.Optional.empty();
        }
    }

    /**
     * Result of a lifecycle ingestion operation.
     */
    public static final class IngestionResult {
        private final String productUnitId;
        private final boolean success;
        private final Map<String, Object> data;
        private final String error;

        public IngestionResult(String productUnitId, boolean success, Map<String, Object> data, String error) {
            this.productUnitId = productUnitId;
            this.success = success;
            this.data = data;
            this.error = error;
        }

        public String productUnitId() { return productUnitId; }
        public boolean success() { return success; }
        public Map<String, Object> data() { return data; }
        public String error() { return error; }

        public static IngestionResult success(String productUnitId, Map<String, Object> data) {
            return new IngestionResult(productUnitId, true, data, null);
        }

        public static IngestionResult failure(String productUnitId, String error) {
            return new IngestionResult(productUnitId, false, null, error);
        }
    }
}
