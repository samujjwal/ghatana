/**
 * Form Validation Utilities
 *
 * Composable, reusable form validation framework with:
 * - Real-time validation feedback
 * - Dirty state tracking (unsaved changes)
 * - Navigation blocking on unsaved changes
 * - Validation schema system
 * - Error message formatting
 *
 * Designed to be promoted to @ghatana/ui shared library.
 *
 * @module lib/form-validation
 * @doc.type module
 * @doc.purpose Composable form validation utilities
 * @doc.layer product
 * @doc.pattern Validation Framework
 */

import { useState, useCallback, useEffect, useRef, type ChangeEvent } from "react";
import { useBlocker } from "react-router";
import { useToast } from "./toast";

// ============================================================================
// Types
// ============================================================================

/**
 * Validation rule function
 *
 * @template T - Field value type
 * @param value - Field value to validate
 * @param allValues - All form values (for cross-field validation)
 * @returns Error message if invalid, null if valid
 */
export type ValidationRule<T = any> = (
    value: T,
    allValues?: Record<string, any>
) => string | null;

/**
 * Validation schema mapping field names to validation rules
 */
export type ValidationSchema<T extends Record<string, any>> = {
    [K in keyof T]?: ValidationRule<T[K]>[];
};

/**
 * Form validation errors
 */
export type FormErrors<T extends Record<string, any>> = Partial<
    Record<keyof T, string>
>;

/**
 * Form touched state (tracks which fields user has interacted with)
 */
export type FormTouched<T extends Record<string, any>> = Partial<
    Record<keyof T, boolean>
>;

/**
 * Form validation options
 */
export interface FormValidationOptions<T extends Record<string, any>> {
    /** Initial form values */
    initialValues: T;
    /** Validation schema */
    validationSchema?: ValidationSchema<T>;
    /** Submit handler */
    onSubmit: (values: T) => Promise<void> | void;
    /** Validate on field change (default: false) */
    validateOnChange?: boolean;
    /** Validate on field blur (default: true) */
    validateOnBlur?: boolean;
    /** Reset form after successful submit (default: false) */
    resetOnSubmit?: boolean;
}

/**
 * Form validation state and utilities
 */
export interface FormValidationReturn<T extends Record<string, any>> {
    /** Current form values */
    values: T;
    /** Validation errors */
    errors: FormErrors<T>;
    /** Touched state */
    touched: FormTouched<T>;
    /** Form is currently submitting */
    isSubmitting: boolean;
    /** Form is valid (no errors) */
    isValid: boolean;
    /** Form has been modified */
    isDirty: boolean;
    /** Handle field change */
    handleChange: (
        e: ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>
    ) => void;
    /** Handle field blur */
    handleBlur: (
        e: ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>
    ) => void;
    /** Handle form submit */
    handleSubmit: (e: React.FormEvent) => Promise<void>;
    /** Set field value programmatically */
    setFieldValue: <K extends keyof T>(field: K, value: T[K]) => void;
    /** Set field error */
    setFieldError: <K extends keyof T>(field: K, error: string) => void;
    /** Set field touched */
    setFieldTouched: <K extends keyof T>(field: K, touched: boolean) => void;
    /** Reset form to initial values */
    resetForm: () => void;
    /** Validate entire form */
    validateForm: () => boolean;
}

// ============================================================================
// Built-in Validation Rules
// ============================================================================

/**
 * Common validation rules
 *
 * Reusable validation functions for common field types.
 */
