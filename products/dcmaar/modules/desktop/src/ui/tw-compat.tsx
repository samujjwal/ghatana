/**
 * @fileoverview Tailwind-based replacements for commonly used MUI components.
 *
 * This module provides drop-in replacements for the MUI components most
 * frequently used in the DCMAAR desktop module. Import from this file
 * instead of '@mui/material' to complete the migration incrementally.
 *
 * Migration path:
 * 1. Replace `import { Box } from '@mui/material'` with `import { Box } from '../ui/tw-compat'`
 * 2. Component API is intentionally similar to MUI for easy migration
 * 3. Once all files are migrated, remove @mui/material from package.json
 */
import * as React from 'react';
import { cn } from '@ghatana/utils';

// ── Box ──────────────────────────────────────────────────────────────────

export interface BoxProps extends React.HTMLAttributes<HTMLDivElement> {
  component?: React.ElementType;
  sx?: Record<string, unknown>; // Ignored — use className instead
  display?: string;
  flexDirection?: string;
  alignItems?: string;
  justifyContent?: string;
  gap?: number | string;
  p?: number | string;
  m?: number | string;
}

export const Box = React.forwardRef<HTMLDivElement, BoxProps>(
  ({ component: Component = 'div', className, children, sx: _sx, ...rest }, ref) => {
    return React.createElement(Component, { ref, className, ...rest }, children);
  }
);
Box.displayName = 'Box';

// ── Typography ───────────────────────────────────────────────────────────

const VARIANT_CLASSES: Record<string, string> = {
  h1: 'text-4xl font-bold tracking-tight',
  h2: 'text-3xl font-bold tracking-tight',
  h3: 'text-2xl font-semibold',
  h4: 'text-xl font-semibold',
  h5: 'text-lg font-medium',
  h6: 'text-base font-medium',
  subtitle1: 'text-base font-medium text-gray-600 dark:text-gray-400',
  subtitle2: 'text-sm font-medium text-gray-600 dark:text-gray-400',
  body1: 'text-base',
  body2: 'text-sm text-gray-600 dark:text-gray-400',
  caption: 'text-xs text-gray-500 dark:text-gray-500',
  overline: 'text-xs uppercase tracking-widest font-medium text-gray-500',
};

const COLOR_CLASSES: Record<string, string> = {
  primary: 'text-primary-600 dark:text-primary-400',
  secondary: 'text-secondary-600 dark:text-secondary-400',
  error: 'text-red-600 dark:text-red-400',
  warning: 'text-amber-600 dark:text-amber-400',
  info: 'text-blue-600 dark:text-blue-400',
  success: 'text-green-600 dark:text-green-400',
  'text.primary': 'text-gray-900 dark:text-gray-100',
  'text.secondary': 'text-gray-600 dark:text-gray-400',
  'text.disabled': 'text-gray-400 dark:text-gray-600',
};

export interface TypographyProps extends React.HTMLAttributes<HTMLElement> {
  variant?: keyof typeof VARIANT_CLASSES;
  color?: string;
  component?: React.ElementType;
  noWrap?: boolean;
  gutterBottom?: boolean;
  align?: 'left' | 'center' | 'right' | 'justify';
}

export const Typography = React.forwardRef<HTMLElement, TypographyProps>(
  ({ variant = 'body1', color, component, className, noWrap, gutterBottom, align, ...rest }, ref) => {
    const tag = component || (variant.startsWith('h') ? variant : 'p');
    return React.createElement(tag, {
      ref,
      className: cn(
        VARIANT_CLASSES[variant],
        color && COLOR_CLASSES[color],
        noWrap && 'truncate',
        gutterBottom && 'mb-2',
        align && `text-${align}`,
        className,
      ),
      ...rest,
    });
  }
);
Typography.displayName = 'Typography';

// ── Stack ────────────────────────────────────────────────────────────────

export interface StackProps extends React.HTMLAttributes<HTMLDivElement> {
  direction?: 'row' | 'column' | 'row-reverse' | 'column-reverse';
  spacing?: number;
  alignItems?: string;
  justifyContent?: string;
  divider?: React.ReactNode;
}

export const Stack = React.forwardRef<HTMLDivElement, StackProps>(
  ({ direction = 'column', spacing = 0, className, children, divider, ...rest }, ref) => {
    const gapClass = spacing > 0 ? `gap-${spacing}` : '';
    const dirClass = direction === 'row' ? 'flex-row' : direction === 'column' ? 'flex-col' : `flex-${direction}`;
    return (
      <div ref={ref} className={cn('flex', dirClass, gapClass, className)} {...rest}>
        {divider ? interleaveChildren(children, divider) : children}
      </div>
    );
  }
);
Stack.displayName = 'Stack';

function interleaveChildren(children: React.ReactNode, divider: React.ReactNode) {
  const arr = React.Children.toArray(children).filter(Boolean);
  return arr.flatMap((child, i) => (i < arr.length - 1 ? [child, React.cloneElement(divider as React.ReactElement, { key: `divider-${i}` })] : [child]));
}

// ── Chip ─────────────────────────────────────────────────────────────────

const CHIP_COLORS: Record<string, string> = {
  default: 'bg-gray-100 text-gray-800 dark:bg-gray-800 dark:text-gray-200',
  primary: 'bg-primary-100 text-primary-800 dark:bg-primary-900 dark:text-primary-200',
  secondary: 'bg-secondary-100 text-secondary-800 dark:bg-secondary-900 dark:text-secondary-200',
  error: 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200',
  warning: 'bg-amber-100 text-amber-800 dark:bg-amber-900 dark:text-amber-200',
  info: 'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200',
  success: 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200',
};

