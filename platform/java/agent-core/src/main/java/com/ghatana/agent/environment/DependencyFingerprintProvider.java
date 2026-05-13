/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.environment;

import com.ghatana.agent.framework.api.AgentContext;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default implementation of EnvironmentFingerprintProvider that analyzes dependencies.
 *
 * <p>This provider reads package.json, pom.xml, Gradle files, and lockfiles to
 * extract version information for the environment fingerprint.
 *
 * @doc.type class
 * @doc.purpose Default implementation of EnvironmentFingerprintProvider
 * @doc.layer agent-core
 * @doc.pattern Service
 */
public final class DependencyFingerprintProvider implements EnvironmentFingerprintProvider {

    private final Executor executor;
    private final VersionDetector versionDetector;

    public DependencyFingerprintProvider(@NotNull Executor executor) {
        this.executor = executor;
        this.versionDetector = new VersionDetector();
    }

    @Override
    @NotNull
    public Promise<EnvironmentFingerprint> fingerprint(
            @NotNull AgentContext ctx,
            @NotNull Object input
    ) {
        return Promise.ofBlocking(executor, () -> {
            String workingDir = ctx.getWorkingDirectory();
            Map<String, String> dependencies = new HashMap<>();
            Map<String, String> tools = new HashMap<>();
            Map<String, String> runtimes = new HashMap<>();
            Map<String, String> frameworks = new HashMap<>();
            Map<String, String> conventions = new HashMap<>();
            Map<String, String> projectFiles = new HashMap<>();
            String projectType = detectProjectType(workingDir);

            // Parse project files based on type
            switch (projectType) {
                case "typescript", "javascript" -> {
                    parseNodeProjectFiles(workingDir, dependencies, projectFiles);
                }
                case "java", "kotlin" -> {
                    parseJavaProjectFiles(workingDir, dependencies, projectFiles);
                }
                case "gradle" -> {
                    parseGradleProjectFiles(workingDir, dependencies, projectFiles);
                }
            }

            // Detect runtime and framework information
            detectRuntimeInfo(workingDir, runtimes, frameworks, tools);

            return new EnvironmentFingerprint(
                    ctx.getTenantId(),
                    ctx.getRepoId(),
                    projectType,
                    dependencies,
                    tools,
                    runtimes,
                    frameworks,
                    conventions,
                    projectFiles,
                    Instant.now(),
                    java.util.List.of()
            );
        });
    }

    /**
     * Detects the project type from working directory.
     *
     * @param workingDir working directory
     * @return project type
     */
    @NotNull
    private String detectProjectType(@NotNull String workingDir) {
        Path dir = Paths.get(workingDir);

        if (Files.exists(dir.resolve("package.json"))) {
            return "typescript";
        }
        if (Files.exists(dir.resolve("pom.xml"))) {
            return "java";
        }
        if (Files.exists(dir.resolve("build.gradle")) || Files.exists(dir.resolve("build.gradle.kts"))) {
            return "gradle";
        }

        return "unknown";
    }

    /**
     * Parses Node.js project files (package.json, lockfiles).
     *
     * @param workingDir working directory
     * @param dependencies dependencies map to populate
     * @param projectFiles project files map to populate
     */
    private void parseNodeProjectFiles(
            @NotNull String workingDir,
            @NotNull Map<String, String> dependencies,
            @NotNull Map<String, String> projectFiles
    ) {
        Path dir = Paths.get(workingDir);
        Path packageJson = dir.resolve("package.json");

        if (Files.exists(packageJson)) {
            try {
                String content = Files.readString(packageJson);
                projectFiles.put("package.json", content);
                Map<String, String> deps = versionDetector.parsePackageJson(content);
                dependencies.putAll(deps);
            } catch (Exception e) {
                // Log error but continue
            }
        }

        // Try lockfiles for exact versions
        Path packageLock = dir.resolve("package-lock.json");
        if (Files.exists(packageLock)) {
            try {
                String content = Files.readString(packageLock);
                Map<String, String> exactVersions = versionDetector.parseLockfile(content, VersionDetector.LockfileType.PACKAGE_LOCK_JSON);
                dependencies.putAll(exactVersions);
            } catch (Exception e) {
                // Log error but continue
            }
        }
    }

