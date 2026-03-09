package com.ghatana.refactorer.codemods.bash;

import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import java.util.Map;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Provides automated fixes for common Bash issues detected by shellcheck. 
 * @doc.type class
 * @doc.purpose Handles bash codemods operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public class BashCodemods {
    private static final Logger logger = LoggerFactory.getLogger(BashCodemods.class);

    private final PolyfixProjectContext context;
    private final Map<String, BiFunction<String, UnifiedDiagnostic, String>> fixers;

    public BashCodemods(PolyfixProjectContext context) {
        this.context = context;
        this.fixers =
                Map.of(
                        // SC2086: Double quote to prevent globbing and word splitting
                        "SC2086", this::fixDoubleQuotes,
                        // SC2006: Use $(..) instead of legacy backticks
                        "SC2006", this::fixBackticks,
                        // SC2162: Add -r flag to read
                        "SC2162", this::fixReadWithoutRFlag,
                        // SC1091: Add source guard
                        "SC1091", this::fixSourceCommand);
    }

    /** Gets a fix for the specified shellcheck rule ID, if available. */
    public String getFix(String ruleId, String content, UnifiedDiagnostic diagnostic) {
        BiFunction<String, UnifiedDiagnostic, String> fixer = fixers.get(ruleId);
        if (fixer != null) {
            try {
                return fixer.apply(content, diagnostic);
            } catch (Exception e) {
                logger.error("Error applying fix for rule " + ruleId, e);
            }
        }
        return content;
    }

    /** Fixes SC2086: Double quote to prevent globbing and word splitting. */
    private String fixDoubleQuotes(String content, UnifiedDiagnostic diagnostic) {
        // This is a simplified example - in a real implementation, we'd parse the AST
        // to properly identify and quote the variable
        int lineNum = diagnostic.getStartLine() - 1; // Convert to 0-based index
        String[] lines = content.split("\n");

        if (lineNum < 0 || lineNum >= lines.length) {
            return content;
        }

        String line = lines[lineNum];
        int col = diagnostic.getStartColumn() - 1; // Convert to 0-based index

        // Find the variable that needs to be quoted
        int varStart = col;
        while (varStart > 0 && isBashVarChar(line.charAt(varStart - 1))) {
            varStart--;
        }

        int varEnd = col;
        while (varEnd < line.length() && isBashVarChar(line.charAt(varEnd))) {
            varEnd++;
        }

        if (varStart >= varEnd) {
            return content;
        }

        // Check if already quoted
        if ((varStart > 0
                        && line.charAt(varStart - 1) == '"'
                        && varEnd < line.length()
                        && line.charAt(varEnd) == '"')
                || (varStart > 0
                        && line.charAt(varStart - 1) == '\''
                        && varEnd < line.length()
                        && line.charAt(varEnd) == '\'')) {
            return content;
        }

        // Apply the fix - wrap the variable in double quotes
        // Make sure we include the $ sign in the quoted section
        if (varStart > 0 && line.charAt(varStart - 1) == '$') {
            // If there's a $ before the variable, include it in the quoted section
            varStart--;
        }

        // Ensure we're not going out of bounds
        if (varStart < 0) varStart = 0;

        String fixedLine =
                line.substring(0, varStart)
                        + "\""
                        + line.substring(varStart, varEnd)
                        + "\""
                        + (varEnd < line.length() ? line.substring(varEnd) : "");

        lines[lineNum] = fixedLine;
        return String.join("\n", lines);
    }

    /** Fixes SC2006: Use $(..) instead of legacy backticks. */
    private String fixBackticks(String content, UnifiedDiagnostic diagnostic) {
        // This is a simplified implementation
        // In a real implementation, we'd need to handle nested backticks properly
        return content.replaceAll("`([^`]+)`", "$(\\$1)");
    }

    /** Fixes SC2162: Add -r flag to read. */
    private String fixReadWithoutRFlag(String content, UnifiedDiagnostic diagnostic) {
        int lineNum = diagnostic.getStartLine() - 1;
        String[] lines = content.split("\n");

        if (lineNum < 0 || lineNum >= lines.length) {
            return content;
        }

        String line = lines[lineNum];

        // Simple pattern matching - in a real implementation, use proper Bash parsing
        if (line.trim().startsWith("read ") && !line.contains(" -r ")) {
            lines[lineNum] = line.replaceFirst("read ", "read -r ");
            return String.join("\n", lines);
        }

        return content;
    }

    /** Fixes SC1091: Add source guard. */
    private String fixSourceCommand(String content, UnifiedDiagnostic diagnostic) {
        int lineNum = diagnostic.getStartLine() - 1;
        String[] lines = content.split("\n");

        if (lineNum < 0 || lineNum >= lines.length) {
            return content;
        }

        String line = lines[lineNum].trim();

        // Simple pattern matching for source or . commands
        if (line.startsWith("source ") || line.startsWith(". ")) {
            String[] parts = line.split("\\s+", 2);
            if (parts.length == 2) {
                String file = parts[1].trim();
                if (!file.isEmpty()) {
                    // Add a file existence check before sourcing
                    String guard = String.format("if [ -f %s ]; then\n  %s\nfi", file, line);
                    lines[lineNum] = guard;
                    return String.join("\n", lines);
                }
            }
        }

        return content;
    }

    private boolean isBashVarChar(char c) {
        return Character.isLetterOrDigit(c)
                || c == '_'
                || c == '['
                || c == ']'
                || c == '*'
                || c == '?'
                || c == '#';
    }
}