export const validators = {
    /**
     * Required field validator
     */
    required: (message = "This field is required"): ValidationRule => (value) => {
        if (typeof value === "string") {
            return value.trim().length > 0 ? null : message;
        }
        return value !== null && value !== undefined && value !== "" ? null : message;
    },

    /**
     * Email format validator
     */
    email: (message = "Please enter a valid email address"): ValidationRule => (
        value
    ) => {
        if (typeof value !== "string") return message;
        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        return emailRegex.test(value) ? null : message;
    },

    /**
     * Minimum length validator
     */
    minLength: (min: number, message?: string): ValidationRule => (value) => {
        if (typeof value !== "string") return `Must be at least ${min} characters`;
        return value.length >= min
            ? null
            : message || `Must be at least ${min} characters`;
    },

    /**
     * Maximum length validator
     */
    maxLength: (max: number, message?: string): ValidationRule => (value) => {
        if (typeof value !== "string") return `Must be at most ${max} characters`;
        return value.length <= max
            ? null
            : message || `Must be at most ${max} characters`;
    },

    /**
     * Minimum value validator (for numbers)
     */
    min: (min: number, message?: string): ValidationRule => (value) => {
        if (typeof value !== "number") return `Must be at least ${min}`;
        return value >= min ? null : message || `Must be at least ${min}`;
    },

    /**
     * Maximum value validator (for numbers)
     */
    max: (max: number, message?: string): ValidationRule => (value) => {
        if (typeof value !== "number") return `Must be at most ${max}`;
        return value <= max ? null : message || `Must be at most ${max}`;
    },

    /**
     * Pattern (regex) validator
     */
    pattern: (regex: RegExp, message = "Invalid format"): ValidationRule => (
        value
    ) => {
        if (typeof value !== "string") return message;
        return regex.test(value) ? null : message;
    },

    /**
     * URL validator
     */
    url: (message = "Please enter a valid URL"): ValidationRule => (value) => {
        if (typeof value !== "string") return message;
        try {
            new URL(value);
            return null;
        } catch {
            return message;
        }
    },

    /**
     * Custom validator with function
     */
    custom: (
        validatorFn: (value: any, allValues?: Record<string, any>) => boolean,
        message: string
    ): ValidationRule => (value, allValues) => {
        return validatorFn(value, allValues) ? null : message;
    },
};

// ============================================================================
// useFormValidation Hook
// ============================================================================

/**
 * Hook for comprehensive form validation with dirty state tracking
 *
 * Provides:
 * - Field-level validation (on change/blur)
 * - Form-level validation (on submit)
 * - Dirty state tracking (unsaved changes)
 * - Touched state tracking (UX)
 * - Async submit handling
 *
 * @template T - Type of form values object
 * @param options - Form configuration options
 * @returns Form state and handlers
 *
 * @example
 * ```tsx
 * function RoleForm() {
 *   const form = useFormValidation({
 *     initialValues: { name: '', description: '' },
 *     validationSchema: {
 *       name: [validators.required(), validators.minLength(3)],
 *       description: [validators.maxLength(500)]
 *     },
 *     onSubmit: async (values) => {
 *       await saveRole(values);
 *     }
 *   });
 *
 *   return (
 *     <form onSubmit={form.handleSubmit}>
 *       <input
 *         name="name"
 *         value={form.values.name}
 *         onChange={form.handleChange}
 *         onBlur={form.handleBlur}
 *       />
 *       {form.touched.name && form.errors.name && (
 *         <span className="error">{form.errors.name}</span>
 *       )}
 *       <button type="submit" disabled={!form.isValid || form.isSubmitting}>
 *         Save
 *       </button>
 *     </form>
 *   );
 * }
 * ```
 *
 * @doc.type function
 * @doc.purpose Form validation hook with dirty state tracking
 * @doc.layer product
 * @doc.pattern Hook
 */
