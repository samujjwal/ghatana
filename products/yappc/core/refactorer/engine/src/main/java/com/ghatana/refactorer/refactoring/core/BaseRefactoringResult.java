package com.ghatana.refactorer.refactoring.core;

import com.ghatana.refactorer.refactoring.api.RefactoringResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base implementation of {@link RefactoringResult}. 
 * @doc.type class
 * @doc.purpose Handles base refactoring result operations
 * @doc.layer core
 * @doc.pattern Implementation
*/
public class BaseRefactoringResult implements RefactoringResult {
    private final boolean success;
    private final String errorMessage;
    private final List<Path> modifiedFiles;
    private final int changeCount;
    private final String changeSummary;

    private BaseRefactoringResult(Builder builder) {
        this.success = builder.success;
        this.errorMessage = builder.errorMessage;
        this.modifiedFiles =
                builder.modifiedFiles != null
                        ? new ArrayList<>(builder.modifiedFiles)
                        : new ArrayList<>();
        this.changeCount = builder.changeCount;
        this.changeSummary = builder.changeSummary != null ? builder.changeSummary : "";
    }

    @Override
    public boolean isSuccess() {
        return success;
    }

    @Override
    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public List<Path> getModifiedFiles() {
        return Collections.unmodifiableList(modifiedFiles);
    }

    @Override
    public int getChangeCount() {
        return changeCount;
    }

    @Override
    public String getChangeSummary() {
        return changeSummary;
    }

    /**
 * Creates a successful result with the given changes. */
    public static BaseRefactoringResult success(
            List<Path> modifiedFiles, int changeCount, String changeSummary) {
        return new Builder()
                .success(true)
                .modifiedFiles(modifiedFiles)
                .changeCount(changeCount)
                .changeSummary(changeSummary)
                .build();
    }

    /**
 * Creates a failed result with the given error message. */
    public static BaseRefactoringResult failure(String errorMessage) {
        return new Builder().success(false).errorMessage(errorMessage).changeCount(0).build();
    }

    /**
 * Creates a partial result with the given changes. */
    public static BaseRefactoringResult partial(
            List<Path> modifiedFiles, int changeCount, String changeSummary) {
        return new Builder()
                .success(true) // Partial results are still considered successful
                .modifiedFiles(modifiedFiles)
                .changeCount(changeCount)
                .changeSummary(changeSummary)
                .build();
    }

    /**
 * Builder for {@link BaseRefactoringResult}. */
    public static class Builder {
        private boolean success = false;
        private String errorMessage;
        private List<Path> modifiedFiles = new ArrayList<>();
        private int changeCount = 0;
        private String changeSummary;

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder modifiedFiles(List<Path> modifiedFiles) {
            this.modifiedFiles =
                    modifiedFiles != null ? new ArrayList<>(modifiedFiles) : new ArrayList<>();
            return this;
        }

        public Builder addModifiedFile(Path file) {
            if (file != null) {
                if (this.modifiedFiles == null) {
                    this.modifiedFiles = new ArrayList<>();
                }
                this.modifiedFiles.add(file);
            }
            return this;
        }

        public Builder changeCount(int changeCount) {
            this.changeCount = Math.max(0, changeCount);
            return this;
        }

        public Builder changeSummary(String changeSummary) {
            this.changeSummary = changeSummary;
            return this;
        }

        public BaseRefactoringResult build() {
            if (!success && errorMessage == null) {
                errorMessage = "Refactoring failed with unknown error";
            }
            return new BaseRefactoringResult(this);
        }
    }
}
