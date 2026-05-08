/**
 * Header
 *
 * Fixed application top bar with workspace switching, notifications, and
 * account actions.
 *
 * @doc.type component
 * @doc.purpose Application header navigation
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

import { Bell as BellIcon, User as UserIcon } from 'lucide-react';
import React from 'react';

import type { Workspace } from 'yappc-core/types';

import { WorkspaceSwitcher } from '../workspace/WorkspaceSwitcher';

export interface HeaderUser {
  id: string;
  name?: string | null;
  email: string;
  avatarUrl?: string | null;
}

export interface HeaderProps {
  workspaces?: Workspace[];
  currentWorkspace?: Workspace | null;
  currentUser?: HeaderUser | null;
  drawerWidth?: number;
  actionsSlot?: React.ReactNode;
  onWorkspaceSelect?: (workspace: Workspace) => void;
  onCreateWorkspace?: () => void;
  onUserMenuOpen?: (event: React.MouseEvent<HTMLElement>) => void;
  onNotificationsOpen?: (event: React.MouseEvent<HTMLElement>) => void;
}

const HEADER_HEIGHT = 48;

function getUserInitials(user: HeaderUser): string {
  const displayName = user.name?.trim();
  if (displayName) {
    return displayName
      .split(/\s+/)
      .slice(0, 2)
      .map((part) => part[0]?.toUpperCase() ?? '')
      .join('');
  }

  return user.email.slice(0, 2).toUpperCase();
}

/**
 * Application header for workspace and account controls.
 */
export const Header: React.FC<HeaderProps> = ({
  workspaces = [],
  currentWorkspace,
  currentUser,
  drawerWidth = 240,
  actionsSlot,
  onWorkspaceSelect,
  onCreateWorkspace,
  onUserMenuOpen,
  onNotificationsOpen,
}) => {
  const headerStyle: React.CSSProperties = {
    minHeight: HEADER_HEIGHT,
    marginLeft: drawerWidth,
    width: `calc(100% - ${drawerWidth}px)`,
  };

  return (
    <header
      className="yappc-app-header fixed right-0 top-0 z-40 flex items-center justify-between border-b border-slate-200 bg-white/95 px-3 shadow-sm backdrop-blur"
      style={headerStyle}
    >
      <div className="flex min-w-0 flex-1 items-center gap-3">
        <WorkspaceSwitcher
          workspaces={workspaces}
          currentWorkspace={currentWorkspace}
          onSelect={onWorkspaceSelect}
          onCreate={onCreateWorkspace}
        />
      </div>

      <div className="flex shrink-0 items-center gap-2">
        {actionsSlot}

        {onNotificationsOpen && (
          <button
            type="button"
            aria-label="Notifications"
            title="Notifications"
            className="inline-flex h-8 w-8 items-center justify-center rounded-full border border-transparent text-slate-600 transition hover:border-slate-200 hover:bg-slate-100 focus:outline-none focus:ring-2 focus:ring-blue-500"
            onClick={onNotificationsOpen}
          >
            <BellIcon size={18} aria-hidden="true" />
          </button>
        )}

        {currentUser && (
          <button
            type="button"
            aria-label="Open user menu"
            title={currentUser.name ?? currentUser.email}
            className="inline-flex h-8 w-8 items-center justify-center overflow-hidden rounded-full border border-slate-200 bg-slate-100 text-xs font-semibold text-slate-700 transition hover:border-slate-300 hover:bg-slate-200 focus:outline-none focus:ring-2 focus:ring-blue-500"
            onClick={onUserMenuOpen}
          >
            {currentUser.avatarUrl ? (
              <img
                src={currentUser.avatarUrl}
                alt=""
                className="h-full w-full object-cover"
              />
            ) : currentUser.name || currentUser.email ? (
              getUserInitials(currentUser)
            ) : (
              <UserIcon size={16} aria-hidden="true" />
            )}
          </button>
        )}
      </div>
    </header>
  );
};
