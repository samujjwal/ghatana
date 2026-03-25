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

package com.ghatana.yappc.core.docs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Auto-documentation and diagram generator for projects.
 *
 * <p>Week 10 Day 47: Auto-doc & diagram generator (`docs generate` → README/ADR/Mermaid).
 *
 * @doc.type class
 * @doc.purpose Auto-documentation and diagram generator for projects.
 * @doc.layer platform
 * @doc.pattern Generator
 */
public class AutoDocGenerator {

    private static final Logger log = LoggerFactory.getLogger(AutoDocGenerator.class);

    private final ProjectAnalyzer projectAnalyzer;
    private final DiagramGenerator diagramGenerator;
    private final TemplateEngine templateEngine;

    public AutoDocGenerator() {
        this.projectAnalyzer = new ProjectAnalyzer();
        this.diagramGenerator = new DiagramGenerator();
        this.templateEngine = new TemplateEngine();
    }

    /**
 * Generates comprehensive project documentation. */
    public DocumentationResult generateDocumentation(DocumentationSpec spec) throws IOException {
        log.info("📚 Analyzing project structure...");
        ProjectStructure structure = projectAnalyzer.analyzeProject(spec.projectPath());

        List<GeneratedDocument> documents = new ArrayList<>();
        Map<String, Object> generationMetrics = new HashMap<>();

        // Generate README if requested
        if (spec.generateReadme()) {
            documents.add(generateReadme(structure, spec));
            generationMetrics.put("readmeGenerated", true);
        }

        // Generate ADRs if requested
        if (spec.generateAdrs()) {
            List<GeneratedDocument> adrs = generateADRs(structure, spec);
            documents.addAll(adrs);
            generationMetrics.put("adrsGenerated", adrs.size());
        }

        // Generate diagrams if requested
        if (spec.generateDiagrams()) {
            List<GeneratedDocument> diagrams = generateDiagrams(structure, spec);
            documents.addAll(diagrams);
            generationMetrics.put("diagramsGenerated", diagrams.size());
        }

        // Generate API documentation if requested
        if (spec.generateApiDocs()) {
            List<GeneratedDocument> apiDocs = generateApiDocumentation(structure, spec);
            documents.addAll(apiDocs);
            generationMetrics.put("apiDocsGenerated", apiDocs.size());
        }

        // Generate project overview
        if (spec.generateOverview()) {
            documents.add(generateProjectOverview(structure, spec));
            generationMetrics.put("overviewGenerated", true);
        }

        return new DocumentationResult(
                documents, generationMetrics, Instant.now(), structure.analysisMetrics());
    }

    private GeneratedDocument generateReadme(ProjectStructure structure, DocumentationSpec spec) {
        Map<String, Object> templateData = new HashMap<>();
        templateData.put("projectName", structure.projectName());
        templateData.put("description", structure.description());
        templateData.put("modules", structure.modules());
        templateData.put("dependencies", structure.dependencies());
        templateData.put("buildTool", structure.buildTool());
        templateData.put("languages", structure.languages());
        templateData.put("frameworks", structure.frameworks());
        templateData.put("hasTests", structure.hasTests());
        templateData.put("testFrameworks", structure.testFrameworks());
        templateData.put("hasDocker", structure.hasDocker());
        templateData.put("hasCI", structure.hasCI());
        templateData.put("ciPlatforms", structure.ciPlatforms());
        templateData.put("generatedAt", Instant.now());

        String readmeContent = templateEngine.processTemplate("readme.md.hbs", templateData);

        return new GeneratedDocument(
                "README.md",
                DocumentType.README,
                readmeContent,
                spec.outputPath().resolve("README.md"),
                Map.of(
                        "sections", List.of("overview", "installation", "usage", "contributing"),
                        "hasCodeExamples", structure.hasCodeExamples(),
                        "hasDiagrams", spec.generateDiagrams()));
    }

