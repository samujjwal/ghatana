package com.ghatana.refactorer.rewriters;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import com.ghatana.refactorer.shared.process.ProcessResult;
import com.ghatana.refactorer.shared.process.ProcessRunner;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A utility class for running various Go tools and processing their output.
 *
 * @doc.type class
 * @doc.purpose Run Go analysis tools (vet, staticcheck, goimports) and parse diagnostics
 * @doc.layer product
 * @doc.pattern Utility
 */
public class GoToolsRunner {

    private static final Pattern GO_VET_PATTERN = Pattern.compile("^(.+?):(\\d+):(\\d+):\\s*(.*)$");
    private static final ObjectMapper MAPPER = JsonUtils.getDefaultMapper();

    private final ProcessRunner processRunner;

    public GoToolsRunner(PolyfixProjectContext context) {
        this.processRunner = new ProcessRunner(Objects.requireNonNull(context, "context"));
    }

    /**
     * Runs go vet on the specified directory and returns any diagnostics found.
     *
     * @param cwd The working directory containing Go code
     * @param timeoutMillis Maximum time to wait for the command to complete
     * (not currently enforced - uses ProcessRunner default)
     * @return List of diagnostics found by go vet
     */
    public List<UnifiedDiagnostic> vet(Path cwd, long timeoutMillis) {
        Objects.requireNonNull(cwd, "cwd");
        ProcessResult result
                = processRunner.execute(
                        "go",
                        List.of("vet", "./..."),
                        cwd,
                        true);

        List<UnifiedDiagnostic> diagnostics = new ArrayList<>();

        // Parse the error output line by line (go vet writes to stderr)
        String errorOutput = result.error() != null ? result.error() : "";
        for (String line : errorOutput.split("\\r?\\n")) {
            if (line.trim().isEmpty()) {
                continue;
            }

            Matcher matcher = GO_VET_PATTERN.matcher(line);
            if (matcher.find()) {
                String filePath = matcher.group(1);
                int lineNum = Integer.parseInt(matcher.group(2));
                int colNum = Integer.parseInt(matcher.group(3));
                String message = matcher.group(4);

                diagnostics.add(
                        UnifiedDiagnostic.error(
                                "go-vet", message, filePath, lineNum, colNum, null));
            } else {
                // If the line doesn't match the expected format, add it as a general error
                diagnostics.add(UnifiedDiagnostic.error("go-vet", line, null, -1, -1, null));
            }
        }

        return diagnostics;
    }

    /**
     * Runs staticcheck on the specified directory and returns any diagnostics
     * found.
     *
     * @param cwd The working directory containing Go code
     * @param timeoutMillis Maximum time to wait for the command to complete
     * (not currently enforced - uses ProcessRunner default)
     * @return List of diagnostics found by staticcheck
     */
    public List<UnifiedDiagnostic> staticcheck(Path cwd, long timeoutMillis) {
        Objects.requireNonNull(cwd, "cwd");
        List<UnifiedDiagnostic> diags = new ArrayList<>();

        try {
            ProcessResult result
                    = processRunner.execute(
                            "staticcheck",
                            List.of("-f", "json", "./..."),
                            cwd,
                            true);

            // staticcheck may emit JSON lines; parse line-by-line
            String output = result.output();
            if (output == null || output.trim().isEmpty()) {
                return diags;
            }

            JsonFactory jf = MAPPER.getFactory();
            try (JsonParser jp = jf.createParser(output)) {
                while (jp.nextToken() != null) {
                    JsonNode n = MAPPER.readTree(jp);
                    if (n == null || !n.isObject()) {
                        continue;
                    }

                    String code = text(n, "code");
                    String message = text(n, "message");
                    String location = text(n.path("location"), "file");
                    int line = intVal(n.path("location"), "line", -1);
                    int col = intVal(n.path("location"), "column", -1);

                    UnifiedDiagnostic d
                            = UnifiedDiagnostic.builder()
                                    .tool("staticcheck")
                                    .code(code == null ? "" : code)
                                    .message(message == null ? "" : message)
                                    .file(location == null ? null : Path.of(location))
                                    .startLine(line)
                                    .startColumn(col)
                                    .endLine(line)
                                    .endColumn(col)
                                    .build();
                    diags.add(d);
                }
            }
        } catch (Exception e) {
            diags.add(
                    UnifiedDiagnostic.error(
                            "staticcheck",
                            "Failed to run staticcheck: " + e.getMessage(),
                            null,
                            -1,
                            -1,
                            e));
        }

        return diags;
    }

    /**
     * Runs goimports on the specified files and returns the number of files
     * that were modified.
     *
     * @param cwd The working directory
     * @param files List of files to format
     * @param timeoutMillis Maximum time to wait for the command to complete
     * (not currently enforced - uses ProcessRunner default)
     * @return Number of files that were modified, or -1 if there was an error
     */
    public int goimports(Path cwd, List<Path> files, long timeoutMillis) {
        Objects.requireNonNull(cwd, "cwd");
        if (files == null || files.isEmpty()) {
            return 0;
        }

        try {
            List<String> args = new ArrayList<>();
            args.add("-w");
            for (Path f : files) {
                args.add(cwd.relativize(f).toString());
            }

            ProcessResult result
                    = processRunner.execute("goimports", args, cwd, true);

            return result.exitCode() == 0 ? files.size() : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && v.isValueNode() ? v.asText() : null;
    }

    private static int intVal(JsonNode node, String field, int def) {
        JsonNode v = node.get(field);
        return v != null && v.isInt() ? v.asInt() : def;
    }
}
