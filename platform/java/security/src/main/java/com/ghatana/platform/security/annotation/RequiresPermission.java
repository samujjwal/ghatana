package com.ghatana.platform.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to specify required permissions for a method or class.
 * Can be applied to controller methods or classes to enforce access control.
 * 
 * <p>Example usage:</p>
 * <pre>
 * {@code @RequiresPermission("event:read:all")}
 *  public Response getEvents() {
 *      // Method implementation
 *  }
 * </pre>
 * 
 * <p>The permission string follows the format: "resource:action:scope"</p>
 * <ul>
 *   <li><strong>resource</strong>: The resource being accessed (e.g., "event", "user")</li>
 *   <li><strong>action</strong>: The action being performed (e.g., "read", "write", "delete")</li>
 *   <li><strong>scope</strong>: The scope of the permission (e.g., "all", "own", "team")</li>
 * </ul>
 * 
 * <p>Wildcards are supported for any part of the permission string. For example:</p>
 * <ul>
 *   <li>"event:*:all" - All actions on events</li>
 *   <li>"*:read:all" - Read access to all resources</li>
 *   <li>"*:*:*" - Full access (admin)</li>
 * </ul>
 *
 * @doc.type annotation
 * @doc.purpose Annotation to enforce required permissions on methods or classes
 * @doc.layer security
 * @doc.pattern Annotation
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {
    /**
     * The permission required to access the annotated method or class.
     * Format: "resource:action:scope" (e.g., "event:read:all")
     * 
     * @return The permission string
     */
    String value();
    
    /**
     * Optional: Specifies if all listed permissions are required (AND) or any one of them (OR).
     * Default is false (any one permission is sufficient).
     * 
     * @return true if all permissions are required, false otherwise
     */
    boolean requireAll() default false;
    
    /**
     * Optional: Alternative to value() for specifying multiple permissions.
     * 
     * @return Array of permission strings
     */
    String[] anyOf() default {};
}