    /**
     * Parses Java project files (pom.xml).
     *
     * @param workingDir working directory
     * @param dependencies dependencies map to populate
     * @param projectFiles project files map to populate
     */
    private void parseJavaProjectFiles(
            @NotNull String workingDir,
            @NotNull Map<String, String> dependencies,
            @NotNull Map<String, String> projectFiles
    ) {
        Path dir = Paths.get(workingDir);
        Path pomXml = dir.resolve("pom.xml");

        if (Files.exists(pomXml)) {
            try {
                String content = Files.readString(pomXml);
                projectFiles.put("pom.xml", content);
                Map<String, String> deps = versionDetector.parsePomXml(content);
                dependencies.putAll(deps);
            } catch (Exception e) {
                // Log error but continue
            }
        }
    }

    /**
     * Parses Gradle project files (build.gradle, build.gradle.kts).
     *
     * @param workingDir working directory
     * @param dependencies dependencies map to populate
     * @param projectFiles project files map to populate
     */
    private void parseGradleProjectFiles(
            @NotNull String workingDir,
            @NotNull Map<String, String> dependencies,
            @NotNull Map<String, String> projectFiles
    ) {
        Path dir = Paths.get(workingDir);
        Path buildGradle = dir.resolve("build.gradle");
        Path buildGradleKts = dir.resolve("build.gradle.kts");

        Path gradleFile = Files.exists(buildGradleKts) ? buildGradleKts : buildGradle;

        if (Files.exists(gradleFile)) {
            try {
                String content = Files.readString(gradleFile);
                projectFiles.put(gradleFile.getFileName().toString(), content);
                Map<String, String> deps = versionDetector.parseGradleBuild(content);
                dependencies.putAll(deps);
            } catch (Exception e) {
                // Log error but continue
            }
        }
    }

    /**
     * Detects runtime and framework information.
     *
     * @param workingDir working directory
     * @param runtimes runtimes map to populate
     * @param frameworks frameworks map to populate
     * @param tools tools map to populate
     */
    private void detectRuntimeInfo(
            @NotNull String workingDir,
            @NotNull Map<String, String> runtimes,
            @NotNull Map<String, String> frameworks,
            @NotNull Map<String, String> tools
    ) {
        Path dir = Paths.get(workingDir);

        // Detect Node.js version from package.json
        Path packageJson = dir.resolve("package.json");
        if (Files.exists(packageJson)) {
            try {
                String content = Files.readString(packageJson);
                if (content.contains("\"engines\"")) {
                    // Extract engines section for Node version
                    Pattern enginesPattern = Pattern.compile("\"engines\"\\s*:\\s*\\{[^}]*\"node\"\\s*:\\s*\"([^\"]+)\"");
                    Matcher matcher = enginesPattern.matcher(content);
                    if (matcher.find()) {
                        runtimes.put("node", matcher.group(1));
                    }
                }
                frameworks.put("npm", "detected");
            } catch (Exception e) {
                // Log error but continue
            }
        }

        // Detect Java version from pom.xml
        Path pomXml = dir.resolve("pom.xml");
        if (Files.exists(pomXml)) {
            try {
                String content = Files.readString(pomXml);
                Pattern javaVersionPattern = Pattern.compile("<maven.compiler.source>([^<]+)</maven.compiler.source>");
                Matcher matcher = javaVersionPattern.matcher(content);
                if (matcher.find()) {
                    runtimes.put("java", matcher.group(1));
                }
                frameworks.put("maven", "detected");
            } catch (Exception e) {
                // Log error but continue
            }
        }

        // Detect Gradle version
        Path gradleWrapper = dir.resolve("gradle/wrapper/gradle-wrapper.properties");
        if (Files.exists(gradleWrapper)) {
            try {
                String content = Files.readString(gradleWrapper);
                Pattern gradleVersionPattern = Pattern.compile("gradle-version=([^\\s]+)");
                Matcher matcher = gradleVersionPattern.matcher(content);
                if (matcher.find()) {
                    tools.put("gradle", matcher.group(1));
                }
            } catch (Exception e) {
                // Log error but continue
            }
        }
    }
}
