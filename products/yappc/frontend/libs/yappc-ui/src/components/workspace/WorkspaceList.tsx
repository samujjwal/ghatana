/**
 * WorkspaceList
 *
 * Renders a scrollable list of WorkspaceCards.
 *
 * @doc.type component
 * @doc.purpose Display a list of workspaces with selection and settings
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

import React from 'react';

import type { Workspace } from 'yappc-core/types';

import { WorkspaceCard } from './WorkspaceCard';

export interface WorkspaceListProps {
  workspaces: Workspace[];
  selectedId?: string | null;
  isLoading?: boolean;
  error?: Error | null;
  onSelect?: (workspace: Workspace) => void;
  onSettings?: (workspace: Workspace) => void;
  emptyMessage?: string;
  className?: string;
}

/**
 * Renders a list of workspaces.
 */
export const WorkspaceList: React.FC<WorkspaceListProps> = ({
  workspaces,
  selectedId,
  isLoading,
  error,
  onSelect,
  onSettings,
  emptyMessage = 'No workspaces found.',
  className,
}) => {
  if (isLoading) {
    return (
      <div className="flex justify-center py-8" aria-label="Loading workspaces">
        <span
          className="h-8 w-8 animate-spin rounded-full border-2 border-slate-200 border-t-blue-600"
          aria-hidden="true"
        />
      </div>
    );
  }

  if (error) {
    return (
      <div
        role="alert"
        className="m-1 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800"
      >
        {error.message}
      </div>
    );
  }

  if (workspaces.length === 0) {
    return (
      <div className="flex justify-center py-8 text-sm text-slate-500">
        {emptyMessage}
      </div>
    );
  }

  return (
    <div className={`flex flex-col gap-2 overflow-y-auto ${className ?? ''}`}>
      {workspaces.map((workspace) => (
        <WorkspaceCard
          key={workspace.id}
          workspace={workspace}
          isSelected={workspace.id === selectedId}
          onSelect={onSelect}
          onSettings={onSettings}
        />
      ))}
    </div>
  );
};
