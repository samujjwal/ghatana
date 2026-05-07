/**
 * LabeledInput Component
 *
 * Thin wrapper around @ghatana/design-system TextField.
 * Preserves local prop names (wrapperClassName, labelSrOnly) for backward
 * compatibility while delegating rendering to the canonical design-system
 * component.
 *
 * @doc.type component
 * @doc.purpose Accessible form input with mandatory label (DS-006 migration)
 * @doc.layer shared
 * @doc.pattern Form Component
 * @example
 * ```tsx
 * <LabeledInput
 *   id="email"
 *   label="Email address"
 *   type="email"
 *   value={email}
 *   onChange={(e) => setEmail(e.target.value)}
 * />
 * ```
 */

import React from 'react';
import { TextField } from '@ghatana/design-system';

interface LabeledInputProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'className'> {
  /** Visible label text — required for accessibility */
  label: string;
  /** Input identifier — links label to input via htmlFor */
  id: string;
  /** Optional helper text displayed below input */
  helperText?: string;
  /** Optional error message */
  error?: string;
  /** Whether to visually hide the label (screen-reader only) */
  labelSrOnly?: boolean;
  /** Custom className for the wrapper */
  wrapperClassName?: string;
}

export const LabeledInput = React.forwardRef<HTMLInputElement, LabeledInputProps>(
  ({ label, id, helperText, error, labelSrOnly = false, wrapperClassName, ...inputProps }, ref) => {
    return (
      <TextField
        label={label}
        id={id}
        helperText={helperText}
        error={error}
        className={wrapperClassName}
        inputRef={ref as React.Ref<HTMLInputElement>}
        inputProps={{ id, ...inputProps }}
      />
    );
  }
);

LabeledInput.displayName = 'LabeledInput';

/**
 * LabeledSelect Component
 *
 * Thin wrapper around @ghatana/design-system TextField in select mode.
 *
 * @doc.type component
 * @doc.purpose Accessible select dropdown with mandatory label (DS-006 migration)
 * @doc.layer shared
 * @doc.pattern Form Component
 */
interface LabeledSelectProps extends Omit<React.SelectHTMLAttributes<HTMLSelectElement>, 'className'> {
  label: string;
  id: string;
  helperText?: string;
  error?: string;
  labelSrOnly?: boolean;
  wrapperClassName?: string;
  children: React.ReactNode;
}

export const LabeledSelect = React.forwardRef<HTMLSelectElement, LabeledSelectProps>(
  ({ label, id, helperText, error, labelSrOnly = false, wrapperClassName, children, ...selectProps }, ref) => {
    return (
      <TextField
        select
        label={label}
        id={id}
        helperText={helperText}
        error={error}
        className={wrapperClassName}
        inputRef={ref as React.Ref<HTMLInputElement>}
        SelectProps={{ id, ...selectProps }}
      >
        {children}
      </TextField>
    );
  }
);

LabeledSelect.displayName = 'LabeledSelect';

export default LabeledInput;
