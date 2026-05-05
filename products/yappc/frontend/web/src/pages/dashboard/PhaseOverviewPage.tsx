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
import { Button } from '@ghatana/design-system';

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
        return 'bg-success-bg text-success-color dark:bg-success-bg dark:text-success-color';
      case 'in_progress':
        return 'bg-info-bg text-info-color dark:bg-info-bg dark:text-info-color';
      case 'pending':
        return 'bg-surface-muted text-fg dark:bg-surface dark:text-fg-muted';
      case 'blocked':
        return 'bg-destructive-bg text-destructive dark:bg-destructive-bg dark:text-destructive';
    }
  };

  const getPriorityColor = (priority: PhaseTask['priority']) => {
    switch (priority) {
      case 'high':
        return 'text-destructive dark:text-destructive';
      case 'medium':
        return 'text-warning-color dark:text-warning-color';
      case 'low':
        return 'text-fg-muted dark:text-fg-muted';
    }
  };

  return (
    <div className="h-full overflow-auto bg-surface-muted p-6 dark:bg-surface">
      <div className="mx-auto max-w-7xl space-y-6">
        {/* Header */}
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-fg dark:text-fg-muted">
              {phase ? phase.charAt(0).toUpperCase() + phase.slice(1) : 'Phase'} Overview
            </h1>
            <p className="mt-1 text-sm text-fg-muted dark:text-fg-muted">
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
                className="rounded-lg border border-border bg-white p-6 dark:border-border dark:bg-surface"
              >
                <div className="flex items-center justify-between">
                  <div className="rounded-lg bg-info-bg p-2 dark:bg-info-bg">
                    <Icon className="h-5 w-5 text-info-color dark:text-info-color" />
                  </div>
                  {metric.change && (
                    <span
                      className={cn(
                        'text-sm font-medium',
                        metric.trend === 'up'
                          ? 'text-success-color dark:text-success-color'
                          : metric.trend === 'down'
                            ? 'text-destructive dark:text-destructive'
                            : 'text-fg-muted dark:text-fg-muted'
                      )}
                    >
                      {metric.change}
                    </span>
                  )}
                </div>
                <div className="mt-4">
                  <p className="text-sm text-fg-muted dark:text-fg-muted">{metric.label}</p>
                  <p className="mt-1 text-2xl font-semibold text-fg dark:text-fg-muted">
                    {metric.value}
                  </p>
                </div>
              </motion.div>
            );
          })}
        </div>

        {/* Recent Tasks */}
        <div className="rounded-lg border border-border bg-white dark:border-border dark:bg-surface">
          <div className="border-b border-border p-6 dark:border-border">
            <h2 className="text-lg font-semibold text-fg dark:text-fg-muted">
              Recent Tasks
            </h2>
          </div>
          <div className="divide-y divide-gray-200 dark:divide-gray-800">
            {recentTasks.map((task) => (
              <div
                key={task.id}
                className="flex items-center justify-between p-6 transition-colors hover:bg-surface-muted dark:hover:bg-surface"
              >
                <div className="flex-1">
                  <div className="flex items-center gap-3">
                    <h3 className="font-medium text-fg dark:text-fg-muted">
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
                  <div className="mt-1 flex items-center gap-4 text-sm text-fg-muted dark:text-fg-muted">
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
        <div className="rounded-lg border border-info-border bg-info-bg p-6 dark:border-info-border dark:bg-info-bg">
          <div className="flex items-start gap-4">
            <div className="rounded-lg bg-info-bg p-2 dark:bg-info-bg">
              <TrendingUp className="h-5 w-5 text-info-color dark:text-info-color" />
            </div>
            <div className="flex-1">
              <h3 className="font-semibold text-info-color dark:text-info-color">
                AI Suggestion
              </h3>
              <p className="mt-1 text-sm text-info-color dark:text-info-color">
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
