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
package com.ghatana.yappc.core.deps;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Multi-language dependency graph extractor for analyzing project dependencies.
 * Supports Java (Maven, Gradle), Node.js (npm, yarn, pnpm), Rust (Cargo), and
 * more.
 *
 * <p>
 * Week 9 Day 41: Dependency graph extractor for multi-language project
 * analysis.
 *
 * @doc.type class
 * @doc.purpose Multi-language dependency graph extractor for analyzing project
 * dependencies. Supports Java
 * @doc.layer platform
 * @doc.pattern Component
 */
public class DependencyGraphExtractor {

    private static final Logger log = LoggerFactory.getLogger(DependencyGraphExtractor.class);

    private final Map<String, DependencyParser> parsers;

    public DependencyGraphExtractor() {
        this.parsers
                = Map.of(
                        "maven", new MavenDependencyParser(),
                        "gradle", new GradleDependencyParser(),
                        "npm", new NpmDependencyParser(),
                        "cargo", new CargoDependencyParser(),
                        "go", new GoDependencyParser());
    }

    /**
     * Extracts dependency graph from a project directory.
     */
    public DependencyGraph extractDependencyGraph(String projectPath) {
        Path path = Paths.get(projectPath).toAbsolutePath();

        if (!Files.exists(path) || !Files.isDirectory(path)) {
            throw new IllegalArgumentException(
                    "Project path does not exist or is not a directory: " + path);
        }

        DependencyGraph.Builder graphBuilder
                = DependencyGraph.builder()
                        .projectPath(path.toString())
                        .extractionTimestamp(System.currentTimeMillis());

        Map<String, Set<Dependency>> allDependencies = new HashMap<>();
        Map<String, DependencyMetadata> metadata = new HashMap<>();

        // Detect and parse dependency files
        for (var entry : parsers.entrySet()) {
            String ecosystem = entry.getKey();
            DependencyParser parser = entry.getValue();

            try {
                Optional<DependencyParseResult> parseResult = parser.parseDependencies(path);

                if (parseResult.isPresent()) {
                    var result = parseResult.get();
                    allDependencies.put(ecosystem, result.dependencies());
                    metadata.put(ecosystem, result.metadata());

                    log.info("📦 Detected {}: {} dependencies", ecosystem, result.dependencies().size());
                }
            } catch (Exception e) {
                log.error("⚠️  Failed to parse {} dependencies: {}", ecosystem, e.getMessage());
            }
        }

        // Build unified dependency graph
        Set<Dependency> unifiedDependencies = unifyDependencies(allDependencies);
        Map<Dependency, Set<Dependency>> dependencyRelations
                = buildDependencyRelations(unifiedDependencies);

        // Analyze for conflicts and issues
        List<DependencyConflict> conflicts = detectConflicts(allDependencies);
        List<SecurityVulnerability> vulnerabilities = detectVulnerabilities(unifiedDependencies);
        DependencyAnalysis analysis = analyzeDependencies(unifiedDependencies, dependencyRelations);

        return graphBuilder
                .ecosystemDependencies(allDependencies)
                .unifiedDependencies(unifiedDependencies)
                .dependencyRelations(dependencyRelations)
                .metadata(metadata)
                .conflicts(conflicts)
                .vulnerabilities(vulnerabilities)
                .analysis(analysis)
                .build();
    }

    /**
     * Extracts dependencies for a specific ecosystem.
     */
    public Optional<DependencyParseResult> extractEcosystemDependencies(
            String projectPath, String ecosystem) {
        Path path = Paths.get(projectPath).toAbsolutePath();
        DependencyParser parser = parsers.get(ecosystem.toLowerCase());

        if (parser == null) {
            throw new IllegalArgumentException("Unsupported ecosystem: " + ecosystem);
        }

        try {
            return parser.parseDependencies(path);
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract " + ecosystem + " dependencies", e);
        }
    }

