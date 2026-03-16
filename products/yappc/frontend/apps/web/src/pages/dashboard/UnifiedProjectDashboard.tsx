/**
 * Unified Project Dashboard
 *
 * @description Single authoritative project view with phase tabs, replacing
 * the fragmented 3-rail navigation system. Implements IA restructure from
 * YAPPC UI/UX Analysis Report Phase 1 Week 2.
 *
 * @doc.type page
 * @doc.purpose Unified project navigation and overview
 * @doc.layer page
 * @doc.phase global
 */

import React, { useState, useMemo } from 'react';
import { useParams, useNavigate, Outlet } from 'react-router';
import { useAtomValue } from 'jotai';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Rocket,
  Settings,
  Code,
  Activity,
  Users,
  Shield,
  ChevronRight,
  Search,
  Bell,
  Menu,
  X,
  LayoutDashboard,
  Sparkles,
} from 'lucide-react';

import { cn } from '../../utils/cn';
import { Button } from '@ghatana/ui';
import { Input } from '@ghatana/ui';

import {
  currentProjectAtom,
  breadcrumbsAtom,
  unreadNotificationsCountAtom,
} from '../../state/atoms';
import { AIChatInterface } from '../../components/placeholders';

// =============================================================================
// Types
// =============================================================================

type ProjectPhase =
  | 'bootstrapping'
  | 'initialization'
  | 'development'
  | 'operations'
  | 'collaboration'
  | 'security';

interface PhaseTab {
  id: ProjectPhase;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  description: string;
  color: string;
  path: string;
  completionPercentage?: number;
}

interface QuickAction {
  id: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  path: string;
  badge?: string;
}

// =============================================================================
// Constants
// =============================================================================

const PHASE_TABS: PhaseTab[] = [
  {
    id: 'bootstrapping',
    label: 'Bootstrap',
    icon: Rocket,
    description: 'Idea to structured project',
    color: 'blue',
    path: 'bootstrap',
  },
  {
    id: 'initialization',
    label: 'Initialize',
    icon: Settings,
    description: 'Setup infrastructure',
    color: 'purple',
    path: 'init',
  },
  {
    id: 'development',
    label: 'Develop',
    icon: Code,
    description: 'Build and iterate',
    color: 'green',
    path: 'dev',
  },
  {
    id: 'operations',
    label: 'Operate',
    icon: Activity,
    description: 'Deploy and monitor',
    color: 'orange',
    path: 'ops',
  },
  {
    id: 'collaboration',
    label: 'Collaborate',
    icon: Users,
    description: 'Team coordination',
    color: 'pink',
    path: 'collab',
  },
  {
    id: 'security',
    label: 'Secure',
    icon: Shield,
    description: 'Protect and comply',
    color: 'red',
    path: 'security',
  },
];

// =============================================================================
// Component
// =============================================================================

