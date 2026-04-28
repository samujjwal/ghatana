package com.ghatana.platform.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to specify required roles for accessing a method or class.
 * Methods annotated with @RequireRole will only be accessible to users
 * with the specified roles.
 *
 * <p>Example usage:</p>
 * <pre>
 * {@code @RequireRole("admin")}
 *  public Response deleteUser() {
 *      // Method implementation
 *  }
 * </pre>
 *
 * <p>Multiple roles can be specified:</p>
 * <pre>
 * {@code @RequireRole({"admin", "moderator"})}
 *  public Response moderateContent() {
 *      // Method implementation
 *  }
 * </pre>
 *
 * @doc.type annotation
 * @doc.purpose Annotation to require specific roles for method or class access
 * @doc.layer security
 * @doc.pattern Annotation
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {
    /**
     * The roles required to access the annotated method or class.
     * A user must have at least one of the specified roles to gain access.
     */
    String[] value();
}
