/**
 * Dev Dashboard Page
 *
 * @description Development phase overview with sprint metrics,
 * recent activity, and quick actions.
 */

import React from 'react';
import { NavLink, useParams } from 'react-router';
import { useAtomValue } from 'jotai';
import { motion } from 'framer-motion';
import {
  LayoutDashboard,
  GitPullRequest,
  Bug,
  CheckCircle2,
  Clock,
  TrendingUp,
  TrendingDown,
  AlertTriangle,
  Users,
  ArrowRight,
  GitBranch,
  Rocket,
  Flag,
  Calendar,
} from 'lucide-react';

import { cn } from '../../utils/cn';
import {
  activeSprintAtom,
  sprintStoriesAtom,
  activeProjectAtom,
} from '../../state/atoms';
import { ROUTES } from '../../router/paths';

// =============================================================================
// Types
// =============================================================================

interface StatCardProps {
  title: string;
  value: string | number;
  change?: number;
  changeLabel?: string;
  icon: React.ReactNode;
  trend?: 'up' | 'down' | 'neutral';
  href?: string;
}

interface ActivityItem {
  id: string;
  type: 'commit' | 'pr' | 'story' | 'deploy' | 'comment';
  title: string;
  user: { name: string; avatar?: string };
  time: string;
  meta?: string;
}

// =============================================================================
// Components
// =============================================================================

const StatCard: React.FC<StatCardProps> = ({
  title,
  value,
  change,
  changeLabel,
  icon,
  trend,
  href,
}) => {
  const content = (
    <div
      className={cn(
        'p-6 rounded-xl bg-zinc-900 border border-zinc-800',
        'hover:border-zinc-700 transition-colors',
        href && 'cursor-pointer'
      )}
    >
      <div className="flex items-start justify-between mb-4">
        <div className="p-2 rounded-lg bg-zinc-800 text-zinc-400">{icon}</div>
        {change !== undefined && (
          <div
            className={cn(
              'flex items-center gap-1 text-sm',
              trend === 'up' && 'text-emerald-400',
              trend === 'down' && 'text-red-400',
              trend === 'neutral' && 'text-zinc-400'
            )}
          >
            {trend === 'up' && <TrendingUp className="w-4 h-4" />}
            {trend === 'down' && <TrendingDown className="w-4 h-4" />}
            {change > 0 ? '+' : ''}
            {change}%
          </div>
        )}
      </div>
      <div className="text-3xl font-bold text-white mb-1">{value}</div>
      <div className="text-sm text-zinc-400">{title}</div>
      {changeLabel && <div className="text-xs text-zinc-500 mt-1">{changeLabel}</div>}
    </div>
  );

  if (href) {
    return <NavLink to={href}>{content}</NavLink>;
  }

  return content;
};

const ActivityFeed: React.FC<{ items: ActivityItem[] }> = ({ items }) => {
  const typeIcons = {
    commit: <GitBranch className="w-4 h-4" />,
    pr: <GitPullRequest className="w-4 h-4" />,
    story: <CheckCircle2 className="w-4 h-4" />,
    deploy: <Rocket className="w-4 h-4" />,
    comment: <LayoutDashboard className="w-4 h-4" />,
  };

  const typeColors = {
    commit: 'text-cyan-400',
    pr: 'text-violet-400',
    story: 'text-emerald-400',
    deploy: 'text-amber-400',
    comment: 'text-zinc-400',
  };

  return (
    <div className="space-y-4">
      {items.map((item, index) => (
        <motion.div
          key={item.id}
          initial={{ opacity: 0, x: -20 }}
          animate={{ opacity: 1, x: 0 }}
          transition={{ delay: index * 0.05 }}
          className="flex items-start gap-4 p-3 rounded-lg hover:bg-zinc-800/50 transition-colors"
        >
          <div
            className={cn(
              'p-2 rounded-lg bg-zinc-800',
              typeColors[item.type]
            )}
          >
            {typeIcons[item.type]}
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-sm text-white truncate">{item.title}</p>
            <div className="flex items-center gap-2 text-xs text-zinc-500 mt-1">
              <span>{item.user.name}</span>
              <span>•</span>
              <span>{item.time}</span>
              {item.meta && (
                <>
                  <span>•</span>
                  <span>{item.meta}</span>
                </>
              )}
            </div>
          </div>
        </motion.div>
      ))}
    </div>
  );
};

// =============================================================================
// Dev Dashboard Page Component
// =============================================================================

