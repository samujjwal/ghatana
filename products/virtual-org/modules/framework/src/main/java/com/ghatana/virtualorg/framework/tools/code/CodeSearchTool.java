package com.ghatana.virtualorg.framework.tools.code;

import com.ghatana.virtualorg.framework.tools.AgentTool;
import com.ghatana.virtualorg.framework.tools.ToolContext;
import com.ghatana.virtualorg.framework.tools.ToolInput;
import com.ghatana.virtualorg.framework.tools.ToolResult;
import com.ghatana.virtualorg.framework.tools.ToolSchema;
import io.activej.promise.Promise;

import static com.ghatana.virtualorg.framework.util.BlockingExecutors.blockingExecutor;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tool for searching code patterns in a repository.
 *
 * <p>
 * <b>Purpose</b><br>
 * Allows agents to search codebases for: - Text patterns with regex support -
 * Class/method definitions - Symbol references - File patterns (glob)
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * AgentTool tool = new CodeSearchTool("/app/workspace");
 *
 * ToolInput input = ToolInput.builder()
 *     .put("pattern", "class.*Service")
 *     .put("file_pattern", "*.java")
 *     .put("is_regex", true)
 *     .put("max_results", 20)
 *     .build();
 *
 * ToolResult result = tool.execute(input, context).getResult();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Search code patterns in repository
 * @doc.layer product
 * @doc.pattern Query
 */
public class CodeSearchTool implements AgentTool {

    private static final String TOOL_NAME = "code.search";
    private static final int DEFAULT_MAX_RESULTS = 50;
    private static final int MAX_FILE_SIZE_BYTES = 1024 * 1024; // 1MB
    private static final int CONTEXT_LINES = 2;

    private final Path rootDirectory;
    private final Set<String> excludedDirs;

    public CodeSearchTool(String rootPath) {
        this(rootPath, Set.of(".git", "node_modules", "target", "build", ".gradle", ".idea", "__pycache__"));
    }

    public CodeSearchTool(String rootPath, Set<String> excludedDirs) {
        this.rootDirectory = Path.of(Objects.requireNonNull(rootPath, "rootPath required"));
        this.excludedDirs = excludedDirs != null ? excludedDirs : Set.of();
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "Search for code patterns in the repository. "
                + "Supports text search, regex patterns, and file filtering. "
                + "Returns matching lines with surrounding context.";
    }

    @Override
    public ToolSchema getInputSchema() {
        return ToolSchema.builder()
                .description("Input parameters for code search")
                .property("pattern", ToolSchema.SchemaType.STRING, "The pattern to search for (text or regex)", true)
                .property("file_pattern", ToolSchema.SchemaType.STRING, "File name pattern (e.g., '*.java', '*.ts')", false)
                .property("is_regex", ToolSchema.SchemaType.BOOLEAN, "Whether the pattern is a regex", false, false)
                .property("case_sensitive", ToolSchema.SchemaType.BOOLEAN, "Whether search is case-sensitive", false, false)
                .property("max_results", ToolSchema.SchemaType.INTEGER, "Maximum number of results", false, DEFAULT_MAX_RESULTS)
                .property("include_context", ToolSchema.SchemaType.BOOLEAN, "Include surrounding lines for context", false, true)
                .build();
    }

    @Override
    public ToolSchema getOutputSchema() {
        return ToolSchema.builder()
                .description("Search results with matching code snippets")
                .property("pattern", ToolSchema.SchemaType.STRING, "The search pattern used", true)
                .property("total_matches", ToolSchema.SchemaType.INTEGER, "Total number of matches found", true)
                .property("matches", ToolSchema.SchemaType.ARRAY, "List of match results", true)
                .property("truncated", ToolSchema.SchemaType.BOOLEAN, "Whether results were truncated", false)
                .build();
    }

    @Override
    public Map<String, Object> getSchema() {
        return getInputSchema().toMap();
    }

    @Override
    public Set<String> getRequiredPermissions() {
        return Set.of("code.read");
    }

