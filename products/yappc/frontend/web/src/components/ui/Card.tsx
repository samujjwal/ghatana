import React, { forwardRef } from 'react';
import type { HTMLAttributes, ReactNode } from 'react';

type CardVariant = 'default' | 'elevated' | 'outlined' | 'filled';
type CardSize = 'sm' | 'md' | 'lg';
type CardState = 'default' | 'hover' | 'pressed' | 'disabled';

interface CardProps extends HTMLAttributes<HTMLDivElement> {
  children: ReactNode;
  variant?: CardVariant;
  size?: CardSize;
  state?: CardState;
  header?: ReactNode;
  footer?: ReactNode;
  clickable?: boolean;
  fullWidth?: boolean;
  padding?: boolean;
}

export const Card = forwardRef<HTMLDivElement, CardProps>(
  (
    {
      children,
      variant = 'default',
      size = 'md',
      state = 'default',
      header,
      footer,
      clickable = false,
      fullWidth = false,
      padding = true,
      className = '',
      ...props
    },
    ref
  ) => {
    const variantClasses = {
      default: ['bg-surface', 'border', 'border-surface'],
      elevated: [
        'bg-surface-elevated',
        'border',
        'border-surface-elevated',
        'shadow-sm',
        'hover:shadow-md',
        'transition-shadow',
        'duration-200',
      ],
      outlined: ['bg-surface', 'border-2', 'border-primary'],
      filled: ['bg-surface-subtle', 'border', 'border-transparent'],
    };

    const sizeClasses = {
      sm: padding ? ['p-4'] : [],
      md: padding ? ['p-6'] : [],
      lg: padding ? ['p-8'] : [],
    };

    const stateClasses = {
      default: [],
      hover: clickable
        ? ['hover:shadow-md', 'hover:border-primary-light', 'transition-all', 'duration-200']
        : [],
      pressed: clickable
        ? ['active:scale-[0.98]', 'active:shadow-sm', 'transition-transform', 'duration-100']
        : [],
      disabled: ['opacity-50', 'cursor-not-allowed'],
    };

    const interactiveClasses = clickable
      ? ['cursor-pointer', 'focus:outline-none', 'focus:ring-2', 'focus:ring-primary', 'focus:ring-offset-2', 'rounded-lg']
      : ['rounded-lg'];

    const baseClasses = ['relative', 'overflow-hidden', 'transition-all', 'duration-200'];

    const cardClasses = [
      ...baseClasses,
      ...variantClasses[variant],
      ...sizeClasses[size],
      ...stateClasses[state],
      ...interactiveClasses,
      fullWidth ? 'w-full' : '',
      className,
    ]
      .filter(Boolean)
      .join(' ');

    return (
      <div
        ref={ref}
        className={cardClasses}
        role={clickable ? 'button' : undefined}
        tabIndex={clickable ? 0 : undefined}
        onKeyDown={
          clickable
            ? (e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault();
                  e.currentTarget.click();
                }
              }
            : undefined
        }
        {...props}
      >
        {header && <div className="card-header">{header}</div>}
        <div className="card-body">{children}</div>
        {footer && <div className="card-footer">{footer}</div>}
      </div>
    );
  }
);

Card.displayName = 'Card';

interface CardSectionProps extends HTMLAttributes<HTMLDivElement> {
  children: ReactNode;
}

export const CardHeader = forwardRef<HTMLDivElement, CardSectionProps>(
  ({ children, className = '', ...props }, ref) => (
    <div ref={ref} className={`mb-4 ${className}`.trim()} {...props}>
      {children}
    </div>
  )
);

CardHeader.displayName = 'CardHeader';

export const CardContent = forwardRef<HTMLDivElement, CardSectionProps>(
  ({ children, className = '', ...props }, ref) => (
    <div ref={ref} className={className} {...props}>
      {children}
    </div>
  )
);

CardContent.displayName = 'CardContent';

interface CardTitleProps extends HTMLAttributes<HTMLHeadingElement> {
  children: ReactNode;
}

export const CardTitle = forwardRef<HTMLHeadingElement, CardTitleProps>(
  ({ children, className = '', ...props }, ref) => (
    <h3 ref={ref} className={`text-lg font-semibold ${className}`.trim()} {...props}>
      {children}
    </h3>
  )
);

CardTitle.displayName = 'CardTitle';

export default Card;
