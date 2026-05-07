/**
 * Home Page
 *
 * Unified home page with contextual recommendations based on user activity.
 * Features Command Bar, Context Sidebar, and quick actions.
 *
 * Layout:
 * - Context Sidebar (collapsible) on the left
 * - Main content area with sections:
 *   - Outcome selection
 *   - Natural language input
 *
 * @doc.type page
 * @doc.purpose Unified home page
 * @doc.layer frontend
 */

import React, { useState, useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router';
import { useQuery, useMutation } from '@tanstack/react-query';
import { brainService } from '../api/brain.service';
import { collectionsApi } from '../lib/api/collections';
import { workflowsApi } from '../lib/api/workflows';
import { getRecentActivity, logActivity } from '../lib/api/user-activity';
import SessionBootstrap, { type ShellRole, canAccessShellRole } from '../lib/auth/session';
import {
  Database,
  Workflow,
  Shield,
  Sparkles,
  TrendingUp,
  AlertTriangle,
  ArrowRight,
  Plus,
  Search,
  BarChart3,
  Activity,
  Send,
  Play,
  FileText,
  Table2,
  User,
  Wrench,
} from 'lucide-react';
import { cn } from '../lib/theme';
import { CommandBar, CommandBarTrigger, ContextSidebar, AmbientIntelligenceBar } from '../components/core';

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
  type: 'collection' | 'workflow' | 'query' | 'insight';
  lastAccessed: string;
  path: string;
}

export interface OutcomeIntent {
  kind: 'query' | 'workflow' | 'governance' | 'operations';
  label: string;
  destination: string;
  state?: Record<string, string>;
}

export function classifyOutcomeIntent(query: string): OutcomeIntent {
  const normalized = query.toLowerCase();

  if (/(workflow|pipeline|automate|sync|ingest|etl|approval)/.test(normalized)) {
    return {
      kind: 'workflow',
      label: 'Build an automated flow',
      destination: '/pipelines/new',
      state: { intent: query },
    };
  }

  if (/(trust|policy|retention|pii|privacy|redact|purge|governance|compliance|audit)/.test(normalized)) {
    return {
      kind: 'governance',
      label: 'Review trust issues',
      destination: '/trust',
      state: { intent: query },
    };
  }

  if (/(failure|failed|incident|alert|outage|latency|degraded|broken)/.test(normalized)) {
    return {
      kind: 'operations',
      label: 'Inspect recent failures',
      destination: '/insights',
      state: { focus: 'operations', query },
    };
  }

  return {
    kind: 'query',
    label: 'Ask a question',
    destination: '/query',
    state: { query },
  };
}

