import { describe, it, expect } from "vitest";
import { createStateMachine } from "../machine";

type TrafficState = "red" | "yellow" | "green";
type TrafficEvent = "go" | "slow" | "stop";

const trafficTransitions = [
  { from: "red" as TrafficState, to: "green" as TrafficState, on: "go" as TrafficEvent },
  { from: "green" as TrafficState, to: "yellow" as TrafficState, on: "slow" as TrafficEvent },
  { from: "yellow" as TrafficState, to: "red" as TrafficState, on: "stop" as TrafficEvent },
] as const;

describe("createStateMachine", () => {
  it("starts in the initial state", () => {
    const m = createStateMachine<TrafficState, TrafficEvent>("red", trafficTransitions);
    expect(m.currentState).toBe("red");
  });

  it("transitions to the next state on valid event", () => {
    const m = createStateMachine<TrafficState, TrafficEvent>("red", trafficTransitions);
    const next = m.transition("go");
    expect(next.currentState).toBe("green");
  });

  it("chained transitions work", () => {
    const m = createStateMachine<TrafficState, TrafficEvent>("red", trafficTransitions);
    const final = m.transition("go").transition("slow").transition("stop");
    expect(final.currentState).toBe("red");
  });

  it("is immutable — original machine unchanged after transition", () => {
    const m = createStateMachine<TrafficState, TrafficEvent>("red", trafficTransitions);
    m.transition("go");
    expect(m.currentState).toBe("red");
  });

  it("throws on invalid event from current state", () => {
    const m = createStateMachine<TrafficState, TrafficEvent>("red", trafficTransitions);
    expect(() => m.transition("slow")).toThrow();
  });

  it("canTransition returns true for valid event", () => {
    const m = createStateMachine<TrafficState, TrafficEvent>("red", trafficTransitions);
    expect(m.canTransition("go")).toBe(true);
    expect(m.canTransition("slow")).toBe(false);
  });

  it("validEvents returns only allowed events", () => {
    const m = createStateMachine<TrafficState, TrafficEvent>("green", trafficTransitions);
    expect(m.validEvents()).toEqual(["slow"]);
  });
});

describe("StateMachine guards", () => {
  type OrderState = "pending" | "approved" | "rejected";
  type OrderEvent = "approve" | "reject";

  it("guard blocks transition when condition is false", () => {
    const context = { isAdmin: false };
    const transitions = [
      {
        from: "pending" as OrderState,
        to: "approved" as OrderState,
        on: "approve" as OrderEvent,
        guard: (ctx: Record<string, unknown>) => ctx["isAdmin"] === true,
      },
    ];
    const m = createStateMachine<OrderState, OrderEvent>("pending", transitions, context as Record<string, unknown>);
    expect(m.canTransition("approve")).toBe(false);
    expect(() => m.transition("approve")).toThrow();
  });

  it("guard allows transition when condition is true", () => {
    const context = { isAdmin: true };
    const transitions = [
      {
        from: "pending" as OrderState,
        to: "approved" as OrderState,
        on: "approve" as OrderEvent,
        guard: (ctx: Record<string, unknown>) => ctx["isAdmin"] === true,
      },
    ];
    const m = createStateMachine<OrderState, OrderEvent>("pending", transitions, context as Record<string, unknown>);
    expect(m.canTransition("approve")).toBe(true);
    const next = m.transition("approve");
    expect(next.currentState).toBe("approved");
  });
});
