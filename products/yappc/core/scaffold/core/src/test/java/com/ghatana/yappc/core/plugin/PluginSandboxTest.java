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

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**

 * @doc.type class

 * @doc.purpose Handles plugin sandbox test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class PluginSandboxTest {

    @Test
    void testPermissiveSandbox() {
        Path workspace = Paths.get("/workspace");
        PluginSandbox sandbox = PluginSandbox.permissive(workspace);

        assertTrue(sandbox.isWriteAllowed(workspace.resolve("file.txt")));
        assertTrue(sandbox.isWriteAllowed(workspace.resolve("subdir/file.txt")));
        assertEquals(Duration.ofMinutes(5), sandbox.timeout());
        assertFalse(sandbox.dryRunOnly());
        assertTrue(sandbox.allowNetworkAccess());
    }

    @Test
    void testRestrictiveSandbox() {
        Path allowed = Paths.get("/workspace");
        PluginSandbox sandbox = PluginSandbox.restrictive(List.of(allowed));

        assertTrue(sandbox.isWriteAllowed(allowed.resolve("file.txt")));
        assertFalse(sandbox.isWriteAllowed(Paths.get("/etc/passwd")));
        assertFalse(sandbox.isWriteAllowed(Paths.get("/sys/config")));
        assertEquals(Duration.ofMinutes(1), sandbox.timeout());
        assertFalse(sandbox.dryRunOnly());
        assertFalse(sandbox.allowNetworkAccess());
    }

    @Test
    void testDryRunSandbox() {
        PluginSandbox sandbox = PluginSandbox.dryRun();

        assertFalse(sandbox.isWriteAllowed(Paths.get("/any/path")));
        assertEquals(Duration.ofSeconds(30), sandbox.timeout());
        assertTrue(sandbox.dryRunOnly());
        assertFalse(sandbox.allowNetworkAccess());
    }

    @Test
    void testIsWriteAllowedWithDeniedPaths() {
        Path workspace = Paths.get("/workspace");
        Path denied = workspace.resolve("restricted");

        PluginSandbox sandbox = new PluginSandbox(
                List.of(workspace),
                List.of(denied),
                Duration.ofMinutes(1),
                false,
                true);

        assertTrue(sandbox.isWriteAllowed(workspace.resolve("file.txt")));
        assertFalse(sandbox.isWriteAllowed(denied.resolve("file.txt")));
    }

    @Test
    void testIsWriteAllowedOutsideAllowedPaths() {
        Path workspace = Paths.get("/workspace");
        PluginSandbox sandbox = new PluginSandbox(
                List.of(workspace),
                List.of(),
                Duration.ofMinutes(1),
                false,
                true);

        assertTrue(sandbox.isWriteAllowed(workspace.resolve("file.txt")));
        assertFalse(sandbox.isWriteAllowed(Paths.get("/other/file.txt")));
    }

    @Test
    void testDryRunBlocksAllWrites() {
        Path workspace = Paths.get("/workspace");
        PluginSandbox sandbox = new PluginSandbox(
                List.of(workspace),
                List.of(),
                Duration.ofMinutes(1),
                true, // dry run
                true);

        assertFalse(sandbox.isWriteAllowed(workspace.resolve("file.txt")));
    }
}
