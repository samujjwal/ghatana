import * as React from 'react';
import { cn } from '@ghatana/utils';
import {
  palette,
  lightColors,
  darkColors,
  transitions,
  fontSize,
  fontWeight,
  componentRadius,
  touchTargets,
} from '@ghatana/tokens';
import { useTheme } from '@ghatana/theme';

import { useFocusRing } from '../hooks/useFocusRing';
import { sxToStyle, type SxProps } from '../utils/sx';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

type ToggleButtonSize = 'sm' | 'md' | 'lg';
type ToggleButtonSizeAlias = ToggleButtonSize | 'small' | 'medium' | 'large';
type ToggleButtonTone = 'primary' | 'secondary' | 'success' | 'warning' | 'danger' | 'neutral';

const normSize = (s: ToggleButtonSizeAlias): ToggleButtonSize => {
  if (s === 'small') return 'sm';
  if (s === 'medium') return 'md';
  if (s === 'large') return 'lg';
  return s;
};

const sizeMetrics: Record<ToggleButtonSize, {
  paddingInline: string;
  paddingBlock: string;
  fontSize: string;
  minHeight: number;
  gap: string;
}> = {
  sm: { paddingInline: '10px', paddingBlock: '6px', fontSize: fontSize.sm, minHeight: touchTargets.small, gap: '6px' },
  md: { paddingInline: '14px', paddingBlock: '8px', fontSize: fontSize.base, minHeight: touchTargets.minimum, gap: '8px' },
  lg: { paddingInline: '18px', paddingBlock: '10px', fontSize: fontSize.lg, minHeight: touchTargets.recommended, gap: '10px' },
};

// ---------------------------------------------------------------------------
// ToggleButton
// ---------------------------------------------------------------------------

export interface ToggleButtonProps
  extends Omit<React.ButtonHTMLAttributes<HTMLButtonElement>, 'value'> {
  /** Unique value in a group */
  value: string;

  /** Whether button is currently selected */
  selected?: boolean;

  /** Callback when toggled — handled internally when used inside ToggleButtonGroup */
  onChange?: (value: string) => void;

  /** Visual size */
  size?: ToggleButtonSizeAlias;

  /** Accent tone when selected */
  tone?: ToggleButtonTone;

  /** If true, only icon is rendered (decreases inline padding) */
  iconOnly?: boolean;

  /** MUI-compatible sx prop */
  sx?: SxProps;
}

export const ToggleButton = React.forwardRef<HTMLButtonElement, ToggleButtonProps>(
  (props, ref) => {
    const {
      value,
      selected = false,
      onChange,
      size: rawSize = 'md',
      tone = 'primary',
      iconOnly = false,
      disabled = false,
      className,
      style,
      children,
      sx,
      onClick,
      ...rest
    } = props;

    const size = normSize(rawSize);
    const metrics = sizeMetrics[size];
    const { resolvedTheme } = useTheme();
    const isDark = resolvedTheme === 'dark';
    const surface = isDark ? darkColors : lightColors;

    const [hovered, setHovered] = React.useState(false);
    const { focusProps, isFocusVisible } = useFocusRing();

    const toneEntry = (palette as Record<string, Record<string, string>>)[tone === 'danger' ? 'error' : tone] ?? palette.primary;
    const main = toneEntry[500] ?? palette.primary[500];

    // Selected / unselected palette
    const bg = selected
      ? (isDark ? (toneEntry[900] ?? toneEntry[800] ?? main) : (toneEntry[50] ?? toneEntry[100] ?? main))
      : 'transparent';
    const bgHover = selected
      ? (isDark ? (toneEntry[800] ?? main) : (toneEntry[100] ?? main))
      : surface.action.hover;
    const fg = selected ? main : surface.text.secondary;
    const border = selected ? main : (isDark ? 'rgba(255,255,255,0.12)' : 'rgba(0,0,0,0.12)');
    const borderHover = selected ? main : (isDark ? 'rgba(255,255,255,0.2)' : 'rgba(0,0,0,0.2)');

    const baseStyle: React.CSSProperties = {
      display: 'inline-flex',
      alignItems: 'center',
      justifyContent: 'center',
      gap: metrics.gap,
      paddingInline: iconOnly ? `${metrics.paddingBlock}` : metrics.paddingInline,
      paddingBlock: metrics.paddingBlock,
      fontSize: metrics.fontSize,
      fontWeight: fontWeight.medium,
      fontFamily: 'inherit',
      lineHeight: 1.5,
      minHeight: `${metrics.minHeight}px`,
      minWidth: iconOnly ? `${metrics.minHeight}px` : undefined,
      border: `1px solid ${hovered && !disabled ? borderHover : border}`,
      borderRadius: componentRadius.md,
      background: hovered && !disabled ? bgHover : bg,
      color: disabled ? surface.text.disabled : fg,
      cursor: disabled ? 'not-allowed' : 'pointer',
      opacity: disabled ? 0.5 : 1,
      transition: `background ${transitions.duration.fast} ${transitions.easing.easeInOut}, border-color ${transitions.duration.fast} ${transitions.easing.easeInOut}, color ${transitions.duration.fast} ${transitions.easing.easeInOut}`,
      outline: 'none',
      boxShadow: isFocusVisible ? `0 0 0 3px rgba(${parseInt(main.slice(1, 3), 16)},${parseInt(main.slice(3, 5), 16)},${parseInt(main.slice(5, 7), 16)},0.4)` : undefined,
      WebkitTapHighlightColor: 'transparent',
    };

    const merged = { ...baseStyle, ...sxToStyle(sx), ...style };

    const handleClick = (e: React.MouseEvent<HTMLButtonElement>) => {
      if (!disabled) {
        onChange?.(value);
        onClick?.(e);
      }
    };

    return (
      <button
        ref={ref}
        type="button"
        role="option"
        aria-pressed={selected}
        aria-selected={selected}
        disabled={disabled}
        className={cn('gh-toggle-button', selected && 'gh-toggle-button--selected', className)}
        style={merged}
        onClick={handleClick}
        onMouseEnter={() => setHovered(true)}
        onMouseLeave={() => setHovered(false)}
        data-value={value}
        data-selected={selected || undefined}
        data-size={size}
        data-tone={tone}
        {...focusProps}
        {...rest}
      >
        {children}
      </button>
    );
  }
);

