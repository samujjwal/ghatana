import * as React from 'react';
import { cn } from '@ghatana/utils';
import {
  fontSize,
  fontWeight,
  palette,
  lightColors,
  darkColors,
} from '@ghatana/tokens';
import { useTheme } from '@ghatana/theme';

import { useId } from '../hooks/useId';

export interface FormFieldProps extends React.HTMLAttributes<HTMLDivElement> {
  label: React.ReactNode;
  description?: React.ReactNode;
  errorMessage?: React.ReactNode;
  required?: boolean;
  id?: string;
  children: React.ReactElement;
}

/**
 * FormField – wraps controls with label, helper text, and error messaging.
 */
export const FormField = React.forwardRef<HTMLDivElement, FormFieldProps>((props, ref) => {
  const {
    label,
    description,
    errorMessage,
    required = false,
    id,
    className,
    children,
    ...rest
  } = props;

  const generatedId = useId('gh-field');
  const fieldId = id ?? generatedId;
  const descriptionId = description ? `${fieldId}-description` : undefined;
  const errorId = errorMessage ? `${fieldId}-error` : undefined;

  const { resolvedTheme } = useTheme();
  const surface = resolvedTheme === 'dark' ? darkColors : lightColors;

  // Ensure we have a single child element
  // Define the expected props for form controls
  interface FormControlProps {
    id?: string;
    'aria-describedby'?: string;
    'aria-invalid'?: boolean;
    required?: boolean;
  }

  // Get the single child element
  const child = React.Children.only(children);
  
  // Get the current props from the child
  const childProps = child.props as FormControlProps;
  
  // Prepare props to be injected
  const injectedProps: FormControlProps = {
    id: fieldId,
    'aria-describedby': [
      childProps['aria-describedby'],
      descriptionId,
      errorId,
    ]
      .filter(Boolean)
      .join(' ') || undefined,
    'aria-invalid': errorMessage ? true : undefined,
    required: required,
  };

  // Merge props, allowing child props to override injected ones
  const mergedProps: FormControlProps = {
    ...injectedProps,
    ...(childProps.id && { id: childProps.id }),
    ...(childProps.required !== undefined && { required: childProps.required }),
    ...(childProps['aria-invalid'] !== undefined && { 
      'aria-invalid': childProps['aria-invalid'] 
    }),
  };

  // Clone the child with merged props
  const control = React.isValidElement(child)
    ? React.cloneElement(child, mergedProps)
    : child;

  return (
    <div
      ref={ref}
      className={cn('gh-form-field', className)}
      data-invalid={errorMessage ? 'true' : undefined}
      {...rest}
    >
      <label
        htmlFor={fieldId}
        className="gh-form-field__label"
        style={{
          display: 'flex',
          alignItems: 'baseline',
          gap: '4px',
          marginBottom: '4px',
          fontSize: fontSize.sm,
          fontWeight: fontWeight.medium,
          color: surface.text.primary,
        }}
      >
        <span>{label}</span>
        {required ? (
          <span
            aria-hidden="true"
            style={{
              color: palette.error[500],
              fontWeight: fontWeight.semibold,
            }}
          >
            *
          </span>
        ) : null}
      </label>

      {control}

      {description ? (
        <p
          id={descriptionId}
          className="gh-form-field__description"
          style={{
            marginTop: '6px',
            fontSize: fontSize.sm,
            color: surface.text.secondary,
          }}
        >
          {description}
        </p>
      ) : null}

      {errorMessage ? (
        <p
          id={errorId}
          className="gh-form-field__error"
          style={{
            marginTop: '6px',
            fontSize: fontSize.sm,
            color: palette.error[500],
            fontWeight: fontWeight.medium,
          }}
        >
          {errorMessage}
        </p>
      ) : null}
    </div>
  );
});

FormField.displayName = 'FormField';
