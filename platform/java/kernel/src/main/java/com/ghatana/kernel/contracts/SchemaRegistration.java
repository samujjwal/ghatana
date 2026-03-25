/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.contracts;

import java.util.Map;
import java.util.Objects;

/**
 * Lightweight registration record for a data-schema surface (event payload, data model, etc.).
 *
 * <p>Used for simple schema metadata registration via {@link ContractRegistry#registerSchemaContract}.
 * For a full structured schema contract with compatibility modes and schema subjects,
 * use {@link SchemaContract} instead.</p>
 *
 * <p>Promoted from the inner type {@code ContractValidator.SchemaContract} in the removed
 * {@code kernel.contract} package.</p>
 *
 * @doc.type record
 * @doc.purpose Lightweight schema registration record (schemaId, version, type, definition, metadata)
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public record SchemaRegistration(
        String schemaId,
        String version,
        String schemaType,
        Map<String, Object> schemaDefinition,
        Map<String, Object> schemaMetadata
) {
    public SchemaRegistration {
        Objects.requireNonNull(schemaId,   "schemaId");
        Objects.requireNonNull(version,    "version");
        Objects.requireNonNull(schemaType, "schemaType");
        schemaDefinition = schemaDefinition != null ? Map.copyOf(schemaDefinition) : Map.of();
        schemaMetadata   = schemaMetadata   != null ? Map.copyOf(schemaMetadata)   : Map.of();
    }
}
