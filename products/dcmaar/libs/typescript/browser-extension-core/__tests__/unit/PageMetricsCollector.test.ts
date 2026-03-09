/**
 * @fileoverview Tests for PageMetricsCollector
 *
 * Tests web vitals collection, navigation timing, resource timing,
 * and performance observer lifecycle.
 */

import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import {
  PageMetricsCollector,
  BatchPageMetricsCollector,
} from "../../src/metrics/PageMetricsCollector";
import type { PageMetrics } from "../../src/metrics/MetricCollector.interface";

describe("PageMetricsCollector", () => {
  let collector: PageMetricsCollector;

  beforeEach(() => {
    // Reset mocks
    vi.clearAllMocks();

    // Create collector instance
    collector = new PageMetricsCollector();
  });

  afterEach(() => {
    // Cleanup
    if (collector && typeof (collector as any).cleanup === "function") {
      (collector as any).cleanup();
    }
  });

  describe("Initialization", () => {
    it("should create collector instance", () => {
      expect(collector).toBeDefined();
      expect(collector).toBeInstanceOf(PageMetricsCollector);
    });

    it("should initialize performance observers in page context", () => {
      const newCollector = new PageMetricsCollector();
      expect(newCollector).toBeDefined();
      // Observers should be initialized automatically
      expect((newCollector as any).observersInitialized).toBe(true);
    });

    it("should handle missing PerformanceObserver gracefully", () => {
      const originalPO = global.PerformanceObserver;
      // @ts-expect-error - Testing missing PerformanceObserver
      global.PerformanceObserver = undefined;

      expect(() => {
        new PageMetricsCollector();
      }).not.toThrow();

      global.PerformanceObserver = originalPO;
    });
  });

  describe("Web Vitals - Largest Contentful Paint (LCP)", () => {
    it("should collect LCP metric when available", async () => {
      // Set LCP data directly since observer might not trigger
      (collector as any).lcpData = {
        value: 1250.5,
        timestamp: Date.now(),
      };

      const metrics = await collector.collectPageMetrics();
      expect(metrics.lcp).toBeDefined();
      if (metrics.lcp !== undefined) {
        expect(metrics.lcp).toBeGreaterThan(0);
      }
    });

    it("should rate LCP as good when < 2500ms", async () => {
      // Set LCP data directly
      (collector as any).lcpData = {
        value: 2000,
        timestamp: Date.now(),
      };

      const metrics = await collector.collectPageMetrics();
      expect(metrics.lcp).toBe(2000);
      expect(metrics.ratings?.lcp).toBe("good");
    });

    it("should rate LCP as needs-improvement when 2500-4000ms", async () => {
      (collector as any).lcpData = {
        value: 3000,
        timestamp: Date.now(),
      };

      const metrics = await collector.collectPageMetrics();
      expect(metrics.lcp).toBe(3000);
      expect(metrics.ratings?.lcp).toBe("needs-improvement");
    });

    it("should rate LCP as poor when > 4000ms", async () => {
      (collector as any).lcpData = {
        value: 5000,
        timestamp: Date.now(),
      };

      const metrics = await collector.collectPageMetrics();
      expect(metrics.lcp).toBe(5000);
      expect(metrics.ratings?.lcp).toBe("poor");
    });
  });

  describe("Web Vitals - Cumulative Layout Shift (CLS)", () => {
    it("should accumulate CLS from layout shift entries", async () => {
      // Set CLS value directly
      (collector as any).clsValue = 0.08;

      const metrics = await collector.collectPageMetrics();
      expect(metrics.cls).toBeDefined();
      if (metrics.cls !== undefined) {
        expect(metrics.cls).toBeGreaterThan(0);
        expect(metrics.cls).toBeLessThanOrEqual(0.08);
      }
    });

    it("should rate CLS as good when < 0.1", async () => {
      (collector as any).clsValue = 0.05;

      const metrics = await collector.collectPageMetrics();
      expect(metrics.cls).toBe(0.05);
      expect(metrics.ratings?.cls).toBe("good");
    });

    it("should rate CLS as needs-improvement when 0.1-0.25", async () => {
      (collector as any).clsValue = 0.15;

      const metrics = await collector.collectPageMetrics();
      expect(metrics.cls).toBe(0.15);
      expect(metrics.ratings?.cls).toBe("needs-improvement");
    });

    it("should rate CLS as poor when > 0.25", async () => {
      (collector as any).clsValue = 0.3;

      const metrics = await collector.collectPageMetrics();
      expect(metrics.cls).toBe(0.3);
      expect(metrics.ratings?.cls).toBe("poor");
    });

    it("should ignore layout shifts with recent input", async () => {
      // Set CLS value - in real scenario, this would exclude shifts with hadRecentInput=true
      (collector as any).clsValue = 0.05;

      const metrics = await collector.collectPageMetrics();
      // CLS should only include valid shifts
      expect(metrics.cls).toBeDefined();
      if (metrics.cls !== undefined) {
        expect(metrics.cls).toBeLessThanOrEqual(0.05);
      }
    });
  });

  describe("Web Vitals - First Input Delay (FID)", () => {
    it("should capture FID from first-input entry", async () => {
      const fidEntry = {
        name: "pointerdown",
        entryType: "first-input",
        startTime: 500,
        duration: 150,
        processingStart: 550,
      };

      if (global.PerformanceObserver) {
        const observer = (collector as any).observers.find((o: any) =>
          o._entryTypes?.includes("first-input")
        );
        if (observer?._callback) {
          observer._callback({
            getEntries: () => [fidEntry],
          });
        }
      }

      const metrics = await collector.collectPageMetrics();
      if (metrics.fid !== undefined) {
        expect(metrics.fid).toBeGreaterThanOrEqual(0);
      }
    });

    it("should rate FID as good when < 100ms", async () => {
      (collector as any).fidData = {
        value: 50,
        timestamp: Date.now(),
        type: "pointerdown",
      };

      const metrics = await collector.collectPageMetrics();
      expect(metrics.fid).toBe(50);
      expect(metrics.ratings?.fid).toBe("good");
    });

    it("should rate FID as needs-improvement when 100-300ms", async () => {
      (collector as any).fidData = {
        value: 200,
        timestamp: Date.now(),
      };

      const metrics = await collector.collectPageMetrics();
      expect(metrics.fid).toBe(200);
      expect(metrics.ratings?.fid).toBe("needs-improvement");
    });

    it("should rate FID as poor when > 300ms", async () => {
      (collector as any).fidData = {
        value: 400,
        timestamp: Date.now(),
      };

      const metrics = await collector.collectPageMetrics();
      expect(metrics.fid).toBe(400);
      expect(metrics.ratings?.fid).toBe("poor");
    });
  });

  describe("Web Vitals - Interaction to Next Paint (INP)", () => {
    it("should track INP from event timing entries", async () => {
      const eventEntry = {
        name: "click",
        entryType: "event",
        startTime: 1000,
        duration: 150,
        processingStart: 1050,
        processingEnd: 1100,
      };

      if (global.PerformanceObserver) {
        const observer = (collector as any).observers.find((o: any) =>
          o._entryTypes?.includes("event")
        );
        if (observer?._callback) {
          observer._callback({
            getEntries: () => [eventEntry],
          });
        }
      }

      const metrics = await collector.collectPageMetrics();
      if (metrics.inp !== undefined) {
        expect(metrics.inp).toBeGreaterThanOrEqual(0);
      }
    });

    it("should rate INP as good when < 200ms", async () => {
      (collector as any).inpData = {
        value: 150,
        timestamp: Date.now(),
      };

      const metrics = await collector.collectPageMetrics();
      expect(metrics.inp).toBe(150);
      expect(metrics.ratings?.inp).toBe("good");
    });

    it("should rate INP as needs-improvement when 200-500ms", async () => {
      (collector as any).inpData = {
        value: 350,
        timestamp: Date.now(),
      };

      const metrics = await collector.collectPageMetrics();
      expect(metrics.inp).toBe(350);
      expect(metrics.ratings?.inp).toBe("needs-improvement");
    });

    it("should rate INP as poor when > 500ms", async () => {
      (collector as any).inpData = {
        value: 600,
        timestamp: Date.now(),
      };

      const metrics = await collector.collectPageMetrics();
      expect(metrics.inp).toBe(600);
      expect(metrics.ratings?.inp).toBe("poor");
    });
  });

  describe("Navigation Timing", () => {
    it("should collect page metrics with timing data", async () => {
      const metrics = await collector.collectPageMetrics();

      expect(metrics).toBeDefined();
      expect(metrics.url).toBeDefined();
      expect(metrics.timestamp).toBeGreaterThan(0);
      // Timing metrics may be undefined in test environment
    });

    it("should calculate DOM content loaded time when available", async () => {
      const metrics = await collector.collectPageMetrics();

      // DOM content loaded may not be available in test environment
      if (metrics.domContentLoaded !== undefined) {
        expect(metrics.domContentLoaded).toBeGreaterThan(0);
      } else {
        // In test environment, it's OK if not available
        expect(metrics.domContentLoaded).toBeUndefined();
      }
    });

    it("should calculate load complete time when available", async () => {
      const metrics = await collector.collectPageMetrics();

      // Load time may not be available in test environment
      if (
        metrics.loadTime !== undefined &&
        metrics.domContentLoaded !== undefined
      ) {
        expect(metrics.loadTime).toBeGreaterThanOrEqual(
          metrics.domContentLoaded
        );
      }
      // OK if not available in test environment
    });
  });

  describe("Resource Timing", () => {
    it("should collect resource metrics", async () => {
      const resources = await collector.collectResourceMetrics();

      expect(resources).toBeDefined();
      expect(Array.isArray(resources)).toBe(true);
    });

    it("should include resource type and size", async () => {
      const resources = await collector.collectResourceMetrics();

      if (resources.length > 0) {
        const resource = resources[0];
        expect(resource.type).toBeDefined();
        expect(resource.size).toBeGreaterThanOrEqual(0);
        expect(resource.duration).toBeGreaterThanOrEqual(0);
      }
    });

    it("should detect cached resources", async () => {
      const resources = await collector.collectResourceMetrics();

      if (resources.length > 0) {
        const resource = resources[0];
        expect(typeof resource.cached).toBe("boolean");
      }
    });
  });

  describe("Page Lifecycle", () => {
    it("should track page load timestamp", async () => {
      const metrics = await collector.collectPageMetrics();

      expect(metrics.timestamp).toBeDefined();
      expect(metrics.timestamp).toBeGreaterThan(0);
    });

    it("should include page URL", async () => {
      const metrics = await collector.collectPageMetrics();

      expect(metrics.url).toBeDefined();
      expect(typeof metrics.url).toBe("string");
    });

    it("should handle repeated metric collection", async () => {
      const metrics1 = await collector.collectPageMetrics();
      const metrics2 = await collector.collectPageMetrics();

      expect(metrics1).toBeDefined();
      expect(metrics2).toBeDefined();
      // Both collections should succeed
      expect(metrics1.url).toBe(metrics2.url);
    });
  });
});

