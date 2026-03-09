package com.ghatana.refactorer.refactoring.model.crosslanguage;

import java.util.Objects;

/**
 * Represents a reference from one language element to another, potentially across different
 * languages.
 
 * @doc.type class
 * @doc.purpose Handles cross language reference operations
 * @doc.layer core
 * @doc.pattern Enum
*/
public class CrossLanguageReference {
    private final String sourceFile;
    private final String sourceLanguage;
    private final String sourceElement;
    private final String sourceElementType;
    private final int sourceLine;
    private final int sourceColumn;

    private final String targetFile;
    private final String targetLanguage;
    private final String targetElement;
    private final String targetElementType;

    private final ReferenceType referenceType;

    public enum ReferenceType {
        IMPORT,
        TYPE_REFERENCE,
        METHOD_CALL,
        FIELD_ACCESS,
        INHERITANCE,
        IMPLEMENTATION,
        ANNOTATION,
        OTHER
    }

    private CrossLanguageReference(Builder builder) {
        this.sourceFile = builder.sourceFile;
        this.sourceLanguage = builder.sourceLanguage;
        this.sourceElement = builder.sourceElement;
        this.sourceElementType = builder.sourceElementType;
        this.sourceLine = builder.sourceLine;
        this.sourceColumn = builder.sourceColumn;
        this.targetFile = builder.targetFile;
        this.targetLanguage = builder.targetLanguage;
        this.targetElement = builder.targetElement;
        this.targetElementType = builder.targetElementType;
        this.referenceType = builder.referenceType;
    }

    public static Builder builder() {
        return new Builder();
    }

    // Getters
    public String getSourceFile() {
        return sourceFile;
    }

    public String getSourceLanguage() {
        return sourceLanguage;
    }

    public String getSourceElement() {
        return sourceElement;
    }

    public String getSourceElementType() {
        return sourceElementType;
    }

    public int getSourceLine() {
        return sourceLine;
    }

    public int getSourceColumn() {
        return sourceColumn;
    }

    public String getTargetFile() {
        return targetFile;
    }

    public String getTargetLanguage() {
        return targetLanguage;
    }

    public String getTargetElement() {
        return targetElement;
    }

    public String getTargetElementType() {
        return targetElementType;
    }

    public ReferenceType getReferenceType() {
        return referenceType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CrossLanguageReference that = (CrossLanguageReference) o;
        return sourceLine == that.sourceLine
                && sourceColumn == that.sourceColumn
                && Objects.equals(sourceFile, that.sourceFile)
                && Objects.equals(sourceLanguage, that.sourceLanguage)
                && Objects.equals(sourceElement, that.sourceElement)
                && Objects.equals(targetFile, that.targetFile)
                && Objects.equals(targetLanguage, that.targetLanguage)
                && Objects.equals(targetElement, that.targetElement)
                && referenceType == that.referenceType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                sourceFile,
                sourceLanguage,
                sourceElement,
                sourceLine,
                sourceColumn,
                targetFile,
                targetLanguage,
                targetElement,
                referenceType);
    }

    @Override
    public String toString() {
        return String.format(
                "%s:%d:%d -> %s:%s (%s)",
                sourceFile, sourceLine, sourceColumn, targetFile, targetElement, referenceType);
    }

    public static class Builder {
        private String sourceFile;
        private String sourceLanguage;
        private String sourceElement;
        private String sourceElementType;
        private int sourceLine = -1;
        private int sourceColumn = -1;

        private String targetFile;
        private String targetLanguage;
        private String targetElement;
        private String targetElementType;

        private ReferenceType referenceType = ReferenceType.OTHER;

        public Builder sourceFile(String sourceFile) {
            this.sourceFile = sourceFile;
            return this;
        }

        public Builder sourceLanguage(String sourceLanguage) {
            this.sourceLanguage = sourceLanguage;
            return this;
        }

        public Builder sourceElement(String sourceElement) {
            this.sourceElement = sourceElement;
            return this;
        }

        public Builder sourceElementType(String sourceElementType) {
            this.sourceElementType = sourceElementType;
            return this;
        }

        public Builder sourcePosition(int line, int column) {
            this.sourceLine = line;
            this.sourceColumn = column;
            return this;
        }

        public Builder targetFile(String targetFile) {
            this.targetFile = targetFile;
            return this;
        }

        public Builder targetLanguage(String targetLanguage) {
            this.targetLanguage = targetLanguage;
            return this;
        }

        public Builder targetElement(String targetElement) {
            this.targetElement = targetElement;
            return this;
        }

        public Builder targetElementType(String targetElementType) {
            this.targetElementType = targetElementType;
            return this;
        }

        public Builder referenceType(ReferenceType referenceType) {
            this.referenceType = referenceType;
            return this;
        }

        public CrossLanguageReference build() {
            return new CrossLanguageReference(this);
        }
    }
}
