import * as React from 'react';
import {
  spacing,
  semanticSpacing,
  containerMaxWidths,
} from '@ghatana/tokens';
import { cn } from '@ghatana/utils';

const spacingValues = spacing as Record<string, number>;
const semanticSpacingValues = semanticSpacing as Record<string, number>;
const containerWidths = containerMaxWidths as Record<string, number>;

export type ContainerWidth = keyof typeof containerMaxWidths | 'full' | false;

function resolveSpacing(value?: string | number): string | undefined {
  if (value === undefined || value === null) return undefined;
  if (typeof value === 'number') return `${value}px`;
  const trimmed = value.trim();
  if (trimmed === '') return undefined;

  if (/^[0-9]+(?:\.[0-9]+)?$/.test(trimmed)) return `${trimmed}px`;
  if (/^[0-9]+(?:\.[0-9]+)?(px|rem|em|%)$/.test(trimmed)) return trimmed;

  if (spacingValues[trimmed] !== undefined) return `${spacingValues[trimmed]}px`;
  if (semanticSpacingValues[trimmed] !== undefined) return `${semanticSpacingValues[trimmed]}px`;

  const tailwindMatch = /^p[xy]?-(.+)$/.exec(trimmed);
  if (tailwindMatch) {
    return resolveSpacing(tailwindMatch[1]);
  }

  return trimmed;
}

export interface ContainerProps extends React.HTMLAttributes<HTMLElement> {
  as?: React.ElementType;
  maxWidth?: ContainerWidth;
  centered?: boolean;
  padding?: boolean;
  paddingValue?: string | number;
  paddingX?: string | number;
  paddingY?: string | number;
  fixed?: boolean;
}

export const Container = React.forwardRef<HTMLElement, ContainerProps>((props, ref) => {
  const {
    as: Component = 'div',
    maxWidth = 'lg',
    centered = true,
    padding = true,
    paddingValue,
    paddingX,
    paddingY,
    fixed = false,
    style,
    className,
    children,
    ...rest
  } = props;

  const resolvedPaddingX = resolveSpacing(paddingX ?? paddingValue ?? semanticSpacingValues.md ?? spacingValues['4']);
  const resolvedPaddingY = resolveSpacing(paddingY ?? 0);

  let resolvedMaxWidth: string | undefined;
  if (maxWidth === 'full') {
    resolvedMaxWidth = '100%';
  } else if (maxWidth && maxWidth in containerWidths) {
    resolvedMaxWidth = `${containerWidths[maxWidth]}px`;
  } else if (typeof maxWidth === 'string' && /^[0-9]+(?:\.[0-9]+)?(px|rem|em|%)$/.test(maxWidth)) {
    resolvedMaxWidth = maxWidth;
  }

  const baseStyle: React.CSSProperties = {
    width: '100%',
    marginLeft: centered ? 'auto' : undefined,
    marginRight: centered ? 'auto' : undefined,
    paddingLeft: padding ? resolvedPaddingX : undefined,
    paddingRight: padding ? resolvedPaddingX : undefined,
    paddingTop: padding && resolvedPaddingY ? resolvedPaddingY : undefined,
    paddingBottom: padding && resolvedPaddingY ? resolvedPaddingY : undefined,
    maxWidth: resolvedMaxWidth,
  };

  const mergedStyle = fixed ? { ...baseStyle, maxWidth: resolvedMaxWidth } : baseStyle;

  return (
    <Component
      ref={ref as React.Ref<HTMLElement>}
      className={cn('gh-container', className)}
      style={{ ...mergedStyle, ...style }}
      {...rest}
    >
      {children}
    </Component>
  );
});

Container.displayName = 'Container';
