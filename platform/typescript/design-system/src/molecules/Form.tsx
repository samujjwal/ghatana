import React, { createContext, useContext, useState } from 'react';
import { tokens } from '@ghatana/tokens';

export interface FormContextType {
  errors: Record<string, string>;
  touched: Record<string, boolean>;
  values: Record<string, unknown>;
  setFieldValue: (name: string, value: unknown) => void;
  setFieldTouched: (name: string, touched: boolean) => void;
  setFieldError: (name: string, error: string) => void;
}

const FormContext = createContext<FormContextType | undefined>(undefined);

export interface FormProps {
  children: React.ReactNode;
  onSubmit: (values: Record<string, unknown>) => void;
  initialValues?: Record<string, unknown>;
  className?: string;
}

/**
 * Form component for managing form state
 */
export const Form: React.FC<FormProps> = ({
  children,
  onSubmit,
  initialValues = {},
  className,
}) => {
  const [values, setValues] = useState(initialValues);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [touched, setTouched] = useState<Record<string, boolean>>({});

  const setFieldValue = (name: string, value: unknown) => {
    setValues((prev) => ({ ...prev, [name]: value }));
  };

  const setFieldTouched = (name: string, isTouched: boolean) => {
    setTouched((prev) => ({ ...prev, [name]: isTouched }));
  };

  const setFieldError = (name: string, error: string) => {
    setErrors((prev) => ({ ...prev, [name]: error }));
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onSubmit(values);
  };

  const contextValue: FormContextType = {
    errors,
    touched,
    values,
    setFieldValue,
    setFieldTouched,
    setFieldError,
  };

  return (
    <FormContext.Provider value={contextValue}>
      <form onSubmit={handleSubmit} className={className}>
        {children}
      </form>
    </FormContext.Provider>
  );
};

Form.displayName = 'Form';

/**
 * Hook to access form context
 */
export function useFormContext() {
  const context = useContext(FormContext);
  if (!context) {
    throw new Error('useFormContext must be used within a Form component');
  }
  return context;
}

// FormField is exported from separate FormField.tsx file to avoid duplicates

export interface FormGroupProps {
  legend?: string;
  children: React.ReactNode;
  className?: string;
}

/**
 * FormGroup component for grouping related fields
 */
export const FormGroup: React.FC<FormGroupProps> = ({
  legend,
  children,
  className,
}) => {
  const groupStyles: React.CSSProperties = {
    border: `1px solid ${tokens.colors.palette.neutral[200]}`,
    borderRadius: tokens.borderRadius.md,
    padding: tokens.spacing[4],
    marginBottom: tokens.spacing[4],
  };

  const legendStyles: React.CSSProperties = {
    fontSize: tokens.typography.fontSize.base,
    fontWeight: 600,
    marginBottom: tokens.spacing[3],
    color: tokens.colors.palette.neutral[900],
  };

  return (
    <fieldset style={groupStyles} className={className}>
      {legend && <legend style={legendStyles}>{legend}</legend>}
      {children}
    </fieldset>
  );
};

FormGroup.displayName = 'FormGroup';

export interface FormSubmitButtonProps {
  children: React.ReactNode;
  loading?: boolean;
  disabled?: boolean;
  className?: string;
}

/**
 * FormSubmitButton component
 */
export const FormSubmitButton: React.FC<FormSubmitButtonProps> = ({
  children,
  loading,
  disabled,
  className,
}) => {
  const buttonStyles: React.CSSProperties = {
    padding: `${tokens.spacing[2]} ${tokens.spacing[4]}`,
    backgroundColor: tokens.colors.palette.primary[600],
    color: tokens.colors.white,
    border: 'none',
    borderRadius: tokens.borderRadius.md,
    fontSize: tokens.typography.fontSize.base,
    fontWeight: 600,
    cursor: disabled || loading ? 'not-allowed' : 'pointer',
    opacity: disabled || loading ? 0.5 : 1,
    transition: `all ${tokens.transitions.duration.fast}ms ease-in-out`,
  };

  return (
    <button
      type="submit"
      disabled={disabled || loading}
      style={buttonStyles}
      className={className}
    >
      {loading ? 'Loading...' : children}
    </button>
  );
};

FormSubmitButton.displayName = 'FormSubmitButton';
