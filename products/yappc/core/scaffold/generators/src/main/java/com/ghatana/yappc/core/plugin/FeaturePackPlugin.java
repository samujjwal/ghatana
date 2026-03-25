/*
 * Copyright (c) 2025 Ghatana Platform Contributors
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

package com.ghatana.yappc.core.plugin;

import com.ghatana.yappc.core.featurepack.FeaturePackSpec;
import java.nio.file.Path;
import java.util.List;

/**
 * Plugin interface for feature pack operations.
 *
 * @doc.type interface
 * @doc.purpose Feature pack plugin
 * @doc.layer platform
 * @doc.pattern Plugin SPI
 */
public interface FeaturePackPlugin extends YappcPlugin {

    /**
     * Returns the feature packs provided by this plugin.
     *
     * @return list of feature pack specifications
     */
    List<FeaturePackSpec> getFeaturePacks();

    /**
     * Checks if a feature is compatible with a project.
     *
     * @param projectPath path to project
     * @param featureName feature name
     * @return true if compatible
     * @throws PluginException if check fails
     */
    boolean isCompatible(Path projectPath, String featureName) throws PluginException;

    /**
     * Returns the merge strategy for this feature.
     *
     * @param featureName feature name
     * @return merge strategy
     */
    MergeStrategy getMergeStrategy(String featureName);

    /**
     * Merge strategy for feature files.
     */
    enum MergeStrategy {
        REPLACE,
        MERGE,
        APPEND,
        SKIP_IF_EXISTS
    }
}
