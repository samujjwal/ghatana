/**
 * @tutorputor/api-client — Auth routes
 *
 * Typed wrappers for `/api/v1/auth` endpoints.
 *
 * @doc.type module
 * @doc.purpose Auth API route client
 * @doc.layer product
 * @doc.pattern Adapter
 */

import type { BoundApiRequest } from "../client.js";
import type { AuthTokenPair } from "@tutorputor/auth-client/token";

// ---------------------------------------------------------------------------
// Request / response types
// ---------------------------------------------------------------------------

export interface LoginRequest {
  email: string;
  password: string;
  tenantId: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: "Bearer";
  expiresIn: number;
}

export interface CurrentUserResponse {
  id: string;
  email: string;
  displayName: string;
  role: string;
  tenantId: string;
}

export interface RefreshTokenRequest {
  refreshToken: string;
}

// ---------------------------------------------------------------------------
// Route client
// ---------------------------------------------------------------------------

/**
 * Auth API client. Bind to a `BoundApiRequest` created from `createBoundRequest()`.
 */
export class AuthApiClient {
  constructor(private readonly request: BoundApiRequest) {}

  /**
   * POST /api/v1/auth/login
   * Returns token pair on success.
   */
  async login(body: LoginRequest): Promise<AuthTokenPair & { expiresIn: number }> {
    const res = await this.request<LoginResponse>("/api/v1/auth/login", {
      method: "POST",
      body,
    });
    return {
      accessToken: res.accessToken,
      refreshToken: res.refreshToken,
      expiresIn: res.expiresIn,
    };
  }

  /**
   * GET /api/v1/auth/me
   * Returns the currently authenticated user's profile.
   */
  async me(): Promise<CurrentUserResponse> {
    return this.request<CurrentUserResponse>("/api/v1/auth/me");
  }

  /**
   * POST /api/v1/auth/refresh
   * Exchanges a refresh token for a new access token.
   */
  async refresh(refreshToken: string): Promise<AuthTokenPair & { expiresIn: number }> {
    const res = await this.request<LoginResponse>("/api/v1/auth/refresh", {
      method: "POST",
      body: { refreshToken } satisfies RefreshTokenRequest,
    });
    return {
      accessToken: res.accessToken,
      refreshToken: res.refreshToken,
      expiresIn: res.expiresIn,
    };
  }

  /**
   * POST /api/v1/auth/logout
   * Invalidates the current session server-side.
   */
  async logout(): Promise<void> {
    await this.request<void>("/api/v1/auth/logout", { method: "POST" });
  }
}
