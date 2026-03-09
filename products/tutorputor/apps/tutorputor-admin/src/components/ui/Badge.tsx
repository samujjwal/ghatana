import React from 'react';
import { cn } from '@/lib/utils';

type BadgeVariant = 'default' | 'secondary' | 'success' | 'destructive' | 'outline';

export interface BadgeProps extends React.HTMLAttributes<HTMLSpanElement> {
    variant?: BadgeVariant;
    children: React.ReactNode;
}

/**
 * Tailwind-based Badge component for TutorPutor
 */
export const Badge = React.forwardRef<HTMLSpanElement, BadgeProps>(
    ({ className, variant = 'default', children, ...props }, ref) => {
        return (
            <span
                ref={ref}
                className={cn(
                    'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold transition-colors',
                    {
                        'bg-primary-100 text-primary-800 dark:bg-primary-900 dark:text-primary-200':
                            variant === 'default',
                        'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-200':
                            variant === 'secondary',
                        'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200':
                            variant === 'success',
                        'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200':
                            variant === 'destructive',
                        'border border-gray-200 dark:border-gray-700 text-gray-700 dark:text-gray-300':
                            variant === 'outline',
                    },
                    className
                )}
                {...props}
            >
                {children}
            </span>
        );
    }
);

Badge.displayName = 'Badge';
