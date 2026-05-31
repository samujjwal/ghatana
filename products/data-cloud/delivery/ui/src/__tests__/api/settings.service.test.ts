/**
 * Settings Service — unit tests
 *
 * Verifies that:
 * - Each API method calls the correct endpoint with correct tenant headers.
 * - Zod schemas parse valid responses without errors.
 * - HTTP 404/405/501 responses are mapped to runtime boundary errors.
 * - Non-boundary errors (network, 500) are re-thrown as-is.
 *
 * @doc.type test
 * @doc.purpose Settings service API contract and boundary behavior
 * @doc.layer frontend
 */

import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  SETTINGS_API_KEY_CREATE_BOUNDARY_MESSAGE,
  SETTINGS_API_KEY_LIST_BOUNDARY_MESSAGE,
  SETTINGS_API_KEY_REVOKE_BOUNDARY_MESSAGE,
  SETTINGS_NOTIFICATION_PREFS_BOUNDARY_MESSAGE,
  SETTINGS_PREFERENCES_BOUNDARY_MESSAGE,
  SETTINGS_PROFILE_BOUNDARY_MESSAGE,
  SETTINGS_SURFACE_DISABLED_MESSAGE,
} from "../../lib/runtime-boundaries";

const { mockApiClient, mockIsSettingsSurfaceEnabled } = vi.hoisted(() => ({
  mockApiClient: {
    get: vi.fn(),
    post: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
  },
  mockIsSettingsSurfaceEnabled: vi.fn(() => true),
}));

vi.mock("../../lib/api/client", () => ({ apiClient: mockApiClient }));
vi.mock("../../lib/feature-gates", () => ({
  isSettingsSurfaceEnabled: mockIsSettingsSurfaceEnabled,
}));

import { SettingsService, type ApiKey } from "../../api/settings.service";
import SessionBootstrap from "../../lib/auth/session";

// ── Test fixtures ─────────────────────────────────────────────────────────────

const TENANT = "tenant-settings-test";

function makeApiKey(overrides: Partial<ApiKey> = {}): ApiKey {
  return {
    id: "key-001",
    name: "CI Pipeline Key",
    prefix: "dc_live_abc",
    scopes: ["read:collections", "execute:queries"],
    createdAt: "2026-04-01T00:00:00.000Z",
    expiresAt: "2027-04-01T00:00:00.000Z",
    lastUsedAt: "2026-04-24T10:00:00.000Z",
    revokedAt: null,
    status: "active",
    ...overrides,
  };
}

function makeBoundaryError(status: number): {
  status: number;
  message: string;
} {
  return { status, message: "Not Found" };
}

// ── Setup ─────────────────────────────────────────────────────────────────────

