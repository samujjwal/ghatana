import { FormField } from './FormField';
import { Input } from '../Input/Input';

import type { InputProps } from '../Input/Input';

/**
 *
 */
export interface FormInputProps extends Omit<InputProps, 'onChange' | 'onBlur' | 'value' | 'error'> {
  name: string;
  validate?: (value: unknown) => string | undefined;
}

/**
 * FormInput component that integrates the Input component with the Form context
 * 
 * @example
 * ```tsx
 * <Form initialValues={{ email: '' }} onSubmit={handleSubmit}>
 *   <FormInput name="email" label="Email" type="email" required />
 *   <Button type="submit">Submit</Button>
 * </Form>
 * ```
 */
export const FormInput = ({ name, validate, ...props }: FormInputProps) => {
  return (
    <FormField name={name} validate={validate}>
      {({ value, onChange, onBlur, error, touched }) => (
        <Input
          {...props}
          name={name}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          onBlur={onBlur}
          error={error}
        />
      )}
    </FormField>
  );
};
