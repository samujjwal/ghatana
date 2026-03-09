package com.ghatana.refactorer.refactoring.model;

/**
 * Represents a TypeScript/JavaScript element that can be refactored. 
 * @doc.type class
 * @doc.purpose Handles type script element operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public class TypeScriptElement {
    private final String name;
    private final TypeScriptElementType type;
    private final String filePath;
    private final int startLine;
    private final int startColumn;
    private final int endLine;
    private final int endColumn;
    private final Object astNode; // Will hold the AST node from the TypeScript/JavaScript parser

    /**
 * Creates a new TypeScriptElement. */
    public TypeScriptElement(
            String name,
            TypeScriptElementType type,
            String filePath,
            int startLine,
            int startColumn,
            int endLine,
            int endColumn,
            Object astNode) {
        this.name = name;
        this.type = type;
        this.filePath = filePath;
        this.startLine = startLine;
        this.startColumn = startColumn;
        this.endLine = endLine;
        this.endColumn = endColumn;
        this.astNode = astNode;
    }

    public String getName() {
        return name;
    }

    public TypeScriptElementType getType() {
        return type;
    }

    public String getFilePath() {
        return filePath;
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

    public Object getAstNode() {
        return astNode;
    }

    @Override
    public String toString() {
        return String.format(
                "%s [%s] at %s:%d:%d-%d:%d",
                name,
                type.name().toLowerCase(),
                filePath,
                startLine,
                startColumn,
                endLine,
                endColumn);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TypeScriptElement that = (TypeScriptElement) o;

        if (startLine != that.startLine) return false;
        if (startColumn != that.startColumn) return false;
        if (endLine != that.endLine) return false;
        if (endColumn != that.endColumn) return false;
        if (!name.equals(that.name)) return false;
        if (type != that.type) return false;
        return filePath.equals(that.filePath);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + type.hashCode();
        result = 31 * result + filePath.hashCode();
        result = 31 * result + startLine;
        result = 31 * result + startColumn;
        result = 31 * result + endLine;
        result = 31 * result + endColumn;
        return result;
    }
}
