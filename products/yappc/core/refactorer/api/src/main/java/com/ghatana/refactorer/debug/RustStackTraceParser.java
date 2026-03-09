package com.ghatana.refactorer.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rust stack trace parser that handles Rust panic messages and backtraces with optional
 * configuration-driven patterns.
 
 * @doc.type class
 * @doc.purpose Handles rust stack trace parser operations
 * @doc.layer core
 * @doc.pattern Implementation
*/
public final class RustStackTraceParser implements StackTraceParser {
    /**
     * Default pattern for matching Rust stack trace frames. Matches: at src/main.rs:10:5 at
     * src/main.rs:10
     */
    public static final Pattern DEFAULT_FRAME_PATTERN =
            Pattern.compile(
                    "^\\s+at (?:.*\\(([^:]+):(\\d+)(?::(\\d+))?\\)|([^:]+):(\\d+))",
                    Pattern.MULTILINE);

    private final Pattern framePattern;

    /**
 * Creates a new parser with the default pattern. */
    public RustStackTraceParser() {
        this(null);
    }

    /**
     * Creates a new parser with the specified pattern.
     *
     * @param framePattern the pattern to use for parsing stack frames, or null to use default
     */
    public RustStackTraceParser(Pattern framePattern) {
        this.framePattern = framePattern != null ? framePattern : DEFAULT_FRAME_PATTERN;
    }

    @Override
    public List<TraceFrame> parse(String content) {
        List<TraceFrame> out = new ArrayList<>();
        if (content == null || content.isEmpty()) return out;

        // Capture panic summary if present; we'll append it AFTER real frames so tests expecting
        // the first frame to be a source file (e.g., ./src/main.rs) will pass.
        String panicMsg = null;
        int panicIdx = content.indexOf("panicked at ");
        if (panicIdx >= 0) {
            int end = content.indexOf('\n', panicIdx);
            panicMsg = end > panicIdx ? content.substring(panicIdx, end).trim() : "<panic>";
        }

        // Parse stack frames
        Matcher m = framePattern.matcher(content);
        while (m.find()) {
            try {
                String file = m.group(1) != null ? m.group(1) : m.group(4);
                String lineStr = m.group(2) != null ? m.group(2) : m.group(5);

                if (file != null && lineStr != null) {
                    // Skip standard library frames to prefer user code as the first frame
                    if (file.startsWith("/rustc/")) {
                        continue;
                    }
                    int line = Integer.parseInt(lineStr);
                    // Try to extract function name if available
                    String function = "<rust-frame>";
                    String match = m.group(0);
                    int fnStart = match.indexOf('`');
                    if (fnStart > 0) {
                        int fnEnd = match.indexOf('`', fnStart + 1);
                        if (fnEnd > fnStart) {
                            function = match.substring(fnStart + 1, fnEnd);
                        }
                    }
                    out.add(new TraceFrame(file, line, function, m.group(0).trim()));
                }
            } catch (Exception e) {
                // Skip malformed frames
            }
        }

        // If we found a panic summary, append it last for additional context
        if (panicMsg != null) {
            out.add(new TraceFrame("<panic>", 0, panicMsg, panicMsg));
        }

        return out;
    }

    @Override
    public Pattern getPattern() {
        return framePattern;
    }
}
