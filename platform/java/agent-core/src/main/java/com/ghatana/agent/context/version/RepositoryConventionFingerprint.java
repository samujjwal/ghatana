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
 * Fingerprint of repository conventions and conventions used in the codebase.
 *
 * @doc.type record
 * @doc.purpose Fingerprint of repository conventions
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record RepositoryConventionFingerprint(
        @NotNull Map<String, String> conventions,
        @NotNull String digest
) {
    public RepositoryConventionFingerprint {
        Objects.requireNonNull(conventions, "conventions must not be null");
        Objects.requireNonNull(digest, "digest must not be null");
        conventions = Map.copyOf(conventions);
    }

    /**
     * Creates a repository convention fingerprint.
     *
     * @param conventions map of convention names to values
     * @return repository convention fingerprint
     */
    @NotNull
    public static RepositoryConventionFingerprint fromConventions(@NotNull Map<String, String> conventions) {
        String digest = computeDigest(conventions);
        return new RepositoryConventionFingerprint(conventions, digest);
    }

    /**
     * Creates a repository convention fingerprint by parsing project files in the current directory.
     * Supports .gitignore, .eslintrc, .prettierrc, tsconfig.json, and similar convention files.
     *
     * @return repository convention fingerprint
     */
    @NotNull
    public static RepositoryConventionFingerprint fromProject() {
        Map<String, String> conventions = new HashMap<>();
        String projectRoot = System.getProperty("user.dir");

        // Try to parse .gitignore
        Path gitignore = Paths.get(projectRoot, ".gitignore");
        if (Files.exists(gitignore)) {
            try {
                conventions.put("hasGitignore", "true");
                conventions.put("gitignoreDigest", computeFileDigest(gitignore));
            } catch (Exception e) {
                // Log and continue
            }
        }

        // Try to detect ESLint
        Path eslintrc = Paths.get(projectRoot, ".eslintrc");
        Path eslintrcJson = Paths.get(projectRoot, ".eslintrc.json");
        Path eslintrcJs = Paths.get(projectRoot, ".eslintrc.js");
        if (Files.exists(eslintrc) || Files.exists(eslintrcJson) || Files.exists(eslintrcJs)) {
            conventions.put("hasEslint", "true");
        }

        // Try to detect Prettier
        Path prettierrc = Paths.get(projectRoot, ".prettierrc");
        Path prettierrcJson = Paths.get(projectRoot, ".prettierrc.json");
        if (Files.exists(prettierrc) || Files.exists(prettierrcJson)) {
            conventions.put("hasPrettier", "true");
        }

        // Try to detect TypeScript
        Path tsconfig = Paths.get(projectRoot, "tsconfig.json");
        if (Files.exists(tsconfig)) {
            conventions.put("hasTypeScript", "true");
            try {
                conventions.put("tsconfigDigest", computeFileDigest(tsconfig));
            } catch (Exception e) {
                // Log and continue
            }
        }

        // Try to detect Java/Kotlin conventions
        Path checkstyle = Paths.get(projectRoot, "checkstyle.xml");
        Path detekt = Paths.get(projectRoot, "detekt.yml");
        if (Files.exists(checkstyle)) {
            conventions.put("hasCheckstyle", "true");
        }
        if (Files.exists(detekt)) {
            conventions.put("hasDetekt", "true");
        }

        // Try to detect Docker
        Path dockerfile = Paths.get(projectRoot, "Dockerfile");
        Path dockerCompose = Paths.get(projectRoot, "docker-compose.yml");
        if (Files.exists(dockerfile)) {
            conventions.put("hasDockerfile", "true");
        }
        if (Files.exists(dockerCompose)) {
            conventions.put("hasDockerCompose", "true");
        }

        // Try to detect CI/CD
        Path githubActions = Paths.get(projectRoot, ".github", "workflows");
        Path gitlabCi = Paths.get(projectRoot, ".gitlab-ci.yml");
        if (Files.exists(githubActions)) {
            conventions.put("hasGithubActions", "true");
        }
        if (Files.exists(gitlabCi)) {
            conventions.put("hasGitlabCi", "true");
        }

        return fromConventions(conventions);
    }

    /**
     * Computes a simple digest for a file.
     *
     * @param path path to file
     * @return digest string
     */
    @NotNull
    private static String computeFileDigest(@NotNull Path path) throws Exception {
        String content = Files.readString(path);
        return Integer.toHexString(content.hashCode());
    }

    /**
     * Computes a digest from conventions.
     *
     * @param conventions conventions map
     * @return digest string
     */
    @NotNull
    private static String computeDigest(@NotNull Map<String, String> conventions) {
        return Integer.toHexString(Objects.hash(conventions));
    }

    /**
     * Returns true if this fingerprint matches another.
     *
     * @param other other fingerprint
     * @return true if digests match
     */
    public boolean matches(@NotNull RepositoryConventionFingerprint other) {
        return this.digest.equals(other.digest);
    }
}
