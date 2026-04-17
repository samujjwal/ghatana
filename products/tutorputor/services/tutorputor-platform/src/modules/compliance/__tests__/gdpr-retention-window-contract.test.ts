import { describe, expect, it } from "vitest";

import { getModelBlock, readPrismaSchema } from "./prisma-schema-contract-utils";

describe("GDPR retention window contract", () => {
  it("keeps retention defaults and optional completion metadata", () => {
    const schema = readPrismaSchema();
    const model = getModelBlock(schema, "DataDeletionRequest");

    expect(model).toContain("retentionDays       Int       @default(30)");
    expect(model).toContain("scheduledDeletionAt DateTime");
    expect(model).toContain("completedAt         DateTime?");
  });
});
