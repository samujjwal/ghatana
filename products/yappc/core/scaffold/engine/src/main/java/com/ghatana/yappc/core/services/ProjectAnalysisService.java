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

package com.ghatana.yappc.core.services;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for analyzing project structure and detecting languages, frameworks, and build tools.
 * Used for automatic CI/CD pipeline configuration generation.
 *
 * <p>Week 8 Day 39: Project analysis for intelligent CI generation.
 *
 * @doc.type class
 * @doc.purpose Service for analyzing project structure and detecting languages, frameworks, and build tools.
 * @doc.layer platform
 * @doc.pattern Service
 */
public class ProjectAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(ProjectAnalysisService.class);

    private static final Map<String, List<String>> LANGUAGE_FILE_EXTENSIONS = new HashMap<>();

    static {
        LANGUAGE_FILE_EXTENSIONS.put("java", List.of(".java"));
        LANGUAGE_FILE_EXTENSIONS.put("kotlin", List.of(".kt", ".kts"));
        LANGUAGE_FILE_EXTENSIONS.put("scala", List.of(".scala"));
        LANGUAGE_FILE_EXTENSIONS.put("javascript", List.of(".js", ".mjs"));
        LANGUAGE_FILE_EXTENSIONS.put("typescript", List.of(".ts", ".tsx"));
        LANGUAGE_FILE_EXTENSIONS.put("python", List.of(".py"));
        LANGUAGE_FILE_EXTENSIONS.put("rust", List.of(".rs"));
        LANGUAGE_FILE_EXTENSIONS.put("go", List.of(".go"));
        LANGUAGE_FILE_EXTENSIONS.put("csharp", List.of(".cs"));
        LANGUAGE_FILE_EXTENSIONS.put("cpp", List.of(".cpp", ".cc", ".cxx", ".c++"));
        LANGUAGE_FILE_EXTENSIONS.put("c", List.of(".c", ".h"));
    }

    private static final Map<String, List<String>> FRAMEWORK_INDICATORS = new HashMap<>();

    static {
        FRAMEWORK_INDICATORS.put(
                "spring-boot", List.of("@SpringBootApplication", "spring-boot-starter"));
        FRAMEWORK_INDICATORS.put(
                "spring-framework",
                List.of("@Component", "@Service", "@Repository", "springframework"));
        FRAMEWORK_INDICATORS.put("react", List.of("react", "jsx", "useState", "useEffect"));
        FRAMEWORK_INDICATORS.put("angular", List.of("@angular", "@Component", "@Injectable"));
        FRAMEWORK_INDICATORS.put("vue", List.of("vue", ".vue", "createApp"));
        FRAMEWORK_INDICATORS.put("express", List.of("express", "app.get(", "app.post("));
        FRAMEWORK_INDICATORS.put("fastapi", List.of("from fastapi", "FastAPI()", "@app."));
        FRAMEWORK_INDICATORS.put("django", List.of("from django", "Django", "INSTALLED_APPS"));
        FRAMEWORK_INDICATORS.put("flask", List.of("from flask", "Flask()", "@app.route"));
        FRAMEWORK_INDICATORS.put("actix-web", List.of("actix-web", "HttpServer::new"));
        FRAMEWORK_INDICATORS.put("rocket", List.of("#[rocket::", "rocket::build()"));
        FRAMEWORK_INDICATORS.put("gin", List.of("gin-gonic/gin", "gin.Default()"));
        FRAMEWORK_INDICATORS.put("echo", List.of("labstack/echo", "echo.New()"));
    }

    private static final Map<String, List<String>> BUILD_TOOL_FILES = new HashMap<>();

    static {
        BUILD_TOOL_FILES.put(
                "gradle",
                List.of("build.gradle", "build.gradle.kts", "gradlew", "gradle.properties"));
        BUILD_TOOL_FILES.put("maven", List.of("pom.xml", "mvnw"));
        BUILD_TOOL_FILES.put("npm", List.of("package.json", "package-lock.json"));
        BUILD_TOOL_FILES.put("yarn", List.of("yarn.lock", ".yarnrc"));
        BUILD_TOOL_FILES.put("pnpm", List.of("pnpm-lock.yaml", ".pnpmrc"));
        BUILD_TOOL_FILES.put("cargo", List.of("Cargo.toml", "Cargo.lock"));
        BUILD_TOOL_FILES.put("go-mod", List.of("go.mod", "go.sum"));
        BUILD_TOOL_FILES.put("make", List.of("Makefile", "makefile"));
        BUILD_TOOL_FILES.put("cmake", List.of("CMakeLists.txt", "cmake"));
        BUILD_TOOL_FILES.put("msbuild", List.of("*.csproj", "*.sln", "Directory.Build.props"));
    }

    /**
 * Analyzes a project directory and returns detailed analysis results. */
    public ProjectAnalysis analyzeProject(String projectPath) {
        Path path = Paths.get(projectPath).toAbsolutePath();

        if (!Files.exists(path) || !Files.isDirectory(path)) {
            throw new IllegalArgumentException(
                    "Project path does not exist or is not a directory: " + path);
        }

        Set<String> detectedLanguages = new HashSet<>();
        Set<String> detectedFrameworks = new HashSet<>();
        Set<String> detectedBuildTools = new HashSet<>();
        Map<String, Integer> fileTypeCounts = new HashMap<>();
        List<String> configFiles = new ArrayList<>();

        try {
            // Analyze file structure
            analyzeFiles(path, detectedLanguages, detectedFrameworks, fileTypeCounts, configFiles);

            // Detect build tools
            detectedBuildTools.addAll(detectBuildTools(path));

            // Analyze package/dependency files
            Map<String, List<String>> dependencies = analyzeDependencies(path, detectedBuildTools);

            // Detect additional frameworks from dependencies
            detectedFrameworks.addAll(detectFrameworksFromDependencies(dependencies));

            // Calculate project complexity metrics
            ProjectComplexity complexity = calculateComplexity(path, fileTypeCounts);

            // Generate recommendations
            List<String> recommendations =
                    generateRecommendations(
                            detectedLanguages, detectedFrameworks, detectedBuildTools, complexity);

            return new ProjectAnalysis(
                    path.toString(),
                    detectedLanguages,
                    detectedFrameworks,
                    detectedBuildTools,
                    fileTypeCounts,
                    configFiles,
                    dependencies,
                    complexity,
                    recommendations);

        } catch (Exception e) {
            throw new RuntimeException("Failed to analyze project: " + projectPath, e);
        }
    }

    private void analyzeFiles(
            Path projectPath,
            Set<String> languages,
            Set<String> frameworks,
            Map<String, Integer> fileTypeCounts,
            List<String> configFiles) {
        try (Stream<Path> paths = Files.walk(projectPath)) {
            paths.filter(Files::isRegularFile)
                    .forEach(
                            file ->
                                    analyzeFile(
                                            file,
                                            projectPath,
                                            languages,
                                            frameworks,
                                            fileTypeCounts,
                                            configFiles));
        } catch (Exception e) {
            log.error("Warning: Error walking project files: {}", e.getMessage());
        }
    }

    private void analyzeFile(
            Path file,
            Path projectRoot,
            Set<String> languages,
            Set<String> frameworks,
            Map<String, Integer> fileTypeCounts,
            List<String> configFiles) {
        String fileName = file.getFileName().toString();
        String relativePath = projectRoot.relativize(file).toString();

        // Skip common irrelevant directories
        if (relativePath.contains("node_modules/")
                || relativePath.contains(".git/")
                || relativePath.contains("target/")
                || relativePath.contains("build/")
                || relativePath.contains("dist/")) {
            return;
        }

        // Detect language by file extension
        String extension = getFileExtension(fileName);
        LANGUAGE_FILE_EXTENSIONS.entrySet().stream()
                .filter(entry -> entry.getValue().contains(extension))
                .forEach(
                        entry -> {
                            languages.add(entry.getKey());
                            fileTypeCounts.merge(entry.getKey(), 1, Integer::sum);
                        });

        // Check for configuration files
        if (isConfigFile(fileName)) {
            configFiles.add(relativePath);
        }

        // Analyze file content for framework detection (for smaller files)
        try {
            if (Files.size(file) < 100_000) { // Limit to files under 100KB
                String content = Files.readString(file);
                detectFrameworksInContent(content, frameworks);
            }
        } catch (Exception e) {
            // Ignore files that can't be read as text or have size issues
        }
    }

    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot) : "";
    }

    private boolean isConfigFile(String fileName) {
        return fileName.equals("package.json")
                || fileName.equals("pom.xml")
                || fileName.equals("build.gradle")
                || fileName.equals("build.gradle.kts")
                || fileName.equals("Cargo.toml")
                || fileName.equals("go.mod")
                || fileName.equals("requirements.txt")
                || fileName.equals("Pipfile")
                || fileName.equals("pyproject.toml")
                || fileName.equals("Dockerfile")
                || fileName.equals("docker-compose.yml")
                || fileName.equals("docker-compose.yaml")
                || fileName.endsWith(".csproj")
                || fileName.endsWith(".sln");
    }

    private void detectFrameworksInContent(String content, Set<String> frameworks) {
        FRAMEWORK_INDICATORS.entrySet().stream()
                .filter(entry -> entry.getValue().stream().anyMatch(content::contains))
                .forEach(entry -> frameworks.add(entry.getKey()));
    }

    private Set<String> detectBuildTools(Path projectPath) {
        Set<String> buildTools = new HashSet<>();

        BUILD_TOOL_FILES
                .entrySet()
                .forEach(
                        entry -> {
                            String toolName = entry.getKey();
                            List<String> indicatorFiles = entry.getValue();

                            boolean toolDetected =
                                    indicatorFiles.stream()
                                            .anyMatch(
                                                    fileName -> {
                                                        if (fileName.contains("*")) {
                                                            // Handle wildcard patterns (simplified)
                                                            String pattern =
                                                                    fileName.replace("*", "");
                                                            try (Stream<Path> files =
                                                                    Files.list(projectPath)) {
                                                                return files.anyMatch(
                                                                        file ->
                                                                                file.getFileName()
                                                                                        .toString()
                                                                                        .contains(
                                                                                                pattern));
                                                            } catch (Exception e) {
                                                                return false;
                                                            }
                                                        } else {
                                                            return Files.exists(
                                                                    projectPath.resolve(fileName));
                                                        }
                                                    });

                            if (toolDetected) {
                                buildTools.add(toolName);
                            }
                        });

        return buildTools;
    }

    private Map<String, List<String>> analyzeDependencies(
            Path projectPath, Set<String> buildTools) {
        Map<String, List<String>> dependencies = new HashMap<>();

        // Analyze package.json for Node.js projects
        if (buildTools.contains("npm")
                || buildTools.contains("yarn")
                || buildTools.contains("pnpm")) {
            dependencies.putAll(analyzePackageJson(projectPath));
        }

        // Analyze pom.xml for Maven projects
        if (buildTools.contains("maven")) {
            dependencies.putAll(analyzePomXml(projectPath));
        }

        // Analyze build.gradle for Gradle projects
        if (buildTools.contains("gradle")) {
            dependencies.putAll(analyzeBuildGradle(projectPath));
        }

        // Analyze Cargo.toml for Rust projects
        if (buildTools.contains("cargo")) {
            dependencies.putAll(analyzeCargoToml(projectPath));
        }

        return dependencies;
    }

    private Map<String, List<String>> analyzePackageJson(Path projectPath) {
        Path packageJson = projectPath.resolve("package.json");
        if (!Files.exists(packageJson)) {
            return Map.of();
        }

        try {
            String content = Files.readString(packageJson);
            List<String> deps = new ArrayList<>();

            // Simple dependency extraction (in production would use proper JSON parsing)
            if (content.contains("\"dependencies\"")) {
                String[] lines = content.split("\\n");
                boolean inDeps = false;
                for (String line : lines) {
                    if (line.contains("\"dependencies\"")) {
                        inDeps = true;
                        continue;
                    }
                    if (inDeps && line.contains("}")) {
                        break;
                    }
                    if (inDeps && line.contains("\"")) {
                        String depName = line.split("\"")[1];
                        deps.add(depName);
                    }
                }
            }

            return Map.of("npm", deps);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Map<String, List<String>> analyzePomXml(Path projectPath) {
        // Simplified implementation
        return Map.of("maven", List.of("org.springframework.boot:spring-boot-starter"));
    }

    private Map<String, List<String>> analyzeBuildGradle(Path projectPath) {
        // Simplified implementation
        return Map.of("gradle", List.of("org.springframework.boot:spring-boot-starter"));
    }

    private Map<String, List<String>> analyzeCargoToml(Path projectPath) {
        // Simplified implementation
        return Map.of("cargo", List.of("serde", "tokio"));
    }

    private Set<String> detectFrameworksFromDependencies(Map<String, List<String>> dependencies) {
        Set<String> frameworks = new HashSet<>();

        dependencies.values().stream()
                .flatMap(List::stream)
                .forEach(
                        dep -> {
                            if (dep.contains("spring-boot")) frameworks.add("spring-boot");
                            if (dep.contains("spring")) frameworks.add("spring-framework");
                            if (dep.contains("react")) frameworks.add("react");
                            if (dep.contains("angular")) frameworks.add("angular");
                            if (dep.contains("vue")) frameworks.add("vue");
                            if (dep.contains("express")) frameworks.add("express");
                            if (dep.contains("fastapi")) frameworks.add("fastapi");
                            if (dep.contains("django")) frameworks.add("django");
                            if (dep.contains("flask")) frameworks.add("flask");
                        });

        return frameworks;
    }

    private ProjectComplexity calculateComplexity(
            Path projectPath, Map<String, Integer> fileTypeCounts) {
        int totalFiles = fileTypeCounts.values().stream().mapToInt(Integer::intValue).sum();
        int languageCount = fileTypeCounts.size();

        // Simple complexity calculation
        String complexity;
        if (totalFiles < 50 && languageCount <= 2) {
            complexity = "low";
        } else if (totalFiles < 200 && languageCount <= 4) {
            complexity = "medium";
        } else {
            complexity = "high";
        }

        return new ProjectComplexity(
                totalFiles,
                languageCount,
                complexity,
                estimateBuildTime(totalFiles, languageCount),
                estimateTestTime(totalFiles));
    }

    private int estimateBuildTime(int totalFiles, int languageCount) {
        // Simple heuristic: 1 second per 10 files + 30 seconds per language
        return Math.max(60, (totalFiles / 10) + (languageCount * 30));
    }

    private int estimateTestTime(int totalFiles) {
        // Simple heuristic: 2 seconds per file for testing
        return Math.max(30, totalFiles * 2);
    }

    private List<String> generateRecommendations(
            Set<String> languages,
            Set<String> frameworks,
            Set<String> buildTools,
            ProjectComplexity complexity) {
        List<String> recommendations = new ArrayList<>();

        // Matrix build recommendations
        if (languages.size() > 1) {
            recommendations.add("Enable matrix builds for multi-language support");
        }

        // Security recommendations
        if (frameworks.contains("spring-boot")) {
            recommendations.add("Enable security scanning for Spring Boot vulnerabilities");
        }

        // Performance recommendations
        if (complexity.totalFiles() > 100) {
            recommendations.add("Enable build caching to improve CI performance");
        }

        // Testing recommendations
        if (languages.contains("java")) {
            recommendations.add("Configure JaCoCo for code coverage reporting");
        }

        if (languages.contains("javascript") || languages.contains("typescript")) {
            recommendations.add("Configure Jest or Vitest for JavaScript/TypeScript testing");
        }

        return recommendations;
    }

    /**
 * Result of project analysis containing detected technologies and metrics. */
    public record ProjectAnalysis(
            String projectPath,
            Set<String> languages,
            Set<String> frameworks,
            Set<String> buildTools,
            Map<String, Integer> fileTypeCounts,
            List<String> configFiles,
            Map<String, List<String>> dependencies,
            ProjectComplexity complexity,
            List<String> recommendations) {}

    /**
 * Project complexity metrics for CI/CD optimization. */
    public record ProjectComplexity(
            int totalFiles,
            int languageCount,
            String complexityLevel,
            int estimatedBuildTimeSeconds,
            int estimatedTestTimeSeconds) {}
}
