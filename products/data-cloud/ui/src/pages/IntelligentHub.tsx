/**
 * Intelligent Hub Page
 *
 * Unified home page that replaces 20+ separate pages with a single
 * adaptive interface. Features Command Bar, Brain Sidebar, and
 * Ambient Intelligence Bar.
 *
 * Features:
 * - Single entry point for all actions
 * - Context-aware workspace that adapts to user intent
 * - Always-visible AI assistant sidebar
 * - Ambient notifications at bottom
 * - Personalized greeting with time-aware messaging
 * - Continue working section with recent items
 * - "Ask Anything" natural language input
 *
 * @doc.type page
 * @doc.purpose Unified intelligent home page
 * @doc.layer frontend
 */

import React, { useState, useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import {
  Database,
  Workflow,
  Shield,
  Sparkles,
  TrendingUp,
  AlertTriangle,
  Clock,
  ArrowRight,
  Plus,
  Search,
  BarChart3,
  Activity,
  Send,
  Play,
  FileText,
  Table2,
} from 'lucide-react';
import { cn } from '../lib/theme';
import { CommandBar, CommandBarTrigger, BrainSidebar, AmbientIntelligenceBar } from '../components/core';
// Mock brain service for development
const mockBrainService = {
  getBrainStats: async () => ({
    spotlightItemsCount: 3,
    autonomyActionsToday: 12,
    averageConfidence: 0.85,
    activeSubsystems: 8,
  }),
};

// =============================================================================
// UTILITIES
// =============================================================================

/**
 * Get time-appropriate greeting
 */
function getGreeting(): string {
  const hour = new Date().getHours();
  if (hour < 12) return 'Good morning';
  if (hour < 18) return 'Good afternoon';
  return 'Good evening';
}

/**
 * Get formatted date
 */
function getFormattedDate(): string {
  return new Date().toLocaleDateString('en-US', {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
  });
}

/**
 * Quick action card
 */
interface QuickAction {
  id: string;
  title: string;
  description: string;
  icon: React.ReactNode;
  color: string;
  action: () => void;
}

/**
 * Insight card data
 */
interface Insight {
  id: string;
  title: string;
  value: string | number;
  change?: number;
  trend?: 'up' | 'down' | 'stable';
  icon: React.ReactNode;
}

/**
 * Quick Action Card Component
 */
function QuickActionCard({ action }: { action: QuickAction }) {
  return (
    <button
      onClick={action.action}
      className={cn(
        'group flex flex-col items-start gap-3 p-4',
        'bg-white dark:bg-gray-800',
        'border border-gray-200 dark:border-gray-700',
        'rounded-xl shadow-sm',
        'hover:shadow-md hover:border-primary-300 dark:hover:border-primary-700',
        'transition-all duration-200'
      )}
    >
      <div
        className={cn(
          'p-2 rounded-lg',
          action.color
        )}
      >
        {action.icon}
      </div>
      <div className="text-left">
        <h3 className="text-sm font-medium text-gray-900 dark:text-gray-100 group-hover:text-primary-600 dark:group-hover:text-primary-400">
          {action.title}
        </h3>
        <p className="text-xs text-gray-500 mt-0.5">
          {action.description}
        </p>
      </div>
      <ArrowRight className="h-4 w-4 text-gray-300 group-hover:text-primary-500 ml-auto transition-colors" />
    </button>
  );
}

/**
 * Insight Card Component
 */
function InsightCard({ insight }: { insight: Insight }) {
  const getTrendColor = (trend?: string) => {
    switch (trend) {
      case 'up':
        return 'text-green-500';
      case 'down':
        return 'text-red-500';
      default:
        return 'text-gray-400';
    }
  };

  return (
    <div
      className={cn(
        'flex items-center gap-4 p-4',
        'bg-white dark:bg-gray-800',
        'border border-gray-200 dark:border-gray-700',
        'rounded-xl'
      )}
    >
      <div className="p-2 bg-gray-100 dark:bg-gray-700 rounded-lg">
        {insight.icon}
      </div>
      <div className="flex-1">
        <p className="text-xs text-gray-500">{insight.title}</p>
        <p className="text-lg font-semibold text-gray-900 dark:text-gray-100">
          {insight.value}
        </p>
      </div>
      {insight.change !== undefined && (
        <div className={cn('flex items-center gap-1', getTrendColor(insight.trend))}>
          {insight.trend === 'up' ? (
            <TrendingUp className="h-4 w-4" />
          ) : insight.trend === 'down' ? (
            <TrendingUp className="h-4 w-4 rotate-180" />
          ) : null}
          <span className="text-sm font-medium">
            {insight.change > 0 ? '+' : ''}{insight.change}%
          </span>
        </div>
      )}
    </div>
  );
}

/**
 * Recent Activity Item
 */
interface ActivityItem {
  id: string;
  action: string;
  target: string;
  timestamp: string;
  type: 'create' | 'update' | 'delete' | 'query' | 'alert';
}

/**
 * Continue Working Item
 */
interface ContinueWorkingItem {
  id: string;
  name: string;
  type: 'collection' | 'workflow' | 'query' | 'dashboard';
  lastAccessed: string;
  path: string;
}

function ContinueWorkingCard({ item, onClick }: { item: ContinueWorkingItem; onClick: () => void }) {
  const typeIcons = {
    collection: <Database className="h-4 w-4 text-blue-500" />,
    workflow: <Workflow className="h-4 w-4 text-purple-500" />,
    query: <FileText className="h-4 w-4 text-green-500" />,
    dashboard: <BarChart3 className="h-4 w-4 text-amber-500" />,
  };

  return (
    <button
      onClick={onClick}
      className={cn(
        'flex items-center gap-3 px-4 py-3',
        'bg-white dark:bg-gray-800',
        'border border-gray-200 dark:border-gray-700',
        'rounded-lg',
        'hover:border-primary-300 dark:hover:border-primary-700',
        'hover:shadow-sm',
        'transition-all duration-200',
        'group'
      )}
    >
      <div className="p-2 bg-gray-100 dark:bg-gray-700 rounded">
        {typeIcons[item.type]}
      </div>
      <div className="flex-1 text-left min-w-0">
        <p className="text-sm font-medium text-gray-900 dark:text-gray-100 truncate group-hover:text-primary-600 dark:group-hover:text-primary-400">
          {item.name}
        </p>
        <p className="text-xs text-gray-500">{item.lastAccessed}</p>
      </div>
      <Play className="h-4 w-4 text-gray-300 group-hover:text-primary-500 transition-colors" />
    </button>
  );
}

/**
 * Ask Anything Input
 */
function AskAnythingInput({ onSubmit }: { onSubmit: (query: string) => void }) {
  const [query, setQuery] = useState('');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (query.trim()) {
      onSubmit(query);
      setQuery('');
    }
  };

  return (
    <form onSubmit={handleSubmit} className="relative">
      <div className={cn(
        'flex items-center gap-3 px-4 py-3',
        'bg-gradient-to-r from-purple-50 to-blue-50 dark:from-purple-900/20 dark:to-blue-900/20',
        'border border-purple-200 dark:border-purple-800',
        'rounded-xl',
        'focus-within:ring-2 focus-within:ring-primary-500 focus-within:ring-offset-2 dark:focus-within:ring-offset-gray-900',
        'transition-all duration-200'
      )}>
        <Sparkles className="h-5 w-5 text-purple-500" />
        <input
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Ask anything... e.g., 'Show me orders from last week' or 'Create a pipeline for...'"
          className={cn(
            'flex-1 bg-transparent border-none outline-none',
            'text-sm text-gray-900 dark:text-gray-100',
            'placeholder:text-gray-500'
          )}
        />
        <button
          type="submit"
          disabled={!query.trim()}
          className={cn(
            'p-2 rounded-lg',
            'bg-primary-600 text-white',
            'hover:bg-primary-700',
            'disabled:opacity-50 disabled:cursor-not-allowed',
            'transition-colors'
          )}
        >
          <Send className="h-4 w-4" />
        </button>
      </div>
    </form>
  );
}

