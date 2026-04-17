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

type DashboardApiEnvelope<T> = {
  data?: T;
};

async function parseJsonResponse<T>(
  response: Response,
  endpoint: string
): Promise<T> {
  const raw = await response.text();

  if (!raw) {
    throw new Error(`Dashboard API returned an empty response for ${endpoint}`);
  }

  try {
    return JSON.parse(raw) as T;
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    throw new Error(`Dashboard API returned invalid JSON for ${endpoint}: ${detail}`);
  }
}

async function readErrorResponse(
  response: Response,
  endpoint: string
): Promise<string> {
  const raw = await response.text();

  if (!raw) {
    return `Java backend returned ${response.status}: ${response.statusText}`;
  }

  try {
    const payload = JSON.parse(raw) as { message?: unknown; error?: unknown };
    if (typeof payload.message === 'string' && payload.message.length > 0) {
      return payload.message;
    }
    if (typeof payload.error === 'string' && payload.error.length > 0) {
      return payload.error;
    }
  } catch {
    if (raw.trim().length > 0) {
      return raw.trim();
    }
  }

  return `Java backend returned ${response.status}: ${response.statusText} for ${endpoint}`;
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
        throw new Error(await readErrorResponse(response, endpoint));
      }
      const json = await parseJsonResponse<DashboardApiEnvelope<T> | T>(response, endpoint);
      if (typeof json === 'object' && json !== null && 'data' in json && json.data !== undefined) {
        return json.data;
      }
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
