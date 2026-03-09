package com.ghatana.yappc.core.maven;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import io.activej.promise.Promise;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Template-based Maven POM generator with intelligent defaults and best practices. Generates
 * production-ready Maven POM.xml files with comprehensive plugin configuration.
 *
 * @doc.type class
 * @doc.purpose Template-based Maven POM generator with intelligent defaults and best practices. Generates
 * @doc.layer platform
 * @doc.pattern Generator
 */
public class MavenPomGenerator implements MavenBuildGenerator {

    private static final Executor BLOCKING_EXECUTOR = Executors.newCachedThreadPool();


    private static final Logger logger = LoggerFactory.getLogger(MavenPomGenerator.class);

    private static final String MAVEN_VERSION = "4.0.0";
    private static final String COMPILER_PLUGIN_VERSION = "3.11.0";
    private static final String SUREFIRE_PLUGIN_VERSION = "3.1.2";
    private static final String JACOCO_PLUGIN_VERSION = "0.8.10";
    private static final String SPOTLESS_PLUGIN_VERSION = "2.40.0";

    @Override
    public Promise<GeneratedMavenProject> generatePom(MavenBuildSpec spec) {
        return Promise.ofBlocking(BLOCKING_EXECUTOR, 
                () -> {
                    try {
                        logger.info(
                                "Generating Maven POM for {}:{}",
                                spec.getGroupId(),
                                spec.getArtifactId());

                        String pomXml = generatePomXml(spec);
                        Map<String, String> additionalFiles = generateAdditionalFiles(spec);
                        List<GeneratedMavenProject.MavenOptimization> optimizations =
                                generateOptimizations(spec);
                        List<String> warnings = generateWarnings(spec);
                        MavenValidationResult validation = performValidation(spec);

                        return new GeneratedMavenProject(
                                pomXml,
                                additionalFiles,
                                optimizations,
                                warnings,
                                validation,
                                Instant.now(),
                                "1.0.0");
                    } catch (Exception e) {
                        logger.error("Error generating Maven POM", e);
                        throw new RuntimeException("Failed to generate Maven POM", e);
                    }
                });
    }

    @Override
    public Promise<MavenValidationResult> validateSpec(MavenBuildSpec spec) {
        return Promise.ofBlocking(BLOCKING_EXECUTOR, () -> performValidation(spec));
    }

    @Override
    public Promise<MavenImprovementSuggestions> suggestImprovements(MavenBuildSpec spec) {
        return Promise.ofBlocking(BLOCKING_EXECUTOR, 
                () -> {
                    List<MavenImprovementSuggestions.DependencyUpgrade> upgrades =
                            new ArrayList<>();
                    List<MavenImprovementSuggestions.PluginRecommendation> plugins =
                            new ArrayList<>();
                    List<MavenImprovementSuggestions.PropertyOptimization> properties =
                            new ArrayList<>();
                    List<MavenImprovementSuggestions.ProfileSuggestion> profiles =
                            new ArrayList<>();
                    List<String> general = new ArrayList<>();

                    // Suggest common improvements
                    if (!hasCompilerPlugin(spec)) {
                        plugins.add(
                                new MavenImprovementSuggestions.PluginRecommendation(
                                        "org.apache.maven.plugins",
                                        "maven-compiler-plugin",
                                        COMPILER_PLUGIN_VERSION,
                                        "Configure Java compilation with proper source/target"
                                                + " versions",
                                        null,
                                        "HIGH"));
                    }

                    if (!hasTestingPlugin(spec)) {
                        plugins.add(
                                new MavenImprovementSuggestions.PluginRecommendation(
                                        "org.apache.maven.plugins",
                                        "maven-surefire-plugin",
                                        SUREFIRE_PLUGIN_VERSION,
                                        "Configure unit test execution with proper reporting",
                                        null,
                                        "HIGH"));
                    }

                    general.add("Consider adding a README.md with build instructions");
                    general.add("Add .gitignore for Maven projects");
                    general.add("Consider setting up CI/CD pipeline");

                    return new MavenImprovementSuggestions(
                            upgrades, plugins, properties, profiles, general, 0.85);
                });
    }

