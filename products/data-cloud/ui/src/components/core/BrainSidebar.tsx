/**
 * Brain Sidebar Component
 *
 * Always-visible collapsible sidebar that hosts AI assistant components.
 * Integrates existing SpotlightRing, AutonomyTimeline, FeedbackWidget, and O11y.
 *
 * Features:
 * - Collapsible to icon-only mode
 * - Real-time updates via WebSocket
 * - Clickable items navigate to context
 * - Feedback buttons on recommendations
 * - O11y tab for execution monitoring
 *
 * @doc.type component
 * @doc.purpose Always-visible AI assistant sidebar
 * @doc.layer frontend
 */

import React, { useState } from 'react';
import {
  Brain,
  ChevronLeft,
  ChevronRight,
  Sparkles,
  Activity,
  MessageSquare,
  Lightbulb,
  AlertTriangle,
  TrendingUp,
  Zap,
  Clock,
  Gauge,
} from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { cn } from '../../lib/theme';
import { brainService, type SpotlightItem, type AutonomyAction, type LearningSignal } from '../../api/brain.service';
import { O11yPanel } from './O11yPanel';

type SidebarTab = 'brain' | 'o11y';

interface BrainSidebarProps {
  collapsed?: boolean;
  onCollapsedChange?: (collapsed: boolean) => void;
  defaultTab?: SidebarTab;
  className?: string;
}

/**
 * Spotlight Item Card
 */
