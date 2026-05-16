import React from 'react';
import type { HTMLAttributes, ReactNode } from 'react';

type BadgeVariant = 'default' | 'secondary' | 'destructive' | 'outline';

interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  children: ReactNode;
  variant?: BadgeVariant;
}

const variantClasses: Record<BadgeVariant, string> = {
  default: 'bg-blue-600 text-white border-transparent',
  secondary: 'bg-zinc-200 text-zinc-900 border-transparent',
  destructive: 'bg-red-600 text-white border-transparent',
  outline: 'bg-transparent text-zinc-900 border-zinc-300',
};

export const Badge: React.FC<BadgeProps> = ({ children, variant = 'default', className = '', ...props }) => {
  const classes = [
    'inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-medium',
    variantClasses[variant],
    className,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <span className={classes} {...props}>
      {children}
    </span>
  );
};

export default Badge;
