package com.ghatana.yappc.cli.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.yappc.core.maven.*;
import io.activej.eventloop.Eventloop;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import picocli.CommandLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CLI command for generating Maven POM files with AI-powered optimization.
 *
 * <p>Example usage: yappc maven generate --group-id com.example --artifact-id my-project
 * --java-version 21
 */
@CommandLine.Command(
        name = "maven",
        description = "Maven POM generation and management",
        subcommands = {
            GenerateMavenCommand.GenerateCommand.class,
            GenerateMavenCommand.ValidateCommand.class,
            GenerateMavenCommand.SuggestCommand.class
        })
/**
 * GenerateMavenCommand component within the YAPPC platform.
 *
 * @doc.type class
 * @doc.purpose GenerateMavenCommand component within the YAPPC platform.
 * @doc.layer platform
 * @doc.pattern Component
 */
public class GenerateMavenCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(GenerateMavenCommand.class);

    @Override
    public Integer call() {
        log.info("Maven POM Generator");
        log.info("Use 'yappc maven generate' to create a new Maven POM");
        log.info("Use 'yappc maven validate' to validate an existing POM specification");
        log.info("Use 'yappc maven suggest' to get improvement suggestions");
        return 0;
    }

    @CommandLine.Command(
            name = "generate",
            description = "Generate Maven POM.xml with intelligent defaults")
    static class GenerateCommand implements Callable<Integer> {

        @CommandLine.Option(
                names = {"-g", "--group-id"},
                required = true,
                description = "Maven group ID (e.g., com.example)")
        private String groupId;

        @CommandLine.Option(
                names = {"-a", "--artifact-id"},
                required = true,
                description = "Maven artifact ID (e.g., my-project)")
        private String artifactId;

        @CommandLine.Option(
                names = {"-v", "--version"},
                defaultValue = "1.0.0",
                description = "Project version (default: 1.0.0)")
        private String version;

        @CommandLine.Option(
                names = {"-p", "--packaging"},
                defaultValue = "jar",
                description = "Packaging type: jar, war, pom (default: jar)")
        private String packaging;

        @CommandLine.Option(
                names = {"-n", "--name"},
                description = "Project name")
        private String name;

        @CommandLine.Option(
                names = {"-d", "--description"},
                description = "Project description")
        private String description;

        @CommandLine.Option(
                names = {"-j", "--java-version"},
                defaultValue = "21",
                description = "Java version (default: 21)")
        private String javaVersion;

        @CommandLine.Option(
                names = {"--dependencies"},
                description = "Dependencies in format groupId:artifactId:version[:scope]")
        private List<String> dependencies = new ArrayList<>();

        @CommandLine.Option(
                names = {"--properties"},
                description = "Properties in format key=value")
        private List<String> properties = new ArrayList<>();

        @CommandLine.Option(
                names = {"-o", "--output"},
                defaultValue = "pom.xml",
                description = "Output file path (default: pom.xml)")
        private String outputPath;

        @CommandLine.Option(
                names = {"--format"},
                defaultValue = "xml",
                description = "Output format: xml, json (default: xml)")
        private String format;

        @CommandLine.Option(
                names = {"--validate"},
                description = "Validate generated POM")
        private boolean validate;

        @CommandLine.Option(
                names = {"--dry-run"},
                description = "Show generated content without writing files")
        private boolean dryRun;

        @Override
        public Integer call() throws Exception {
            try {
                // Parse dependencies
                List<MavenBuildSpec.MavenDependency> mavenDependencies =
                        parseDependencies(dependencies);

                // Parse properties
                Map<String, String> projectProperties = parseProperties(properties);

                // Create Maven specification
                MavenBuildSpec spec =
                        new MavenBuildSpec(
                                groupId,
                                artifactId,
                                version,
                                packaging,
                                name,
                                description,
                                javaVersion,
                                mavenDependencies,
                                List.of(),
                                List.of(),
                                projectProperties,
                                null,
                                List.of(),
                                null);

                // Generate Maven project
                MavenPomGenerator generator = new MavenPomGenerator();
                AtomicReference<GeneratedMavenProject> projRef = new AtomicReference<>();
                Eventloop el1 = Eventloop.create();
                el1.post(() -> generator.generatePom(spec).whenResult(projRef::set));
                el1.run();
                GeneratedMavenProject project = projRef.get();

                // Validate if requested
                if (validate) {
                    MavenValidationResult validation = project.getValidation();
                    if (!validation.isValid()) {
                        log.error("❌ Validation failed:");
                        validation
                                .getErrors()
                                .forEach(
                                        error ->
                                                    log.error("  ERROR: {}", error.getMessage()));
                        return 1;
                    }
                    log.info("✅ Validation passed (score: {})", String.format("%.2f", validation.getScore()));
                }

                // Output result
                if ("json".equalsIgnoreCase(format)) {
                    ObjectMapper mapper = JsonUtils.getDefaultMapper();
                    String json =
                            mapper.writerWithDefaultPrettyPrinter().writeValueAsString(project);

                    if (dryRun) {
                        log.info("{}", json);
                    } else {
                        Files.write(Paths.get(outputPath + ".json"), json.getBytes());
                        log.info("Generated Maven project JSON: {}.json", outputPath);
                    }
                } else {
                    // XML format (default)
                    if (dryRun) {
                        log.info("=== Generated POM.xml ===");
                        log.info("{}", project.getPomXml());

                        if (!project.getAdditionalFiles().isEmpty()) {
                            log.info("\n=== Additional Files ===");
                            project.getAdditionalFiles()
                                    .forEach(
                                            (filename, content) -> {
                                                log.info("\n--- {} ---", filename);
                                                log.info("{}", content);
                                            });
                        }
                    } else {
                        // Write POM file
                        Path pomPath = Paths.get(outputPath);
                        Files.write(pomPath, project.getPomXml().getBytes());
                        log.info("✅ Generated Maven POM: {}", pomPath.toAbsolutePath());

                        // Write additional files
                        for (Map.Entry<String, String> file :
                                project.getAdditionalFiles().entrySet()) {
                            Path filePath = pomPath.getParent().resolve(file.getKey());
                            Files.createDirectories(filePath.getParent());
                            Files.write(filePath, file.getValue().getBytes());
                            log.info("📄 Generated: {}", filePath.getFileName());
                        }
                    }
                }

                // Show optimizations
                if (!project.getOptimizations().isEmpty()) {
                    log.info("\n🚀 Applied Optimizations:");
                    project.getOptimizations().forEach(opt ->
                        log.info("  • {}", opt.getDescription()));
                }
                // Show warnings
                if (!project.getWarnings().isEmpty()) {
                    log.info("\n⚠️  Warnings:");
                    project.getWarnings().forEach(warning ->
                        log.info("  • {}", warning));
                }
                return 0;
            }
            catch (Exception e) {
                log.error("❌ Failed to generate Maven POM: {}", e.getMessage());
                e.printStackTrace();
                return 1;
            }
        }

        private List<MavenBuildSpec.MavenDependency> parseDependencies(List<String> depStrings) {
            List<MavenBuildSpec.MavenDependency> dependencies = new ArrayList<>();

            for (String depString : depStrings) {
                String[] parts = depString.split(":");
                if (parts.length >= 3) {
                    String groupId = parts[0];
                    String artifactId = parts[1];
                    String version = parts[2];
                    String scope = parts.length > 3 ? parts[3] : null;

                    dependencies.add(
                            new MavenBuildSpec.MavenDependency(
                                    groupId, artifactId, version, scope, null, null, List.of()));
                } else {
                    log.error("⚠️  Invalid dependency format: {} (expected: groupId:artifactId:version[:scope])", depString);
                }
            }

            return dependencies;
        }

        private Map<String, String> parseProperties(List<String> propStrings) {
            Map<String, String> properties = new HashMap<>();

            for (String propString : propStrings) {
                String[] parts = propString.split("=", 2);
                if (parts.length == 2) {
                    properties.put(parts[0], parts[1]);
                } else {
                    log.error("⚠️  Invalid property format: {} (expected: key=value)", propString);
                }
            }

            return properties;
        }
    }

    @CommandLine.Command(name = "validate", description = "Validate Maven POM specification")
    static class ValidateCommand implements Callable<Integer> {

        @CommandLine.Parameters(index = "0", description = "Path to Maven specification JSON file")
        private String specFile;

        @Override
        public Integer call() throws Exception {
            try {
                // Read specification file
                Path specPath = Paths.get(specFile);
                if (!Files.exists(specPath)) {
                    log.error("❌ Specification file not found: {}", specFile);
                    return 1;
                }

                ObjectMapper mapper = JsonUtils.getDefaultMapper();
                MavenBuildSpec spec = mapper.readValue(specPath.toFile(), MavenBuildSpec.class);

                // Validate
                MavenPomGenerator generator = new MavenPomGenerator();
                AtomicReference<MavenValidationResult> valRef = new AtomicReference<>();
                Eventloop el2 = Eventloop.create();
                el2.post(() -> generator.validateSpec(spec).whenResult(valRef::set));
                el2.run();
                MavenValidationResult result = valRef.get();

                // Display results
                log.info("Maven POM Validation Results");
                log.info("============================");
                log.info("Valid: {}", (result.isValid() ? "✅ Yes" : "❌ No"));
                log.info("Score: {}", String.format("%.2f", result.getScore()));

                if (!result.getErrors().isEmpty()) {
                    log.info("\n❌ Errors:");
                    result.getErrors()
                            .forEach(
                                    error ->
                                                log.info("  • {}{}", error.getMessage(), (error.getField() != null ? " (field: " + error.getField() + ")" : "")));
                }

                if (!result.getWarnings().isEmpty()) {
                    log.info("\n⚠️  Warnings:");
                    result.getWarnings().forEach(warning ->
                        log.info("  • {}", warning.getMessage()));
                }
                if (!result.getRecommendations().isEmpty()) {
                    log.info("\n💡 Recommendations:");
                    result.getRecommendations().forEach(rec ->
                        log.info("  • {}", rec));
                }
                return result.isValid() ? 0 : 1;
            }
            catch (Exception e) {
                log.error("❌ Failed to validate Maven specification: {}", e.getMessage());
                return 1;
            }
        }
    }

    @CommandLine.Command(
            name = "suggest",
            description = "Get improvement suggestions for Maven POM")
    static class SuggestCommand implements Callable<Integer> {

        @CommandLine.Parameters(index = "0", description = "Path to Maven specification JSON file")
        private String specFile;

        @Override
        public Integer call() throws Exception {
            try {
                // Read specification file
                Path specPath = Paths.get(specFile);
                if (!Files.exists(specPath)) {
                    log.error("❌ Specification file not found: {}", specFile);
                    return 1;
                }

                ObjectMapper mapper = JsonUtils.getDefaultMapper();
                MavenBuildSpec spec = mapper.readValue(specPath.toFile(), MavenBuildSpec.class);

                // Get suggestions
                MavenPomGenerator generator = new MavenPomGenerator();
                AtomicReference<MavenImprovementSuggestions> sugRef = new AtomicReference<>();
                Eventloop el3 = Eventloop.create();
                el3.post(() -> generator.suggestImprovements(spec).whenResult(sugRef::set));
                el3.run();
                MavenImprovementSuggestions suggestions = sugRef.get();

                // Display suggestions
                log.info("Maven POM Improvement Suggestions");
                log.info("=================================");
                log.info("Confidence Score: {}", String.format("%.2f", suggestions.getConfidenceScore()));

                if (!suggestions.getDependencyUpgrades().isEmpty()) {
                    log.info("\n📦 Dependency Upgrades:");
                    suggestions
                            .getDependencyUpgrades()
                            .forEach(
                                    upgrade ->
                                            log.info("  • {}:{} {} → {} ({} risk)", upgrade.getGroupId(),
                                                    upgrade.getArtifactId(),
                                                    upgrade.getCurrentVersion(),
                                                    upgrade.getRecommendedVersion(),
                                                    upgrade.getRiskLevel()));
                }

                if (!suggestions.getPluginRecommendations().isEmpty()) {
                    log.info("\n🔌 Plugin Recommendations:");
                    suggestions
                            .getPluginRecommendations()
                            .forEach(
                                    plugin ->
                                            log.info("  • {}:{} ({} priority) - {}", plugin.getGroupId() != null
                                                            ? plugin.getGroupId()
                                                            : "org.apache.maven.plugins",
                                                    plugin.getArtifactId(),
                                                    plugin.getPriority(),
                                                    plugin.getPurpose()));
                }

                if (!suggestions.getGeneralRecommendations().isEmpty()) {
                    log.info("\n💡 General Recommendations:");
                    suggestions
                            .getGeneralRecommendations().forEach(rec ->
                                log.info("  • {}", rec));
                }
                return 0;
            }
            catch (Exception e) {
                log.error("❌ Failed to get Maven suggestions: {}", e.getMessage());
                return 1;
            }
        }
    }
}
