import { z } from 'zod';

/**
 * Deployment health check
 */
export interface DeploymentHealthCheck {
  checkId: string;
  checkName: string;
  checkType: HealthCheckType;
  config: HealthCheckConfig;
  timeoutMs: number;
  retries: number;
}

/**
 * Health check type
 */
export type HealthCheckType = 'http' | 'tcp' | 'command' | 'custom';

/**
 * Health check configuration
 */
export interface HealthCheckConfig {
  http?: HttpHealthCheckConfig;
  tcp?: TcpHealthCheckConfig;
  command?: CommandHealthCheckConfig;
  custom?: Record<string, unknown>;
}

/**
 * HTTP health check configuration
 */
export interface HttpHealthCheckConfig {
  url: string;
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  headers?: Record<string, string>;
  expectedStatus?: number;
  expectedBody?: string;
}

/**
 * TCP health check configuration
 */
export interface TcpHealthCheckConfig {
  host: string;
  port: number;
  timeoutMs?: number;
}

/**
 * Command health check configuration
 */
export interface CommandHealthCheckConfig {
  command: string[];
  workingDirectory?: string;
  env?: Record<string, string>;
}

/**
 * Zod schema for deployment health check validation
 */
export const DeploymentHealthCheckSchema = z.object({
  checkId: z.string().min(1),
  checkName: z.string().min(1),
  checkType: z.enum(['http', 'tcp', 'command', 'custom']),
  config: z.object({
    http: z.object({
      url: z.string().url(),
      method: z.enum(['GET', 'POST', 'PUT', 'DELETE']).optional(),
      headers: z.record(z.string(), z.string()).optional(),
      expectedStatus: z.number().int().optional(),
      expectedBody: z.string().optional(),
    }).optional(),
    tcp: z.object({
      host: z.string(),
      port: z.number().int().positive(),
      timeoutMs: z.number().int().nonnegative().optional(),
    }).optional(),
    command: z.object({
      command: z.array(z.string()),
      workingDirectory: z.string().optional(),
      env: z.record(z.string(), z.string()).optional(),
    }).optional(),
    custom: z.record(z.string(), z.unknown()).optional(),
  }),
  timeoutMs: z.number().int().nonnegative(),
  retries: z.number().int().nonnegative(),
});

export type DeploymentHealthCheckInput = z.infer<typeof DeploymentHealthCheckSchema>;
