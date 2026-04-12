/**
 * @fileoverview SyncStatusIndicator - Shows sync state of four-layer model.
 *
 * @doc.type component
 * @doc.purpose Indicates whether a representation is synced, syncing, or conflicted.
 * @doc.category atom
 * @doc.tags ai, visibility, sync
 */

import * as React from 'react';
import type { SyncStatus } from '@ghatana/platform-events';

export interface SyncStatusIndicatorProps {
  /** The sync status to display */
  readonly status: SyncStatus;
  /** Size variant */
  readonly size?: 'sm' | 'md' | 'lg';
  /** Whether to show detailed breakdown */
  readonly showDetails?: boolean;
  /** Additional CSS classes */
  readonly className?: string;
}

type RepresentationStatus =
  | 'synced'
  | 'syncing'
  | 'conflict'
  | 'stale'
  | 'user-modified'
  | 'unknown';

const statusConfig: Record<
  RepresentationStatus,
  {
    readonly icon: React.ReactNode;
    readonly color: string;
    readonly label: string;
  }
> = {
  synced: {
    icon: (
      <div className="h-2 w-2 rounded-full bg-green-500" aria-hidden="true" />
    ),
    color: 'text-green-600',
    label: 'Synced',
  },
  syncing: {
    icon: (
      <div className="h-2 w-2 rounded-full bg-blue-500 animate-pulse" aria-hidden="true" />
    ),
    color: 'text-blue-600',
    label: 'Syncing',
  },
  conflict: {
    icon: (
      <div className="h-2 w-2 rounded-full bg-red-500" aria-hidden="true" />
    ),
    color: 'text-red-600',
    label: 'Conflict',
  },
  stale: {
    icon: (
      <div className="h-2 w-2 rounded-full bg-yellow-500" aria-hidden="true" />
    ),
    color: 'text-yellow-600',
    label: 'Stale',
  },
  'user-modified': {
    icon: (
      <div className="h-2 w-2 rounded-full bg-purple-500" aria-hidden="true" />
    ),
    color: 'text-purple-600',
    label: 'User Modified',
  },
  unknown: {
    icon: (
      <div className="h-2 w-2 rounded-full bg-gray-400" aria-hidden="true" />
    ),
    color: 'text-gray-500',
    label: 'Unknown',
  },
};

const sizeConfig = {
  sm: 'text-xs gap-1',
  md: 'text-sm gap-1.5',
  lg: 'text-base gap-2',
};

const representationLabels: Record<keyof SyncStatus, string> = {
  designSystem: 'Design System',
  builderDocument: 'Builder Document',
  visualProjection: 'Visual',
  codeProjection: 'Code',
  lastSyncAt: 'Last Sync',
  syncInProgress: 'Syncing',
};

/**
 * Individual representation status indicator.
 */
export const RepresentationIndicator: React.FC<{
  readonly name: string;
  readonly status: RepresentationStatus;
  readonly size?: 'sm' | 'md' | 'lg';
}> = React.memo(({ name, status, size = 'sm' }) => {
  const config = statusConfig[status] ?? statusConfig.unknown;
  const sizeClasses = sizeConfig[size];

  return (
    <span
      className={`inline-flex items-center ${sizeClasses} ${config.color}`}
      role="status"
      aria-label={`${name}: ${config.label}`}
    >
      {config.icon}
      <span className="font-medium">{name}</span>
    </span>
  );
});

RepresentationIndicator.displayName = 'RepresentationIndicator';

/**
 * SyncStatusIndicator component - shows four-layer sync status.
 */
export const SyncStatusIndicator: React.FC<SyncStatusIndicatorProps> = React.memo(({
  status,
  size = 'md',
  showDetails = false,
  className = '',
}) => {
  const sizeClasses = sizeConfig[size];

  // Determine overall status
  const overallStatus: RepresentationStatus = status.syncInProgress
    ? 'syncing'
    : status.codeProjection === 'conflict' || status.designSystem === 'conflict'
      ? 'conflict'
      : status.visualProjection === 'stale'
        ? 'stale'
        : 'synced';

  const config = statusConfig[overallStatus];

  if (!showDetails) {
    return (
      <span
        className={`inline-flex items-center ${sizeClasses} ${config.color} ${className}`}
        role="status"
        aria-label={`Sync status: ${config.label}`}
      >
        {config.icon}
        <span className="font-medium">{config.label}</span>
      </span>
    );
  }

  return (
    <div className={`space-y-1 ${className}`} role="group" aria-label="Sync status by representation">
      <div className={`inline-flex items-center ${sizeClasses} ${config.color}`}>
        {config.icon}
        <span className="font-medium">{config.label}</span>
      </div>
      <div className="ml-4 space-y-0.5 border-l-2 border-gray-200 pl-2">
        <RepresentationIndicator
          name="Design System"
          status={status.designSystem}
          size={size === 'lg' ? 'md' : 'sm'}
        />
        <RepresentationIndicator
          name="Builder"
          status={status.builderDocument}
          size={size === 'lg' ? 'md' : 'sm'}
        />
        <RepresentationIndicator
          name="Visual"
          status={status.visualProjection}
          size={size === 'lg' ? 'md' : 'sm'}
        />
        <RepresentationIndicator
          name="Code"
          status={status.codeProjection}
          size={size === 'lg' ? 'md' : 'sm'}
        />
      </div>
      {status.lastSyncAt && (
        <div className={`${sizeClasses} text-gray-500 ml-4`}>
          Last sync: {new Date(status.lastSyncAt).toLocaleTimeString()}
        </div>
      )}
    </div>
  );
});

SyncStatusIndicator.displayName = 'SyncStatusIndicator';
