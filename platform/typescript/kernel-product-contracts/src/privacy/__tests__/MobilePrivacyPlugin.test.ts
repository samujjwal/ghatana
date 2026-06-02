import { describe, expect, it, vi } from "vitest";
import {
  MobilePrivacyClearError,
  PhiCacheValidationError,
  clearKernelMobilePrivacyState,
  stripRestrictedFields,
  validatePhiCacheConfig,
  isEntryExpired,
  isEntrySessionBound,
  type MobilePrivacyCacheClearer,
  type PhiCacheEntry,
  type PhiCacheEntryConfig,
} from "../MobilePrivacyPlugin.js";

describe("MobilePrivacyPlugin", () => {
  describe("clearKernelMobilePrivacyState", () => {
    it("clears every registered cache", async () => {
      const encryptedPhi = vi.fn<() => Promise<void>>().mockResolvedValue(undefined);
      const offline = vi.fn<() => Promise<void>>().mockResolvedValue(undefined);

      await clearKernelMobilePrivacyState("logout", [
        { cacheName: "encrypted-phi", clear: encryptedPhi },
        { cacheName: "dashboard-offline", clear: offline },
      ]);

      expect(encryptedPhi).toHaveBeenCalledTimes(1);
      expect(offline).toHaveBeenCalledTimes(1);
    });

    it("attempts every clearer and reports all failures", async () => {
      const reporter = { debug: vi.fn(), warn: vi.fn() };
      const clearers: readonly MobilePrivacyCacheClearer[] = [
        { cacheName: "encrypted-phi", clear: vi.fn<() => Promise<void>>().mockRejectedValue(new Error("failed")) },
        { cacheName: "dashboard-offline", clear: vi.fn<() => Promise<void>>().mockResolvedValue(undefined) },
        { cacheName: "documents-offline", clear: vi.fn<() => Promise<void>>().mockRejectedValue(new Error("failed")) },
      ];

      await expect(clearKernelMobilePrivacyState("session-clear", clearers, reporter)).rejects.toMatchObject({
        reason: "session-clear",
        failedCaches: ["encrypted-phi", "documents-offline"],
      } satisfies Partial<MobilePrivacyClearError>);

      expect(reporter.warn).toHaveBeenCalledTimes(1);
    });
  });

  describe("stripRestrictedFields (MOB-01)", () => {
    it("strips fields not in allowed list", () => {
      const data = {
        patientId: "p1",
        name: "John Doe",
        ssn: "123-45-6789",
        diagnosis: "Condition",
      };
      const allowedFields = ["patientId", "name"] as const;

      const result = stripRestrictedFields(data, allowedFields);

      expect(result).toEqual({
        patientId: "p1",
        name: "John Doe",
      });
      expect(result).not.toHaveProperty("ssn");
      expect(result).not.toHaveProperty("diagnosis");
    });

    it("returns empty object when no fields match", () => {
      const data = { patientId: "p1", name: "John" };
      const allowedFields = ["diagnosis"] as const;

      const result = stripRestrictedFields(data, allowedFields);

      expect(result).toEqual({});
    });

    it("preserves all fields when all are allowed", () => {
      const data = { patientId: "p1", name: "John" };
      const allowedFields = ["patientId", "name"] as const;

      const result = stripRestrictedFields(data, allowedFields);

      expect(result).toEqual(data);
    });
  });

  describe("validatePhiCacheConfig (MOB-01)", () => {
    it("accepts valid configuration", () => {
      const config: PhiCacheEntryConfig = {
        ttl: 3600,
        sessionId: "session-123",
        allowedFields: ["patientId", "name"],
        isSensitive: true,
      };

      expect(() => validatePhiCacheConfig(config)).not.toThrow();
    });

    it("rejects zero TTL", () => {
      const config: PhiCacheEntryConfig = {
        ttl: 0,
        sessionId: "session-123",
        allowedFields: ["patientId"],
        isSensitive: false,
      };

      expect(() => validatePhiCacheConfig(config)).toThrow("TTL must be positive");
    });

    it("rejects negative TTL", () => {
      const config: PhiCacheEntryConfig = {
        ttl: -100,
        sessionId: "session-123",
        allowedFields: ["patientId"],
        isSensitive: false,
      };

      expect(() => validatePhiCacheConfig(config)).toThrow("TTL must be positive");
    });

    it("rejects TTL exceeding 24 hours", () => {
      const config: PhiCacheEntryConfig = {
        ttl: 86401,
        sessionId: "session-123",
        allowedFields: ["patientId"],
        isSensitive: false,
      };

      expect(() => validatePhiCacheConfig(config)).toThrow("TTL must not exceed 24 hours");
    });

    it("rejects empty session ID", () => {
      const config: PhiCacheEntryConfig = {
        ttl: 3600,
        sessionId: "",
        allowedFields: ["patientId"],
        isSensitive: false,
      };

      expect(() => validatePhiCacheConfig(config)).toThrow("Session ID is required");
    });

    it("rejects empty allowed fields", () => {
      const config: PhiCacheEntryConfig = {
        ttl: 3600,
        sessionId: "session-123",
        allowedFields: [],
        isSensitive: false,
      };

      expect(() => validatePhiCacheConfig(config)).toThrow("At least one allowed field must be specified");
    });
  });

  describe("isEntryExpired (MOB-01)", () => {
    it("returns true for expired entry", () => {
      const entry: PhiCacheEntry = {
        data: { patientId: "p1" },
        sessionId: "session-123",
        createdAt: Date.now() - 10000,
        expiresAt: Date.now() - 1000,
        isSensitive: false,
      };

      expect(isEntryExpired(entry)).toBe(true);
    });

    it("returns false for valid entry", () => {
      const entry: PhiCacheEntry = {
        data: { patientId: "p1" },
        sessionId: "session-123",
        createdAt: Date.now() - 1000,
        expiresAt: Date.now() + 3600000,
        isSensitive: false,
      };

      expect(isEntryExpired(entry)).toBe(false);
    });
  });

  describe("isEntrySessionBound (MOB-01)", () => {
    it("returns true for matching session", () => {
      const entry: PhiCacheEntry = {
        data: { patientId: "p1" },
        sessionId: "session-123",
        createdAt: Date.now(),
        expiresAt: Date.now() + 3600000,
        isSensitive: false,
      };

      expect(isEntrySessionBound(entry, "session-123")).toBe(true);
    });

    it("returns false for different session", () => {
      const entry: PhiCacheEntry = {
        data: { patientId: "p1" },
        sessionId: "session-123",
        createdAt: Date.now(),
        expiresAt: Date.now() + 3600000,
        isSensitive: false,
      };

      expect(isEntrySessionBound(entry, "session-456")).toBe(false);
    });
  });
});
