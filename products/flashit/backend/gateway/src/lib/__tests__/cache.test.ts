/**
 * @fileoverview Cache Service Tests
 * Tests for Redis caching layer with graceful fallback
 */

import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import {
  initCache,
  disconnectCache,
  cacheGet,
  cacheSet,
  cacheDel,
  cacheInvalidateNamespace,
  isCacheHealthy,
  cacheGetOrSet,
} from "../cache";

// Mock Redis client
const mockRedisClient = {
  get: vi.fn(),
  set: vi.fn(),
  del: vi.fn(),
  scan: vi.fn(),
  on: vi.fn(),
  connect: vi.fn(),
  quit: vi.fn(),
};

vi.mock("redis", () => ({
  createClient: vi.fn(() => mockRedisClient),
}));

describe("Cache Service", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    process.env.REDIS_URL = "redis://localhost:6383";
  });

  afterEach(async () => {
    await disconnectCache();
  });

  describe("initCache", () => {
    it("should initialize Redis client successfully", async () => {
      mockRedisClient.connect.mockResolvedValue(undefined);

      await initCache();

      expect(mockRedisClient.on).toHaveBeenCalledWith(
        "error",
        expect.any(Function),
      );
      expect(mockRedisClient.on).toHaveBeenCalledWith(
        "connect",
        expect.any(Function),
      );
      expect(mockRedisClient.on).toHaveBeenCalledWith(
        "reconnecting",
        expect.any(Function),
      );
      expect(mockRedisClient.connect).toHaveBeenCalled();
    });

    it("should handle connection failure gracefully", async () => {
      mockRedisClient.connect.mockRejectedValue(
        new Error("Connection refused"),
      );

      await initCache();

      // Should not throw, but disable caching
      expect(isCacheHealthy()).toBe(false);
    });
  });

  describe("cacheGet", () => {
    it("should return null when Redis is not connected", async () => {
      mockRedisClient.connect.mockRejectedValue(
        new Error("Connection refused"),
      );
      await initCache();

      const result = await cacheGet("user", "123");
      expect(result).toBeNull();
    });

    it("should return cached value when key exists", async () => {
      const mockData = { id: "123", name: "Test User" };
      mockRedisClient.connect.mockResolvedValue(undefined);
      mockRedisClient.get.mockResolvedValue(JSON.stringify(mockData));

      await initCache();
      const result = await cacheGet<{ id: string; name: string }>(
        "user",
        "123",
      );

      expect(result).toEqual(mockData);
      expect(mockRedisClient.get).toHaveBeenCalledWith("flashit:user:123");
    });

    it("should return null when key does not exist", async () => {
      mockRedisClient.connect.mockResolvedValue(undefined);
      mockRedisClient.get.mockResolvedValue(null);

      await initCache();
      const result = await cacheGet("user", "456");

      expect(result).toBeNull();
    });

    it("should handle JSON parse errors gracefully", async () => {
      mockRedisClient.connect.mockResolvedValue(undefined);
      mockRedisClient.get.mockResolvedValue("invalid json");

      await initCache();
      const result = await cacheGet("user", "123");

      expect(result).toBeNull();
    });
  });

  describe("cacheSet", () => {
    it("should not set cache when Redis is disconnected", async () => {
      mockRedisClient.connect.mockRejectedValue(
        new Error("Connection refused"),
      );
      await initCache();

      await cacheSet("user", "123", { name: "Test" }, 300);
      expect(mockRedisClient.set).not.toHaveBeenCalled();
    });

    it("should set cache with TTL", async () => {
      mockRedisClient.connect.mockResolvedValue(undefined);
      mockRedisClient.set.mockResolvedValue("OK");

      await initCache();
      await cacheSet("user", "123", { name: "Test" }, 300);

      expect(mockRedisClient.set).toHaveBeenCalledWith(
        "flashit:user:123",
        JSON.stringify({ name: "Test" }),
        { EX: 300 },
      );
    });

    it("should use default TTL when not specified", async () => {
      mockRedisClient.connect.mockResolvedValue(undefined);
      mockRedisClient.set.mockResolvedValue("OK");

      await initCache();
      await cacheSet("user", "123", { name: "Test" });

      expect(mockRedisClient.set).toHaveBeenCalledWith(
        "flashit:user:123",
        JSON.stringify({ name: "Test" }),
        { EX: 300 },
      );
    });

    it("should handle set errors gracefully", async () => {
      mockRedisClient.connect.mockResolvedValue(undefined);
      mockRedisClient.set.mockRejectedValue(new Error("Redis error"));

      await initCache();
      // Should not throw
      await expect(
        cacheSet("user", "123", { name: "Test" }),
      ).resolves.not.toThrow();
    });
  });

  describe("cacheDel", () => {
    it("should not delete when Redis is disconnected", async () => {
      mockRedisClient.connect.mockRejectedValue(
        new Error("Connection refused"),
      );
      await initCache();

      await cacheDel("user", "123");
      expect(mockRedisClient.del).not.toHaveBeenCalled();
    });

    it("should delete cached key", async () => {
      mockRedisClient.connect.mockResolvedValue(undefined);
      mockRedisClient.del.mockResolvedValue(1);

      await initCache();
      await cacheDel("user", "123");

      expect(mockRedisClient.del).toHaveBeenCalledWith("flashit:user:123");
    });
  });

  describe("cacheInvalidateNamespace", () => {
    it("should not invalidate when Redis is disconnected", async () => {
      mockRedisClient.connect.mockRejectedValue(
        new Error("Connection refused"),
      );
      await initCache();

      await cacheInvalidateNamespace("user");
      expect(mockRedisClient.scan).not.toHaveBeenCalled();
    });

    it("should invalidate keys matching pattern", async () => {
      mockRedisClient.connect.mockResolvedValue(undefined);
      mockRedisClient.scan.mockResolvedValueOnce({
        cursor: 0,
        keys: ["flashit:user:123", "flashit:user:456"],
      });
      mockRedisClient.del.mockResolvedValue(2);

      await initCache();
      await cacheInvalidateNamespace("user");

      expect(mockRedisClient.scan).toHaveBeenCalledWith(0, {
        MATCH: "flashit:user:*",
        COUNT: 100,
      });
      expect(mockRedisClient.del).toHaveBeenCalledWith([
        "flashit:user:123",
        "flashit:user:456",
      ]);
    });

    it("should return 0 when no keys match", async () => {
      mockRedisClient.connect.mockResolvedValue(undefined);
      mockRedisClient.scan.mockResolvedValue({ cursor: 0, keys: [] });

      await initCache();
      await cacheInvalidateNamespace("user");

      expect(mockRedisClient.del).not.toHaveBeenCalled();
    });
  });

  describe("isCacheHealthy", () => {
    it("should return unhealthy when Redis is disconnected", async () => {
      mockRedisClient.connect.mockRejectedValue(
        new Error("Connection refused"),
      );
      await initCache();

      expect(isCacheHealthy()).toBe(false);
    });

    it("should return healthy when connected", async () => {
      mockRedisClient.connect.mockResolvedValue(undefined);

      await initCache();
      expect(isCacheHealthy()).toBe(true);
    });

    it("should return unhealthy when connection fails", async () => {
      mockRedisClient.connect.mockRejectedValue(new Error("Connection failed"));

      await initCache();
      expect(isCacheHealthy()).toBe(false);
    });
  });

  describe("disconnectCache", () => {
    it("should disconnect Redis client", async () => {
      mockRedisClient.connect.mockResolvedValue(undefined);
      mockRedisClient.quit.mockResolvedValue(undefined);

      await initCache();
      await disconnectCache();

      expect(mockRedisClient.quit).toHaveBeenCalled();
      expect(isCacheHealthy()).toBe(false);
    });

    it("should handle disconnect when client is null", async () => {
      mockRedisClient.connect.mockRejectedValue(
        new Error("Connection refused"),
      );
      await initCache();

      // Should not throw
      await expect(disconnectCache()).resolves.not.toThrow();
    });
  });
});
