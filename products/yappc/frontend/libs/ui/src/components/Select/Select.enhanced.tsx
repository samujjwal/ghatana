/**
 * Enhanced Select Component (Molecule)
 *
 * A composable select dropdown with validation states and design token integration.
 * Built on MUI Select for enhanced functionality.
 *
 * @packageDocumentation
 */

import { CheckCircle as CheckCircleIcon } from 'lucide-react';
import { AlertCircle as ErrorIcon } from 'lucide-react';
import { Select as MuiSelect, FormControl, InputLabel, MenuItem, FormHelperText, type SelectProps as MuiSelectProps, type SelectChangeEvent } from '@ghatana/ui';
import { getPaletteMain } from '../../utils/safePalette';
import {
  borderRadiusSm,
  borderRadiusMd,
  spacingSm,
  fontSizeSm,
} from '@ghatana/yappc-shared-ui-core/tokens';
import React from 'react';

// (types imported above in the main import for consolidation)

/**
 *
 */
export interface SelectOption {
  value: string;
  label: string;
  disabled?: boolean;
}

/**
 *
 */
export interface SelectProps extends Omit<MuiSelectProps, 'onChange'> {
  /**
   * Select shape variant using design tokens
   * - rounded: Standard rounded (4px)
   * - soft: Softer rounded (8px)
   * - square: Minimal rounding (2px)
   */
  shape?: 'rounded' | 'soft' | 'square';

  /**
   * Select options
   */
  options: SelectOption[];

  /**
   * Helper text to display below select
   */
  helperText?: string;

  /**
   * Validation state
   * - success: Shows green check icon
   * - error: Shows red error icon (default MUI behavior)
   * - warning: Shows warning state
   */
  validationState?: 'success' | 'error' | 'warning';

  /**
   * Change handler with typed event
   */
  onChange?: (event: SelectChangeEvent<unknown>, child: React.ReactNode) => void;
}

/** Map shape to Tailwind border-radius classes */
const shapeClasses: Record<string, string> = {
  rounded: 'rounded',
  soft: 'rounded-lg',
  square: 'rounded-sm',
};

/** Map validation state to Tailwind border colors */
const validationClasses: Record<string, string> = {
  success: 'border-green-500 focus-within:border-green-500 focus-within:ring-2 focus-within:ring-green-500/20',
  error: 'border-red-500 focus-within:border-red-500 focus-within:ring-2 focus-within:ring-red-500/20',
  warning: 'border-yellow-500 focus-within:border-yellow-500 focus-within:ring-2 focus-within:ring-yellow-500/20',
};

/**
 * Select Component
 *
 * A molecule component for dropdown selection with validation and design tokens.
 *
 * ## Features
 * - ✅ WCAG 2.1 AA compliant (44px minimum height)
 * - ✅ Design token integration
 * - ✅ Three shape variants (rounded, soft, square)
 * - ✅ Validation states (success, error, warning)
 * - ✅ Accessible labels and helper text
 * - ✅ Keyboard navigation
 * - ✅ Native MUI Select features
 *
 * @example Basic Usage
 * ```tsx
 * <Select
 *   label="Country"
 *   options={[
 *     { value: 'us', label: 'United States' },
 *     { value: 'uk', label: 'United Kingdom' },
 *   ]}
 *   onChange={(e) => setCountry(e.target.value)}
 * />
 * ```
 *
 * @example With Validation
 * ```tsx
 * <Select
 *   label="Country"
 *   options={countries}
 *   value={selectedCountry}
 *   validationState="success"
 *   helperText="Country selected"
 * />
 * ```
 */
export const Select = React.forwardRef<HTMLDivElement, SelectProps>((props, ref) => {
  const {
    shape = 'rounded',
    options,
    label,
    helperText,
    validationState,
    error,
    fullWidth = true,
    ...rest
  } = props;

  // Build helper text with validation icon
  const helperContent = helperText ? (
    <span style={{ display: 'flex', alignItems: 'center', gap: spacingSm }}>
      {validationState === 'success' && (
        <CheckCircleIcon
          className="text-base text-green-600"
          aria-hidden="true"
        />
      )}
      {validationState === 'error' && (
        <ErrorIcon className="text-base text-red-600" aria-hidden="true" />
      )}
      {validationState === 'warning' && (
        <ErrorIcon className="text-base text-amber-600" aria-hidden="true" />
      )}
      {helperText}
    </span>
  ) : undefined;

  const selectClassName = [
    'min-h-[44px] transition-all duration-200',
    shapeClasses[shape] || shapeClasses.rounded,
    validationState ? validationClasses[validationState] : '',
    'contrast-more:border-2',
  ].filter(Boolean).join(' ');

  return (
    <FormControl
      ref={ref}
      fullWidth={fullWidth}
      error={error || validationState === 'error'}
      className={selectClassName}
    >
      {label && <InputLabel>{label}</InputLabel>}
      <MuiSelect label={label} {...rest}>
        {options.map((option) => (
          <MenuItem
            key={option.value}
            value={option.value}
            disabled={option.disabled}
            className="min-h-[44px] focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-[-2px]"
          >
            {option.label}
          </MenuItem>
        ))}
      </MuiSelect>
      {helperContent && <FormHelperText>{helperContent}</FormHelperText>}
    </FormControl>
  );
});

Select.displayName = 'Select';

export default Select;