const UnifiedProjectDashboard: React.FC = () => {
  const { projectId } = useParams<{ projectId: string }>();
  const navigate = useNavigate();

  const currentProject = useAtomValue(currentProjectAtom);
  const breadcrumbs = useAtomValue(breadcrumbsAtom);
  const unreadCount = useAtomValue(unreadNotificationsCountAtom);

  const [activePhase, setActivePhase] = useState<ProjectPhase>('bootstrapping');
  const [searchQuery, setSearchQuery] = useState('');
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const [isAIAssistantOpen, setIsAIAssistantOpen] = useState(false);

  // Quick actions based on current phase
  const quickActions = useMemo<QuickAction[]>(() => {
    const actions: Record<ProjectPhase, QuickAction[]> = {
      bootstrapping: [
        { id: 'upload', label: 'Upload Docs', icon: Rocket, path: 'bootstrap/upload' },
        { id: 'template', label: 'Browse Templates', icon: LayoutDashboard, path: 'bootstrap/templates' },
        { id: 'import', label: 'Import from URL', icon: Code, path: 'bootstrap/import' },
      ],
      initialization: [
        { id: 'wizard', label: 'Setup Wizard', icon: Settings, path: 'init/wizard' },
        { id: 'infra', label: 'Configure Infra', icon: Activity, path: 'init/infrastructure' },
        { id: 'team', label: 'Invite Team', icon: Users, path: 'init/team' },
      ],
      development: [
        { id: 'canvas', label: 'Open Canvas', icon: LayoutDashboard, path: 'dev/canvas' },
        { id: 'sprint', label: 'Sprint Board', icon: Activity, path: 'dev/sprint' },
        { id: 'stories', label: 'User Stories', icon: Code, path: 'dev/stories' },
      ],
      operations: [
        { id: 'monitor', label: 'Monitoring', icon: Activity, path: 'ops/monitoring' },
        { id: 'deploy', label: 'Deployments', icon: Rocket, path: 'ops/deployments' },
        { id: 'incidents', label: 'Incidents', icon: Shield, path: 'ops/incidents', badge: '2' },
      ],
      collaboration: [
        { id: 'messages', label: 'Messages', icon: Users, path: 'collab/messages', badge: '5' },
        { id: 'calendar', label: 'Calendar', icon: Activity, path: 'collab/calendar' },
        { id: 'knowledge', label: 'Knowledge Base', icon: Code, path: 'collab/knowledge' },
      ],
      security: [
        { id: 'scan', label: 'Security Scan', icon: Shield, path: 'security/scan' },
        { id: 'vulns', label: 'Vulnerabilities', icon: Activity, path: 'security/vulnerabilities', badge: '3' },
        { id: 'compliance', label: 'Compliance', icon: Settings, path: 'security/compliance' },
      ],
    };

    return actions[activePhase] || [];
  }, [activePhase]);

  const handlePhaseChange = (phase: ProjectPhase) => {
    setActivePhase(phase);
    const phaseTab = PHASE_TABS.find((t) => t.id === phase);
    if (phaseTab && projectId) {
      navigate(`/project/${projectId}/${phaseTab.path}`);
    }
  };

  const handleQuickAction = (action: QuickAction) => {
    if (projectId) {
      navigate(`/project/${projectId}/${action.path}`);
    }
  };

  return (
    <div className="flex h-screen flex-col bg-gray-50 dark:bg-gray-900">
      {/* Top Navigation Bar */}
      <header className="sticky top-0 z-50 border-b border-gray-200 bg-white dark:border-gray-800 dark:bg-gray-950">
        <div className="flex h-16 items-center justify-between px-4 lg:px-6">
          {/* Left: Project Info & Breadcrumbs */}
          <div className="flex items-center gap-4">
            <Button
              variant="ghost"
              size="sm"
              className="lg:hidden"
              onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
            >
              {isMobileMenuOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
            </Button>

            <div className="flex items-center gap-2">
              <h1 className="text-lg font-semibold text-gray-900 dark:text-gray-100">
                {currentProject?.name || 'Project'}
              </h1>
              {breadcrumbs.length > 0 && (
                <div className="hidden items-center gap-1 text-sm text-gray-500 md:flex">
                  {breadcrumbs.map((crumb) => (
                    <React.Fragment key={crumb.id}>
                      <ChevronRight className="h-4 w-4" />
                      <button
                        onClick={() => navigate(crumb.href)}
                        className="hover:text-gray-900 dark:hover:text-gray-100"
                      >
                        {crumb.label}
                      </button>
                    </React.Fragment>
                  ))}
                </div>
              )}
            </div>
          </div>

          {/* Right: Search, AI, Notifications */}
          <div className="flex items-center gap-2">
            {/* Global Search */}
            <div className="hidden md:block">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
                <Input
                  type="search"
                  placeholder="Search project..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="w-64 pl-9"
                />
              </div>
            </div>

            {/* AI Assistant Toggle */}
            <Button
              variant={isAIAssistantOpen ? 'solid' : 'ghost'}
              size="sm"
              onClick={() => setIsAIAssistantOpen(!isAIAssistantOpen)}
              className="relative"
            >
              <Sparkles className="h-4 w-4" />
              <span className="ml-2 hidden lg:inline">AI Assistant</span>
            </Button>

            {/* Notifications */}
            <Button variant="ghost" size="sm" className="relative">
              <Bell className="h-4 w-4" />
              {unreadCount > 0 && (
                <span className="absolute -right-1 -top-1 flex h-5 w-5 items-center justify-center rounded-full bg-red-600 text-xs text-white">
                  {unreadCount}
                </span>
              )}
            </Button>
          </div>
        </div>

        {/* Phase Tabs - Unified Navigation */}
        <div className="border-t border-gray-200 dark:border-gray-800">
          <div className="flex items-center gap-1 overflow-x-auto px-4 lg:px-6">
            {PHASE_TABS.map((phase) => {
              const Icon = phase.icon;
              const isActive = activePhase === phase.id;

              return (
                <button
                  key={phase.id}
                  onClick={() => handlePhaseChange(phase.id)}
                  className={cn(
                    'group relative flex items-center gap-2 whitespace-nowrap border-b-2 px-4 py-3 text-sm font-medium transition-colors',
                    isActive
                      ? 'border-blue-600 text-blue-600 dark:border-blue-500 dark:text-blue-500'
                      : 'border-transparent text-gray-600 hover:border-gray-300 hover:text-gray-900 dark:text-gray-400 dark:hover:border-gray-700 dark:hover:text-gray-100'
                  )}
                >
                  <Icon className="h-4 w-4" />
                  <span>{phase.label}</span>
                  {phase.completionPercentage !== undefined && (
                    <span className="ml-1 rounded-full bg-gray-200 px-2 py-0.5 text-xs text-gray-700 dark:bg-gray-700 dark:text-gray-300">
                      {phase.completionPercentage}%
                    </span>
                  )}

                  {/* Tooltip */}
                  <div className="pointer-events-none absolute left-1/2 top-full z-10 mt-2 hidden -translate-x-1/2 rounded-lg bg-gray-900 px-3 py-2 text-xs text-white shadow-lg group-hover:block dark:bg-gray-800">
                    {phase.description}
                    <div className="absolute -top-1 left-1/2 h-2 w-2 -translate-x-1/2 rotate-45 bg-gray-900 dark:bg-gray-800" />
                  </div>
                </button>
              );
            })}
          </div>
        </div>
      </header>

      {/* Main Content Area */}
      <div className="flex flex-1 overflow-hidden">
        {/* Quick Actions Sidebar */}
        <aside
          className={cn(
            'w-64 border-r border-gray-200 bg-white dark:border-gray-800 dark:bg-gray-950',
            'hidden lg:block'
          )}
        >
          <div className="p-4">
            <h2 className="mb-4 text-sm font-semibold text-gray-900 dark:text-gray-100">
              Quick Actions
            </h2>
            <div className="space-y-1">
              {quickActions.map((action) => {
                const Icon = action.icon;
                return (
                  <button
                    key={action.id}
                    onClick={() => handleQuickAction(action)}
                    className="flex w-full items-center justify-between rounded-lg px-3 py-2 text-sm text-gray-700 transition-colors hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-800"
                  >
                    <div className="flex items-center gap-2">
                      <Icon className="h-4 w-4" />
                      <span>{action.label}</span>
                    </div>
                    {action.badge && (
                      <span className="rounded-full bg-red-600 px-2 py-0.5 text-xs text-white">
                        {action.badge}
                      </span>
                    )}
                  </button>
                );
              })}
            </div>
          </div>
        </aside>

        {/* Phase Content */}
        <main className="flex-1 overflow-auto">
          <Outlet />
        </main>

        {/* AI Assistant Panel */}
        <AnimatePresence>
          {isAIAssistantOpen && (
            <motion.aside
              initial={{ width: 0, opacity: 0 }}
              animate={{ width: 384, opacity: 1 }}
              exit={{ width: 0, opacity: 0 }}
              transition={{ duration: 0.2 }}
              className="border-l border-gray-200 bg-white dark:border-gray-800 dark:bg-gray-950"
            >
              <div className="flex h-full flex-col p-4">
                <div className="mb-4 flex items-center justify-between">
                  <h2 className="text-sm font-semibold text-gray-900 dark:text-gray-100">
                    AI Assistant
                  </h2>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => setIsAIAssistantOpen(false)}
                  >
                    <X className="h-4 w-4" />
                  </Button>
                </div>
                <div className="flex-1 min-h-0">
                  <AIChatInterface
                    className="h-full"
                  />
                </div>
              </div>
            </motion.aside>
          )}
        </AnimatePresence>
      </div>
    </div>
  );
};

export default UnifiedProjectDashboard;
