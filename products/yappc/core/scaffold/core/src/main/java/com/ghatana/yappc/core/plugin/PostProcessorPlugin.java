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

import java.nio.file.Path;
import java.util.List;

/**
 * Plugin interface for post-processing generated files.
 *
 * @doc.type interface
 * @doc.purpose Post-processor plugin
 * @doc.layer platform
 * @doc.pattern Plugin SPI
 */
public interface PostProcessorPlugin extends YappcPlugin {

    /**
     * Post-processes generated files.
     *
     * @param projectPath    path to project
     * @param generatedFiles list of generated file paths
     * @return post-processing result
     * @throws PluginException if post-processing fails
     */
    PostProcessResult process(Path projectPath, List<Path> generatedFiles) throws PluginException;

    /**
     * Returns the file patterns this processor handles.
     *
     * @return list of glob patterns (e.g., "*.java", "*.ts")
     */
    List<String> getFilePatterns();

    /**
     * Returns the execution order priority (lower runs first).
     *
     * @return priority value
     */
    int getPriority();

    /**
     * Post-processing result.
     */
    record PostProcessResult(
            int filesProcessed,
            int filesModified,
            List<String> messages,
            boolean success) {

        public static PostProcessResult success(int processed, int modified) {
            return new PostProcessResult(processed, modified, List.of(), true);
        }

        public static PostProcessResult failure(String message) {
            return new PostProcessResult(0, 0, List.of(message), false);
        }
    }
}
