/**
 * Header
 *
 * Application top bar with workspace switcher, user avatar menu,
 * and optional action slot.
 *
 * @doc.type component
 * @doc.purpose Application top navigation bar
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

import {
  AppBar,
  Toolbar,
  Box,
  Typography,
  Avatar,
  IconButton,
  Tooltip,
} from '@mui/material';
import { Bell as BellIcon } from 'lucide-react';
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
  workspaces: Workspace[];
  currentWorkspace?: Workspace | null;
  currentUser?: HeaderUser | null;
  onWorkspaceSelect: (workspace: Workspace) => void;
  onCreateWorkspace?: () => void;
  onUserMenuOpen?: (event: React.MouseEvent<HTMLElement>) => void;
  onNotificationsOpen?: (event: React.MouseEvent<HTMLElement>) => void;
  /** Optional slot for extra action buttons next to the user avatar */
  actionsSlot?: React.ReactNode;
  drawerWidth?: number;
}

/**
 * Application header with workspace switcher and user controls.
 */
export const Header: React.FC<HeaderProps> = ({
  workspaces,
  currentWorkspace,
  currentUser,
  onWorkspaceSelect,
  onCreateWorkspace,
  onUserMenuOpen,
  onNotificationsOpen,
  actionsSlot,
  drawerWidth = 240,
}) => {
  const userInitials = currentUser
    ? (currentUser.name ?? currentUser.email)
        .split(' ')
        .map((w) => w[0])
        .slice(0, 2)
        .join('')
        .toUpperCase()
    : '?';

  return (
    <AppBar
      position="fixed"
      elevation={0}
      sx={{
        width: { sm: `calc(100% - ${drawerWidth}px)` },
        ml: { sm: `${drawerWidth}px` },
        bgcolor: 'background.paper',
        color: 'text.primary',
        borderBottom: 1,
        borderColor: 'divider',
      }}
    >
      <Toolbar variant="dense" sx={{ minHeight: 48, gap: 1 }}>
        {/* Workspace switcher */}
        <WorkspaceSwitcher
          workspaces={workspaces}
          currentWorkspace={currentWorkspace}
          onSelect={onWorkspaceSelect}
          onCreateNew={onCreateWorkspace}
        />

        <Box flexGrow={1} />

        {actionsSlot}

        {onNotificationsOpen && (
          <Tooltip title="Notifications">
            <IconButton size="small" onClick={onNotificationsOpen}>
              <BellIcon size={18} />
            </IconButton>
          </Tooltip>
        )}

        {currentUser && (
          <Tooltip title={currentUser.name ?? currentUser.email}>
            <IconButton size="small" onClick={onUserMenuOpen} sx={{ p: 0.25 }}>
              <Avatar
                src={currentUser.avatarUrl ?? undefined}
                sx={{ width: 28, height: 28, fontSize: 12 }}
              >
                {!currentUser.avatarUrl && userInitials}
              </Avatar>
            </IconButton>
          </Tooltip>
        )}
      </Toolbar>
    </AppBar>
  );
};
