import { describe, expect, it } from "vitest";
import { getModelBlock, readPrismaSchema } from "./prisma-schema-contract-utils";

describe("GDPR delete cascade contract", () => {
  it("keeps cascade relations for user-scoped personal data models", () => {
    const schema = readPrismaSchema();

    const userModel = getModelBlock(schema, "User");
    const learnerProfileModel = getModelBlock(schema, "LearnerProfile");
    const learnerMasteryModel = getModelBlock(schema, "LearnerMastery");
    const preferenceChangeModel = getModelBlock(schema, "PreferenceChange");
    const ssoUserLinkModel = getModelBlock(schema, "SsoUserLink");

    expect(userModel).toContain("onDelete: Cascade");
    expect(learnerProfileModel).toContain("onDelete: Cascade");
    expect(learnerMasteryModel).toContain("onDelete: Cascade");
    expect(preferenceChangeModel).toContain("onDelete: Cascade");
    expect(ssoUserLinkModel).toContain("onDelete: Cascade");
  });
});
