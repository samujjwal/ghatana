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
 * Tool for reading code files.
 *
 * <p>
 * <b>Purpose</b><br>
 * Allows agents to read file contents with: - Line range support - Multiple
 * file reading - Size limits for safety
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * AgentTool tool = new CodeReadTool("/app/workspace");
 *
 * ToolInput input = ToolInput.builder()
 *     .put("file", "src/main/java/MyClass.java")
 *     .put("start_line", 10)
 *     .put("end_line", 50)
 *     .build();
 *
 * ToolResult result = tool.execute(input, context).getResult();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Read code files
 * @doc.layer product
 * @doc.pattern Query
 */
public class CodeReadTool implements AgentTool {

    private static final String TOOL_NAME = "code.read";
    private static final int MAX_FILE_SIZE_BYTES = 1024 * 1024; // 1MB
    private static final int MAX_LINES = 500;

    private final Path rootDirectory;

    public CodeReadTool(String rootPath) {
        this.rootDirectory = Path.of(Objects.requireNonNull(rootPath, "rootPath required"));
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "Read the contents of a code file. "
                + "Supports reading specific line ranges. "
                + "File paths are relative to the repository root.";
    }

    @Override
    public Map<String, Object> getSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "file", Map.of(
                                "type", "string",
                                "description", "Relative path to the file to read"
                        ),
                        "start_line", Map.of(
                                "type", "integer",
                                "description", "Start line number (1-based, inclusive)"
                        ),
                        "end_line", Map.of(
                                "type", "integer",
                                "description", "End line number (1-based, inclusive)"
                        )
                ),
                "required", List.of("file")
        );
    }

    @Override
    public Set<String> getRequiredPermissions() {
        return Set.of("code.read");
    }

    @Override
    public Promise<ToolResult> execute(ToolInput input, ToolContext context) {
        return Promise.ofBlocking(blockingExecutor(), () -> {
            String filePath = input.getString("file");
            Integer startLine = input.has("start_line")
                    ? ((Number) input.get("start_line")).intValue() : null;
            Integer endLine = input.has("end_line")
                    ? ((Number) input.get("end_line")).intValue() : null;

            // Resolve and validate path
            Path resolvedPath = rootDirectory.resolve(filePath).normalize();

            // Security check: ensure path is within root
            if (!resolvedPath.startsWith(rootDirectory)) {
                return ToolResult.failure("Access denied: path outside repository root");
            }

            if (!Files.exists(resolvedPath)) {
                return ToolResult.failure("File not found: " + filePath);
            }

            if (!Files.isRegularFile(resolvedPath)) {
                return ToolResult.failure("Not a regular file: " + filePath);
            }

            if (Files.size(resolvedPath) > MAX_FILE_SIZE_BYTES) {
                return ToolResult.failure("File too large (max 1MB)");
            }

            try {
                List<String> lines = Files.readAllLines(resolvedPath);
                int totalLines = lines.size();

                // Determine line range
                int start = startLine != null ? Math.max(1, startLine) : 1;
                int end = endLine != null ? Math.min(totalLines, endLine) : totalLines;

                // Enforce max lines
                if (end - start + 1 > MAX_LINES) {
                    end = start + MAX_LINES - 1;
                }

                // Extract lines (convert to 0-based index)
                List<String> selectedLines = new ArrayList<>();
                for (int i = start - 1; i < end && i < lines.size(); i++) {
                    selectedLines.add(lines.get(i));
                }

                String content = String.join("\n", selectedLines);

                Map<String, Object> data = new java.util.HashMap<>();
                data.put("file", filePath);
                data.put("total_lines", totalLines);
                data.put("start_line", start);
                data.put("end_line", Math.min(end, totalLines));
                data.put("content", content);

                if (end < totalLines) {
                    data.put("truncated", true);
                }

                return ToolResult.success(data);

            } catch (IOException e) {
                return ToolResult.failure("Failed to read file: " + e.getMessage());
            }
        });
    }

    @Override
    public List<String> validate(ToolInput input) {
        List<String> errors = new ArrayList<>();
        if (!input.has("file")) {
            errors.add("'file' is required");
        }
        return errors;
    }
}
