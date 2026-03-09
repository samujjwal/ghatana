package com.ghatana.refactorer.refactoring.core;

import com.ghatana.refactorer.refactoring.api.RefactoringContext;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Base implementation of {@link RefactoringContext}. 
 * @doc.type class
 * @doc.purpose Handles base refactoring context operations
 * @doc.layer core
 * @doc.pattern Implementation
*/
public class BaseRefactoringContext implements RefactoringContext {
    private final PolyfixProjectContext projectContext;
    private final Path projectRoot;
    private final Set<Path> affectedFiles;
    private final boolean dryRun;
    private final boolean interactive;

    protected BaseRefactoringContext(Builder builder) {
        this.projectContext = builder.projectContext;
        this.projectRoot = builder.projectRoot;
        this.affectedFiles =
                builder.affectedFiles != null
                        ? new HashSet<>(builder.affectedFiles)
                        : new HashSet<>();
        this.dryRun = builder.dryRun;
        this.interactive = builder.interactive;
    }

    @Override
    public PolyfixProjectContext getPolyfixProjectContext() {
        return projectContext;
    }

    @Override
    public Path getProjectRoot() {
        return projectRoot;
    }

    @Override
    public Set<Path> getAffectedFiles() {
        return Collections.unmodifiableSet(affectedFiles);
    }

    @Override
    public boolean isDryRun() {
        return dryRun;
    }

    @Override
    public boolean isInteractive() {
        return interactive;
    }

    /**
 * Builder for {@link BaseRefactoringContext}. */
    public static class Builder {
        private PolyfixProjectContext projectContext;
        private Path projectRoot;
        private Set<Path> affectedFiles;
        private boolean dryRun = false;
        private boolean interactive = false;

        public Builder projectContext(PolyfixProjectContext projectContext) {
            this.projectContext = projectContext;
            return this;
        }

        public Builder projectRoot(Path projectRoot) {
            this.projectRoot = projectRoot;
            return this;
        }

        public Builder affectedFiles(Set<Path> affectedFiles) {
            this.affectedFiles = affectedFiles;
            return this;
        }

        public Builder dryRun(boolean dryRun) {
            this.dryRun = dryRun;
            return this;
        }

        public Builder interactive(boolean interactive) {
            this.interactive = interactive;
            return this;
        }

        public BaseRefactoringContext build() {
            if (projectContext == null) {
                throw new IllegalStateException("Project context is required");
            }
            if (projectRoot == null) {
                projectRoot = projectContext.getProjectRoot();
            }
            return new BaseRefactoringContext(this);
        }
    }
}
