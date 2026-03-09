/**
 * Enhanced TextField Component (Molecule)
 *
 * A composable text input component with label, helper text, and validation.
 * Integrates design tokens from the design system.
 *
 * @packageDocumentation
 */

import { CheckCircle as CheckCircleIcon } from 'lucide-react';
import { AlertCircle as ErrorIcon } from 'lucide-react';
import { TextField as BaseTextField, InputAdornment, IconButton, FormHelperText, type TextFieldProps as BaseTextFieldProps } from '@ghatana/ui';
import {
  borderRadiusSm,
  borderRadiusMd,
  spacingSm,
  spacingMd,
  fontSizeSm,
} from '@ghatana/yappc-shared-ui-core/tokens';
import React from 'react';

// (types consolidated into main import above)

/**
 *
 */
export interface TextFieldProps extends BaseTextFieldProps {
  /**
   * Input shape variant using design tokens
   * - rounded: Standard rounded (4px)
   * - soft: Softer rounded (8px)
   * - square: Minimal rounding (2px)
   */
  shape?: 'rounded' | 'soft' | 'square';

  /**
   * Icon to display at the start of the input
   * Automatically marked as decorative
   */
  startIcon?: React.ReactNode;

  /**
   * Icon to display at the end of the input
   * Automatically marked as decorative
   */
  endIcon?: React.ReactNode;

  /**
   * Callback for when the end icon is clicked
   */
  onEndIconClick?: () => void;

  /**
   * Show character count
   * Displays current/max if maxLength is set
   */
  showCharacterCount?: boolean;

  /**
   * Maximum character length
   * Works with showCharacterCount
   */
  maxLength?: number;

  /**
   * Validation state
   * - success: Shows green check icon
   * - error: Shows red error icon (default MUI behavior)
   * - warning: Shows warning state
   */
  validationState?: 'success' | 'error' | 'warning';

  /**
   * Show clear button when input has value
   */
  clearable?: boolean;

  /**
   * Callback when clear button is clicked
   */
  onClear?: () => void;
}

const shapeClasses: Record<string, string> = {
  rounded: 'rounded',
  soft: 'rounded-lg',
  square: 'rounded-sm',
};

const validationClasses: Record<string, string> = {
  success: 'border-green-500 focus-within:border-green-600 focus-within:ring-2 focus-within:ring-green-200',
  error: 'border-red-500 focus-within:border-red-600 focus-within:ring-2 focus-within:ring-red-200',
  warning: 'border-yellow-500 focus-within:border-yellow-600 focus-within:ring-2 focus-within:ring-yellow-200',
};

/**
 * TextField Component
 *
 * A molecule component combining input, label, helper text, and validation.
 *
 * ## Features
 * - ✅ WCAG 2.1 AA compliant (44px minimum height)
 * - ✅ Design token integration
 * - ✅ Validation states (success, error, warning)
 * - ✅ Icon support (start/end)
 * - ✅ Character count
 * - ✅ Clearable input
 * - ✅ Accessible labels and helper text
 * - ✅ Keyboard navigation
 *
 * @example Basic Usage
 * ```tsx
 * <TextField
 *   label="Email"
 *   placeholder="Enter your email"
 *   required
 * />
 * ```
 *
 * @example With Validation
 * ```tsx
 * <TextField
 *   label="Username"
 *   validationState="success"
 *   helperText="Username is available"
 * />
 * ```
 *
 * @example With Icons
 * ```tsx
 * <TextField
 *   label="Search"
 *   startIcon={<SearchIcon />}
 *   clearable
 *   onClear={handleClear}
 * />
 * ```
 */
export const TextField = React.forwardRef<HTMLDivElement, TextFieldProps>((props, ref) => {
  const {
    shape = 'rounded',
    startIcon,
    endIcon,
    onEndIconClick,
    showCharacterCount = false,
    maxLength,
    validationState,
    clearable = false,
    onClear,
    value,
    InputProps: inputPropsProp,
    helperText,
    onChange,
    ...rest
  } = props;

  const [internalValue, setInternalValue] = React.useState(value || '');

  React.useEffect(() => {
    setInternalValue(value || '');
  }, [value]);

  const handleChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const newValue = event.target.value;

    // Enforce max length
    if (maxLength && newValue.length > maxLength) {
      return;
    }

    setInternalValue(newValue);
    onChange?.(event);
  };

  const handleClear = () => {
    setInternalValue('');
    onClear?.();
  };

  // Build adornments
  const startAdornment = startIcon ? (
    <InputAdornment position="start">
      <span aria-hidden="true">{startIcon}</span>
    </InputAdornment>
  ) : undefined;

  const buildEndAdornment = () => {
    const elements: React.ReactNode[] = [];

    // Validation icon
    if (validationState === 'success') {
      elements.push(
        <CheckCircleIcon
          key="success-icon"
          tone="success"
          size={16}
          aria-hidden="true"
        />
      );
    } else if (validationState === 'error') {
      elements.push(
        <ErrorIcon key="error-icon" tone="danger" size={16} aria-hidden="true" />
      );
    } else if (validationState === 'warning') {
      elements.push(
        <ErrorIcon key="warning-icon" tone="warning" size={16} aria-hidden="true" />
      );
    }

    // Clear button
    if (clearable && internalValue) {
      elements.push(
        <IconButton
          key="clear-button"
          aria-label="Clear input"
          onClick={handleClear}
          edge="end"
          size="sm"
        >
          ×
        </IconButton>
      );
    }

    // Custom end icon
    if (endIcon) {
      if (onEndIconClick) {
        elements.push(
          <IconButton
            key="end-icon"
            aria-label="Action"
            onClick={onEndIconClick}
            edge="end"
            size="sm"
          >
            {endIcon}
          </IconButton>
        );
      } else {
        elements.push(
          <span key="end-icon" aria-hidden="true">
            {endIcon}
          </span>
        );
      }
    }

    return elements.length > 0 ? (
      <InputAdornment position="end">{elements}</InputAdornment>
    ) : undefined;
  };

  // Character count helper text
  const characterCountText = showCharacterCount && maxLength ? (
    <span>
      {String(internalValue).length}/{maxLength}
    </span>
  ) : null;

  const combinedHelperText = (
    <>
      {helperText}
      {characterCountText && (
        <>
          {helperText && ' '}
          {characterCountText}
        </>
      )}
    </>
  );

  const inputProps = {
    ...inputPropsProp,
    startAdornment,
    endAdornment: buildEndAdornment(),
  };

  const fieldClassName = [
    'min-h-[44px] transition-[border-color,box-shadow] duration-200',
    shapeClasses[shape] || shapeClasses.rounded,
    validationState ? validationClasses[validationState] : '',
    'contrast-more:border-2',
    rest.className,
  ].filter(Boolean).join(' ');

  return (
    <BaseTextField
      ref={ref}
      value={internalValue}
      onChange={handleChange}
      InputProps={inputProps}
      helperText={combinedHelperText || undefined}
      error={validationState === 'error'}
      className={fieldClassName}
      inputProps={{
        ...inputPropsProp?.inputProps,
        maxLength,
        'aria-invalid': validationState === 'error' ? true : undefined,
      }}
      {...rest}
    />
  );
});

TextField.displayName = 'TextField';

export default TextField;
