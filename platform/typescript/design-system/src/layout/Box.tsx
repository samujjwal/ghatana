import * as React from 'react';
import {
  spacing,
  semanticSpacing,
  palette,
  borderRadius as borderRadiusTokens,
  componentRadius,
  lightShadows,
  darkShadows,
} from '@ghatana/tokens';
import { cn } from '@ghatana/utils';
import { useTheme } from '@ghatana/theme';
import { sxToStyle } from '../utils/sx';

const spacingValues = spacing as Record<string, number>;
const semanticSpacingValues = semanticSpacing as Record<string, number>;

export type SpacingValue = keyof typeof spacing | keyof typeof semanticSpacing | number | string;

export interface BoxProps extends React.HTMLAttributes<HTMLElement> {
  as?: React.ElementType;

  /** Minimal MUI-like style prop. Supports spacing shorthands. */
  sx?: unknown;

  // Spacing (modern)
  padding?: SpacingValue;
  paddingX?: SpacingValue;
  paddingY?: SpacingValue;
  paddingTop?: SpacingValue;
  paddingRight?: SpacingValue;
  paddingBottom?: SpacingValue;
  paddingLeft?: SpacingValue;
  margin?: SpacingValue;
  marginX?: SpacingValue;
  marginY?: SpacingValue;
  marginTop?: SpacingValue;
  marginRight?: SpacingValue;
  marginBottom?: SpacingValue;
  marginLeft?: SpacingValue;

  // Spacing (legacy aliases)
  p?: SpacingValue;
  px?: SpacingValue;
  py?: SpacingValue;
  pt?: SpacingValue;
  pr?: SpacingValue;
  pb?: SpacingValue;
  pl?: SpacingValue;
  m?: SpacingValue;
  mx?: SpacingValue;
  my?: SpacingValue;
  mt?: SpacingValue;
  mr?: SpacingValue;
  mb?: SpacingValue;
  ml?: SpacingValue;

  background?: string;
  backgroundColor?: string;
  bg?: string;
  color?: string;
  textColor?: string;

  radius?: number | string;
  rounded?: string;
  borderRadius?: number | string;

  borderColor?: string;
  borderWidth?: number | string;
  borderStyle?: React.CSSProperties['borderStyle'];
  border?: string;

  shadow?: string | number;

  display?: React.CSSProperties['display'];

  width?: number | string;
  w?: number | string;
  height?: number | string;
  h?: number | string;
  maxWidth?: number | string;
  maxW?: number | string;
  maxHeight?: number | string;
  maxH?: number | string;
  minWidth?: number | string;
  minW?: number | string;
  minHeight?: number | string;
  minH?: number | string;

  overflow?: string;
  position?: React.CSSProperties['position'];
  top?: string | number;
  right?: string | number;
  bottom?: string | number;
  left?: string | number;
  zIndex?: string | number;
}

function resolveSpacing(input?: SpacingValue): string | undefined {
  if (input === undefined || input === null) return undefined;
  if (typeof input === 'number') return `${input}px`;
  if (typeof input === 'string') {
    const trimmed = input.trim();

    if (trimmed === '') return undefined;

    if (/^[0-9]+(?:\.[0-9]+)?$/.test(trimmed)) {
      return `${trimmed}px`;
    }

    if (/^[0-9]+(?:\.[0-9]+)?(px|rem|em|%)$/.test(trimmed) || trimmed === 'auto') {
      return trimmed;
    }

    const tailwindMatch = /^(?:p|px|py|pt|pr|pb|pl|m|mx|my|mt|mr|mb|ml|gap|gap-[xy])-(.+)$/.exec(trimmed);
    if (tailwindMatch) {
      return resolveSpacing(tailwindMatch[1]);
    }

    if (spacingValues[trimmed] !== undefined) {
      return `${spacingValues[trimmed]}px`;
    }

    if (semanticSpacingValues[trimmed] !== undefined) {
      return `${semanticSpacingValues[trimmed]}px`;
    }

    return trimmed;
  }

  return undefined;
}

