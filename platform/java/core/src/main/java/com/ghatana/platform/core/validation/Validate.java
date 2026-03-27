package com.ghatana.platform.core.validation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class, method, or field as requiring validation.
 *
 * <p>Can be used as a marker annotation for documentation and tooling purposes.
 * Frameworks can scan for this annotation to apply registered validators.
 *
 * @doc.type class
 * @doc.purpose Marker annotation for validation-required fields and classes
 * @doc.layer core
 * @doc.pattern Annotation, Validation
 *
 * @since 2026-03-27
 */
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface Validate {

    /**
     * Optional validator class to apply.
     * When specified, the named validator is looked up in {@link ValidatorRegistry}.
     */
    Class<? extends Validator<?>> using() default Validator.NoOp.class;

    /**
     * Optional validation group(s) this annotation applies to.
     * Empty array means applies to all groups.
     */
    String[] groups() default {};

    /**
     * Optional human-readable message to include in the violation report.
     */
    String message() default "";
}
