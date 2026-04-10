import { describe, it, expect, vi, beforeEach } from "vitest";

import { AbstractBrowserEventCapture } from "../capture";
import type { BrowserEventFilter } from "../types";
import type { BrowserEvent, TabEvent } from "../types";

// ---------------------------------------------------------------------------
// Minimal concrete implementation for testing
// ---------------------------------------------------------------------------

class StubCapture extends AbstractBrowserEventCapture {
  capturedFilters: Map<string, BrowserEventFilter | undefined> = new Map();
  stopped = false;

  captureTabEvents(filter?: BrowserEventFilter): void {
    this.capturedFilters.set("tabs", filter);
    this.activeCaptures.add("tabs");
  }

  captureNavigationEvents(filter?: BrowserEventFilter): void {
    this.capturedFilters.set("navigation", filter);
    this.activeCaptures.add("navigation");
  }

  captureNetworkEvents(filter?: BrowserEventFilter): void {
    this.capturedFilters.set("network", filter);
    this.activeCaptures.add("network");
  }

  captureWebRequestEvents(filter?: BrowserEventFilter): void {
    this.capturedFilters.set("webRequest", filter);
    this.activeCaptures.add("webRequest");
  }

  captureHistoryEvents(filter?: BrowserEventFilter): void {
    this.capturedFilters.set("history", filter);
    this.activeCaptures.add("history");
  }

  stop(): void {
    this.stopped = true;
    this.activeCaptures.clear();
  }

  /** Expose `emit` for testing */
  async emitEvent(event: BrowserEvent): Promise<void> {
    return this.emit(event);
  }

  /** Expose `makeSource` / `newEventId` for testing */
  getSource() {
    return this.makeSource();
  }

  getId() {
    return this.newEventId();
  }
}

function makeTabEvent(overrides?: Partial<TabEvent>): TabEvent {
  return {
    id: "cap-001",
    type: "tab.created",
    timestamp: Date.now(),
    source: { type: "browser", id: "ext-test" },
    data: { action: "created", tabId: 5 },
    ...overrides,
  };
}

// ---------------------------------------------------------------------------

describe("AbstractBrowserEventCapture — handler management", () => {
  let capture: StubCapture;

  beforeEach(() => {
    capture = new StubCapture("ext-test");
  });

  it("registers and calls event handlers", async () => {
    const handler = vi.fn();
    capture.onEvent(handler);
    const event = makeTabEvent();
    await capture.emitEvent(event);
    expect(handler).toHaveBeenCalledWith(event);
  });

  it("removes event handler via offEvent", async () => {
    const handler = vi.fn();
    capture.onEvent(handler);
    capture.offEvent(handler);
    await capture.emitEvent(makeTabEvent());
    expect(handler).not.toHaveBeenCalled();
  });

  it("calls all registered handlers", async () => {
    const h1 = vi.fn();
    const h2 = vi.fn();
    capture.onEvent(h1);
    capture.onEvent(h2);
    await capture.emitEvent(makeTabEvent());
    expect(h1).toHaveBeenCalledTimes(1);
    expect(h2).toHaveBeenCalledTimes(1);
  });

  it("isolates handler errors — other handlers still run", async () => {
    const consoleSpy = vi.spyOn(console, "error").mockImplementation(() => {});
    const throwing = vi.fn().mockRejectedValue(new Error("boom"));
    const safe = vi.fn();
    capture.onEvent(throwing);
    capture.onEvent(safe);
    await capture.emitEvent(makeTabEvent());
    expect(safe).toHaveBeenCalled();
    consoleSpy.mockRestore();
  });
});

describe("AbstractBrowserEventCapture — event counts", () => {
  let capture: StubCapture;

  beforeEach(() => {
    capture = new StubCapture("ext-test");
  });

  it("increments event count on emit", async () => {
    await capture.emitEvent(makeTabEvent({ type: "tab.created" }));
    await capture.emitEvent(makeTabEvent({ type: "tab.created" }));
    expect(capture.getEventCounts()["tab.created"]).toBe(2);
  });

  it("tracks counts by event type separately", async () => {
    await capture.emitEvent(makeTabEvent({ type: "tab.created" }));
    await capture.emitEvent(makeTabEvent({ type: "tab.removed" }));
    const counts = capture.getEventCounts();
    expect(counts["tab.created"]).toBe(1);
    expect(counts["tab.removed"]).toBe(1);
  });

  it("clearEventCounts resets all counts", async () => {
    await capture.emitEvent(makeTabEvent());
    capture.clearEventCounts();
    expect(Object.keys(capture.getEventCounts())).toHaveLength(0);
  });
});

describe("AbstractBrowserEventCapture — status", () => {
  let capture: StubCapture;

  beforeEach(() => {
    capture = new StubCapture("ext-test");
  });

  it("reports no captures active initially", () => {
    const status = capture.getStatus();
    expect(status.tabs).toBe(false);
    expect(status.navigation).toBe(false);
  });

  it("reports active captures after captureTabEvents", () => {
    capture.captureTabEvents();
    expect(capture.getStatus().tabs).toBe(true);
  });

  it("captureAll activates all categories", () => {
    capture.captureAll();
    const status = capture.getStatus();
    expect(status.tabs).toBe(true);
    expect(status.navigation).toBe(true);
    expect(status.network).toBe(true);
    expect(status.webRequest).toBe(true);
    expect(status.history).toBe(true);
  });

  it("stop clears all active captures", () => {
    capture.captureAll();
    capture.stop();
    const status = capture.getStatus();
    expect(status.tabs).toBe(false);
    expect(status.navigation).toBe(false);
  });
});

describe("AbstractBrowserEventCapture — factory helpers", () => {
  it("makeSource returns correct browser source", () => {
    const capture = new StubCapture("my-ext-id");
    expect(capture.getSource()).toEqual({ type: "browser", id: "my-ext-id" });
  });

  it("newEventId returns a non-empty string", () => {
    const capture = new StubCapture("x");
    expect(typeof capture.getId()).toBe("string");
    expect(capture.getId().length).toBeGreaterThan(0);
  });
});
