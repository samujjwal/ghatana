/**
 * TutorPutor Offline UI Components
 *
 * UI components for offline status indication.
 * Uses @ghatana/ui atoms and follows design system patterns.
 *
 * @doc.type module
 * @doc.purpose Offline UI components
 * @doc.layer product
 * @doc.pattern Component
 */

import * as React from 'react';
import { Badge, Spinner, Button } from '@/components/ui';
import { useOnlineStatus, useOfflineProgress, useServiceWorker } from '../hooks/useOffline';

// ============================================================================
// Offline Banner
// ============================================================================

export interface OfflineBannerProps {
  className?: string;
  showDismiss?: boolean;
  onDismiss?: () => void;
}

/**
 * Banner shown when the user is offline.
 */
export function OfflineBanner({ className, showDismiss, onDismiss }: OfflineBannerProps) {
  const { isOnline, wasOffline } = useOnlineStatus();
  const [isDismissed, setIsDismissed] = React.useState(false);

  // Reset dismissed state when going offline
  React.useEffect(() => {
    if (!isOnline) {
      setIsDismissed(false);
    }
  }, [isOnline]);

  // Show "back online" message briefly
  const [showBackOnline, setShowBackOnline] = React.useState(false);
  React.useEffect(() => {
    if (isOnline && wasOffline) {
      setShowBackOnline(true);
      const timer = setTimeout(() => setShowBackOnline(false), 3000);
      return () => clearTimeout(timer);
    }
  }, [isOnline, wasOffline]);

  if (isDismissed) return null;
  if (isOnline && !showBackOnline) return null;

  const handleDismiss = () => {
    setIsDismissed(true);
    onDismiss?.();
  };

  return (
    <div
      className={`
        fixed top-0 left-0 right-0 z-50
        px-4 py-3 flex items-center justify-center gap-3
        text-sm font-medium
        ${isOnline
          ? 'bg-green-500 text-white'
          : 'bg-amber-500 text-amber-950'}
        ${className ?? ''}
      `}
      role="alert"
      aria-live="polite"
    >
      {isOnline ? (
        <>
          <CheckIcon className="w-4 h-4" />
          <span>You're back online! Syncing your progress...</span>
        </>
      ) : (
        <>
          <CloudOffIcon className="w-4 h-4" />
          <span>You're offline. Your progress will sync when you reconnect.</span>
        </>
      )}

      {showDismiss && (
        <button
          onClick={handleDismiss}
          className="ml-4 p-1 rounded hover:bg-black/10"
          aria-label="Dismiss"
        >
          <XIcon className="w-4 h-4" />
        </button>
      )}
    </div>
  );
}

// ============================================================================
// Sync Status Indicator
// ============================================================================

export interface SyncStatusIndicatorProps {
  className?: string;
  showLabel?: boolean;
}

/**
 * Compact indicator showing sync status in the header.
 */
export function SyncStatusIndicator({ className, showLabel = true }: SyncStatusIndicatorProps) {
  const { isOnline } = useOnlineStatus();
  const { pendingUpdates, syncProgress } = useOfflineProgress();
  const [isSyncing, setIsSyncing] = React.useState(false);

  const handleSync = async () => {
    if (!isOnline || isSyncing) return;
    setIsSyncing(true);
    try {
      await syncProgress();
    } finally {
      setIsSyncing(false);
    }
  };

  // Auto-sync when coming back online
  React.useEffect(() => {
    if (isOnline && pendingUpdates > 0) {
      handleSync();
    }
  }, [isOnline]);

  if (isOnline && pendingUpdates === 0) {
    return null; // All synced, nothing to show
  }

  return (
    <div className={`flex items-center gap-2 ${className ?? ''}`}>
      {isSyncing ? (
        <>
          <Spinner className="w-4 h-4" />
          {showLabel && <span className="text-sm text-gray-500">Syncing...</span>}
        </>
      ) : !isOnline ? (
        <>
          <Badge tone="warning" variant="soft">
            <CloudOffIcon className="w-3 h-3" />
            Offline
          </Badge>
        </>
      ) : pendingUpdates > 0 ? (
        <button
          onClick={handleSync}
          className="flex items-center gap-1 text-sm text-blue-600 hover:text-blue-700"
        >
          <SyncIcon className="w-4 h-4" />
          {showLabel && <span>Sync ({pendingUpdates})</span>}
        </button>
      ) : null}
    </div>
  );
}

// ============================================================================
// Download Button
// ============================================================================

export interface DownloadButtonProps {
  moduleId: string;
  moduleTitle: string;
  isDownloaded: boolean;
  onDownload: () => Promise<void>;
  onRemove: () => Promise<void>;
  size?: 'sm' | 'md' | 'lg';
  className?: string;
}

/**
 * Button to download/remove modules for offline access.
 */
