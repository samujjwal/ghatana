/**
 * PermissionBanner Component - Display and manage permission requirements
 *
 * Shows:
 * - Permission name and icon
 * - Permission status (granted/denied/pending)
 * - Permission description
 * - Grant/Revoke action buttons
 * - Warning/error indicators for critical permissions
 *
 * Used in permission request flows, settings, and authorization screens.
 *
 * @doc.type component
 * @doc.purpose Display individual permission status with action controls
 * @doc.layer product
 * @doc.pattern Molecule
 */

import React from 'react';
import { cn } from '@ghatana/utils';
import { palette, lightColors, darkColors, componentRadius, fontSize, fontWeight } from '@ghatana/tokens';
import { useTheme } from '@ghatana/theme';
import { Badge } from '../atoms/Badge';

/**
 * Permission status type
 */
export type PermissionStatus = 'granted' | 'denied' | 'pending' | 'notRequired';

/**
 * Permission metadata
 */
export interface Permission {
  /** Permission identifier (e.g., 'ACCESSIBILITY_SERVICE') */
  id: string;
  /** Human-readable permission name */
  displayName: string;
  /** Permission description/purpose */
  description?: string;
  /** Current permission status */
  status: PermissionStatus;
  /** Whether this permission is critical/required */
  isCritical?: boolean;
  /** Icon or emoji */
  icon?: string;
}

/**
 * Props for PermissionBanner component
 */
export interface PermissionBannerProps extends React.HTMLAttributes<HTMLDivElement> {
  /** Permission metadata */
  permission: Permission;
  /** Callback to grant permission */
  onGrant?: (permission: Permission) => void;
  /** Callback to revoke permission */
  onRevoke?: (permission: Permission) => void;
  /** Show action buttons */
  showActions?: boolean;
  /** Loading state for actions */
  isLoading?: boolean;
  /** Error message if action failed */
  error?: string | null;
  /** Compact mode */
  compact?: boolean;
}

/**
 * Get badge tone based on permission status
 */
function getStatusTone(status: PermissionStatus): 'success' | 'danger' | 'warning' | 'neutral' {
  switch (status) {
    case 'granted':
      return 'success';
    case 'denied':
      return 'danger';
    case 'pending':
      return 'warning';
    default:
      return 'neutral';
  }
}

/**
 * Get human-readable status label
 */
function getStatusLabel(status: PermissionStatus): string {
  switch (status) {
    case 'granted':
      return 'Granted';
    case 'denied':
      return 'Denied';
    case 'pending':
      return 'Pending';
    case 'notRequired':
      return 'Not Required';
    default:
      return 'Unknown';
  }
}

/**
 * PermissionBanner - Molecule for displaying permission status and actions
 */
