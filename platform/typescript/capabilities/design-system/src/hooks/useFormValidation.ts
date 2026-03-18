import { useState, useCallback, useMemo } from 'react';

export type ValidationRule<T> = {
  /** Validation function that returns true if valid */
  validate: (value: T, formValues?: Record<string, unknown>) => boolean | Promise<boolean>;
  /** Error message to display when validation fails */
  message: string;
  /** Optional async validation (shows validating state) */
  async?: boolean;
};

export type FieldValidation<T> = ValidationRule<T>[];

export type FormValidationSchema<T extends Record<string, unknown>> = {
  [K in keyof T]?: FieldValidation<T[K]>;
};

export type FieldState = {
  /** Current error message (null if valid) */
  error: string | null;
  /** Whether the field is currently being validated */
  validating: boolean;
  /** Whether the field has been touched (blurred) */
  touched: boolean;
  /** Whether the field is valid */
  isValid: boolean;
};

export type FormState<T extends Record<string, unknown>> = {
  [K in keyof T]: FieldState;
};

export interface UseFormValidationOptions<T extends Record<string, unknown>> {
  /** Validation schema for the form */
  schema: FormValidationSchema<T>;
  /** Initial form values */
  initialValues: T;
  /** Validate on change (default: false) */
  validateOnChange?: boolean;
  /** Validate on blur (default: true) */
  validateOnBlur?: boolean;
  /** Debounce time for async validation in ms (default: 300) */
  debounceMs?: number;
}

export interface UseFormValidationResult<T extends Record<string, unknown>> {
  /** Current form values */
  values: T;
  /** Field states (error, validating, touched) */
  fieldStates: FormState<T>;
  /** Whether the entire form is valid */
  isValid: boolean;
  /** Whether any field is currently validating */
  isValidating: boolean;
  /** Whether the form has been submitted */
  isSubmitted: boolean;
  /** Set a field value */
  setValue: <K extends keyof T>(field: K, value: T[K]) => void;
  /** Set multiple values at once */
  setValues: (values: Partial<T>) => void;
  /** Mark a field as touched (trigger validation on blur) */
  touchField: (field: keyof T) => void;
  /** Validate a specific field */
  validateField: (field: keyof T) => Promise<boolean>;
  /** Validate all fields */
  validateAll: () => Promise<boolean>;
  /** Reset form to initial values */
  reset: () => void;
  /** Clear all errors */
  clearErrors: () => void;
  /** Get props for a field (for easy integration with FormField component) */
  getFieldProps: <K extends keyof T>(field: K) => {
    value: T[K];
    error: string | null;
    validating: boolean;
    touched: boolean;
    onChange: (value: T[K]) => void;
    onBlur: () => void;
  };
  /** Handle form submission */
  handleSubmit: (onSubmit: (values: T) => void | Promise<void>) => (e?: React.FormEvent) => Promise<void>;
}

/**
 * Hook for form validation with support for inline validation states.
 * Works great with the FormField component.
 * 
 * @example
 * ```tsx
 * const { values, getFieldProps, handleSubmit, isValid } = useFormValidation({
 *   schema: {
 *     email: [
 *       { validate: (v) => !!v, message: 'Email is required' },
 *       { validate: (v) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v), message: 'Invalid email format' },
 *     ],
 *     password: [
 *       { validate: (v) => !!v, message: 'Password is required' },
 *       { validate: (v) => v.length >= 8, message: 'Password must be at least 8 characters' },
 *     ],
 *   },
 *   initialValues: { email: '', password: '' },
 * });
 * 
 * return (
 *   <form onSubmit={handleSubmit(onSubmit)}>
 *     <FormField label="Email" {...getFieldProps('email')}>
 *       <input type="email" value={values.email} onChange={(e) => getFieldProps('email').onChange(e.target.value)} />
 *     </FormField>
 *   </form>
 * );
 * ```
 */
