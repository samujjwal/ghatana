/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.registry;

import com.ghatana.aep.action.ActionSpec;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lifecycle-Aware Action Registry (WS2-9).
 *
 * <p>WS2-9: Stores action definitions as metadata only, without embedding runtime
 * execution semantics into registry persistence:
 * <ul>
 *   <li>Action definition persistence (metadata only)</li>
 *   <li>Version management with rollback support</li>
 *   <li>Lifecycle status tracking (DRAFT, ACTIVE, RETIRED)</li>
 *   <li>Owner and tenant isolation</li>
 *   <li>Validation result storage</li>
 *   <li>Activation and retirement timestamps</li>
 *   <li>Learning feedback references</li>
 * </ul>
 *
 * <p>Runtime execution semantics (execution state, side effects, compensation) are
 * handled by the ActionRun orchestrator and AgentRun entities, not by this registry.
 *
 * @doc.type class
 * @doc.purpose Metadata-only action registry without runtime execution semantics
 * @doc.layer product
 * @doc.pattern Registry, Repository
 */
public class LifecycleAwareActionRegistry {

    private final ActionRepository repository;
    private final ValidationResultStore validationStore;
    private final LearningFeedbackStore feedbackStore;

    // In-memory cache of active actions
    private final Map<String, RegisteredAction> actionCache = new ConcurrentHashMap<>();

    public LifecycleAwareActionRegistry(
            ActionRepository repository,
            ValidationResultStore validationStore,
            LearningFeedbackStore feedbackStore) {
        this.repository = repository;
        this.validationStore = validationStore;
        this.feedbackStore = feedbackStore;
    }

    // ==================== Registration ====================

    /**
     * Register a new action with initial DRAFT lifecycle state.
     *
     * <p>WS2-9: Stores only action definition metadata. Runtime execution semantics
     * are not embedded in the registry persistence.
     *
     * @param tenantId tenant identifier
     * @param owner owner of the action
     * @param spec action specification (metadata only)
     * @param validationResult validation result
     * @return registered action metadata
     */
    public Promise<RegisteredAction> register(
            String tenantId,
            String owner,
            ActionSpec spec,
            ActionValidationResult validationResult) {

        String actionId = spec.metadata().name();
        String version = spec.metadata().version();

        RegisteredAction action = new RegisteredAction(
            actionId,
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
        return repository.save(action)
            .then(v -> validationStore.save(actionId, version, validationResult))
            .map(v -> {
                actionCache.put(cacheKey(tenantId, actionId), action);
                return action;
            });
    }

    /**
     * Register a new version of an existing action.
     *
     * @param tenantId tenant identifier
     * @param owner owner of the action
     * @param spec action specification with new version
     * @param validationResult validation result
     * @return registered action metadata
     */
    public Promise<RegisteredAction> registerVersion(
            String tenantId,
            String owner,
            ActionSpec spec,
            ActionValidationResult validationResult) {

        String actionId = spec.metadata().name();
        String newVersion = spec.metadata().version();

        // Get current active version for rollback reference
        return repository.findById(tenantId, actionId)
            .then(optCurrent -> {
                String rollbackVersion = optCurrent
                    .filter(a -> a.state() == LifecycleState.ACTIVE)
                    .map(RegisteredAction::version)
                    .orElse(null);

                RegisteredAction newAction = new RegisteredAction(
                    actionId,
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

                return repository.save(newAction)
                    .then(v -> validationStore.save(actionId, newVersion, validationResult))
                    .map(v -> newAction);
            });
    }

    // ==================== Lifecycle Management ====================

    /**
     * Activate an action (transition from DRAFT to ACTIVE).
     *
     * @param tenantId tenant identifier
     * @param actionId action identifier
     * @param actor user or system activating the action
     * @return updated registered action
     */
    public Promise<RegisteredAction> activate(String tenantId, String actionId, String actor) {
        return repository.findById(tenantId, actionId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.ofException(new IllegalArgumentException("Action not found: " + actionId));
                }
                RegisteredAction action = opt.get();
                if (action.state() != LifecycleState.DRAFT && action.state() != LifecycleState.RETIRED) {
                    return Promise.ofException(new IllegalStateException(
                        "Cannot activate action in state: " + action.state()));
                }

                RegisteredAction activated = action.withState(
                    LifecycleState.ACTIVE,
                    Instant.now()
                );

                return repository.save(activated)
                    .then(v -> repository.recordLifecycleEvent(
                        tenantId, actionId, "ACTIVATED", actor, Map.of()))
                    .map(v -> {
                        actionCache.put(cacheKey(tenantId, actionId), activated);
                        return activated;
                    });
            });
    }