export const PermissionBanner = React.forwardRef<HTMLDivElement, PermissionBannerProps>(
  (
    {
      permission,
      onGrant,
      onRevoke,
      showActions = true,
      isLoading = false,
      error = null,
      compact = false,
      className,
      ...props
    },
    ref
  ) => {
    const { resolvedTheme } = useTheme();
    const isDark = resolvedTheme === 'dark';
    const surface = isDark ? darkColors : lightColors;

    const handleGrant = (e: React.MouseEvent) => {
      e.stopPropagation();
      onGrant?.(permission);
    };

    const handleRevoke = (e: React.MouseEvent) => {
      e.stopPropagation();
      onRevoke?.(permission);
    };

    const padding = compact ? '12px 16px' : '16px 20px';

    // Determine background color based on status
    const getBgColor = () => {
      if (permission.status === 'granted') {
        return isDark ? 'rgba(16, 185, 129, 0.1)' : 'rgba(16, 185, 129, 0.05)';
      }
      if (permission.status === 'denied') {
        return isDark ? 'rgba(239, 68, 68, 0.1)' : 'rgba(239, 68, 68, 0.05)';
      }
      if (permission.status === 'pending') {
        return isDark ? 'rgba(245, 158, 11, 0.1)' : 'rgba(245, 158, 11, 0.05)';
      }
      return surface.background.elevated;
    };

    const getBorderColor = () => {
      if (permission.status === 'granted') {
        return 'rgba(16, 185, 129, 0.3)';
      }
      if (permission.status === 'denied') {
        return 'rgba(239, 68, 68, 0.3)';
      }
      if (permission.status === 'pending') {
        return 'rgba(245, 158, 11, 0.3)';
      }
      return surface.border;
    };

    return (
      <div
        ref={ref}
        className={cn(
          'rounded-lg border p-4 transition-all',
          'flex flex-col gap-3',
          className
        )}
        style={{
          padding,
          backgroundColor: getBgColor(),
          borderColor: getBorderColor(),
        } as React.CSSProperties}
        {...props}
      >
        {/* Header: Icon, Name, and Status Badge */}
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-start gap-3 flex-1">
            {permission.icon && (
              <span
                className="flex-shrink-0 text-lg"
                style={{
                  fontSize: '20px',
                  lineHeight: '1',
                }}
              >
                {permission.icon}
              </span>
            )}
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2 mb-1">
                <h4
                  className="font-semibold"
                  style={{
                    color: surface.text.primary,
                    fontSize: '14px',
                    fontWeight: 600,
                  }}
                >
                  {permission.displayName}
                </h4>
                {permission.isCritical && (
                  <span
                    className="text-xs px-1.5 py-0.5 rounded"
                    style={{
                      backgroundColor: 'rgba(239, 68, 68, 0.15)',
                      color: palette.error[600],
                      fontSize: '10px',
                      fontWeight: 600,
                      textTransform: 'uppercase',
                      letterSpacing: '0.3px',
                    }}
                  >
                    Critical
                  </span>
                )}
              </div>

              {permission.description && (
                <p
                  className="text-sm opacity-75 line-clamp-2"
                  style={{
                    color: surface.text.primary,
                    fontSize: '12px',
                    lineHeight: '1.4',
                  }}
                >
                  {permission.description}
                </p>
              )}
            </div>
          </div>

          {/* Status Badge */}
          <div className="flex-shrink-0">
            <Badge tone={getStatusTone(permission.status)}>
              {getStatusLabel(permission.status)}
            </Badge>
          </div>
        </div>

        {/* Error message (if any) */}
        {error && (
          <p
            className="text-xs p-2 rounded"
            style={{
              backgroundColor: 'rgba(239, 68, 68, 0.1)',
              color: palette.error[600],
              fontSize: '11px',
              lineHeight: '1.4',
            }}
          >
            {error}
          </p>
        )}

        {/* Action buttons */}
        {showActions && (permission.status === 'denied' || permission.status === 'pending') && (
          <div className="flex items-center gap-2 pt-2">
            {permission.status === 'pending' || permission.status === 'denied' ? (
              <button
                onClick={handleGrant}
                disabled={isLoading}
                className="px-3 py-1.5 rounded text-sm font-medium transition-colors hover:opacity-90 disabled:opacity-50"
                style={{
                  backgroundColor: palette.primary[500],
                  color: '#ffffff',
                  fontSize: '13px',
                  fontWeight: 500,
                  border: 'none',
                  cursor: isLoading ? 'default' : 'pointer',
                }}
              >
                {isLoading ? 'Granting...' : 'Grant Permission'}
              </button>
            ) : null}
          </div>
        )}

        {/* Revoke button for granted permissions */}
        {showActions && permission.status === 'granted' && !permission.isCritical && (
          <div className="flex items-center gap-2 pt-2 border-t">
            <button
              onClick={handleRevoke}
              disabled={isLoading}
              className="px-3 py-1.5 rounded text-sm font-medium transition-colors hover:opacity-90 disabled:opacity-50"
              style={{
                backgroundColor: 'transparent',
                color: palette.error[500],
                fontSize: '13px',
                fontWeight: 500,
                border: `1px solid ${palette.error[200]}`,
                cursor: isLoading ? 'default' : 'pointer',
              }}
            >
              {isLoading ? 'Revoking...' : 'Revoke Permission'}
            </button>
          </div>
        )}

        {/* Critical permission warning */}
        {permission.isCritical && permission.status === 'granted' && (
          <p
            className="text-xs p-2 rounded flex items-center gap-2"
            style={{
              backgroundColor: 'rgba(245, 158, 11, 0.1)',
              color: palette.warning[600],
              fontSize: '11px',
              lineHeight: '1.4',
            }}
          >
            <span>⚠️</span>
            This is a critical permission required for Guardian functionality.
          </p>
        )}
      </div>
    );
  }
);

PermissionBanner.displayName = 'PermissionBanner';

export default PermissionBanner;
