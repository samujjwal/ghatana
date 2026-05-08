/**
 * Typography Component (Tailwind CSS)
 *
 * A component for rendering text with consistent styling using semantic HTML
 * and Tailwind CSS classes. Replaces MUI Typography with native HTML elements.
 */

import React, { forwardRef } from 'react';
import type { ElementType } from 'react';

import { cn } from '../../utils/cn';

export type TypographyVariant =
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

type SpacingValue = string | number;

/**
 *
 */
export interface TypographyProps extends React.HTMLAttributes<HTMLElement> {
  /**
   * Typography variant determines the HTML element and default styling
   * @default 'body1'
   */
  variant?: TypographyVariant;

  /**
   * Override the default HTML element
   */
  component?: ElementType;

  /**
   * Component alias used by newer package-local primitives.
   */
  as?: ElementType;

  /**
   * Text alignment
   */
  align?: 'left' | 'center' | 'right' | 'justify';

  /**
   * Text color (from theme palette)
   */
  color?:
    | 'primary'
    | 'secondary'
    | 'error'
    | 'warning'
    | 'info'
    | 'success'
    | 'text'
    | 'primary.main'
    | 'secondary.main'
    | 'success.main'
    | 'error.main'
    | 'warning.main'
    | 'text.primary'
    | 'text.secondary'
    | 'text.disabled'
    | 'muted'
    | 'inherit';

  /**
   * Tone alias used by newer package-local primitives.
   */
  tone?: TypographyProps['color'];

  /**
   * Font weight convenience prop.
   */
  fontWeight?: string | number;

  /**
   * Display convenience prop.
   */
  display?: string;

  /**
   * Margin-top convenience alias.
   */
  mt?: SpacingValue;

  /**
   * Margin-bottom convenience alias.
   */
  mb?: SpacingValue;

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
  'primary.main': 'text-primary-500',
  secondary: 'text-secondary-500',
  'secondary.main': 'text-secondary-500',
  'success.main': 'text-success-500',
  'error.main': 'text-error-500',
  'warning.main': 'text-warning-500',
  error: 'text-error-500',
  warning: 'text-warning-500',
  info: 'text-info-500',
  success: 'text-success-500',
  text: 'text-grey-900',
  'text.primary': 'text-grey-900',
  'text.secondary': 'text-grey-600',
  'text.disabled': 'text-grey-400',
  muted: 'text-grey-600',
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

function spacingClass(prefix: string, value?: SpacingValue): string | undefined {
  if (value === undefined) return undefined;
  if (typeof value === 'number') return `${prefix}-${value}`;
  if (value.startsWith(`${prefix}-`)) return value;
  return `${prefix}-${value}`;
}

export const Typography = forwardRef<HTMLElement, TypographyProps>(
  (
    {
      variant = 'body1',
      component,
      as,
      align,
      color,
      tone,
      fontWeight,
      display,
      mt,
      mb,
      noWrap = false,
      gutterBottom = false,
      className,
      style,
      children,
      ...props
    },
    ref
  ) => {
    // Determine the element to render
    const Component = component || as || variantToElement[variant] || 'p';
    const resolvedColor = tone ?? color;

    return (
      <Component
        ref={ref as unknown}
        className={cn(
          variantClasses[variant],
          align && alignClasses[align],
          resolvedColor && colorClasses[resolvedColor],
          noWrap && 'whitespace-nowrap overflow-hidden text-ellipsis',
          gutterBottom && 'mb-2',
          display,
          spacingClass('mt', mt),
          spacingClass('mb', mb),
          className
        )}
        style={{
          ...style,
          ...(fontWeight !== undefined ? { fontWeight } : {}),
        }}
        {...props}
      >
        {children}
      </Component>
    );
  }
);

Typography.displayName = 'Typography';

export type TextProps = TypographyProps;
export const Text = Typography;

export type HeadingProps = TypographyProps;
export const Heading = Typography;

export type LinkProps = TypographyProps &
  React.AnchorHTMLAttributes<HTMLAnchorElement>;
export const Link = forwardRef<HTMLAnchorElement, LinkProps>(
  ({ as = 'a', ...props }, ref) => (
    <Typography
      ref={ref as unknown as React.Ref<HTMLElement>}
      as={as}
      className={cn('text-primary-600 underline-offset-2 hover:underline', props.className)}
      {...props}
    />
  )
);
Link.displayName = 'Link';

export type ListProps = TypographyProps;
export const List = Typography;

export type CodeProps = TypographyProps;
export const Code = forwardRef<HTMLElement, CodeProps>((props, ref) => (
  <Typography
    ref={ref}
    as="code"
    className={cn('rounded bg-grey-100 px-1.5 py-0.5 font-mono text-sm', props.className)}
    {...props}
  />
));
Code.displayName = 'Code';
