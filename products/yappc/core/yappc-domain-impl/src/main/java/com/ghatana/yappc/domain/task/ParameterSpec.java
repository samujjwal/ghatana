package com.ghatana.products.yappc.domain.task;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Parameter specification for task input.
 *
 * @param type        The parameter type (string, number, boolean, object, array)
 * @param required    Whether the parameter is required
 * @param description Parameter description
 * @param defaultValue Optional default value
 * @doc.type record
 * @doc.purpose Define task input parameter schema
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ParameterSpec(
        @NotNull String type,
        boolean required,
        @NotNull String description,
        @Nullable Object defaultValue
) {
    public ParameterSpec {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Type cannot be null or blank");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Description cannot be null or blank");
        }
    }

    /**
     * Creates a required parameter.
     *
     * @param type        The parameter type
     * @param description The description
     * @return A new required parameter spec
     */
    public static ParameterSpec required(@NotNull String type, @NotNull String description) {
        return new ParameterSpec(type, true, description, null);
    }

    /**
     * Creates an optional parameter.
     *
     * @param type        The parameter type
     * @param description The description
     * @param defaultValue The default value
     * @return A new optional parameter spec
     */
    public static ParameterSpec optional(@NotNull String type, @NotNull String description, @Nullable Object defaultValue) {
        return new ParameterSpec(type, false, description, defaultValue);
    }
}
