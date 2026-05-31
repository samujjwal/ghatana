/**
 * Settings Service
 *
 * Typed contract for Data Cloud launcher-backed settings APIs.
 * All methods attempt the backend first and raise a runtime boundary error
 * when the identity/security backend is unavailable (404/405/501).
 *
 * This service is intentionally boundary-first: every method documents the
 * required backend contract so that wiring the real endpoints is a matter of
 * removing the fallback, not redesigning the integration.
 *
 * @doc.type service
 * @doc.purpose Typed settings API client — API key lifecycle + user preferences
 * @doc.layer frontend
 * @doc.pattern Service
 */

import { z } from "zod";
import { apiClient } from "../lib/api/client";
import SessionBootstrap from "../lib/auth/session";
import { isSettingsSurfaceEnabled } from "../lib/feature-gates";
import {
  SETTINGS_API_KEY_CREATE_BOUNDARY_MESSAGE,
  SETTINGS_API_KEY_LIST_BOUNDARY_MESSAGE,
  SETTINGS_API_KEY_REVOKE_BOUNDARY_MESSAGE,
  SETTINGS_NOTIFICATION_PREFS_BOUNDARY_MESSAGE,
  SETTINGS_PREFERENCES_BOUNDARY_MESSAGE,
  SETTINGS_PROFILE_BOUNDARY_MESSAGE,
  SETTINGS_SURFACE_DISABLED_MESSAGE,
  createRuntimeBoundaryError,
} from "../lib/runtime-boundaries";

// ── Schemas ───────────────────────────────────────────────────────────────────

export const ApiKeySchema = z.object({
  id: z.string(),
  name: z.string(),
  prefix: z.string().describe("First N characters of the key, safe to display"),
  scopes: z.array(z.string()),
  createdAt: z.string().datetime(),
  expiresAt: z.string().datetime().nullable(),
  lastUsedAt: z.string().datetime().nullable(),
  revokedAt: z.string().datetime().nullable(),
  status: z.enum(["active", "expired", "revoked"]),
});

export const ApiKeyCreateRequestSchema = z.object({
  name: z.string().min(1).max(100),
  scopes: z.array(z.string()).min(1),
  expiresInDays: z.number().int().positive().nullable(),
});

export const ApiKeyCreateResponseSchema = z.object({
  key: ApiKeySchema,
  /**
   * Full API key value — only returned on creation and never again.
   * The client must display this to the user immediately; it cannot be retrieved later.
   */
  secret: z
    .string()
    .describe("Full key value — only available once at creation time"),
});

export const ApiKeyListEnvelopeSchema = z.object({
  tenantId: z.string(),
  keys: z.array(ApiKeySchema),
  count: z.number().int().nonnegative(),
  timestamp: z.string().datetime(),
});

export const UserProfileSchema = z.object({
  userId: z.string(),
  displayName: z.string(),
  email: z.string().email(),
  avatarUrl: z.string().url().nullable(),
  createdAt: z.string().datetime(),
  updatedAt: z.string().datetime(),
});

export const UserProfileUpdateRequestSchema = z.object({
  displayName: z.string().min(1).max(200).optional(),
  avatarUrl: z.string().url().nullable().optional(),
});

export const UserPreferencesSchema = z.object({
  userId: z.string(),
  theme: z.enum(["light", "dark", "system"]).default("system"),
  timezone: z.string(),
  dateFormat: z.enum(["ISO", "US", "EU"]).default("ISO"),
  defaultView: z.string().optional(),
  updatedAt: z.string().datetime(),
});

export const UserPreferencesUpdateRequestSchema = z.object({
  theme: z.enum(["light", "dark", "system"]).optional(),
  timezone: z.string().optional(),
  dateFormat: z.enum(["ISO", "US", "EU"]).optional(),
  defaultView: z.string().optional(),
});

export const NotificationPreferencesSchema = z.object({
  userId: z.string(),
  channels: z.object({
    email: z.boolean(),
    inApp: z.boolean(),
    slack: z.boolean().optional(),
    webhook: z.boolean().optional(),
  }),
  alertSeverityThreshold: z.enum(["critical", "warning", "info"]),
  digestFrequency: z.enum(["realtime", "hourly", "daily", "weekly"]),
  updatedAt: z.string().datetime(),
});

export const NotificationPreferencesUpdateRequestSchema = z.object({
  channels: z
    .object({
      email: z.boolean().optional(),
      inApp: z.boolean().optional(),
      slack: z.boolean().optional(),
      webhook: z.boolean().optional(),
    })
    .optional(),
  alertSeverityThreshold: z.enum(["critical", "warning", "info"]).optional(),
  digestFrequency: z.enum(["realtime", "hourly", "daily", "weekly"]).optional(),
});

// ── Inferred types ────────────────────────────────────────────────────────────

