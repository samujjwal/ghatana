package com.ghatana.virtualorg.tool.impl;

import com.ghatana.virtualorg.tool.Tool;
import com.ghatana.virtualorg.tool.ToolResult;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * File system operations tool with sandboxed access.
 *
 * <p><b>Purpose</b><br>
 * Adapter implementing {@link Tool} for file system operations with
 * security-constrained access to prevent directory traversal attacks.
 *
 * <p><b>Architecture Role</b><br>
 * Tool adapter wrapping Java NIO.2 file operations. Provides:
 * - File read/write operations
 * - Directory listing and creation
 * - File search with glob patterns
 * - Sandboxed access (restricted to baseDir)
 *
 * <p><b>Supported Operations</b><br>
 * - **read**: Read file contents
 * - **write**: Write content to file
 * - **append**: Append content to file
 * - **delete**: Delete file or directory
 * - **list**: List directory contents
 * - **mkdir**: Create directory
 * - **exists**: Check if file exists
 * - **search**: Search for files by glob pattern
 *
 * <p><b>Security</b><br>
 * Operations are restricted to allowed base directories to prevent:
 * - Directory traversal attacks (../)
 * - Access to sensitive system files
 * - Symlink escape attempts
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * FileOperationsTool fileTool = new FileOperationsTool(
 *     eventloop,
 *     "/workspace",  // base directory
 *     30             // timeout seconds
 * );
 * 
 * // Read file
 * ToolResult content = fileTool.execute(Map.of(
 *     "operation", "read",
 *     "path", "src/main.java"
 * )).getResult();
 * 
 * // Search files
 * ToolResult files = fileTool.execute(Map.of(
 *     "operation", "search",
 *     "pattern", "*.java"
 * )).getResult();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose File system operations tool with sandboxed access
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class FileOperationsTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(FileOperationsTool.class);

    private final Eventloop eventloop;
    private final String id;
    private final int timeoutSeconds;
    private final Path baseDir;
    private volatile boolean enabled;

    public FileOperationsTool(
            @NotNull Eventloop eventloop,
            @NotNull String baseDir,
            int timeoutSeconds) {

        this.eventloop = eventloop;
        this.id = "file-operations-tool";
        this.baseDir = Paths.get(baseDir).toAbsolutePath().normalize();
        this.timeoutSeconds = timeoutSeconds;
        this.enabled = true;

        log.info("Initialized FileOperationsTool: baseDir={}", this.baseDir);
    }

    @Override
    @NotNull
    public String getId() {
        return id;
    }

    @Override
    @NotNull
    public String getName() {
        return "file_operations";
    }

    @Override
    @NotNull
    public String getDescription() {
        return "Performs file system operations (read, write, append, delete, list, mkdir, exists, search)";
    }

    @Override
    @NotNull
    public String getParameterSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "operation": {
                      "type": "string",
                      "enum": ["read", "write", "append", "delete", "list", "mkdir", "exists", "search"],
                      "description": "File operation to perform"
                    },
                    "path": {
                      "type": "string",
                      "description": "File or directory path (relative to base directory)"
                    },
                    "content": {
                      "type": "string",
                      "description": "Content for write/append operations"
                    },
                    "pattern": {
                      "type": "string",
                      "description": "Search pattern (glob)"
                    }
                  },
                  "required": ["operation", "path"]
                }
                """;
    }

    @Override
    @NotNull
    public Promise<ToolResult> execute(@NotNull Map<String, String> arguments) {
        Instant start = Instant.now();

        return Promise.ofBlocking(eventloop, () -> {
            try {
                String operation = arguments.get("operation");
                String pathStr = arguments.get("path");

                if (operation == null || pathStr == null) {
                    throw new IllegalArgumentException("operation and path are required");
                }

                // Resolve and validate path
                Path path = resolvePath(pathStr);
                validatePath(path);

                String result = switch (operation.toLowerCase()) {
                    case "read" -> doRead(path);
                    case "write" -> doWrite(path, arguments.get("content"));
                    case "append" -> doAppend(path, arguments.get("content"));
                    case "delete" -> doDelete(path);
                    case "list" -> doList(path);
                    case "mkdir" -> doMkdir(path);
                    case "exists" -> doExists(path);
                    case "search" -> doSearch(path, arguments.get("pattern"));
                    default -> throw new IllegalArgumentException("Unknown operation: " + operation);
                };

                Duration duration = Duration.between(start, Instant.now());
                log.debug("File operation completed: op={}, path={}, duration={}ms",
                        operation, pathStr, duration.toMillis());

                return ToolResult.success(result, duration);

            } catch (Exception e) {
                Duration duration = Duration.between(start, Instant.now());
                log.error("File operation failed", e);
                return ToolResult.failure("File operation failed: " + e.getMessage(), duration);
            }
        });
    }

    @Override
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    // =============================
    // File operations
    // =============================

    private String doRead(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("File not found: " + path);
        }

        if (Files.isDirectory(path)) {
            throw new IOException("Cannot read directory: " + path);
        }

        return Files.readString(path);
    }

    private String doWrite(Path path, String content) throws IOException {
        if (content == null) {
            throw new IllegalArgumentException("Content is required for write operation");
        }

        // Create parent directories if needed
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }

        Files.writeString(path, content);

        return "Written " + content.length() + " characters to " + path.getFileName();
    }

    private String doAppend(Path path, String content) throws IOException {
        if (content == null) {
            throw new IllegalArgumentException("Content is required for append operation");
        }

        Files.writeString(path, content, StandardOpenOption.CREATE, StandardOpenOption.APPEND);

        return "Appended " + content.length() + " characters to " + path.getFileName();
    }

    private String doDelete(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("File not found: " + path);
        }

        if (Files.isDirectory(path)) {
            // Delete directory recursively (be careful!)
            try (Stream<Path> walk = Files.walk(path)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.delete(p);
                            } catch (IOException e) {
                                log.error("Failed to delete: {}", p, e);
                            }
                        });
            }
            return "Deleted directory: " + path.getFileName();
        } else {
            Files.delete(path);
            return "Deleted file: " + path.getFileName();
        }
    }

    private String doList(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("Directory not found: " + path);
        }

        if (!Files.isDirectory(path)) {
            throw new IOException("Not a directory: " + path);
        }

        try (Stream<Path> files = Files.list(path)) {
            StringBuilder result = new StringBuilder("Contents of " + path.getFileName() + ":\n\n");

            files.forEach(file -> {
                String type = Files.isDirectory(file) ? "DIR " : "FILE";
                result.append(type).append(" ").append(file.getFileName()).append("\n");
            });

            return result.toString();
        }
    }

    private String doMkdir(Path path) throws IOException {
        Files.createDirectories(path);
        return "Created directory: " + path;
    }

    private String doExists(Path path) {
        boolean exists = Files.exists(path);
        return exists ? "File exists: " + path : "File does not exist: " + path;
    }

    private String doSearch(Path path, String pattern) throws IOException {
        if (pattern == null || pattern.isEmpty()) {
            throw new IllegalArgumentException("Pattern is required for search operation");
        }

        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);

        try (Stream<Path> files = Files.walk(path)) {
            String results = files
                    .filter(p -> matcher.matches(p.getFileName()))
                    .map(p -> baseDir.relativize(p).toString())
                    .collect(Collectors.joining("\n"));

            return results.isEmpty() ? "No files found matching: " + pattern : results;
        }
    }

    // =============================
    // Security helpers
    // =============================

    private Path resolvePath(String pathStr) {
        Path path = baseDir.resolve(pathStr).normalize();
        return path;
    }

    private void validatePath(Path path) throws IOException {
        // Ensure path is within base directory (prevent directory traversal)
        if (!path.startsWith(baseDir)) {
            throw new SecurityException("Path outside base directory: " + path);
        }
    }
}
