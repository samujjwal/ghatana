/**
 * App Layout
 *
 * @description Main application layout with sidebar navigation,
 * header, and content area for authenticated users.
 */

import React, { useState, useCallback, useMemo } from 'react';
import { Outlet, NavLink, useLocation } from 'react-router';
import { useAtomValue, useSetAtom } from 'jotai';
import { AnimatePresence, motion } from 'framer-motion';
import {
  Home,
  FolderKanban,
  Settings,
  User,
  LogOut,
  ChevronLeft,
  ChevronRight,
  Bell,
  Search,
  Plus,
  Moon,
  Sun,
  Command,
  HelpCircle,
  MessageSquare,
} from 'lucide-react';

import { cn } from '../utils/cn';
import {
  currentUserAtom,
  sidebarCollapsedAtom,
  notificationsAtom,
  activeProjectAtom,
  themeAtom,
} from '../state/atoms';
import { ROUTES } from '../router/paths';
import { useAppNavigation } from '../router/hooks';

// =============================================================================
// Types
// =============================================================================

interface NavItem {
  label: string;
  path: string;
  icon: React.ReactNode;
  badge?: number;
}

// =============================================================================
// Sidebar Navigation Items
// =============================================================================

const mainNavItems: NavItem[] = [
  { label: 'Dashboard', path: ROUTES.DASHBOARD, icon: <Home className="w-5 h-5" /> },
  { label: 'Projects', path: ROUTES.PROJECTS, icon: <FolderKanban className="w-5 h-5" /> },
];

const bottomNavItems: NavItem[] = [
  { label: 'Settings', path: ROUTES.SETTINGS, icon: <Settings className="w-5 h-5" /> },
  { label: 'Profile', path: ROUTES.PROFILE, icon: <User className="w-5 h-5" /> },
];

// =============================================================================
// Sidebar Component
// =============================================================================

interface SidebarProps {
  collapsed: boolean;
  onToggle: () => void;
}

