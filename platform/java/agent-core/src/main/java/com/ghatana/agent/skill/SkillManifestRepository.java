/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.skill;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Repository for skill manifests.
 *
 * <p>Provides access to canonical skill manifests with tenant isolation support.
 *
 * @doc.type interface
 * @doc.purpose Repository for skill manifests
 * @doc.layer agent-core
 * @doc.pattern Repository
 */
public interface SkillManifestRepository {

    /**
     * Finds a skill manifest by ID.
     *
     * @param skillId skill identifier
     * @return Promise of Optional containing the manifest if found
     */
    Promise<Optional<SkillManifest>> findById(@NotNull String skillId);

    /**
     * Finds skill manifests by domain.
     *
     * @param domain domain identifier
     * @return Promise of list of manifests in the domain
     */
    Promise<List<SkillManifest>> findByDomain(@NotNull String domain);

    /**
     * Finds skill manifests by category.
     *
     * @param category category identifier
     * @return Promise of list of manifests in the category
     */
    Promise<List<SkillManifest>> findByCategory(@NotNull String category);

    /**
     * Lists all skill manifests.
     *
     * @return Promise of list of all manifests
     */
    Promise<List<SkillManifest>> listAll();

    /**
     * Saves a skill manifest.
     *
     * @param manifest skill manifest to save
     * @return Promise of saved manifest
     */
    Promise<SkillManifest> save(@NotNull SkillManifest manifest);

    /**
     * Deletes a skill manifest by ID.
     *
     * @param skillId skill identifier
     * @return Promise of void
     */
    Promise<Void> deleteById(@NotNull String skillId);

    /**
     * Searches for skill manifests by query.
     *
     * @param query search query
     * @return Promise of list of matching manifests
     */
    Promise<List<SkillManifest>> search(@NotNull SkillManifestQuery query);

    /**
     * Query for skill manifests.
     */
    record SkillManifestQuery(
            String domain,
            String category,
            String version,
            Set<String> requiredEvaluationPacks
    ) {}
}
