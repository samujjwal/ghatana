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

type TextAreaSize = 'sm' | 'md' | 'lg';

const sizeMetrics: Record<TextAreaSize, { paddingBlock: string; paddingInline: string; fontSize: string; minHeight: number }> = {
  sm: {
    paddingBlock: '8px',
    paddingInline: '12px',
    fontSize: fontSize.sm,
    minHeight: 80,
  },
  md: {
    paddingBlock: '10px',
    paddingInline: '14px',
    fontSize: fontSize.base,
    minHeight: 120,
  },
  lg: {
    paddingBlock: '12px',
    paddingInline: '16px',
    fontSize: fontSize.lg,
    minHeight: 160,
  },
};

export interface TextAreaProps extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {
  label?: string;
  description?: string;
  errorMessage?: string;
  size?: TextAreaSize;
  resize?: 'none' | 'vertical' | 'horizontal' | 'both';
}

/**
 * Multiline text input with theme-aware styling.
 */
export const TextArea = React.forwardRef<HTMLTextAreaElement, TextAreaProps>((props, ref) => {
  const {
    id,
    label,
    description,
    errorMessage,
    size = 'md',
    resize = 'vertical',
    disabled,
    className,
    style,
    onFocus,
    onBlur,
    ...rest
  } = props;

  const generatedId = useId('gh-text-area');
  const textAreaId = id ?? generatedId;
  const descriptionId = description ? `${textAreaId}-description` : undefined;
  const errorId = errorMessage ? `${textAreaId}-error` : undefined;

  const { resolvedTheme } = useTheme();
  const { focusProps, isFocusVisible } = useFocusRing<HTMLTextAreaElement>({ onFocus, onBlur });

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

  const baseStyle: React.CSSProperties = {
    width: '100%',
    minHeight: `${metrics.minHeight}px`,
    paddingInline: metrics.paddingInline,
    paddingBlock: metrics.paddingBlock,
    fontSize: metrics.fontSize,
    fontWeight: fontWeight.regular,
    color: disabled ? colors.disabledText : colors.text,
    backgroundColor: disabled ? colors.disabledBackground : colors.background,
    borderRadius: `${componentRadius.input}px`,
    borderWidth: 1,
    borderStyle: 'solid',
    borderColor: isFocusVisible ? colors.focusRing : colors.border,
    boxShadow: isFocusVisible ? `0 0 0 3px ${colors.focusRing}33` : 'none',
    resize,
    transition: transitions.default,
    outline: 'none',
  };

  const mergedStyle = style ? { ...baseStyle, ...style } : baseStyle;

  return (
    <div className={cn('gh-text-area', className)}>
      {label ? (
        <label
          htmlFor={textAreaId}
          className="gh-text-area__label"
          style={{
            display: 'block',
            marginBottom: '4px',
            fontSize: fontSize.sm,
            fontWeight: fontWeight.medium,
            color: colors.text,
          }}
        >
          {label}
        </label>
      ) : null}

      <textarea
        {...rest}
        {...focusProps}
        ref={ref}
        id={textAreaId}
        disabled={disabled}
        aria-describedby={[descriptionId, errorId].filter(Boolean).join(' ') || undefined}
        aria-invalid={hasError || undefined}
        className="gh-text-area__input"
        style={mergedStyle}
      />

      {description ? (
        <p
          id={descriptionId}
          className="gh-text-area__description"
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
          className="gh-text-area__error"
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

TextArea.displayName = 'TextArea';