const Sidebar: React.FC<SidebarProps> = ({ collapsed, onToggle }) => {
  const currentUser = useAtomValue(currentUserAtom);
  const activeProject = useAtomValue(activeProjectAtom);
  const notifications = useAtomValue(notificationsAtom);
  const unreadCount = notifications.filter((n) => !n.read).length;

  return (
    <aside
      className={cn(
        'fixed left-0 top-0 h-full bg-zinc-900 border-r border-zinc-800',
        'flex flex-col transition-all duration-300 z-40',
        collapsed ? 'w-16' : 'w-64'
      )}
    >
      {/* Logo */}
      <div className="h-16 flex items-center justify-between px-4 border-b border-zinc-800">
        {!collapsed && (
          <span className="text-xl font-bold bg-gradient-to-r from-violet-400 to-fuchsia-400 bg-clip-text text-transparent">
            YAPPC
          </span>
        )}
        <button
          onClick={onToggle}
          className="p-2 rounded-lg hover:bg-zinc-800 text-zinc-400 hover:text-white transition-colors"
          aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
        >
          {collapsed ? (
            <ChevronRight className="w-5 h-5" />
          ) : (
            <ChevronLeft className="w-5 h-5" />
          )}
        </button>
      </div>

      {/* Active Project */}
      {activeProject && (
        <div className={cn('px-3 py-4 border-b border-zinc-800', collapsed && 'px-2')}>
          <NavLink
            to={ROUTES.project(activeProject.id)}
            className={cn(
              'flex items-center gap-3 p-2 rounded-lg',
              'bg-violet-500/10 border border-violet-500/20',
              'hover:bg-violet-500/20 transition-colors'
            )}
          >
            <div className="w-8 h-8 rounded-lg bg-violet-500 flex items-center justify-center text-white text-sm font-medium flex-shrink-0">
              {activeProject.name.charAt(0).toUpperCase()}
            </div>
            {!collapsed && (
              <div className="flex-1 min-w-0">
                <div className="text-sm font-medium text-white truncate">
                  {activeProject.name}
                </div>
                <div className="text-xs text-zinc-400 truncate">{activeProject.status}</div>
              </div>
            )}
          </NavLink>
        </div>
      )}

      {/* Main Navigation */}
      <nav className="flex-1 overflow-y-auto py-4 px-3 space-y-1">
        {mainNavItems.map((item) => (
          <NavLink
            key={item.path}
            to={item.path}
            className={({ isActive }) =>
              cn(
                'flex items-center gap-3 px-3 py-2.5 rounded-lg transition-colors',
                'text-zinc-400 hover:text-white hover:bg-zinc-800',
                isActive && 'bg-violet-500/10 text-violet-400 border border-violet-500/20',
                collapsed && 'justify-center px-2'
              )
            }
          >
            {item.icon}
            {!collapsed && <span className="text-sm font-medium">{item.label}</span>}
            {!collapsed && item.badge && item.badge > 0 && (
              <span className="ml-auto bg-violet-500 text-white text-xs px-2 py-0.5 rounded-full">
                {item.badge}
              </span>
            )}
          </NavLink>
        ))}

        {/* Notifications */}
        <NavLink
          to="/notifications"
          className={({ isActive }) =>
            cn(
              'flex items-center gap-3 px-3 py-2.5 rounded-lg transition-colors',
              'text-zinc-400 hover:text-white hover:bg-zinc-800',
              isActive && 'bg-violet-500/10 text-violet-400 border border-violet-500/20',
              collapsed && 'justify-center px-2'
            )
          }
        >
          <div className="relative">
            <Bell className="w-5 h-5" />
            {unreadCount > 0 && (
              <span className="absolute -top-1 -right-1 w-4 h-4 bg-red-500 rounded-full text-white text-[10px] flex items-center justify-center">
                {unreadCount > 9 ? '9+' : unreadCount}
              </span>
            )}
          </div>
          {!collapsed && <span className="text-sm font-medium">Notifications</span>}
        </NavLink>
      </nav>

      {/* Bottom Navigation */}
      <div className="border-t border-zinc-800 py-4 px-3 space-y-1">
        {bottomNavItems.map((item) => (
          <NavLink
            key={item.path}
            to={item.path}
            className={({ isActive }) =>
              cn(
                'flex items-center gap-3 px-3 py-2.5 rounded-lg transition-colors',
                'text-zinc-400 hover:text-white hover:bg-zinc-800',
                isActive && 'bg-violet-500/10 text-violet-400 border border-violet-500/20',
                collapsed && 'justify-center px-2'
              )
            }
          >
            {item.icon}
            {!collapsed && <span className="text-sm font-medium">{item.label}</span>}
          </NavLink>
        ))}
      </div>

      {/* User Profile */}
      {currentUser && (
        <div className={cn('px-3 pb-4', collapsed && 'px-2')}>
          <div
            className={cn(
              'flex items-center gap-3 p-2 rounded-lg',
              'bg-zinc-800/50 border border-zinc-700/50',
              collapsed && 'justify-center'
            )}
          >
            <div className="w-8 h-8 rounded-full bg-gradient-to-br from-violet-500 to-fuchsia-500 flex items-center justify-center text-white text-sm font-medium flex-shrink-0">
              {currentUser.name?.charAt(0).toUpperCase() || 'U'}
            </div>
            {!collapsed && (
              <div className="flex-1 min-w-0">
                <div className="text-sm font-medium text-white truncate">
                  {currentUser.name}
                </div>
                <div className="text-xs text-zinc-400 truncate">{currentUser.email}</div>
              </div>
            )}
          </div>
        </div>
      )}
    </aside>
  );
};

// =============================================================================
// Header Component
// =============================================================================

interface HeaderProps {
  sidebarCollapsed: boolean;
}

