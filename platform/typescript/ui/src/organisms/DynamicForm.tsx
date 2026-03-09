import React, { useState, useCallback } from 'react';

/**
 * Field configuration for form generation.
 *
 * <p><b>Purpose</b><br>
 * Defines a single form field with its rendering, validation, and behavior.
 * Supports conditional visibility, custom validation, and data transformation.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const nameField: FieldConfig<UserData> = {
 *   name: 'username',
 *   type: 'text',
 *   label: 'Username',
 *   required: true,
 *   validation: (value) => !value ? 'Username required' : undefined
 * };
 * }</pre>
 *
 * @typeParam T - Form data type
 * @doc.type interface
 * @doc.purpose Form field configuration
 * @doc.layer platform
 * @doc.pattern Configuration
 */
export interface FieldConfig<T = unknown> {
  /** Field name (must match form data property) */
  name: keyof T;

  /** Field input type */
  type: 'text' | 'number' | 'select' | 'textarea' | 'time' | 'date' | 'email' | 'password' | 'tel';

  /** Label text displayed above field */
  label: string;

  /** Placeholder text for empty field */
  placeholder?: string;

  /** Whether field is required */
  required?: boolean;

  /** Conditional visibility based on form state */
  visible?: (formData: Partial<T>) => boolean;

  /** Conditional disabled state based on form state */
  disabled?: (formData: Partial<T>) => boolean;

  /** Custom validation function */
  validation?: (value: unknown, formData: Partial<T>) => string | undefined;

  /** Options for select fields */
  options?: Array<{ label: string; value: unknown }>;

  /** Data transformation between form and submit values */
  transform?: {
    /** Transform value from data to form display */
    toForm?: (value: unknown) => unknown;
    /** Transform value from form to submit data */
    fromForm?: (value: unknown) => unknown;
  };

  /** Help text displayed below field */
  helpText?: string;

  /** Minimum value (for number/date inputs) */
  min?: number | string;

  /** Maximum value (for number/date inputs) */
  max?: number | string;

  /** Step value (for number inputs) */
  step?: number;

  /** Number of rows (for textarea) */
  rows?: number;

  /** Custom CSS classes for field wrapper */
  className?: string;

  /** Custom CSS classes for input element */
  inputClassName?: string;
}

/**
 * Form configuration for dynamic form generation.
 *
 * <p><b>Purpose</b><br>
 * Defines complete form structure with fields, validation, and callbacks.
 * Enables declarative form creation with built-in state management.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const config: FormConfig<UserData> = {
 *   fields: [nameField, emailField, passwordField],
 *   onSubmit: async (data) => await createUser(data),
 *   submitText: 'Register'
 * };
 * }</pre>
 *
 * @typeParam T - Form data type
 * @doc.type interface
 * @doc.purpose Form configuration
 * @doc.layer platform
 * @doc.pattern Configuration
 */
export interface FormConfig<T> {
  /** Field configurations */
  fields: FieldConfig<T>[];

  /** Submit callback with form data */
  onSubmit: (data: T) => void | Promise<void>;

  /** Cancel callback */
  onCancel?: () => void;

  /** Initial form data (for edit mode) */
  initialData?: Partial<T>;

  /** Submit button text */
  submitText?: string;

  /** Cancel button text */
  cancelText?: string;

  /** Custom CSS classes for submit button */
  submitButtonClassName?: string;

  /** Custom CSS classes for cancel button */
  cancelButtonClassName?: string;

  /** Custom CSS classes for form element */
  formClassName?: string;

  /** Disable form while submitting */
  disableOnSubmit?: boolean;
}

/**
 * Form state management.
 *
 * @typeParam T - Form data type
 */
interface FormState<T> {
  /** Current form data */
  data: Partial<T>;

  /** Validation errors per field */
  errors: Partial<Record<keyof T, string>>;

  /** Touched fields (for validation display) */
  touched: Partial<Record<keyof T, boolean>>;

  /** Form is submitting */
  isSubmitting: boolean;
}

