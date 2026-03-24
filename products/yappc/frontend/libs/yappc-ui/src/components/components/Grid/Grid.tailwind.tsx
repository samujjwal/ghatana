 /**
 * Grid Component (Tailwind CSS)
 *
 * A layout component that arranges children in a CSS Grid layout
 * with responsive column support.
 */

import React, { forwardRef } from 'react';

import { cn } from '../../utils/cn';

import type { ElementType } from 'react';

/**
 *
 */
export interface GridProps extends React.HTMLAttributes<HTMLElement> {
  /**
   * The HTML element to render
   * @default 'div'
   */
  as?: ElementType;

  /**
   * Number of columns (Tailwind grid-cols classes)
   * Examples: 'grid-cols-1', 'grid-cols-2', 'grid-cols-3', 'grid-cols-12'
   * Can also be an object for responsive: { xs: 'grid-cols-1', md: 'md:grid-cols-3' }
   */
  cols?: string;

  /**
   * Number of rows (Tailwind grid-rows classes)
   * Examples: 'grid-rows-1', 'grid-rows-2', 'grid-rows-3'
   */
  rows?: string;

  /**
   * Gap between items (Tailwind gap classes)
   * Examples: 'gap-2', 'gap-4', 'gap-6'
   * @default 'gap-4'
   */
  gap?: string;

  /**
   * Gap between columns (Tailwind gap-x classes)
   */
  gapX?: string;

  /**
   * Gap between rows (Tailwind gap-y classes)
   */
  gapY?: string;

  /**
   * Auto-fit columns (creates responsive grid that auto-fits items)
   * When true, uses auto-fit with minmax
   */
  autoFit?: boolean;

  /**
   * Minimum column width for auto-fit (Tailwind size)
   * @default '250px'
   */
  minColWidth?: string;

  /**
   * Alignment on cross axis
   * Examples: 'items-start', 'items-center', 'items-end', 'items-stretch'
   */
  align?: string;

  /**
   * Justification of items
   * Examples: 'justify-items-start', 'justify-items-center', 'justify-items-end', 'justify-items-stretch'
   */
  justifyItems?: string;

  /**
   * Alignment of grid content
   * Examples: 'content-start', 'content-center', 'content-end', 'content-between'
   */
  alignContent?: string;

  /**
   * Justification of grid content
   * Examples: 'justify-start', 'justify-center', 'justify-end', 'justify-between'
   */
  justifyContent?: string;

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

/**
 * Grid - A layout component for CSS Grid layouts
 *
 * @example
 * ```tsx
 * // Simple 3-column grid
 * <Grid cols="grid-cols-3" gap="gap-4">
 *   <div>Item 1</div>
 *   <div>Item 2</div>
 *   <div>Item 3</div>
 * </Grid>
 *
 * // Responsive grid
 * <Grid cols="grid-cols-1 md:grid-cols-2 lg:grid-cols-4" gap="gap-6">
 *   <Card>Card 1</Card>
 *   <Card>Card 2</Card>
 *   <Card>Card 3</Card>
 *   <Card>Card 4</Card>
 * </Grid>
 *
 * // Auto-fit grid (responsive without breakpoints)
 * <Grid autoFit minColWidth="300px" gap="gap-4">
 *   <div>Item 1</div>
 *   <div>Item 2</div>
 *   <div>Item 3</div>
 * </Grid>
 * ```
 */
export const Grid = forwardRef<HTMLElement, GridProps>(
  (
    {
      as = 'div',
      cols,
      rows,
      gap = 'gap-4',
      gapX,
      gapY,
      autoFit = false,
      minColWidth = '250px',
      align,
      justifyItems,
      alignContent,
      justifyContent,
      fullWidth = false,
      fullHeight = false,
      className,
      children,
      style,
      ...rest
    },
    ref
  ) => {
    const Component = as as ElementType;

    const classes = cn(
      'grid',
      !autoFit && cols,
      rows,
      gap,
      gapX,
      gapY,
      align,
      justifyItems,
      alignContent,
      justifyContent,
      fullWidth && 'w-full',
      fullHeight && 'h-full',
      className
    );

    const gridStyle = autoFit
      ? {
          ...style,
          gridTemplateColumns: `repeat(auto-fit, minmax(${minColWidth}, 1fr))`,
        }
      : style;

    // Filter out problematic props that shouldn't be passed to DOM elements
    const filteredRest = Object.entries(rest).reduce<Record<string, unknown>>(
      (acc, [key, value]) => {
        // Filter out Material-UI specific attributes that shouldn't reach the DOM
        // and boolean values for non-standard HTML attributes
        if (
          key === 'item' ||
          key === 'container' ||
          key === 'xs' ||
          key === 'sm' ||
          key === 'md' ||
          key === 'lg' ||
          key === 'xl'
        ) {
          // Skip these attributes entirely or convert boolean to string
          if (typeof value === 'boolean') {
            acc[key] = value.toString();
          } else if (value !== undefined && value !== null) {
            acc[key] = value;
          }
        } else {
          // Keep other props
          acc[key] = value;
        }
        return acc;
      },
      {}
    );

    return (
      <Component ref={ref as unknown} className={classes} style={gridStyle} {...filteredRest}>
        {children}
      </Component>
    );
  }
);

Grid.displayName = 'Grid';
