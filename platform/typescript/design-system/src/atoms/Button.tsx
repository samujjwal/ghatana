import * as React from 'react';
import { cn } from '@ghatana/utils';
import {
  palette,
  lightColors,
  darkColors,
  transitions,
  fontSize,
  fontWeight,
  lineHeight,
  componentRadius,
  touchTargets,
} from '@ghatana/tokens';
import { useTheme } from '@ghatana/theme';

import { useFocusRing } from '../hooks/useFocusRing';
import { Spinner } from './Spinner';
import { sxToStyle, type SxProps } from '../utils/sx';

type ButtonVariant = 'solid' | 'outline' | 'soft' | 'ghost' | 'link';
type ButtonTone = 'primary' | 'secondary' | 'success' | 'warning' | 'danger' | 'info' | 'neutral';
type ButtonSize = 'sm' | 'md' | 'lg';
type ButtonSizeAlias = ButtonSize | 'small' | 'medium' | 'large';

interface ButtonColorState {
  background: string;
  border: string;
  foreground: string;
  focusRing: string;
  textDecoration?: string;
}

interface ButtonColorScheme {
  base: ButtonColorState;
  hover: ButtonColorState;
  active: ButtonColorState;
  disabled: ButtonColorState;
}

const tonePalette: Record<ButtonTone, Record<string, string | undefined>> = {
  primary: palette.primary,
  secondary: palette.secondary,
  success: palette.success,
  warning: palette.warning,
  danger: palette.error,
  info: palette.info,
  neutral: palette.neutral,
};

const focusRingAlpha = 0.4;

const sizeMetrics: Record<ButtonSize, { paddingInline: string; paddingBlock: string; gap: string; fontSize: string; lineHeight: number; minHeight: number }> = {
  sm: {
    paddingInline: '12px',
    paddingBlock: '8px',
    gap: '8px',
    fontSize: fontSize.sm,
    lineHeight: lineHeight.normal,
    minHeight: touchTargets.small,
  },
  md: {
    paddingInline: '16px',
    paddingBlock: '10px',
    gap: '10px',
    fontSize: fontSize.base,
    lineHeight: lineHeight.normal,
    minHeight: touchTargets.minimum,
  },
  lg: {
    paddingInline: '20px',
    paddingBlock: '12px',
    gap: '12px',
    fontSize: fontSize.lg,
    lineHeight: lineHeight.relaxed,
    minHeight: touchTargets.recommended,
  },
};

