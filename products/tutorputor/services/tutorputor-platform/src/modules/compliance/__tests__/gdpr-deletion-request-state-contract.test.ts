import { describe, expect, it } from "vitest";

import { getModelBlock, readPrismaSchema } from "./prisma-schema-contract-utils";

describe("GDPR deletion request state contract", () => {
  it("retains required default state and timestamps", () => {
    const schema = readPrismaSchema();
    const model = getModelBlock(schema, "DataDeletionRequest");

    expect(model).toContain('status              String    @default("scheduled")');
    expect(model).toContain("requestedAt         DateTime  @default(now())");
    expect(model).toContain("scheduledDeletionAt DateTime");
    expect(model).toContain("completedAt         DateTime?");
  });
});
