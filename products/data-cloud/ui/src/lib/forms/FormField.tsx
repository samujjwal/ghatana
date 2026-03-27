/**
 * Form Field Components
 *
 * Accessible form field building blocks used with react-hook-form + Zod.
 * See `./validation.ts` for schema definitions.
 *
 * @doc.type component
 * @doc.purpose Accessible form field building blocks
 * @doc.layer frontend
 * @doc.pattern Presentational Component
 */

import React from 'react';

// ─────────────────────────────────────────────────────────────────────────────
// FieldError
// ─────────────────────────────────────────────────────────────────────────────

interface FieldErrorProps {
  /** Must match the `id` given to the input's `aria-describedby`. */
  id: string;
  error?: { message?: string };
  className?: string;
}

/**
 * Accessible field error message.
 *
 * Renders nothing when `error` is undefined. When rendered, the message is
 * announced to screen readers via `role="alert"` and linked to the corresponding
 * input via the shared `id`.
 *
 * @example
 * ```tsx
 * <input aria-describedby="name-error" {...register('name')} />
 * <FieldError id="name-error" error={errors.name} />
 * ```
 */
export function FieldError({ id, error, className = '' }: FieldErrorProps): React.ReactElement | null {
  if (!error?.message) return null;
  return (
    <p
      id={id}
      role="alert"
      aria-live="polite"
      className={`mt-1 text-xs text-red-600 dark:text-red-400 ${className}`}
    >
      {error.message}
    </p>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// FormField
// ─────────────────────────────────────────────────────────────────────────────

interface FormFieldProps {
  /** Visible label text. */
  label: string;
  /** Must match the `id` on the rendered input element. */
  htmlFor: string;
  /** Validation error from react-hook-form. */
  error?: { message?: string };
  /** Whether the field is required — shows a visual asterisk. */
  required?: boolean;
  /** Optional hint displayed below the label. */
  hint?: string;
  /** The form control (input, select, textarea, etc.). */
  children: React.ReactNode;
}

/**
 * Labelled form field with optional hint and inline error message.
 *
 * Associates the label, hint, and error with the child input via
 * `htmlFor` + `aria-describedby`, so screen readers correctly announce
 * all contextual information.
 *
 * @example
 * ```tsx
 * <FormField label="Collection name" htmlFor="name" error={errors.name} required>
 *   <input id="name" {...register('name')} className="..." />
 * </FormField>
 * ```
 */
export function FormField({
  label,
  htmlFor,
  error,
  required,
  hint,
  children,
}: FormFieldProps): React.ReactElement {
  const hintId = hint ? `${htmlFor}-hint` : undefined;
  const errorId = `${htmlFor}-error`;
  const describedBy = [hintId, error ? errorId : undefined].filter(Boolean).join(' ') || undefined;

  return (
    <div className="mb-4">
      <label
        htmlFor={htmlFor}
        className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1"
      >
        {label}
        {required && (
          <span aria-hidden="true" className="ml-1 text-red-500">
            *
          </span>
        )}
      </label>

      {hint && (
        <p id={hintId} className="text-xs text-gray-500 dark:text-gray-400 mb-1">
          {hint}
        </p>
      )}

      {/* Inject aria attributes onto the child input */}
      {React.isValidElement(children)
        ? React.cloneElement(children as React.ReactElement<Record<string, unknown>>, {
            'aria-describedby': describedBy,
            'aria-invalid': error ? ('true' as const) : undefined,
            'aria-required': required ? ('true' as const) : undefined,
          })
        : children}

      <FieldError id={errorId} error={error} />
    </div>
  );
}
