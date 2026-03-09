package com.ghatana.refactorer.refactoring.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Compatibility RefactoringResult used by tests in the core package. This is a
 * lightweight, test-focused value type (uses String file names rather than
 * Path) and provides static factories and a builder expected by legacy tests.
 
 * @doc.type class
 * @doc.purpose Handles refactoring result operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class RefactoringResult {

    private final boolean success;
    private final String errorMessage;
    private final List<String> modifiedFiles;
    private final int changeCount;
    private final String summary;
    private final Map<String, Object> metadata;

    private RefactoringResult(Builder b) {
        this.success = b.success;
        this.errorMessage = b.errorMessage;
        this.modifiedFiles = b.modifiedFiles != null ? Collections.unmodifiableList(new ArrayList<>(b.modifiedFiles)) : Collections.emptyList();
        this.changeCount = b.changeCount;
        this.summary = b.summary != null ? b.summary : "";
        this.metadata = b.metadata != null ? Collections.unmodifiableMap(new HashMap<>(b.metadata)) : Collections.emptyMap();
    }

    public static RefactoringResult success(List<String> modifiedFiles, int changeCount, String summary) {
        return new Builder().success(true).modifiedFiles(modifiedFiles).changeCount(changeCount).summary(summary).build();
    }

    public static RefactoringResult failure(String errorMessage) {
        return new Builder().success(false).errorMessage(errorMessage).changeCount(0).build();
    }

    public static RefactoringResult partial(List<String> modifiedFiles, int changeCount, String summary, String errorMessage) {
        return new Builder().success(false).modifiedFiles(modifiedFiles).changeCount(changeCount).summary(summary).errorMessage(errorMessage).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public List<String> getModifiedFiles() {
        return modifiedFiles;
    }

    public int getChangeCount() {
        return changeCount;
    }

    public String getSummary() {
        return summary;
    }

    public Object getMetadata(String key) {
        return metadata.get(key);
    }

    public Map<String, Object> getAllMetadata() {
        return metadata;
    }

    public static final class Builder {

        private boolean success = false;
        private String errorMessage;
        private List<String> modifiedFiles = new ArrayList<>();
        private int changeCount = 0;
        private String summary;
        private Map<String, Object> metadata;

        private Builder() {
        }

        public Builder success(boolean s) {
            this.success = s;
            return this;
        }

        public Builder errorMessage(String msg) {
            this.errorMessage = msg;
            return this;
        }

        public Builder modifiedFiles(List<String> files) {
            this.modifiedFiles = files != null ? new ArrayList<>(files) : new ArrayList<>();
            return this;
        }

        public Builder addModifiedFile(String file) {
            if (this.modifiedFiles == null) {
                this.modifiedFiles = new ArrayList<>();
            }
            if (file != null) {
                this.modifiedFiles.add(file);
            }
            return this;
        }

        public Builder changeCount(int c) {
            this.changeCount = Math.max(0, c);
            return this;
        }

        public Builder summary(String s) {
            this.summary = s;
            return this;
        }

        public Builder metadata(Map<String, Object> m) {
            this.metadata = m != null ? new HashMap<>(m) : new HashMap<>();
            return this;
        }

        public Builder metadata(String key, Object value) {
            if (this.metadata == null) {
                this.metadata = new HashMap<>();
            }
            this.metadata.put(key, value);
            return this;
        }

        public RefactoringResult build() {
            if (!success && errorMessage == null) {
                errorMessage = "Refactoring failed";
            }
            return new RefactoringResult(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RefactoringResult)) {
            return false;
        }
        RefactoringResult that = (RefactoringResult) o;
        return success == that.success
                && changeCount == that.changeCount
                && Objects.equals(errorMessage, that.errorMessage)
                && Objects.equals(modifiedFiles, that.modifiedFiles)
                && Objects.equals(summary, that.summary)
                && Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, errorMessage, modifiedFiles, changeCount, summary, metadata);
    }
}
