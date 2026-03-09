/**
 * @fileoverview Insights Panel Component
 *
 * Displays actionable insights and recommendations
 * based on analytics data analysis.
 *
 * @module ui/components/insights
 * @since 2.0.0
 */

import React, { useMemo } from 'react';
import { Card } from '@ghatana/dcmaar-shared-ui-tailwind';
import type { ProcessedMetrics } from '../../../analytics/AnalyticsPipeline';
import { getTopActions, hasPlaybook } from '../../../analytics/guidance/ActionPlaybooks';
import { useGuidance } from '../../context/GuidanceContext';

interface InsightsPanelProps {
  data: ProcessedMetrics[];
  timeRange?: { from: number; to: number };
}

interface Insight {
  id: string;
  type: 'performance' | 'network' | 'usage' | 'opportunity';
  severity: 'high' | 'medium' | 'low';
  title: string;
  description: string;
  recommendation: string;
  impact: string;
  effort: 'low' | 'medium' | 'high';
}

const mapInsightToGuidance = (
  insight: Insight
): { metric: string; severity: 'warning' | 'critical' } | null => {
  if (insight.id.startsWith('lcp')) {
    return { metric: 'lcp', severity: insight.severity === 'high' ? 'critical' : 'warning' };
  }
  if (insight.id.startsWith('cls')) {
    return { metric: 'cls', severity: insight.severity === 'high' ? 'critical' : 'warning' };
  }
  if (insight.id.startsWith('inp')) {
    return { metric: 'inp', severity: insight.severity === 'high' ? 'critical' : 'warning' };
  }
  if (insight.id.startsWith('budget') || insight.id.startsWith('large-transfer')) {
    return null;
  }
  return null;
};

/**
 * Insights Panel Component
 *
 * Generates and displays actionable insights from analytics data.
 */
