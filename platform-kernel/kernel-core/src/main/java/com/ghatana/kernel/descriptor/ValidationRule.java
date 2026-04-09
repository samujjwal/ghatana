package com.ghatana.kernel.descriptor;

import java.util.Objects;

/**
 * Defines validation rules for kernel components.
 *
 * <p>Validation rules specify constraints that must be satisfied for a kernel
 * component to be considered valid. These include configuration validation,
 * dependency validation, and custom business rule validation.</p>
 *
 * @doc.type class
 * @doc.purpose Validation rule definition for kernel component constraints
 * @doc.layer core
 * @doc.pattern ValueObject
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public final class ValidationRule {

    private final String ruleId;
    private final String name;
    private final String description;
    private final ValidationType type;
    private final String constraint;
    private final boolean required;
    private final String errorMessage;

    /**
     * Creates a validation rule.
     *
     * @param ruleId unique rule identifier
     * @param name human-readable name
     * @param description detailed description
     * @param type the validation type
     * @param constraint the constraint specification
     * @param required whether this rule is required
     * @param errorMessage error message when validation fails
     */
    public ValidationRule(String ruleId, String name, String description,
                           ValidationType type, String constraint,
                           boolean required, String errorMessage) {
        if (ruleId == null || ruleId.trim().isEmpty()) {
            throw new IllegalArgumentException("ruleId cannot be null or empty");
        }
        this.ruleId = ruleId;
        this.name = name != null ? name : ruleId;
        this.description = description != null ? description : "";
        this.type = type != null ? type : ValidationType.CUSTOM;
        this.constraint = constraint != null ? constraint : "";
        this.required = required;
        this.errorMessage = errorMessage != null ? errorMessage : "Validation failed: " + ruleId;
    }

    // Getters
    public String getRuleId() { return ruleId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public ValidationType getType() { return type; }
    public String getConstraint() { return constraint; }
    public boolean isRequired() { return required; }
    public String getErrorMessage() { return errorMessage; }

    /**
     * Validates a value against this rule's constraint.
     *
     * @param value the value to validate
     * @return true if valid
     */
    public boolean validate(Object value) {
        if (value == null) {
            return !required;
        }

        switch (type) {
            case NOT_NULL:
                return value != null;
            case NOT_EMPTY:
                return value.toString() != null && !value.toString().isEmpty();
            case REGEX:
                return value.toString().matches(constraint);
            case RANGE:
                return validateRange(value);
            case ENUM:
                return validateEnum(value);
            case CUSTOM:
                return true; // Custom validation handled externally
            default:
                return true;
        }
    }

    private boolean validateRange(Object value) {
        try {
            String[] parts = constraint.split(",");
            double min = Double.parseDouble(parts[0]);
            double max = Double.parseDouble(parts[1]);
            double val = Double.parseDouble(value.toString());
            return val >= min && val <= max;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean validateEnum(Object value) {
        String[] allowed = constraint.split(",");
        for (String allowedValue : allowed) {
            if (allowedValue.trim().equals(value.toString())) {
                return true;
            }
        }
        return false;
    }

    // Object methods
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ValidationRule that = (ValidationRule) o;
        return Objects.equals(ruleId, that.ruleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ruleId);
    }

    @Override
    public String toString() {
        return String.format("ValidationRule{id='%s', name='%s', type=%s}",
            ruleId, name, type);
    }

    public enum ValidationType {
        NOT_NULL,
        NOT_EMPTY,
        REGEX,
        RANGE,
        ENUM,
        CUSTOM
    }
}
