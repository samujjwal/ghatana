/**
 * @fileoverview Domain Analytics Component
 *
 * Comprehensive domain-level analytics with performance
 * metrics, comparisons, and insights.
 *
 * @module ui/components/analytics
 * @since 2.0.0
 */

import React, { useMemo, useState } from 'react';
import { Card } from '@ghatana/dcmaar-shared-ui-tailwind';
import type { ProcessedMetrics } from '../../../analytics/AnalyticsPipeline';
import { MetricCard } from '../metrics/MetricCard';
import { MetricChart } from '../metrics/MetricChart';
import { MetricTable } from '../metrics/MetricTable';

interface DomainAnalyticsProps {
  data: ProcessedMetrics[];
  timeRange: { from: number; to: number };
  selectedDomain?: string;
  onDomainSelect: (domain?: string) => void;
}

// Mock domain data - in production, this would come from your analytics
const MOCK_DOMAINS = [
  'example.com',
  'test.com',
  'staging.example.com',
  'api.example.com',
];

/**
 * Domain Analytics Component
 *
 * Displays domain-level performance analytics and comparisons.
 */
export const DomainAnalytics: React.FC<DomainAnalyticsProps> = ({
  data,
  timeRange,
  selectedDomain,
  onDomainSelect,
}) => {
  const [sortBy, setSortBy] = useState<'performance' | 'traffic' | 'errors'>('performance');

  // Aggregate metrics by domain
  const domainMetrics = useMemo(() => {
    // In production, you would group actual data by domain
    // For now, we'll create mock aggregated data
    return MOCK_DOMAINS.map(domain => {
      const domainData = data.slice(0, 10); // Mock subset
      const latest = domainData[domainData.length - 1];
      
      return {
        domain,
        performance: {
          lcp: latest?.summary.lcp ? latest.summary.lcp + Math.random() * 1000 : 2500 + Math.random() * 2000,
          inp: latest?.summary.inp ? latest.summary.inp + Math.random() * 100 : 150 + Math.random() * 200,
          cls: latest?.summary.cls ? latest.summary.cls + Math.random() * 0.1 : 0.05 + Math.random() * 0.2,
          tbt: latest?.summary.tbt ? latest.summary.tbt + Math.random() * 200 : 200 + Math.random() * 400,
        },
        traffic: {
          pageViews: Math.floor(Math.random() * 10000) + 1000,
          uniqueVisitors: Math.floor(Math.random() * 5000) + 500,
          bounceRate: Math.random() * 0.5 + 0.2,
          avgSessionDuration: Math.floor(Math.random() * 300) + 60,
        },
        errors: {
          errorRate: Math.random() * 0.05,
          jsErrors: Math.floor(Math.random() * 50),
          networkErrors: Math.floor(Math.random() * 20),
          consoleErrors: Math.floor(Math.random() * 100),
        },
      };
    });
  }, [data]);

  // Sort domains based on selected criteria
  const sortedDomains = useMemo(() => {
    return [...domainMetrics].sort((a, b) => {
      switch (sortBy) {
        case 'performance':
          return a.performance.lcp - b.performance.lcp;
        case 'traffic':
          return b.traffic.pageViews - a.traffic.pageViews;
        case 'errors':
          return b.errors.errorRate - a.errors.errorRate;
        default:
          return 0;
      }
    });
  }, [domainMetrics, sortBy]);

  // Calculate domain status
  const getDomainStatus = (metrics: typeof domainMetrics[0]): 'good' | 'warning' | 'poor' => {
    const { performance, errors } = metrics;
    
    if (performance.lcp > 4000 || errors.errorRate > 0.02) return 'poor';
    if (performance.lcp > 2500 || errors.errorRate > 0.01) return 'warning';
    return 'good';
  };

  // Render domain list
  const renderDomainList = () => (
    <div className="space-y-3">
      {sortedDomains.map(({ domain, performance, traffic, errors }) => {
        const status = getDomainStatus({ domain, performance, traffic, errors });
        
        return (
          <div
            key={domain}
            className={`p-4 border rounded-lg cursor-pointer transition-all hover:shadow-md ${
              selectedDomain === domain ? 'border-blue-500 bg-blue-50' : 'border-slate-200'
            }`}
            onClick={() => onDomainSelect(domain === selectedDomain ? undefined : domain)}
          >
            <div className="flex items-center justify-between mb-3">
              <div>
                <h3 className="font-semibold text-slate-900">{domain}</h3>
                <div className="flex items-center gap-2 mt-1">
                  <div className={`h-2 w-2 rounded-full ${
                    status === 'good' ? 'bg-emerald-500' :
                    status === 'warning' ? 'bg-amber-500' :
                    'bg-rose-500'
                  }`}></div>
                  <span className="text-xs text-slate-500 capitalize">{status}</span>
                </div>
              </div>
              
              <div className="text-right">
                <div className="text-sm text-slate-500">Page Views</div>
                <div className="text-lg font-semibold text-slate-900">
                  {traffic.pageViews.toLocaleString()}
                </div>
              </div>
            </div>
            
            <div className="grid grid-cols-3 gap-4 text-sm">
              <div>
                <div className="text-slate-500">LCP</div>
                <div className="font-medium">{performance.lcp.toFixed(0)} ms</div>
              </div>
              <div>
                <div className="text-slate-500">Error Rate</div>
                <div className="font-medium">{(errors.errorRate * 100).toFixed(2)}%</div>
              </div>
              <div>
                <div className="text-slate-500">Bounce Rate</div>
                <div className="font-medium">{(traffic.bounceRate * 100).toFixed(1)}%</div>
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );

  // Render selected domain details
  const renderDomainDetails = () => {
    if (!selectedDomain) return null;
    
    const selectedMetrics = domainMetrics.find(d => d.domain === selectedDomain);
    if (!selectedMetrics) return null;

    return (
      <div className="space-y-6">
        <div className="flex items-center justify-between">
          <h2 className="text-xl font-semibold text-slate-900">
            {selectedDomain} Details
          </h2>
          <button
            onClick={() => onDomainSelect(undefined)}
            className="text-sm text-slate-500 hover:text-slate-700"
          >
            Clear selection
          </button>
        </div>

        {/* Performance KPIs */}
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <MetricCard
            title="LCP"
            metricKey="lcp"
            value={selectedMetrics.performance.lcp}
            unit="ms"
            status={selectedMetrics.performance.lcp > 4000 ? 'poor' : 
                    selectedMetrics.performance.lcp > 2500 ? 'warning' : 'good'}
          />
          <MetricCard
            title="INP"
            metricKey="inp"
            value={selectedMetrics.performance.inp}
            unit="ms"
            status={selectedMetrics.performance.inp > 500 ? 'poor' : 
                    selectedMetrics.performance.inp > 200 ? 'warning' : 'good'}
          />
          <MetricCard
            title="CLS"
            metricKey="cls"
            value={selectedMetrics.performance.cls}
            precision={3}
            status={selectedMetrics.performance.cls > 0.25 ? 'poor' : 
                    selectedMetrics.performance.cls > 0.1 ? 'warning' : 'good'}
          />
          <MetricCard
            title="Error Rate"
            metricKey="errorRate"
            value={selectedMetrics.errors.errorRate * 100}
            unit="%"
            status={selectedMetrics.errors.errorRate > 0.02 ? 'poor' : 
                    selectedMetrics.errors.errorRate > 0.01 ? 'warning' : 'good'}
          />
        </div>

        {/* Traffic Analytics */}
        <Card title="Traffic Analytics">
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <div className="text-center">
              <div className="text-2xl font-bold text-slate-900">
                {selectedMetrics.traffic.pageViews.toLocaleString()}
              </div>
              <div className="text-sm text-slate-500">Page Views</div>
            </div>
            <div className="text-center">
              <div className="text-2xl font-bold text-slate-900">
                {selectedMetrics.traffic.uniqueVisitors.toLocaleString()}
              </div>
              <div className="text-sm text-slate-500">Unique Visitors</div>
            </div>
            <div className="text-center">
              <div className="text-2xl font-bold text-slate-900">
                {Math.round(selectedMetrics.traffic.avgSessionDuration)}s
              </div>
              <div className="text-sm text-slate-500">Avg Session</div>
            </div>
            <div className="text-center">
              <div className="text-2xl font-bold text-slate-900">
                {(selectedMetrics.traffic.bounceRate * 100).toFixed(1)}%
              </div>
              <div className="text-sm text-slate-500">Bounce Rate</div>
            </div>
          </div>
        </Card>

        {/* Performance Chart */}
        <MetricChart
          title="Performance Trends"
          data={data}
          metrics={['lcp', 'inp', 'cls']}
          type="line"
          height={300}
        />
      </div>
    );
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-semibold text-slate-900">Domain Analytics</h2>
        
        <div className="flex items-center gap-2">
          <span className="text-sm text-slate-500">Sort by:</span>
          <select
            value={sortBy}
            onChange={(e) => setSortBy(e.target.value as typeof sortBy)}
            className="px-3 py-1 text-sm border border-slate-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="performance">Performance</option>
            <option value="traffic">Traffic</option>
            <option value="errors">Errors</option>
          </select>
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="lg:col-span-1">
          <Card title="Domains" description={`${MOCK_DOMAINS.length} domains analyzed`}>
            {renderDomainList()}
          </Card>
        </div>
        
        <div className="lg:col-span-2">
          {selectedDomain ? (
            renderDomainDetails()
          ) : (
            <Card title="Select a Domain">
              <div className="text-center py-12 text-slate-500">
                <div className="text-lg font-medium mb-2">No Domain Selected</div>
                <div className="text-sm">Select a domain from the list to view detailed analytics</div>
              </div>
            </Card>
          )}
        </div>
      </div>
    </div>
  );
};

export default DomainAnalytics;