    /**
     * Retire an action (transition from ACTIVE to RETIRED).
     *
     * @param tenantId tenant identifier
     * @param actionId action identifier
     * @param actor user or system retiring the action
     * @return updated registered action
     */
    public Promise<RegisteredAction> retire(String tenantId, String actionId, String actor) {
        return repository.findById(tenantId, actionId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.ofException(new IllegalArgumentException("Action not found: " + actionId));
                }
                RegisteredAction action = opt.get();
                if (action.state() != LifecycleState.ACTIVE) {
                    return Promise.ofException(new IllegalStateException(
                        "Cannot retire action in state: " + action.state()));
                }

                RegisteredAction retired = action.withState(LifecycleState.RETIRED, null);

                return repository.save(retired)
                    .then(v -> repository.recordLifecycleEvent(
                        tenantId, actionId, "RETIRED", actor, Map.of()))
                    .map(v -> {
                        actionCache.remove(cacheKey(tenantId, actionId));
                        return retired;
                    });
            });
    }

    /**
     * Rollback to a previous version of an action.
     *
     * @param tenantId tenant identifier
     * @param actionId action identifier
     * @param targetVersion version to rollback to
     * @param actor user or system performing rollback
     * @return rolled back action
     */
    public Promise<RegisteredAction> rollback(
            String tenantId,
            String actionId,
            String targetVersion,
            String actor) {

        return repository.findVersion(tenantId, actionId, targetVersion)
            .then(optTarget -> optTarget
                .map(target -> {
                    // Get current version for reference
                    return repository.findById(tenantId, actionId)
                        .then(optCurrent -> {
                            String currentVersion = optCurrent.map(RegisteredAction::version).orElse(null);

                            // Activate the target version
                            RegisteredAction rolledBack = target.withState(
                                LifecycleState.ACTIVE,
                                Instant.now()
                            ).withRollbackVersion(currentVersion);

                            return repository.save(rolledBack)
                                .then(v -> repository.recordLifecycleEvent(
                                    tenantId, actionId, "ROLLED_BACK", actor,
                                    Map.of("fromVersion", currentVersion, "toVersion", targetVersion)))
                                .map(v -> {
                                    actionCache.put(cacheKey(tenantId, actionId), rolledBack);
                                    return rolledBack;
                                });
                        });
                })
                .orElseGet(() -> Promise.ofException(
                    new IllegalArgumentException("Target version not found: " + targetVersion))));
    }

    // ==================== Query Operations ====================

    /**
     * Get an action by ID.
     */
    public Promise<Optional<RegisteredAction>> get(String tenantId, String actionId) {
        String key = cacheKey(tenantId, actionId);
        RegisteredAction cached = actionCache.get(key);
        if (cached != null) {
            return Promise.of(Optional.of(cached));
        }
        return repository.findById(tenantId, actionId);
    }

    /**
     * Get a specific version of an action.
     */
    public Promise<Optional<RegisteredAction>> getVersion(
            String tenantId,
            String actionId,
            String version) {
        return repository.findVersion(tenantId, actionId, version);
    }

    /**
     * List all actions for a tenant.
     */
    public Promise<List<RegisteredAction>> list(String tenantId) {
        return repository.findByTenant(tenantId);
    }

    /**
     * List actions by lifecycle state.
     */
    public Promise<List<RegisteredAction>> listByState(String tenantId, LifecycleState state) {
        return repository.findByTenantAndState(tenantId, state);
    }

    /**
     * Get all versions of an action.
     */
    public Promise<List<RegisteredAction>> getAllVersions(String tenantId, String actionId) {
        return repository.findAllVersions(tenantId, actionId);
    }

    // ==================== Learning Feedback ====================

    /**
     * Add learning feedback reference to an action.
     */
    public Promise<Void> addLearningFeedback(
            String tenantId,
            String actionId,
            String feedbackId,
            Map<String, Object> feedbackData) {

        return repository.findById(tenantId, actionId)
            .then(opt -> opt
                .map(a -> {
                    List<String> updatedFeedback = new java.util.ArrayList<>(a.learningFeedbackRefs());
                    updatedFeedback.add(feedbackId);

                    RegisteredAction updated = a.withLearningFeedback(updatedFeedback);

                    return repository.save(updated)
                        .then(v -> feedbackStore.save(actionId, feedbackId, feedbackData));
                })
                .orElseGet(() -> Promise.ofException(
                    new IllegalArgumentException("Action not found: " + actionId))));
    }

    // ==================== Utility Methods ====================

    private String cacheKey(String tenantId, String actionId) {
        return tenantId + ":" + actionId;
    }

    // ==================== Supporting Types ====================

    /**
     * Registered action with full lifecycle metadata.
     *
     * <p>WS2-9: Contains only action definition metadata. Runtime execution semantics
     * (execution state, side effects, compensation) are handled by ActionRun orchestrator.
     */
    public record RegisteredAction(
        String actionId,
        String tenantId,
        String owner,
        String version,
        ActionSpec spec,
        LifecycleState state,
        ActionValidationResult validationResult,
        Instant registrationTime,
        Instant activationTime,
        String rollbackVersion,
        List<String> learningFeedbackRefs
    ) {
        public RegisteredAction {
            learningFeedbackRefs = List.copyOf(learningFeedbackRefs != null ? learningFeedbackRefs : List.of());
        }

        public RegisteredAction withState(LifecycleState newState, Instant activationTime) {
            return new RegisteredAction(
                actionId, tenantId, owner, version, spec, newState,
                validationResult, registrationTime, activationTime, rollbackVersion, learningFeedbackRefs
            );
        }

        public RegisteredAction withRollbackVersion(String rollbackVersion) {
            return new RegisteredAction(
                actionId, tenantId, owner, version, spec, state,
                validationResult, registrationTime, activationTime, rollbackVersion, learningFeedbackRefs
            );
        }

        public RegisteredAction withLearningFeedback(List<String> feedbackRefs) {
            return new RegisteredAction(
                actionId, tenantId, owner, version, spec, state,
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

    /**
     * Action validation result.
     */
    public record ActionValidationResult(
        boolean valid,
        List<String> errors,
        List<String> warnings
    ) {
        public ActionValidationResult {
            errors = List.copyOf(errors != null ? errors : List.of());
            warnings = List.copyOf(warnings != null ? warnings : List.of());
        }

        public static ActionValidationResult validResult() {
            return new ActionValidationResult(true, List.of(), List.of());
        }

        public static ActionValidationResult invalid(List<String> errors) {
            return new ActionValidationResult(false, errors, List.of());
        }

        public static ActionValidationResult invalid(List<String> errors, List<String> warnings) {
            return new ActionValidationResult(false, errors, warnings);
        }
    }

    // ==================== Repository Interfaces ====================

    public interface ActionRepository {
        Promise<Void> save(RegisteredAction action);
        Promise<Optional<RegisteredAction>> findById(String tenantId, String actionId);
        Promise<Optional<RegisteredAction>> findVersion(String tenantId, String actionId, String version);
        Promise<List<RegisteredAction>> findByTenant(String tenantId);
        Promise<List<RegisteredAction>> findByTenantAndState(String tenantId, LifecycleState state);
        Promise<List<RegisteredAction>> findAllVersions(String tenantId, String actionId);
        Promise<Void> recordLifecycleEvent(String tenantId, String actionId, String event, String actor, Map<String, Object> metadata);
    }

    public interface ValidationResultStore {
        Promise<Void> save(String actionId, String version, ActionValidationResult result);
        Promise<Optional<ActionValidationResult>> find(String actionId, String version);
    }

    public interface LearningFeedbackStore {
        Promise<Void> save(String actionId, String feedbackId, Map<String, Object> feedbackData);
        Promise<List<Map<String, Object>>> findByAction(String actionId);
    }
}
