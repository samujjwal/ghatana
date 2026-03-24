/**
 * Usage Analytics Service
 * Tracks user interactions and provides insights for canvas usage patterns
 */

/**
 *
 */
export interface AnalyticsEvent {
  id: string;
  type: 'canvas_action' | 'user_interaction' | 'performance' | 'error';
  category: string;
  action: string;
  label?: string;
  value?: number;
  metadata?: Record<string, unknown>;
  userId?: string;
  sessionId: string;
  timestamp: number;
  canvasId?: string;
}

/**
 *
 */
export interface UsageMetrics {
  totalCanvases: number;
  totalElements: number;
  totalConnections: number;
  averageElementsPerCanvas: number;
  mostUsedElementTypes: Array<{ type: string; count: number; percentage: number }>;
  canvasInteractionFrequency: Array<{ canvasId: string; interactions: number; lastAccessed: number }>;
  userActivityPattern: Array<{ hour: number; interactions: number }>;
  featureUsage: Record<string, number>;
  performanceMetrics: {
    averageFPS: number;
    averageRenderTime: number;
    memoryUsage: number;
  };
}

/**
 *
 */
export interface AnalyticsInsight {
  id: string;
  type: 'suggestion' | 'warning' | 'optimization' | 'pattern';
  title: string;
  description: string;
  impact: 'low' | 'medium' | 'high';
  actionable: boolean;
  actions?: Array<{ label: string; action: string }>;
  data?: unknown;
}

/**
 *
 */
export class UsageAnalyticsService {
  private events: AnalyticsEvent[] = [];
  private sessionId: string;
  private isEnabled: boolean = true;
  private listeners: Set<(event: AnalyticsEvent) => void> = new Set();
  private metricsCache: UsageMetrics | null = null;
  private cacheExpiry: number = 0;

  /**
   *
   */
  constructor() {
    this.sessionId = this.generateSessionId();
    this.initializeTracking();
  }

  /**
   * Track a canvas action
   */
  trackCanvasAction(action: string, canvasId: string, metadata?: Record<string, unknown>): void {
    if (!this.isEnabled) return;

    this.trackEvent({
      type: 'canvas_action',
      category: 'canvas',
      action,
      canvasId,
      metadata,
    });
  }

  /**
   * Track user interaction
   */
  trackUserInteraction(category: string, action: string, label?: string, value?: number): void {
    if (!this.isEnabled) return;

    this.trackEvent({
      type: 'user_interaction',
      category,
      action,
      label,
      value,
    });
  }

  /**
   * Track performance metrics
   */
  trackPerformance(metrics: { fps: number; renderTime: number; memoryUsage: number; elementCount: number }): void {
    if (!this.isEnabled) return;

    this.trackEvent({
      type: 'performance',
      category: 'performance',
      action: 'metrics_collected',
      metadata: metrics,
    });
  }

  /**
   * Track error occurrence
   */
  trackError(error: Error, context?: Record<string, unknown>): void {
    if (!this.isEnabled) return;

    this.trackEvent({
      type: 'error',
      category: 'error',
      action: 'error_occurred',
      label: error.name,
      metadata: {
        message: error.message,
        stack: error.stack,
        ...context,
      },
    });
  }

  /**
   * Generic event tracking
   */
  private trackEvent(eventData: Partial<AnalyticsEvent>): void {
    const event: AnalyticsEvent = {
      id: this.generateEventId(),
      sessionId: this.sessionId,
      timestamp: Date.now(),
      userId: this.getUserId(),
      ...eventData,
    } as AnalyticsEvent;

    this.events.push(event);
    this.notifyListeners(event);

    // Keep only last 1000 events to prevent memory issues
    if (this.events.length > 1000) {
      this.events = this.events.slice(-1000);
    }

    // Invalidate metrics cache
    this.metricsCache = null;
  }

  /**
   * Get usage metrics with caching
   */
  getUsageMetrics(): UsageMetrics {
    const now = Date.now();
    if (this.metricsCache && now < this.cacheExpiry) {
      return this.metricsCache;
    }

    this.metricsCache = this.calculateMetrics();
    this.cacheExpiry = now + 60000; // Cache for 1 minute
    return this.metricsCache;
  }

