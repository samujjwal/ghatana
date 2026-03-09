/**
 * @fileoverview Metric Registry
 *
 * Central registry for all metrics with full metadata, relationships,
 * and visualization configurations. This is the single source of truth
 * for metric definitions in the analytics system.
 *
 * @module analytics/metrics
 * @since 2.0.0
 */

import type { 
  MetricNamespace, 
  MetricDefinition, 
  AggregationStrategy,
  ThresholdConfig,
  InsightConfig,
  VisualizationConfig,
  DataSource
} from '../types/MetricTypes';

/**
 * Metric Registry
 *
 * Provides a centralized, type-safe registry for all metrics
 * with full metadata and relationships.
 */
export class MetricRegistry {
  private namespaces = new Map<string, MetricNamespace>();
  private metrics = new Map<string, MetricDefinition>();
  private relationships = new Map<string, string[]>();

  constructor() {
    this.initializeCoreMetrics();
  }

  /**
   * Register a metric namespace
   */
  registerNamespace(namespace: MetricNamespace): void {
    this.namespaces.set(namespace.id, namespace);
    
    // Register all metrics in the namespace
    for (const metric of Object.values(namespace.metrics)) {
      this.registerMetric(metric);
    }
  }

  /**
   * Register a single metric
   */
  registerMetric(metric: MetricDefinition): void {
    this.metrics.set(metric.id, metric);
    
    // Track relationships
    if (!this.relationships.has(metric.id)) {
      this.relationships.set(metric.id, []);
    }
  }

  /**
   * Get metric by ID
   */
  getMetric(id: string): MetricDefinition | undefined {
    return this.metrics.get(id);
  }

  /**
   * Get namespace by ID
   */
  getNamespace(id: string): MetricNamespace | undefined {
    return this.namespaces.get(id);
  }

  /**
   * Get all metrics in namespace
   */
  getMetricsInNamespace(namespaceId: string): MetricDefinition[] {
    const namespace = this.namespaces.get(namespaceId);
    if (!namespace) return [];
    
    return Object.values(namespace.metrics);
  }

  /**
   * Get related metrics
   */
  getRelatedMetrics(metricId: string): MetricDefinition[] {
    const relatedIds = this.relationships.get(metricId) || [];
    return relatedIds
      .map(id => this.metrics.get(id))
      .filter((m): m is MetricDefinition => m !== undefined);
  }

  /**
   * Search metrics by name or description
   */
  searchMetrics(query: string): MetricDefinition[] {
    const lowerQuery = query.toLowerCase();
    return Array.from(this.metrics.values()).filter(metric =>
      metric.name.toLowerCase().includes(lowerQuery) ||
      metric.description.toLowerCase().includes(lowerQuery)
    );
  }

  /**
   * Get metrics by category
   */
  getMetricsByCategory(category: string): MetricDefinition[] {
    return Array.from(this.namespaces.values())
      .filter(ns => ns.category === category)
      .flatMap(ns => Object.values(ns.metrics));
  }

