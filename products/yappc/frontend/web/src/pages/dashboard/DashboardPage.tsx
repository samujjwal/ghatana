/**
 * Dashboard Page
 *
 * @description Main dashboard with project overview, recent activity,
 * and quick actions for authenticated users.
 */

import React from 'react';
import { NavLink } from 'react-router';
import { useAtomValue } from 'jotai';
import { Button } from '@/components/ui/Button';
import { ProgressBar } from '@/components/ui/ProgressBar';
import {
  Plus,
  FolderKanban,
  TrendingUp,
  Users,
  Activity,
  ArrowRight,
  Sparkles,
  Zap,
  CheckCircle2,
  AlertTriangle,
  GitPullRequest,
} from 'lucide-react';

import { cn } from '../../utils/cn';
import { currentUserAtom, projectsAtom } from '../../state/atoms';
import { ROUTES } from '../../router/paths';
import { useI18n } from '../../i18n/I18nProvider';

// =============================================================================
// Types
// =============================================================================

interface Project {
  id: string;
  name: string;
  description: string;
  status: 'active' | 'paused' | 'archived';
  phase: string;
  progress: number;
  lastActivity: string;
  team: { name: string; avatar?: string }[];
}

interface QuickStat {
  label: string;
  value: string | number;
  icon: React.ReactNode;
  trend?: 'up' | 'down';
  change?: string;
}

// =============================================================================
// Dashboard Page Component
// =============================================================================