    private List<GeneratedDocument> generateADRs(
            ProjectStructure structure, DocumentationSpec spec) {
        List<GeneratedDocument> adrs = new ArrayList<>();

        // Generate architecture decision records based on project analysis
        List<ArchitecturalDecision> decisions = identifyArchitecturalDecisions(structure);

        for (ArchitecturalDecision decision : decisions) {
            String adrContent = generateADRContent(decision, structure);
            String filename =
                    String.format(
                            "ADR-%03d-%s.md",
                            decisions.indexOf(decision) + 1,
                            decision.title().toLowerCase().replaceAll("[^a-z0-9]+", "-"));

            adrs.add(
                    new GeneratedDocument(
                            filename,
                            DocumentType.ADR,
                            adrContent,
                            spec.outputPath().resolve("docs/decisions").resolve(filename),
                            Map.of(
                                    "status", decision.status(),
                                    "decisionDate", decision.date(),
                                    "tags", decision.tags())));
        }

        return adrs;
    }

    private List<GeneratedDocument> generateDiagrams(
            ProjectStructure structure, DocumentationSpec spec) {
        List<GeneratedDocument> diagrams = new ArrayList<>();

        // Generate system architecture diagram
        if (structure.modules().size() > 1) {
            String architectureDiagram = diagramGenerator.generateArchitectureDiagram(structure);
            diagrams.add(
                    new GeneratedDocument(
                            "architecture.mmd",
                            DocumentType.DIAGRAM,
                            architectureDiagram,
                            spec.outputPath().resolve("docs/diagrams").resolve("architecture.mmd"),
                            Map.of("diagramType", "architecture", "format", "mermaid")));
        }

        // Generate dependency diagram
        if (!structure.dependencies().isEmpty()) {
            String dependencyDiagram = diagramGenerator.generateDependencyDiagram(structure);
            diagrams.add(
                    new GeneratedDocument(
                            "dependencies.mmd",
                            DocumentType.DIAGRAM,
                            dependencyDiagram,
                            spec.outputPath().resolve("docs/diagrams").resolve("dependencies.mmd"),
                            Map.of("diagramType", "dependencies", "format", "mermaid")));
        }

        // Generate class diagrams for Java projects
        if (structure.languages().contains("Java")) {
            List<String> classDiagrams = diagramGenerator.generateClassDiagrams(structure);
            for (int i = 0; i < classDiagrams.size(); i++) {
                String filename = String.format("classes-%d.mmd", i + 1);
                diagrams.add(
                        new GeneratedDocument(
                                filename,
                                DocumentType.DIAGRAM,
                                classDiagrams.get(i),
                                spec.outputPath().resolve("docs/diagrams").resolve(filename),
                                Map.of("diagramType", "class", "format", "mermaid", "index", i)));
            }
        }

        // Generate sequence diagrams for API flows
        if (structure.hasApiEndpoints()) {
            List<String> sequenceDiagrams = diagramGenerator.generateSequenceDiagrams(structure);
            for (int i = 0; i < sequenceDiagrams.size(); i++) {
                String filename = String.format("api-flow-%d.mmd", i + 1);
                diagrams.add(
                        new GeneratedDocument(
                                filename,
                                DocumentType.DIAGRAM,
                                sequenceDiagrams.get(i),
                                spec.outputPath().resolve("docs/diagrams").resolve(filename),
                                Map.of(
                                        "diagramType",
                                        "sequence",
                                        "format",
                                        "mermaid",
                                        "index",
                                        i)));
            }
        }

        return diagrams;
    }

    private List<GeneratedDocument> generateApiDocumentation(
            ProjectStructure structure, DocumentationSpec spec) {
        List<GeneratedDocument> apiDocs = new ArrayList<>();

        if (!structure.apiEndpoints().isEmpty()) {
            // Generate OpenAPI specification
            String openApiSpec = generateOpenApiSpec(structure);
            apiDocs.add(
                    new GeneratedDocument(
                            "openapi.yaml",
                            DocumentType.API_SPEC,
                            openApiSpec,
                            spec.outputPath().resolve("docs/api").resolve("openapi.yaml"),
                            Map.of(
                                    "format",
                                    "openapi3",
                                    "endpoints",
                                    structure.apiEndpoints().size())));

            // Generate API reference documentation
            String apiReference = generateApiReference(structure);
            apiDocs.add(
                    new GeneratedDocument(
                            "api-reference.md",
                            DocumentType.API_DOCS,
                            apiReference,
                            spec.outputPath().resolve("docs/api").resolve("api-reference.md"),
                            Map.of("endpoints", structure.apiEndpoints().size())));
        }

        return apiDocs;
    }

