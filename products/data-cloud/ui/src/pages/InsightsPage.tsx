/**
 * Insights Page
 *
 * Unified analytics dashboard combining:
 * - Brain Dashboard (AI system consciousness)
 * - Custom Dashboards (user-created analytics)
 * - Cost Optimization (spend analysis)
 *
 * @doc.type page
 * @doc.purpose Unified analytics and AI insights
 * @doc.layer frontend
 */

import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { brainService } from '../api/brain.service';
import { costService } from '../api/cost.service';
import { workflowsApi } from '../lib/api/workflows';
import {
  Brain,
  BarChart3,
  DollarSign,
  Activity,
  TrendingUp,
  TrendingDown,
  Sparkles,
  Zap,
  AlertTriangle,
  CheckCircle,
  Clock,
  Plus,
  RefreshCw,
} from 'lucide-react';
import { cn } from '../lib/theme';
import {
  PageHeader,
  PageContent,
  AISidebar,
  AISuggestion,
  StatCard,
} from '../components/layout/PageLayout';
import { SpotlightRing } from '../components/brain/SpotlightRing';
import { AutonomyTimeline } from '../components/brain/AutonomyTimeline';

// =============================================================================
// TYPES
// =============================================================================

type TabType = 'overview' | 'brain' | 'analytics' | 'cost';

interface BrainStats {
  spotlightItemsCount: number;
  autonomyActionsToday: number;
  averageConfidence: number;
  activeSubsystems: number;
}

// =============================================================================
// TAB NAVIGATION
// =============================================================================

interface TabButtonProps {
  id: TabType;
  label: string;
  icon: React.ReactNode;
  active: boolean;
  onClick: () => void;
}

function TabButton({ label, icon, active, onClick }: TabButtonProps) {
  return (
    <button
      onClick={onClick}
      className={cn(
        'flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-lg transition-colors',
        active
          ? 'bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-300'
          : 'text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800'
      )}
    >
      {icon}
      {label}
    </button>
  );
}

// =============================================================================
// OVERVIEW TAB
// =============================================================================

function OverviewTab({
  brainStats,
  activePipelines,
  monthlyCost,
}: {
  brainStats?: BrainStats;
  activePipelines?: number;
  monthlyCost?: number;
}) {
  return (
    <div className="space-y-6">
      {/* Stats Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          label="AI Actions Today"
          value={brainStats?.autonomyActionsToday || 0}
          icon={<Sparkles className="h-5 w-5" />}
          trend={{ value: 12, direction: 'up' }}
          color="purple"
        />
        <StatCard
          label="System Confidence"
          value={`${Math.round((brainStats?.averageConfidence || 0) * 100)}%`}
          icon={<Brain className="h-5 w-5" />}
          trend={{ value: 5, direction: 'up' }}
          color="blue"
        />
        <StatCard
          label="Active Pipelines"
          value={activePipelines ?? '–'}
          icon={<Activity className="h-5 w-5" />}
          trend={{ value: 0, direction: 'neutral' }}
          color="green"
        />
        <StatCard
          label="Est. Monthly Cost"
          value={monthlyCost != null ? `$${monthlyCost.toLocaleString()}` : '–'}
          icon={<DollarSign className="h-5 w-5" />}
          trend={{ value: 3, direction: 'down' }}
          color="yellow"
        />
      </div>

      {/* Two Column Layout */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* AI Spotlight */}
        <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-4">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-sm font-medium text-gray-900 dark:text-white flex items-center gap-2">
              <Zap className="h-4 w-4 text-yellow-500" />
              AI Spotlight
            </h3>
            <span className="text-xs text-gray-500">
              {brainStats?.spotlightItemsCount || 0} items
            </span>
          </div>
          <div className="space-y-3">
            <SpotlightItem
              title="Query optimization available"
              description="3 queries could save ~$45/month"
              type="optimization"
            />
            <SpotlightItem
              title="Data freshness alert"
              description="'orders' table hasn't updated in 6h"
              type="warning"
            />
            <SpotlightItem
              title="Pattern detected"
              description="Weekly sales spike can be pre-cached"
              type="insight"
            />
          </div>
        </div>

        {/* Recent Activity */}
        <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-4">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-sm font-medium text-gray-900 dark:text-white flex items-center gap-2">
              <Activity className="h-4 w-4 text-blue-500" />
              Recent AI Actions
            </h3>
            <button className="text-xs text-primary-600 dark:text-primary-400 hover:underline">
              View all
            </button>
          </div>
          <div className="space-y-3">
            <ActivityItem
              action="Auto-indexed"
              target="customer_events.email"
              time="5m ago"
              status="success"
            />
            <ActivityItem
              action="Schema validated"
              target="orders table"
              time="12m ago"
              status="success"
            />
            <ActivityItem
              action="Quality check"
              target="user_profiles"
              time="1h ago"
              status="warning"
            />
          </div>
        </div>
      </div>

      {/* Cost Savings Banner */}
      <div className="bg-gradient-to-r from-green-50 to-emerald-50 dark:from-green-900/20 dark:to-emerald-900/20 border border-green-200 dark:border-green-800 rounded-xl p-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-green-100 dark:bg-green-900/50 rounded-lg">
              <TrendingDown className="h-5 w-5 text-green-600 dark:text-green-400" />
            </div>
            <div>
              <p className="font-medium text-green-900 dark:text-green-100">
                $127 saved this month
              </p>
              <p className="text-sm text-green-700 dark:text-green-300">
                AI optimizations reduced query costs by 12%
              </p>
            </div>
          </div>
          <button className="px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 transition-colors text-sm">
            View Details
          </button>
        </div>
      </div>
    </div>
  );
}

