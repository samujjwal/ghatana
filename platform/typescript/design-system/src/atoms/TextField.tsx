import * as React from 'react';
import { cn } from '@ghatana/utils';
import {
  palette,
  lightColors,
  darkColors,
  fontSize,
  fontWeight,
  componentRadius,
  transitions,
} from '@ghatana/tokens';
import { useTheme } from '@ghatana/theme';

import { useFocusRing } from '../hooks/useFocusRing';
import { useId } from '../hooks/useId';
import { useMergeRefs } from '../hooks/useMergeRefs';

type TextFieldSize = 'sm' | 'md' | 'lg';

const sizeMetrics: Record<TextFieldSize, { paddingBlock: string; paddingInline: string; fontSize: string; labelFontSize: string }> = {
  sm: {
    paddingBlock: '6px',
    paddingInline: '10px',
    fontSize: fontSize.sm,
    labelFontSize: fontSize.sm,
  },
  md: {
    paddingBlock: '8px',
    paddingInline: '12px',
    fontSize: fontSize.base,
    labelFontSize: fontSize.sm,
  },
  lg: {
    paddingBlock: '10px',
    paddingInline: '14px',
    fontSize: fontSize.lg,
    labelFontSize: fontSize.base,
  },
};

export interface TextFieldProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'size' | 'prefix' | 'suffix'> {
  label?: string;
  description?: string;
  errorMessage?: string;
  prefix?: React.ReactNode;
  suffix?: React.ReactNode;
  size?: TextFieldSize;
  /** Stretch the field container to full width. */
  fullWidth?: boolean;

  /** MUI-compatible visual variant (ignored; provided for compatibility). */
  variant?: 'standard' | 'filled' | 'outlined';
  /** MUI-compatible alias for the input element ref. */
  inputRef?: React.Ref<HTMLInputElement>;

  /** MUI-like select mode. When true, renders a native <select>. */
  select?: boolean;
  /** Additional props forwarded to the <select> element when `select` is true. */
  SelectProps?: React.SelectHTMLAttributes<HTMLSelectElement>;
  /** Options / children for select mode. */
  children?: React.ReactNode;
}

/**
 * Text input field with theme-aware styling.
 */
