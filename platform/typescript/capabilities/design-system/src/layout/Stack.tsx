import * as React from 'react';
import {
  spacing,
  semanticSpacing,
} from '@ghatana/tokens';
import { cn } from '@ghatana/utils';
import { sxToStyle } from '../utils/sx';

const spacingValues = spacing as Record<string, number>;
const semanticValues = semanticSpacing as Record<string, number>;

export type StackGap = keyof typeof spacing | keyof typeof semanticSpacing | number | string;

export type StackDirection = 'vertical' | 'horizontal';
export type StackAlign = 'start' | 'center' | 'end' | 'stretch' | 'baseline';
export type StackJustify = 'start' | 'center' | 'end' | 'between' | 'around' | 'evenly';
export type StackWrap = 'nowrap' | 'wrap' | 'wrap-reverse';

export interface StackProps extends React.HTMLAttributes<HTMLElement> {
  as?: React.ElementType;
  /**
   * Layout direction.
   *
   * Supports legacy aliases ('row' | 'column') for backward compatibility.
   */
  direction?: StackDirection | 'row' | 'column';
  gap?: StackGap;
  /**
   * Legacy alias for `gap`.
   */
  spacing?: StackGap;
  align?: StackAlign;
  /** Legacy alias for `align`. Accepts CSS `align-items` values. */
  alignItems?: React.CSSProperties['alignItems'];
  justify?: StackJustify;
  /** Legacy alias for `justify`. Accepts CSS `justify-content` values. */
  justifyContent?: React.CSSProperties['justifyContent'];
  wrap?: StackWrap | boolean;
  /** MUI-compat alias for `wrap`. */
  flexWrap?: React.CSSProperties['flexWrap'];
  divider?: React.ReactNode;
  fullWidth?: boolean;
  fullHeight?: boolean;

  /** Minimal MUI-like style prop. Supports spacing shorthands. */
  sx?: unknown;
}

function resolveGap(gap?: StackGap): string | undefined {
  if (gap === undefined) {
    const value = semanticValues['md'] ?? spacingValues['4'];
    return value !== undefined ? `${value}px` : undefined;
  }

  if (typeof gap === 'number') {
    return `${gap}px`;
  }

  if (typeof gap === 'string') {
    if (spacingValues[gap] !== undefined) {
      return `${spacingValues[gap]}px`;
    }
    if (semanticValues[gap] !== undefined) {
      return `${semanticValues[gap]}px`;
    }

    // gap-4, gap-x-4, etc.
    const match = /^gap(?:-[xy])?-([0-9]+(?:\.[0-9]+)?)$/.exec(gap);
    if (match) {
      const key = match[1];
      if (spacingValues[key] !== undefined) {
        return `${spacingValues[key]}px`;
      }
    }

    if (/^[0-9]+(px|rem|em|%)$/.test(gap) || gap === '0') {
      return gap;
    }

    // fallback to CSS variable or raw string
    return gap;
  }

  return undefined;
}

const alignMap: Record<StackAlign, React.CSSProperties['alignItems']> = {
  start: 'flex-start',
  center: 'center',
  end: 'flex-end',
  stretch: 'stretch',
  baseline: 'baseline',
};

const justifyMap: Record<StackJustify, React.CSSProperties['justifyContent']> = {
  start: 'flex-start',
  center: 'center',
  end: 'flex-end',
  between: 'space-between',
  around: 'space-around',
  evenly: 'space-evenly',
};

function normaliseWrap(wrap?: StackWrap | boolean): React.CSSProperties['flexWrap'] {
  if (wrap === true) return 'wrap';
  if (wrap === false || wrap === undefined) return 'nowrap';
  return wrap;
}

export const Stack = React.forwardRef<HTMLElement, StackProps>((props, ref) => {
  const {
    as: Component = 'div',
    direction = 'vertical',
    gap,
    spacing,
    sx,
    align,
    alignItems: alignItemsProp,
    justify,
    justifyContent: justifyContentProp,
    wrap,
    flexWrap: flexWrapProp,
    divider,
    fullWidth = false,
    fullHeight = false,
    className,
    style,
    children,
    ...rest
  } = props;

  const normalisedDirection: StackDirection =
    direction === 'row' ? 'horizontal' : direction === 'column' ? 'vertical' : direction;
  const flexDirection = normalisedDirection === 'vertical' ? 'column' : 'row';
  const gapValue = resolveGap(gap ?? spacing);
  const alignItems = align ? alignMap[align] : alignItemsProp;
  const justifyContent = justify ? justifyMap[justify] : justifyContentProp;
  const flexWrap = flexWrapProp ?? normaliseWrap(wrap);

  const baseStyles: React.CSSProperties = {
    display: 'flex',
    flexDirection,
    gap: gapValue,
    alignItems,
    justifyContent,
    flexWrap,
    width: fullWidth ? '100%' : undefined,
    height: fullHeight ? '100%' : undefined,
    ...sxToStyle(sx),
    ...style,
  };

  const childrenArray = React.Children.toArray(children);
  const content = divider
    ? childrenArray.reduce<React.ReactNode[]>((acc, child, index) => {
      if (index > 0) {
        acc.push(
          <React.Fragment key={`divider-${index}`}>{divider}</React.Fragment>
        );
      }
      acc.push(child);
      return acc;
    }, [])
    : children;

  return (
    <Component
      ref={ref as React.Ref<HTMLElement>}
      className={cn('gh-stack', className)}
      style={baseStyles}
      {...rest}
    >
      {content}
    </Component>
  );
});

Stack.displayName = 'Stack';
