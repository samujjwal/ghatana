package com.ghatana.platform.core.exception;

import java.util.Map;

/**
 * Exception thrown when a resource is not found.
 *
 * <p>Base exception for 404 scenarios. Use specialized subclasses like
 * {@link EventNotFoundException} for specific resource types.
 *
 * @doc.type exception
 * @doc.purpose Base exception for resource not found (404) scenarios
 * @doc.layer core
 * @doc.pattern Exception, Base Class
 */
public class ResourceNotFoundException extends BaseException {

    /**
     * Creates a new ResourceNotFoundException.
     */
    public ResourceNotFoundException() {
        super(ErrorCode.RESOURCE_NOT_FOUND);
    }

    /**
     * Creates a new ResourceNotFoundException with the specified message.
     *
     * @param message The error message
     */
    public ResourceNotFoundException(String message) {
        super(ErrorCode.RESOURCE_NOT_FOUND, message);
    }

    /**
     * Creates a new ResourceNotFoundException with the specified message and cause.
     *
     * @param message The error message
     * @param cause The cause
     */
    public ResourceNotFoundException(String message, Throwable cause) {
        super(ErrorCode.RESOURCE_NOT_FOUND, message, cause);
    }

    /**
     * Creates a new ResourceNotFoundException with the specified cause.
     *
     * @param cause The cause
     */
    public ResourceNotFoundException(Throwable cause) {
        super(ErrorCode.RESOURCE_NOT_FOUND, cause);
    }

    /**
     * Creates a new ResourceNotFoundException with the specified message, cause, and metadata.
     *
     * @param message The error message
     * @param cause The cause
     * @param metadata The metadata
     */
    public ResourceNotFoundException(String message, Throwable cause, Map<String, Object> metadata) {
        super(ErrorCode.RESOURCE_NOT_FOUND, message, cause, metadata);
    }

    /**
     * Creates a new ResourceNotFoundException for the specified resource type and ID.
     *
     * @param resourceType The resource type
     * @param resourceId The resource ID
     * @return The exception
     */
    public static ResourceNotFoundException forResource(String resourceType, String resourceId) {
        String message = String.format("%s with ID %s not found", resourceType, resourceId);
        ResourceNotFoundException exception = new ResourceNotFoundException(message);
        exception.addMetadata("resourceType", resourceType);
        exception.addMetadata("resourceId", resourceId);
        return exception;
    }
}