  /**
   * Generate actionable insights from usage data
   */
  generateInsights(): AnalyticsInsight[] {
    const metrics = this.getUsageMetrics();
    const insights: AnalyticsInsight[] = [];

    // Performance insights
    if (metrics.performanceMetrics.averageFPS < 30) {
      insights.push({
        id: 'low-fps-warning',
        type: 'warning',
        title: 'Low Frame Rate Detected',
        description: `Average FPS is ${metrics.performanceMetrics.averageFPS.toFixed(1)}, which may impact user experience.`,
        impact: 'high',
        actionable: true,
        actions: [
          { label: 'Optimize Canvas', action: 'optimize_canvas' },
          { label: 'Reduce Elements', action: 'reduce_elements' }
        ],
      });
    }

    // Canvas complexity insights
    if (metrics.averageElementsPerCanvas > 500) {
      insights.push({
        id: 'complex-canvas-suggestion',
        type: 'suggestion',
        title: 'Consider Canvas Simplification',
        description: `Average ${metrics.averageElementsPerCanvas} elements per canvas. Consider breaking down complex canvases.`,
        impact: 'medium',
        actionable: true,
        actions: [
          { label: 'Create Sub-Canvases', action: 'create_sub_canvases' },
          { label: 'Use Portal Elements', action: 'use_portals' }
        ],
      });
    }

    // Feature usage insights
    const underusedFeatures = Object.entries(metrics.featureUsage)
      .filter(([, count]) => count < 5)
      .map(([feature]) => feature);

    if (underusedFeatures.length > 0) {
      insights.push({
        id: 'underused-features',
        type: 'pattern',
        title: 'Discover Underused Features',
        description: `You haven't explored: ${underusedFeatures.slice(0, 3).join(', ')}`,
        impact: 'low',
        actionable: true,
        actions: [
          { label: 'Feature Tour', action: 'start_feature_tour' },
          { label: 'Help Center', action: 'open_help' }
        ],
        data: { features: underusedFeatures },
      });
    }

    // Activity pattern insights
    const peakHour = metrics.userActivityPattern.reduce((peak, current) => 
      current.interactions > peak.interactions ? current : peak
    );

    insights.push({
      id: 'activity-pattern',
      type: 'pattern',
      title: 'Peak Activity Time',
      description: `You're most productive at ${peakHour.hour}:00 with ${peakHour.interactions} interactions.`,
      impact: 'low',
      actionable: false,
      data: { peakHour: peakHour.hour },
    });

    return insights;
  }

  /**
   * Get events for a specific time range
   */
  getEventsByTimeRange(startTime: number, endTime: number): AnalyticsEvent[] {
    return this.events.filter(event => 
      event.timestamp >= startTime && event.timestamp <= endTime
    );
  }

  /**
   * Get events by type
   */
  getEventsByType(type: AnalyticsEvent['type']): AnalyticsEvent[] {
    return this.events.filter(event => event.type === type);
  }

  /**
   * Get canvas-specific events
   */
  getCanvasEvents(canvasId: string): AnalyticsEvent[] {
    return this.events.filter(event => event.canvasId === canvasId);
  }

  /**
   * Export analytics data
   */
  exportData(format: 'json' | 'csv' = 'json'): string {
    if (format === 'csv') {
      const headers = ['id', 'type', 'category', 'action', 'label', 'value', 'canvasId', 'timestamp'];
      const rows = this.events.map(event => [
        event.id,
        event.type,
        event.category,
        event.action,
        event.label || '',
        event.value || '',
        event.canvasId || '',
        new Date(event.timestamp).toISOString(),
      ]);
      
      return [headers, ...rows].map(row => row.join(',')).join('\n');
    }

    return JSON.stringify({
      sessionId: this.sessionId,
      exportTime: new Date().toISOString(),
      events: this.events,
      metrics: this.getUsageMetrics(),
      insights: this.generateInsights(),
    }, null, 2);
  }

