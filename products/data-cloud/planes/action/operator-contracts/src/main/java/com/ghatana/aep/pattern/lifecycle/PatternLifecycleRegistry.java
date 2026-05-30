package com.ghatana.aep.pattern.lifecycle;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Tracks PatternSpec lifecycle state and auditable transition events.
 *
 * <p>Production use requires a durable {@link PatternLifecycleRepository} for
 * state and event persistence. In-memory mode is only for local development and
 * deterministic unit tests.
 *
 * @doc.type class
 * @doc.purpose Maintains governed lifecycle state so promotion decisions are validated against stored state
 * @doc.layer product
 * @doc.pattern Registry
 */
public final class PatternLifecycleRegistry {

    private static final Logger log = LoggerFactory.getLogger(PatternLifecycleRegistry.class);

    private final PatternLifecycleService lifecycleService;
    private final Optional<PatternLifecycleRepository> repository;
    private final boolean useInMemoryMode;

    // In-memory fallback for local development and unit tests
    private final java.util.concurrent.ConcurrentHashMap<PatternKey, PatternLifecycleState> inMemoryStates = 
        new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<PatternKey, List<PatternLifecycleEvent>> inMemoryEvents = 
        new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Creates a registry with durable repository (production mode).
     *
     * @param lifecycleService lifecycle service for transition validation
     * @param repository durable repository for state and event storage
     */
    public PatternLifecycleRegistry(
            PatternLifecycleService lifecycleService,
            PatternLifecycleRepository repository) {
        this.lifecycleService = Objects.requireNonNull(lifecycleService, "lifecycleService");
        this.repository = Optional.ofNullable(repository);
        this.useInMemoryMode = repository == null;
        
        if (useInMemoryMode) {
            log.warn("[pattern-lifecycle] Using in-memory mode - state is not durable");
        }
    }

    /**
     * Creates a registry with in-memory storage (non-production only).
     *
     * @param lifecycleService lifecycle service for transition validation
     * @deprecated Use {@link #PatternLifecycleRegistry(PatternLifecycleService, PatternLifecycleRepository)}
     *             for production with durable storage
     */
    @Deprecated
    public PatternLifecycleRegistry(PatternLifecycleService lifecycleService) {
        this(lifecycleService, null);
    }

    /**
     * Initialize a pattern in DRAFT state.
     *
     * @param tenantId tenant identifier
     * @param patternId pattern identifier
     * @return promise completing when initialized
     */
    public Promise<Void> initializeDraft(String tenantId, String patternId) {
        PatternKey key = new PatternKey(tenantId, patternId);
        
        if (useInMemoryMode) {
            if (inMemoryStates.containsKey(key)) {
                return Promise.ofException(new IllegalStateException(
                    "Pattern lifecycle already exists: " + patternId));
            }
            inMemoryStates.put(key, PatternLifecycleState.DRAFT);
            inMemoryEvents.put(key, new java.util.concurrent.CopyOnWriteArrayList<>());
            return Promise.complete();
        }
        
        return repository.get().getState(tenantId, patternId)
            .then(existingState -> {
                if (existingState.isPresent()) {
                    return Promise.ofException(new IllegalStateException(
                        "Pattern lifecycle already exists: " + patternId));
                }
                return repository.get().saveState(tenantId, patternId, PatternLifecycleState.DRAFT);
            });
    }

    /**
     * Execute a lifecycle transition.
     *
     * @param transition transition request
     * @return promise of lifecycle event
     */
    public Promise<PatternLifecycleEvent> transition(PatternLifecycleTransition transition) {
        PatternKey key = new PatternKey(transition.tenantId(), transition.patternId());
        
        if (useInMemoryMode) {
            PatternLifecycleState currentState = inMemoryStates.get(key);
            if (currentState == null) {
                return Promise.ofException(new IllegalStateException(
                    "Pattern lifecycle does not exist: " + transition.patternId()));
            }
            if (currentState != transition.from()) {
                return Promise.ofException(new IllegalArgumentException(
                    "Pattern lifecycle state mismatch for " + transition.patternId()
                        + ": expected " + currentState + " but transition declared " + transition.from()));
            }

            PatternLifecycleEvent event = lifecycleService.transition(transition);
            inMemoryStates.put(key, transition.to());
            inMemoryEvents.computeIfAbsent(key, ignored -> new java.util.concurrent.CopyOnWriteArrayList<>())
                .add(event);
            return Promise.of(event);
        }
        
        return repository.get().getState(transition.tenantId(), transition.patternId())
            .then(currentStateOpt -> {
                PatternLifecycleState currentState = currentStateOpt.orElse(null);
                if (currentState == null) {
                    return Promise.ofException(new IllegalStateException(
                        "Pattern lifecycle does not exist: " + transition.patternId()));
                }
                if (currentState != transition.from()) {
                    return Promise.ofException(new IllegalArgumentException(
                        "Pattern lifecycle state mismatch for " + transition.patternId()
                            + ": expected " + currentState + " but transition declared " + transition.from()));
                }

                PatternLifecycleEvent event = lifecycleService.transition(transition);
                return repository.get().saveState(transition.tenantId(), transition.patternId(), transition.to())
                    .then(() -> repository.get().saveEvent(event))
                    .map(ignored -> event);
            });
    }

    /**
     * Get current lifecycle state for a pattern.
     *
     * @param tenantId tenant identifier
     * @param patternId pattern identifier
     * @return promise of current state (empty if not found)
     */
    public Promise<Optional<PatternLifecycleState>> currentState(String tenantId, String patternId) {
        if (useInMemoryMode) {
            return Promise.of(Optional.ofNullable(inMemoryStates.get(new PatternKey(tenantId, patternId))));
        }
        return repository.get().getState(tenantId, patternId);
    }

    /**
     * Get lifecycle events for a pattern.
     *
     * @param tenantId tenant identifier
     * @param patternId pattern identifier
     * @return promise of lifecycle events (empty list if none)
     */
    public Promise<List<PatternLifecycleEvent>> events(String tenantId, String patternId) {
        if (useInMemoryMode) {
            return Promise.of(List.copyOf(
                inMemoryEvents.getOrDefault(new PatternKey(tenantId, patternId), List.of())));
        }
        return repository.get().getEvents(tenantId, patternId);
    }

    /**
     * Delete lifecycle state and events for a pattern.
     *
     * @param tenantId tenant identifier
     * @param patternId pattern identifier
     * @return promise completing when deleted
     */
    public Promise<Void> delete(String tenantId, String patternId) {
        PatternKey key = new PatternKey(tenantId, patternId);
        
        if (useInMemoryMode) {
            inMemoryStates.remove(key);
            inMemoryEvents.remove(key);
            return Promise.complete();
        }
        
        return repository.get().delete(tenantId, patternId);
    }

    /**
     * Check if this registry is using in-memory mode (non-production).
     *
     * @return true if using in-memory storage
     */
    public boolean isInMemoryMode() {
        return useInMemoryMode;
    }

    private record PatternKey(String tenantId, String patternId) {
        private PatternKey {
            tenantId = requireText(tenantId, "tenantId");
            patternId = requireText(patternId, "patternId");
        }

        private static String requireText(String value, String fieldName) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(fieldName + " must not be blank");
            }
            return value;
        }
    }
}
