/**
 * AppShell
 *
 * Root layout wrapper providing the full-page shell:
 * responsive Sidebar (drawer), Header (top bar), and scrollable main content area.
 *
 * Usage:
 * ```tsx
 * <AppShell
 *   workspaces={workspaces}
 *   currentWorkspace={currentWorkspace}
 *   projects={projects}
 *   currentUser={user}
 *   onWorkspaceSelect={handleSelect}
 *   activePath={router.pathname}
 * >
 *   <YourPageContent />
 * </AppShell>
 * ```
 *
 * @doc.type component
 * @doc.purpose Full-page application shell with sidebar + header
 * @doc.layer product
 * @doc.pattern Compound Component / Layout Shell
 */

import { Box, CssBaseline, Toolbar } from '@mui/material';
import React, { useState } from 'react';

import type { Workspace, Project } from 'yappc-core/types';

import { Header, type HeaderUser } from './Header';
import { Sidebar, type NavItem } from './Sidebar';

export interface AppShellProps {
  children: React.ReactNode;
  workspaces?: Workspace[];
  currentWorkspace?: Workspace | null;
  projects?: Project[];
  projectsLoading?: boolean;
  activeProjectId?: string | null;
  currentUser?: HeaderUser | null;
  activePath?: string;
  drawerWidth?: number;
  onWorkspaceSelect?: (workspace: Workspace) => void;
  onCreateWorkspace?: () => void;
  onProjectSelect?: (project: Project) => void;
  onNavItemClick?: (item: NavItem) => void;
  onUserMenuOpen?: (event: React.MouseEvent<HTMLElement>) => void;
  onNotificationsOpen?: (event: React.MouseEvent<HTMLElement>) => void;
  /** Optional slot for extra header action buttons */
  headerActionsSlot?: React.ReactNode;
}

/**
 * Responsive application shell with collapsible sidebar drawer.
 *
 * The sidebar is permanently visible on `sm+` breakpoints and shown
 * as a temporary overlay on mobile (toggleable via the Header).
 */
export const AppShell: React.FC<AppShellProps> = ({
  children,
  workspaces = [],
  currentWorkspace,
  projects = [],
  projectsLoading = false,
  activeProjectId,
  currentUser,
  activePath,
  drawerWidth = 240,
  onWorkspaceSelect,
  onCreateWorkspace,
  onProjectSelect,
  onNavItemClick,
  onUserMenuOpen,
  onNotificationsOpen,
  headerActionsSlot,
}) => {
  const [mobileDrawerOpen, setMobileDrawerOpen] = useState(false);

  const handleWorkspaceSelect = (ws: Workspace) => {
    onWorkspaceSelect?.(ws);
  };

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      <CssBaseline />

      <Header
        workspaces={workspaces}
        currentWorkspace={currentWorkspace}
        currentUser={currentUser}
        onWorkspaceSelect={handleWorkspaceSelect}
        onCreateWorkspace={onCreateWorkspace}
        onUserMenuOpen={onUserMenuOpen}
        onNotificationsOpen={onNotificationsOpen}
        actionsSlot={headerActionsSlot}
        drawerWidth={drawerWidth}
      />

      <Sidebar
        open={mobileDrawerOpen}
        onClose={() => setMobileDrawerOpen(false)}
        drawerWidth={drawerWidth}
        projects={projects}
        projectsLoading={projectsLoading}
        activeProjectId={activeProjectId}
        activePath={activePath}
        onProjectSelect={onProjectSelect}
        onNavItemClick={onNavItemClick}
      />

      <Box
        component="main"
        sx={{
          flexGrow: 1,
          width: { sm: `calc(100% - ${drawerWidth}px)` },
          display: 'flex',
          flexDirection: 'column',
          minHeight: '100vh',
          bgcolor: 'background.default',
        }}
      >
        {/* Offset for fixed AppBar */}
        <Toolbar variant="dense" sx={{ minHeight: 48 }} />
        {children}
      </Box>
    </Box>
  );
};
