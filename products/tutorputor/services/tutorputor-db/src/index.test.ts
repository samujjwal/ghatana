import { describe, it, expect } from "vitest";
import { createPrismaClient, DEFAULT_TENANT_ID, DEFAULT_USER_ID } from "./index.js";

describe("TutorPutor DB exports", () => {
  it("exposes factory helpers", () => {
    expect(typeof createPrismaClient).toBe("function");
    expect(DEFAULT_TENANT_ID).toBeDefined();
    expect(DEFAULT_USER_ID).toBeDefined();
  });
});
