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
 * @doc.purpose Canonical repository inventory scanner with stable sorted walk, streaming SHA-256, comprehensive skip reasons
 * @doc.layer service
 * @doc.pattern Scanner
 * 
 * P1: Canonical scanner replacing all ad-hoc repository walking logic.
 * P0: Streaming SHA-256 checksum computation for memory efficiency with large files.
 * Provides:
 * - Stable sorted walk for deterministic ordering across OS/filesystem
 * - .gitignore pattern matching (simplified - production should use jgitignore library for full spec compliance)
 * - Include/exclude rule support for custom filtering
 * - Comprehensive skip reasons (vendor, generated, binary, large files, package boundaries)
 * - File type classification (source, config, docs, test, assets, build, unknown)
 * - SHA-256 checksum computation with streaming for memory efficiency
 * - Package boundary detection for multi-module projects
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

    public enum SkipReason {
        GITIGNORE,
        BINARY_FILE,
        VENDOR_DIRECTORY,
        GENERATED_FILE,
        FILE_TOO_LARGE,
        PACKAGE_BOUNDARY
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

    public record SkippedEntry(
        String relativePath,
        SkipReason reason
    ) {}

    /**
     * Repository inventory result
     */
    private static final long MAX_SINGLE_FILE_BYTES = 10L * 1024 * 1024; // 10 MB

    private static final Set<String> BINARY_EXTENSIONS = Set.of(
        ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".ico", ".webp", ".tiff",
        ".mp4", ".mp3", ".wav", ".ogg", ".mov", ".avi",
        ".pdf", ".zip", ".tar", ".gz", ".bz2", ".xz", ".7z", ".rar",
        ".jar", ".war", ".ear", ".class", ".pyc", ".pyo",
        ".exe", ".dll", ".so", ".dylib", ".lib", ".a",
        ".woff", ".woff2", ".ttf", ".eot", ".otf"
    );

    private static final Set<String> VENDOR_SEGMENTS = Set.of(
        "node_modules", "vendor", ".git", ".svn", ".hg",
        "__pycache__", ".venv", "venv", ".mypy_cache"
    );

    private static final Set<String> GENERATED_SEGMENTS = Set.of(
        "generated", "gen", "dist", "build", "out", ".next",
        ".gradle", "target", "__generated__"
    );

    private static final Set<String> PACKAGE_BOUNDARY_FILES = Set.of(
        "package.json", "pom.xml", "build.gradle", "build.gradle.kts",
        "go.mod", "Cargo.toml", "pyproject.toml"
    );

    public record InventoryResult(
        List<InventoryEntry> files,
        List<SkippedEntry> skipped,
        Map<FileType, Integer> fileCounts,
        int totalFiles,
        long totalBytes,
        List<String> packageBoundaries
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
     * Returns the skip reasons summary for a given inventory result.
     */
    public Map<SkipReason, Long> skipReasonSummary(InventoryResult result) {
        return result.skipped().stream()
            .collect(java.util.stream.Collectors.groupingBy(SkippedEntry::reason, java.util.stream.Collectors.counting()));
    }

    /**
     * Scan a repository directory with .gitignore filtering.
     * 
     * P1: Canonical scanner method with gitignore patterns.
     * 
     * @param rootPath Root directory of the repository
     * @param gitignorePatterns .gitignore patterns to filter
     * @return Inventory result with classified files
     * @throws IOException if scanning fails
     */
    public InventoryResult scanRepository(Path rootPath, Set<String> gitignorePatterns) throws IOException {
        return scanRepository(rootPath, gitignorePatterns, Set.of(), Set.of());
    }

    /**
     * Scan a repository directory with .gitignore filtering and include/exclude rules.
     * 
     * P1: Canonical scanner method with gitignore patterns and include/exclude rules.
     * Include rules take precedence over exclude rules and gitignore.
     * 
     * @param rootPath Root directory of the repository
     * @param gitignorePatterns .gitignore patterns to filter
     * @param includePatterns Patterns that must be included (overrides other filters)
     * @param excludePatterns Patterns that must be excluded (applied after include rules)
     * @return Inventory result with classified files
     * @throws IOException if scanning fails
     */
    public InventoryResult scanRepository(Path rootPath, Set<String> gitignorePatterns, 
                                       Set<String> includePatterns, Set<String> excludePatterns) throws IOException {
        List<InventoryEntry> files = new ArrayList<>();
        List<SkippedEntry> skipped = new ArrayList<>();
        Map<FileType, Integer> fileCounts = new HashMap<>();
        List<String> packageBoundaries = new ArrayList<>();
        long totalBytes = 0L;

        // Sorted walk for deterministic ordering across OS/filesystem
        List<Path> allPaths;
        try (var stream = Files.walk(rootPath)) {
            allPaths = stream
                .filter(Files::isRegularFile)
                .sorted()
                .toList();
        }

        for (Path path : allPaths) {
            String relative = rootPath.relativize(path).toString().replace('\\', '/');

            // P1: Include rules take precedence - if matched, skip all other filtering
            if (matchesAnyPattern(relative, includePatterns)) {
                files.add(toInventoryEntry(rootPath, path));
                continue;
            }

            // Package boundary detection
            String filename = path.getFileName().toString();
            if (PACKAGE_BOUNDARY_FILES.contains(filename.toLowerCase())) {
                String boundaryDir = rootPath.relativize(path.getParent()).toString().replace('\\', '/');
                if (!boundaryDir.isBlank() && !packageBoundaries.contains(boundaryDir)) {
                    packageBoundaries.add(boundaryDir);
                }
            }

            // P1: Exclude rules applied after include rules
            if (matchesAnyPattern(relative, excludePatterns)) {
                skipped.add(new SkippedEntry(relative, SkipReason.PACKAGE_BOUNDARY));
                continue;
            }

            // Skip vendor directories
            if (isInVendorDirectory(relative)) {
                skipped.add(new SkippedEntry(relative, SkipReason.VENDOR_DIRECTORY));
                continue;
            }

            // Skip generated directories
            if (isInGeneratedDirectory(relative)) {
                skipped.add(new SkippedEntry(relative, SkipReason.GENERATED_FILE));
                continue;
            }

            // Skip .gitignore-matched paths
            if (matchesGitignore(relative, gitignorePatterns)) {
                skipped.add(new SkippedEntry(relative, SkipReason.GITIGNORE));
                continue;
            }

            // Skip binary files
            if (isBinaryFile(relative)) {
                skipped.add(new SkippedEntry(relative, SkipReason.BINARY_FILE));
                continue;
            }

            // Skip oversized files
            long size = Files.size(path);
            if (size > MAX_SINGLE_FILE_BYTES) {
                skipped.add(new SkippedEntry(relative, SkipReason.FILE_TOO_LARGE));
                continue;
            }

            files.add(toInventoryEntry(rootPath, path));
        }

        for (InventoryEntry entry : files) {
            fileCounts.merge(entry.fileType(), 1, Integer::sum);
            totalBytes += entry.sizeBytes();
        }

        return new InventoryResult(files, skipped, fileCounts, files.size(), totalBytes, List.copyOf(packageBoundaries));
    }

    private static boolean isInVendorDirectory(String relativePath) {
        String[] segments = relativePath.split("/");
        for (String segment : segments) {
            if (VENDOR_SEGMENTS.contains(segment)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInGeneratedDirectory(String relativePath) {
        String[] segments = relativePath.split("/");
        for (String segment : segments) {
            if (GENERATED_SEGMENTS.contains(segment)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBinaryFile(String relativePath) {
        int dot = relativePath.lastIndexOf('.');
        if (dot < 0) {
            return false;
        }
        String ext = relativePath.substring(dot).toLowerCase();
        return BINARY_EXTENSIONS.contains(ext);
    }

    /**
     * Parse .gitignore file into a set of patterns.
        * Uses ordered rules (LinkedHashSet) so last-rule-wins semantics are preserved.
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
            .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
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
        boolean ignored = false;

        for (String pattern : patterns) {
            String candidate = pattern.trim();
            if (candidate.isEmpty() || "!".equals(candidate)) {
                continue;
            }

            boolean negated = candidate.startsWith("!");
            if (negated) {
                candidate = candidate.substring(1).trim();
                if (candidate.isEmpty()) {
                    continue;
                }
            }

            if (matchesPattern(normalizedPath, candidate)) {
                ignored = !negated;
            }
        }
        return ignored;
    }

    /**
     * Check if a path matches any pattern from a set.
     * P1: Used for include/exclude rule matching.
     */
    private static boolean matchesAnyPattern(String path, Set<String> patterns) {
        if (patterns.isEmpty()) {
            return false;
        }
        for (String pattern : patterns) {
            if (matchesPattern(path, pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if a path matches a specific gitignore pattern.
     * Supports globstar, wildcard, character classes, escaped tokens, anchored rules, and directory rules.
     */
    private static boolean matchesPattern(String path, String pattern) {
        String normalizedPath = path.replace('\\', '/');
        String candidate = pattern.trim();
        if (candidate.isEmpty()) {
            return false;
        }

        boolean directoryOnly = candidate.endsWith("/");
        if (directoryOnly) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }

        boolean anchored = candidate.startsWith("/");
        if (anchored) {
            candidate = candidate.substring(1);
        }

        String regexBody = globToRegex(candidate);
        String regex;
        if (anchored) {
            regex = "^" + regexBody + (directoryOnly ? "(?:/.*)?$" : "$");
        } else {
            regex = "^(?:.*/)?" + regexBody + (directoryOnly ? "(?:/.*)?$" : "$");
        }

        return normalizedPath.matches(regex);
    }

    private static String globToRegex(String pattern) {
        StringBuilder regex = new StringBuilder();
        boolean escaping = false;

        for (int i = 0; i < pattern.length(); i++) {
            char ch = pattern.charAt(i);

            if (escaping) {
                regex.append(java.util.regex.Pattern.quote(String.valueOf(ch)));
                escaping = false;
                continue;
            }

            if (ch == '\\') {
                escaping = true;
                continue;
            }

            if (ch == '*') {
                if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '*') {
                    regex.append(".*");
                    i++;
                } else {
                    regex.append("[^/]*");
                }
                continue;
            }

            if (ch == '?') {
                regex.append("[^/]");
                continue;
            }

            if (ch == '[') {
                int closing = pattern.indexOf(']', i + 1);
                if (closing > i + 1) {
                    String cls = pattern.substring(i, closing + 1);
                    regex.append(cls);
                    i = closing;
                    continue;
                }
            }

            if (".(){}+^$|".indexOf(ch) >= 0) {
                regex.append('\\');
            }
            regex.append(ch);
        }

        if (escaping) {
            regex.append("\\\\");
        }
        return regex.toString();
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
     * Compute SHA-256 checksum of a file using streaming to avoid loading entire file into memory.
     * 
     * P0: Streaming implementation for large file support and memory efficiency.
     * 
     * @param file File path
     * @return SHA-256 checksum
     * @throws IOException if reading fails
     */
    private String computeFileChecksum(Path file) throws IOException {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192]; // 8KB buffer for streaming
            
            try (java.io.InputStream in = java.nio.file.Files.newInputStream(file)) {
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            
            byte[] hash = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 not available", e);
        }
    }

}
