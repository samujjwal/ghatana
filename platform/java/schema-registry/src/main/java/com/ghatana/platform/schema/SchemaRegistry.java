package com.ghatana.platform.schema;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Registry for event payload schemas supporting storage, retrieval,
 * validation, and compatibility enforcement.
 *
 * <h2>Compatibility Modes</h2>
 * <ul>
 *   <li>{@link CompatibilityMode#BACKWARD} — consumers using the new schema can read
 *       data produced with the old schema (you may add optional fields or remove
 *       fields with defaults; you must not remove required fields).</li>
 *   <li>{@link CompatibilityMode#FORWARD} — consumers using the old schema can read
 *       data produced with the new schema (you may add required fields; old consumers
 *       must tolerate unknown fields).</li>
 *   <li>{@link CompatibilityMode#FULL} — both BACKWARD and FORWARD must hold.</li>
 * </ul>
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>Bootstrap (once at startup): {@link SchemaBootstrapper} seeds well-known schemas.</li>
 *   <li>Registration: producers call {@link #registerSchema} which enforces compatibility.</li>
 *   <li>Validation: consumers call {@link #validate} before processing events.</li>
 * </ol>
 *
 * @doc.type interface
 * @doc.purpose Schema registration, retrieval, and validation SPI
 * @doc.layer platform
 * @doc.pattern Service Provider Interface
 */
public interface SchemaRegistry {

    /**
     * Retrieves a registered schema by name and version.
     *
     * @param schemaName    schema identifier (e.g. {@code "OrderCreated"})
     * @param schemaVersion semantic version (e.g. {@code "1.0.0"})
     * @return promise of the schema if found, or {@link Optional#empty()}
     */
    @NotNull
    Promise<Optional<RegisteredSchema>> getSchema(
            @NotNull String schemaName, @NotNull String schemaVersion);

    /**
     * Retrieves the latest registered version of a schema.
     *
     * @param schemaName schema identifier
     * @return promise of the latest schema, or {@link Optional#empty()} if none registered
     */
    @NotNull
    Promise<Optional<RegisteredSchema>> getLatestSchema(@NotNull String schemaName);

    /**
     * Validates a JSON payload string against the specified schema version.
     *
     * @param schemaName    schema identifier
     * @param schemaVersion schema version
     * @param payloadJson   JSON string to validate
     * @return promise of validation result (always resolves; never rejected)
     */
    @NotNull
    Promise<ValidationResult> validate(
            @NotNull String schemaName,
            @NotNull String schemaVersion,
            @NotNull String payloadJson);

    /**
     * Registers a new schema version using the default {@link CompatibilityMode#BACKWARD} mode.
     *
     * <p>Idempotent: if the exact same schema (same name, version, and body) is already
     * registered the call is a no-op.
     *
     * @param schemaName    schema identifier
     * @param schemaVersion semantic version
     * @param jsonSchema    JSON Schema (Draft-07) string
     * @return promise of the newly registered schema
     * @throws SchemaCompatibilityException if the new schema breaks backward compatibility
     *                                      with the currently registered schema
     */
    @NotNull
    Promise<RegisteredSchema> registerSchema(
            @NotNull String schemaName,
            @NotNull String schemaVersion,
            @NotNull String jsonSchema);

    /**
     * Registers a new schema version with an explicit compatibility mode.
     *
     * @param schemaName       schema identifier
     * @param schemaVersion    semantic version
     * @param jsonSchema       JSON Schema (Draft-07) string
     * @param compatibilityMode compatibility mode to enforce
     * @return promise of the newly registered schema
     * @throws SchemaCompatibilityException if the new schema violates the given mode
     */
    @NotNull
    Promise<RegisteredSchema> registerSchema(
            @NotNull String schemaName,
            @NotNull String schemaVersion,
            @NotNull String jsonSchema,
            @NotNull CompatibilityMode compatibilityMode);
}
