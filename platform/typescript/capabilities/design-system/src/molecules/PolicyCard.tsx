/**
 * PolicyCard Component - Display policy information and controls
 *
 * Shows:
 * - Policy name and description
 * - Policy status (active/inactive)
 * - Number of apps covered
 * - Rules applied
 * - Quick action buttons (Edit, Toggle, Delete)
 * - Last updated timestamp
 *
 * Used in policy management, dashboard overview, and policy lists.
 *
 * @doc.type component
 * @doc.purpose Display policy metadata with quick actions
 * @doc.layer product
 * @doc.pattern Molecule
 */

import React from 'react';
import { cn } from '@ghatana/utils';
import { palette, lightColors, darkColors, componentRadius, fontSize, fontWeight } from '@ghatana/tokens';
import { useTheme } from '@ghatana/theme';
import { Badge } from '../atoms/Badge';

/**
 * Policy status type
 */
export type PolicyStatus = 'active' | 'inactive' | 'draft' | 'archived';

/**
 * Policy metadata structure
 */
export interface PolicyMetadata {
  /** Policy unique identifier */
  id: string;
  /** Policy name */
  name: string;
  /** Policy description */
  description?: string;
  /** Current status */
  status: PolicyStatus;
  /** Number of apps this policy applies to */
  appsCount: number;
  /** Number of rules in this policy */
  rulesCount: number;
  /** Last modified timestamp (ISO string or number) */
  lastModified?: number | string;
  /** Policy creator or owner */
  owner?: string;
  /** Priority level (0-10, higher = more important) */
  priority?: number;
}

/**
 * Props for PolicyCard component
 */
export interface PolicyCardProps extends Omit<React.HTMLAttributes<HTMLDivElement>, 'onSelect'> {
  /** Policy metadata */
  policy: PolicyMetadata;
  /** Whether card is selected */
  isSelected?: boolean;
  /** Callback when card is clicked */
  onSelect?: (policy: PolicyMetadata) => void;
  /** Callback for action button clicks */
  onAction?: (action: 'edit' | 'toggle' | 'delete', policy: PolicyMetadata) => void;
  /** Show action buttons */
  showActions?: boolean;
  /** Action button labels */
  actionLabels?: {
    edit?: string;
    toggle?: string;
    delete?: string;
  };
  /** Compact mode */
  compact?: boolean;
}

/**
 * Get badge tone based on policy status
 */
function getStatusTone(status: PolicyStatus): 'neutral' | 'success' | 'warning' | 'danger' {
  switch (status) {
    case 'active':
      return 'success';
    case 'inactive':
      return 'neutral';
    case 'draft':
      return 'warning';
    case 'archived':
      return 'danger';
    default:
      return 'neutral';
  }
}

/**
 * Get human-readable status label
 */
function getStatusLabel(status: PolicyStatus): string {
  return status.charAt(0).toUpperCase() + status.slice(1);
}

/**
 * Format timestamp for display
 */
function formatTimestamp(ts?: number | string): string {
  if (!ts) return '';
  const date = typeof ts === 'string' ? new Date(ts) : new Date(ts);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffMins = Math.floor(diffMs / (1000 * 60));
  const diffHours = Math.floor(diffMins / 60);
  const diffDays = Math.floor(diffHours / 24);

  if (diffMins < 1) return 'Just now';
  if (diffMins < 60) return `${diffMins}m ago`;
  if (diffHours < 24) return `${diffHours}h ago`;
  if (diffDays < 7) return `${diffDays}d ago`;

  return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
}

/**
 * PolicyCard - Molecule for displaying policy information
 */