    private GeneratedDocument generateProjectOverview(
            ProjectStructure structure, DocumentationSpec spec) {
        Map<String, Object> templateData = new HashMap<>();
        templateData.put("project", structure);
        templateData.put("metrics", calculateProjectMetrics(structure));
        templateData.put("recommendations", generateProjectRecommendations(structure));
        templateData.put("generatedAt", Instant.now());

        String overviewContent =
                templateEngine.processTemplate("project-overview.md.hbs", templateData);

        return new GeneratedDocument(
                "PROJECT-OVERVIEW.md",
                DocumentType.OVERVIEW,
                overviewContent,
                spec.outputPath().resolve("docs").resolve("PROJECT-OVERVIEW.md"),
                Map.of(
                        "includesMetrics", true,
                        "includesRecommendations", true,
                        "sections",
                                List.of("summary", "architecture", "metrics", "recommendations")));
    }

    private List<ArchitecturalDecision> identifyArchitecturalDecisions(ProjectStructure structure) {
        List<ArchitecturalDecision> decisions = new ArrayList<>();

        // Identify build tool decisions
        decisions.add(
                new ArchitecturalDecision(
                        "Build Tool Selection",
                        "We chose " + structure.buildTool() + " as our build tool",
                        "ACCEPTED",
                        Instant.now(),
                        List.of("build", "tooling"),
                        "Based on project requirements and team familiarity",
                        List.of("Maven", "Gradle", "npm", "Cargo"),
                        "Better integration with our development workflow"));

        // Identify framework decisions
        if (!structure.frameworks().isEmpty()) {
            decisions.add(
                    new ArchitecturalDecision(
                            "Framework Selection",
                            "We selected "
                                    + String.join(", ", structure.frameworks())
                                    + " as our primary frameworks",
                            "ACCEPTED",
                            Instant.now(),
                            List.of("framework", "architecture"),
                            "Framework choice based on project requirements and scalability needs",
                            List.of(), // Would analyze alternatives in real implementation
                            "Provides the best balance of features and performance for our use"
                                    + " case"));
        }

        // Identify testing strategy decisions
        if (structure.hasTests()) {
            decisions.add(
                    new ArchitecturalDecision(
                            "Testing Strategy",
                            "We implemented comprehensive testing using "
                                    + String.join(", ", structure.testFrameworks()),
                            "ACCEPTED",
                            Instant.now(),
                            List.of("testing", "quality"),
                            "Ensuring code quality and reliability through automated testing",
                            List.of(), // Would analyze testing alternatives
                            "Provides good coverage and integration with our CI/CD pipeline"));
        }

        // Identify deployment decisions
        if (structure.hasDocker()) {
            decisions.add(
                    new ArchitecturalDecision(
                            "Containerization Strategy",
                            "We adopted Docker for application containerization",
                            "ACCEPTED",
                            Instant.now(),
                            List.of("deployment", "containerization"),
                            "Standardizing deployment across environments",
                            List.of("VM", "Native", "Other containers"),
                            "Provides consistent deployment and easier scaling"));
        }

        return decisions;
    }

    private String generateADRContent(ArchitecturalDecision decision, ProjectStructure structure) {
        return String.format(
                """
            # %s

            **Status:** %s
            **Date:** %s
            **Tags:** %s

            ## Context

            %s

            ## Decision

            %s

            ## Rationale

            %s

            ## Consequences

            - **Positive:**
              - Improved development workflow
              - Better maintainability
              - Enhanced team productivity

            - **Negative:**
              - Learning curve for new team members
              - Additional tooling complexity

            ## Related Decisions

            - This decision impacts the overall project architecture
            - Related to our CI/CD pipeline strategy

            ---
            *This ADR was auto-generated by YAPPC on %s*
            """,
                decision.title(),
                decision.status(),
                decision.date(),
                String.join(", ", decision.tags()),
                decision.context(),
                decision.decision(),
                decision.rationale(),
                Instant.now());
    }

