package com.ghatana.yappc.services.source;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @doc.type class
 * @doc.purpose Repository inventory scanner with .gitignore parsing and file classification
 * @doc.layer service
 * @doc.pattern Scanner
 * 
 * P0: Added repository inventory scanner to classify files and respect .gitignore rules.
 * Provides file type classification (source, config, docs, test, etc.) and gitignore filtering.
 */
public final class RepositoryInventoryScanner {

    /**
     * File classification types
     */
    public enum FileType {
        SOURCE,
        CONFIG,
        DOCS,
        TEST,
        ASSETS,
        BUILD,
        UNKNOWN
    }

    /**
     * File inventory entry
     */
    public record InventoryEntry(
        String relativePath,
        FileType fileType,
        long sizeBytes,
        String checksum
    ) {}

    /**
     * Repository inventory result
     */
    public record InventoryResult(
        List<InventoryEntry> files,
        Map<FileType, Integer> fileCounts,
        int totalFiles,
        long totalBytes
    ) {}

    /**
     * Scan a repository directory and classify files.
     * 
     * @param rootPath Root directory of the repository
     * @return Inventory result with classified files
     * @throws IOException if scanning fails
     */
    public InventoryResult scanRepository(Path rootPath) throws IOException {
        return scanRepository(rootPath, Set.of());
    }

    /**
     * Scan a repository directory with .gitignore filtering.
     * 
     * @param rootPath Root directory of the repository
     * @param gitignorePatterns .gitignore patterns to filter
     * @return Inventory result with classified files
     * @throws IOException if scanning fails
     */
    public InventoryResult scanRepository(Path rootPath, Set<String> gitignorePatterns) throws IOException {
        List<InventoryEntry> files = new ArrayList<>();
        Map<FileType, Integer> fileCounts = new HashMap<>();
        long totalBytes = 0L;

        try (var stream = Files.walk(rootPath)) {
            files = new ArrayList<>();
            for (Path path : stream
                .filter(Files::isRegularFile)
                .filter(path -> !matchesGitignore(rootPath.relativize(path).toString(), gitignorePatterns))
                .toList()) {
                files.add(toInventoryEntry(rootPath, path));
            }
        }

        for (InventoryEntry entry : files) {
            fileCounts.merge(entry.fileType(), 1, Integer::sum);
            totalBytes += entry.sizeBytes();
        }

        return new InventoryResult(files, fileCounts, files.size(), totalBytes);
    }

    /**
     * Parse .gitignore file into a set of patterns.
     * 
     * @param rootPath Root directory of the repository
     * @return Set of .gitignore patterns
     * @throws IOException if reading fails
     */
    public Set<String> parseGitignore(Path rootPath) throws IOException {
        Path gitignorePath = rootPath.resolve(".gitignore");
        if (!Files.exists(gitignorePath)) {
            return Set.of();
        }

        return Files.lines(gitignorePath)
            .map(String::trim)
            .filter(line -> !line.isEmpty() && !line.startsWith("#"))
            .collect(Collectors.toSet());
    }