    private Set<Dependency> unifyDependencies(Map<String, Set<Dependency>> allDependencies) {
        Set<Dependency> unified = new HashSet<>();

        for (var entry : allDependencies.entrySet()) {
            unified.addAll(entry.getValue());
        }

        return unified;
    }

    private Map<Dependency, Set<Dependency>> buildDependencyRelations(
            Set<Dependency> dependencies) {
        // For now, return empty relations - in production would analyze transitive dependencies
        Map<Dependency, Set<Dependency>> relations = new HashMap<>();

        for (Dependency dep : dependencies) {
            relations.put(dep, new HashSet<>());
        }

        return relations;
    }

    private List<DependencyConflict> detectConflicts(Map<String, Set<Dependency>> allDependencies) {
        List<DependencyConflict> conflicts = new ArrayList<>();

        // Check for version conflicts within ecosystems
        for (var entry : allDependencies.entrySet()) {
            String ecosystem = entry.getKey();
            Set<Dependency> deps = entry.getValue();

            Map<String, List<Dependency>> groupedByName
                    = deps.stream().collect(Collectors.groupingBy(Dependency::name));

            for (var nameEntry : groupedByName.entrySet()) {
                String name = nameEntry.getKey();
                List<Dependency> versions = nameEntry.getValue();

                if (versions.size() > 1) {
                    Set<String> distinctVersions
                            = versions.stream().map(Dependency::version).collect(Collectors.toSet());

                    if (distinctVersions.size() > 1) {
                        conflicts.add(
                                new DependencyConflict(
                                        name,
                                        ecosystem,
                                        new ArrayList<>(distinctVersions),
                                        DependencyConflict.ConflictType.VERSION_CONFLICT,
                                        "Multiple versions of "
                                        + name
                                        + " detected: "
                                        + distinctVersions));
                    }
                }
            }
        }

        return conflicts;
    }

    private List<SecurityVulnerability> detectVulnerabilities(Set<Dependency> dependencies) {
        // Simplified vulnerability detection - in production would use CVE databases
        List<SecurityVulnerability> vulnerabilities = new ArrayList<>();

        for (Dependency dep : dependencies) {
            // Example: detect known vulnerable versions
            if (isKnownVulnerableVersion(dep)) {
                vulnerabilities.add(
                        new SecurityVulnerability(
                                dep,
                                "CVE-2023-XXXX",
                                Severity.HIGH,
                                "Known security vulnerability in "
                                + dep.name()
                                + " "
                                + dep.version(),
                                "Update to version " + getRecommendedVersion(dep)));
            }
        }

        return vulnerabilities;
    }

    private DependencyAnalysis analyzeDependencies(
            Set<Dependency> dependencies, Map<Dependency, Set<Dependency>> relations) {
        int totalDependencies = dependencies.size();

        Map<String, Integer> licenseDistribution
                = dependencies.stream()
                        .collect(
                                Collectors.groupingBy(
                                        dep -> dep.license() != null ? dep.license() : "Unknown",
                                        Collectors.collectingAndThen(
                                                Collectors.counting(), Math::toIntExact)));

        Map<Dependency.DependencyPhase, Integer> scopeDistribution
                = dependencies.stream()
                        .collect(
                                Collectors.groupingBy(
                                        Dependency::scope,
                                        Collectors.collectingAndThen(
                                                Collectors.counting(), Math::toIntExact)));

        // Calculate depth statistics
        int maxDepth
                = relations.isEmpty()
                ? 1
                : relations.values().stream().mapToInt(Set::size).max().orElse(0) + 1;

        // Identify large dependencies (simplified heuristic)
        List<Dependency> largeDependencies
                = dependencies.stream()
                        .filter(dep -> dep.size() > 1024 * 1024) // > 1MB
                        .collect(Collectors.toList());

        return new DependencyAnalysis(
                totalDependencies,
                licenseDistribution,
                scopeDistribution,
                maxDepth,
                largeDependencies,
                calculateRiskScore(dependencies),
                generateRecommendations(dependencies));
    }