function hexToRgba(hex: string, alpha: number): string {
  const sanitized = hex.replace('#', '');
  const bigint = parseInt(sanitized, 16);
  const r = (bigint >> 16) & 255;
  const g = (bigint >> 8) & 255;
  const b = bigint & 255;
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

function getButtonScheme(
  variant: ButtonVariant,
  tone: ButtonTone,
  mode: 'light' | 'dark'
): ButtonColorScheme {
  const paletteEntry = tonePalette[tone] ?? palette.primary;
  const isDark = mode === 'dark';
  const surface = isDark ? darkColors : lightColors;

  const main = paletteEntry[500] ?? palette.primary[500];
  const hover = paletteEntry[600] ?? main;
  const active = paletteEntry[700] ?? hover;

  const soft = paletteEntry[100] ?? (isDark ? hexToRgba('#ffffff', 0.08) : hexToRgba('#000000', 0.04));
  const softHover =
    paletteEntry[200] ?? (isDark ? hexToRgba('#ffffff', 0.12) : hexToRgba('#000000', 0.08));

  const foreground =
    paletteEntry.contrastText ??
    (tone === 'neutral'
      ? surface.text.primary
      : isDark
        ? surface.text.primary
        : '#ffffff');

  const focusRing = hexToRgba(main, focusRingAlpha);

  const disabledBackground = surface.action.disabledBackground;
  const disabledColor = surface.text.disabled;
  const disabledBorder =
    variant === 'solid'
      ? disabledBackground
      : hexToRgba(isDark ? '#ffffff' : '#000000', 0.18);

  switch (variant) {
    case 'outline':
      return {
        base: {
          background: 'transparent',
          border: main,
          foreground: main,
          focusRing,
        },
        hover: {
          background: soft,
          border: hover,
          foreground: hover,
          focusRing,
        },
        active: {
          background: softHover,
          border: active,
          foreground: active,
          focusRing,
        },
        disabled: {
          background: 'transparent',
          border: disabledBorder,
          foreground: disabledColor,
          focusRing,
        },
      };
    case 'soft':
      return {
        base: {
          background: soft,
          border: 'transparent',
          foreground: tone === 'neutral' ? surface.text.primary : main,
          focusRing,
        },
        hover: {
          background: softHover,
          border: 'transparent',
          foreground: tone === 'neutral' ? surface.text.primary : hover,
          focusRing,
        },
        active: {
          background: hover,
          border: 'transparent',
          foreground: foreground,
          focusRing,
        },
        disabled: {
          background: disabledBackground,
          border: 'transparent',
          foreground: disabledColor,
          focusRing,
        },
      };
    case 'ghost':
      return {
        base: {
          background: 'transparent',
          border: 'transparent',
          foreground: tone === 'neutral' ? surface.text.primary : main,
          focusRing,
        },
        hover: {
          background: surface.action.hover,
          border: 'transparent',
          foreground: tone === 'neutral' ? surface.text.primary : hover,
          focusRing,
        },
        active: {
          background: surface.action.selected,
          border: 'transparent',
          foreground: tone === 'neutral' ? surface.text.primary : active,
          focusRing,
        },
        disabled: {
          background: 'transparent',
          border: 'transparent',
          foreground: disabledColor,
          focusRing,
        },
      };
    case 'link':
      return {
        base: {
          background: 'transparent',
          border: 'transparent',
          foreground: main,
          focusRing,
          textDecoration: 'underline transparent',
        },
        hover: {
          background: 'transparent',
          border: 'transparent',
          foreground: hover,
          focusRing,
          textDecoration: `underline ${hover}`,
        },
        active: {
          background: 'transparent',
          border: 'transparent',
          foreground: active,
          focusRing,
          textDecoration: `underline ${active}`,
        },
        disabled: {
          background: 'transparent',
          border: 'transparent',
          foreground: disabledColor,
          focusRing,
          textDecoration: 'none',
        },
      };
    case 'solid':
    default:
      return {
        base: {
          background: main,
          border: main,
          foreground,
          focusRing,
        },
        hover: {
          background: hover,
          border: hover,
          foreground,
          focusRing,
        },
        active: {
          background: active,
          border: active,
          foreground,
          focusRing,
        },
        disabled: {
          background: disabledBackground,
          border: disabledBackground,
          foreground: disabledColor,
          focusRing,
        },
      };
  }
}

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  /**
   * Visual variant of the button.
   */
  variant?: ButtonVariant | 'default' | 'secondary' | 'destructive' | 'primary' | 'contained' | 'outlined' | 'text';

  /**
   * Tone controls the color palette.
   */
  tone?: ButtonTone;

  /**
   * Size of the button.
   */
  size?: ButtonSizeAlias;

  /**
   * Optional leading icon.
   */
  leadingIcon?: React.ReactNode;

  /** MUI-like alias for `leadingIcon`. */
  startIcon?: React.ReactNode;

  /**
   * Optional trailing icon.
   */
  trailingIcon?: React.ReactNode;

  /** MUI-like alias for `trailingIcon`. */
  endIcon?: React.ReactNode;

  /**
   * Display a busy state.
   */
  loading?: boolean;

  /** MUI-like sx prop (limited support). */
  sx?: SxProps;

  /** Optional link target. When provided, renders as an <a>. */
  href?: string;
  target?: string;
  rel?: string;
  download?: string;

  /**
   * Stretch to full width.
   */
  fullWidth?: boolean;
}

/**
 * Accessible, theme-aware button component.
 */
