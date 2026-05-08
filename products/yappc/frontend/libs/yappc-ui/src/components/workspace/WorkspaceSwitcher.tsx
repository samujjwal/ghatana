/**
 * WorkspaceSwitcher
 *
 * Compact workspace selector rendered as a button that opens a dropdown list
 * of available workspaces. Selecting one calls `onSelect` and closes the menu.
 *
 * @doc.type component
 * @doc.purpose Quick workspace switching via dropdown
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

import {
  Check as CheckIcon,
  ChevronDown as ChevronDownIcon,
  Plus as PlusIcon,
} from 'lucide-react';
import React, { useState } from 'react';

import type { Workspace } from 'yappc-core/types';

export interface WorkspaceSwitcherProps {
  workspaces: Workspace[];
  currentWorkspace?: Workspace | null;
  onSelect?: (workspace: Workspace) => void;
  onCreateNew?: () => void;
  onCreate?: () => void;
}

function getInitials(name: string): string {
  return name
    .split(/\s+/)
    .slice(0, 2)
    .map((word) => word[0]?.toUpperCase() ?? '')
    .join('');
}

/**
 * A compact workspace selector button with dropdown.
 */
export const WorkspaceSwitcher: React.FC<WorkspaceSwitcherProps> = ({
  workspaces,
  currentWorkspace,
  onSelect,
  onCreateNew,
  onCreate,
}) => {
  const [open, setOpen] = useState(false);
  const createHandler = onCreateNew ?? onCreate;

  const handleSelect = (workspace: Workspace): void => {
    onSelect?.(workspace);
    setOpen(false);
  };

  const handleCreate = (): void => {
    createHandler?.();
    setOpen(false);
  };

  return (
    <div className="relative inline-flex min-w-0">
      <button
        type="button"
        className="inline-flex max-w-xs items-center gap-2 rounded-lg px-2 py-1 text-sm font-semibold text-slate-800 transition hover:bg-slate-100 focus:outline-none focus:ring-2 focus:ring-blue-500"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
      >
        {currentWorkspace ? (
          <>
            <span className="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-blue-600 text-[0.65rem] font-bold text-white">
              {getInitials(currentWorkspace.name)}
            </span>
            <span className="truncate">{currentWorkspace.name}</span>
          </>
        ) : (
          <span className="text-slate-500">Select workspace</span>
        )}
        <ChevronDownIcon size={14} aria-hidden="true" />
      </button>

      {open && (
        <div
          role="menu"
          className="absolute left-0 top-full z-50 mt-2 max-h-80 w-64 overflow-y-auto rounded-xl border border-slate-200 bg-white py-1 shadow-xl"
        >
          {workspaces.map((workspace) => {
            const selected = workspace.id === currentWorkspace?.id;
            return (
              <button
                key={workspace.id}
                type="button"
                role="menuitemradio"
                aria-checked={selected}
                className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-slate-700 hover:bg-slate-100 focus:bg-slate-100 focus:outline-none"
                onClick={() => handleSelect(workspace)}
              >
                <span className="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-slate-700 text-[0.65rem] font-bold text-white">
                  {getInitials(workspace.name)}
                </span>
                <span className="min-w-0 flex-1 truncate font-medium">
                  {workspace.name}
                </span>
                {selected && <CheckIcon size={14} aria-hidden="true" />}
              </button>
            );
          })}

          {createHandler && (
            <>
              <div className="my-1 border-t border-slate-200" />
              <button
                type="button"
                role="menuitem"
                className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm font-medium text-blue-700 hover:bg-blue-50 focus:bg-blue-50 focus:outline-none"
                onClick={handleCreate}
              >
                <PlusIcon size={16} aria-hidden="true" />
                New workspace
              </button>
            </>
          )}
        </div>
      )}
    </div>
  );
};
