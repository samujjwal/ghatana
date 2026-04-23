/*
 * Copyright (c) 2025 Ghatana Platform Contributors // GH-90000
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); // GH-90000
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
    void testPermissiveSandbox() { // GH-90000
        Path workspace = Paths.get("/workspace");
        PluginSandbox sandbox = PluginSandbox.permissive(workspace); // GH-90000

        assertTrue(sandbox.isWriteAllowed(workspace.resolve("file.txt")));
        assertTrue(sandbox.isWriteAllowed(workspace.resolve("subdir/file.txt")));
        assertEquals(Duration.ofMinutes(5), sandbox.timeout()); // GH-90000
        assertFalse(sandbox.dryRunOnly()); // GH-90000
        assertTrue(sandbox.allowNetworkAccess()); // GH-90000
    }

    @Test
    void testRestrictiveSandbox() { // GH-90000
        Path allowed = Paths.get("/workspace");
        PluginSandbox sandbox = PluginSandbox.restrictive(List.of(allowed)); // GH-90000

        assertTrue(sandbox.isWriteAllowed(allowed.resolve("file.txt")));
        assertFalse(sandbox.isWriteAllowed(Paths.get("/etc/passwd")));
        assertFalse(sandbox.isWriteAllowed(Paths.get("/sys/config")));
        assertEquals(Duration.ofMinutes(1), sandbox.timeout()); // GH-90000
        assertFalse(sandbox.dryRunOnly()); // GH-90000
        assertFalse(sandbox.allowNetworkAccess()); // GH-90000
    }

    @Test
    void testDryRunSandbox() { // GH-90000
        PluginSandbox sandbox = PluginSandbox.dryRun(); // GH-90000

        assertFalse(sandbox.isWriteAllowed(Paths.get("/any/path")));
        assertEquals(Duration.ofSeconds(30), sandbox.timeout()); // GH-90000
        assertTrue(sandbox.dryRunOnly()); // GH-90000
        assertFalse(sandbox.allowNetworkAccess()); // GH-90000
    }

    @Test
    void testIsWriteAllowedWithDeniedPaths() { // GH-90000
        Path workspace = Paths.get("/workspace");
        Path denied = workspace.resolve("restricted");

        PluginSandbox sandbox = new PluginSandbox( // GH-90000
                List.of(workspace), // GH-90000
                List.of(denied), // GH-90000
                Duration.ofMinutes(1), // GH-90000
                false,
                true);

        assertTrue(sandbox.isWriteAllowed(workspace.resolve("file.txt")));
        assertFalse(sandbox.isWriteAllowed(denied.resolve("file.txt")));
    }

    @Test
    void testIsWriteAllowedOutsideAllowedPaths() { // GH-90000
        Path workspace = Paths.get("/workspace");
        PluginSandbox sandbox = new PluginSandbox( // GH-90000
                List.of(workspace), // GH-90000
                List.of(), // GH-90000
                Duration.ofMinutes(1), // GH-90000
                false,
                true);

        assertTrue(sandbox.isWriteAllowed(workspace.resolve("file.txt")));
        assertFalse(sandbox.isWriteAllowed(Paths.get("/other/file.txt")));
    }

    @Test
    void testDryRunBlocksAllWrites() { // GH-90000
        Path workspace = Paths.get("/workspace");
        PluginSandbox sandbox = new PluginSandbox( // GH-90000
                List.of(workspace), // GH-90000
                List.of(), // GH-90000
                Duration.ofMinutes(1), // GH-90000
                true, // dry run
                true);

        assertFalse(sandbox.isWriteAllowed(workspace.resolve("file.txt")));
    }
}
