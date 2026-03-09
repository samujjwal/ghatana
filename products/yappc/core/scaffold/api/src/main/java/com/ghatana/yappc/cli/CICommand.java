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

package com.ghatana.yappc.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CI command for validating and generating CI/CD configurations.
 *
 * <p>Day 15 requirement: CI dry-run command for E2E validation.
 */
@Command(
        name = "ci",
        description = "CI/CD configuration and validation commands",
        subcommands = {
            CICommand.ValidateCommand.class,
            CICommand.GenerateCommand.class,
            CICommand.DryRunCommand.class
        })
/**
 * CICommand component within the YAPPC platform.
 *
 * @doc.type class
 * @doc.purpose CICommand component within the YAPPC platform.
 * @doc.layer platform
 * @doc.pattern Component
 */
public class CICommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(CICommand.class);

    @Override
    public Integer call() throws Exception {
        log.info("Use 'yappc ci --help' to see available CI commands");
        return 0;
    }

    @Command(name = "validate", description = "Validate existing CI configuration")
    static class ValidateCommand implements Callable<Integer> {

        @Option(
                names = {"-p", "--path"},
                description = "Path to workspace root",
                defaultValue = ".")
        private String workspacePath;

        @Override
        public Integer call() throws Exception {
            Path workspace = Paths.get(workspacePath);

            log.info("🔍 Validating CI configuration...");

            // Check for common CI files
            boolean hasGitHubActions = Files.exists(workspace.resolve(".github/workflows"));
            boolean hasGradleWrapper = Files.exists(workspace.resolve("gradlew"));
            boolean hasPackageJson = Files.exists(workspace.resolve("package.json"));
            boolean hasNxConfig = Files.exists(workspace.resolve("nx.json"));

            log.info("\n📋 CI Configuration Status:");
            log.info("  ✓ GitHub Actions:     {}", hasGitHubActions ? "Found" : "Missing");
            log.info("  ✓ Gradle Wrapper:     {}", hasGradleWrapper ? "Found" : "Missing");
            log.info("  ✓ Package.json:       {}", hasPackageJson ? "Found" : "Missing");
            log.info("  ✓ Nx Configuration:   {}", hasNxConfig ? "Found" : "Missing");

            if (hasGitHubActions && hasGradleWrapper && hasPackageJson) {
                log.info("\n✅ CI configuration looks valid");
                return 0;
            } else {
                log.info("\n⚠️  Missing required CI components");
                return 1;
            }
        }
    }

    @Command(name = "generate", description = "Generate CI configuration templates")
    static class GenerateCommand implements Callable<Integer> {

        @Option(
                names = {"-p", "--path"},
                description = "Path to workspace root",
                defaultValue = ".")
        private String workspacePath;

        @Option(
                names = {"--provider"},
                description = "CI provider (github-actions, gitlab-ci)",
                defaultValue = "github-actions")
        private String provider;

        @Override
        public Integer call() throws Exception {
            Path workspace = Paths.get(workspacePath);

            log.info("🔧 Generating {} CI configuration...", provider);

            if ("github-actions".equals(provider)) {
                generateGitHubActions(workspace);
            } else {
                log.error("❌ Unsupported CI provider: {}", provider);
                return 1;
            }

            log.info("✅ CI configuration generated successfully");
            return 0;
        }

        private void generateGitHubActions(Path workspace) throws Exception {
            Path workflowsDir = workspace.resolve(".github/workflows");
            Files.createDirectories(workflowsDir);

            String ciWorkflow =
                    """
                name: CI

                on:
                  push:
                    branches: [ main, develop ]
                  pull_request:
                    branches: [ main ]

                jobs:
                  test:
                    runs-on: ubuntu-latest

                    steps:
                    - uses: actions/checkout@v4

                    - name: Setup Java
                      uses: actions/setup-java@v4
                      with:
                        java-version: '21'
                        distribution: 'temurin'

                    - name: Setup Node.js
                      uses: actions/setup-node@v4
                      with:
                        node-version: '18'

                    - name: Setup pnpm
                      uses: pnpm/action-setup@v2
                      with:
                        version: 8

                    - name: Install dependencies
                      run: pnpm install

                    - name: Run tests
                      run: |
                        ./gradlew test
                        pnpm test

                    - name: Build
                      run: |
                        ./gradlew build
                        pnpm build
                """;

            Files.writeString(workflowsDir.resolve("ci.yml"), ciWorkflow);
        }
    }

    @Command(name = "dry-run", description = "Validate CI configuration without execution")
    static class DryRunCommand implements Callable<Integer> {

        @Option(
                names = {"-p", "--path"},
                description = "Path to workspace root",
                defaultValue = ".")
        private String workspacePath;

        @Override
        public Integer call() throws Exception {
            Path workspace = Paths.get(workspacePath);

            log.info("🧪 Running CI dry-run validation...");

            // Validate workspace structure
            boolean valid = validateWorkspaceStructure(workspace);

            if (valid) {
                log.info("✅ CI dry-run validation passed");
                log.info("📋 Ready for CI execution");
                return 0;
            } else {
                log.info("❌ CI dry-run validation failed");
                return 1;
            }
        }

        private boolean validateWorkspaceStructure(Path workspace) {
            try {
                // Check essential files exist
                boolean hasGradle =
                        Files.exists(workspace.resolve("gradlew"))
                                && Files.exists(workspace.resolve("settings.gradle"));
                boolean hasNode = Files.exists(workspace.resolve("package.json"));
                boolean hasPnpmWorkspace = Files.exists(workspace.resolve("pnpm-workspace.yaml"));

                log.info("  ✓ Gradle build:       {}", hasGradle ? "Ready" : "Missing");
                log.info("  ✓ Node.js project:    {}", hasNode ? "Ready" : "Missing");
                log.info("  ✓ pnpm workspace:     {}", hasPnpmWorkspace ? "Ready" : "Missing");

                // Validate build files are well-formed
                if (hasGradle) {
                    String settings = Files.readString(workspace.resolve("settings.gradle"));
                    boolean hasValidSettings = settings.contains("rootProject.name");
                    log.info("  ✓ Gradle settings:    {}", hasValidSettings ? "Valid" : "Invalid");
                    hasGradle = hasValidSettings;
                }

                if (hasNode) {
                    String packageJson = Files.readString(workspace.resolve("package.json"));
                    boolean hasValidPackage =
                            packageJson.contains("\"name\"") && packageJson.contains("\"scripts\"");
                    log.info("  ✓ Package.json:       {}", hasValidPackage ? "Valid" : "Invalid");
                    hasNode = hasValidPackage;
                }

                return hasGradle && hasNode;

            } catch (Exception e) {
                log.error("❌ Error validating workspace: {}", e.getMessage());
                return false;
            }
        }
    }
}
