package com.ghatana.yappc.domain.generate;

import com.ghatana.yappc.domain.shape.ShapeSpec;
import com.ghatana.yappc.domain.validate.LifecycleValidationResult;

/**
 * @doc.type record
 * @doc.purpose Shape spec with validation result
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record ValidatedSpec(
    ShapeSpec shapeSpec,
    LifecycleValidationResult validationResult
) {
    public static ValidatedSpec of(ShapeSpec shapeSpec, LifecycleValidationResult validationResult) {
        return new ValidatedSpec(shapeSpec, validationResult);
    }
}
