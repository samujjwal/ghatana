/**
 * @fileoverview Page Analytics Component
 *
 * Detailed page-level analytics with performance metrics,
 * user behavior, and optimization insights.
 *
 * @module ui/components/analytics
 * @since 2.0.0
 */

import React, { useMemo, useState } from 'react';
import { Card } from '@ghatana/dcmaar-shared-ui-tailwind';
import type { ProcessedMetrics } from '../../../analytics/AnalyticsPipeline';
import { MetricCard } from '../metrics/MetricCard';
import { MetricChart } from '../metrics/MetricChart';

interface PageAnalyticsProps {
  data: ProcessedMetrics[];
  timeRange: { from: number; to: number };
  selectedPage?: string;
  onPageSelect: (page?: string) => void;
}

// Mock page data - in production, this would come from your analytics
const MOCK_PAGES = [
  { path: '/home', title: 'Home Page', views: 15420 },
  { path: '/about', title: 'About Us', views: 8320 },
  { path: '/products', title: 'Products', views: 12100 },
  { path: '/contact', title: 'Contact', views: 3450 },
  { path: '/dashboard', title: 'Dashboard', views: 6780 },
];

/**
 * Page Analytics Component
 *
 * Displays page-level performance analytics and user behavior.
 */
export const PageAnalytics: React.FC<PageAnalyticsProps> = ({
  data,
  timeRange,
  selectedPage,
  onPageSelect,
}) => {
  const [sortBy, setSortBy] = useState<'performance' | 'views' | 'engagement'>('performance');

  // Aggregate metrics by page
  const pageMetrics = useMemo(() => {
    return MOCK_PAGES.map(page => {
      const pageData = data.slice(0, 8); // Mock subset
      const latest = pageData[pageData.length - 1];
      
      return {
        ...page,
        performance: {
          lcp: latest?.summary.lcp ? latest.summary.lcp + Math.random() * 800 : 1800 + Math.random() * 1500,
          inp: latest?.summary.inp ? latest.summary.inp + Math.random() * 80 : 120 + Math.random() * 150,
          cls: latest?.summary.cls ? latest.summary.cls + Math.random() * 0.08 : 0.03 + Math.random() * 0.15,
          tbt: latest?.summary.tbt ? latest.summary.tbt + Math.random() * 150 : 150 + Math.random() * 300,
          fcp: latest?.summary.fcp ? latest.summary.fcp + Math.random() * 500 : 1000 + Math.random() * 1000,
          ttfb: latest?.summary.ttfb ? latest.summary.ttfb + Math.random() * 300 : 400 + Math.random() * 600,
        },
        userBehavior: {
          avgTimeOnPage: Math.floor(Math.random() * 180) + 30,
          bounceRate: Math.random() * 0.4 + 0.2,
          exitRate: Math.random() * 0.3 + 0.1,
          conversionRate: Math.random() * 0.08 + 0.02,
        },
        resources: {
          totalRequests: Math.floor(Math.random() * 100) + 20,
          totalSize: Math.floor(Math.random() * 2000) + 500,
          cachedRequests: Math.floor(Math.random() * 30) + 5,
          imageRequests: Math.floor(Math.random() * 40) + 10,
        },
      };
    });
  }, [data]);

  // Sort pages based on selected criteria
  const sortedPages = useMemo(() => {
    return [...pageMetrics].sort((a, b) => {
      switch (sortBy) {
        case 'performance':
          return a.performance.lcp - b.performance.lcp;
        case 'views':
          return b.views - a.views;
        case 'engagement':
          return b.userBehavior.avgTimeOnPage - a.userBehavior.avgTimeOnPage;
        default:
          return 0;
      }
    });
  }, [pageMetrics, sortBy]);

  // Calculate page performance score
  const getPerformanceScore = (metrics: typeof pageMetrics[0]): number => {
    const { performance } = metrics;
    
    let score = 100;
    
    // LCP scoring (40% weight)
    if (performance.lcp > 4000) score -= 40;
    else if (performance.lcp > 2500) score -= 20;
    
    // INP scoring (30% weight)
    if (performance.inp > 500) score -= 30;
    else if (performance.inp > 200) score -= 15;
    
    // CLS scoring (20% weight)
    if (performance.cls > 0.25) score -= 20;
    else if (performance.cls > 0.1) score -= 10;
    
    // TBT scoring (10% weight)
    if (performance.tbt > 600) score -= 10;
    else if (performance.tbt > 300) score -= 5;
    
    return Math.max(0, score);
  };

  // Get performance grade
  const getPerformanceGrade = (score: number): { grade: string; color: string } => {
    if (score >= 90) return { grade: 'A', color: 'text-emerald-600' };
    if (score >= 80) return { grade: 'B', color: 'text-blue-600' };
    if (score >= 70) return { grade: 'C', color: 'text-amber-600' };
    if (score >= 60) return { grade: 'D', color: 'text-orange-600' };
    return { grade: 'F', color: 'text-rose-600' };
  };

  // Render page list
  const renderPageList = () => (
    <div className="space-y-3">
      {sortedPages.map(page => {
        const score = getPerformanceScore(page);
        const grade = getPerformanceGrade(score);
        
        return (
          <div
            key={page.path}
            className={`p-4 border rounded-lg cursor-pointer transition-all hover:shadow-md ${
              selectedPage === page.path ? 'border-blue-500 bg-blue-50' : 'border-slate-200'
            }`}
            onClick={() => onPageSelect(page.path === selectedPage ? undefined : page.path)}
          >
            <div className="flex items-center justify-between mb-3">
              <div>
                <h3 className="font-semibold text-slate-900">{page.title}</h3>
                <div className="text-xs text-slate-500 mt-1">{page.path}</div>
              </div>
              
              <div className="text-right">
                <div className={`text-2xl font-bold ${grade.color}`}>{grade.grade}</div>
                <div className="text-xs text-slate-500">{score}/100</div>
              </div>
            </div>
            
            <div className="grid grid-cols-3 gap-4 text-sm">
              <div>
                <div className="text-slate-500">LCP</div>
                <div className="font-medium">{page.performance.lcp.toFixed(0)} ms</div>
              </div>
              <div>
                <div className="text-slate-500">Views</div>
                <div className="font-medium">{page.views.toLocaleString()}</div>
              </div>
              <div>
                <div className="text-slate-500">Time on Page</div>
                <div className="font-medium">{page.userBehavior.avgTimeOnPage}s</div>
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );

  // Render selected page details
  const renderPageDetails = () => {
    if (!selectedPage) return null;
    
    const selectedMetrics = pageMetrics.find(p => p.path === selectedPage);
    if (!selectedMetrics) return null;

    const score = getPerformanceScore(selectedMetrics);
    const grade = getPerformanceGrade(score);

    return (
      <div className="space-y-6">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-xl font-semibold text-slate-900">
              {selectedMetrics.title}
            </h2>
            <p className="text-sm text-slate-500">{selectedMetrics.path}</p>
          </div>
          <div className="flex items-center gap-4">
            <div className="text-right">
              <div className={`text-3xl font-bold ${grade.color}`}>{grade.grade}</div>
              <div className="text-sm text-slate-500">Performance Score</div>
            </div>
            <button
              onClick={() => onPageSelect(undefined)}
              className="text-sm text-slate-500 hover:text-slate-700"
            >
              Clear selection
            </button>
          </div>
        </div>

        {/* Core Web Vitals */}
        <Card title="Core Web Vitals">
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <MetricCard
              title="Largest Contentful Paint"
              metricKey="lcp"
              value={selectedMetrics.performance.lcp}
              unit="ms"
              status={selectedMetrics.performance.lcp > 4000 ? 'poor' : 
                      selectedMetrics.performance.lcp > 2500 ? 'warning' : 'good'}
            />
            <MetricCard
              title="Interaction to Next Paint"
              metricKey="inp"
              value={selectedMetrics.performance.inp}
              unit="ms"
              status={selectedMetrics.performance.inp > 500 ? 'poor' : 
                      selectedMetrics.performance.inp > 200 ? 'warning' : 'good'}
            />
            <MetricCard
              title="Cumulative Layout Shift"
              metricKey="cls"
              value={selectedMetrics.performance.cls}
              precision={3}
              status={selectedMetrics.performance.cls > 0.25 ? 'poor' : 
                      selectedMetrics.performance.cls > 0.1 ? 'warning' : 'good'}
            />
            <MetricCard
              title="Total Blocking Time"
              metricKey="tbt"
              value={selectedMetrics.performance.tbt}
              unit="ms"
              status={selectedMetrics.performance.tbt > 600 ? 'poor' : 
                      selectedMetrics.performance.tbt > 300 ? 'warning' : 'good'}
            />
            <MetricCard
              title="First Contentful Paint"
              metricKey="fcp"
              value={selectedMetrics.performance.fcp}
              unit="ms"
              status={selectedMetrics.performance.fcp > 3000 ? 'poor' : 
                      selectedMetrics.performance.fcp > 1800 ? 'warning' : 'good'}
            />
            <MetricCard
              title="Time to First Byte"
              metricKey="ttfb"
              value={selectedMetrics.performance.ttfb}
              unit="ms"
              status={selectedMetrics.performance.ttfb > 1000 ? 'poor' : 
                      selectedMetrics.performance.ttfb > 600 ? 'warning' : 'good'}
            />
          </div>
        </Card>

        {/* User Behavior */}
        <Card title="User Behavior Analytics">
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <div className="text-center">
              <div className="text-2xl font-bold text-slate-900">
                {selectedMetrics.userBehavior.avgTimeOnPage}s
              </div>
              <div className="text-sm text-slate-500">Avg Time on Page</div>
            </div>
            <div className="text-center">
              <div className="text-2xl font-bold text-slate-900">
                {(selectedMetrics.userBehavior.bounceRate * 100).toFixed(1)}%
              </div>
              <div className="text-sm text-slate-500">Bounce Rate</div>
            </div>
            <div className="text-center">
              <div className="text-2xl font-bold text-slate-900">
                {(selectedMetrics.userBehavior.exitRate * 100).toFixed(1)}%
              </div>
              <div className="text-sm text-slate-500">Exit Rate</div>
            </div>
            <div className="text-center">
              <div className="text-2xl font-bold text-slate-900">
                {(selectedMetrics.userBehavior.conversionRate * 100).toFixed(1)}%
              </div>
              <div className="text-sm text-slate-500">Conversion Rate</div>
            </div>
          </div>
        </Card>

        {/* Resource Analysis */}
        <Card title="Resource Analysis">
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <div className="text-center">
              <div className="text-2xl font-bold text-slate-900">
                {selectedMetrics.resources.totalRequests}
              </div>
              <div className="text-sm text-slate-500">Total Requests</div>
            </div>
            <div className="text-center">
              <div className="text-2xl font-bold text-slate-900">
                {(selectedMetrics.resources.totalSize / 1024).toFixed(1)} MB
              </div>
              <div className="text-sm text-slate-500">Total Size</div>
            </div>
            <div className="text-center">
              <div className="text-2xl font-bold text-slate-900">
                {selectedMetrics.resources.cachedRequests}
              </div>
              <div className="text-sm text-slate-500">Cached Requests</div>
            </div>
            <div className="text-center">
              <div className="text-2xl font-bold text-slate-900">
                {selectedMetrics.resources.imageRequests}
              </div>
              <div className="text-sm text-slate-500">Image Requests</div>
            </div>
          </div>
        </Card>

        {/* Performance Trends */}
        <MetricChart
          title="Performance Trends"
          data={data}
          metrics={['lcp', 'inp', 'cls', 'tbt']}
          type="line"
          height={300}
        />
      </div>
    );
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-semibold text-slate-900">Page Analytics</h2>
        
        <div className="flex items-center gap-2">
          <span className="text-sm text-slate-500">Sort by:</span>
          <select
            value={sortBy}
            onChange={(e) => setSortBy(e.target.value as typeof sortBy)}
            className="px-3 py-1 text-sm border border-slate-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="performance">Performance</option>
            <option value="views">Page Views</option>
            <option value="engagement">Engagement</option>
          </select>
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="lg:col-span-1">
          <Card title="Pages" description={`${MOCK_PAGES.length} pages analyzed`}>
            {renderPageList()}
          </Card>
        </div>
        
        <div className="lg:col-span-2">
          {selectedPage ? (
            renderPageDetails()
          ) : (
            <Card title="Select a Page">
              <div className="text-center py-12 text-slate-500">
                <div className="text-lg font-medium mb-2">No Page Selected</div>
                <div className="text-sm">Select a page from the list to view detailed analytics</div>
              </div>
            </Card>
          )}
        </div>
      </div>
    </div>
  );
};

export default PageAnalytics;
