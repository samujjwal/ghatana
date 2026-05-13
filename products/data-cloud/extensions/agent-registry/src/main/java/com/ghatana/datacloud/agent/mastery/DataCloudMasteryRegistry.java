/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.mastery;

import com.ghatana.agent.environment.EnvironmentFingerprint;
import com.ghatana.agent.mastery.*;
import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.EntityRepository;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.*;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Data Cloud-backed implementation of MasteryRegistry.
 *
 * <p>This implementation stores mastery items, transitions, and evidence in Data Cloud collections:
 * <ul>
 *   <li>agent-mastery-items: mastery items with scores and metadata</li>
 *   <li>agent-mastery-transitions: append-only transition log</li>
 *   <li>agent-mastery-evidence: evidence references and bundles</li>
 * </ul>
 *
 * <p>Uses EntityRepository for durable persistence with tenant isolation.
 *
 * @doc.type class
 * @doc.purpose Data Cloud implementation of MasteryRegistry
 * @doc.layer data-cloud
 * @doc.pattern Repository
 */
public final class DataCloudMasteryRegistry implements MasteryRegistry {

    private static final String COLLECTION_MASTERY_ITEMS = "agent-mastery-items";
    private static final String COLLECTION_MASTERY_TRANSITIONS = "agent-mastery-transitions";
    private static final String COLLECTION_MASTERY_EVIDENCE = "agent-mastery-evidence";

    private final EntityRepository entityRepository;
    private final MasteryTransitionRepository transitionRepository;
    private final MasteryEvidenceRepository evidenceRepository;

    /** Reverse index: masteryId → tenantId, populated on every save(). */
    private final ConcurrentHashMap<String, String> masteryIdToTenantId = new ConcurrentHashMap<>();

    /** All tenant IDs seen via save(), used for cross-tenant findStale(). */
    private final Set<String> knownTenantIds = ConcurrentHashMap.newKeySet();

    /**
     * Creates a new DataCloudMasteryRegistry.
     *
     * @param entityRepository Data Cloud entity repository
     * @param transitionRepository mastery transition repository
     * @param evidenceRepository mastery evidence repository
     */
    public DataCloudMasteryRegistry(
            @NotNull EntityRepository entityRepository,
            @NotNull MasteryTransitionRepository transitionRepository,
            @NotNull MasteryEvidenceRepository evidenceRepository
    ) {
        this.entityRepository = entityRepository;
        this.transitionRepository = transitionRepository;
        this.evidenceRepository = evidenceRepository;
    }

    /**
     * Finds the best mastery item matching the query, ranked by:
     * version applicability (active > maintenance > unknown),
     * mastery state (mastered > competent > practiced > observed),
     * execution score, and freshness.
     *
     * <p>Tenant ID from the query is required for tenant isolation.
     */
    @Override
    @NotNull
    public Promise<Optional<MasteryItem>> findBest(@NotNull MasteryQuery query) {
        return query(query.withLimit(50))
                .then(items -> {
                    Instant currentTime = query.currentTime() != null ? query.currentTime() : Instant.now();
                    Optional<MasteryItem> best = items.stream()
                            .filter(item -> item.isFresh(currentTime))
                            .max(Comparator
                                    .comparingInt((MasteryItem item) -> stateRank(item.state()))
                                    .thenComparingDouble(item -> item.score().executionScore()));
                    return Promise.of(best);
                });
    }

    /**
     * Rank mastery states from highest to lowest for findBest selection.
     * MASTERED(4) > COMPETENT(3) > PRACTICED(2) > OBSERVED(1) > everything else(0).
     */
    private static int stateRank(@NotNull MasteryState state) {
        return switch (state) {
            case MASTERED -> 4;
            case COMPETENT -> 3;
            case PRACTICED -> 2;
            case OBSERVED -> 1;
            default -> 0;
        };
    }

    /**
     * Rank mastery states by version applicability: active > maintenance > unknown/blocked.
     * Items in active learning states (MASTERED, COMPETENT, PRACTICED, OBSERVED) rank highest.
     * MAINTENANCE_ONLY ranks in the middle so legacy-version agents can still use them.
     * All other states (UNKNOWN, QUARANTINED, OBSOLETE, RETIRED) rank 0.
     */
    private static int versionApplicabilityRank(@NotNull MasteryState state) {
        return switch (state) {
            case MASTERED, COMPETENT, PRACTICED, OBSERVED -> 2;
            case MAINTENANCE_ONLY -> 1;
            default -> 0;
        };
    }

