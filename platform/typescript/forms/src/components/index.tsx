import React from 'react';

export interface FormProps extends React.FormHTMLAttributes<HTMLFormElement> {
  children: React.ReactNode;
}

/**
 * @doc.type component
 * @doc.purpose Base form wrapper with accessible semantics.
 * @doc.layer platform
 * @doc.pattern UI Component
 */
export const Form = React.forwardRef<HTMLFormElement, FormProps>(
  ({ children, ...props }, ref) => (
    <form ref={ref} noValidate {...props}>
      {children}
    </form>
  )
);
Form.displayName = 'Form';

export interface FormFieldProps {
  label: string;
  htmlFor: string;
  error?: string;
  required?: boolean;
  children: React.ReactNode;
}

/**
 * @doc.type component
 * @doc.purpose Accessible labeled field wrapper with inline error display.
 * @doc.layer platform
 * @doc.pattern UI Component
 */
export function FormField({ label, htmlFor, error, required, children }: FormFieldProps) {
  return (
    <div>
      <label htmlFor={htmlFor}>
        {label}
        {required && <span aria-hidden="true"> *</span>}
      </label>
      {children}
      {error && (
        <p role="alert" id={`${htmlFor}-error`}>
          {error}
        </p>
      )}
    </div>
  );
}

export interface FormErrorProps {
  message?: string;
}

export function FormError({ message }: FormErrorProps) {
  if (!message) return null;
  return <p role="alert">{message}</p>;
}

export interface FormSuccessProps {
  message?: string;
}

export function FormSuccess({ message }: FormSuccessProps) {
  if (!message) return null;
  return <p role="status">{message}</p>;
}
