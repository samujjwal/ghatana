/**
 * Ghatana Platform Auth-Gateway HTTP Client
 *
 * <p><b>Purpose</b><br>
 * Validates platform-issued JWT tokens against the Ghatana auth-gateway service.
 * Used when DCMAAR needs to accept calls authenticated by the broader Ghatana
 * platform (e.g. from other microservices or the YAPPC frontend).
 *
 * <p><b>Design</b><br>
 * The client is intentionally optional — when {@code AUTH_GATEWAY_URL} is not
 * configured, all validation calls return an unauthenticated identity so that
 * DCMAAR can continue to run standalone with its own local JWT auth.
 *
 * <p><b>API contract</b><br>
 * {@code GET /auth/validate} with {@code Authorization: Bearer <token>}
 * → {@code { valid: boolean, userId?: string, email?: string, error?: string }}
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const identity = await AuthGatewayClient.getInstance().validate(token);
 * if (identity.valid) {
 *   request.platformUserId = identity.userId;
 * }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Platform auth-gateway token validation client
 * @doc.layer backend
 * @doc.pattern Adapter
 */
import { logger } from '../utils/logger';

const VALIDATE_PATH = '/auth/validate';
const DEFAULT_TIMEOUT_MS = 5_000;

/** Identity returned by the auth-gateway validate endpoint. */
export interface PlatformIdentity {
  valid: boolean;
  userId?: string;
  email?: string;
  /** Roles assigned to this identity by the platform IAM system. */
  roles?: readonly string[];
  error?: string;
}

/** Raw auth-gateway validate response. */
interface AuthGatewayValidateResponse {
  valid?: boolean;
  userId?: string;
  email?: string;
  roles?: string[];
  error?: string;
}

export class AuthGatewayClient {
  private static instance: AuthGatewayClient;

  private readonly baseUrl: string | null;

  private constructor() {
    this.baseUrl = process.env['AUTH_GATEWAY_URL'] ?? null;

    if (this.baseUrl) {
      logger.info('AuthGatewayClient initialised', { baseUrl: this.baseUrl });
    } else {
      logger.warn(
        'AUTH_GATEWAY_URL not configured — platform token validation disabled. ' +
          'DCMAAR will operate in standalone auth mode.',
      );
    }
  }

  /**
   * Returns the singleton instance. Thread-safe via module-level singleton.
   */
  public static getInstance(): AuthGatewayClient {
    if (!AuthGatewayClient.instance) {
      AuthGatewayClient.instance = new AuthGatewayClient();
    }
    return AuthGatewayClient.instance;
  }

  /**
   * Validates a platform-issued Bearer token against the auth-gateway.
   *
   * @param token  raw JWT token string (without the "Bearer " prefix)
   * @returns resolved identity if valid; {@code { valid: false }} when the
   *          gateway is unreachable or the token is rejected
   */
  async validate(token: string): Promise<PlatformIdentity> {
    if (!this.baseUrl) {
      // Gateway not configured — cannot validate platform tokens in this deployment
      return { valid: false, error: 'AUTH_GATEWAY_URL not configured' };
    }

    const url = `${this.baseUrl}${VALIDATE_PATH}`;
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), DEFAULT_TIMEOUT_MS);

    try {
      const response = await fetch(url, {
        method: 'GET',
        headers: {
          Authorization: `Bearer ${token}`,
          Accept: 'application/json',
        },
        signal: controller.signal,
      });

      const body = (await response.json()) as AuthGatewayValidateResponse;

      if (!response.ok || body.valid === false) {
        logger.debug('AuthGateway rejected token', { status: response.status, error: body.error });
        return { valid: false, error: body.error ?? 'Token rejected by auth-gateway' };
      }

      return {
        valid: true,
        userId: body.userId,
        email: body.email,
        roles: body.roles,
      };
    } catch (err) {
      // Network error or timeout — fail open to avoid blocking DCMAAR's own auth path
      logger.warn('AuthGateway validation request failed — treating token as invalid', {
        url,
        error: err instanceof Error ? err.message : String(err),
      });
      return { valid: false, error: 'Auth-gateway unreachable' };
    } finally {
      clearTimeout(timeoutId);
    }
  }

  /**
   * Returns {@code true} when the auth-gateway URL is configured and the service
   * can be used for platform token validation.
   */
  isConfigured(): boolean {
    return this.baseUrl !== null;
  }
}

export const authGatewayClient = AuthGatewayClient.getInstance();
