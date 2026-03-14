import React from 'react';
import { clsx } from 'clsx';

export interface EmptyStateProps {
    /**
     * Icon or illustration to display
     */
    icon?: React.ReactNode;
    /**
     * Primary heading text
     */
    title: string;
    /**
     * Descriptive text
     */
    description?: string;
    /**
     * Primary action button
     */
    action?: React.ReactNode;
    /**
     * Secondary action button
     */
    secondaryAction?: React.ReactNode;
    /**
     * Visual size variant
     * @default 'md'
     */
    size?: 'sm' | 'md' | 'lg';
    /**
     * Additional CSS classes
     */
    className?: string;
}

/**
 * EmptyState component for displaying empty or no-data states.
 *
 * @example
 * ```tsx
 * <EmptyState
 *   icon={<FolderIcon className="h-12 w-12 text-gray-400" />}
 *   title="No projects yet"
 *   description="Get started by creating your first project"
 *   action={<Button onClick={handleCreate}>Create Project</Button>}
 * />
 * ```
 */
export const EmptyState: React.FC<EmptyStateProps> = ({
    icon,
    title,
    description,
    action,
    secondaryAction,
    size = 'md',
    className,
}) => {
    const sizeClasses = {
        sm: {
            container: 'py-8',
            icon: 'mb-3',
            title: 'text-base',
            description: 'text-sm',
            actions: 'gap-2 mt-4',
        },
        md: {
            container: 'py-12',
            icon: 'mb-4',
            title: 'text-lg',
            description: 'text-base',
            actions: 'gap-3 mt-6',
        },
        lg: {
            container: 'py-16',
            icon: 'mb-6',
            title: 'text-xl',
            description: 'text-lg',
            actions: 'gap-4 mt-8',
        },
    };

    return (
        <div
            className={clsx(
                'flex flex-col items-center justify-center text-center',
                sizeClasses[size].container,
                className
            )}
            role="status"
            aria-label={title}
        >
            {icon && (
                <div className={clsx('text-gray-400', sizeClasses[size].icon)}>
                    {icon}
                </div>
            )}
            <h3
                className={clsx(
                    'font-semibold text-gray-900',
                    sizeClasses[size].title
                )}
            >
                {title}
            </h3>
            {description && (
                <p
                    className={clsx(
                        'mt-2 text-gray-500 max-w-md',
                        sizeClasses[size].description
                    )}
                >
                    {description}
                </p>
            )}
            {(action || secondaryAction) && (
                <div
                    className={clsx(
                        'flex items-center',
                        sizeClasses[size].actions
                    )}
                >
                    {action}
                    {secondaryAction}
                </div>
            )}
        </div>
    );
};

EmptyState.displayName = 'EmptyState';
