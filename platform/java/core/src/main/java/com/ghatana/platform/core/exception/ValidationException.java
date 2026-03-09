package com.ghatana.platform.core.exception;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

/**
 * Exception thrown when validation fails.
 * 
 * <p>Carries a map of validation errors for each failed field or constraint.
 * Extends {@link BaseException} with error code and status mapping.
 *
 * @doc.type exception
 * @doc.purpose Input validation failure exception with field-level error tracking
 * @doc.layer core
 * @doc.pattern Exception, Data Holder
 */
public class ValidationException extends BaseException {

    private final Map<String, Object> validationErrors;

    /**
     * Creates a new ValidationException.
     */
    public ValidationException() {
        super(ErrorCode.VALIDATION_ERROR);
        this.validationErrors = new HashMap<>();
    }

    /**
     * Creates a new ValidationException with the specified message.
     *
     * @param message The error message
     */
    public ValidationException(String message) {
        super(ErrorCode.VALIDATION_ERROR, message);
        this.validationErrors = new HashMap<>();
    }

    /**
     * Creates a new ValidationException with the specified message and validation errors.
     *
     * @param message The error message
     * @param validationErrors The validation errors
     */
    public ValidationException(String message, Map<String, Object> validationErrors) {
        super(ErrorCode.VALIDATION_ERROR, message);
        this.validationErrors = new HashMap<>(validationErrors);
        addMetadata("validationErrors", validationErrors);
    }

    /**
     * Creates a new ValidationException with the specified message, cause, and validation errors.
     *
     * @param message The error message
     * @param cause The cause
     * @param validationErrors The validation errors
     */
    public ValidationException(String message, Throwable cause, Map<String, Object> validationErrors) {
        super(ErrorCode.VALIDATION_ERROR, message, cause);
        this.validationErrors = new HashMap<>(validationErrors);
        addMetadata("validationErrors", validationErrors);
    }

    /**
     * Gets the validation errors.
     *
     * @return The validation errors
     */
    public Map<String, Object> getValidationErrors() {
        return validationErrors;
    }

    /**
     * Adds a validation error.
     *
     * @param field The field name
     * @param error The error message
     * @return This exception
     */
    public ValidationException addValidationError(String field, Object error) {
        validationErrors.put(field, error);
        addMetadata("validationErrors", validationErrors);
        return this;
    }

    /**
     * Creates a new ValidationException for the specified field and error message.
     *
     * @param field The field name
     * @param errorMessage The error message
     * @return The exception
     */
    public static ValidationException forField(String field, String errorMessage) {
        Map<String, Object> errors = new HashMap<>();
        errors.put(field, errorMessage);
        return new ValidationException("Validation failed for field: " + field, errors);
    }

    /**
     * Creates a new ValidationException for multiple validation errors.
     *
     * @param errors The validation errors
     * @return The exception
     */
    public static ValidationException forErrors(Map<String, Object> errors) {
        return new ValidationException("Validation failed with " + errors.size() + " errors", errors);
    }
}
