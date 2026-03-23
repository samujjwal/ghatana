import * as React from 'react';

export interface IconProps extends React.HTMLAttributes<HTMLSpanElement> {
  /** Icon name or children (pass an SVG or icon font class) */
  children?: React.ReactNode;
  /** Font size / icon size @default 'inherit' */
  fontSize?: 'inherit' | 'small' | 'medium' | 'large' | string;
  /** Color @default 'inherit' */
  color?: 'inherit' | 'primary' | 'secondary' | 'error' | 'warning' | 'info' | 'success' | 'disabled';
  /** Additional CSS classes */
  className?: string;
}

const fontSizeMap: Record<string, string> = {
  inherit: 'text-[inherit]',
  small: 'text-lg',
  medium: 'text-2xl',
  large: 'text-4xl',
};

const colorMap: Record<string, string> = {
  inherit: 'text-inherit',
  primary: 'text-blue-600 dark:text-blue-400',
  secondary: 'text-purple-600 dark:text-purple-400',
  error: 'text-red-600 dark:text-red-400',
  warning: 'text-amber-600 dark:text-amber-400',
  info: 'text-cyan-600 dark:text-cyan-400',
  success: 'text-green-600 dark:text-green-400',
  disabled: 'text-neutral-400 dark:text-neutral-500',
};

/**
 * Generic Icon wrapper — renders children (typically an SVG or icon font glyph) 
 * with consistent sizing and color. Drop-in replacement for MUI Icon.
 */
export const Icon = React.forwardRef<HTMLSpanElement, IconProps>(
  ({ children, fontSize = 'inherit', color = 'inherit', className, ...rest }, ref) => {
    const sizeClass = fontSizeMap[fontSize] ?? `text-[${fontSize}]`;
    const colorClass = colorMap[color] ?? 'text-inherit';

    return (
      <span
        ref={ref}
        aria-hidden="true"
        className={`inline-flex shrink-0 items-center justify-center ${sizeClass} ${colorClass} ${className ?? ''}`}
        {...rest}
      >
        {children}
      </span>
    );
  },
);

Icon.displayName = 'Icon';
