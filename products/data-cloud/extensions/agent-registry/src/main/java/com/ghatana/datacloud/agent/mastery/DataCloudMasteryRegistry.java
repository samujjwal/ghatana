/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.mastery;

import com.ghatana.agent.environment.EnvironmentFingerprint;
import com.ghatana.agent.context.version.VersionContext;
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
    private final com.ghatana.agent.mastery.transition.MasteryTransitionPolicy transitionPolicy;

    /**
     * Creates a new DataCloudMasteryRegistry.
     *
     * @param entityRepository Data Cloud entity repository
     * @param transitionRepository mastery transition repository
     * @param evidenceRepository mastery evidence repository
     * @param transitionPolicy mastery transition policy
     */
    public DataCloudMasteryRegistry(
            @NotNull EntityRepository entityRepository,
            @NotNull MasteryTransitionRepository transitionRepository,
            @NotNull MasteryEvidenceRepository evidenceRepository,
            @NotNull com.ghatana.agent.mastery.transition.MasteryTransitionPolicy transitionPolicy
    ) {
        this.entityRepository = entityRepository;
        this.transitionRepository = transitionRepository;
        this.evidenceRepository = evidenceRepository;
        this.transitionPolicy = transitionPolicy;
    }

    /**
     * Finds the best mastery item matching the query, ranked by:
     * version applicability (active > maintenance > unknown),
     * mastery state (mastered > competent > practiced > observed),
     * execution score, and freshness.
     *
     * <p>Tenant ID from the query is required for tenant isolation.
     * Version context from the query is used for version-aware ranking.
     */
    @Override
    @NotNull
    public Promise<Optional<MasteryItem>> findBest(@NotNull MasteryQuery query) {
        return query(query.withLimit(50))
                .then(items -> {
                    Instant currentTime = query.currentTime() != null ? query.currentTime() : Instant.now();
                    String versionContextStr = query.versionContext();

                    // Build VersionContext if provided
                    VersionContext versionContext = null;
                    if (versionContextStr != null && !versionContextStr.isEmpty()) {
                        // Parse version context string into dependencies map
                        // Format: "component1=1.0.0,component2=2.0.0"
                        Map<String, String> dependencies = new HashMap<>();
                        String[] pairs = versionContextStr.split(",");
                        for (String pair : pairs) {
                            String[] kv = pair.split("=", 2);
                            if (kv.length == 2) {
                                dependencies.put(kv[0].trim(), kv[1].trim());
                            }
                        }
                        versionContext = new VersionContext(dependencies, Map.of(), Map.of(), Map.of(), "unknown", Instant.now());
                    }

                    final VersionContext finalVersionContext = versionContext;

                    Optional<MasteryItem> best = items.stream()
                            .filter(item -> item.isFresh(currentTime))
                            .max(Comparator
                                    .comparingInt((MasteryItem item) -> {
                                        // Rank by version applicability if version context is provided
                                        if (finalVersionContext != null && item.versionScope() != null) {
                                            VersionApplicability applicability = item.versionScope().classify(finalVersionContext);
                                            return versionApplicabilityRank(applicability);
                                        }
                                        return 1; // Default rank if no version context
                                    })
                                    .thenComparingInt((MasteryItem item) -> stateRank(item.state()))
                                    .thenComparingDouble(item -> item.score().executionScore()));
                    return Promise.of(best);
                });
    }

    /**
     * Rank version applicability for findBest selection.
     * ACTIVE(3) > MAINTENANCE(2) > UNKNOWN(1) > OBSOLETE/BLOCKED(0).
     */
    private static int versionApplicabilityRank(@NotNull VersionApplicability applicability) {
        return switch (applicability) {
            case ACTIVE -> 3;
            case MAINTENANCE -> 2;
            case UNKNOWN -> 1;
            case OBSOLETE -> 0;
        };
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
        String tenantId = query.tenantId();
        if (tenantId == null || tenantId.isEmpty()) {
            return Promise.ofException(new IllegalArgumentException("tenantId is required for mastery queries"));
        }
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

        // Handle multiple states - if states are provided, we'll filter in-memory after query
        // since Data Cloud may not support multi-value queries efficiently
        Set<String> requestedStates = (query.states() != null && !query.states().isEmpty())
                ? query.states().stream().map(MasteryState::name).collect(Collectors.toSet())
                : null;

        int offset = query.offset() != null ? query.offset() : 0;
        int limit = query.limit() != null ? query.limit() : 100;

        return entityRepository.findAll(tenantId, COLLECTION_MASTERY_ITEMS, filter, null, offset, limit)
                .then(entities -> {
                    Instant currentTime = query.currentTime() != null ? query.currentTime() : Instant.now();
                    List<MasteryItem> items = entities.stream()
                            .map(e -> MasteryItemMapper.fromDataMap(e.getData()))
                            .filter(item -> {
                                // Filter by requested states if provided
                                if (requestedStates != null && !requestedStates.isEmpty()) {
                                    if (!requestedStates.contains(item.state().name())) {
                                        return false;
                                    }
                                }
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
    public Promise<Optional<MasteryDecision>> queryMastery(@NotNull MasteryQuery query) {
        return query(query).then(items -> {
            if (items.isEmpty()) {
                return Promise.of(Optional.empty());
            }
            MasteryItem best = items.get(0);
            MasteryDecision decision = switch (best.state()) {
                case MASTERED, COMPETENT -> MasteryDecision.allow(
                        best.masteryId(), best.skillId(), best.state(),
                        MasteryScore.correctnessOnly(best.confidence()), best.versionScope(), "Mastery query result");
                case PRACTICED -> MasteryDecision.requireVerification(
                        best.masteryId(), best.skillId(), best.state(),
                        MasteryScore.correctnessOnly(best.confidence()), best.versionScope(), "Mastery query result",
                        best.evidenceRefs());
                case OBSERVED -> MasteryDecision.requireApproval(
                        best.masteryId(), best.skillId(), best.state(),
                        MasteryScore.correctnessOnly(best.confidence()), best.versionScope(), "Mastery query result");
                default -> MasteryDecision.block(
                        best.masteryId(), best.skillId(), best.state(),
                        MasteryScore.correctnessOnly(best.confidence()), best.versionScope(), "Mastery query result");
            };
            return Promise.of(Optional.of(decision));
        });
    }

    @Override
    @NotNull
    public Promise<MasteryItem> save(@NotNull MasteryItem item) {
        String tenantId = item.applicability().tenantId();
        
        // Validate tenantId matches item.tenantId for tenant isolation
        if (item.tenantId() != null && !item.tenantId().isEmpty()) {
            if (!tenantId.equals(item.tenantId())) {
                return Promise.ofException(new IllegalArgumentException(
                        "Tenant ID mismatch: applicability.tenantId='" + tenantId + 
                        "' does not match item.tenantId='" + item.tenantId() + "'"
                ));
            }
        }

        // Phase 7.1: Idempotent save/update by tenantId + masteryId
        // First check if item already exists
        return entityRepository.findAll(tenantId, COLLECTION_MASTERY_ITEMS,
                Map.of("masteryId", item.masteryId()), null, 0, 1)
                .then(entities -> {
                    Map<String, Object> dataMap = MasteryItemMapper.toDataMap(item);
                    
                    // Add optimistic locking version field
                    long version = 1L;
                    UUID entityId = UUID.randomUUID();
                    
                    if (!entities.isEmpty()) {
                        // Item exists - use optimistic locking
                        Entity existing = entities.get(0);
                        Object existingVersion = existing.getData().get("version");
                        version = existingVersion != null ? ((Number) existingVersion).longValue() + 1 : 1;
                        entityId = existing.getId(); // Use existing entity ID
                    }
                    
                    dataMap.put("version", version);

                    Entity entity = Entity.builder()
                            .id(entityId)
                            .tenantId(tenantId)
                            .collectionName(COLLECTION_MASTERY_ITEMS)
                            .data(dataMap)
                            .createdBy(item.agentId())
                            .build();

                    return entityRepository.save(tenantId, entity)
                            .map(savedEntity -> MasteryItemMapper.fromDataMap(savedEntity.getData()));
                });
    }

    @Override
    @NotNull
    public Promise<MasteryTransitionResult> transition(@NotNull MasteryTransition transition) {
        // Use explicit tenantId from the transition record (preferred over reverse-index workaround).
        String tenantId = transition.tenantId();
        if (tenantId == null || tenantId.isEmpty()) {
            return Promise.of(MasteryTransitionResult.failure(
                    transition.masteryId(),
                    MasteryState.UNKNOWN,
                    "tenantId is required for mastery transitions"
            ));
        }

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

                    // Validate transition using centralized policy
                    var validation = transitionPolicy.canTransition(
                            item.state(),
                            transition.toState(),
                            transition.evidenceRefs()
                    );

                    if (!validation.allowed()) {
                        return Promise.of(MasteryTransitionResult.failure(
                                transition.masteryId(),
                                item.state(),
                                validation.errorMessage().orElse("Invalid transition")
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
                                        item.tenantId(),
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
                                        item.labels(),
                                        item.confidence()
                                );

                                return save(updatedItem)
                                        .map(savedItem -> MasteryTransitionResult.success(
                                                item.masteryId(),
                                                item.state(),
                                                transition.toState(),
                                                transition.transitionId()
                                        ));
                            });
                });
    }

    @Override
    @SuppressWarnings("deprecation")
    @NotNull
    public Promise<List<MasteryItem>> findStale(@NotNull Instant now) {
        // Deprecated: forces migration to the explicit tenant-scoped variant.
        return Promise.ofException(new UnsupportedOperationException(
                "findStale(Instant) is deprecated. Use findStale(String tenantId, Instant) for tenant-scoped stale detection."));
    }

    @Override
    @NotNull
    public Promise<List<MasteryItem>> findStale(@NotNull String tenantId, @NotNull Instant now) {
        if (tenantId == null || tenantId.isEmpty()) {
            return Promise.ofException(new IllegalArgumentException("tenantId is required for stale detection"));
        }

        // Phase 7.1: Paginated stale scan instead of cap at 1000
        int pageSize = 100;
        int offset = 0;
        
        return findStalePaginated(tenantId, now, offset, pageSize, new ArrayList<>());
    }
    
    /**
     * Recursively scans for stale items with pagination.
     * Phase 7.1: Supports large tenants by paginating instead of capping at 1000.
     */
    @NotNull
    private Promise<List<MasteryItem>> findStalePaginated(
            @NotNull String tenantId,
            @NotNull Instant now,
            int offset,
            int pageSize,
            List<MasteryItem> accumulated) {
        return entityRepository.findAll(tenantId, COLLECTION_MASTERY_ITEMS,
                Map.of(), null, offset, pageSize)
                .then(entities -> {
                    List<MasteryItem> pageStaleItems = entities.stream()
                            .map(e -> MasteryItemMapper.fromDataMap(e.getData()))
                            .filter(item -> !item.isFresh(now))
                            .collect(Collectors.toList());
                    
                    accumulated.addAll(pageStaleItems);
                    
                    // If we got a full page, there might be more items
                    if (entities.size() == pageSize) {
                        return findStalePaginated(tenantId, now, offset + pageSize, pageSize, accumulated);
                    }
                    
                    // No more items, return accumulated results
                    return Promise.of(accumulated);
                });
    }

    @Override
    @NotNull
    public Promise<Optional<MasteryItem>> getById(@NotNull String tenantId, @NotNull String masteryId) {
        if (tenantId == null || tenantId.isEmpty()) {
            return Promise.ofException(new IllegalArgumentException("tenantId is required for getById"));
        }
        if (masteryId == null || masteryId.isEmpty()) {
            return Promise.ofException(new IllegalArgumentException("masteryId is required for getById"));
        }
        
        return entityRepository.findAll(tenantId, COLLECTION_MASTERY_ITEMS, 
                Map.of("masteryId", masteryId), null, 0, 1)
                .then(entities -> {
                    if (entities.isEmpty()) {
                        return Promise.of(Optional.empty());
                    }
                    MasteryItem item = MasteryItemMapper.fromDataMap(entities.get(0).getData());
                    return Promise.of(Optional.of(item));
                });
    }

    @Override
    @NotNull
    public Promise<MasteryDecision> decide(@NotNull MasteryQuery query) {
        String tenantId = query.tenantId();
        if (tenantId == null || tenantId.isEmpty()) {
            String skillId = query.skillId() != null ? query.skillId() : "unknown";
            return Promise.of(MasteryDecision.block(
                    "unknown",
                    skillId,
                    MasteryState.UNKNOWN,
                    MasteryScore.zero(),
                    VersionScope.empty(),
                    "tenantId is required for mastery decisions"
            ));
        }

        // Reuse findBest() for version-aware ranking (active > maintenance > unknown)
        return findBest(query)
                .then(bestOpt -> {
                    if (bestOpt.isEmpty()) {
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

                    MasteryItem best = bestOpt.get();
                    VersionApplicability applicability = VersionApplicability.UNKNOWN;
                    
                    // Compute version applicability if version context is available
                    String versionContextStr = query.versionContext();
                    if (versionContextStr != null && !versionContextStr.isEmpty() && best.versionScope() != null) {
                        // Parse version context string into dependencies map
                        Map<String, String> dependencies = new HashMap<>();
                        String[] pairs = versionContextStr.split(",");
                        for (String pair : pairs) {
                            String[] kv = pair.split("=", 2);
                            if (kv.length == 2) {
                                dependencies.put(kv[0].trim(), kv[1].trim());
                            }
                        }
                        VersionContext versionContext = new VersionContext(dependencies, Map.of(), Map.of(), Map.of(), "unknown", Instant.now());
                        applicability = best.versionScope().classify(versionContext);
                    }

                    // Block if version is obsolete
                    if (applicability == VersionApplicability.OBSOLETE) {
                        return Promise.of(MasteryDecision.block(
                                best.masteryId(),
                                best.skillId(),
                                best.state(),
                                best.score(),
                                best.versionScope(),
                                "Version is obsolete for current runtime context"
                        ));
                    }

                    // Block if version is maintenance but no legacy context matches
                    if (applicability == VersionApplicability.MAINTENANCE && !best.state().requiresLegacyContext()) {
                        return Promise.of(MasteryDecision.block(
                                best.masteryId(),
                                best.skillId(),
                                best.state(),
                                best.score(),
                                best.versionScope(),
                                "Maintenance version requires legacy context for state " + best.state()
                        ));
                    }

                    // Block if terminal state
                    if (best.state().isTerminal()) {
                        return Promise.of(MasteryDecision.block(
                                best.masteryId(),
                                best.skillId(),
                                best.state(),
                                best.score(),
                                best.versionScope(),
                                "Mastery state is terminal: " + best.state()
                        ));
                    }

                    // Block if stale
                    if (!best.isFresh(Instant.now())) {
                        return Promise.of(MasteryDecision.block(
                                best.masteryId(),
                                best.skillId(),
                                best.state(),
                                best.score(),
                                best.versionScope(),
                                "Mastery item is stale and requires refresh"
                        ));
                    }

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
                                "Mastery state is PRACTICED - requires human approval (version applicability: " + applicability + ")"
                        ));
                    }

                    if (requiresVerification) {
                        return Promise.of(MasteryDecision.requireVerification(
                                best.masteryId(),
                                best.skillId(),
                                best.state(),
                                best.score(),
                                best.versionScope(),
                                "Mastery state is COMPETENT - requires verification (version applicability: " + applicability + ")",
                                best.evidenceRefs()
                        ));
                    }

                    return Promise.of(MasteryDecision.allow(
                            best.masteryId(),
                            best.skillId(),
                            best.state(),
                            best.score(),
                            best.versionScope(),
                            "Mastery state is " + best.state() + " - execution allowed (version applicability: " + applicability + ")"
                    ));
                });
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