function normalizeSize(value?: string | number): string | undefined {
  if (value === undefined || value === null) return undefined;
  if (typeof value === 'number') return `${value}px`;
  const trimmed = value.toString().trim();
  if (trimmed === '') return undefined;
  if (/^[0-9]+(?:\.[0-9]+)?$/.test(trimmed)) {
    return `${trimmed}px`;
  }
  return trimmed;
}

function resolvePaletteColor(token?: string): string | undefined {
  if (!token) return undefined;
  const cleaned = token.replace(/^bg-/, '').replace(/^text-/, '').replace(/^border-/, '');

  if (cleaned === 'transparent') return 'transparent';
  if (cleaned === 'white') return '#ffffff';
  if (cleaned === 'black') return '#000000';

  const [rawScale, rawShade] = cleaned.split('-');
  if (!rawScale) return undefined;

  const scale = rawScale === 'grey' ? 'gray' : rawScale;
  const shade = rawShade ?? '500';

  const group = (palette as Record<string, Record<string, string | undefined>>)[scale];
  if (group) {
    if (typeof group === 'object') {
      if (group[shade] !== undefined) return group[shade] as string;
      if (group.main && typeof group.main === 'string') return group.main;
      if (group[500]) return group[500];
    }
  }

  return undefined;
}

function resolveRadius(value?: number | string): string | undefined {
  if (value === undefined || value === null) return undefined;
  if (typeof value === 'number') return `${value}px`;
  const trimmed = value.trim();
  if (trimmed === '') return undefined;

  if (/^[0-9]+(?:\.[0-9]+)?$/.test(trimmed)) {
    return `${trimmed}px`;
  }

  if (borderRadiusTokens[trimmed as keyof typeof borderRadiusTokens] !== undefined) {
    return `${borderRadiusTokens[trimmed as keyof typeof borderRadiusTokens]}px`;
  }

  if (componentRadius[trimmed as keyof typeof componentRadius] !== undefined) {
    return `${componentRadius[trimmed as keyof typeof componentRadius]}px`;
  }

  if (/^[0-9]+(?:\.[0-9]+)?(px|rem|em|%)$/.test(trimmed)) {
    return trimmed;
  }

  return trimmed;
}

function resolveShadow(value: string | number | undefined, theme: 'light' | 'dark'): string | undefined {
  if (value === undefined || value === null) return undefined;

  const shadows = theme === 'dark' ? darkShadows : lightShadows;

  if (typeof value === 'number') {
    return shadows[Math.min(shadows.length - 1, Math.max(0, value))];
  }

  const trimmed = value.trim();
  if (trimmed === '') return undefined;
  if (trimmed === 'none' || trimmed === 'shadow-none') return 'none';

  const tailwindMap: Record<string, number> = {
    shadow: 2,
    'shadow-sm': 1,
    'shadow-md': 4,
    'shadow-lg': 8,
    'shadow-xl': 12,
    'shadow-2xl': 16,
  };

  if (tailwindMap[trimmed] !== undefined) {
    return shadows[Math.min(shadows.length - 1, tailwindMap[trimmed])];
  }

  const numeric = Number(trimmed);
  if (!Number.isNaN(numeric)) {
    return shadows[Math.min(shadows.length - 1, Math.max(0, numeric))];
  }

  return trimmed;
}

function resolveOverflow(value?: string): React.CSSProperties['overflow'] {
  if (!value) return undefined;
  const trimmed = value.trim();
  const map: Record<string, React.CSSProperties['overflow']> = {
    'overflow-hidden': 'hidden',
    'overflow-auto': 'auto',
    'overflow-scroll': 'scroll',
    'overflow-visible': 'visible',
  };
  return map[trimmed] ?? (trimmed as React.CSSProperties['overflow']);
}

