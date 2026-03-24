import { forwardRef } from 'react';

import { cn } from '../../utils/cn';

import type { HTMLAttributes } from 'react';

/**
 * Divider orientation variants
 */
export type DividerOrientation = 'horizontal' | 'vertical';

/**
 * Divider variant types
 */
export type DividerVariant = 'fullWidth' | 'inset' | 'middle';

/**
 * Divider text alignment options
 */
export type DividerTextAlign = 'left' | 'center' | 'right';

/**
 * Props for the Divider component
 */
export interface DividerProps extends HTMLAttributes<HTMLHRElement> {
  /**
   * Orientation of the divider
   * @default 'horizontal'
   */
  orientation?: DividerOrientation;
  
  /**
   * Width/spacing variant
   * @default 'fullWidth'
   */
  variant?: DividerVariant;
  
  /**
   * Text or content to display within the divider
   */
  children?: React.ReactNode;
  
  /**
   * Alignment of text within divider
   * @default 'center'
   */
  textAlign?: DividerTextAlign;
  
  /**
   * Additional CSS classes
   */
  className?: string;
}

/**
 * Divider component for visually separating content sections.
 * 
 * Features:
 * - Two orientations: horizontal (default) and vertical
 * - Three width variants: fullWidth, inset, middle
 * - Optional text/content within divider
 * - Text alignment options: left, center, right
 * - Semantic HTML using hr element
 * - Design token colors for consistency
 * 
 * @example
 * ```tsx
 * // Simple horizontal divider
 * <Divider />
 * 
 * // Divider with text
 * <Divider>OR</Divider>
 * 
 * // Vertical divider
 * <div className="flex items-center h-12">
 *   <span>Left content</span>
 *   <Divider orientation="vertical" />
 *   <span>Right content</span>
 * </div>
 * 
 * // Inset divider (reduced width)
 * <Divider variant="inset" />
 * ```
 */
export const Divider = forwardRef<HTMLHRElement, DividerProps>(
  (
    {
      orientation = 'horizontal',
      variant = 'fullWidth',
      children,
      textAlign = 'center',
      className,
      ...props
    },
    ref
  ) => {
    const isVertical = orientation === 'vertical';
    const hasText = Boolean(children);

    // Variant margin classes
    const variantClasses: Record<DividerVariant, string> = {
      fullWidth: '',
      inset: isVertical ? 'my-2' : 'mx-4',     // 8px/16px inset
      middle: isVertical ? 'my-4' : 'mx-8',    // 16px/32px middle spacing
    };

    // Text alignment classes
    const textAlignClasses: Record<DividerTextAlign, string> = {
      left: 'justify-start',
      center: 'justify-center',
      right: 'justify-end',
    };

    // Simple divider without text
    if (!hasText) {
      return (
        <hr
          ref={ref}
          className={cn(
            'border-0 shrink-0',
            isVertical 
              ? 'border-l border-l-grey-300 w-px h-full'
              : 'border-t border-t-grey-300 h-px w-full',
            variantClasses[variant],
            className
          )}
          {...props}
        />
      );
    }

    // Divider with text content
    return (
      <div
        className={cn(
          'flex items-center',
          isVertical ? 'flex-col h-full w-auto' : 'flex-row w-full h-auto',
          isVertical ? textAlignClasses.center : textAlignClasses[textAlign],
          variantClasses[variant],
          className
        )}
      >
        {/* Leading line */}
        {(textAlign === 'center' || textAlign === 'right') && (
          <hr
            className={cn(
              'border-0 flex-1 shrink-0',
              isVertical 
                ? 'border-l border-l-grey-300 w-px h-full'
                : 'border-t border-t-grey-300 h-px w-full'
            )}
          />
        )}
        
        {/* Text content */}
        <span
          className={cn(
            'text-sm text-grey-600 whitespace-nowrap',
            isVertical ? 'py-2' : 'px-2'
          )}
        >
          {children}
        </span>
        
        {/* Trailing line */}
        {(textAlign === 'center' || textAlign === 'left') && (
          <hr
            className={cn(
              'border-0 flex-1 shrink-0',
              isVertical 
                ? 'border-l border-l-grey-300 w-px h-full'
                : 'border-t border-t-grey-300 h-px w-full'
            )}
          />
        )}
      </div>
    );
  }
);

Divider.displayName = 'Divider';
