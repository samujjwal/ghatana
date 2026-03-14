package com.ghatana.appplatform.config.domain;

import java.util.Objects;

/**
 * Versioned JSON Schema that defines and validates config entries for a namespace.
 *
 * <p>A namespace groups related config keys (e.g. "payments", "kyc", "limits").
 * The {@code jsonSchema} field holds a JSON Schema v7 document as a string.
 * The {@code defaults} field is a JSON object string with key/value defaults that
 * are used when no explicit entry exists for a key at any hierarchy level.
 *
 * @param namespace   unique namespace identifier (e.g. "payments")
 * @param version     semantic version string (e.g. "1.0.0")
 * @param jsonSchema  JSON Schema v7 document as a raw JSON string
 * @param description human-readable description of the namespace
 * @param defaults    JSON object encoding default key/value pairs; may be "{}"
 *
 * @doc.type record
 * @doc.purpose Versioned JSON Schema definition for a config namespace
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ConfigSchema(
    String namespace,
    String version,
    String jsonSchema,
    String description,
    String defaults
) {
    public ConfigSchema {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(jsonSchema, "jsonSchema");
        if (namespace.isBlank()) throw new IllegalArgumentException("namespace must not be blank");
        if (version.isBlank())   throw new IllegalArgumentException("version must not be blank");
        if (defaults == null)    defaults = "{}";
    }
}
