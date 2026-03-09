/**
 * EmptyState Component
 * 
 * Consistent empty states across all views.
 * Provides helpful messaging and actions when no data is available.
 * 
 * @doc.type component
 * @doc.purpose Empty state UI
 * @doc.layer product
 * @doc.pattern Component
 */

import React from 'react';

interface EmptyStateProps {
  icon?: React.ReactNode;
  title: string;
  description?: string;
  action?: React.ReactNode;
  secondaryAction?: React.ReactNode;
  variant?: 'default' | 'compact' | 'large';
  className?: string;
}

export function EmptyState({
  icon,
  title,
  description,
  action,
  secondaryAction,
  variant = 'default',
  className = '',
}: EmptyStateProps) {
  const variantStyles = {
    compact: {
      container: 'py-6 px-4',
      icon: 'w-10 h-10 mb-3',
      title: 'text-sm',
      description: 'text-xs',
    },
    default: {
      container: 'py-12 px-6',
      icon: 'w-16 h-16 mb-4',
      title: 'text-lg',
      description: 'text-sm',
    },
    large: {
      container: 'py-20 px-8',
      icon: 'w-24 h-24 mb-6',
      title: 'text-xl',
      description: 'text-base',
    },
  };

  const styles = variantStyles[variant];

  return (
    <div className={`flex flex-col items-center justify-center text-center ${styles.container} ${className}`}>
      {/* Icon */}
      {icon && (
        <div className={`${styles.icon} text-grey-400 dark:text-grey-500 flex items-center justify-center`}>
          {icon}
        </div>
      )}

      {/* Title */}
      <h3 className={`font-semibold text-text-primary ${styles.title}`}>
        {title}
      </h3>

      {/* Description */}
      {description && (
        <p className={`mt-1 text-text-secondary max-w-sm ${styles.description}`}>
          {description}
        </p>
      )}

      {/* Actions */}
      {(action || secondaryAction) && (
        <div className="mt-4 flex flex-col sm:flex-row items-center gap-3">
          {action}
          {secondaryAction}
        </div>
      )}
    </div>
  );
}

/**
 * Pre-built empty state variants
 */

export function NoProjectsEmptyState({ onCreateProject }: { onCreateProject?: () => void }) {
  return (
    <EmptyState
      icon={
        <svg className="w-full h-full" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z" />
        </svg>
      }
      title="No projects yet"
      description="Create your first project to get started building with AI."
      action={
        onCreateProject && (
          <button
            onClick={onCreateProject}
            className="px-4 py-2 bg-primary-600 text-white rounded-lg font-medium hover:bg-primary-700 transition-colors"
          >
            Create Project
          </button>
        )
      }
    />
  );
}

export function NoTasksEmptyState({ onCreateTask }: { onCreateTask?: () => void }) {
  return (
    <EmptyState
      icon={
        <svg className="w-full h-full" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4" />
        </svg>
      }
      title="No tasks"
      description="All caught up! Create a new task or let AI suggest what to work on next."
      action={
        onCreateTask && (
          <button
            onClick={onCreateTask}
            className="px-4 py-2 bg-primary-600 text-white rounded-lg font-medium hover:bg-primary-700 transition-colors"
          >
            Create Task
          </button>
        )
      }
    />
  );
}

export function NoSearchResultsEmptyState({ query, onClear }: { query: string; onClear?: () => void }) {
  return (
    <EmptyState
      icon={
        <svg className="w-full h-full" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
        </svg>
      }
      title="No results found"
      description={`We couldn't find anything matching "${query}". Try a different search term.`}
      action={
        onClear && (
          <button
            onClick={onClear}
            className="px-4 py-2 border border-divider text-text-primary rounded-lg font-medium hover:bg-grey-50 dark:hover:bg-grey-800 transition-colors"
          >
            Clear Search
          </button>
        )
      }
    />
  );
}

export function NoNotificationsEmptyState() {
  return (
    <EmptyState
      variant="compact"
      icon={
        <svg className="w-full h-full" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
        </svg>
      }
      title="All caught up!"
      description="No new notifications"
    />
  );
}

export function NoCanvasElementsEmptyState({ onAddComponent }: { onAddComponent?: () => void }) {
  return (
    <EmptyState
      icon={
        <svg className="w-full h-full" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M4 5a1 1 0 011-1h14a1 1 0 011 1v2a1 1 0 01-1 1H5a1 1 0 01-1-1V5zM4 13a1 1 0 011-1h6a1 1 0 011 1v6a1 1 0 01-1 1H5a1 1 0 01-1-1v-6zM16 13a1 1 0 011-1h2a1 1 0 011 1v6a1 1 0 01-1 1h-2a1 1 0 01-1-1v-6z" />
        </svg>
      }
      title="Empty canvas"
      description="Start building by adding components from the palette or describing what you want to create."
      action={
        onAddComponent && (
          <button
            onClick={onAddComponent}
            className="px-4 py-2 bg-primary-600 text-white rounded-lg font-medium hover:bg-primary-700 transition-colors"
          >
            Add Component
          </button>
        )
      }
    />
  );
}

export function ErrorEmptyState({ 
  title = 'Something went wrong',
  message,
  onRetry,
}: { 
  title?: string;
  message?: string;
  onRetry?: () => void;
}) {
  return (
    <EmptyState
      icon={
        <svg className="w-full h-full text-red-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
        </svg>
      }
      title={title}
      description={message || 'An unexpected error occurred. Please try again.'}
      action={
        onRetry && (
          <button
            onClick={onRetry}
            className="px-4 py-2 bg-primary-600 text-white rounded-lg font-medium hover:bg-primary-700 transition-colors"
          >
            Try Again
          </button>
        )
      }
    />
  );
}

export default EmptyState;
