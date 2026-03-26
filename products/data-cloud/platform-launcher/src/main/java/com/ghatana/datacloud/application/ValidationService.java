package com.ghatana.datacloud.application;

import com.ghatana.datacloud.entity.DataType;
import com.ghatana.datacloud.entity.FieldValidation;
import com.ghatana.datacloud.entity.MetaField;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Validates entity data against field definitions and rules.
 *
 * <p><b>Purpose</b><br>
 * Provides field-level validation for dynamic entities based on MetaField definitions.
 * Supports type validation, constraints, and custom rules.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * ValidationService validator = new ValidationService();
 *
 * // Validate entity data
 * Map<String, Object> entity = Map.of(
 *     "name", "Product A",
 *     "price", 99.99,
 *     "email", "user@example.com"
 * );
 *
 * List<MetaField> fields = ...;
 * Promise<ValidationResult> result = validator.validate(entity, fields);
 *
 * // In test:
 * ValidationResult validation = runPromise(() -> result);
 * if (!validation.isValid()) {
 *     validation.getErrors().forEach(e -> System.out.println(e));
 * }
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - Validator in application layer
 * - Used by EntityService for entity validation
 * - Supports multiple validation types (type, constraint, format)
 * - Returns structured validation errors
 *
 * <p><b>Validation Types</b><br>
 * - Type validation (STRING, NUMBER, BOOLEAN, etc.)
 * - Constraint validation (required, min/max, length)
 * - Format validation (email, URL, phone, etc.)
 * - Custom validation rules
 *
 * <p><b>Thread Safety</b><br>
 * Stateless validator - thread-safe.
 *
 * @see MetaField
 * @see ValidationResult
 * @doc.type class
 * @doc.purpose Field-level validation for dynamic entities
 * @doc.layer product
 * @doc.pattern Validator (Application Layer)
 */
public class ValidationService {

    private static final Logger logger = LoggerFactory.getLogger(ValidationService.class);

    private static final Pattern EMAIL_PATTERN = 
        Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");
    private static final Pattern URL_PATTERN = 
        Pattern.compile("^https?://.*");
    private static final Pattern PHONE_PATTERN = 
        Pattern.compile("^[+]?[0-9]{1,3}[-. ]?[0-9]{1,4}[-. ]?[0-9]{1,4}[-. ]?[0-9]{1,4}$");
    private static final Pattern HEX_COLOR_PATTERN = 
        Pattern.compile("^#[0-9A-Fa-f]{6}$");

    /**
     * Validates entity data against field definitions.
     *
     * @param entity the entity data to validate (required)
     * @param fields the field definitions (required)
     * @return Promise of ValidationResult
     */
    public Promise<ValidationResult> validate(Map<String, Object> entity, List<MetaField> fields) {
        Objects.requireNonNull(entity, "Entity must not be null");
        Objects.requireNonNull(fields, "Fields must not be null");

        List<ValidationError> errors = new ArrayList<>();

        for (MetaField field : fields) {
            if (!field.getActive()) {
                continue; // Skip inactive fields
            }

            Object value = entity.get(field.getName());

            // Check required
            if (field.getRequired() && (value == null || isEmpty(value))) {
                errors.add(new ValidationError(
                    field.getName(),
                    "REQUIRED",
                    "Field is required"
                ));
                continue;
            }

            // Skip validation if value is null and not required
            if (value == null) {
                continue;
            }

            // Validate type
            List<ValidationError> typeErrors = validateType(field, value);
            errors.addAll(typeErrors);

            // Validate constraints
            if (typeErrors.isEmpty()) {
                List<ValidationError> constraintErrors = validateConstraints(field, value);
                errors.addAll(constraintErrors);
            }
        }

        logger.debug("Validation complete: errors={}", errors.size());
        return Promise.of(new ValidationResult(errors.isEmpty(), errors));
    }

