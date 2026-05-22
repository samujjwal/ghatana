/**
 * Integration tests for TypeScript fixture service.
 */

import { describe, it, expect } from "vitest";
import { handler, healthHandler } from "../src/main.js";

describe("TypeScript Fixture Service", () => {
  it("should handle basic request", async () => {
    const result = await handler({});
    expect(result.statusCode).toBe(200);
    const body = JSON.parse(result.body);
    expect(body.message).toBe("Hello, World!");
  });

  it("should return healthy status", async () => {
    const result = await healthHandler();
    expect(result.statusCode).toBe(200);
    const body = JSON.parse(result.body);
    expect(body.status).toBe("healthy");
    expect(body.message).toBe("TypeScript Fixture Service");
  });
});
