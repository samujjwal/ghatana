import React from 'react';

import {
  Select as BaseSelect,
  type SelectOption,
  type SelectProps as BaseSelectProps,
} from './Select';

export type SelectChangeEvent<T = unknown> =
  React.ChangeEvent<HTMLSelectElement> & {
    target: React.ChangeEvent<HTMLSelectElement>['target'] & { value: T };
  };

export interface SelectProps
  extends Omit<BaseSelectProps, 'error' | 'onChange' | 'options'> {
  options: SelectOption[];
  helperText?: string;
  validationState?: 'success' | 'error' | 'warning';
  error?: boolean | string;
  onChange?: (
    event: SelectChangeEvent<unknown>,
    child: React.ReactNode
  ) => void;
}

export const Select = React.forwardRef<HTMLSelectElement, SelectProps>(
  (
    {
      validationState,
      error,
      helperText,
      onChange,
      className,
      ...props
    },
    ref
  ) => {
    const errorMessage =
      typeof error === 'string'
        ? error
        : error || validationState === 'error'
          ? helperText ?? 'Invalid selection'
          : undefined;

    return (
      <BaseSelect
        ref={ref}
        className={[
          validationState === 'success' ? 'border-green-500' : '',
          validationState === 'warning' ? 'border-yellow-500' : '',
          className,
        ]
          .filter(Boolean)
          .join(' ')}
        error={errorMessage}
        helperText={errorMessage ? undefined : helperText}
        onChange={(event) => onChange?.(event as SelectChangeEvent<unknown>, null)}
        {...props}
      />
    );
  }
);

Select.displayName = 'Select';

export default Select;
