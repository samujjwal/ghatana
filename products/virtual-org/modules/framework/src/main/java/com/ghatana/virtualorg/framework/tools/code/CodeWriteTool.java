package com.ghatana.virtualorg.framework.tools.code;

import static com.ghatana.virtualorg.framework.util.BlockingExecutors.blockingExecutor;

import com.ghatana.virtualorg.framework.tools.AgentTool;
import com.ghatana.virtualorg.framework.tools.ToolContext;
import com.ghatana.virtualorg.framework.tools.ToolInput;
import com.ghatana.virtualorg.framework.tools.ToolResult;
import io.activej.promise.Promise;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Tool for writing code files.
 *
 * <p>
 * <b>Purpose</b><br>
 * Allows agents to write or modify code files with: - Create new files -
 * Overwrite existing files - Insert content at specific lines - Replace content
 * within line ranges
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * AgentTool tool = new CodeWriteTool("/app/workspace");
 *
 * // Create new file
 * ToolInput input = ToolInput.builder()
 *     .put("file", "src/main/java/NewClass.java")
 *     .put("content", "public class NewClass { }")
 *     .put("mode", "create")
 *     .build();
 *
 * // Replace lines
 * ToolInput input2 = ToolInput.builder()
 *     .put("file", "src/main/java/MyClass.java")
 *     .put("content", "// New implementation")
 *     .put("start_line", 10)
 *     .put("end_line", 20)
 *     .put("mode", "replace")
 *     .build();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Write code files
 * @doc.layer product
 * @doc.pattern Command
 */
public class CodeWriteTool implements AgentTool {

    private static final String TOOL_NAME = "code.write";
    private static final int MAX_CONTENT_SIZE = 500 * 1024; // 500KB

    private final Path rootDirectory;
    private final Set<String> protectedPaths;

    public CodeWriteTool(String rootPath) {
        this(rootPath, Set.of(".git", ".env", "secrets", "credentials"));
    }

    public CodeWriteTool(String rootPath, Set<String> protectedPaths) {
        this.rootDirectory = Path.of(Objects.requireNonNull(rootPath, "rootPath required"));
        this.protectedPaths = protectedPaths != null ? protectedPaths : Set.of();
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "Write content to a code file. "
                + "Supports creating new files, overwriting existing files, "
                + "or replacing specific line ranges. "
                + "File paths are relative to the repository root.";
    }