    private boolean isKnownVulnerableVersion(Dependency dep) {
        // Simplified check - in production would query vulnerability databases
        return (dep.name().contains("log4j") && dep.version().startsWith("2."))
                || (dep.name().contains("jackson") && dep.version().startsWith("2.9"))
                || (dep.name().contains("spring") && dep.version().startsWith("4."));
    }

    private String getRecommendedVersion(Dependency dep) {
        // Simplified recommendation - in production would query package registries
        if (dep.name().contains("log4j")) {
            return "2.17.1";
        }
        if (dep.name().contains("jackson")) {
            return "2.15.0";
        }
        if (dep.name().contains("spring")) {
            return "5.3.21";
        }
        return "latest";
    }

    private double calculateRiskScore(Set<Dependency> dependencies) {
        double score = 0.0;

        for (Dependency dep : dependencies) {
            // Risk factors
            if (isKnownVulnerableVersion(dep)) {
                score += 0.3;
            }
            if (dep.license() == null || dep.license().equals("Unknown")) {
                score += 0.1;
            }
            if (dep.scope() == Dependency.DependencyPhase.RUNTIME) {
                score += 0.05;
            }
            if (dep.size() > 10 * 1024 * 1024) {
                score += 0.1; // > 10MB

                    }}

        return Math.min(1.0, score / dependencies.size());
    }

    private List<String> generateRecommendations(Set<Dependency> dependencies) {
        List<String> recommendations = new ArrayList<>();

        long unknownLicenses
                = dependencies.stream()
                        .filter(dep -> dep.license() == null || dep.license().equals("Unknown"))
                        .count();

        if (unknownLicenses > 0) {
            recommendations.add(
                    "Review and document licenses for " + unknownLicenses + " dependencies");
        }

        long vulnerableDeps = dependencies.stream().filter(this::isKnownVulnerableVersion).count();

        if (vulnerableDeps > 0) {
            recommendations.add(
                    "Update " + vulnerableDeps + " dependencies with known vulnerabilities");
        }

        if (dependencies.size() > 100) {
            recommendations.add(
                    "Consider dependency cleanup - project has "
                    + dependencies.size()
                    + " dependencies");
        }

        return recommendations;
    }

    // Dependency parser interface and implementations
    interface DependencyParser {

        Optional<DependencyParseResult> parseDependencies(Path projectPath) throws IOException;
    }

    static class MavenDependencyParser implements DependencyParser {

        private static final Pattern DEPENDENCY_PATTERN
                = Pattern.compile(
                        "<dependency>.*?<groupId>(.*?)</groupId>.*?<artifactId>(.*?)</artifactId>.*?<version>(.*?)</version>.*?</dependency>",
                        Pattern.DOTALL);

        @Override
        public Optional<DependencyParseResult> parseDependencies(Path projectPath)
                throws IOException {
            Path pomFile = projectPath.resolve("pom.xml");
            if (!Files.exists(pomFile)) {
                return Optional.empty();
            }

            String content = Files.readString(pomFile);
            Set<Dependency> dependencies = new HashSet<>();
            Matcher matcher = DEPENDENCY_PATTERN.matcher(content);

            while (matcher.find()) {
                String groupId = matcher.group(1);
                String artifactId = matcher.group(2);
                String version = matcher.group(3);

                dependencies.add(
                        new Dependency(
                                groupId + ":" + artifactId,
                                version,
                                "maven",
                                Dependency.DependencyPhase.COMPILE,
                                null, // License would be resolved separately
                                0, // Size would be resolved separately
                                Map.of("groupId", groupId, "artifactId", artifactId)));
            }

            DependencyMetadata metadata
                    = new DependencyMetadata(
                            "pom.xml",
                            "Maven POM",
                            Map.of("projectName", extractProjectName(content)));

            return Optional.of(new DependencyParseResult(dependencies, metadata));
        }

        private String extractProjectName(String pomContent) {
            Pattern namePattern = Pattern.compile("<artifactId>(.*?)</artifactId>");
            Matcher matcher = namePattern.matcher(pomContent);
            return matcher.find() ? matcher.group(1) : "unknown";
        }
    }