    private Map<String, Object> calculateProjectMetrics(ProjectStructure structure) {
        Map<String, Object> metrics = new HashMap<>();

        metrics.put("moduleCount", structure.modules().size());
        metrics.put("dependencyCount", structure.dependencies().size());
        metrics.put("languageCount", structure.languages().size());
        metrics.put("frameworkCount", structure.frameworks().size());

        // Calculate complexity score
        int complexityScore =
                structure.modules().size() * 2
                        + structure.dependencies().size()
                        + structure.languages().size() * 3;

        String complexityLevel;
        if (complexityScore < 10) {
            complexityLevel = "Simple";
        } else if (complexityScore < 25) {
            complexityLevel = "Moderate";
        } else if (complexityScore < 50) {
            complexityLevel = "Complex";
        } else {
            complexityLevel = "Very Complex";
        }

        metrics.put("complexityScore", complexityScore);
        metrics.put("complexityLevel", complexityLevel);

        return metrics;
    }

    private List<String> generateProjectRecommendations(ProjectStructure structure) {
        List<String> recommendations = new ArrayList<>();

        if (!structure.hasTests()) {
            recommendations.add("Consider adding unit tests to improve code quality");
        }

        if (!structure.hasDocker()) {
            recommendations.add("Consider adding Docker support for consistent deployments");
        }

        if (!structure.hasCI()) {
            recommendations.add("Set up CI/CD pipeline for automated builds and deployments");
        }

        if (structure.dependencies().size() > 20) {
            recommendations.add("Review dependencies to identify unused or outdated packages");
        }

        if (structure.modules().size() > 10) {
            recommendations.add(
                    "Consider modularizing the project further for better maintainability");
        }

        return recommendations;
    }

    private String generateOpenApiSpec(ProjectStructure structure) {
        StringBuilder openApi = new StringBuilder();
        openApi.append("openapi: 3.0.0\n");
        openApi.append("info:\n");
        openApi.append("  title: ").append(structure.projectName()).append(" API\n");
        openApi.append("  version: 1.0.0\n");
        openApi.append("  description: ").append(structure.description()).append("\n");
        openApi.append("paths:\n");

        for (ApiEndpoint endpoint : structure.apiEndpoints()) {
            openApi.append("  ").append(endpoint.path()).append(":\n");
            openApi.append("    ").append(endpoint.method().toLowerCase()).append(":\n");
            openApi.append("      summary: ").append(endpoint.summary()).append("\n");
            openApi.append("      responses:\n");
            openApi.append("        '200':\n");
            openApi.append("          description: Successful response\n");
        }

        return openApi.toString();
    }

    private String generateApiReference(ProjectStructure structure) {
        StringBuilder apiRef = new StringBuilder();
        apiRef.append("# API Reference\n\n");
        apiRef.append("## Endpoints\n\n");

        for (ApiEndpoint endpoint : structure.apiEndpoints()) {
            apiRef.append("### ")
                    .append(endpoint.method())
                    .append(" ")
                    .append(endpoint.path())
                    .append("\n\n");
            apiRef.append(endpoint.summary()).append("\n\n");
            if (!endpoint.description().isEmpty()) {
                apiRef.append(endpoint.description()).append("\n\n");
            }
        }

        return apiRef.toString();
    }

    // Data classes and interfaces
    public record DocumentationSpec(
            Path projectPath,
            Path outputPath,
            boolean generateReadme,
            boolean generateAdrs,
            boolean generateDiagrams,
            boolean generateApiDocs,
            boolean generateOverview,
            Map<String, Object> options) {
        public static DocumentationSpec defaultSpec(Path projectPath) {
            return new DocumentationSpec(
                    projectPath,
                    projectPath.resolve("docs"),
                    true,
                    true,
                    true,
                    true,
                    true,
                    Map.of());
        }
    }

    public record DocumentationResult(
            List<GeneratedDocument> documents,
            Map<String, Object> metrics,
            Instant generatedAt,
            Map<String, Object> analysisMetrics) {}

    public record GeneratedDocument(
            String filename,
            DocumentType type,
            String content,
            Path outputPath,
            Map<String, Object> metadata) {}

    public enum DocumentType {
        README,
        ADR,
        DIAGRAM,
        API_SPEC,
        API_DOCS,
        OVERVIEW,
        GUIDE
    }