const Header: React.FC<HeaderProps> = ({ sidebarCollapsed }) => {
  const [searchOpen, setSearchOpen] = useState(false);
  const theme = useAtomValue(themeAtom);
  const setTheme = useSetAtom(themeAtom);
  const navigation = useAppNavigation();

  const toggleTheme = useCallback(() => {
    setTheme((prev: 'light' | 'dark' | 'system') => (prev === 'dark' ? 'light' : 'dark'));
  }, [setTheme]);

  return (
    <header
      className={cn(
        'fixed top-0 right-0 h-16 bg-zinc-900/80 backdrop-blur-lg',
        'border-b border-zinc-800 flex items-center justify-between px-6 z-30',
        'transition-all duration-300',
        sidebarCollapsed ? 'left-16' : 'left-64'
      )}
    >
      {/* Search */}
      <div className="flex items-center gap-4">
        <button
          onClick={() => setSearchOpen(true)}
          className={cn(
            'flex items-center gap-3 px-4 py-2 rounded-lg',
            'bg-zinc-800 border border-zinc-700 text-zinc-400',
            'hover:border-zinc-600 hover:text-zinc-300 transition-colors',
            'min-w-[240px]'
          )}
        >
          <Search className="w-4 h-4" />
          <span className="text-sm">Search...</span>
          <div className="ml-auto flex items-center gap-1 text-xs text-zinc-500">
            <Command className="w-3 h-3" />
            <span>K</span>
          </div>
        </button>
      </div>

      {/* Actions */}
      <div className="flex items-center gap-2">
        {/* New Project */}
        <button
          onClick={navigation.toNewProject}
          className={cn(
            'flex items-center gap-2 px-4 py-2 rounded-lg',
            'bg-violet-500 text-white hover:bg-violet-600',
            'transition-colors font-medium text-sm'
          )}
        >
          <Plus className="w-4 h-4" />
          <span>New Project</span>
        </button>

        {/* Theme Toggle */}
        <button
          onClick={toggleTheme}
          className="p-2 rounded-lg hover:bg-zinc-800 text-zinc-400 hover:text-white transition-colors"
          aria-label="Toggle theme"
        >
          {theme === 'dark' ? (
            <Sun className="w-5 h-5" />
          ) : (
            <Moon className="w-5 h-5" />
          )}
        </button>

        {/* Help */}
        <button
          className="p-2 rounded-lg hover:bg-zinc-800 text-zinc-400 hover:text-white transition-colors"
          aria-label="Help"
        >
          <HelpCircle className="w-5 h-5" />
        </button>

        {/* Feedback */}
        <button
          className="p-2 rounded-lg hover:bg-zinc-800 text-zinc-400 hover:text-white transition-colors"
          aria-label="Send feedback"
        >
          <MessageSquare className="w-5 h-5" />
        </button>
      </div>

      {/* Search Modal - placeholder */}
      <AnimatePresence>
        {searchOpen && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 bg-black/50 flex items-start justify-center pt-[20vh] z-50"
            onClick={() => setSearchOpen(false)}
          >
            <motion.div
              initial={{ opacity: 0, scale: 0.95, y: -20 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.95, y: -20 }}
              className="w-full max-w-2xl bg-zinc-900 border border-zinc-700 rounded-xl shadow-2xl"
              onClick={(e) => e.stopPropagation()}
            >
              <div className="flex items-center gap-3 px-4 py-3 border-b border-zinc-800">
                <Search className="w-5 h-5 text-zinc-400" />
                <input
                  type="text"
                  placeholder="Search projects, stories, incidents..."
                  className="flex-1 bg-transparent text-white placeholder-zinc-500 outline-none text-lg"
                  autoFocus
                />
                <kbd className="px-2 py-1 bg-zinc-800 rounded text-xs text-zinc-400">ESC</kbd>
              </div>
              <div className="p-4">
                <p className="text-sm text-zinc-500">Start typing to search...</p>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </header>
  );
};

// =============================================================================
// App Layout Component
// =============================================================================

const AppLayout: React.FC = () => {
  const [sidebarCollapsed, setSidebarCollapsed] = useAtomValue(sidebarCollapsedAtom)
    ? [useAtomValue(sidebarCollapsedAtom), useSetAtom(sidebarCollapsedAtom)]
    : useState(false);

  // Use state if atoms not available
  const [collapsed, setCollapsed] = useState(false);

  const handleToggleSidebar = useCallback(() => {
    setCollapsed((prev) => !prev);
  }, []);

  return (
    <div className="min-h-screen bg-zinc-950">
      {/* Sidebar */}
      <Sidebar collapsed={collapsed} onToggle={handleToggleSidebar} />

      {/* Header */}
      <Header sidebarCollapsed={collapsed} />

      {/* Main Content */}
      <main
        className={cn(
          'pt-16 min-h-screen transition-all duration-300',
          collapsed ? 'pl-16' : 'pl-64'
        )}
      >
        <div className="p-6">
          <Outlet />
        </div>
      </main>
    </div>
  );
};

export default AppLayout;
