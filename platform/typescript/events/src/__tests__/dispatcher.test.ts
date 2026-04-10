import { describe, it, expect, vi } from "vitest";

import { EventDispatcher } from "../dispatcher";
import type { PlatformEvent } from "../types";

function makeEvent(type = "test.event", data: unknown = {}): PlatformEvent {
  return {
    id: "evt-001",
    type,
    timestamp: Date.now(),
    source: { type: "client", id: "test" },
    data,
  };
}

describe("EventDispatcher — subscribe / unsubscribe", () => {
  it("returns a subscription token with matching eventType", () => {
    const d = new EventDispatcher();
    const token = d.subscribe("user.created", vi.fn());
    expect(token.eventType).toBe("user.created");
    expect(typeof token.id).toBe("string");
  });

  it("removes subscription on unsubscribe", async () => {
    const d = new EventDispatcher();
    const handler = vi.fn();
    const token = d.subscribe("a.b", handler);
    d.unsubscribe(token);
    await d.dispatch(makeEvent("a.b"));
    expect(handler).not.toHaveBeenCalled();
  });

  it("tracks subscriptionCount correctly", () => {
    const d = new EventDispatcher();
    expect(d.subscriptionCount).toBe(0);
    const t1 = d.subscribe("a", vi.fn());
    const t2 = d.subscribe("b", vi.fn());
    expect(d.subscriptionCount).toBe(2);
    d.unsubscribe(t1);
    expect(d.subscriptionCount).toBe(1);
    d.unsubscribe(t2);
    expect(d.subscriptionCount).toBe(0);
  });

  it("clear() removes all subscriptions", async () => {
    const d = new EventDispatcher();
    const handler = vi.fn();
    d.subscribe("x", handler);
    d.subscribe("y", handler);
    d.clear();
    await d.dispatch(makeEvent("x"));
    expect(handler).not.toHaveBeenCalled();
    expect(d.subscriptionCount).toBe(0);
  });
});

describe("EventDispatcher — dispatch", () => {
  it("calls handler for matching event type", async () => {
    const d = new EventDispatcher();
    const handler = vi.fn();
    d.subscribe("user.created", handler);
    const event = makeEvent("user.created", { userId: "u1" });
    await d.dispatch(event);
    expect(handler).toHaveBeenCalledWith(event);
  });

  it("does NOT call handler for non-matching event type", async () => {
    const d = new EventDispatcher();
    const handler = vi.fn();
    d.subscribe("user.created", handler);
    await d.dispatch(makeEvent("order.placed"));
    expect(handler).not.toHaveBeenCalled();
  });

  it("calls wildcard (*) handler for any event type", async () => {
    const d = new EventDispatcher();
    const handler = vi.fn();
    d.subscribe("*", handler);
    const e1 = makeEvent("tab.created");
    const e2 = makeEvent("user.created");
    await d.dispatch(e1);
    await d.dispatch(e2);
    expect(handler).toHaveBeenCalledTimes(2);
  });

  it("applies filter — skips event when filter returns false", async () => {
    const d = new EventDispatcher();
    const handler = vi.fn();
    d.subscribe("order.placed", handler, (e) => (e.data as { amount: number }).amount > 100);
    await d.dispatch(makeEvent("order.placed", { amount: 50 }));
    expect(handler).not.toHaveBeenCalled();
  });

  it("applies filter — calls handler when filter returns true", async () => {
    const d = new EventDispatcher();
    const handler = vi.fn();
    d.subscribe("order.placed", handler, (e) => (e.data as { amount: number }).amount > 100);
    const event = makeEvent("order.placed", { amount: 200 });
    await d.dispatch(event);
    expect(handler).toHaveBeenCalledWith(event);
  });

  it("calls multiple handlers for the same event type", async () => {
    const d = new EventDispatcher();
    const h1 = vi.fn();
    const h2 = vi.fn();
    d.subscribe("x", h1);
    d.subscribe("x", h2);
    await d.dispatch(makeEvent("x"));
    expect(h1).toHaveBeenCalledTimes(1);
    expect(h2).toHaveBeenCalledTimes(1);
  });

  it("isolates handler errors and calls onError", async () => {
    const d = new EventDispatcher();
    const error = new Error("boom");
    const onError = vi.fn();
    const h1 = vi.fn().mockRejectedValue(error);
    const h2 = vi.fn();
    d.subscribe("x", h1);
    d.subscribe("x", h2);
    await d.dispatch(makeEvent("x"), onError);
    expect(h2).toHaveBeenCalled();
    expect(onError).toHaveBeenCalledWith(error, expect.objectContaining({ eventType: "x" }));
  });

  it("awaits async handlers", async () => {
    const d = new EventDispatcher();
    const results: string[] = [];
    d.subscribe("seq", async () => {
      await new Promise((r) => setTimeout(r, 5));
      results.push("done");
    });
    await d.dispatch(makeEvent("seq"));
    expect(results).toEqual(["done"]);
  });
});

describe("EventDispatcher — dispatchSync", () => {
  it("calls sync handler immediately", () => {
    const d = new EventDispatcher();
    const handler = vi.fn();
    d.subscribe("s", handler);
    d.dispatchSync(makeEvent("s"));
    expect(handler).toHaveBeenCalledTimes(1);
  });

  it("catches sync handler errors and calls onError", () => {
    const d = new EventDispatcher();
    const error = new Error("sync-boom");
    const onError = vi.fn();
    d.subscribe("s", () => { throw error; });
    d.dispatchSync(makeEvent("s"), onError);
    expect(onError).toHaveBeenCalledWith(error, expect.objectContaining({ eventType: "s" }));
  });
});

describe("EventDispatcher — subscriptionCountForType", () => {
  it("counts subscriptions correctly", () => {
    const d = new EventDispatcher();
    d.subscribe("a", vi.fn());
    d.subscribe("a", vi.fn());
    d.subscribe("b", vi.fn());
    d.subscribe("*", vi.fn()); // wildcard counts for "a"
    expect(d.subscriptionCountForType("a")).toBe(3); // 2 + wildcard
    expect(d.subscriptionCountForType("b")).toBe(2); // 1 + wildcard
  });
});