    static class GradleDependencyParser implements DependencyParser {

        private static final Pattern DEPENDENCY_PATTERN
                = Pattern.compile(
                        "(implementation|compile|runtime|testImplementation|testCompile)\\s+['\"]([^'\"]+)['\"]");

        @Override
        public Optional<DependencyParseResult> parseDependencies(Path projectPath)
                throws IOException {
            Path buildFile = projectPath.resolve("build.gradle");
            if (!Files.exists(buildFile)) {
                buildFile = projectPath.resolve("build.gradle.kts");
                if (!Files.exists(buildFile)) {
                    return Optional.empty();
                }
            }

            String content = Files.readString(buildFile);
            Set<Dependency> dependencies = new HashSet<>();
            Matcher matcher = DEPENDENCY_PATTERN.matcher(content);

            while (matcher.find()) {
                String configuration = matcher.group(1);
                String coordinate = matcher.group(2);

                String[] parts = coordinate.split(":");
                if (parts.length >= 3) {
                    String name = parts[0] + ":" + parts[1];
                    String version = parts[2];

                    Dependency.DependencyPhase scope = mapGradleScope(configuration);

                    dependencies.add(
                            new Dependency(
                                    name,
                                    version,
                                    "gradle",
                                    scope,
                                    null, // License would be resolved separately
                                    0, // Size would be resolved separately
                                    Map.of(
                                            "configuration",
                                            configuration,
                                            "coordinate",
                                            coordinate)));
                }
            }

            DependencyMetadata metadata
                    = new DependencyMetadata(
                            buildFile.getFileName().toString(),
                            "Gradle Build Script",
                            Map.of("buildFile", buildFile.getFileName().toString()));

            return Optional.of(new DependencyParseResult(dependencies, metadata));
        }

        private Dependency.DependencyPhase mapGradleScope(String configuration) {
            return switch (configuration) {
                case "implementation", "compile" ->
                    Dependency.DependencyPhase.COMPILE;
                case "runtime" ->
                    Dependency.DependencyPhase.RUNTIME;
                case "testImplementation", "testCompile" ->
                    Dependency.DependencyPhase.TEST;
                default ->
                    Dependency.DependencyPhase.COMPILE;
            };
        }
    }

    static class NpmDependencyParser implements DependencyParser {

        @Override
        public Optional<DependencyParseResult> parseDependencies(Path projectPath)
                throws IOException {
            Path packageJson = projectPath.resolve("package.json");
            if (!Files.exists(packageJson)) {
                return Optional.empty();
            }

            String content = Files.readString(packageJson);
            Set<Dependency> dependencies = new HashSet<>();

            // Simplified JSON parsing - in production would use proper JSON parser
            dependencies.addAll(
                    parseJsonDependencies(
                            content, "dependencies", Dependency.DependencyPhase.RUNTIME));
            dependencies.addAll(
                    parseJsonDependencies(
                            content, "devDependencies", Dependency.DependencyPhase.TEST));

            DependencyMetadata metadata
                    = new DependencyMetadata(
                            "package.json",
                            "npm Package Descriptor",
                            Map.of("packageManager", "npm"));

            return Optional.of(new DependencyParseResult(dependencies, metadata));
        }

        private Set<Dependency> parseJsonDependencies(
                String content, String section, Dependency.DependencyPhase scope) {
            Set<Dependency> deps = new HashSet<>();

            // Very simplified JSON parsing - just for demonstration
            Pattern sectionPattern
                    = Pattern.compile("\"" + section + "\"\\s*:\\s*\\{([^}]+)\\}", Pattern.DOTALL);

            Matcher sectionMatcher = sectionPattern.matcher(content);
            if (sectionMatcher.find()) {
                String depsSection = sectionMatcher.group(1);
                Pattern depPattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]+)\"");
                Matcher depMatcher = depPattern.matcher(depsSection);

