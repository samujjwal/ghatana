/**
 * Project Layout
 *
 * @description Layout for project-scoped pages with phase navigation,
 * project header, and contextual sidebar.
 */

import React, { useState, useCallback, useMemo } from 'react';
import { Outlet, NavLink, useParams } from 'react-router';
import { useAtomValue, useSetAtom } from 'jotai';
import { AnimatePresence, motion } from 'framer-motion';
import {
  Sparkles,
  Settings2,
  Code2,
  Activity,
  Users,
  Shield,
  ChevronLeft,
  ChevronRight,
  MoreVertical,
  Star,
  Share2,
  Settings,
  Trash2,
  Bell,
  Search,
  Command,
  GitBranch,
  Cloud,
  Circle,
} from 'lucide-react';

import { cn } from '../utils/cn';
import {
  activeProjectAtom,
  projectPhaseAtom,
  currentUserAtom,
} from '../state/atoms';
import { ROUTES } from '../router/paths';
import { usePhaseNavigation, useBreadcrumbs } from '../router/hooks';

// =============================================================================
// Types
// =============================================================================

interface PhaseNavItem {
  id: string;
  label: string;
  icon: React.ReactNode;
  path: (projectId: string) => string;
  description: string;
}

// =============================================================================
// Phase Navigation Configuration
// =============================================================================

const phaseNavItems: PhaseNavItem[] = [
  {
    id: 'bootstrap',
    label: 'Bootstrap',
    icon: <Sparkles className="w-5 h-5" />,
    path: (id) => ROUTES.bootstrap.root(id),
    description: 'AI-powered project scaffolding',
  },
  {
    id: 'setup',
    label: 'Setup',
    icon: <Settings2 className="w-5 h-5" />,
    path: (id) => ROUTES.setup.root(id),
    description: 'Infrastructure & environment',
  },
  {
    id: 'development',
    label: 'Development',
    icon: <Code2 className="w-5 h-5" />,
    path: (id) => ROUTES.development.root(id),
    description: 'Sprint boards & code',
  },
  {
    id: 'operations',
    label: 'Operations',
    icon: <Activity className="w-5 h-5" />,
    path: (id) => ROUTES.operations.root(id),
    description: 'Monitoring & incidents',
  },
  {
    id: 'team',
    label: 'Team',
    icon: <Users className="w-5 h-5" />,
    path: (id) => ROUTES.team.root(id),
    description: 'Collaboration & knowledge',
  },
  {
    id: 'security',
    label: 'Security',
    icon: <Shield className="w-5 h-5" />,
    path: (id) => ROUTES.security.root(id),
    description: 'Vulnerabilities & compliance',
  },
];

// =============================================================================
// Sub-navigation items per phase
// =============================================================================