export const TextField = React.forwardRef<HTMLInputElement, TextFieldProps>((props, ref) => {
  const {
    id,
    label,
    description,
    errorMessage,
    prefix,
    suffix,
    size = 'md',
    fullWidth = false,
    select = false,
    SelectProps,
    disabled,
    className,
    onFocus,
    onBlur,
    autoComplete = 'off',
    children,
    variant: _variant,
    inputRef,
    ...rest
  } = props;

  const generatedId = useId('gh-text-field');
  const inputId = id ?? generatedId;
  const descriptionId = description ? `${inputId}-description` : undefined;
  const errorId = errorMessage ? `${inputId}-error` : undefined;

  const { resolvedTheme } = useTheme();
  const { focusProps, isFocusVisible } = useFocusRing<HTMLInputElement>({ onFocus, onBlur });

  const mergedInputRef = useMergeRefs(ref, inputRef);

  const isDark = resolvedTheme === 'dark';
  const surface = isDark ? darkColors : lightColors;

  const hasError = Boolean(errorMessage ?? rest['aria-invalid']);

  const colors = React.useMemo(() => {
    const baseBorder = hasError ? palette.error[400] : surface.border;
    const focusRing = hasError ? palette.error[400] : palette.primary[400];

    return {
      background: surface.background.elevated,
      text: surface.text.primary,
      placeholder: surface.text.secondary,
      border: baseBorder,
      focusRing,
      helper: surface.text.secondary,
      error: palette.error[500],
      disabledBackground: surface.action.disabledBackground,
      disabledText: surface.text.disabled,
    };
  }, [hasError, surface]);

  const metrics = sizeMetrics[size];

  return (
    <div className={cn('gh-text-field', className)} style={{ width: fullWidth ? '100%' : undefined }}>
      {label ? (
        <label
          htmlFor={inputId}
          className="gh-text-field__label"
          style={{
            display: 'block',
            marginBottom: '4px',
            fontSize: metrics.labelFontSize,
            fontWeight: fontWeight.medium,
            color: colors.text,
          }}
        >
          {label}
        </label>
      ) : null}

      <div
        className="gh-text-field__control"
        style={{
          position: 'relative',
          display: 'flex',
          alignItems: 'center',
          borderRadius: `${componentRadius.input}px`,
          borderWidth: 1,
          borderStyle: 'solid',
          borderColor: isFocusVisible ? colors.focusRing : colors.border,
          boxShadow: isFocusVisible ? `0 0 0 3px ${colors.focusRing}33` : 'none',
          backgroundColor: disabled ? colors.disabledBackground : colors.background,
          transition: transitions.default,
          paddingInline: prefix || suffix ? '0px' : metrics.paddingInline,
        }}
        data-disabled={disabled ? 'true' : undefined}
        data-focused={isFocusVisible ? 'true' : undefined}
        data-invalid={hasError ? 'true' : undefined}
      >
        {prefix ? (
          <span
            className="gh-text-field__prefix"
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              paddingInline: metrics.paddingInline,
              color: colors.helper,
              fontSize: metrics.fontSize,
            }}
          >
            {prefix}
          </span>
        ) : null}

        {select ? (
          <select
            {...(rest as unknown as React.SelectHTMLAttributes<HTMLSelectElement>)}
            {...SelectProps}
            {...(focusProps as unknown as React.SelectHTMLAttributes<HTMLSelectElement>)}
            id={inputId}
            autoComplete={autoComplete}
            disabled={disabled}
            aria-describedby={[descriptionId, errorId].filter(Boolean).join(' ') || undefined}
            aria-invalid={hasError || undefined}
            className="gh-text-field__input"
            style={{
              flex: '1 1 auto',
              border: 'none',
              outline: 'none',
              background: 'transparent',
              paddingInline: prefix ? '0px' : metrics.paddingInline,
              paddingBlock: metrics.paddingBlock,
              fontSize: metrics.fontSize,
              fontWeight: fontWeight.regular,
              color: disabled ? colors.disabledText : colors.text,
              appearance: 'none',
            }}
          >
            {children}
          </select>
        ) : (
          <input
            {...rest}
            {...focusProps}
            ref={mergedInputRef}
            id={inputId}
            autoComplete={autoComplete}
            disabled={disabled}
            aria-describedby={[descriptionId, errorId].filter(Boolean).join(' ') || undefined}
            aria-invalid={hasError || undefined}
            className="gh-text-field__input"
            style={{
              flex: '1 1 auto',
              border: 'none',
              outline: 'none',
              background: 'transparent',
              paddingInline: prefix ? '0px' : metrics.paddingInline,
              paddingBlock: metrics.paddingBlock,
              fontSize: metrics.fontSize,
              fontWeight: fontWeight.regular,
              color: disabled ? colors.disabledText : colors.text,
            }}
          />
        )}

        {suffix ? (
          <span
            className="gh-text-field__suffix"
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              paddingInline: metrics.paddingInline,
              color: colors.helper,
              fontSize: metrics.fontSize,
            }}
          >
            {suffix}
          </span>
        ) : null}
      </div>

      {description ? (
        <p
          id={descriptionId}
          className="gh-text-field__description"
          style={{
            marginTop: '6px',
            fontSize: fontSize.sm,
            color: colors.helper,
          }}
        >
          {description}
        </p>
      ) : null}

      {errorMessage ? (
        <p
          id={errorId}
          className="gh-text-field__error"
          style={{
            marginTop: '6px',
            fontSize: fontSize.sm,
            color: colors.error,
          }}
        >
          {errorMessage}
        </p>
      ) : null}
    </div>
  );
});

TextField.displayName = 'TextField';