/**
 * Dynamic form component with configuration-driven rendering.
 *
 * <p><b>Purpose</b><br>
 * Generic form component that renders fields based on configuration.
 * Handles validation, conditional visibility, data transformation,
 * and submission logic. Eliminates boilerplate form code.
 *
 * <p><b>Features</b><br>
 * - Configuration-driven field rendering
 * - Built-in validation with custom validators
 * - Conditional field visibility and disabled state
 * - Data transformation (form ↔ submit values)
 * - Edit mode with initial data
 * - Accessible with ARIA labels and keyboard navigation
 * - Responsive Tailwind styling
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Simple contact form
 * <Form
 *   fields={[
 *     { name: 'name', type: 'text', label: 'Name', required: true },
 *     { name: 'email', type: 'email', label: 'Email', required: true },
 *     { name: 'message', type: 'textarea', label: 'Message', rows: 4 }
 *   ]}
 *   onSubmit={(data) => sendMessage(data)}
 *   submitText="Send"
 * />
 *
 * // Complex form with conditional fields
 * <Form
 *   fields={[
 *     { 
 *       name: 'type', 
 *       type: 'select', 
 *       label: 'Type',
 *       options: [
 *         { label: 'Personal', value: 'personal' },
 *         { label: 'Business', value: 'business' }
 *       ]
 *     },
 *     {
 *       name: 'companyName',
 *       type: 'text',
 *       label: 'Company Name',
 *       visible: (data) => data.type === 'business'
 *     }
 *   ]}
 *   onSubmit={(data) => createAccount(data)}
 * />
 *
 * // Edit mode
 * <Form
 *   fields={userFields}
 *   initialData={existingUser}
 *   onSubmit={(data) => updateUser(data)}
 *   submitText="Update"
 * />
 * }</pre>
 *
 * <p><b>Validation</b><br>
 * Validation runs on blur and submit. Fields marked required automatically
 * validate presence. Custom validators receive field value and full form data
 * for cross-field validation.
 *
 * <p><b>Accessibility</b><br>
 * - All fields have proper labels with htmlFor
 * - Error messages linked with aria-describedby
 * - Required fields marked with aria-required
 * - Form submission via Enter key
 *
 * <p><b>Thread Safety</b><br>
 * React component - not thread-safe. Use in React context only.
 *
 * @typeParam T - Form data type (must be object)
 * @param props - Form configuration
 *
 * @see FieldConfig
 * @see FormConfig
 * @doc.type class
 * @doc.purpose Dynamic form rendering
 * @doc.layer platform
 * @doc.pattern Component
 */
