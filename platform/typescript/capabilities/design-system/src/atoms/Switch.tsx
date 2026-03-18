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

type SwitchTone = 'primary' | 'secondary' | 'success' | 'warning' | 'danger' | 'neutral';
type SwitchSize = 'sm' | 'md' | 'lg';

const toneMap: Record<SwitchTone, string> = {
  primary: palette.primary[500],
  secondary: palette.secondary[500],
  success: palette.success.main ?? palette.success[500],
  warning: palette.warning.main ?? palette.warning[500],
  danger: palette.error.main ?? palette.error[500],
  neutral: palette.gray[500],
};

const sizeMap: Record<
  SwitchSize,
  { trackWidth: number; trackHeight: number; thumbSize: number; translateX: number; fontSize: string }
> = {
  sm: { trackWidth: 32, trackHeight: 20, thumbSize: 14, translateX: 12, fontSize: tokenFontSize.sm },
  md: { trackWidth: 44, trackHeight: 24, thumbSize: 18, translateX: 18, fontSize: tokenFontSize.base },
  lg: { trackWidth: 56, trackHeight: 28, thumbSize: 22, translateX: 24, fontSize: tokenFontSize.lg },
};

export interface SwitchProps extends Omit<React.ButtonHTMLAttributes<HTMLButtonElement>, 'type' | 'onChange' | 'onToggle'> {
  label?: React.ReactNode;
  tone?: SwitchTone;
  size?: SwitchSize | 'small' | 'medium' | 'large';
  containerClassName?: string;
  labelClassName?: string;
  // Form-control style props
  checked?: boolean;
  defaultChecked?: boolean;
  /**
   * Preferred callback: receives new checked state and original click event
   */
  onToggle?: (checked: boolean, event?: React.MouseEvent<HTMLButtonElement>) => void;
  /** MUI-compat signature */
  onChange?: (event: React.ChangeEvent<HTMLInputElement>, checked: boolean) => void;
  /**
   * Legacy input-style change event (optional)
   */
  onChangeInput?: (event: React.ChangeEvent<HTMLInputElement>) => void;
}

/**
 * Theme-aware toggle switch.
 */
export const Switch = React.forwardRef<HTMLButtonElement, SwitchProps>((props, ref) => {
  const {
    id,
    label,
    tone = 'primary',
    size = 'md',
    containerClassName,
    labelClassName,
    disabled,
    className,
    checked: checkedProp,
    defaultChecked,
    onToggle,
    onChange,
    onChangeInput,
    onClick,
    ...rest
  } = props;

  const autoId = useId('gh-switch');
  const switchId = id ?? autoId;

  const { resolvedTheme } = useTheme();
  const surface = resolvedTheme === 'dark' ? darkColors : lightColors;

  const [checked, setChecked] = useControllableState<boolean>({
    value: typeof checkedProp === 'boolean' ? checkedProp : undefined,
    defaultValue: defaultChecked ?? false,
  });

  const resolvedSize: SwitchSize =
    size === 'small' ? 'sm' : size === 'medium' ? 'md' : size === 'large' ? 'lg' : size;

  const metrics = sizeMap[resolvedSize];
  const activeColor = toneMap[tone] ?? toneMap.primary;

  const spacingMap = spacing as Record<string, number>;
  const gap = spacingMap['2'] ?? 8;

  const handleToggle = (event: React.MouseEvent<HTMLButtonElement>) => {
    if (disabled) return;
    const next = !checked;
    setChecked(next);
    onToggle?.(next, event);

    const syntheticChangeEvent = {
      target: { checked: next },
      currentTarget: { checked: next },
      preventDefault: event.preventDefault.bind(event),
      stopPropagation: event.stopPropagation.bind(event),
    } as unknown as React.ChangeEvent<HTMLInputElement>;

    onChange?.(syntheticChangeEvent, next);
    onChangeInput?.(syntheticChangeEvent);
    onClick?.(event);
  };

  return (
    <label
      className={cn('gh-switch', containerClassName)}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap,
        cursor: disabled ? 'not-allowed' : 'pointer',
        userSelect: 'none',
        opacity: disabled ? 0.6 : 1,
      }}
      htmlFor={switchId}
    >
      <button
        {...rest}
        ref={ref}
        id={switchId}
        role="switch"
        aria-checked={checked}
        disabled={disabled}
        onClick={handleToggle}
        className={cn('gh-switch__button', className)}
        style={{
          position: 'relative',
          display: 'inline-flex',
          alignItems: 'center',
          width: metrics.trackWidth,
          height: metrics.trackHeight,
          borderRadius: metrics.trackHeight / 2,
          backgroundColor: checked ? activeColor : surface.border,
          transition: 'background-color 160ms ease',
          padding: 2,
          border: 'none',
          cursor: disabled ? 'not-allowed' : 'pointer',
        }}
      >
        <span
          aria-hidden="true"
          style={{
            position: 'absolute',
            top: 2,
            left: 2,
            width: metrics.thumbSize,
            height: metrics.thumbSize,
            borderRadius: componentRadius.switch,
            backgroundColor: '#ffffff',
            transform: checked ? `translateX(${metrics.translateX}px)` : 'translateX(0)',
            transition: 'transform 160ms ease',
            boxShadow: '0 2px 6px rgba(15, 23, 42, 0.18)',
          }}
        />
        <span
          aria-hidden="true"
          style={{
            opacity: 0,
            width: 0,
            height: 0,
            overflow: 'hidden',
          }}
        >
          {/* ensure button has accessible label via label prop */}
        </span>
      </button>
      {label ? (
        <span
          className={cn('gh-switch__label', labelClassName)}
          style={{
            fontSize: metrics.fontSize,
            fontWeight: fontWeight.medium,
            color: disabled ? surface.text.disabled : surface.text.primary,
          }}
        >
          {label}
        </span>
      ) : null}
    </label>
  );
});

Switch.displayName = 'Switch';
