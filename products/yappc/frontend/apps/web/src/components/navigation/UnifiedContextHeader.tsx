/**
 * Unified Context Header Component
 *
 * Single, intelligent header that adapts to all contexts:
 * - Combines logo, navigation, actions, and user sections
 * - 56px consistent height across all screens
 * - Context-aware action buttons
 * - Smooth transitions between states
 * - Full keyboard navigation support
 *
 * Replaces:
 * - UnifiedHeaderBar (global)
 * - Project header (app/project/_shell.tsx)
 * - UnifiedTopBar (canvas)
 *
 * Features consolidated from all 3 previous bars:
 * - Logo with home link
 * - Workspace/Project breadcrumb navigation
 * - Canvas mode selector (when on canvas)
 * - Phase indicator (when in project)
 * - Context-aware action buttons (undo/redo, export, share, etc.)
 * - AI/Agent status indicators
 * - Quick actions (New button)
 * - Notifications with badge
 * - Theme toggle
 * - User menu with profile/logout
 *
 * @doc.type component
 * @doc.purpose Single unified header for all contexts
 * @doc.layer components
 */

import React from 'react';
import { Link } from 'react-router';
import { Avatar, IconButton, Menu, MenuItem, ListItemIcon, ListItemText, Divider, Tooltip, Button, Chip } from '@ghatana/ui';
import { LogOut as Logout, UserCircle as AccountCircle, Moon as DarkMode, Sun as LightMode, HelpCircle as HelpOutline, Command as KeyboardCommandKey, Sparkles as AutoAwesome, Zap as Bolt } from 'lucide-react';
import {
  NavigationBreadcrumb,
  type CanvasMode,
  type WorkspaceInfo,
  type ProjectInfo,
} from './NavigationBreadcrumb';
import {
  ActionsToolbar,
  type Action,
  type ActionContext,
} from './ActionsToolbar';
import { NewButton } from './QuickActionsPanel';
import { AgentActivityBadge } from '../workspace/AgentActivityBadge';
import type { HeaderRoleInfo } from '../../state/atoms/layoutAtom';
import { cn } from '../../lib/utils';

export interface UserInfo {
  id: string;
  name: string;
  email: string;
  avatar?: string;
  initials?: string;
}

export interface PhaseInfo {
  phase: string;
  label: string;
  progress?: number;
  status?: 'active' | 'completed' | 'pending';
}

export interface UnifiedContextHeaderProps {
  /** Current user */
  user?: UserInfo;
  /** Current workspace */
  workspace?: WorkspaceInfo;
  /** Current project */
  project?: ProjectInfo;
  /** Current section name */
  section?: string;
  /** Canvas mode (when on canvas) */
  canvasMode?: CanvasMode;
  /** Available workspaces */
  workspaces?: WorkspaceInfo[];
  /** Available projects */
  projects?: ProjectInfo[];
  /** Show canvas mode selector */
  showCanvasMode?: boolean;
  /** Current action context */
  actionContext?: ActionContext;
  /** Global actions */
  globalActions?: Action[];
  /** Context-specific actions */
  contextActions?: Action[];
  /** Notification count */
  notificationCount?: number;
  /** Dark mode enabled */
  darkMode?: boolean;
  /** Show agent activity badge */
  showAgentActivity?: boolean;
  /** Current phase info (for project context) */
  phaseInfo?: PhaseInfo;
  /** Current role info (for canvas context) */
  roleInfo?: HeaderRoleInfo;
  /** Callback when canvas mode changes */
  onCanvasModeChange?: (mode: CanvasMode) => void;
  /** Callback for new item */
  onNew?: () => void;
  /** Callback for search */
  onSearch?: () => void;
  /** Callback for notifications */
  onNotifications?: () => void;
  /** Callback for help */
  onHelp?: () => void;
  /** Callback for keyboard shortcuts */
  onKeyboardShortcuts?: () => void;
  /** Callback for theme toggle */
  onThemeToggle?: () => void;
  /** Callback for profile */
  onProfile?: () => void;
  /** Callback for logout */
  onLogout?: () => void;
  /** Callback for creating workspace */
  onCreateWorkspace?: () => void;
  /** Callback for creating project */
  onCreateProject?: () => void;
  /** Callback for creating workflow */
  onCreateWorkflow?: () => void;
  /** Additional CSS classes */
  className?: string;
}

