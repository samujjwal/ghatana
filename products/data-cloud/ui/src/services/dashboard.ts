/**
 * Dashboard Service Interface
 * 
 * @doc.type interface
 * @doc.purpose Dashboard data fetching and widget management
 * @doc.layer ui
 * @doc.pattern Service
 */
export interface DashboardService {
  /** Fetch dashboard summary for tenant */
  getDashboardSummary(tenantId: string): Promise<DashboardSummary>;
  
  /** Fetch quick action statistics */
  getQuickActions(tenantId: string): Promise<QuickAction[]>;
  
  /** Fetch recent activity feed */
  getRecentActivity(tenantId: string, limit: number): Promise<ActivityItem[]>;
  
  /** Fetch widget data by widget ID */
  getWidgetData(widgetId: string, tenantId: string): Promise<WidgetData>;
  
  /** Update widget configuration */
  updateWidgetConfig(widgetId: string, config: WidgetConfig): Promise<WidgetConfig>;
  
  /** Subscribe to real-time dashboard updates */
  subscribeToUpdates(tenantId: string, callback: (update: DashboardUpdate) => void): () => void;
}

/** Dashboard summary statistics */
export interface DashboardSummary {
  tenantId: string;
  entityCount: number;
  eventCount: number;
  queryCount: number;
  reportCount: number;
  lastUpdated: string;
  alerts: DashboardAlert[];
}

/** Dashboard alert */
export interface DashboardAlert {
  id: string;
  severity: 'info' | 'warning' | 'error' | 'critical';
  title: string;
  message: string;
  timestamp: string;
  acknowledged: boolean;
}

/** Quick action item */
export interface QuickAction {
  id: string;
  label: string;
  icon: string;
  route: string;
  badge?: number;
  enabled: boolean;
}

/** Activity feed item */
export interface ActivityItem {
  id: string;
  type: 'entity_created' | 'entity_updated' | 'query_executed' | 'report_generated' | 'user_action';
  title: string;
  description: string;
  userId: string;
  userName: string;
  timestamp: string;
  metadata?: Record<string, unknown>;
}

/** Widget data structure */
export interface WidgetData {
  widgetId: string;
  type: WidgetType;
  title: string;
  data: unknown;
  loading: boolean;
  error?: string;
  lastUpdated: string;
}

/** Widget types */
export type WidgetType = 
  | 'entity-count'
  | 'event-timeline'
  | 'query-stats'
  | 'recent-activity'
  | 'system-health'
  | 'custom';

/** Widget configuration */
export interface WidgetConfig {
  widgetId: string;
  type: WidgetType;
  title: string;
  position: { x: number; y: number; w: number; h: number };
  settings: Record<string, unknown>;
  refreshInterval?: number;
}

/** Real-time dashboard update */
export interface DashboardUpdate {
  type: 'alert' | 'widget_update' | 'activity';
  payload: unknown;
  timestamp: string;
}
