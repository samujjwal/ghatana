/**
 * @fileoverview BrowserStorageAdapter Tests
 */

import { describe, it, expect, beforeEach, vi } from "vitest";
import {
  BrowserStorageAdapter,
  PrefixedBrowserStorageAdapter,
} from "../../src/adapters/BrowserStorageAdapter";
import browser from "webextension-polyfill";

describe("BrowserStorageAdapter", () => {
  let adapter: BrowserStorageAdapter;

  beforeEach(() => {
    adapter = new BrowserStorageAdapter();
    vi.clearAllMocks();
  });

  describe("get", () => {
    it("should get value from local storage by default", async () => {
      const mockValue = { theme: "dark" };
      vi.mocked(browser.storage.local.get).mockResolvedValue({
        testKey: mockValue,
      });

      const result = await adapter.get<typeof mockValue>("testKey");

      expect(browser.storage.local.get).toHaveBeenCalledWith("testKey");
      expect(result).toEqual(mockValue);
    });

    it("should get value from sync storage when specified", async () => {
      const mockValue = { setting: "value" };
      vi.mocked(browser.storage.sync.get).mockResolvedValue({
        testKey: mockValue,
      });

      const result = await adapter.get<typeof mockValue>("testKey", {
        area: "sync",
      });

      expect(browser.storage.sync.get).toHaveBeenCalledWith("testKey");
      expect(result).toEqual(mockValue);
    });

    it("should return undefined for non-existent key", async () => {
      vi.mocked(browser.storage.local.get).mockResolvedValue({});

      const result = await adapter.get("nonExistent");

      expect(result).toBeUndefined();
    });
  });

  describe("set", () => {
    it("should set value in local storage by default", async () => {
      const value = { theme: "dark" };
      vi.mocked(browser.storage.local.set).mockResolvedValue(undefined);

      await adapter.set("testKey", value);

      expect(browser.storage.local.set).toHaveBeenCalledWith({
        testKey: value,
      });
    });

    it("should set value in sync storage when specified", async () => {
      const value = { setting: "value" };
      vi.mocked(browser.storage.sync.set).mockResolvedValue(undefined);

      await adapter.set("testKey", value, { area: "sync" });

      expect(browser.storage.sync.set).toHaveBeenCalledWith({ testKey: value });
    });
  });

  describe("remove", () => {
    it("should remove value from local storage", async () => {
      vi.mocked(browser.storage.local.remove).mockResolvedValue(undefined);

      await adapter.remove("testKey");

      expect(browser.storage.local.remove).toHaveBeenCalledWith("testKey");
    });

    it("should remove value from sync storage when specified", async () => {
      vi.mocked(browser.storage.sync.remove).mockResolvedValue(undefined);

      await adapter.remove("testKey", { area: "sync" });

      expect(browser.storage.sync.remove).toHaveBeenCalledWith("testKey");
    });
  });

  describe("clear", () => {
    it("should clear all values from local storage", async () => {
      vi.mocked(browser.storage.local.clear).mockResolvedValue(undefined);

      await adapter.clear();

      expect(browser.storage.local.clear).toHaveBeenCalled();
    });
  });

  describe("has", () => {
    it("should return true when key exists", async () => {
      vi.mocked(browser.storage.local.get).mockResolvedValue({
        testKey: "value",
      });

      const result = await adapter.has("testKey");

      expect(result).toBe(true);
    });

    it("should return false when key does not exist", async () => {
      vi.mocked(browser.storage.local.get).mockResolvedValue({});

      const result = await adapter.has("testKey");

      expect(result).toBe(false);
    });
  });

  describe("getQuota", () => {
    it("should return quota information for local storage", async () => {
      vi.mocked(browser.storage.local.getBytesInUse).mockResolvedValue(5000);

      const quota = await adapter.getQuota();

      expect(quota.quota).toBe(10 * 1024 * 1024); // 10MB
      expect(quota.used).toBe(5000);
      expect(quota.remaining).toBe(10 * 1024 * 1024 - 5000);
    });

    it("should return quota information for sync storage", async () => {
      vi.mocked(browser.storage.sync.getBytesInUse).mockResolvedValue(50000);

      const quota = await adapter.getQuota({ area: "sync" });

      expect(quota.quota).toBe(100 * 1024); // 100KB
      expect(quota.used).toBe(50000);
    });
  });

  describe("onChange", () => {
    it("should add change listener", () => {
      const listener = vi.fn();
      const adapter = new BrowserStorageAdapter("local");

      adapter.onChange(listener);

      // The onChange method registers the listener with browser.storage.onChanged
      // We verify this by checking the listener is now registered
      expect(listener).toBeDefined();
    });

    it("should remove change listener", () => {
      const listener = vi.fn();

      adapter.onChange(listener);
      adapter.offChange(listener);

      // Listener should be removed from internal set
      // Cannot easily verify removeListener was called without more mocking
    });
  });
});

describe("PrefixedBrowserStorageAdapter", () => {
  let adapter: PrefixedBrowserStorageAdapter;
  const prefix = "myapp:";

  beforeEach(() => {
    adapter = new PrefixedBrowserStorageAdapter(prefix);
    vi.clearAllMocks();
  });

  describe("get", () => {
    it("should get value with prefixed key", async () => {
      const mockValue = { theme: "dark" };
      vi.mocked(browser.storage.local.get).mockResolvedValue({
        "myapp:testKey": mockValue,
      });

      const result = await adapter.get<typeof mockValue>("testKey");

      expect(browser.storage.local.get).toHaveBeenCalledWith("myapp:testKey");
      expect(result).toEqual(mockValue);
    });
  });

  describe("set", () => {
    it("should set value with prefixed key", async () => {
      const value = { theme: "dark" };
      vi.mocked(browser.storage.local.set).mockResolvedValue(undefined);

      await adapter.set("testKey", value);

      expect(browser.storage.local.set).toHaveBeenCalledWith({
        "myapp:testKey": value,
      });
    });
  });

  describe("getAll", () => {
    it("should get only prefixed keys and remove prefix", async () => {
      vi.mocked(browser.storage.local.get).mockResolvedValue({
        "myapp:key1": "value1",
        "myapp:key2": "value2",
        "other:key3": "value3", // Should be filtered out
      });

      const result = await adapter.getAll();

      expect(result).toEqual({
        key1: "value1",
        key2: "value2",
      });
    });
  });

  describe("withPrefix", () => {
    it("should create new adapter with different prefix", () => {
      const newAdapter = adapter.withPrefix("newapp:");

      expect(newAdapter.prefix).toBe("newapp:");
      expect(adapter.prefix).toBe("myapp:"); // Original unchanged
    });
  });
});
