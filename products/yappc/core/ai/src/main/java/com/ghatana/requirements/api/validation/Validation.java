package com.ghatana.requirements.api.validation;

import com.ghatana.requirements.api.error.ErrorResponse;
import io.activej.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Request validation utility for common validation patterns.
 *
 * <p><b>Purpose</b><br>
 * Provides reusable validation methods for HTTP requests and common data types.
 * Throws standardized exceptions for validation failures.
 *
 * <p><b>Validation Categories</b><br>
 * - **UUID Validation:** Validates UUID format strings
 * - **String Validation:** Length, pattern, and null checks
 * - **Numeric Validation:** Range and type checks
 * - **Request Validation:** Header, parameter, and body validation
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Validate path parameter
 * String workspaceId = Validation.requireValidUuid(
 *     request.getPathParameter("workspaceId"), "workspaceId"
 * );
 * 
 * // Validate request body
 * Validation.requireNonNull(body, "request body");
 * Validation.requireValidLength(name, "name", 1, 100);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Request validation utility
 * @doc.layer product
 * @doc.pattern Utility
 * @since 1.0.0
 */
public final class Validation {
    private static final Logger logger = LoggerFactory.getLogger(Validation.class);
    
    // Common patterns
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );
    
    private static final Pattern USERNAME_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._-]{3,30}$"
    );
    
    private static final Pattern WORKSPACE_NAME_PATTERN = Pattern.compile(
        "^[\\w\\s\\-_.()]{1,100}$"
    );
    
    private static final Pattern PROJECT_NAME_PATTERN = Pattern.compile(
        "^[\\w\\s\\-_.()]{1,100}$"
    );
    
    private static final Pattern REQUIREMENT_TITLE_PATTERN = Pattern.compile(
        "^[\\w\\s\\-_.()!?]{1,200}$"
    );
    
    private Validation() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Require that a value is not null.
     *
     * @param value the value to check
     * @param fieldName the field name for error messages
     * @throws ErrorResponse.ValidationException if value is null
     */
    public static void requireNonNull(Object value, String fieldName) throws ErrorResponse.ValidationException {
        if (value == null) {
            throw new ErrorResponse.ValidationException(
                fieldName + " is required",
                Map.of("field", fieldName, "error", "NULL_VALUE")
            );
        }
    }
    
    /**
     * Require that a string is not null or empty.
     *
     * @param value the string to check
     * @param fieldName the field name for error messages
     * @throws ErrorResponse.ValidationException if string is null or empty
     */
    public static void requireNonEmpty(String value, String fieldName) throws ErrorResponse.ValidationException {
        requireNonNull(value, fieldName);
        if (value.trim().isEmpty()) {
            throw new ErrorResponse.ValidationException(
                fieldName + " cannot be empty",
                Map.of("field", fieldName, "error", "EMPTY_STRING")
            );
        }
    }
    
    /**
     * Validate and parse a UUID string.
     *
     * @param uuidString the UUID string to validate
     * @param fieldName the field name for error messages
     * @return parsed UUID
     * @throws ErrorResponse.ValidationException if UUID is invalid
     */
    public static UUID requireValidUuid(String uuidString, String fieldName) throws ErrorResponse.ValidationException {
        requireNonEmpty(uuidString, fieldName);
        
        try {
            return UUID.fromString(uuidString);
        } catch (IllegalArgumentException e) {
            throw new ErrorResponse.ValidationException(
                fieldName + " must be a valid UUID",
                Map.of("field", fieldName, "value", uuidString, "error", "INVALID_UUID")
            );
        }
    }
    
    /**
     * Validate string length.
     *
     * @param value the string to validate
     * @param fieldName the field name for error messages
     * @param minLength minimum allowed length (inclusive)
     * @param maxLength maximum allowed length (inclusive)
     * @throws ErrorResponse.ValidationException if length is invalid
     */
    public static void requireValidLength(String value, String fieldName, int minLength, int maxLength) 
            throws ErrorResponse.ValidationException {
        requireNonNull(value, fieldName);
        
        int length = value.length();
        if (length < minLength || length > maxLength) {
            throw new ErrorResponse.ValidationException(
                fieldName + " must be between " + minLength + " and " + maxLength + " characters",
                Map.of("field", fieldName, "minLength", minLength, "maxLength", maxLength, 
                       "actualLength", length, "error", "INVALID_LENGTH")
            );
        }
    }
    
    /**
     * Validate email format.
     *
     * @param email the email to validate
     * @param fieldName the field name for error messages
     * @throws ErrorResponse.ValidationException if email is invalid
     */
    public static void requireValidEmail(String email, String fieldName) throws ErrorResponse.ValidationException {
        requireNonEmpty(email, fieldName);
        
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new ErrorResponse.ValidationException(
                fieldName + " must be a valid email address",
                Map.of("field", fieldName, "value", email, "error", "INVALID_EMAIL")
            );
        }
    }
    
    /**
     * Validate username format.
     *
     * @param username the username to validate
     * @param fieldName the field name for error messages
     * @throws ErrorResponse.ValidationException if username is invalid
     */
    public static void requireValidUsername(String username, String fieldName) throws ErrorResponse.ValidationException {
        requireNonEmpty(username, fieldName);
        
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new ErrorResponse.ValidationException(
                fieldName + " must be 3-30 characters and contain only letters, numbers, dots, underscores, and hyphens",
                Map.of("field", fieldName, "value", username, "error", "INVALID_USERNAME")
            );
        }
    }
    
    /**
     * Validate workspace name format.
     *
     * @param name the workspace name to validate
     * @param fieldName the field name for error messages
     * @throws ErrorResponse.ValidationException if name is invalid
     */
    public static void requireValidWorkspaceName(String name, String fieldName) throws ErrorResponse.ValidationException {
        requireValidLength(name, fieldName, 1, 100);
        
        if (!WORKSPACE_NAME_PATTERN.matcher(name).matches()) {
            throw new ErrorResponse.ValidationException(
                fieldName + " contains invalid characters. Only letters, numbers, spaces, and basic punctuation are allowed",
                Map.of("field", fieldName, "value", name, "error", "INVALID_WORKSPACE_NAME")
            );
        }
    }
    
    /**
     * Validate project name format.
     *
     * @param name the project name to validate
     * @param fieldName the field name for error messages
     * @throws ErrorResponse.ValidationException if name is invalid
     */
    public static void requireValidProjectName(String name, String fieldName) throws ErrorResponse.ValidationException {
        requireValidLength(name, fieldName, 1, 100);
        
        if (!PROJECT_NAME_PATTERN.matcher(name).matches()) {
            throw new ErrorResponse.ValidationException(
                fieldName + " contains invalid characters. Only letters, numbers, spaces, and basic punctuation are allowed",
                Map.of("field", fieldName, "value", name, "error", "INVALID_PROJECT_NAME")
            );
        }
    }
    
    /**
     * Validate requirement title format.
     *
     * @param title the requirement title to validate
     * @param fieldName the field name for error messages
     * @throws ErrorResponse.ValidationException if title is invalid
     */
    public static void requireValidRequirementTitle(String title, String fieldName) throws ErrorResponse.ValidationException {
        requireValidLength(title, fieldName, 1, 200);
        
        if (!REQUIREMENT_TITLE_PATTERN.matcher(title).matches()) {
            throw new ErrorResponse.ValidationException(
                fieldName + " contains invalid characters. Only letters, numbers, spaces, and basic punctuation are allowed",
                Map.of("field", fieldName, "value", title, "error", "INVALID_REQUIREMENT_TITLE")
            );
        }
    }
    
    /**
     * Validate requirement description.
     *
     * @param description the requirement description to validate
     * @param fieldName the field name for error messages
     * @throws ErrorResponse.ValidationException if description is invalid
     */
    public static void requireValidRequirementDescription(String description, String fieldName) 
            throws ErrorResponse.ValidationException {
        requireValidLength(description, fieldName, 1, 2000);
    }
    
    /**
     * Validate that a value is within a numeric range.
     *
     * @param value the value to validate
     * @param fieldName the field name for error messages
     * @param min minimum allowed value (inclusive)
     * @param max maximum allowed value (inclusive)
     * @throws ErrorResponse.ValidationException if value is out of range
     */
    public static void requireValidRange(int value, String fieldName, int min, int max) 
            throws ErrorResponse.ValidationException {
        if (value < min || value > max) {
            throw new ErrorResponse.ValidationException(
                fieldName + " must be between " + min + " and " + max,
                Map.of("field", fieldName, "min", min, "max", max, 
                       "actualValue", value, "error", "OUT_OF_RANGE")
            );
        }
    }
    
    /**
     * Validate that a list is not null and within size limits.
     *
     * @param list the list to validate
     * @param fieldName the field name for error messages
     * @param maxSize maximum allowed size
     * @throws ErrorResponse.ValidationException if list is invalid
     */
    public static void requireValidListSize(List<?> list, String fieldName, int maxSize) 
            throws ErrorResponse.ValidationException {
        requireNonNull(list, fieldName);
        
        if (list.size() > maxSize) {
            throw new ErrorResponse.ValidationException(
                fieldName + " cannot contain more than " + maxSize + " items",
                Map.of("field", fieldName, "maxSize", maxSize, 
                       "actualSize", list.size(), "error", "LIST_TOO_LARGE")
            );
        }
    }
    
    /**
     * Validate that an enum value is valid.
     *
     * @param value the enum value to validate
     * @param fieldName the field name for error messages
     * @param validValues array of valid enum values
     * @throws ErrorResponse.ValidationException if value is invalid
     */
    public static void requireValidEnum(String value, String fieldName, String[] validValues) 
            throws ErrorResponse.ValidationException {
        requireNonEmpty(value, fieldName);
        
        for (String validValue : validValues) {
            if (validValue.equals(value)) {
                return; // Valid value found
            }
        }
        
        throw new ErrorResponse.ValidationException(
            fieldName + " must be one of: " + String.join(", ", validValues),
            Map.of("field", fieldName, "value", value, "validValues", validValues, 
                   "error", "INVALID_ENUM_VALUE")
        );
    }
    
    /**
     * Validate HTTP request path parameter.
     *
     * @param request the HTTP request
     * @param parameterName the parameter name
     * @return the parameter value
     * @throws ErrorResponse.ValidationException if parameter is missing or invalid
     */
    public static String requirePathParameter(HttpRequest request, String parameterName) 
            throws ErrorResponse.ValidationException {
        String value;
        try {
            value = request.getPathParameter(parameterName);
        } catch (IllegalArgumentException e) {
            // Path parameter not found by routing framework (e.g., direct controller call in tests)
            value = null;
        }
        
        // Fallback for testing: extract from URL path if routing hasn't parsed parameters
        if (value == null) {
            String path = request.getPath();
            if (path != null) {
                String[] parts = path.split("/");
                if (parameterName.equals("id")) {
                    // Look for segment after known resource names first
                    for (int i = 0; i < parts.length - 1; i++) {
                        if (parts[i].equals("requirements") || parts[i].equals("suggestions")
                                || parts[i].equals("workspaces") || parts[i].equals("projects")) {
                            value = parts[i + 1];
                            break;
                        }
                    }
                    // Fallback: take last segment
                    if (value == null && parts.length > 0) {
                        value = parts[parts.length - 1];
                    }
                } else if (parameterName.endsWith("Id")) {
                    // Convert "projectId" -> "projects", "workspaceId" -> "workspaces"
                    String resourceName = parameterName.substring(0, parameterName.length() - 2) + "s";
                    for (int i = 0; i < parts.length - 1; i++) {
                        if (parts[i].equals(resourceName)) {
                            value = parts[i + 1];
                            break;
                        }
                    }
                }
            }
        }
        
        requireNonEmpty(value, parameterName);
        return value;
    }
    
    /**
     * Validate HTTP request header.
     *
     * @param request the HTTP request
     * @param headerName the header name
     * @return the header value
     * @throws ErrorResponse.ValidationException if header is missing or invalid
     */
    public static String requireHeader(HttpRequest request, String headerName) 
            throws ErrorResponse.ValidationException {
        String value = request.getHeader(io.activej.http.HttpHeaders.of(headerName));
        requireNonEmpty(value, headerName);
        return value;
    }
    
    /**
     * Validate multiple fields and collect all validation errors.
     *
     * @param validations list of validation functions to execute
     * @throws ErrorResponse.ValidationException if any validation fails
     */
    public static void validateAll(List<Runnable> validations) throws ErrorResponse.ValidationException {
        List<Map<String, Object>> errors = new ArrayList<>();
        
        for (Runnable validation : validations) {
            try {
                validation.run();
            } catch (RuntimeException e) {
                // Unwrap to check if it's a ValidationException
                Throwable cause = e.getCause();
                if (cause instanceof ErrorResponse.ValidationException validationException) {
                    if (validationException.getDetails() != null) {
                        errors.add(validationException.getDetails());
                    }
                } else {
                    // Re-throw other runtime exceptions
                    throw e;
                }
            }
        }
        
        if (!errors.isEmpty()) {
            throw new ErrorResponse.ValidationException(
                "Multiple validation errors occurred",
                Map.of("errors", errors, "error", "MULTIPLE_VALIDATION_ERRORS")
            );
        }
    }
}
