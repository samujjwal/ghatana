/**
 * Container Component (Tailwind CSS)
 * 
 * A responsive container component with max-width constraints
 * that centers content horizontally.
 */

import React, { forwardRef } from 'react';

import { cn } from '../../utils/cn';

import type { ElementType } from 'react';

/**
 *
 */
export interface ContainerProps extends React.HTMLAttributes<HTMLElement> {
  /**
   * The HTML element to render
   * @default 'div'
   */
  as?: ElementType;
  
  /**
   * Maximum width size
   * @default 'lg'
   */
  maxWidth?: 'xs' | 'sm' | 'md' | 'lg' | 'xl' | '2xl' | '3xl' | '4xl' | '5xl' | '6xl' | '7xl' | 'full' | false;
  
  /**
   * Center the container horizontally
   * @default true
   */
  centered?: boolean;
  
  /**
   * Add horizontal padding
   * @default true
   */
  padding?: boolean;
  
  /**
   * Custom padding class
   * Only used if padding is true
   * @default 'px-4 sm:px-6 lg:px-8'
   */
  paddingClass?: string;
  
  /**
   * Disable responsive max-width behavior
   * When true, uses fixed max-width at all breakpoints
   * @default false
   */
  fixed?: boolean;
  
  /**
   * Additional CSS classes
   */
  className?: string;
  
  /**
   * Children elements
   */
  children?: React.ReactNode;
}

const maxWidthClasses = {
  xs: 'max-w-xs',      // 320px
  sm: 'max-w-sm',      // 384px
  md: 'max-w-md',      // 448px
  lg: 'max-w-lg',      // 512px
  xl: 'max-w-xl',      // 576px
  '2xl': 'max-w-2xl',  // 672px
  '3xl': 'max-w-3xl',  // 768px
  '4xl': 'max-w-4xl',  // 896px
  '5xl': 'max-w-5xl',  // 1024px
  '6xl': 'max-w-6xl',  // 1152px
  '7xl': 'max-w-7xl',  // 1280px
  full: 'max-w-full',  // 100%
};

/**
 * Container - A responsive container component
 * 
 * @example
 * ```tsx
 * // Default container (max-w-lg, centered, with padding)
 * <Container>
 *   <h1>Welcome</h1>
 *   <p>Content goes here</p>
 * </Container>
 * 
 * // Large container
 * <Container maxWidth="2xl">
 *   <Article />
 * </Container>
 * 
 * // Full-width container without padding
 * <Container maxWidth="full" padding={false}>
 *   <FullWidthImage />
 * </Container>
 * 
 * // Container as section element
 * <Container as="section" maxWidth="4xl">
 *   <Hero />
 * </Container>
 * ```
 */
export const Container = forwardRef<HTMLElement, ContainerProps>(
  (
    {
      as = 'div',
      maxWidth = 'lg',
      centered = true,
      padding = true,
      paddingClass = 'px-4 sm:px-6 lg:px-8',
      fixed = false,
      className,
      children,
      ...rest
    },
    ref
  ) => {
    const Component = as as ElementType;
    
    const classes = cn(
      'w-full',
      maxWidth !== false && maxWidthClasses[maxWidth],
      centered && 'mx-auto',
      padding && paddingClass,
      className
    );

    return (
      <Component ref={ref as unknown} className={classes} {...rest}>
        {children}
      </Component>
    );
  }
);

Container.displayName = 'Container';
