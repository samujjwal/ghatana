package com.ghatana.refactorer.server.error;

import com.ghatana.platform.core.exception.ErrorCode;
import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.refactorer.server.dto.RestModels;
import io.activej.http.HttpResponse;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Centralized exception handler for Refactorer service.

 * Implements Binding Decision #3 enforcement (exception abstraction).

 *

 * <p>Converts service exceptions to HTTP responses with canonical ErrorCode mappings

 * for consistent error reporting across all REST endpoints.

 *

 * <p>Satisfies: All exceptions use core/common-utils ErrorCode patterns (no direct HTTP response construction).

 *

 * @doc.type class

 * @doc.purpose Translate service exceptions into canonical ErrorCode HTTP responses.

 * @doc.layer product

 * @doc.pattern Exception Mapper

 */

public final class ExceptionHandler {
    private static final Logger logger = LogManager.getLogger(ExceptionHandler.class);

    private ExceptionHandler() {
        // Utility class
    }

    /**
     * Handles any exception and converts to HTTP response with appropriate error code.
     *
     * @param exception The exception to handle
     * @param correlationId Correlation ID for request tracking
     * @return HttpResponse with error details
     */
    public static HttpResponse handle(Exception exception, String correlationId) {
        if (exception instanceof ValidationException validationEx) {
            return handleValidationException(validationEx, correlationId);
        }

        if (exception instanceof AuthenticationException authEx) {
            return handleAuthenticationException(authEx, correlationId);
        }

        if (exception instanceof AuthorizationException authzEx) {
            return handleAuthorizationException(authzEx, correlationId);
        }

        if (exception instanceof ResourceNotFoundException notFoundEx) {
            return handleResourceNotFoundException(notFoundEx, correlationId);
        }

        if (exception instanceof ServiceException serviceEx) {
            return handleServiceException(serviceEx, correlationId);
        }

        // Default: unhandled exception
        logger.error("Unhandled exception in request {}", correlationId, exception);
        return createErrorResponse(
                ErrorCode.UNKNOWN_ERROR,
                "An unexpected error occurred",
                correlationId);
    }

    /**
     * Handles validation exceptions.
     */
    private static HttpResponse handleValidationException(
            ValidationException ex, String correlationId) {
        logger.debug("Validation error in request {}: {}", correlationId, ex.getMessage());
        return createErrorResponse(ErrorCode.VALIDATION_ERROR, ex.getMessage(), correlationId);
    }

    /**
     * Handles authentication exceptions.
     */
    private static HttpResponse handleAuthenticationException(
            AuthenticationException ex, String correlationId) {
        logger.debug("Authentication error in request {}: {}", correlationId, ex.getMessage());
        return createErrorResponse(
                ErrorCode.AUTHENTICATION_ERROR, ex.getMessage(), correlationId);
    }

    /**
     * Handles authorization exceptions.
     */
    private static HttpResponse handleAuthorizationException(
            AuthorizationException ex, String correlationId) {
        logger.debug("Authorization error in request {}: {}", correlationId, ex.getMessage());
        return createErrorResponse(
                ErrorCode.AUTHORIZATION_ERROR, ex.getMessage(), correlationId);
    }

    /**
     * Handles resource not found exceptions.
     */
    private static HttpResponse handleResourceNotFoundException(
            ResourceNotFoundException ex, String correlationId) {
        logger.debug(
                "Resource not found in request {}: {}", correlationId, ex.getMessage());
        return createErrorResponse(
                ErrorCode.RESOURCE_NOT_FOUND, ex.getMessage(), correlationId);
    }

    /**
     * Handles service exceptions.
     */
    private static HttpResponse handleServiceException(ServiceException ex, String correlationId) {
        logger.error("Service error in request {}: {}", correlationId, ex.getMessage());
        return createErrorResponse(
                ex.getErrorCode(), ex.getMessage(), correlationId);
    }

    /**
     * Creates an error response using ResponseBuilder with ErrorCode mapping.
     *
     * @param errorCode The canonical error code
     * @param message Custom error message
     * @param correlationId Correlation ID for tracking
     * @return HttpResponse with error details
     */
    private static HttpResponse createErrorResponse(
            ErrorCode errorCode, String message, String correlationId) {
        RestModels.ErrorResponse errorResponse =
                new RestModels.ErrorResponse(
                        errorCode.getCode(),
                        message != null ? message : errorCode.getDefaultMessage(),
                        errorCode.name(),
                        correlationId);

        return ResponseBuilder.status(errorCode.getHttpStatus())
                .json(errorResponse)
                .build();
    }

    /**
     * Validation exception for request validation failures.
     */
    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }

        public ValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Authentication exception for missing/invalid credentials.
     */
    public static class AuthenticationException extends RuntimeException {
        public AuthenticationException(String message) {
            super(message);
        }

        public AuthenticationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Authorization exception for insufficient permissions.
     */
    public static class AuthorizationException extends RuntimeException {
        public AuthorizationException(String message) {
            super(message);
        }

        public AuthorizationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Resource not found exception.
     */
    public static class ResourceNotFoundException extends RuntimeException {
        private final String resourceId;

        public ResourceNotFoundException(String message, String resourceId) {
            super(message);
            this.resourceId = resourceId;
        }

        public String getResourceId() {
            return resourceId;
        }
    }

    /**
     * Service exception for domain-specific errors.
     */
    public static class ServiceException extends RuntimeException {
        private final ErrorCode errorCode;

        public ServiceException(ErrorCode errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        public ServiceException(ErrorCode errorCode, String message, Throwable cause) {
            super(message, cause);
            this.errorCode = errorCode;
        }

        public ErrorCode getErrorCode() {
            return errorCode;
        }
    }
}
