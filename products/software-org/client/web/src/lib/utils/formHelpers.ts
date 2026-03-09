/**
 * Form Handling Utilities
 *
 * <p><b>Purpose</b><br>
 * Utility functions for form state management, validation, field handling,
 * error display, and form submission processing.
 *
 * <p><b>Functions</b><br>
 * - createFormState: Initialize form state
 * - validateForm: Validate form data
 * - validateField: Validate single field
 * - setFieldValue: Update field value
 * - setFieldError: Update field error
 * - resetForm: Reset form to initial state
 * - getFormErrors: Get all form errors
 * - isFormValid: Check form validity
 *
 * @doc.type utility
 * @doc.purpose Form state and validation utilities
 * @doc.layer product
 * @doc.pattern Utility Module
 */

/**
 * Field value type
 */
export type FieldValue = string | number | boolean | Date | null | undefined;

/**
 * Form field definition
 */
export interface FormField {
    value: FieldValue;
    error?: string;
    touched?: boolean;
    dirty?: boolean;
}

/**
 * Form state type
 */
export type FormState = Record<string, FormField>;

/**
 * Validation rule type
 */
export type ValidationRule = (value: FieldValue) => string | null;

/**
 * Form validation schema
 */
export type ValidationSchema = Record<string, ValidationRule | ValidationRule[]>;

/**
 * Form configuration
 */
export interface FormConfig {
    initialValues: Record<string, FieldValue>;
    validationSchema?: ValidationSchema;
    onSubmit?: (values: Record<string, FieldValue>) => void | Promise<void>;
}

/**
 * Create initial form state
 *
 * @param initialValues - Initial field values
 * @returns Form state object
 */
export function createFormState(
    initialValues: Record<string, FieldValue>
): FormState {
    const state: FormState = {};
    Object.entries(initialValues).forEach(([key, value]) => {
        state[key] = {
            value,
            error: undefined,
            touched: false,
            dirty: false,
        };
    });
    return state;
}

/**
 * Built-in validation rules
 */
export const validationRules = {
    /**
     * Validate required field
     */
    required: (message = 'This field is required'): ValidationRule => (value) => {
        if (
            value === null ||
            value === undefined ||
            (typeof value === 'string' && value.trim() === '')
        ) {
            return message;
        }
        return null;
    },

    /**
     * Validate email format
     */
    email: (message = 'Please enter a valid email'): ValidationRule => (value) => {
        if (!value) return null;
        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        if (!emailRegex.test(String(value))) {
            return message;
        }
        return null;
    },

    /**
     * Validate minimum length
     */
    minLength: (min: number, message?: string): ValidationRule => (value) => {
        if (!value) return null;
        if (String(value).length < min) {
            return message || `Minimum ${min} characters required`;
        }
        return null;
    },

    /**
     * Validate maximum length
     */
    maxLength: (max: number, message?: string): ValidationRule => (value) => {
        if (!value) return null;
        if (String(value).length > max) {
            return message || `Maximum ${max} characters allowed`;
        }
        return null;
    },

    /**
     * Validate minimum value
     */
    min: (min: number, message?: string): ValidationRule => (value) => {
        if (value === null || value === undefined) return null;
        if (typeof value === 'number' && value < min) {
            return message || `Minimum value is ${min}`;
        }
        return null;
    },

    /**
     * Validate maximum value
     */
    max: (max: number, message?: string): ValidationRule => (value) => {
        if (value === null || value === undefined) return null;
        if (typeof value === 'number' && value > max) {
            return message || `Maximum value is ${max}`;
        }
        return null;
    },

    /**
     * Validate with regex pattern
     */
    pattern: (regex: RegExp, message = 'Invalid format'): ValidationRule => (value) => {
        if (!value) return null;
        if (!regex.test(String(value))) {
            return message;
        }
        return null;
    },

    /**
     * Custom validation function
     */
    custom: (fn: (value: FieldValue) => boolean, message = 'Invalid'): ValidationRule => (value) => {
        if (fn(value)) return null;
        return message;
    },
};

/**
 * Validate single field
 *
 * @param value - Field value
 * @param rules - Validation rule(s)
 * @returns Error message or null
 */
