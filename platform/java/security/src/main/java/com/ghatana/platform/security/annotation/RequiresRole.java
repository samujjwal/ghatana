package com.ghatana.platform.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to specify required roles for accessing a method or class.
 * Can be applied to controller methods or classes to enforce role-based access control.
 *
 * <p>Example usage:</p>
 * <pre>
 * {@code @RequiresRole("ADMIN")}
 *  public Response deleteUser() {
 *      // Method implementation
 *  }
 * </pre>
 *
 * <p>Multiple roles can be specified using the value array:</p>
 * <pre>
 * {@code @RequiresRole({"ADMIN", "OPERATOR"})}
 *  public Response performMaintenance() {
 *      // Method implementation
 *  }
 * </pre>
 *
 * <p>By default, any of the specified roles grants access (OR semantics).
 * Set requireAll to true for AND semantics (all roles required).</p>
 *
 * @doc.type annotation
 * @doc.purpose Annotation to enforce required roles on methods or classes
 * @doc.layer security
 * @doc.pattern Annotation
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresRole {
    /**
     * The role(s) required to access the annotated method or class.
     *
     * @return Array of role names
     */
    String[] value();

    /**
     * Specifies if all listed roles are required (AND) or any one of them (OR).
     * Default is false (any one role is sufficient).
     *
     * @return true if all roles are required, false otherwise
     */
    boolean requireAll() default false;
}