    @Override
    public Promise<ToolResult> execute(ToolInput input, ToolContext context) {
        return Promise.ofBlocking(blockingExecutor(), () -> {
            String pattern = input.getString("pattern");
            String filePattern = input.has("file_pattern") ? input.getString("file_pattern") : null;
            boolean isRegex = input.has("is_regex") && Boolean.TRUE.equals(input.get("is_regex"));
            boolean caseSensitive = input.has("case_sensitive") && Boolean.TRUE.equals(input.get("case_sensitive"));
            int maxResults = input.has("max_results") ? ((Number) input.get("max_results")).intValue() : DEFAULT_MAX_RESULTS;
            boolean includeContext = !input.has("include_context") || Boolean.TRUE.equals(input.get("include_context"));

            List<SearchResult> results = new ArrayList<>();
            Pattern searchPattern = compilePattern(pattern, isRegex, caseSensitive);

            try {
                Files.walkFileTree(rootDirectory, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        String dirName = dir.getFileName().toString();
                        if (excludedDirs.contains(dirName)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (results.size() >= maxResults) {
                            return FileVisitResult.TERMINATE;
                        }

                        if (attrs.size() > MAX_FILE_SIZE_BYTES) {
                            return FileVisitResult.CONTINUE;
                        }

                        if (filePattern != null && !matchesFilePattern(file, filePattern)) {
                            return FileVisitResult.CONTINUE;
                        }

                        searchInFile(file, searchPattern, includeContext, results, maxResults);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) {
                        // Skip files we can't read
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                return ToolResult.failure("Search failed: " + e.getMessage());
            }

            List<Map<String, Object>> resultData = results.stream()
                    .map(SearchResult::toMap)
                    .toList();

            Map<String, Object> data = new java.util.HashMap<>();
            data.put("pattern", pattern);
            data.put("total_matches", results.size());
            data.put("matches", resultData);

            if (results.size() >= maxResults) {
                data.put("truncated", true);
                data.put("message", "Results limited to " + maxResults + " matches");
            }

            return ToolResult.success(data);
        });
    }

    private Pattern compilePattern(String pattern, boolean isRegex, boolean caseSensitive) {
        int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
        if (isRegex) {
            return Pattern.compile(pattern, flags);
        } else {
            return Pattern.compile(Pattern.quote(pattern), flags);
        }
    }

    private boolean matchesFilePattern(Path file, String pattern) {
        String fileName = file.getFileName().toString();
        // Simple glob matching
        String regex = pattern
                .replace(".", "\\.")
                .replace("*", ".*")
                .replace("?", ".");
        return fileName.matches(regex);
    }

    private void searchInFile(Path file, Pattern pattern, boolean includeContext,
            List<SearchResult> results, int maxResults) {
        try {
            List<String> lines = Files.readAllLines(file);
            String relativePath = rootDirectory.relativize(file).toString();

            for (int i = 0; i < lines.size() && results.size() < maxResults; i++) {
                String line = lines.get(i);
                Matcher matcher = pattern.matcher(line);

                if (matcher.find()) {
                    SearchResult result = new SearchResult(
                            relativePath,
                            i + 1, // 1-based line number
                            line.trim(),
                            includeContext ? getContext(lines, i) : null
                    );
                    results.add(result);
                }
            }
        } catch (IOException e) {
            // Skip files we can't read
        }
    }

    private List<String> getContext(List<String> lines, int lineIndex) {
        List<String> context = new ArrayList<>();
        int start = Math.max(0, lineIndex - CONTEXT_LINES);
        int end = Math.min(lines.size(), lineIndex + CONTEXT_LINES + 1);

        for (int i = start; i < end; i++) {
            if (i != lineIndex) {
                context.add((i + 1) + ": " + lines.get(i).trim());
            }
        }
        return context;
    }

    @Override
    public List<String> validate(ToolInput input) {
        List<String> errors = new ArrayList<>();
        if (!input.has("pattern")) {
            errors.add("'pattern' is required");
        }
        return errors;
    }

    // ========== Result Record ==========
    private record SearchResult(
            String file,
            int line,
            String match,
            List<String> context
    ) {
        Map<String, Object> toMap
            
        
            () {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("file", file);
            map.put("line", line);
            map.put("match", match);
            if (context != null && !context.isEmpty()) {
                map.put("context", context);
            }
            return map;
        }
    }
}
