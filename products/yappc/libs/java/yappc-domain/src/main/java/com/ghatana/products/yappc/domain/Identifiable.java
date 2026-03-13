/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Domain — Identifiable Contract
 */
package com.ghatana.products.yappc.domain;

/**
 * Marker contract for entities that have a stable unique identifier.
 *
 * <p>Implementing this interface enables compile-time enforcement that only properly
 * identifiable domain objects are stored through {@code YappcDataCloudRepository}.
 * It pairs naturally with {@link AggregateRoot}, which already requires an ID through
 * its abstract {@code getId()} method.
 *
 * @param <ID> the type of the unique identifier (typically {@link java.util.UUID})
 * @doc.type interface
 * @doc.purpose Compile-time type contract for repository-storable entities
 * @doc.layer domain
 * @doc.pattern Value Object / Contract
 */
public interface Identifiable<ID> {

    /**
     * Returns the unique identifier of this entity.
     *
     * @return unique identifier, never {@code null}
     */
    ID getId();
}
