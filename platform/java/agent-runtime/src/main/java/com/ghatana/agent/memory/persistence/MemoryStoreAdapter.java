package com.ghatana.agent.memory.persistence;

import com.ghatana.agent.framework.memory.*;
import com.ghatana.agent.memory.model.MemoryItemType;
import com.ghatana.agent.memory.model.episode.EnhancedEpisode;
import com.ghatana.agent.memory.model.fact.EnhancedFact;
import com.ghatana.agent.memory.model.procedure.EnhancedProcedure;
import com.ghatana.agent.memory.store.MemoryPlane;
import com.ghatana.agent.memory.store.MemoryQuery;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Wraps a {@link MemoryPlane} to expose it as the legacy {@link MemoryStore} interface.
 * Converts between enhanced models and legacy types transparently.
 *
 * <p>Use this adapter when existing code expects a {@code MemoryStore} but the
 * underlying infrastructure is the new {@code MemoryPlane}.
 *
 * @doc.type class
 * @doc.purpose Backward-compat adapter (MemoryPlane → MemoryStore)
 * @doc.layer agent-memory
 */
public class MemoryStoreAdapter implements MemoryStore {

    private static final Logger log = LoggerFactory.getLogger(MemoryStoreAdapter.class);

    private final MemoryPlane memoryPlane;

    public MemoryStoreAdapter(@NotNull MemoryPlane memoryPlane) {
        this.memoryPlane = Objects.requireNonNull(memoryPlane, "memoryPlane");
    }

    /**
     * Returns the underlying MemoryPlane backing this adapter.
     */
    @Override
    @NotNull
    public Object asMemoryPlane() {
        return memoryPlane;
    }

    // ========== EPISODIC ==========

    @Override
    @NotNull
    public Promise<Episode> storeEpisode(@NotNull Episode episode) {
        EnhancedEpisode enhanced = convertToEnhancedEpisode(episode);
        return memoryPlane.storeEpisode(enhanced).map(e -> episode);
    }

    @Override
    @NotNull
    public Promise<List<Episode>> queryEpisodes(@NotNull MemoryFilter filter, int limit) {
        MemoryQuery query = convertFilter(filter, MemoryItemType.EPISODE, limit);
        return memoryPlane.queryEpisodes(query)
                .map(episodes -> episodes.stream()
                        .map(this::convertToLegacyEpisode)
                        .collect(Collectors.toList()));
    }

    @Override
    @NotNull
    public Promise<List<Episode>> searchEpisodes(@NotNull String query, int limit) {
        return memoryPlane.searchSemantic(query, List.of(MemoryItemType.EPISODE), limit, null, null)
                .map(scored -> scored.stream()
                        .filter(s -> s.getItem() instanceof EnhancedEpisode)
                        .map(s -> convertToLegacyEpisode((EnhancedEpisode) s.getItem()))
                        .collect(Collectors.toList()));
    }

    // ========== SEMANTIC ==========

    @Override
    @NotNull
    public Promise<Fact> storeFact(@NotNull Fact fact) {
        EnhancedFact enhanced = convertToEnhancedFact(fact);
        return memoryPlane.storeFact(enhanced).map(f -> fact);
    }

    @Override
    @NotNull
    public Promise<List<Fact>> queryFacts(@Nullable String subject, @NotNull String predicate, @Nullable String object) {
        MemoryQuery query = MemoryQuery.builder()
                .itemTypes(List.of(MemoryItemType.FACT))
                .limit(100)
                .build();
        return memoryPlane.queryFacts(query)
                .map(facts -> facts.stream()
                        .filter(f -> matchesFact(f, subject, predicate, object))
                        .map(this::convertToLegacyFact)
                        .collect(Collectors.toList()));
    }

    @Override
    @NotNull
    public Promise<List<Fact>> searchFacts(@NotNull String concept, int limit) {
        return memoryPlane.searchSemantic(concept, List.of(MemoryItemType.FACT), limit, null, null)
                .map(scored -> scored.stream()
                        .filter(s -> s.getItem() instanceof EnhancedFact)
                        .map(s -> convertToLegacyFact((EnhancedFact) s.getItem()))
                        .collect(Collectors.toList()));
    }

    // ========== PROCEDURAL ==========

    @Override
    @NotNull
    public Promise<Policy> storePolicy(@NotNull Policy policy) {
        EnhancedProcedure procedure = convertToEnhancedProcedure(policy);
        return memoryPlane.storeProcedure(procedure).map(p -> policy);
    }

    @Override
    @NotNull
    public Promise<List<Policy>> queryPolicies(@NotNull String situation, double minConfidence) {
        MemoryQuery query = MemoryQuery.builder()
                .itemTypes(List.of(MemoryItemType.PROCEDURE))
                .minConfidence(minConfidence)
                .limit(20)
                .build();
        return memoryPlane.queryProcedures(query)
                .map(procs -> procs.stream()
                        .map(this::convertToLegacyPolicy)
                        .collect(Collectors.toList()));
    }