export interface ChipProps {
  label: React.ReactNode;
  color?: keyof typeof CHIP_COLORS;
  size?: 'small' | 'medium';
  variant?: 'filled' | 'outlined';
  onDelete?: () => void;
  icon?: React.ReactNode;
  className?: string;
}

export function Chip({ label, color = 'default', size = 'medium', variant = 'filled', onDelete, icon, className }: ChipProps) {
  const sizeClass = size === 'small' ? 'text-xs px-2 py-0.5' : 'text-sm px-3 py-1';
  const variantClass = variant === 'outlined'
    ? 'border border-current bg-transparent'
    : CHIP_COLORS[color];
  return (
    <span className={cn('inline-flex items-center rounded-full font-medium', sizeClass, variantClass, className)}>
      {icon && <span className="mr-1">{icon}</span>}
      {label}
      {onDelete && (
        <button onClick={onDelete} className="ml-1 hover:opacity-75" aria-label="Remove">×</button>
      )}
    </span>
  );
}

// ── Card ─────────────────────────────────────────────────────────────────

export interface CardProps extends React.HTMLAttributes<HTMLDivElement> {
  elevation?: number;
  variant?: 'elevation' | 'outlined';
}

export const Card = React.forwardRef<HTMLDivElement, CardProps>(
  ({ variant = 'elevation', className, children, ...rest }, ref) => (
    <div
      ref={ref}
      className={cn(
        'rounded-lg bg-white dark:bg-gray-900',
        variant === 'outlined' ? 'border border-gray-200 dark:border-gray-700' : 'shadow-card',
        className,
      )}
      {...rest}
    >
      {children}
    </div>
  )
);
Card.displayName = 'Card';

export function CardContent({ className, children, ...rest }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('p-4', className)} {...rest}>{children}</div>;
}

export function CardHeader({ className, children, ...rest }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('px-4 pt-4 pb-2', className)} {...rest}>{children}</div>;
}

// ── CircularProgress ─────────────────────────────────────────────────────

export interface CircularProgressProps {
  size?: number;
  className?: string;
  color?: string;
}

export function CircularProgress({ size = 40, className }: CircularProgressProps) {
  return (
    <svg
      className={cn('animate-spin text-primary-600 dark:text-primary-400', className)}
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
    >
      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
    </svg>
  );
}

// ── LinearProgress ───────────────────────────────────────────────────────

export interface LinearProgressProps {
  value?: number;
  variant?: 'determinate' | 'indeterminate';
  className?: string;
  color?: string;
}

export function LinearProgress({ value, variant = 'indeterminate', className }: LinearProgressProps) {
  return (
    <div className={cn('h-1 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700', className)}>
      <div
        className={cn(
          'h-full rounded-full bg-primary-600 dark:bg-primary-400 transition-all',
          variant === 'indeterminate' && 'animate-[progress_1.5s_ease-in-out_infinite] w-1/3',
        )}
        style={variant === 'determinate' ? { width: `${value ?? 0}%` } : undefined}
      />
    </div>
  );
}

// ── Divider ──────────────────────────────────────────────────────────────

export interface DividerProps {
  orientation?: 'horizontal' | 'vertical';
  className?: string;
}

export function Divider({ orientation = 'horizontal', className }: DividerProps) {
  return orientation === 'horizontal'
    ? <hr className={cn('border-gray-200 dark:border-gray-700', className)} />
    : <div className={cn('w-px bg-gray-200 dark:bg-gray-700 self-stretch', className)} />;
}

// ── Button (re-export from platform UI) ──────────────────────────────────

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'contained' | 'outlined' | 'text';
  color?: 'primary' | 'secondary' | 'error' | 'inherit';
  size?: 'small' | 'medium' | 'large';
  startIcon?: React.ReactNode;
  endIcon?: React.ReactNode;
  fullWidth?: boolean;
}

const BTN_SIZE: Record<string, string> = {
  small: 'px-3 py-1.5 text-sm',
  medium: 'px-4 py-2 text-sm',
  large: 'px-6 py-3 text-base',
};

export function Button({
  variant = 'contained', color = 'primary', size = 'medium',
  startIcon, endIcon, fullWidth, className, children, disabled, ...rest
}: ButtonProps) {
  const base = 'inline-flex items-center justify-center font-medium rounded-lg transition-colors focus:outline-none focus:ring-2 focus:ring-offset-2';
  const variantClass = variant === 'contained'
    ? 'bg-primary-600 text-white hover:bg-primary-700 focus:ring-primary-500'
    : variant === 'outlined'
    ? 'border border-primary-600 text-primary-600 hover:bg-primary-50 focus:ring-primary-500'
    : 'text-primary-600 hover:bg-primary-50 focus:ring-primary-500';
  return (
    <button
      className={cn(base, variantClass, BTN_SIZE[size], fullWidth && 'w-full', disabled && 'opacity-50 cursor-not-allowed', className)}
      disabled={disabled}
      {...rest}
    >
      {startIcon && <span className="mr-2">{startIcon}</span>}
      {children}
      {endIcon && <span className="ml-2">{endIcon}</span>}
    </button>
  );
}

// ── IconButton ───────────────────────────────────────────────────────────

export interface IconButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  size?: 'small' | 'medium' | 'large';
  color?: string;
}

export function IconButton({ size = 'medium', className, children, ...rest }: IconButtonProps) {
  const sizeClass = size === 'small' ? 'p-1' : size === 'large' ? 'p-3' : 'p-2';
  return (
    <button
      className={cn('rounded-full hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors', sizeClass, className)}
      {...rest}
    >
      {children}
    </button>
  );
}