    @Override
    public Map<String, Object> getSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "file", Map.of(
                                "type", "string",
                                "description", "Relative path to the file to write"
                        ),
                        "content", Map.of(
                                "type", "string",
                                "description", "Content to write"
                        ),
                        "mode", Map.of(
                                "type", "string",
                                "enum", List.of("create", "overwrite", "replace", "insert"),
                                "description", "Write mode: create (new file), overwrite (replace entire file), "
                                + "replace (replace line range), insert (insert at line)",
                                "default", "overwrite"
                        ),
                        "start_line", Map.of(
                                "type", "integer",
                                "description", "Start line for replace/insert mode (1-based)"
                        ),
                        "end_line", Map.of(
                                "type", "integer",
                                "description", "End line for replace mode (1-based, inclusive)"
                        )
                ),
                "required", List.of("file", "content")
        );
    }

    @Override
    public Set<String> getRequiredPermissions() {
        return Set.of("code.write");
    }

    @Override
    public Promise<ToolResult> execute(ToolInput input, ToolContext context) {
        return Promise.ofBlocking(blockingExecutor(), () -> {
            String filePath = input.getString("file");
            String content = input.getString("content");
            String mode = input.has("mode") ? input.getString("mode") : "overwrite";
            Integer startLine = input.has("start_line")
                    ? ((Number) input.get("start_line")).intValue() : null;
            Integer endLine = input.has("end_line")
                    ? ((Number) input.get("end_line")).intValue() : null;

            // Size check
            if (content.length() > MAX_CONTENT_SIZE) {
                return ToolResult.failure("Content too large (max 500KB)");
            }

            // Resolve and validate path
            Path resolvedPath = rootDirectory.resolve(filePath).normalize();

            // Security check: ensure path is within root
            if (!resolvedPath.startsWith(rootDirectory)) {
                return ToolResult.failure("Access denied: path outside repository root");
            }

            // Check protected paths
            for (String protectedPath : protectedPaths) {
                if (filePath.contains(protectedPath)) {
                    return ToolResult.failure("Access denied: protected path");
                }
            }

            try {
                switch (mode) {
                    case "create" -> {
                        if (Files.exists(resolvedPath)) {
                            return ToolResult.failure("File already exists: " + filePath);
                        }
                        return writeFile(resolvedPath, filePath, content, false);
                    }
                    case "overwrite" -> {
                        return writeFile(resolvedPath, filePath, content, true);
                    }
                    case "replace" -> {
                        if (startLine == null || endLine == null) {
                            return ToolResult.failure("start_line and end_line required for replace mode");
                        }
                        return replaceLines(resolvedPath, filePath, content, startLine, endLine);
                    }
                    case "insert" -> {
                        if (startLine == null) {
                            return ToolResult.failure("start_line required for insert mode");
                        }
                        return insertAtLine(resolvedPath, filePath, content, startLine);
                    }
                    default -> {
                        return ToolResult.failure("Unknown mode: " + mode);
                    }
                }
            } catch (IOException e) {
                return ToolResult.failure("Failed to write file: " + e.getMessage());
            }
        });
    }

    private ToolResult writeFile(Path path, String filePath, String content, boolean createDirs)
            throws IOException {
        if (createDirs) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, content, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);

        Map<String, Object> data = Map.of(
                "file", filePath,
                "bytes_written", content.length(),
                "lines_written", content.lines().count()
        );
        return ToolResult.success(data);
    }

    private ToolResult replaceLines(Path path, String filePath, String content,
            int startLine, int endLine) throws IOException {
        if (!Files.exists(path)) {
            return ToolResult.failure("File not found: " + filePath);
        }

        List<String> lines = new ArrayList<>(Files.readAllLines(path));
        int totalLines = lines.size();

        // Validate line range
        if (startLine < 1 || startLine > totalLines) {
            return ToolResult.failure("Invalid start_line: " + startLine);
        }
        if (endLine < startLine || endLine > totalLines) {
            return ToolResult.failure("Invalid end_line: " + endLine);
        }

        // Remove old lines and insert new content
        List<String> newContentLines = content.lines().toList();
        for (int i = endLine - 1; i >= startLine - 1; i--) {
            lines.remove(i);
        }
        lines.addAll(startLine - 1, newContentLines);

        String result = String.join("\n", lines);
        Files.writeString(path, result);

        Map<String, Object> data = Map.of(
                "file", filePath,
                "replaced_lines", endLine - startLine + 1,
                "new_lines", newContentLines.size(),
                "total_lines", lines.size()
        );
        return ToolResult.success(data);
    }

    private ToolResult insertAtLine(Path path, String filePath, String content, int line)
            throws IOException {
        if (!Files.exists(path)) {
            return ToolResult.failure("File not found: " + filePath);
        }

        List<String> lines = new ArrayList<>(Files.readAllLines(path));

        // Validate line
        if (line < 1 || line > lines.size() + 1) {
            return ToolResult.failure("Invalid line: " + line);
        }

        List<String> newContentLines = content.lines().toList();
        lines.addAll(line - 1, newContentLines);

        String result = String.join("\n", lines);
        Files.writeString(path, result);

        Map<String, Object> data = Map.of(
                "file", filePath,
                "inserted_at_line", line,
                "lines_inserted", newContentLines.size(),
                "total_lines", lines.size()
        );
        return ToolResult.success(data);
    }

    @Override
    public List<String> validate(ToolInput input) {
        List<String> errors = new ArrayList<>();
        if (!input.has("file")) {
            errors.add("'file' is required");
        }
        if (!input.has("content")) {
            errors.add("'content' is required");
        }
        return errors;
    }
}
