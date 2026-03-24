import { useState, useCallback, type ChangeEvent } from 'react';

import { runFieldValidationIfNeeded, markAllTouched } from './utils';
import {
  validateForm as baseValidateForm,
  hasErrors,
  validateField as baseValidateField,
  type ValidationRules,
  type ValidationErrors,
} from '../../utils/validation';

import type { UseFormOptions, UseFormReturn } from './types';

/**
 * Adapter function to wrap baseValidateField for use with runFieldValidationIfNeeded.
 * Converts the validation rule from unknown type to string | null return type.
 */
function validateFieldAdapter(
  value: unknown,
  rule: unknown
): string | undefined {
  if (!Array.isArray(rule)) return undefined;
  const error = baseValidateField(value, rule);
  return error ?? undefined;
}

/**
 * Run full form validation and update errors state.
 * Returns true when there are no validation errors.
 */
function runValidateForm(
  values: Record<string, unknown>,
  rules: ValidationRules,
  setErrors: (e: ValidationErrors) => void
) {
  const newErrors = baseValidateForm(values, rules);
  setErrors(newErrors);
  return !hasErrors(newErrors);
}

/**
 * Hook for form state management with validation
 * 
 * Comprehensive form handling with field-level and form-level validation.
 * Tracks values, errors, touched state, and submission status.
 * 
 * Features:
 * - Field-level validation on change/blur
 * - Form-level validation on submit
 * - Touched state tracking for UX
 * - Built-in submission handling with async support
 * - Updater functions for dynamic field changes
 * - Reset functionality
 * 
 * @template T - Type of form values object
 * @param options - Form configuration options
 * @param options.initialValues - Initial form values
 * @param options.validationRules - Validation rules per field
 * @param options.onSubmit - Async submit handler
 * @param options.validateOnChange - Enable real-time validation (default: false)
 * @param options.validateOnBlur - Enable validation on blur (default: true)
 * @returns Form state and handlers
 * 
 * @example
 * ```tsx
 * function LoginForm() {
 *   const form = useForm({
 *     initialValues: { email: '', password: '' },
 *     validationRules: {
 *       email: [['required'], ['email']],
 *       password: [['required'], ['minLength', 8]]
 *     },
 *     onSubmit: async (values) => {
 *       await login(values);
 *     },
 *     validateOnBlur: true
 *   });
 * 
 *   return (
 *     <form onSubmit={form.handleSubmit}>
 *       <input
 *         name="email"
 *         value={form.values.email}
 *         onChange={form.handleChange}
 *         onBlur={form.handleBlur}
 *       />
 *       {form.touched.email && form.errors.email && (
 *         <span className="error">{form.errors.email}</span>
 *       )}
 *       
 *       <input
 *         type="password"
 *         name="password"
 *         value={form.values.password}
 *         onChange={form.handleChange}
 *         onBlur={form.handleBlur}
 *       />
 *       {form.touched.password && form.errors.password && (
 *         <span className="error">{form.errors.password}</span>
 *       )}
 *       
 *       <button type="submit" disabled={form.isSubmitting}>
 *         {form.isSubmitting ? 'Logging in...' : 'Login'}
 *       </button>
 *     </form>
 *   );
 * }
 * 
 * function DynamicForm() {
 *   const form = useForm({
 *     initialValues: { items: [{ name: '', quantity: 0 }] },
 *     onSubmit: async (values) => console.log(values)
 *   });
 * 
 *   const addItem = () => {
 *     form.setFieldValue('items', (prev) => [
 *       ...prev,
 *       { name: '', quantity: 0 }
 *     ]);
 *   };
 * 
 *   return (
 *     <form onSubmit={form.handleSubmit}>
 *       {form.values.items.map((item, idx) => (
 *         <div key={idx}>
 *           <input
 *             value={item.name}
 *             onChange={(e) =>
 *               form.setFieldValue(`items.${idx}.name`, e.target.value)
 *             }
 *           />
 *         </div>
 *       ))}
 *       <button type="button" onClick={addItem}>Add Item</button>
 *       <button type="submit">Submit</button>
 *     </form>
 *   );
 * }
 * ```
 */
// eslint-disable-next-line max-lines-per-function
export function useForm<T extends Record<string, unknown>>({
  initialValues,
  validationRules = {},
  onSubmit,
  validateOnChange = false,
  validateOnBlur = true,
}: UseFormOptions<T>): UseFormReturn<T> {
  const [values, setValues] = useState<T>(initialValues);
  const [errors, setErrors] = useState<ValidationErrors>({});
  const [touched, setTouched] = useState<Record<string, boolean>>({});
  const [isSubmitting, setIsSubmitting] = useState(false);

  const validateForm = useCallback(() => {
    return runValidateForm(
      values,
      validationRules as ValidationRules,
      setErrors
    );
  }, [values, validationRules]);
  /** Handle field change and optionally run field-level validation. */
  function handleFieldChangeImpl(
    e: ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>
  ) {
    const { name, value, type } = e.target;
    const newValue =
      type === 'checkbox' ? (e.target as HTMLInputElement).checked : value;
    setValues((prev) => ({ ...prev, [name]: newValue }));
    runFieldValidationIfNeeded(
      name,
      values as Record<string, unknown>,
      validationRules as Record<string, unknown>,
      setErrors,
      validateOnChange,
      validateFieldAdapter
    );
  }

  const handleChange = useCallback(handleFieldChangeImpl, [
    validateOnChange,
    validationRules,
    values,
  ]);

  const handleBlur = useCallback(
    (
      e: ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>
    ) => {
      const { name } = e.target;
      setTouched((prev) => ({ ...prev, [name]: true }));
      runFieldValidationIfNeeded(
        name,
        values as Record<string, unknown>,
        validationRules as Record<string, unknown>,
        setErrors,
        validateOnBlur,
        validateFieldAdapter
      );
    },
    [validateOnBlur, validationRules, values]
  );

  const doSubmit = useCallback(
    async (valuesParam: T) => {
      setIsSubmitting(true);
      try {
        await onSubmit(valuesParam);
      } finally {
        setIsSubmitting(false);
      }
    },
    [onSubmit]
  );

  const handleSubmit = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault();
      setTouched(markAllTouched(values));
      const ok = validateForm();
      if (!ok) return;
      await doSubmit(values);
    },
    [values, validateForm, doSubmit]
  );

  const setFieldValue = useCallback((field: string, value: unknown) => {
    setValues((prev) => ({ ...prev, [field]: value }) as T);
  }, []);

  const setFieldError = useCallback((field: string, error: string) => {
    setErrors((prev) => ({ ...prev, [field]: error }));
  }, []);

  const setFieldTouched = useCallback((field: string, isTouched: boolean) => {
    setTouched((prev) => ({ ...prev, [field]: isTouched }));
  }, []);

  const resetForm = useCallback(() => {
    setValues(initialValues);
    setErrors({});
    setTouched({});
    setIsSubmitting(false);
  }, [initialValues]);

  const isValid = !hasErrors(errors);

  return {
    values,
    errors,
    touched,
    isSubmitting,
    isValid,
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

export { markAllTouched } from './utils';
export { runFieldValidationIfNeeded as validateFieldForNameModule } from './utils';