    @Override
    public Promise<MavenBuildSpec> convertFromGradle(Object gradleSpec) {
        return Promise.ofBlocking(BLOCKING_EXECUTOR, 
                () -> {
                    // Mock conversion - in real implementation, would parse Gradle build.gradle
                    return new MavenBuildSpec(
                            "com.example",
                            "converted-project",
                            "1.0.0",
                            "jar",
                            "Converted Project",
                            "Converted from Gradle",
                            "17",
                            List.of(),
                            List.of(),
                            List.of(),
                            Map.of(),
                            null,
                            List.of(),
                            null);
                });
    }

    private String generatePomXml(MavenBuildSpec spec) {
        StringBuilder pom = new StringBuilder();
        pom.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        pom.append("<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n");
        pom.append("         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        pom.append("         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0\n");
        pom.append("                             http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n");
        pom.append("    <modelVersion>").append(MAVEN_VERSION).append("</modelVersion>\n\n");

        // Parent
        if (spec.getParent() != null) {
            pom.append("    <parent>\n");
            pom.append("        <groupId>")
                    .append(spec.getParent().getGroupId())
                    .append("</groupId>\n");
            pom.append("        <artifactId>")
                    .append(spec.getParent().getArtifactId())
                    .append("</artifactId>\n");
            pom.append("        <version>")
                    .append(spec.getParent().getVersion())
                    .append("</version>\n");
            if (spec.getParent().getRelativePath() != null) {
                pom.append("        <relativePath>")
                        .append(spec.getParent().getRelativePath())
                        .append("</relativePath>\n");
            }
            pom.append("    </parent>\n\n");
        }

        // Project coordinates
        pom.append("    <groupId>").append(spec.getGroupId()).append("</groupId>\n");
        pom.append("    <artifactId>").append(spec.getArtifactId()).append("</artifactId>\n");
        pom.append("    <version>").append(spec.getVersion()).append("</version>\n");
        pom.append("    <packaging>").append(spec.getPackaging()).append("</packaging>\n\n");

        if (spec.getName() != null) {
            pom.append("    <name>").append(spec.getName()).append("</name>\n");
        }
        if (spec.getDescription() != null) {
            pom.append("    <description>")
                    .append(spec.getDescription())
                    .append("</description>\n\n");
        }

        // Properties
        pom.append("    <properties>\n");
        pom.append("        <maven.compiler.source>")
                .append(spec.getJavaVersion())
                .append("</maven.compiler.source>\n");
        pom.append("        <maven.compiler.target>")
                .append(spec.getJavaVersion())
                .append("</maven.compiler.target>\n");
        pom.append("        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>\n");
        pom.append(
                "       "
                    + " <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>\n");

        // Add custom properties
        for (Map.Entry<String, String> property : spec.getProperties().entrySet()) {
            pom.append("        <")
                    .append(property.getKey())
                    .append(">")
                    .append(property.getValue())
                    .append("</")
                    .append(property.getKey())
                    .append(">\n");
        }
        pom.append("    </properties>\n\n");

        // Dependency Management
        if (spec.getDependencyManagement() != null
                && !spec.getDependencyManagement().getDependencies().isEmpty()) {
            pom.append("    <dependencyManagement>\n");
            pom.append("        <dependencies>\n");
            for (MavenBuildSpec.MavenDependency dep :
                    spec.getDependencyManagement().getDependencies()) {
                appendDependency(pom, dep, "            ");
            }
            pom.append("        </dependencies>\n");
            pom.append("    </dependencyManagement>\n\n");
        }

        // Dependencies
        if (!spec.getDependencies().isEmpty()) {
            pom.append("    <dependencies>\n");
            for (MavenBuildSpec.MavenDependency dependency : spec.getDependencies()) {
                appendDependency(pom, dependency, "        ");
            }
            pom.append("    </dependencies>\n\n");
        }

        // Repositories
        if (!spec.getRepositories().isEmpty()) {
            pom.append("    <repositories>\n");
            for (MavenBuildSpec.MavenRepository repo : spec.getRepositories()) {
                pom.append("        <repository>\n");
                pom.append("            <id>").append(repo.getId()).append("</id>\n");
                pom.append("            <url>").append(repo.getUrl()).append("</url>\n");
                pom.append("            <layout>").append(repo.getLayout()).append("</layout>\n");
                pom.append("            <releases><enabled>")
                        .append(repo.isReleases())
                        .append("</enabled></releases>\n");
                pom.append("            <snapshots><enabled>")
                        .append(repo.isSnapshots())
                        .append("</enabled></snapshots>\n");
                pom.append("        </repository>\n");
            }
            pom.append("    </repositories>\n\n");
        }

        // Build section
        pom.append("    <build>\n");
        pom.append("        <plugins>\n");

        // Add essential plugins
        appendEssentialPlugins(pom, spec);

        // Add custom plugins
        for (MavenBuildSpec.MavenPlugin plugin : spec.getPlugins()) {
            appendPlugin(pom, plugin);
        }

        pom.append("        </plugins>\n");
        pom.append("    </build>\n\n");

        // Profiles
        if (!spec.getProfiles().isEmpty()) {
            pom.append("    <profiles>\n");
            for (MavenBuildSpec.MavenProfile profile : spec.getProfiles()) {
                appendProfile(pom, profile);
            }
            pom.append("    </profiles>\n\n");
        }

        pom.append("</project>\n");
        return pom.toString();
    }

    private void appendDependency(
            StringBuilder pom, MavenBuildSpec.MavenDependency dep, String indent) {
        pom.append(indent).append("<dependency>\n");
        pom.append(indent).append("    <groupId>").append(dep.getGroupId()).append("</groupId>\n");
        pom.append(indent)
                .append("    <artifactId>")
                .append(dep.getArtifactId())
                .append("</artifactId>\n");

        if (dep.getVersion() != null) {
            pom.append(indent)
                    .append("    <version>")
                    .append(dep.getVersion())
                    .append("</version>\n");
        }
        if (dep.getScope() != null) {
            pom.append(indent).append("    <scope>").append(dep.getScope()).append("</scope>\n");
        }
        if (dep.getType() != null) {
            pom.append(indent).append("    <type>").append(dep.getType()).append("</type>\n");
        }
        if (dep.getClassifier() != null) {
            pom.append(indent)
                    .append("    <classifier>")
                    .append(dep.getClassifier())
                    .append("</classifier>\n");
        }

        if (!dep.getExclusions().isEmpty()) {
            pom.append(indent).append("    <exclusions>\n");
            for (MavenBuildSpec.MavenExclusion exclusion : dep.getExclusions()) {
                pom.append(indent).append("        <exclusion>\n");
                pom.append(indent)
                        .append("            <groupId>")
                        .append(exclusion.getGroupId())
                        .append("</groupId>\n");
                pom.append(indent)
                        .append("            <artifactId>")
                        .append(exclusion.getArtifactId())
                        .append("</artifactId>\n");
                pom.append(indent).append("        </exclusion>\n");
            }
            pom.append(indent).append("    </exclusions>\n");
        }

        pom.append(indent).append("</dependency>\n");
    }

    private void appendPlugin(StringBuilder pom, MavenBuildSpec.MavenPlugin plugin) {
        pom.append("            <plugin>\n");
        if (plugin.getGroupId() != null) {
            pom.append("                <groupId>")
                    .append(plugin.getGroupId())
                    .append("</groupId>\n");
        }
        pom.append("                <artifactId>")
                .append(plugin.getArtifactId())
                .append("</artifactId>\n");
        if (plugin.getVersion() != null) {
            pom.append("                <version>")
                    .append(plugin.getVersion())
                    .append("</version>\n");
        }

        if (!plugin.getConfiguration().isEmpty()) {
            pom.append("                <configuration>\n");
            // Simple configuration handling - in real implementation would handle nested objects
            for (Map.Entry<String, Object> config : plugin.getConfiguration().entrySet()) {
                pom.append("                    <")
                        .append(config.getKey())
                        .append(">")
                        .append(config.getValue().toString())
                        .append("</")
                        .append(config.getKey())
                        .append(">\n");
            }
            pom.append("                </configuration>\n");
        }

        if (!plugin.getExecutions().isEmpty()) {
            pom.append("                <executions>\n");
            for (MavenBuildSpec.MavenExecution execution : plugin.getExecutions()) {
                pom.append("                    <execution>\n");
                if (execution.getId() != null) {
                    pom.append("                        <id>")
                            .append(execution.getId())
                            .append("</id>\n");
                }
                if (execution.getPhase() != null) {
                    pom.append("                        <phase>")
                            .append(execution.getPhase())
                            .append("</phase>\n");
                }
                if (!execution.getGoals().isEmpty()) {
                    pom.append("                        <goals>\n");
                    for (String goal : execution.getGoals()) {
                        pom.append("                            <goal>")
                                .append(goal)
                                .append("</goal>\n");
                    }
                    pom.append("                        </goals>\n");
                }
                pom.append("                    </execution>\n");
            }
            pom.append("                </executions>\n");
        }

        pom.append("            </plugin>\n");
    }

    private void appendProfile(StringBuilder pom, MavenBuildSpec.MavenProfile profile) {
        pom.append("        <profile>\n");
        pom.append("            <id>").append(profile.getId()).append("</id>\n");

        if (profile.isActiveByDefault()) {
            pom.append("            <activation>\n");
            pom.append("                <activeByDefault>true</activeByDefault>\n");
            pom.append("            </activation>\n");
        }

        if (!profile.getProperties().isEmpty()) {
            pom.append("            <properties>\n");
            for (Map.Entry<String, String> prop : profile.getProperties().entrySet()) {
                pom.append("                <")
                        .append(prop.getKey())
                        .append(">")
                        .append(prop.getValue())
                        .append("</")
                        .append(prop.getKey())
                        .append(">\n");
            }
            pom.append("            </properties>\n");
        }

        pom.append("        </profile>\n");
    }

    private void appendEssentialPlugins(StringBuilder pom, MavenBuildSpec spec) {
        // Maven Compiler Plugin
        pom.append("            <plugin>\n");
        pom.append("                <groupId>org.apache.maven.plugins</groupId>\n");
        pom.append("                <artifactId>maven-compiler-plugin</artifactId>\n");
        pom.append("                <version>")
                .append(COMPILER_PLUGIN_VERSION)
                .append("</version>\n");
        pom.append("                <configuration>\n");
        pom.append("                    <source>")
                .append(spec.getJavaVersion())
                .append("</source>\n");
        pom.append("                    <target>")
                .append(spec.getJavaVersion())
                .append("</target>\n");
        pom.append("                    <encoding>UTF-8</encoding>\n");
        pom.append("                </configuration>\n");
        pom.append("            </plugin>\n");

        // Maven Surefire Plugin
        pom.append("            <plugin>\n");
        pom.append("                <groupId>org.apache.maven.plugins</groupId>\n");
        pom.append("                <artifactId>maven-surefire-plugin</artifactId>\n");
        pom.append("                <version>")
                .append(SUREFIRE_PLUGIN_VERSION)
                .append("</version>\n");
        pom.append("            </plugin>\n");
    }

    private Map<String, String> generateAdditionalFiles(MavenBuildSpec spec) {
        Map<String, String> files = new HashMap<>();

        // Generate .gitignore
        files.put(".gitignore", generateGitIgnore());

        // Generate mvnw wrapper (placeholder)
        files.put("mvnw", generateMavenWrapper());

        return files;
    }

    private String generateGitIgnore() {
        return """
            # Maven
            target/
            pom.xml.tag
            pom.xml.releaseBackup
            pom.xml.versionsBackup
            pom.xml.next
            release.properties
            dependency-reduced-pom.xml
            buildNumber.properties
            .mvn/timing.properties
            .mvn/wrapper/maven-wrapper.jar

            # IDE
            .idea/
            *.iml
            .vscode/
            *.swp
            *.swo
            *~

            # OS
            .DS_Store
            Thumbs.db
            """;
    }

    private String generateMavenWrapper() {
        // Basic Maven wrapper script
        // FUTURE: Generate full mvnw script with download logic
        return """
            #!/bin/sh
            # Maven Wrapper Script
            # This is a basic wrapper. For production use, consider using official Maven wrapper.
            
            MAVEN_VERSION=3.9.6
            echo "Using Maven $MAVEN_VERSION"
            exec mvn "$@"
            """;
    }

    private List<GeneratedMavenProject.MavenOptimization> generateOptimizations(
            MavenBuildSpec spec) {
        List<GeneratedMavenProject.MavenOptimization> optimizations = new ArrayList<>();

        optimizations.add(
                new GeneratedMavenProject.MavenOptimization(
                        "compiler-plugin",
                        "Added Maven Compiler Plugin with proper Java version configuration",
                        "Ensures consistent compilation across environments",
                        "Improved build reliability"));

        optimizations.add(
                new GeneratedMavenProject.MavenOptimization(
                        "encoding",
                        "Set UTF-8 encoding for source and reporting",
                        "Prevents encoding issues across different platforms",
                        "Better cross-platform compatibility"));

        return optimizations;
    }

    private List<String> generateWarnings(MavenBuildSpec spec) {
        List<String> warnings = new ArrayList<>();

        if (spec.getDependencies().isEmpty()) {
            warnings.add("No dependencies specified - project may be incomplete");
        }

        if (spec.getDescription() == null || spec.getDescription().trim().isEmpty()) {
            warnings.add("No project description provided");
        }

        return warnings;
    }

    private MavenValidationResult performValidation(MavenBuildSpec spec) {
        List<MavenValidationResult.ValidationIssue> errors = new ArrayList<>();
        List<MavenValidationResult.ValidationIssue> warnings = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        // Validate required fields
        if (spec.getGroupId().isEmpty()) {
            errors.add(
                    new MavenValidationResult.ValidationIssue(
                            "MISSING_GROUP_ID", "Group ID is required", "groupId", "ERROR"));
        }

        if (spec.getArtifactId().isEmpty()) {
            errors.add(
                    new MavenValidationResult.ValidationIssue(
                            "MISSING_ARTIFACT_ID",
                            "Artifact ID is required",
                            "artifactId",
                            "ERROR"));
        }

        // Validate Java version
        try {
            int javaVersion = Integer.parseInt(spec.getJavaVersion());
            if (javaVersion < 8) {
                warnings.add(
                        new MavenValidationResult.ValidationIssue(
                                "OLD_JAVA_VERSION",
                                "Java version is older than 8",
                                "javaVersion",
                                "WARNING"));
            }
        } catch (NumberFormatException e) {
            errors.add(
                    new MavenValidationResult.ValidationIssue(
                            "INVALID_JAVA_VERSION",
                            "Java version must be numeric",
                            "javaVersion",
                            "ERROR"));
        }

        recommendations.add("Consider adding unit tests");
        recommendations.add("Add integration tests for better coverage");

        boolean isValid = errors.isEmpty();
        double score = isValid ? (warnings.isEmpty() ? 1.0 : 0.8) : 0.0;

        return new MavenValidationResult(isValid, errors, warnings, recommendations, score);
    }

    private boolean hasCompilerPlugin(MavenBuildSpec spec) {
        return spec.getPlugins().stream()
                .anyMatch(plugin -> "maven-compiler-plugin".equals(plugin.getArtifactId()));
    }

    private boolean hasTestingPlugin(MavenBuildSpec spec) {
        return spec.getPlugins().stream()
                .anyMatch(
                        plugin ->
                                "maven-surefire-plugin".equals(plugin.getArtifactId())
                                        || "maven-failsafe-plugin".equals(plugin.getArtifactId()));
    }
}