/**
 * Unified Context Header Component
 */
export function UnifiedContextHeader({
  user,
  workspace,
  project,
  section,
  canvasMode,
  workspaces = [],
  projects = [],
  showCanvasMode = false,
  actionContext = 'global',
  globalActions = [],
  contextActions = [],
  notificationCount = 0,
  darkMode = false,
  showAgentActivity = false,
  phaseInfo,
  roleInfo,
  onCanvasModeChange,
  onNew,
  onSearch,
  onNotifications,
  onHelp,
  onKeyboardShortcuts,
  onThemeToggle,
  onProfile,
  onLogout,
  onCreateWorkspace,
  onCreateProject,
  onCreateWorkflow,
  className,
}: UnifiedContextHeaderProps) {
  const [userMenuAnchor, setUserMenuAnchor] =
    React.useState<null | HTMLElement>(null);

  const handleUserMenuOpen = (event: React.MouseEvent<HTMLButtonElement>) => {
    setUserMenuAnchor(event.currentTarget);
  };

  const handleUserMenuClose = () => {
    setUserMenuAnchor(null);
  };

  const handleProfileClick = () => {
    onProfile?.();
    handleUserMenuClose();
  };

  const handleLogoutClick = () => {
    onLogout?.();
    handleUserMenuClose();
  };

  return (
    <header
      className={cn(
        'flex items-center justify-between',
        'h-14 px-4 border-b border-divider',
        'bg-bg-default sticky top-0 z-50',
        className
      )}
      role="banner"
    >
      {/* Left Section: Logo + Navigation */}
      <div className="flex items-center gap-4 flex-1 overflow-hidden">
        {/* Logo */}
        <Link
          to="/app"
          className="flex items-center gap-2 flex-shrink-0 group no-underline"
          aria-label="YAPPC Home"
        >
          <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-primary-500 to-primary-600 flex items-center justify-center transition-transform group-hover:scale-105">
            <span className="text-white font-bold text-lg">Y</span>
          </div>
          <span className="hidden md:inline font-semibold text-text-primary">
            YAPPC
          </span>
        </Link>

        {/* Navigation Breadcrumb */}
        <NavigationBreadcrumb
          workspace={workspace}
          project={project}
          section={section}
          canvasMode={canvasMode}
          workspaces={workspaces}
          projects={projects}
          showCanvasMode={showCanvasMode}
          onCanvasModeChange={onCanvasModeChange}
          className="min-w-0"
        />
      </div>

      {/* Right Section: Actions + User */}
      <div className="flex items-center gap-2 flex-shrink-0">
        {/* Phase Badge (when in project context) */}
        {phaseInfo && (
          <Chip
            icon={<Bolt className="text-base" />}
            label={`${phaseInfo.label}${phaseInfo.progress ? ` ${phaseInfo.progress}%` : ''}`}
            size="sm"
            color={
              phaseInfo.status === 'completed'
                ? 'success'
                : phaseInfo.status === 'active'
                  ? 'primary'
                  : 'default'
            }
            className="text-xs font-semibold h-[28px]"
          />
        )}

        {/* Role Badge (when in canvas context) */}
        {roleInfo && (
          <Chip
            icon={<span className="flex items-center">{roleInfo.icon}</span>}
            label={roleInfo.label}
            size="sm"
            className="h-[28px] text-xs font-semibold" style={{ backgroundColor: roleInfo.color || '#f0f0f0' }}
          />
        )}

        {/* Agent Activity Badge */}
        {showAgentActivity && <AgentActivityBadge size="sm" />}

        {/* Vertical Divider */}
        {(showAgentActivity || phaseInfo) && (
          <div className="h-6 w-px bg-divider" />
        )}

        {/* Actions Toolbar */}
        <ActionsToolbar
          context={actionContext}
          globalActions={globalActions}
          contextActions={contextActions}
          notificationCount={notificationCount}
          onSearch={onSearch}
          onNotifications={onNotifications}
        />

        {/* New Button */}
        {onNew && (
          <NewButton
            onCreateProject={onCreateProject}
            onCreateWorkflow={onCreateWorkflow}
            onCreateWorkspace={onCreateWorkspace}
            variant="default"
          />
        )}

        {/* Vertical Divider */}
        <div className="h-6 w-px bg-divider" />

        {/* Help */}
        {onHelp && (
          <Tooltip title="Help & Documentation" arrow>
            <IconButton
              size="sm"
              onClick={onHelp}
              className="hidden lg:inline-flex"
              aria-label="Help"
            >
              <HelpOutline />
            </IconButton>
          </Tooltip>
        )}

        {/* Keyboard Shortcuts */}
        {onKeyboardShortcuts && (
          <Tooltip title="Keyboard Shortcuts" arrow>
            <IconButton
              size="sm"
              onClick={onKeyboardShortcuts}
              className="hidden lg:inline-flex"
              aria-label="Keyboard shortcuts"
            >
              <KeyboardCommandKey />
            </IconButton>
          </Tooltip>
        )}

        {/* Theme Toggle */}
        {onThemeToggle && (
          <Tooltip
            title={darkMode ? 'Switch to light mode' : 'Switch to dark mode'}
            arrow
          >
            <IconButton
              size="sm"
              onClick={onThemeToggle}
              className="hidden sm:inline-flex"
              aria-label="Toggle theme"
            >
              {darkMode ? <LightMode /> : <DarkMode />}
            </IconButton>
          </Tooltip>
        )}

        {/* User Menu */}
        {user && (
          <>
            <Tooltip title="Account" arrow>
              <IconButton
                onClick={handleUserMenuOpen}
                size="sm"
                aria-label="User menu"
                aria-controls={
                  Boolean(userMenuAnchor) ? 'user-menu' : undefined
                }
                aria-haspopup="true"
                aria-expanded={Boolean(userMenuAnchor)}
              >
                <Avatar
                  src={user.avatar}
                  alt={user.name}
                  className="w-[32px] h-[32px]"
                >
                  {user.initials || user.name.charAt(0).toUpperCase()}
                </Avatar>
              </IconButton>
            </Tooltip>

            <Menu
              id="user-menu"
              anchorEl={userMenuAnchor}
              open={Boolean(userMenuAnchor)}
              onClose={handleUserMenuClose}
              anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
              transformOrigin={{ vertical: 'top', horizontal: 'right' }}
              PaperProps={{
                sx: { minWidth: 240, mt: 1 },
              }}
            >
              {/* User Info */}
              <div className="px-4 py-3 border-b border-border-subtle">
                <div className="text-sm font-medium text-text-primary">
                  {user.name}
                </div>
                <div className="text-xs text-text-secondary truncate">
                  {user.email}
                </div>
              </div>

              {/* Profile */}
              {onProfile && (
                <MenuItem onClick={handleProfileClick}>
                  <ListItemIcon>
                    <AccountCircle size={16} />
                  </ListItemIcon>
                  <ListItemText>Profile</ListItemText>
                </MenuItem>
              )}

              {/* Theme Toggle (mobile) */}
              {onThemeToggle && (
                <MenuItem onClick={onThemeToggle} className="sm:hidden">
                  <ListItemIcon>
                    {darkMode ? (
                      <LightMode size={16} />
                    ) : (
                      <DarkMode size={16} />
                    )}
                  </ListItemIcon>
                  <ListItemText>
                    {darkMode ? 'Light Mode' : 'Dark Mode'}
                  </ListItemText>
                </MenuItem>
              )}

              {/* Help (mobile) */}
              {onHelp && (
                <MenuItem onClick={onHelp} className="lg:hidden">
                  <ListItemIcon>
                    <HelpOutline size={16} />
                  </ListItemIcon>
                  <ListItemText>Help & Documentation</ListItemText>
                </MenuItem>
              )}

              {/* Keyboard Shortcuts (mobile) */}
              {onKeyboardShortcuts && (
                <MenuItem onClick={onKeyboardShortcuts} className="lg:hidden">
                  <ListItemIcon>
                    <KeyboardCommandKey size={16} />
                  </ListItemIcon>
                  <ListItemText>Keyboard Shortcuts</ListItemText>
                </MenuItem>
              )}

              <Divider />

              {/* Logout */}
              {onLogout && (
                <MenuItem onClick={handleLogoutClick}>
                  <ListItemIcon>
                    <Logout size={16} />
                  </ListItemIcon>
                  <ListItemText>Logout</ListItemText>
                </MenuItem>
              )}
            </Menu>
          </>
        )}
      </div>
    </header>
  );
}

export default UnifiedContextHeader;
