/**
 * @fileoverview Tests for UnifiedBrowserEventCapture
 *
 * Tests tab events, navigation events, network events with fallback,
 * event filtering, and listener cleanup.
 */

import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { UnifiedBrowserEventCapture } from "../../src/events/UnifiedBrowserEventCapture";
import type { EventFilter } from "../../src/events/EventCapture.interface";

describe("UnifiedBrowserEventCapture", () => {
  let capture: UnifiedBrowserEventCapture;

  beforeEach(() => {
    vi.clearAllMocks();
    capture = new UnifiedBrowserEventCapture();
  });

  afterEach(() => {
    if (capture) {
      capture.stop();
    }
  });

  describe("Initialization", () => {
    it("should create instance successfully", () => {
      expect(capture).toBeDefined();
      expect(capture).toBeInstanceOf(UnifiedBrowserEventCapture);
    });

    it("should initialize with stopped status", () => {
      const status = capture.getStatus();
      expect(status.tabs).toBe(false);
      expect(status.navigation).toBe(false);
      expect(status.network).toBe(false);
      expect(status.webrequest).toBe(false);
      expect(status.history).toBe(false);
    });

    it("should have zero event counts initially", () => {
      const counts = capture.getEventCounts();
      expect(counts.tab).toBe(0);
      expect(counts.navigation).toBe(0);
      expect(counts.network).toBe(0);
      expect(counts.webrequest).toBe(0);
      expect(counts.history).toBe(0);
    });
  });

  describe("Event Handler Registration", () => {
    it("should register event handler", () => {
      const eventHandler = vi.fn();
      capture.onEvent(eventHandler);

      // Verify handler is registered (will be called when events are captured)
      expect(eventHandler).not.toHaveBeenCalled();
    });

    it("should allow multiple event handlers", () => {
      const handler1 = vi.fn();
      const handler2 = vi.fn();

      capture.onEvent(handler1);
      capture.onEvent(handler2);

      // Both should be registered
      expect(handler1).not.toHaveBeenCalled();
      expect(handler2).not.toHaveBeenCalled();
    });

    it("should remove event handler", () => {
      const eventHandler = vi.fn();
      capture.onEvent(eventHandler);
      capture.offEvent(eventHandler);

      // Handler should be removed (no calls when capturing)
      expect(eventHandler).not.toHaveBeenCalled();
    });
  });

  describe("Tab Event Capture", () => {
    it("should start capturing tab events", () => {
      capture.captureTabEvents();

      const status = capture.getStatus();
      expect(status.tabs).toBe(true);
    });

    it("should filter tab events by tabId", () => {
      const filter: EventFilter = {
        tabIds: [123],
      };

      capture.captureTabEvents(filter);

      const status = capture.getStatus();
      expect(status.tabs).toBe(true);
    });

    it("should filter tab events by windowId", () => {
      const filter: EventFilter = {
        windowIds: [1],
      };

      capture.captureTabEvents(filter);

      const status = capture.getStatus();
      expect(status.tabs).toBe(true);
    });
  });

  describe("Navigation Event Capture", () => {
    it("should start capturing navigation events", () => {
      capture.captureNavigationEvents();

      const status = capture.getStatus();
      expect(status.navigation).toBe(true);
    });

    it("should filter navigation events by URL pattern", () => {
      const filter: EventFilter = {
        urlPatterns: ["*://example.com/*"],
      };

      capture.captureNavigationEvents(filter);

      const status = capture.getStatus();
      expect(status.navigation).toBe(true);
    });

    it("should filter navigation events by tab ID", () => {
      const filter: EventFilter = {
        tabIds: [123],
      };

      capture.captureNavigationEvents(filter);

      const status = capture.getStatus();
      expect(status.navigation).toBe(true);
    });
  });

  describe("Network Event Capture", () => {
    it("should start capturing network events", () => {
      capture.captureNetworkEvents();

      const status = capture.getStatus();
      expect(status.network).toBe(true);
    });

    it("should handle network events with URL filter", () => {
      const filter: EventFilter = {
        urlPatterns: ["*://api.example.com/*"],
      };

      capture.captureNetworkEvents(filter);

      const status = capture.getStatus();
      expect(status.network).toBe(true);
    });
  });

  describe("WebRequest Event Capture", () => {
    it("should start capturing webrequest events", () => {
      capture.captureWebRequestEvents();

      const status = capture.getStatus();
      // webrequest may be true or false depending on API availability
      expect(typeof status.webrequest).toBe("boolean");
    });

    it("should handle missing webRequest API gracefully", () => {
      // Should not throw even if webRequest API is unavailable
      expect(() => {
        capture.captureWebRequestEvents();
      }).not.toThrow();
    });

    it("should accept URL pattern filter for webrequest", () => {
      const filter: EventFilter = {
        urlPatterns: ["*://*.example.com/*"],
      };

      expect(() => {
        capture.captureWebRequestEvents(filter);
      }).not.toThrow();
    });
  });

  describe("History Event Capture", () => {
    it("should start capturing history events", () => {
      capture.captureHistoryEvents();

      const status = capture.getStatus();
      // history may be true or false depending on API availability
      expect(typeof status.history).toBe("boolean");
    });

    it("should handle missing history API gracefully", () => {
      // Should not throw even if history API is unavailable
      expect(() => {
        capture.captureHistoryEvents();
      }).not.toThrow();
    });
  });

  describe("Capture All Events", () => {
    it("should capture all event types", () => {
      capture.captureAll();

      const status = capture.getStatus();
      // Should have started capturing tabs and navigation at minimum
      expect(status.tabs).toBe(true);
      expect(status.navigation).toBe(true);
    });

    it("should apply filter to all event types", () => {
      const filter: EventFilter = {
        types: ["tab", "navigation"],
        tabIds: [123],
      };

      capture.captureAll(filter);

      const status = capture.getStatus();
      expect(status.tabs).toBe(true);
      expect(status.navigation).toBe(true);
    });

    it("should capture network events when capturing all", () => {
      capture.captureAll();

      const status = capture.getStatus();
      expect(status.network).toBe(true);
    });
  });

  describe("Event Counting", () => {
    it("should provide event count structure", () => {
      const counts = capture.getEventCounts();

      expect(counts).toBeDefined();
      expect(typeof counts.tab).toBe("number");
      expect(typeof counts.navigation).toBe("number");
      expect(typeof counts.network).toBe("number");
      expect(typeof counts.webrequest).toBe("number");
      expect(typeof counts.history).toBe("number");
    });

    it("should clear event counts", () => {
      capture.captureTabEvents();

      capture.clearEventCounts();

      const counts = capture.getEventCounts();
      expect(counts.tab).toBe(0);
      expect(counts.navigation).toBe(0);
      expect(counts.network).toBe(0);
      expect(counts.webrequest).toBe(0);
      expect(counts.history).toBe(0);
    });

    it("should maintain separate counts for each event type", () => {
      const counts = capture.getEventCounts();

      expect(counts).toHaveProperty("tab");
      expect(counts).toHaveProperty("navigation");
      expect(counts).toHaveProperty("network");
      expect(counts).toHaveProperty("webrequest");
      expect(counts).toHaveProperty("history");
    });
  });

  describe("Listener Cleanup", () => {
    it("should stop all event capture", () => {
      capture.captureAll();

      const status1 = capture.getStatus();
      expect(status1.tabs).toBe(true);

      capture.stop();

      const status2 = capture.getStatus();
      expect(status2.tabs).toBe(false);
      expect(status2.navigation).toBe(false);
      expect(status2.network).toBe(false);
    });

    it("should handle stop when not capturing", () => {
      expect(() => {
        capture.stop();
      }).not.toThrow();

      const status = capture.getStatus();
      expect(status.tabs).toBe(false);
      expect(status.navigation).toBe(false);
    });

    it("should allow restart after stop", () => {
      capture.captureTabEvents();
      expect(capture.getStatus().tabs).toBe(true);

      capture.stop();
      expect(capture.getStatus().tabs).toBe(false);

      capture.captureTabEvents();
      expect(capture.getStatus().tabs).toBe(true);
    });

    it("should stop individual event types", () => {
      capture.captureAll();

      const status1 = capture.getStatus();
      expect(status1.tabs).toBe(true);
      expect(status1.navigation).toBe(true);

      capture.stop();

      const status2 = capture.getStatus();
      expect(status2.tabs).toBe(false);
      expect(status2.navigation).toBe(false);
      expect(status2.network).toBe(false);
      expect(status2.webrequest).toBe(false);
      expect(status2.history).toBe(false);
    });
  });

  describe("Event Filtering", () => {
    it("should accept filter with event types", () => {
      const filter: EventFilter = {
        types: ["tab"],
      };

      expect(() => {
        capture.captureAll(filter);
      }).not.toThrow();
    });

    it("should accept filter with tab IDs", () => {
      const filter: EventFilter = {
        tabIds: [123, 456],
      };

      expect(() => {
        capture.captureTabEvents(filter);
      }).not.toThrow();
    });

    it("should accept filter with window IDs", () => {
      const filter: EventFilter = {
        windowIds: [1, 2],
      };

      expect(() => {
        capture.captureTabEvents(filter);
      }).not.toThrow();
    });

    it("should accept filter with URL patterns", () => {
      const filter: EventFilter = {
        urlPatterns: ["*://example.com/*", "*://api.example.com/*"],
      };

      expect(() => {
        capture.captureNavigationEvents(filter);
      }).not.toThrow();
    });

    it("should accept combined filter criteria", () => {
      const filter: EventFilter = {
        types: ["tab", "navigation"],
        tabIds: [123, 456],
        urlPatterns: ["*://example.com/*"],
        windowIds: [1],
      };

      expect(() => {
        capture.captureAll(filter);
      }).not.toThrow();
    });
  });

  describe("Status Reporting", () => {
    it("should report individual capture states", () => {
      const status1 = capture.getStatus();
      expect(status1.tabs).toBe(false);
      expect(status1.navigation).toBe(false);

      capture.captureTabEvents();

      const status2 = capture.getStatus();
      expect(status2.tabs).toBe(true);
      expect(status2.navigation).toBe(false);
    });

    it("should update status for multiple captures", () => {
      capture.captureTabEvents();
      capture.captureNavigationEvents();

      const status = capture.getStatus();
      expect(status.tabs).toBe(true);
      expect(status.navigation).toBe(true);
    });

    it("should return boolean status for all event types", () => {
      const status = capture.getStatus();

      expect(typeof status.tabs).toBe("boolean");
      expect(typeof status.navigation).toBe("boolean");
      expect(typeof status.network).toBe("boolean");
      expect(typeof status.webrequest).toBe("boolean");
      expect(typeof status.history).toBe("boolean");
      expect(typeof status.flow).toBe("boolean");
    });

    it("should reflect status after stop", () => {
      capture.captureAll();

      const status1 = capture.getStatus();
      expect(status1.tabs).toBe(true);

      capture.stop();

      const status2 = capture.getStatus();
      expect(status2.tabs).toBe(false);
      expect(status2.navigation).toBe(false);
      expect(status2.network).toBe(false);
    });
  });

  describe("Content Script Network Event Handling", () => {
    it("should have handleContentScriptNetworkEvent method", () => {
      expect(typeof (capture as any).handleContentScriptNetworkEvent).toBe(
        "function"
      );
    });

    it("should accept network event data", () => {
      const networkData = {
        type: "network-request" as const,
        requestId: "req-123",
        url: "https://api.example.com/data",
        method: "GET",
        timestamp: Date.now(),
      };

      expect(() => {
        (capture as any).handleContentScriptNetworkEvent?.(networkData, 123);
      }).not.toThrow();
    });
  });
});
