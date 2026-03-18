import * as React from 'react';
import { cn } from '@ghatana/utils';
import {
  palette,
  componentRadius,
  fontSize as tokenFontSize,
  fontWeight,
  spacing,
  lightColors,
  darkColors,
} from '@ghatana/tokens';
import { useTheme } from '@ghatana/theme';

import { useControllableState } from '../hooks/useControllableState';
import { useId } from '../hooks/useId';

type CheckboxTone = 'primary' | 'secondary' | 'success' | 'warning' | 'danger' | 'neutral';
type CheckboxSize = 'sm' | 'md' | 'lg';

const toneMap: Record<CheckboxTone, string> = {
  primary: palette.primary[500],
  secondary: palette.secondary[500],
  success: palette.success.main ?? palette.success[500],
  warning: palette.warning.main ?? palette.warning[500],
  danger: palette.error.main ?? palette.error[500],
  neutral: palette.gray[500],
};

const sizeTokens: Record<CheckboxSize, { box: number; icon: number; fontSize: string }> = {
  sm: { box: 16, icon: 10, fontSize: tokenFontSize.sm },
  md: { box: 20, icon: 12, fontSize: tokenFontSize.base },
  lg: { box: 24, icon: 14, fontSize: tokenFontSize.lg },
};

export interface CheckboxProps
  extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'size' | 'color'> {
  label?: React.ReactNode;
  indeterminate?: boolean;
  tone?: CheckboxTone;
  size?: CheckboxSize | 'small' | 'medium' | 'large';
  /** MUI-compat: custom icons */
  icon?: React.ReactNode;
  checkedIcon?: React.ReactNode;
  indeterminateIcon?: React.ReactNode;
  helperText?: React.ReactNode;
  labelClassName?: string;
  containerClassName?: string;
  helperClassName?: string;
}

/**
 * Theme-aware checkbox with indeterminate support.
 */
export const Checkbox = React.forwardRef<HTMLInputElement, CheckboxProps>((props, ref) => {
  const {
    id,
    label,
    indeterminate = false,
    tone = 'primary',
    size = 'md',
    icon,
    checkedIcon,
    indeterminateIcon,
    helperText,
    className,
    labelClassName,
    containerClassName,
    helperClassName,
    disabled,
    checked: checkedProp,
    defaultChecked,
    onChange,
    ...rest
  } = props;

  const autoId = useId('gh-checkbox');
  const inputId = id ?? autoId;
  const helperId = helperText ? `${inputId}-helper` : undefined;

  const inputRef = React.useRef<HTMLInputElement>(null);
  React.useImperativeHandle(ref, () => inputRef.current as HTMLInputElement);

  const { resolvedTheme } = useTheme();
  const surface = resolvedTheme === 'dark' ? darkColors : lightColors;

  const [checked, setChecked] = useControllableState<boolean>({
    value: checkedProp,
    defaultValue: defaultChecked ?? false,
  });

  React.useEffect(() => {
    if (inputRef.current) {
      inputRef.current.indeterminate = indeterminate;
    }
  }, [indeterminate, checked]);

  const handleChange = React.useCallback(
    (event: React.ChangeEvent<HTMLInputElement>) => {
      setChecked(event.target.checked);
      onChange?.(event);
    },
    [setChecked, onChange]
  );

  const resolvedSize: CheckboxSize =
    size === 'small' ? 'sm' : size === 'medium' ? 'md' : size === 'large' ? 'lg' : size;

  const metrics = sizeTokens[resolvedSize];
  const color = toneMap[tone] ?? toneMap.primary;

  const spacingMap = spacing as Record<string, number>;
  const gap = spacingMap['2'] ?? 8;
  const helperSpacing = spacingMap['1'] ?? 4;

  return (
    <label
      style={{
        display: 'inline-flex',
        alignItems: 'flex-start',
        gap,
        cursor: disabled ? 'not-allowed' : 'pointer',
        userSelect: 'none',
        opacity: disabled ? 0.6 : 1,
      }}
      className={cn('gh-checkbox', containerClassName)}
    >
      <span
        style={{
          position: 'relative',
          display: 'inline-flex',
          width: metrics.box,
          height: metrics.box,
          borderRadius: componentRadius.checkbox,
          border: `1px solid ${checked || indeterminate ? color : surface.border}`,
          backgroundColor: checked || indeterminate ? color : surface.background.elevated,
          transition: 'all 120ms ease',
          alignItems: 'center',
          justifyContent: 'center',
          flexShrink: 0,
          boxShadow: '0 0 0 2px transparent',
        }}
      >
        <input
          ref={inputRef}
          id={inputId}
          type="checkbox"
          checked={checked}
          disabled={disabled}
          aria-describedby={helperId}
          onChange={handleChange}
          style={{
            position: 'absolute',
            inset: 0,
            width: '100%',
            height: '100%',
            margin: 0,
            opacity: 0,
            cursor: disabled ? 'not-allowed' : 'pointer',
          }}
          className={cn('gh-checkbox__input', className)}
          {...rest}
        />
        {(checked || indeterminate || icon) && (
          <span
            aria-hidden="true"
            style={{
              width: metrics.icon,
              height: metrics.icon,
              color: '#ffffff',
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              transition: 'transform 150ms ease',
              fontSize: metrics.icon - 2,
            }}
          >
            {indeterminate
              ? (indeterminateIcon ?? '−')
              : checked
                ? (checkedIcon ?? (
                  <svg
                    viewBox="0 0 16 16"
                    fill="none"
                    xmlns="http://www.w3.org/2000/svg"
                    width={metrics.icon}
                    height={metrics.icon}
                  >
                    <path
                      d="M13.333 4L6.666 10.667 3.333 7.333"
                      stroke="currentColor"
                      strokeWidth="2"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    />
                  </svg>
                ))
                : (icon ?? null)}
          </span>
        )}
      </span>

      {(label || helperText) && (
        <span style={{ lineHeight: 1.4 }}>
          {label && (
            <span
              style={{
                display: 'inline-block',
                fontSize: metrics.fontSize,
                fontWeight: fontWeight.medium,
                color: disabled ? surface.text.disabled : surface.text.primary,
              }}
              className={cn('gh-checkbox__label', labelClassName)}
            >
              {label}
            </span>
          )}
          {helperText ? (
            <span
              style={{
                display: 'block',
                marginTop: helperSpacing,
                fontSize: tokenFontSize.sm,
                color: disabled ? surface.text.disabled : surface.text.secondary,
              }}
              id={helperId}
              className={cn('gh-checkbox__helper', helperClassName)}
            >
              {helperText}
            </span>
          ) : null}
        </span>
      )}
    </label>
  );
});

Checkbox.displayName = 'Checkbox';