export const PolicyCard = React.forwardRef<HTMLDivElement, PolicyCardProps>(
  (
    {
      policy,
      isSelected = false,
      onSelect,
      onAction,
      showActions = true,
      actionLabels = { edit: 'Edit', toggle: 'Toggle', delete: 'Delete' },
      compact = false,
      className,
      onClick,
      ...props
    },
    ref
  ) => {
    const { resolvedTheme } = useTheme();
    const isDark = resolvedTheme === 'dark';
    const surface = isDark ? darkColors : lightColors;

    const handleClick = (e: React.MouseEvent<HTMLDivElement>) => {
      if (onSelect) {
        onSelect(policy);
      }
      onClick?.(e);
    };

    const handleActionClick = (action: 'edit' | 'toggle' | 'delete', e: React.MouseEvent) => {
      e.stopPropagation();
      onAction?.(action, policy);
    };

    const padding = compact ? '12px 16px' : '16px 20px';

    return (
      <div
        ref={ref}
        onClick={handleClick}
        className={cn(
          'rounded-lg border p-4 transition-all cursor-pointer',
          'hover:shadow-md',
          isSelected && 'ring-2 ring-blue-400 shadow-md',
          className
        )}
        style={{
          padding,
          backgroundColor: surface.background.elevated,
          borderColor: isSelected ? '#3b82f6' : surface.border,
          borderWidth: isSelected ? '2px' : '1px',
        } as React.CSSProperties}
        {...props}
      >
        {/* Header: Title and Status Badge */}
        <div className="flex items-start justify-between gap-3 mb-2">
          <div className="flex-1 min-w-0">
            <h3
              className="font-semibold truncate"
              style={{
                color: surface.text.primary,
                fontSize: '15px',
                fontWeight: 600,
              }}
            >
              {policy.name}
            </h3>
          </div>
          <Badge tone={getStatusTone(policy.status)} variant="solid">
            {getStatusLabel(policy.status)}
          </Badge>
        </div>

        {/* Description */}
        {policy.description && (
          <p
            className="text-sm line-clamp-2 mb-3"
            style={{
              color: surface.text.secondary || surface.text.primary,
              fontSize: '13px',
              opacity: 0.75,
              lineHeight: '1.4',
            }}
          >
            {policy.description}
          </p>
        )}

        {/* Stats Row: Apps and Rules Count */}
        <div className="flex items-center gap-4 mb-3">
          <div className="flex items-center gap-1">
            <span
              style={{
                fontSize: '12px',
                color: surface.text.primary,
                opacity: 0.6,
              }}
            >
              Apps:
            </span>
            <span
              className="font-semibold"
              style={{
                fontSize: '13px',
                color: surface.text.primary,
              }}
            >
              {policy.appsCount}
            </span>
          </div>
          <div
            style={{
              width: '1px',
              height: '16px',
              backgroundColor: surface.border,
              opacity: 0.5,
            }}
          />
          <div className="flex items-center gap-1">
            <span
              style={{
                fontSize: '12px',
                color: surface.text.primary,
                opacity: 0.6,
              }}
            >
              Rules:
            </span>
            <span
              className="font-semibold"
              style={{
                fontSize: '13px',
                color: surface.text.primary,
              }}
            >
              {policy.rulesCount}
            </span>
          </div>

          {/* Priority indicator */}
          {policy.priority !== undefined && policy.priority > 0 && (
            <>
              <div
                style={{
                  width: '1px',
                  height: '16px',
                  backgroundColor: surface.border,
                  opacity: 0.5,
                }}
              />
              <div className="flex items-center gap-1">
                <span
                  style={{
                    fontSize: '12px',
                    color: palette.warning[500],
                  }}
                >
                  ⭐ P{policy.priority}
                </span>
              </div>
            </>
          )}
        </div>

        {/* Footer: Last Modified + Actions */}
        <div className="flex items-center justify-between gap-2 pt-3 border-t">
          {policy.lastModified && (
            <p
              className="text-xs"
              style={{
                color: surface.text.primary,
                fontSize: '11px',
                opacity: 0.6,
              }}
            >
              Modified {formatTimestamp(policy.lastModified)}
            </p>
          )}

          {showActions && (
            <div className="flex items-center gap-2 ml-auto">
              <button
                onClick={(e) => handleActionClick('edit', e)}
                className="px-2 py-1 text-xs rounded transition-colors hover:bg-blue-100 dark:hover:bg-blue-900"
                style={{
                  color: palette.primary[500],
                  backgroundColor: 'transparent',
                  border: 'none',
                  cursor: 'pointer',
                  fontSize: '12px',
                  fontWeight: 500,
                }}
              >
                {actionLabels.edit}
              </button>

              <button
                onClick={(e) => handleActionClick('toggle', e)}
                className="px-2 py-1 text-xs rounded transition-colors hover:bg-gray-100 dark:hover:bg-gray-700"
                style={{
                  color:
                    policy.status === 'active'
                      ? palette.warning[500]
                      : palette.success[500],
                  backgroundColor: 'transparent',
                  border: 'none',
                  cursor: 'pointer',
                  fontSize: '12px',
                  fontWeight: 500,
                }}
              >
                {policy.status === 'active' ? 'Disable' : 'Enable'}
              </button>

              {!compact && (
                <button
                  onClick={(e) => handleActionClick('delete', e)}
                  className="px-2 py-1 text-xs rounded transition-colors hover:bg-red-100 dark:hover:bg-red-900"
                  style={{
                    color: palette.error[500],
                    backgroundColor: 'transparent',
                    border: 'none',
                    cursor: 'pointer',
                    fontSize: '12px',
                    fontWeight: 500,
                  }}
                >
                  {actionLabels.delete}
                </button>
              )}
            </div>
          )}
        </div>
      </div>
    );
  }
);

PolicyCard.displayName = 'PolicyCard';

export default PolicyCard;
