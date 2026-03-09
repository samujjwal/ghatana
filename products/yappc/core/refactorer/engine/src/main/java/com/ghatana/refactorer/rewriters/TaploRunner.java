package com.ghatana.refactorer.rewriters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import com.ghatana.refactorer.shared.util.ProcessExec;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Taplo runner to check and format TOML files. 
 * @doc.type class
 * @doc.purpose Handles taplo runner operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class TaploRunner {
    private static final ObjectMapper MAPPER = JsonUtils.getDefaultMapper();

    public List<UnifiedDiagnostic> check(Path cwd, List<Path> files, long timeoutMillis) {
        Objects.requireNonNull(cwd, "cwd");
        List<UnifiedDiagnostic> diags = new ArrayList<>();
        if (files == null || files.isEmpty()) return diags;
        // taplo check --format json <files>
        List<String> cmd = new ArrayList<>();
        cmd.add("taplo");
        cmd.add("check");
        cmd.add("--color");
        cmd.add("never");
        cmd.add("--format");
        cmd.add("json");
        for (Path f : files) cmd.add(cwd.relativize(f).toString());
        ProcessExec.Result r =
                ProcessExec.run(cwd, Duration.ofMillis(timeoutMillis), cmd, Map.of());
        // Parse JSON output to structured diagnostics
        try {
            if (r.out() != null && !r.out().isBlank()) {
                JsonNode root = MAPPER.readTree(r.out());
                // Taplo may return either an array of diagnostics or an object with diagnostics
                // field
                if (root.isArray()) {
                    for (JsonNode n : root) parseDiagNode(diags, n);
                } else if (root.isObject()) {
                    JsonNode arr = root.get("diagnostics");
                    if (arr != null && arr.isArray()) {
                        for (JsonNode n : arr) parseDiagNode(diags, n);
                    }
                }
            }
            if (r.exitCode() != 0 && diags.isEmpty()) {
                diags.add(
                        UnifiedDiagnostic.error(
                                "taplo",
                                r.err().isBlank() ? "taplo failed" : r.err(),
                                null,
                                -1,
                                -1,
                                null));
            }
        } catch (Exception e) {
            diags.add(
                    UnifiedDiagnostic.error(
                            "taplo", "Failed to parse JSON: " + e.getMessage(), null, -1, -1, e));
        }
        return diags;
    }

    public int format(Path cwd, List<Path> files, long timeoutMillis) {
        Objects.requireNonNull(cwd, "cwd");
        if (files == null || files.isEmpty()) return 0;
        List<String> cmd = new ArrayList<>();
        cmd.add("taplo");
        cmd.add("format");
        for (Path f : files) cmd.add(cwd.relativize(f).toString());
        ProcessExec.Result r =
                ProcessExec.run(cwd, Duration.ofMillis(timeoutMillis), cmd, Map.of());
        return r.exitCode() == 0 ? files.size() : 0;
    }

    private static void parseDiagNode(List<UnifiedDiagnostic> out, JsonNode n) {
        // Expected fields: message, severity, range {start{line,column}, end{line,column}},
        // path/file
        String message = text(n, "message");
        String file = text(n, "path");
        if (file == null) file = text(n, "file");
        JsonNode range = n.get("range");
        int sl = -1, sc = -1, el = -1, ec = -1;
        if (range != null && range.isObject()) {
            JsonNode start = range.get("start");
            JsonNode end = range.get("end");
            if (start != null) {
                sl = intVal(start, "line", -1);
                sc = intVal(start, "column", -1);
            }
            if (end != null) {
                el = intVal(end, "line", -1);
                ec = intVal(end, "column", -1);
            }
        }
        UnifiedDiagnostic d =
                UnifiedDiagnostic.builder()
                        .tool("taplo")
                        .code("")
                        .message(message == null ? "" : message)
                        .file(file == null ? null : Path.of(file))
                        .startLine(sl)
                        .startColumn(sc)
                        .endLine(el)
                        .endColumn(ec)
                        .build();
        out.add(d);
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
