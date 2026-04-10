import { describe, it, expect } from "vitest";
import { z } from "zod";

import {
  validatePlatformEvent,
  validatePlatformEventWithData,
  safeParsePlatformEvent,
  EventValidationError,
} from "../validation";
import type { PlatformEvent } from "../types";

function makeEvent(overrides?: Partial<PlatformEvent>): PlatformEvent {
  return {
    id: "val-001",
    type: "order.placed",
    timestamp: 1_700_000_000_000,
    source: { type: "client", id: "checkout-ui" },
    data: { orderId: "o-99", amount: 150 },
    ...overrides,
  };
}

describe("validatePlatformEvent", () => {
  it("returns the event when valid", () => {
    const event = makeEvent();
    const result = validatePlatformEvent(event);
    expect(result.id).toBe(event.id);
  });

  it("throws EventValidationError for missing id", () => {
    const { id: _, ...noId } = makeEvent();
    expect(() => validatePlatformEvent(noId)).toThrow(EventValidationError);
  });

  it("includes zodIssues in the error", () => {
    try {
      validatePlatformEvent({});
    } catch (err) {
      expect(err).toBeInstanceOf(EventValidationError);
      expect((err as EventValidationError).issues.length).toBeGreaterThan(0);
    }
  });
});

describe("validatePlatformEventWithData", () => {
  const orderSchema = z.object({
    orderId: z.string(),
    amount: z.number().positive(),
  });

  it("returns typed event when data matches schema", () => {
    const event = makeEvent();
    const result = validatePlatformEventWithData(event, orderSchema);
    expect(result.data.orderId).toBe("o-99");
    expect(result.data.amount).toBe(150);
  });

  it("throws EventValidationError when data does not match schema", () => {
    const event = makeEvent({ data: { orderId: 123, amount: -1 } });
    expect(() => validatePlatformEventWithData(event, orderSchema)).toThrow(
      EventValidationError
    );
  });
});

describe("safeParsePlatformEvent", () => {
  it("returns a PlatformEvent for valid input", () => {
    const event = makeEvent();
    const result = safeParsePlatformEvent(event);
    expect(result).not.toBeNull();
    expect(result!.id).toBe(event.id);
  });

  it("returns null for invalid input", () => {
    expect(safeParsePlatformEvent({ id: "x" })).toBeNull();
    expect(safeParsePlatformEvent(null)).toBeNull();
  });
});
