/**
 * Product Environment Contract
 * 
 * Defines the contract for product environments and their configuration.
 * Consumed by environment UI components to display environment information.
 * 
 * @doc.type module
 * @doc.purpose Product environment contract for UI components
 * @doc.layer platform
 */

/**
 * Environment type
 */
export type EnvironmentType = 'local' | 'development' | 'staging' | 'production';

/**
 * Environment configuration
 */
export interface ProductEnvironment {
  readonly environmentId: string;
  readonly productId: string;
  readonly name: string;
  readonly type: EnvironmentType;
  readonly config: EnvironmentConfig;
  readonly surfaces: readonly EnvironmentSurface[];
  readonly createdAt: string;
  readonly updatedAt: string;
}

/**
 * Environment configuration
 */
export interface EnvironmentConfig {
  readonly deploymentTarget: string;
  readonly namespace?: string;
  readonly region?: string;
  readonly variables?: readonly EnvironmentVariable[];
}

/**
 * Environment variable
 */
export interface EnvironmentVariable {
  readonly name: string;
  readonly value?: string;
  readonly secret: boolean;
  readonly source?: 'config' | 'secret' | 'runtime';
}

/**
 * Surface-specific environment configuration
 */
export interface EnvironmentSurface {
  readonly surface: string;
  readonly port?: number;
  readonly variables?: readonly EnvironmentVariable[];
  readonly healthCheck?: HealthCheckConfig;
}

/**
 * Health check configuration
 */
export interface HealthCheckConfig {
  readonly type: 'http' | 'tcp' | 'command';
  readonly path?: string;
  readonly port?: number;
  readonly interval: number;
  readonly timeout: number;
  readonly retries?: number;
}
