/**
 * Card Component (Tailwind CSS)
 * 
 * A structured surface component with header, content, and actions sections.
 * Provides a consistent card layout pattern commonly used in UI design.
 */

import { forwardRef } from 'react';

import { cn } from '../../utils/cn';
import { Box, type BoxProps } from '../Box';

/**
 *
 */
export interface CardProps extends BoxProps {
  /**
   * Card variant
   * @default 'elevation'
   */
  variant?: 'elevation' | 'outlined';
  
  /**
   * Shadow elevation (only applies to elevation variant)
   * @default 1
   */
  elevation?: 0 | 1 | 2 | 3 | 4 | 6 | 8 | 12 | 16 | 24;

  /**
   * Apply hover elevation and lift effect
   * @default false
   */
  hover?: boolean;

  /**
   * Mark card as interactive (adds focus styles and keyboard semantics)
   * @default false
   */
  interactive?: boolean;
}

/**
 *
 */
export interface CardHeaderProps extends Omit<BoxProps, 'title'> {
  /**
   * Card title
   */
  title?: React.ReactNode;
  
  /**
   * Card subtitle
   */
  subheader?: React.ReactNode;
  
  /**
   * Action element (usually icon buttons)
   */
  action?: React.ReactNode;
  
  /**
   * Avatar element
   */
  avatar?: React.ReactNode;
}

/**
 *
 */
export interface CardContentProps extends BoxProps {}

/**
 *
 */
export interface CardActionsProps extends BoxProps {
  /**
   * Disable spacing between actions
   * @default false
   */
  disableSpacing?: boolean;
}

/**
 *
 */
export interface CardMediaProps extends BoxProps {
  /**
   * Image source
   */
  image?: string;
  
  /**
   * Alt text for image
   */
  alt?: string;
  
  /**
   * Component to render (defaults to 'img' if image provided, otherwise 'div')
   */
  component?: React.ElementType;
  
  /**
   * Height of media
   */
  height?: string;
}

/**
 * Map elevation to Tailwind shadow classes
 */
const elevationToShadow: Record<number, string> = {
  0: 'shadow-none',
  1: 'shadow-sm',
  2: 'shadow',
  3: 'shadow-md',
  4: 'shadow-md',
  6: 'shadow-lg',
  8: 'shadow-lg',
  12: 'shadow-xl',
  16: 'shadow-2xl',
  24: 'shadow-2xl',
};

export const Card = forwardRef<HTMLDivElement, CardProps>(
  (
    {
      variant = 'elevation',
      elevation = 1,
      className,
      bg = 'bg-white',
      rounded = 'rounded-lg',
      border,
      shadow,
      hover = false,
      interactive = false,
      ...props
    },
    ref
  ) => {
    const interactiveProps = interactive
      ? ({ role: 'button', tabIndex: 0 } as const)
      : null;

    return (
      <Box
        ref={ref}
        bg={bg}
        rounded={rounded}
        shadow={variant === 'elevation' ? (shadow || elevationToShadow[elevation]) : shadow}
        border={variant === 'outlined' ? (border || 'border border-grey-300') : border}
        className={cn(
          'overflow-hidden',
          hover && 'transition-transform duration-200 hover:-translate-y-0.5 hover:shadow-lg',
          interactive && 'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500 focus-visible:ring-offset-2',
          className
        )}
        {...interactiveProps}
        {...props}
      />
    );
  }
);

export const CardHeader = forwardRef<HTMLDivElement, CardHeaderProps>(
  (
    {
      title,
      subheader,
      action,
      avatar,
      className,
      p = 'p-4',
      ...props
    },
    ref
  ) => {
    return (
      <Box
        ref={ref}
        p={p}
        className={cn('flex items-start gap-3', className)}
        {...props}
      >
        {avatar && <div className="flex-shrink-0">{avatar}</div>}
        
        <div className="flex-grow min-w-0">
          {title && (
            <div className="text-base font-semibold text-grey-900">
              {title}
            </div>
          )}
          {subheader && (
            <div className="text-sm text-grey-600 mt-1">
              {subheader}
            </div>
          )}
        </div>
        
        {action && <div className="flex-shrink-0">{action}</div>}
      </Box>
    );
  }
);

export const CardContent = forwardRef<HTMLDivElement, CardContentProps>(
  (
    {
      className,
      p = 'p-4',
      ...props
    },
    ref
  ) => {
    return (
      <Box
        ref={ref}
        p={p}
        className={className}
        {...props}
      />
    );
  }
);

export const CardActions = forwardRef<HTMLDivElement, CardActionsProps>(
  (
    {
      disableSpacing = false,
      className,
      p = 'p-4',
      ...props
    },
    ref
  ) => {
    return (
      <Box
        ref={ref}
        p={p}
        className={cn(
          'flex items-center',
          !disableSpacing && 'gap-2',
          className
        )}
        {...props}
      />
    );
  }
);

export const CardMedia = forwardRef<HTMLDivElement, CardMediaProps>(
  (
    {
      image,
      alt = '',
      component,
      height = 'h-48',
      className,
      ...props
    },
    ref
  ) => {
    const Component = component || (image ? 'img' : 'div');
    
    if (Component === 'img' && image) {
      return (
        <img
          ref={ref as unknown}
          src={image}
          alt={alt}
          className={cn('w-full object-cover', height, className)}
          {...(props as unknown)}
        />
      );
    }
    
    return (
      <Box
        ref={ref}
        as={Component}
        className={cn('w-full bg-grey-200', height, className)}
        {...props}
      />
    );
  }
);
