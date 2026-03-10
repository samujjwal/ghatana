/*
 * Copyright (c) 2024 Ghatana, Inc.
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
    void testSuccessfulHookExecution(@TempDir Path tempDir) {
        DefaultHookExecutor executor = new DefaultHookExecutor();

        // Use allowed executables: git --version always exits 0 and prints output
        List<String> hooks = List.of("git --version", "git --version");
        Map<String, Object> context = Map.of("test", "value");

        HookExecutor.HookExecutionResult result =
                executor.executePostGeneration(hooks, tempDir, context);

        assertTrue(result.successful());
        assertEquals(2, result.hookResults().size());
        assertTrue(result.errors().isEmpty());
        assertTrue(result.totalExecutionTimeMs() >= 0);

        for (HookExecutor.HookResult hookResult : result.hookResults()) {
            assertTrue(hookResult.successful());
            assertEquals(0, hookResult.exitCode());
            assertFalse(hookResult.stdout().isEmpty());
        }
    }

    @Test
    void testFailedHookExecution(@TempDir Path tempDir) {
        DefaultHookExecutor executor = new DefaultHookExecutor();

        // git status on a non-git directory exits non-zero; git --version always succeeds
        List<String> hooks = List.of("git status", "git --version");
        Map<String, Object> context = Map.of();

        HookExecutor.HookExecutionResult result =
                executor.executePostGeneration(hooks, tempDir, context);

        assertFalse(result.successful());
        assertEquals(2, result.hookResults().size());
        assertFalse(result.errors().isEmpty());

        // First hook (git status on non-git dir) should fail
        assertFalse(result.hookResults().get(0).successful());
        assertNotEquals(0, result.hookResults().get(0).exitCode());

        // Second hook (git --version) should succeed
        assertTrue(result.hookResults().get(1).successful());
        assertEquals(0, result.hookResults().get(1).exitCode());
    }

    @Test
    void testYappcHookSimulation(@TempDir Path tempDir) {
        DefaultHookExecutor executor = new DefaultHookExecutor();

        List<String> hooks = List.of("yappc doctor", "yappc init --help");
        Map<String, Object> context = Map.of();

        HookExecutor.HookExecutionResult result =
                executor.executePreGeneration(hooks, tempDir, context);

        assertTrue(result.successful());
        assertEquals(2, result.hookResults().size());

        for (HookExecutor.HookResult hookResult : result.hookResults()) {
            assertTrue(hookResult.successful());
            assertEquals(0, hookResult.exitCode());
            assertFalse(hookResult.stdout().isEmpty());
        }
    }

    @Test
    void testEmptyHooksList(@TempDir Path tempDir) {
        DefaultHookExecutor executor = new DefaultHookExecutor();

        List<String> hooks = List.of();
        Map<String, Object> context = Map.of();

        HookExecutor.HookExecutionResult result =
                executor.executePostGeneration(hooks, tempDir, context);

        assertTrue(result.successful());
        assertEquals(0, result.hookResults().size());
        assertTrue(result.errors().isEmpty());
        assertEquals(0, result.totalExecutionTimeMs());
    }

    @Test
    void testNullHooksList(@TempDir Path tempDir) {
        DefaultHookExecutor executor = new DefaultHookExecutor();

        Map<String, Object> context = Map.of();

        HookExecutor.HookExecutionResult result =
                executor.executePostGeneration(null, tempDir, context);

        assertTrue(result.successful());
        assertEquals(0, result.hookResults().size());
        assertTrue(result.errors().isEmpty());
        assertEquals(0, result.totalExecutionTimeMs());
    }
}
