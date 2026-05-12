/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * Repository for managing mastery evidence records.
 *
 * <p>Provides CRUD operations for evidence that supports mastery state transitions.
 * Evidence is first-class and can be queried by type, reference, or associated mastery items.
 *
 * @doc.type interface
 * @doc.purpose Repository for mastery evidence records
 * @doc.layer agent-core
 * @doc.pattern Repository
 */
public interface MasteryEvidenceRepository {

    /**
     * Saves a mastery evidence record.
     *
     * @param evidence evidence to save
     * @return promise of the saved evidence
     */
    @NotNull
    Promise<MasteryEvidence> save(@NotNull MasteryEvidence evidence);

    /**
     * Finds an evidence record by its ID.
     *
     * @param evidenceId evidence ID
     * @return promise of the evidence, or empty if not found
     */
    @NotNull
    Promise<Optional<MasteryEvidence>> findById(@NotNull String evidenceId);

    /**
     * Finds all evidence records for a specific mastery item.
     *
     * @param masteryId mastery item ID
     * @return promise of list of evidence records
     */
    @NotNull
    Promise<List<MasteryEvidence>> findByMasteryId(@NotNull String masteryId);

    /**
     * Finds all evidence records of a specific type.
     *
     * @param type evidence type
     * @return promise of list of evidence records
     */
    @NotNull
    Promise<List<MasteryEvidence>> findByType(@NotNull MasteryEvidenceType type);

    /**
     * Finds evidence records by reference (e.g., episode ID, evaluation ID).
     *
     * @param ref reference string
     * @return promise of list of evidence records
     */
    @NotNull
    Promise<List<MasteryEvidence>> findByRef(@NotNull String ref);

    /**
     * Finds evidence records created by a specific agent or user.
     *
     * @param createdBy creator ID
     * @return promise of list of evidence records
     */
    @NotNull
    Promise<List<MasteryEvidence>> findByCreatedBy(@NotNull String createdBy);

    /**
     * Deletes an evidence record by its ID.
     *
     * @param evidenceId evidence ID
     * @return promise of completion
     */
    @NotNull
    Promise<Void> deleteById(@NotNull String evidenceId);
}
