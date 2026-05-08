/**
 * Box Component (Tailwind CSS)
 *
 * A flexible container component that serves as a building block for layouts.
 * Supports polymorphic rendering (can be rendered as any HTML element).
 */

import React, { forwardRef } from 'react';
import type { ElementType } from 'react';

import { cn } from '../../utils/cn';

type SpacingValue = string | number;
type FlexValue = string | number | boolean;

/**
 *
 */
export interface BoxProps
  extends Omit<React.HTMLAttributes<HTMLElement>, 'color'> {
  /**
   * The HTML element to render
   * @default 'div'
   */
  as?: ElementType;

  /**
   * MUI-compatible element alias.
   */
  component?: ElementType;

  /**
   * Padding (theme spacing units or Tailwind classes)
   * Examples: 'p-4', 'px-6 py-4', 'p-md'
   */
  p?: SpacingValue;

  /**
   * Padding X-axis
   */
  px?: SpacingValue;

  /**
   * Padding Y-axis
   */
  py?: SpacingValue;

  /**
   * Padding top
   */
  pt?: SpacingValue;

  /**
   * Padding right
   */
  pr?: SpacingValue;

  /**
   * Padding bottom
   */
  pb?: SpacingValue;

  /**
   * Padding left
   */
  pl?: SpacingValue;

  /**
   * Margin (theme spacing units or Tailwind classes)
   */
  m?: SpacingValue;

  /**
   * Margin X-axis
   */
  mx?: SpacingValue;

  /**
   * Margin Y-axis
   */
  my?: SpacingValue;

  /**
   * Margin top
   */
  mt?: SpacingValue;

  /**
   * Margin right
   */
  mr?: SpacingValue;

  /**
   * Margin bottom
   */
  mb?: SpacingValue;

  /**
   * Margin left
   */
  ml?: SpacingValue;

  /**
   * Background color (Tailwind classes)
   * Examples: 'bg-white', 'bg-primary-500', 'bg-grey-100'
   */
  bg?: string;

  /**
   * Text color (Tailwind classes)
   */
  color?: string;

  /**
   * Border radius (Tailwind classes)
   * Examples: 'rounded', 'rounded-md', 'rounded-lg', 'rounded-full'
   */
  rounded?: string;

  /**
   * Border (Tailwind classes)
   * Examples: 'border', 'border-2', 'border-grey-300'
   */
  border?: string;

  /**
   * Shadow (Tailwind classes)
   * Examples: 'shadow', 'shadow-md', 'shadow-lg'
   */
  shadow?: string;

  /**
   * Display mode (Tailwind classes)
   * Examples: 'block', 'flex', 'grid', 'inline-block'
   */
  display?: string;

  /**
   * MUI-compatible layout aliases.
   */
  alignItems?: string;
  justifyContent?: string;
  gap?: SpacingValue;
  flexWrap?: string;
  flexDirection?: string;
  flex?: FlexValue;

  /**
   * Width (Tailwind classes)
   * Examples: 'w-full', 'w-1/2', 'w-64'
   */
  w?: string;

  /**
   * Height (Tailwind classes)
   * Examples: 'h-full', 'h-64', 'h-screen'
   */
  h?: string;

  /**
   * Max width (Tailwind classes)
   */
  maxW?: string;

  /**
   * Max height (Tailwind classes)
   */
  maxH?: string;

  /**
   * Min width (Tailwind classes)
   */
  minW?: string;

  /**
   * Min height (Tailwind classes)
   */
  minH?: string;

  /**
   * Overflow (Tailwind classes)
   * Examples: 'overflow-hidden', 'overflow-auto', 'overflow-scroll'
   */
  overflow?: string;

  /**
   * Position (Tailwind classes)
   * Examples: 'relative', 'absolute', 'fixed', 'sticky'
   */
  position?: string;

  /**
   * Top position (Tailwind classes)
   */
  top?: string;

  /**
   * Right position (Tailwind classes)
   */
  right?: string;

  /**
   * Bottom position (Tailwind classes)
   */
  bottom?: string;

  /**
   * Left position (Tailwind classes)
   */
  left?: string;

  /**
   * Z-index (Tailwind classes)
   */
  zIndex?: string;

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
 * Box - A flexible container component
 *
 * @example
 * ```tsx
 * // Simple box with padding and background
 * <Box p="p-4" bg="bg-white" rounded="rounded-lg">
 *   Content
 * </Box>
 *
 * // Box as a different element
 * <Box as="section" p="p-6" shadow="shadow-md">
 *   Section content
 * </Box>
 *
 * // Flex container
 * <Box display="flex" className="items-center gap-4">
 *   <div>Item 1</div>
 *   <div>Item 2</div>
 * </Box>
 * ```
 */
export const Box = forwardRef<HTMLElement, BoxProps>(
  (
    {
      as = 'div',
      component,
      p,
      px,
      py,
      pt,
      pr,
      pb,
      pl,
      m,
      mx,
      my,
      mt,
      mr,
      mb,
      ml,
      bg,
      color,
      rounded,
      border,
      shadow,
      display,
      alignItems,
      justifyContent,
      gap,
      flexWrap,
      flexDirection,
      flex,
      w,
      h,
      maxW,
      maxH,
      minW,
      minH,
      overflow,
      position,
      top,
      right,
      bottom,
      left,
      zIndex,
      className,
      children,
      ...rest
    },
    ref
  ) => {
    const Component = component || as;

    const spacingClass = (
      prefix: string,
      value?: SpacingValue
    ): string | undefined => {
      if (value === undefined) return undefined;
      if (typeof value === 'number') return `${prefix}-${value}`;
      if (value.startsWith(`${prefix}-`)) return value;
      return `${prefix}-${value}`;
    };

    const flexClass = (prefix: string, value?: string): string | undefined => {
      if (!value) return undefined;
      if (value.startsWith(`${prefix}-`)) return value;
      return `${prefix}-${value}`;
    };

    const classes = cn(
      // Padding
      spacingClass('p', p),
      spacingClass('px', px),
      spacingClass('py', py),
      spacingClass('pt', pt),
      spacingClass('pr', pr),
      spacingClass('pb', pb),
      spacingClass('pl', pl),
      // Margin
      spacingClass('m', m),
      spacingClass('mx', mx),
      spacingClass('my', my),
      spacingClass('mt', mt),
      spacingClass('mr', mr),
      spacingClass('mb', mb),
      spacingClass('ml', ml),
      // Colors
      bg,
      color,
      // Border & Shadow
      rounded,
      border,
      shadow,
      // Layout
      display,
      flexClass('items', alignItems),
      flexClass('justify', justifyContent),
      spacingClass('gap', gap),
      flexClass('flex', flexWrap),
      flexClass('flex', flexDirection),
      typeof flex === 'string' ? flexClass('flex', flex) : flex === 1 ? 'flex-1' : undefined,
      w,
      h,
      maxW,
      maxH,
      minW,
      minH,
      overflow,
      // Positioning
      position,
      top,
      right,
      bottom,
      left,
      zIndex,
      // Additional classes
      className
    );

    return (
      <Component ref={ref as unknown} className={classes} {...rest}>
        {children}
      </Component>
    );
  }
);

Box.displayName = 'Box';
