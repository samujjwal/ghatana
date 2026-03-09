/**
 * TopNav Component
 *
 * A navigation bar for the DevSecOps Canvas with navigation links,
 * notifications, and user profile.
 *
 * @module DevSecOps/TopNav
 */

import { BarChart3 as AssessmentIcon } from 'lucide-react';
import { LayoutDashboard as DashboardIcon } from 'lucide-react';
import { Bell as NotificationsIcon } from 'lucide-react';
import { Settings as SettingsIcon } from 'lucide-react';
import { Activity as TimelineIcon } from 'lucide-react';
import { Users as PeopleIcon } from 'lucide-react';
import { LayoutDashboard as ViewQuiltIcon } from 'lucide-react';
import { Kanban as ViewKanbanIcon } from 'lucide-react';
import { GitBranch as AccountTreeIcon } from 'lucide-react';
import { AppBar, Avatar, Badge, Box, Button, IconButton, Toolbar, Typography } from '@ghatana/ui';

import type { NavigationPage, TopNavProps } from './types';
import type React from 'react';

/**
 * TopNav - Primary navigation bar
 *
 * Provides top-level navigation for the DevSecOps Canvas with support for
 * notifications, user profile, and active page highlighting.
 *
 * @param props - TopNav component props
 * @returns Rendered TopNav component
 *
 * @example
 * ```tsx
 * <TopNav
 *   currentPage="dashboard"
 *   onNavigate={(page) => navigate(page)}
 *   notificationCount={3}
 *   user={{ name: 'Jane Smith', role: 'Developer' }}
 * />
 * ```
 */
export const TopNav: React.FC<TopNavProps> = ({
  currentPage = 'dashboard',
  onNavigate,
  notificationCount = 0,
  user,
  onProfileClick,
  onNotificationsClick,
}) => {
  const handleNavClick = (page: NavigationPage) => {
    onNavigate?.(page);
  };

  return (
    <AppBar
      position="sticky"
      variant="raised"
      className="bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 border-b border-gray-200 dark:border-gray-700"
    >
      <Toolbar className="gap-4 h-[64px]">
        {/* Logo / Brand */}
        <Box className="flex items-center gap-2">
          <TimelineIcon
            className="text-blue-600"
            style={{
              fontSize: 32,
              background: 'linear-gradient(135deg, #2563eb, #14b8a6)',
              backgroundClip: 'text',
              WebkitBackgroundClip: 'text',
              WebkitTextFillColor: 'transparent',
            }}
          />
          <Typography
            as="h6"
            className="font-bold" >
            DevSecOps Canvas
          </Typography>
        </Box>

        <Box className="grow" />

        {/* Navigation Buttons */}
        <Button
          startIcon={<DashboardIcon />}
          variant={currentPage === 'dashboard' ? 'contained' : 'text'}
          onClick={() => handleNavClick('dashboard')}
          className="min-w-[120px] transition-all [transition-duration:var(--ds-duration-base)] [transition-timing-function:var(--ds-ease-in-out)] hover:-translate-y-0.5"
          aria-current={currentPage === 'dashboard' ? 'page' : undefined}
        >
          Dashboard
        </Button>

        <Button
          startIcon={<TimelineIcon />}
          variant={currentPage === 'phases' ? 'contained' : 'text'}
          onClick={() => handleNavClick('phases')}
          className="min-w-[120px] transition-all [transition-duration:var(--ds-duration-base)] [transition-timing-function:var(--ds-ease-in-out)] hover:-translate-y-0.5"
          aria-current={currentPage === 'phases' ? 'page' : undefined}
        >
          Phases
        </Button>

        <Button
          startIcon={<PeopleIcon />}
          variant={currentPage === 'persona' ? 'contained' : 'text'}
          onClick={() => handleNavClick('persona')}
          className="min-w-[120px] transition-all [transition-duration:var(--ds-duration-base)] [transition-timing-function:var(--ds-ease-in-out)] hover:-translate-y-0.5"
          aria-current={currentPage === 'persona' ? 'page' : undefined}
        >
          Persona
        </Button>

        <Button
          startIcon={<AccountTreeIcon />}
          variant={currentPage === 'workflows' ? 'contained' : 'text'}
          onClick={() => handleNavClick('workflows')}
          className="min-w-[120px] transition-all [transition-duration:var(--ds-duration-base)] [transition-timing-function:var(--ds-ease-in-out)] hover:-translate-y-0.5"
          aria-current={currentPage === 'workflows' ? 'page' : undefined}
        >
          Workflows
        </Button>

        <Button
          startIcon={<AssessmentIcon />}
          variant={currentPage === 'reports' ? 'contained' : 'text'}
          onClick={() => handleNavClick('reports')}
          className="min-w-[120px] transition-all [transition-duration:var(--ds-duration-base)] [transition-timing-function:var(--ds-ease-in-out)] hover:-translate-y-0.5"
          aria-current={currentPage === 'reports' ? 'page' : undefined}
        >
          Reports
        </Button>

        <Button
          startIcon={<ViewKanbanIcon />}
          variant={currentPage === 'task-board' ? 'contained' : 'text'}
          onClick={() => handleNavClick('task-board')}
          className="min-w-[140px] transition-all [transition-duration:var(--ds-duration-base)] [transition-timing-function:var(--ds-ease-in-out)] hover:-translate-y-0.5"
          aria-current={currentPage === 'task-board' ? 'page' : undefined}
        >
          Task Board
        </Button>

        <Button
          startIcon={<ViewQuiltIcon />}
          variant={currentPage === 'canvas' ? 'contained' : 'text'}
          onClick={() => handleNavClick('canvas')}
          className="min-w-[120px] transition-all [transition-duration:var(--ds-duration-base)] [transition-timing-function:var(--ds-ease-in-out)] hover:-translate-y-0.5"
          aria-current={currentPage === 'canvas' ? 'page' : undefined}
        >
          Canvas
        </Button>

        <Box style={{ flexGrow: 0.5 }} />

        {/* Settings */}
        <IconButton
          aria-label="Settings"
          onClick={() => handleNavClick('settings')}
          className={currentPage === 'settings' ? 'text-blue-600' : 'text-gray-500'}
        >
          <SettingsIcon />
        </IconButton>

        {/* Notifications */}
        <IconButton
          aria-label={`${notificationCount} notifications`}
          onClick={onNotificationsClick}
        >
          <Badge badgeContent={notificationCount} tone="danger">
            <NotificationsIcon />
          </Badge>
        </IconButton>

        {/* User Profile */}
        {user && (
          <Box
            className="flex items-center gap-2 cursor-pointer hover:opacity-80"
            onClick={onProfileClick}
          >
            <Box className="text-right hidden md:block">
              <Typography as="p" className="text-sm" fontWeight={600}>
                {user.name}
              </Typography>
              <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                {user.role}
              </Typography>
            </Box>
            <Avatar
              src={user.avatar}
              alt={user.name}
              className="w-10 h-10 border-2 border-solid border-blue-600 transition-all [transition-duration:var(--ds-duration-base)] [transition-timing-function:var(--ds-ease-in-out)] hover:scale-105 hover:[box-shadow:var(--ds-shadow-md)]"
            >
              {user.name.charAt(0)}
            </Avatar>
          </Box>
        )}
      </Toolbar>
    </AppBar>
  );
};
