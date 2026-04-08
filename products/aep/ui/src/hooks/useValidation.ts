/**
 * Validation hooks using Zod schemas.
 *
 * Designed for cross-product reuse. Can be extracted to @ghatana/validation
 * after validation in AEP and Data Cloud.
 *
 * @doc.type hook
 * @doc.purpose Provide runtime validation using Zod schemas
 * @doc.layer frontend
 */

import { useState, useCallback, useRef } from 'react';
import { z, ZodSchema, ZodError } from 'zod';

/**
 * Use form validation hook
 */
export function useFormValidation<T extends z.ZodType>(
  schema: T,
  initialValues: z.infer<T>
) {
  type FormValues = z.infer<T>;

  const [values, setValues] = useState<FormValues>(initialValues);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [touched, setTouched] = useState<Record<string, boolean>>({});

  const validate = useCallback(
    (data: FormValues): { valid: boolean; errors: Record<string, string> } => {
      try {
        schema.parse(data);
        return { valid: true, errors: {} };
      } catch (error) {
        if (error instanceof ZodError) {
          const fieldErrors: Record<string, string> = {};
          error.issues.forEach((issue: z.ZodIssue) => {
            const path = issue.path.join('.');
            fieldErrors[path] = issue.message;
          });
          return { valid: false, errors: fieldErrors };
        }
        return { valid: false, errors: { _form: 'Validation failed' } };
      }
    },
    [schema]
  );

  const handleChange = useCallback(
    (field: keyof FormValues) => (value: unknown) => {
      setValues((prev) => Object.assign({}, prev, { [field]: value as FormValues[keyof FormValues] }));
      setTouched((prev) => Object.assign({}, prev, { [field as string]: true }));
    },
    []
  );

  const handleBlur = useCallback((field: keyof FormValues) => () => {
    setTouched((prev) => ({ ...prev, [field as string]: true }));
  }, []);

  const reset = useCallback(() => {
    setValues(initialValues);
    setErrors({});
    setTouched({});
  }, [initialValues]);

  const submit = useCallback(
    (onSubmit: (values: FormValues) => void | Promise<void>) => async () => {
      const { valid, errors: validationErrors } = validate(values);
      setErrors(validationErrors);
      
      if (valid) {
        await onSubmit(values);
      }
    },
    [values, validate]
  );

  return {
    values,
    errors,
    touched,
    handleChange,
    handleBlur,
    reset,
    submit,
    isValid: Object.keys(errors).length === 0,
  };
}

/**
 * Use schema validation hook for single values
 */
export function useSchemaValidation<T>(schema: ZodSchema<T>) {
  const [error, setError] = useState<string | null>(null);
  const [isValid, setIsValid] = useState(true);

  const validate = useCallback(
    (value: unknown): boolean => {
      try {
        schema.parse(value);
        setError(null);
        setIsValid(true);
        return true;
      } catch (error) {
        if (error instanceof ZodError) {
          setError(error.issues[0]?.message ?? 'Invalid value');
          setIsValid(false);
          return false;
        }
        setError('Validation failed');
        setIsValid(false);
        return false;
      }
    },
    [schema]
  );

  return {
    error,
    isValid,
    validate,
    clearError: useCallback(() => setError(null), []),
  };
}

/**
 * Debounced validation hook
 */
export function useDebouncedValidation<T>(
  schema: ZodSchema<T>,
  delay: number = 500
) {
  const [error, setError] = useState<string | null>(null);
  const [isValid, setIsValid] = useState(true);
  const timeoutRef = useRef<NodeJS.Timeout | null>(null);

  const validateDebounced = useCallback(
    (value: unknown) => {
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current);
      }

      timeoutRef.current = setTimeout(() => {
        try {
          schema.parse(value);
          setError(null);
          setIsValid(true);
        } catch (error) {
          if (error instanceof ZodError) {
            setError(error.issues[0]?.message ?? 'Invalid value');
            setIsValid(false);
          } else {
            setError('Validation failed');
            setIsValid(false);
          }
        }
      }, delay);
    },
    [schema, delay]
  );

  const clearError = useCallback(() => {
    if (timeoutRef.current) {
      clearTimeout(timeoutRef.current);
    }
    setError(null);
    setIsValid(true);
  }, []);

  return {
    error,
    isValid,
    validate: validateDebounced,
    clearError,
  };
}
