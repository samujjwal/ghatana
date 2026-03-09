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

package com.ghatana.yappc.core.rca;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Day 26: Build log normalizer interface for different build tools. Converts raw build output into
 * standardized NormalizedBuildLog format.
 *
 * @doc.type interface
 * @doc.purpose Day 26: Build log normalizer interface for different build tools. Converts raw build output into
 * @doc.layer platform
 * @doc.pattern Component
 */
public interface BuildLogNormalizer {

    /**
     * Parse and normalize raw build log content
     *
     * @param rawLog Raw build output as string
     * @return Normalized build log structure
     * @throws IOException if parsing fails
     */
    NormalizedBuildLog normalize(String rawLog) throws IOException;

    /**
     * Parse and normalize build log from file
     *
     * @param logFile Path to build log file
     * @return Normalized build log structure
     * @throws IOException if file reading or parsing fails
     */
    NormalizedBuildLog normalize(Path logFile) throws IOException;

    /**
     * Check if this normalizer can handle the given log content
     *
     * @param rawLog Raw build output
     * @return true if this normalizer can parse the log
     */
    boolean canHandle(String rawLog);

    /**
     * Get the build tool this normalizer handles
     *
     * @return The build tool type
     */
    NormalizedBuildLog.BuildTool getSupportedTool();
}
