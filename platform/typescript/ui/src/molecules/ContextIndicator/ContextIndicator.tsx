import React from 'react';
import { CheckCircle, Clock, AlertTriangle } from 'lucide-react';

export interface ContextIndicatorProps {
  /** Title of the current context */
  title: string;
  /** Subtitle or description */
  subtitle?: string;
  /** Status of the current work */
  status?: 'saved' | 'unsaved' | 'saving' | 'error';
  /** Type icon to display */
  icon?: React.ReactNode;
  /** Additional metadata to show */
  metadata?: Array<{ label: string; value: string }>;
  /** Actions/buttons to show */
  actions?: React.ReactNode;
  /** Custom class name */
  className?: string;
}

/**
 * Context Indicator Component
 * 
 * Shows the current editing context with status, metadata, and actions.
 * Provides persistent visibility of what the user is working on.
 * 
 * @doc.type component
 * @doc.purpose Show current editing context and work status
 * @doc.layer core
 * @doc.pattern Status Indicator
 * 
 * @example
 * ```tsx
 * <ContextIndicator
 *   title="Physics: Newton's Laws"
 *   subtitle="Learning Experience"
 *   status="saved"
 *   metadata={[
 *     { label: 'Domain', value: 'Physics' },
 *     { label: 'Grade', value: '9-12' }
 *   ]}
 *   actions={
 *     <button>Preview</button>
 *   }
 * />
 * ```
 */
export function ContextIndicator({
  title,
  subtitle,
  status = 'saved',
  icon,
  metadata = [],
  actions,
  className = '',
}: ContextIndicatorProps) {
  const statusConfig = {
    saved: {
      icon: <CheckCircle className="w-4 h-4 text-green-500" />,
      text: 'All changes saved',
      color: 'text-green-600 dark:text-green-400',
    },
    unsaved: {
      icon: <AlertTriangle className="w-4 h-4 text-yellow-500" />,
      text: 'Unsaved changes',
      color: 'text-yellow-600 dark:text-yellow-400',
    },
    saving: {
      icon: <Clock className="w-4 h-4 text-blue-500 animate-spin" />,
      text: 'Saving...',
      color: 'text-blue-600 dark:text-blue-400',
    },
    error: {
      icon: <AlertTriangle className="w-4 h-4 text-red-500" />,
      text: 'Failed to save',
      color: 'text-red-600 dark:text-red-400',
    },
  };

  const currentStatus = statusConfig[status];

  return (
    <div
      className={`bg-white dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700 px-6 py-3 ${className}`}
    >
      <div className="flex items-center justify-between">
        {/* Left: Context Info */}
        <div className="flex items-center gap-4">
          {/* Icon */}
          {icon && (
            <div className="flex-shrink-0 text-gray-500 dark:text-gray-400">
              {icon}
            </div>
          )}

          {/* Title & Subtitle */}
          <div>
            <h2 className="text-lg font-semibold text-gray-900 dark:text-white">
              {title}
            </h2>
            {subtitle && (
              <p className="text-sm text-gray-600 dark:text-gray-400">
                {subtitle}
              </p>
            )}
          </div>

          {/* Metadata Pills */}
          {metadata.length > 0 && (
            <div className="flex items-center gap-2 ml-4">
              {metadata.map((item, index) => (
                <div
                  key={index}
                  className="flex items-center gap-1.5 px-3 py-1 bg-gray-100 dark:bg-gray-700 rounded-full"
                >
                  <span className="text-xs font-medium text-gray-600 dark:text-gray-400">
                    {item.label}:
                  </span>
                  <span className="text-xs font-semibold text-gray-900 dark:text-white">
                    {item.value}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Right: Status & Actions */}
        <div className="flex items-center gap-4">
          {/* Status Indicator */}
          <div className="flex items-center gap-2">
            {currentStatus.icon}
            <span className={`text-sm font-medium ${currentStatus.color}`}>
              {currentStatus.text}
            </span>
          </div>

          {/* Actions */}
          {actions && <div className="flex items-center gap-2">{actions}</div>}
        </div>
      </div>
    </div>
  );
}

/**
 * Compact Context Indicator
 * 
 * A minimal version suitable for sidebars or tight spaces
 */
export function ContextIndicatorCompact({
  title,
  status = 'saved',
  className = '',
}: Pick<ContextIndicatorProps, 'title' | 'status' | 'className'>) {
  const statusColors = {
    saved: 'bg-green-500',
    unsaved: 'bg-yellow-500',
    saving: 'bg-blue-500 animate-pulse',
    error: 'bg-red-500',
  };

  return (
    <div
      className={`flex items-center gap-2 px-3 py-2 bg-gray-100 dark:bg-gray-700 rounded-lg ${className}`}
    >
      <div className={`w-2 h-2 rounded-full ${statusColors[status]}`} />
      <span className="text-sm font-medium text-gray-900 dark:text-white truncate">
        {title}
      </span>
    </div>
  );
}

export default ContextIndicator;