                while (depMatcher.find()) {
                    String name = depMatcher.group(1);
                    String version = depMatcher.group(2);

                    deps.add(
                            new Dependency(
                                    name,
                                    version.replaceAll("[^\\d\\.]", ""), // Clean version
                                    "npm",
                                    scope,
                                    null, // License would be resolved separately
                                    0, // Size would be resolved separately
                                    Map.of("rawVersion", version)));
                }
            }

            return deps;
        }
    }

    static class CargoDependencyParser implements DependencyParser {

        private static final Pattern DEPENDENCY_PATTERN
                = Pattern.compile("^([a-zA-Z0-9_-]+)\\s*=\\s*\"([^\"]+)\"", Pattern.MULTILINE);

        @Override
        public Optional<DependencyParseResult> parseDependencies(Path projectPath)
                throws IOException {
            Path cargoToml = projectPath.resolve("Cargo.toml");
            if (!Files.exists(cargoToml)) {
                return Optional.empty();
            }

            String content = Files.readString(cargoToml);
            Set<Dependency> dependencies = new HashSet<>();

            // Find [dependencies] section
            String[] lines = content.split("\\n");
            boolean inDepsSection = false;

            for (String line : lines) {
                line = line.trim();

                if (line.equals("[dependencies]")) {
                    inDepsSection = true;
                    continue;
                }

                if (line.startsWith("[") && !line.equals("[dependencies]")) {
                    inDepsSection = false;
                    continue;
                }

                if (inDepsSection && !line.isEmpty() && !line.startsWith("#")) {
                    Matcher matcher = DEPENDENCY_PATTERN.matcher(line);
                    if (matcher.find()) {
                        String name = matcher.group(1);
                        String version = matcher.group(2);

                        dependencies.add(
                                new Dependency(
                                        name,
                                        version,
                                        "cargo",
                                        Dependency.DependencyPhase.COMPILE,
                                        null, // License would be resolved separately
                                        0, // Size would be resolved separately
                                        Map.of("rawVersion", version)));
                    }
                }
            }

            DependencyMetadata metadata
                    = new DependencyMetadata(
                            "Cargo.toml",
                            "Rust Package Manifest",
                            Map.of("packageManager", "cargo"));

            return Optional.of(new DependencyParseResult(dependencies, metadata));
        }
    }

    static class GoDependencyParser implements DependencyParser {

        @Override
        public Optional<DependencyParseResult> parseDependencies(Path projectPath)
                throws IOException {
            Path goMod = projectPath.resolve("go.mod");
            if (!Files.exists(goMod)) {
                return Optional.empty();
            }

            String content = Files.readString(goMod);
            Set<Dependency> dependencies = new HashSet<>();

            // Parse require section
            Pattern requirePattern
                    = Pattern.compile(
                            "require\\s*\\(([^)]+)\\)|require\\s+([^\\s]+)\\s+([^\\s]+)",
                            Pattern.DOTALL);

            Matcher matcher = requirePattern.matcher(content);
            while (matcher.find()) {
                if (matcher.group(1) != null) {
                    // Multi-line require block
                    String requireBlock = matcher.group(1);
                    String[] lines = requireBlock.split("\\n");

                    for (String line : lines) {
                        line = line.trim();
                        if (!line.isEmpty() && !line.startsWith("//")) {
                            String[] parts = line.split("\\s+");
                            if (parts.length >= 2) {
                                dependencies.add(
                                        new Dependency(
                                                parts[0],
                                                parts[1],
                                                "go",
                                                Dependency.DependencyPhase.COMPILE,
                                                null,
                                                0,
                                                Map.of("module", parts[0])));
                            }
                        }
                    }
                } else if (matcher.group(2) != null && matcher.group(3) != null) {
                    // Single line require
                    dependencies.add(
                            new Dependency(
                                    matcher.group(2),
                                    matcher.group(3),
                                    "go",
                                    Dependency.DependencyPhase.COMPILE,
                                    null,
                                    0,
                                    Map.of("module", matcher.group(2))));
                }
            }

            DependencyMetadata metadata
                    = new DependencyMetadata(
                            "go.mod", "Go Module Descriptor", Map.of("packageManager", "go"));

            return Optional.of(new DependencyParseResult(dependencies, metadata));
        }
    }

    // Data classes for dependency graph structure
    public record Dependency(
            String name,
            String version,
            String ecosystem,
            DependencyPhase scope,
            String license,
            long size,
            Map<String, String> metadata) {
        

    public enum DependencyPhase {
        COMPILE,
        RUNTIME,
        TEST,
        PROVIDED,
        SYSTEM
    }
}

