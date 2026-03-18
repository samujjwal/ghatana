/**
 * AppListItem Component - Individual app card for app monitoring list
 *
 * Displays:
 * - App name and package
 * - App icon
 * - Current usage time (if active)
 * - Permission status indicators
 * - Interactive selection state
 *
 * Used in app monitoring dashboards, app selection screens, and activity feeds.
 *
 * @doc.type component
 * @doc.purpose Display individual app in list with metadata and status
 * @doc.layer product
 * @doc.pattern Molecule
 */

import React from 'react';
import { cn } from '@ghatana/utils';
import {
  lightColors,
  darkColors,
  componentRadius,
  fontSize,
  fontWeight,
} from '@ghatana/tokens';
import { useTheme } from '@ghatana/theme';
import { Box } from '../layout/Box';
import { Badge } from '../atoms/Badge';

/**
 * Status type for app permission/monitoring state
 */
export type AppStatus = 'active' | 'idle' | 'restricted' | 'blocked' | 'error';

/**
 * Individual app item metadata
 */
export interface AppMetadata {
  /** App package name (unique identifier) */
  packageName: string;
  /** Display name of the app */
  displayName: string;
  /** Icon URL or base64 encoded data */
  icon?: string;
  /** Current status */
  status?: AppStatus;
  /** Usage time in seconds (if applicable) */
  usageTimeSeconds?: number;
  /** Number of permissions needed */
  permissionsCount?: number;
  /** Last accessed timestamp */
  lastAccessedAt?: number;
  /** Whether app is currently in focus */
  isFocused?: boolean;
  /** Custom data (e.g., policy applied, risk level) */
  metadata?: Record<string, unknown>;
}

/**
 * Props for AppListItem component
 */
export interface AppListItemProps extends Omit<React.HTMLAttributes<HTMLDivElement>, 'title' | 'onSelect'> {
  /** App metadata */
  app: AppMetadata;
  /** Whether item is selected */
  isSelected?: boolean;
  /** Whether item is interactive (clickable) */
  interactive?: boolean;
  /** Callback when item is clicked */
  onSelect?: (app: AppMetadata) => void;
  /** Callback when action button is clicked */
  onAction?: (action: string, app: AppMetadata) => void;
  /** Slot for action buttons/controls */
  actions?: React.ReactNode;
  /** Compact mode (smaller height, less padding) */
  compact?: boolean;
  /** Show additional metadata */
  showMetadata?: boolean;
}

/**
 * Get status color based on app status
 */
function getStatusColor(status?: AppStatus): string {
  switch (status) {
    case 'active':
      return '#10b981';
    case 'idle':
      return '#8b5cf6';
    case 'restricted':
      return '#f59e0b';
    case 'blocked':
      return '#ef4444';
    case 'error':
      return '#6b7280';
    default:
      return '#9ca3af';
  }
}

/**
 * Get status label
 */
function getStatusLabel(status?: AppStatus): string {
  switch (status) {
    case 'active':
      return 'Active';
    case 'idle':
      return 'Idle';
    case 'restricted':
      return 'Restricted';
    case 'blocked':
      return 'Blocked';
    case 'error':
      return 'Error';
    default:
      return 'Unknown';
  }
}

/**
 * Format usage time for display
 */
function formatUsageTime(seconds?: number): string {
  if (!seconds || seconds <= 0) return '0s';
  if (seconds < 60) return `${Math.round(seconds)}s`;
  if (seconds < 3600) return `${Math.round(seconds / 60)}m`;
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.round((seconds % 3600) / 60);
  return `${hours}h ${minutes}m`;
}

/**
 * AppListItem - Molecule for displaying individual app in list
 */