    public record ArchitecturalDecision(
            String title,
            String decision,
            String status,
            Instant date,
            List<String> tags,
            String context,
            List<String> alternatives,
            String rationale) {}

    public record ProjectStructure(
            String projectName,
            String description,
            List<String> modules,
            List<String> dependencies,
            String buildTool,
            List<String> languages,
            List<String> frameworks,
            boolean hasTests,
            List<String> testFrameworks,
            boolean hasDocker,
            boolean hasCI,
            List<String> ciPlatforms,
            boolean hasCodeExamples,
            boolean hasApiEndpoints,
            List<ApiEndpoint> apiEndpoints,
            Map<String, Object> analysisMetrics) {}

    public record ApiEndpoint(String path, String method, String summary, String description) {}

    // Helper classes
    private static class ProjectAnalyzer {
        public ProjectStructure analyzeProject(Path projectPath) throws IOException {
            String projectName = projectPath.getFileName().toString();

            // Analyze project structure
            List<String> modules = findModules(projectPath);
            List<String> dependencies = findDependencies(projectPath);
            String buildTool = detectBuildTool(projectPath);
            List<String> languages = detectLanguages(projectPath);
            List<String> frameworks = detectFrameworks(projectPath);
            boolean hasTests = detectTests(projectPath);
            List<String> testFrameworks = detectTestFrameworks(projectPath);
            boolean hasDocker = Files.exists(projectPath.resolve("Dockerfile"));
            boolean hasCI = detectCI(projectPath);
            List<String> ciPlatforms = detectCIPlatforms(projectPath);
            List<ApiEndpoint> apiEndpoints = findApiEndpoints(projectPath);

            Map<String, Object> metrics =
                    Map.of(
                            "filesAnalyzed", countFiles(projectPath),
                            "linesOfCode", countLinesOfCode(projectPath),
                            "analysisTime", Instant.now());

            return new ProjectStructure(
                    projectName,
                    "Auto-generated project description", // Would be smarter in real implementation
                    modules,
                    dependencies,
                    buildTool,
                    languages,
                    frameworks,
                    hasTests,
                    testFrameworks,
                    hasDocker,
                    hasCI,
                    ciPlatforms,
                    true, // hasCodeExamples - simplified
                    !apiEndpoints.isEmpty(),
                    apiEndpoints,
                    metrics);
        }

        private List<String> findModules(Path projectPath) throws IOException {
            List<String> modules = new ArrayList<>();

            // Look for Gradle modules
            Path settingsGradle = projectPath.resolve("settings.gradle");
            if (Files.exists(settingsGradle)) {
                String content = Files.readString(settingsGradle);
                Pattern pattern = Pattern.compile("include[\\s]*['\"]([^'\"]+)['\"]");
                Matcher matcher = pattern.matcher(content);
                while (matcher.find()) {
                    modules.add(matcher.group(1));
                }
            }

            // Look for Maven modules
            Path pomXml = projectPath.resolve("pom.xml");
            if (Files.exists(pomXml)) {
                String content = Files.readString(pomXml);
                Pattern pattern = Pattern.compile("<module>([^<]+)</module>");
                Matcher matcher = pattern.matcher(content);
                while (matcher.find()) {
                    modules.add(matcher.group(1));
                }
            }

            // Fallback: look for directories with build files
            if (modules.isEmpty()) {
                Files.list(projectPath)
                        .filter(Files::isDirectory)
                        .filter(
                                dir ->
                                        Files.exists(dir.resolve("build.gradle"))
                                                || Files.exists(dir.resolve("pom.xml"))
                                                || Files.exists(dir.resolve("package.json")))
                        .forEach(dir -> modules.add(dir.getFileName().toString()));
            }

            return modules;
        }

        private List<String> findDependencies(Path projectPath) {
            List<String> dependencies = new ArrayList<>();

            // This would analyze build files for dependencies
            // Simplified implementation
            dependencies.add("spring-boot-starter");
            dependencies.add("junit");
            dependencies.add("mockito");

            return dependencies;
        }

