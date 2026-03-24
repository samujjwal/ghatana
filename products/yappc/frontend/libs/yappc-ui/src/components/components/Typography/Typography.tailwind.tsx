/**
 * Typography Component (Tailwind CSS)
 * 
 * A component for rendering text with consistent styling using semantic HTML
 * and Tailwind CSS classes. Replaces MUI Typography with native HTML elements.
 */

import React, { forwardRef } from 'react';

import { cn } from '../../utils/cn';

import type { ElementType } from 'react';

/**
 *
 */
export interface TypographyProps extends React.HTMLAttributes<HTMLElement> {
  /**
   * Typography variant determines the HTML element and default styling
   * @default 'body1'
   */
  variant?: 
    | 'h1' 
    | 'h2' 
    | 'h3' 
    | 'h4' 
    | 'h5' 
    | 'h6' 
    | 'subtitle1' 
    | 'subtitle2' 
    | 'body1' 
    | 'body2' 
    | 'caption' 
    | 'overline'
    | 'button';
  
  /**
   * Override the default HTML element
   */
  component?: ElementType;
  
  /**
   * Text alignment
   */
  align?: 'left' | 'center' | 'right' | 'justify';
  
  /**
   * Text color (from theme palette)
   */
  color?: 'primary' | 'secondary' | 'error' | 'warning' | 'info' | 'success' | 'text' | 'inherit';
  
  /**
   * Prevent text wrapping
   * @default false
   */
  noWrap?: boolean;
  
  /**
   * Show ellipsis for overflowing text
   * @default false
   */
  gutterBottom?: boolean;
}

/**
 * Map variants to default HTML elements
 */
const variantToElement: Record<string, ElementType> = {
  h1: 'h1',
  h2: 'h2',
  h3: 'h3',
  h4: 'h4',
  h5: 'h5',
  h6: 'h6',
  subtitle1: 'h6',
  subtitle2: 'h6',
  body1: 'p',
  body2: 'p',
  caption: 'span',
  overline: 'span',
  button: 'span',
};

/**
 * Map variants to Tailwind classes
 */
const variantClasses: Record<string, string> = {
  h1: 'text-6xl font-bold leading-tight tracking-tight',
  h2: 'text-5xl font-bold leading-tight tracking-tight',
  h3: 'text-4xl font-semibold leading-snug tracking-tight',
  h4: 'text-3xl font-semibold leading-snug',
  h5: 'text-2xl font-medium leading-snug',
  h6: 'text-xl font-medium leading-normal',
  subtitle1: 'text-base font-medium leading-relaxed',
  subtitle2: 'text-sm font-medium leading-relaxed',
  body1: 'text-base leading-relaxed',
  body2: 'text-sm leading-relaxed',
  caption: 'text-xs leading-normal text-grey-600',
  overline: 'text-xs font-medium uppercase tracking-wider leading-normal',
  button: 'text-sm font-medium uppercase tracking-wide',
};

/**
 * Map color prop to Tailwind classes
 */
const colorClasses: Record<string, string> = {
  primary: 'text-primary-500',
  secondary: 'text-secondary-500',
  error: 'text-error-500',
  warning: 'text-warning-500',
  info: 'text-info-500',
  success: 'text-success-500',
  text: 'text-grey-900',
  inherit: 'text-inherit',
};

/**
 * Map align prop to Tailwind classes
 */
const alignClasses: Record<string, string> = {
  left: 'text-left',
  center: 'text-center',
  right: 'text-right',
  justify: 'text-justify',
};

export const Typography = forwardRef<HTMLElement, TypographyProps>(
  (
    {
      variant = 'body1',
      component,
      align,
      color,
      noWrap = false,
      gutterBottom = false,
      className,
      children,
      ...props
    },
    ref
  ) => {
    // Determine the element to render
    const Component = (component || variantToElement[variant] || 'p') as ElementType;
    
    return (
      <Component
        ref={ref as unknown}
        className={cn(
          variantClasses[variant],
          align && alignClasses[align],
          color && colorClasses[color],
          noWrap && 'whitespace-nowrap overflow-hidden text-ellipsis',
          gutterBottom && 'mb-2',
          className
        )}
        {...props}
      >
        {children}
      </Component>
    );
  }
);
