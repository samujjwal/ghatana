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

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Hook execution service interface. Week 2, Day 9 deliverable - Post-generation hook execution
 * system.
 *
 * @doc.type interface
 * @doc.purpose Hook execution service interface. Week 2, Day 9 deliverable - Post-generation hook execution
 * @doc.layer platform
 * @doc.pattern Executor
 */
public interface HookExecutor {

    /**
     * Execute pre-generation hooks.
     *
     * @param hooks List of hook commands to execute
     * @param workingDirectory Directory to execute commands in
     * @param context Execution context variables
     * @return Hook execution result
     */
    HookExecutionResult executePreGeneration(
            List<String> hooks, Path workingDirectory, Map<String, Object> context);

    /**
     * Execute post-generation hooks.
     *
     * @param hooks List of hook commands to execute
     * @param workingDirectory Directory to execute commands in
     * @param context Execution context variables
     * @return Hook execution result
     */
    HookExecutionResult executePostGeneration(
            List<String> hooks, Path workingDirectory, Map<String, Object> context);

    /**
     * Execute pre-build hooks.
     *
     * @param hooks List of hook commands to execute
     * @param workingDirectory Directory to execute commands in
     * @param context Execution context variables
     * @return Hook execution result
     */
    HookExecutionResult executePreBuild(
            List<String> hooks, Path workingDirectory, Map<String, Object> context);

    /**
     * Execute post-build hooks.
     *
     * @param hooks List of hook commands to execute
     * @param workingDirectory Directory to execute commands in
     * @param context Execution context variables
     * @return Hook execution result
     */
    HookExecutionResult executePostBuild(
            List<String> hooks, Path workingDirectory, Map<String, Object> context);

    /**
 * Hook execution result. */
    record HookExecutionResult(
            boolean successful,
            List<HookResult> hookResults,
            List<String> errors,
            List<String> warnings,
            long totalExecutionTimeMs) {}

    /**
 * Individual hook execution result. */
    record HookResult(
            String command,
            int exitCode,
            String stdout,
            String stderr,
            long executionTimeMs,
            boolean successful) {}
}
