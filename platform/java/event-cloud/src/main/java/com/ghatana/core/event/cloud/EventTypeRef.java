package com.ghatana.core.event.cloud;

import java.util.Objects;

/**
 * Reference to an event type in the catalog.
 * Combines event type name and version for schema lookup and validation.
 
 *
 * @doc.type record
 * @doc.purpose Event type ref
 * @doc.layer platform
 * @doc.pattern ValueObject
*/
public record EventTypeRef(String name, Version version) {

    public EventTypeRef {
        Objects.requireNonNull(name, "name required");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Event type name cannot be blank");
        }
        Objects.requireNonNull(version, "version required");
    }

    /**
     * Create EventTypeRef from name and version components.
     */
    public static EventTypeRef of(String name, int major, int minor) {
        return new EventTypeRef(name, new Version(major, minor));
    }

    @Override
    public String toString() {
        return name + "@" + version;
    }
}