export function validateField(
    value: FieldValue,
    rules: ValidationRule | ValidationRule[] | undefined
): string | null {
    if (!rules) return null;

    const ruleArray = Array.isArray(rules) ? rules : [rules];

    for (const rule of ruleArray) {
        const error = rule(value);
        if (error) return error;
    }

    return null;
}

/**
 * Validate entire form
 *
 * @param values - Form values
 * @param schema - Validation schema
 * @returns Errors object
 */
export function validateForm(
    values: Record<string, FieldValue>,
    schema?: ValidationSchema
): Record<string, string> {
    if (!schema) return {};

    const errors: Record<string, string> = {};

    Object.entries(schema).forEach(([fieldName, rules]) => {
        const error = validateField(values[fieldName], rules);
        if (error) {
            errors[fieldName] = error;
        }
    });

    return errors;
}

/**
 * Update field value in form state
 *
 * @param formState - Current form state
 * @param fieldName - Field name
 * @param value - New value
 * @param validateRules - Optional validation rules
 * @returns Updated form state
 */
export function setFieldValue(
    formState: FormState,
    fieldName: string,
    value: FieldValue,
    validateRules?: ValidationRule | ValidationRule[]
): FormState {
    const newState = { ...formState };

    if (!newState[fieldName]) {
        newState[fieldName] = { value: null };
    }

    newState[fieldName] = {
        ...newState[fieldName],
        value,
        dirty: true,
    };

    if (validateRules) {
        const error = validateField(value, validateRules);
        newState[fieldName].error = error || undefined;
    }

    return newState;
}

/**
 * Update field error in form state
 *
 * @param formState - Current form state
 * @param fieldName - Field name
 * @param error - Error message
 * @returns Updated form state
 */
export function setFieldError(
    formState: FormState,
    fieldName: string,
    error?: string
): FormState {
    const newState = { ...formState };

    if (!newState[fieldName]) {
        newState[fieldName] = { value: null };
    }

    newState[fieldName] = {
        ...newState[fieldName],
        error,
        touched: true,
    };

    return newState;
}

/**
 * Mark field as touched
 *
 * @param formState - Current form state
 * @param fieldName - Field name
 * @returns Updated form state
 */
export function touchField(
    formState: FormState,
    fieldName: string
): FormState {
    const newState = { ...formState };

    if (!newState[fieldName]) {
        newState[fieldName] = { value: null };
    }

    newState[fieldName] = {
        ...newState[fieldName],
        touched: true,
    };

    return newState;
}

/**
 * Reset form to initial state
 *
 * @param initialValues - Initial field values
 * @returns Reset form state
 */
export function resetForm(
    initialValues: Record<string, FieldValue>
): FormState {
    return createFormState(initialValues);
}

/**
 * Get all form errors
 *
 * @param formState - Current form state
 * @returns Record of field errors
 */
export function getFormErrors(formState: FormState): Record<string, string> {
    const errors: Record<string, string> = {};

    Object.entries(formState).forEach(([fieldName, field]) => {
        if (field.error && field.touched) {
            errors[fieldName] = field.error;
        }
    });

    return errors;
}

/**
 * Get form values
 *
 * @param formState - Current form state
 * @returns Record of field values
 */
export function getFormValues(formState: FormState): Record<string, FieldValue> {
    const values: Record<string, FieldValue> = {};

    Object.entries(formState).forEach(([fieldName, field]) => {
        values[fieldName] = field.value;
    });

    return values;
}

/**
 * Check if form is valid
 *
 * @param formState - Current form state
 * @returns True if no errors
 */
export function isFormValid(formState: FormState): boolean {
    return Object.values(formState).every((field) => !field.error);
}

/**
 * Check if form has any changes
 *
 * @param formState - Current form state
 * @returns True if any field is dirty
 */
export function isFormDirty(formState: FormState): boolean {
    return Object.values(formState).some((field) => field.dirty);
}

/**
 * Check if form has been touched
 *
 * @param formState - Current form state
 * @returns True if any field is touched
 */
export function isFormTouched(formState: FormState): boolean {
    return Object.values(formState).some((field) => field.touched);
}

export default {
    createFormState,
    validationRules,
    validateField,
    validateForm,
    setFieldValue,
    setFieldError,
    touchField,
    resetForm,
    getFormErrors,
    getFormValues,
    isFormValid,
    isFormDirty,
    isFormTouched,
};