public record DependencyParseResult(
        Set<Dependency> dependencies, DependencyMetadata metadata) {

}

public record DependencyMetadata(
        String sourceFile, String description, Map<String, String> properties) {

}

public record DependencyConflict(
        String dependencyName,
        String ecosystem,
        List<String> conflictingVersions,
        ConflictType type,
        String description) {

    public enum ConflictType {
        VERSION_CONFLICT,
        SCOPE_CONFLICT,
        DUPLICATE_DEPENDENCY
    }
}

/**
 * Severity level for security vulnerabilities.
 */
public enum Severity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

public record SecurityVulnerability(
        Dependency dependency,
        String cveId,
        Severity severity,
        String description,
        String recommendation) {

}

public record DependencyAnalysis(
        int totalDependencies,
        Map<String, Integer> licenseDistribution,
        Map<Dependency.DependencyPhase, Integer> scopeDistribution,
        int maxDependencyDepth,
        List<Dependency> largeDependencies,
        double riskScore,
        List<String> recommendations) {

}

public record DependencyGraph(
        String projectPath,
        long extractionTimestamp,
        Map<String, Set<Dependency>> ecosystemDependencies,
        Set<Dependency> unifiedDependencies,
        Map<Dependency, Set<Dependency>> dependencyRelations,
        Map<String, DependencyMetadata> metadata,
        List<DependencyConflict> conflicts,
        List<SecurityVulnerability> vulnerabilities,
        DependencyAnalysis analysis) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String projectPath;
        private long extractionTimestamp;
        private Map<String, Set<Dependency>> ecosystemDependencies = new HashMap<>();
        private Set<Dependency> unifiedDependencies = new HashSet<>();
        private Map<Dependency, Set<Dependency>> dependencyRelations = new HashMap<>();
        private Map<String, DependencyMetadata> metadata = new HashMap<>();
        private List<DependencyConflict> conflicts = new ArrayList<>();
        private List<SecurityVulnerability> vulnerabilities = new ArrayList<>();
        private DependencyAnalysis analysis;

        public Builder projectPath(String projectPath) {
            this.projectPath = projectPath;
            return this;
        }

        public Builder extractionTimestamp(long extractionTimestamp) {
            this.extractionTimestamp = extractionTimestamp;
            return this;
        }

        public Builder ecosystemDependencies(
                Map<String, Set<Dependency>> ecosystemDependencies) {
            this.ecosystemDependencies = ecosystemDependencies;
            return this;
        }

        public Builder unifiedDependencies(Set<Dependency> unifiedDependencies) {
            this.unifiedDependencies = unifiedDependencies;
            return this;
        }

        public Builder dependencyRelations(
                Map<Dependency, Set<Dependency>> dependencyRelations) {
            this.dependencyRelations = dependencyRelations;
            return this;
        }

        public Builder metadata(Map<String, DependencyMetadata> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder conflicts(List<DependencyConflict> conflicts) {
            this.conflicts = conflicts;
            return this;
        }

        public Builder vulnerabilities(List<SecurityVulnerability> vulnerabilities) {
            this.vulnerabilities = vulnerabilities;
            return this;
        }

        public Builder analysis(DependencyAnalysis analysis) {
            this.analysis = analysis;
            return this;
        }

        public DependencyGraph build() {
            return new DependencyGraph(
                    projectPath,
                    extractionTimestamp,
                    ecosystemDependencies,
                    unifiedDependencies,
                    dependencyRelations,
                    metadata,
                    conflicts,
                    vulnerabilities,
                    analysis);
        }
    }
}
}
