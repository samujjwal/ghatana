/**
 * Dynamic form generator based on collection schema.
 *
 * <p><b>Purpose</b><br>
 * Generates complete forms from collection schema definitions.
 * Handles validation, error display, and form submission.
 *
 * <p><b>Features</b><br>
 * - Automatic field rendering based on schema
 * - Real-time validation with error display
 * - Support for custom validation rules
 * - Accessible form with proper labels and ARIA attributes
 * - Loading and disabled states
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { DynamicForm } from '@/features/schema/components/DynamicForm';
 *
 * <DynamicForm
 *   schema={collectionSchema}
 *   onSubmit={handleFormSubmit}
 *   initialValues={existingData}
 * />
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose Dynamic form generation
 * @doc.layer frontend
 * @doc.pattern Container Component
 */

import React, { useState, useCallback, useMemo } from 'react';
import clsx from 'clsx';
import { DynamicField } from './DynamicField';
import type { MetaCollection, MetaField } from '../../../types/schema.types';

export interface DynamicFormProps {
  schema: MetaCollection;
  onSubmit: (formData: Record<string, unknown>) => void | Promise<void>;
  onCancel?: () => void;
  initialValues?: Record<string, unknown>;
  loading?: boolean;
  disabled?: boolean;
  submitLabel?: string;
  cancelLabel?: string;
}

interface FormErrors {
  [key: string]: string;
}

/**
 * Validates field value based on field schema.
 *
 * @param field field schema
 * @param value field value
 * @returns error message or null if valid
 */
function validateField(field: MetaField, value: unknown): string | null {
  // Check required fields
  if (field.required && (value === null || value === undefined || value === '')) {
    return `${field.name} is required`;
  }

  // Type-specific validation
  switch (field.type) {
    case 'email': {
      const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
      if (value && !emailRegex.test(String(value))) {
        return `${field.name} must be a valid email address`;
      }
      break;
    }

    case 'number': {
      if (value !== null && value !== undefined && value !== '') {
        const numValue = Number(value);
        if (isNaN(numValue)) {
          return `${field.name} must be a number`;
        }
        if (field.validations?.min !== undefined && numValue < Number(field.validations.min)) {
          return `${field.name} must be at least ${field.validations.min}`;
        }
        if (field.validations?.max !== undefined && numValue > Number(field.validations.max)) {
          return `${field.name} must be at most ${field.validations.max}`;
        }
      }
      break;
    }

    case 'text': {
      if (field.validations?.minLength && String(value).length < Number(field.validations.minLength)) {
        return `${field.name} must be at least ${field.validations.minLength} characters`;
      }
      if (field.validations?.maxLength && String(value).length > Number(field.validations.maxLength)) {
        return `${field.name} must be at most ${field.validations.maxLength} characters`;
      }
      break;
    }

    default:
      break;
  }

  return null;
}

/**
 * Dynamic form component that generates fields from schema.
 *
 * @param props form configuration
 * @returns rendered form
 */
export function DynamicForm({
  schema,
  onSubmit,
  onCancel,
  initialValues = {},
  loading = false,
  disabled = false,
  submitLabel = 'Submit',
  cancelLabel = 'Cancel',
}: DynamicFormProps) {
  const [formData, setFormData] = useState<Record<string, unknown>>(initialValues);
  const [errors, setErrors] = useState<FormErrors>({});
  const [isSubmitting, setIsSubmitting] = useState(false);

  const requiredFields = useMemo(
    () => schema.fields.filter((f) => f.required).map((f) => f.name).join(', '),
    [schema.fields]
  );

  const handleFieldChange = useCallback((fieldId: string, value: unknown) => {
    setFormData((prev) => ({ ...prev, [fieldId]: value }));
    // Clear error for this field
    setErrors((prev) => {
      const next = { ...prev };
      delete next[fieldId];
      return next;
    });
  }, []);

  const validateForm = useCallback((): boolean => {
    const newErrors: FormErrors = {};

    schema.fields.forEach((field) => {
      const error = validateField(field, formData[field.id]);
      if (error) {
        newErrors[field.id] = error;
      }
    });

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  }, [schema.fields, formData]);

  const handleSubmit = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault();

      if (!validateForm()) {
        return;
      }

      setIsSubmitting(true);
      try {
        await onSubmit(formData);
      } catch (error) {
        const message = error instanceof Error ? error.message : 'Form submission failed';
        setErrors((prev) => ({ ...prev, _form: message }));
      } finally {
        setIsSubmitting(false);
      }
    },
    [formData, validateForm, onSubmit]
  );

  return (
    <form onSubmit={handleSubmit} className="space-y-6">
      {/* Form-level error */}
      {errors._form && (
        <div className="p-4 bg-red-50 border border-red-200 rounded-md">
          <p className="text-sm text-red-700">{errors._form}</p>
        </div>
      )}

      {/* Dynamic fields */}
      <fieldset
        disabled={disabled || isSubmitting}
        className="space-y-4"
        aria-label={`${schema.name} Form`}
      >
        {schema.fields.length === 0 ? (
          <p className="text-sm text-gray-500">No fields available for this schema</p>
        ) : (
          schema.fields.map((field) => (
            <DynamicField
              key={field.id}
              field={field}
              value={formData[field.id]}
              onChange={(value) => handleFieldChange(field.id, value)}
              error={errors[field.id]}
              disabled={disabled || isSubmitting || loading}
            />
          ))
        )}
      </fieldset>

      {/* Required fields note */}
      {requiredFields && (
        <p className="text-xs text-gray-500">
          <span className="text-red-500">*</span> Required fields: {requiredFields}
        </p>
      )}

      {/* Form actions */}
      <div className="flex gap-3 pt-4">
        <button
          type="submit"
          disabled={disabled || isSubmitting || loading}
          className={clsx(
            'px-4 py-2 rounded-md font-medium text-white',
            'transition-colors duration-200',
            disabled || isSubmitting || loading
              ? 'bg-gray-400 cursor-not-allowed'
              : 'bg-blue-600 hover:bg-blue-700'
          )}
        >
          {isSubmitting || loading ? 'Submitting...' : submitLabel}
        </button>

        {onCancel && (
          <button
            type="button"
            onClick={onCancel}
            disabled={disabled || isSubmitting || loading}
            className={clsx(
              'px-4 py-2 rounded-md font-medium text-gray-700',
              'border border-gray-300 transition-colors duration-200',
              disabled || isSubmitting || loading
                ? 'bg-gray-100 cursor-not-allowed'
                : 'bg-white hover:bg-gray-50'
            )}
          >
            {cancelLabel}
          </button>
        )}
      </div>
    </form>
  );
}