const DevDashboardPage: React.FC = () => {
  const { projectId } = useParams<{ projectId: string }>();
  const activeSprint = useAtomValue(activeSprintAtom);
  const stories = useAtomValue(sprintStoriesAtom);
  const project = useAtomValue(activeProjectAtom);

  // Calculate metrics
  const completedStories = stories.filter((s) => s.status === 'done').length;
  const inProgressStories = stories.filter((s) => s.status === 'in-progress').length;
  const blockedStories = stories.filter((s) => s.status === 'blocked').length;
  const totalStories = stories.length;
  const completionRate = totalStories > 0 ? Math.round((completedStories / totalStories) * 100) : 0;

  // Mock data for demonstration
  const recentActivity: ActivityItem[] = [
    {
      id: '1',
      type: 'pr',
      title: 'feat: Add user authentication flow',
      user: { name: 'Sarah Chen' },
      time: '10 min ago',
      meta: '#142',
    },
    {
      id: '2',
      type: 'story',
      title: 'Implement payment processing',
      user: { name: 'Alex Kim' },
      time: '25 min ago',
    },
    {
      id: '3',
      type: 'deploy',
      title: 'Deployed to staging',
      user: { name: 'CI/CD Bot' },
      time: '1 hour ago',
      meta: 'v1.2.3-beta',
    },
    {
      id: '4',
      type: 'commit',
      title: 'fix: Resolve memory leak in websocket handler',
      user: { name: 'Mike Johnson' },
      time: '2 hours ago',
    },
    {
      id: '5',
      type: 'pr',
      title: 'refactor: Migrate to new API client',
      user: { name: 'Emma Wilson' },
      time: '3 hours ago',
      meta: '#139',
    },
  ];

  const quickLinks = [
    {
      label: 'Sprint Board',
      description: 'View current sprint',
      icon: <LayoutDashboard className="w-5 h-5" />,
      href: ROUTES.development.board(projectId || ''),
      color: 'bg-violet-500/10 text-violet-400',
    },
    {
      label: 'Pull Requests',
      description: '3 awaiting review',
      icon: <GitPullRequest className="w-5 h-5" />,
      href: ROUTES.development.prs(projectId || ''),
      color: 'bg-cyan-500/10 text-cyan-400',
    },
    {
      label: 'Deployments',
      description: 'Staging ready',
      icon: <Rocket className="w-5 h-5" />,
      href: ROUTES.development.deployments(projectId || ''),
      color: 'bg-amber-500/10 text-amber-400',
    },
    {
      label: 'Feature Flags',
      description: '12 active',
      icon: <Flag className="w-5 h-5" />,
      href: ROUTES.development.flags(projectId || ''),
      color: 'bg-emerald-500/10 text-emerald-400',
    },
  ];

  return (
    <div className="p-6 max-w-7xl mx-auto">
      {/* Header */}
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-white mb-2">Development</h1>
        <p className="text-zinc-400">
          {activeSprint?.name || 'No active sprint'} • {activeSprint?.daysRemaining || 0} days
          remaining
        </p>
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        <StatCard
          title="Sprint Progress"
          value={`${completionRate}%`}
          change={8}
          changeLabel="vs last sprint"
          icon={<TrendingUp className="w-5 h-5" />}
          trend="up"
          href={ROUTES.development.board(projectId || '')}
        />
        <StatCard
          title="Stories Completed"
          value={completedStories}
          change={-5}
          icon={<CheckCircle2 className="w-5 h-5" />}
          trend="down"
        />
        <StatCard
          title="In Progress"
          value={inProgressStories}
          icon={<Clock className="w-5 h-5" />}
        />
        <StatCard
          title="Blocked"
          value={blockedStories}
          icon={<AlertTriangle className="w-5 h-5" />}
          trend={blockedStories > 0 ? 'down' : 'neutral'}
        />
      </div>

      {/* Main Content Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Quick Links */}
        <div className="lg:col-span-1">
          <div className="bg-zinc-900 border border-zinc-800 rounded-xl p-6">
            <h2 className="text-lg font-semibold text-white mb-4">Quick Access</h2>
            <div className="space-y-3">
              {quickLinks.map((link) => (
                <NavLink
                  key={link.label}
                  to={link.href}
                  className="flex items-center gap-4 p-3 rounded-lg hover:bg-zinc-800 transition-colors group"
                >
                  <div className={cn('p-2 rounded-lg', link.color)}>{link.icon}</div>
                  <div className="flex-1">
                    <div className="text-sm font-medium text-white">{link.label}</div>
                    <div className="text-xs text-zinc-500">{link.description}</div>
                  </div>
                  <ArrowRight className="w-4 h-4 text-zinc-600 group-hover:text-zinc-400 transition-colors" />
                </NavLink>
              ))}
            </div>
          </div>

          {/* Sprint Burndown Mini */}
          <div className="bg-zinc-900 border border-zinc-800 rounded-xl p-6 mt-6">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg font-semibold text-white">Sprint Burndown</h2>
              <NavLink
                to={ROUTES.development.velocity(projectId || '')}
                className="text-sm text-violet-400 hover:text-violet-300"
              >
                View Details
              </NavLink>
            </div>
            
            {/* Mini burndown chart placeholder */}
            <div className="h-32 bg-zinc-800/50 rounded-lg flex items-end justify-around p-4">
              {[80, 70, 55, 45, 40, 35, 25, 18].map((height, i) => (
                <div
                  key={i}
                  className="w-4 bg-gradient-to-t from-violet-500 to-violet-400 rounded-t"
                  style={{ height: `${height}%` }}
                />
              ))}
            </div>
            <div className="flex items-center justify-between mt-4 text-xs text-zinc-500">
              <span>Day 1</span>
              <span>Day 8</span>
            </div>
          </div>
        </div>

        {/* Recent Activity */}
        <div className="lg:col-span-2">
          <div className="bg-zinc-900 border border-zinc-800 rounded-xl p-6">
            <div className="flex items-center justify-between mb-6">
              <h2 className="text-lg font-semibold text-white">Recent Activity</h2>
              <button className="text-sm text-violet-400 hover:text-violet-300">
                View All
              </button>
            </div>
            <ActivityFeed items={recentActivity} />
          </div>

          {/* Team Activity */}
          <div className="bg-zinc-900 border border-zinc-800 rounded-xl p-6 mt-6">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg font-semibold text-white">Team Activity</h2>
              <div className="flex items-center gap-2">
                <Users className="w-4 h-4 text-zinc-500" />
                <span className="text-sm text-zinc-400">5 active now</span>
              </div>
            </div>

            <div className="flex items-center gap-2">
              {[
                { name: 'Sarah', color: 'from-violet-400 to-fuchsia-400' },
                { name: 'Alex', color: 'from-cyan-400 to-blue-400' },
                { name: 'Mike', color: 'from-emerald-400 to-teal-400' },
                { name: 'Emma', color: 'from-amber-400 to-orange-400' },
                { name: 'John', color: 'from-pink-400 to-rose-400' },
              ].map((member, i) => (
                <div
                  key={member.name}
                  className={cn(
                    'w-10 h-10 rounded-full flex items-center justify-center',
                    'bg-gradient-to-br text-white text-sm font-medium',
                    member.color,
                    i > 0 && '-ml-2',
                    'border-2 border-zinc-900'
                  )}
                  title={member.name}
                >
                  {member.name.charAt(0)}
                </div>
              ))}
              <span className="ml-2 text-sm text-zinc-500">All working on Sprint 12</span>
            </div>
          </div>

          {/* Upcoming */}
          <div className="bg-zinc-900 border border-zinc-800 rounded-xl p-6 mt-6">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg font-semibold text-white">Upcoming</h2>
              <Calendar className="w-4 h-4 text-zinc-500" />
            </div>

            <div className="space-y-3">
              {[
                { label: 'Sprint Planning', time: 'Tomorrow, 10:00 AM', type: 'meeting' },
                { label: 'PR Review Session', time: 'Tomorrow, 2:00 PM', type: 'review' },
                { label: 'Release v1.3.0', time: 'Friday', type: 'release' },
              ].map((event) => (
                <div
                  key={event.label}
                  className="flex items-center justify-between p-3 rounded-lg bg-zinc-800/50"
                >
                  <div>
                    <div className="text-sm font-medium text-white">{event.label}</div>
                    <div className="text-xs text-zinc-500">{event.time}</div>
                  </div>
                  <span
                    className={cn(
                      'px-2 py-1 rounded text-xs font-medium',
                      event.type === 'meeting' && 'bg-violet-500/20 text-violet-400',
                      event.type === 'review' && 'bg-cyan-500/20 text-cyan-400',
                      event.type === 'release' && 'bg-emerald-500/20 text-emerald-400'
                    )}
                  >
                    {event.type}
                  </span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default DevDashboardPage;
