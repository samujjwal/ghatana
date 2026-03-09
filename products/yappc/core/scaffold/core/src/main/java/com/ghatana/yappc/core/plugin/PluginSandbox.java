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
import java.time.Duration;
import java.util.List;

/**
 * Sandbox configuration for plugin execution boundaries.
 *
 * @doc.type record
 * @doc.purpose Plugin execution sandbox
 * @doc.layer platform
 * @doc.pattern Security Boundary
 */
public record PluginSandbox(
        List<Path> allowedWritePaths,
        List<Path> deniedWritePaths,
        Duration timeout,
        boolean dryRunOnly,
        boolean allowNetworkAccess) {

    /**
     * Checks if a path is allowed for writing.
     *
     * @param path path to check
     * @return true if write is allowed
     */
    public boolean isWriteAllowed(Path path) {
        if (dryRunOnly) {
            return false;
        }

        Path normalized = path.normalize().toAbsolutePath();

        // Check denied paths first
        for (Path denied : deniedWritePaths) {
            if (normalized.startsWith(denied)) {
                return false;
            }
        }

        // Check allowed paths
        for (Path allowed : allowedWritePaths) {
            if (normalized.startsWith(allowed)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Creates a permissive sandbox for development.
     *
     * @param workspacePath workspace root
     * @return permissive sandbox
     */
    public static PluginSandbox permissive(Path workspacePath) {
        return new PluginSandbox(
                List.of(workspacePath),
                List.of(),
                Duration.ofMinutes(5),
                false,
                true);
    }

    /**
     * Creates a restrictive sandbox for production.
     *
     * @param allowedPaths allowed write paths
     * @return restrictive sandbox
     */
    public static PluginSandbox restrictive(List<Path> allowedPaths) {
        return new PluginSandbox(
                allowedPaths,
                List.of(Path.of("/etc"), Path.of("/sys"), Path.of("/proc")),
                Duration.ofMinutes(1),
                false,
                false);
    }

    /**
     * Creates a dry-run only sandbox.
     *
     * @return dry-run sandbox
     */
    public static PluginSandbox dryRun() {
        return new PluginSandbox(
                List.of(),
                List.of(),
                Duration.ofSeconds(30),
                true,
                false);
    }
}