export type ApiKey = z.infer<typeof ApiKeySchema>;
export type ApiKeyCreateRequest = z.infer<typeof ApiKeyCreateRequestSchema>;
export type ApiKeyCreateResponse = z.infer<typeof ApiKeyCreateResponseSchema>;
export type UserProfile = z.infer<typeof UserProfileSchema>;
export type UserProfileUpdateRequest = z.infer<
  typeof UserProfileUpdateRequestSchema
>;
export type UserPreferences = z.infer<typeof UserPreferencesSchema>;
export type UserPreferencesUpdateRequest = z.infer<
  typeof UserPreferencesUpdateRequestSchema
>;
export type NotificationPreferences = z.infer<
  typeof NotificationPreferencesSchema
>;
export type NotificationPreferencesUpdateRequest = z.infer<
  typeof NotificationPreferencesUpdateRequestSchema
>;

// ── Internal helpers ──────────────────────────────────────────────────────────

function getTenantId(): string {
  return SessionBootstrap.requireTenantId();
}

/**
 * Converts HTTP 404/405/501 into a runtime boundary error, which callers can
 * catch and map to an `UnsupportedSurfaceBoundary`. All other errors are
 * re-thrown as-is so genuine network or validation failures are not swallowed.
 */
function normaliseSettingsApiError(
  error: unknown,
  boundaryMessage: string,
): never {
  if (typeof error === "object" && error !== null && "status" in error) {
    const status = Number((error as { status?: unknown }).status);
    if (status === 404 || status === 405 || status === 501) {
      throw createRuntimeBoundaryError(boundaryMessage);
    }
  }
  throw error instanceof Error
    ? error
    : new Error("Unknown settings API error");
}

function assertSettingsSurfaceEnabled(): void {
  if (!isSettingsSurfaceEnabled()) {
    throw createRuntimeBoundaryError(SETTINGS_SURFACE_DISABLED_MESSAGE);
  }
}

// ── Service class ─────────────────────────────────────────────────────────────

/**
 * SettingsService
 *
 * Canonical boundary-aware client for the launcher settings APIs.
 * When the identity/security backend does not exist, every method raises
 * a runtime boundary error with a human-readable message that the
 * SettingsPage surfaces via `UnsupportedSurfaceBoundary`.
 *
 * Backend contract:
 *   GET    /api/v1/settings/keys            → ApiKeyListEnvelopeSchema
 *   POST   /api/v1/settings/keys            → ApiKeyCreateResponseSchema
 *   DELETE /api/v1/settings/keys/:id/revoke → ApiKeySchema (with status=revoked)
 *   GET    /api/v1/settings/profile         → UserProfileSchema
 *   PATCH  /api/v1/settings/profile         → UserProfileSchema
 *   GET    /api/v1/settings/preferences     → UserPreferencesSchema
 *   PATCH  /api/v1/settings/preferences     → UserPreferencesSchema
 *   GET    /api/v1/settings/notifications   → NotificationPreferencesSchema
 *   PATCH  /api/v1/settings/notifications   → NotificationPreferencesSchema
 */
export class SettingsService {
  // ── API Key lifecycle ───────────────────────────────────────────────────────

  /**
   * List all API keys for the current tenant.
   *
   * GET /api/v1/settings/keys
   */
  async listApiKeys(): Promise<ApiKey[]> {
    assertSettingsSurfaceEnabled();
    const tenantId = getTenantId();
    try {
      const response = await apiClient.get("/settings/keys", {
        headers: { "X-Tenant-ID": tenantId },
      });
      return ApiKeyListEnvelopeSchema.parse(response).keys;
    } catch (error) {
      return normaliseSettingsApiError(
        error,
        SETTINGS_API_KEY_LIST_BOUNDARY_MESSAGE,
      );
    }
  }

  /**
   * Create a new API key for the current tenant.
   * The `secret` in the response is only available once — the caller must
   * display it immediately and cannot retrieve it later.
   *
   * POST /api/v1/settings/keys
   */
  async createApiKey(
    request: ApiKeyCreateRequest,
  ): Promise<ApiKeyCreateResponse> {
    assertSettingsSurfaceEnabled();
    const tenantId = getTenantId();
    const validated = ApiKeyCreateRequestSchema.parse(request);
    try {
      const response = await apiClient.post("/settings/keys", validated, {
        headers: { "X-Tenant-ID": tenantId },
      });
      return ApiKeyCreateResponseSchema.parse(response);
    } catch (error) {
      return normaliseSettingsApiError(
        error,
        SETTINGS_API_KEY_CREATE_BOUNDARY_MESSAGE,
      );
    }
  }

