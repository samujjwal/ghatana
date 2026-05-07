package com.ghatana.datacloud.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Request DTO for creating a new entity in a collection.
 *
 * <p><b>Purpose</b><br>
 * Strongly-typed request payload for entity creation. The data field contains the actual
 * entity payload which will be validated against the collection's JSON Schema.
 *
 * <p><b>Validation Rules</b><br>
 * - data: Required, must conform to collection's validationSchema
 * - Schema validation is performed by ValidationService before persistence
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * CreateEntityRequest request = new CreateEntityRequest(
 *     Map.of(
 *         "name", "Product A",
 *         "price", 99.99,
 *         "category", "electronics"
 *     )
 * );
 *
 * // Validate against schema
 * ValidationResult result = validationService.validate(request.data(), collectionSchema);
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
 * @doc.purpose Request DTO for entity creation
 * @doc.layer product
 * @doc.pattern DTO (Data Transfer Object)
 */
public record CreateEntityRequest(
    @NotNull(message = "Entity data is required")
    Map<String, Object> data
) {
    /**
     * Creates a request with the given data.
     *
     * @param data entity data
     * @return request
     */
    public static CreateEntityRequest of(Map<String, Object> data) {
        return new CreateEntityRequest(data);
    }
}
