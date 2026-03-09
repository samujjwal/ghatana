/**
 * @fileoverview Enhanced Page Usage Dashboard
 *
 * World-class analytics dashboard with hierarchical views,
 * real-time updates, and comprehensive metric coverage.
 * Builds upon existing PageUsageDashboard without duplication.
 *
 * @module ui/dashboard
 * @since 2.0.0
 */

import React, { useMemo, useState, useCallback, useEffect } from 'react';
import { Card } from '@ghatana/dcmaar-shared-ui-tailwind';
import { usePerformanceSummary, useMetricsHistory, useAlerts } from '../hooks/useAnalytics';
import type { ProcessedMetrics } from '../../analytics/AnalyticsPipeline';
import { useAnalyticsContext } from '../context/AnalyticsContext';

type Alert = {
  id: string;
  title: string;
  message: string;
  severity: 'info' | 'warning' | 'error' | 'critical';
  timestamp: number;
  metadata?: Record<string, unknown>;
};

// Import the TimeRange type from TimeRangeSelector
import type { TimeRange as TimeRangeSelectorType } from '../components/filters/TimeRangeSelector';

type TimeRange = {
  from: number;
  to: number;
  preset: '1h' | '24h' | '7d' | '30d' | 'custom';
};

type ComparisonPresetA = 'today' | 'yesterday' | 'last7' | 'last30' | 'custom';
type ComparisonPresetB = 'yesterday' | 'lastWeek' | 'lastMonth' | 'custom';

interface ComparisonPeriodState {
  preset: ComparisonPresetA | ComparisonPresetB;
  label: string;
  range: { from: number; to: number };
  data?: ProcessedMetrics[];
}

interface CustomRangeState {
  from: string;
  to: string;
}
import { MetricCard } from '../components/metrics/MetricCard';
import { MetricChart } from '../components/metrics/MetricChart';
import { MetricTable } from '../components/metrics/MetricTable';
import { DomainAnalytics } from '../components/analytics/DomainAnalytics';
import { PageAnalytics } from '../components/analytics/PageAnalytics';
import { ComparisonView } from '../components/analytics/ComparisonView';
import { TimeRangeSelector } from '../components/filters/TimeRangeSelector';
import { ContextSelector } from '../components/filters/ContextSelector';
import { ExportButton } from '../components/actions/ExportButton';
import { AlertPanel } from '../components/alerts/AlertPanel';
import { InsightsPanel } from '../components/insights/InsightsPanel';
import { OnboardingTour, useOnboardingStatus } from '../components/onboarding/OnboardingTour';

type ViewMode = 'summary' | 'performance' | 'network' | 'usage' | 'domain' | 'page' | 'comparison';
type Scope = 'global' | 'domain' | 'page';

interface EnhancedPageUsageDashboardProps {
  variant?: 'full' | 'summary' | 'compact';
  title?: string;
  initialView?: ViewMode;
  initialScope?: Scope;
  enableRealTime?: boolean;
}

const VIEWS: Record<ViewMode, { label: string; description: string }> = {
  summary: { label: 'Summary', description: 'Overview of key metrics and alerts' },
  performance: { label: 'Performance', description: 'Core Web Vitals and performance metrics' },
  network: { label: 'Network', description: 'Resource loading and network performance' },
  usage: { label: 'Usage', description: 'User behavior and engagement analytics' },
  domain: { label: 'Domain', description: 'Domain-level analytics and comparisons' },
  page: { label: 'Page', description: 'Page-level performance and user experience' },
  comparison: { label: 'Comparison', description: 'Compare metrics across entities' },
};

const SCOPES: Record<Scope, { label: string; description: string }> = {
  global: { label: 'Global', description: 'All domains and pages' },
  domain: { label: 'Domain', description: 'Specific domain analytics' },
  page: { label: 'Page', description: 'Specific page analytics' },
};

const startOfDay = (date: Date): number => {
  const copy = new Date(date);
  copy.setHours(0, 0, 0, 0);
  return copy.getTime();
};

const endOfDay = (date: Date): number => {
  const copy = new Date(date);
  copy.setHours(23, 59, 59, 999);
  return copy.getTime();
};

