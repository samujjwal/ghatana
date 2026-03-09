package com.ghatana.platform.core.exception;

import java.util.Map;

/**
 * Exception thrown when a service operation fails.
 *
 * <p>Generic service-level exception for non-specific failures.
 * Use specialized exceptions ({@link ValidationException}, {@link ResourceNotFoundException})
 * when the error type is known.
 *
 * @doc.type exception
 * @doc.purpose Generic service-level exception for operation failures
 * @doc.layer core
 * @doc.pattern Exception, Generic Holder
 */
public class ServiceException extends BaseException {

    /**
     * Creates a new ServiceException.
     */
    public ServiceException() {
        super(ErrorCode.SERVICE_ERROR);
    }

    /**
     * Creates a new ServiceException with the specified message.
     *
     * @param message The error message
     */
    public ServiceException(String message) {
        super(ErrorCode.SERVICE_ERROR, message);
    }

    /**
     * Creates a new ServiceException with the specified message and cause.
     *
     * @param message The error message
     * @param cause The cause
     */
    public ServiceException(String message, Throwable cause) {
        super(ErrorCode.SERVICE_ERROR, message, cause);
    }

    /**
     * Creates a new ServiceException with the specified cause.
     *
     * @param cause The cause
     */
    public ServiceException(Throwable cause) {
        super(ErrorCode.SERVICE_ERROR, cause);
    }

    /**
     * Creates a new ServiceException with the specified message, cause, and metadata.
     *
     * @param message The error message
     * @param cause The cause
     * @param metadata The metadata
     */
    public ServiceException(String message, Throwable cause, Map<String, Object> metadata) {
        super(ErrorCode.SERVICE_ERROR, message, cause, metadata);
    }

    /**
     * Creates a new ServiceException with a specific error code.
     *
     * @param errorCode The error code
     * @param message The error message
     */
    public ServiceException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * Creates a new ServiceException with a specific error code and cause.
     *
     * @param errorCode The error code
     * @param message The error message
     * @param cause The cause
     */
    public ServiceException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    /**
     * Creates a new ServiceException for the specified service and operation.
     *
     * @param serviceName The service name
     * @param operation The operation name
     * @param cause The cause
     * @return The exception
     */
    public static ServiceException forService(String serviceName, String operation, Throwable cause) {
        String message = String.format("Service '%s' failed during operation '%s'", serviceName, operation);
        ServiceException exception = new ServiceException(message, cause);
        exception.addMetadata("serviceName", serviceName);
        exception.addMetadata("operation", operation);
        return exception;
    }

    /**
     * Creates a new ServiceException for a service that is unavailable.
     *
     * @param serviceName The service name
     * @return The exception
     */
    public static ServiceException serviceUnavailable(String serviceName) {
        String message = String.format("Service '%s' is unavailable", serviceName);
        ServiceException exception = new ServiceException(ErrorCode.SERVICE_UNAVAILABLE, message);
        exception.addMetadata("serviceName", serviceName);
        return exception;
    }

    /**
     * Creates a new ServiceException for a service timeout.
     *
     * @param serviceName The service name
     * @param operation The operation name
     * @param timeoutMs The timeout in milliseconds
     * @return The exception
     */
    public static ServiceException timeout(String serviceName, String operation, long timeoutMs) {
        String message = String.format("Service '%s' timed out after %d ms during operation '%s'", 
                serviceName, timeoutMs, operation);
        ServiceException exception = new ServiceException(ErrorCode.SERVICE_TIMEOUT, message);
        exception.addMetadata("serviceName", serviceName);
        exception.addMetadata("operation", operation);
        exception.addMetadata("timeoutMs", timeoutMs);
        return exception;
    }
}