export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>((props, ref) => {
  const {
    variant = 'solid',
    tone = 'primary',
    size: sizeProp = 'md',
    leadingIcon,
    startIcon,
    trailingIcon,
    endIcon,
    loading = false,
    sx,
    fullWidth = false,
    className,
    disabled,
    href,
    target,
    rel,
    download,
    children,
    type = 'button',
    onMouseEnter,
    onMouseLeave,
    onMouseDown,
    onMouseUp,
    onClick,
    onFocus,
    onBlur,
    style: styleProp,
    ...rest
  } = props;

  const resolvedLeadingIcon = leadingIcon ?? startIcon;
  const resolvedTrailingIcon = trailingIcon ?? endIcon;

  const size: ButtonSize =
    sizeProp === 'small' ? 'sm' : sizeProp === 'medium' ? 'md' : sizeProp === 'large' ? 'lg' : sizeProp;

  // Map variant aliases to actual variants
  const mappedVariant: ButtonVariant =
    variant === 'default' || variant === 'primary' || variant === 'contained' || variant === 'destructive'
      ? 'solid'
      : variant === 'outlined'
        ? 'outline'
        : variant === 'text'
          ? 'ghost'
          : (variant as ButtonVariant);

  const mappedTone: ButtonTone =
    variant === 'destructive'
      ? 'danger'
      : variant === 'secondary'
        ? 'secondary'
        : variant === 'primary'
          ? 'primary'
          : tone;

  const { resolvedTheme } = useTheme();
  const { focusProps, isFocusVisible } = useFocusRing<HTMLElement>({ onFocus, onBlur });

  const [isHovered, setIsHovered] = React.useState(false);
  const [isPressed, setIsPressed] = React.useState(false);

  const scheme = React.useMemo(
    () => getButtonScheme(mappedVariant, mappedTone, resolvedTheme),
    [mappedVariant, mappedTone, resolvedTheme]
  );

  const currentState = disabled || rest['aria-disabled']
    ? scheme.disabled
    : isPressed
      ? scheme.active
      : isHovered
        ? scheme.hover
        : scheme.base;

  const metrics = sizeMetrics[size];

  const handleMouseEnter = React.useCallback(
    (event: React.MouseEvent<HTMLElement>) => {
      setIsHovered(true);
      onMouseEnter?.(event as unknown as React.MouseEvent<HTMLButtonElement>);
    },
    [onMouseEnter]
  );

  const handleMouseLeave = React.useCallback(
    (event: React.MouseEvent<HTMLElement>) => {
      setIsHovered(false);
      setIsPressed(false);
      onMouseLeave?.(event as unknown as React.MouseEvent<HTMLButtonElement>);
    },
    [onMouseLeave]
  );

  const handleMouseDown = React.useCallback(
    (event: React.MouseEvent<HTMLElement>) => {
      if (event.button === 0) {
        setIsPressed(true);
      }
      onMouseDown?.(event as unknown as React.MouseEvent<HTMLButtonElement>);
    },
    [onMouseDown]
  );

  const handleMouseUp = React.useCallback(
    (event: React.MouseEvent<HTMLElement>) => {
      setIsPressed(false);
      onMouseUp?.(event as unknown as React.MouseEvent<HTMLButtonElement>);
    },
    [onMouseUp]
  );

  const isDisabled = Boolean(disabled || loading || rest['aria-disabled']);

  const baseStyle: React.CSSProperties = {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    width: fullWidth ? '100%' : undefined,
    minHeight: `${metrics.minHeight}px`,
    gap: metrics.gap,
    paddingInline: metrics.paddingInline,
    paddingBlock: metrics.paddingBlock,
    fontSize: metrics.fontSize,
    lineHeight: metrics.lineHeight,
    fontWeight: fontWeight.medium,
    borderRadius: `${componentRadius.button}px`,
    borderWidth: variant === 'link' ? 0 : 1,
    borderStyle: variant === 'link' ? 'none' : 'solid',
    backgroundColor: currentState.background,
    borderColor: currentState.border,
    color: currentState.foreground,
    textDecoration: currentState.textDecoration ?? 'none',
    transition: transitions.default,
    outline: 'none',
    boxShadow: isFocusVisible ? `0 0 0 3px ${currentState.focusRing}` : undefined,
    cursor: isDisabled ? 'not-allowed' : 'pointer',
    opacity: isDisabled && variant === 'link' ? 0.6 : undefined,
  };

  const combinedStyle = {
    ...baseStyle,
    ...sxToStyle(sx),
    ...(styleProp ?? {}),
  };

  const content = (
    <>
      {loading && (
        <Spinner
          aria-hidden="true"
          size={size === 'lg' ? 'md' : 'sm'}
          color={currentState.foreground}
        />
      )}
      {!loading && resolvedLeadingIcon ? (
        <span className="gh-button__icon--leading">{resolvedLeadingIcon}</span>
      ) : null}
      <span className="gh-button__label">{children}</span>
      {resolvedTrailingIcon ? (
        <span className="gh-button__icon--trailing">{resolvedTrailingIcon}</span>
      ) : null}
    </>
  );

  if (href) {
    return (
      <a
        {...(rest as unknown as React.AnchorHTMLAttributes<HTMLAnchorElement>)}
        {...focusProps}
        ref={ref as unknown as React.Ref<HTMLAnchorElement>}
        href={isDisabled ? undefined : href}
        target={target}
        rel={rel}
        download={download}
        className={cn('gh-button', className)}
        style={combinedStyle}
        onMouseEnter={handleMouseEnter}
        onMouseLeave={handleMouseLeave}
        onMouseDown={handleMouseDown}
        onMouseUp={handleMouseUp}
        onClick={(e) => {
          if (isDisabled) {
            e.preventDefault();
            e.stopPropagation();
          }
          onClick?.(e as unknown as React.MouseEvent<HTMLButtonElement>);
        }}
        aria-disabled={isDisabled || undefined}
        tabIndex={isDisabled ? -1 : rest.tabIndex}
        data-variant={variant}
        data-tone={tone}
        data-size={size}
        data-loading={loading ? 'true' : undefined}
        aria-busy={loading || undefined}
      >
        {content}
      </a>
    );
  }

  return (
    <button
      {...rest}
      {...focusProps}
      ref={ref}
      type={type}
      className={cn('gh-button', className)}
      style={combinedStyle}
      onMouseEnter={handleMouseEnter}
      onMouseLeave={handleMouseLeave}
      onMouseDown={handleMouseDown}
      onMouseUp={handleMouseUp}
      onClick={(e) => {
        if (isDisabled) {
          e.preventDefault();
          e.stopPropagation();
        } else {
          onClick?.(e);
        }
      }}
      disabled={isDisabled}
      data-variant={variant}
      data-tone={tone}
      data-size={size}
      data-loading={loading ? 'true' : undefined}
      aria-busy={loading || undefined}
    >
      {content}
    </button>
  );
});

Button.displayName = 'Button';