  /**
   * Initialize core metrics
   */
  private initializeCoreMetrics(): void {
    // Performance namespace
    this.registerNamespace({
      id: 'performance',
      name: 'Performance',
      description: 'Core Web Vitals and performance metrics',
      icon: 'speed',
      category: 'performance',
      metrics: {
        lcp: {
          id: 'lcp',
          name: 'Largest Contentful Paint',
          description: 'Measures loading performance. To provide a good user experience, LCP should occur within 2.5s of when the page first starts loading.',
          unit: 'ms',
          format: 'duration',
          precision: 0,
          aggregation: {
            type: 'percentile',
            value: 75,
            window: '1h'
          },
          thresholds: {
            good: 2500,
            needsImprovement: 4000,
            poor: Infinity,
            direction: 'lower-is-better'
          },
          insights: {
            title: 'Loading Performance',
            description: 'LCP measures the time it takes for the largest content element to become visible.',
            recommendations: [
              'Optimize server response time',
              'Optimize resource loading',
              'Eliminate render-blocking resources',
              'Optimize images'
            ]
          },
          visualization: {
            type: 'sparkline',
            color: 'primary',
            showTrend: true,
            showThresholds: true
          },
          sources: [
            {
              type: 'page',
              path: 'metrics.lcp',
              required: true
            }
          ]
        },
        inp: {
          id: 'inp',
          name: 'Interaction to Next Paint',
          description: 'Measures responsiveness. To provide a good user experience, pages should have an INP of 200 or less.',
          unit: 'ms',
          format: 'duration',
          precision: 0,
          aggregation: {
            type: 'percentile',
            value: 75,
            window: '1h'
          },
          thresholds: {
            good: 200,
            needsImprovement: 500,
            poor: Infinity,
            direction: 'lower-is-better'
          },
          insights: {
            title: 'Interaction Responsiveness',
            description: 'INP measures the time from user interaction to the next paint.',
            recommendations: [
              'Reduce JavaScript execution time',
              'Break up long tasks',
              'Optimize event handlers',
              'Use web workers for heavy computations'
            ]
          },
          visualization: {
            type: 'sparkline',
            color: 'warning',
            showTrend: true,
            showThresholds: true
          },
          sources: [
            {
              type: 'page',
              path: 'metrics.inp',
              required: true
            }
          ]
        },
        cls: {
          id: 'cls',
          name: 'Cumulative Layout Shift',
          description: 'Measures visual stability. To provide a good user experience, pages should maintain a CLS of 0.1 or less.',
          unit: '',
          format: 'number',
          precision: 3,
          aggregation: {
            type: 'sum',
            window: '1h'
          },
          thresholds: {
            good: 0.1,
            needsImprovement: 0.25,
            poor: Infinity,
            direction: 'lower-is-better'
          },
          insights: {
            title: 'Visual Stability',
            description: 'CLS measures unexpected layout shifts during page load.',
            recommendations: [
              'Always include width and height attributes on images and video elements',
              'Reserve space for dynamic content',
              'Avoid inserting content above existing content',
              'Use transform animations instead of animating properties that trigger layout'
            ]
          },
          visualization: {
            type: 'sparkline',
            color: 'error',
            showTrend: true,
            showThresholds: true
          },
          sources: [
            {
              type: 'page',
              path: 'metrics.cls',
              required: true
            }
          ]
        },
        fcp: {
          id: 'fcp',
          name: 'First Contentful Paint',
          description: 'Measures when the first content is painted. Good user experience starts with FCP under 1.8s.',
          unit: 'ms',
          format: 'duration',
          precision: 0,
          aggregation: {
            type: 'percentile',
            value: 75,
            window: '1h'
          },
          thresholds: {
            good: 1800,
            needsImprovement: 3000,
            poor: Infinity,
            direction: 'lower-is-better'
          },
          insights: {
            title: 'Initial Paint Time',
            description: 'FCP measures when the first text or image is painted.',
            recommendations: [
              'Reduce server response times',
              'Eliminate render-blocking resources',
              'Minimize critical resource size',
              'Optimize resource loading'
            ]
          },
          visualization: {
            type: 'sparkline',
            color: 'info',
            showTrend: true,
            showThresholds: true
          },
          sources: [
            {
              type: 'page',
              path: 'metrics.fcp',
              required: true
            }
          ]
        },
        ttfb: {
          id: 'ttfb',
          name: 'Time to First Byte',
          description: 'Measures the time between the request for a resource and when the first byte of a response begins to arrive.',
          unit: 'ms',
          format: 'duration',
          precision: 0,
          aggregation: {
            type: 'percentile',
            value: 75,
            window: '1h'
          },
          thresholds: {
            good: 800,
            needsImprovement: 1800,
            poor: Infinity,
            direction: 'lower-is-better'
          },
          insights: {
            title: 'Server Response Time',
            description: 'TTFB measures server responsiveness and network latency.',
            recommendations: [
              'Improve server response time',
              'Use CDN for static assets',
              'Optimize database queries',
              'Enable HTTP/2 or HTTP/3'
            ]
          },
          visualization: {
            type: 'sparkline',
            color: 'secondary',
            showTrend: true,
            showThresholds: true
          },
          sources: [
            {
              type: 'network',
              path: 'ttfb',
              required: true
            }
          ]
        },
        tbt: {
          id: 'tbt',
          name: 'Total Blocking Time',
          description: 'Measures the total amount of time between FCP and TTI where the main thread was blocked for long enough to prevent input responsiveness.',
          unit: 'ms',
          format: 'duration',
          precision: 0,
          aggregation: {
            type: 'sum',
            window: '1h'
          },
          thresholds: {
            good: 200,
            needsImprovement: 600,
            poor: Infinity,
            direction: 'lower-is-better'
          },
          insights: {
            title: 'Main Thread Blocking',
            description: 'TBT measures the total time the main thread is blocked, preventing user interaction.',
            recommendations: [
              'Reduce JavaScript execution time',
              'Break up long tasks',
              'Use web workers',
              'Optimize third-party scripts'
            ]
          },
          visualization: {
            type: 'sparkline',
            color: 'warning',
            showTrend: true,
            showThresholds: true
          },
          sources: [
            {
              type: 'page',
              path: 'diagnostics.totalBlockingTime',
              required: true
            }
          ]
        }
      },
      relationships: []
    });

    // Network namespace
    this.registerNamespace({
      id: 'network',
      name: 'Network',
      description: 'Network performance and resource metrics',
      icon: 'network',
      category: 'network',
      metrics: {
        requests: {
          id: 'requests',
          name: 'Total Requests',
          description: 'Total number of network requests made by the page.',
          unit: '',
          format: 'number',
          precision: 0,
          aggregation: {
            type: 'count',
            window: '1h'
          },
          thresholds: {
            good: 50,
            needsImprovement: 100,
            poor: Infinity,
            direction: 'lower-is-better'
          },
          insights: {
            title: 'Request Count',
            description: 'Number of network requests impacts page load performance.',
            recommendations: [
              'Combine multiple requests into one',
              'Use resource bundling',
              'Implement lazy loading',
              'Optimize third-party dependencies'
            ]
          },
          visualization: {
            type: 'bar',
            color: 'primary',
            showTrend: true,
            showThresholds: true
          },
          sources: [
            {
              type: 'network',
              path: 'totalRequests',
              required: true
            }
          ]
        },
        transferSize: {
          id: 'transferSize',
          name: 'Transfer Size',
          description: 'Total size of all transferred resources in kilobytes.',
          unit: 'KB',
          format: 'bytes',
          precision: 1,
          aggregation: {
            type: 'sum',
            window: '1h'
          },
          thresholds: {
            good: 1600,
            needsImprovement: 3200,
            poor: Infinity,
            direction: 'lower-is-better'
          },
          insights: {
            title: 'Data Transfer',
            description: 'Total data transferred affects load time and user costs.',
            recommendations: [
              'Compress text resources',
              'Optimize images',
              'Use modern image formats',
              'Implement resource caching'
            ]
          },
          visualization: {
            type: 'area',
            color: 'info',
            showTrend: true,
            showThresholds: true
          },
          sources: [
            {
              type: 'network',
              path: 'totalBytes',
              required: true
            }
          ]
        },
        cachedRequests: {
          id: 'cachedRequests',
          name: 'Cached Requests',
          description: 'Percentage of requests served from cache.',
          unit: '%',
          format: 'percentage',
          precision: 1,
          aggregation: {
            type: 'ratio',
            numerator: 'cachedRequests',
            denominator: 'totalRequests',
            window: '1h'
          },
          thresholds: {
            good: 80,
            needsImprovement: 50,
            poor: 0,
            direction: 'higher-is-better'
          },
          insights: {
            title: 'Cache Hit Rate',
            description: 'Higher cache hit rates improve performance and reduce server load.',
            recommendations: [
              'Implement proper cache headers',
              'Use service workers',
              'Optimize cache strategies',
              'Version static assets'
            ]
          },
          visualization: {
            type: 'gauge',
            color: 'success',
            showTrend: true,
            showThresholds: true
          },
          sources: [
            {
              type: 'network',
              path: 'cachedRequests',
              required: true
            }
          ]
        }
      },
      relationships: []
    });

    // Usage namespace
    this.registerNamespace({
      id: 'usage',
      name: 'Usage Analytics',
      description: 'User behavior and engagement metrics',
      icon: 'analytics',
      category: 'usage',
      metrics: {
        pageViews: {
          id: 'pageViews',
          name: 'Page Views',
          description: 'Total number of page views in the selected time range.',
          unit: '',
          format: 'number',
          precision: 0,
          aggregation: {
            type: 'count',
            window: '1h'
          },
          thresholds: {
            good: 1000,
            needsImprovement: 500,
            poor: 0,
            direction: 'higher-is-better'
          },
          insights: {
            title: 'Page Engagement',
            description: 'Page views indicate user engagement with your content.',
            recommendations: [
              'Analyze popular content',
              'Improve page navigation',
              'Optimize content structure',
              'Track user journeys'
            ]
          },
          visualization: {
            type: 'line',
            color: 'primary',
            showTrend: true,
            showThresholds: true
          },
          sources: [
            {
              type: 'usage',
              path: 'pageViews',
              required: true
            }
          ]
        },
        sessionDuration: {
          id: 'sessionDuration',
          name: 'Session Duration',
          description: 'Average time users spend on the site per session.',
          unit: 'min',
          format: 'duration',
          precision: 1,
          aggregation: {
            type: 'average',
            window: '1h'
          },
          thresholds: {
            good: 300,
            needsImprovement: 120,
            poor: 60,
            direction: 'higher-is-better'
          },
          insights: {
            title: 'User Engagement Time',
            description: 'Longer sessions typically indicate higher engagement and satisfaction.',
            recommendations: [
              'Improve content quality',
              'Enhance user experience',
              'Add interactive features',
              'Reduce friction points'
            ]
          },
          visualization: {
            type: 'bar',
            color: 'success',
            showTrend: true,
            showThresholds: true
          },
          sources: [
            {
              type: 'usage',
              path: 'sessionDuration',
              required: true
            }
          ]
        },
        bounceRate: {
          id: 'bounceRate',
          name: 'Bounce Rate',
          description: 'Percentage of single-page sessions.',
          unit: '%',
          format: 'percentage',
          precision: 1,
          aggregation: {
            type: 'ratio',
            numerator: 'bounceSessions',
            denominator: 'totalSessions',
            window: '1h'
          },
          thresholds: {
            good: 40,
            needsImprovement: 60,
            poor: 80,
            direction: 'lower-is-better'
          },
          insights: {
            title: 'Bounce Rate',
            description: 'Lower bounce rates indicate users find relevant content and explore further.',
            recommendations: [
              'Improve page load speed',
              'Enhance content relevance',
              'Optimize navigation',
              'Add related content suggestions'
            ]
          },
          visualization: {
            type: 'gauge',
            color: 'warning',
            showTrend: true,
            showThresholds: true
          },
          sources: [
            {
              type: 'usage',
              path: 'bounceRate',
              required: true
            }
          ]
        }
      },
      relationships: []
    });

    // Establish relationships between metrics
    this.establishRelationships();
  }

