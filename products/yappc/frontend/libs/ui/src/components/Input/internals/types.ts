import type { InputHTMLAttributes, ReactNode } from 'react';

/**
 * Autocomplete option for Input suggestions.
 *
 * @example
 * ```tsx
 * const option: AutocompleteOption = {
 *   value: 'us',
 *   label: 'United States'
 * };
 * ```
 */
export interface AutocompleteOption {
  /**
   * Option value
   */
  value: string;

  /**
   * Option label (displayed to user)
   */
  label?: string;
}

/**
 * Input mask type for automatic input formatting.
 *
 * - `phone`: (123) 456-7890
 * - `date`: MM/DD/YYYY
 * - `time`: HH:MM (24h)
 * - `creditCard`: XXXX XXXX XXXX XXXX
 * - `zipCode`: 12345 or 12345-6789
 * - `currency`: $1,234.56
 * - `custom`: Custom pattern via maskPattern prop
 */
export type MaskType =
  | 'phone'
  | 'date'
  | 'time'
  | 'creditCard'
  | 'zipCode'
  | 'currency'
  | 'custom';

/**
 * Input format type for text transformation.
 *
 * - `uppercase`: Convert to uppercase
 * - `lowercase`: Convert to lowercase
 * - `capitalize`: Capitalize first letter of each word
 * - `number`: Format as number with commas
 * - `percent`: Format as percentage
 * - `email`: Format as email (lowercase)
 * - `trim`: Trim whitespace
 * - `custom`: Custom formatter via formatter prop
 */
export type FormatType =
  | 'uppercase'
  | 'lowercase'
  | 'capitalize'
  | 'number'
  | 'percent'
  | 'email'
  | 'trim'
  | 'custom';

/**
 * Error severity level for error display styling.
 */
export type ErrorSeverity = 'error' | 'warning' | 'info';

/**
 * Input component theme preference.
 */
export type InputTheme = 'light' | 'dark' | 'system';

/**
 * Input size variant.
 */
export type InputSize = 'small' | 'medium' | 'large';

/**
 * Arrow key direction for keyboard navigation.
 */
export type ArrowDirection = 'up' | 'down' | 'left' | 'right';

/**
 * Props for the Input component.
 *
 * Extends standard HTML input attributes with additional features including
 * validation states, icons, character counter, clear button, password visibility toggle,
 * loading state, autocomplete, input masking, text formatting, debounced input,
 * and keyboard navigation.
 *
 * @example
 * ```tsx
 * <Input
 *   label="Email"
 *   type="email"
 *   placeholder="Enter your email"
 *   helperText="We'll never share your email"
 * />
 * ```
 */
export interface InputProps
  extends Omit<InputHTMLAttributes<HTMLInputElement>, 'size'> {
  /**
   * Input label
   */
  label?: string;

  /**
   * Helper text displayed below the input
   */
  helperText?: string;

  /**
   * Error message (overrides helperText when present)
   */
  error?: string;

  /**
   * Error message severity level
   *
   * @default 'error'
   */
  errorSeverity?: ErrorSeverity;

  /**
   * Show error icon
   *
   * @default true
   */
  showErrorIcon?: boolean;

  /**
   * Input size variant
   *
   * @default 'medium'
   */
  size?: InputSize;

  /**
   * Full width input
   *
   * @default false
   */
  fullWidth?: boolean;

  /**
   * Icon to display at the start of the input
   */
  startIcon?: ReactNode;

  /**
   * Icon to display at the end of the input
   */
  endIcon?: ReactNode;

  /**
   * Show character counter
   *
   * @default false
   */
  showCounter?: boolean;

  /**
   * Input is required
   *
   * @default false
   */
  required?: boolean;

  /**
   * Show clear button when input has value
   *
   * @default false
   */
  clearable?: boolean;

  /**
   * Callback when clear button is clicked
   */
  onClear?: () => void;

  /**
   * Show password visibility toggle for password inputs
   *
   * @default false
   */
  showPasswordToggle?: boolean;

  /**
   * Show loading indicator
   *
   * @default false
   */
  loading?: boolean;

  /**
   * Enable autocomplete with suggestions
   *
   * @default false
   */
  autocomplete?: boolean;

  /**
   * Autocomplete options
   *
   * @default []
   */
  options?: AutocompleteOption[];

  /**
   * Callback when an autocomplete option is selected
   */
  onOptionSelect?: (option: AutocompleteOption) => void;

  /**
   * Maximum number of autocomplete options to show
   *
   * @default 5
   */
  maxOptions?: number;

  /**
   * Enable input masking
   */
  mask?: MaskType;

  /**
   * Custom mask pattern (used when mask is 'custom')
   *
   * Use # for digits, A for letters, ? for alphanumeric
   *
   * @example '##/##/####' for date
   */
  maskPattern?: string;

  /**
   * Placeholder character for mask
   *
   * @default '_'
   */
  maskPlaceholder?: string;

  /**
   * Enable input formatting
   */
  format?: FormatType;

  /**
   * Custom formatter function (used when format is 'custom')
   */
  formatter?: (value: string) => string;

  /**
   * Format on blur instead of on every change
   *
   * @default false
   */
  formatOnBlur?: boolean;

  /**
   * Enable debounced input
   *
   * @default false
   */
  debounced?: boolean;

  /**
   * Debounce delay in milliseconds
   *
   * @default 300
   */
  debounceDelay?: number;

  /**
   * Callback for debounced value changes
   */
  onDebouncedChange?: (value: string) => void;

  /**
   * Enable keyboard navigation
   *
   * @default false
   */
  keyboardNavigation?: boolean;

  /**
   * Callback when Tab key is pressed
   */
  onTabPress?: (e: React.KeyboardEvent<HTMLInputElement>) => void;

  /**
   * Callback when Enter key is pressed
   */
  onEnterPress?: (e: React.KeyboardEvent<HTMLInputElement>) => void;

  /**
   * Callback when Escape key is pressed
   */
  onEscapePress?: (e: React.KeyboardEvent<HTMLInputElement>) => void;

  /**
   * Callback when Arrow keys are pressed
   */
  onArrowPress?: (
    direction: ArrowDirection,
    e: React.KeyboardEvent<HTMLInputElement>
  ) => void;

  /**
   * Additional ARIA label for the input
   */
  ariaLabel?: string;

  /**
   * Additional ARIA description for the input
   */
  ariaDescription?: string;

  /**
   * Announce changes to screen readers
   *
   * @default false
   */
  announceChanges?: boolean;

  /**
   * Theme for the input
   *
   * @default 'light'
   */
  theme?: InputTheme;

  /**
   * Enable mobile optimization
   *
   * @default true
   */
  mobileOptimized?: boolean;
}
