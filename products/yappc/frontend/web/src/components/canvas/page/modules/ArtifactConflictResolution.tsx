/**
 * Artifact Conflict Resolution Module
 *
 * @doc.type component
 * @doc.purpose UX for resolving remote artifact version conflicts with reload, compare, reapply, discard, retry options
 * @doc.layer product
 * @doc.pattern Widget
 */

import React from 'react';
import { RefreshCw, GitCompare, Save, Trash2, RotateCcw } from 'lucide-react';
import { Button } from '@ghatana/design-system';
import { Typography } from '@ghatana/design-system';

export interface ArtifactConflict {
  readonly artifactId: string;
  readonly localVersion: number;
  readonly remoteVersion: number;
  readonly conflictType: 'version-mismatch' | 'concurrent-edit' | 'server-update';
  readonly lastSyncedAt: string;
  readonly remoteUpdatedAt: string;
}

export interface ArtifactConflictResolutionProps {
  readonly conflict: ArtifactConflict;
  readonly onReload: () => void;
  readonly onCompare: () => void;
  readonly onReapply: () => void;
  readonly onDiscard: () => void;
  readonly onRetry: () => void;
}

export function ArtifactConflictResolution({
  conflict,
  onReload,
  onCompare,
  onReapply,
  onDiscard,
  onRetry,
}: ArtifactConflictResolutionProps): React.JSX.Element {
  const getConflictDescription = () => {
    switch (conflict.conflictType) {
      case 'version-mismatch':
        return 'The artifact on the server has been updated since your last sync.';
      case 'concurrent-edit':
        return 'Another user has made changes to this artifact while you were editing.';
      case 'server-update':
        return 'The server has updated this artifact with changes that conflict with your local version.';
    }
  };

  return (
    <div className="rounded-lg border border-red-200 bg-red-50 p-4 dark:border-red-900/50 dark:bg-red-950/20">
      <div className="mb-3 flex items-center gap-2">
        <RefreshCw className="h-5 w-5 text-red-600 dark:text-red-400" />
        <Typography variant="h3" className="text-base font-semibold text-red-800 dark:text-red-200">
          Artifact Version Conflict
        </Typography>
      </div>

      <Typography variant="body2" className="mb-4 text-sm text-red-700 dark:text-red-300">
        {getConflictDescription()}
      </Typography>

      <div className="mb-4 rounded border border-red-300 bg-white p-3 text-sm dark:border-red-900/50 dark:bg-gray-900">
        <div className="grid grid-cols-2 gap-2">
          <div>
            <Typography variant="body2" className="font-medium text-gray-700 dark:text-gray-300">
              Your Version:
            </Typography>
            <Typography variant="caption" className="text-gray-600 dark:text-gray-400">
              v{conflict.localVersion}
            </Typography>
          </div>
          <div>
            <Typography variant="body2" className="font-medium text-gray-700 dark:text-gray-300">
              Remote Version:
            </Typography>
            <Typography variant="caption" className="text-gray-600 dark:text-gray-400">
              v{conflict.remoteVersion}
            </Typography>
          </div>
        </div>
        <div className="mt-2 text-xs text-gray-500 dark:text-gray-400">
          <div>Last synced: {new Date(conflict.lastSyncedAt).toLocaleString()}</div>
          <div>Remote updated: {new Date(conflict.remoteUpdatedAt).toLocaleString()}</div>
        </div>
      </div>

      <div className="flex flex-wrap gap-2">
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={onReload}
          className="flex items-center gap-1"
        >
          <RefreshCw className="h-3 w-3" />
          Reload
        </Button>
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={onCompare}
          className="flex items-center gap-1"
        >
          <GitCompare className="h-3 w-3" />
          Compare
        </Button>
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={onReapply}
          className="flex items-center gap-1"
        >
          <Save className="h-3 w-3" />
          Reapply
        </Button>
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={onDiscard}
          className="flex items-center gap-1"
        >
          <Trash2 className="h-3 w-3" />
          Discard
        </Button>
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={onRetry}
          className="flex items-center gap-1"
        >
          <RotateCcw className="h-3 w-3" />
          Retry
        </Button>
      </div>
    </div>
  );
}
