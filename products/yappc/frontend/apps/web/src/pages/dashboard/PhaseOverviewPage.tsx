/**
 * Phase Overview Page
 *
 * @description Generic phase overview component that displays phase-specific
 * metrics, progress, and quick actions. Used as default view for each phase tab.
 *
 * @doc.type page
 * @doc.purpose Phase-specific dashboard
 * @doc.layer page
 */

import React from 'react';
import { useParams } from 'react-router';
import { motion } from 'framer-motion';
import {
  TrendingUp,
  Clock,
  CheckCircle2,
  AlertCircle,
  ArrowRight,
} from 'lucide-react';

import { cn } from '../../utils/cn';
import { Button } from '@ghatana/ui';

// =============================================================================
// Types
// =============================================================================

interface PhaseMetric {
  id: string;
  label: string;
  value: string | number;
  change?: string;
  trend?: 'up' | 'down' | 'neutral';
  icon: React.ComponentType<{ className?: string }>;
}

interface PhaseTask {
  id: string;
  title: string;
  status: 'completed' | 'in_progress' | 'pending' | 'blocked';
  priority: 'high' | 'medium' | 'low';
  dueDate?: string;
}

// =============================================================================
// Component
// =============================================================================

const PhaseOverviewPage: React.FC = () => {
  const { phase } = useParams<{ projectId: string; phase: string }>();

  // Mock data - will be replaced with real data from state
  const metrics: PhaseMetric[] = [
    {
      id: 'progress',
      label: 'Phase Progress',
      value: '67%',
      change: '+12%',
      trend: 'up',
      icon: TrendingUp,
    },
    {
      id: 'tasks',
      label: 'Tasks Completed',
      value: '24/36',
      change: '+3',
      trend: 'up',
      icon: CheckCircle2,
    },
    {
      id: 'time',
      label: 'Time Remaining',
      value: '5 days',
      change: '-2 days',
      trend: 'down',
      icon: Clock,
    },
    {
      id: 'blockers',
      label: 'Active Blockers',
      value: '2',
      change: '+1',
      trend: 'down',
      icon: AlertCircle,
    },
  ];

  const recentTasks: PhaseTask[] = [
    {
      id: '1',
      title: 'Complete infrastructure setup',
      status: 'in_progress',
      priority: 'high',
      dueDate: '2026-02-05',
    },
    {
      id: '2',
      title: 'Review security configurations',
      status: 'pending',
      priority: 'medium',
      dueDate: '2026-02-06',
    },
    {
      id: '3',
      title: 'Deploy to staging environment',
      status: 'completed',
      priority: 'high',
    },
  ];

  const getStatusColor = (status: PhaseTask['status']) => {
    switch (status) {
      case 'completed':
        return 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200';
      case 'in_progress':
        return 'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200';
      case 'pending':
        return 'bg-gray-100 text-gray-800 dark:bg-gray-800 dark:text-gray-200';
      case 'blocked':
        return 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200';
    }
  };

  const getPriorityColor = (priority: PhaseTask['priority']) => {
    switch (priority) {
      case 'high':
        return 'text-red-600 dark:text-red-400';
      case 'medium':
        return 'text-yellow-600 dark:text-yellow-400';
      case 'low':
        return 'text-gray-600 dark:text-gray-400';
    }
  };

  return (
    <div className="h-full overflow-auto bg-gray-50 p-6 dark:bg-gray-900">
      <div className="mx-auto max-w-7xl space-y-6">
        {/* Header */}
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">
              {phase ? phase.charAt(0).toUpperCase() + phase.slice(1) : 'Phase'} Overview
            </h1>
            <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
              Track progress and manage tasks for this phase
            </p>
          </div>
          <Button variant="solid">
            <span>View All Tasks</span>
            <ArrowRight className="ml-2 h-4 w-4" />
          </Button>
        </div>

        {/* Metrics Grid */}
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {metrics.map((metric) => {
            const Icon = metric.icon;
            return (
              <motion.div
                key={metric.id}
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                className="rounded-lg border border-gray-200 bg-white p-6 dark:border-gray-800 dark:bg-gray-950"
              >
                <div className="flex items-center justify-between">
                  <div className="rounded-lg bg-blue-100 p-2 dark:bg-blue-900">
                    <Icon className="h-5 w-5 text-blue-600 dark:text-blue-400" />
                  </div>
                  {metric.change && (
                    <span
                      className={cn(
                        'text-sm font-medium',
                        metric.trend === 'up'
                          ? 'text-green-600 dark:text-green-400'
                          : metric.trend === 'down'
                            ? 'text-red-600 dark:text-red-400'
                            : 'text-gray-600 dark:text-gray-400'
                      )}
                    >
                      {metric.change}
                    </span>
                  )}
                </div>
                <div className="mt-4">
                  <p className="text-sm text-gray-500 dark:text-gray-400">{metric.label}</p>
                  <p className="mt-1 text-2xl font-semibold text-gray-900 dark:text-gray-100">
                    {metric.value}
                  </p>
                </div>
              </motion.div>
            );
          })}
        </div>

        {/* Recent Tasks */}
        <div className="rounded-lg border border-gray-200 bg-white dark:border-gray-800 dark:bg-gray-950">
          <div className="border-b border-gray-200 p-6 dark:border-gray-800">
            <h2 className="text-lg font-semibold text-gray-900 dark:text-gray-100">
              Recent Tasks
            </h2>
          </div>
          <div className="divide-y divide-gray-200 dark:divide-gray-800">
            {recentTasks.map((task) => (
              <div
                key={task.id}
                className="flex items-center justify-between p-6 transition-colors hover:bg-gray-50 dark:hover:bg-gray-900"
              >
                <div className="flex-1">
                  <div className="flex items-center gap-3">
                    <h3 className="font-medium text-gray-900 dark:text-gray-100">
                      {task.title}
                    </h3>
                    <span
                      className={cn(
                        'rounded-full px-2 py-0.5 text-xs font-medium',
                        getStatusColor(task.status)
                      )}
                    >
                      {task.status.replace('_', ' ')}
                    </span>
                  </div>
                  <div className="mt-1 flex items-center gap-4 text-sm text-gray-500 dark:text-gray-400">
                    <span className={getPriorityColor(task.priority)}>
                      {task.priority} priority
                    </span>
                    {task.dueDate && <span>Due {task.dueDate}</span>}
                  </div>
                </div>
                <Button variant="ghost" size="sm">
                  View
                </Button>
              </div>
            ))}
          </div>
        </div>

        {/* AI Suggestions */}
        <div className="rounded-lg border border-blue-200 bg-blue-50 p-6 dark:border-blue-900 dark:bg-blue-950">
          <div className="flex items-start gap-4">
            <div className="rounded-lg bg-blue-100 p-2 dark:bg-blue-900">
              <TrendingUp className="h-5 w-5 text-blue-600 dark:text-blue-400" />
            </div>
            <div className="flex-1">
              <h3 className="font-semibold text-blue-900 dark:text-blue-100">
                AI Suggestion
              </h3>
              <p className="mt-1 text-sm text-blue-800 dark:text-blue-200">
                Based on your current progress, consider prioritizing infrastructure setup
                to unblock dependent tasks. This could accelerate your timeline by 2 days.
              </p>
              <div className="mt-4 flex gap-2">
                <Button variant="solid" size="sm">
                  Apply Suggestion
                </Button>
                <Button variant="ghost" size="sm">
                  Dismiss
                </Button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default PhaseOverviewPage;