  /**
   * Add event listener
   */
  addEventListener(listener: (event: AnalyticsEvent) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  /**
   * Enable/disable analytics
   */
  setEnabled(enabled: boolean): void {
    this.isEnabled = enabled;
  }

  /**
   * Clear all analytics data
   */
  clearData(): void {
    this.events = [];
    this.metricsCache = null;
    this.cacheExpiry = 0;
  }

  /**
   *
   */
  private calculateMetrics(): UsageMetrics {
    const canvasEvents = this.events.filter(e => e.type === 'canvas_action');
    const canvases = new Set(canvasEvents.map(e => e.canvasId).filter(Boolean));
    
    // Element type tracking
    const elementTypeCount = new Map<string, number>();
    let totalElements = 0;
    let totalConnections = 0;

    canvasEvents.forEach(event => {
      if (event.action === 'element_added' && event.metadata?.elementType) {
        const type = event.metadata.elementType;
        elementTypeCount.set(type, (elementTypeCount.get(type) || 0) + 1);
        totalElements++;
      } else if (event.action === 'connection_added') {
        totalConnections++;
      }
    });

    // Feature usage tracking
    const featureUsage: Record<string, number> = {};
    this.events.forEach(event => {
      const feature = `${event.category}.${event.action}`;
      featureUsage[feature] = (featureUsage[feature] || 0) + 1;
    });

    // Activity pattern (by hour)
    const hourlyActivity = Array.from({ length: 24 }, (_, hour) => ({ hour, interactions: 0 }));
    this.events.forEach(event => {
      const hour = new Date(event.timestamp).getHours();
      hourlyActivity[hour].interactions++;
    });

    // Performance metrics
    const perfEvents = this.events.filter(e => e.type === 'performance' && e.metadata);
    const avgPerf = perfEvents.reduce(
      (acc, event) => {
        const metadata = event.metadata!;
        return {
          fps: acc.fps + (metadata.fps || 0),
          renderTime: acc.renderTime + (metadata.renderTime || 0),
          memoryUsage: acc.memoryUsage + (metadata.memoryUsage || 0),
          count: acc.count + 1,
        };
      },
      { fps: 0, renderTime: 0, memoryUsage: 0, count: 0 }
    );

    const performanceMetrics = avgPerf.count > 0 ? {
      averageFPS: avgPerf.fps / avgPerf.count,
      averageRenderTime: avgPerf.renderTime / avgPerf.count,
      memoryUsage: avgPerf.memoryUsage / avgPerf.count,
    } : {
      averageFPS: 0,
      averageRenderTime: 0,
      memoryUsage: 0,
    };

    return {
      totalCanvases: canvases.size,
      totalElements,
      totalConnections,
      averageElementsPerCanvas: canvases.size > 0 ? totalElements / canvases.size : 0,
      mostUsedElementTypes: Array.from(elementTypeCount.entries())
        .map(([type, count]) => ({
          type,
          count,
          percentage: (count / totalElements) * 100,
        }))
        .sort((a, b) => b.count - a.count)
        .slice(0, 10),
      canvasInteractionFrequency: Array.from(canvases).map(canvasId => {
        const canvasEvents = this.events.filter(e => e.canvasId === canvasId);
        return {
          canvasId: canvasId!,
          interactions: canvasEvents.length,
          lastAccessed: Math.max(...canvasEvents.map(e => e.timestamp)),
        };
      }).sort((a, b) => b.interactions - a.interactions),
      userActivityPattern: hourlyActivity,
      featureUsage,
      performanceMetrics,
    };
  }

  /**
   *
   */
  private initializeTracking(): void {
    // Track page visibility changes
    document.addEventListener('visibilitychange', () => {
      this.trackUserInteraction('system', document.hidden ? 'page_hidden' : 'page_visible');
    });

    // Track errors
    window.addEventListener('error', (event) => {
      this.trackError(event.error, { filename: event.filename, lineno: event.lineno });
    });

    // Track unhandled promise rejections
    window.addEventListener('unhandledrejection', (event) => {
      this.trackError(new Error(event.reason), { type: 'unhandled_promise' });
    });
  }

  /**
   *
   */
  private generateSessionId(): string {
    return `session_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }

  /**
   *
   */
  private generateEventId(): string {
    return `event_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }

  /**
   *
   */
  private getUserId(): string {
    // In a real app, this would come from authentication
    return localStorage.getItem('userId') || 'anonymous';
  }

  /**
   *
   */
  private notifyListeners(event: AnalyticsEvent): void {
    this.listeners.forEach(listener => {
      try {
        listener(event);
      } catch (error) {
        console.error('Analytics listener error:', error);
      }
    });
  }
}

// Singleton instance
export const usageAnalytics = new UsageAnalyticsService();