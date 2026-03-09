package com.ghatana.refactorer.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Go stack trace parser that handles Go panic messages and goroutine traces with optional
 * configuration-driven patterns.
 
 * @doc.type class
 * @doc.purpose Handles go stack trace parser operations
 * @doc.layer core
 * @doc.pattern Implementation
*/
public final class GoStackTraceParser implements StackTraceParser {
    /**
     * Default pattern for matching Go stack trace frames. Matches: main.main()
     * /tmp/sandbox1234/main.go:8 +0x1b
     */
    public static final Pattern DEFAULT_FRAME_PATTERN =
            Pattern.compile("^\\s*([^\n(]+)\\([^)]*\\)\\n\\s+(\\S+):(\\d+)", Pattern.MULTILINE);

    // Matches the panic message line
    private static final Pattern PANIC_PATTERN =
            Pattern.compile("^panic: (.+?)(?:\\n|$)", Pattern.MULTILINE);

    private final Pattern framePattern;
    private final Pattern panicPattern;

    /**
 * Creates a new parser with default patterns. */
    public GoStackTraceParser() {
        this(null, null);
    }

    /**
     * Creates a new parser with the specified patterns.
     *
     * @param framePattern the pattern for stack frames, or null for default
     * @param panicPattern the pattern for panic messages, or null for default
     */
    public GoStackTraceParser(Pattern framePattern, Pattern panicPattern) {
        this.framePattern = framePattern != null ? framePattern : DEFAULT_FRAME_PATTERN;
        this.panicPattern = panicPattern != null ? panicPattern : PANIC_PATTERN;
    }

    @Override
    public List<TraceFrame> parse(String content) {
        List<TraceFrame> out = new ArrayList<>();
        if (content == null || content.isEmpty()) return out;

        // Capture panic info but append it after real frames so tests expecting the first frame
        // to be a real file:line (e.g., BaseParserTest) will pass.
        String panicMsg = null;
        String panicSrc = null;
        Matcher panicMatcher = panicPattern.matcher(content);
        if (panicMatcher.find()) {
            panicMsg = panicMatcher.group(1);
            int panicEnd = panicMatcher.end();
            if (panicEnd < content.length()) {
                int nextLineStart = content.indexOf('\n', panicEnd);
                if (nextLineStart > 0 && nextLineStart < content.length() - 1) {
                    String nextLine = content.substring(nextLineStart + 1).split("\\n")[0].trim();
                    if (!nextLine.isEmpty()) {
                        panicSrc = nextLine;
                    }
                }
            }
        }

        // Parse stack frames (function + file:line pairs)
        Matcher m = framePattern.matcher(content);
        while (m.find()) {
            try {
                String func = m.group(1).trim();
                String file = m.group(2);
                int line = Integer.parseInt(m.group(3));

                // Skip empty function names or files
                if (!func.isEmpty() && !file.isEmpty()) {
                    out.add(new TraceFrame(file, line, func, m.group(0).trim()));
                }
            } catch (Exception e) {
                // Skip malformed frames
            }
        }

        // Append panic summary/source last for context
        if (panicMsg != null) {
            out.add(new TraceFrame("<panic>", 0, panicMsg, "panic: " + panicMsg));
            if (panicSrc != null) {
                out.add(new TraceFrame("<panic-source>", 0, panicSrc, panicSrc));
            }
        }

        return out;
    }

    @Override
    public Pattern getPattern() {
        return framePattern;
    }
}