        private String detectBuildTool(Path projectPath) {
            if (Files.exists(projectPath.resolve("build.gradle"))
                    || Files.exists(projectPath.resolve("settings.gradle"))) {
                return "Gradle";
            } else if (Files.exists(projectPath.resolve("pom.xml"))) {
                return "Maven";
            } else if (Files.exists(projectPath.resolve("package.json"))) {
                return "npm";
            } else if (Files.exists(projectPath.resolve("Cargo.toml"))) {
                return "Cargo";
            } else if (Files.exists(projectPath.resolve("go.mod"))) {
                return "Go";
            }
            return "Unknown";
        }

        private List<String> detectLanguages(Path projectPath) throws IOException {
            Set<String> languages = new HashSet<>();

            Files.walk(projectPath)
                    .filter(Files::isRegularFile)
                    .forEach(
                            file -> {
                                String filename = file.getFileName().toString();
                                if (filename.endsWith(".java")) languages.add("Java");
                                else if (filename.endsWith(".ts") || filename.endsWith(".tsx"))
                                    languages.add("TypeScript");
                                else if (filename.endsWith(".js") || filename.endsWith(".jsx"))
                                    languages.add("JavaScript");
                                else if (filename.endsWith(".py")) languages.add("Python");
                                else if (filename.endsWith(".rs")) languages.add("Rust");
                                else if (filename.endsWith(".go")) languages.add("Go");
                                else if (filename.endsWith(".kt")) languages.add("Kotlin");
                            });

            return new ArrayList<>(languages);
        }

        private List<String> detectFrameworks(Path projectPath) {
            List<String> frameworks = new ArrayList<>();

            // Detect Spring Boot
            if (Files.exists(projectPath.resolve("src/main/java"))
                    && containsText(projectPath, "@SpringBootApplication")) {
                frameworks.add("Spring Boot");
            }

            // Detect React
            if (Files.exists(projectPath.resolve("package.json"))
                    && containsText(projectPath, "react")) {
                frameworks.add("React");
            }

            return frameworks;
        }

        private boolean detectTests(Path projectPath) {
            return Files.exists(projectPath.resolve("src/test"))
                    || Files.exists(projectPath.resolve("test"))
                    || containsText(projectPath, "@Test");
        }

        private List<String> detectTestFrameworks(Path projectPath) {
            List<String> frameworks = new ArrayList<>();

            if (containsText(projectPath, "junit")) frameworks.add("JUnit");
            if (containsText(projectPath, "mockito")) frameworks.add("Mockito");
            if (containsText(projectPath, "testng")) frameworks.add("TestNG");
            if (containsText(projectPath, "jest")) frameworks.add("Jest");

            return frameworks;
        }

        private boolean detectCI(Path projectPath) {
            return Files.exists(projectPath.resolve(".github/workflows"))
                    || Files.exists(projectPath.resolve(".gitlab-ci.yml"))
                    || Files.exists(projectPath.resolve("azure-pipelines.yml"));
        }

        private List<String> detectCIPlatforms(Path projectPath) {
            List<String> platforms = new ArrayList<>();

            if (Files.exists(projectPath.resolve(".github/workflows"))) {
                platforms.add("GitHub Actions");
            }
            if (Files.exists(projectPath.resolve(".gitlab-ci.yml"))) {
                platforms.add("GitLab CI");
            }
            if (Files.exists(projectPath.resolve("azure-pipelines.yml"))) {
                platforms.add("Azure DevOps");
            }

            return platforms;
        }

        private List<ApiEndpoint> findApiEndpoints(Path projectPath) {
            List<ApiEndpoint> endpoints = new ArrayList<>();

            // This would analyze source code for API endpoints
            // Simplified implementation for demonstration
            if (containsText(projectPath, "@RestController")
                    || containsText(projectPath, "@Controller")) {
                endpoints.add(
                        new ApiEndpoint(
                                "/api/health",
                                "GET",
                                "Health check endpoint",
                                "Returns system health status"));
                endpoints.add(
                        new ApiEndpoint(
                                "/api/users", "GET", "List users", "Returns list of all users"));
                endpoints.add(
                        new ApiEndpoint(
                                "/api/users/{id}",
                                "GET",
                                "Get user",
                                "Returns specific user by ID"));
            }

            return endpoints;
        }

