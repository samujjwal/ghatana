import { useEffect } from 'react';

import { useForm } from './Form';

import type { ReactNode} from 'react';

/**
 *
 */
export interface FormFieldProps {
  name: string;
  children: (props: FormFieldRenderProps) => ReactNode;
  validate?: (value: unknown) => string | undefined;
}

/**
 *
 */
export interface FormFieldRenderProps {
  name: string;
  value: unknown;
  onChange: (value: unknown) => void;
  onBlur: () => void;
  error?: string;
  touched: boolean;
}

/**
 * FormField component that connects form fields to the Form context
 */
export const FormField = ({ name, children, validate }: FormFieldProps) => {
  const { 
    values, 
    errors, 
    touched, 
    setFieldValue, 
    setFieldError, 
    setFieldTouched 
  } = useForm();

  // Get the field value
  const value = values[name];
  
  // Get the field error
  const error = errors[name];
  
  // Get the field touched state
  const isTouched = touched[name] || false;

  // Validate the field when value changes
  useEffect(() => {
    if (validate) {
      const validationError = validate(value);
      if (validationError) {
        setFieldError(name, validationError);
      } else {
        // Clear the error if it exists
        if (errors[name]) {
          setFieldError(name, '');
        }
      }
    }
  }, [value, name, validate, setFieldError, errors]);

  // Handle field change
  const handleChange = (newValue: unknown) => {
    setFieldValue(name, newValue);
  };

  // Handle field blur
  const handleBlur = () => {
    setFieldTouched(name, true);
  };

  // Create the render props
  const renderProps: FormFieldRenderProps = {
    name,
    value,
    onChange: handleChange,
    onBlur: handleBlur,
    error: isTouched ? error : undefined,
    touched: isTouched,
  };

  return <>{children(renderProps)}</>;
};
