import { describe, it, expect } from "vitest";

import {
  isBrowserEvent,
  isBrowserEventOfType,
  createDomainFilter,
  createUrlPatternFilter,
  TabEventDataSchema,
  NavigationEventDataSchema,
  HistoryEventDataSchema,
  type TabEvent,
  type NavigationEvent,
  type BrowserEvent,
} from "../types";

function makeTabEvent(overrides?: Partial<TabEvent>): TabEvent {
  return {
    id: "be-001",
    type: "tab.created",
    timestamp: 1_700_000_000_000,
    source: { type: "browser", id: "ext-v1" },
    data: { action: "created", tabId: 10, url: "https://example.com/page" },
    ...overrides,
  };
}

function makeNavEvent(overrides?: Partial<NavigationEvent>): NavigationEvent {
  return {
    id: "be-002",
    type: "navigation.completed",
    timestamp: 1_700_000_000_001,
    source: { type: "browser", id: "ext-v1" },
    data: { action: "completed", tabId: 10, url: "https://example.com/page" },
    ...overrides,
  };
}

// ---------------------------------------------------------------------------
describe("TabEventDataSchema", () => {
  it("accepts a valid tab event payload", () => {
    expect(
      TabEventDataSchema.safeParse({
        action: "created",
        tabId: 5,
        url: "https://example.com",
      }).success
    ).toBe(true);
  });

  it("rejects unknown action", () => {
    expect(
      TabEventDataSchema.safeParse({ action: "teleported", tabId: 5 }).success
    ).toBe(false);
  });
});

describe("NavigationEventDataSchema", () => {
  it("accepts a valid navigation payload", () => {
    expect(
      NavigationEventDataSchema.safeParse({
        action: "completed",
        tabId: 1,
        url: "https://example.com",
      }).success
    ).toBe(true);
  });

  it("rejects non-URL url field", () => {
    expect(
      NavigationEventDataSchema.safeParse({
        action: "completed",
        tabId: 1,
        url: "not-a-url",
      }).success
    ).toBe(false);
  });
});

describe("HistoryEventDataSchema", () => {
  it("accepts a valid history payload", () => {
    expect(
      HistoryEventDataSchema.safeParse({ action: "visited", url: "https://x.com" }).success
    ).toBe(true);
  });

  it("rejects unknown action", () => {
    expect(
      HistoryEventDataSchema.safeParse({ action: "warped" }).success
    ).toBe(false);
  });
});

// ---------------------------------------------------------------------------
describe("isBrowserEvent", () => {
  it("returns true for a valid tab event", () => {
    expect(isBrowserEvent(makeTabEvent())).toBe(true);
  });

  it("returns false when source.type is not browser", () => {
    const notBrowser = { ...makeTabEvent(), source: { type: "client", id: "x" } };
    expect(isBrowserEvent(notBrowser)).toBe(false);
  });

  it("returns false for null", () => {
    expect(isBrowserEvent(null)).toBe(false);
  });

  it("returns false for a plain PlatformEvent with non-browser source", () => {
    expect(
      isBrowserEvent({
        id: "x",
        type: "blah",
        timestamp: 1,
        source: { type: "server", id: "s" },
        data: {},
      })
    ).toBe(false);
  });
});

describe("isBrowserEventOfType", () => {
  it("returns true when event type matches", () => {
    const event = makeTabEvent({ type: "tab.activated" });
    expect(isBrowserEventOfType<TabEvent>(event as BrowserEvent, "tab.activated")).toBe(true);
  });

  it("returns false when event type does not match", () => {
    const event = makeTabEvent();
    expect(isBrowserEventOfType<TabEvent>(event as BrowserEvent, "tab.removed")).toBe(false);
  });
});

// ---------------------------------------------------------------------------
describe("createDomainFilter", () => {
  it("accepts events with exact domain match", () => {
    const filter = createDomainFilter("example.com");
    expect(filter(makeTabEvent())).toBe(true);
  });

  it("accepts events with sub-domain match", () => {
    const filter = createDomainFilter("example.com");
    const event = makeTabEvent({ data: { action: "created", tabId: 1, url: "https://sub.example.com/path" } });
    expect(filter(event as BrowserEvent)).toBe(true);
  });

  it("rejects events from a different domain", () => {
    const filter = createDomainFilter("safe.com");
    expect(filter(makeTabEvent())).toBe(false);
  });

  it("rejects events with no url in data", () => {
    const filter = createDomainFilter("example.com");
    const event = makeTabEvent({ data: { action: "created", tabId: 1 } });
    expect(filter(event as BrowserEvent)).toBe(false);
  });

  it("rejects events matching multiple domains — accepts when any matches", () => {
    const filter = createDomainFilter("safe.com", "example.com");
    expect(filter(makeTabEvent())).toBe(true);
  });
});

describe("createUrlPatternFilter", () => {
  it("accepts events whose URL matches the pattern", () => {
    const filter = createUrlPatternFilter(/\/page$/);
    expect(filter(makeTabEvent())).toBe(true);
  });

  it("rejects events whose URL does not match", () => {
    const filter = createUrlPatternFilter(/\/admin/);
    expect(filter(makeTabEvent())).toBe(false);
  });

  it("rejects events with no URL in data", () => {
    const filter = createUrlPatternFilter(/example/);
    const event = makeTabEvent({ data: { action: "created", tabId: 1 } });
    expect(filter(event as BrowserEvent)).toBe(false);
  });
});
