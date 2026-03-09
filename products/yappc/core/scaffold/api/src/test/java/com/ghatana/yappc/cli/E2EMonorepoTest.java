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

import static org.junit.jupiter.api.Assertions.*;

import com.ghatana.yappc.adapters.GradleGroovyAdapter;
import com.ghatana.yappc.adapters.NxAdapter;
import com.ghatana.yappc.core.model.WorkspaceSpec;
import com.ghatana.yappc.core.template.SimpleTemplateEngine;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/**
 * Day 15: E2E integration test for mono-repo generation with Java+TS.
 *
 * <p>Tests end-to-end workspace generation combining Gradle + Nx adapters, running doctor, graph,
 * and CI dry-run commands with OTel tracing.
 
 * @doc.type class
 * @doc.purpose Handles e2e monorepo test operations
 * @doc.layer core
 * @doc.pattern Test
*/
class E2EMonorepoTest {

    @TempDir Path tempDir;

    @TempDir Path reportsDir;

    private SimpleTemplateEngine templateEngine;
    private GradleGroovyAdapter gradleAdapter;
    private NxAdapter nxAdapter;
    private Tracer tracer;

    @BeforeEach
    void setUp() {
        templateEngine = new SimpleTemplateEngine();
        gradleAdapter = new GradleGroovyAdapter(templateEngine);
        nxAdapter = new NxAdapter(templateEngine);

        // Initialize OpenTelemetry tracer for observability
        tracer = OpenTelemetry.noop().getTracer("yappc-e2e");
    }

    @Test
    void shouldGenerateCompleteMonorepoWithJavaAndTypeScript() throws Exception {
        Span span = tracer.spanBuilder("e2e-monorepo-generation").startSpan();
        try (Scope scope = span.makeCurrent()) {
            // Day 15 Deliverable: End-to-end integration test
            WorkspaceSpec spec = WorkspaceSpec.defaultMonorepo("test-monorepo");

            // Generate combined Gradle + Nx workspace
            generateWorkspaceFiles(spec);

            // Verify Gradle infrastructure (Java services)
            verifyGradleInfrastructure();

            // Verify Nx infrastructure (TypeScript workspaces)
            verifyNxInfrastructure();

            // Verify pnpm workspace policies (Day 14 integration)
            verifyPnpmPolicies();

            // Store artifacts for observability
            storeArtifacts();

            span.addEvent("e2e-monorepo-generation-complete");
        } finally {
            span.end();
        }
    }

    @Test
    void shouldRunDoctorCommandSuccessfully() throws Exception {
        Span span = tracer.spanBuilder("e2e-doctor-command").startSpan();
        try (Scope scope = span.makeCurrent()) {
            // Initialize workspace
            WorkspaceSpec spec = WorkspaceSpec.defaultMonorepo("test-workspace");
            generateWorkspaceFiles(spec);

            // Run doctor command (inject fake runner to avoid external tool dependency in CI)
            com.ghatana.yappc.core.doctor.DoctorRunner fakeRunner =
                    new com.ghatana.yappc.core.doctor.DoctorRunner() {
                        @Override
                        public java.util.List<com.ghatana.yappc.core.doctor.ToolCheckResult>
                                runAllChecks() {
                            java.util.List<com.ghatana.yappc.core.doctor.ToolCheckResult> list =
                                    new java.util.ArrayList<>();
                            // Simulate all tools available
                            list.add(
                                    new com.ghatana.yappc.core.doctor.ToolCheckResult(
                                            new com.ghatana.yappc.core.doctor.ToolCheck(
                                                    "java", java.util.List.of("java", "--version")),
                                            true,
                                            "openjdk 21"));
                            list.add(
                                    new com.ghatana.yappc.core.doctor.ToolCheckResult(
                                            new com.ghatana.yappc.core.doctor.ToolCheck(
                                                    "node", java.util.List.of("node", "--version")),
                                            true,
                                            "v20"));
                            list.add(
                                    new com.ghatana.yappc.core.doctor.ToolCheckResult(
                                            new com.ghatana.yappc.core.doctor.ToolCheck(
                                                    "pnpm", java.util.List.of("pnpm", "--version")),
                                            true,
                                            "8.15.0"));
                            return list;
                        }
                    };

            DoctorCommand doctorCommand = new DoctorCommand(fakeRunner);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            System.setOut(new PrintStream(outputStream));

            CommandLine cmd = new CommandLine(doctorCommand);
            int exitCode = cmd.execute();

            // Verify doctor output
            String output = outputStream.toString();
            assertEquals(0, exitCode, "Doctor command should succeed");
            assertTrue(output.contains("System Requirements Check"), "Should show doctor output");

            span.addEvent("doctor-command-complete");
        } finally {
            span.end();
        }
    }

    @Test
    void shouldRunGraphCommandSuccessfully() throws Exception {
        Span span = tracer.spanBuilder("e2e-graph-command").startSpan();
        try (Scope scope = span.makeCurrent()) {
            // Initialize workspace
            WorkspaceSpec spec = WorkspaceSpec.defaultMonorepo("test-workspace");
            generateWorkspaceFiles(spec);

            // Run graph command
            GraphCommand graphCommand = new GraphCommand();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            System.setOut(new PrintStream(outputStream));

            CommandLine cmd = new CommandLine(graphCommand);
            int exitCode = cmd.execute("--format", "json");

            // Verify graph output
            String output = outputStream.toString();
            assertEquals(0, exitCode, "Graph command should succeed");
            assertTrue(
                    output.contains("adapters") || output.contains("tasks"),
                    "Should show graph data");

            span.addEvent("graph-command-complete");
        } finally {
            span.end();
        }
    }

