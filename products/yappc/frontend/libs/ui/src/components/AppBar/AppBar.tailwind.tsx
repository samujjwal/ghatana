import React from 'react';

import { cn } from '../../utils/cn';

/**
 * Position variant for AppBar component
 */
export type AppBarPosition = 'static' | 'fixed' | 'sticky' | 'absolute' | 'relative';

/**
 * Color variant for AppBar component
 */
export type AppBarColor = 'default' | 'primary' | 'secondary' | 'transparent' | 'inherit';

/**
 * Elevation level for AppBar shadow
 */
export type AppBarElevation = 0 | 1 | 2 | 3 | 4 | 6 | 8 | 12 | 16 | 24;

/**
 * Props for the AppBar component
 */
export interface AppBarProps extends React.HTMLAttributes<HTMLDivElement> {
  /**
   * The content to display inside the AppBar
   */
  children?: React.ReactNode;

  /**
   * CSS positioning behavior
   * @default 'static'
   */
  position?: AppBarPosition;

  /**
   * Color variant
   * @default 'default'
   */
  color?: AppBarColor;

  /**
   * Elevation (shadow depth)
   * @default 4
   */
  elevation?: AppBarElevation;

  /**
   * Custom height (if not using default)
   */
  height?: 'compact' | 'normal' | 'tall';

  /**
   * Enable blur backdrop effect (useful with transparent color)
   * @default false
   */
  blur?: boolean;

  /**
   * HTML element to render as
   * @default 'header'
   */
  component?: 'header' | 'nav' | 'div';

  /**
   * Additional CSS classes
   */
  className?: string;
}

/**
 * Position class mappings
 */
const positionClasses: Record<AppBarPosition, string> = {
  static: 'static',
  fixed: 'fixed top-0 left-0 right-0 z-appbar',
  sticky: 'sticky top-0 z-appbar',
  absolute: 'absolute top-0 left-0 right-0 z-appbar',
  relative: 'relative',
};

/**
 * Color class mappings
 */
const colorClasses: Record<AppBarColor, string> = {
  default: 'bg-white dark:bg-grey-900 text-grey-900 dark:text-white',
  primary: 'bg-primary-500 text-white',
  secondary: 'bg-secondary-500 text-white',
  transparent: 'bg-transparent text-inherit',
  inherit: 'bg-inherit text-inherit',
};

/**
 * Elevation (shadow) class mappings
 */
const elevationClasses: Record<AppBarElevation, string> = {
  0: 'shadow-none',
  1: 'shadow-sm',
  2: 'shadow',
  3: 'shadow-md',
  4: 'shadow-lg',
  6: 'shadow-xl',
  8: 'shadow-2xl',
  12: 'shadow-2xl',
  16: 'shadow-2xl',
  24: 'shadow-2xl',
};

/**
 * Height class mappings
 */
const heightClasses = {
  compact: 'h-12',
  normal: 'h-16',
  tall: 'h-20',
};

/**
 * AppBar - Header bar component for navigation and branding
 * 
 * Simple Tailwind component for top-level application bars. Commonly used with
 * Toolbar component for layout and spacing.
 * 
 * @example
 * ```tsx
 * <AppBar position="sticky" color="primary" elevation={4}>
 *   <Toolbar>
 *     <h1>My App</h1>
 *   </Toolbar>
 * </AppBar>
 * ```
 * 
 * @example
 * ```tsx
 * <AppBar position="fixed" color="transparent" blur>
 *   <Toolbar className="justify-between">
 *     <Logo />
 *     <Navigation />
 *   </Toolbar>
 * </AppBar>
 * ```
 */
export const AppBar = React.forwardRef<
  HTMLDivElement,
  AppBarProps
>(
  (
    {
      children,
      position = 'static',
      color = 'default',
      elevation = 4,
      height = 'normal',
      blur = false,
      component = 'header',
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
          // Base styles
          'w-full flex items-center',
          
          // Position
          positionClasses[position],
          
          // Color
          colorClasses[color],
          
          // Elevation (shadow)
          elevationClasses[elevation],
          
          // Height
          heightClasses[height],
          
          // Blur backdrop
          blur && 'backdrop-blur-md',
          
          // Transitions
          'transition-shadow duration-200',
          
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

AppBar.displayName = 'AppBar';
