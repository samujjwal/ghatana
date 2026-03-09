import React, { createContext, useContext, useState } from 'react';
// import { tokens } from '@ghatana/tokens'; // TODO: Fix tokens import when available

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
    border: '1px solid #E0E0E0', // TODO: Replace with tokens.colors.neutral[200] when available
    borderRadius: '0.25rem', // TODO: Replace with tokens.borderRadius.md when available
    padding: '1rem', // TODO: Replace with tokens.spacing[4] when available
    marginBottom: '1rem', // TODO: Replace with tokens.spacing[4] when available
  };

  const legendStyles: React.CSSProperties = {
    fontSize: 16, // TODO: Replace with tokens.typography.fontSize.base when available
    fontWeight: 600,
    marginBottom: '0.75rem', // TODO: Replace with tokens.spacing[3] when available
    color: '#212121', // TODO: Replace with tokens.colors.neutral[900] when available
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
    padding: '0.5rem 1rem', // TODO: Replace with tokens.spacing[2] and tokens.spacing[4] when available
    backgroundColor: '#3498db', // TODO: Replace with tokens.colors.primary[600] when available
    color: '#fff', // TODO: Replace with tokens.colors.neutral[0] when available
    border: 'none',
    borderRadius: '0.25rem', // TODO: Replace with tokens.borderRadius.md when available
    fontSize: 16, // TODO: Replace with tokens.typography.fontSize.base when available
    fontWeight: 600,
    cursor: disabled || loading ? 'not-allowed' : 'pointer',
    opacity: disabled || loading ? 0.5 : 1,
    transition: 'all 0.2s ease-in-out', // TODO: Replace with tokens.transitions.duration.fast and tokens.transitions.easing.easeInOut when available
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
