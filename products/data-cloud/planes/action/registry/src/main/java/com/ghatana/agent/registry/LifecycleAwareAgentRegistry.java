/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.registry;

import com.ghatana.contracts.agent.v1.AgentManifestProto;
import com.ghatana.platform.domain.auth.TenantId;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lifecycle-Aware Agent Registry (P4-04).
 *
 * <p>P4-04: Makes pattern/agent registry versioned and lifecycle-aware:
 * <ul>
 *   <li>Agent definition persistence</li>
 *   <li>Version management with rollback support</li>
 *   <li>Lifecycle status tracking (DRAFT, ACTIVE, RETIRED)</li>
 *   <li>Owner and tenant isolation</li>
 *   <li>Validation result storage</li>
 *   <li>Activation and retirement timestamps</li>
 *   <li>Learning feedback references</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Versioned and lifecycle-aware agent registry
 * @doc.layer product
 * @doc.pattern Registry, Repository
 */
public class LifecycleAwareAgentRegistry {

    private final AgentRepository repository;
    private final ValidationResultStore validationStore;
    private final LearningFeedbackStore feedbackStore;

    // In-memory cache of active agents
    private final Map<String, RegisteredAgent> agentCache = new ConcurrentHashMap<>();

    public LifecycleAwareAgentRegistry(
            AgentRepository repository,
            ValidationResultStore validationStore,
            LearningFeedbackStore feedbackStore) {
        this.repository = repository;
        this.validationStore = validationStore;
        this.feedbackStore = feedbackStore;
    }

    // ==================== Registration ====================

    /**
     * Register a new agent with initial DRAFT lifecycle state.
     *
     * @param tenantId tenant identifier
     * @param owner owner of the agent
     * @param manifest agent manifest
     * @param validationResult validation result
     * @return registered agent metadata
     */
    public Promise<RegisteredAgent> register(
            TenantId tenantId,
            String owner,
            AgentManifestProto manifest,
            AgentValidationResult validationResult) {

        String agentId = manifest.getMetadata().getId();
        String version = manifest.getMetadata().getVersion();

        RegisteredAgent agent = new RegisteredAgent(
            agentId,
            tenantId.value(),
            owner,
            version,
            manifest,
            LifecycleState.DRAFT,
            validationResult,
            Instant.now(),
            null,
            null,
            List.of()
        );

        // Persist to repository
        return repository.save(agent)
            .then(v -> validationStore.save(agentId, version, validationResult))
            .map(v -> {
                agentCache.put(cacheKey(tenantId.value(), agentId), agent);
                return agent;
            });
    }

    /**
     * Register a new version of an existing agent.
     *
     * @param tenantId tenant identifier
     * @param owner owner of the agent
     * @param manifest agent manifest with new version
     * @param validationResult validation result
     * @return registered agent metadata
     */
    public Promise<RegisteredAgent> registerVersion(
            TenantId tenantId,
            String owner,
            AgentManifestProto manifest,
            AgentValidationResult validationResult) {

        String agentId = manifest.getMetadata().getId();
        String newVersion = manifest.getMetadata().getVersion();

        // Get current active version for rollback reference
        return repository.findById(tenantId.value(), agentId)
            .then(optCurrent -> {
                String rollbackVersion = optCurrent
                    .filter(a -> a.state() == LifecycleState.ACTIVE)
                    .map(RegisteredAgent::version)
                    .orElse(null);

                RegisteredAgent newAgent = new RegisteredAgent(
                    agentId,
                    tenantId.value(),
                    owner,
                    newVersion,
                    manifest,
                    LifecycleState.DRAFT,
                    validationResult,
                    Instant.now(),
                    null,
                    rollbackVersion,
                    List.of()
                );

                return repository.save(newAgent)
                    .then(v -> validationStore.save(agentId, newVersion, validationResult))
                    .map(v -> newAgent);
            });
    }

    // ==================== Lifecycle Management ====================

