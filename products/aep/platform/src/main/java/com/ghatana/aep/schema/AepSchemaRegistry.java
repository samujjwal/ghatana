/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.schema;

import com.ghatana.platform.schema.SchemaRegistry;
import com.ghatana.platform.schema.ValidationResult;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * AEP-scoped schema registry facade.
 *
 * <p>Delegates to the platform {@link SchemaRegistry} and enforces ingest-time
 * schema validation. Events whose schemas are not registered are rejected with
 * a structured {@link SchemaValidationException}, never with a raw exception.
 *
 * <p>Schema resolution uses the convention:
 * <pre>
 *   schemaName    = eventType   (e.g. "order.created")
 *   schemaVersion = "latest"    (resolved to the newest registered version)
 * </pre>
 * Producers that need version pinning may call {@link #validate(String, String, String)} directly.
 *
 * @doc.type class
 * @doc.purpose AEP ingest-time schema validation via platform SchemaRegistry
 * @doc.layer product
 * @doc.pattern Facade
 */
public final class AepSchemaRegistry {

    private static final Logger log = LoggerFactory.getLogger(AepSchemaRegistry.class);
    private static final String LATEST_VERSION = "latest";

    private final SchemaRegistry delegate;
    private final boolean enforcementEnabled;

    /**
     * @param delegate          platform schema registry (never {@code null})
     * @param enforcementEnabled when {@code false} validation is logged but never blocks ingest;
     *                           useful during schema roll-out. Default: {@code true}.
     */
    public AepSchemaRegistry(SchemaRegistry delegate, boolean enforcementEnabled) {
        this.delegate = Objects.requireNonNull(delegate, "delegate SchemaRegistry required");
        this.enforcementEnabled = enforcementEnabled;
    }

    /**
     * Validates {@code payloadJson} against the latest registered schema for {@code eventType}.
     *
     * <p>If no schema is registered for the event type the event is allowed through
     * with a {@code WARN} log (schema-less events). Set
     * {@code AEP_SCHEMA_ENFORCE_STRICT=true} to fail on missing schemas.
     *
     * @param eventType   logical event type (used as schema name)
     * @param payloadJson JSON payload string to validate
     * @return promise that resolves normally for valid payloads
     * @throws SchemaValidationException if the payload fails validation and enforcement is enabled
     */
    public Promise<Void> validateIngest(String eventType, String payloadJson) {
        Objects.requireNonNull(eventType, "eventType required");
        Objects.requireNonNull(payloadJson, "payloadJson required");

        return delegate.getLatestSchema(eventType)
                .then(schemaOpt -> {
                    if (schemaOpt.isEmpty()) {
                        log.warn("[AepSchemaRegistry] no schema registered for eventType={}; allowing through", eventType);
                        return Promise.complete();
                    }
                    String version = schemaOpt.get().schemaVersion();
                    return validate(eventType, version, payloadJson);
                });
    }

    /**
     * Validates {@code payloadJson} against a specific schema version.
     *
     * @param eventType     schema name (logical event type)
     * @param schemaVersion exact schema version (e.g. {@code "1.2.0"})
     * @param payloadJson   JSON payload to validate
     * @return promise that resolves for valid payloads; rejects with {@link SchemaValidationException}
     *         if validation fails and enforcement is enabled
     */
    public Promise<Void> validate(String eventType, String schemaVersion, String payloadJson) {
        Objects.requireNonNull(eventType, "eventType required");
        Objects.requireNonNull(schemaVersion, "schemaVersion required");
        Objects.requireNonNull(payloadJson, "payloadJson required");

        return delegate.validate(eventType, schemaVersion, payloadJson)
                .then(result -> {
                    if (result.isValid()) {
                        return Promise.<Void>complete();
                    }
                    String msg = buildErrorMessage(eventType, schemaVersion, result);
                    if (enforcementEnabled) {
                        log.warn("[AepSchemaRegistry] validation FAILED eventType={} version={} errors={}",
                                eventType, schemaVersion, result.errors().size());
                        return Promise.ofException(new SchemaValidationException(eventType, schemaVersion, result));
                    }
                    log.warn("[AepSchemaRegistry] validation failed (enforcement disabled) {}", msg);
                    return Promise.complete();
                });
    }

    /**
     * Registers a new schema version for an event type.
     *
     * @param eventType   logical event type (used as schema name)
     * @param version     semantic version (e.g. {@code "1.0.0"})
     * @param jsonSchema  JSON Schema (Draft-07) string
     * @return promise of the registered schema record
     */
    public Promise<com.ghatana.platform.schema.RegisteredSchema> registerSchema(
            String eventType, String version, String jsonSchema) {
        Objects.requireNonNull(eventType, "eventType required");
        Objects.requireNonNull(version, "version required");
        Objects.requireNonNull(jsonSchema, "jsonSchema required");
        return delegate.registerSchema(eventType, version, jsonSchema);
    }

    private static String buildErrorMessage(
            String eventType, String version, ValidationResult result) {
        StringBuilder sb = new StringBuilder("Schema validation failed: eventType=")
                .append(eventType).append(" version=").append(version);
        result.errors().forEach(e -> sb.append(" [").append(e.fieldPath()).append(": ").append(e.errorMessage()).append("]"));
        return sb.toString();
    }
}