    @Override
    @NotNull
    @Deprecated
    public Promise<Optional<MasteryItem>> findBySkill(
            @NotNull String skillId,
            @NotNull EnvironmentFingerprint env
    ) {
        String tenantId = env.tenantId();
        Map<String, Object> filter = new HashMap<>();
        filter.put("skillId", skillId);
        filter.put("applicabilityTenantId", tenantId);

        return entityRepository.findAll(tenantId, COLLECTION_MASTERY_ITEMS, filter, "lastVerifiedAt:DESC", 0, 10)
                .then(entities -> {
                    Optional<MasteryItem> best = entities.stream()
                            .map(e -> MasteryItemMapper.fromDataMap(e.getData()))
                            .filter(MasteryItem::isActiveForRetrieval)
                            .filter(item -> item.isFresh(Instant.now()))
                            .max(Comparator.comparingDouble(item -> item.score().executionScore()));
                    return Promise.of(best);
                });
    }

    @Override
    @NotNull
    public Promise<List<MasteryItem>> query(@NotNull MasteryQuery query) {
        String tenantId = query.tenantId() != null ? query.tenantId() : "default";
        Map<String, Object> filter = new HashMap<>();

        if (query.skillId() != null) {
            filter.put("skillId", query.skillId());
        }
        if (query.agentId() != null) {
            filter.put("agentId", query.agentId());
        }
        if (query.agentReleaseId() != null) {
            filter.put("agentReleaseId", query.agentReleaseId());
        }
        if (query.domain() != null) {
            filter.put("domain", query.domain());
        }
        if (query.states() != null && !query.states().isEmpty()) {
            filter.put("state", query.states().iterator().next().name());
        }

        int offset = query.offset() != null ? query.offset() : 0;
        int limit = query.limit() != null ? query.limit() : 100;

        return entityRepository.findAll(tenantId, COLLECTION_MASTERY_ITEMS, filter, null, offset, limit)
                .then(entities -> {
                    Instant currentTime = query.currentTime() != null ? query.currentTime() : Instant.now();
                    List<MasteryItem> items = entities.stream()
                            .map(e -> MasteryItemMapper.fromDataMap(e.getData()))
                            .filter(item -> {
                                if (query.includeObsolete() == null || !query.includeObsolete()) {
                                    if (item.state() == MasteryState.OBSOLETE) return false;
                                }
                                if (query.includeRetired() == null || !query.includeRetired()) {
                                    if (item.state() == MasteryState.RETIRED) return false;
                                }
                                // MAINTENANCE_ONLY items are NOT excluded unless the caller explicitly
                                // requests exclusion (includeMaintenanceOnly=false). When the flag is
                                // null we keep them so version classification can rank them properly.
                                if (Boolean.FALSE.equals(query.includeMaintenanceOnly())) {
                                    if (item.state() == MasteryState.MAINTENANCE_ONLY) return false;
                                }
                                if (query.requireFreshness() != null && query.requireFreshness()) {
                                    if (!item.isFresh(currentTime)) return false;
                                }
                                return true;
                            })
                            // Rank by: version applicability (via state), mastery state, execution score,
                            // then freshness (items expiring sooner are ranked lower).
                            .sorted(Comparator
                                    .comparingInt((MasteryItem item) -> versionApplicabilityRank(item.state()))
                                    .thenComparingInt(item -> stateRank(item.state()))
                                    .thenComparingDouble(item -> item.score().executionScore())
                                    .reversed())
                            .collect(Collectors.toList());
                    return Promise.of(items);
                });
    }

    @Override
    @NotNull
    public Promise<MasteryItem> save(@NotNull MasteryItem item) {
        String tenantId = item.applicability().tenantId();
        Map<String, Object> dataMap = MasteryItemMapper.toDataMap(item);

        // Maintain reverse indexes so transition() and findStale() can look up the tenant.
        masteryIdToTenantId.put(item.masteryId(), tenantId);
        knownTenantIds.add(tenantId);

        Entity entity = Entity.builder()
                .tenantId(tenantId)
                .collectionName(COLLECTION_MASTERY_ITEMS)
                .data(dataMap)
                .createdBy(item.agentId())
                .build();

        return entityRepository.save(tenantId, entity)
                .map(savedEntity -> MasteryItemMapper.fromDataMap(savedEntity.getData()));
    }