        private boolean containsText(Path projectPath, String text) {
            try {
                return Files.walk(projectPath)
                        .filter(Files::isRegularFile)
                        .filter(
                                file ->
                                        file.getFileName()
                                                .toString()
                                                .matches(".*\\.(java|ts|js|py|rs|go)"))
                        .anyMatch(
                                file -> {
                                    try {
                                        return Files.readString(file).contains(text);
                                    } catch (IOException e) {
                                        return false;
                                    }
                                });
            } catch (IOException e) {
                return false;
            }
        }

        private long countFiles(Path projectPath) {
            try {
                return Files.walk(projectPath).filter(Files::isRegularFile).count();
            } catch (IOException e) {
                return 0;
            }
        }

        private long countLinesOfCode(Path projectPath) {
            try {
                return Files.walk(projectPath)
                        .filter(Files::isRegularFile)
                        .filter(
                                file ->
                                        file.getFileName()
                                                .toString()
                                                .matches(".*\\.(java|ts|js|py|rs|go|kt)"))
                        .mapToLong(
                                file -> {
                                    try {
                                        return Files.readAllLines(file).size();
                                    } catch (IOException e) {
                                        return 0;
                                    }
                                })
                        .sum();
            } catch (IOException e) {
                return 0;
            }
        }
    }

    private static class DiagramGenerator {
        public String generateArchitectureDiagram(ProjectStructure structure) {
            StringBuilder mermaid = new StringBuilder();
            mermaid.append("graph TB\n");
            mermaid.append("    %% Architecture Overview\n");

            for (int i = 0; i < structure.modules().size(); i++) {
                String module = structure.modules().get(i);
                String nodeId = "M" + i;
                mermaid.append("    ").append(nodeId).append("[").append(module).append("]\n");

                // Add some relationships
                if (i > 0) {
                    mermaid.append("    M0 --> ").append(nodeId).append("\n");
                }
            }

            return mermaid.toString();
        }

        public String generateDependencyDiagram(ProjectStructure structure) {
            StringBuilder mermaid = new StringBuilder();
            mermaid.append("graph LR\n");
            mermaid.append("    %% Dependencies\n");
            mermaid.append("    APP[Application]\n");

            for (int i = 0; i < Math.min(structure.dependencies().size(), 5); i++) {
                String dep = structure.dependencies().get(i);
                String nodeId = "D" + i;
                mermaid.append("    ").append(nodeId).append("[").append(dep).append("]\n");
                mermaid.append("    APP --> ").append(nodeId).append("\n");
            }

            return mermaid.toString();
        }

        public List<String> generateClassDiagrams(ProjectStructure structure) {
            List<String> diagrams = new ArrayList<>();

            // Generate a simple class diagram
            StringBuilder mermaid = new StringBuilder();
            mermaid.append("classDiagram\n");
            mermaid.append("    class MainApplication {\n");
            mermaid.append("        +main(args: String[])\n");
            mermaid.append("    }\n");
            mermaid.append("    class Service {\n");
            mermaid.append("        +process()\n");
            mermaid.append("        +validate()\n");
            mermaid.append("    }\n");
            mermaid.append("    MainApplication --> Service\n");

            diagrams.add(mermaid.toString());
            return diagrams;
        }

        public List<String> generateSequenceDiagrams(ProjectStructure structure) {
            List<String> diagrams = new ArrayList<>();

            // Generate API sequence diagram
            StringBuilder mermaid = new StringBuilder();
            mermaid.append("sequenceDiagram\n");
            mermaid.append("    participant C as Client\n");
            mermaid.append("    participant A as API\n");
            mermaid.append("    participant S as Service\n");
            mermaid.append("    participant D as Database\n");
            mermaid.append("    \n");
            mermaid.append("    C->>A: HTTP Request\n");
            mermaid.append("    A->>S: Process Request\n");
            mermaid.append("    S->>D: Query Data\n");
            mermaid.append("    D-->>S: Return Results\n");
            mermaid.append("    S-->>A: Processed Data\n");
            mermaid.append("    A-->>C: HTTP Response\n");

            diagrams.add(mermaid.toString());
            return diagrams;
        }
    }

    private static class TemplateEngine {
        public String processTemplate(String templateName, Map<String, Object> data) {
            // Simplified template engine - in real implementation would use Handlebars
            return switch (templateName) {
                case "readme.md.hbs" -> generateReadmeTemplate(data);
                case "project-overview.md.hbs" -> generateOverviewTemplate(data);
                default -> "Template not found: " + templateName;
            };
        }

