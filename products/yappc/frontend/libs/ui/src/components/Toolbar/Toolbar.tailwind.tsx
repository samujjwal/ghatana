import React from 'react';

import { cn } from '../../utils/cn';

/**
 * Justification options for Toolbar content alignment
 */
export type ToolbarJustify = 'start' | 'center' | 'end' | 'between' | 'around' | 'evenly';

/**
 * Gap size between Toolbar items
 */
export type ToolbarGap = 'none' | 'xs' | 'sm' | 'md' | 'lg' | 'xl';

/**
 * Padding variant for Toolbar
 */
export type ToolbarPadding = 'none' | 'sm' | 'md' | 'lg';

/**
 * Props for the Toolbar component
 */
export interface ToolbarProps extends React.HTMLAttributes<HTMLDivElement> {
  /**
   * The content to display inside the Toolbar
   */
  children?: React.ReactNode;

  /**
   * Content justification (flex justify-content)
   * @default 'start'
   */
  justify?: ToolbarJustify;

  /**
   * Gap size between items
   * @default 'md'
   */
  gap?: ToolbarGap;

  /**
   * Padding around Toolbar content
   * @default 'md'
   */
  padding?: ToolbarPadding;

  /**
   * Whether to center items vertically
   * @default true
   */
  centerItems?: boolean;

  /**
   * Whether toolbar should take full width
   * @default true
   */
  fullWidth?: boolean;

  /**
   * HTML element to render as
   * @default 'div'
   */
  component?: 'div' | 'nav' | 'section';

  /**
   * Additional CSS classes
   */
  className?: string;
}

/**
 * Justify class mappings
 */
const justifyClasses: Record<ToolbarJustify, string> = {
  start: 'justify-start',
  center: 'justify-center',
  end: 'justify-end',
  between: 'justify-between',
  around: 'justify-around',
  evenly: 'justify-evenly',
};

/**
 * Gap class mappings
 */
const gapClasses: Record<ToolbarGap, string> = {
  none: 'gap-0',
  xs: 'gap-1',
  sm: 'gap-2',
  md: 'gap-4',
  lg: 'gap-6',
  xl: 'gap-8',
};

/**
 * Padding class mappings
 */
const paddingClasses: Record<ToolbarPadding, string> = {
  none: 'p-0',
  sm: 'px-4 py-2',
  md: 'px-6 py-3',
  lg: 'px-8 py-4',
};

/**
 * Toolbar - Flex container component for AppBar content and actions
 * 
 * A simple layout component that provides consistent spacing and alignment
 * for AppBar content. Commonly used as a child of AppBar.
 * 
 * @example
 * ```tsx
 * <AppBar>
 *   <Toolbar justify="between">
 *     <Logo />
 *     <Navigation />
 *   </Toolbar>
 * </AppBar>
 * ```
 * 
 * @example
 * ```tsx
 * <Toolbar justify="center" gap="lg">
 *   <Button>Action 1</Button>
 *   <Button>Action 2</Button>
 *   <Button>Action 3</Button>
 * </Toolbar>
 * ```
 */
export const Toolbar = React.forwardRef<HTMLDivElement, ToolbarProps>(
  (
    {
      children,
      justify = 'start',
      gap = 'md',
      padding = 'md',
      centerItems = true,
      fullWidth = true,
      component = 'div',
      className,
      ...props
    },
    ref
  ) => {
    const Component = component as unknown as React.ElementType;

    return (
      <Component
        ref={ref}
        className={cn(
          // Base flex container
          'flex',

          // Width
          fullWidth && 'w-full',

          // Vertical alignment
          centerItems && 'items-center',

          // Horizontal alignment
          justifyClasses[justify],

          // Gap
          gapClasses[gap],

          // Padding
          paddingClasses[padding],

          // Custom className
          className
        )}
        {...props}
      >
        {children}
      </Component>
    );
  }
);

Toolbar.displayName = 'Toolbar';
