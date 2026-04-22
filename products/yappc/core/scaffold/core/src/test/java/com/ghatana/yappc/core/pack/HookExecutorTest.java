/*
 * Copyright (c) 2024 Ghatana, Inc. // GH-90000
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

package com.ghatana.yappc.core.pack;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for hook execution functionality. Week 2, Day 9 deliverable tests.
 * @doc.type class
 * @doc.purpose Handles hook executor test operations
 * @doc.layer core
 * @doc.pattern Test
*/
class HookExecutorTest {

    @Test
    void testSuccessfulHookExecution(@TempDir Path tempDir) { // GH-90000
        DefaultHookExecutor executor = new DefaultHookExecutor(); // GH-90000

        // Use allowed executables: git --version always exits 0 and prints output
        List<String> hooks = List.of("git --version", "git --version"); // GH-90000
        Map<String, Object> context = Map.of("test", "value"); // GH-90000

        HookExecutor.HookExecutionResult result =
                executor.executePostGeneration(hooks, tempDir, context); // GH-90000

        assertTrue(result.successful()); // GH-90000
        assertEquals(2, result.hookResults().size()); // GH-90000
        assertTrue(result.errors().isEmpty()); // GH-90000
        assertTrue(result.totalExecutionTimeMs() >= 0); // GH-90000

        for (HookExecutor.HookResult hookResult : result.hookResults()) { // GH-90000
            assertTrue(hookResult.successful()); // GH-90000
            assertEquals(0, hookResult.exitCode()); // GH-90000
            assertFalse(hookResult.stdout().isEmpty()); // GH-90000
        }
    }

    @Test
    void testFailedHookExecution(@TempDir Path tempDir) { // GH-90000
        DefaultHookExecutor executor = new DefaultHookExecutor(); // GH-90000

        // git status on a non-git directory exits non-zero; git --version always succeeds
        List<String> hooks = List.of("git status", "git --version"); // GH-90000
        Map<String, Object> context = Map.of(); // GH-90000

        HookExecutor.HookExecutionResult result =
                executor.executePostGeneration(hooks, tempDir, context); // GH-90000

        assertFalse(result.successful()); // GH-90000
        assertEquals(2, result.hookResults().size()); // GH-90000
        assertFalse(result.errors().isEmpty()); // GH-90000

        // First hook (git status on non-git dir) should fail // GH-90000
        assertFalse(result.hookResults().get(0).successful()); // GH-90000
        assertNotEquals(0, result.hookResults().get(0).exitCode()); // GH-90000

        // Second hook (git --version) should succeed // GH-90000
        assertTrue(result.hookResults().get(1).successful()); // GH-90000
        assertEquals(0, result.hookResults().get(1).exitCode()); // GH-90000
    }

    @Test
    void testYappcHookSimulation(@TempDir Path tempDir) { // GH-90000
        DefaultHookExecutor executor = new DefaultHookExecutor(); // GH-90000

        List<String> hooks = List.of("yappc doctor", "yappc init --help"); // GH-90000
        Map<String, Object> context = Map.of(); // GH-90000

        HookExecutor.HookExecutionResult result =
                executor.executePreGeneration(hooks, tempDir, context); // GH-90000

        assertTrue(result.successful()); // GH-90000
        assertEquals(2, result.hookResults().size()); // GH-90000

        for (HookExecutor.HookResult hookResult : result.hookResults()) { // GH-90000
            assertTrue(hookResult.successful()); // GH-90000
            assertEquals(0, hookResult.exitCode()); // GH-90000
            assertFalse(hookResult.stdout().isEmpty()); // GH-90000
        }
    }

    @Test
    void testEmptyHooksList(@TempDir Path tempDir) { // GH-90000
        DefaultHookExecutor executor = new DefaultHookExecutor(); // GH-90000

        List<String> hooks = List.of(); // GH-90000
        Map<String, Object> context = Map.of(); // GH-90000

        HookExecutor.HookExecutionResult result =
                executor.executePostGeneration(hooks, tempDir, context); // GH-90000

        assertTrue(result.successful()); // GH-90000
        assertEquals(0, result.hookResults().size()); // GH-90000
        assertTrue(result.errors().isEmpty()); // GH-90000
        assertEquals(0, result.totalExecutionTimeMs()); // GH-90000
    }

    @Test
    void testNullHooksList(@TempDir Path tempDir) { // GH-90000
        DefaultHookExecutor executor = new DefaultHookExecutor(); // GH-90000

        Map<String, Object> context = Map.of(); // GH-90000

        HookExecutor.HookExecutionResult result =
                executor.executePostGeneration(null, tempDir, context); // GH-90000

        assertTrue(result.successful()); // GH-90000
        assertEquals(0, result.hookResults().size()); // GH-90000
        assertTrue(result.errors().isEmpty()); // GH-90000
        assertEquals(0, result.totalExecutionTimeMs()); // GH-90000
    }
}
