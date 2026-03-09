/**
 * @ghatana/yappc-ide - Performance Monitoring Dashboard
 * 
 * Comprehensive performance monitoring with real-time metrics,
 * resource usage tracking, and optimization suggestions.
 * 
 * @doc.type component
 * @doc.purpose Performance monitoring dashboard for IDE
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useState, useCallback, useEffect, useRef } from 'react';
import { useToastNotifications } from './Toast';
import { InteractiveButton } from './MicroInteractions';
import { ProgressBar } from './MicroInteractions';

/**
 * Performance metric types
 */
export type MetricType = 'cpu' | 'memory' | 'network' | 'disk' | 'render' | 'bundle';

/**
 * Performance metric interface
 */
export interface PerformanceMetric {
  id: string;
  name: string;
  type: MetricType;
  value: number;
  unit: string;
  threshold: number;
  status: 'good' | 'warning' | 'critical';
  trend: 'up' | 'down' | 'stable';
  history: number[];
  timestamp: Date;
}

/**
 * Performance alert interface
 */
export interface PerformanceAlert {
  id: string;
  type: 'warning' | 'error' | 'info';
  title: string;
  description: string;
  metric: string;
  value: number;
  threshold: number;
  timestamp: Date;
  acknowledged: boolean;
}

/**
 * Performance suggestion interface
 */
export interface PerformanceSuggestion {
  id: string;
  title: string;
  description: string;
  type: 'optimization' | 'refactor' | 'cleanup' | 'upgrade';
  impact: 'low' | 'medium' | 'high';
  effort: 'low' | 'medium' | 'high';
  implemented: boolean;
  code?: string;
}

/**
 * Performance snapshot interface
 */
export interface PerformanceSnapshot {
  id: string;
  timestamp: Date;
  metrics: PerformanceMetric[];
  score: number;
  issues: PerformanceAlert[];
}

/**
 * Performance dashboard props
 */
export interface PerformanceDashboardProps {
  isVisible: boolean;
  onClose: () => void;
  currentProject?: {
    id: string;
    name: string;
    description?: string;
  };
  onOptimizationApply: (suggestion: PerformanceSuggestion) => void;
  className?: string;
}

/**
 * Performance Dashboard Component
 */
