import React from 'react';
import type { BadgeVariant } from '@ghatana/dcmaar-shared-ui-core';

export interface BadgeProps {
  label: string;
  variant?: BadgeVariant;
  size?: 'sm' | 'md';
  icon?: React.ReactNode;
  className?: string;
}

export const Badge: React.FC<BadgeProps> = ({
    label,
    variant = 'primary',
    size = 'md',
    icon,
    className = '',
}) => {
    const variantClasses = {
        primary: 'badge-primary',
        success: 'badge-success',
        warning: 'badge-warning',
        error: 'badge-error',
        info: 'bg-blue-100 text-blue-700',
    };

    const sizeClasses = {
        sm: 'px-2 py-1 text-xs',
        md: 'px-3 py-1 text-sm',
    };

    return (
        <span className={`${variantClasses[variant]} ${sizeClasses[size]} inline-flex items-center gap-1 rounded-full font-medium ${className}`}>
            {icon && <span>{icon}</span>}
            {label}
        </span>
    );
};
