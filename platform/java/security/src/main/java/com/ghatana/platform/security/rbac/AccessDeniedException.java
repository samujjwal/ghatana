package com.ghatana.platform.security.rbac;

import com.ghatana.platform.core.exception.BaseException;
import com.ghatana.platform.core.exception.ErrorCode;

import java.util.Map;

/**
 * Exception thrown when access is denied due to insufficient permissions.
 
 *
 * @doc.type class
 * @doc.purpose Access denied exception
 * @doc.layer core
 * @doc.pattern Exception
*/
public class AccessDeniedException extends BaseException {

    /**
     * Creates a new AccessDeniedException.
     */
    public AccessDeniedException() {
        super(ErrorCode.AUTHORIZATION_ERROR);
    }

    /**
     * Creates a new AccessDeniedException with the specified message.
     *
     * @param message The error message
     */
    public AccessDeniedException(String message) {
        super(ErrorCode.AUTHORIZATION_ERROR, message);
    }

    /**
     * Creates a new AccessDeniedException with the specified message and cause.
     *
     * @param message The error message
     * @param cause The cause
     */
    public AccessDeniedException(String message, Throwable cause) {
        super(ErrorCode.AUTHORIZATION_ERROR, message, cause);
    }

    /**
     * Creates a new AccessDeniedException with the specified cause.
     *
     * @param cause The cause
     */
    public AccessDeniedException(Throwable cause) {
        super(ErrorCode.AUTHORIZATION_ERROR, cause);
    }

    /**
     * Creates a new AccessDeniedException with the specified message, cause, and metadata.
     *
     * @param message The error message
     * @param cause The cause
     * @param metadata The metadata
     */
    public AccessDeniedException(String message, Throwable cause, Map<String, Object> metadata) {
        super(ErrorCode.AUTHORIZATION_ERROR, message, cause, metadata);
    }

    /**
     * Creates a new AccessDeniedException for the specified role, resource, and permission.
     *
     * @param role The role
     * @param resource The resource
     * @param permission The permission
     * @return The exception
     */
    public static AccessDeniedException forPermission(String role, String resource, String permission) {
        String message = String.format("Access denied: %s does not have permission %s for resource %s", role, permission, resource);
        AccessDeniedException exception = new AccessDeniedException(message);
        exception.addMetadata("role", role);
        exception.addMetadata("resource", resource);
        exception.addMetadata("permission", permission);
        return exception;
    }
}