    /**
     * Activate an agent (transition from DRAFT to ACTIVE).
     *
     * @param tenantId tenant identifier
     * @param agentId agent identifier
     * @param actor user or system activating the agent
     * @return updated registered agent
     */
    public Promise<RegisteredAgent> activate(TenantId tenantId, String agentId, String actor) {
        String tenantValue = tenantId.value();
        return repository.findById(tenantValue, agentId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.ofException(new IllegalArgumentException("Agent not found: " + agentId));
                }
                RegisteredAgent agent = opt.get();
                if (agent.state() != LifecycleState.DRAFT && agent.state() != LifecycleState.RETIRED) {
                    return Promise.ofException(new IllegalStateException(
                        "Cannot activate agent in state: " + agent.state()));
                }

                RegisteredAgent activated = agent.withState(
                    LifecycleState.ACTIVE,
                    Instant.now()
                );

                return repository.save(activated)
                    .then(v -> repository.recordLifecycleEvent(
                        tenantValue, agentId, "ACTIVATED", actor, Map.of()))
                    .map(v -> {
                        agentCache.put(cacheKey(tenantValue, agentId), activated);
                        return activated;
                    });
            });
    }

    /**
     * Retire an agent (transition from ACTIVE to RETIRED).
     *
     * @param tenantId tenant identifier
     * @param agentId agent identifier
     * @param actor user or system retiring the agent
     * @return updated registered agent
     */
    public Promise<RegisteredAgent> retire(TenantId tenantId, String agentId, String actor) {
        String tenantValue = tenantId.value();
        return repository.findById(tenantValue, agentId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.ofException(new IllegalArgumentException("Agent not found: " + agentId));
                }
                RegisteredAgent agent = opt.get();
                if (agent.state() != LifecycleState.ACTIVE) {
                    return Promise.ofException(new IllegalStateException(
                        "Cannot retire agent in state: " + agent.state()));
                }

                RegisteredAgent retired = agent.withState(LifecycleState.RETIRED, null);

                return repository.save(retired)
                    .then(v -> repository.recordLifecycleEvent(
                        tenantValue, agentId, "RETIRED", actor, Map.of()))
                    .map(v -> {
                        agentCache.remove(cacheKey(tenantValue, agentId));
                        return retired;
                    });
            });
    }

    /**
     * Rollback to a previous version of an agent.
     *
     * @param tenantId tenant identifier
     * @param agentId agent identifier
     * @param targetVersion version to rollback to
     * @param actor user or system performing rollback
     * @return rolled back agent
     */
    public Promise<RegisteredAgent> rollback(
            TenantId tenantId,
            String agentId,
            String targetVersion,
            String actor) {

        String tenantValue = tenantId.value();
        return repository.findVersion(tenantValue, agentId, targetVersion)
            .then(optTarget -> optTarget
                .map(target -> {
                    // Get current version for reference
                    return repository.findById(tenantValue, agentId)
                        .then(optCurrent -> {
                            String currentVersion = optCurrent.map(RegisteredAgent::version).orElse(null);

                            // Activate the target version
                            RegisteredAgent rolledBack = target.withState(
                                LifecycleState.ACTIVE,
                                Instant.now()
                            ).withRollbackVersion(currentVersion);

                            return repository.save(rolledBack)
                                .then(v -> repository.recordLifecycleEvent(
                                    tenantValue, agentId, "ROLLED_BACK", actor,
                                    Map.of("fromVersion", currentVersion, "toVersion", targetVersion)))
                                .map(v -> {
                                    agentCache.put(cacheKey(tenantValue, agentId), rolledBack);
                                    return rolledBack;
                                });
                        });
                })
                .orElseGet(() -> Promise.ofException(
                    new IllegalArgumentException("Target version not found: " + targetVersion))));
    }

    // ==================== Query Operations ====================

    /**
     * Get an agent by ID.
     */
    public Promise<Optional<RegisteredAgent>> get(TenantId tenantId, String agentId) {
        String key = cacheKey(tenantId.value(), agentId);
        RegisteredAgent cached = agentCache.get(key);
        if (cached != null) {
            return Promise.of(Optional.of(cached));
        }
        return repository.findById(tenantId.value(), agentId);
    }

    /**
     * Get a specific version of an agent.
     */
    public Promise<Optional<RegisteredAgent>> getVersion(
            TenantId tenantId,
            String agentId,
            String version) {
        return repository.findVersion(tenantId.value(), agentId, version);
    }

    /**
     * List all agents for a tenant.
     */
    public Promise<List<RegisteredAgent>> list(TenantId tenantId) {
        return repository.findByTenant(tenantId.value());
    }

    /**
     * List agents by lifecycle state.
     */
    public Promise<List<RegisteredAgent>> listByState(TenantId tenantId, LifecycleState state) {
        return repository.findByTenantAndState(tenantId.value(), state);
    }

    /**
     * Get all versions of an agent.
     */
    public Promise<List<RegisteredAgent>> getAllVersions(TenantId tenantId, String agentId) {
        return repository.findAllVersions(tenantId.value(), agentId);
    }

    // ==================== Learning Feedback ====================

    /**
     * Add learning feedback reference to an agent.
     */
    public Promise<Void> addLearningFeedback(
            TenantId tenantId,
            String agentId,
            String feedbackId,
            Map<String, Object> feedbackData) {

        return repository.findById(tenantId.value(), agentId)
            .then(opt -> opt
                .map(a -> {
                    List<String> updatedFeedback = new java.util.ArrayList<>(a.learningFeedbackRefs());
                    updatedFeedback.add(feedbackId);

                    RegisteredAgent updated = a.withLearningFeedback(updatedFeedback);

                    return repository.save(updated)
                        .then(v -> feedbackStore.save(agentId, feedbackId, feedbackData));
                })
                .orElseGet(() -> Promise.ofException(
                    new IllegalArgumentException("Agent not found: " + agentId))));
    }

    // ==================== Utility Methods ====================

    private String cacheKey(String tenantId, String agentId) {
        return tenantId + ":" + agentId;
    }

    // ==================== Supporting Types ====================

    /**
     * Registered agent with full lifecycle metadata.
     */
    public record RegisteredAgent(
        String agentId,
        String tenantId,
        String owner,
        String version,
        AgentManifestProto manifest,
        LifecycleState state,
        AgentValidationResult validationResult,
        Instant registrationTime,
        Instant activationTime,
        String rollbackVersion,
        List<String> learningFeedbackRefs
    ) {
        public RegisteredAgent {
            learningFeedbackRefs = List.copyOf(learningFeedbackRefs != null ? learningFeedbackRefs : List.of());
        }

        public RegisteredAgent withState(LifecycleState newState, Instant activationTime) {
            return new RegisteredAgent(
                agentId, tenantId, owner, version, manifest, newState,
                validationResult, registrationTime, activationTime, rollbackVersion, learningFeedbackRefs
            );
        }

        public RegisteredAgent withRollbackVersion(String rollbackVersion) {
            return new RegisteredAgent(
                agentId, tenantId, owner, version, manifest, state,
                validationResult, registrationTime, activationTime, rollbackVersion, learningFeedbackRefs
            );
        }

        public RegisteredAgent withLearningFeedback(List<String> feedbackRefs) {
            return new RegisteredAgent(
                agentId, tenantId, owner, version, manifest, state,
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
     * Agent validation result.
     */
    public record AgentValidationResult(
        boolean valid,
        List<String> errors,
        List<String> warnings
    ) {
        public AgentValidationResult {
            errors = List.copyOf(errors != null ? errors : List.of());
            warnings = List.copyOf(warnings != null ? warnings : List.of());
        }

        public static AgentValidationResult validResult() {
            return new AgentValidationResult(true, List.of(), List.of());
        }

        public static AgentValidationResult invalid(List<String> errors) {
            return new AgentValidationResult(false, errors, List.of());
        }

        public static AgentValidationResult invalid(List<String> errors, List<String> warnings) {
            return new AgentValidationResult(false, errors, warnings);
        }
    }

    // ==================== Repository Interfaces ====================

    public interface AgentRepository {
        Promise<Void> save(RegisteredAgent agent);
        Promise<Optional<RegisteredAgent>> findById(String tenantId, String agentId);
        Promise<Optional<RegisteredAgent>> findVersion(String tenantId, String agentId, String version);
        Promise<List<RegisteredAgent>> findByTenant(String tenantId);
        Promise<List<RegisteredAgent>> findByTenantAndState(String tenantId, LifecycleState state);
        Promise<List<RegisteredAgent>> findAllVersions(String tenantId, String agentId);
        Promise<Void> recordLifecycleEvent(String tenantId, String agentId, String event, String actor, Map<String, Object> metadata);
    }

    public interface ValidationResultStore {
        Promise<Void> save(String agentId, String version, AgentValidationResult result);
        Promise<Optional<AgentValidationResult>> find(String agentId, String version);
    }

    public interface LearningFeedbackStore {
        Promise<Void> save(String agentId, String feedbackId, Map<String, Object> feedbackData);
        Promise<List<Map<String, Object>>> findByAgent(String agentId);
    }
}
