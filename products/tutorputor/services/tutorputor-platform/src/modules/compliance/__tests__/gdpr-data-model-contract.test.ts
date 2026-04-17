import { describe, expect, it } from "vitest";

import { getModelBlock, readPrismaSchema } from "./prisma-schema-contract-utils";

describe("GDPR data model contract", () => {
  it("retains deletion-request fields required for delayed purge workflows", () => {
    const schema = readPrismaSchema();
    const dataDeletionRequestModel = getModelBlock(schema, "DataDeletionRequest");

    expect(dataDeletionRequestModel).toContain("tenantId            String");
    expect(dataDeletionRequestModel).toContain("userId              String");
    expect(dataDeletionRequestModel).toContain("scheduledDeletionAt DateTime");
    expect(dataDeletionRequestModel).toContain("retentionDays       Int");
    expect(dataDeletionRequestModel).toContain("@@index([tenantId, status])");
    expect(dataDeletionRequestModel).toContain("@@index([tenantId, userId])");
  });

  it("retains deletion-verification token constraints", () => {
    const schema = readPrismaSchema();
    const deletionVerificationModel = getModelBlock(schema, "DeletionVerification");

    expect(deletionVerificationModel).toContain("token     String   @unique");
    expect(deletionVerificationModel).toContain("expiresAt DateTime");
    expect(deletionVerificationModel).toContain("@@index([userId])");
  });
});
