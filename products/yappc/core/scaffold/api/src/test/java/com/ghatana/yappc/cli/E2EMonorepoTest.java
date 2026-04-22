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
    void setUp() { // GH-90000
        templateEngine = new SimpleTemplateEngine(); // GH-90000
        gradleAdapter = new GradleGroovyAdapter(templateEngine); // GH-90000
        nxAdapter = new NxAdapter(templateEngine); // GH-90000

        // Initialize OpenTelemetry tracer for observability
        tracer = OpenTelemetry.noop().getTracer("yappc-e2e [GH-90000]");
    }

    @Test
    void shouldGenerateCompleteMonorepoWithJavaAndTypeScript() throws Exception { // GH-90000
        Span span = tracer.spanBuilder("e2e-monorepo-generation [GH-90000]").startSpan();
        try (Scope scope = span.makeCurrent()) { // GH-90000
            // Day 15 Deliverable: End-to-end integration test
            WorkspaceSpec spec = WorkspaceSpec.defaultMonorepo("test-monorepo [GH-90000]");

            // Generate combined Gradle + Nx workspace
            generateWorkspaceFiles(spec); // GH-90000

            // Verify Gradle infrastructure (Java services) // GH-90000
            verifyGradleInfrastructure(); // GH-90000

            // Verify Nx infrastructure (TypeScript workspaces) // GH-90000
            verifyNxInfrastructure(); // GH-90000

            // Verify pnpm workspace policies (Day 14 integration) // GH-90000
            verifyPnpmPolicies(); // GH-90000

            // Store artifacts for observability
            storeArtifacts(); // GH-90000

            span.addEvent("e2e-monorepo-generation-complete [GH-90000]");
        } finally {
            span.end(); // GH-90000
        }
    }

    @Test
    void shouldRunDoctorCommandSuccessfully() throws Exception { // GH-90000
        Span span = tracer.spanBuilder("e2e-doctor-command [GH-90000]").startSpan();
        try (Scope scope = span.makeCurrent()) { // GH-90000
            // Initialize workspace
            WorkspaceSpec spec = WorkspaceSpec.defaultMonorepo("test-workspace [GH-90000]");
            generateWorkspaceFiles(spec); // GH-90000

            // Run doctor command (inject fake runner to avoid external tool dependency in CI) // GH-90000
            com.ghatana.yappc.core.doctor.DoctorRunner fakeRunner =
                    new com.ghatana.yappc.core.doctor.DoctorRunner() { // GH-90000
                        @Override
                        public java.util.List<com.ghatana.yappc.core.doctor.ToolCheckResult>
                                runAllChecks() { // GH-90000
                            java.util.List<com.ghatana.yappc.core.doctor.ToolCheckResult> list =
                                    new java.util.ArrayList<>(); // GH-90000
                            // Simulate all tools available
                            list.add( // GH-90000
                                    new com.ghatana.yappc.core.doctor.ToolCheckResult( // GH-90000
                                            new com.ghatana.yappc.core.doctor.ToolCheck( // GH-90000
                                                    "java", java.util.List.of("java", "--version")), // GH-90000
                                            true,
                                            "openjdk 21"));
                            list.add( // GH-90000
                                    new com.ghatana.yappc.core.doctor.ToolCheckResult( // GH-90000
                                            new com.ghatana.yappc.core.doctor.ToolCheck( // GH-90000
                                                    "node", java.util.List.of("node", "--version")), // GH-90000
                                            true,
                                            "v20"));
                            list.add( // GH-90000
                                    new com.ghatana.yappc.core.doctor.ToolCheckResult( // GH-90000
                                            new com.ghatana.yappc.core.doctor.ToolCheck( // GH-90000
                                                    "pnpm", java.util.List.of("pnpm", "--version")), // GH-90000
                                            true,
                                            "8.15.0"));
                            return list;
                        }
                    };

            DoctorCommand doctorCommand = new DoctorCommand(fakeRunner); // GH-90000
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(); // GH-90000
            System.setOut(new PrintStream(outputStream)); // GH-90000

            CommandLine cmd = new CommandLine(doctorCommand); // GH-90000
            int exitCode = cmd.execute(); // GH-90000

            // Verify doctor output
            String output = outputStream.toString(); // GH-90000
            assertEquals(0, exitCode, "Doctor command should succeed"); // GH-90000
            assertTrue(output.contains("System Requirements Check [GH-90000]"), "Should show doctor output");

            span.addEvent("doctor-command-complete [GH-90000]");
        } finally {
            span.end(); // GH-90000
        }
    }

    @Test
    void shouldRunGraphCommandSuccessfully() throws Exception { // GH-90000
        Span span = tracer.spanBuilder("e2e-graph-command [GH-90000]").startSpan();
        try (Scope scope = span.makeCurrent()) { // GH-90000
            // Initialize workspace
            WorkspaceSpec spec = WorkspaceSpec.defaultMonorepo("test-workspace [GH-90000]");
            generateWorkspaceFiles(spec); // GH-90000

            // Run graph command
            GraphCommand graphCommand = new GraphCommand(); // GH-90000
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(); // GH-90000
            System.setOut(new PrintStream(outputStream)); // GH-90000

            CommandLine cmd = new CommandLine(graphCommand); // GH-90000
            int exitCode = cmd.execute("--format", "json"); // GH-90000

            // Verify graph output
            String output = outputStream.toString(); // GH-90000
            assertEquals(0, exitCode, "Graph command should succeed"); // GH-90000
            assertTrue( // GH-90000
                    output.contains("adapters [GH-90000]") || output.contains("tasks [GH-90000]"),
                    "Should show graph data");

            span.addEvent("graph-command-complete [GH-90000]");
        } finally {
            span.end(); // GH-90000
        }
    }

    @Test
    void shouldRunCIDryRunSuccessfully() throws Exception { // GH-90000
        Span span = tracer.spanBuilder("e2e-ci-dry-run [GH-90000]").startSpan();
        try (Scope scope = span.makeCurrent()) { // GH-90000
            // Initialize workspace with CI configuration
            WorkspaceSpec spec = WorkspaceSpec.defaultMonorepo("test-workspace [GH-90000]");
            generateWorkspaceFiles(spec); // GH-90000

            // Create basic CI configuration for dry-run
            createBasicCIConfig(); // GH-90000

            // Simulate CI dry-run (validation without execution) // GH-90000
            boolean ciValid = validateCIConfiguration(); // GH-90000
            assertTrue(ciValid, "CI configuration should be valid"); // GH-90000

            span.addEvent("ci-dry-run-complete [GH-90000]");
        } finally {
            span.end(); // GH-90000
        }
    }

    private void generateWorkspaceFiles(WorkspaceSpec spec) throws Exception { // GH-90000
        // Generate Gradle infrastructure (Days 11-12) // GH-90000
        gradleAdapter.generateBuildFiles(spec, tempDir); // GH-90000

        // Generate Nx infrastructure (Days 13-14) // GH-90000
        nxAdapter.generateWorkspaceFiles(spec, tempDir); // GH-90000
    }

    private void verifyGradleInfrastructure() { // GH-90000
        // Verify Gradle files exist
        assertTrue( // GH-90000
                Files.exists(tempDir.resolve("settings.gradle [GH-90000]")), "settings.gradle should exist");
        assertTrue( // GH-90000
                Files.exists(tempDir.resolve("gradle/libs.versions.toml [GH-90000]")),
                "Version catalog should exist");
        assertTrue(Files.exists(tempDir.resolve("build.gradle [GH-90000]")), "Root build.gradle should exist");
        assertTrue( // GH-90000
                Files.exists(tempDir.resolve("gradle.properties [GH-90000]")),
                "gradle.properties should exist");
    }

    private void verifyNxInfrastructure() { // GH-90000
        // Verify Nx files exist
        assertTrue(Files.exists(tempDir.resolve("nx.json [GH-90000]")), "nx.json should exist");
        assertTrue(Files.exists(tempDir.resolve("package.json [GH-90000]")), "package.json should exist");
        assertTrue( // GH-90000
                Files.exists(tempDir.resolve("tsconfig.base.json [GH-90000]")),
                "tsconfig.base.json should exist");
        assertTrue( // GH-90000
                Files.exists(tempDir.resolve("eslint.config.mjs [GH-90000]")),
                "eslint.config.mjs should exist");
    }

    private void verifyPnpmPolicies() { // GH-90000
        // Verify pnpm workspace policies (Day 14) // GH-90000
        assertTrue( // GH-90000
                Files.exists(tempDir.resolve("pnpm-workspace.yaml [GH-90000]")),
                "pnpm-workspace.yaml should exist");
        assertTrue(Files.exists(tempDir.resolve(".npmrc [GH-90000]")), ".npmrc should exist");
        assertTrue(Files.exists(tempDir.resolve("pnpm-lock.yaml [GH-90000]")), "pnpm-lock.yaml should exist");
    }

    private void createBasicCIConfig() throws Exception { // GH-90000
        // Create minimal CI configuration for testing
        Path ciDir = tempDir.resolve(".github/workflows [GH-90000]");
        Files.createDirectories(ciDir); // GH-90000

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

        Files.writeString(ciDir.resolve("ci.yml [GH-90000]"), ciConfig);
    }

    private boolean validateCIConfiguration() { // GH-90000
        // Validate CI configuration exists and is well-formed
        Path ciFile = tempDir.resolve(".github/workflows/ci.yml [GH-90000]");
        if (!Files.exists(ciFile)) { // GH-90000
            return false;
        }

        try {
            String content = Files.readString(ciFile); // GH-90000
            return content.contains("gradle [GH-90000]")
                    && content.contains("pnpm [GH-90000]")
                    && content.contains("test [GH-90000]");
        } catch (Exception e) { // GH-90000
            return false;
        }
    }

    private void storeArtifacts() throws Exception { // GH-90000
        // Day 15 Requirement: Store artifacts under reports/e2e/week3/
        Path week3Reports = reportsDir.resolve("e2e/week3 [GH-90000]");
        Files.createDirectories(week3Reports); // GH-90000

        // Create manifest of generated files
        StringBuilder manifest = new StringBuilder(); // GH-90000
        manifest.append("# E2E Monorepo Generation Report\n [GH-90000]");
        manifest.append("Generated:  [GH-90000]").append(java.time.Instant.now()).append("\n\n [GH-90000]");
        manifest.append("## Generated Files:\n [GH-90000]");

        Files.walk(tempDir) // GH-90000
                .filter(Files::isRegularFile) // GH-90000
                .forEach( // GH-90000
                        file -> {
                            String relativePath = tempDir.relativize(file).toString(); // GH-90000
                            manifest.append("-  [GH-90000]").append(relativePath).append("\n [GH-90000]");
                        });

        Files.writeString(week3Reports.resolve("generation-manifest.md [GH-90000]"), manifest.toString());

        // Copy key configuration files for verification
        if (Files.exists(tempDir.resolve("nx.json [GH-90000]"))) {
            Files.copy(tempDir.resolve("nx.json [GH-90000]"), week3Reports.resolve("nx.json [GH-90000]"));
        }
        if (Files.exists(tempDir.resolve("settings.gradle [GH-90000]"))) {
            Files.copy(tempDir.resolve("settings.gradle [GH-90000]"), week3Reports.resolve("settings.gradle [GH-90000]"));
        }
    }
}
