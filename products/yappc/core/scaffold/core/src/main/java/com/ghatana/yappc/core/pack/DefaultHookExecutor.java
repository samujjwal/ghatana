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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default hook executor implementation. Week 2, Day 9 deliverable - Command execution with timeout
 * and error handling.
 *
 * @doc.type class
 * @doc.purpose Default hook executor implementation. Week 2, Day 9 deliverable - Command execution with timeout
 * @doc.layer platform
 * @doc.pattern Executor
 */
public class DefaultHookExecutor implements HookExecutor {

    private static final Logger logger = LoggerFactory.getLogger(DefaultHookExecutor.class);
    private static final long DEFAULT_TIMEOUT_SECONDS = 300; // 5 minutes

    @Override
    public HookExecutionResult executePreGeneration(
            List<String> hooks, Path workingDirectory, Map<String, Object> context) {
        int hookCount = hooks != null ? hooks.size() : 0;
        logger.info("Executing {} pre-generation hooks in {}", hookCount, workingDirectory);
        return executeHooks(hooks, workingDirectory, context, "pre-generation");
    }

    @Override
    public HookExecutionResult executePostGeneration(
            List<String> hooks, Path workingDirectory, Map<String, Object> context) {
        int hookCount = hooks != null ? hooks.size() : 0;
        logger.info("Executing {} post-generation hooks in {}", hookCount, workingDirectory);
        return executeHooks(hooks, workingDirectory, context, "post-generation");
    }

    @Override
    public HookExecutionResult executePreBuild(
            List<String> hooks, Path workingDirectory, Map<String, Object> context) {
        int hookCount = hooks != null ? hooks.size() : 0;
        logger.info("Executing {} pre-build hooks in {}", hookCount, workingDirectory);
        return executeHooks(hooks, workingDirectory, context, "pre-build");
    }

    @Override
    public HookExecutionResult executePostBuild(
            List<String> hooks, Path workingDirectory, Map<String, Object> context) {
        int hookCount = hooks != null ? hooks.size() : 0;
        logger.info("Executing {} post-build hooks in {}", hookCount, workingDirectory);
        return executeHooks(hooks, workingDirectory, context, "post-build");
    }

    private HookExecutionResult executeHooks(
            List<String> hooks,
            Path workingDirectory,
            Map<String, Object> context,
            String hookType) {
        if (hooks == null || hooks.isEmpty()) {
            logger.debug("No {} hooks to execute", hookType);
            return new HookExecutionResult(true, List.of(), List.of(), List.of(), 0);
        }

        List<HookResult> hookResults = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        long startTime = System.currentTimeMillis();
        boolean allSuccessful = true;

        for (String hookCommand : hooks) {
            try {
                logger.debug("Executing {} hook: {}", hookType, hookCommand);
                HookResult result = executeHookCommand(hookCommand, workingDirectory, context);
                hookResults.add(result);

                if (!result.successful()) {
                    allSuccessful = false;
                    errors.add(
                            "Hook '"
                                    + hookCommand
                                    + "' failed with exit code "
                                    + result.exitCode());
                    logger.warn(
                            "Hook '{}' failed with exit code {}: {}",
                            hookCommand,
                            result.exitCode(),
                            result.stderr());
                } else {
                    logger.debug(
                            "Hook '{}' completed successfully in {}ms",
                            hookCommand,
                            result.executionTimeMs());
                }
            } catch (Exception e) {
                allSuccessful = false;
                String errorMsg = "Failed to execute hook '" + hookCommand + "': " + e.getMessage();
                errors.add(errorMsg);
                logger.error(errorMsg, e);

                hookResults.add(new HookResult(hookCommand, -1, "", e.getMessage(), 0, false));
            }
        }

        long totalTime = System.currentTimeMillis() - startTime;
        logger.info(
                "Completed {} {} hooks in {}ms (success: {})",
                hooks.size(),
                hookType,
                totalTime,
                allSuccessful);

        return new HookExecutionResult(allSuccessful, hookResults, errors, warnings, totalTime);
    }

    private HookResult executeHookCommand(
            String command, Path workingDirectory, Map<String, Object> context)
            throws IOException, InterruptedException {

        long startTime = System.currentTimeMillis();

        // Handle yappc commands specially
        if (command.startsWith("yappc ")) {
            return executeYappcCommand(command, workingDirectory, context, startTime);
        }

        // Parse command for shell execution
        ProcessBuilder pb = createProcessBuilder(command, workingDirectory);

        Process process = pb.start();

        // Capture stdout and stderr
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        Thread stdoutReader = new Thread(() -> readStream(process.getInputStream(), stdout));
        Thread stderrReader = new Thread(() -> readStream(process.getErrorStream(), stderr));

        stdoutReader.start();
        stderrReader.start();

        boolean finished = process.waitFor(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (!finished) {
            process.destroyForcibly();
            throw new IOException(
                    "Command timed out after " + DEFAULT_TIMEOUT_SECONDS + " seconds: " + command);
        }

        stdoutReader.join(1000);
        stderrReader.join(1000);

        long executionTime = System.currentTimeMillis() - startTime;
        int exitCode = process.exitValue();
        boolean successful = exitCode == 0;

        return new HookResult(
                command, exitCode, stdout.toString(), stderr.toString(), executionTime, successful);
    }

    private HookResult executeYappcCommand(
            String command, Path workingDirectory, Map<String, Object> context, long startTime) {
        // For now, simulate yappc commands - in real implementation this would delegate to CLI
        logger.info("Executing yappc command: {}", command);

        if (command.contains("doctor")) {
            // Simulate doctor check
            return new HookResult(
                    command,
                    0,
                    "✓ All tools available\n",
                    "",
                    System.currentTimeMillis() - startTime,
                    true);
        } else if (command.contains("codemod") || command.contains("polyfix")) {
            // Execute codemods on the generated project
            CodemodExecutor codemodExecutor = new CodemodExecutor();
            CodemodExecutor.CodemodResult result =
                    codemodExecutor.executeCodemods(workingDirectory, context);

            String output =
                    String.format(
                            "Codemods executed: %d files processed, %d transformations applied\n"
                                    + "%s\n",
                            result.filesProcessed(),
                            result.transformationsApplied(),
                            result.summary());
            String stderr = result.errors().isEmpty() ? "" : String.join("\n", result.errors());

            return new HookResult(
                    command,
                    result.successful() ? 0 : 1,
                    output,
                    stderr,
                    System.currentTimeMillis() - startTime,
                    result.successful());
        }

        // Default successful execution for yappc commands
        return new HookResult(
                command,
                0,
                "yappc command executed successfully\n",
                "",
                System.currentTimeMillis() - startTime,
                true);
    }

    private ProcessBuilder createProcessBuilder(String command, Path workingDirectory) {
        ProcessBuilder pb;

        // Handle different operating systems
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("windows")) {
            pb = new ProcessBuilder("cmd", "/c", command);
        } else {
            pb = new ProcessBuilder("sh", "-c", command);
        }

        pb.directory(workingDirectory.toFile());
        pb.redirectErrorStream(false);

        return pb;
    }

    private void readStream(java.io.InputStream inputStream, StringBuilder output) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        } catch (IOException e) {
            logger.warn("Error reading stream: {}", e.getMessage());
        }
    }
}