    @Override
    @NotNull
    public Promise<MasteryTransitionResult> transition(@NotNull MasteryTransition transition) {
        // Derive the tenant from the reverse index populated during save().
        // Falls back to "default" only when the item was never saved through this registry instance.
        String tenantId = masteryIdToTenantId.getOrDefault(transition.masteryId(), "default");

        // Phase 1: find the current mastery item
        return entityRepository.findAll(tenantId, COLLECTION_MASTERY_ITEMS,
                Map.of("masteryId", transition.masteryId()), null, 0, 1)
                .then(entities -> {
                    if (entities.isEmpty()) {
                        return Promise.of(MasteryTransitionResult.failure(
                                transition.masteryId(),
                                MasteryState.UNKNOWN,
                                "Mastery item not found: " + transition.masteryId()
                        ));
                    }

                    MasteryItem item = MasteryItemMapper.fromDataMap(entities.get(0).getData());

                    // Validate the requested transition before touching the log
                    if (!isValidTransition(item.state(), transition.toState(), transition.evidenceRefs())) {
                        return Promise.of(MasteryTransitionResult.failure(
                                transition.masteryId(),
                                item.state(),
                                "Invalid transition from " + item.state() + " to " + transition.toState()
                        ));
                    }

                    // Phase 2 (atomic ordering): append the transition record FIRST.
                    // The transitionId acts as an idempotency key: a retry with the same
                    // transitionId is safe because the repository append is idempotent.
                    // If the subsequent item save fails, the transition log still has the
                    // record, allowing the caller to detect and retry via the same id.
                    return transitionRepository.append(transition)
                            .then(savedTransition -> {
                                // Phase 3: update mastery item state to match the committed transition
                                MasteryItem updatedItem = new MasteryItem(
                                        item.masteryId(),
                                        item.skillId(),
                                        item.domain(),
                                        item.agentId(),
                                        item.agentReleaseId(),
                                        transition.toState(),
                                        item.versionScope(),
                                        item.applicability(),
                                        item.score(),
                                        item.procedureIds(),
                                        item.semanticFactIds(),
                                        item.negativeKnowledgeIds(),
                                        item.evidenceRefs(),
                                        item.evaluationRefs(),
                                        item.knownFailureModeIds(),
                                        appendTransition(item.stateHistory(), savedTransition),
                                        Instant.now(),
                                        item.staleAfter(),
                                        item.labels()
                                );

                                return save(updatedItem)
                                        .map(saved -> MasteryTransitionResult.success(
                                                item.masteryId(),
                                                item.state(),
                                                transition.toState(),
                                                transition.transitionId()
                                        ));
                            });
                });
    }

    @Override
    @NotNull
    public Promise<List<MasteryItem>> findStale(@NotNull Instant now) {
        // Query mastery items across all known tenants; fall back to "default" when empty.
        Set<String> tenants = knownTenantIds.isEmpty()
                ? Set.of("default")
                : new HashSet<>(knownTenantIds);

        List<Promise<List<Entity>>> tenantQueries = tenants.stream()
                .map(tid -> entityRepository.count(tid, COLLECTION_MASTERY_ITEMS)
                        .then(count -> {
                            int limit = count > 1000 ? 1000 : (int) count.longValue();
                            return entityRepository.findAll(tid, COLLECTION_MASTERY_ITEMS,
                                    Map.of(), null, 0, limit);
                        }))
                .collect(Collectors.toList());

        return Promises.toList(tenantQueries)
                .then(entityLists -> {
                    List<MasteryItem> staleItems = entityLists.stream()
                            .flatMap(List::stream)
                            .map(e -> MasteryItemMapper.fromDataMap(e.getData()))
                            .filter(item -> !item.isFresh(now))
                            .collect(Collectors.toList());
                    return Promise.of(staleItems);
                });
    }

