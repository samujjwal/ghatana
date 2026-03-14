import * as React from 'react';
import { cn } from '@ghatana/utils';
import {
  palette,
  lightColors,
  darkColors,
  fontSize,
  fontWeight,
  componentRadius,
} from '@ghatana/tokens';
import { useTheme } from '@ghatana/theme';

type BadgeVariant = 'solid' | 'soft' | 'outline';
type BadgeTone = 'neutral' | 'primary' | 'secondary' | 'success' | 'info' | 'warning' | 'danger';

const tonePalette: Record<BadgeTone, Record<string, string | undefined>> = {
  neutral: palette.gray,
  primary: palette.primary,
  secondary: palette.secondary,
  success: palette.success,
  info: palette.info,
  warning: palette.warning,
  danger: palette.error,
};

export interface BadgeProps extends React.HTMLAttributes<HTMLSpanElement> {
  variant?: BadgeVariant | 'default' | 'secondary' | 'destructive' | 'success';
  tone?: BadgeTone;
  /** MUI-like badge content (when provided, renders an overlaid counter badge). */
  badgeContent?: React.ReactNode;
  /** MUI-like color alias for the counter badge. */
  color?: 'default' | 'primary' | 'secondary' | 'success' | 'info' | 'warning' | 'error';
  startIcon?: React.ReactNode;
  endIcon?: React.ReactNode;
}

/**
 * Badge component – compact status indicator.
 */
export const Badge = React.forwardRef<HTMLSpanElement, BadgeProps>((props, ref) => {
  const {
    variant = 'soft',
    tone = 'neutral',
    badgeContent,
    color,
    startIcon,
    endIcon,
    className,
    style,
    children,
    ...rest
  } = props;

  // Map variant aliases to actual variants
  const mappedVariant = variant === 'default' ? 'solid' : variant === 'secondary' ? 'soft' : variant === 'success' ? 'solid' : (variant as BadgeVariant);
  const mappedTone = variant === 'destructive' ? 'danger' : variant === 'secondary' ? 'secondary' : variant === 'success' ? 'success' : tone;

  const mappedToneFromColor: BadgeTone | undefined =
    color === 'error'
      ? 'danger'
      : color === 'default'
        ? 'neutral'
        : color === undefined
          ? undefined
          : (color as BadgeTone);

  const { resolvedTheme } = useTheme();
  const paletteEntry = tonePalette[mappedToneFromColor ?? mappedTone] ?? palette.gray;
  const isDark = resolvedTheme === 'dark';
  const surface = isDark ? darkColors : lightColors;

  const main = paletteEntry[500] ?? palette.gray[500];
  const contrast =
    paletteEntry.contrastText ??
    (tone === 'neutral' ? surface.text.secondary : isDark ? surface.text.primary : '#ffffff');

  const softBackground = paletteEntry[100] ?? (isDark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.04)');
  const outlineColor = paletteEntry[400] ?? main;

  const baseStyle: React.CSSProperties = {
    display: 'inline-flex',
    alignItems: 'center',
    gap: '6px',
    paddingInline: '10px',
    paddingBlock: '4px',
    borderRadius: `${componentRadius.badge}px`,
    fontSize: fontSize.sm,
    fontWeight: fontWeight.medium,
    lineHeight: 1,
    textTransform: 'uppercase',
    letterSpacing: '0.08em',
    borderWidth: 1,
    borderStyle: 'solid',
  };

  let colorStyle: React.CSSProperties;
  switch (mappedVariant) {
    case 'solid':
      colorStyle = {
        backgroundColor: main,
        borderColor: main,
        color: contrast,
      };
      break;
    case 'outline':
      colorStyle = {
        backgroundColor: 'transparent',
        borderColor: outlineColor,
        color: main,
      };
      break;
    case 'soft':
    default:
      colorStyle = {
        backgroundColor: softBackground,
        borderColor: 'transparent',
        color: tone === 'neutral' ? surface.text.secondary : main,
      };
      break;
  }

  const mergedStyle = style ? { ...baseStyle, ...colorStyle, ...style } : { ...baseStyle, ...colorStyle };

  if (badgeContent !== undefined) {
    return (
      <span
        ref={ref}
        className={cn('gh-badge', className)}
        style={{ position: 'relative', display: 'inline-flex', alignItems: 'center', ...style }}
        {...rest}
      >
        {children}
        <span
          style={{
            position: 'absolute',
            top: '-6px',
            right: '-6px',
            minWidth: '18px',
            height: '18px',
            paddingInline: '6px',
            borderRadius: '9999px',
            backgroundColor: main,
            color: contrast,
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: fontSize.xs,
            fontWeight: fontWeight.semibold,
            lineHeight: 1,
            border: `2px solid ${isDark ? surface.background.elevated : '#ffffff'}`,
          }}
        >
          {badgeContent}
        </span>
      </span>
    );
  }

  return (
    <span
      ref={ref}
      className={cn('gh-badge', className)}
      style={mergedStyle}
      data-variant={variant}
      data-tone={tone}
      {...rest}
    >
      {startIcon ? <span className="gh-badge__icon gh-badge__icon--start">{startIcon}</span> : null}
      <span className="gh-badge__label">{children}</span>
      {endIcon ? <span className="gh-badge__icon gh-badge__icon--end">{endIcon}</span> : null}
    </span>
  );
});

Badge.displayName = 'Badge';
