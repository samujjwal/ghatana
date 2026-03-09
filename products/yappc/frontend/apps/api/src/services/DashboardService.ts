/**
 * DashboardService - Thin Client for Java Backend Dashboard API
 *
 * Delegates all dashboard loading to the Java backend.
 * The Java backend (DashboardController + DashboardService) handles data management.
 * This avoids duplication of dashboard logic.
 *
 * @see products/yappc/backend/api/.../DashboardController.java
 * @see products/yappc/backend/api/.../DashboardService.java
 */

// Types matching Java Dashboard model
export interface Widget {
  id: string;
  type: string;
  title: string;
  dataBinding: Record<string, unknown>;
}

export interface Action {
  id: string;
  label: string;
  permission: string;
  ui: Record<string, unknown>;
  execution: Record<string, unknown>;
}

export interface Audience {
  levels: number[];
  personas: string[];
}

export interface Layout {
  kind: string;
  columns: number;
  items: unknown[];
}

export interface Dashboard {
  id: string;
  domainId: string;
  title: string;
  layout: Layout;
  widgets: Widget[];
  actions: Action[];
  audience: Audience;
}

export class DashboardService {
  private static instance: DashboardService;
  private javaBackendUrl: string;
  private dashboardCache: Map<string, Dashboard> = new Map();

  private constructor() {
    // Java backend URL (configurable via environment)
    this.javaBackendUrl =
      process.env.JAVA_BACKEND_URL || 'http://localhost:7003';
  }

  public static getInstance(): DashboardService {
    if (!DashboardService.instance) {
      DashboardService.instance = new DashboardService();
    }
    return DashboardService.instance;
  }

  /**
   * Fetch data from Java backend API
   */
  private async fetchFromJava<T>(endpoint: string): Promise<T> {
    try {
      const response = await fetch(`${this.javaBackendUrl}${endpoint}`);
      if (!response.ok) {
        throw new Error(
          `Java backend returned ${response.status}: ${response.statusText}`
        );
      }
      const json = await response.json();
      return json as T;
    } catch (error) {
      console.error(`Failed to fetch ${endpoint} from Java backend:`, error);
      throw error;
    }
  }

  /**
   * Get all dashboards
   */
  public async getDashboards(): Promise<Dashboard[]> {
    const response = await this.fetchFromJava<{ dashboards: Dashboard[] }>(
      '/api/dashboards'
    );
    return response.dashboards || [];
  }

  /**
   * Get dashboards by domain
   */
  public async getDashboardsByDomain(domainId: string): Promise<Dashboard[]> {
    const response = await this.fetchFromJava<{ dashboards: Dashboard[] }>(
      `/api/dashboards/domain/${domainId}`
    );
    return response.dashboards || [];
  }

  /**
   * Get a single dashboard by ID
   */
  public async getDashboard(id: string): Promise<Dashboard | null> {
    try {
      const response = await this.fetchFromJava<{ dashboard: Dashboard }>(
        `/api/dashboards/${id}`
      );
      return response.dashboard || null;
    } catch (error) {
      console.warn(`Dashboard not found: ${id}`);
      return null;
    }
  }
}