export const InsightsPanel: React.FC<InsightsPanelProps> = ({
  data,
  timeRange,
}) => {
  const { openGuidance } = useGuidance();

  // Generate insights from data
  const insights = useMemo((): Insight[] => {
    if (data.length === 0) return [];

    const latest = data[data.length - 1];
    const insights: Insight[] = [];

    // Performance insights
    if (latest?.summary.lcp && latest.summary.lcp > 3000) {
      insights.push({
        id: 'lcp-slow',
        type: 'performance',
        severity: latest.summary.lcp > 4000 ? 'high' : 'medium',
        title: 'Slow Largest Contentful Paint',
        description: `LCP is ${Math.round(latest.summary.lcp)}ms, which is ${latest.summary.lcp > 4000 ? 'well' : ''} above the recommended threshold of 2500ms.`,
        recommendation: 'Optimize image loading, reduce server response time, and eliminate render-blocking resources.',
        impact: 'Improves user experience and search rankings',
        effort: latest.summary.lcp > 4000 ? 'high' : 'medium',
      });
    }

    if (latest?.summary.cls && latest.summary.cls > 0.1) {
      insights.push({
        id: 'cls-high',
        type: 'performance',
        severity: latest.summary.cls > 0.25 ? 'high' : 'medium',
        title: 'High Cumulative Layout Shift',
        description: `CLS is ${latest.summary.cls.toFixed(3)}, indicating visual instability.`,
        recommendation: 'Include size attributes on images and videos, avoid inserting content above existing content.',
        impact: 'Reduces user frustration and improves perceived quality',
        effort: 'medium',
      });
    }

    if (latest?.summary.inp && latest.summary.inp > 200) {
      insights.push({
        id: 'inp-slow',
        type: 'performance',
        severity: latest.summary.inp > 500 ? 'high' : 'medium',
        title: 'Slow Interaction to Next Paint',
        description: `INP is ${Math.round(latest.summary.inp)}ms, indicating slow response to user interactions.`,
        recommendation: 'Minimize JavaScript execution time, reduce main thread work, and use web workers.',
        impact: 'Improves interactivity and user satisfaction',
        effort: 'high',
      });
    }

    // Network insights
    if (latest?.summary.resourceTransfer && latest.summary.resourceTransfer > 3000000) {
      insights.push({
        id: 'large-transfer',
        type: 'network',
        severity: 'medium',
        title: 'Large Resource Transfer Size',
        description: `${(latest.summary.resourceTransfer / 1024 / 1024).toFixed(1)}MB transferred, which may impact load times.`,
        recommendation: 'Compress images, enable text compression, and implement lazy loading for off-screen content.',
        impact: 'Faster page loads and reduced data usage',
        effort: 'medium',
      });
    }

    if (latest?.summary.resourceCount && latest.summary.resourceCount > 100) {
      insights.push({
        id: 'many-requests',
        type: 'network',
        severity: 'medium',
        title: 'High Number of Requests',
        description: `${latest.summary.resourceCount} requests detected, which can slow down page loading.`,
        recommendation: 'Bundle resources, use CSS sprites, and consider HTTP/2 or HTTP/3 for better multiplexing.',
        impact: 'Reduced latency and faster page loads',
        effort: 'low',
      });
    }

    // Usage insights
    const avgInteractions = data.reduce((sum, entry) => 
      sum + (entry.summary.interactionCount || 0), 0
    ) / data.length;

    if (avgInteractions < 5) {
      insights.push({
        id: 'low-engagement',
        type: 'usage',
        severity: 'low',
        title: 'Low User Engagement',
        description: `Average of ${avgInteractions.toFixed(1)} interactions per session suggests low engagement.`,
        recommendation: 'Improve content relevance, enhance navigation, and add interactive elements.',
        impact: 'Increased user retention and conversion',
        effort: 'medium',
      });
    }

    // Opportunity insights
    if (latest?.summary.lcp && latest.summary.lcp < 1500) {
      insights.push({
        id: 'lcp-opportunity',
        type: 'opportunity',
        severity: 'low',
        title: 'Excellent LCP Performance',
        description: `LCP is ${Math.round(latest.summary.lcp)}ms, which is excellent! This is a competitive advantage.`,
        recommendation: 'Highlight performance in marketing materials and use as a benchmark for other pages.',
        impact: 'Competitive differentiation and user trust',
        effort: 'low',
      });
    }

    // Budget violations
    if (latest?.summary.budgetViolations && latest.summary.budgetViolations > 0) {
      insights.push({
        id: 'budget-violations',
        type: 'performance',
        severity: 'high',
        title: 'Performance Budget Violations',
        description: `${latest.summary.budgetViolations} performance budget violations detected.`,
        recommendation: 'Review performance budget settings and prioritize fixing critical violations first.',
        impact: 'Maintains performance standards and prevents regression',
        effort: 'high',
      });
    }

    return insights.sort((a, b) => {
      const severityOrder = { high: 3, medium: 2, low: 1 };
      return severityOrder[b.severity] - severityOrder[a.severity];
    });
  }, [data, timeRange]);

  // Get insight type icon
  const getTypeIcon = (type: Insight['type']): string => {
    switch (type) {
      case 'performance': return '⚡';
      case 'network': return '🌐';
      case 'usage': return '👥';
      case 'opportunity': return '💡';
      default: return '📊';
    }
  };

  // Get severity color
  const getSeverityColor = (severity: Insight['severity']): string => {
    switch (severity) {
      case 'high': return 'text-rose-600 bg-rose-50 border-rose-200';
      case 'medium': return 'text-amber-600 bg-amber-50 border-amber-200';
      case 'low': return 'text-blue-600 bg-blue-50 border-blue-200';
      default: return 'text-slate-600 bg-slate-50 border-slate-200';
    }
  };

  // Get effort indicator
  const getEffortIndicator = (effort: Insight['effort']): { color: string; label: string } => {
    switch (effort) {
      case 'low': return { color: 'text-emerald-600', label: 'Quick Win' };
      case 'medium': return { color: 'text-amber-600', label: 'Medium Effort' };
      case 'high': return { color: 'text-rose-600', label: 'Major Project' };
      default: return { color: 'text-slate-600', label: 'Unknown' };
    }
  };

  // Render insight item
  const renderInsight = (insight: Insight) => {
    const severityColor = getSeverityColor(insight.severity);
    const effortIndicator = getEffortIndicator(insight.effort);
    const guidanceContext = mapInsightToGuidance(insight);
    const quickActions =
      guidanceContext && hasPlaybook(guidanceContext.metric, guidanceContext.severity)
        ? getTopActions(guidanceContext.metric, guidanceContext.severity, 2)
        : [];
    
    return (
      <div
        key={insight.id}
        className={`p-4 border rounded-lg ${severityColor}`}
      >
        <div className="flex items-start justify-between mb-3">
          <div className="flex items-center gap-2">
            <span className="text-lg">{getTypeIcon(insight.type)}</span>
            <div>
              <h3 className="font-semibold text-slate-900">{insight.title}</h3>
              <span className="text-xs text-slate-500 capitalize">{insight.type}</span>
            </div>
          </div>
          
          <div className="flex items-center gap-2">
            <span className={`text-xs font-medium ${effortIndicator.color}`}>
              {effortIndicator.label}
            </span>
            <span className={`px-2 py-1 text-xs font-medium rounded-full ${
              insight.severity === 'high' ? 'bg-rose-100 text-rose-700' :
              insight.severity === 'medium' ? 'bg-amber-100 text-amber-700' :
              'bg-blue-100 text-blue-700'
            }`}>
              {insight.severity.toUpperCase()}
            </span>
          </div>
        </div>
        
        <p className="text-sm text-slate-700 mb-3">
          {insight.description}
        </p>
        
        <div className="space-y-2">
          <div>
            <h4 className="text-xs font-semibold text-slate-600 mb-1">Recommendation</h4>
            <p className="text-sm text-slate-700">{insight.recommendation}</p>
          </div>
          
          <div>
            <h4 className="text-xs font-semibold text-slate-600 mb-1">Impact</h4>
            <p className="text-sm text-slate-700">{insight.impact}</p>
          </div>
        </div>

        {guidanceContext && quickActions.length > 0 && (
          <div className="mt-3 space-y-3 rounded-lg border border-blue-100 bg-blue-50 p-3 text-blue-900">
            <div className="flex items-center justify-between">
              <h4 className="text-sm font-semibold text-blue-900">🎯 Suggested Actions</h4>
              <span className="text-xs uppercase tracking-wide text-blue-500">
                {guidanceContext.severity === 'critical' ? 'Critical' : 'Warning'}
              </span>
            </div>
            <ul className="space-y-2">
              {quickActions.map((action) => (
                <li key={action.id} className="rounded-md bg-white/70 p-2 shadow-sm">
                  <div className="flex items-center justify-between">
                    <div>
                      <p className="text-sm font-medium text-blue-900">{action.title}</p>
                      <p className="text-xs text-blue-700">
                        {action.expectedImprovement} • {action.timeToImplement}
                      </p>
                      <p className="text-xs text-blue-700">{action.description}</p>
                    </div>
                    <button
                      type="button"
                      className="ml-3 rounded border border-blue-200 px-2 py-1 text-xs font-medium text-blue-600 hover:bg-blue-100"
                      onClick={() =>
                        openGuidance({
                          metric: guidanceContext.metric,
                          severity: guidanceContext.severity,
                          source: 'insight',
                        })
                      }
                    >
                      Start This Fix
                    </button>
                  </div>
                </li>
              ))}
            </ul>
            <button
              type="button"
              className="text-sm font-medium text-blue-600 hover:text-blue-700"
              onClick={() =>
                openGuidance({
                  metric: guidanceContext.metric,
                  severity: guidanceContext.severity,
                  source: 'insight',
                })
              }
            >
              View complete fix guide →
            </button>
          </div>
        )}
      </div>
    );
  };

  // Render insights summary
  const renderSummary = () => {
    const summary = insights.reduce((acc, insight) => {
      acc[insight.severity] = (acc[insight.severity] || 0) + 1;
      acc[insight.type] = (acc[insight.type] || 0) + 1;
      return acc;
    }, {} as Record<string, number>);

    return (
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <div className="text-center">
          <div className="text-2xl font-bold text-rose-600">
            {summary.high || 0}
          </div>
          <div className="text-sm text-slate-500">High Priority</div>
        </div>
        <div className="text-center">
          <div className="text-2xl font-bold text-amber-600">
            {summary.medium || 0}
          </div>
          <div className="text-sm text-slate-500">Medium Priority</div>
        </div>
        <div className="text-center">
          <div className="text-2xl font-bold text-blue-600">
            {summary.low || 0}
          </div>
          <div className="text-sm text-slate-500">Low Priority</div>
        </div>
        <div className="text-center">
          <div className="text-2xl font-bold text-slate-900">
            {insights.length}
          </div>
          <div className="text-sm text-slate-500">Total Insights</div>
        </div>
      </div>
    );
  };

  return (
    <Card title="Performance Insights" description="AI-powered analysis and recommendations">
      <div className="space-y-6">
        {insights.length > 0 && renderSummary()}
        
        {insights.length === 0 ? (
          <div className="text-center py-8 text-slate-500">
            <div className="text-lg font-medium mb-2">No Insights Available</div>
            <div className="text-sm">
              {data.length === 0 
                ? 'Collect some data first to generate insights'
                : 'Great job! No critical issues detected'
              }
            </div>
          </div>
        ) : (
          <div className="space-y-4">
            {insights.map(renderInsight)}
          </div>
        )}
        
        {insights.length > 0 && (
          <div className="text-center pt-4 border-t border-slate-200">
            <p className="text-sm text-slate-500 mb-3">
              Insights are generated based on your performance data and best practices
            </p>
            <button className="text-sm text-blue-600 hover:text-blue-700">
              Generate detailed report →
            </button>
          </div>
        )}
      </div>
    </Card>
  );
};

export default InsightsPanel;
