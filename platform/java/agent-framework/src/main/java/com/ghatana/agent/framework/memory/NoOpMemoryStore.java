package com.ghatana.agent.framework.memory;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * No-op {@link MemoryStore} that stores nothing and always returns empty results.
 * <p>
 * Used by workflow agents, legacy adapters, and tests that do not need memory.
 *
 * @doc.type class
 * @doc.purpose No-op memory store for contexts that do not need memory
 * @doc.layer framework
 * @doc.pattern Null Object
 * @doc.gaa.memory episodic|semantic|procedural|preference
 */
final class NoOpMemoryStore implements MemoryStore {

    static final NoOpMemoryStore INSTANCE = new NoOpMemoryStore();

    private NoOpMemoryStore() {}

    // ── Episodic ─────────────────────────────────────────────────────────────

    @Override
    public @NotNull Promise<Episode> storeEpisode(@NotNull Episode episode) {
        return Promise.of(episode);
    }

    @Override
    public @NotNull Promise<List<Episode>> queryEpisodes(@NotNull MemoryFilter filter, int limit) {
        return Promise.of(List.of());
    }

    @Override
    public @NotNull Promise<List<Episode>> searchEpisodes(@NotNull String query, int limit) {
        return Promise.of(List.of());
    }

    // ── Semantic ─────────────────────────────────────────────────────────────

    @Override
    public @NotNull Promise<Fact> storeFact(@NotNull Fact fact) {
        return Promise.of(fact);
    }

    @Override
    public @NotNull Promise<List<Fact>> queryFacts(
            @Nullable String subject, @NotNull String predicate, @Nullable String object) {
        return Promise.of(List.of());
    }

    @Override
    public @NotNull Promise<List<Fact>> searchFacts(@NotNull String concept, int limit) {
        return Promise.of(List.of());
    }

    // ── Procedural ───────────────────────────────────────────────────────────

    @Override
    public @NotNull Promise<Policy> storePolicy(@NotNull Policy policy) {
        return Promise.of(policy);
    }

    @Override
    public @NotNull Promise<List<Policy>> queryPolicies(@NotNull String situation, double minConfidence) {
        return Promise.of(List.of());
    }

    @Override
    public @NotNull Promise<Policy> getPolicy(@NotNull String policyId) {
        return Promise.of(null);
    }

    // ── Preference ───────────────────────────────────────────────────────────

    @Override
    public @NotNull Promise<Preference> storePreference(@NotNull Preference preference) {
        return Promise.of(preference);
    }

    @Override
    public @NotNull Promise<String> getPreference(@NotNull String key) {
        return Promise.of(null);
    }

    @Override
    public @NotNull Promise<Map<String, String>> getPreferences(@NotNull String namespace) {
        return Promise.of(Map.of());
    }

    // ── Management ───────────────────────────────────────────────────────────

    @Override
    public @NotNull Promise<GovernanceResult> applyGovernance(@NotNull GovernancePolicy policy) {
        return Promise.of(new GovernanceResult(0, 0, 0, 0));
    }

    @Override
    public @NotNull Promise<Integer> clearMemory() {
        return Promise.of(0);
    }

    @Override
    public @NotNull Promise<MemoryStats> getStats() {
        return Promise.of(new MemoryStats(0, 0, 0, 0, 0));
    }
}
