/**
 * Tests for TypeScript fixture handler.
 */

import { describe, it, expect } from "vitest";
import { handler } from "../src/main.js";

describe("Handler", () => {
  it("should return a successful response", async () => {
    const result = await handler({});
    expect(result.statusCode).toBe(200);
    expect(result.body).toContain("Hello");
  });
});
