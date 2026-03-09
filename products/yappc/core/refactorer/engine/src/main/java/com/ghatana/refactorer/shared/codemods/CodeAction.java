package com.ghatana.refactorer.shared.codemods;

import java.util.Objects;

/**
 * Represents a single code modification action. 
 * @doc.type class
 * @doc.purpose Handles code action operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public class CodeAction {
    private final String description;
    private final String kind;

    /**
     * Creates a new code action.
     *
     * @param description Human-readable description of the action
     * @param kind The kind of action (e.g., "add-import", "refactor")
     */
    public CodeAction(String description, String kind) {
        this.description = Objects.requireNonNull(description, "description cannot be null");
        this.kind = Objects.requireNonNull(kind, "kind cannot be null");
    }

    /**
 * Gets a human-readable description of this action. */
    public String getDescription() {
        return description;
    }

    /**
 * Gets the kind of this action. */
    public String getKind() {
        return kind;
    }

    @Override
    public String toString() {
        return String.format("%s (%s)", description, kind);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CodeAction that = (CodeAction) o;
        return description.equals(that.description) && kind.equals(that.kind);
    }

    @Override
    public int hashCode() {
        return Objects.hash(description, kind);
    }
}