export const PerformanceDashboard: React.FC<PerformanceDashboardProps> = ({
  isVisible,
  onClose,
  onOptimizationApply,
  className = '',
}) => {
  const [activeTab, setActiveTab] = useState<'overview' | 'metrics' | 'alerts' | 'suggestions' | 'history' | 'settings'>('overview');
  const [metrics, setMetrics] = useState<PerformanceMetric[]>([]);
  const [alerts, setAlerts] = useState<PerformanceAlert[]>([]);
  const [suggestions, setSuggestions] = useState<PerformanceSuggestion[]>([]);
  const [snapshots, setSnapshots] = useState<PerformanceSnapshot[]>([]);
  const [isMonitoring, setIsMonitoring] = useState(false);
  const [selectedTimeRange, setSelectedTimeRange] = useState<'1h' | '6h' | '24h' | '7d' | '30d' | '90d'>('1h');
  const [isGeneratingReport, setIsGeneratingReport] = useState(false);
  const intervalRef = useRef<NodeJS.Timeout | null>(null);
  const { success, error, warning, info } = useToastNotifications();

  /**
   * Generate mock metrics
   */
  const generateMockMetrics = useCallback((): PerformanceMetric[] => [
    {
      id: 'cpu',
      name: 'CPU Usage',
      type: 'cpu',
      value: Math.random() * 100,
      unit: '%',
      threshold: 80,
      status: 'good',
      trend: 'stable',
      history: Array.from({ length: 20 }, () => Math.random() * 100),
      timestamp: new Date(),
    },
    {
      id: 'memory',
      name: 'Memory Usage',
      type: 'memory',
      value: Math.random() * 100,
      unit: '%',
      threshold: 85,
      status: 'good',
      trend: 'up',
      history: Array.from({ length: 20 }, () => Math.random() * 100),
      timestamp: new Date(),
    },
    {
      id: 'network',
      name: 'Network Latency',
      type: 'network',
      value: Math.random() * 100,
      unit: 'ms',
      threshold: 50,
      status: 'warning',
      trend: 'down',
      history: Array.from({ length: 20 }, () => Math.random() * 100),
      timestamp: new Date(),
    },
  ], []);

  /**
   * Generate mock alerts
   */
  const generateMockAlerts = useCallback((metricsData: PerformanceMetric[]): PerformanceAlert[] => {
    const alerts: PerformanceAlert[] = [];
    
    metricsData.forEach(metric => {
      if (metric.value > metric.threshold) {
        alerts.push({
          id: Math.random().toString(36).substr(2, 9),
          type: metric.value > metric.threshold * 1.2 ? 'error' : 'warning',
          title: `${metric.name} Threshold Exceeded`,
          description: `${metric.name} is at ${metric.value.toFixed(1)}${metric.unit}, exceeding the threshold of ${metric.threshold}${metric.unit}`,
          metric: metric.name,
          value: metric.value,
          threshold: metric.threshold,
          timestamp: new Date(),
          acknowledged: false,
        });
      }
    });
    
    return alerts;
  }, []);

  /**
   * Generate mock suggestions
   */
  const generateMockSuggestions = useCallback((): PerformanceSuggestion[] => [
    {
      id: '1',
      title: 'Optimize Bundle Size',
      description: 'Reduce bundle size by implementing code splitting and tree shaking',
      type: 'optimization',
      impact: 'high',
      effort: 'medium',
      implemented: false,
      code: 'const LazyComponent = React.lazy(() => import("./LazyComponent"));',
    },
    {
      id: '2',
      title: 'Add Memory Leaks Detection',
      description: 'Implement memory leak detection to prevent performance degradation',
      type: 'refactor',
      impact: 'medium',
      effort: 'low',
      implemented: false,
    },
    {
      id: '3',
      title: 'Upgrade Dependencies',
      description: 'Update outdated dependencies to improve performance and security',
      type: 'upgrade',
      impact: 'medium',
      effort: 'high',
      implemented: false,
    },
  ], []);

  /**
   * Generate mock snapshots
   */
  const generateMockSnapshots = useCallback((): PerformanceSnapshot[] => {
    return Array.from({ length: 10 }, (_, i) => ({
      id: Math.random().toString(36).substr(2, 9),
      timestamp: new Date(Date.now() - i * 3600000),
      metrics: generateMockMetrics(),
      score: Math.random() * 100,
      issues: [],
    }));
  }, [generateMockMetrics]);

  /**
   * Update metrics with new values
   */
  const updateMetrics = useCallback((currentMetrics: PerformanceMetric[]): PerformanceMetric[] => {
    return currentMetrics.map(metric => ({
      ...metric,
      value: Math.max(0, Math.min(100, metric.value + (Math.random() - 0.5) * 10)),
      history: [...metric.history.slice(1), Math.random() * 100],
      timestamp: new Date(),
    }));
  }, []);

  /**
   * Check for new alerts based on metrics
   */
  const checkForAlerts = useCallback((metricsData: PerformanceMetric[]): PerformanceAlert[] => {
    return generateMockAlerts(metricsData);
  }, [generateMockAlerts]);

  /**
   * Initialize performance monitoring
   */
  const initializeMonitoring = useCallback(() => {
    // Generate initial metrics
    const initialMetrics = generateMockMetrics();
    setMetrics(initialMetrics);

    // Generate initial alerts
    const initialAlerts = generateMockAlerts(initialMetrics);
    setAlerts(initialAlerts);

    // Generate suggestions
    const initialSuggestions = generateMockSuggestions();
    setSuggestions(initialSuggestions);

    // Generate historical snapshots
    const historicalSnapshots = generateMockSnapshots();
    setSnapshots(historicalSnapshots);
  }, [generateMockMetrics, generateMockAlerts, generateMockSuggestions, generateMockSnapshots]);

  /**
   * Start monitoring
   */
  const startMonitoring = useCallback(() => {
    setIsMonitoring(true);
    info('Performance monitoring started');

    // Update metrics every 2 seconds
    intervalRef.current = setInterval(() => {
      const updatedMetrics = updateMetrics(metrics);
      setMetrics(updatedMetrics);

      // Check for new alerts
      const newAlerts = checkForAlerts(updatedMetrics);
      if (newAlerts.length > 0) {
        setAlerts(prev => [...newAlerts, ...prev]);
        warning(`New performance alert: ${newAlerts[0].title}`);
      }
    }, 2000);
  }, [metrics, info, warning, updateMetrics, checkForAlerts]);

  /**
   * Stop monitoring
   */
  const stopMonitoring = useCallback(() => {
    setIsMonitoring(false);
    if (intervalRef.current) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
    }
    info('Performance monitoring stopped');
  }, [info]);

  /**
   * Acknowledge alert
   */
  const acknowledgeAlert = useCallback((alertId: string) => {
    setAlerts(prev => prev.map(alert =>
      alert.id === alertId ? { ...alert, acknowledged: true } : alert
    ));
    info('Alert acknowledged');
  }, [info]);

  /**
   * Apply suggestion
   */
  const applySuggestion = useCallback((suggestion: PerformanceSuggestion) => {
    setSuggestions(prev => prev.map(s =>
      s.id === suggestion.id ? { ...s, implemented: true } : s
    ));
    onOptimizationApply(suggestion);
    success(`Applied optimization: ${suggestion.title}`);
  }, [onOptimizationApply, success]);

  /**
   * Generate performance report
   */
  const generateReport = useCallback(async () => {
    setIsGeneratingReport(true);
    try {
      // Simulate report generation
      await new Promise(resolve => setTimeout(resolve, 2000));
      
      const report = {
        timestamp: new Date(),
        metrics: metrics,
        alerts: alerts.filter(alert => !alert.acknowledged),
        suggestions: suggestions.filter(s => !s.implemented),
        summary: {
          overallScore: calculateOverallScore(metrics),
          criticalIssues: alerts.filter(alert => alert.type === 'error').length,
          warnings: alerts.filter(alert => alert.type === 'warning').length,
          optimizations: suggestions.filter(s => !s.implemented).length,
        },
      };

      // Download report as JSON
      const blob = new Blob([JSON.stringify(report, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `performance-report-${Date.now()}.json`;
      a.click();
      URL.revokeObjectURL(url);

      success('Performance report generated and downloaded');
    } catch {
      error('Failed to generate report');
    } finally {
      setIsGeneratingReport(false);
    }
  }, [metrics, alerts, suggestions, success, error]);

  /**
   * Calculate overall performance score
   */
  const calculateOverallScore = (metricsData: PerformanceMetric[]): number => {
    const scores = metricsData.map(metric => {
      const ratio = metric.value / metric.threshold;
      if (ratio < 0.5) return 100;
      if (ratio < 0.8) return 80;
      if (ratio < 1) return 60;
      return 40;
    });
    return Math.round(scores.reduce((a, b) => a + b, 0) / scores.length);
  };

  // Initialize monitoring on mount
  useEffect(() => {
    if (isVisible) {
      initializeMonitoring();
    }
  }, [isVisible, initializeMonitoring]);

  if (!isVisible) return null;

  return (
    <div className={`fixed right-4 top-20 bottom-4 w-96 bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-700 rounded-lg shadow-2xl flex flex-col ${className}`}>
      {/* Header */}
      <div className="flex items-center justify-between p-4 border-b border-gray-200 dark:border-gray-700">
        <div className="flex items-center space-x-2">
          <span className="text-xl">📊</span>
          <h3 className="text-lg font-semibold text-gray-900 dark:text-gray-100">
            Performance Dashboard
          </h3>
        </div>
        <InteractiveButton
          variant="ghost"
          size="sm"
          onClick={onClose}
          className="text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200"
        >
          ✕
        </InteractiveButton>
      </div>

      {/* Tabs */}
      <div className="flex border-b border-gray-200 dark:border-gray-700">
        {[
          { id: 'overview', label: 'Overview' },
          { id: 'metrics', label: 'Metrics' },
          { id: 'alerts', label: 'Alerts', count: alerts.filter(a => !a.acknowledged).length },
          { id: 'suggestions', label: 'Suggestions', count: suggestions.filter(s => !s.implemented).length },
          { id: 'history', label: 'History' },
        ].map(tab => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id as 'overview' | 'metrics' | 'alerts' | 'suggestions' | 'history' | 'settings')}
            className={`
              flex-1 px-3 py-2 text-sm font-medium border-b-2 transition-colors
              ${activeTab === tab.id
                ? 'border-blue-500 text-blue-600 dark:text-blue-400'
                : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300'
              }
            `}
          >
            {tab.label}
            {tab.count !== undefined && tab.count > 0 && (
              <span className="ml-1 text-xs bg-gray-100 dark:bg-gray-800 px-1 rounded">
                {tab.count}
              </span>
            )}
          </button>
        ))}
      </div>

      {/* Content */}
      <div className="flex-1 overflow-y-auto p-4">
        {activeTab === 'overview' && (
          <div className="space-y-4">
            <div className="bg-blue-50 dark:bg-blue-900/20 p-3 rounded-lg">
              <div className="flex items-center justify-between">
                <span className="text-sm font-medium text-blue-900 dark:text-blue-100">
                  Overall Score
                </span>
                <span className="text-2xl font-bold text-blue-600 dark:text-blue-400">
                  {calculateOverallScore(metrics)}%
                </span>
              </div>
            </div>

            <div className="space-y-2">
              {metrics.slice(0, 3).map(metric => (
                <div key={metric.id} className="p-3 border border-gray-200 dark:border-gray-700 rounded-lg">
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-sm font-medium text-gray-900 dark:text-gray-100">
                      {metric.name}
                    </span>
                    <span className={`text-xs px-2 py-1 rounded ${
                      metric.status === 'good' ? 'bg-green-100 text-green-800 dark:bg-green-900/20 dark:text-green-400' :
                      metric.status === 'warning' ? 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/20 dark:text-yellow-400' :
                      'bg-red-100 text-red-800 dark:bg-red-900/20 dark:text-red-400'
                    }`}>
                      {metric.status}
                    </span>
                  </div>
                  <ProgressBar
                    value={metric.value}
                    max={100}
                    className="h-2"
                  />
                  <div className="text-xs text-gray-500 dark:text-gray-400 mt-1">
                    {metric.value.toFixed(1)}{metric.unit} / {metric.threshold}{metric.unit}
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {activeTab === 'metrics' && (
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <span className="text-sm font-medium text-gray-900 dark:text-gray-100">
                Real-time Metrics
              </span>
              <div className="flex items-center space-x-2">
                <select
                  value={selectedTimeRange}
                  onChange={(e) => setSelectedTimeRange(e.target.value as '1h' | '6h' | '24h' | '7d' | '30d' | '90d')}
                  className="px-3 py-1 text-sm border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-800 dark:text-gray-100"
                >
                  <option value="1h">Last Hour</option>
                  <option value="6h">Last 6 Hours</option>
                  <option value="24h">Last 24 Hours</option>
                  <option value="7d">Last 7 Days</option>
                  <option value="30d">Last 30 Days</option>
                  <option value="90d">Last 90 Days</option>
                </select>
                <InteractiveButton
                  variant={isMonitoring ? 'secondary' : 'primary'}
                  size="sm"
                  onClick={isMonitoring ? stopMonitoring : startMonitoring}
                >
                  {isMonitoring ? 'Stop' : 'Start'}
                </InteractiveButton>
              </div>
            </div>

            <div className="space-y-3">
              {metrics.map(metric => (
                <div key={metric.id} className="p-4 border border-gray-200 dark:border-gray-700 rounded-lg">
                  <div className="flex items-center justify-between mb-3">
                    <div>
                      <h4 className="font-medium text-gray-900 dark:text-gray-100">
                        {metric.name}
                      </h4>
                      <p className="text-sm text-gray-500 dark:text-gray-400">
                        {metric.type} • {metric.unit}
                      </p>
                    </div>
                    <div className="text-right">
                      <div className="text-lg font-semibold text-gray-900 dark:text-gray-100">
                        {metric.value.toFixed(1)}
                      </div>
                      <div className={`text-xs px-2 py-1 rounded ${
                        metric.status === 'good' ? 'bg-green-100 text-green-800 dark:bg-green-900/20 dark:text-green-400' :
                        metric.status === 'warning' ? 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/20 dark:text-yellow-400' :
                        'bg-red-100 text-red-800 dark:bg-red-900/20 dark:text-red-400'
                      }`}>
                        {metric.status}
                      </div>
                    </div>
                  </div>
                  <ProgressBar
                    value={metric.value}
                    max={100}
                    className="h-3"
                  />
                  <div className="mt-2 text-xs text-gray-500 dark:text-gray-400">
                    Threshold: {metric.threshold}{metric.unit} • Trend: {metric.trend}
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {activeTab === 'alerts' && (
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <span className="text-sm font-medium text-gray-900 dark:text-gray-100">
                Performance Alerts
              </span>
              <span className="text-xs text-gray-500 dark:text-gray-400">
                {alerts.filter(a => !a.acknowledged).length} active
              </span>
            </div>

            <div className="space-y-2">
              {alerts.length === 0 ? (
                <div className="text-center py-8 text-gray-500 dark:text-gray-400">
                  No alerts at this time
                </div>
              ) : (
                alerts.map(alert => (
                  <div
                    key={alert.id}
                    className={`p-3 border rounded-lg ${
                      alert.acknowledged
                        ? 'border-gray-200 dark:border-gray-700 opacity-60'
                        : alert.type === 'error'
                        ? 'border-red-200 dark:border-red-800 bg-red-50 dark:bg-red-900/20'
                        : alert.type === 'warning'
                        ? 'border-yellow-200 dark:border-yellow-800 bg-yellow-50 dark:bg-yellow-900/20'
                        : 'border-blue-200 dark:border-blue-800 bg-blue-50 dark:bg-blue-900/20'
                    }`}
                  >
                    <div className="flex items-start justify-between">
                      <div className="flex-1">
                        <div className="flex items-center space-x-2">
                          <span className={`text-xs px-2 py-1 rounded ${
                            alert.type === 'error' ? 'bg-red-100 text-red-800 dark:bg-red-900/20 dark:text-red-400' :
                            alert.type === 'warning' ? 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/20 dark:text-yellow-400' :
                            'bg-blue-100 text-blue-800 dark:bg-blue-900/20 dark:text-blue-400'
                          }`}>
                            {alert.type}
                          </span>
                          <h4 className="font-medium text-gray-900 dark:text-gray-100">
                            {alert.title}
                          </h4>
                        </div>
                        <p className="text-sm text-gray-600 dark:text-gray-400 mt-1">
                          {alert.description}
                        </p>
                        <div className="text-xs text-gray-500 dark:text-gray-400 mt-2">
                          {alert.metric}: {alert.value.toFixed(1)} / {alert.threshold}
                        </div>
                      </div>
                      {!alert.acknowledged && (
                        <InteractiveButton
                          variant="ghost"
                          size="sm"
                          onClick={() => acknowledgeAlert(alert.id)}
                        >
                          Acknowledge
                        </InteractiveButton>
                      )}
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>
        )}

        {activeTab === 'suggestions' && (
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <span className="text-sm font-medium text-gray-900 dark:text-gray-100">
                Optimization Suggestions
              </span>
              <span className="text-xs text-gray-500 dark:text-gray-400">
                {suggestions.filter(s => !s.implemented).length} pending
              </span>
            </div>

            <div className="space-y-3">
              {suggestions.map(suggestion => (
                <div
                  key={suggestion.id}
                  className={`p-4 border border-gray-200 dark:border-gray-700 rounded-lg ${
                    suggestion.implemented ? 'opacity-60' : ''
                  }`}
                >
                  <div className="flex items-start justify-between mb-2">
                    <div>
                      <h4 className="font-medium text-gray-900 dark:text-gray-100">
                        {suggestion.title}
                      </h4>
                      <p className="text-sm text-gray-600 dark:text-gray-400 mt-1">
                        {suggestion.description}
                      </p>
                    </div>
                    <div className="flex space-x-1">
                      <span className={`text-xs px-2 py-1 rounded ${
                        suggestion.impact === 'high' ? 'bg-red-100 text-red-800 dark:bg-red-900/20 dark:text-red-400' :
                        suggestion.impact === 'medium' ? 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/20 dark:text-yellow-400' :
                        'bg-green-100 text-green-800 dark:bg-green-900/20 dark:text-green-400'
                      }`}>
                        {suggestion.impact} impact
                      </span>
                      <span className={`text-xs px-2 py-1 rounded ${
                        suggestion.effort === 'high' ? 'bg-red-100 text-red-800 dark:bg-red-900/20 dark:text-red-400' :
                        suggestion.effort === 'medium' ? 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/20 dark:text-yellow-400' :
                        'bg-green-100 text-green-800 dark:bg-green-900/20 dark:text-green-400'
                      }`}>
                        {suggestion.effort} effort
                      </span>
                    </div>
                  </div>
                  {suggestion.code && (
                    <pre className="bg-gray-100 dark:bg-gray-800 p-2 rounded text-xs overflow-x-auto mt-2">
                      <code>{suggestion.code}</code>
                    </pre>
                  )}
                  {!suggestion.implemented && (
                    <div className="mt-3">
                      <InteractiveButton
                        variant="primary"
                        size="sm"
                        onClick={() => applySuggestion(suggestion)}
                      >
                        Apply Suggestion
                      </InteractiveButton>
                    </div>
                  )}
                </div>
              ))}
            </div>
          </div>
        )}

        {activeTab === 'history' && (
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <span className="text-sm font-medium text-gray-900 dark:text-gray-100">
                Performance History
              </span>
              <InteractiveButton
                variant="primary"
                size="sm"
                onClick={generateReport}
                disabled={isGeneratingReport}
              >
                {isGeneratingReport ? 'Generating...' : 'Generate Report'}
              </InteractiveButton>
            </div>

            <div className="space-y-2">
              {snapshots.map(snapshot => (
                <div key={snapshot.id} className="p-3 border border-gray-200 dark:border-gray-700 rounded-lg">
                  <div className="flex items-center justify-between">
                    <div>
                      <div className="font-medium text-gray-900 dark:text-gray-100">
                        Score: {snapshot.score.toFixed(0)}%
                      </div>
                      <div className="text-xs text-gray-500 dark:text-gray-400">
                        {snapshot.timestamp.toLocaleString()}
                      </div>
                    </div>
                    <div className="text-xs text-gray-500 dark:text-gray-400">
                      {snapshot.metrics.length} metrics
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

/**
 * Performance dashboard hook
 */
export const usePerformanceDashboard = () => {
  const [isVisible, setIsVisible] = useState(false);
  const [currentProject, setCurrentProject] = useState<{
    id: string;
    name: string;
    description?: string;
  } | null>(null);

  const openDashboard = useCallback((project?: {
    id: string;
    name: string;
    description?: string;
  } | null) => {
    setCurrentProject(project || null);
    setIsVisible(true);
  }, []);

  const closeDashboard = useCallback(() => {
    setIsVisible(false);
  }, []);

  const toggleDashboard = useCallback((project?: {
    id: string;
    name: string;
    description?: string;
  } | null) => {
    if (isVisible) {
      closeDashboard();
    } else {
      openDashboard(project || null);
    }
  }, [isVisible, openDashboard, closeDashboard]);

  return {
    isVisible,
    currentProject,
    openDashboard,
    closeDashboard,
    toggleDashboard,
  };
};

export default {
  PerformanceDashboard,
  usePerformanceDashboard,
};