function RecentActivityItem({ item }: { item: ActivityItem }) {
  const typeIcons = {
    create: <Plus className="h-3 w-3 text-green-500" />,
    update: <Activity className="h-3 w-3 text-blue-500" />,
    delete: <AlertTriangle className="h-3 w-3 text-red-500" />,
    query: <Search className="h-3 w-3 text-purple-500" />,
    alert: <AlertTriangle className="h-3 w-3 text-amber-500" />,
  };

  return (
    <div className="flex items-center gap-3 py-2">
      <div className="p-1.5 bg-gray-100 dark:bg-gray-700 rounded">
        {typeIcons[item.type]}
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-sm text-gray-900 dark:text-gray-100 truncate">
          {item.action}
        </p>
        <p className="text-xs text-gray-500 truncate">{item.target}</p>
      </div>
      <span className="text-xs text-gray-400">{item.timestamp}</span>
    </div>
  );
}

/**
 * Intelligent Hub Page
 */
export function IntelligentHub() {
  const navigate = useNavigate();
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);

  // Fetch brain stats
  const { data: brainStats } = useQuery({
    queryKey: ['brain-stats'],
    queryFn: () => mockBrainService.getBrainStats(),
    staleTime: 60000,
  });

  // Quick actions
  const quickActions: QuickAction[] = [
    {
      id: 'explore-data',
      title: 'Explore Data',
      description: 'Browse collections and datasets',
      icon: <Database className="h-5 w-5 text-white" />,
      color: 'bg-gradient-to-br from-blue-500 to-blue-600',
      action: () => navigate('/data'),
    },
    {
      id: 'create-workflow',
      title: 'Build Pipeline',
      description: 'Create a new data workflow',
      icon: <Workflow className="h-5 w-5 text-white" />,
      color: 'bg-gradient-to-br from-purple-500 to-purple-600',
      action: () => navigate('/pipelines/new'),
    },
    {
      id: 'check-quality',
      title: 'Data Quality',
      description: 'Review quality issues',
      icon: <Activity className="h-5 w-5 text-white" />,
      color: 'bg-gradient-to-br from-green-500 to-green-600',
      action: () => navigate('/data?view=quality'),
    },
    {
      id: 'governance',
      title: 'Trust Center',
      description: 'Governance & compliance',
      icon: <Shield className="h-5 w-5 text-white" />,
      color: 'bg-gradient-to-br from-amber-500 to-amber-600',
      action: () => navigate('/trust'),
    },
  ];

  // Insights
  const insights: Insight[] = [
    {
      id: 'collections',
      title: 'Total Collections',
      value: '24',
      change: 12,
      trend: 'up',
      icon: <Database className="h-5 w-5 text-blue-500" />,
    },
    {
      id: 'workflows',
      title: 'Active Pipelines',
      value: '8',
      change: 0,
      trend: 'stable',
      icon: <Workflow className="h-5 w-5 text-purple-500" />,
    },
    {
      id: 'quality',
      title: 'Data Quality Score',
      value: '94%',
      change: 3,
      trend: 'up',
      icon: <BarChart3 className="h-5 w-5 text-green-500" />,
    },
    {
      id: 'actions',
      title: 'AI Actions Today',
      value: brainStats?.autonomyActionsToday || 0,
      icon: <Sparkles className="h-5 w-5 text-amber-500" />,
    },
  ];

  // Mock recent activity
  const recentActivity: ActivityItem[] = [
    {
      id: '1',
      action: 'Schema updated',
      target: 'customer_events',
      timestamp: '5m ago',
      type: 'update',
    },
    {
      id: '2',
      action: 'Query executed',
      target: 'SELECT * FROM orders...',
      timestamp: '12m ago',
      type: 'query',
    },
    {
      id: '3',
      action: 'Pipeline completed',
      target: 'daily_aggregation',
      timestamp: '1h ago',
      type: 'create',
    },
    {
      id: '4',
      action: 'Quality alert',
      target: 'user_profiles: 5% null values',
      timestamp: '2h ago',
      type: 'alert',
    },
  ];

  // Continue working items (would come from user activity API)
  const continueWorking: ContinueWorkingItem[] = [
    {
      id: '1',
      name: 'customer_events',
      type: 'collection',
      lastAccessed: 'Edited 5 min ago',
      path: '/data/collections/customer_events',
    },
    {
      id: '2',
      name: 'daily_aggregation',
      type: 'workflow',
      lastAccessed: 'Ran 1 hour ago',
      path: '/pipelines/daily_aggregation',
    },
    {
      id: '3',
      name: 'Sales by Region',
      type: 'query',
      lastAccessed: 'Opened yesterday',
      path: '/sql?query=sales_by_region',
    },
  ];

  // Handle ask anything
  const handleAskAnything = useCallback((query: string) => {
    // In a real implementation, this would trigger the command bar or AI
    console.log('Ask:', query);
    // For now, navigate to search
    navigate(`/data?search=${encodeURIComponent(query)}`);
  }, [navigate]);

  // Get personalized greeting
  const greeting = useMemo(() => getGreeting(), []);
  const formattedDate = useMemo(() => getFormattedDate(), []);

  return (
    <div className="flex h-full">
      {/* Brain Sidebar */}
      <BrainSidebar
        collapsed={sidebarCollapsed}
        onCollapsedChange={setSidebarCollapsed}
      />

      {/* Main Content */}
      <div className="flex-1 flex flex-col min-w-0">
        {/* Header with Command Bar */}
        <header className="flex items-center justify-between px-6 py-4 border-b border-gray-200 dark:border-gray-700">
          <div>
            <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">
              {greeting}
            </h1>
            <p className="text-sm text-gray-500 mt-0.5">
              {formattedDate} • Here's what's happening with your data
            </p>
          </div>
          <CommandBarTrigger />
        </header>

        {/* Content Area */}
        <main className="flex-1 overflow-y-auto p-6">
          {/* Ask Anything */}
          <section className="mb-8">
            <AskAnythingInput onSubmit={handleAskAnything} />
          </section>

          {/* Continue Working */}
          {continueWorking.length > 0 && (
            <section className="mb-8">
              <h2 className="text-sm font-medium text-gray-500 uppercase tracking-wide mb-4">
                Continue Working
              </h2>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                {continueWorking.map((item) => (
                  <ContinueWorkingCard
                    key={item.id}
                    item={item}
                    onClick={() => navigate(item.path)}
                  />
                ))}
              </div>
            </section>
          )}

          {/* Quick Actions */}
          <section className="mb-8">
            <h2 className="text-sm font-medium text-gray-500 uppercase tracking-wide mb-4">
              Quick Actions
            </h2>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
              {quickActions.map((action) => (
                <QuickActionCard key={action.id} action={action} />
              ))}
            </div>
          </section>

          {/* Insights */}
          <section className="mb-8">
            <h2 className="text-sm font-medium text-gray-500 uppercase tracking-wide mb-4">
              Insights
            </h2>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
              {insights.map((insight) => (
                <InsightCard key={insight.id} insight={insight} />
              ))}
            </div>
          </section>

          {/* Two Column Layout */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {/* Recent Activity */}
            <section>
              <div className="flex items-center justify-between mb-4">
                <h2 className="text-sm font-medium text-gray-500 uppercase tracking-wide">
                  Recent Activity
                </h2>
                <button className="text-xs text-primary-600 dark:text-primary-400 hover:underline">
                  View all
                </button>
              </div>
              <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-4">
                <div className="divide-y divide-gray-100 dark:divide-gray-700">
                  {recentActivity.map((item) => (
                    <RecentActivityItem key={item.id} item={item} />
                  ))}
                </div>
              </div>
            </section>

            {/* AI Recommendations */}
            <section>
              <div className="flex items-center justify-between mb-4">
                <h2 className="text-sm font-medium text-gray-500 uppercase tracking-wide">
                  AI Recommendations
                </h2>
                <span className="inline-flex items-center gap-1 text-xs text-purple-600 dark:text-purple-400">
                  <Sparkles className="h-3 w-3" />
                  Powered by Brain
                </span>
              </div>
              <div className="bg-gradient-to-br from-purple-50 to-pink-50 dark:from-purple-900/20 dark:to-pink-900/20 border border-purple-200 dark:border-purple-800 rounded-xl p-4">
                <div className="space-y-3">
                  <div className="flex items-start gap-3 p-3 bg-white/80 dark:bg-gray-800/80 rounded-lg">
                    <div className="p-1.5 bg-purple-100 dark:bg-purple-900/50 rounded">
                      <TrendingUp className="h-4 w-4 text-purple-600" />
                    </div>
                    <div>
                      <p className="text-sm font-medium text-gray-900 dark:text-gray-100">
                        Optimize query performance
                      </p>
                      <p className="text-xs text-gray-500 mt-0.5">
                        3 queries could save ~$45/month with suggested rewrites
                      </p>
                    </div>
                  </div>
                  <div className="flex items-start gap-3 p-3 bg-white/80 dark:bg-gray-800/80 rounded-lg">
                    <div className="p-1.5 bg-amber-100 dark:bg-amber-900/50 rounded">
                      <AlertTriangle className="h-4 w-4 text-amber-600" />
                    </div>
                    <div>
                      <p className="text-sm font-medium text-gray-900 dark:text-gray-100">
                        Data freshness alert
                      </p>
                      <p className="text-xs text-gray-500 mt-0.5">
                        'orders' table hasn't been updated in 6 hours
                      </p>
                    </div>
                  </div>
                  <div className="flex items-start gap-3 p-3 bg-white/80 dark:bg-gray-800/80 rounded-lg">
                    <div className="p-1.5 bg-green-100 dark:bg-green-900/50 rounded">
                      <Clock className="h-4 w-4 text-green-600" />
                    </div>
                    <div>
                      <p className="text-sm font-medium text-gray-900 dark:text-gray-100">
                        Pattern detected
                      </p>
                      <p className="text-xs text-gray-500 mt-0.5">
                        Weekly sales spike pattern can be pre-cached
                      </p>
                    </div>
                  </div>
                </div>
              </div>
            </section>
          </div>
        </main>

        {/* Ambient Intelligence Bar */}
        <AmbientIntelligenceBar />

        {/* Command Bar Modal */}
        <CommandBar />
      </div>
    </div>
  );
}

export default IntelligentHub;