  /**
   * Establish relationships between metrics
   */
  private establishRelationships(): void {
    // Performance metrics relationships
    this.addRelationship('lcp', ['fcp', 'ttfb', 'requests', 'transferSize']);
    this.addRelationship('inp', ['tbt', 'requests']);
    this.addRelationship('cls', ['requests']);
    this.addRelationship('fcp', ['ttfb']);
    
    // Network metrics relationships
    this.addRelationship('requests', ['transferSize', 'cachedRequests']);
    this.addRelationship('transferSize', ['requests', 'ttfb']);
    
    // Usage metrics relationships
    this.addRelationship('pageViews', ['sessionDuration', 'bounceRate']);
    this.addRelationship('sessionDuration', ['bounceRate']);
  }

  /**
   * Add relationship between metrics
   */
  private addRelationship(metricId: string, relatedIds: string[]): void {
    this.relationships.set(metricId, relatedIds);
  }

  /**
   * Get all registered namespaces
   */
  getAllNamespaces(): MetricNamespace[] {
    return Array.from(this.namespaces.values());
  }

  /**
   * Get all registered metrics
   */
  getAllMetrics(): MetricDefinition[] {
    return Array.from(this.metrics.values());
  }

  /**
   * Get metrics for visualization
   */
  getMetricsForVisualization(type: string): MetricDefinition[] {
    return Array.from(this.metrics.values()).filter(
      metric => metric.visualization.type === type
    );
  }

  /**
   * Validate metric configuration
   */
  validateMetric(metric: MetricDefinition): boolean {
    const required = ['id', 'name', 'description', 'unit', 'format', 'aggregation', 'thresholds', 'visualization'];
    return required.every(field => metric[field as keyof MetricDefinition] !== undefined);
  }

  /**
   * Export registry configuration
   */
  export(): Record<string, any> {
    return {
      namespaces: Object.fromEntries(this.namespaces),
      metrics: Object.fromEntries(this.metrics),
      relationships: Object.fromEntries(this.relationships)
    };
  }
}

// Singleton instance
export const metricRegistry = new MetricRegistry();