const phaseSubNavItems: Record<string, { label: string; path: (id: string) => string }[]> = {
  bootstrap: [
    { label: 'AI Chat', path: (id) => ROUTES.bootstrap.root(id) },
    { label: 'Preview', path: (id) => ROUTES.bootstrap.preview(id) },
  ],
  setup: [
    { label: 'Wizard', path: (id) => ROUTES.setup.root(id) },
    { label: 'Infrastructure', path: (id) => ROUTES.setup.infrastructure(id) },
    { label: 'Environments', path: (id) => ROUTES.setup.environments(id) },
    { label: 'Team', path: (id) => ROUTES.setup.team(id) },
  ],
  development: [
    { label: 'Dashboard', path: (id) => ROUTES.development.root(id) },
    { label: 'Board', path: (id) => ROUTES.development.board(id) },
    { label: 'Backlog', path: (id) => ROUTES.development.backlog(id) },
    { label: 'Epics', path: (id) => ROUTES.development.epics(id) },
    { label: 'PRs', path: (id) => ROUTES.development.prs(id) },
    { label: 'Flags', path: (id) => ROUTES.development.flags(id) },
    { label: 'Deployments', path: (id) => ROUTES.development.deployments(id) },
  ],
  operations: [
    { label: 'Dashboard', path: (id) => ROUTES.operations.root(id) },
    { label: 'Incidents', path: (id) => ROUTES.operations.incidents(id) },
    { label: 'Alerts', path: (id) => ROUTES.operations.alerts(id) },
    { label: 'Dashboards', path: (id) => ROUTES.operations.dashboards(id) },
    { label: 'Logs', path: (id) => ROUTES.operations.logs(id) },
    { label: 'Metrics', path: (id) => ROUTES.operations.metrics(id) },
    { label: 'Runbooks', path: (id) => ROUTES.operations.runbooks(id) },
    { label: 'On-Call', path: (id) => ROUTES.operations.oncall(id) },
  ],
  team: [
    { label: 'Hub', path: (id) => ROUTES.team.root(id) },
    { label: 'Calendar', path: (id) => ROUTES.team.calendar(id) },
    { label: 'Knowledge', path: (id) => ROUTES.team.knowledge(id) },
    { label: 'Standups', path: (id) => ROUTES.team.standups(id) },
    { label: 'Retros', path: (id) => ROUTES.team.retros(id) },
    { label: 'Messages', path: (id) => ROUTES.team.messages(id) },
    { label: 'Goals', path: (id) => ROUTES.team.goals(id) },
  ],
  security: [
    { label: 'Dashboard', path: (id) => ROUTES.security.root(id) },
    { label: 'Vulnerabilities', path: (id) => ROUTES.security.vulnerabilities(id) },
    { label: 'Scans', path: (id) => ROUTES.security.scans(id) },
    { label: 'Compliance', path: (id) => ROUTES.security.compliance(id) },
    { label: 'Secrets', path: (id) => ROUTES.security.secrets(id) },
    { label: 'Policies', path: (id) => ROUTES.security.policies(id) },
    { label: 'Audit', path: (id) => ROUTES.security.audit(id) },
  ],
};

// =============================================================================
// Project Header Component
// =============================================================================