export function useFormValidation<T extends Record<string, any>>(
    options: FormValidationOptions<T>
): FormValidationReturn<T> {
    const {
        initialValues,
        validationSchema = {} as ValidationSchema<T>,
        onSubmit,
        validateOnChange = false,
        validateOnBlur = true,
        resetOnSubmit = false,
    } = options;

    const [values, setValues] = useState<T>(initialValues);
    const [errors, setErrors] = useState<FormErrors<T>>({});
    const [touched, setTouched] = useState<FormTouched<T>>({});
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [isDirty, setIsDirty] = useState(false);

    const initialValuesRef = useRef(initialValues);

    /**
     * Validate a single field
     */
    const validateField = useCallback(
        <K extends keyof T>(field: K, value: T[K]): string | null => {
            const rules = validationSchema[field];
            if (!rules) return null;

            for (const rule of rules) {
                const error = rule(value, values);
                if (error) return error;
            }

            return null;
        },
        [validationSchema, values]
    );

    /**
     * Validate entire form
     */
    const validateForm = useCallback((): boolean => {
        const newErrors: FormErrors<T> = {};

        Object.keys(validationSchema).forEach((field) => {
            const error = validateField(field as keyof T, values[field as keyof T]);
            if (error) {
                newErrors[field as keyof T] = error;
            }
        });

        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    }, [validationSchema, values, validateField]);

    /**
     * Handle field change
     */
    const handleChange = useCallback(
        (
            e: ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>
        ) => {
            const { name, value, type } = e.target;
            const newValue =
                type === "checkbox" ? (e.target as HTMLInputElement).checked : value;

            setValues((prev) => {
                const updated = { ...prev, [name]: newValue };
                setIsDirty(
                    JSON.stringify(updated) !== JSON.stringify(initialValuesRef.current)
                );
                return updated as T;
            });

            // Validate on change if enabled
            if (validateOnChange) {
                const error = validateField(name as keyof T, newValue as any);
                setErrors((prev) => ({
                    ...prev,
                    [name]: error || undefined,
                }));
            }
        },
        [validateOnChange, validateField]
    );

    /**
     * Handle field blur
     */
    const handleBlur = useCallback(
        (
            e: ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>
        ) => {
            const { name } = e.target;

            setTouched((prev) => ({ ...prev, [name]: true }));

            // Validate on blur if enabled
            if (validateOnBlur) {
                const error = validateField(name as keyof T, values[name as keyof T]);
                setErrors((prev) => ({
                    ...prev,
                    [name]: error || undefined,
                }));
            }
        },
        [validateOnBlur, values, validateField]
    );

    /**
     * Handle form submit
     */
    const handleSubmit = useCallback(
        async (e: React.FormEvent) => {
            e.preventDefault();

            // Mark all fields as touched
            const allTouched = Object.keys(values).reduce(
                (acc, key) => ({ ...acc, [key]: true }),
                {} as FormTouched<T>
            );
            setTouched(allTouched);

            // Validate form
            const isValid = validateForm();
            if (!isValid) return;

            // Submit
            setIsSubmitting(true);
            try {
                await onSubmit(values);

                if (resetOnSubmit) {
                    setValues(initialValues);
                    setErrors({});
                    setTouched({});
                    setIsDirty(false);
                }
            } finally {
                setIsSubmitting(false);
            }
        },
        [values, validateForm, onSubmit, initialValues, resetOnSubmit]
    );

    /**
     * Set field value programmatically
     */
    const setFieldValue = useCallback(<K extends keyof T>(field: K, value: T[K]) => {
        setValues((prev) => {
            const updated = { ...prev, [field]: value };
            setIsDirty(
                JSON.stringify(updated) !== JSON.stringify(initialValuesRef.current)
            );
            return updated;
        });
    }, []);

    /**
     * Set field error
     */
    const setFieldError = useCallback(<K extends keyof T>(field: K, error: string) => {
        setErrors((prev) => ({ ...prev, [field]: error }));
    }, []);

    /**
     * Set field touched
     */
    const setFieldTouched = useCallback(
        <K extends keyof T>(field: K, touchedValue: boolean) => {
            setTouched((prev) => ({ ...prev, [field]: touchedValue }));
        },
        []
    );

    /**
     * Reset form to initial values
     */
    const resetForm = useCallback(() => {
        setValues(initialValues);
        setErrors({});
        setTouched({});
        setIsDirty(false);
        setIsSubmitting(false);
    }, [initialValues]);

    const isValid = Object.keys(errors).length === 0;

    return {
        values,
        errors,
        touched,
        isSubmitting,
        isValid,
        isDirty,
        handleChange,
        handleBlur,
        handleSubmit,
        setFieldValue,
        setFieldError,
        setFieldTouched,
        resetForm,
        validateForm,
    };
}

