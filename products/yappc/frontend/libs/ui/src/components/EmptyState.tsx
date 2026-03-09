/**
 * Empty State Component
 * 
 * Displays empty state with icon, message, and optional action.
 * 
 * @module ui/components
 */

import React from 'react';

export interface EmptyStateProps {
  /** Icon to display */
  icon?: React.ReactNode;
  
  /** Title */
  title: string;
  
  /** Description */
  description?: string;
  
  /** Action button */
  action?: {
    label: string;
    onClick: () => void;
  };
  
  /** Additional CSS classes */
  className?: string;
}

/**
 * Empty State Component
 * 
 * @example
 * ```tsx
 * <EmptyState
 *   icon={<InboxIcon />}
 *   title="No messages"
 *   description="You don't have any messages yet."
 *   action={{
 *     label: "Send a message",
 *     onClick: () => openComposer(),
 *   }}
 * />
 * ```
 */
export const EmptyState: React.FC<EmptyStateProps> = ({
  icon,
  title,
  description,
  action,
  className = '',
}) => {
  return (
    <div className={`flex flex-col items-center justify-center py-12 px-4 text-center ${className}`}>
      {icon && (
        <div className="w-16 h-16 rounded-full bg-zinc-800 flex items-center justify-center mb-4 text-zinc-400">
          {icon}
        </div>
      )}
      
      <h3 className="text-lg font-semibold text-white mb-2">
        {title}
      </h3>
      
      {description && (
        <p className="text-sm text-zinc-400 max-w-md mb-6">
          {description}
        </p>
      )}
      
      {action && (
        <button
          onClick={action.onClick}
          className="px-4 py-2 bg-violet-600 text-white rounded-lg hover:bg-violet-700 transition-colors"
        >
          {action.label}
        </button>
      )}
    </div>
  );
};

export default EmptyState;