const subtractDays = (date: Date, days: number): Date => {
  const copy = new Date(date);
  copy.setDate(copy.getDate() - days);
  return copy;
};

const PRESET_LABELS_A: Record<ComparisonPresetA, string> = {
  today: 'Today',
  yesterday: 'Yesterday',
  last7: 'Last 7 days',
  last30: 'Last 30 days',
  custom: 'Custom range',
};

const PRESET_LABELS_B: Record<ComparisonPresetB, string> = {
  yesterday: 'Yesterday',
  lastWeek: 'Last week',
  lastMonth: 'Last month',
  custom: 'Custom range',
};

const PERIOD_OPTIONS_A: Array<{ value: ComparisonPresetA; label: string }> = [
  { value: 'today', label: PRESET_LABELS_A.today },
  { value: 'yesterday', label: PRESET_LABELS_A.yesterday },
  { value: 'last7', label: PRESET_LABELS_A.last7 },
  { value: 'last30', label: PRESET_LABELS_A.last30 },
  { value: 'custom', label: PRESET_LABELS_A.custom },
];

const PERIOD_OPTIONS_B: Array<{ value: ComparisonPresetB; label: string }> = [
  { value: 'yesterday', label: PRESET_LABELS_B.yesterday },
  { value: 'lastWeek', label: PRESET_LABELS_B.lastWeek },
  { value: 'lastMonth', label: PRESET_LABELS_B.lastMonth },
  { value: 'custom', label: PRESET_LABELS_B.custom },
];

const computePresetRangeA = (
  preset: ComparisonPresetA,
  custom?: { from: string; to: string }
): { from: number; to: number } => {
  const now = new Date();
  switch (preset) {
    case 'today': {
      const from = startOfDay(now);
      return { from, to: now.getTime() };
    }
    case 'yesterday': {
      const yesterday = subtractDays(now, 1);
      return { from: startOfDay(yesterday), to: endOfDay(yesterday) };
    }
    case 'last7': {
      const from = subtractDays(now, 6);
      return { from: startOfDay(from), to: now.getTime() };
    }
    case 'last30': {
      const from = subtractDays(now, 29);
      return { from: startOfDay(from), to: now.getTime() };
    }
    case 'custom': {
      if (custom?.from && custom?.to) {
        const fromDate = new Date(custom.from);
        const toDate = new Date(custom.to);
        let from = startOfDay(fromDate);
        let to = endOfDay(toDate);
        if (from > to) {
          const temp = from;
          from = to;
          to = temp;
        }
        return { from, to };
      }
      const fallback = subtractDays(now, 6);
      return { from: startOfDay(fallback), to: now.getTime() };
    }
    default:
      return { from: startOfDay(now), to: now.getTime() };
  }
};

const computePresetRangeB = (
  preset: ComparisonPresetB,
  custom?: { from: string; to: string }
): { from: number; to: number } => {
  const now = new Date();
  switch (preset) {
    case 'yesterday': {
      const yesterday = subtractDays(now, 1);
      return { from: startOfDay(yesterday), to: endOfDay(yesterday) };
    }
    case 'lastWeek': {
      const end = subtractDays(now, 7);
      const start = subtractDays(end, 6);
      return { from: startOfDay(start), to: endOfDay(end) };
    }
    case 'lastMonth': {
      const end = subtractDays(now, 30);
      const start = subtractDays(end, 29);
      return { from: startOfDay(start), to: endOfDay(end) };
    }
    case 'custom': {
      if (custom?.from && custom?.to) {
        const fromDate = new Date(custom.from);
        const toDate = new Date(custom.to);
        let from = startOfDay(fromDate);
        let to = endOfDay(toDate);
        if (from > to) {
          const temp = from;
          from = to;
          to = temp;
        }
        return { from, to };
      }
      const end = subtractDays(now, 7);
      const start = subtractDays(end, 6);
      return { from: startOfDay(start), to: end.getTime() };
    }
    default:
      return { from: startOfDay(now), to: now.getTime() };
  }
};