export function DownloadButton({
  moduleId,
  moduleTitle,
  isDownloaded,
  onDownload,
  onRemove,
  size = 'md',
  className,
}: DownloadButtonProps) {
  const [isLoading, setIsLoading] = React.useState(false);

  const handleClick = async () => {
    setIsLoading(true);
    try {
      if (isDownloaded) {
        await onRemove();
      } else {
        await onDownload();
      }
    } finally {
      setIsLoading(false);
    }
  };

  const sizeClasses = {
    sm: 'p-1.5',
    md: 'p-2',
    lg: 'p-3',
  };

  return (
    <button
      onClick={handleClick}
      disabled={isLoading}
      className={`
        rounded-full transition-colors
        ${sizeClasses[size]}
        ${isDownloaded
          ? 'bg-green-100 text-green-600 hover:bg-green-200'
          : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}
        disabled:opacity-50 disabled:cursor-not-allowed
        ${className ?? ''}
      `}
      aria-label={isDownloaded ? `Remove ${moduleTitle} from downloads` : `Download ${moduleTitle} for offline`}
    >
      {isLoading ? (
        <Spinner className="w-5 h-5" />
      ) : isDownloaded ? (
        <CheckCircleIcon className="w-5 h-5" />
      ) : (
        <DownloadIcon className="w-5 h-5" />
      )}
    </button>
  );
}

// ============================================================================
// Downloaded Module Badge
// ============================================================================

export interface DownloadedBadgeProps {
  className?: string;
}

/**
 * Small badge indicating a module is available offline.
 */
export function DownloadedBadge({ className }: DownloadedBadgeProps) {
  return (
    <Badge
      tone="success"
      variant="soft"
      className={className}
      startIcon={<DownloadIcon className="w-3 h-3" />}
    >
      Offline
    </Badge>
  );
}

// ============================================================================
// Update Available Banner
// ============================================================================

export interface UpdateBannerProps {
  className?: string;
}

/**
 * Banner shown when a new version of the app is available.
 */
export function UpdateBanner({ className }: UpdateBannerProps) {
  const { isUpdateAvailable, update } = useServiceWorker();
  const [isDismissed, setIsDismissed] = React.useState(false);

  if (!isUpdateAvailable || isDismissed) return null;

  return (
    <div
      className={`
        fixed bottom-4 left-4 right-4 md:left-auto md:right-4 md:max-w-sm
        p-4 rounded-lg shadow-lg
        bg-blue-600 text-white
        flex items-center gap-4
        ${className ?? ''}
      `}
    >
      <div className="flex-1">
        <p className="font-medium">Update available</p>
        <p className="text-sm text-blue-100">A new version of TutorPutor is ready.</p>
      </div>
      <div className="flex gap-2">
        <button
          onClick={() => setIsDismissed(true)}
          className="px-3 py-1.5 text-sm rounded hover:bg-blue-700"
        >
          Later
        </button>
        <button
          onClick={update}
          className="px-3 py-1.5 text-sm rounded bg-white text-blue-600 font-medium hover:bg-blue-50"
        >
          Update
        </button>
      </div>
    </div>
  );
}

// ============================================================================
// Storage Usage Indicator
// ============================================================================

export interface StorageUsageProps {
  usedBytes: number;
  totalBytes?: number;
  className?: string;
}

/**
 * Shows storage usage for offline content.
 */
export function StorageUsage({ usedBytes, totalBytes = 500 * 1024 * 1024, className }: StorageUsageProps) {
  const percentage = Math.min((usedBytes / totalBytes) * 100, 100);
  const usedMB = (usedBytes / (1024 * 1024)).toFixed(1);
  const totalMB = (totalBytes / (1024 * 1024)).toFixed(0);

  const getColor = () => {
    if (percentage > 90) return 'bg-red-500';
    if (percentage > 70) return 'bg-amber-500';
    return 'bg-blue-500';
  };

  return (
    <div className={className}>
      <div className="flex justify-between text-sm mb-1">
        <span className="text-gray-600">Offline Storage</span>
        <span className="text-gray-900 font-medium">{usedMB} / {totalMB} MB</span>
      </div>
      <div className="h-2 bg-gray-200 rounded-full overflow-hidden">
        <div
          className={`h-full ${getColor()} transition-all duration-300`}
          style={{ width: `${percentage}%` }}
        />
      </div>
      {percentage > 90 && (
        <p className="text-xs text-red-600 mt-1">
          Storage almost full. Consider removing some downloaded modules.
        </p>
      )}
    </div>
  );
}

// ============================================================================
// Icon Components (inline SVGs for simplicity)
// ============================================================================

function CloudOffIcon({ className }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M18.364 5.636a9 9 0 010 12.728m0 0l-2.829-2.829m2.829 2.829L21 21M15.536 8.464a5 5 0 010 7.072m0 0l-2.829-2.829m-4.243 2.829a4.978 4.978 0 01-1.414-2.83m-1.414 5.658a9 9 0 01-2.167-9.238m7.824 2.167a1 1 0 111.414 1.414m-1.414-1.414L3 3m8.293 8.293l1.414 1.414" />
    </svg>
  );
}

function CheckIcon({ className }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
    </svg>
  );
}

function XIcon({ className }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
    </svg>
  );
}

function SyncIcon({ className }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
    </svg>
  );
}

function DownloadIcon({ className }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
    </svg>
  );
}

function CheckCircleIcon({ className }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
    </svg>
  );
}
