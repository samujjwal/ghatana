/**
 * Dashboard Page
 *
 * @description Main dashboard with project overview, recent activity,
 * and quick actions for authenticated users.
 */

import React from 'react';
import { NavLink } from 'react-router';
import { useAtomValue } from 'jotai';
import { motion } from 'framer-motion';
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

  // Get time-based greeting
  const getGreeting = () => {
    const hour = new Date().getHours();
    if (hour < 12) return 'Good morning';
    if (hour < 18) return 'Good afternoon';
    return 'Good evening';
  };

  const quickStats: QuickStat[] = [
    {
      label: 'Active Projects',
      value: projects.filter((p: Project) => p.status === 'active').length,
      icon: <FolderKanban className="w-5 h-5" />,
    },
    {
      label: 'Open Tasks',
      value: 24,
      icon: <CheckCircle2 className="w-5 h-5" />,
      trend: 'down',
      change: '-3 this week',
    },
    {
      label: 'Open PRs',
      value: 8,
      icon: <GitPullRequest className="w-5 h-5" />,
      trend: 'up',
      change: '+2 today',
    },
    {
      label: 'Active Incidents',
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
          <p className="text-zinc-400">Here's what's happening across your projects</p>
        </div>
        <NavLink
          to={ROUTES.TEMPLATES}
          className={cn(
            'flex items-center gap-2 px-4 py-2 rounded-lg font-medium',
            'bg-violet-500 text-white hover:bg-violet-600 transition-colors'
          )}
        >
          <Plus className="w-4 h-4" />
          New Project
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
            className="p-6 rounded-xl bg-zinc-900 border border-zinc-800"
          >
            <div className="flex items-start justify-between mb-4">
              <div className="p-2 rounded-lg bg-zinc-800 text-zinc-400">{stat.icon}</div>
              {stat.trend && (
                <span
                  className={cn(
                    'text-xs font-medium',
                    stat.trend === 'up' ? 'text-emerald-400' : 'text-cyan-400'
                  )}
                >
                  {stat.change}
                </span>
              )}
            </div>
            <div className="text-3xl font-bold text-white mb-1">{stat.value}</div>
            <div className="text-sm text-zinc-400">{stat.label}</div>
          </motion.div>
        ))}
      </div>

      {/* Main Content Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Projects List */}
        <div className="lg:col-span-2">
          <div className="bg-zinc-900 border border-zinc-800 rounded-xl">
            <div className="flex items-center justify-between p-6 border-b border-zinc-800">
              <h2 className="text-lg font-semibold text-white">Your Projects</h2>
              <NavLink
                to={ROUTES.PROJECTS}
                className="text-sm text-violet-400 hover:text-violet-300 flex items-center gap-1"
              >
                View All
                <ArrowRight className="w-4 h-4" />
              </NavLink>
            </div>
            <div className="divide-y divide-zinc-800">
              {projects.slice(0, 4).map((project: Project, index: number) => (
                <NavLink
                  key={project.id}
                  to={ROUTES.project(project.id)}
                  className="flex items-center gap-4 p-4 hover:bg-zinc-800/50 transition-colors"
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
                          project.status === 'paused' && 'bg-amber-500/20 text-amber-400',
                          project.status === 'archived' && 'bg-zinc-500/20 text-zinc-400'
                        )}
                      >
                        {project.status}
                      </span>
                    </div>
                    <div className="flex items-center gap-4 text-sm text-zinc-500 mt-1">
                      <span>{project.phase}</span>
                      <span>•</span>
                      <span>{project.lastActivity}</span>
                    </div>
                  </div>
                  <div className="flex items-center gap-3">
                    {/* Progress */}
                    <div className="w-24">
                      <div className="flex items-center justify-between text-xs mb-1">
                        <span className="text-zinc-500">Progress</span>
                        <span className="text-white">{project.progress}%</span>
                      </div>
                      <div className="h-1.5 bg-zinc-800 rounded-full overflow-hidden">
                        <div
                          className="h-full bg-violet-500 rounded-full"
                          style={{ width: `${project.progress}%` }}
                        />
                      </div>
                    </div>
                    {/* Team avatars */}
                    <div className="flex -space-x-2">
                      {project.team?.slice(0, 3).map((member, i) => (
                        <div
                          key={i}
                          className="w-8 h-8 rounded-full bg-gradient-to-br from-zinc-600 to-zinc-700 border-2 border-zinc-900 flex items-center justify-center text-white text-xs font-medium"
                          title={member.name}
                        >
                          {member.name.charAt(0)}
                        </div>
                      ))}
                      {project.team?.length > 3 && (
                        <div className="w-8 h-8 rounded-full bg-zinc-700 border-2 border-zinc-900 flex items-center justify-center text-zinc-400 text-xs">
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
                  <h3 className="text-lg font-medium text-white mb-2">No projects yet</h3>
                  <p className="text-zinc-400 mb-6">
                    Start your first project and let AI guide you through the process
                  </p>
                  <NavLink
                    to={ROUTES.TEMPLATES}
                    className={cn(
                      'inline-flex items-center gap-2 px-4 py-2 rounded-lg font-medium',
                      'bg-violet-500 text-white hover:bg-violet-600 transition-colors'
                    )}
                  >
                    <Zap className="w-4 h-4" />
                    Create Your First Project
                  </NavLink>
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Sidebar */}
        <div className="space-y-6">
          {/* Recent Activity */}
          <div className="bg-zinc-900 border border-zinc-800 rounded-xl p-6">
            <h2 className="text-lg font-semibold text-white mb-4">Recent Activity</h2>
            <div className="space-y-4">
              {recentActivity.map((item) => (
                <div key={item.id} className="flex items-start gap-3">
                  <div
                    className={cn(
                      'p-1.5 rounded',
                      item.type === 'deploy' && 'bg-emerald-500/20 text-emerald-400',
                      item.type === 'pr' && 'bg-violet-500/20 text-violet-400',
                      item.type === 'story' && 'bg-cyan-500/20 text-cyan-400',
                      item.type === 'comment' && 'bg-amber-500/20 text-amber-400'
                    )}
                  >
                    <Activity className="w-3 h-3" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm text-white truncate">{item.message}</p>
                    <div className="flex items-center gap-2 text-xs text-zinc-500 mt-0.5">
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
          <div className="bg-zinc-900 border border-zinc-800 rounded-xl p-6">
            <h2 className="text-lg font-semibold text-white mb-4">Quick Actions</h2>
            <div className="space-y-2">
              {[
                { label: 'Start New Sprint', icon: <TrendingUp className="w-4 h-4" />, href: '#' },
                { label: 'Review Pull Requests', icon: <GitPullRequest className="w-4 h-4" />, href: '#' },
                { label: 'Check Deployments', icon: <Zap className="w-4 h-4" />, href: '#' },
                { label: 'Team Calendar', icon: <Users className="w-4 h-4" />, href: '#' },
              ].map((action) => (
                <NavLink
                  key={action.label}
                  to={action.href}
                  className="flex items-center gap-3 p-3 rounded-lg hover:bg-zinc-800 transition-colors group"
                >
                  <div className="p-2 rounded-lg bg-zinc-800 text-zinc-400 group-hover:text-violet-400 transition-colors">
                    {action.icon}
                  </div>
                  <span className="text-sm text-zinc-300 group-hover:text-white transition-colors">
                    {action.label}
                  </span>
                  <ArrowRight className="w-4 h-4 ml-auto text-zinc-600 group-hover:text-zinc-400 transition-colors" />
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
                <h3 className="font-medium text-white mb-1">AI Assistant Tip</h3>
                <p className="text-sm text-zinc-400">
                  Try describing your project idea in natural language. The AI will help
                  you design the architecture and set up infrastructure automatically.
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