const formatDateInputValue = (timestamp: number): string => {
  const date = new Date(timestamp);
  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${year}-${month}-${day}`;
};

/**
 * Enhanced Page Usage Dashboard
 *
 * Provides hierarchical analytics with multiple view modes,
 * real-time updates, and comprehensive metric coverage.
 */
export const EnhancedPageUsageDashboard: React.FC<EnhancedPageUsageDashboardProps> = ({
  variant = 'full',
  title = 'Analytics Dashboard',
  initialView = 'summary',
  initialScope = 'global',
  enableRealTime = true,
}) => {
  const [viewMode, setViewMode] = useState<ViewMode>(initialView);
  const [scope, setScope] = useState<Scope>(initialScope);
  const [selectedEntity, setSelectedEntity] = useState<string>('');
  const [comparisonEnabled, setComparisonEnabled] = useState<boolean>(false);
  const [comparisonPeriodA, setComparisonPeriodA] = useState<ComparisonPeriodState>(() => {
    const range = computePresetRangeA('last7');
    return {
      preset: 'last7',
      label: PRESET_LABELS_A.last7,
      range,
    };
  });
  const [comparisonPeriodB, setComparisonPeriodB] = useState<ComparisonPeriodState>(() => {
    const range = computePresetRangeB('lastWeek');
    return {
      preset: 'lastWeek',
      label: PRESET_LABELS_B.lastWeek,
      range,
    };
  });
  const [customRangeA, setCustomRangeA] = useState<CustomRangeState>(() => {
    const range = computePresetRangeA('last7');
    return {
      from: formatDateInputValue(range.from),
      to: formatDateInputValue(range.to),
    };
  });
  const [customRangeB, setCustomRangeB] = useState<CustomRangeState>(() => {
    const range = computePresetRangeB('lastWeek');
    return {
      from: formatDateInputValue(range.from),
      to: formatDateInputValue(range.to),
    };
  });
  
  // Use analytics context for time range and refresh settings
  const { timeRange, autoRefresh, refreshInterval, refreshTrigger } = useAnalyticsContext();
  
  // Onboarding tour
  const { shouldShow: showOnboarding, markCompleted: completeOnboarding } = useOnboardingStatus();

  // Fetch data using existing hooks
  const {
    data: summary,
    isLoading: loadingSummary,
    error: summaryError,
    refetch: refetchSummary,
  } = usePerformanceSummary({
    refetchInterval: enableRealTime && autoRefresh ? refreshInterval : undefined,
    staleTime: refreshInterval,
  });

  const {
    data: history = [],
    isLoading: loadingHistory,
    error: historyError,
    refetch: refetchHistory,
  } = useMetricsHistory(100, {
    refetchInterval: enableRealTime && autoRefresh ? refreshInterval : undefined,
    staleTime: refreshInterval,
  });

  const {
    data: alerts = [],
    isLoading: loadingAlerts,
    error: alertsError,
    refetch: refetchAlerts,
  } = useAlerts({
    refetchInterval: enableRealTime && autoRefresh ? refreshInterval : undefined,
    staleTime: refreshInterval,
  });

  // Trigger manual refresh when refreshTrigger changes
  useEffect(() => {
    if (refreshTrigger) {
      refetchSummary();
      refetchHistory();
      refetchAlerts();
    }
  }, [refreshTrigger, refetchSummary, refetchHistory, refetchAlerts]);

  useEffect(() => {
    if (comparisonEnabled && viewMode !== 'comparison') {
      setViewMode('comparison');
    }
  }, [comparisonEnabled, viewMode]);

  // Derived data
  const isLoading = loadingSummary || loadingHistory || loadingAlerts;
  const error = summaryError || historyError || alertsError;

  // Filter data based on scope and selected entity
  const filteredData = useMemo(() => {
    if (!history) return [];
    
    if (scope === 'global') return history;
    
    // If we have a selected entity, filter by it
    if (selectedEntity) {
      return history.filter(entry => {
        // Implement your entity filtering logic here
        // For example, if your entries have a 'domain' or 'page' field:
        return entry.domain === selectedEntity || entry.page === selectedEntity;
      });
    }
    
    return history;
  }, [history, scope, selectedEntity]);

  // Calculate aggregated metrics
  const aggregatedMetrics = useMemo(() => {
    if (filteredData.length === 0) return null;

    const latest = filteredData[filteredData.length - 1];
    const aggregated = {
      performance: {
        lcp: latest?.summary.lcp,
        inp: latest?.summary.inp,
        cls: latest?.summary.cls,
        tbt: latest?.summary.tbt,
        fcp: latest?.summary.fcp,
        ttfb: latest?.summary.ttfb,
      },
      network: {
        resourceCount: latest?.summary.resourceCount,
        resourceTransfer: latest?.summary.resourceTransfer,
        cachedRequests: latest?.summary.cachedRequests,
      },
      usage: {
        interactionCount: latest?.summary.interactionCount,
        sessionCount: filteredData.length,
        averageSessionDuration: filteredData.reduce((acc, entry) => {
          // Calculate session duration
          return acc + 0; // Placeholder
        }, 0) / filteredData.length,
      },
      alerts: {
        critical: alerts.filter(a => a.severity === 'critical').length,
        warning: alerts.filter(a => a.severity === 'warning').length,
        info: alerts.filter(a => a.severity === 'info').length,
      },
    };

    return aggregated;
  }, [filteredData, alerts]);

  const updateComparisonPeriodA = useCallback(
    (preset: ComparisonPresetA, nextCustom?: CustomRangeState) => {
      const custom = nextCustom ?? customRangeA;
      const range = computePresetRangeA(preset, custom);
      setComparisonPeriodA({
        preset,
        label: PRESET_LABELS_A[preset],
        range,
      });
    },
    [customRangeA]
  );

  const updateComparisonPeriodB = useCallback(
    (preset: ComparisonPresetB, nextCustom?: CustomRangeState) => {
      const custom = nextCustom ?? customRangeB;
      const range = computePresetRangeB(preset, custom);
      setComparisonPeriodB({
        preset,
        label: PRESET_LABELS_B[preset],
        range,
      });
    },
    [customRangeB]
  );

  const handleComparisonToggle = useCallback(() => {
    setComparisonEnabled(prev => !prev);
  }, []);

  const handlePresetAChange = useCallback(
    (value: ComparisonPresetA) => {
      updateComparisonPeriodA(value);
    },
    [updateComparisonPeriodA]
  );

  const handlePresetBChange = useCallback(
    (value: ComparisonPresetB) => {
      updateComparisonPeriodB(value);
    },
    [updateComparisonPeriodB]
  );

  const handleCustomRangeAChange = useCallback(
    (field: keyof CustomRangeState, value: string) => {
      setCustomRangeA(prev => {
        const next = { ...prev, [field]: value };
        if (comparisonPeriodA.preset === 'custom') {
          updateComparisonPeriodA('custom', next);
        }
        return next;
      });
    },
    [comparisonPeriodA.preset, updateComparisonPeriodA]
  );

  const handleCustomRangeBChange = useCallback(
    (field: keyof CustomRangeState, value: string) => {
      setCustomRangeB(prev => {
        const next = { ...prev, [field]: value };
        if (comparisonPeriodB.preset === 'custom') {
          updateComparisonPeriodB('custom', next);
        }
        return next;
      });
    },
    [comparisonPeriodB.preset, updateComparisonPeriodB]
  );

  // Export data
  const handleExport = useCallback(async (format: 'json' | 'csv' | 'pdf') => {
    const exportData = {
      summary,
      history: filteredData,
      alerts,
      timeRange,
      scope,
      exportedAt: Date.now(),
    };

    switch (format) {
      case 'json':
        const blob = new Blob([JSON.stringify(exportData, null, 2)], {
          type: 'application/json',
        });
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = `analytics-export-${Date.now()}.json`;
        anchor.click();
        URL.revokeObjectURL(url);
        break;
      
      case 'csv':
        // Implement CSV export
        console.log('CSV export not implemented yet');
        break;
      
      case 'pdf':
        // Implement PDF export
        console.log('PDF export not implemented yet');
        break;
    }
  }, [summary, filteredData, alerts, timeRange, scope]);

  const comparisonPeriods = useMemo(() => {
    if (!comparisonEnabled) return undefined;

    const filterByRange = (range: { from: number; to: number }) =>
      filteredData.filter((entry) => entry.timestamp >= range.from && entry.timestamp <= range.to);

    const periodAData = filterByRange(comparisonPeriodA.range);
    const periodBData = filterByRange(comparisonPeriodB.range);

    if (!periodAData.length && !periodBData.length) {
      return undefined;
    }

    return {
      periodA: {
        ...comparisonPeriodA,
        data: periodAData,
      },
      periodB: {
        ...comparisonPeriodB,
        data: periodBData,
      },
    };
  }, [comparisonEnabled, filteredData, comparisonPeriodA, comparisonPeriodB]);

  // Render header with controls
  const renderHeader = () => (
    <header className="flex flex-wrap items-center justify-between gap-4 pb-6 border-b border-slate-200">
      <div className="flex-1">
        <h1 className="text-3xl font-bold text-slate-900">{title}</h1>
        <p className="mt-1 text-sm text-slate-500">
          {VIEWS[viewMode].description} • {SCOPES[scope].description}
        </p>
      </div>
      
      <div className="flex items-center gap-3">
        <ContextSelector
          scope={scope}
          entity={selectedEntity || ''}
          onScopeChange={setScope}
          onEntityChange={(entity) => setSelectedEntity(entity || '')}
        />
        
        <ExportButton onExport={handleExport} />
      </div>
    </header>
  );

  // Render view mode tabs
  const renderTabs = () => (
    <div className="flex space-x-1 border-b border-slate-200 mb-6">
      {Object.entries(VIEWS).map(([key, view]) => (
        <button
          key={key}
          type="button"
          className={`px-4 py-2 text-sm font-medium rounded-t-lg transition-colors ${
            viewMode === key
              ? 'bg-white border-t border-l border-r border-slate-200 text-slate-900 -mb-px'
              : 'text-slate-500 hover:text-slate-700 hover:bg-slate-50'
          }`}
          onClick={() => setViewMode(key as ViewMode)}
        >
          {view.label}
        </button>
      ))}
    </div>
  );

  const renderComparisonControls = () => (
    <div className="mb-6 rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
      <div className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
        <label className="comparison-toggle flex items-center gap-2 text-sm text-slate-700">
          <input
            type="checkbox"
            className="h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500"
            checked={comparisonEnabled}
            onChange={handleComparisonToggle}
          />
          Enable comparison mode
        </label>

        {comparisonEnabled && (
          <div className="flex flex-1 flex-col gap-4 md:flex-row md:items-end">
            <div className="flex-1">
              <label htmlFor="comparison-period-a" className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                Period A
              </label>
              <select
                id="comparison-period-a"
                className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-700 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-200"
                value={comparisonPeriodA.preset}
                onChange={(event) => handlePresetAChange(event.target.value as ComparisonPresetA)}
              >
                {PERIOD_OPTIONS_A.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
              {comparisonPeriodA.preset === 'custom' && (
                <div className="mt-2 flex gap-2">
                  <div className="flex-1">
                    <label className="text-xs text-slate-500">From</label>
                    <input
                      type="date"
                      className="mt-1 w-full rounded-md border border-slate-300 px-3 py-1.5 text-sm text-slate-700 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-200"
                      value={customRangeA.from}
                      onChange={(event) => handleCustomRangeAChange('from', event.target.value)}
                    />
                  </div>
                  <div className="flex-1">
                    <label className="text-xs text-slate-500">To</label>
                    <input
                      type="date"
                      className="mt-1 w-full rounded-md border border-slate-300 px-3 py-1.5 text-sm text-slate-700 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-200"
                      value={customRangeA.to}
                      onChange={(event) => handleCustomRangeAChange('to', event.target.value)}
                    />
                  </div>
                </div>
              )}
            </div>

            <div className="flex-1">
              <label htmlFor="comparison-period-b" className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                Period B
              </label>
              <select
                id="comparison-period-b"
                className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-700 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-200"
                value={comparisonPeriodB.preset}
                onChange={(event) => handlePresetBChange(event.target.value as ComparisonPresetB)}
              >
                {PERIOD_OPTIONS_B.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
              {comparisonPeriodB.preset === 'custom' && (
                <div className="mt-2 flex gap-2">
                  <div className="flex-1">
                    <label className="text-xs text-slate-500">From</label>
                    <input
                      type="date"
                      className="mt-1 w-full rounded-md border border-slate-300 px-3 py-1.5 text-sm text-slate-700 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-200"
                      value={customRangeB.from}
                      onChange={(event) => handleCustomRangeBChange('from', event.target.value)}
                    />
                  </div>
                  <div className="flex-1">
                    <label className="text-xs text-slate-500">To</label>
                    <input
                      type="date"
                      className="mt-1 w-full rounded-md border border-slate-300 px-3 py-1.5 text-sm text-slate-700 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-200"
                      value={customRangeB.to}
                      onChange={(event) => handleCustomRangeBChange('to', event.target.value)}
                    />
                  </div>
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
  // Render summary view
  const renderSummaryView = () => {
    if (variant === 'compact') {
      return (
        <Card title="Quick Overview" className="p-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <MetricCard
              title="Largest Contentful Paint"
              metricKey="lcp"
              value={aggregatedMetrics?.performance.lcp}
              unit="ms"
              status={aggregatedMetrics?.performance.lcp ? 
                (aggregatedMetrics.performance.lcp > 4000 ? 'poor' : 
                 aggregatedMetrics.performance.lcp > 2500 ? 'warning' : 'good') : 'unknown'}
              trend={{ direction: 'stable', percentage: 0, significance: 'low' }}
            />
            <MetricCard
              title="Sessions"
              metricKey="sessionCount"
              value={aggregatedMetrics?.usage.sessionCount}
              status="good"
              trend={{ direction: 'up', percentage: 12, significance: 'high' }}
            />
          </div>
        </Card>
      );
    }

    return (
      <div className="space-y-6">
        {/* KPI Grid */}
        <div className="metric-cards-grid grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <MetricCard
            title="Largest Contentful Paint"
            metricKey="lcp"
            value={aggregatedMetrics?.performance.lcp}
            unit="ms"
            status={aggregatedMetrics?.performance.lcp ? 
              (aggregatedMetrics.performance.lcp > 4000 ? 'poor' : 
               aggregatedMetrics.performance.lcp > 2500 ? 'warning' : 'good') : 'unknown'}
            trend={{ direction: 'stable', percentage: 0, significance: 'low' }}
          />
          
          <MetricCard
            title="Total Transfer Size"
            metricKey="resourceTransfer"
            value={aggregatedMetrics?.network.resourceTransfer}
            unit="KB"
            status="good"
            trend={{ direction: 'down', percentage: -5, significance: 'medium' }}
          />
          
          <MetricCard
            title="User Interactions"
            metricKey="interactionCount"
            value={aggregatedMetrics?.usage.interactionCount}
            unit="interactions"
            status="good"
            trend={{ direction: 'up', percentage: 12, significance: 'high' }}
          />
          
          <MetricCard
            title="Active Alerts"
            metricKey="activeAlerts"
            value={aggregatedMetrics?.alerts.critical}
            unit="critical"
            status={aggregatedMetrics?.alerts.critical ? 'poor' : 'good'}
          />
        </div>

        {/* Charts Row */}
        <div className="metric-charts grid gap-4 lg:grid-cols-2">
          <MetricChart
            title="Performance Trends"
            data={filteredData}
            metrics={['lcp', 'inp', 'cls']}
            type="line"
            height={300}
          />
          
          <MetricChart
            title="Network Usage"
            data={filteredData}
            metrics={['resourceTransfer', 'resourceCount']}
            type="area"
            height={300}
          />
        </div>

        {/* Detailed Views */}
        <div className="grid gap-4 lg:grid-cols-3">
          <div className="lg:col-span-2">
            <Card title="Detailed Analytics" className="p-4">
              <div className="space-y-4">
                <MetricChart
                  title="Performance Metrics"
                  data={filteredData}
                  metrics={['lcp', 'inp', 'cls']}
                  type="line"
                  height={300}
                />
                <MetricTable
                  title="Page Performance"
                  data={filteredData}
                  columns={['timestamp', 'lcp', 'inp', 'cls']}
                />
              </div>
            </Card>
          </div>
          
          <div className="space-y-4">
            <div className="alert-panel">
              <AlertPanel alerts={alerts} />
            </div>
            <div className="insights-panel">
              <InsightsPanel data={filteredData} />
            </div>
          </div>
        </div>
      </div>
    );
  };

  // Render performance view
  const renderPerformanceView = () => (
    <div className="space-y-6">
      <div className="grid gap-4 lg:grid-cols-2">
        <MetricChart
          title="Core Web Vitals"
          data={filteredData}
          metrics={['lcp', 'inp', 'cls', 'tbt']}
          type="line"
          height={400}
        />
        
        <MetricChart
          title="Performance Diagnostics"
          data={filteredData}
          metrics={['longTaskCount', 'totalBlockingTime', 'maxInteractionLatency']}
          type="bar"
          height={400}
        />
      </div>
      
      <MetricTable
        title="Performance Metrics Table"
        data={filteredData}
        columns={['timestamp', 'lcp', 'inp', 'cls', 'tbt', 'fcp', 'ttfb']}
        sortable
        filterable
      />
    </div>
  );

  // Render network view
  const renderNetworkView = () => (
    <div className="space-y-6">
      <div className="grid gap-4 lg:grid-cols-2">
        <MetricChart
          title="Resource Loading"
          data={filteredData}
          metrics={['resourceTransfer', 'resourceCount']}
          type="area"
          height={400}
        />
        
        <MetricChart
          title="Cache Performance"
          data={filteredData}
          metrics={['cachedRequests']}
          type="line"
          height={400}
        />
      </div>
      
      <MetricTable
        title="Network Metrics"
        data={filteredData}
        columns={['timestamp', 'resourceTransfer', 'resourceCount', 'cachedRequests']}
        sortable
        filterable
      />
    </div>
  );

  // Render usage view
  const renderUsageView = () => (
    <div className="space-y-6">
      <div className="grid gap-4 lg:grid-cols-2">
        <MetricChart
          title="User Interactions"
          data={filteredData}
          metrics={['interactionCount']}
          type="line"
          height={400}
        />
        
        <MetricChart
          title="Session Analytics"
          data={filteredData}
          metrics={['sessionCount', 'averageSessionDuration']}
          type="bar"
          height={400}
        />
      </div>
      
      <MetricTable
        title="Usage Metrics"
        data={filteredData}
        columns={['timestamp', 'interactionCount', 'sessionCount']}
        sortable
        filterable
      />
    </div>
  );

  // Render domain view
  const renderDomainView = () => (
    <DomainAnalytics
      data={filteredData}
      timeRange={timeRange}
      selectedDomain={selectedEntity}
      onDomainSelect={(domain) => setSelectedEntity(domain || '')}
    />
  );

  // Render page view
  const renderPageView = () => (
    <PageAnalytics
      data={filteredData}
      timeRange={timeRange}
      selectedPage={selectedEntity}
      onPageSelect={(page) => setSelectedEntity(page || '')}
    />
  );

  // Render comparison view
  const renderComparisonView = () => (
    <ComparisonView
      metrics={['lcp', 'inp', 'cls', 'resourceTransfer']}
      periodA={comparisonPeriods?.periodA}
      periodB={comparisonPeriods?.periodB}
    />
  );

  // Render content based on view mode
  const renderContent = () => {
    if (error) {
      return (
        <div className="rounded-lg border border-rose-200 bg-rose-50 p-4">
          <p className="text-sm text-rose-700">
            {error instanceof Error ? error.message : String(error)}
          </p>
        </div>
      );
    }

    if (isLoading) {
      return (
        <div className="flex items-center justify-center py-12">
          <div className="text-sm text-slate-500">Loading analytics data...</div>
        </div>
      );
    }

    switch (viewMode) {
      case 'summary':
        return renderSummaryView();
      case 'performance':
        return renderPerformanceView();
      case 'network':
        return renderNetworkView();
      case 'usage':
        return renderUsageView();
      case 'domain':
        return renderDomainView();
      case 'page':
        return renderPageView();
      case 'comparison':
        return renderComparisonView();
      default:
        return renderSummaryView();
    }
  };

  return (
    <div className="flex flex-col gap-6 dashboard-container">
      <div className="dashboard-header">
        {renderHeader()}
      </div>
      {renderTabs()}
      {variant !== 'compact' && renderComparisonControls()}
      {renderContent()}
      
      {/* Onboarding Tour */}
      <OnboardingTour
        isOpen={showOnboarding}
        onComplete={completeOnboarding}
        onSkip={completeOnboarding}
      />
    </div>
  );
};

export default EnhancedPageUsageDashboard;
