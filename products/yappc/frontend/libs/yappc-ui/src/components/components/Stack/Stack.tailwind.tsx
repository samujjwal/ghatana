/**
 * Stack Component (Tailwind CSS)
 *
 * A layout component that arranges children in a vertical or horizontal stack
 * with consistent spacing between items.
 */

import React, { forwardRef } from 'react';
import type { ElementType } from 'react';

import { cn } from '../../utils/cn';

type SpacingValue = string | number;
type StackDirection = 'vertical' | 'horizontal' | 'row' | 'column';
type StackAlignment =
  | 'start'
  | 'center'
  | 'end'
  | 'stretch'
  | 'baseline'
  | 'flex-start'
  | 'flex-end';
type StackJustification =
  | 'start'
  | 'center'
  | 'end'
  | 'between'
  | 'around'
  | 'evenly'
  | 'flex-start'
  | 'flex-end'
  | 'space-between'
  | 'space-around'
  | 'space-evenly';
type StackWrap = 'wrap' | 'nowrap' | 'wrap-reverse' | boolean | string;

/**
 *
 */
export interface StackProps
  extends Omit<React.HTMLAttributes<HTMLElement>, 'wrap'> {
  /**
   * The HTML element to render
   * @default 'div'
   */
  as?: ElementType;

  /**
   * Stack direction
   * @default 'vertical'
   */
  direction?: StackDirection;

  /**
   * Spacing between items (Tailwind gap classes)
   * Examples: 'gap-2', 'gap-4', 'gap-6'
   * @default 'gap-4'
   */
  spacing?: SpacingValue;

  /**
   * Gap between items. Overrides spacing when provided.
   */
  gap?: SpacingValue;

  /**
   * Alignment on cross axis
   * For vertical: 'items-start' | 'items-center' | 'items-end' | 'items-stretch'
   * For horizontal: 'items-start' | 'items-center' | 'items-end' | 'items-baseline'
   */
  align?: string;

  /**
   * Alignment on cross axis specified with CSS keywords.
   * Convenience wrapper that maps to Tailwind classes.
   */
  alignItems?: StackAlignment;

  /**
   * Justification on main axis
   * Examples: 'justify-start', 'justify-center', 'justify-end', 'justify-between', 'justify-around'
   */
  justify?: string;

  /**
   * Main-axis justification specified with CSS keywords.
   * Convenience wrapper that maps to Tailwind classes.
   */
  justifyContent?: StackJustification;

  /**
   * Wrap items (Tailwind flex-wrap classes)
   * Examples: 'flex-wrap', 'flex-nowrap', 'flex-wrap-reverse'
   */
  wrap?: StackWrap;

  /**
   * MUI-compatible flex-wrap alias.
   */
  flexWrap?: StackWrap;

  /**
   * Margin-top convenience alias.
   */
  mt?: SpacingValue;

  /**
   * Margin-bottom convenience alias.
   */
  mb?: SpacingValue;

  /**
   * Divider between items
   * Renders a divider element between each child
   */
  divider?: React.ReactNode;

  /**
   * Full width
   * @default false
   */
  fullWidth?: boolean;

  /**
   * Full height
   * @default false
   */
  fullHeight?: boolean;

  /**
   * Additional CSS classes
   */
  className?: string;

  /**
   * Children elements
   */
  children?: React.ReactNode;
}

function spacingClass(prefix: string, value?: SpacingValue): string | undefined {
  if (value === undefined) return undefined;
  if (typeof value === 'number') return `${prefix}-${value}`;
  if (value.startsWith(`${prefix}-`)) return value;
  return `${prefix}-${value}`;
}

function mapAlignment(value?: string): string | undefined {
  if (!value) return undefined;

  const map: Record<string, string> = {
    start: 'items-start',
    center: 'items-center',
    end: 'items-end',
    stretch: 'items-stretch',
    baseline: 'items-baseline',
    'flex-start': 'items-start',
    'flex-end': 'items-end',
  };

  return map[value] ?? value;
}

function mapJustification(value?: string): string | undefined {
  if (!value) return undefined;

  const map: Record<string, string> = {
    start: 'justify-start',
    center: 'justify-center',
    end: 'justify-end',
    between: 'justify-between',
    around: 'justify-around',
    evenly: 'justify-evenly',
    'flex-start': 'justify-start',
    'flex-end': 'justify-end',
    'space-between': 'justify-between',
    'space-around': 'justify-around',
    'space-evenly': 'justify-evenly',
  };

  return map[value] ?? value;
}

function mapWrap(value?: StackWrap): string | undefined {
  if (value === undefined || value === false) return undefined;
  if (value === true || value === 'wrap') return 'flex-wrap';
  if (value === 'nowrap') return 'flex-nowrap';
  if (value === 'wrap-reverse') return 'flex-wrap-reverse';
  return value;
}

/**
 * Stack - A layout component for arranging children with consistent spacing
 *
 * @example
 * ```tsx
 * // Vertical stack with spacing
 * <Stack spacing="gap-4">
 *   <div>Item 1</div>
 *   <div>Item 2</div>
 *   <div>Item 3</div>
 * </Stack>
 *
 * // Horizontal stack with center alignment
 * <Stack direction="horizontal" spacing="gap-6" align="items-center">
 *   <button>Button 1</button>
 *   <button>Button 2</button>
 * </Stack>
 *
 * // Stack with dividers
 * <Stack divider={<hr className="border-grey-200" />}>
 *   <div>Section 1</div>
 *   <div>Section 2</div>
 * </Stack>
 * ```
 */
export const Stack = forwardRef<HTMLElement, StackProps>(
  (
    {
      as = 'div',
      direction = 'vertical',
      spacing = 'gap-4',
      gap,
      align,
      alignItems,
      justify,
      justifyContent,
      wrap,
      flexWrap,
      mt,
      mb,
      divider,
      fullWidth = false,
      fullHeight = false,
      className,
      children,
      ...rest
    },
    ref
  ) => {
    const Component = as;

    const isVertical = direction === 'vertical' || direction === 'column';
    const gapClass = spacingClass('gap', gap ?? spacing);

    const classes = cn(
      'flex',
      isVertical ? 'flex-col' : 'flex-row',
      gapClass,
      mapAlignment(align ?? alignItems),
      mapJustification(justify ?? justifyContent),
      mapWrap(flexWrap ?? wrap),
      spacingClass('mt', mt),
      spacingClass('mb', mb),
      fullWidth && 'w-full',
      fullHeight && 'h-full',
      className
    );

    // If divider is provided, insert it between children
    const childrenArray = React.Children.toArray(children);
    const childrenWithDividers = divider
      ? childrenArray.reduce<React.ReactNode[]>((acc, child, index) => {
          if (index > 0) {
            acc.push(
              <React.Fragment key={`divider-${index}`}>
                {divider}
              </React.Fragment>
            );
          }
          acc.push(child);
          return acc;
        }, [])
      : children;

    return (
      <Component ref={ref as unknown} className={classes} {...rest}>
        {childrenWithDividers}
      </Component>
    );
  }
);

Stack.displayName = 'Stack';
