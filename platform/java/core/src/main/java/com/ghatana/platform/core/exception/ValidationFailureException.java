package com.ghatana.platform.core.exception;

/**
 * Exception thrown when validation fails.
 * This exception wraps validation results and provides access to detailed validation errors.
 *
 * <p>Carries transient validation result object for error introspection.
 *
 * @doc.type exception
 * @doc.purpose Validation failure exception with detailed result encapsulation
 * @doc.layer core
 * @doc.pattern Exception, Data Holder
 */
public class ValidationFailureException extends RuntimeException {
    
    private final transient Object validationResult;
    
    /**
     * Creates a new ValidationFailureException with the specified validation result.
     *
     * @param validationResult The validation result containing error details
     */
    public ValidationFailureException(Object validationResult) {
        super("Validation failed");
        this.validationResult = validationResult;
    }
    
    /**
     * Creates a new ValidationFailureException with a custom message.
     *
     * @param message The error message
     */
    public ValidationFailureException(String message) {
        super(message);
        this.validationResult = null;
    }
    
    /**
     * Gets the validation result.
     *
     * @return The validation result
     */
    public Object getValidationResult() {
        return validationResult;
    }
}
