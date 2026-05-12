/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.services.content;

import com.ghatana.yappc.api.UserEdits;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Service for preserving user edits across review cycles.
 * Stores user edits with generationRunId, actorId, timestamp, and file-level edits.
 * Allows retrieval and reapplication of user edits during subsequent reviews.
 *
 * @doc.type interface
 * @doc.purpose Service for preserving user edits across review cycles
 * @doc.layer product
 * @doc.pattern Service
 */
public interface UserEditsPreservationService {

    /**
     * Stores user edits for a generation run.
     *
     * @param userEdits The user edits to store
     * @return true if successful, false otherwise
     */
    boolean storeUserEdits(@NotNull UserEdits userEdits);

    /**
     * Retrieves user edits by generation run ID.
     *
     * @param generationRunId The generation run ID
     * @return Optional containing the user edits if found
     */
    Optional<UserEdits> retrieveUserEditsByRunId(@NotNull String generationRunId);

    /**
     * Retrieves user edits by edits ID.
     *
     * @param editsId The edits ID
     * @return Optional containing the user edits if found
     */
    Optional<UserEdits> retrieveUserEditsById(@NotNull String editsId);

    /**
     * Checks if user edits exist for a generation run.
     *
     * @param generationRunId The generation run ID
     * @return true if user edits exist, false otherwise
     */
    boolean hasUserEdits(@NotNull String generationRunId);

    /**
     * Deletes user edits by generation run ID.
     *
     * @param generationRunId The generation run ID
     * @return true if successful, false otherwise
     */
    boolean deleteUserEditsByRunId(@NotNull String generationRunId);

    /**
     * Reapplies user edits to current content.
     * Merges user edits into the current content based on file paths and line ranges.
     *
     * @param currentContent The current content
     * @param userEdits The user edits to apply
     * @return The content with user edits applied
     */
    @NotNull String reapplyUserEdits(@NotNull String currentContent, @NotNull UserEdits userEdits);

    /**
     * Validates that user edits are still applicable to current content.
     * Checks if file paths and line ranges are still valid.
     *
     * @param currentContent The current content
     * @param userEdits The user edits to validate
     * @return true if edits are still applicable, false otherwise
     */
    boolean validateUserEditsApplicability(@NotNull String currentContent, @NotNull UserEdits userEdits);
}
