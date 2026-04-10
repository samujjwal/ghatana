import { describe, it, expect } from "vitest";

import {
  serializeEvent,
  deserializeEvent,
  validateEvent,
  EventDeserializationError,
} from "../serializer";
import type { PlatformEvent } from "../types";

function makeEvent(overrides?: Partial<PlatformEvent>): PlatformEvent {
  return {
    id: "ser-001",
    type: "data.synced",
    timestamp: 1_700_000_000_000,
    source: { type: "server", id: "sync-service" },
    data: { records: 42 },
    correlationId: "corr-abc",
    ...overrides,
  };
}

describe("serializeEvent", () => {
  it("produces valid JSON", () => {
    const json = serializeEvent(makeEvent());
    expect(() => JSON.parse(json)).not.toThrow();
  });

  it("includes _v: 1 envelope", () => {
    const obj = JSON.parse(serializeEvent(makeEvent())) as Record<string, unknown>;
    expect(obj._v).toBe(1);
  });

  it("preserves all required fields", () => {
    const event = makeEvent();
    const obj = JSON.parse(serializeEvent(event)) as Record<string, unknown>;
    expect(obj.id).toBe(event.id);
    expect(obj.type).toBe(event.type);
    expect(obj.timestamp).toBe(event.timestamp);
    expect(obj.correlationId).toBe(event.correlationId);
  });

  it("excludes correlationId when not present", () => {
    const event = makeEvent({ correlationId: undefined });
    const obj = JSON.parse(serializeEvent(event)) as Record<string, unknown>;
    expect(obj.correlationId).toBeUndefined();
  });
});

describe("deserializeEvent", () => {
  it("round-trips an event through serialize → deserialize", () => {
    const original = makeEvent();
    const json = serializeEvent(original);
    const restored = deserializeEvent(json);
    expect(restored.id).toBe(original.id);
    expect(restored.type).toBe(original.type);
    expect(restored.timestamp).toBe(original.timestamp);
    expect(restored.correlationId).toBe(original.correlationId);
    expect(restored.data).toEqual(original.data);
  });

  it("throws EventDeserializationError for malformed JSON", () => {
    expect(() => deserializeEvent("not-json")).toThrow(EventDeserializationError);
  });

  it("throws EventDeserializationError for missing required fields", () => {
    const bad = JSON.stringify({ _v: 1, id: "x" });
    expect(() => deserializeEvent(bad)).toThrow(EventDeserializationError);
  });

  it("throws EventDeserializationError for wrong envelope version", () => {
    const wrong = JSON.stringify({ _v: 99, id: "x", type: "t", timestamp: 1, source: { type: "client", id: "y" }, data: {} });
    expect(() => deserializeEvent(wrong)).toThrow(EventDeserializationError);
  });
});

describe("validateEvent", () => {
  it("accepts a valid event", () => {
    const event = makeEvent();
    expect(() => validateEvent(event)).not.toThrow();
  });

  it("throws for invalid input", () => {
    expect(() => validateEvent({ id: "x" })).toThrow(EventDeserializationError);
  });
});
