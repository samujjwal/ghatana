/**
 * Empty State Component
 * 
 * Displays a placeholder when no data is available.
 * Uses centralized theme styles for consistency.
 * 
 * @doc.type component
 * @doc.purpose Display empty state with optional action
 * @doc.layer frontend
 * @doc.pattern Presentational Component
 */

import React from 'react';
import { cn, textStyles } from '../../lib/theme';

interface EmptyStateProps {
  /** Title text */
  title: string;
  /** Description text */
  description: string;
  /** Optional icon element */
  icon?: React.ReactNode;
  /** Optional action element (e.g., button) */
  action?: React.ReactNode;
  /** Additional CSS classes */
  className?: string;
}

/**
 * Empty state component for displaying when no data is available.
 * 
 * @example
 * ```tsx
 * <EmptyState 
 *   title="No collections" 
 *   description="Get started by creating a new collection."
 *   action={<Button>Create Collection</Button>}
 * />
 * ```
 */
export const EmptyState: React.FC<EmptyStateProps> = ({
  title,
  description,
  icon,
  action,
  className = ''
}) => (
  <div className={cn('text-center py-12', className)}>
    {icon && <div className="mx-auto h-12 w-12 text-gray-400 dark:text-gray-500">{icon}</div>}
    <h3 className={cn('mt-2', textStyles.h4)}>{title}</h3>
    <p className={cn('mt-1', textStyles.muted)}>{description}</p>
    {action && <div className="mt-6">{action}</div>}
  </div>
);

export default EmptyState;
