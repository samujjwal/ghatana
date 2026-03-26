/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.contracts;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Contract for data schema surfaces: event payloads, data models, evolution rules.
 *
 * <p>A schema contract declares the event types and data models a module owns,
 * their schema format, compatibility mode, and evolution lifecycle. Aligns with
 * the AppPlatform event-store's {@code SchemaCompatibilityChecker} and
 * {@code SchemaBreakingChangeDetector} patterns.</p>
 *
 * @doc.type class
 * @doc.purpose Schema contract for event and data model declarations
 * @doc.layer core
 * @doc.pattern ValueObject
 * @author Ghatana Kernel Team
 * @since 1.1.0
 */
public final class SchemaContract extends KernelContract {

    /**
     * Compatibility mode for schema evolution, mirroring AppPlatform event-store patterns.
     */
    public enum CompatibilityMode {
        /** New schema can read data written by old schema. */
        BACKWARD,
        /** Old schema can read data written by new schema. */
        FORWARD,
        /** Both backward and forward compatible. */
        FULL,
        /** No compatibility guarantee (breaking change allowed). */
        NONE
    }

    /**
     * Supported schema formats.
     */
    public enum SchemaFormat {
        JSON_SCHEMA_V7,
        AVRO,
        PROTOBUF
    }

    /**
     * Declares a single schema subject (event type or data model).
     */
    public record SchemaSubject(String subjectId, SchemaFormat format,
                                CompatibilityMode compatibility, String schemaLocation) {
        public SchemaSubject {
            Objects.requireNonNull(subjectId, "subjectId required");
            Objects.requireNonNull(format, "format required");
            Objects.requireNonNull(compatibility, "compatibility required");
        }
    }

    private final List<SchemaSubject> subjects;

    private SchemaContract(Builder builder) {
        super(builder.contractId, builder.name, builder.version,
              KernelContract.ContractFamily.SCHEMA, builder.metadata);
        this.subjects = builder.subjects != null ? List.copyOf(builder.subjects) : List.of();
        validate();
    }

    public List<SchemaSubject> getSubjects() { return subjects; }

    @Override
    protected void validate() {
        super.validate();
        for (SchemaSubject subject : subjects) {
            if (subject.subjectId().isBlank()) {
                throw new IllegalArgumentException("Schema subjectId cannot be blank");
            }
        }
    }

    /**
     * Creates a new builder for {@link SchemaContract}.
     */
    public static Builder builder(String contractId, String name, String version) {
        return new Builder(contractId, name, version);
    }

    /**
     * Fluent builder for {@link SchemaContract}.
     */
    public static final class Builder {
        private final String contractId;
        private final String name;
        private final String version;
        private Map<String, String> metadata = Map.of();
        private List<SchemaSubject> subjects = List.of();

        private Builder(String contractId, String name, String version) {
            this.contractId = contractId;
            this.name = name;
            this.version = version;
        }

        public Builder metadata(Map<String, String> metadata) { this.metadata = metadata; return this; }
        public Builder subjects(List<SchemaSubject> subjects) { this.subjects = subjects; return this; }

        public SchemaContract build() { return new SchemaContract(this); }
    }
}
