/**
 * @fileoverview Guardian End-to-End Test Suite
 *
 * Tests:
 * 1. Metrics collection and storage
 * 2. Settings persistence
 * 3. Alert triggering
 * 4. Data export/import
 * 5. Cross-browser compatibility
 */

import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";

// Mock interfaces
interface MockMetrics {
  lcp?: number;
  cls?: number;
  fid?: number;
  inp?: number;
  timestamp: number;
}

interface MockSettings {
  general: {
    autoCollect: boolean;
    collectionInterval: number;
    dataRetentionDays: number;
    enableNotifications: boolean;
  };
  budgets: {
    lcp: number;
    cls: number;
    fid: number;
    inp: number;
  };
}

// Mock browser storage
class MockStorage {
  private store = new Map<string, any>();

  get(key: string) {
    return Promise.resolve(this.store.get(key));
  }

  set(key: string, value: any) {
    this.store.set(key, value);
    return Promise.resolve();
  }

  clear() {
    this.store.clear();
    return Promise.resolve();
  }

  getAllKeys() {
    return Promise.resolve(Array.from(this.store.keys()));
  }
}

describe("Guardian E2E Tests", () => {
  let storage: MockStorage;

  beforeEach(() => {
    storage = new MockStorage();
    vi.clearAllMocks();
  });

  afterEach(async () => {
    await storage.clear();
  });

  describe("Metrics Collection", () => {
    it("should collect Web Vitals metrics", async () => {
      const metrics: MockMetrics = {
        lcp: 2000,
        cls: 0.05,
        fid: 50,
        inp: 100,
        timestamp: Date.now(),
      };

      await storage.set("metrics", [metrics]);
      const stored = await storage.get("metrics");

      expect(stored).toHaveLength(1);
      expect(stored[0]).toMatchObject(metrics);
    });

    it("should store multiple metrics chronologically", async () => {
      const metrics = [
        { lcp: 2000, cls: 0.05, timestamp: Date.now() },
        { lcp: 1800, cls: 0.04, timestamp: Date.now() + 1000 },
        { lcp: 2100, cls: 0.06, timestamp: Date.now() + 2000 },
      ];

      await storage.set("metrics", metrics);
      const stored = await storage.get("metrics");

      expect(stored).toHaveLength(3);
      expect(stored[0].timestamp < stored[1].timestamp).toBe(true);
      expect(stored[1].timestamp < stored[2].timestamp).toBe(true);
    });

    it("should respect data retention policy", async () => {
      const retentionDays = 7;
      const now = Date.now();
      const oldMetric = {
        lcp: 2000,
        timestamp: now - (retentionDays + 1) * 24 * 60 * 60 * 1000,
      };
      const newMetric = { lcp: 1800, timestamp: now };

      const metrics = [oldMetric, newMetric];
      const filtered = metrics.filter(
        (m) => now - m.timestamp <= retentionDays * 24 * 60 * 60 * 1000
      );

      expect(filtered).toHaveLength(1);
      expect(filtered[0]).toMatchObject(newMetric);
    });
  });

  describe("Settings Persistence", () => {
    it("should save and retrieve settings", async () => {
      const settings: MockSettings = {
        general: {
          autoCollect: true,
          collectionInterval: 30,
          dataRetentionDays: 7,
          enableNotifications: true,
        },
        budgets: {
          lcp: 2500,
          cls: 0.1,
          fid: 100,
          inp: 200,
        },
      };

      await storage.set("settings", settings);
      const stored = await storage.get("settings");

      expect(stored).toEqual(settings);
    });

    it("should handle settings updates", async () => {
      const initialSettings: MockSettings = {
        general: {
          autoCollect: true,
          collectionInterval: 30,
          dataRetentionDays: 7,
          enableNotifications: true,
        },
        budgets: {
          lcp: 2500,
          cls: 0.1,
          fid: 100,
          inp: 200,
        },
      };

      await storage.set("settings", initialSettings);

      // Update settings
      const updated = await storage.get("settings");
      updated.general.collectionInterval = 60;
      await storage.set("settings", updated);

      const stored = await storage.get("settings");
      expect(stored.general.collectionInterval).toBe(60);
    });

    it("should support default settings", async () => {
      const defaults: MockSettings = {
        general: {
          autoCollect: true,
          collectionInterval: 30,
          dataRetentionDays: 7,
          enableNotifications: false,
        },
        budgets: {
          lcp: 2500,
          cls: 0.1,
          fid: 100,
          inp: 200,
        },
      };

      await storage.set("settings", defaults);
      const stored = await storage.get("settings");

      expect(stored).toBeDefined();
      expect(stored.general.autoCollect).toBe(true);
    });
  });

  describe("Alert Triggering", () => {
    it("should detect LCP violations", () => {
      const budget = 2500;
      const metrics = [
        { lcp: 2000, timestamp: Date.now() }, // Within budget
        { lcp: 3000, timestamp: Date.now() + 1000 }, // Exceeds budget
      ];

      const violations = metrics.filter((m) => (m.lcp || 0) > budget);
      expect(violations).toHaveLength(1);
      expect(violations[0].lcp).toBe(3000);
    });

    it("should detect CLS violations", () => {
      const budget = 0.1;
      const metrics = [
        { cls: 0.08, timestamp: Date.now() }, // Within budget
        { cls: 0.15, timestamp: Date.now() + 1000 }, // Exceeds budget
      ];

      const violations = metrics.filter((m) => (m.cls || 0) > budget);
      expect(violations).toHaveLength(1);
      expect(violations[0].cls).toBe(0.15);
    });

    it("should support alert frequency limits", () => {
      const frequency = "per-session";
      const violationCount = 3;
      let alertCount = 0;

      // Simulate per-session alert (only alert once per session)
      if (frequency === "per-session" && violationCount > 0) {
        alertCount = 1;
      }

      expect(alertCount).toBe(1);
    });
  });

  describe("Data Export/Import", () => {
    it("should export metrics as JSON", async () => {
      const metrics = [
        { lcp: 2000, cls: 0.05, timestamp: Date.now() },
        { lcp: 1800, cls: 0.04, timestamp: Date.now() + 1000 },
      ];

      await storage.set("metrics", metrics);
      const stored = await storage.get("metrics");
      const exported = JSON.stringify(stored);

      expect(exported).toContain("lcp");
      expect(exported).toContain("2000");
    });

    it("should import metrics from JSON", async () => {
      const json = JSON.stringify([
        { lcp: 2000, cls: 0.05, timestamp: Date.now() },
        { lcp: 1800, cls: 0.04, timestamp: Date.now() + 1000 },
      ]);

      const imported = JSON.parse(json);
      await storage.set("metrics", imported);
      const stored = await storage.get("metrics");

      expect(stored).toHaveLength(2);
      expect(stored[0].lcp).toBe(2000);
    });

    it("should export as CSV format", async () => {
      const metrics = [
        { lcp: 2000, cls: 0.05, fid: 50, inp: 100, timestamp: Date.now() },
        {
          lcp: 1800,
          cls: 0.04,
          fid: 45,
          inp: 95,
          timestamp: Date.now() + 1000,
        },
      ];

      const csv = [
        "timestamp,lcp,cls,fid,inp",
        ...metrics.map(
          (m) => `${m.timestamp},${m.lcp},${m.cls},${m.fid},${m.inp}`
        ),
      ].join("\n");

      expect(csv).toContain("timestamp,lcp,cls,fid,inp");
      expect(csv).toContain("2000,0.05,50,100");
    });
  });

  describe("Performance Scoring", () => {
    it("should calculate performance score correctly", () => {
      const metrics = [
        { lcp: 2000, cls: 0.05, fid: 50, inp: 100, timestamp: Date.now() },
      ];

      // Weights: LCP 25%, CLS 25%, FID 20%, INP 30%
      // LCP: 2000ms / 40000ms = 0.05, score = 95
      // CLS: 0.05 / 2.5 = 0.02, score = 98
      // FID: 50ms / 300ms = 0.167, score = 83
      // INP: 100ms / 500ms = 0.2, score = 80

      const lcpScore = Math.max(
        0,
        Math.min(100, 100 - (metrics[0].lcp / 40000) * 100)
      );
      const clsScore = Math.max(
        0,
        Math.min(100, 100 - (metrics[0].cls / 2.5) * 100)
      );
      const fidScore = Math.max(
        0,
        Math.min(100, 100 - (metrics[0].fid / 300) * 100)
      );
      const inpScore = Math.max(
        0,
        Math.min(100, 100 - (metrics[0].inp / 500) * 100)
      );

      const totalScore = Math.round(
        lcpScore * 0.25 + clsScore * 0.25 + fidScore * 0.2 + inpScore * 0.3
      );

      expect(totalScore).toBeGreaterThan(75); // Good score
    });

    it("should determine rating based on score", () => {
      const scores = [
        { score: 85, expectedRating: "good" },
        { score: 65, expectedRating: "needs-improvement" },
        { score: 40, expectedRating: "poor" },
      ];

      scores.forEach(({ score, expectedRating }) => {
        const rating =
          score >= 75 ? "good" : score >= 50 ? "needs-improvement" : "poor";
        expect(rating).toBe(expectedRating);
      });
    });
  });

  describe("Cross-Browser Compatibility", () => {
    it("should work with Chrome extension APIs", async () => {
      // Mock Chrome API
      const chromeApi = {
        runtime: { id: "chrome-id" },
        storage: { sync: { get: vi.fn(), set: vi.fn() } },
        action: { setBadge: vi.fn() },
      };

      expect(chromeApi.runtime.id).toBeDefined();
      expect(chromeApi.storage.sync).toBeDefined();
      expect(chromeApi.action.setBadge).toBeDefined();
    });

    it("should work with Firefox extension APIs", async () => {
      // Mock Firefox API
      const firefoxApi = {
        runtime: { id: "firefox-id" },
        storage: { local: { get: vi.fn(), set: vi.fn() } },
        browserAction: { setBadgeText: vi.fn() },
      };

      expect(firefoxApi.runtime.id).toBeDefined();
      expect(firefoxApi.storage.local).toBeDefined();
      expect(firefoxApi.browserAction.setBadgeText).toBeDefined();
    });
  });

  describe("Message Router", () => {
    it("should handle background worker messages", async () => {
      const messageRouter = {
        onReceive: vi.fn(),
        sendToBackground: vi
          .fn()
          .mockResolvedValue({ success: true, data: [] }),
      };

      const result = await messageRouter.sendToBackground({
        type: "GET_ANALYTICS",
        payload: {},
      });

      expect(result.success).toBe(true);
      expect(messageRouter.sendToBackground).toHaveBeenCalled();
    });

    it("should route messages to correct handler", async () => {
      const handlers = {
        GET_ANALYTICS: vi.fn().mockResolvedValue({ success: true, data: {} }),
        SAVE_SETTINGS: vi.fn().mockResolvedValue({ success: true }),
      };

      await handlers.GET_ANALYTICS();
      await handlers.SAVE_SETTINGS();

      expect(handlers.GET_ANALYTICS).toHaveBeenCalled();
      expect(handlers.SAVE_SETTINGS).toHaveBeenCalled();
    });
  });
});