const DashboardPage: React.FC = () => {
  const currentUser = useAtomValue(currentUserAtom);
  const projects = useAtomValue(projectsAtom);
  const { t } = useI18n();

  // Get time-based greeting
  const getGreeting = () => {
    const hour = new Date().getHours();
    if (hour < 12) return t('dashboard.greetingMorning');
    if (hour < 18) return t('dashboard.greetingAfternoon');
    return t('dashboard.greetingEvening');
  };

  const quickStats: QuickStat[] = [
    {
      label: t('dashboard.stat.activeProjects'),
      value: projects.filter((p: Project) => p.status === 'active').length,
      icon: <FolderKanban className="w-5 h-5" />,
    },
    {
      label: t('dashboard.stat.openTasks'),
      value: 24,
      icon: <CheckCircle2 className="w-5 h-5" />,
      trend: 'down',
      change: '-3 this week',
    },
    {
      label: t('dashboard.stat.openPRs'),
      value: 8,
      icon: <GitPullRequest className="w-5 h-5" />,
      trend: 'up',
      change: '+2 today',
    },
    {
      label: t('dashboard.stat.activeIncidents'),
      value: 0,
      icon: <AlertTriangle className="w-5 h-5" />,
    },
  ];

  const recentActivity = [
    {
      id: '1',
      type: 'deploy',
      message: 'Deployed to production',
      project: 'E-commerce API',
      time: '10 min ago',
    },
    {
      id: '2',
      type: 'pr',
      message: 'PR merged: Add authentication',
      project: 'Mobile App',
      time: '25 min ago',
    },
    {
      id: '3',
      type: 'story',
      message: 'Completed: Payment integration',
      project: 'E-commerce API',
      time: '1 hour ago',
    },
    {
      id: '4',
      type: 'comment',
      message: 'Sarah commented on your PR',
      project: 'Dashboard',
      time: '2 hours ago',
    },
  ];

  return (
    <div className="max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold text-white mb-2">
            {getGreeting()}, {currentUser?.name?.split(' ')[0] || 'there'}!
          </h1>
          <p className="text-fg-muted">{t('dashboard.subtitle')}</p>
        </div>
        <NavLink
          to={ROUTES.TEMPLATES}
          className={cn(
            'flex items-center gap-2 px-4 py-2 rounded-lg font-medium',
            'bg-violet-500 text-white hover:bg-violet-600 transition-colors'
          )}
        >
          <Plus className="w-4 h-4" />
            {t('dashboard.newProject')}
        </NavLink>
      </div>

      {/* Quick Stats */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        {quickStats.map((stat, index) => (
          <motion.div
            key={stat.label}
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: index * 0.1 }}
            className="p-6 rounded-xl bg-surface border border-border"
          >
            <div className="flex items-start justify-between mb-4">
              <div className="p-2 rounded-lg bg-surface text-fg-muted">{stat.icon}</div>
              {stat.trend && (
                <span
                  className={cn(
                    'text-xs font-medium',
                    stat.trend === 'up' ? 'text-emerald-400' : 'text-info-color'
                  )}
                >
                  {stat.change}
                </span>
              )}
            </div>
            <div className="text-3xl font-bold text-white mb-1">{stat.value}</div>
            <div className="text-sm text-fg-muted">{stat.label}</div>
          </motion.div>
        ))}
      </div>

      {/* Main Content Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Projects List */}
        <div className="lg:col-span-2">
          <div className="bg-surface border border-border rounded-xl">
            <div className="flex items-center justify-between p-6 border-b border-border">
              <h2 className="text-lg font-semibold text-white">{t('dashboard.yourProjects')}</h2>
              <NavLink
                to={ROUTES.PROJECTS}
                className="text-sm text-violet-400 hover:text-violet-300 flex items-center gap-1"
              >
                {t('dashboard.viewAll')}
                <ArrowRight className="w-4 h-4" />
              </NavLink>
            </div>
            <div className="divide-y divide-zinc-800">
              {projects.slice(0, 4).map((project: Project, index: number) => (
                <NavLink
                  key={project.id}
                  to={ROUTES.project(project.id)}
                  className="flex items-center gap-4 p-4 hover:bg-surface/50 transition-colors"
                >
                  <div
                    className={cn(
                      'w-12 h-12 rounded-xl flex items-center justify-center text-white text-lg font-bold',
                      index % 4 === 0 && 'bg-gradient-to-br from-violet-500 to-fuchsia-500',
                      index % 4 === 1 && 'bg-gradient-to-br from-cyan-500 to-blue-500',
                      index % 4 === 2 && 'bg-gradient-to-br from-emerald-500 to-teal-500',
                      index % 4 === 3 && 'bg-gradient-to-br from-amber-500 to-orange-500'
                    )}
                  >
                    {project.name.charAt(0)}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="font-medium text-white truncate">{project.name}</span>
                      <span
                        className={cn(
                          'px-2 py-0.5 rounded text-xs font-medium capitalize',
                          project.status === 'active' && 'bg-emerald-500/20 text-emerald-400',
                          project.status === 'paused' && 'bg-warning-bg/20 text-warning-color',
                          project.status === 'archived' && 'bg-surface-muted/20 text-fg-muted'
                        )}
                      >
                        {project.status}
                      </span>
                    </div>
                    <div className="flex items-center gap-4 text-sm text-fg-muted mt-1">
                      <span>{project.phase}</span>
                      <span>•</span>
                      <span>{project.lastActivity}</span>
                    </div>
                  </div>
                  <div className="flex items-center gap-3">
                    {/* Progress */}
                    <div className="w-24">
                      <div className="flex items-center justify-between text-xs mb-1">
                        <span className="text-fg-muted">{t('dashboard.progress')}</span>
                        <span className="text-white">{project.progress}%</span>
                      </div>
                      <ProgressBar percentage={project.progress} height="h-1.5" />
                    </div>
                    {/* Team avatars */}
                    <div className="flex -space-x-2">
                      {project.team?.slice(0, 3).map((member, i) => (
                        <div
                          key={i}
                          className="w-8 h-8 rounded-full bg-gradient-to-br from-zinc-600 to-zinc-700 border-2 border-border flex items-center justify-center text-white text-xs font-medium"
                          title={member.name}
                        >
                          {member.name.charAt(0)}
                        </div>
                      ))}
                      {project.team?.length > 3 && (
                        <div className="w-8 h-8 rounded-full bg-surface-muted border-2 border-border flex items-center justify-center text-fg-muted text-xs">
                          +{project.team.length - 3}
                        </div>
                      )}
                    </div>
                  </div>
                </NavLink>
              ))}
              {projects.length === 0 && (
                <div className="p-12 text-center">
                  <Sparkles className="w-12 h-12 text-violet-400 mx-auto mb-4" />
                  <h3 className="text-lg font-medium text-white mb-2">{t('dashboard.noProjectsTitle')}</h3>
                  <p className="text-fg-muted mb-6">
                    {t('dashboard.noProjectsDesc')}
                  </p>
                  <NavLink
                    to={ROUTES.TEMPLATES}
                    className={cn(
                      'inline-flex items-center gap-2 px-4 py-2 rounded-lg font-medium',
                      'bg-violet-500 text-white hover:bg-violet-600 transition-colors'
                    )}
                  >
                    <Zap className="w-4 h-4" />
                    {t('dashboard.createFirstProject')}
                  </NavLink>
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Sidebar */}
        <div className="space-y-6">
          {/* Recent Activity */}
          <div className="bg-surface border border-border rounded-xl p-6">
            <h2 className="text-lg font-semibold text-white mb-4">{t('dashboard.recentActivity')}</h2>
            <div className="space-y-4">
              {recentActivity.map((item) => (
                <div key={item.id} className="flex items-start gap-3">
                  <div
                    className={cn(
                      'p-1.5 rounded',
                      item.type === 'deploy' && 'bg-emerald-500/20 text-emerald-400',
                      item.type === 'pr' && 'bg-violet-500/20 text-violet-400',
                      item.type === 'story' && 'bg-info-bg/20 text-info-color',
                      item.type === 'comment' && 'bg-warning-bg/20 text-warning-color'
                    )}
                  >
                    <Activity className="w-3 h-3" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm text-white truncate">{item.message}</p>
                    <div className="flex items-center gap-2 text-xs text-fg-muted mt-0.5">
                      <span>{item.project}</span>
                      <span>•</span>
                      <span>{item.time}</span>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Quick Actions */}
          <div className="bg-surface border border-border rounded-xl p-6">
            <h2 className="text-lg font-semibold text-white mb-4">{t('dashboard.quickActions')}</h2>
            <div className="space-y-2">
              {[
                { label: t('dashboard.action.startSprint'), icon: <TrendingUp className="w-4 h-4" />, href: '#' },
                { label: t('dashboard.action.reviewPRs'), icon: <GitPullRequest className="w-4 h-4" />, href: '#' },
                { label: t('dashboard.action.checkDeployments'), icon: <Zap className="w-4 h-4" />, href: '#' },
                { label: t('dashboard.action.teamCalendar'), icon: <Users className="w-4 h-4" />, href: '#' },
              ].map((action) => (
                <NavLink
                  key={action.label}
                  to={action.href}
                  className="flex items-center gap-3 p-3 rounded-lg hover:bg-surface transition-colors group"
                >
                  <div className="p-2 rounded-lg bg-surface text-fg-muted group-hover:text-violet-400 transition-colors">
                    {action.icon}
                  </div>
                  <span className="text-sm text-fg-muted group-hover:text-white transition-colors">
                    {action.label}
                  </span>
                  <ArrowRight className="w-4 h-4 ml-auto text-fg-muted group-hover:text-fg-muted transition-colors" />
                </NavLink>
              ))}
            </div>
          </div>

          {/* AI Assistant Tip */}
          <div className="bg-gradient-to-br from-violet-500/10 to-fuchsia-500/10 border border-violet-500/20 rounded-xl p-6">
            <div className="flex items-start gap-3">
              <div className="p-2 rounded-lg bg-violet-500/20">
                <Sparkles className="w-5 h-5 text-violet-400" />
              </div>
              <div>
                <h3 className="font-medium text-white mb-1">{t('dashboard.aiTipTitle')}</h3>
                <p className="text-sm text-fg-muted">
                  {t('dashboard.aiTipText')}
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default DashboardPage;