    @Override
    @NotNull
    public Promise<MasteryDecision> decide(@NotNull MasteryQuery query) {
        String tenantId = query.tenantId() != null ? query.tenantId() : "default";
        Map<String, Object> filter = new HashMap<>();

        if (query.skillId() != null) {
            filter.put("skillId", query.skillId());
        }
        if (query.agentId() != null) {
            filter.put("agentId", query.agentId());
        }

        // Find matching mastery items (including MAINTENANCE_ONLY for proper mode selection)
        return entityRepository.findAll(tenantId, COLLECTION_MASTERY_ITEMS, filter, null, 0, 50)
                .then(entities -> {
                    List<MasteryItem> matches = entities.stream()
                            .map(e -> MasteryItemMapper.fromDataMap(e.getData()))
                            .filter(item -> query.states() == null || query.states().contains(item.state()))
                            .filter(item -> item.isFresh(Instant.now()))
                            .toList();

                    if (matches.isEmpty()) {
                        // No matching mastery - block with unknown state and zero confidence
                        String skillId = query.skillId() != null ? query.skillId() : "unknown";
                        return Promise.of(MasteryDecision.block(
                                "unknown",
                                skillId,
                                MasteryState.UNKNOWN,
                                MasteryScore.zero(),
                                VersionScope.empty(),
                                "No matching mastery item found"
                        ));
                    }

                    // Get the best matching item (highest execution score)
                    MasteryItem best = matches.stream()
                            .max(Comparator.comparingDouble(item -> item.score().executionScore()))
                            .orElse(matches.get(0));

                    // Determine if human approval or verification is required based on mastery state
                    boolean requiresHumanApproval = best.state() == MasteryState.PRACTICED;
                    boolean requiresVerification = best.state() == MasteryState.COMPETENT;

                    if (requiresHumanApproval) {
                        return Promise.of(MasteryDecision.requireApproval(
                                best.masteryId(),
                                best.skillId(),
                                best.state(),
                                best.score(),
                                best.versionScope(),
                                "Mastery state is PRACTICED - requires human approval"
                        ));
                    }

                    if (requiresVerification) {
                        return Promise.of(MasteryDecision.requireVerification(
                                best.masteryId(),
                                best.skillId(),
                                best.state(),
                                best.score(),
                                best.versionScope(),
                                "Mastery state is COMPETENT - requires verification",
                                best.evidenceRefs()
                        ));
                    }

                    return Promise.of(MasteryDecision.allow(
                            best.masteryId(),
                            best.skillId(),
                            best.state(),
                            best.score(),
                            best.versionScope(),
                            "Mastery state is " + best.state() + " - execution allowed"
                    ));
                });
    }

    /**
     * Validates whether a transition is allowed based on state and evidence.
     *
     * @param fromState current state
     * @param toState target state
     * @param evidenceRefs evidence references
     * @return true if transition is valid
     */
    private boolean isValidTransition(MasteryState fromState, MasteryState toState, Map<String, String> evidenceRefs) {
        // Direct UNKNOWN to MASTERED is not allowed
        if (fromState == MasteryState.UNKNOWN && toState == MasteryState.MASTERED) {
            return false;
        }

        // MASTERED requires evaluation evidence
        if (toState == MasteryState.MASTERED && evidenceRefs.isEmpty()) {
            return false;
        }

        // COMPETENT requires some evidence
        if (toState == MasteryState.COMPETENT && evidenceRefs.isEmpty()) {
            return false;
        }

        // OBSERVED can transition from UNKNOWN with minimal evidence
        if (toState == MasteryState.OBSERVED && fromState == MasteryState.UNKNOWN) {
            return true;
        }

        // QUARANTINED can be reached from any state (for safety)
        if (toState == MasteryState.QUARANTINED) {
            return true;
        }

        // OBSOLETE can be reached from any active state
        if (toState == MasteryState.OBSOLETE && fromState.isActiveForRetrieval()) {
            return true;
        }

        // RETIRED can only be reached from OBSOLETE
        if (toState == MasteryState.RETIRED && fromState == MasteryState.OBSOLETE) {
            return true;
        }

        // MAINTENANCE_ONLY can be reached from MASTERED
        if (toState == MasteryState.MAINTENANCE_ONLY && fromState == MasteryState.MASTERED) {
            return true;
        }

        // Forward progression through lifecycle
        if (fromState == MasteryState.OBSERVED && toState == MasteryState.PRACTICED) {
            return true;
        }

        if (fromState == MasteryState.PRACTICED && toState == MasteryState.COMPETENT) {
            return true;
        }

        if (fromState == MasteryState.COMPETENT && toState == MasteryState.MASTERED) {
            return !evidenceRefs.isEmpty();
        }

        return false;
    }

    /**
     * Returns a new list with {@code transition} appended to {@code existing}.
     *
     * @param existing  current state history (immutable)
     * @param transition the transition to append
     * @return new unmodifiable list
     */
    private static List<MasteryTransition> appendTransition(
            List<MasteryTransition> existing, MasteryTransition transition) {
        List<MasteryTransition> updated = new java.util.ArrayList<>(existing);
        updated.add(transition);
        return java.util.Collections.unmodifiableList(updated);
    }
}
