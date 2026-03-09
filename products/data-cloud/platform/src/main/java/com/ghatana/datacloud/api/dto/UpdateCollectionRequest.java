package com.ghatana.datacloud.api.dto;

import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * Request DTO for updating an existing collection.
 *
 * <p><b>Purpose</b><br>
 * Strongly-typed request payload for collection updates. All fields are optional
 * (partial update semantics). Only non-null fields will be applied to the existing collection.
 *
 * <p><b>Validation Rules</b><br>
 * - label: Optional, max 255 chars
 * - description: Optional, max 2000 chars
 * - permission: Optional RBAC map (replaces existing if provided)
 * - applications: Optional application list (replaces existing if provided)
 * - validationSchema: Optional JSON Schema (replaces existing if provided)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * UpdateCollectionRequest request = new UpdateCollectionRequest(
 *     "Updated Products",  // new label
 *     null,                // keep existing description
 *     Map.of("read", List.of("ADMIN", "USER", "GUEST")),  // update permissions
 *     null,                // keep existing applications
 *     null                 // keep existing schema
 * );
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Immutable record - thread-safe.
 *
 * @param label Human-readable label (null = no change)
 * @param description Collection description (null = no change)
 * @param permission RBAC permission map (null = no change)
 * @param applications Application visibility configuration (null = no change)
 * @param validationSchema JSON Schema for entity validation (null = no change)
 *
 * @see com.ghatana.datacloud.entity.MetaCollection
 * @see com.ghatana.datacloud.api.http.CollectionApiController
 * @doc.type record
 * @doc.purpose Request DTO for collection updates (partial)
 * @doc.layer product
 * @doc.pattern DTO (Data Transfer Object)
 */
public record UpdateCollectionRequest(
    @Size(max = 255, message = "Label must not exceed 255 characters")
    String label,

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    String description,

    Map<String, List<String>> permission,

    List<Map<String, Object>> applications,

    Map<String, Object> validationSchema
) {
    /**
     * Creates an empty update request (no changes).
     *
     * @return empty request
     */
    public static UpdateCollectionRequest empty() {
        return new UpdateCollectionRequest(null, null, null, null, null);
    }

    /**
     * Creates a request updating only the label.
     *
     * @param label new label
     * @return request with label update
     */
    public static UpdateCollectionRequest labelOnly(String label) {
        return new UpdateCollectionRequest(label, null, null, null, null);
    }

    /**
     * Creates a request updating only the description.
     *
     * @param description new description
     * @return request with description update
     */
    public static UpdateCollectionRequest descriptionOnly(String description) {
        return new UpdateCollectionRequest(null, description, null, null, null);
    }
}
