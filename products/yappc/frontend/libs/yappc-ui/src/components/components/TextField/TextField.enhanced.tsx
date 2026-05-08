import React from 'react';

import {
  TextField as BaseTextField,
  type TextFieldProps as BaseTextFieldProps,
} from './TextField';

export interface TextFieldProps extends BaseTextFieldProps {
  validationState?: 'success' | 'error' | 'warning';
  clearable?: boolean;
  onClear?: () => void;
  showCharacterCount?: boolean;
  maxLength?: number;
}

export const TextField = React.forwardRef<HTMLDivElement, TextFieldProps>(
  (
    {
      validationState,
      clearable = false,
      onClear,
      showCharacterCount = false,
      maxLength,
      helperText,
      value,
      endIcon,
      onEndIconClick,
      ...props
    },
    ref
  ) => {
    const hasValue =
      typeof value === 'string' || typeof value === 'number'
        ? String(value).length > 0
        : false;
    const characterCount =
      showCharacterCount && maxLength !== undefined
        ? `${String(value ?? '').length}/${maxLength}`
        : undefined;
    const resolvedEndIcon =
      clearable && hasValue ? (
        <button
          aria-label="Clear input"
          className="text-gray-500 hover:text-gray-900"
          onClick={onClear}
          type="button"
        >
          x
        </button>
      ) : (
        endIcon
      );

    return (
      <BaseTextField
        ref={ref}
        value={value}
        endIcon={resolvedEndIcon}
        onEndIconClick={clearable && hasValue ? undefined : onEndIconClick}
        helperText={[helperText, characterCount].filter(Boolean).join(' ') || undefined}
        error={props.error ?? validationState === 'error'}
        InputProps={{
          ...props.InputProps,
          inputProps: {
            ...props.InputProps?.inputProps,
            maxLength,
            'aria-invalid': validationState === 'error' ? true : undefined,
          },
        }}
        {...props}
      />
    );
  }
);

TextField.displayName = 'TextField';

export default TextField;