export function useFormValidation<T extends Record<string, unknown>>(
  options: UseFormValidationOptions<T>
): UseFormValidationResult<T> {
  const {
    schema,
    initialValues,
    validateOnChange = false,
    validateOnBlur = true,
    debounceMs = 300,
  } = options;

  const [values, setValuesState] = useState<T>(initialValues);
  const [isSubmitted, setIsSubmitted] = useState(false);

  // Initialize field states
  const initialFieldStates = useMemo(() => {
    const states = {} as FormState<T>;
    for (const key of Object.keys(initialValues) as Array<keyof T>) {
      states[key] = {
        error: null,
        validating: false,
        touched: false,
        isValid: true,
      };
    }
    return states;
  }, [initialValues]);

  const [fieldStates, setFieldStates] = useState<FormState<T>>(initialFieldStates);

  // Debounce timers for async validation
  const debounceTimers = useMemo(() => new Map<keyof T, NodeJS.Timeout>(), []);

  const validateField = useCallback(
    async (field: keyof T): Promise<boolean> => {
      const rules = schema[field];
      if (!rules || rules.length === 0) {
        setFieldStates((prev) => ({
          ...prev,
          [field]: { ...prev[field], error: null, isValid: true, validating: false },
        }));
        return true;
      }

      const value = values[field];
      const hasAsyncRule = rules.some((r) => r.async);

      // Set validating state if there are async rules
      if (hasAsyncRule) {
        setFieldStates((prev) => ({
          ...prev,
          [field]: { ...prev[field], validating: true },
        }));
      }

      // Run validations in order
      for (const rule of rules) {
        try {
          const isValid = await rule.validate(value, values as Record<string, unknown>);
          if (!isValid) {
            setFieldStates((prev) => ({
              ...prev,
              [field]: {
                ...prev[field],
                error: rule.message,
                isValid: false,
                validating: false,
              },
            }));
            return false;
          }
        } catch {
          setFieldStates((prev) => ({
            ...prev,
            [field]: {
              ...prev[field],
              error: 'Validation error',
              isValid: false,
              validating: false,
            },
          }));
          return false;
        }
      }

      // All validations passed
      setFieldStates((prev) => ({
        ...prev,
        [field]: { ...prev[field], error: null, isValid: true, validating: false },
      }));
      return true;
    },
    [schema, values]
  );

  const setValue = useCallback(
    <K extends keyof T>(field: K, value: T[K]) => {
      setValuesState((prev) => ({ ...prev, [field]: value }));

      if (validateOnChange || isSubmitted) {
        // Clear any pending debounce timer
        const existingTimer = debounceTimers.get(field);
        if (existingTimer) {
          clearTimeout(existingTimer);
        }

        // Check if field has async rules
        const rules = schema[field];
        const hasAsyncRule = rules?.some((r) => r.async);

        if (hasAsyncRule) {
          // Debounce async validation
          const timer = setTimeout(() => {
            validateField(field);
          }, debounceMs);
          debounceTimers.set(field, timer);
        } else {
          // Immediate validation for sync rules
          validateField(field);
        }
      }
    },
    [validateOnChange, isSubmitted, schema, debounceMs, debounceTimers, validateField]
  );

  const setValues = useCallback(
    (newValues: Partial<T>) => {
      setValuesState((prev) => ({ ...prev, ...newValues }));
    },
    []
  );

  const touchField = useCallback(
    (field: keyof T) => {
      setFieldStates((prev) => ({
        ...prev,
        [field]: { ...prev[field], touched: true },
      }));

      if (validateOnBlur) {
        validateField(field);
      }
    },
    [validateOnBlur, validateField]
  );

  const validateAll = useCallback(async (): Promise<boolean> => {
    const fields = Object.keys(schema) as Array<keyof T>;
    const results = await Promise.all(fields.map((field) => validateField(field)));
    return results.every((isValid) => isValid);
  }, [schema, validateField]);

  const reset = useCallback(() => {
    setValuesState(initialValues);
    setFieldStates(initialFieldStates);
    setIsSubmitted(false);
    debounceTimers.forEach((timer) => clearTimeout(timer));
    debounceTimers.clear();
  }, [initialValues, initialFieldStates, debounceTimers]);

  const clearErrors = useCallback(() => {
    setFieldStates((prev) => {
      const newStates = { ...prev };
      for (const key of Object.keys(newStates) as Array<keyof T>) {
        newStates[key] = { ...newStates[key], error: null, isValid: true };
      }
      return newStates;
    });
  }, []);

  const getFieldProps = useCallback(
    <K extends keyof T>(field: K) => ({
      value: values[field],
      error: fieldStates[field]?.error ?? null,
      validating: fieldStates[field]?.validating ?? false,
      touched: fieldStates[field]?.touched ?? false,
      onChange: (value: T[K]) => setValue(field, value),
      onBlur: () => touchField(field),
    }),
    [values, fieldStates, setValue, touchField]
  );

  const handleSubmit = useCallback(
    (onSubmit: (values: T) => void | Promise<void>) =>
      async (e?: React.FormEvent) => {
        if (e) {
          e.preventDefault();
        }

        setIsSubmitted(true);

        // Mark all fields as touched
        setFieldStates((prev) => {
          const newStates = { ...prev };
          for (const key of Object.keys(newStates) as Array<keyof T>) {
            newStates[key] = { ...newStates[key], touched: true };
          }
          return newStates;
        });

        const isValid = await validateAll();
        if (isValid) {
          await onSubmit(values);
        }
      },
    [validateAll, values]
  );

  // Compute derived state
  const isValid = useMemo(
    () => Object.values(fieldStates).every((state) => (state as FieldState).isValid),
    [fieldStates]
  );

  const isValidating = useMemo(
    () => Object.values(fieldStates).some((state) => (state as FieldState).validating),
    [fieldStates]
  );

  return {
    values,
    fieldStates,
    isValid,
    isValidating,
    isSubmitted,
    setValue,
    setValues,
    touchField,
    validateField,
    validateAll,
    reset,
    clearErrors,
    getFieldProps,
    handleSubmit,
  };
}