    /**
     * Validates field type.
     *
     * @param field the field definition
     * @param value the value to validate
     * @return list of validation errors (empty if valid)
     */
    private List<ValidationError> validateType(MetaField field, Object value) {
        List<ValidationError> errors = new ArrayList<>();
        DataType type = field.getType();

        try {
            switch (type) {
                case STRING:
                    if (!(value instanceof String)) {
                        errors.add(new ValidationError(field.getName(), "TYPE_MISMATCH", 
                            "Expected string, got " + value.getClass().getSimpleName()));
                    }
                    break;

                case NUMBER:
                    if (!(value instanceof Number)) {
                        errors.add(new ValidationError(field.getName(), "TYPE_MISMATCH", 
                            "Expected number, got " + value.getClass().getSimpleName()));
                    }
                    break;

                case BOOLEAN:
                    if (!(value instanceof Boolean)) {
                        errors.add(new ValidationError(field.getName(), "TYPE_MISMATCH", 
                            "Expected boolean, got " + value.getClass().getSimpleName()));
                    }
                    break;

                case EMAIL:
                    if (!(value instanceof String) || !EMAIL_PATTERN.matcher((String) value).matches()) {
                        errors.add(new ValidationError(field.getName(), "INVALID_EMAIL", 
                            "Invalid email format"));
                    }
                    break;

                case URL:
                    if (!(value instanceof String) || !URL_PATTERN.matcher((String) value).matches()) {
                        errors.add(new ValidationError(field.getName(), "INVALID_URL", 
                            "Invalid URL format"));
                    }
                    break;

                case PHONE:
                    if (!(value instanceof String) || !PHONE_PATTERN.matcher((String) value).matches()) {
                        errors.add(new ValidationError(field.getName(), "INVALID_PHONE", 
                            "Invalid phone format"));
                    }
                    break;

                case COLOR:
                    if (!(value instanceof String) || !HEX_COLOR_PATTERN.matcher((String) value).matches()) {
                        errors.add(new ValidationError(field.getName(), "INVALID_COLOR", 
                            "Invalid color format (expected #RRGGBB)"));
                    }
                    break;

                case ARRAY:
                    if (!(value instanceof List)) {
                        errors.add(new ValidationError(field.getName(), "TYPE_MISMATCH", 
                            "Expected array, got " + value.getClass().getSimpleName()));
                    }
                    break;

                case EMBEDDED:
                case JSON:
                    if (!(value instanceof Map)) {
                        errors.add(new ValidationError(field.getName(), "TYPE_MISMATCH", 
                            "Expected object, got " + value.getClass().getSimpleName()));
                    }
                    break;

                default:
                    // Other types pass through
                    break;
            }
        } catch (Exception e) {
            logger.warn("Error validating type for field: {}", field.getName(), e);
            errors.add(new ValidationError(field.getName(), "VALIDATION_ERROR", e.getMessage()));
        }

        return errors;
    }

    /**
     * Validates field constraints.
     *
     * @param field the field definition
     * @param value the value to validate
     * @return list of validation errors (empty if valid)
     */
    private List<ValidationError> validateConstraints(MetaField field, Object value) {
        List<ValidationError> errors = new ArrayList<>();
        FieldValidation validation = field.getValidationTyped();

        if (validation == null) {
            return errors;
        }

        // Check min/max for numbers
        if (field.getType() == DataType.NUMBER && value instanceof Number) {
            Number num = (Number) value;
            if (validation.min() != null && num.doubleValue() < validation.min()) {
                errors.add(new ValidationError(field.getName(), "MIN_VALUE", 
                    "Value must be >= " + validation.min()));
            }
            if (validation.max() != null && num.doubleValue() > validation.max()) {
                errors.add(new ValidationError(field.getName(), "MAX_VALUE", 
                    "Value must be <= " + validation.max()));
            }
        }

        // Check length for strings
        if (field.getType() == DataType.STRING && value instanceof String) {
            String str = (String) value;
            if (validation.minLength() != null && str.length() < validation.minLength()) {
                errors.add(new ValidationError(field.getName(), "MIN_LENGTH", 
                    "Length must be >= " + validation.minLength()));
            }
            if (validation.maxLength() != null && str.length() > validation.maxLength()) {
                errors.add(new ValidationError(field.getName(), "MAX_LENGTH", 
                    "Length must be <= " + validation.maxLength()));
            }
            if (validation.pattern() != null && !str.matches(validation.pattern())) {
                errors.add(new ValidationError(field.getName(), "PATTERN_MISMATCH", 
                    "Value does not match pattern: " + validation.pattern()));
            }
        }

        return errors;
    }

    /**
     * Checks if a value is empty.
     *
     * @param value the value to check
     * @return true if empty
     */
    private boolean isEmpty(Object value) {
        if (value == null) return true;
        if (value instanceof String) return ((String) value).trim().isEmpty();
        if (value instanceof Collection) return ((Collection<?>) value).isEmpty();
        if (value instanceof Map) return ((Map<?, ?>) value).isEmpty();
        return false;
    }

    /**
     * Validation result containing errors.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<ValidationError> errors;

        /**
         * Creates a validation result.
         *
         * @param valid whether validation passed
         * @param errors list of validation errors
         */
        public ValidationResult(boolean valid, List<ValidationError> errors) {
            this.valid = valid;
            this.errors = Objects.requireNonNull(errors, "Errors must not be null");
        }

        /**
         * Checks if validation passed.
         *
         * @return true if valid
         */
        public boolean isValid() {
            return valid;
        }

        /**
         * Gets validation errors.
         *
         * @return list of errors
         */
        public List<ValidationError> getErrors() {
            return errors;
        }
    }

    /**
     * Validation error details.
     */
    public static class ValidationError {
        private final String field;
        private final String code;
        private final String message;

        /**
         * Creates a validation error.
         *
         * @param field the field name
         * @param code the error code
         * @param message the error message
         */
        public ValidationError(String field, String code, String message) {
            this.field = field;
            this.code = code;
            this.message = message;
        }

        /**
         * Gets the field name.
         *
         * @return field name
         */
        public String getField() {
            return field;
        }

        /**
         * Gets the error code.
         *
         * @return error code
         */
        public String getCode() {
            return code;
        }

        /**
         * Gets the error message.
         *
         * @return error message
         */
        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return "ValidationError{" +
                    "field='" + field + '\'' +
                    ", code='" + code + '\'' +
                    ", message='" + message + '\'' +
                    '}';
        }
    }
}
