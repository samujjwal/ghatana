/**
 * Dashboard Service Implementation
 * 
 * @doc.type class
 * @doc.purpose Concrete implementation of dashboard service with caching
 * @doc.layer ui
 * @doc.pattern Service Implementation
 */

import type { DashboardService, DashboardSummary, QuickAction, ActivityItem, WidgetData, WidgetConfig, DashboardUpdate, WidgetType, DashboardAlert } from './dashboard';

interface CacheEntry<T> {
  data: T;
  timestamp: number;
  ttl: number;
}

export class DashboardServiceImpl implements DashboardService {
  private baseUrl: string;
  private cache: Map<string, CacheEntry<unknown>> = new Map();
  private subscribers: Map<string, ((update: DashboardUpdate) => void)[]> = new Map();
  private updateInterval: ReturnType<typeof setInterval> | null = null;

  constructor(baseUrl = '/api/v1') {
    this.baseUrl = baseUrl;
    this.startUpdatePolling();
  }

  async getDashboardSummary(tenantId: string): Promise<DashboardSummary> {
    const cacheKey = `summary:${tenantId}`;
    const cached = this.getCached<DashboardSummary>(cacheKey);
    if (cached) return cached;

    const response = await fetch(`${this.baseUrl}/dashboard/${tenantId}/summary`);
    if (!response.ok) throw new Error(`Failed to fetch dashboard summary: ${response.status}`);
    
    const data: DashboardSummary = await response.json();
    this.setCache(cacheKey, data, 30000); // 30s cache
    return data;
  }

  async getQuickActions(tenantId: string): Promise<QuickAction[]> {
    const cacheKey = `actions:${tenantId}`;
    const cached = this.getCached<QuickAction[]>(cacheKey);
    if (cached) return cached;

    // Default actions if API fails
    const defaultActions: QuickAction[] = [
      { id: '1', label: 'New Collection', icon: 'plus', route: '/collections/new', enabled: true },
      { id: '2', label: 'Run Query', icon: 'play', route: '/query', enabled: true },
      { id: '3', label: 'Generate Report', icon: 'file-text', route: '/reports/new', enabled: true },
      { id: '4', label: 'Import Data', icon: 'upload', route: '/import', enabled: true }
    ];

    try {
      const response = await fetch(`${this.baseUrl}/dashboard/${tenantId}/actions`);
      if (!response.ok) return defaultActions;
      
      const data: QuickAction[] = await response.json();
      this.setCache(cacheKey, data, 60000); // 1m cache
      return data;
    } catch {
      return defaultActions;
    }
  }

  async getRecentActivity(tenantId: string, limit: number): Promise<ActivityItem[]> {
    const cacheKey = `activity:${tenantId}:${limit}`;
    const cached = this.getCached<ActivityItem[]>(cacheKey);
    if (cached) return cached;

    const response = await fetch(`${this.baseUrl}/dashboard/${tenantId}/activity?limit=${limit}`);
    if (!response.ok) throw new Error(`Failed to fetch activity: ${response.status}`);
    
    const data: ActivityItem[] = await response.json();
    this.setCache(cacheKey, data, 15000); // 15s cache
    return data;
  }

  async getWidgetData(widgetId: string, tenantId: string): Promise<WidgetData> {
    const cacheKey = `widget:${tenantId}:${widgetId}`;
    const cached = this.getCached<WidgetData>(cacheKey);
    if (cached) return cached;

    const response = await fetch(`${this.baseUrl}/dashboard/${tenantId}/widgets/${widgetId}`);
    if (!response.ok) {
      return {
        widgetId,
        type: 'custom' as WidgetType,
        title: 'Error',
        data: null,
        loading: false,
        error: `Failed to load widget: ${response.status}`,
        lastUpdated: new Date().toISOString()
      };
    }
    
    const data: WidgetData = await response.json();
    this.setCache(cacheKey, data, 20000); // 20s cache
    return data;
  }

  async updateWidgetConfig(widgetId: string, config: WidgetConfig): Promise<WidgetConfig> {
    const response = await fetch(`${this.baseUrl}/widgets/${widgetId}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(config)
    });
    
    if (!response.ok) throw new Error(`Failed to update widget: ${response.status}`);
    
    // Clear cache for this widget
    const tenantId = this.extractTenantId(widgetId);
    this.cache.delete(`widget:${tenantId}:${widgetId}`);
    
    return response.json();
  }

  subscribeToUpdates(tenantId: string, callback: (update: DashboardUpdate) => void): () => void {
    if (!this.subscribers.has(tenantId)) {
      this.subscribers.set(tenantId, []);
    }
    
    const callbacks = this.subscribers.get(tenantId)!;
    callbacks.push(callback);
    
    // Return unsubscribe function
    return () => {
      const index = callbacks.indexOf(callback);
      if (index > -1) {
        callbacks.splice(index, 1);
      }
    };
  }

  private startUpdatePolling(): void {
    this.updateInterval = setInterval(() => {
      this.subscribers.forEach((callbacks, tenantId) => {
        this.pollForUpdates(tenantId).then(updates => {
          updates.forEach(update => {
            callbacks.forEach(cb => {
              try { cb(update); } catch { /* ignore */ }
            });
          });
        });
      });
    }, 5000); // Poll every 5 seconds
  }

  private async pollForUpdates(tenantId: string): Promise<DashboardUpdate[]> {
    try {
      const response = await fetch(`${this.baseUrl}/dashboard/${tenantId}/updates`);
      if (!response.ok) return [];
      return response.json();
    } catch {
      return [];
    }
  }

  private getCached<T>(key: string): T | null {
    const entry = this.cache.get(key);
    if (!entry) return null;
    
    if (Date.now() - entry.timestamp > entry.ttl) {
      this.cache.delete(key);
      return null;
    }
    
    return entry.data as T;
  }

  private setCache<T>(key: string, data: T, ttl: number): void {
    this.cache.set(key, { data, timestamp: Date.now(), ttl });
  }

  private extractTenantId(widgetId: string): string {
    // Extract tenant from widget ID (format: tenant-widget-id)
    const parts = widgetId.split('-');
    return parts[0] || 'default';
  }

  dispose(): void {
    if (this.updateInterval) {
      clearInterval(this.updateInterval);
      this.updateInterval = null;
    }
    this.cache.clear();
    this.subscribers.clear();
  }
}
