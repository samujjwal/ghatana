package com.ghatana.refactorer.refactoring.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Compatibility RefactoringContext used by tests. Provides a simple builder and
 * property map.
 
 * @doc.type class
 * @doc.purpose Handles refactoring context operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class RefactoringContext {

    private final String sourceFile;
    private final String oldName;
    private final String newName;
    private final String elementType;
    private final int lineNumber;
    private final int offset;
    private final boolean dryRun;
    private final Map<String, Object> properties;

    private RefactoringContext(Builder b) {
        this.sourceFile = b.sourceFile;
        this.oldName = b.oldName;
        this.newName = b.newName;
        this.elementType = b.elementType;
        this.lineNumber = b.lineNumber;
        this.offset = b.offset;
        this.dryRun = b.dryRun;
        this.properties = b.properties != null ? Collections.unmodifiableMap(new HashMap<>(b.properties)) : Collections.emptyMap();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public String getOldName() {
        return oldName;
    }

    public String getNewName() {
        return newName;
    }

    public String getElementType() {
        return elementType;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public int getOffset() {
        return offset;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public boolean hasProperty(String key) {
        return properties.containsKey(key);
    }

    public Object getProperty(String key) {
        return properties.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T getProperty(String key, T defaultValue) {
        Object v = properties.get(key);
        return v != null ? (T) v : defaultValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RefactoringContext)) {
            return false;
        }
        RefactoringContext that = (RefactoringContext) o;
        return lineNumber == that.lineNumber
                && offset == that.offset
                && dryRun == that.dryRun
                && Objects.equals(sourceFile, that.sourceFile)
                && Objects.equals(oldName, that.oldName)
                && Objects.equals(newName, that.newName)
                && Objects.equals(elementType, that.elementType)
                && Objects.equals(properties, that.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceFile, oldName, newName, elementType, lineNumber, offset, dryRun, properties);
    }

    public static final class Builder {

        private String sourceFile;
        private String oldName;
        private String newName;
        private String elementType;
        private int lineNumber;
        private int offset;
        private boolean dryRun;
        private Map<String, Object> properties;

        private Builder() {
        }

        public Builder sourceFile(String f) {
            this.sourceFile = f;
            return this;
        }

        public Builder oldName(String n) {
            this.oldName = n;
            return this;
        }

        public Builder newName(String n) {
            this.newName = n;
            return this;
        }

        public Builder elementType(String t) {
            this.elementType = t;
            return this;
        }

        public Builder lineNumber(int ln) {
            this.lineNumber = ln;
            return this;
        }

        public Builder offset(int off) {
            this.offset = off;
            return this;
        }

        public Builder dryRun(boolean dr) {
            this.dryRun = dr;
            return this;
        }

        public Builder property(String key, Object value) {
            if (this.properties == null) {
                this.properties = new HashMap<>();
            }
            this.properties.put(key, value);
            return this;
        }

        public RefactoringContext build() {
            return new RefactoringContext(this);
        }
    }
}
