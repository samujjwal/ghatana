/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.shared;

import com.ghatana.platform.domain.domain.Severity;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
/**
 * @doc.type class
 * @doc.purpose Handles unified diagnostic operations
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public class UnifiedDiagnostic {
    private final String tool;
    private final String ruleId;
    private final String message;
    private final String file;
    @Builder.Default private final String code = "";
    private final int line;
    private final int column;
    private final Severity severity;
    private final Map<String, String> metadata;

    // For backward compatibility
    public static class UnifiedDiagnosticBuilder {
        private String tool;
        private String ruleId;
        private String message;
        private String file;
        private String code = "";
        private int line;
        private int column;
        private Severity severity;
        private Map<String, String> metadata = new HashMap<>();

        public UnifiedDiagnosticBuilder tool(String tool) {
            this.tool = tool;
            return this;
        }

        public UnifiedDiagnosticBuilder ruleId(String ruleId) {
            this.ruleId = ruleId;
            return this;
        }

        public UnifiedDiagnosticBuilder message(String message) {
            this.message = message;
            return this;
        }

        public UnifiedDiagnosticBuilder file(String file) {
            this.file = file;
            return this;
        }

        public UnifiedDiagnosticBuilder file(Path file) {
            this.file = file != null ? file.toString() : "";
            return this;
        }

        public UnifiedDiagnosticBuilder code(String code) {
            this.code = code != null ? code : "";
            return this;
        }

        public UnifiedDiagnosticBuilder line(int line) {
            this.line = line;
            return this;
        }

        // Alias for line() for backward compatibility
        public UnifiedDiagnosticBuilder startLine(int line) {
            return line(line);
        }

        // Alias for column() for backward compatibility
        public UnifiedDiagnosticBuilder startColumn(int column) {
            return column(column);
        }

        // Alias for line() for end line position
        public UnifiedDiagnosticBuilder endLine(int line) {
            return line(line);
        }

        // Alias for column() for end column position
        public UnifiedDiagnosticBuilder endColumn(int column) {
            return column(column);
        }

        public UnifiedDiagnosticBuilder column(int column) {
            this.column = column;
            return this;
        }

        public UnifiedDiagnosticBuilder severity(Severity severity) {
            this.severity = severity != null ? severity : Severity.ERROR;
            return this;
        }

        public UnifiedDiagnosticBuilder metadata(String key, String value) {
            this.metadata.put(key, value);
            return this;
        }

        public UnifiedDiagnosticBuilder metadata(Map<String, String> metadata) {
            if (metadata != null) {
                this.metadata.putAll(metadata);
            }
            return this;
        }

        public UnifiedDiagnostic build() {
            if (code != null && !code.isEmpty() && !metadata.containsKey("code")) {
                metadata.put("code", code);
            }
            return new UnifiedDiagnostic(
                    tool != null ? tool : "",
                    ruleId != null ? ruleId : "",
                    message != null ? message : "",
                    file != null ? file : "",
                    code != null ? code : "",
                    line,
                    column,
                    severity != null ? severity : Severity.ERROR,
                    new HashMap<>(metadata));
        }
    }

    // For backward compatibility
    public static UnifiedDiagnosticBuilder builder() {
        return new UnifiedDiagnosticBuilder();
    }

    // Getters for backward compatibility
    public String tool() {
        return tool;
    }

    public String ruleId() {
        return ruleId;
    }

    public String rule() {
        return ruleId;
    }

    public String message() {
        return message;
    }

    /**
     * Gets the file path where the diagnostic was found.
     *
     * @return The file path as a string
     */
    public String getFile() {
        return file;
    }

    public String file() {
        return file;
    }

    public int line() {
        return line;
    }

    // Alias for line() for backward compatibility
    public int getStartLine() {
        return line;
    }

    public int column() {
        return column;
    }

    // Alias for column() for backward compatibility
    public int getStartColumn() {
        return column;
    }

    public Severity severity() {
        return severity;
    }

    public Map<String, String> metadata() {
        return metadata;
    }

    public UnifiedDiagnostic(
            String tool,
            String ruleId,
            String message,
            String file,
            int line,
            int column,
            Severity severity,
            Map<String, String> metadata) {
        this(tool, ruleId, message, file, "", line, column, severity, metadata);
    }

    public UnifiedDiagnostic(
            String tool,
            String ruleId,
            String message,
            String file,
            String code,
            int line,
            int column,
            Severity severity,
            Map<String, String> metadata) {
        this.tool = tool != null ? tool : "";
        this.ruleId = ruleId != null ? ruleId : "";
        this.message = message != null ? message : "";
        this.file = file != null ? file : "";
        this.code = code != null ? code : "";
        this.line = line;
        this.column = column;
        this.severity = severity != null ? severity : Severity.ERROR;
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();

        // Add code to metadata if provided
        if (!this.code.isEmpty()) {
            this.metadata.put("code", this.code);
        }
    }

    // Constructor that accepts Path
    public UnifiedDiagnostic(
            String tool,
            String ruleId,
            String message,
            Path file,
            int line,
            int column,
            Severity severity,
            Map<String, String> metadata) {
        this(
                tool,
                ruleId,
                message,
                file != null ? file.toString() : "",
                "",
                line,
                column,
                severity,
                metadata);
    }

    // Constructor that accepts Path and code
    public UnifiedDiagnostic(
            String tool,
            String ruleId,
            String message,
            Path file,
            String code,
            int line,
            int column,
            Severity severity,
            Map<String, String> metadata) {
        this(
                tool,
                ruleId,
                message,
                file != null ? file.toString() : "",
                code,
                line,
                column,
                severity,
                metadata);
    }

    public static UnifiedDiagnostic error(
            String tool, String message, Object file, int line, int column, Throwable cause) {
        String filePath = "";
        if (file instanceof Path) {
            filePath = ((Path) file).toString();
        } else if (file instanceof String) {
            filePath = (String) file;
        }

        return new UnifiedDiagnostic(
                tool,
                "",
                message,
                filePath,
                normalizePosition(line),
                normalizePosition(column),
                Severity.ERROR,
                metadataFrom(cause));
    }

    public static UnifiedDiagnostic warning(
            String tool, String message, Path file, int line, int column, Throwable cause) {
        return new UnifiedDiagnostic(
                tool,
                "",
                message,
                file,
                normalizePosition(line),
                normalizePosition(column),
                Severity.WARNING,
                metadataFrom(cause));
    }

    private static int normalizePosition(int value) {
        return value > 0 ? value : 1;
    }

    private static Map<String, String> metadataFrom(Throwable cause) {
        if (cause == null) {
            return Map.of();
        }
        Map<String, String> metadata = new HashMap<>();
        metadata.put("exception", cause.getClass().getName());
        String message = cause.getMessage();
        if (message != null && !message.isBlank()) {
            metadata.put("exceptionMessage", message);
        }
        return metadata;
    }
}
