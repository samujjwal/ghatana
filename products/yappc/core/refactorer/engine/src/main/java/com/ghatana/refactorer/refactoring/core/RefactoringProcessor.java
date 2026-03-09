package com.ghatana.refactorer.refactoring.core;

import com.ghatana.refactorer.refactoring.api.Refactoring;
import com.ghatana.refactorer.refactoring.api.RefactoringContext;
import com.ghatana.refactorer.refactoring.api.RefactoringResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Processes refactoring operations and handles their execution. 
 * @doc.type class
 * @doc.purpose Handles refactoring processor operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public class RefactoringProcessor {
    private static final Logger log = LoggerFactory.getLogger(RefactoringProcessor.class);

    private final List<Refactoring<?>> availableRefactorings;

    public RefactoringProcessor() {
        this.availableRefactorings = new ArrayList<>();
    }

    /**
 * Registers a refactoring operation with the processor. */
    public void registerRefactoring(Refactoring<?> refactoring) {
        Objects.requireNonNull(refactoring, "Refactoring cannot be null");
        availableRefactorings.add(refactoring);
        log.debug("Registered refactoring: {}", refactoring.getId());
    }

    /**
 * Finds a refactoring by its ID. */
    @SuppressWarnings("unchecked")
    public <T extends RefactoringContext> Refactoring<T> findRefactoring(
            String id, Class<T> contextType) {
        return (Refactoring<T>)
                availableRefactorings.stream()
                        .filter(
                                r ->
                                        r.getId().equals(id)
                                                && contextType.isAssignableFrom(r.getContextType()))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "No refactoring found with ID: " + id));
    }

    /**
 * Executes a refactoring operation. */
    @SuppressWarnings("unchecked")
    public <T extends RefactoringContext> RefactoringResult execute(
            Refactoring<T> refactoring, T context) {
        Objects.requireNonNull(refactoring, "Refactoring cannot be null");
        Objects.requireNonNull(context, "Context cannot be null");

        log.info("Executing refactoring: {}", refactoring.getId());

        try {
            if (!refactoring.canApply(context)) {
                String message =
                        String.format(
                                "Refactoring '%s' cannot be applied to the given context",
                                refactoring.getId());
                log.warn(message);
                return RefactoringResult.failure(message);
            }

            return refactoring.apply(context);
        } catch (Exception e) {
            String message =
                    String.format(
                            "Failed to execute refactoring '%s': %s",
                            refactoring.getId(), e.getMessage());
            log.error(message, e);
            return RefactoringResult.failure(message);
        }
    }

    /**
 * Previews the changes that would be made by a refactoring operation. */
    @SuppressWarnings("unchecked")
    public <T extends RefactoringContext> RefactoringResult preview(
            Refactoring<T> refactoring, T context) {
        Objects.requireNonNull(refactoring, "Refactoring cannot be null");
        Objects.requireNonNull(context, "Context cannot be null");

        log.info("Previewing refactoring: {}", refactoring.getId());

        try {
            if (!refactoring.canApply(context)) {
                String message =
                        String.format(
                                "Refactoring '%s' cannot be applied to the given context",
                                refactoring.getId());
                log.warn(message);
                return RefactoringResult.failure(message);
            }

            return refactoring.preview(context);
        } catch (Exception e) {
            String message =
                    String.format(
                            "Failed to preview refactoring '%s': %s",
                            refactoring.getId(), e.getMessage());
            log.error(message, e);
            return RefactoringResult.failure(message);
        }
    }

    /**
 * Gets all available refactoring operations. */
    public List<Refactoring<?>> getAvailableRefactorings() {
        return new ArrayList<>(availableRefactorings);
    }
}
