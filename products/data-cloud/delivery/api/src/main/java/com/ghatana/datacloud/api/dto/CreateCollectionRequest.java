package com.ghatana.datacloud.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * Request DTO for creating a new collection.
 *
 * <p><b>Purpose</b><br>
 * Strongly-typed request payload for collection creation with Bean Validation constraints.
 * Ensures all required fields are present and valid before reaching the service layer.
 *
 * <p><b>Validation Rules</b><br>
 * - name: Required, 1-255 chars, alphanumeric with underscores/hyphens
 * - label: Optional, max 255 chars
 * - description: Optional, max 2000 chars
 * - permission: Optional RBAC map
 * - applications: Optional application visibility list
 * - validationSchema: Optional JSON Schema for entity validation
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * CreateCollectionRequest request = new CreateCollectionRequest(
 *     "products",
 *     "Products",
 *     "Product catalog",
 *     Map.of("read", List.of("ADMIN", "USER")),
 *     List.of(Map.of("name", "admin", "visible", true)),
 *     Map.of("type", "object", "properties", Map.of(...))
 * );
 *
 * // Validate
 * ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
 * Validator validator = factory.getValidator();
 * Set<ConstraintViolation<CreateCollectionRequest>> violations = validator.validate(request);
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Immutable record - thread-safe.
 *
 * @param name Collection name (unique per tenant, alphanumeric with _/-)
 * @param label Human-readable label
 * @param description Collection description
 * @param permission RBAC permission map (operation -> roles)
 * @param applications Application visibility configuration
 * @param validationSchema JSON Schema for entity validation
 *
 * @see com.ghatana.datacloud.entity.MetaCollection
 * @see com.ghatana.datacloud.api.http.CollectionApiController
 * @doc.type record
 * @doc.purpose Request DTO for collection creation
 * @doc.layer product
 * @doc.pattern DTO (Data Transfer Object)
 */
public record CreateCollectionRequest(
    @NotBlank(message = "Collection name is required")
    @Size(min = 1, max = 255, message = "Collection name must be 1-255 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Collection name must be alphanumeric with underscores/hyphens")
    String name,

    @Size(max = 255, message = "Label must not exceed 255 characters")
    String label,

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    String description,

    Map<String, List<String>> permission,

    List<Map<String, Object>> applications,

    Map<String, Object> validationSchema
) {
    /**
     * Creates a minimal request with only required fields.
     *
     * @param name Collection name
     * @return request with defaults
     */
    public static CreateCollectionRequest minimal(String name) {
        return new CreateCollectionRequest(name, null, null, null, null, null);
    }

    /**
     * Creates a request with name and label.
     *
     * @param name Collection name
     * @param label Collection label
     * @return request with name and label
     */
    public static CreateCollectionRequest withLabel(String name, String label) {
        return new CreateCollectionRequest(name, label, null, null, null, null);
    }
}
