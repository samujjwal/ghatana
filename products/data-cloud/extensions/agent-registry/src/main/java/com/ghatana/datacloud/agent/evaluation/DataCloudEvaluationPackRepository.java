/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.evaluation;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Data Cloud-backed repository for evaluation packs.
 *
 * <p>Evaluation packs contain test cases and benchmarks for evaluating agent skills.
 *
 * <p>TODO: Replace in-memory storage with actual Data Cloud persistence.
 *
 * @doc.type class
 * @doc.purpose Data Cloud repository for evaluation packs
 * @doc.layer data-cloud
 * @doc.pattern Repository
 */
public final class DataCloudEvaluationPackRepository {

    private final ConcurrentHashMap<String, EvaluationPack> packs = new ConcurrentHashMap<>();

    /**
     * Saves an evaluation pack.
     *
     * @param pack evaluation pack to save
     * @return promise of saved evaluation pack
     */
    @NotNull
    public Promise<EvaluationPack> save(@NotNull EvaluationPack pack) {
        packs.put(pack.packId(), pack);
        return Promise.of(pack);
    }

    /**
     * Finds an evaluation pack by ID.
     *
     * @param packId pack identifier
     * @return promise of optional evaluation pack
     */
    @NotNull
    public Promise<Optional<EvaluationPack>> findById(@NotNull String packId) {
        return Promise.of(Optional.ofNullable(packs.get(packId)));
    }

    /**
     * Finds evaluation packs by skill ID.
     *
     * @param skillId skill identifier
     * @return promise of list of evaluation packs
     */
    @NotNull
    public Promise<List<EvaluationPack>> findBySkillId(@NotNull String skillId) {
        return Promise.of(packs.values().stream()
                .filter(p -> p.skillId().equals(skillId))
                .toList());
    }

    /**
     * Finds evaluation packs by agent ID.
     *
     * @param agentId agent identifier
     * @return promise of list of evaluation packs
     */
    @NotNull
    public Promise<List<EvaluationPack>> findByAgentId(@NotNull String agentId) {
        return Promise.of(packs.values().stream()
                .filter(p -> p.agentId().equals(agentId))
                .toList());
    }

    /**
     * Finds active evaluation packs.
     *
     * @return promise of list of active evaluation packs
     */
    @NotNull
    public Promise<List<EvaluationPack>> findActive() {
        return Promise.of(packs.values().stream()
                .filter(EvaluationPack::isActive)
                .toList());
    }

    /**
     * Deletes an evaluation pack.
     *
     * @param packId pack identifier
     * @return promise of completion
     */
    @NotNull
    public Promise<Void> delete(@NotNull String packId) {
        packs.remove(packId);
        return Promise.of(null);
    }

    /**
     * Evaluation pack record.
     *
     * @doc.type record
     * @doc.purpose Evaluation pack record
     * @doc.layer data-cloud
     * @doc.pattern Record
     */
    public record EvaluationPack(
            @NotNull String packId,
            @NotNull String skillId,
            @NotNull String agentId,
            @NotNull String name,
            @NotNull String description,
            @NotNull List<TestCase> testCases,
            @NotNull Instant createdAt,
            @NotNull Instant updatedAt,
            boolean active,
            @NotNull String createdBy
    ) {
        public EvaluationPack {
            testCases = List.copyOf(testCases);
        }

        /**
         * Returns true if this pack is active.
         *
         * @return true if active
         */
        public boolean isActive() {
            return active;
        }
    }

    /**
     * Test case within an evaluation pack.
     *
     * @doc.type record
     * @doc.purpose Test case record
     * @doc.layer data-cloud
     * @doc.pattern Record
     */
    public record TestCase(
            @NotNull String caseId,
            @NotNull String name,
            @NotNull String description,
            @NotNull Map<String, Object> input,
            @NotNull Map<String, Object> expectedOutput,
            @NotNull String category
    ) {
        public TestCase {
            input = Map.copyOf(input);
            expectedOutput = Map.copyOf(expectedOutput);
        }
    }
}