function SpotlightCard({ item }: { item: SpotlightItem }) {
  const getSeverityColor = (score: number): string => {
    if (score >= 0.9) return 'border-l-red-500 bg-red-50 dark:bg-red-900/20';
    if (score >= 0.7) return 'border-l-orange-500 bg-orange-50 dark:bg-orange-900/20';
    if (score >= 0.5) return 'border-l-yellow-500 bg-yellow-50 dark:bg-yellow-900/20';
    return 'border-l-blue-500 bg-blue-50 dark:bg-blue-900/20';
  };

  const getCategoryIcon = (category: string) => {
    switch (category?.toLowerCase()) {
      case 'anomaly':
      case 'alert':
        return <AlertTriangle className="h-4 w-4 text-red-500" />;
      case 'performance':
        return <Activity className="h-4 w-4 text-blue-500" />;
      case 'trend':
        return <TrendingUp className="h-4 w-4 text-green-500" />;
      default:
        return <Zap className="h-4 w-4 text-purple-500" />;
    }
  };

  return (
    <div
      className={cn(
        'p-3 rounded-lg border-l-4 cursor-pointer',
        'hover:shadow-md transition-shadow',
        getSeverityColor(item.salienceScore.score)
      )}
    >
      <div className="flex items-start gap-2">
        {getCategoryIcon(item.category)}
        <div className="flex-1 min-w-0">
          <p className="text-sm font-medium text-gray-900 dark:text-gray-100 line-clamp-2">
            {item.summary}
          </p>
          <div className="flex items-center gap-2 mt-1">
            <span className="text-xs text-gray-500">
              {Math.round(item.salienceScore.score * 100)}% salience
            </span>
            {item.emergency && (
              <span className="text-xs px-1.5 py-0.5 bg-red-100 text-red-700 dark:bg-red-900 dark:text-red-300 rounded">
                Emergency
              </span>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

/**
 * Autonomy Action Item
 */
function AutonomyActionItem({ action }: { action: AutonomyAction }) {
  const getStatusColor = (status: string) => {
    switch (status) {
      case 'SUCCESS':
        return 'text-green-500';
      case 'FAILED':
        return 'text-red-500';
      case 'PENDING':
        return 'text-yellow-500';
      default:
        return 'text-blue-500';
    }
  };

  const formatTime = (timestamp: string) => {
    const date = new Date(timestamp);
    const now = new Date();
    const diff = now.getTime() - date.getTime();
    const minutes = Math.floor(diff / 60000);
    if (minutes < 60) return `${minutes}m ago`;
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `${hours}h ago`;
    return date.toLocaleDateString();
  };

  return (
    <div className="flex items-center gap-2 py-2 px-3 hover:bg-gray-50 dark:hover:bg-gray-800 rounded-lg cursor-pointer">
      <Activity className={cn('h-4 w-4', getStatusColor(action.status))} />
      <div className="flex-1 min-w-0">
        <p className="text-xs font-medium text-gray-700 dark:text-gray-300 truncate">
          {action.action}
        </p>
        <p className="text-xs text-gray-500">{action.domain}</p>
      </div>
      <span className="text-xs text-gray-400">{formatTime(action.timestamp)}</span>
    </div>
  );
}

/**
 * Learning Signal Item
 */
function LearningSignalItem({ signal }: { signal: LearningSignal }) {
  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'APPLIED':
        return <Sparkles className="h-4 w-4 text-green-500" />;
      case 'PROCESSED':
        return <Lightbulb className="h-4 w-4 text-blue-500" />;
      default:
        return <Clock className="h-4 w-4 text-gray-400" />;
    }
  };

  return (
    <div className="flex items-center gap-2 py-2 px-3 hover:bg-gray-50 dark:hover:bg-gray-800 rounded-lg cursor-pointer">
      {getStatusIcon(signal.status)}
      <div className="flex-1 min-w-0">
        <p className="text-xs font-medium text-gray-700 dark:text-gray-300 truncate">
          {signal.signalType}
        </p>
        <p className="text-xs text-gray-500">
          Impact: {Math.round(signal.impact * 100)}%
        </p>
      </div>
      <span
        className={cn(
          'text-xs px-1.5 py-0.5 rounded',
          signal.status === 'APPLIED'
            ? 'bg-green-100 text-green-700 dark:bg-green-900 dark:text-green-300'
            : signal.status === 'PROCESSED'
              ? 'bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300'
              : 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300'
        )}
      >
        {signal.status.toLowerCase()}
      </span>
    </div>
  );
}

/**
 * Brain Sidebar Component
 */
export function BrainSidebar({
  collapsed: controlledCollapsed,
  onCollapsedChange,
  defaultTab = 'brain',
  className,
}: BrainSidebarProps) {
  const [internalCollapsed, setInternalCollapsed] = useState(false);
  const [activeTab, setActiveTab] = useState<SidebarTab>(defaultTab);
  const collapsed = controlledCollapsed ?? internalCollapsed;

  const toggleCollapsed = () => {
    const newValue = !collapsed;
    setInternalCollapsed(newValue);
    onCollapsedChange?.(newValue);
  };

  // Fetch spotlight items
  const { data: spotlightItems = [], isLoading: spotlightLoading } = useQuery({
    queryKey: ['brain-spotlight'],
    queryFn: () => brainService.getSpotlight(),
    refetchInterval: 30000,
    staleTime: 15000,
  });

  // Fetch autonomy timeline
  const { data: autonomyActions = [], isLoading: autonomyLoading } = useQuery({
    queryKey: ['brain-autonomy'],
    queryFn: () => brainService.getAutonomyTimeline(undefined, 10),
    refetchInterval: 60000,
    staleTime: 30000,
  });

  // Fetch learning signals
  const { data: learningSignals = [], isLoading: learningLoading } = useQuery({
    queryKey: ['brain-learning'],
    queryFn: () => brainService.getLearningSignals(5),
    refetchInterval: 120000,
    staleTime: 60000,
  });

  const topSpotlight = spotlightItems.slice(0, 3);
  const recentActions = autonomyActions.slice(0, 5);
  const recentSignals = learningSignals.slice(0, 3);

  return (
    <aside
      className={cn(
        'flex flex-col h-full',
        'bg-white dark:bg-gray-900',
        'border-r border-gray-200 dark:border-gray-700',
        'transition-all duration-300',
        collapsed ? 'w-16' : 'w-72',
        className
      )}
    >
      {/* Header */}
      <div className="flex items-center justify-between p-3 border-b border-gray-200 dark:border-gray-700">
        {!collapsed && (
          <div className="flex items-center gap-2">
            <div className="p-1.5 bg-gradient-to-br from-purple-500 to-pink-500 rounded-lg">
              <Brain className="h-4 w-4 text-white" />
            </div>
            <span className="font-semibold text-gray-900 dark:text-gray-100">
              Brain
            </span>
          </div>
        )}
        {collapsed && (
          <div className="mx-auto p-1.5 bg-gradient-to-br from-purple-500 to-pink-500 rounded-lg">
            <Brain className="h-4 w-4 text-white" />
          </div>
        )}
        <button
          onClick={toggleCollapsed}
          className={cn(
            'p-1 hover:bg-gray-100 dark:hover:bg-gray-800 rounded',
            collapsed && 'mx-auto mt-2'
          )}
        >
          {collapsed ? (
            <ChevronRight className="h-4 w-4 text-gray-500" />
          ) : (
            <ChevronLeft className="h-4 w-4 text-gray-500" />
          )}
        </button>
      </div>

      {/* Content */}
      <div className="flex-1 overflow-y-auto">
        {collapsed ? (
          // Collapsed view - just icons
          <div className="flex flex-col items-center gap-4 py-4">
            <button
              onClick={() => { setActiveTab('brain'); toggleCollapsed(); }}
              className={cn(
                'p-2 hover:bg-gray-100 dark:hover:bg-gray-800 rounded-lg relative',
                activeTab === 'brain' && 'bg-purple-100 dark:bg-purple-900/30'
              )}
              title="Spotlight"
            >
              <Zap className="h-5 w-5 text-purple-500" />
              {topSpotlight.length > 0 && (
                <span className="absolute -top-1 -right-1 w-4 h-4 bg-purple-500 text-white text-xs rounded-full flex items-center justify-center">
                  {topSpotlight.length}
                </span>
              )}
            </button>
            <button
              onClick={() => { setActiveTab('o11y'); toggleCollapsed(); }}
              className={cn(
                'p-2 hover:bg-gray-100 dark:hover:bg-gray-800 rounded-lg relative',
                activeTab === 'o11y' && 'bg-green-100 dark:bg-green-900/30'
              )}
              title="Observability"
            >
              <Gauge className="h-5 w-5 text-green-500" />
            </button>
            <button
              className="p-2 hover:bg-gray-100 dark:hover:bg-gray-800 rounded-lg relative"
              title="Activity"
            >
              <Activity className="h-5 w-5 text-blue-500" />
              {recentActions.length > 0 && (
                <span className="absolute -top-1 -right-1 w-4 h-4 bg-blue-500 text-white text-xs rounded-full flex items-center justify-center">
                  {recentActions.length}
                </span>
              )}
            </button>
            <button
              className="p-2 hover:bg-gray-100 dark:hover:bg-gray-800 rounded-lg relative"
              title="Learning"
            >
              <Lightbulb className="h-5 w-5 text-amber-500" />
              {recentSignals.filter((s) => s.status === 'PENDING').length > 0 && (
                <span className="absolute -top-1 -right-1 w-4 h-4 bg-amber-500 text-white text-xs rounded-full flex items-center justify-center">
                  {recentSignals.filter((s) => s.status === 'PENDING').length}
                </span>
              )}
            </button>
          </div>
        ) : (
          // Expanded view
          <div className="flex flex-col h-full">
            {/* Tab Switcher */}
            <div className="flex border-b border-gray-200 dark:border-gray-700 px-3">
              <button
                onClick={() => setActiveTab('brain')}
                className={cn(
                  'flex items-center gap-1.5 px-3 py-2 text-sm font-medium border-b-2 -mb-px transition-colors',
                  activeTab === 'brain'
                    ? 'border-purple-500 text-purple-600 dark:text-purple-400'
                    : 'border-transparent text-gray-500 hover:text-gray-700 dark:hover:text-gray-300'
                )}
              >
                <Brain className="h-4 w-4" />
                Brain
              </button>
              <button
                onClick={() => setActiveTab('o11y')}
                className={cn(
                  'flex items-center gap-1.5 px-3 py-2 text-sm font-medium border-b-2 -mb-px transition-colors',
                  activeTab === 'o11y'
                    ? 'border-green-500 text-green-600 dark:text-green-400'
                    : 'border-transparent text-gray-500 hover:text-gray-700 dark:hover:text-gray-300'
                )}
              >
                <Gauge className="h-4 w-4" />
                O11y
              </button>
            </div>

            {/* Tab Content */}
            <div className="flex-1 overflow-y-auto p-3">
              {activeTab === 'brain' ? (
                <div className="space-y-4">
                  {/* Spotlight Section */}
                  <div>
                    <div className="flex items-center gap-2 mb-2">
                      <Zap className="h-4 w-4 text-purple-500" />
                      <h3 className="text-xs font-semibold text-gray-500 uppercase tracking-wide">
                        Spotlight
                      </h3>
                    </div>
                    {spotlightLoading ? (
                      <div className="animate-pulse space-y-2">
                        {[1, 2].map((i) => (
                          <div key={i} className="h-16 bg-gray-100 dark:bg-gray-800 rounded-lg" />
                        ))}
                      </div>
                    ) : topSpotlight.length > 0 ? (
                      <div className="space-y-2">
                        {topSpotlight.map((item) => (
                          <SpotlightCard key={item.id} item={item} />
                        ))}
                      </div>
                    ) : (
                      <p className="text-xs text-gray-400 text-center py-4">
                        No spotlight items
                      </p>
                    )}
                  </div>

                  {/* Autonomy Section */}
                  <div>
                    <div className="flex items-center gap-2 mb-2">
                      <Activity className="h-4 w-4 text-blue-500" />
                      <h3 className="text-xs font-semibold text-gray-500 uppercase tracking-wide">
                        Recent Actions
                      </h3>
                    </div>
                    {autonomyLoading ? (
                      <div className="animate-pulse space-y-1">
                        {[1, 2, 3].map((i) => (
                          <div key={i} className="h-10 bg-gray-100 dark:bg-gray-800 rounded-lg" />
                        ))}
                      </div>
                    ) : recentActions.length > 0 ? (
                      <div className="space-y-1">
                        {recentActions.map((action) => (
                          <AutonomyActionItem key={action.id} action={action} />
                        ))}
                      </div>
                    ) : (
                      <p className="text-xs text-gray-400 text-center py-4">
                        No recent actions
                      </p>
                    )}
                  </div>

                  {/* Learning Section */}
                  <div>
                    <div className="flex items-center gap-2 mb-2">
                      <Lightbulb className="h-4 w-4 text-amber-500" />
                      <h3 className="text-xs font-semibold text-gray-500 uppercase tracking-wide">
                        Learning
                      </h3>
                    </div>
                    {learningLoading ? (
                      <div className="animate-pulse space-y-1">
                        {[1, 2].map((i) => (
                          <div key={i} className="h-10 bg-gray-100 dark:bg-gray-800 rounded-lg" />
                        ))}
                      </div>
                    ) : recentSignals.length > 0 ? (
                      <div className="space-y-1">
                        {recentSignals.map((signal) => (
                          <LearningSignalItem key={signal.id} signal={signal} />
                        ))}
                      </div>
                    ) : (
                      <p className="text-xs text-gray-400 text-center py-4">
                        No learning signals
                      </p>
                    )}
                  </div>
                </div>
              ) : (
                <O11yPanel
                  onExpandExecution={(id) => {
                    // Navigate to execution details
                    window.location.href = `/pipelines/executions/${id}`;
                  }}
                />
              )}
            </div>
          </div>
        )}
      </div>

      {/* Footer */}
      {!collapsed && (
        <div className="p-3 border-t border-gray-200 dark:border-gray-700">
          <button
            className={cn(
              'w-full flex items-center justify-center gap-2',
              'px-3 py-2 rounded-lg',
              'bg-gradient-to-r from-purple-500 to-pink-500',
              'text-white text-sm font-medium',
              'hover:from-purple-600 hover:to-pink-600',
              'transition-colors'
            )}
          >
            <MessageSquare className="h-4 w-4" />
            Ask Brain
          </button>
        </div>
      )}
    </aside>
  );
}

export default BrainSidebar;