// ============================================================================
// useUnsavedChangesWarning Hook
// ============================================================================

/**
 * Hook to warn users about unsaved changes before navigation
 *
 * Uses React Router's useBlocker to prevent navigation when form has unsaved changes.
 * Shows browser confirmation dialog.
 *
 * @param isDirty - Whether form has unsaved changes
 * @param message - Warning message (default: "You have unsaved changes...")
 *
 * @example
 * ```tsx
 * function EditForm() {
 *   const form = useFormValidation({...});
 *   useUnsavedChangesWarning(form.isDirty);
 *
 *   return <form>{...}</form>;
 * }
 * ```
 *
 * @doc.type function
 * @doc.purpose Warn users about unsaved changes before navigation
 * @doc.layer product
 * @doc.pattern Hook
 */
export function useUnsavedChangesWarning(
    isDirty: boolean,
    message = "You have unsaved changes. Are you sure you want to leave?"
) {
    // Block navigation when dirty
    useBlocker(({ currentLocation, nextLocation }) => {
        return isDirty && currentLocation.pathname !== nextLocation.pathname;
    });

    // Browser beforeunload event (page refresh, close tab)
    useEffect(() => {
        const handleBeforeUnload = (e: BeforeUnloadEvent) => {
            if (isDirty) {
                e.preventDefault();
                // Modern browsers ignore custom message, show generic warning
                e.returnValue = message;
                return message;
            }
        };

        window.addEventListener("beforeunload", handleBeforeUnload);
        return () => window.removeEventListener("beforeunload", handleBeforeUnload);
    }, [isDirty, message]);
}

// ============================================================================
// useDirtyState Hook
// ============================================================================

/**
 * Hook to track dirty state (unsaved changes) with visual feedback
 *
 * Provides dirty state tracking and optional toast notification.
 *
 * @param isDirty - Whether data has unsaved changes
 * @param showToast - Show toast when dirty state changes (default: false)
 *
 * @example
 * ```tsx
 * function DataEditor() {
 *   const [data, setData] = useState(initialData);
 *   const isDirty = JSON.stringify(data) !== JSON.stringify(initialData);
 *   useDirtyState(isDirty, true); // Show toast
 *
 *   return <div>...</div>;
 * }
 * ```
 *
 * @doc.type function
 * @doc.purpose Track dirty state with optional notifications
 * @doc.layer product
 * @doc.pattern Hook
 */
export function useDirtyState(isDirty: boolean, showToast = false) {
    const { showToast: toast } = useToast();
    const wasDirtyRef = useRef(isDirty);

    useEffect(() => {
        const wasDirty = wasDirtyRef.current;

        if (showToast && isDirty && !wasDirty) {
            toast({
                type: "warning",
                message: "You have unsaved changes",
                duration: 3000,
            });
        }

        if (showToast && !isDirty && wasDirty) {
            toast({
                type: "success",
                message: "All changes saved",
                duration: 2000,
            });
        }

        wasDirtyRef.current = isDirty;
    }, [isDirty, showToast, toast]);
}

// ============================================================================
// Error Message Formatting Utilities
// ============================================================================

/**
 * Format error message for display
 *
 * @param error - Error object or string
 * @returns Formatted error message
 */
export function formatErrorMessage(error: unknown): string {
    if (!error) return "";
    if (typeof error === "string") return error;
    if (error instanceof Error) return error.message;
    if (typeof error === "object" && "message" in error) {
        return String((error as any).message);
    }
    return "An error occurred";
}

/**
 * Get field error if field is touched
 *
 * @param errors - Form errors object
 * @param touched - Form touched object
 * @param field - Field name
 * @returns Error message if field is touched and has error, null otherwise
 */
export function getFieldError<T extends Record<string, any>>(
    errors: FormErrors<T>,
    touched: FormTouched<T>,
    field: keyof T
): string | null {
    return touched[field] && errors[field] ? (errors[field] as string) : null;
}