    /**
     * Classify a file by its extension and path.
     * 
     * @param relativePath Relative path of the file
     * @return File type classification
     */
    public static FileType classifyFile(String relativePath) {
        String lowerPath = relativePath.toLowerCase();
        
        // Source files
        if (lowerPath.endsWith(".java") || lowerPath.endsWith(".kt") || lowerPath.endsWith(".scala") ||
            lowerPath.endsWith(".ts") || lowerPath.endsWith(".tsx") || lowerPath.endsWith(".js") ||
            lowerPath.endsWith(".py") || lowerPath.endsWith(".go") || lowerPath.endsWith(".rs") ||
            lowerPath.endsWith(".c") || lowerPath.endsWith(".cpp") || lowerPath.endsWith(".h")) {
            return FileType.SOURCE;
        }
        
        // Config files
        if (lowerPath.endsWith(".yaml") || lowerPath.endsWith(".yml") || lowerPath.endsWith(".json") ||
            lowerPath.endsWith(".toml") || lowerPath.endsWith(".properties") || lowerPath.endsWith(".xml") ||
            lowerPath.endsWith(".conf") || lowerPath.endsWith(".ini")) {
            return FileType.CONFIG;
        }
        
        // Documentation
        if (lowerPath.endsWith(".md") || lowerPath.endsWith(".rst") || lowerPath.endsWith(".txt") ||
            lowerPath.contains("readme") || lowerPath.contains("docs/") || lowerPath.contains("doc/")) {
            return FileType.DOCS;
        }
        
        // Test files
        if (lowerPath.contains("test") || lowerPath.contains("spec") || lowerPath.endsWith(".test.ts") ||
            lowerPath.endsWith(".test.js") || lowerPath.endsWith("_test.go") || lowerPath.endsWith("_test.py")) {
            return FileType.TEST;
        }
        
        // Build files
        if (lowerPath.endsWith("build.gradle") || lowerPath.endsWith("pom.xml") || lowerPath.endsWith("package.json") ||
            lowerPath.endsWith("makefile") || lowerPath.endsWith("dockerfile") || lowerPath.contains("build/")) {
            return FileType.BUILD;
        }
        
        // Assets
        if (lowerPath.endsWith(".png") || lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg") ||
            lowerPath.endsWith(".svg") || lowerPath.endsWith(".gif") || lowerPath.endsWith(".ico") ||
            lowerPath.endsWith(".css") || lowerPath.endsWith(".scss") || lowerPath.endsWith(".less")) {
            return FileType.ASSETS;
        }
        
        return FileType.UNKNOWN;
    }

    /**
     * Check if a path matches any gitignore pattern.
     * 
     * @param relativePath Relative path to check
     * @param patterns Gitignore patterns
     * @return true if path matches any pattern
     */
    private static boolean matchesGitignore(String relativePath, Set<String> patterns) {
        if (patterns.isEmpty()) {
            return false;
        }
        
        String normalizedPath = relativePath.replace('\\', '/');
        
        for (String pattern : patterns) {
            if (matchesPattern(normalizedPath, pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if a path matches a specific gitignore pattern (simplified implementation).
     * 
     * @param path Path to check
     * @param pattern Gitignore pattern
     * @return true if path matches pattern
     */
    private static boolean matchesPattern(String path, String pattern) {
        // Simplified gitignore matching
        // For production, use a proper gitignore library
        
        // Exact match
        if (path.equals(pattern)) {
            return true;
        }
        
        // Directory match
        if (pattern.endsWith("/") && path.startsWith(pattern)) {
            return true;
        }
        
        // Prefix match
        if (path.startsWith(pattern)) {
            return true;
        }
        
        // Wildcard match (simplified)
        if (pattern.contains("*")) {
            String regex = pattern.replace(".", "\\.")
                .replace("*", ".*")
                .replace("?", ".");
            return path.matches(regex);
        }
        
        return false;
    }

    /**
     * Convert a file path to an inventory entry.
     * 
     * @param root Root directory
     * @param file File path
     * @return Inventory entry
     * @throws IOException if reading fails
     */
    private InventoryEntry toInventoryEntry(Path root, Path file) throws IOException {
        String relativePath = root.relativize(file).toString().replace('\\', '/');
        FileType fileType = classifyFile(relativePath);
        long sizeBytes = Files.size(file);
        String checksum = computeFileChecksum(file);
        
        return new InventoryEntry(relativePath, fileType, sizeBytes, checksum);
    }

    /**
     * Compute SHA-256 checksum of a file.
     * 
     * @param file File path
     * @return SHA-256 checksum
     * @throws IOException if reading fails
     */
    private String computeFileChecksum(Path file) throws IOException {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] content = Files.readAllBytes(file);
            digest.update(content);
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
