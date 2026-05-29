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

import com.ghatana.datacloud.DataCloudClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Ingests Kernel lifecycle events or local pilot manifests into YAPPC read model.
 *
 * <p>This service reads Kernel lifecycle outputs from local filesystem for development
 * and normalizes them into a YAPPC read model for health visualization. Production
 * deployments should consume Kernel lifecycle truth through {@link KernelLifecycleTruthSource}
 * implementations backed by Data Cloud or Event Cloud.
 *
 * <p><b>Local Filesystem Provider</b></p>
 * <ul>
 *   <li>Reads <code>.kernel/out/products/&lt;productId&gt;/**</code> for local/dev</li>
 *   <li>Parses public lifecycle-plan/result/artifact/deployment/health JSON files</li>
 *   <li>Normalizes into read model for health visualization</li>
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
public final class KernelLifecycleEventIngestService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(KernelLifecycleEventIngestService.class);

    private static final AtomicLong THREAD_COUNTER = new AtomicLong(0);
    private final KernelLifecycleEventProvider provider;

    /**
     * Creates a local filesystem-backed ingest service for development/test usage.
     *
     * <p>Uses {@code .kernel} as the output root.
     */
    public static KernelLifecycleEventIngestService forLocalDevelopment() {
        return new KernelLifecycleEventIngestService(Path.of(".kernel"));
    }

    /**
     * Creates a local filesystem-backed ingest service for development/test usage.
     *
     * @param kernelOutputRoot root directory for local Kernel output files
     */
    public static KernelLifecycleEventIngestService forLocalDevelopment(@NotNull Path kernelOutputRoot) {
        return new KernelLifecycleEventIngestService(kernelOutputRoot);
    }

    /**
     * Creates a local filesystem-backed ingest service with externally managed executor.
     *
     * @param kernelOutputRoot root directory for local Kernel output files
     * @param blockingExecutor executor used for blocking filesystem reads
     */
    public static KernelLifecycleEventIngestService forLocalDevelopment(
            @NotNull Path kernelOutputRoot,
            @NotNull Executor blockingExecutor
    ) {
        return new KernelLifecycleEventIngestService(kernelOutputRoot, blockingExecutor);
    }

    private KernelLifecycleEventIngestService() {
        this(localFilesystemProvider(Path.of(".kernel"), createOwnedBlockingExecutor(), true));
    }

    private KernelLifecycleEventIngestService(@NotNull Path kernelOutputRoot) {
        this(localFilesystemProvider(kernelOutputRoot, createOwnedBlockingExecutor(), true));
    }

    private KernelLifecycleEventIngestService(@NotNull Path kernelOutputRoot, @NotNull Executor blockingExecutor) {
        this(localFilesystemProvider(kernelOutputRoot, blockingExecutor, false));
    }

    /**
     * Constructs a new KernelLifecycleEventIngestService backed by a production truth source.
     *
     * @param truthSource truth source implementation (for example Data Cloud backed)
     */
    public KernelLifecycleEventIngestService(@NotNull KernelLifecycleTruthSource truthSource) {
        this(new TruthSourceKernelLifecycleEventProvider(truthSource));
    }

    /**
     * Constructs a new KernelLifecycleEventIngestService backed by Data Cloud truth.
     *
     * @param dataCloudClient Data Cloud client
     * @param tenantId tenant identifier
     */
    public KernelLifecycleEventIngestService(
            @NotNull DataCloudClient dataCloudClient,
            @NotNull String tenantId
    ) {
        this(new DataCloudKernelLifecycleTruthSource(dataCloudClient, tenantId));
    }

    /**
     * Constructs a new KernelLifecycleEventIngestService with explicit provider implementation.
     *
     * @param provider lifecycle event provider
     */
    public KernelLifecycleEventIngestService(@NotNull KernelLifecycleEventProvider provider) {
        this.provider = provider;
    }

    private static ExecutorService createOwnedBlockingExecutor() {
        return Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r);
            thread.setName("kernel-visibility-blocking-" + THREAD_COUNTER.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    private static LocalFilesystemKernelLifecycleEventProvider localFilesystemProvider(
            @NotNull Path kernelOutputRoot,
            @NotNull Executor blockingExecutor,
            boolean ownsExecutor
    ) {
        assertLocalProviderAllowed();
        return new LocalFilesystemKernelLifecycleEventProvider(kernelOutputRoot, blockingExecutor, ownsExecutor);
    }

    private static void assertLocalProviderAllowed() {
        if (KernelRuntimeProfiles.isProductionRuntime()) {
            throw new IllegalStateException(
                    "Local filesystem Kernel lifecycle provider is dev/test-only; production must inject DataCloudKernelLifecycleTruthSource");
        }
    }

    /**
     * Ingests lifecycle data for a specific ProductUnit from local Kernel output.
     *
     * @param productUnitId the ProductUnit ID to ingest data for
     * @return promise resolving to the ingested lifecycle data map
     */
    public Promise<Map<String, Object>> ingestProductUnitLifecycle(@NotNull String productUnitId) {
        return provider.ingestProductUnitLifecycle(productUnitId);
    }

    /**
     * Ingests lifecycle data for all ProductUnits from local Kernel output.
     *
     * @return promise resolving to a list of lifecycle data maps for all ProductUnits
     */
    public Promise<java.util.List<Map<String, Object>>> ingestAllProductUnitLifecycles() {
        return provider.ingestAllProductUnitLifecycles();
    }

    /**
     * Checks if Kernel output data exists for a ProductUnit.
     *
     * @param productUnitId the ProductUnit ID to check
     * @return promise resolving to true if data exists, false otherwise
     */
    public Promise<Boolean> hasProductUnitLifecycleData(@NotNull String productUnitId) {
        return provider.hasProductUnitLifecycleData(productUnitId);
    }

    @Override
    public void close() {
        try {
            provider.close();
        } catch (Exception e) {
            log.warn("Failed to close Kernel lifecycle provider", e);
        }
    }

    /**
     * Provider interface for ingesting Kernel lifecycle events from different truth sources.
     */
    public interface KernelLifecycleEventProvider extends AutoCloseable {
        Promise<Map<String, Object>> ingestProductUnitLifecycle(@NotNull String productUnitId);

        Promise<List<Map<String, Object>>> ingestAllProductUnitLifecycles();

        Promise<Boolean> hasProductUnitLifecycleData(@NotNull String productUnitId);

        @Override
        default void close() {
            // default no-op
        }
    }

    private static final class TruthSourceKernelLifecycleEventProvider implements KernelLifecycleEventProvider {

        private final KernelLifecycleTruthSource truthSource;

        private TruthSourceKernelLifecycleEventProvider(@NotNull KernelLifecycleTruthSource truthSource) {
            this.truthSource = truthSource;
        }

        @Override
        public Promise<Map<String, Object>> ingestProductUnitLifecycle(@NotNull String productUnitId) {
            return truthSource.getProductUnitLifecycleData(productUnitId);
        }

        @Override
        public Promise<List<Map<String, Object>>> ingestAllProductUnitLifecycles() {
            return truthSource.listAllProductUnitLifecycleData();
        }

        @Override
        public Promise<Boolean> hasProductUnitLifecycleData(@NotNull String productUnitId) {
            return truthSource.hasLifecycleData(productUnitId);
        }
    }

    private static final class LocalFilesystemKernelLifecycleEventProvider implements KernelLifecycleEventProvider {

        private final ObjectMapper objectMapper;
        private final Path kernelOutputRoot;
        private final Executor blockingExecutor;
        @Nullable
        private final ExecutorService ownedExecutor;

        private LocalFilesystemKernelLifecycleEventProvider(
                @NotNull Path kernelOutputRoot,
                @NotNull Executor blockingExecutor,
                boolean ownsExecutor
        ) {
            this.objectMapper = new ObjectMapper();
            this.kernelOutputRoot = kernelOutputRoot;
            this.blockingExecutor = blockingExecutor;
            this.ownedExecutor = ownsExecutor && blockingExecutor instanceof ExecutorService executorService
                    ? executorService
                    : null;
        }

        @Override
        public Promise<Map<String, Object>> ingestProductUnitLifecycle(@NotNull String productUnitId) {
            return Promise.ofBlocking(blockingExecutor, () -> {
                Path productUnitPath = kernelOutputRoot.resolve("out/products").resolve(productUnitId);

                if (!Files.exists(productUnitPath)) {
                    log.warn("Kernel output directory does not exist for ProductUnit: {}", productUnitId);
                    return Map.of("productUnitId", productUnitId, "status", "not_found", "truthSource", "local-filesystem");
                }

                Map<String, Object> lifecycleData = new HashMap<>();
                lifecycleData.put("productUnitId", productUnitId);
                lifecycleData.put("status", "found");
                lifecycleData.put("truthSource", "local-filesystem");

                readJsonFile(productUnitPath.resolve("lifecycle-plan.json"))
                        .ifPresent(data -> lifecycleData.put("lifecyclePlan", data));
                readJsonFile(productUnitPath.resolve("lifecycle-result.json"))
                        .ifPresent(data -> lifecycleData.put("lifecycleResult", data));
                readJsonFile(productUnitPath.resolve("artifacts.json"))
                        .ifPresent(data -> lifecycleData.put("artifacts", data));
                readJsonFile(productUnitPath.resolve("deployment.json"))
                        .ifPresent(data -> lifecycleData.put("deployment", data));
                readJsonFile(productUnitPath.resolve("health-snapshot.json"))
                        .ifPresent(data -> lifecycleData.put("healthSnapshot", data));
                readJsonFile(productUnitPath.resolve("gates.json"))
                        .ifPresent(data -> lifecycleData.put("gates", data));

                log.info("Ingested lifecycle data for ProductUnit: {}", productUnitId);
                return lifecycleData;
            });
        }

        @Override
        public Promise<List<Map<String, Object>>> ingestAllProductUnitLifecycles() {
            return Promise.ofBlocking(blockingExecutor, () -> {
                Path productsPath = kernelOutputRoot.resolve("out/products");

                if (!Files.exists(productsPath)) {
                    log.warn("Kernel products output directory does not exist: {}", productsPath);
                    return List.of();
                }

                List<Map<String, Object>> allLifecycleData = new java.util.ArrayList<>();

                try (var stream = Files.list(productsPath)) {
                    stream.filter(Files::isDirectory)
                            .forEach(productUnitDir -> {
                                String productUnitId = productUnitDir.getFileName().toString();
                                try {
                                    Map<String, Object> data = new HashMap<>();
                                    data.put("productUnitId", productUnitId);
                                    data.put("status", "found");
                                    data.put("truthSource", "local-filesystem");

                                    Path healthSnapshotPath = productUnitDir.resolve("health-snapshot.json");
                                    if (Files.exists(healthSnapshotPath)) {
                                        readJsonFile(healthSnapshotPath).ifPresent(snapshot -> data.put("healthSnapshot", snapshot));
                                    }

                                    Path lifecycleResultPath = productUnitDir.resolve("lifecycle-result.json");
                                    if (Files.exists(lifecycleResultPath)) {
                                        readJsonFile(lifecycleResultPath).ifPresent(result -> data.put("lifecycleResult", result));
                                    }

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
                                    errorData.put("truthSource", "local-filesystem");
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

        @Override
        public Promise<Boolean> hasProductUnitLifecycleData(@NotNull String productUnitId) {
            return Promise.ofBlocking(blockingExecutor, () -> {
                Path productUnitPath = kernelOutputRoot.resolve("out/products").resolve(productUnitId);
                return Files.exists(productUnitPath) && Files.isDirectory(productUnitPath);
            });
        }

        @Override
        public void close() {
            if (ownedExecutor != null) {
                ownedExecutor.shutdown();
            }
        }

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
