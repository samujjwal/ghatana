/*
 * Copyright (c) 2026 Ghatana Platform Contributors
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

package com.ghatana.yappc.cli;

import com.ghatana.yappc.core.telemetry.TelemetryManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that {@link CreateCommand} correctly handles the {@code kernel-product-unit} target type.
 *
 * <p>Verifies that:<ul>
 *   <li>The command succeeds (exit code 0) with a valid project name and output path.</li>
 *   <li>A ProductUnitIntent YAML file is written at the specified path.</li>
 *   <li>The command does NOT touch the Kernel registry or GitHub workflow directories.</li>
 * </ul>
 */
@DisplayName("CreateCommand — kernel-product-unit target")
class CreateCommandKernelProductUnitTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // Disable telemetry to avoid ActiveJ Reactor requirement in unit tests
        System.setProperty("yappc.telemetry.disabled", "true");
        TelemetryManager.disable();
    }

    @AfterEach
    void tearDown() {
        TelemetryManager.shutdown();
    }

    @Test
    @DisplayName("exits 0 and writes ProductUnitIntent YAML for kernel-product-unit target")
    void writesIntentFileForKernelProductUnitTarget() {
        Path intentOutput = tempDir.resolve("product-unit-intent.yaml");

        int exitCode = new CommandLine(new CreateCommand()).execute(
                "digital-marketing",
                "--target", "kernel-product-unit",
                "--intent-output", intentOutput.toString(),
                "--workspace-id", "workspace-001",
                "--project-id", "digital-marketing",
                "--surface", "backend-api",
                "--lifecycle-profile", "standard-web-api-product"
        );

        assertThat(exitCode).isEqualTo(0);
        assertThat(intentOutput).exists();
        assertThat(intentOutput).isNotEmptyFile();
    }

    @Test
    @DisplayName("intent YAML contains required ProductUnit schema fields")
    void intentYamlContainsRequiredFields() throws Exception {
        Path intentOutput = tempDir.resolve("product-unit-intent.yaml");

        int exitCode = new CommandLine(new CreateCommand()).execute(
                "campaign-management",
                "--target", "kernel-product-unit",
                "--intent-output", intentOutput.toString(),
                "--runtime-provider", "ghatana-file-registry",
                "--workspace-id", "workspace-001",
                "--project-id", "campaign-management",
                "--surface", "backend-api",
                "--lifecycle-profile", "standard-web-api-product"
        );

        assertThat(exitCode).isEqualTo(0);
        String content = Files.readString(intentOutput);
        assertThat(content).containsIgnoringCase("schemaVersion");
        assertThat(content).containsIgnoringCase("intentId");
    }

    @Test
    @DisplayName("does not mutate Kernel registry or GitHub workflow files")
    void doesNotCreateWorkflowFilesOrMutateRegistry() throws Exception {
        Path workspaceRoot = tempDir.resolve("workspace");
        Files.createDirectories(workspaceRoot);
        Path intentOutput = workspaceRoot.resolve("product-unit-intent.yaml");

        int exitCode = new CommandLine(new CreateCommand()).execute(
                "test-product",
                "--target", "kernel-product-unit",
                "--intent-output", intentOutput.toString(),
                "--workspace-id", "workspace-001",
                "--project-id", "test-product",
                "--surface", "backend-api",
                "--lifecycle-profile", "standard-web-api-product"
        );

        assertThat(exitCode).isEqualTo(0);
        // Kernel registry and GitHub workflows must NOT be created by yappc create
        assertThat(workspaceRoot.resolve(".github/workflows")).doesNotExist();
        assertThat(workspaceRoot.resolve("kernel-product.yaml")).doesNotExist();
    }

    @Test
    @DisplayName("fails with exit code 1 when project name is missing for kernel-product-unit")
    void failsWithExitCode1WhenProjectNameMissing() {
        Path intentOutput = tempDir.resolve("intent.yaml");

        int exitCode = new CommandLine(new CreateCommand()).execute(
                "--target", "kernel-product-unit",
                "--intent-output", intentOutput.toString()
        );

        assertThat(exitCode).isEqualTo(1);
        assertThat(intentOutput).doesNotExist();
    }

    @Test
    @DisplayName("fails when workspace ID is missing for kernel-product-unit")
    void failsWhenWorkspaceIdMissing() {
        Path intentOutput = tempDir.resolve("missing-workspace.yaml");

        int exitCode = new CommandLine(new CreateCommand()).execute(
                "digital-marketing",
                "--target", "kernel-product-unit",
                "--intent-output", intentOutput.toString(),
                "--project-id", "digital-marketing",
                "--surface", "backend-api",
                "--lifecycle-profile", "standard-web-api-product"
        );

        assertThat(exitCode).isEqualTo(1);
        assertThat(intentOutput).doesNotExist();
    }

    @Test
    @DisplayName("fails when surfaces are missing for kernel-product-unit")
    void failsWhenSurfacesMissing() {
        Path intentOutput = tempDir.resolve("missing-surface.yaml");

        int exitCode = new CommandLine(new CreateCommand()).execute(
                "digital-marketing",
                "--target", "kernel-product-unit",
                "--intent-output", intentOutput.toString(),
                "--workspace-id", "workspace-001",
                "--project-id", "digital-marketing",
                "--lifecycle-profile", "standard-web-api-product"
        );

        assertThat(exitCode).isEqualTo(1);
        assertThat(intentOutput).doesNotExist();
    }
}
