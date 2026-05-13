/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.environment;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects version information from various sources (package files, lockfiles, etc.).
 *
 * @doc.type class
 * @doc.purpose Detects version information from project files
 * @doc.layer agent-core
 * @doc.pattern Service
 */
public final class VersionDetector {

    /**
     * Parses package.json and extracts dependency versions.
     *
     * @param packageJsonContent content of package.json
     * @return map of dependency name to version
     */
    @NotNull
    public Map<String, String> parsePackageJson(@NotNull String packageJsonContent) {
        Objects.requireNonNull(packageJsonContent, "packageJsonContent must not be null");
        Map<String, String> dependencies = new HashMap<>();
        
        // Extract dependencies section
        Pattern depsPattern = Pattern.compile("\"dependencies\"\\s*:\\s*\\{([^}]+)\\}", Pattern.DOTALL);
        Matcher depsMatcher = depsPattern.matcher(packageJsonContent);
        
        if (depsMatcher.find()) {
            String depsContent = depsMatcher.group(1);
            extractDependencies(depsContent, dependencies);
        }
        
        // Extract devDependencies section
        Pattern devDepsPattern = Pattern.compile("\"devDependencies\"\\s*:\\s*\\{([^}]+)\\}", Pattern.DOTALL);
        Matcher devDepsMatcher = devDepsPattern.matcher(packageJsonContent);
        
        if (devDepsMatcher.find()) {
            String devDepsContent = devDepsMatcher.group(1);
            extractDependencies(devDepsContent, dependencies);
        }
        
        return dependencies;
    }

    /**
     * Extracts dependencies from JSON-like content.
     *
     * @param content JSON-like content
     * @param dependencies map to populate
     */
    private void extractDependencies(@NotNull String content, @NotNull Map<String, String> dependencies) {
        Pattern depPattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = depPattern.matcher(content);
        
        while (matcher.find()) {
            String name = matcher.group(1);
            String version = matcher.group(2);
            dependencies.put(name, version);
        }
    }

    /**
     * Parses pom.xml and extracts dependency versions.
     *
     * @param pomXmlContent content of pom.xml
     * @return map of dependency name to version
     */
    @NotNull
    public Map<String, String> parsePomXml(@NotNull String pomXmlContent) {
        Objects.requireNonNull(pomXmlContent, "pomXmlContent must not be null");
        Map<String, String> dependencies = new HashMap<>();
        
        // Pattern for Maven dependencies: <dependency><groupId>...</groupId><artifactId>...</artifactId><version>...</version></dependency>
        Pattern depPattern = Pattern.compile(
                "<dependency>\\s*<groupId>([^<]+)</groupId>\\s*<artifactId>([^<]+)</artifactId>\\s*<version>([^<]+)</version>",
                Pattern.DOTALL
        );
        
        Matcher matcher = depPattern.matcher(pomXmlContent);
        while (matcher.find()) {
            String groupId = matcher.group(1).trim();
            String artifactId = matcher.group(2).trim();
            String version = matcher.group(3).trim();
            String name = groupId + ":" + artifactId;
            dependencies.put(name, version);
        }
        
        return dependencies;
    }

    /**
     * Parses Gradle build files and extracts dependency versions.
     *
     * @param buildGradleContent content of build.gradle or build.gradle.kts
     * @return map of dependency name to version
     */
    @NotNull
    public Map<String, String> parseGradleBuild(@NotNull String buildGradleContent) {
        Objects.requireNonNull(buildGradleContent, "buildGradleContent must not be null");
        Map<String, String> dependencies = new HashMap<>();
        
        // Pattern for Gradle dependencies: implementation 'group:name:version' or implementation("group:name:version")
        Pattern depPattern = Pattern.compile(
                "(?:implementation|api|compile|testImplementation)\\s*['\"]([^'\"]+)['\"]",
                Pattern.MULTILINE
        );
        
        Matcher matcher = depPattern.matcher(buildGradleContent);
        while (matcher.find()) {
            String dep = matcher.group(1);
            // Parse group:name:version format
            String[] parts = dep.split(":");
            if (parts.length >= 3) {
                String name = parts[0] + ":" + parts[1];
                String version = parts[2];
                dependencies.put(name, version);
            }
        }
        
        return dependencies;
    }

