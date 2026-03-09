import { atom } from 'jotai';

/**
 * Form Helpers Library
 *
 * <p><b>Purpose</b><br>
 * Provides utilities for form handling including validation, error management,
 * field state management, and async submission handling. Integrates with Jotai
 * for app-scoped state management.
 *
 * <p><b>Features</b><br>
 * - Field-level validation with error messages
 * - Form-level validation
 * - Async validation support
 * - Error tracking and display
 * - Dirty field tracking
 * - Form state persistence
 * - Submit handling with loading state
 * - Reset functionality
 * - Field array support for dynamic fields
 * - Custom validators
 *
 * <p><b>Validation Rules</b><br>
 * - required: Field must have value
 * - email: Valid email format
 * - minLength: Minimum string length
 * - maxLength: Maximum string length
 * - pattern: Regex pattern matching
 * - custom: Custom validation function
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * const formSchema = {
 *   email: { required: true, email: true },
 *   password: { required: true, minLength: 8 },
 *   name: { required: true, maxLength: 100 },
 * };
 *
 * const validator = createValidator(formSchema);
 * const errors = validator.validate(formData);
 * if (errors.length > 0) {
 *   // Show validation errors
 * }
 * ```
 *
 * @doc.type utility
 * @doc.purpose Form handling and validation
 * @doc.layer product
 * @doc.pattern Utility Library
 */

/**
 * Validation rule interface.
 *
 * @doc.type interface
 * @doc.purpose Validation rule definition
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface ValidationRule {
    required?: boolean;
    email?: boolean;
    minLength?: number;
    maxLength?: number;
    pattern?: RegExp;
    custom?: (value: any) => string | null;
    min?: number;
    max?: number;
}

/**
 * Validation error interface.
 */
export interface ValidationError {
    field: string;
    message: string;
}

/**
 * Form schema type.
 */
export type FormSchema = Record<string, ValidationRule>;

/**
 * Create a validator function.
 *
 * @param schema - Form validation schema
 * @returns Validator with validate method
 */
export function createValidator(schema: FormSchema) {
    return {
        validate(data: Record<string, any>): ValidationError[] {
            const errors: ValidationError[] = [];

            for (const [field, rules] of Object.entries(schema)) {
                const value = data[field];
                const fieldErrors = validateField(field, value, rules);
                errors.push(...fieldErrors);
            }

            return errors;
        },

        validateField(field: string, value: any, rule: ValidationRule): ValidationError[] {
            return validateField(field, value, rule);
        },
    };
}

/**
 * Validate a single field.
 */
function validateField(
    field: string,
    value: any,
    rules: ValidationRule
): ValidationError[] {
    const errors: ValidationError[] = [];

    // Required validation
    if (rules.required && (!value || value.toString().trim() === '')) {
        errors.push({
            field,
            message: `${field} is required`,
        });
        return errors;
    }

    if (!value) return errors;

    // Email validation
    if (rules.email) {
        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        if (!emailRegex.test(value)) {
            errors.push({
                field,
                message: 'Invalid email address',
            });
        }
    }

    // Min length validation
    if (rules.minLength && value.toString().length < rules.minLength) {
        errors.push({
            field,
            message: `${field} must be at least ${rules.minLength} characters`,
        });
    }

    // Max length validation
    if (rules.maxLength && value.toString().length > rules.maxLength) {
        errors.push({
            field,
            message: `${field} must not exceed ${rules.maxLength} characters`,
        });
    }

    // Pattern validation
    if (rules.pattern && !rules.pattern.test(value)) {
        errors.push({
            field,
            message: `${field} has invalid format`,
        });
    }

    // Min number validation
    if (rules.min !== undefined && Number(value) < rules.min) {
        errors.push({
            field,
            message: `${field} must be at least ${rules.min}`,
        });
    }

    // Max number validation
    if (rules.max !== undefined && Number(value) > rules.max) {
        errors.push({
            field,
            message: `${field} must not exceed ${rules.max}`,
        });
    }

    // Custom validation
    if (rules.custom) {
        const customError = rules.custom(value);
        if (customError) {
            errors.push({
                field,
                message: customError,
            });
        }
    }

    return errors;
}

/**
 * Create form state atoms.
 */
export function createFormAtoms<T extends Record<string, any>>(initialValues: T) {
    return {
        valuesAtom: atom<T>(initialValues),
        errorsAtom: atom<ValidationError[]>([]),
        dirtyFieldsAtom: atom<Set<string>>(new Set<string>()),
        isSubmittingAtom: atom(false),
        isValidAtom: atom(true),
    };
}

/**
 * Serialize form data to JSON.
 */
export function serializeFormData(data: Record<string, any>): string {
    return JSON.stringify(data);
}

/**
 * Deserialize form data from JSON.
 */
export function deserializeFormData<T extends Record<string, any>>(
    json: string
): T {
    return JSON.parse(json) as T;
}

/**
 * Get field error message.
 */
export function getFieldError(
    field: string,
    errors: ValidationError[]
): string | null {
    return errors.find((e) => e.field === field)?.message || null;
}

/**
 * Check if field has error.
 */
export function hasFieldError(field: string, errors: ValidationError[]): boolean {
    return errors.some((e) => e.field === field);
}

/**
 * Filter errors by field.
 */
export function getFieldErrors(
    field: string,
    errors: ValidationError[]
): ValidationError[] {
    return errors.filter((e) => e.field === field);
}

/**
 * Clear specific field errors.
 */
export function clearFieldErrors(
    field: string,
    errors: ValidationError[]
): ValidationError[] {
    return errors.filter((e) => e.field !== field);
}

/**
 * Mark field as dirty.
 */
export function markFieldDirty(field: string, dirty: Set<string>): Set<string> {
    const newDirty = new Set(dirty);
    newDirty.add(field);
    return newDirty;
}

/**
 * Mark field as pristine.
 */
export function markFieldPristine(field: string, dirty: Set<string>): Set<string> {
    const newDirty = new Set(dirty);
    newDirty.delete(field);
    return newDirty;
}

/**
 * Touch all fields (mark as dirty).
 */
export function touchAllFields(schema: FormSchema): Set<string> {
    return new Set(Object.keys(schema));
}

/**
 * Reset form to initial values.
 */
export function resetForm<T extends Record<string, any>>(initialValues: T): {
    values: T;
    errors: ValidationError[];
    dirtyFields: Set<string>;
} {
    return {
        values: initialValues,
        errors: [],
        dirtyFields: new Set(),
    };
}

/**
 * Get form summary for debugging.
 */
export function getFormSummary(
    _values: Record<string, any>,
    errors: ValidationError[],
    dirtyFields: Set<string>
): object {
    return {
        isValid: errors.length === 0,
        isDirty: dirtyFields.size > 0,
        errorCount: errors.length,
        dirtyFieldCount: dirtyFields.size,
        errors,
        dirtyFields: Array.from(dirtyFields),
    };
}
