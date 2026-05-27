package com.ghatana.phr.api.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Request DTO validator for PHR API endpoints.
 *
 * <p>Provides validation for request DTOs using Jakarta Bean Validation annotations.
 * This ensures consistent validation across all API endpoints and provides
 * descriptive error messages for invalid requests.
 *
 * @doc.type class
 * @doc.purpose Validates request DTOs using Bean Validation
 * @doc.layer product
 * @doc.pattern Validator
 */
public final class PhrRequestValidator {

    private static final Validator VALIDATOR;

    static {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            VALIDATOR = factory.getValidator();
        }
    }

    private PhrRequestValidator() {
        // Utility class - prevent instantiation
    }

    /**
     * Validates a request DTO and throws an exception if validation fails.
     *
     * @param dto the request DTO to validate
     * @param dtoName the name of the DTO for error messages
     * @throws IllegalArgumentException if validation fails with constraint violations
     */
    public static void validate(Object dto, String dtoName) {
        Set<ConstraintViolation<Object>> violations = VALIDATOR.validate(dto);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining(", "));
            throw new IllegalArgumentException("Invalid " + dtoName + ": " + message);
        }
    }

    /**
     * Validates a request DTO and returns true if valid, false otherwise.
     *
     * @param dto the request DTO to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValid(Object dto) {
        return VALIDATOR.validate(dto).isEmpty();
    }

    /**
     * Returns constraint violations for a request DTO.
     *
     * @param dto the request DTO to validate
     * @return set of constraint violations, empty if valid
     */
    public static Set<ConstraintViolation<Object>> getViolations(Object dto) {
        return VALIDATOR.validate(dto);
    }
}
