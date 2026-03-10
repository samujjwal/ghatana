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

import com.ghatana.yappc.core.docs.AIADRGenerator;
import com.ghatana.yappc.core.docs.adr.model.ADRModels.*;
import com.ghatana.yappc.core.docs.adr.model.ADREnums.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import picocli.CommandLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CLI command for AI-powered Architecture Decision Record (ADR) generation and
 * management.
 *
 * <p>
 * Week 10 Day 48: ADR templates with decision rationale (AI assists drafting).
 */
@CommandLine.Command(
        name = "adr",
        description = "AI-powered Architecture Decision Record generation and management",
        subcommands = {
            ADRCommand.CreateCommand.class,
            ADRCommand.ValidateCommand.class,
            ADRCommand.ListCommand.class,
            ADRCommand.SuggestCommand.class,
            ADRCommand.InitCommand.class
        })
/**
 * ADRCommand component within the YAPPC platform.
 *
 * @doc.type class
 * @doc.purpose ADRCommand component within the YAPPC platform.
 * @doc.layer platform
 * @doc.pattern Component
 */
public class ADRCommand implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ADRCommand.class);

    @CommandLine.Option(
            names = {"-h", "--help"},
            usageHelp = true,
            description = "Show help message")
    private boolean helpRequested = false;

    @Override
    public void run() {
        log.info("🤖 YAPPC AI ADR Generator");
        log.info("Use 'adr <command> --help' for specific command help");
        log.info("");;
        log.info("Available commands:");
        log.info("  init      Initialize ADR directory structure");
        log.info("  create    Create a new ADR with AI assistance");
        log.info("  validate  Validate existing ADR for quality");
        log.info("  list      List all ADRs in the project");
        log.info("  suggest   Get AI suggestions for decision alternatives");
    }

    @CommandLine.Command(name = "init", description = "Initialize ADR directory structure")
    static class InitCommand implements Runnable {

        @CommandLine.Option(
                names = {"-d", "--directory"},
                description = "ADR directory path (default: ./docs/adr)",
                defaultValue = "./docs/adr")
        private String adrDirectory;

        @CommandLine.Option(
                names = {"-t", "--template"},
                description = "Default template type (${COMPLETION-CANDIDATES})",
                defaultValue = "STANDARD")
        private ADRTemplateType defaultTemplate;

        @Override
        public void run() {
            try {
                initializeADRStructure();
                log.info("✅ ADR directory structure initialized successfully!");
            } catch (Exception e) {
                log.error("❌ Error initializing ADR structure: {}", e.getMessage());
                System.exit(1);
            }
        }

        private void initializeADRStructure() throws IOException {
            Path adrPath = Paths.get(adrDirectory);

            // Create directory structure
            Files.createDirectories(adrPath);
            Files.createDirectories(adrPath.resolve("templates"));
            Files.createDirectories(adrPath.resolve("diagrams"));

            // Create index file
            createIndexFile(adrPath);

            // Create configuration file
            createConfigFile(adrPath);

            // Create template files
            createTemplateFiles(adrPath.resolve("templates"));

            // Create README
            createADRReadme(adrPath);

            log.info("📁 Created ADR directory: {}", adrPath.toAbsolutePath());
            log.info("📄 Created configuration and templates");
        }

        private void createIndexFile(Path adrPath) throws IOException {
            String indexContent
                    = String.format(
                            """
                # Architecture Decision Records (ADRs)

                This directory contains the architecture decision records for our project.

                ## ADR Index

                | Number | Title | Status | Date | Decision Type |
                |--------|-------|---------|------|---------------|
                | 001 | [Example ADR](001-example-adr.md) | Template | %s | Example |

                ## Status Legend

                - 🟢 **Accepted** - Decision has been accepted and is being implemented
                - 🟡 **Proposed** - Decision is proposed and under review
                - 🔴 **Deprecated** - Decision has been deprecated by a newer decision
                - ⚪ **Superseded** - Decision has been superseded by a newer decision

                ## Decision Types

                - **Architectural** - Fundamental system structure decisions
                - **Technological** - Technology stack and tool selections
                - **Process** - Development and operational process decisions
                - **Quality** - Quality standards and practices
                - **Security** - Security-related decisions
                - **Performance** - Performance and scalability decisions

                ---
                *Generated by YAPPC AI ADR Generator*
                """,
                            LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));

            Files.writeString(adrPath.resolve("README.md"), indexContent);
        }

        private void createConfigFile(Path adrPath) throws IOException {
            String configContent
                    = String.format(
                            """
                # ADR Configuration

                ## Settings

                ```yaml
                adr:
                  default_template: %s
                  numbering_scheme: sequential  # sequential, date, custom
                  auto_update_index: true
                  validate_on_create: true
                  ai_assistance: true

                project:
                  type: "Java Application"
                  technologies:
                    - "Java"
                    - "Spring Boot"
                    - "Gradle"
                  architectural_style: "Layered Architecture"

                templates:
                  standard: "Standard ADR with full sections"
                  brief: "Brief ADR for simple decisions"
                  detailed: "Detailed ADR for complex decisions"
                  y_statement: "Y-Statement format ADR"
                  madr: "Markdown Any Decision Records format"
                ```

                ## Usage Guidelines

                1. Use appropriate template based on decision complexity
                2. Always fill in rationale and consequences
                3. Document alternatives considered
                4. Update index when creating new ADRs
                5. Review and update ADRs when decisions change

                ---
                *Created by YAPPC AI ADR Generator on %s*
                """,
                            defaultTemplate,
                            LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));

            Files.writeString(adrPath.resolve("adr-config.md"), configContent);
        }

        private void createTemplateFiles(Path templatesPath) throws IOException {
            // Create example templates for each type
            for (ADRTemplateType templateType : ADRTemplateType.values()) {
                String templateContent = createTemplateContent(templateType);
                Files.writeString(
                        templatesPath.resolve(templateType.name().toLowerCase() + "-template.md"),
                        templateContent);
            }
        }

        private String createTemplateContent(ADRTemplateType templateType) {
            return switch (templateType) {
                case STANDARD ->
                    """
                    # [ADR Number]: [Title]

                    **Status:** [Proposed | Accepted | Deprecated | Superseded]
                    **Date:** [YYYY-MM-DD]
                    **Decision Type:** [Architectural | Technological | Process | Quality | Security | Performance]

                    ## Context

                    [Describe the situation that led to this decision]

                    ## Decision

                    [State the decision that was made]

                    ## Rationale

                    [Explain why this decision was made]

                    ## Alternatives Considered

                    - Alternative 1: [Description]
                    - Alternative 2: [Description]

                    ## Consequences

                    ### Positive
                    - [Positive consequence 1]
                    - [Positive consequence 2]

                    ### Negative
                    - [Negative consequence 1]
                    - [Trade-off 1]

                    ## Implementation Notes

                    - Timeline: [Timeline]
                    - Responsible parties: [Names/Teams]
                    - Success metrics: [How to measure success]

                    ## Related Decisions

                    - [Link to related ADR 1]
                    - [Link to related ADR 2]
                    """;

                case BRIEF ->
                    """
                    # [Title]

                    **Status:** [Status] | **Date:** [Date]

                    ## Decision

                    [What was decided]

                    ## Rationale

                    [Why this was decided]

                    ## Impact

                    [Key impacts and consequences]
                    """;

                case Y_STATEMENT ->
                    """
                    # [Title]

                    **Date:** [Date]
                    **Status:** [Status]

                    ## Decision Statement

                    In the context of [context],
                    facing [concern],
                    we decided for [option]
                    to achieve [benefit],
                    accepting [downside].

                    ## Rationale

                    [Detailed rationale]

                    ## Implications

                    [Key implications]
                    """;

                default ->
                    """
                    # Template for """
                    + templateType
                    + """

                    [Template content to be defined]
                    """;
            };
        }

        private void createADRReadme(Path adrPath) throws IOException {
            String readmeContent
                    = """
                # ADR Directory

                This directory contains Architecture Decision Records (ADRs) for the project.

                ## Quick Start

                ```bash
                # Create a new ADR
                yappc adr create "Use PostgreSQL for primary database"

                # Validate an existing ADR
                yappc adr validate 001-database-selection.md

                # Get AI suggestions for alternatives
                yappc adr suggest "Choose between REST and GraphQL APIs"

                # List all ADRs
                yappc adr list
                ```

                ## File Naming Convention

                - Format: `NNN-title-with-hyphens.md`
                - Examples: `001-database-selection.md`, `002-api-design.md`

                ## Best Practices

                1. Keep titles concise but descriptive
                2. Always document the rationale
                3. Consider and document alternatives
                4. Update status when decisions change
                5. Link related decisions

                ## AI Assistance Features

                - Template selection based on decision complexity
                - Alternative suggestions and trade-off analysis
                - Quality validation and improvement suggestions
                - Follow-up questions to improve completeness
                """;

            Files.writeString(adrPath.resolve("README.md"), readmeContent);
        }
    }

    @CommandLine.Command(name = "create", description = "Create a new ADR with AI assistance")
    static class CreateCommand implements Runnable {

        @CommandLine.Parameters(index = "0", description = "ADR title")
        private String title;

        @CommandLine.Option(
                names = {"-c", "--context"},
                description = "Decision context")
        private String context = "";

        @CommandLine.Option(
                names = {"-d", "--decision"},
                description = "The decision made")
        private String decision = "";

        @CommandLine.Option(
                names = {"-r", "--rationale"},
                description = "Decision rationale")
        private String rationale = "";

        @CommandLine.Option(
                names = {"-a", "--alternatives"},
                description = "Comma-separated list of alternatives considered")
        private String alternatives = "";

        @CommandLine.Option(
                names = {"-s", "--stakeholders"},
                description = "Comma-separated list of stakeholders")
        private String stakeholders = "";

        @CommandLine.Option(
                names = {"-t", "--template"},
                description = "Template type (${COMPLETION-CANDIDATES})")
        private ADRTemplateType templateType;

        @CommandLine.Option(
                names = {"-o", "--output"},
                description = "Output file path (default: auto-generated)")
        private String outputPath;

        @CommandLine.Option(
                names = {"--interactive", "-i"},
                description = "Interactive mode with AI assistance")
        private boolean interactive = false;

        @CommandLine.Option(
                names = {"--dir"},
                description = "ADR directory",
                defaultValue = "./docs/adr")
        private String adrDirectory;

        @Override
        public void run() {
            try {
                if (interactive) {
                    runInteractiveMode();
                } else {
                    createADR();
                }
                log.info("✅ ADR created successfully!");
            } catch (Exception e) {
                log.error("❌ Error creating ADR: {}", e.getMessage());
                System.exit(1);
            }
        }

        private void runInteractiveMode() throws IOException {
            Scanner scanner = new Scanner(System.in);
            AIADRGenerator generator = new AIADRGenerator();

            log.info("🤖 Interactive ADR Creation with AI Assistance");
            log.info("=".repeat(50));

            // Get basic information
            if (title == null || title.trim().isEmpty()) {
                System.out.print("Decision title: ");
                title = scanner.nextLine();
            }

            System.out.print("Decision context (what led to this decision): ");
            if (context.isEmpty()) {
                context = scanner.nextLine();
            }

            System.out.print("What was decided: ");
            if (decision.isEmpty()) {
                decision = scanner.nextLine();
            }

            // Build initial request
            ADRRequest.Builder builder
                    = ADRRequest.builder()
                            .title(title)
                            .context(context)
                            .decision(decision)
                            .rationale(rationale);

            if (!alternatives.isEmpty()) {
                builder.alternatives(Arrays.asList(alternatives.split(",")));
            }

            if (!stakeholders.isEmpty()) {
                builder.stakeholders(Arrays.asList(stakeholders.split(",")));
            }

            // Add project info
            Map<String, Object> projectInfo = new HashMap<>();
            projectInfo.put("projectType", "Java Application");
            projectInfo.put("technologies", List.of("Java", "Spring Boot", "Gradle"));
            projectInfo.put("architecturalStyle", "Layered Architecture");
            builder.projectInfo(projectInfo);

            ADRRequest request = builder.build();

            // Get AI suggestions
            log.info("\n🤖 AI Analysis in progress...");
            List<String> followUpQuestions = generator.generateFollowUpQuestions(request);

            if (!followUpQuestions.isEmpty()) {
                log.info("\n💡 AI suggests these questions to improve your ADR:");
                for (int i = 0; i < followUpQuestions.size(); i++) {
                    log.info("{}. {}", i + 1, followUpQuestions.get(i));
                }

                System.out.print("\nWould you like to answer these questions? (y/n): ");
                if (scanner.nextLine().toLowerCase().startsWith("y")) {
                    answerFollowUpQuestions(scanner, followUpQuestions, builder);
                    request = builder.build();
                }
            }

            // Get alternatives suggestions if none provided
            if (request.alternatives().isEmpty()) {
                log.info("\n🔄 Getting AI alternative suggestions...");
                DecisionAlternatives aiAlternatives = generator.suggestAlternatives(title, context);

                log.info("\n💡 AI suggests these alternatives:");
                for (int i = 0; i < aiAlternatives.alternatives().size(); i++) {
                    Alternative alt = aiAlternatives.alternatives().get(i);
                    log.info("{}. {} - {}", i + 1, alt.name(), alt.description());
                }

                System.out.print("\nInclude these in your ADR? (y/n): ");
                if (scanner.nextLine().toLowerCase().startsWith("y")) {
                    List<String> altNames
                            = aiAlternatives.alternatives().stream()
                                    .map(Alternative::name)
                                    .collect(Collectors.toList());
                    builder.alternatives(altNames);
                    request = builder.build();
                }
            }

            // Generate ADR
            ADRGenerationResult result = generator.generateADR(request);

            // Show preview
            log.info("\n📄 Generated ADR Preview:");
            log.info("=".repeat(50));
            log.info("{}...", result.content().substring(0, Math.min(500, result.content().length())));
            log.info("=".repeat(50));

            log.info(String.format("Template: %s, Confidence: %.1f%%", result.templateType(), result.confidence() * 100));

            System.out.print("\nSave this ADR? (y/n): ");
            if (scanner.nextLine().toLowerCase().startsWith("y")) {
                saveADR(result);
            }
        }

        private void createADR() throws IOException {
            ADRRequest.Builder builder
                    = ADRRequest.builder()
                            .title(title)
                            .context(context)
                            .decision(decision)
                            .rationale(rationale);

            if (!alternatives.isEmpty()) {
                builder.alternatives(Arrays.asList(alternatives.split(",\\s*")));
            }

            if (!stakeholders.isEmpty()) {
                builder.stakeholders(Arrays.asList(stakeholders.split(",\\s*")));
            }

            // Add project info
            Map<String, Object> projectInfo = new HashMap<>();
            projectInfo.put("projectType", "Java Application");
            projectInfo.put("technologies", List.of("Java", "Spring Boot", "Gradle"));
            projectInfo.put("architecturalStyle", "Layered Architecture");
            builder.projectInfo(projectInfo);

            ADRRequest request = builder.build();

            AIADRGenerator generator = new AIADRGenerator();
            ADRGenerationResult result = generator.generateADR(request);

            saveADR(result);

            log.info(String.format("Generated %s template with %.1f%% confidence", result.templateType(), result.confidence() * 100));

            if (!result.recommendations().isEmpty()) {
                log.info("\n💡 AI Recommendations:");
                result.recommendations().forEach(rec ->
                    log.info("  - {}", rec));
            }
        }
        private void answerFollowUpQuestions( Scanner scanner, List<String> questions, ADRRequest.Builder builder) {
            for (String question : questions) {
                    log.info("\n{}", question);
                System.out.print("Answer: ");
                String answer = scanner.nextLine().trim();

                if (!answer.isEmpty()) {
                    // Update builder based on question type
                    if (question.toLowerCase().contains("context")
                            || question.toLowerCase().contains("problem")) {
                        builder.context(builder.build().context() + " " + answer);
                    } else if (question.toLowerCase().contains("rationale")
                            || question.toLowerCase().contains("why")) {
                        builder.rationale(builder.build().rationale() + " " + answer);
                    } else if (question.toLowerCase().contains("alternative")) {
                        List<String> currentAlts = new ArrayList<>(builder.build().alternatives());
                        currentAlts.add(answer);
                        builder.alternatives(currentAlts);
                    } else if (question.toLowerCase().contains("consequence")
                            || question.toLowerCase().contains("impact")) {
                        List<String> currentCons = new ArrayList<>(builder.build().consequences());
                        currentCons.add(answer);
                        builder.consequences(currentCons);
                    } else if (question.toLowerCase().contains("stakeholder")) {
                        List<String> currentStake = new ArrayList<>(builder.build().stakeholders());
                        currentStake.add(answer);
                        builder.stakeholders(currentStake);
                    }
                }
            }
        }

        private void saveADR(ADRGenerationResult result) throws IOException {
            Path adrDir = Paths.get(adrDirectory);
            Files.createDirectories(adrDir);

            String filename;
            if (outputPath != null) {
                filename = outputPath;
            } else {
                // Auto-generate filename
                int nextNumber = getNextADRNumber(adrDir);
                String titleSlug
                        = title.toLowerCase()
                                .replaceAll("[^a-zA-Z0-9\\s]", "")
                                .replaceAll("\\s+", "-");
                filename = String.format("%03d-%s.md", nextNumber, titleSlug);
            }

            Path adrFile = adrDir.resolve(filename);
            Files.writeString(adrFile, result.content());

            log.info("📄 ADR saved to: {}", adrFile.toAbsolutePath());

            // Update index if it exists
            updateADRIndex(adrDir, filename, result);
        }

        private int getNextADRNumber(Path adrDir) throws IOException {
            if (!Files.exists(adrDir)) {
                return 1;
            }

            return Files.list(adrDir)
                    .filter(path -> path.getFileName().toString().matches("\\d{3}-.*\\.md"))
                    .mapToInt(
                            path -> {
                                String filename = path.getFileName().toString();
                                return Integer.parseInt(filename.substring(0, 3));
                            })
                    .max()
                    .orElse(0)
                    + 1;
        }

        private void updateADRIndex(Path adrDir, String filename, ADRGenerationResult result) {
            try {
                Path indexFile = adrDir.resolve("README.md");
                if (Files.exists(indexFile)) {
                    String content = Files.readString(indexFile);

                    // Add new entry to the index table
                    String newEntry
                            = String.format(
                                    "| %s | [%s](%s) | %s | %s | %s |",
                                    filename.substring(0, 3),
                                    title,
                                    filename,
                                    "Proposed", // Default status
                                    LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
                                    result.metadata().getOrDefault("decisionType", "Unknown"));

                    // Insert after the header row
                    String updatedContent
                            = content.replaceFirst(
                                    "(\\| Number \\| Title \\| Status \\| Date \\| Decision Type"
                                    + " \\|\\n"
                                    + "\\|[^\\n"
                                    + "]+\\|\\n"
                                    + ")",
                                    "$1" + newEntry + "\n");

                    Files.writeString(indexFile, updatedContent);
                    log.info("📊 Updated ADR index");
                }
            } catch (IOException e) {
                log.info("⚠️  Could not update ADR index: {}", e.getMessage());
            }
        }
    }

    @CommandLine.Command(name = "validate", description = "Validate existing ADR for quality")
    static class ValidateCommand implements Runnable {

        @CommandLine.Parameters(index = "0", description = "ADR file path")
        private String adrFile;

        @CommandLine.Option(
                names = {"-v", "--verbose"},
                description = "Show detailed validation report")
        private boolean verbose = false;

        @Override
        public void run() {
            try {
                validateADR();
            } catch (Exception e) {
                log.error("❌ Error validating ADR: {}", e.getMessage());
                System.exit(1);
            }
        }

        private void validateADR() throws IOException {
            Path adrPath = Paths.get(adrFile);
            if (!Files.exists(adrPath)) {
                log.error("❌ ADR file not found: {}", adrFile);
                return;
            }

            String content = Files.readString(adrPath);

            AIADRGenerator generator = new AIADRGenerator();
            ADRValidationResult result = generator.validateADR(content);

            // Display results
            log.info("📊 ADR Validation Results for {}", adrPath.getFileName());
            log.info("=".repeat(50));

            if (result.isValid()) {
                log.info("✅ ADR is valid");
            } else {
                log.info("❌ ADR has validation issues");
            }

            log.info(String.format("Quality Score: %.1f/10", result.qualityScore() * 10));

            if (!result.issues().isEmpty()) {
                log.info("\n🔍 Issues Found:");
                for (ValidationIssue issue : result.issues()) {
                    String icon
                            = switch (issue.severity()) {
                        case CRITICAL ->
                            "🔴";
                        case ERROR ->
                            "🟠";
                        case WARNING ->
                            "🟡";
                        case INFO ->
                            "🔵";
                    };

                    log.info("{} [{}] {}: {}", icon, issue.severity(), issue.section(), issue.message());

                    if (verbose && issue.suggestion() != null) {
                        log.info("   💡 {}", issue.suggestion());
                    }
                }
            }

            if (!result.suggestions().isEmpty()) {
                log.info("\n💡 Improvement Suggestions:");
                result.suggestions().forEach(suggestion ->
                    log.info("  - {}", suggestion));
            }
            // Quality breakdown
            if (verbose) {
                    log.info("\n📈 Quality Breakdown:");
                log.info("  - Structure: {}", (content.contains("# ") ? "✅" : "❌"));
                log.info("  - Completeness: {}", (content.length() > 200 ? "✅" : "❌"));
                log.info("  - Clarity: {}", (result.qualityScore() > 0.7 ? "✅" : "❌"));
            }
        }
    }

    @CommandLine.Command(name = "list", description = "List all ADRs in the project")
    static class ListCommand implements Runnable {

        @CommandLine.Option(
                names = {"--dir"},
                description = "ADR directory",
                defaultValue = "./docs/adr")
        private String adrDirectory;

        @CommandLine.Option(
                names = {"-s", "--status"},
                description = "Filter by status")
        private String status;

        @CommandLine.Option(
                names = {"-t", "--type"},
                description = "Filter by decision type")
        private String decisionType;

        @CommandLine.Option(
                names = {"--json"},
                description = "Output as JSON")
        private boolean jsonOutput = false;

        @Override
        public void run() {
            try {
                listADRs();
            } catch (Exception e) {
                log.error("❌ Error listing ADRs: {}", e.getMessage());
                System.exit(1);
            }
        }

        private void listADRs() throws IOException {
            Path adrDir = Paths.get(adrDirectory);

            if (!Files.exists(adrDir)) {
                log.info("📁 No ADR directory found. Run 'yappc adr init' to initialize.");
                return;
            }

            List<Path> adrFiles
                    = Files.list(adrDir)
                            .filter(path -> path.getFileName().toString().endsWith(".md"))
                            .filter(path -> !path.getFileName().toString().equals("README.md"))
                            .filter(path -> !path.getFileName().toString().contains("template"))
                            .sorted()
                            .collect(Collectors.toList());

            if (adrFiles.isEmpty()) {
                log.info("📄 No ADRs found in {}", adrDirectory);
                return;
            }

            if (jsonOutput) {
                outputAsJson(adrFiles);
            } else {
                outputAsTable(adrFiles);
            }
        }

        private void outputAsTable(List<Path> adrFiles) throws IOException {
            log.info("📚 Architecture Decision Records");
            log.info("=".repeat(80));
            log.info(String.format("%-6s %-40s %-12s %-12s", "Number", "Title", "Status", "Date"));
            log.info("-".repeat(80));

            for (Path adrFile : adrFiles) {
                String content = Files.readString(adrFile);
                String filename = adrFile.getFileName().toString();

                // Extract information from content
                String number = filename.substring(0, Math.min(3, filename.length()));
                String title = extractTitle(content);
                String adrStatus = extractStatus(content);
                String date = extractDate(content);

                // Apply filters
                if (status != null && !adrStatus.toLowerCase().contains(status.toLowerCase())) {
                    continue;
                }

                String icon
                        = switch (adrStatus.toLowerCase()) {
                    case "accepted" ->
                        "🟢";
                    case "proposed" ->
                        "🟡";
                    case "deprecated" ->
                        "🔴";
                    case "superseded" ->
                        "⚪";
                    default ->
                        "❓";
                };

                log.info(String.format("%s %-3s %-40s %-12s %-12s", icon,
                        number,
                        title.length() > 38 ? title.substring(0, 35) + "..." : title,
                        adrStatus,
                        date));
            }
        }

        private void outputAsJson(List<Path> adrFiles) throws IOException {
            log.info("[");
            for (int i = 0; i < adrFiles.size(); i++) {
                Path adrFile = adrFiles.get(i);
                String content = Files.readString(adrFile);
                String filename = adrFile.getFileName().toString();

                log.info("""
                  {
                    "number": "%s",
                    "title": "%s",
                    "status": "%s",
                    "date": "%s",
                    "file": "%s",
                    "path": "%s"
                  }""",
                        filename.substring(0, Math.min(3, filename.length())),
                        extractTitle(content).replace("\"", "\\\""),
                        extractStatus(content),
                        extractDate(content),
                        filename,
                        adrFile.toAbsolutePath());

                if (i < adrFiles.size() - 1) {
                    log.info(",");
                } else {
                    log.info("");;
                }
            }
            log.info("]");
        }

        private String extractTitle(String content) {
            return content.lines()
                    .filter(line -> line.startsWith("# "))
                    .map(line -> line.substring(2).trim())
                    .findFirst()
                    .orElse("Unknown Title");
        }

        private String extractStatus(String content) {
            return content.lines()
                    .filter(line -> line.toLowerCase().contains("status"))
                    .map(line -> line.replaceAll(".*[Ss]tatus[^a-zA-Z]*([a-zA-Z]+).*", "$1"))
                    .findFirst()
                    .orElse("Unknown");
        }

        private String extractDate(String content) {
            return content.lines()
                    .filter(line -> line.toLowerCase().contains("date"))
                    .map(line -> line.replaceAll(".*([0-9]{4}-[0-9]{2}-[0-9]{2}).*", "$1"))
                    .filter(line -> line.matches("\\d{4}-\\d{2}-\\d{2}"))
                    .findFirst()
                    .orElse("Unknown");
        }
    }

    @CommandLine.Command(
            name = "suggest",
            description = "Get AI suggestions for decision alternatives")
    static class SuggestCommand implements Runnable {

        @CommandLine.Parameters(index = "0", description = "Decision title or description")
        private String decisionTitle;

        @CommandLine.Option(
                names = {"-c", "--context"},
                description = "Additional context for the decision")
        private String context = "";

        @CommandLine.Option(
                names = {"--detailed"},
                description = "Show detailed analysis of alternatives")
        private boolean detailed = false;

        @Override
        public void run() {
            try {
                getSuggestions();
            } catch (Exception e) {
                log.error("❌ Error getting suggestions: {}", e.getMessage());
                System.exit(1);
            }
        }

        private void getSuggestions() {
            log.info("🤖 AI Decision Analysis");
            log.info("=".repeat(50));

            AIADRGenerator generator = new AIADRGenerator();
            DecisionAlternatives alternatives
                    = generator.suggestAlternatives(decisionTitle, context);

            log.info("Decision: {}", alternatives.originalDecision());
            log.info("");;

            log.info("💡 Suggested Alternatives:");
            for (int i = 0; i < alternatives.alternatives().size(); i++) {
                Alternative alt = alternatives.alternatives().get(i);
                log.info("\n{}. {}", i + 1, alt.name());
                log.info("   {}", alt.description());
                log.info(String.format("   📊 Feasibility: %.1f/10 | Impact: %.1f/10", alt.feasibilityScore() * 10, alt.impactScore() * 10));

                if (detailed) {
                    log.info("   ✅ Pros:");
                    alt.pros().forEach(pro ->
                        log.info("     • {}", pro));
                    log.info("   ❌ Cons:");
                    alt.cons().forEach(con ->
                        log.info("     • {}", con));
                }
            }
            if (detailed && alternatives.comparisonMatrix() != null) {
                log.info("\n📊 Comparison Matrix:");
                ComparisonMatrix matrix = alternatives.comparisonMatrix();

                log.info(String.format("%-20s", "Alternative"));
                matrix.criteria().forEach(criterion -> log.info(String.format("%-12s", criterion)));
                log.info("");
                log.info("-".repeat(20 + matrix.criteria().size() * 12));

                for (Alternative alt : alternatives.alternatives()) {
                    log.info(String.format("%-20s", alt.name().substring(0, Math.min(18, alt.name().length()))));
                    for (String criterion : matrix.criteria()) {
                        Double score = matrix.scores().get(alt.name()).get(criterion);
                        log.info(String.format("%-12s", score != null ? String.format("%.1f", score * 10) : "N/A"));
                    }
                    log.info("");
                }
            }

            log.info("\n🎯 AI Recommendations:");
            alternatives.recommendations().forEach(rec ->
                log.info("  • {}", rec));
        }
    }
}