import { describe, it, expect } from "vitest";

import {
  isPlatformEvent,
  isEventOfType,
  PlatformEventSchema,
  EventSourceSchema,
  type PlatformEvent,
  type EventSource,
} from "../types";

function makeEvent(overrides?: Partial<PlatformEvent>): PlatformEvent {
  return {
    id: "evt-001",
    type: "user.logged-in",
    timestamp: 1_700_000_000_000,
    source: { type: "client", id: "web-app" },
    data: { userId: "u1" },
    ...overrides,
  };
}

describe("EventSourceSchema", () => {
  it("accepts all valid source types", () => {
    const types: EventSource["type"][] = [
      "browser",
      "server",
      "client",
      "extension",
      "mobile",
      "desktop",
    ];
    for (const type of types) {
      expect(EventSourceSchema.safeParse({ type, id: "x" }).success).toBe(true);
    }
  });

  it("rejects unknown source type", () => {
    expect(
      EventSourceSchema.safeParse({ type: "fax", id: "x" }).success
    ).toBe(false);
  });

  it("rejects empty id", () => {
    expect(
      EventSourceSchema.safeParse({ type: "client", id: "" }).success
    ).toBe(false);
  });
});

describe("PlatformEventSchema", () => {
  it("accepts a valid event", () => {
    expect(PlatformEventSchema.safeParse(makeEvent()).success).toBe(true);
  });

  it("accepts event with optional correlationId", () => {
    expect(
      PlatformEventSchema.safeParse(makeEvent({ correlationId: "c-123" }))
        .success
    ).toBe(true);
  });

  it("rejects missing id", () => {
    const { id: _, ...noId } = makeEvent();
    expect(PlatformEventSchema.safeParse(noId).success).toBe(false);
  });

  it("rejects missing type", () => {
    const { type: _, ...noType } = makeEvent();
    expect(PlatformEventSchema.safeParse(noType).success).toBe(false);
  });

  it("rejects negative timestamp", () => {
    expect(
      PlatformEventSchema.safeParse(makeEvent({ timestamp: -1 })).success
    ).toBe(false);
  });
});

describe("isPlatformEvent", () => {
  it("returns true for a valid event", () => {
    expect(isPlatformEvent(makeEvent())).toBe(true);
  });

  it("returns false for null", () => {
    expect(isPlatformEvent(null)).toBe(false);
  });

  it("returns false for a plain object missing fields", () => {
    expect(isPlatformEvent({ id: "x" })).toBe(false);
  });
});

describe("isEventOfType", () => {
  it("returns true when types match", () => {
    const event = makeEvent({ type: "tab.created" });
    expect(isEventOfType(event, "tab.created")).toBe(true);
  });

  it("returns false when types do not match", () => {
    const event = makeEvent({ type: "tab.created" });
    expect(isEventOfType(event, "tab.removed")).toBe(false);
  });
});