function ContinueWorkingCard({ item, onClick }: { item: ContinueWorkingItem; onClick: () => void }) {
  const typeIcons = {
    collection: <Database className="h-4 w-4 text-blue-500" />,
    workflow: <Workflow className="h-4 w-4 text-purple-500" />,
    query: <FileText className="h-4 w-4 text-green-500" />,
    insight: <BarChart3 className="h-4 w-4 text-amber-500" />,
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

interface OutcomeAction {
  id: string;
  title: string;
  description: string;
  icon: React.ReactNode;
  supportingText: string;
  onClick: () => void;
}

function OutcomeActionCard({ action }: { action: OutcomeAction }) {
  return (
    <button
      onClick={action.onClick}
      className={cn(
        'flex flex-col items-start gap-3 rounded-2xl border border-gray-200 bg-white p-5 text-left shadow-sm transition-all duration-200 hover:border-primary-300 hover:shadow-md dark:border-gray-700 dark:bg-gray-800 dark:hover:border-primary-700'
      )}
    >
      <div className="flex items-center gap-3">
        <div className="rounded-xl bg-gray-100 p-2 dark:bg-gray-700">{action.icon}</div>
        <div>
          <h3 className="text-base font-semibold text-gray-900 dark:text-gray-100">{action.title}</h3>
          <p className="text-sm text-gray-500">{action.description}</p>
        </div>
      </div>
      <p className="text-sm text-gray-600 dark:text-gray-300">{action.supportingText}</p>
      <span className="inline-flex items-center gap-1 text-sm font-medium text-primary-600 dark:text-primary-400">
        Open
        <ArrowRight className="h-4 w-4" />
      </span>
    </button>
  );
}

function buildQuickActions(
  navigate: ReturnType<typeof useNavigate>,
  logActivityMutation: { mutate: (data: Parameters<typeof logActivity>[0]) => void },
  shellRole: ShellRole,
): QuickAction[] {
  const actions: QuickAction[] = [
    {
      id: 'explore-data',
      title: 'Explore Data',
      description: 'Browse collections and datasets',
      icon: <Database className="h-5 w-5 text-white" />,
      color: 'bg-gradient-to-br from-blue-500 to-blue-600',
      action: () => {
        logActivityMutation.mutate({
          action: 'navigate',
          target: 'Explore Data',
          type: 'query',
          resourceType: 'navigation',
          resourceId: '/data',
        });
        navigate('/data');
      },
    },
    {
      id: 'create-workflow',
      title: 'Build Pipeline',
      description: 'Create a new data workflow',
      icon: <Workflow className="h-5 w-5 text-white" />,
      color: 'bg-gradient-to-br from-purple-500 to-purple-600',
      action: () => {
        logActivityMutation.mutate({
          action: 'navigate',
          target: 'Build Pipeline',
          type: 'create',
          resourceType: 'navigation',
          resourceId: '/pipelines/new',
        });
        navigate('/pipelines/new');
      },
    },
  ];

  if (canAccessShellRole(shellRole, 'operator')) {
    actions.push(
      {
        id: 'check-quality',
        title: 'Data Quality',
        description: 'Review quality issues',
        icon: <Activity className="h-5 w-5 text-white" />,
        color: 'bg-gradient-to-br from-green-500 to-green-600',
        action: () => {
          logActivityMutation.mutate({
            action: 'navigate',
            target: 'Data Quality',
            type: 'query',
            resourceType: 'navigation',
            resourceId: '/data?view=quality',
          });
          navigate('/data?view=quality');
        },
      },
      {
        id: 'governance',
        title: 'Trust Center',
        description: 'Governance & compliance',
        icon: <Shield className="h-5 w-5 text-white" />,
        color: 'bg-gradient-to-br from-amber-500 to-amber-600',
        action: () => {
          logActivityMutation.mutate({
            action: 'navigate',
            target: 'Trust Center',
            type: 'query',
            resourceType: 'navigation',
            resourceId: '/trust',
          });
          navigate('/trust');
        },
      },
    );
  }

  return actions;
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
          placeholder="What do you need to do? Ask a question, build a flow, or review trust issues"
          aria-label="Ask a question, build a flow, or review trust issues"
          className={cn(
            'flex-1 bg-transparent border-none outline-none',
            'text-sm text-gray-900 dark:text-gray-100',
            'placeholder:text-gray-500'
          )}
        />
        <button
          type="submit"
          aria-label="Send"
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
/**
 * Persona type for progressive disclosure
 */
type Persona = 'primary' | 'operator' | 'admin';

export function IntelligentHub(): React.ReactElement {
  const navigate = useNavigate();
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [persona, setPersona] = useState<Persona>('primary');
  const shellRole = SessionBootstrap.getShellRole();

  // Fetch brain stats
  const { data: brainStats } = useQuery({
    queryKey: ['brain-stats'],
    queryFn: () => brainService.getBrainStats(),
    staleTime: 60_000,
  });

  // Fetch collection count for insights
  const { data: collectionsPage } = useQuery({
    queryKey: ['collections-count'],
    queryFn: () => collectionsApi.list({ pageSize: 1 }),
    staleTime: 120_000,
  });

  // Fetch active workflow count for insights
  const { data: workflowsPage } = useQuery({
    queryKey: ['active-workflows-count'],
    queryFn: () => workflowsApi.list({ status: 'active', pageSize: 1 }),
    staleTime: 120_000,
  });

  // Fetch recent user activity
  const { data: activityData } = useQuery({
    queryKey: ['user-activity'],
    queryFn: () => getRecentActivity(),
    staleTime: 30_000,
  });

  // Audit logging mutation
  const logActivityMutation = useMutation({
    mutationFn: logActivity,
  });

  // Insights — using real data wherever available; filtered by persona
  const insights: Insight[] = [
    {
      id: 'collections',
      title: 'Total Collections',
      value: collectionsPage?.total ?? '–',
      icon: <Database className="h-5 w-5 text-blue-500" />,
    },
    {
      id: 'workflows',
      title: 'Active Pipelines',
      value: workflowsPage?.total ?? '–',
      icon: <Workflow className="h-5 w-5 text-purple-500" />,
    },
    ...(persona !== 'primary'
      ? [
        {
          id: 'patterns',
          title: 'Active Patterns',
          value: brainStats?.activePatterns ?? ('–' as string | number),
          icon: <BarChart3 className="h-5 w-5 text-green-500" />,
        },
        {
          id: 'processed',
          title: 'Records Processed',
          value: brainStats?.totalRecordsProcessed ?? ('–' as string | number),
          icon: <Sparkles className="h-5 w-5 text-amber-500" />,
        },
      ]
      : []),
  ];

  // Recent activity and continue-working items from user-activity API
  const recentActivity: ActivityItem[] = activityData?.activities ?? [];
  const continueWorking: ContinueWorkingItem[] = activityData?.continueWorking ?? [];

  const outcomeActions: OutcomeAction[] = useMemo(() => {
    const actions: OutcomeAction[] = [
      {
        id: 'ask-question',
        title: 'Ask a question',
        description: 'Start from natural language and land in the SQL workspace.',
        icon: <Search className="h-5 w-5 text-blue-500" />,
        supportingText: collectionsPage?.total
          ? `${collectionsPage.total} collections are ready for exploration.`
          : 'Use the live SQL workspace instead of navigating raw modules first.',
        onClick: () => navigate('/query'),
      },
      {
        id: 'build-flow',
        title: 'Build an automated flow',
        description: 'Create or refine a pipeline from one place.',
        icon: <Workflow className="h-5 w-5 text-purple-500" />,
        supportingText: workflowsPage?.total
          ? `${workflowsPage.total} active pipelines are already running.`
          : 'Start a new workflow without leaving the launcher page.',
        onClick: () => navigate('/pipelines/new'),
      },
    ];

    // IA-004: Operator/admin personas expose additional actions
    if (persona === 'operator' || persona === 'admin') {
      actions.push(
        {
          id: 'review-trust',
          title: 'Review trust issues',
          description: 'Jump straight to retention, privacy, and audit actions.',
          icon: <Shield className="h-5 w-5 text-amber-500" />,
          supportingText: 'Trust Center is the canonical place for policy actions and audit visibility.',
          onClick: () => navigate('/trust'),
        },
        {
          id: 'inspect-failures',
          title: 'Inspect recent failures',
          description: 'Open the operator view for degraded runtime paths.',
          icon: <AlertTriangle className="h-5 w-5 text-red-500" />,
          supportingText: recentActivity.length > 0
            ? `${recentActivity.length} recent activity items are available for investigation.`
            : 'Use Insights for runtime truth when something looks degraded.',
          onClick: () => navigate('/insights'),
        },
      );
    }

    // IA-004: Admin persona adds workspace shortcuts
    if (persona === 'admin') {
      actions.push(
        {
          id: 'admin-settings',
          title: 'System Settings',
          description: 'Configure platform-wide preferences and controls.',
          icon: <Table2 className="h-5 w-5 text-gray-500" />,
          supportingText: 'Admin workspace with privileged controls and blast-radius awareness.',
          onClick: () => navigate('/settings'),
        },
      );
    }

    return actions;
  }, [collectionsPage?.total, navigate, recentActivity.length, shellRole, workflowsPage?.total, persona]);

  // Handle ask anything
  const handleAskAnything = useCallback((query: string) => {
    const intent = classifyOutcomeIntent(query);
    logActivityMutation.mutate({
      action: intent.kind,
      target: query,
      type: 'query',
      resourceType: 'outcome-intent',
      resourceId: intent.destination,
    });
    navigate(intent.destination, intent.state ? { state: intent.state } : undefined);
  }, [navigate, logActivityMutation]);

  // Get personalized greeting
  const greeting = useMemo(() => getGreeting(), []);
  const formattedDate = useMemo(() => getFormattedDate(), []);

  return (
    <div className="flex h-full" data-testid="intelligent-hub-page">
      {/* Context Sidebar */}
      <ContextSidebar
        collapsed={sidebarCollapsed}
        onCollapsedChange={setSidebarCollapsed}
      />

      {/* Main Content */}
      <div className="flex-1 flex flex-col min-w-0">
        {/* Header with Command Bar */}
        <header className="flex items-center justify-between px-6 py-4 border-b border-gray-200 dark:border-gray-700" data-testid="intelligent-hub-header">
          <div>
            <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">
              {greeting}
            </h1>
            <p className="text-sm text-gray-500 mt-0.5">
              {formattedDate} • Here's what's happening with your data
            </p>
          </div>
          <div className="flex items-center gap-3">
            {/* View mode switcher — IA-004 progressive disclosure; does not change backend permissions */}
            <div className="hidden sm:flex items-center gap-1 bg-gray-100 dark:bg-gray-800 rounded-lg p-1" data-testid="persona-switcher" aria-label="View mode">
              {([
                { id: 'primary' as Persona, label: 'Standard', icon: <User className="h-3.5 w-3.5" /> },
                { id: 'operator' as Persona, label: 'Operator', icon: <Wrench className="h-3.5 w-3.5" /> },
                { id: 'admin' as Persona, label: 'Admin', icon: <Shield className="h-3.5 w-3.5" /> },
              ] as { id: Persona; label: string; icon: React.ReactNode }[]).map((p) => (
                <button
                  key={p.id}
                  onClick={() => setPersona(p.id)}
                  className={cn(
                    'flex items-center gap-1.5 px-2.5 py-1.5 rounded-md text-xs font-medium transition-colors',
                    persona === p.id
                      ? 'bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 shadow-sm'
                      : 'text-gray-500 hover:text-gray-700 dark:hover:text-gray-300'
                  )}
                  aria-pressed={persona === p.id}
                >
                  {p.icon}
                  {p.label}
                </button>
              ))}
            </div>
            <CommandBarTrigger />
          </div>
        </header>

        {/* Content Area — DC-UX-008: Prioritized work queue layout */}
        <section className="flex-1 overflow-y-auto p-6">

          {/* 1. Resume work — most important for returning users */}
          {continueWorking.length > 0 && (
            <section className="mb-8" data-testid="intelligent-hub-continue-working">
              <h2 className="text-sm font-medium text-gray-500 uppercase tracking-wide mb-3">
                Continue Where You Left Off
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

          {/* 2. What to do next — one outcome at a time */}
          <section className="mb-8" data-testid="intelligent-hub-outcome-section">
            <div className="mb-4">
              <p className="text-sm font-medium uppercase tracking-wide text-gray-500">Next action</p>
              <h2 className="mt-1 text-xl font-semibold text-gray-900 dark:text-gray-100">
                {persona === 'primary'
                  ? 'Query your data or build a pipeline'
                  : persona === 'operator'
                    ? 'Inspect, review trust, and manage operational health'
                    : 'Admin controls — check blast radius before acting'}
              </h2>
            </div>
            <div className="grid grid-cols-1 gap-4 lg:grid-cols-2 xl:grid-cols-4">
              {outcomeActions.map((action) => (
                <OutcomeActionCard key={action.id} action={action} />
              ))}
            </div>
          </section>

          {/* 3. Natural language intent */}
          <section className="mb-8" data-testid="intelligent-hub-intent-input">
            <AskAnythingInput onSubmit={handleAskAnything} />
          </section>

          {/* 4. Key metrics — only the most useful numbers */}
          <section className="mb-8" data-testid="intelligent-hub-insights">
            <h2 className="text-sm font-medium text-gray-500 uppercase tracking-wide mb-4">
              Platform snapshot
            </h2>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
              {insights.map((insight) => (
                <InsightCard key={insight.id} insight={insight} />
              ))}
            </div>
          </section>

          {/* 5. Recent activity — only when populated */}
          {recentActivity.length > 0 && (
            <section data-testid="intelligent-hub-recent-activity">
              <div className="flex items-center justify-between mb-4">
                <h2 className="text-sm font-medium text-gray-500 uppercase tracking-wide">
                  Recent Activity
                </h2>
              </div>
              <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-4">
                <div className="divide-y divide-gray-100 dark:divide-gray-700">
                  {recentActivity.slice(0, 6).map((item) => (
                    <RecentActivityItem key={item.id} item={item} />
                  ))}
                </div>
              </div>
            </section>
          )}
        </section>

        {/* Ambient Intelligence Bar */}
        <AmbientIntelligenceBar />

        {/* Command Bar Modal */}
        <CommandBar />
      </div>
    </div>
  );
}

export default IntelligentHub;