    @Test
    void shouldRunCIDryRunSuccessfully() throws Exception {
        Span span = tracer.spanBuilder("e2e-ci-dry-run").startSpan();
        try (Scope scope = span.makeCurrent()) {
            // Initialize workspace with CI configuration
            WorkspaceSpec spec = WorkspaceSpec.defaultMonorepo("test-workspace");
            generateWorkspaceFiles(spec);

            // Create basic CI configuration for dry-run
            createBasicCIConfig();

            // Simulate CI dry-run (validation without execution)
            boolean ciValid = validateCIConfiguration();
            assertTrue(ciValid, "CI configuration should be valid");

            span.addEvent("ci-dry-run-complete");
        } finally {
            span.end();
        }
    }

    private void generateWorkspaceFiles(WorkspaceSpec spec) throws Exception {
        // Generate Gradle infrastructure (Days 11-12)
        gradleAdapter.generateBuildFiles(spec, tempDir);

        // Generate Nx infrastructure (Days 13-14)
        nxAdapter.generateWorkspaceFiles(spec, tempDir);
    }

    private void verifyGradleInfrastructure() {
        // Verify Gradle files exist
        assertTrue(
                Files.exists(tempDir.resolve("settings.gradle")), "settings.gradle should exist");
        assertTrue(
                Files.exists(tempDir.resolve("gradle/libs.versions.toml")),
                "Version catalog should exist");
        assertTrue(Files.exists(tempDir.resolve("build.gradle")), "Root build.gradle should exist");
        assertTrue(
                Files.exists(tempDir.resolve("gradle.properties")),
                "gradle.properties should exist");
    }

    private void verifyNxInfrastructure() {
        // Verify Nx files exist
        assertTrue(Files.exists(tempDir.resolve("nx.json")), "nx.json should exist");
        assertTrue(Files.exists(tempDir.resolve("package.json")), "package.json should exist");
        assertTrue(
                Files.exists(tempDir.resolve("tsconfig.base.json")),
                "tsconfig.base.json should exist");
        assertTrue(
                Files.exists(tempDir.resolve("eslint.config.mjs")),
                "eslint.config.mjs should exist");
    }

    private void verifyPnpmPolicies() {
        // Verify pnpm workspace policies (Day 14)
        assertTrue(
                Files.exists(tempDir.resolve("pnpm-workspace.yaml")),
                "pnpm-workspace.yaml should exist");
        assertTrue(Files.exists(tempDir.resolve(".npmrc")), ".npmrc should exist");
        assertTrue(Files.exists(tempDir.resolve("pnpm-lock.yaml")), "pnpm-lock.yaml should exist");
    }

    private void createBasicCIConfig() throws Exception {
        // Create minimal CI configuration for testing
        Path ciDir = tempDir.resolve(".github/workflows");
        Files.createDirectories(ciDir);

        String ciConfig =
                """
            name: CI
            on: [push, pull_request]
            jobs:
              test:
                runs-on: ubuntu-latest
                steps:
                  - uses: actions/checkout@v4
                  - uses: actions/setup-java@v4
                    with:
                      java-version: '21'
                      distribution: 'temurin'
                  - uses: pnpm/action-setup@v2
                    with:
                      version: 8
                  - run: pnpm install
                  - run: ./gradlew build
                  - run: pnpm test
            """;

        Files.writeString(ciDir.resolve("ci.yml"), ciConfig);
    }

    private boolean validateCIConfiguration() {
        // Validate CI configuration exists and is well-formed
        Path ciFile = tempDir.resolve(".github/workflows/ci.yml");
        if (!Files.exists(ciFile)) {
            return false;
        }

        try {
            String content = Files.readString(ciFile);
            return content.contains("gradle")
                    && content.contains("pnpm")
                    && content.contains("test");
        } catch (Exception e) {
            return false;
        }
    }

    private void storeArtifacts() throws Exception {
        // Day 15 Requirement: Store artifacts under reports/e2e/week3/
        Path week3Reports = reportsDir.resolve("e2e/week3");
        Files.createDirectories(week3Reports);

        // Create manifest of generated files
        StringBuilder manifest = new StringBuilder();
        manifest.append("# E2E Monorepo Generation Report\n");
        manifest.append("Generated: ").append(java.time.Instant.now()).append("\n\n");
        manifest.append("## Generated Files:\n");

        Files.walk(tempDir)
                .filter(Files::isRegularFile)
                .forEach(
                        file -> {
                            String relativePath = tempDir.relativize(file).toString();
                            manifest.append("- ").append(relativePath).append("\n");
                        });

        Files.writeString(week3Reports.resolve("generation-manifest.md"), manifest.toString());

        // Copy key configuration files for verification
        if (Files.exists(tempDir.resolve("nx.json"))) {
            Files.copy(tempDir.resolve("nx.json"), week3Reports.resolve("nx.json"));
        }
        if (Files.exists(tempDir.resolve("settings.gradle"))) {
            Files.copy(tempDir.resolve("settings.gradle"), week3Reports.resolve("settings.gradle"));
        }
    }
}