  /**
   * Revoke an API key by ID, permanently invalidating it.
   *
   * DELETE /api/v1/settings/keys/:id/revoke
   */
  async revokeApiKey(keyId: string): Promise<ApiKey> {
    assertSettingsSurfaceEnabled();
    const tenantId = getTenantId();
    try {
      const response = await apiClient.delete(
        `/settings/keys/${keyId}/revoke`,
        {
          headers: { "X-Tenant-ID": tenantId },
        },
      );
      return ApiKeySchema.parse(response);
    } catch (error) {
      return normaliseSettingsApiError(
        error,
        SETTINGS_API_KEY_REVOKE_BOUNDARY_MESSAGE,
      );
    }
  }

  // ── Profile ─────────────────────────────────────────────────────────────────

  /**
   * Get the current user's profile.
   *
   * GET /api/v1/settings/profile
   */
  async getProfile(): Promise<UserProfile> {
    assertSettingsSurfaceEnabled();
    const tenantId = getTenantId();
    try {
      const response = await apiClient.get("/settings/profile", {
        headers: { "X-Tenant-ID": tenantId },
      });
      return UserProfileSchema.parse(response);
    } catch (error) {
      return normaliseSettingsApiError(
        error,
        SETTINGS_PROFILE_BOUNDARY_MESSAGE,
      );
    }
  }

  /**
   * Update the current user's profile fields.
   *
   * PATCH /api/v1/settings/profile
   */
  async updateProfile(request: UserProfileUpdateRequest): Promise<UserProfile> {
    assertSettingsSurfaceEnabled();
    const tenantId = getTenantId();
    const validated = UserProfileUpdateRequestSchema.parse(request);
    try {
      const response = await apiClient.patch("/settings/profile", validated, {
        headers: { "X-Tenant-ID": tenantId },
      });
      return UserProfileSchema.parse(response);
    } catch (error) {
      return normaliseSettingsApiError(
        error,
        SETTINGS_PROFILE_BOUNDARY_MESSAGE,
      );
    }
  }

  // ── Preferences ─────────────────────────────────────────────────────────────

  /**
   * Get the current user's UI preferences.
   *
   * GET /api/v1/settings/preferences
   */
  async getPreferences(): Promise<UserPreferences> {
    assertSettingsSurfaceEnabled();
    const tenantId = getTenantId();
    try {
      const response = await apiClient.get("/settings/preferences", {
        headers: { "X-Tenant-ID": tenantId },
      });
      return UserPreferencesSchema.parse(response);
    } catch (error) {
      return normaliseSettingsApiError(
        error,
        SETTINGS_PREFERENCES_BOUNDARY_MESSAGE,
      );
    }
  }

  /**
   * Update the current user's UI preferences.
   *
   * PATCH /api/v1/settings/preferences
   */
  async updatePreferences(
    request: UserPreferencesUpdateRequest,
  ): Promise<UserPreferences> {
    assertSettingsSurfaceEnabled();
    const tenantId = getTenantId();
    const validated = UserPreferencesUpdateRequestSchema.parse(request);
    try {
      const response = await apiClient.patch(
        "/settings/preferences",
        validated,
        {
          headers: { "X-Tenant-ID": tenantId },
        },
      );
      return UserPreferencesSchema.parse(response);
    } catch (error) {
      return normaliseSettingsApiError(
        error,
        SETTINGS_PREFERENCES_BOUNDARY_MESSAGE,
      );
    }
  }

  // ── Notifications ────────────────────────────────────────────────────────────

  /**
   * Get the current user's notification preferences.
   *
   * GET /api/v1/settings/notifications
   */
  async getNotificationPreferences(): Promise<NotificationPreferences> {
    assertSettingsSurfaceEnabled();
    const tenantId = getTenantId();
    try {
      const response = await apiClient.get("/settings/notifications", {
        headers: { "X-Tenant-ID": tenantId },
      });
      return NotificationPreferencesSchema.parse(response);
    } catch (error) {
      return normaliseSettingsApiError(
        error,
        SETTINGS_NOTIFICATION_PREFS_BOUNDARY_MESSAGE,
      );
    }
  }

  /**
   * Update the current user's notification preferences.
   *
   * PATCH /api/v1/settings/notifications
   */
  async updateNotificationPreferences(
    request: NotificationPreferencesUpdateRequest,
  ): Promise<NotificationPreferences> {
    assertSettingsSurfaceEnabled();
    const tenantId = getTenantId();
    const validated = NotificationPreferencesUpdateRequestSchema.parse(request);
    try {
      const response = await apiClient.patch(
        "/settings/notifications",
        validated,
        {
          headers: { "X-Tenant-ID": tenantId },
        },
      );
      return NotificationPreferencesSchema.parse(response);
    } catch (error) {
      return normaliseSettingsApiError(
        error,
        SETTINGS_NOTIFICATION_PREFS_BOUNDARY_MESSAGE,
      );
    }
  }
}

export const settingsService = new SettingsService();
