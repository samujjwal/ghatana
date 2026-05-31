/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.registry;

import com.ghatana.aep.pattern.spec.PatternSpec;
import com.ghatana.aep.pattern.spec.PatternSpecValidationResult;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lifecycle-Aware Pattern Registry (P4-04).
 *
 * <p>P4-04: Makes pattern/agent registry versioned and lifecycle-aware:
 * <ul>
 *   <li>Pattern definition persistence</li>
 *   <li>Version management with rollback support</li>
 *   <li>Lifecycle status tracking (DRAFT, ACTIVE, RETIRED)</li>
 *   <li>Owner and tenant isolation</li>
 *   <li>Validation result storage</li>
 *   <li>Activation and retirement timestamps</li>
 *   <li>Learning feedback references</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Versioned and lifecycle-aware pattern registry
 * @doc.layer product
 * @doc.pattern Registry, Repository
 */
public class LifecycleAwarePatternRegistry {

    private final PatternRepository repository;
    private final ValidationResultStore validationStore;
    private final LearningFeedbackStore feedbackStore;

    // In-memory cache of active patterns
    private final Map<String, RegisteredPattern> patternCache = new ConcurrentHashMap<>();

    public LifecycleAwarePatternRegistry(
            PatternRepository repository,
            ValidationResultStore validationStore,
            LearningFeedbackStore feedbackStore) {
        this.repository = repository;
        this.validationStore = validationStore;
        this.feedbackStore = feedbackStore;
    }

    // ==================== Registration ====================

    /**
     * Register a new pattern with initial DRAFT lifecycle state.
     *
     * @param tenantId tenant identifier
     * @param owner owner of the pattern
     * @param spec pattern specification
     * @param validationResult validation result
     * @return registered pattern metadata
     */
    public Promise<RegisteredPattern> register(
            String tenantId,
            String owner,
            PatternSpec spec,
            PatternSpecValidationResult validationResult) {

        String patternId = spec.metadata().name();
        String version = spec.metadata().version();

        RegisteredPattern pattern = new RegisteredPattern(
            patternId,
            tenantId,
            owner,
            version,
            spec,
            LifecycleState.DRAFT,
            validationResult,
            Instant.now(),
            null,
            null,
            List.of()
        );

        // Persist to repository
        return repository.save(pattern)
            .then(v -> validationStore.save(patternId, version, validationResult))
            .map(v -> {
                patternCache.put(cacheKey(tenantId, patternId), pattern);
                return pattern;
            });
    }

    /**
     * Register a new version of an existing pattern.
     *
     * @param tenantId tenant identifier
     * @param owner owner of the pattern
     * @param spec pattern specification with new version
     * @param validationResult validation result
     * @return registered pattern metadata
     */
    public Promise<RegisteredPattern> registerVersion(
            String tenantId,
            String owner,
            PatternSpec spec,
            PatternSpecValidationResult validationResult) {

        String patternId = spec.metadata().name();
        String newVersion = spec.metadata().version();

        // Get current active version for rollback reference
        return repository.findById(tenantId, patternId)
            .then(optCurrent -> {
                String rollbackVersion = optCurrent
                    .filter(p -> p.state() == LifecycleState.ACTIVE)
                    .map(RegisteredPattern::version)
                    .orElse(null);

                RegisteredPattern newPattern = new RegisteredPattern(
                    patternId,
                    tenantId,
                    owner,
                    newVersion,
                    spec,
                    LifecycleState.DRAFT,
                    validationResult,
                    Instant.now(),
                    null,
                    rollbackVersion,
                    List.of()
                );

                return repository.save(newPattern)
                    .then(v -> validationStore.save(patternId, newVersion, validationResult))
                    .map(v -> newPattern);
            });
    }

    // ==================== Lifecycle Management ====================