// Common validation rules
export const validators = {
  required: (message = 'This field is required'): ValidationRule<unknown> => ({
    validate: (value) => {
      if (typeof value === 'string') return value.trim().length > 0;
      if (Array.isArray(value)) return value.length > 0;
      return value !== null && value !== undefined;
    },
    message,
  }),

  email: (message = 'Invalid email address'): ValidationRule<string> => ({
    validate: (value) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value),
    message,
  }),

  minLength: (min: number, message?: string): ValidationRule<string> => ({
    validate: (value) => value.length >= min,
    message: message ?? `Must be at least ${min} characters`,
  }),

  maxLength: (max: number, message?: string): ValidationRule<string> => ({
    validate: (value) => value.length <= max,
    message: message ?? `Must be at most ${max} characters`,
  }),

  pattern: (regex: RegExp, message: string): ValidationRule<string> => ({
    validate: (value) => regex.test(value),
    message,
  }),

  min: (min: number, message?: string): ValidationRule<number> => ({
    validate: (value) => value >= min,
    message: message ?? `Must be at least ${min}`,
  }),

  max: (max: number, message?: string): ValidationRule<number> => ({
    validate: (value) => value <= max,
    message: message ?? `Must be at most ${max}`,
  }),

  url: (message = 'Invalid URL'): ValidationRule<string> => ({
    validate: (value) => {
      try {
        new URL(value);
        return true;
      } catch {
        return false;
      }
    },
    message,
  }),

  match: <T>(fieldToMatch: string, message?: string): ValidationRule<T> => ({
    validate: (value, formValues) => value === formValues?.[fieldToMatch],
    message: message ?? 'Values do not match',
  }),

  custom: <T>(
    fn: (value: T, formValues?: Record<string, unknown>) => boolean | Promise<boolean>,
    message: string,
    async = false
  ): ValidationRule<T> => ({
    validate: fn,
    message,
    async,
  }),
};
