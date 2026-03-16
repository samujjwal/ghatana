/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Domain — Domain Event Registry
 */
package com.ghatana.yappc.api.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry of all concrete {@link DomainEvent} types in YAPPC.
 *
 * <p>Events are registered explicitly at application startup (typically in the service module's
 * {@code onStart()} hook or a dedicated bootstrap class). The registry validates that every
 * registered event type has a valid schema version (≥&nbsp;1) and logs a summary on startup.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // In service bootstrap or DI module:
 * DomainEventRegistry.INSTANCE.register(ProjectCreatedEvent.class);
 * DomainEventRegistry.INSTANCE.register(ProjectStageAdvancedEvent.class);
 * DomainEventRegistry.INSTANCE.validate(); // throws if any event is invalid
 * }</pre>
 *
 * <h2>Validation Rules</h2>
 * <ul>
 *   <li>Schema version must be ≥ 1 (a value of 0 indicates the event forgot to set it).</li>
 *   <li>Event type string must be non-blank (caught by DomainEvent constructor).</li>
 *   <li>The concrete class must be non-abstract and a direct or indirect subclass of
 *       {@link DomainEvent}.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Central registry for DomainEvent types with schema version validation
 * @doc.layer domain
 * @doc.pattern EventSourced
 */
public class DomainEventRegistry {

    private static final Logger log = LoggerFactory.getLogger(DomainEventRegistry.class);

    /** Singleton instance. */
    public static final DomainEventRegistry INSTANCE = new DomainEventRegistry();

    /** Registered event metadata keyed by simple event-type name (e.g. {@code "ProjectCreated"}). */
    private final Map<String, EventTypeMetadata> registry = new ConcurrentHashMap<>();

    /**
     * Creates a new registry instance. The singleton {@link #INSTANCE} should be used in
     * production; this constructor is accessible for subclassing in tests.
     */
    protected DomainEventRegistry() {}

    // ─── Registration ─────────────────────────────────────────────────────────

    /**
     * Registers a concrete {@link DomainEvent} subclass.
     *
     * <p>The class must be concrete (non-abstract) and must be a subclass of {@link DomainEvent}.
     * The schema version is read from the class's {@link DomainEventMetaInfo} annotation if
     * present; otherwise it defaults to {@code 1}.
     *
     * @param eventClass the concrete event class to register
     * @throws IllegalArgumentException if the class is abstract or not a DomainEvent subclass
     */
    public void register(Class<? extends DomainEvent> eventClass) {
        if (!DomainEvent.class.isAssignableFrom(eventClass)) {
            throw new IllegalArgumentException(
                    "Not a DomainEvent subclass: " + eventClass.getName());
        }
        if (java.lang.reflect.Modifier.isAbstract(eventClass.getModifiers())) {
            throw new IllegalArgumentException(
                    "Cannot register abstract DomainEvent class: " + eventClass.getName());
        }

        int schemaVersion = resolveSchemaVersion(eventClass);
        String eventType  = resolveEventType(eventClass);

        EventTypeMetadata meta = new EventTypeMetadata(eventType, eventClass.getName(), schemaVersion);
        registry.put(eventType, meta);

        log.debug("Registered DomainEvent: type={} class={} schemaVersion={}",
                eventType, eventClass.getSimpleName(), schemaVersion);
    }

    // ─── Validation ───────────────────────────────────────────────────────────

    /**
     * Validates all registered event types and logs a startup summary.
     *
     * <p>Validation checks that every registered event has {@code schemaVersion ≥ 1}.
     * Throws an {@link InvalidEventRegistrationException} listing all violations if any are found.
     *
     * @throws InvalidEventRegistrationException if one or more registered events are invalid
     */
    public void validate() {
        if (registry.isEmpty()) {
            log.warn("DomainEventRegistry: no events registered. " +
                     "Call register() for each DomainEvent subclass before validate().");
            return;
        }

        StringBuilder violations = new StringBuilder();
        for (EventTypeMetadata meta : registry.values()) {
            if (meta.schemaVersion() < 1) {
                violations.append(String.format(
                        "  - %s (class=%s) has schemaVersion=%d; must be >= 1%n",
                        meta.eventType(), meta.className(), meta.schemaVersion()));
            }
        }

        if (!violations.isEmpty()) {
            throw new InvalidEventRegistrationException(
                    "DomainEventRegistry validation failed:%n" + violations);
        }

        log.info("DomainEventRegistry: validated {} event type(s): {}",
                registry.size(), registry.keySet());
    }

    // ─── Queries ──────────────────────────────────────────────────────────────

    /**
     * Returns an unmodifiable view of all registered event type names.
     *
     * @return set of registered event type strings (e.g. {@code {"ProjectCreated", "ProjectStageAdvanced"}})
     */
    public Set<String> registeredTypes() {
        return Collections.unmodifiableSet(registry.keySet());
    }

    /**
     * Returns the metadata for a registered event type, or {@code null} if not found.
     *
     * @param eventType the event type string (e.g. {@code "ProjectCreated"})
     * @return event metadata, or {@code null}
     */
    public EventTypeMetadata getMetadata(String eventType) {
        return registry.get(eventType);
    }

    /**
     * Returns {@code true} if the given event type is registered.
     *
     * @param eventType the event type string
     * @return {@code true} if registered
     */
    public boolean isRegistered(String eventType) {
        return registry.containsKey(eventType);
    }

    /** Returns the number of registered event types. */
    public int size() {
        return registry.size();
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    /**
     * Resolves the schema version from the class's {@link DomainEventMetaInfo} annotation,
     * with a fallback to {@code 1} if the annotation is absent.
     */
    private static int resolveSchemaVersion(Class<? extends DomainEvent> cls) {
        DomainEventMetaInfo meta = cls.getAnnotation(DomainEventMetaInfo.class);
        return (meta != null) ? meta.schemaVersion() : 1;
    }

    /**
     * Resolves the canonical event type string: prefers the annotation's {@code eventType} field,
     * falls back to the simple class name.
     */
    private static String resolveEventType(Class<? extends DomainEvent> cls) {
        DomainEventMetaInfo meta = cls.getAnnotation(DomainEventMetaInfo.class);
        if (meta != null && !meta.eventType().isBlank()) {
            return meta.eventType();
        }
        // Strip "Event" suffix for canonical type name: "ProjectCreatedEvent" → "ProjectCreated"
        String simpleName = cls.getSimpleName();
        return simpleName.endsWith("Event")
                ? simpleName.substring(0, simpleName.length() - 5)
                : simpleName;
    }

    // ─── Nested types ─────────────────────────────────────────────────────────

    /**
     * Immutable metadata snapshot for a registered event type.
     *
     * @param eventType     canonical event type string (e.g. {@code "ProjectCreated"})
     * @param className     fully qualified implementation class name
     * @param schemaVersion schema version (≥ 1)
     */
    public record EventTypeMetadata(
            String eventType,
            String className,
            int schemaVersion) {}

    /**
     * Thrown when {@link #validate()} finds one or more invalid event registrations.
     */
    public static final class InvalidEventRegistrationException extends RuntimeException {
        public InvalidEventRegistrationException(String message) {
            super(message);
        }
    }
}
