/**
 * Dynamic field renderer for schema-based forms.
 *
 * <p><b>Purpose</b><br>
 * Renders form fields dynamically based on field schema definitions.
 * Supports various field types with validation and error handling.
 *
 * <p><b>Supported Field Types</b><br>
 * - text: String input fields
 * - email: Email validation
 * - number: Numeric input with validation
 * - boolean: Checkbox input
 * - date: Date picker
 * - select: Dropdown with options
 * - textarea: Multi-line text
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { DynamicField } from '@/features/schema/components/DynamicField';
 *
 * <DynamicField
 *   field={fieldSchema}
 *   value={value}
 *   onChange={handleChange}
 *   error={errorMessage}
 * />
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose Dynamic field rendering
 * @doc.layer frontend
 * @doc.pattern Presentational Component
 */

import React, { useMemo } from 'react';
import clsx from 'clsx';
import type { MetaField } from '../../../types/schema.types';

export interface DynamicFieldProps {
  field: MetaField;
  value: unknown;
  onChange: (value: unknown) => void;
  error?: string;
  disabled?: boolean;
  placeholder?: string;
}

/**
 * Renders a dynamic form field based on schema definition.
 *
 * @param props field configuration and handlers
 * @returns rendered form field component
 */
export function DynamicField({
  field,
  value,
  onChange,
  error,
  disabled = false,
  placeholder,
}: DynamicFieldProps) {
  const fieldId = useMemo(() => `field-${field.id}`, [field.id]);

  const renderInput = () => {
    switch (field.type) {
      case 'text':
        return (
          <input
            id={fieldId}
            type="text"
            value={(value as string) || ''}
            onChange={(e) => onChange(e.target.value)}
            placeholder={placeholder || field.description}
            disabled={disabled}
            className={clsx(
              'px-3 py-2 border rounded-md',
              'text-sm font-medium leading-6',
              'placeholder-gray-400',
              'focus:outline-none focus:ring-2 focus:ring-blue-500',
              error ? 'border-red-500' : 'border-gray-300'
            )}
          />
        );

      case 'email':
        return (
          <input
            id={fieldId}
            type="email"
            value={(value as string) || ''}
            onChange={(e) => onChange(e.target.value)}
            placeholder={placeholder || 'user@example.com'}
            disabled={disabled}
            className={clsx(
              'px-3 py-2 border rounded-md',
              'text-sm font-medium leading-6',
              'placeholder-gray-400',
              'focus:outline-none focus:ring-2 focus:ring-blue-500',
              error ? 'border-red-500' : 'border-gray-300'
            )}
          />
        );

      case 'number':
        return (
          <input
            id={fieldId}
            type="number"
            value={(value as number) ?? ''}
            onChange={(e) => onChange(e.target.value ? Number(e.target.value) : null)}
            placeholder={placeholder}
            disabled={disabled}
            className={clsx(
              'px-3 py-2 border rounded-md',
              'text-sm font-medium leading-6',
              'placeholder-gray-400',
              'focus:outline-none focus:ring-2 focus:ring-blue-500',
              error ? 'border-red-500' : 'border-gray-300'
            )}
          />
        );

      case 'boolean':
        return (
          <input
            id={fieldId}
            type="checkbox"
            checked={(value as boolean) || false}
            onChange={(e) => onChange(e.target.checked)}
            disabled={disabled}
            className="w-4 h-4 rounded border-gray-300 focus:ring-blue-500"
          />
        );

      case 'date':
        return (
          <input
            id={fieldId}
            type="date"
            value={(value as string) || ''}
            onChange={(e) => onChange(e.target.value)}
            disabled={disabled}
            className={clsx(
              'px-3 py-2 border rounded-md',
              'text-sm font-medium leading-6',
              'focus:outline-none focus:ring-2 focus:ring-blue-500',
              error ? 'border-red-500' : 'border-gray-300'
            )}
          />
        );

      case 'textarea':
        return (
          <textarea
            id={fieldId}
            value={(value as string) || ''}
            onChange={(e) => onChange(e.target.value)}
            placeholder={placeholder || field.description}
            disabled={disabled}
            rows={4}
            className={clsx(
              'px-3 py-2 border rounded-md',
              'text-sm font-medium leading-6',
              'placeholder-gray-400',
              'focus:outline-none focus:ring-2 focus:ring-blue-500',
              error ? 'border-red-500' : 'border-gray-300'
            )}
          />
        );

      case 'select': {
        const options = field.validations?.options as Array<{ value: unknown; label: string }>;
        return (
          <select
            id={fieldId}
            value={(value as string) || ''}
            onChange={(e) => onChange(e.target.value)}
            disabled={disabled}
            className={clsx(
              'px-3 py-2 border rounded-md',
              'text-sm font-medium leading-6',
              'focus:outline-none focus:ring-2 focus:ring-blue-500',
              error ? 'border-red-500' : 'border-gray-300'
            )}
          >
            <option value="">-- Select --</option>
            {options?.map((opt) => (
              <option key={String(opt.value)} value={String(opt.value)}>
                {opt.label}
              </option>
            ))}
          </select>
        );
      }

      default:
        return (
          <input
            id={fieldId}
            type="text"
            value={(value as string) || ''}
            onChange={(e) => onChange(e.target.value)}
            placeholder={placeholder}
            disabled={disabled}
            className={clsx(
              'px-3 py-2 border rounded-md',
              'text-sm font-medium leading-6',
              'placeholder-gray-400',
              'focus:outline-none focus:ring-2 focus:ring-blue-500',
              error ? 'border-red-500' : 'border-gray-300'
            )}
          />
        );
    }
  };

  return (
    <div className="flex flex-col gap-2">
      <label htmlFor={fieldId} className="block text-sm font-medium text-gray-700">
        {field.name}
        {field.required && <span className="text-red-500 ml-1">*</span>}
      </label>
      {renderInput()}
      {error && <p className="text-sm text-red-500">{error}</p>}
      {field.description && !error && (
        <p className="text-xs text-gray-500">{field.description}</p>
      )}
    </div>
  );
}

