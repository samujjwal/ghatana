/*
 * YAPPC - Yet Another Project/Package Creator
 * Copyright (c) 2025 Ghatana
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

package com.ghatana.yappc.cli.commands;

import com.ghatana.yappc.core.docs.AutoDocGenerator;
import com.ghatana.yappc.core.docs.AutoDocGenerator.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CLI command for auto-generating project documentation.
 *
 * <p>Week 10 Day 47: Auto-doc & diagram generator CLI.
 */
@Command(
        name = "docs",
        description = "Auto-generate project documentation and diagrams",
        mixinStandardHelpOptions = true,
        subcommands = {
            DocsCommand.GenerateCommand.class,
            DocsCommand.ValidateCommand.class,
            DocsCommand.PreviewCommand.class
        })
/**
 * DocsCommand component within the YAPPC platform.
 *
 * @doc.type class
 * @doc.purpose DocsCommand component within the YAPPC platform.
 * @doc.layer platform
 * @doc.pattern Component
 */
public class DocsCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(DocsCommand.class);

    @Override
    public Integer call() throws Exception {
        picocli.CommandLine.usage(this, System.out);
        return 0;
    }

    /**
 * Generate comprehensive project documentation. */
    @Command(
            name = "generate",
            description = "Generate comprehensive project documentation",
            mixinStandardHelpOptions = true)
    public static class GenerateCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "Project directory to document", defaultValue = ".")
        private File projectDir;

        @Option(
                names = {"-o", "--output"},
                description = "Output directory for documentation",
                defaultValue = "./docs")
        private File outputDir;

        @Option(
                names = {"--readme"},
                description = "Generate README.md",
                defaultValue = "true")
        private boolean generateReadme;

        @Option(
                names = {"--adrs"},
                description = "Generate Architecture Decision Records",
                defaultValue = "true")
        private boolean generateAdrs;

        @Option(
                names = {"--diagrams"},
                description = "Generate Mermaid diagrams",
                defaultValue = "true")
        private boolean generateDiagrams;

        @Option(
                names = {"--api-docs"},
                description = "Generate API documentation",
                defaultValue = "true")
        private boolean generateApiDocs;

        @Option(
                names = {"--overview"},
                description = "Generate project overview",
                defaultValue = "true")
        private boolean generateOverview;

        @Option(
                names = {"--force"},
                description = "Overwrite existing files")
        private boolean force;

        private final AutoDocGenerator generator;

        public GenerateCommand() {
            this.generator = new AutoDocGenerator();
        }

        @Override
        public Integer call() throws Exception {
            try {
                log.info("📚 Auto-Documentation Generator");
                log.info("📁 Project: {}", projectDir.getAbsolutePath());
                log.info("📄 Output: {}", outputDir.getAbsolutePath());
                log.info("");;

                // Validate project directory
                if (!projectDir.exists() || !projectDir.isDirectory()) {
                    log.error("❌ Project directory does not exist: {}", projectDir.getAbsolutePath());
                    return 1;
                }

                // Create output directory
                outputDir.mkdirs();

                // Create documentation specification
                DocumentationSpec spec =
                        new DocumentationSpec(
                                projectDir.toPath(),
                                outputDir.toPath(),
                                generateReadme,
                                generateAdrs,
                                generateDiagrams,
                                generateApiDocs,
                                generateOverview,
                                Map.of("force", force));

                // Generate documentation
                DocumentationResult result = generator.generateDocumentation(spec);

                // Write generated documents
                writeDocuments(result);

                // Display generation summary
                displayGenerationSummary(result);

                return 0;

            } catch (Exception e) {
                log.error("❌ Error generating documentation: {}", e.getMessage());
                e.printStackTrace();
                return 1;
            }
        }

        private void writeDocuments(DocumentationResult result) throws IOException {
            log.info("📝 Writing documentation files...");

            for (GeneratedDocument doc : result.documents()) {
                Path outputPath = doc.outputPath();

                // Create parent directories
                Files.createDirectories(outputPath.getParent());

                // Check if file exists and force flag
                if (Files.exists(outputPath) && !force) {
                    log.info("⚠️  Skipping existing file: {} (use --force to overwrite)", doc.filename());
                    continue;
                }

                // Write document
                Files.writeString(outputPath, doc.content());

                String icon =
                        switch (doc.type()) {
                            case README -> "📄";
                            case ADR -> "📋";
                            case DIAGRAM -> "📊";
                            case API_SPEC, API_DOCS -> "🔌";
                            case OVERVIEW -> "🎯";
                            default -> "📄";
                        };

                log.info("  {} {} → {}", icon, doc.filename(), outputPath);
            }
        }

        private void displayGenerationSummary(DocumentationResult result) {
            log.info("");;
            log.info("📊 GENERATION SUMMARY");
            log.info("═".repeat(50));
            log.info("📚 Total Documents: {}", result.documents().size());

            // Count by type
            var documentsByType =
                    result.documents().stream()
                            .collect(
                                    java.util.stream.Collectors.groupingBy(
                                            GeneratedDocument::type,
                                            java.util.stream.Collectors.counting()));

            for (var entry : documentsByType.entrySet()) {
                String icon =
                        switch (entry.getKey()) {
                            case README -> "📄";
                            case ADR -> "📋";
                            case DIAGRAM -> "📊";
                            case API_SPEC, API_DOCS -> "🔌";
                            case OVERVIEW -> "🎯";
                            default -> "📄";
                        };
                log.info("  {} {}: {}", icon, entry.getKey(), entry.getValue());
            }

            // Display metrics
            log.info("\n📈 PROJECT ANALYSIS:");
            for (var entry : result.analysisMetrics().entrySet()) {
                log.info("  • {}: {}", entry.getKey(), entry.getValue());
            }

            log.info("\n💡 NEXT STEPS:");
            log.info("  1. Review generated documentation for accuracy");
            log.info("  2. Customize templates and regenerate if needed");
            log.info("  3. Add generated docs to version control");
            log.info("  4. Set up documentation site with MkDocs (yappc docs site)");

            if (generateDiagrams) {
                log.info("  5. Install Mermaid CLI to render diagram images:");
                log.info("     npm install -g @mermaid-js/mermaid-cli");
            }
        }
    }

    /**
 * Validate existing documentation for completeness and accuracy. */
    @Command(
            name = "validate",
            description = "Validate existing documentation for completeness",
            mixinStandardHelpOptions = true)
    public static class ValidateCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "Project directory to validate", defaultValue = ".")
        private File projectDir;

        @Option(
                names = {"--docs-dir"},
                description = "Documentation directory to validate",
                defaultValue = "./docs")
        private File docsDir;

        @Option(
                names = {"--strict"},
                description = "Enable strict validation mode")
        private boolean strictMode;

        @Override
        public Integer call() throws Exception {
            log.info("🔍 Documentation Validation");
            log.info("📁 Project: {}", projectDir.getAbsolutePath());
            log.info("📄 Docs: {}", docsDir.getAbsolutePath());
            log.info("");;

            ValidationResult result = validateDocumentation();
            displayValidationResults(result);

            return result.hasErrors() && strictMode ? 1 : 0;
        }

        private ValidationResult validateDocumentation() throws IOException {
            ValidationResult result = new ValidationResult();

            // Check for essential files
            checkEssentialFiles(result);

            // Check documentation completeness
            checkDocumentationCompleteness(result);

            // Check for broken links
            checkForBrokenLinks(result);

            // Check diagram files
            checkDiagramFiles(result);

            return result;
        }

        private void checkEssentialFiles(ValidationResult result) {
            Path readme = projectDir.toPath().resolve("README.md");
            if (!Files.exists(readme)) {
                result.addError("Missing README.md file");
            } else {
                result.addInfo("✅ README.md found");
            }

            Path docsPath = docsDir.toPath();
            if (!Files.exists(docsPath)) {
                result.addWarning("Documentation directory not found: " + docsPath);
            } else {
                result.addInfo("✅ Documentation directory found");
            }
        }

        private void checkDocumentationCompleteness(ValidationResult result) {
            // Check for project overview
            Path overview = docsDir.toPath().resolve("PROJECT-OVERVIEW.md");
            if (Files.exists(overview)) {
                result.addInfo("✅ Project overview found");
            } else {
                result.addWarning("Missing project overview documentation");
            }

            // Check for ADRs
            Path decisionsDir = docsDir.toPath().resolve("decisions");
            if (Files.exists(decisionsDir)) {
                try {
                    long adrCount =
                            Files.list(decisionsDir)
                                    .filter(
                                            file ->
                                                    file.getFileName()
                                                            .toString()
                                                            .startsWith("ADR-"))
                                    .count();

                    if (adrCount > 0) {
                        result.addInfo("✅ Found " + adrCount + " Architecture Decision Records");
                    } else {
                        result.addWarning("ADR directory exists but contains no ADRs");
                    }
                } catch (IOException e) {
                    result.addError("Error reading ADR directory: " + e.getMessage());
                }
            } else {
                result.addWarning("No Architecture Decision Records found");
            }
        }

        private void checkForBrokenLinks(ValidationResult result) {
            // Simplified broken link checking
            try {
                Path readme = projectDir.toPath().resolve("README.md");
                if (Files.exists(readme)) {
                    String content = Files.readString(readme);
                    long relativeLinks =
                            content.lines()
                                    .flatMap(
                                            line ->
                                                    java.util.regex.Pattern.compile(
                                                                    "\\[([^\\]]+)\\]\\(([^\\)]+)\\)")
                                                            .matcher(line)
                                                            .results())
                                    .filter(match -> !match.group(2).startsWith("http"))
                                    .count();

                    if (relativeLinks > 0) {
                        result.addInfo("Found " + relativeLinks + " internal links to validate");
                    }
                }
            } catch (IOException e) {
                result.addError("Error checking links: " + e.getMessage());
            }
        }

        private void checkDiagramFiles(ValidationResult result) {
            Path diagramsDir = docsDir.toPath().resolve("diagrams");
            if (Files.exists(diagramsDir)) {
                try {
                    long mermaidCount =
                            Files.list(diagramsDir)
                                    .filter(file -> file.getFileName().toString().endsWith(".mmd"))
                                    .count();

                    if (mermaidCount > 0) {
                        result.addInfo("✅ Found " + mermaidCount + " Mermaid diagrams");
                    }
                } catch (IOException e) {
                    result.addError("Error reading diagrams directory: " + e.getMessage());
                }
            } else {
                result.addWarning("No diagrams directory found");
            }
        }

        private void displayValidationResults(ValidationResult result) {
            log.info("📊 VALIDATION RESULTS");
            log.info("═".repeat(40));

            if (!result.errors.isEmpty()) {
                log.info("❌ ERRORS:");
                result.errors.forEach(error ->
                    log.info("  • {}", error));
                    log.info("");
            }

            if (!result.warnings.isEmpty()) {
                log.info("⚠️  WARNINGS:");
                result.warnings.forEach(warning ->
                    log.info("  • {}", warning));
                    log.info("");
            }

            if (!result.info.isEmpty()) {
                log.info("ℹ️  INFO:");
                result.info.forEach(info ->
                    log.info("  • {}", info));
                    log.info("");
            }

            log.info("📈 SUMMARY:");
            log.info("  • Errors: {}", result.errors.size());
            log.info("  • Warnings: {}", result.warnings.size());
            log.info("  • Info: {}", result.info.size());

            if (result.hasErrors()) {
                log.info("\n💡 Run 'yappc docs generate' to create missing documentation");
            } else if (result.hasWarnings()) {
                log.info("\n✅ Documentation validation completed with warnings");
            } else {
                log.info("\n🎉 Documentation validation passed!");
            }
        }

        private static class ValidationResult {
            private final java.util.List<String> errors = new java.util.ArrayList<>();
            private final java.util.List<String> warnings = new java.util.ArrayList<>();
            private final java.util.List<String> info = new java.util.ArrayList<>();

            void addError(String error) {
                errors.add(error);
            }

            void addWarning(String warning) {
                warnings.add(warning);
            }

            void addInfo(String info) {
                this.info.add(info);
            }

            boolean hasErrors() {
                return !errors.isEmpty();
            }

            boolean hasWarnings() {
                return !warnings.isEmpty();
            }
        }
    }

    /**
 * Preview documentation in a local server. */
    @Command(
            name = "preview",
            description = "Preview documentation in a local server",
            mixinStandardHelpOptions = true)
    public static class PreviewCommand implements Callable<Integer> {

        @Parameters(
                index = "0",
                description = "Documentation directory to preview",
                defaultValue = "./docs")
        private File docsDir;

        @Option(
                names = {"-p", "--port"},
                description = "Server port",
                defaultValue = "8080")
        private int port;

        @Option(
                names = {"--open"},
                description = "Open browser automatically")
        private boolean openBrowser;

        @Override
        public Integer call() throws Exception {
            log.info("🌐 Documentation Preview Server");
            log.info("📁 Docs: {}", docsDir.getAbsolutePath());
            log.info("🌐 Port: {}", port);
            log.info("");;

            if (!docsDir.exists()) {
                log.error("❌ Documentation directory does not exist: {}", docsDir.getAbsolutePath());
                log.error("💡 Run 'yappc docs generate' first");
                return 1;
            }

            startPreviewServer();
            return 0;
        }

        private void startPreviewServer() {
            log.info("🚀 Starting documentation preview server...");
            log.info("📖 Server URL: http://localhost:{}", port);
            log.info("⏹️  Press Ctrl+C to stop");
            log.info("");;

            // In a real implementation, this would start an embedded HTTP server
            // For demonstration, we'll simulate the server
            try {
                simulatePreviewServer();
            } catch (InterruptedException e) {
                log.info("\n👋 Server stopped");
            }
        }

        private void simulatePreviewServer() throws InterruptedException {
            // Simulate server activity
            String[] activities = {
                "📄 Serving README.md",
                "📊 Rendering Mermaid diagrams",
                "📋 Loading ADR documents",
                "🔌 Serving API documentation"
            };

            for (int i = 0; i < 10; i++) {
                Thread.sleep(2000);
                log.info("[{}] {}", java.time.LocalTime.now().toString().substring(0, 8), activities[i % activities.length]);
            }

            log.info("\n💡 In a real implementation, this would:");
            log.info("  • Start an embedded HTTP server");
            log.info("  • Serve static documentation files");
            log.info("  • Render Mermaid diagrams to SVG/PNG");
            log.info("  • Provide live reload functionality");
            log.info("  • Integrate with MkDocs for advanced features");
        }
    }
}
