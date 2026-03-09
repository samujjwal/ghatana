package com.ghatana.yappc.core.io;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Diff renderer for displaying textual differences between files.
 * Provides simple line-by-line diff rendering.
 *
 * @doc.type class
 * @doc.purpose Render unified diffs for file changes
 * @doc.layer product
 * @doc.pattern Renderer
 */
public class DiffRenderer {

    private static final Logger log = LoggerFactory.getLogger(DiffRenderer.class);

    /**
     * Render simple diff between two texts.
     * Uses basic line-by-line comparison.
     *
     * @param original original text
     * @param modified modified text
     * @return rendered diff
     */
    public String render(String original, String modified) {
        try {
            List<String> originalLines = Arrays.asList(original.split("\\n"));
            List<String> modifiedLines = Arrays.asList(modified.split("\\n"));
            
            StringBuilder result = new StringBuilder();
            result.append("--- original\n");
            result.append("+++ modified\n");
            
            int maxLines = Math.max(originalLines.size(), modifiedLines.size());
            
            for (int i = 0; i < maxLines; i++) {
                String origLine = i < originalLines.size() ? originalLines.get(i) : null;
                String modLine = i < modifiedLines.size() ? modifiedLines.get(i) : null;
                
                if (origLine == null) {
                    // Line added
                    result.append("+ ").append(modLine).append("\n");
                } else if (modLine == null) {
                    // Line removed
                    result.append("- ").append(origLine).append("\n");
                } else if (!origLine.equals(modLine)) {
                    // Line changed
                    result.append("- ").append(origLine).append("\n");
                    result.append("+ ").append(modLine).append("\n");
                } else {
                    // Line unchanged
                    result.append("  ").append(origLine).append("\n");
                }
            }
            
            return result.toString();
        } catch (Exception e) {
            log.error("Failed to render diff", e);
            return "Error rendering diff: " + e.getMessage();
        }
    }

    /**
     * Render patch lines with formatting.
     *
     * @param patches list of patch lines
     * @return rendered output with indicators
     */
    public String renderPatch(List<String> patches) {
        if (patches == null || patches.isEmpty()) {
            return "";
        }
        
        return patches.stream()
            .map(this::formatPatchLine)
            .collect(Collectors.joining("\n"));
    }
    
    /**
     * Format a single patch line with indicators.
     */
    private String formatPatchLine(String line) {
        if (line.startsWith("+")) {
            return "[+] " + line.substring(1);
        } else if (line.startsWith("-")) {
            return "[-] " + line.substring(1);
        } else if (line.startsWith("@")) {
            return "[@] " + line;
        } else {
            return "    " + line;
        }
    }
}