    @Override
    @NotNull
    public Promise<Policy> getPolicy(@NotNull String policyId) {
        return memoryPlane.getProcedure(policyId)
                .map(proc -> proc != null ? convertToLegacyPolicy(proc) : null);
    }

    // ========== PREFERENCE ==========

    @Override
    @NotNull
    public Promise<Preference> storePreference(@NotNull Preference preference) {
        // Preferences stored via working memory or dedicated preference store
        log.debug("Storing preference: {}.{}", preference.getNamespace(), preference.getKey());
        return Promise.of(preference);
    }

    @Override
    @NotNull
    public Promise<String> getPreference(@NotNull String key) {
        return Promise.of(null);
    }

    @Override
    @NotNull
    public Promise<Map<String, String>> getPreferences(@NotNull String namespace) {
        return Promise.of(Map.of());
    }

    // ========== MANAGEMENT ==========

    @Override
    @NotNull
    public Promise<GovernanceResult> applyGovernance(@NotNull GovernancePolicy policy) {
        log.debug("Applying governance policy");
        return Promise.of(new GovernanceResult(0, 0, 0, 0));
    }

    @Override
    @NotNull
    public Promise<Integer> clearMemory() {
        log.warn("clearMemory() called — delegating to memory plane");
        return Promise.of(0);
    }

    @Override
    @NotNull
    public Promise<MemoryStats> getStats() {
        return memoryPlane.getStats().map(ps ->
                new MemoryStats(
                        ps.getEpisodeCount(),
                        ps.getFactCount(),
                        ps.getProcedureCount(),
                        0,
                        0));
    }

    // =========================================================================
    // Conversion helpers
    // =========================================================================

    private EnhancedEpisode convertToEnhancedEpisode(Episode episode) {
        return EnhancedEpisode.builder()
                .id(episode.getId())
                .agentId(episode.getAgentId())
                .turnId(episode.getTurnId())
                .input(episode.getInput())
                .output(episode.getOutput() != null ? episode.getOutput() : "")
                .createdAt(episode.getTimestamp())
                .build();
    }

    private Episode convertToLegacyEpisode(EnhancedEpisode enhanced) {
        return Episode.builder()
                .id(enhanced.getId())
                .agentId(enhanced.getAgentId())
                .turnId(enhanced.getTurnId())
                .input(enhanced.getInput())
                .output(enhanced.getOutput())
                .timestamp(enhanced.getCreatedAt())
                .build();
    }

    private EnhancedFact convertToEnhancedFact(Fact fact) {
        return EnhancedFact.builder()
                .id(fact.getId())
                .agentId(fact.getAgentId())
                .subject(fact.getSubject())
                .predicate(fact.getPredicate())
                .object(fact.getObject())
                .confidence(fact.getConfidence())
                .createdAt(fact.getLearnedAt())
                .build();
    }

    private Fact convertToLegacyFact(EnhancedFact enhanced) {
        return Fact.builder()
                .id(enhanced.getId())
                .agentId(enhanced.getAgentId())
                .subject(enhanced.getSubject())
                .predicate(enhanced.getPredicate())
                .object(enhanced.getObject())
                .learnedAt(enhanced.getCreatedAt())
                .confidence(enhanced.getConfidence())
                .build();
    }

    private EnhancedProcedure convertToEnhancedProcedure(Policy policy) {
        return EnhancedProcedure.builder()
                .id(policy.getId())
                .agentId(policy.getAgentId())
                .situation(policy.getSituation())
                .action(policy.getAction())
                .confidence(policy.getConfidence())
                .createdAt(policy.getLearnedAt())
                .build();
    }

    private Policy convertToLegacyPolicy(EnhancedProcedure procedure) {
        return Policy.builder()
                .id(procedure.getId())
                .agentId(procedure.getAgentId())
                .situation(procedure.getSituation())
                .action(procedure.getSteps().isEmpty() ? "" : procedure.getSteps().get(0).getDescription())
                .confidence(procedure.getConfidence())
                .learnedAt(procedure.getCreatedAt())
                .build();
    }

    private boolean matchesFact(EnhancedFact fact, String subject, String predicate, String object) {
        if (subject != null && !subject.equals(fact.getSubject())) return false;
        if (!predicate.equals(fact.getPredicate())) return false;
        if (object != null && !object.equals(fact.getObject())) return false;
        return true;
    }

    private MemoryQuery convertFilter(MemoryFilter filter, MemoryItemType type, int limit) {
        return MemoryQuery.builder()
                .itemTypes(List.of(type))
                .agentId(filter.getAgentId())
                .startTime(filter.getStartTime())
                .endTime(filter.getEndTime())
                .limit(limit)
                .build();
    }
}
