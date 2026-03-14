import * as React from 'react';
import { cn } from '@ghatana/utils';
import {
  fontSize,
  fontWeight,
  lineHeight,
  letterSpacing,
  palette,
  lightColors,
  darkColors,
} from '@ghatana/tokens';
import { useTheme } from '@ghatana/theme';

export type TextVariant =
  | 'body'
  | 'body-sm'
  | 'body-xs'
  | 'caption'
  | 'overline'
  | 'button'
  | 'code';

export type TextTone =
  | 'default'
  | 'subtle'
  | 'muted'
  | 'primary'
  | 'secondary'
  | 'success'
  | 'warning'
  | 'danger'
  | 'info';

export type TextAlign = 'start' | 'center' | 'end' | 'justify';

export interface TextProps extends React.HTMLAttributes<HTMLElement> {
  as?: React.ElementType;
  variant?: TextVariant;
  tone?: TextTone;
  weight?: 'regular' | 'medium' | 'semibold' | 'bold';
  align?: TextAlign;
  uppercase?: boolean;
  noWrap?: boolean;
  truncate?: boolean;
}

const variantStyles: Record<TextVariant, { fontSize: string; fontWeight: number; lineHeight: number; letterSpacing?: string; textTransform?: string }> = {
  body: {
    fontSize: fontSize.base,
    fontWeight: fontWeight.regular,
    lineHeight: lineHeight.relaxed,
  },
  'body-sm': {
    fontSize: fontSize.sm,
    fontWeight: fontWeight.regular,
    lineHeight: lineHeight.normal,
  },
  'body-xs': {
    fontSize: fontSize.xs,
    fontWeight: fontWeight.regular,
    lineHeight: lineHeight.normal,
  },
  caption: {
    fontSize: fontSize.xs,
    fontWeight: fontWeight.medium,
    lineHeight: lineHeight.normal,
    letterSpacing: letterSpacing.wide,
  },
  overline: {
    fontSize: fontSize.xs,
    fontWeight: fontWeight.medium,
    lineHeight: lineHeight.normal,
    letterSpacing: letterSpacing.widest,
    textTransform: 'uppercase',
  },
  button: {
    fontSize: fontSize.sm,
    fontWeight: fontWeight.semibold,
    lineHeight: lineHeight.normal,
    letterSpacing: letterSpacing.wide,
  },
  code: {
    fontSize: fontSize.sm,
    fontWeight: fontWeight.regular,
    lineHeight: lineHeight.normal,
  },
};

const tonePalette: Record<TextTone, string | undefined> = {
  default: undefined,
  subtle: 'subtle',
  muted: 'muted',
  primary: palette.primary[500],
  secondary: palette.secondary[500],
  success: palette.success.main ?? palette.success[500],
  warning: palette.warning.main ?? palette.warning[500],
  danger: palette.error.main ?? palette.error[500],
  info: palette.info.main ?? palette.info[500],
};

const alignMap: Record<TextAlign, React.CSSProperties['textAlign']> = {
  start: 'left',
  center: 'center',
  end: 'right',
  justify: 'justify',
};

export const Text = React.forwardRef<HTMLElement, TextProps>((props, ref) => {
  const {
    as,
    variant = 'body',
    tone = 'default',
    weight,
    align,
    uppercase,
    noWrap = false,
    truncate = false,
    className: _className,
    style,
    children,
    ...rest
  } = props;

  const Component = (as || (variant === 'body' || variant === 'body-sm' || variant === 'body-xs' ? 'p' : 'span')) as React.ElementType;

  const { resolvedTheme } = useTheme();
  const isDark = resolvedTheme === 'dark';
  const surface = isDark ? darkColors : lightColors;

  const variantStyle = variantStyles[variant];

  const color = (() => {
    if (tone === 'default') return surface.text.primary;
    if (tone === 'subtle') return surface.text.secondary;
    if (tone === 'muted') return surface.text.disabled;
    const paletteColor = tonePalette[tone];
    if (paletteColor) return paletteColor;
    return surface.text.primary;
  })();

  const fontWeightValue = (() => {
    if (weight === 'medium') return fontWeight.medium;
    if (weight === 'semibold') return fontWeight.semibold;
    if (weight === 'bold') return fontWeight.bold;
    if (weight === 'regular') return fontWeight.regular;
    return variantStyle.fontWeight;
  })();

  return (
    <Component
      ref={ref as React.Ref<HTMLElement>}
      className={cn(
        'gh-text',
        truncate && 'gh-text--truncate'
      )}
      style={{
        fontSize: variantStyle.fontSize,
        fontWeight: fontWeightValue,
        lineHeight: variantStyle.lineHeight,
        letterSpacing: variantStyle.letterSpacing,
        textTransform: uppercase ? 'uppercase' : variantStyle.textTransform,
        color,
        textAlign: align ? alignMap[align] : undefined,
        whiteSpace: noWrap ? 'nowrap' : undefined,
        overflow: truncate ? 'hidden' : undefined,
        textOverflow: truncate ? 'ellipsis' : undefined,
        fontFamily: variant === 'code' ? 'Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace' : undefined,
        ...style,
      }}
      {...rest}
    >
      {children}
    </Component>
  );
});

Text.displayName = 'Text';
