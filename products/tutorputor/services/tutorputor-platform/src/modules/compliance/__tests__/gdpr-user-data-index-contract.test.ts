import { describe, expect, it } from "vitest";

import { getModelBlock, readPrismaSchema } from "./prisma-schema-contract-utils";

describe("GDPR user data index contract", () => {
  it("retains index paths needed for deletion lookups", () => {
    const schema = readPrismaSchema();
    const dataDeletionModel = getModelBlock(schema, "DataDeletionRequest");
    const verificationModel = getModelBlock(schema, "DeletionVerification");

    expect(dataDeletionModel).toContain("@@index([tenantId, status])");
    expect(dataDeletionModel).toContain("@@index([tenantId, userId])");
    expect(verificationModel).toContain("@@index([userId])");
  });
});