ToggleButton.displayName = 'ToggleButton';

// ---------------------------------------------------------------------------
// ToggleButtonGroup
// ---------------------------------------------------------------------------

export interface ToggleButtonGroupProps
  extends Omit<React.HTMLAttributes<HTMLDivElement>, 'onChange'> {
  /** Currently selected value(s). String for exclusive, string[] for multi-select. */
  value?: string | string[];

  /** Change handler */
  onChange?: (value: string | string[]) => void;

  /** Whether multiple values can be selected */
  exclusive?: boolean;

  /** Visual size applied to children */
  size?: ToggleButtonSizeAlias;

  /** Accent tone applied to children */
  tone?: ToggleButtonTone;

  /** Layout orientation */
  orientation?: 'horizontal' | 'vertical';

  /** Whether the group spans full width */
  fullWidth?: boolean;

  /** MUI-compatible sx prop */
  sx?: SxProps;

  children: React.ReactNode;
}

export const ToggleButtonGroup = React.forwardRef<HTMLDivElement, ToggleButtonGroupProps>(
  (props, ref) => {
    const {
      value,
      onChange,
      exclusive = true,
      size = 'md',
      tone = 'primary',
      orientation = 'horizontal',
      fullWidth = false,
      className,
      style,
      children,
      sx,
      ...rest
    } = props;

    const isVertical = orientation === 'vertical';

    const handleChildChange = React.useCallback(
      (childValue: string) => {
        if (!onChange) return;

        if (exclusive) {
          // Exclusive mode: toggle off if clicking the already-selected value
          const next = value === childValue ? '' : childValue;
          onChange(next);
        } else {
          // Multi-select: toggle value in/out of array
          const arr = Array.isArray(value) ? value : value ? [value] : [];
          const next = arr.includes(childValue)
            ? arr.filter((v) => v !== childValue)
            : [...arr, childValue];
          onChange(next);
        }
      },
      [value, onChange, exclusive]
    );

    const isSelected = React.useCallback(
      (childValue: string) => {
        if (Array.isArray(value)) return value.includes(childValue);
        return value === childValue;
      },
      [value]
    );

    // Clone children to inject group-level props
    const enhanced = React.Children.map(children, (child) => {
      if (!React.isValidElement(child)) return child;

      const childProps = child.props as ToggleButtonProps;
      const childValue = childProps.value;
      if (childValue === undefined) return child;

      return React.cloneElement(child as React.ReactElement<ToggleButtonProps>, {
        selected: isSelected(childValue),
        onChange: handleChildChange,
        size: childProps.size ?? size,
        tone: childProps.tone ?? tone,
        style: {
          ...(childProps.style ?? {}),
          // Remove inner border-radius when grouped for connected appearance
          borderRadius: 0,
        },
      });
    });

    const groupStyle: React.CSSProperties = {
      display: fullWidth ? 'flex' : 'inline-flex',
      flexDirection: isVertical ? 'column' : 'row',
      borderRadius: componentRadius.md,
      overflow: 'hidden',
      ...(sxToStyle(sx) ?? {}),
      ...(style ?? {}),
    };

    return (
      <div
        ref={ref}
        role="group"
        aria-orientation={orientation}
        className={cn(
          'gh-toggle-button-group',
          isVertical && 'gh-toggle-button-group--vertical',
          fullWidth && 'gh-toggle-button-group--full-width',
          className
        )}
        style={groupStyle}
        data-orientation={orientation}
        {...rest}
      >
        {enhanced}
      </div>
    );
  }
);

ToggleButtonGroup.displayName = 'ToggleButtonGroup';
