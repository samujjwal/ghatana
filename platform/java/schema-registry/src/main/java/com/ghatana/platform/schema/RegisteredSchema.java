package com.ghatana.platform.schema;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable snapshot of a registered schema at a specific version.
 *
 * @param schemaName      schema identifier (e.g. {@code "OrderCreated"})
 * @param schemaVersion   semantic version (e.g. {@code "1.0.0"})
 * @param jsonSchema      the JSON Schema (Draft-07) definition string
 * @param compatibilityMode the mode that was active when this schema was registered
 * @param registeredAt    registration timestamp
 *
 * @doc.type record
 * @doc.purpose Immutable snapshot of a registered schema version
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record RegisteredSchema(
        @NotNull String schemaName,
        @NotNull String schemaVersion,
        @NotNull String jsonSchema,
        @NotNull CompatibilityMode compatibilityMode,
        @NotNull Instant registeredAt
) {
    public RegisteredSchema {
        Objects.requireNonNull(schemaName, "schemaName must not be null");
        Objects.requireNonNull(schemaVersion, "schemaVersion must not be null");
        Objects.requireNonNull(jsonSchema, "jsonSchema must not be null");
        Objects.requireNonNull(compatibilityMode, "compatibilityMode must not be null");
        Objects.requireNonNull(registeredAt, "registeredAt must not be null");
    }

    /** Returns the qualified identifier: {@code schemaName:schemaVersion}. */
    @NotNull
    public String qualifiedId() {
        return schemaName + ":" + schemaVersion;
    }
}
