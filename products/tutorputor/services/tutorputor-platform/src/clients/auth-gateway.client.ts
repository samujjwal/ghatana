/**
 * Auth-Gateway Platform Client
 *
 * Optional singleton that validates tokens issued by the Ghatana platform
 * auth-gateway service. Designed for graceful degradation — when
 * AUTH_GATEWAY_URL is absent the client logs a warning and all validate()
 * calls resolve to `{ valid: false }` without throwing.
 *
 * Usage:
 *   import { authGatewayClient } from './auth-gateway.client.js';
 *   const identity = await authGatewayClient.validate(bearerToken);
 *   if (identity.valid) { ... }
 *
 * Environment variables:
 *   AUTH_GATEWAY_URL — base URL, e.g. http://auth-gateway:8080
 */

import { logger as rootLogger } from '../core/observability/metrics.js';

const logger = rootLogger?.child
  ? rootLogger.child({ component: 'AuthGatewayClient' })
  : console;

export interface PlatformIdentity {
  /** Whether the presented token is valid. */
  valid: boolean;
  /** Platform user ID (present when valid === true). */
  userId?: string;
  /** Platform email address (present when valid === true). */
  email?: string;
  /** Human-readable error reason when valid === false. */
  error?: string;
}

const REQUEST_TIMEOUT_MS = 5_000;

class AuthGatewayClient {
  private static instance: AuthGatewayClient;
  private readonly baseUrl: string | undefined;

  private constructor() {
    this.baseUrl = process.env['AUTH_GATEWAY_URL'];
    if (!this.baseUrl) {
      (logger.warn ?? logger.log).call(
        logger,
        'AUTH_GATEWAY_URL not configured — platform token validation is disabled',
      );
    }
  }

  static getInstance(): AuthGatewayClient {
    if (!AuthGatewayClient.instance) {
      AuthGatewayClient.instance = new AuthGatewayClient();
    }
    return AuthGatewayClient.instance;
  }

  /**
   * Validate a bearer token against the platform auth-gateway.
   *
   * @param token - Raw bearer token (without the "Bearer " prefix).
   * @returns Resolved identity; `valid: false` on any error or when unconfigured.
   */
  async validate(token: string): Promise<PlatformIdentity> {
    if (!this.baseUrl) {
      return { valid: false, error: 'Auth-gateway not configured' };
    }
    if (!token) {
      return { valid: false, error: 'No token provided' };
    }

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

    try {
      const res = await fetch(`${this.baseUrl}/auth/validate`, {
        method: 'GET',
        headers: { Authorization: `Bearer ${token}` },
        signal: controller.signal,
      });

      if (res.status === 401) {
        return { valid: false, error: 'Token rejected by auth-gateway' };
      }
      if (!res.ok) {
        (logger.warn ?? logger.log).call(
          logger,
          `Auth-gateway returned HTTP ${res.status}`,
        );
        return { valid: false, error: `Upstream error: HTTP ${res.status}` };
      }

      const body = (await res.json()) as PlatformIdentity;
      return body;
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      (logger.warn ?? logger.log).call(
        logger,
        `Auth-gateway request failed: ${msg}`,
      );
      return { valid: false, error: msg };
    } finally {
      clearTimeout(timeoutId);
    }
  }
}

export const authGatewayClient = AuthGatewayClient.getInstance();
