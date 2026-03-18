import * as React from 'react';
import { cn } from '@ghatana/utils';
import {
  palette,
  lightColors,
  darkColors,
  transitions,
  touchTargets,
  fontWeight,
  fontSize,
} from '@ghatana/tokens';
import { useTheme } from '@ghatana/theme';

import { useFocusRing } from '../hooks/useFocusRing';
import { sxToStyle, type SxProps } from '../utils/sx';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

type FabSize = 'sm' | 'md' | 'lg';
type FabSizeAlias = FabSize | 'small' | 'medium' | 'large';
type FabTone = 'primary' | 'secondary' | 'success' | 'warning' | 'danger' | 'neutral';
type FabVariant = 'circular' | 'extended';

const normSize = (s: FabSizeAlias): FabSize => {
  if (s === 'small') return 'sm';
  if (s === 'medium') return 'md';
  if (s === 'large') return 'lg';
  return s;
};

const sizeDimensions: Record<FabSize, number> = {
  sm: 40,
  md: 56,
  lg: 72,
};

const sizeIconScale: Record<FabSize, string> = {
  sm: '18px',
  md: '24px',
  lg: '28px',
};

// ---------------------------------------------------------------------------
// Fab
// ---------------------------------------------------------------------------

export interface FabProps
  extends Omit<React.ButtonHTMLAttributes<HTMLButtonElement>, 'color'> {
  /** Icon element */
  icon: React.ReactNode;

  /** Accent tone */
  tone?: FabTone;

  /** Size */
  size?: FabSizeAlias;

  /** Variant — `circular` (icon only) or `extended` (icon + label) */
  variant?: FabVariant;

  /** Extended label text */
  label?: string;

  /** Accessible label for screen readers */
  'aria-label': string;

  /** MUI-compatible color prop (maps to tone) */
  color?: FabTone | 'default' | 'inherit';

  /** MUI-compatible sx prop */
  sx?: SxProps;
}

/**
 * Floating Action Button — a prominent circular button for the primary action on screen.
 * WCAG 2.1 AA: focus ring, aria-label required, 40-72 px touch target.
 */
export const Fab = React.forwardRef<HTMLButtonElement, FabProps>((props, ref) => {
  const {
    icon,
    tone: toneProp,
    size: rawSize = 'md',
    variant = 'circular',
    label,
    color,
    className,
    style,
    disabled = false,
    sx,
    ...rest
  } = props;

  const effectiveTone: FabTone = toneProp ??
    (color === 'default' || color === 'inherit' ? 'neutral' : color ?? 'primary');
  const size = normSize(rawSize);
  const dim = sizeDimensions[size];
  const isExtended = variant === 'extended' || !!label;

  const { resolvedTheme } = useTheme();
  const isDark = resolvedTheme === 'dark';
  const surface = isDark ? darkColors : lightColors;

  const [hovered, setHovered] = React.useState(false);
  const { focusProps, isFocusVisible } = useFocusRing();

  const toneEntry = (palette as Record<string, Record<string, string>>)[
    effectiveTone === 'danger' ? 'error' : effectiveTone
  ] ?? palette.primary;
  const main = toneEntry[500] ?? palette.primary[500];
  const mainHover = toneEntry[600] ?? main;
  const mainActive = toneEntry[700] ?? mainHover;
  const fg =
    toneEntry.contrastText ?? '#ffffff';

  const bg = disabled
    ? surface.action.disabledBackground
    : hovered
      ? mainHover
      : main;
  const textColor = disabled ? surface.text.disabled : fg;

  const btnStyle: React.CSSProperties = {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    gap: isExtended ? '8px' : '0',
    width: isExtended ? 'auto' : `${dim}px`,
    height: `${dim}px`,
    minWidth: `${dim}px`,
    paddingInline: isExtended ? '20px' : '0',
    border: 'none',
    borderRadius: isExtended ? `${dim / 2}px` : '50%',
    background: bg,
    color: textColor,
    cursor: disabled ? 'not-allowed' : 'pointer',
    opacity: disabled ? 0.6 : 1,
    fontFamily: 'inherit',
    fontSize: fontSize.base,
    fontWeight: fontWeight.medium,
    boxShadow: disabled
      ? 'none'
      : `0 3px 5px -1px rgba(0,0,0,0.2), 0 6px 10px rgba(0,0,0,0.14), 0 1px 18px rgba(0,0,0,0.12)`,
    transition: `background ${transitions.duration.fast} ${transitions.easing.easeInOut}, box-shadow ${transitions.duration.fast} ${transitions.easing.easeInOut}, transform ${transitions.duration.fast} ${transitions.easing.easeInOut}`,
    outline: 'none',
    WebkitTapHighlightColor: 'transparent',
    transform: hovered && !disabled ? 'scale(1.05)' : 'scale(1)',
    ...(isFocusVisible
      ? {
          boxShadow: `0 0 0 3px rgba(${parseInt(main.slice(1, 3), 16)},${parseInt(main.slice(3, 5), 16)},${parseInt(main.slice(5, 7), 16)},0.4), 0 3px 5px -1px rgba(0,0,0,0.2), 0 6px 10px rgba(0,0,0,0.14)`,
        }
      : {}),
    ...(sxToStyle(sx) ?? {}),
    ...(style ?? {}),
  };

  return (
    <button
      ref={ref}
      type="button"
      disabled={disabled}
      className={cn(
        'gh-fab',
        isExtended && 'gh-fab--extended',
        disabled && 'gh-fab--disabled',
        className
      )}
      style={btnStyle}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      data-size={size}
      data-tone={effectiveTone}
      data-variant={isExtended ? 'extended' : 'circular'}
      {...focusProps}
      {...rest}
    >
      <span
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          width: sizeIconScale[size],
          height: sizeIconScale[size],
          fontSize: sizeIconScale[size],
        }}
      >
        {icon}
      </span>
      {isExtended && label && (
        <span>{label}</span>
      )}
    </button>
  );
});

Fab.displayName = 'Fab';