describe("SettingsService", () => {
  let service: SettingsService;

  beforeEach(() => {
    vi.clearAllMocks();
    mockIsSettingsSurfaceEnabled.mockReturnValue(true);
    SessionBootstrap.setTenantId(TENANT);
    service = new SettingsService();
  });

  it("fails closed before network calls when settings surface gate is disabled", async () => {
    mockIsSettingsSurfaceEnabled.mockReturnValue(false);

    await expect(service.listApiKeys()).rejects.toThrow(
      SETTINGS_SURFACE_DISABLED_MESSAGE,
    );
    await expect(service.getProfile()).rejects.toThrow(
      SETTINGS_SURFACE_DISABLED_MESSAGE,
    );

    expect(mockApiClient.get).not.toHaveBeenCalled();
    expect(mockApiClient.post).not.toHaveBeenCalled();
    expect(mockApiClient.patch).not.toHaveBeenCalled();
    expect(mockApiClient.delete).not.toHaveBeenCalled();
  });

  // ── API Key lifecycle ─────────────────────────────────────────────────────

  describe("listApiKeys", () => {
    it("calls GET /settings/keys with tenant header and returns parsed key list", async () => {
      const key = makeApiKey();
      mockApiClient.get.mockResolvedValue({
        tenantId: TENANT,
        keys: [key],
        count: 1,
        timestamp: "2026-04-24T12:00:00.000Z",
      });

      const result = await service.listApiKeys();

      expect(mockApiClient.get).toHaveBeenCalledWith("/settings/keys", {
        headers: { "X-Tenant-ID": TENANT },
      });
      expect(result).toHaveLength(1);
      expect(result[0]?.id).toBe("key-001");
      expect(result[0]?.status).toBe("active");
    });

    it("raises a boundary error on 404 (identity backend not deployed)", async () => {
      mockApiClient.get.mockRejectedValue(makeBoundaryError(404));

      await expect(service.listApiKeys()).rejects.toThrow(
        SETTINGS_API_KEY_LIST_BOUNDARY_MESSAGE,
      );
    });

    it("raises a boundary error on 405", async () => {
      mockApiClient.get.mockRejectedValue(makeBoundaryError(405));

      await expect(service.listApiKeys()).rejects.toThrow(
        SETTINGS_API_KEY_LIST_BOUNDARY_MESSAGE,
      );
    });

    it("raises a boundary error on 501", async () => {
      mockApiClient.get.mockRejectedValue(makeBoundaryError(501));

      await expect(service.listApiKeys()).rejects.toThrow(
        SETTINGS_API_KEY_LIST_BOUNDARY_MESSAGE,
      );
    });

    it("re-throws non-boundary errors so they propagate to the query client", async () => {
      const networkError = new Error("Network failure");
      mockApiClient.get.mockRejectedValue(networkError);

      await expect(service.listApiKeys()).rejects.toBe(networkError);
    });
  });

  describe("createApiKey", () => {
    it("calls POST /settings/keys with validated request and returns key + secret", async () => {
      const key = makeApiKey();
      mockApiClient.post.mockResolvedValue({
        key,
        secret: "dc_live_abcdefghij1234567890",
      });

      const result = await service.createApiKey({
        name: "CI Pipeline Key",
        scopes: ["read:collections"],
        expiresInDays: 365,
      });

      expect(mockApiClient.post).toHaveBeenCalledWith(
        "/settings/keys",
        {
          name: "CI Pipeline Key",
          scopes: ["read:collections"],
          expiresInDays: 365,
        },
        { headers: { "X-Tenant-ID": TENANT } },
      );
      expect(result.key.id).toBe("key-001");
      expect(result.secret).toBe("dc_live_abcdefghij1234567890");
    });

    it("raises a boundary error on 404", async () => {
      mockApiClient.post.mockRejectedValue(makeBoundaryError(404));

      await expect(
        service.createApiKey({
          name: "Key",
          scopes: ["read:collections"],
          expiresInDays: null,
        }),
      ).rejects.toThrow(SETTINGS_API_KEY_CREATE_BOUNDARY_MESSAGE);
    });

    it("rejects malformed create requests via Zod before reaching the API", async () => {
      await expect(
        service.createApiKey({
          name: "",
          scopes: [],
          expiresInDays: null,
        }),
      ).rejects.toThrow();

      expect(mockApiClient.post).not.toHaveBeenCalled();
    });
  });

  describe("revokeApiKey", () => {
    it("calls DELETE /settings/keys/:id/revoke and returns the revoked key", async () => {
      const revoked = makeApiKey({
        status: "revoked",
        revokedAt: "2026-04-24T12:00:00.000Z",
      });
      mockApiClient.delete.mockResolvedValue(revoked);

      const result = await service.revokeApiKey("key-001");

      expect(mockApiClient.delete).toHaveBeenCalledWith(
        "/settings/keys/key-001/revoke",
        {
          headers: { "X-Tenant-ID": TENANT },
        },
      );
      expect(result.status).toBe("revoked");
    });

    it("raises a boundary error on 404", async () => {
      mockApiClient.delete.mockRejectedValue(makeBoundaryError(404));

      await expect(service.revokeApiKey("key-001")).rejects.toThrow(
        SETTINGS_API_KEY_REVOKE_BOUNDARY_MESSAGE,
      );
    });
  });

  // ── Profile ───────────────────────────────────────────────────────────────

  describe("getProfile", () => {
    it("calls GET /settings/profile and returns parsed user profile", async () => {
      mockApiClient.get.mockResolvedValue({
        userId: "user-001",
        displayName: "Jane Doe",
        email: "jane@example.com",
        avatarUrl: null,
        createdAt: "2026-01-01T00:00:00.000Z",
        updatedAt: "2026-04-24T10:00:00.000Z",
      });

      const result = await service.getProfile();

      expect(mockApiClient.get).toHaveBeenCalledWith("/settings/profile", {
        headers: { "X-Tenant-ID": TENANT },
      });
      expect(result.userId).toBe("user-001");
      expect(result.email).toBe("jane@example.com");
    });

    it("raises a boundary error on 404", async () => {
      mockApiClient.get.mockRejectedValue(makeBoundaryError(404));

      await expect(service.getProfile()).rejects.toThrow(
        SETTINGS_PROFILE_BOUNDARY_MESSAGE,
      );
    });
  });

  describe("updateProfile", () => {
    it("calls PATCH /settings/profile with validated fields", async () => {
      const updated = {
        userId: "user-001",
        displayName: "Jane Smith",
        email: "jane@example.com",
        avatarUrl: null,
        createdAt: "2026-01-01T00:00:00.000Z",
        updatedAt: "2026-04-24T11:00:00.000Z",
      };
      mockApiClient.patch.mockResolvedValue(updated);

      const result = await service.updateProfile({ displayName: "Jane Smith" });

      expect(mockApiClient.patch).toHaveBeenCalledWith(
        "/settings/profile",
        { displayName: "Jane Smith" },
        { headers: { "X-Tenant-ID": TENANT } },
      );
      expect(result.displayName).toBe("Jane Smith");
    });

    it("raises a boundary error on 501", async () => {
      mockApiClient.patch.mockRejectedValue(makeBoundaryError(501));

      await expect(
        service.updateProfile({ displayName: "Test" }),
      ).rejects.toThrow(SETTINGS_PROFILE_BOUNDARY_MESSAGE);
    });
  });

  // ── Preferences ───────────────────────────────────────────────────────────

  describe("getPreferences", () => {
    it("calls GET /settings/preferences and returns typed preferences", async () => {
      mockApiClient.get.mockResolvedValue({
        userId: "user-001",
        theme: "dark",
        timezone: "UTC",
        dateFormat: "ISO",
        updatedAt: "2026-04-24T10:00:00.000Z",
      });

      const result = await service.getPreferences();

      expect(result.theme).toBe("dark");
      expect(result.dateFormat).toBe("ISO");
    });

    it("raises a boundary error on 404", async () => {
      mockApiClient.get.mockRejectedValue(makeBoundaryError(404));

      await expect(service.getPreferences()).rejects.toThrow(
        SETTINGS_PREFERENCES_BOUNDARY_MESSAGE,
      );
    });
  });

  describe("updatePreferences", () => {
    it("calls PATCH /settings/preferences with validated fields", async () => {
      const updated = {
        userId: "user-001",
        theme: "light",
        timezone: "America/New_York",
        dateFormat: "US",
        updatedAt: "2026-04-24T12:00:00.000Z",
      };
      mockApiClient.patch.mockResolvedValue(updated);

      const result = await service.updatePreferences({
        theme: "light",
        dateFormat: "US",
      });

      expect(mockApiClient.patch).toHaveBeenCalledWith(
        "/settings/preferences",
        { theme: "light", dateFormat: "US" },
        { headers: { "X-Tenant-ID": TENANT } },
      );
      expect(result.theme).toBe("light");
    });
  });

  // ── Notification preferences ──────────────────────────────────────────────

  describe("getNotificationPreferences", () => {
    it("calls GET /settings/notifications and returns typed preferences", async () => {
      mockApiClient.get.mockResolvedValue({
        userId: "user-001",
        channels: { email: true, inApp: true },
        alertSeverityThreshold: "warning",
        digestFrequency: "daily",
        updatedAt: "2026-04-24T10:00:00.000Z",
      });

      const result = await service.getNotificationPreferences();

      expect(result.channels.email).toBe(true);
      expect(result.alertSeverityThreshold).toBe("warning");
      expect(result.digestFrequency).toBe("daily");
    });

    it("raises a boundary error on 404", async () => {
      mockApiClient.get.mockRejectedValue(makeBoundaryError(404));

      await expect(service.getNotificationPreferences()).rejects.toThrow(
        SETTINGS_NOTIFICATION_PREFS_BOUNDARY_MESSAGE,
      );
    });
  });

  describe("updateNotificationPreferences", () => {
    it("calls PATCH /settings/notifications with partial update", async () => {
      const updated = {
        userId: "user-001",
        channels: { email: false, inApp: true },
        alertSeverityThreshold: "critical",
        digestFrequency: "realtime",
        updatedAt: "2026-04-24T13:00:00.000Z",
      };
      mockApiClient.patch.mockResolvedValue(updated);

      const result = await service.updateNotificationPreferences({
        alertSeverityThreshold: "critical",
        digestFrequency: "realtime",
      });

      expect(mockApiClient.patch).toHaveBeenCalledWith(
        "/settings/notifications",
        { alertSeverityThreshold: "critical", digestFrequency: "realtime" },
        { headers: { "X-Tenant-ID": TENANT } },
      );
      expect(result.digestFrequency).toBe("realtime");
    });
  });
});