const ProjectHeader: React.FC = () => {
  const activeProject = useAtomValue(activeProjectAtom);
  const [menuOpen, setMenuOpen] = useState(false);
  const breadcrumbs = useBreadcrumbs();

  if (!activeProject) return null;

  const statusColors: Record<string, string> = {
    active: 'bg-emerald-500',
    paused: 'bg-amber-500',
    archived: 'bg-zinc-500',
    setup: 'bg-violet-500',
  };

  return (
    <div className="flex items-center justify-between py-4 px-6 bg-zinc-900/50 border-b border-zinc-800">
      {/* Left: Project Info */}
      <div className="flex items-center gap-4">
        <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-violet-500 to-fuchsia-500 flex items-center justify-center text-white text-lg font-bold">
          {activeProject.name.charAt(0).toUpperCase()}
        </div>
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-lg font-semibold text-white">{activeProject.name}</h1>
            <span
              className={cn(
                'w-2 h-2 rounded-full',
                statusColors[activeProject.status] || 'bg-zinc-500'
              )}
            />
          </div>
          <div className="flex items-center gap-2 text-sm text-zinc-400">
            {breadcrumbs.map((crumb, i) => (
              <React.Fragment key={i}>
                {i > 0 && <span className="text-zinc-600">/</span>}
                {crumb.path ? (
                  <NavLink to={crumb.path} className="hover:text-zinc-300 transition-colors">
                    {crumb.label}
                  </NavLink>
                ) : (
                  <span>{crumb.label}</span>
                )}
              </React.Fragment>
            ))}
          </div>
        </div>
      </div>

      {/* Right: Actions */}
      <div className="flex items-center gap-2">
        {/* Environment indicator */}
        <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-zinc-800 border border-zinc-700">
          <Cloud className="w-4 h-4 text-emerald-400" />
          <span className="text-sm text-zinc-300">Production</span>
        </div>

        {/* Branch indicator */}
        <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-zinc-800 border border-zinc-700">
          <GitBranch className="w-4 h-4 text-violet-400" />
          <span className="text-sm text-zinc-300">main</span>
        </div>

        {/* Star */}
        <button
          className="p-2 rounded-lg hover:bg-zinc-800 text-zinc-400 hover:text-amber-400 transition-colors"
          aria-label="Star project"
        >
          <Star className="w-5 h-5" />
        </button>

        {/* Share */}
        <button
          className="p-2 rounded-lg hover:bg-zinc-800 text-zinc-400 hover:text-white transition-colors"
          aria-label="Share project"
        >
          <Share2 className="w-5 h-5" />
        </button>

        {/* Search */}
        <button
          className="p-2 rounded-lg hover:bg-zinc-800 text-zinc-400 hover:text-white transition-colors"
          aria-label="Search in project"
        >
          <Search className="w-5 h-5" />
        </button>

        {/* Notifications */}
        <button
          className="p-2 rounded-lg hover:bg-zinc-800 text-zinc-400 hover:text-white transition-colors relative"
          aria-label="Project notifications"
        >
          <Bell className="w-5 h-5" />
          <span className="absolute top-1 right-1 w-2 h-2 bg-red-500 rounded-full" />
        </button>

        {/* More Menu */}
        <div className="relative">
          <button
            onClick={() => setMenuOpen(!menuOpen)}
            className="p-2 rounded-lg hover:bg-zinc-800 text-zinc-400 hover:text-white transition-colors"
            aria-label="More options"
          >
            <MoreVertical className="w-5 h-5" />
          </button>

          <AnimatePresence>
            {menuOpen && (
              <>
                <div
                  className="fixed inset-0 z-10"
                  onClick={() => setMenuOpen(false)}
                />
                <motion.div
                  initial={{ opacity: 0, scale: 0.95, y: -10 }}
                  animate={{ opacity: 1, scale: 1, y: 0 }}
                  exit={{ opacity: 0, scale: 0.95, y: -10 }}
                  className="absolute right-0 top-full mt-2 w-48 bg-zinc-800 border border-zinc-700 rounded-lg shadow-xl z-20 py-1"
                >
                  <button className="w-full flex items-center gap-2 px-4 py-2 text-sm text-zinc-300 hover:bg-zinc-700 transition-colors">
                    <Settings className="w-4 h-4" />
                    Project Settings
                  </button>
                  <button className="w-full flex items-center gap-2 px-4 py-2 text-sm text-zinc-300 hover:bg-zinc-700 transition-colors">
                    <Users className="w-4 h-4" />
                    Manage Team
                  </button>
                  <hr className="my-1 border-zinc-700" />
                  <button className="w-full flex items-center gap-2 px-4 py-2 text-sm text-red-400 hover:bg-zinc-700 transition-colors">
                    <Trash2 className="w-4 h-4" />
                    Archive Project
                  </button>
                </motion.div>
              </>
            )}
          </AnimatePresence>
        </div>
      </div>
    </div>
  );
};

// =============================================================================
// Phase Navigation Component
// =============================================================================

interface PhaseNavProps {
  collapsed: boolean;
  onToggle: () => void;
}