    /**
     * Parses lockfiles (package-lock.json, yarn.lock, pnpm-lock.yaml) and extracts exact versions.
     *
     * @param lockfileContent content of lockfile
     * @param lockfileType type of lockfile
     * @return map of dependency name to exact version
     */
    @NotNull
    public Map<String, String> parseLockfile(@NotNull String lockfileContent, @NotNull LockfileType lockfileType) {
        Objects.requireNonNull(lockfileContent, "lockfileContent must not be null");
        Objects.requireNonNull(lockfileType, "lockfileType must not be null");
        
        return switch (lockfileType) {
            case PACKAGE_LOCK_JSON -> parsePackageLockJson(lockfileContent);
            case YARN_LOCK -> parseYarnLock(lockfileContent);
            case PNPM_LOCK_YAML -> parsePnpmLockYaml(lockfileContent);
            case GRADLE_LOCK -> parseGradleLock(lockfileContent);
        };
    }

    /**
     * Parses package-lock.json and extracts exact versions.
     *
     * @param lockfileContent content of package-lock.json
     * @return map of dependency name to exact version
     */
    @NotNull
    private Map<String, String> parsePackageLockJson(@NotNull String lockfileContent) {
        Map<String, String> dependencies = new HashMap<>();
        
        // Pattern for package-lock.json dependencies: "name": "version"
        Pattern depPattern = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"[\\s\\S]*?\"version\"\\s*:\\s*\"([^\"]+)\"", Pattern.DOTALL);
        Matcher matcher = depPattern.matcher(lockfileContent);
        
        while (matcher.find()) {
            String name = matcher.group(1);
            String version = matcher.group(2);
            dependencies.put(name, version);
        }
        
        return dependencies;
    }

    /**
     * Parses yarn.lock and extracts exact versions.
     *
     * @param lockfileContent content of yarn.lock
     * @return map of dependency name to exact version
     */
    @NotNull
    private Map<String, String> parseYarnLock(@NotNull String lockfileContent) {
        Map<String, String> dependencies = new HashMap<>();
        
        // Pattern for yarn.lock entries: name@version:
        Pattern entryPattern = Pattern.compile("^([\\w\\-@/.]+)@([^:]+):", Pattern.MULTILINE);
        Matcher matcher = entryPattern.matcher(lockfileContent);
        
        while (matcher.find()) {
            String name = matcher.group(1);
            String version = matcher.group(2);
            dependencies.put(name, version);
        }
        
        return dependencies;
    }

    /**
     * Parses pnpm-lock.yaml and extracts exact versions.
     *
     * @param lockfileContent content of pnpm-lock.yaml
     * @return map of dependency name to exact version
     */
    @NotNull
    private Map<String, String> parsePnpmLockYaml(@NotNull String lockfileContent) {
        Map<String, String> dependencies = new HashMap<>();
        
        // Pattern for pnpm-lock.yaml entries: name: version
        Pattern depPattern = Pattern.compile("^\\s+([\\w\\-@/.]+):\\s+([^\\s]+)", Pattern.MULTILINE);
        Matcher matcher = depPattern.matcher(lockfileContent);
        
        while (matcher.find()) {
            String name = matcher.group(1);
            String version = matcher.group(2);
            dependencies.put(name, version);
        }
        
        return dependencies;
    }

    /**
     * Parses Gradle lockfile and extracts exact versions.
     *
     * @param lockfileContent content of Gradle lockfile
     * @return map of dependency name to exact version
     */
    @NotNull
    private Map<String, String> parseGradleLock(@NotNull String lockfileContent) {
        Map<String, String> dependencies = new HashMap<>();
        
        // Pattern for Gradle lockfile entries: group:name:version
        Pattern depPattern = Pattern.compile("([\\w\\-.]+):([\\w\\-.]+):([\\w\\-.+]+)");
        Matcher matcher = depPattern.matcher(lockfileContent);
        
        while (matcher.find()) {
            String group = matcher.group(1);
            String name = matcher.group(2);
            String version = matcher.group(3);
            String fullName = group + ":" + name;
            dependencies.put(fullName, version);
        }
        
        return dependencies;
    }

    /**
     * Types of lockfiles.
     */
    public enum LockfileType {
        PACKAGE_LOCK_JSON,
        YARN_LOCK,
        PNPM_LOCK_YAML,
        GRADLE_LOCK
    }
}
