package com.ghatana.refactorer.shared.action;

import java.nio.file.Path;
import java.util.Map;

/**

 * @doc.type class

 * @doc.purpose Handles fix action operations

 * @doc.layer core

 * @doc.pattern ValueObject

 */

public class FixAction {
    private final String type;
    private final String description;
    private final Path file;
    private final int startLine;
    private final int startColumn;
    private final int endLine;
    private final int endColumn;
    private final String originalContent;
    private final String replacementContent;
    private final Map<String, String> metadata;

    public FixAction(
            String type,
            String description,
            Path file,
            int startLine,
            int startColumn,
            int endLine,
            int endColumn,
            String originalContent,
            String replacementContent,
            Map<String, String> metadata) {
        this.type = type;
        this.description = description;
        this.file = file;
        this.startLine = startLine;
        this.startColumn = startColumn;
        this.endLine = endLine;
        this.endColumn = endColumn;
        this.originalContent = originalContent;
        this.replacementContent = replacementContent;
        this.metadata = metadata;
    }

    // Getters
    public String getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public Path getFile() {
        return file;
    }

    public int getStartLine() {
        return startLine;
    }

    public int getStartColumn() {
        return startColumn;
    }

    public int getEndLine() {
        return endLine;
    }

    public int getEndColumn() {
        return endColumn;
    }

    public String getOriginalContent() {
        return originalContent;
    }

    public String getReplacementContent() {
        return replacementContent;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }
}
