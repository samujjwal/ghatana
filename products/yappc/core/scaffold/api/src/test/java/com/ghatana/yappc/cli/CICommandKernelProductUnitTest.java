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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that {@link CICommand}'s {@code generate} subcommand correctly handles
 * the {@code kernel-product-unit} target type.
 *
 * <p>Verifies that:<ul>
 *   <li>The command exits 0 when {@code --target kernel-product-unit} is passed.</li>
 *   <li>No raw GitHub workflow files are generated — lifecycle CI is delegated to Kernel.</li>
 * </ul>
 */
@DisplayName("CICommand generate — kernel-product-unit target")
class CICommandKernelProductUnitTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("exits 0 without generating GitHub workflows for kernel-product-unit")
    void exitsZeroWithoutWorkflowsForKernelProductUnitTarget() {
        int exitCode = new CommandLine(new CICommand()).execute(
                "generate",
                "--target", "kernel-product-unit",
                "--path", tempDir.toString()
        );

        assertThat(exitCode).isEqualTo(0);
        assertThat(tempDir.resolve(".github/workflows")).doesNotExist();
    }

    @Test
    @DisplayName("does not create CI workflow files for kernel-product-unit target")
    void doesNotCreateWorkflowFilesForKernelProductUnitTarget() throws Exception {
        Path workspaceRoot = tempDir.resolve("workspace");
        Files.createDirectories(workspaceRoot);

        int exitCode = new CommandLine(new CICommand()).execute(
                "generate",
                "--target", "kernel-product-unit",
                "--path", workspaceRoot.toString()
        );

        assertThat(exitCode).isEqualTo(0);
        // YAPPC must NOT generate raw GitHub Actions workflows for lifecycle-governed ProductUnits
        assertThat(workspaceRoot.resolve(".github")).doesNotExist();
    }

    @Test
    @DisplayName("does generate GitHub workflows for generic-project target")
    void generatesWorkflowsForGenericProjectTarget() throws Exception {
        Path workspaceRoot = tempDir.resolve("generic-workspace");
        Files.createDirectories(workspaceRoot);

        int exitCode = new CommandLine(new CICommand()).execute(
                "generate",
                "--target", "generic-project",
                "--provider", "github-actions",
                "--path", workspaceRoot.toString()
        );

        assertThat(exitCode).isEqualTo(0);
        // Generic projects get raw workflow generation
        assertThat(workspaceRoot.resolve(".github/workflows")).exists();
    }

    @Test
    @DisplayName("validate subcommand succeeds in a basic workspace")
    void validateSubcommandSucceedsInBasicWorkspace() throws Exception {
        Path workspace = tempDir.resolve("complete-workspace");
        Files.createDirectories(workspace.resolve(".github/workflows"));
        Files.createFile(workspace.resolve("gradlew"));
        Files.createFile(workspace.resolve("package.json"));

        int exitCode = new CommandLine(new CICommand()).execute(
                "validate",
                "--path", workspace.toString()
        );

        assertThat(exitCode).isEqualTo(0);
    }
}