const PhaseNav: React.FC<PhaseNavProps> = ({ collapsed, onToggle }) => {
  const { projectId } = useParams<{ projectId: string }>();
  const { currentPhase } = usePhaseNavigation();

  const currentSubItems = currentPhase ? phaseSubNavItems[currentPhase] : [];

  return (
    <aside
      className={cn(
        'fixed left-0 top-0 h-full bg-zinc-900 border-r border-zinc-800',
        'flex flex-col transition-all duration-300 z-40',
        collapsed ? 'w-16' : 'w-56'
      )}
    >
      {/* Back to Dashboard */}
      <div className="h-14 flex items-center justify-between px-3 border-b border-zinc-800">
        <NavLink
          to={ROUTES.DASHBOARD}
          className={cn(
            'flex items-center gap-2 text-zinc-400 hover:text-white transition-colors',
            collapsed && 'justify-center w-full'
          )}
        >
          <ChevronLeft className="w-5 h-5" />
          {!collapsed && <span className="text-sm font-medium">Dashboard</span>}
        </NavLink>
        {!collapsed && (
          <button
            onClick={onToggle}
            className="p-1.5 rounded hover:bg-zinc-800 text-zinc-500 hover:text-white transition-colors"
          >
            <ChevronLeft className="w-4 h-4" />
          </button>
        )}
      </div>

      {/* Phase Navigation */}
      <nav className="flex-1 overflow-y-auto py-4 px-2">
        <div className="space-y-1">
          {phaseNavItems.map((item) => (
            <NavLink
              key={item.id}
              to={projectId ? item.path(projectId) : '#'}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-3 px-3 py-2.5 rounded-lg transition-all',
                  'text-zinc-400 hover:text-white hover:bg-zinc-800',
                  (isActive || currentPhase === item.id) &&
                    'bg-violet-500/10 text-violet-400 border border-violet-500/20',
                  collapsed && 'justify-center px-2'
                )
              }
              title={collapsed ? item.label : undefined}
            >
              {item.icon}
              {!collapsed && (
                <div className="flex-1 min-w-0">
                  <div className="text-sm font-medium">{item.label}</div>
                </div>
              )}
            </NavLink>
          ))}
        </div>

        {/* Sub-navigation for current phase */}
        {!collapsed && currentSubItems.length > 0 && (
          <div className="mt-6 pt-4 border-t border-zinc-800">
            <div className="px-3 mb-2">
              <span className="text-xs font-medium text-zinc-500 uppercase tracking-wider">
                {currentPhase}
              </span>
            </div>
            <div className="space-y-0.5">
              {currentSubItems.map((item) => (
                <NavLink
                  key={item.label}
                  to={projectId ? item.path(projectId) : '#'}
                  end
                  className={({ isActive }) =>
                    cn(
                      'flex items-center gap-2 px-3 py-2 rounded-lg text-sm transition-colors',
                      'text-zinc-500 hover:text-zinc-300 hover:bg-zinc-800/50',
                      isActive && 'text-white bg-zinc-800'
                    )
                  }
                >
                  <Circle className="w-1.5 h-1.5" />
                  {item.label}
                </NavLink>
              ))}
            </div>
          </div>
        )}
      </nav>

      {/* Toggle Button (collapsed state) */}
      {collapsed && (
        <div className="p-3 border-t border-zinc-800">
          <button
            onClick={onToggle}
            className="w-full p-2 rounded-lg hover:bg-zinc-800 text-zinc-400 hover:text-white transition-colors flex items-center justify-center"
          >
            <ChevronRight className="w-5 h-5" />
          </button>
        </div>
      )}
    </aside>
  );
};

// =============================================================================
// Project Layout Component
// =============================================================================

const ProjectLayout: React.FC = () => {
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);

  const handleToggleSidebar = useCallback(() => {
    setSidebarCollapsed((prev) => !prev);
  }, []);

  return (
    <div className="min-h-screen bg-zinc-950">
      {/* Phase Navigation Sidebar */}
      <PhaseNav collapsed={sidebarCollapsed} onToggle={handleToggleSidebar} />

      {/* Main Content Area */}
      <div
        className={cn(
          'transition-all duration-300',
          sidebarCollapsed ? 'pl-16' : 'pl-56'
        )}
      >
        {/* Project Header */}
        <ProjectHeader />

        {/* Page Content */}
        <main className="min-h-[calc(100vh-64px)]">
          <Outlet />
        </main>
      </div>
    </div>
  );
};

export default ProjectLayout;
