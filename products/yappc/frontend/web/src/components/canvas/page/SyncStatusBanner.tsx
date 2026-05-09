import React from 'react';
import { AlertCircle, CloudOff, RefreshCw, ShieldCheck } from 'lucide-react';
import type { PageArtifactDocument, PageArtifactSource, PageArtifactSyncStatus } from './pageArtifactDocument';

interface SyncStatusBannerProps {
  document: PageArtifactDocument | null | undefined;
  onReload?: () => void;
  onForceSync?: () => void;
}

/**
 * Banner component that displays sync status and document source information.
 * Provides explicit UX for recovered drafts and sync state visibility.
 *
 * @doc.type component
 * @doc.purpose Sync status banner for page artifact persistence
 * @doc.layer product
 * @doc.pattern Component
 */
export function SyncStatusBanner({ document, onReload, onForceSync }: SyncStatusBannerProps) {
  if (!document) {
    return null;
  }

  const { syncStatus, source } = document;

  if (syncStatus === 'synced' && source === 'server') {
    return null; // No banner needed for synced server-authoritative documents
  }

  const isRecoveredDraft = source === 'recovered-draft' || source === 'local-draft';
  const isOffline = syncStatus === 'offline';
  const hasError = syncStatus === 'error';
  const isDirty = syncStatus === 'dirty';

  return (
    <div
      className={`flex items-center gap-3 px-4 py-3 text-sm border-l-4 ${
        isRecoveredDraft
          ? 'bg-amber-50 border-amber-500 text-amber-900'
          : isOffline
          ? 'bg-gray-50 border-gray-500 text-gray-900'
          : hasError
          ? 'bg-red-50 border-red-500 text-red-900'
          : 'bg-blue-50 border-blue-500 text-blue-900'
      }`}
      role="alert"
      aria-live="polite"
    >
      {isRecoveredDraft ? (
        <>
          <CloudOff className="w-5 h-5 flex-shrink-0" />
          <div className="flex-1">
            <p className="font-medium">Recovered Draft</p>
            <p className="text-xs opacity-90">
              This document was recovered from local storage. Changes will not be saved to the server.
            </p>
          </div>
          {onForceSync && (
            <button
              onClick={onForceSync}
              className="px-3 py-1.5 text-xs font-medium bg-white border border-current rounded hover:bg-opacity-90 transition-colors"
              aria-label="Force sync to server"
            >
              Sync to Server
            </button>
          )}
        </>
      ) : isOffline ? (
        <>
          <CloudOff className="w-5 h-5 flex-shrink-0" />
          <div className="flex-1">
            <p className="font-medium">Offline Mode</p>
            <p className="text-xs opacity-90">
              Changes are saved locally. They will sync when connection is restored.
            </p>
          </div>
          {onReload && (
            <button
              onClick={onReload}
              className="px-3 py-1.5 text-xs font-medium bg-white border border-current rounded hover:bg-opacity-90 transition-colors"
              aria-label="Try to reconnect"
            >
              Reconnect
            </button>
          )}
        </>
      ) : hasError ? (
        <>
          <AlertCircle className="w-5 h-5 flex-shrink-0" />
          <div className="flex-1">
            <p className="font-medium">Sync Error</p>
            <p className="text-xs opacity-90">
              Failed to save changes. Please try again or reload the document.
            </p>
          </div>
          {onReload && (
            <button
              onClick={onReload}
              className="px-3 py-1.5 text-xs font-medium bg-white border border-current rounded hover:bg-opacity-90 transition-colors"
              aria-label="Retry save"
            >
              Retry
            </button>
          )}
        </>
      ) : isDirty ? (
        <>
          <RefreshCw className="w-5 h-5 flex-shrink-0 animate-spin" />
          <div className="flex-1">
            <p className="font-medium">Saving...</p>
            <p className="text-xs opacity-90">Changes are being saved to the server.</p>
          </div>
        </>
      ) : source === 'server' ? (
        <>
          <ShieldCheck className="w-5 h-5 flex-shrink-0" />
          <div className="flex-1">
            <p className="font-medium">Server Authoritative</p>
            <p className="text-xs opacity-90">
              This document is synced with the server and is the source of truth.
            </p>
          </div>
        </>
      ) : null}
    </div>
  );
}