export const AppListItem = React.forwardRef<HTMLDivElement, AppListItemProps>(
  (
    {
      app,
      isSelected = false,
      interactive = true,
      onSelect,
      onAction,
      actions,
      compact = false,
      showMetadata = true,
      className,
      onClick,
      ...props
    },
    ref
  ) => {
    const { resolvedTheme } = useTheme();
    const isDark = resolvedTheme === 'dark';
    const surface = isDark ? darkColors : lightColors;
    const bgColor = surface.background.elevated || surface.background.paper;
    const textColor = surface.text.primary;
    const borderColor = surface.border;
    const _hoverBgColor = isDark ? '#2c2c2c' : '#f5f5f5';

    const handleClick = (e: React.MouseEvent<HTMLDivElement>) => {
      if (interactive && onSelect) {
        onSelect(app);
      }
      onClick?.(e);
    };

    const padding = compact ? '12px 16px' : '16px 20px';
    const heightClass = compact ? 'min-h-16' : 'min-h-20';

    return (
      <div
        ref={ref}
        onClick={handleClick}
        className={cn(
          'relative flex items-center gap-3 rounded-lg border transition-colors',
          interactive && 'cursor-pointer',
          isSelected && `bg-blue-50 border-blue-300 dark:bg-blue-900 dark:border-blue-600`,
          !isSelected && heightClass,
          className
        )}
        style={{
          padding,
          backgroundColor: isSelected ? undefined : bgColor,
          borderColor: isSelected ? undefined : borderColor,
          ...(interactive && !isSelected && { cursor: 'pointer' }),
        } as React.CSSProperties}
        {...props}
      >
        {/* App Icon */}
        <div
          className="flex-shrink-0 w-10 h-10 rounded-lg overflow-hidden bg-gray-200 dark:bg-gray-700 flex items-center justify-center"
          style={{
            backgroundColor: '#e5e7eb',
            minWidth: '40px',
            minHeight: '40px',
          }}
        >
          {app.icon ? (
            <img
              src={app.icon}
              alt={app.displayName}
              className="w-full h-full object-cover"
              loading="lazy"
            />
          ) : (
            <span
              className="text-lg font-bold text-gray-600 dark:text-gray-300"
              style={{ fontSize: '18px', color: '#4b5563' }}
            >
              {app.displayName.charAt(0).toUpperCase()}
            </span>
          )}
        </div>

        {/* App Info */}
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1">
            <p
              className="font-semibold truncate"
              style={{
                color: textColor,
                fontSize: '14px',
                fontWeight: 600,
              }}
            >
              {app.displayName}
            </p>
            {app.isFocused && (
              <Badge tone="success">
                In Focus
              </Badge>
            )}
            {app.status && (
              <Badge
                tone={
                  app.status === 'active'
                    ? 'success'
                    : app.status === 'blocked'
                      ? 'danger'
                      : app.status === 'restricted'
                        ? 'warning'
                        : 'neutral'
                }
              >
                {getStatusLabel(app.status)}
              </Badge>
            )}
          </div>

          {/* Package name and metadata */}
          <div className="flex items-center gap-2">
            <p
              className="text-sm truncate opacity-75"
              style={{
                color: textColor,
                fontSize: '12px',
                opacity: 0.7,
              }}
            >
              {app.packageName}
            </p>

            {showMetadata && app.usageTimeSeconds !== undefined && (
              <>
                <span
                  style={{
                    color: borderColor,
                    margin: '0 4px',
                  }}
                >
                  •
                </span>
                <p
                  className="text-sm"
                  style={{
                    color: textColor,
                    fontSize: '12px',
                    opacity: 0.7,
                  }}
                >
                  {formatUsageTime(app.usageTimeSeconds)}
                </p>
              </>
            )}

            {showMetadata && app.permissionsCount !== undefined && app.permissionsCount > 0 && (
              <>
                <span
                  style={{
                    color: borderColor,
                    margin: '0 4px',
                  }}
                >
                  •
                </span>
                <p
                  className="text-sm"
                  style={{
                    color: textColor,
                    fontSize: '12px',
                    opacity: 0.7,
                  }}
                >
                  {app.permissionsCount} permissions
                </p>
              </>
            )}
          </div>
        </div>

        {/* Status indicator dot */}
        <div
          className="flex-shrink-0 w-3 h-3 rounded-full"
          style={{
            backgroundColor: getStatusColor(app.status),
            minWidth: '12px',
            minHeight: '12px',
          }}
        />

        {/* Action buttons */}
        {actions && <div className="flex-shrink-0 flex items-center gap-2">{actions}</div>}
      </div>
    );
  }
);

AppListItem.displayName = 'AppListItem';

export default AppListItem;
