package com.ghatana.platform.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation to indicate that a method or class requires authentication.
 * Methods annotated with @Secured will only be accessible to authenticated users.
 *
 * <p>Example usage:</p>
 * <pre>
 * {@code @Secured}
 *  public Response getUserProfile() {
 *      // Method implementation
 *  }
 * </pre>
 *
 * <p>This is a coarse-grained security annotation. For fine-grained control,
 * use {@link RequiresRole} or {@link RequiresPermission} instead.</p>
 *
 * @doc.type annotation
 * @doc.purpose Marker annotation to require authentication for methods or classes
 * @doc.layer security
 * @doc.pattern Annotation
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Secured {
}
