/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.entity.validation;

import io.activej.promise.Promise;

/**
 * Interface for validating foreign key-like references between collections.
 *
 * <p>Implementations check whether a referenced value exists in the target
 * collection and field. This enables referential integrity validation at the
 * API boundary without tight coupling to persistence layers.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * validator.setReferenceChecker((tenantId, collection, field, value) -> {
 *     // Query database to check if value exists
 *     return repository.exists(tenantId, collection, field, value);
 * });
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Pluggable reference validation for foreign key-like constraints
 * @doc.layer product
 * @doc.pattern Strategy
 */
@FunctionalInterface
public interface ReferenceChecker {

    /**
     * Checks whether a reference value exists in the target collection and field.
     *
     * <p>This method is called asynchronously to avoid blocking validation
     * on expensive database lookups. Implementations should return a completed
     * future if the check can be performed synchronously (e.g., from a cache).
     *
     * @param tenantId   tenant identifier
     * @param collection target collection name
     * @param field      target field name within the collection
     * @param value      the reference value to check
     * @return Promise resolving to {@code true} if the reference exists
     */
    Promise<Boolean> referenceExists(String tenantId, String collection, String field, Object value);
}