// =============================================================================
// BRAIN TAB
// =============================================================================

function BrainTab() {
  return (
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
      {/* Left Column: Spotlight Ring */}
      <div className="lg:col-span-1">
        <SpotlightRing maxItems={5} autoRefresh={true} />
      </div>

      {/* Right Column: Autonomy Timeline */}
      <div className="lg:col-span-2">
        <AutonomyTimeline maxItems={10} showFilters={true} />
      </div>
    </div>
  );
}

// =============================================================================
// ANALYTICS TAB
// =============================================================================

function AnalyticsTab() {
  return (
    <div className="space-y-6">
      {/* Dashboard Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        <DashboardCard
          title="System Overview"
          description="Key metrics and health indicators"
          status="healthy"
        />
        <DashboardCard
          title="Data Quality"
          description="Quality scores across datasets"
          status="warning"
        />
        <DashboardCard
          title="Pipeline Performance"
          description="Execution times and throughput"
          status="healthy"
        />
      </div>

      {/* Empty State for More */}
      <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 border-dashed rounded-xl p-8 text-center">
        <div className="inline-flex items-center justify-center w-12 h-12 mb-4 rounded-full bg-gray-100 dark:bg-gray-700">
          <Plus className="h-6 w-6 text-gray-400" />
        </div>
        <h3 className="text-lg font-medium text-gray-900 dark:text-white mb-1">
          Create a Dashboard
        </h3>
        <p className="text-sm text-gray-500 dark:text-gray-400 mb-4">
          Build custom dashboards with the metrics that matter to you
        </p>
        <button className="px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 transition-colors">
          New Dashboard
        </button>
      </div>
    </div>
  );
}

// =============================================================================
// COST TAB
// =============================================================================