export function DynamicForm<T extends Record<string, unknown>>({
  fields,
  onSubmit,
  onCancel,
  initialData = {},
  submitText = 'Submit',
  cancelText = 'Cancel',
  submitButtonClassName,
  cancelButtonClassName,
  formClassName = 'space-y-4',
  disableOnSubmit = true,
}: FormConfig<T>): React.JSX.Element {
  // Initialize form state
  const [state, setState] = useState<FormState<T>>(() => {
    const data: Partial<T> = {};

    // Transform initial data using field transformers
    fields.forEach((field) => {
      if (initialData[field.name] !== undefined) {
        const value = initialData[field.name];
        data[field.name] = field.transform?.toForm
          ? field.transform.toForm(value)
          : value;
      }
    });

    return {
      data,
      errors: {},
      touched: {},
      isSubmitting: false,
    };
  });

  /**
   * Validates a single field.
   *
   * @param field - Field configuration
   * @param value - Field value
   * @returns Error message or undefined
   */
  const validateField = useCallback(
    (field: FieldConfig<T>, value: unknown): string | undefined => {
      // Skip validation if field is not visible
      if (field.visible && !field.visible(state.data)) {
        return undefined;
      }

      // Required validation
      if (field.required && (value === undefined || value === null || value === '')) {
        return `${field.label} is required`;
      }

      // Custom validation
      if (field.validation) {
        return field.validation(value, state.data);
      }

      return undefined;
    },
    [state.data]
  );

  /**
   * Validates all visible fields.
   *
   * @returns True if form is valid
   */
  const validateForm = useCallback((): boolean => {
    const newErrors: Partial<Record<keyof T, string>> = {};

    fields.forEach((field) => {
      const error = validateField(field, state.data[field.name]);
      if (error) {
        newErrors[field.name] = error;
      }
    });

    setState((prev) => ({ ...prev, errors: newErrors }));
    return Object.keys(newErrors).length === 0;
  }, [fields, validateField, state.data]);

  /**
   * Handles field value change.
   *
   * @param fieldName - Field name
   * @param value - New value
   */
  const handleChange = useCallback((fieldName: keyof T, value: unknown) => {
    setState((prev) => ({
      ...prev,
      data: { ...prev.data, [fieldName]: value },
      errors: { ...prev.errors, [fieldName]: undefined },
    }));
  }, []);

  /**
   * Handles field blur (triggers validation).
   *
   * @param fieldName - Field name
   */
  const handleBlur = useCallback(
    (fieldName: keyof T) => {
      const field = fields.find((f) => f.name === fieldName);
      if (!field) return;

      const error = validateField(field, state.data[fieldName]);

      setState((prev) => ({
        ...prev,
        touched: { ...prev.touched, [fieldName]: true },
        errors: { ...prev.errors, [fieldName]: error },
      }));
    },
    [fields, validateField, state.data]
  );

  /**
   * Handles form submission.
   *
   * @param e - Form event
   */
  const handleSubmit = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault();

      if (!validateForm()) {
        return;
      }

      setState((prev) => ({ ...prev, isSubmitting: true }));

      try {
        // Transform data for submission
        const submitData: unknown = {};
        fields.forEach((field) => {
          const value = state.data[field.name];
          submitData[field.name] = field.transform?.fromForm
            ? field.transform.fromForm(value)
            : value;
        });

        await onSubmit(submitData as T);
      } finally {
        setState((prev) => ({ ...prev, isSubmitting: false }));
      }
    },
    [validateForm, fields, state.data, onSubmit]
  );

  /**
   * Renders a single form field based on configuration.
   *
   * @param field - Field configuration
   * @returns Field JSX element or null if not visible
   */
  const renderField = useCallback(
    (field: FieldConfig<T>) => {
      // Check visibility
      if (field.visible && !field.visible(state.data)) {
        return null;
      }

      const value = state.data[field.name] ?? '';
      const error = state.errors[field.name];
      const showError = state.touched[field.name] && error;
      const isDisabled = state.isSubmitting || (field.disabled && field.disabled(state.data));

      const fieldId = `form-field-${String(field.name)}`;
      const errorId = `${fieldId}-error`;
      const helpId = `${fieldId}-help`;

      const baseInputClasses = `w-full px-3 py-2 border rounded-md focus:outline-none focus:ring-2 ${showError
          ? 'border-red-500 focus:ring-red-500'
          : 'border-gray-300 focus:ring-blue-500'
        }`;

      const inputClasses = field.inputClassName
        ? `${baseInputClasses} ${field.inputClassName}`
        : baseInputClasses;

      const fieldWrapper = (
        <div key={String(field.name)} className={field.className}>
          <label
            htmlFor={fieldId}
            className="block text-sm font-medium text-gray-700 mb-1"
          >
            {field.label}
            {field.required && <span className="text-red-500 ml-1">*</span>}
          </label>

          {field.type === 'select' ? (
            <select
              id={fieldId}
              value={value}
              onChange={(e) => handleChange(field.name, e.target.value)}
              onBlur={() => handleBlur(field.name)}
              disabled={isDisabled}
              required={field.required}
              aria-required={field.required}
              aria-invalid={!!showError}
              aria-describedby={showError ? errorId : field.helpText ? helpId : undefined}
              className={inputClasses}
            >
              {!field.required && <option value="">Select...</option>}
              {field.options?.map((option) => (
                <option key={String(option.value)} value={String(option.value)}>
                  {option.label}
                </option>
              ))}
            </select>
          ) : field.type === 'textarea' ? (
            <textarea
              id={fieldId}
              value={value}
              onChange={(e) => handleChange(field.name, e.target.value)}
              onBlur={() => handleBlur(field.name)}
              disabled={isDisabled}
              required={field.required}
              aria-required={field.required}
              aria-invalid={!!showError}
              aria-describedby={showError ? errorId : field.helpText ? helpId : undefined}
              placeholder={field.placeholder}
              rows={field.rows || 3}
              className={inputClasses}
            />
          ) : (
            <input
              id={fieldId}
              type={field.type}
              value={value}
              onChange={(e) => {
                const newValue =
                  field.type === 'number' ? e.target.value : e.target.value;
                handleChange(field.name, newValue);
              }}
              onBlur={() => handleBlur(field.name)}
              disabled={isDisabled}
              required={field.required}
              aria-required={field.required}
              aria-invalid={!!showError}
              aria-describedby={showError ? errorId : field.helpText ? helpId : undefined}
              placeholder={field.placeholder}
              min={field.min}
              max={field.max}
              step={field.step}
              className={inputClasses}
            />
          )}

          {field.helpText && !showError && (
            <p id={helpId} className="mt-1 text-sm text-gray-500">
              {field.helpText}
            </p>
          )}

          {showError && (
            <p id={errorId} className="mt-1 text-sm text-red-600" role="alert">
              {error}
            </p>
          )}
        </div>
      );

      return fieldWrapper;
    },
    [state, handleChange, handleBlur]
  );

  const submitBtnClasses =
    submitButtonClassName ||
    'flex-1 px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed';

  const cancelBtnClasses =
    cancelButtonClassName ||
    'flex-1 px-4 py-2 bg-gray-300 text-gray-700 rounded-md hover:bg-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-500 disabled:opacity-50 disabled:cursor-not-allowed';

  return (
    <form onSubmit={handleSubmit} className={formClassName} noValidate>
      {fields.map((field) => renderField(field))}

      <div className="flex gap-3 pt-4">
        <button
          type="submit"
          disabled={disableOnSubmit && state.isSubmitting}
          className={submitBtnClasses}
        >
          {state.isSubmitting ? 'Submitting...' : submitText}
        </button>
        {onCancel && (
          <button
            type="button"
            onClick={onCancel}
            disabled={state.isSubmitting}
            className={cancelBtnClasses}
          >
            {cancelText}
          </button>
        )}
      </div>
    </form>
  );
}

export default DynamicForm;
