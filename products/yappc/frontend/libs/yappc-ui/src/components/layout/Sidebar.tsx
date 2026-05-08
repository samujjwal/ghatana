/**
 * Sidebar
 *
 * Application sidebar with workspace-scoped project navigation
 * and quick links (Canvas, Copilot, Settings).
 *
 * @doc.type component
 * @doc.purpose Application sidebar navigation
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

import {
  Bot as CopilotIcon,
  FolderOpen as ProjectIcon,
  Layers as CanvasIcon,
  LayoutDashboard as DashboardIcon,
  Settings as SettingsIcon,
} from 'lucide-react';
import React from 'react';

import type { Project } from 'yappc-core/types';

export interface NavItem {
  id: string;
  label: string;
  icon: React.ReactNode;
  path?: string;
  onClick?: () => void;
}

export interface SidebarProps {
  open: boolean;
  drawerWidth?: number;
  /** Responsive variant: 'temporary' for mobile, 'permanent' for desktop. */
  variant?: 'temporary' | 'permanent' | 'persistent';
  onClose?: () => void;
  projects?: Project[];
  projectsLoading?: boolean;
  activeProjectId?: string | null;
  activePath?: string;
  onProjectSelect?: (project: Project) => void;
  onNavItemClick?: (item: NavItem) => void;
}

const DEFAULT_NAV_ITEMS: NavItem[] = [
  {
    id: 'dashboard',
    label: 'Dashboard',
    icon: <DashboardIcon size={18} aria-hidden="true" />,
    path: '/',
  },
  {
    id: 'canvas',
    label: 'Canvas',
    icon: <CanvasIcon size={18} aria-hidden="true" />,
    path: '/canvas',
  },
  {
    id: 'copilot',
    label: 'AI Copilot',
    icon: <CopilotIcon size={18} aria-hidden="true" />,
    path: '/copilot',
  },
  {
    id: 'settings',
    label: 'Settings',
    icon: <SettingsIcon size={18} aria-hidden="true" />,
    path: '/settings',
  },
];

function cx(...classes: Array<string | false | null | undefined>): string {
  return classes.filter(Boolean).join(' ');
}

/**
 * Application drawer sidebar with project list and nav links.
 */
export const Sidebar: React.FC<SidebarProps> = ({
  open,
  drawerWidth = 240,
  variant = 'permanent',
  onClose,
  projects = [],
  projectsLoading = false,
  activeProjectId,
  activePath,
  onProjectSelect,
  onNavItemClick,
}) => {
  const isTemporary = variant === 'temporary';
  const isVisible = isTemporary ? open : true;

  if (!isVisible) {
    return null;
  }

  return (
    <nav aria-label="Primary" className="yappc-app-sidebar">
      {isTemporary && (
        <button
          type="button"
          aria-label="Close navigation"
          className="fixed inset-0 z-30 bg-slate-950/40"
          onClick={onClose}
        />
      )}

      <aside
        className={cx(
          'fixed left-0 top-0 z-40 flex h-screen flex-col overflow-hidden border-r border-slate-200 bg-white text-slate-900 shadow-sm',
          isTemporary ? 'shadow-xl' : 'hidden sm:flex'
        )}
        style={{ width: drawerWidth }}
      >
        <div className="px-4 py-3">
          <p className="text-xs font-bold uppercase tracking-[0.18em] text-slate-700">
            YAPPC
          </p>
        </div>

        <div className="border-t border-slate-200 py-1">
          {DEFAULT_NAV_ITEMS.map((item) => {
            const isActive = activePath === item.path;
            return (
              <button
                key={item.id}
                type="button"
                className={cx(
                  'mx-2 my-1 flex w-[calc(100%-1rem)] items-center gap-2 rounded-lg px-3 py-2 text-left text-sm transition focus:outline-none focus:ring-2 focus:ring-blue-500',
                  isActive
                    ? 'bg-blue-50 font-semibold text-blue-700'
                    : 'text-slate-700 hover:bg-slate-100'
                )}
                aria-current={isActive ? 'page' : undefined}
                onClick={() => onNavItemClick?.(item)}
              >
                <span className="flex h-5 w-5 items-center justify-center">
                  {item.icon}
                </span>
                <span>{item.label}</span>
              </button>
            );
          })}
        </div>

        <div className="flex min-h-0 flex-1 flex-col border-t border-slate-200 py-2">
          <p className="px-4 py-1 text-[0.68rem] font-semibold uppercase tracking-[0.16em] text-slate-500">
            Projects
          </p>

          {projectsLoading ? (
            <div className="space-y-2 px-4 py-2" aria-label="Loading projects">
              {[0, 1, 2].map((item) => (
                <div
                  key={item}
                  className="h-8 animate-pulse rounded-lg bg-slate-100"
                />
              ))}
            </div>
          ) : projects.length === 0 ? (
            <p className="px-4 py-2 text-xs text-slate-500">No projects yet</p>
          ) : (
            <div className="min-h-0 flex-1 overflow-y-auto pb-3">
              {projects.map((project) => {
                const isActive = activeProjectId === project.id;
                return (
                  <button
                    key={project.id}
                    type="button"
                    className={cx(
                      'mx-2 my-1 flex w-[calc(100%-1rem)] items-center gap-2 rounded-lg px-3 py-2 text-left text-sm transition focus:outline-none focus:ring-2 focus:ring-blue-500',
                      isActive
                        ? 'bg-slate-900 font-semibold text-white'
                        : 'text-slate-700 hover:bg-slate-100'
                    )}
                    title={project.name}
                    aria-current={isActive ? 'page' : undefined}
                    onClick={() => onProjectSelect?.(project)}
                  >
                    <ProjectIcon size={16} aria-hidden="true" />
                    <span className="truncate">{project.name}</span>
                  </button>
                );
              })}
            </div>
          )}
        </div>
      </aside>
    </nav>
  );
};