function CostTab() {
  return (
    <div className="space-y-6">
      {/* Cost Overview */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <StatCard
          label="Current Month"
          value="$1,247"
          icon={<DollarSign className="h-5 w-5" />}
          trend={{ value: 8, direction: 'up' }}
          color="default"
        />
        <StatCard
          label="Projected"
          value="$1,890"
          icon={<TrendingUp className="h-5 w-5" />}
          color="yellow"
        />
        <StatCard
          label="AI Savings"
          value="$127"
          icon={<Sparkles className="h-5 w-5" />}
          trend={{ value: 12, direction: 'up' }}
          color="green"
        />
      </div>

      {/* Optimization Opportunities */}
      <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-4">
        <h3 className="text-sm font-medium text-gray-900 dark:text-white mb-4 flex items-center gap-2">
          <Sparkles className="h-4 w-4 text-purple-500" />
          AI-Detected Optimization Opportunities
        </h3>
        <div className="space-y-3">
          <OptimizationItem
            title="Consolidate redundant queries"
            savings="$45/month"
            effort="Low"
            impact="Medium"
          />
          <OptimizationItem
            title="Archive cold data to cheaper tier"
            savings="$89/month"
            effort="Medium"
            impact="High"
          />
          <OptimizationItem
            title="Optimize join patterns in daily ETL"
            savings="$23/month"
            effort="Low"
            impact="Low"
          />
        </div>
      </div>

      {/* Cost by Resource */}
      <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-4">
        <h3 className="text-sm font-medium text-gray-900 dark:text-white mb-4">
          Cost by Resource
        </h3>
        <div className="space-y-3">
          <CostBar label="Compute" value={540} total={1247} color="blue" />
          <CostBar label="Storage" value={380} total={1247} color="green" />
          <CostBar label="Queries" value={210} total={1247} color="purple" />
          <CostBar label="Egress" value={117} total={1247} color="orange" />
        </div>
      </div>
    </div>
  );
}

// =============================================================================
// HELPER COMPONENTS
// =============================================================================

function SpotlightItem({
  title,
  description,
  type,
}: {
  title: string;
  description: string;
  type: 'optimization' | 'warning' | 'insight';
}) {
  const icons = {
    optimization: <TrendingUp className="h-4 w-4 text-green-500" />,
    warning: <AlertTriangle className="h-4 w-4 text-amber-500" />,
    insight: <Sparkles className="h-4 w-4 text-purple-500" />,
  };
  const colors = {
    optimization: 'bg-green-50 dark:bg-green-900/20',
    warning: 'bg-amber-50 dark:bg-amber-900/20',
    insight: 'bg-purple-50 dark:bg-purple-900/20',
  };

  return (
    <div className={cn('p-3 rounded-lg', colors[type])}>
      <div className="flex items-start gap-3">
        <div className="mt-0.5">{icons[type]}</div>
        <div>
          <p className="text-sm font-medium text-gray-900 dark:text-white">
            {title}
          </p>
          <p className="text-xs text-gray-500 dark:text-gray-400">
            {description}
          </p>
        </div>
      </div>
    </div>
  );
}

function ActivityItem({
  action,
  target,
  time,
  status,
}: {
  action: string;
  target: string;
  time: string;
  status: 'success' | 'warning' | 'error';
}) {
  const icons = {
    success: <CheckCircle className="h-4 w-4 text-green-500" />,
    warning: <AlertTriangle className="h-4 w-4 text-amber-500" />,
    error: <AlertTriangle className="h-4 w-4 text-red-500" />,
  };

  return (
    <div className="flex items-center gap-3 py-2">
      {icons[status]}
      <div className="flex-1 min-w-0">
        <p className="text-sm text-gray-900 dark:text-white truncate">
          {action} <span className="font-medium">{target}</span>
        </p>
      </div>
      <span className="text-xs text-gray-400">{time}</span>
    </div>
  );
}

function DashboardCard({
  title,
  description,
  status,
}: {
  title: string;
  description: string;
  status: 'healthy' | 'warning' | 'error';
}) {
  return (
    <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-4 hover:shadow-md transition-shadow cursor-pointer">
      <div className="flex items-start gap-4">
        <div className="p-3 bg-blue-100 dark:bg-blue-900/30 rounded-lg">
          <BarChart3 className="h-6 w-6 text-blue-600 dark:text-blue-400" />
        </div>
        <div className="flex-1">
          <h3 className="font-medium text-gray-900 dark:text-white">{title}</h3>
          <p className="text-sm text-gray-500 mt-1">{description}</p>
          <div className="flex items-center gap-2 mt-3">
            <span
              className={cn(
                'inline-flex items-center gap-1 text-xs font-medium',
                status === 'healthy' && 'text-green-600',
                status === 'warning' && 'text-amber-600',
                status === 'error' && 'text-red-600'
              )}
            >
              {status === 'healthy' && <CheckCircle className="h-3 w-3" />}
              {status === 'warning' && <AlertTriangle className="h-3 w-3" />}
              {status === 'healthy' ? 'All systems normal' : 'Needs attention'}
            </span>
          </div>
        </div>
      </div>
    </div>
  );
}

function OptimizationItem({
  title,
  savings,
  effort,
  impact,
}: {
  title: string;
  savings: string;
  effort: string;
  impact: string;
}) {
  return (
    <div className="flex items-center gap-4 p-3 bg-gray-50 dark:bg-gray-700/50 rounded-lg">
      <div className="flex-1">
        <p className="text-sm font-medium text-gray-900 dark:text-white">
          {title}
        </p>
        <div className="flex items-center gap-3 mt-1 text-xs text-gray-500">
          <span>Effort: {effort}</span>
          <span>Impact: {impact}</span>
        </div>
      </div>
      <div className="text-right">
        <p className="text-sm font-semibold text-green-600 dark:text-green-400">
          {savings}
        </p>
        <button className="text-xs text-primary-600 dark:text-primary-400 hover:underline">
          Apply
        </button>
      </div>
    </div>
  );
}

function CostBar({
  label,
  value,
  total,
  color,
}: {
  label: string;
  value: number;
  total: number;
  color: 'blue' | 'green' | 'purple' | 'orange';
}) {
  const percentage = (value / total) * 100;
  const colors = {
    blue: 'bg-blue-500',
    green: 'bg-green-500',
    purple: 'bg-purple-500',
    orange: 'bg-orange-500',
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-1">
        <span className="text-sm text-gray-600 dark:text-gray-400">{label}</span>
        <span className="text-sm font-medium text-gray-900 dark:text-white">
          ${value}
        </span>
      </div>
      <div className="quality-bar">
        <div
          className={cn('quality-bar-fill', colors[color])}
          style={{ width: `${percentage}%` }}
        />
      </div>
    </div>
  );
}

// =============================================================================
// MAIN COMPONENT
// =============================================================================

export function InsightsPage() {
  const [activeTab, setActiveTab] = useState<TabType>('overview');

  // Fetch brain stats
  const { data: brainStats } = useQuery({
    queryKey: ['brain-stats'],
    queryFn: () => brainService.getBrainStats(),
    staleTime: 60_000,
  });

  // Fetch active pipeline count
  const { data: workflowsPage } = useQuery({
    queryKey: ['active-workflows-count'],
    queryFn: () => workflowsApi.list({ status: 'active', pageSize: 1 }),
    staleTime: 120_000,
  });

  // Fetch cost analysis
  const { data: costData } = useQuery({
    queryKey: ['cost-analysis'],
    queryFn: () => costService.getCostAnalysis('30d'),
    staleTime: 300_000,
  });

  const tabs: { id: TabType; label: string; icon: React.ReactNode }[] = [
    { id: 'overview', label: 'Overview', icon: <Activity className="h-4 w-4" /> },
    { id: 'brain', label: 'AI Brain', icon: <Brain className="h-4 w-4" /> },
    { id: 'analytics', label: 'Dashboards', icon: <BarChart3 className="h-4 w-4" /> },
    { id: 'cost', label: 'Cost', icon: <DollarSign className="h-4 w-4" /> },
  ];

  // AI Sidebar content
  const aiSidebarContent = (
    <AISidebar title="AI Insights">
      <div className="space-y-3">
        <AISuggestion
          icon={<TrendingUp className="h-4 w-4 text-green-600" />}
          title="Optimize query patterns"
          description="3 queries identified for potential 40% cost reduction"
          confidence={0.92}
          onAction={() => { }}
        />
        <AISuggestion
          icon={<Clock className="h-4 w-4 text-blue-600" />}
          title="Schedule off-peak jobs"
          description="Moving 2 pipelines could save $35/month"
          confidence={0.85}
          onAction={() => { }}
        />
        <AISuggestion
          icon={<AlertTriangle className="h-4 w-4 text-amber-600" />}
          title="Data freshness concern"
          description="'inventory' table is 12 hours stale"
          confidence={0.98}
          onAction={() => { }}
        />
      </div>
    </AISidebar>
  );

  return (
    <div className="flex flex-col h-full">
      <PageHeader
        title="Insights"
        subtitle="AI-powered analytics, system intelligence, and cost optimization"
        icon={<BarChart3 className="h-6 w-6 text-primary-600" />}
        aiPowered
        actions={
          <button className="flex items-center gap-2 px-3 py-2 text-sm text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors">
            <RefreshCw className="h-4 w-4" />
            Refresh
          </button>
        }
      />

      {/* Tabs */}
      <div className="px-6 py-3 border-b border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800">
        <div className="flex items-center gap-2">
          {tabs.map((tab) => (
            <TabButton
              key={tab.id}
              id={tab.id}
              label={tab.label}
              icon={tab.icon}
              active={activeTab === tab.id}
              onClick={() => setActiveTab(tab.id)}
            />
          ))}
        </div>
      </div>

      <PageContent aiSidebar={aiSidebarContent}>
        {activeTab === 'overview' && (
          <OverviewTab
            brainStats={brainStats}
            activePipelines={workflowsPage?.total}
            monthlyCost={costData?.total}
          />
        )}
        {activeTab === 'brain' && <BrainTab />}
        {activeTab === 'analytics' && <AnalyticsTab />}
        {activeTab === 'cost' && <CostTab />}
      </PageContent>
    </div>
  );
}

export default InsightsPage;
