/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Task 5.3 — Event Schema Registry: stores, versions, and validates schemas.
 */
package com.ghatana.datacloud.schema;

import com.ghatana.datacloud.schema.SchemaCompatibilityChecker.CompatibilityResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Schema Registry for event and entity schemas with compatibility enforcement.
 *
 * <h2>Capabilities</h2>
 * <ul>
 *   <li>Schema registration with automatic versioning</li>
 *   <li>Compatibility checking (BACKWARD, FORWARD, FULL, NONE)</li>
 *   <li>Subject-level compatibility mode configuration</li>
 *   <li>Schema version history per subject</li>
 *   <li>Thread-safe concurrent access</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * EventSchemaRegistry registry = EventSchemaRegistry.create();
 * registry.setCompatibilityMode("order.created", CompatibilityMode.BACKWARD);
 *
 * EventSchema v1 = EventSchema.create("order.created", SchemaFormat.JSON_SCHEMA,
 *     "{...}", List.of(SchemaField.required("orderId", "string")));
 * registry.register(v1);  // Registers successfully
 *
 * EventSchema v2 = v1.nextVersion("{...}", List.of(
 *     SchemaField.required("orderId", "string"),
 *     SchemaField.optional("priority", "integer")));
 * registry.register(v2);  // Compatible — adds optional field
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Thread-safe schema registry with automatic versioning and configurable compatibility enforcement
 * @doc.layer product
 * @doc.pattern Service
 */
public class EventSchemaRegistry {

    private static final Logger log = LoggerFactory.getLogger(EventSchemaRegistry.class);

    // subject → ordered list of schema versions (latest last)
    private final ConcurrentHashMap<String, List<EventSchema>> schemas = new ConcurrentHashMap<>();
    // subject → compatibility mode
    private final ConcurrentHashMap<String, CompatibilityMode> compatibilityModes = new ConcurrentHashMap<>();
    private final SchemaCompatibilityChecker checker;
    private CompatibilityMode defaultMode;

    private EventSchemaRegistry(CompatibilityMode defaultMode) {
        this.defaultMode = defaultMode;
        this.checker = new SchemaCompatibilityChecker();
    }

    /**
     * Creates a registry with BACKWARD compatibility as default.
     */
    public static EventSchemaRegistry create() {
        return new EventSchemaRegistry(CompatibilityMode.BACKWARD);
    }

    /**
     * Creates a registry with a custom default compatibility mode.
     */
    public static EventSchemaRegistry create(CompatibilityMode defaultMode) {
        return new EventSchemaRegistry(defaultMode);
    }

    /**
     * Registers a new schema version. If a previous version exists for the same subject,
     * compatibility is validated before registration.
     *
     * @param schema the schema to register
     * @return the registered schema
     * @throws SchemaRegistrationException if the schema is incompatible
     */
    public EventSchema register(EventSchema schema) {
        Objects.requireNonNull(schema, "schema required");

        return schemas.compute(schema.subject(), (subject, existing) -> {
            if (existing == null) {
                existing = new ArrayList<>();
            }

            if (!existing.isEmpty()) {
                EventSchema latest = existing.get(existing.size() - 1);
                CompatibilityMode mode = getCompatibilityMode(subject);

                CompatibilityResult result = checker.check(latest, schema, mode);
                if (!result.compatible()) {
                    throw new SchemaRegistrationException(subject, schema.version(),
                            mode, result.violations());
                }

                if (schema.version() <= latest.version()) {
                    throw new SchemaRegistrationException(subject, schema.version(),
                            mode, List.of("Version " + schema.version() +
                            " must be greater than current version " + latest.version()));
                }
            }

            List<EventSchema> updated = new ArrayList<>(existing);
            updated.add(schema);
            log.info("Registered schema: subject={}, version={}, format={}",
                    subject, schema.version(), schema.format());
            return updated;
        }).get(schemas.get(schema.subject()).size() - 1);
    }

    /**
     * Tests whether a schema would be compatible without actually registering it.
     *
     * @param schema the schema to test
     * @return the compatibility result
     */
    public CompatibilityResult testCompatibility(EventSchema schema) {
        List<EventSchema> existing = schemas.get(schema.subject());
        if (existing == null || existing.isEmpty()) {
            return CompatibilityResult.ok();
        }
        EventSchema latest = existing.get(existing.size() - 1);
        return checker.check(latest, schema, getCompatibilityMode(schema.subject()));
    }

    /**
     * Gets the latest schema for a subject.
     */
    public Optional<EventSchema> getLatest(String subject) {
        List<EventSchema> versions = schemas.get(subject);
        if (versions == null || versions.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(versions.get(versions.size() - 1));
    }

    /**
     * Gets a specific schema version for a subject.
     */
    public Optional<EventSchema> getVersion(String subject, int version) {
        List<EventSchema> versions = schemas.get(subject);
        if (versions == null) {
            return Optional.empty();
        }
        return versions.stream()
                .filter(s -> s.version() == version)
                .findFirst();
    }

    /**
     * Gets all versions of a subject, ordered oldest to newest.
     */
    public List<EventSchema> getAllVersions(String subject) {
        List<EventSchema> versions = schemas.get(subject);
        return versions != null ? List.copyOf(versions) : List.of();
    }

    /**
     * Lists all registered subjects.
     */
    public Set<String> listSubjects() {
        return Set.copyOf(schemas.keySet());
    }

    /**
     * Sets the compatibility mode for a specific subject.
     */
    public void setCompatibilityMode(String subject, CompatibilityMode mode) {
        compatibilityModes.put(subject, mode);
    }

    /**
     * Gets the compatibility mode for a subject (falls back to default).
     */
    public CompatibilityMode getCompatibilityMode(String subject) {
        return compatibilityModes.getOrDefault(subject, defaultMode);
    }

    /**
     * Sets the default compatibility mode.
     */
    public void setDefaultCompatibilityMode(CompatibilityMode mode) {
        this.defaultMode = mode;
    }

    /**
     * Deletes all versions of a subject.
     *
     * @return the number of versions deleted
     */
    public int deleteSubject(String subject) {
        List<EventSchema> removed = schemas.remove(subject);
        compatibilityModes.remove(subject);
        return removed != null ? removed.size() : 0;
    }

    /**
     * Returns total number of schemas across all subjects.
     */
    public int totalSchemaCount() {
        return schemas.values().stream().mapToInt(List::size).sum();
    }

    /**
     * Returns the number of registered subjects.
     */
    public int subjectCount() {
        return schemas.size();
    }

    /**
     * Exception thrown when a schema registration fails compatibility checks.
     */
    public static class SchemaRegistrationException extends RuntimeException {
        private final String subject;
        private final int version;
        private final CompatibilityMode mode;
        private final List<String> violations;

        public SchemaRegistrationException(String subject, int version,
                                           CompatibilityMode mode, List<String> violations) {
            super(String.format("Schema registration failed for '%s' v%d " +
                    "under %s compatibility: %s", subject, version, mode, violations));
            this.subject = subject;
            this.version = version;
            this.mode = mode;
            this.violations = List.copyOf(violations);
        }

        public String getSubject() { return subject; }
        public int getVersion() { return version; }
        public CompatibilityMode getMode() { return mode; }
        public List<String> getViolations() { return violations; }
    }
}