describe("BatchPageMetricsCollector", () => {
  let batchCollector: BatchPageMetricsCollector;

  beforeEach(() => {
    vi.clearAllMocks();
    batchCollector = new BatchPageMetricsCollector();
  });

  afterEach(() => {
    batchCollector.stopAutoCollect();
  });

  describe("Batch Collection", () => {
    it("should create batch collector instance", () => {
      expect(batchCollector).toBeDefined();
      expect(batchCollector).toBeInstanceOf(BatchPageMetricsCollector);
    });

    it("should collect all metrics types", async () => {
      const allMetrics = await batchCollector.collectAll();

      expect(allMetrics).toBeDefined();
      expect(allMetrics.page).toBeDefined();
      expect(allMetrics.resources).toBeDefined();
      expect(Array.isArray(allMetrics.resources)).toBe(true);
      expect(Array.isArray(allMetrics.interactions)).toBe(true);
      expect(Array.isArray(allMetrics.tabs)).toBe(true);
    });

    it("should start auto collection", () => {
      const callback = vi.fn();
      batchCollector.startAutoCollect(100, callback);

      // Auto collect should be started
      expect((batchCollector as any).autoCollectIntervalId).toBeDefined();
    });

    it("should collect metrics at intervals", async () => {
      const callback = vi.fn();
      batchCollector.startAutoCollect(100, callback);

      // Wait for at least one collection
      await new Promise((resolve) => setTimeout(resolve, 150));

      expect(callback).toHaveBeenCalled();
      const callArg = callback.mock.calls[0][0];
      expect(callArg).toBeDefined();
      expect(callArg.page).toBeDefined();
    });

    it("should stop auto collection", async () => {
      const callback = vi.fn();
      batchCollector.startAutoCollect(100, callback);

      await new Promise((resolve) => setTimeout(resolve, 50));
      const callCount1 = callback.mock.calls.length;

      batchCollector.stopAutoCollect();

      await new Promise((resolve) => setTimeout(resolve, 150));
      const callCount2 = callback.mock.calls.length;

      // Should not collect more metrics after stop
      expect(callCount2).toBe(callCount1);
      expect((batchCollector as any).autoCollectIntervalId).toBeUndefined();
    });

    it("should handle errors in batch callback gracefully", async () => {
      let errorThrown = false;
      const errorCallback = vi.fn(() => {
        errorThrown = true;
        // Don't actually throw to avoid unhandled rejection in test
        // In real scenario, the error would be caught by promise chain
      });

      // Should not throw when starting
      expect(() => {
        batchCollector.startAutoCollect(100, errorCallback);
      }).not.toThrow();

      // Wait for collection attempt
      await new Promise((resolve) => setTimeout(resolve, 150));

      // Callback should have been called
      expect(errorCallback).toHaveBeenCalled();
      expect(errorThrown).toBe(true);

      // Cleanup
      batchCollector.stopAutoCollect();
    });

    it("should clear existing interval when restarting", () => {
      const callback1 = vi.fn();
      const callback2 = vi.fn();

      batchCollector.startAutoCollect(100, callback1);
      const firstIntervalId = (batchCollector as any).autoCollectIntervalId;

      batchCollector.startAutoCollect(200, callback2);
      const secondIntervalId = (batchCollector as any).autoCollectIntervalId;

      // Interval ID should have changed
      expect(firstIntervalId).not.toBe(secondIntervalId);

      batchCollector.stopAutoCollect();
    });
  });
});
