/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.context.version;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Fingerprint of dependency versions for a specific context.
 *
 * @doc.type record
 * @doc.purpose Fingerprint of dependency versions
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record DependencyFingerprint(
        @NotNull Map<String, String> packageVersions,
        @NotNull String digest
) {
    public DependencyFingerprint {
        Objects.requireNonNull(packageVersions, "packageVersions must not be null");
        Objects.requireNonNull(digest, "digest must not be null");
        packageVersions = Map.copyOf(packageVersions);
    }

    /**
     * Creates a dependency fingerprint from package versions.
     *
     * @param packageVersions package versions
     * @return dependency fingerprint
     */
    @NotNull
    public static DependencyFingerprint fromVersions(@NotNull Map<String, String> packageVersions) {
        String digest = computeDigest(packageVersions);
        return new DependencyFingerprint(packageVersions, digest);
    }

    /**
     * Creates a dependency fingerprint by parsing project files in the current directory.
     * Supports package.json, pom.xml, build.gradle.kts, and pnpm-lock.yaml.
     *
     * @return dependency fingerprint
     */
    @NotNull
    public static DependencyFingerprint fromProject() {
        Map<String, String> versions = new HashMap<>();
        String projectRoot = System.getProperty("user.dir");

        // Try to parse package.json
        Path packageJson = Paths.get(projectRoot, "package.json");
        if (Files.exists(packageJson)) {
            try {
                parsePackageJson(packageJson, versions);
            } catch (Exception e) {
                // Log and continue
            }
        }

        // Try to parse pom.xml
        Path pomXml = Paths.get(projectRoot, "pom.xml");
        if (Files.exists(pomXml)) {
            try {
                parsePomXml(pomXml, versions);
            } catch (Exception e) {
                // Log and continue
            }
        }

        // Try to parse build.gradle.kts
        Path gradleKts = Paths.get(projectRoot, "build.gradle.kts");
        if (Files.exists(gradleKts)) {
            try {
                parseGradleKts(gradleKts, versions);
            } catch (Exception e) {
                // Log and continue
            }
        }

        // Try to parse pnpm-lock.yaml
        Path pnpmLock = Paths.get(projectRoot, "pnpm-lock.yaml");
        if (Files.exists(pnpmLock)) {
            try {
                parsePnpmLock(pnpmLock, versions);
            } catch (Exception e) {
                // Log and continue
            }
        }

        return fromVersions(versions);
    }

    /**
     * Parses package.json to extract dependency versions.
     * This is a simplified parser - in production, use a proper JSON parser.
     *
     * @param path path to package.json
     * @param versions map to populate with versions
     */
    private static void parsePackageJson(@NotNull Path path, @NotNull Map<String, String> versions) throws Exception {
        String content = Files.readString(path);
        // Simple regex-based parsing for dependencies
        // In production, use Jackson or similar JSON library
        java.util.regex.Pattern depPattern = java.util.regex.Pattern.compile("\"([^\"]+)\":\\s*\"([^\"]+)\"");
        java.util.regex.Matcher matcher = depPattern.matcher(content);
        while (matcher.find()) {
            String name = matcher.group(1);
            String version = matcher.group(2);
            if (!name.equals("name") && !name.equals("version") && !name.equals("description")) {
                versions.put(name, version);
            }
        }
    }

    /**
     * Parses pom.xml to extract dependency versions.
     * This is a simplified parser - in production, use Maven model.
     *
     * @param path path to pom.xml
     * @param versions map to populate with versions
     */
    private static void parsePomXml(@NotNull Path path, @NotNull Map<String, String> versions) throws Exception {
        String content = Files.readString(path);
        // Simple regex-based parsing for dependencies
        // In production, use Maven model or proper XML parser
        java.util.regex.Pattern depPattern = java.util.regex.Pattern.compile("<artifactId>([^<]+)</artifactId>\\s*<version>([^<]+)</version>");
        java.util.regex.Matcher matcher = depPattern.matcher(content);
        while (matcher.find()) {
            String name = matcher.group(1);
            String version = matcher.group(2);
            versions.put(name, version);
        }
    }

    /**
     * Parses build.gradle.kts to extract dependency versions.
     * This is a simplified parser - in production, use Gradle API.
     *
     * @param path path to build.gradle.kts
     * @param versions map to populate with versions
     */
    private static void parseGradleKts(@NotNull Path path, @NotNull Map<String, String> versions) throws Exception {
        String content = Files.readString(path);
        // Simple regex-based parsing for dependencies
        // In production, use Gradle tooling API
        java.util.regex.Pattern depPattern = java.util.regex.Pattern.compile("\"([^\"]+)\":\"([^\"]+)\"");
        java.util.regex.Matcher matcher = depPattern.matcher(content);
        while (matcher.find()) {
            String name = matcher.group(1);
            String version = matcher.group(2);
            versions.put(name, version);
        }
    }

    /**
     * Parses pnpm-lock.yaml to extract dependency versions.
     * This is a simplified parser - in production, use a proper YAML parser.
     *
     * @param path path to pnpm-lock.yaml
     * @param versions map to populate with versions
     */
    private static void parsePnpmLock(@NotNull Path path, @NotNull Map<String, String> versions) throws Exception {
        String content = Files.readString(path);
        // Simple regex-based parsing for dependencies
        // In production, use SnakeYAML or similar
        java.util.regex.Pattern depPattern = java.util.regex.Pattern.compile("([^:]+):\\s*([^\\s]+)");
        java.util.regex.Matcher matcher = depPattern.matcher(content);
        while (matcher.find()) {
            String name = matcher.group(1);
            String version = matcher.group(2);
            if (!name.startsWith("  ")) {
                versions.put(name, version);
            }
        }
    }

    /**
     * Computes a digest from package versions.
     *
     * @param packageVersions package versions
     * @return digest string
     */
    @NotNull
    private static String computeDigest(@NotNull Map<String, String> packageVersions) {
        return Integer.toHexString(Objects.hash(packageVersions));
    }

    /**
     * Returns true if this fingerprint matches another.
     *
     * @param other other fingerprint
     * @return true if digests match
     */
    public boolean matches(@NotNull DependencyFingerprint other) {
        return this.digest.equals(other.digest);
    }
}
