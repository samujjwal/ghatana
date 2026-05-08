/**
 * WorkspaceCard
 *
 * Displays a workspace as a clickable summary card.
 *
 * @doc.type component
 * @doc.purpose Display a workspace summary with name, description, and owner
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

import { Settings as SettingsIcon, Star as StarIcon } from 'lucide-react';
import React from 'react';

import type { Workspace } from 'yappc-core/types';

export interface WorkspaceCardProps {
  workspace: Workspace;
  isSelected?: boolean;
  onSelect?: (workspace: Workspace) => void;
  onSettings?: (workspace: Workspace) => void;
  className?: string;
}

function getInitials(name: string): string {
  return name
    .split(/\s+/)
    .slice(0, 2)
    .map((word) => word[0]?.toUpperCase() ?? '')
    .join('');
}

/**
 * Card component for a single workspace.
 */
export const WorkspaceCard: React.FC<WorkspaceCardProps> = ({
  workspace,
  isSelected = false,
  onSelect,
  onSettings,
  className,
}) => {
  const handleKeyDown = (event: React.KeyboardEvent<HTMLElement>): void => {
    if (!onSelect || (event.key !== 'Enter' && event.key !== ' ')) {
      return;
    }
    event.preventDefault();
    onSelect(workspace);
  };

  return (
    <article
      className={`rounded-xl border bg-white p-4 shadow-sm transition ${
        isSelected ? 'border-blue-500 ring-2 ring-blue-100' : 'border-slate-200'
      } ${onSelect ? 'cursor-pointer hover:border-blue-300 hover:shadow-md' : ''} ${
        className ?? ''
      }`}
      role={onSelect ? 'button' : undefined}
      tabIndex={onSelect ? 0 : undefined}
      onClick={onSelect ? () => onSelect(workspace) : undefined}
      onKeyDown={handleKeyDown}
    >
      <div className="flex items-start gap-3">
        <span
          className={`inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-sm font-bold text-white ${
            isSelected ? 'bg-blue-600' : 'bg-slate-700'
          }`}
        >
          {getInitials(workspace.name)}
        </span>

        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-1">
            <h3 className="min-w-0 flex-1 truncate text-sm font-semibold text-slate-900">
              {workspace.name}
            </h3>
            {workspace.isDefault && (
              <span title="Default workspace">
                <StarIcon
                  size={14}
                  className="text-amber-500"
                  aria-label="Default workspace"
                />
              </span>
            )}
          </div>

          {workspace.description && (
            <p className="mt-1 line-clamp-2 text-xs leading-5 text-slate-500">
              {workspace.description}
            </p>
          )}

          {workspace.aiTags && workspace.aiTags.length > 0 && (
            <div className="mt-2 flex flex-wrap gap-1">
              {workspace.aiTags.slice(0, 3).map((tag) => (
                <span
                  key={tag}
                  className="rounded-full border border-slate-200 px-2 py-0.5 text-[0.65rem] font-medium text-slate-600"
                >
                  {tag}
                </span>
              ))}
            </div>
          )}
        </div>

        {onSettings && (
          <button
            type="button"
            aria-label={`Open settings for ${workspace.name}`}
            title="Workspace settings"
            className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-slate-500 transition hover:bg-slate-100 hover:text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500"
            onClick={(event) => {
              event.stopPropagation();
              onSettings(workspace);
            }}
          >
            <SettingsIcon size={16} aria-hidden="true" />
          </button>
        )}
      </div>
    </article>
  );
};
