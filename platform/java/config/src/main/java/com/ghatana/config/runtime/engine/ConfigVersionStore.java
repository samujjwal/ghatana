/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */
package com.ghatana.config.runtime.engine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * File-backed version store for configuration snapshots.
 * <p>
 * Each call to {@link #snapshot(String, Path)} computes an SHA-256 digest of
 * the source file. If the digest has changed since the last snapshot an
 * incremented version label ({@code v1}, {@code v2}, …) is created and the
 * content is persisted under {@code baseDir/<configName>/<version>.yaml}.
 * </p>
 *
 * @doc.type class
 * @doc.purpose Versioned configuration storage backed by the filesystem
 * @doc.layer platform
 * @doc.pattern Repository
 */
public class ConfigVersionStore {

    private final Path baseDir;

    /** configName → ordered list of versions (newest first) */
    private final Map<String, List<String>> versionsByConfig = new ConcurrentHashMap<>();

    /** configName → latest SHA-256 hex */
    private final Map<String, String> lastDigest = new ConcurrentHashMap<>();

    public ConfigVersionStore(Path baseDir) {
        this.baseDir = Objects.requireNonNull(baseDir, "baseDir");
    }

    /**
     * Creates a new version snapshot for the given config if the content has changed.
     *
     * @param configName logical configuration name
     * @param sourceFile the YAML file to snapshot
     * @return the version label (e.g. {@code v1}); returns the current version if content is unchanged
     * @throws IOException if file I/O fails
     */
    public String snapshot(String configName, Path sourceFile) throws IOException {
        String content = Files.readString(sourceFile);
        String digest = sha256(content);

        // If content hasn't changed, return current version without bumping
        if (digest.equals(lastDigest.get(configName))) {
            return getCurrentVersion(configName);
        }

        List<String> versions = versionsByConfig.computeIfAbsent(configName, k -> new ArrayList<>());
        int nextNum = versions.size() + 1;
        String version = "v" + nextNum;

        // Persist content
        Path configDir = baseDir.resolve(configName);
        Files.createDirectories(configDir);
        Files.writeString(configDir.resolve(version + ".yaml"), content);

        versions.add(0, version); // newest first
        lastDigest.put(configName, digest);

        return version;
    }

    /**
     * Returns the latest version label for a config, or {@code "v0"} if unknown.
     */
    public String getCurrentVersion(String configName) {
        List<String> versions = versionsByConfig.get(configName);
        if (versions == null || versions.isEmpty()) {
            return "v0";
        }
        return versions.get(0);
    }

    /**
     * Returns all versions for a config, newest first.
     */
    public List<String> listVersions(String configName) {
        return List.copyOf(versionsByConfig.getOrDefault(configName, List.of()));
    }

    /**
     * Returns the total number of snapshots across all configs.
     */
    public int totalSnapshots() {
        return versionsByConfig.values().stream().mapToInt(List::size).sum();
    }

    /**
     * Returns the stored content for a specific version.
     */
    public Optional<String> getVersionContent(String configName, String version) {
        Path file = baseDir.resolve(configName).resolve(version + ".yaml");
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(file));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * Restores content from a previous version, creating a new snapshot.
     *
     * @return the path of the restored file, or empty if the version is not found
     */
    public Optional<Path> rollback(String configName, String version) throws IOException {
        Optional<String> content = getVersionContent(configName, version);
        if (content.isEmpty()) {
            return Optional.empty();
        }

        // Write content to a temp file, then snapshot it to create a new version
        Path tempFile = baseDir.resolve(configName + "-rollback.tmp");
        Files.writeString(tempFile, content.get());

        // Force digest change by clearing the cached digest so the snapshot always records
        lastDigest.remove(configName);
        snapshot(configName, tempFile);

        Files.deleteIfExists(tempFile);
        return Optional.of(baseDir.resolve(configName).resolve(getCurrentVersion(configName) + ".yaml"));
    }

    // ─── Internals ──────────────────────────────────────────────────────────

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