        @SuppressWarnings("unchecked")
        private String generateReadmeTemplate(Map<String, Object> data) {
            return String.format(
                    """
                # %s

                %s

                ## Quick Start

                ### Prerequisites

                - %s
                - %s

                ### Installation

                ```bash
                # Clone the repository
                git clone <repository-url>
                cd %s

                # Build the project
                %s
                ```

                ### Usage

                ```bash
                # Run the application
                %s
                ```

                ## Architecture

                This project consists of %d modules:

                %s

                ## Development

                ### Testing

                %s

                ### Contributing

                1. Fork the repository
                2. Create a feature branch
                3. Commit your changes
                4. Push to the branch
                5. Create a Pull Request

                ## License

                This project is licensed under the Apache License 2.0 - see the LICENSE file for details.

                ---
                *This README was auto-generated by YAPPC on %s*
                """,
                    data.get("projectName"),
                    data.get("description"),
                    ((List<String>) data.get("languages")).get(0) + " Runtime",
                    data.get("buildTool"),
                    data.get("projectName"),
                    getBuildCommand((String) data.get("buildTool")),
                    getRunCommand((String) data.get("buildTool")),
                    ((List<String>) data.get("modules")).size(),
                    ((List<String>) data.get("modules"))
                            .stream()
                                    .map(module -> "- `" + module + "`")
                                    .collect(Collectors.joining("\n")),
                    (Boolean) data.get("hasTests")
                            ? "Run tests with `"
                                    + getTestCommand((String) data.get("buildTool"))
                                    + "`"
                            : "No tests configured yet. Consider adding unit tests.",
                    data.get("generatedAt"));
        }

        @SuppressWarnings("unchecked")
        private String generateOverviewTemplate(Map<String, Object> data) {
            ProjectStructure project = (ProjectStructure) data.get("project");
            Map<String, Object> metrics = (Map<String, Object>) data.get("metrics");
            List<String> recommendations = (List<String>) data.get("recommendations");

            return String.format(
                    """
                # Project Overview: %s

                ## Summary

                %s

                ## Architecture

                - **Build Tool:** %s
                - **Languages:** %s
                - **Frameworks:** %s
                - **Modules:** %d
                - **Dependencies:** %d

                ## Quality Metrics

                - **Complexity:** %s (%d/100)
                - **Test Coverage:** %s
                - **CI/CD:** %s

                ## Recommendations

                %s

                ---
                *Generated by YAPPC Auto-Doc Generator on %s*
                """,
                    project.projectName(),
                    project.description(),
                    project.buildTool(),
                    String.join(", ", project.languages()),
                    String.join(", ", project.frameworks()),
                    project.modules().size(),
                    project.dependencies().size(),
                    metrics.get("complexityLevel"),
                    metrics.get("complexityScore"),
                    project.hasTests() ? "✅ Tests Present" : "❌ No Tests",
                    project.hasCI() ? "✅ CI Configured" : "❌ No CI",
                    recommendations.stream()
                            .map(rec -> "- " + rec)
                            .collect(Collectors.joining("\n")),
                    data.get("generatedAt"));
        }

        private String getBuildCommand(String buildTool) {
            return switch (buildTool.toLowerCase()) {
                case "gradle" -> "./gradlew build";
                case "maven" -> "mvn clean install";
                case "npm" -> "npm install && npm run build";
                case "cargo" -> "cargo build";
                default -> "# Build command for " + buildTool;
            };
        }

        private String getRunCommand(String buildTool) {
            return switch (buildTool.toLowerCase()) {
                case "gradle" -> "./gradlew run";
                case "maven" -> "mvn spring-boot:run";
                case "npm" -> "npm start";
                case "cargo" -> "cargo run";
                default -> "# Run command for " + buildTool;
            };
        }

        private String getTestCommand(String buildTool) {
            return switch (buildTool.toLowerCase()) {
                case "gradle" -> "./gradlew test";
                case "maven" -> "mvn test";
                case "npm" -> "npm test";
                case "cargo" -> "cargo test";
                default -> "# Test command for " + buildTool;
            };
        }
    }
}
