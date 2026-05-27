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

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Local filesystem implementation of {@link KernelLifecycleTruthSource}.
 *
 * <p>Reads public Kernel manifest files from the local filesystem under
 * {@code .kernel/out/products/<productUnitId>/}. Intended for development,
 * CI, and local pilot environments where a Kernel runtime writes output to disk.
 *
 * <p><b>Files read (all public Kernel contracts)</b></p>
 * <ul>
 *   <li>{@code lifecycle-plan.json}</li>
 *   <li>{@code lifecycle-result.json}</li>
 *   <li>{@code gate-result-manifest.json}</li>
 *   <li>{@code artifact-manifest.json}</li>
 *   <li>{@code deployment-manifest.json}</li>
 *   <li>{@code verify-health-report.json}</li>
 *   <li>{@code lifecycle-health-snapshot.json}</li>
 * </ul>
 *
 * <p><b>Constraints</b></p>
 * <ul>
 *   <li>Does NOT read stdout/stderr logs.</li>
 *   <li>Does NOT write to Kernel directories or registry files.</li>
 *   <li>All file I/O is dispatched to a blocking executor via {@code Promise.ofBlocking}.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Local filesystem Kernel lifecycle truth source for development environments
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class LocalKernelManifestTruthSource implements KernelLifecycleTruthSource {

    private static final Logger LOG = LoggerFactory.getLogger(LocalKernelManifestTruthSource.class);

    private final KernelLifecycleEventIngestService ingestService;

    /**
     * Creates a dev/test-only local manifest truth source using default local Kernel output.
     */
    public static LocalKernelManifestTruthSource forLocalDevelopment() {
        return new LocalKernelManifestTruthSource(KernelLifecycleEventIngestService.forLocalDevelopment());
    }

    /**
     * Creates a dev/test-only local manifest truth source using explicit ingest wiring.
     *
     * @param ingestService ingest service reading local Kernel output files
     */
    public static LocalKernelManifestTruthSource forLocalDevelopment(@NotNull KernelLifecycleEventIngestService ingestService) {
        return new LocalKernelManifestTruthSource(ingestService);
    }

    /**
     * Constructs with the default {@link KernelLifecycleEventIngestService}.
     * Uses {@code .kernel} as the output root.
     */
    @Deprecated(since = "2026-05", forRemoval = false)
    public LocalKernelManifestTruthSource() {
        this(KernelLifecycleEventIngestService.forLocalDevelopment());
    }

    /**
     * Constructs with a custom ingest service.
     *
     * @param ingestService the ingest service that reads Kernel manifest files
     */
    @Deprecated(since = "2026-05", forRemoval = false)
    public LocalKernelManifestTruthSource(@NotNull KernelLifecycleEventIngestService ingestService) {
        this.ingestService = ingestService;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to {@link KernelLifecycleEventIngestService#ingestProductUnitLifecycle}
     * to read and merge public manifest files for the given ProductUnit.
     */
    @Override
    public Promise<Map<String, Object>> getProductUnitLifecycleData(@NotNull String productUnitId) {
        LOG.debug("Reading local manifest data for productUnitId={}", productUnitId);
        return ingestService.ingestProductUnitLifecycle(productUnitId)
                .map(data -> {
                    if (data == null || data.isEmpty()) {
                        LOG.warn("No local manifest data found for productUnitId={}", productUnitId);
                        Map<String, Object> empty = new HashMap<>();
                        empty.put("productUnitId", productUnitId);
                        empty.put("status", "unknown");
                        return empty;
                    }
                    return data;
                });
    }

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to {@link KernelLifecycleEventIngestService#ingestAllProductUnitLifecycles}
     * to enumerate and read manifests for all ProductUnits.
     */
    @Override
    public Promise<List<Map<String, Object>>> listAllProductUnitLifecycleData() {
        LOG.debug("Reading local manifest data for all ProductUnits");
        return ingestService.ingestAllProductUnitLifecycles()
                .map(dataList -> {
                    if (dataList == null) {
                        return List.of();
                    }
                    List<Map<String, Object>> result = new ArrayList<>(dataList.size());
                    for (Map<String, Object> data : dataList) {
                        if (data != null && !data.isEmpty()) {
                            result.add(data);
                        }
                    }
                    return result;
                });
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns the list of ProductUnit IDs discovered on the local filesystem
     * by scanning the {@code .kernel/out/products/} directory.
     */
    @Override
    public Promise<List<String>> listProductUnitIds() {
        return listAllProductUnitLifecycleData()
                .map(dataList -> {
                    List<String> ids = new ArrayList<>(dataList.size());
                    for (Map<String, Object> data : dataList) {
                        Object id = data.get("productUnitId");
                        if (id instanceof String s && !s.isBlank()) {
                            ids.add(s);
                        }
                    }
                    return ids;
                });
    }

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to {@link KernelLifecycleEventIngestService#hasProductUnitLifecycleData}.
     */
    @Override
    public Promise<Boolean> hasLifecycleData(@NotNull String productUnitId) {
        return ingestService.hasProductUnitLifecycleData(productUnitId);
    }
}