function resolvePositionValue(value?: string | number): string | undefined {
  if (value === undefined || value === null) return undefined;
  if (typeof value === 'number') return `${value}px`;
  const trimmed = value.trim();
  if (trimmed === '') return undefined;

  const tailwindMatch = /^(?:top|right|bottom|left)-(.*)$/.exec(trimmed);
  if (tailwindMatch) {
    return resolveSpacing(tailwindMatch[1]);
  }

  if (/^[0-9]+(?:\.[0-9]+)?$/.test(trimmed)) {
    return `${trimmed}px`;
  }

  return trimmed;
}

function resolveZIndex(value?: string | number): number | string | undefined {
  if (value === undefined || value === null) return undefined;
  if (typeof value === 'number') return value;
  const trimmed = value.trim();
  const tailwindMatch = /^z-(.*)$/.exec(trimmed);
  if (tailwindMatch) {
    const numeric = Number(tailwindMatch[1]);
    if (!Number.isNaN(numeric)) return numeric;
  }
  const numeric = Number(trimmed);
  if (!Number.isNaN(numeric)) return numeric;
  return trimmed;
}

export const Box = React.forwardRef<HTMLElement, BoxProps>((props, ref) => {
  const {
    as: Component = 'div',
    sx,
    padding,
    paddingX,
    paddingY,
    paddingTop,
    paddingRight,
    paddingBottom,
    paddingLeft,
    margin,
    marginX,
    marginY,
    marginTop,
    marginRight,
    marginBottom,
    marginLeft,
    p,
    px,
    py,
    pt,
    pr,
    pb,
    pl,
    m,
    mx,
    my,
    mt,
    mr,
    mb,
    ml,
    background,
    backgroundColor,
    bg,
    color,
    textColor,
    radius,
    rounded,
    borderRadius,
    borderColor,
    borderWidth,
    borderStyle,
    border,
    shadow,
    display,
    width,
    w,
    height,
    h,
    maxWidth,
    maxW,
    maxHeight,
    maxH,
    minWidth,
    minW,
    minHeight,
    minH,
    overflow,
    position,
    top,
    right,
    bottom,
    left,
    zIndex,
    style,
    className,
    ...rest
  } = props;

  const { resolvedTheme } = useTheme();
  const themeMode: 'light' | 'dark' = resolvedTheme === 'dark' ? 'dark' : 'light';

  const computedStyle: React.CSSProperties = {
    display,
    width: normalizeSize(w ?? width),
    height: normalizeSize(h ?? height),
    maxWidth: normalizeSize(maxW ?? maxWidth),
    maxHeight: normalizeSize(maxH ?? maxHeight),
    minWidth: normalizeSize(minW ?? minWidth),
    minHeight: normalizeSize(minH ?? minHeight),
    overflow: resolveOverflow(overflow),
    position,
    top: resolvePositionValue(top),
    right: resolvePositionValue(right),
    bottom: resolvePositionValue(bottom),
    left: resolvePositionValue(left),
    zIndex: resolveZIndex(zIndex),
  };

  const paddingValue = padding ?? p;
  const paddingXValue = paddingX ?? px;
  const paddingYValue = paddingY ?? py;
  const paddingTopValue = paddingTop ?? pt;
  const paddingRightValue = paddingRight ?? pr;
  const paddingBottomValue = paddingBottom ?? pb;
  const paddingLeftValue = paddingLeft ?? pl;

  const marginValue = margin ?? m;
  const marginXValue = marginX ?? mx;
  const marginYValue = marginY ?? my;
  const marginTopValue = marginTop ?? mt;
  const marginRightValue = marginRight ?? mr;
  const marginBottomValue = marginBottom ?? mb;
  const marginLeftValue = marginLeft ?? ml;

  if (paddingValue !== undefined) computedStyle.padding = resolveSpacing(paddingValue);
  if (paddingXValue !== undefined) computedStyle.paddingInline = resolveSpacing(paddingXValue);
  if (paddingYValue !== undefined) computedStyle.paddingBlock = resolveSpacing(paddingYValue);
  if (paddingTopValue !== undefined) computedStyle.paddingTop = resolveSpacing(paddingTopValue);
  if (paddingRightValue !== undefined) computedStyle.paddingRight = resolveSpacing(paddingRightValue);
  if (paddingBottomValue !== undefined) computedStyle.paddingBottom = resolveSpacing(paddingBottomValue);
  if (paddingLeftValue !== undefined) computedStyle.paddingLeft = resolveSpacing(paddingLeftValue);

  if (marginValue !== undefined) computedStyle.margin = resolveSpacing(marginValue);
  if (marginXValue !== undefined) computedStyle.marginInline = resolveSpacing(marginXValue);
  if (marginYValue !== undefined) computedStyle.marginBlock = resolveSpacing(marginYValue);
  if (marginTopValue !== undefined) computedStyle.marginTop = resolveSpacing(marginTopValue);
  if (marginRightValue !== undefined) computedStyle.marginRight = resolveSpacing(marginRightValue);
  if (marginBottomValue !== undefined) computedStyle.marginBottom = resolveSpacing(marginBottomValue);
  if (marginLeftValue !== undefined) computedStyle.marginLeft = resolveSpacing(marginLeftValue);

  const resolvedBackground = resolvePaletteColor(bg ?? background ?? backgroundColor) ?? background ?? backgroundColor;
  if (resolvedBackground) computedStyle.background = resolvedBackground;

  const resolvedColor = resolvePaletteColor(color ?? textColor) ?? color ?? textColor;
  if (resolvedColor) computedStyle.color = resolvedColor;

  const resolvedRadius = resolveRadius(radius ?? rounded ?? borderRadius);
  if (resolvedRadius) computedStyle.borderRadius = resolvedRadius;

  const resolvedBorderColor = resolvePaletteColor(borderColor) ?? borderColor;
  if (resolvedBorderColor) computedStyle.borderColor = resolvedBorderColor;

  const resolvedBorderWidth = normalizeSize(borderWidth);
  if (resolvedBorderWidth) computedStyle.borderWidth = resolvedBorderWidth;

  if (borderStyle) computedStyle.borderStyle = borderStyle;
  if (border === 'border-none') {
    computedStyle.borderWidth = 0;
  } else if (typeof border === 'string') {
    const parts = border.split(/\s+/).filter(Boolean);
    let extractedWidth: string | undefined;
    let extractedColor: string | undefined;
    parts.forEach((part) => {
      if (part === 'border') {
        extractedWidth = '1px';
        computedStyle.borderStyle = computedStyle.borderStyle ?? 'solid';
      } else if (/^border-[0-9]+$/.test(part)) {
        extractedWidth = `${part.split('-')[1]}px`;
        computedStyle.borderStyle = computedStyle.borderStyle ?? 'solid';
      } else if (part.startsWith('border-')) {
        extractedColor = resolvePaletteColor(part) ?? extractedColor;
      }
    });
    if (extractedWidth) computedStyle.borderWidth = extractedWidth;
    if (extractedColor) computedStyle.borderColor = extractedColor;
  }

  if (computedStyle.borderWidth !== undefined && computedStyle.borderStyle === undefined) {
    computedStyle.borderStyle = 'solid';
  }

  if (computedStyle.borderColor !== undefined && computedStyle.borderStyle === undefined) {
    computedStyle.borderStyle = 'solid';
  }

  const resolvedShadow = resolveShadow(shadow, themeMode);
  if (resolvedShadow) computedStyle.boxShadow = resolvedShadow;

  const mergedStyle = { ...computedStyle, ...sxToStyle(sx), ...style };

  return (
    <Component ref={ref as React.Ref<HTMLElement>} className={cn('gh-box', className)} style={mergedStyle} {...rest} />
  );
});

Box.displayName = 'Box';
