package com.ghatana.datacloud.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Request DTO for updating an existing entity.
 *
 * <p><b>Purpose</b><br>
 * Strongly-typed request payload for entity updates. The data field replaces the entire
 * entity payload (full update semantics, not partial). Must conform to collection schema.
 *
 * <p><b>Validation Rules</b><br>
 * - data: Required, must conform to collection's validationSchema
 * - Schema validation is performed by ValidationService before persistence
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * UpdateEntityRequest request = new UpdateEntityRequest(
 *     Map.of(
 *         "name", "Product A (Updated)",
 *         "price", 89.99,
 *         "category", "electronics",
 *         "inStock", true
 *     )
 * );
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Immutable record - thread-safe.
 *
 * @param data Entity data (JSON object, validated against collection schema)
 *
 * @see com.ghatana.datacloud.entity.MetaEntity
 * @see com.ghatana.datacloud.api.http.EntityApiController
 * @see com.ghatana.datacloud.application.ValidationService
 * @doc.type record
 * @doc.purpose Request DTO for entity updates
 * @doc.layer product
 * @doc.pattern DTO (Data Transfer Object)
 */
public record UpdateEntityRequest(
    @NotNull(message = "Entity data is required")
    Map<String, Object> data
) {
    /**
     * Creates a request with the given data.
     *
     * @param data entity data
     * @return request
     */
    public static UpdateEntityRequest of(Map<String, Object> data) {
        return new UpdateEntityRequest(data);
    }
}
