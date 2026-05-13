/**
 * Product Health Contract
 * 
 * Defines the contract for product health checks and status.
 * Consumed by health UI components to display health information.
 * 
 * @doc.type module
 * @doc.purpose Product health contract for UI components
 * @doc.layer platform
 */

/**
 * Health status
 */
export type HealthStatus = 'healthy' | 'degraded' | 'unhealthy' | 'unknown';

/**
 * Health check result
 */
export interface HealthCheckResult {
  readonly checkId: string;
  readonly productId: string;
  readonly surface: string;
  readonly type: 'http' | 'tcp' | 'command';
  readonly status: HealthStatus;
  readonly message?: string;
  readonly lastChecked: string;
  readonly responseTimeMs?: number;
  readonly details?: HealthCheckDetails;
}

/**
 * Detailed health check information
 */
export interface HealthCheckDetails {
  readonly statusCode?: number;
  readonly headers?: Record<string, string>;
  readonly body?: string;
}

/**
 * Product health summary
 */
export interface ProductHealthSummary {
  readonly productId: string;
  readonly environment: string;
  readonly overallStatus: HealthStatus;
  readonly checks: readonly HealthCheckResult[];
  readonly lastUpdated: string;
}

/**
 * Health check configuration
 */
export interface HealthCheckConfig {
  readonly surface: string;
  readonly type: 'http' | 'tcp' | 'command';
  readonly path?: string;
  readonly port?: number;
  readonly command?: string;
  readonly interval: number;
  readonly timeout: number;
  readonly retries?: number;
  readonly threshold?: number;
}
