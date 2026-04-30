/**
 * EmptyState — consistent empty state component.
 *
 * @doc.type component
 * @doc.purpose Empty state display component
 * @doc.layer frontend
 */
/* eslint-disable ghatana/prefer-design-system-primitives */
import React from 'react';

interface EmptyStateProps {
  title: string;
  description?: string;
  action?: {
    label: string;
    onClick: () => void;
  };
  icon?: React.ReactNode;
}

export const EmptyState: React.FC<EmptyStateProps> = ({
  title,
  description,
  action,
  icon,
}) => {
  return (
    <div className="flex flex-col items-center justify-center h-40 gap-2 text-center">
      {icon && <div className="text-gray-300 dark:text-gray-600 mb-1">{icon}</div>}
      <p className="text-sm text-gray-500 dark:text-gray-400 font-medium">{title}</p>
      {description && <p className="text-xs text-gray-400 dark:text-gray-500 max-w-sm">{description}</p>}
      {action && (
        <button
          type="button"
          onClick={action.onClick}
          className="mt-1 text-xs text-indigo-600 dark:text-indigo-400 hover:underline font-medium"
        >
          {action.label}
        </button>
      )}
    </div>
  );
};
