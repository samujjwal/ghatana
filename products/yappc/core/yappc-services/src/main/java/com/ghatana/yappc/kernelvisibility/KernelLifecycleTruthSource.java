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

import java.util.List;
import java.util.Map;

/**
 * Port interface for reading Kernel lifecycle truth data.
 *
 * <p>Abstracts over the source of Kernel lifecycle data to allow different
 * implementations (local filesystem manifests, Data Cloud event stream, etc.)
 * without changing the health snapshot or recommendation logic.
 *
 * <p><b>Contract</b></p>
 * <ul>
 *   <li>Reads ONLY from public Kernel manifest files — never from stdout/stderr logs.</li>
 *   <li>Does NOT mutate Kernel registry files or lifecycle state.</li>
 *   <li>Returns empty/absent values when data is unavailable, never throws for "not found".</li>
 *   <li>All implementations must be non-blocking; I/O must be dispatched off the event loop.</li>
 * </ul>
 *
 * <p><b>Public manifest files that implementations may read</b></p>
 * <ul>
 *   <li>{@code lifecycle-plan.json} — the declared lifecycle plan</li>
 *   <li>{@code lifecycle-result.json} — the last lifecycle execution result</li>
 *   <li>{@code gate-result-manifest.json} — gate evaluation outcomes</li>
 *   <li>{@code artifact-manifest.json} — produced artifacts</li>
 *   <li>{@code deployment-manifest.json} — deployment state</li>
 *   <li>{@code verify-health-report.json} — post-deployment health verification</li>
 *   <li>{@code lifecycle-health-snapshot.json} — current lifecycle health snapshot</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Port for reading Kernel lifecycle truth data; enables multiple truth-source implementations
 * @doc.layer product
 * @doc.pattern Port
 */
public interface KernelLifecycleTruthSource {

    /**
     * Returns normalized lifecycle data for a single ProductUnit.
     *
     * <p>The returned map contains a merged view of all available public manifest data.
     * Returns an empty map when no data is found for the given {@code productUnitId}.
     *
     * @param productUnitId the ProductUnit identifier
     * @return promise resolving to the normalized lifecycle data map (never null, may be empty)
     */
    Promise<Map<String, Object>> getProductUnitLifecycleData(String productUnitId);

    /**
     * Returns normalized lifecycle data for all known ProductUnits.
     *
     * <p>Implementations should scan the backing store for all available ProductUnit IDs
     * and aggregate lifecycle data for each.
     *
     * @return promise resolving to a list of lifecycle data maps (one per ProductUnit)
     */
    Promise<List<Map<String, Object>>> listAllProductUnitLifecycleData();

    /**
     * Returns all known ProductUnit IDs available in this truth source.
     *
     * @return promise resolving to the list of ProductUnit IDs (never null, may be empty)
     */
    Promise<List<String>> listProductUnitIds();

    /**
     * Returns whether lifecycle data exists for the given ProductUnit.
     *
     * @param productUnitId the ProductUnit identifier
     * @return promise resolving to {@code true} when data is present
     */
    Promise<Boolean> hasLifecycleData(String productUnitId);
}
