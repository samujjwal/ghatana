/**
 * Loading Spinner Component
 * 
 * Production-grade loading spinner with multiple sizes and variants
 * 
 * @module ui/components/Loading/Spinner
 * @doc.type component
 * @doc.purpose Loading state indicator
 * @doc.layer ui
 */

import React from 'react';
import './Spinner.css';

export type SpinnerSize = 'xs' | 'sm' | 'md' | 'lg' | 'xl';
export type SpinnerVariant = 'primary' | 'secondary' | 'white' | 'black';

export interface SpinnerProps {
  /** Size of the spinner */
  size?: SpinnerSize;
  /** Color variant of the spinner */
  variant?: SpinnerVariant;
  /** Additional CSS class names */
  className?: string;
  /** Label for screen readers */
  label?: string;
  /** Whether to center the spinner in its container */
  centered?: boolean;
  /** Whether to show fullscreen overlay */
  fullscreen?: boolean;
}

/**
 * Loading spinner component
 * 
 * @example Basic usage
 * ```tsx
 * <Spinner />
 * ```
 * 
 * @example Large centered spinner
 * ```tsx
 * <Spinner size="lg" centered />
 * ```
 * 
 * @example Fullscreen loading overlay
 * ```tsx
 * <Spinner fullscreen label="Loading data..." />
 * ```
 */
export function Spinner({
  size = 'md',
  variant = 'primary',
  className = '',
  label = 'Loading',
  centered = false,
  fullscreen = false,
}: SpinnerProps): React.JSX.Element {
  const spinnerElement = (
    <div
      className={`spinner spinner--${size} spinner--${variant} ${className}`}
      role="status"
      aria-label={label}
    >
      <svg
        className="spinner__svg"
        viewBox="0 0 50 50"
        xmlns="http://www.w3.org/2000/svg"
      >
        <circle
          className="spinner__circle"
          cx="25"
          cy="25"
          r="20"
          fill="none"
          strokeWidth="4"
        />
      </svg>
      <span className="spinner__label">{label}</span>
    </div>
  );

  if (fullscreen) {
    return (
      <div className="spinner__overlay" aria-live="polite">
        {spinnerElement}
      </div>
    );
  }

  if (centered) {
    return (
      <div className="spinner__container" aria-live="polite">
        {spinnerElement}
      </div>
    );
  }

  return spinnerElement;
}

/**
 * Inline spinner for use within text or buttons
 */
export interface InlineSpinnerProps {
  /** Size of the spinner */
  size?: 'xs' | 'sm';
  /** Color variant */
  variant?: SpinnerVariant;
  /** Additional CSS class names */
  className?: string;
}

export function InlineSpinner({
  size = 'sm',
  variant = 'primary',
  className = '',
}: InlineSpinnerProps): React.JSX.Element {
  return (
    <span
      className={`spinner-inline spinner-inline--${size} spinner-inline--${variant} ${className}`}
      role="status"
      aria-label="Loading"
    >
      <svg
        className="spinner-inline__svg"
        viewBox="0 0 50 50"
        xmlns="http://www.w3.org/2000/svg"
      >
        <circle
          className="spinner-inline__circle"
          cx="25"
          cy="25"
          r="20"
          fill="none"
          strokeWidth="5"
        />
      </svg>
    </span>
  );
}

/**
 * Loading button component - button with integrated spinner
 */
export interface LoadingButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  /** Whether the button is in loading state */
  loading?: boolean;
  /** Size of the spinner */
  spinnerSize?: 'xs' | 'sm';
  /** Text to show when loading */
  loadingText?: string;
  /** Children (button content) */
  children: React.ReactNode;
}

export function LoadingButton({
  loading = false,
  spinnerSize = 'sm',
  loadingText,
  children,
  disabled,
  className = '',
  ...props
}: LoadingButtonProps): React.JSX.Element {
  return (
    <button
      {...props}
      disabled={disabled || loading}
      className={`loading-button ${loading ? 'loading-button--loading' : ''} ${className}`}
    >
      {loading && (
        <InlineSpinner
          size={spinnerSize}
          variant="white"
          className="loading-button__spinner"
        />
      )}
      <span className={loading ? 'loading-button__text--loading' : ''}>
        {loading && loadingText ? loadingText : children}
      </span>
    </button>
  );
}