    /**
     * Activate a pattern (transition from DRAFT to ACTIVE).
     *
     * @param tenantId tenant identifier
     * @param patternId pattern identifier
     * @param actor user or system activating the pattern
     * @return updated registered pattern
     */
    public Promise<RegisteredPattern> activate(String tenantId, String patternId, String actor) {
        return repository.findById(tenantId, patternId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.ofException(new IllegalArgumentException("Pattern not found: " + patternId));
                }
                RegisteredPattern pattern = opt.get();
                if (pattern.state() != LifecycleState.DRAFT && pattern.state() != LifecycleState.RETIRED) {
                    return Promise.ofException(new IllegalStateException(
                        "Cannot activate pattern in state: " + pattern.state()));
                }

                RegisteredPattern activated = pattern.withState(
                    LifecycleState.ACTIVE,
                    Instant.now()
                );

                return repository.save(activated)
                    .then(v -> repository.recordLifecycleEvent(
                        tenantId, patternId, "ACTIVATED", actor, Map.of()))
                    .map(v -> {
                        patternCache.put(cacheKey(tenantId, patternId), activated);
                        return activated;
                    });
            });
    }

    /**
     * Retire a pattern (transition from ACTIVE to RETIRED).
     *
     * @param tenantId tenant identifier
     * @param patternId pattern identifier
     * @param actor user or system retiring the pattern
     * @return updated registered pattern
     */
    public Promise<RegisteredPattern> retire(String tenantId, String patternId, String actor) {
        return repository.findById(tenantId, patternId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.ofException(new IllegalArgumentException("Pattern not found: " + patternId));
                }
                RegisteredPattern pattern = opt.get();
                if (pattern.state() != LifecycleState.ACTIVE) {
                    return Promise.ofException(new IllegalStateException(
                        "Cannot retire pattern in state: " + pattern.state()));
                }

                RegisteredPattern retired = pattern.withState(LifecycleState.RETIRED, null);

                return repository.save(retired)
                    .then(v -> repository.recordLifecycleEvent(
                        tenantId, patternId, "RETIRED", actor, Map.of()))
                    .map(v -> {
                        patternCache.remove(cacheKey(tenantId, patternId));
                        return retired;
                    });
            });
    }

    /**
     * Rollback to a previous version of a pattern.
     *
     * @param tenantId tenant identifier
     * @param patternId pattern identifier
     * @param targetVersion version to rollback to
     * @param actor user or system performing rollback
     * @return rolled back pattern
     */
    public Promise<RegisteredPattern> rollback(
            String tenantId,
            String patternId,
            String targetVersion,
            String actor) {

        return repository.findVersion(tenantId, patternId, targetVersion)
            .then(optTarget -> optTarget
                .map(target -> {
                    // Get current version for reference
                    return repository.findById(tenantId, patternId)
                        .then(optCurrent -> {
                            String currentVersion = optCurrent.map(RegisteredPattern::version).orElse(null);

                            // Activate the target version
                            RegisteredPattern rolledBack = target.withState(
                                LifecycleState.ACTIVE,
                                Instant.now()
                            ).withRollbackVersion(currentVersion);

                            return repository.save(rolledBack)
                                .then(v -> repository.recordLifecycleEvent(
                                    tenantId, patternId, "ROLLED_BACK", actor,
                                    Map.of("fromVersion", currentVersion, "toVersion", targetVersion)))
                                .map(v -> {
                                    patternCache.put(cacheKey(tenantId, patternId), rolledBack);
                                    return rolledBack;
                                });
                        });
                })
                .orElseGet(() -> Promise.ofException(
                    new IllegalArgumentException("Target version not found: " + targetVersion))));
    }

    // ==================== Query Operations ====================

    /**
     * Get a pattern by ID.
     */
    public Promise<Optional<RegisteredPattern>> get(String tenantId, String patternId) {
        String key = cacheKey(tenantId, patternId);
        RegisteredPattern cached = patternCache.get(key);
        if (cached != null) {
            return Promise.of(Optional.of(cached));
        }
        return repository.findById(tenantId, patternId);
    }

    /**
     * Get a specific version of a pattern.
     */
    public Promise<Optional<RegisteredPattern>> getVersion(
            String tenantId,
            String patternId,
            String version) {
        return repository.findVersion(tenantId, patternId, version);
    }

    /**
     * List all patterns for a tenant.
     */
    public Promise<List<RegisteredPattern>> list(String tenantId) {
        return repository.findByTenant(tenantId);
    }

    /**
     * List patterns by lifecycle state.
     */
    public Promise<List<RegisteredPattern>> listByState(String tenantId, LifecycleState state) {
        return repository.findByTenantAndState(tenantId, state);
    }

    /**
     * Get all versions of a pattern.
     */
    public Promise<List<RegisteredPattern>> getAllVersions(String tenantId, String patternId) {
        return repository.findAllVersions(tenantId, patternId);
    }

    // ==================== Learning Feedback ====================

    /**
     * Add learning feedback reference to a pattern.
     */
    public Promise<Void> addLearningFeedback(
            String tenantId,
            String patternId,
            String feedbackId,
            Map<String, Object> feedbackData) {

        return repository.findById(tenantId, patternId)
            .then(opt -> opt
                .map(p -> {
                    List<String> updatedFeedback = new java.util.ArrayList<>(p.learningFeedbackRefs());
                    updatedFeedback.add(feedbackId);

                    RegisteredPattern updated = p.withLearningFeedback(updatedFeedback);

                    return repository.save(updated)
                        .then(v -> feedbackStore.save(patternId, feedbackId, feedbackData));
                })
                .orElseGet(() -> Promise.ofException(
                    new IllegalArgumentException("Pattern not found: " + patternId))));
    }

    // ==================== Utility Methods ====================

    private String cacheKey(String tenantId, String patternId) {
        return tenantId + ":" + patternId;
    }

    // ==================== Supporting Types ====================

    /**
     * Registered pattern with full lifecycle metadata.
     */
    public record RegisteredPattern(
        String patternId,
        String tenantId,
        String owner,
        String version,
        PatternSpec spec,
        LifecycleState state,
        PatternSpecValidationResult validationResult,
        Instant registrationTime,
        Instant activationTime,
        String rollbackVersion,
        List<String> learningFeedbackRefs
    ) {
        public RegisteredPattern {
            learningFeedbackRefs = List.copyOf(learningFeedbackRefs != null ? learningFeedbackRefs : List.of());
        }

        public RegisteredPattern withState(LifecycleState newState, Instant activationTime) {
            return new RegisteredPattern(
                patternId, tenantId, owner, version, spec, newState,
                validationResult, registrationTime, activationTime, rollbackVersion, learningFeedbackRefs
            );
        }

        public RegisteredPattern withRollbackVersion(String rollbackVersion) {
            return new RegisteredPattern(
                patternId, tenantId, owner, version, spec, state,
                validationResult, registrationTime, activationTime, rollbackVersion, learningFeedbackRefs
            );
        }

        public RegisteredPattern withLearningFeedback(List<String> feedbackRefs) {
            return new RegisteredPattern(
                patternId, tenantId, owner, version, spec, state,
                validationResult, registrationTime, activationTime, rollbackVersion, feedbackRefs
            );
        }

        public boolean isActive() {
            return state == LifecycleState.ACTIVE;
        }
    }

    public enum LifecycleState {
        DRAFT,      // Initial state, not yet active
        ACTIVE,     // Currently in use
        RETIRED,    // No longer in use but preserved for history
        ARCHIVED    // Archived for compliance
    }

    // ==================== Repository Interfaces ====================

    public interface PatternRepository {
        Promise<Void> save(RegisteredPattern pattern);
        Promise<Optional<RegisteredPattern>> findById(String tenantId, String patternId);
        Promise<Optional<RegisteredPattern>> findVersion(String tenantId, String patternId, String version);
        Promise<List<RegisteredPattern>> findByTenant(String tenantId);
        Promise<List<RegisteredPattern>> findByTenantAndState(String tenantId, LifecycleState state);
        Promise<List<RegisteredPattern>> findAllVersions(String tenantId, String patternId);
        Promise<Void> recordLifecycleEvent(String tenantId, String patternId, String event, String actor, Map<String, Object> metadata);
    }

    public interface ValidationResultStore {
        Promise<Void> save(String patternId, String version, PatternSpecValidationResult result);
        Promise<Optional<PatternSpecValidationResult>> find(String patternId, String version);
    }

    public interface LearningFeedbackStore {
        Promise<Void> save(String patternId, String feedbackId, Map<String, Object> feedbackData);
        Promise<List<Map<String, Object>>> findByPattern(String patternId);
    }
}
