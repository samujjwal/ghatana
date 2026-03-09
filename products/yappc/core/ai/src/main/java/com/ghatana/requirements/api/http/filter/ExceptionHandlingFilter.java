package com.ghatana.requirements.api.http.filter;

import com.ghatana.requirements.api.error.ErrorResponse;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.Function;

/**
 * Global exception handling filter for consistent error responses.
 *
 * <p><b>Purpose</b><br>
 * Catches all exceptions from servlet execution and converts them to
 * standardized error responses with proper HTTP status codes and logging.
 *
 * <p><b>Features</b><br>
 * - Catches all runtime exceptions and errors
 * - Converts exceptions to standardized error responses
 * - Comprehensive error logging with context
 * - Prevents stack trace leakage to clients
 * - Maintains request/response correlation
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * AsyncServlet servlet = new ExceptionHandlingFilter()
 *     .wrap(controllerServlet);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Global exception handling filter
 * @doc.layer product
 * @doc.pattern Filter
 * @since 1.0.0
 */
public final class ExceptionHandlingFilter {
    private static final Logger logger = LoggerFactory.getLogger(ExceptionHandlingFilter.class);
    
    /**
     * Wrap an async servlet with global exception handling.
     *
     * @param servlet the servlet to wrap
     * @return wrapped servlet that handles exceptions
     */
    public Function<HttpRequest, Promise<HttpResponse>> wrap(AsyncServlet servlet) {
        return request -> handle(request, servlet);
    }
    
    /**
     * Handle request with exception catching.
     *
     * @param request the HTTP request
     * @param servlet the next handler
     * @return promise of response (error response if exception occurs)
     */
    private Promise<HttpResponse> handle(HttpRequest request, AsyncServlet servlet) {
        try {
            return servlet.serve(request)
                .then(response -> {
                    // Response completed successfully
                    return Promise.of(response);
                })
                .whenException(throwable -> {
                    // Handle async exceptions - this will be caught by the outer try-catch
                    throw new RuntimeException(throwable);
                });
                
        } catch (Exception e) {
            // Handle synchronous exceptions
            return Promise.of(handleException(request, e));
        }
    }
    
    /**
     * Convert exception to appropriate error response.
     *
     * @param request the HTTP request (for logging context)
     * @param exception the exception that occurred
     * @return standardized error response
     */
    private HttpResponse handleException(HttpRequest request, Throwable exception) {
        String requestId = generateRequestId();
        String path = request.getPath();
        String method = request.getMethod().toString();
        
        // Log the exception with full context
        logger.error("Request failed: {} {} [requestId: {}]", method, path, requestId, exception);
        
        // Check for specific exception types
        if (exception instanceof ErrorResponse.ValidationException) {
            return ErrorResponse.fromException((ErrorResponse.ValidationException) exception);
        }
        
        if (exception instanceof ErrorResponse.AuthenticationException) {
            return ErrorResponse.fromException((ErrorResponse.AuthenticationException) exception);
        }
        
        if (exception instanceof ErrorResponse.AuthorizationException) {
            return ErrorResponse.fromException((ErrorResponse.AuthorizationException) exception);
        }
        
        if (exception instanceof ErrorResponse.NotFoundException) {
            return ErrorResponse.fromException((ErrorResponse.NotFoundException) exception);
        }
        
        if (exception instanceof ErrorResponse.ConflictException) {
            return ErrorResponse.fromException((ErrorResponse.ConflictException) exception);
        }
        
        if (exception instanceof ErrorResponse.RateLimitException) {
            return ErrorResponse.fromException((ErrorResponse.RateLimitException) exception);
        }
        
        // Handle common runtime exceptions
        if (exception instanceof IllegalArgumentException) {
            return ErrorResponse.badRequest(
                exception.getMessage(),
                "INVALID_ARGUMENT",
                Map.of("requestId", requestId)
            );
        }
        
        if (exception instanceof IllegalStateException) {
            return ErrorResponse.conflict(
                exception.getMessage(),
                "INVALID_STATE",
                Map.of("requestId", requestId)
            );
        }
        
        if (exception instanceof SecurityException) {
            return ErrorResponse.forbidden(
                "Access denied",
                "SECURITY_VIOLATION",
                Map.of("requestId", requestId)
            );
        }
        
        // Handle null pointer exceptions (often indicate programming errors)
        if (exception instanceof NullPointerException) {
            logger.error("Null pointer exception detected - programming error: {} {} [requestId: {}]", 
                method, path, requestId, exception);
            return ErrorResponse.internalServerError(
                "A system error occurred",
                "NULL_POINTER_ERROR",
                Map.of("requestId", requestId)
            );
        }
        
        // Handle all other exceptions
        return ErrorResponse.internalServerError(
            "An unexpected error occurred",
            "UNEXPECTED_ERROR",
            Map.of("requestId", requestId)
        );
    }
    
    /**
     * Generate a unique request ID for correlation.
     *
     * @return unique request identifier
     */
    private String generateRequestId() {
        return java.util.UUID.randomUUID().toString().substring(0, 8);
    }
    
    /**
     * Convert an async handler function to be wrapped by this filter.
     *
     * @param handler the handler function
     * @return async servlet that can be wrapped
     */
    public AsyncServlet toServlet(Function<HttpRequest, Promise<HttpResponse>> handler) {
        return handler::apply;
    }
}
