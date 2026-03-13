/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Domain — AggregateRoot Base Pattern
 */
package com.ghatana.products.yappc.domain;

import com.ghatana.yappc.api.events.DomainEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for all YAPPC aggregate roots.
 *
 * <p>An aggregate root is the entry point into an aggregate (cluster of related entities).
 * This base class provides the infrastructure for collecting and flushing domain events
 * that are raised during aggregate state transitions.
 *
 * <h2>Usage Pattern</h2>
 * <pre>{@code
 * public class ProjectEntity extends AggregateRoot<String> {
 *
 *     public void advanceStage(PhaseType target, TransitionPolicy policy) {
 *         policy.validate(this.currentPhase, target);
 *         this.currentPhase = target;
 *         raiseEvent(new PhaseAdvancedEvent(this.id, this.tenantId, target));
 *     }
 * }
 *
 * // Caller
 * ProjectEntity project = projectRepository.findById(id);
 * project.advanceStage(PhaseType.SHAPE, transitionPolicy);
 * List<DomainEvent> events = project.flushEvents();
 * eventPublisher.publishAll(events);
 * projectRepository.save(project);
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * The uncommitted-event list is NOT thread-safe. Aggregate roots are intended to be
 * used within a single request/command context and should not be shared across threads.
 *
 * @param <ID> the type of the aggregate's unique identifier
 * @doc.type class
 * @doc.purpose Base class for domain aggregate roots — collects and flushes domain events
 * @doc.layer domain
 * @doc.pattern Aggregate Root / Domain-Driven Design
 */
public abstract class AggregateRoot<ID> implements Identifiable<ID> {

    /** Uncommitted domain events raised during the current unit of work. */
    private final List<DomainEvent> uncommittedEvents = new ArrayList<>();

    /**
     * Returns the unique identifier of this aggregate root.
     * Subclasses must implement this to expose their specific ID type.
     *
     * @return the aggregate's unique identifier, never null
     */
    public abstract ID getId();

    /**
     * Registers a domain event to be published when this aggregate is saved.
     *
     * <p>Events are held in memory until explicitly flushed by calling {@link #flushEvents()}.
     * The caller (typically a repository or application service) is responsible for
     * flushing and publishing the events after the aggregate is persisted.
     *
     * @param event the domain event to register, must not be null
     */
    protected void raiseEvent(DomainEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Domain event must not be null");
        }
        uncommittedEvents.add(event);
    }

    /**
     * Flushes and returns all uncommitted events accumulated since the last flush.
     *
     * <p>After this call, the internal event list is cleared. This method is typically
     * called by the persistence layer immediately after saving the aggregate to ensure
     * events are published exactly once per state transition.
     *
     * @return an immutable snapshot of the uncommitted events (may be empty, never null)
     */
    public List<DomainEvent> flushEvents() {
        List<DomainEvent> events = List.copyOf(uncommittedEvents);
        uncommittedEvents.clear();
        return events;
    }

    /**
     * Returns the number of uncommitted events pending flush.
     * Useful for testing and debugging.
     *
     * @return count of pending domain events
     */
    public int pendingEventCount() {
        return uncommittedEvents.size();
    }

    /**
     * Returns whether this aggregate has uncommitted events pending publication.
     *
     * @return true if there are pending domain events
     */
    public boolean hasPendingEvents() {
        return !uncommittedEvents.isEmpty();
    }
}
