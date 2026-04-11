package com.ghatana.platform.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a public API class as having required @doc.* documentation tags.
 *
 * <p>This annotation is used by the documentation validation system to ensure
 * all public API classes have proper documentation coverage.
 *
 * <p>Required tags for public API classes:
 * <ul>
 *   <li>@doc.type - The type of construct (class, interface, enum, record)</li>
 *   <li>@doc.purpose - What this construct does</li>
 *   <li>@doc.layer - The architectural layer (platform, product, plugin)</li>
 *   <li>@doc.pattern - The design pattern (Service, Repository, Factory, etc.)</li>
 * </ul>
 *
 * @doc.type annotation
 * @doc.purpose Marks public API classes with required documentation
 * @doc.layer platform
 * @doc.pattern Annotation
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
public @interface Documented {
    /**
     * The documentation type of the annotated element.
     */
    String type() default "";

    /**
     * The purpose/description of the annotated element.
     */
    String purpose() default "";

    /**
     * The architectural layer this element belongs to.
     */
    String layer() default "";

    /**
     * The design pattern implemented by this element.
     */
    String pattern() default "";
}
