/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.mastery;

import com.ghatana.agent.environment.EnvironmentFingerprint;
import com.ghatana.agent.mastery.*;
import com.ghatana.agent.runtime.mode.ExecutionMode;
import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.EntityRepository;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.*;
import java.util.UUID;
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

    @Override
    @NotNull
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
                                if (query.includeMaintenanceOnly() == null || !query.includeMaintenanceOnly()) {
                                    if (item.state() == MasteryState.MAINTENANCE_ONLY) return false;
                                }
                                if (query.requireFreshness() != null && query.requireFreshness()) {
                                    if (!item.isFresh(currentTime)) return false;
                                }
                                return true;
                            })
                            .sorted(Comparator.comparingDouble((MasteryItem item) -> item.score().executionScore()).reversed())
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
            // Determine execution mode based on mastery state
            ExecutionMode recommendedMode = switch (best.state()) {
                case MASTERED, COMPETENT -> ExecutionMode.AUTONOMOUS;
                case PRACTICED -> ExecutionMode.SUPERVISED;
                default -> ExecutionMode.BLOCKED;
            };
            
            MasteryDecision decision = new MasteryDecision(
                    best.masteryId(),
                    best.skillId(),
                    recommendedMode,
                    best.state().isActiveForRetrieval(),
                    false,
                    false,
                    "Mastery query result",
                    best.evidenceRefs(),
                    best.state(),
                    best.versionScope(),
                    best.confidence()
            );
            return Promise.of(Optional.of(decision));
        });
    }

    @Override
    @NotNull
    public Promise<MasteryItem> save(@NotNull MasteryItem item) {
        String tenantId = item.applicability().tenantId();
        Map<String, Object> dataMap = MasteryItemMapper.toDataMap(item);

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
        String tenantId = transition.tenantId();
        if (tenantId == null || tenantId.isEmpty()) {
            return Promise.of(MasteryTransitionResult.failure(
                    transition.masteryId(),
                    MasteryState.UNKNOWN,
                    "tenantId is required for mastery transitions"
            ));
        }
        
        // First, find the current mastery item
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

                    // Build the updated mastery item first (before any persistence)
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
                            Instant.now(),
                            item.staleAfter(),
                            item.labels(),
                            item.stateHistory(),
                            item.confidence()
                    );

                    // Attempt to save the updated mastery item first (this is the critical state change)
                    return save(updatedItem)
                            .then(savedItem -> {
                                // Item saved successfully, now append transition (append-only, safe to retry)
                                return transitionRepository.append(transition)
                                        .map(savedTransition -> MasteryTransitionResult.success(
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
        // Deprecated method - throw exception to force migration to tenant-scoped version
        return Promise.ofException(new UnsupportedOperationException(
                "findStale(Instant) is deprecated. Use findStale(tenantId, Instant) for tenant-scoped stale detection."));
    }

    @Override
    @NotNull
    public Promise<List<MasteryItem>> findStale(@NotNull String tenantId, @NotNull Instant now) {
        if (tenantId == null || tenantId.isEmpty()) {
            return Promise.ofException(new IllegalArgumentException("tenantId is required for stale detection"));
        }
        
        // Query all entities for the tenant and filter for stale items
        return entityRepository.count(tenantId, COLLECTION_MASTERY_ITEMS)
                .then(count -> {
                    int limit = count > 1000 ? 1000 : (int) count.longValue();
                    return entityRepository.findAll(tenantId, COLLECTION_MASTERY_ITEMS, 
                            Map.of(), null, 0, limit);
                })
                .then(entities -> {
                    List<MasteryItem> staleItems = entities.stream()
                            .map(e -> MasteryItemMapper.fromDataMap(e.getData()))
                            .filter(item -> !item.isFresh(now))
                            .collect(Collectors.toList());
                    return Promise.of(staleItems);
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
                    ExecutionMode.BLOCKED,
                    "tenantId is required for mastery decisions"
            ));
        }
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
                        // No matching mastery - return decision to block
                        String skillId = query.skillId() != null ? query.skillId() : "unknown";
                        return Promise.of(MasteryDecision.block(
                                "unknown",
                                skillId,
                                ExecutionMode.BLOCKED,
                                "No matching mastery item found"
                        ));
                    }

                    // Get the best matching item (highest execution score)
                    MasteryItem best = matches.stream()
                            .max(Comparator.comparingDouble(item -> item.score().executionScore()))
                            .orElse(matches.get(0));

                    // Determine execution mode based on mastery state
                    ExecutionMode mode = determineExecutionMode(best);

                    // Determine if human approval or verification is required
                    boolean requiresHumanApproval = best.state() == MasteryState.PRACTICED;
                    boolean requiresVerification = best.state() == MasteryState.COMPETENT;

                    if (requiresHumanApproval) {
                        return Promise.of(MasteryDecision.requireApproval(
                                best.masteryId(),
                                best.skillId(),
                                mode,
                                "Mastery state is PRACTICED - requires human approval"
                        ));
                    }

                    if (requiresVerification) {
                        return Promise.of(MasteryDecision.requireVerification(
                                best.masteryId(),
                                best.skillId(),
                                mode,
                                "Mastery state is COMPETENT - requires verification",
                                best.evidenceRefs()
                        ));
                    }

                    return Promise.of(MasteryDecision.allow(
                            best.masteryId(),
                            best.skillId(),
                            mode,
                            "Mastery state is " + best.state() + " - execution allowed"
                    ));
                });
    }

    /**
     * Determines the execution mode based on mastery state.
     *
     * @param item mastery item
     * @return execution mode
     */
    private ExecutionMode determineExecutionMode(MasteryItem item) {
        return switch (item.state()) {
            case MASTERED -> ExecutionMode.DETERMINISTIC_EXECUTION;
            case COMPETENT -> ExecutionMode.BOUNDED_PROBABILISTIC_REASONING;
            case PRACTICED -> ExecutionMode.EXPLORATORY_FAST_LEARNING;
            case OBSERVED -> ExecutionMode.VERIFICATION_FIRST;
            case MAINTENANCE_ONLY -> ExecutionMode.MAINTENANCE_ONLY;
            case OBSOLETE, RETIRED, QUARANTINED, UNKNOWN -> ExecutionMode.BLOCKED;
        };
    }

}
