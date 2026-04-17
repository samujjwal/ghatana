/**
 * GDPR Delete Cascade Integration Test
 *
 * Verifies that deleting a user cascades properly through all related data
 * in the actual database (Postgres).
 *
 * @doc.type test
 * @doc.purpose Integration test for GDPR delete cascade behavior
 * @doc.layer platform
 * @doc.pattern IntegrationTest
 */

import { describe, it, expect, beforeAll, afterAll } from "vitest";
import { PrismaClient } from "@tutorputor/core/db";

const prisma = new PrismaClient();

describe("GDPR delete cascade integration", () => {
  let testUserId: string;
  let testTenantId: string;
  let learnerProfileId: string;
  let learnerMasteryId: string;
  let preferenceChangeId: string;

  beforeAll(async () => {
    // Create test tenant
    const tenant = await prisma.tenant.create({
      data: {
        id: "test-tenant-gdpr-cascade",
        name: "Test Tenant GDPR Cascade",
        slug: "test-tenant-gdpr-cascade",
      },
    });
    testTenantId = tenant.id;

    // Create test user with related data
    const user = await prisma.user.create({
      data: {
        id: "test-user-gdpr-cascade",
        email: "test-gdpr-cascade@example.com",
        tenantId: testTenantId,
        isActive: true,
        // Create learner profile
        learnerProfile: {
          create: {
            id: "test-learner-profile-gdpr",
            gradeLevel: 9,
            learningGoals: ["math", "science"],
          },
        },
      },
    });
    testUserId = user.id;

    // Get the created learner profile ID
    const learnerProfile = await prisma.learnerProfile.findFirst({
      where: { userId: testUserId },
    });
    learnerProfileId = learnerProfile!.id;

    // Create learner mastery record
    const mastery = await prisma.learnerMastery.create({
      data: {
        id: "test-mastery-gdpr",
        learnerProfileId: learnerProfileId,
        conceptId: "test-concept-1",
        masteryLevel: 0.5,
        lastAssessedAt: new Date(),
      },
    });
    learnerMasteryId = mastery.id;

    // Create preference change record
    const prefChange = await prisma.preferenceChange.create({
      data: {
        id: "test-pref-change-gdpr",
        userId: testUserId,
        key: "theme",
        oldValue: "light",
        newValue: "dark",
        changedAt: new Date(),
      },
    });
    preferenceChangeId = prefChange.id;
  });

  afterAll(async () => {
    // Cleanup in case test fails
    try {
      await prisma.preferenceChange.deleteMany({
        where: { userId: testUserId },
      });
      await prisma.learnerMastery.deleteMany({
        where: { learnerProfileId },
      });
      await prisma.learnerProfile.deleteMany({
        where: { userId: testUserId },
      });
      await prisma.user.deleteMany({
        where: { id: testUserId },
      });
      await prisma.tenant.deleteMany({
        where: { id: testTenantId },
      });
    } catch (_error) {
      // Ignore cleanup errors
    }
    await prisma.$disconnect();
  });

  it("should cascade delete all user-related data when user is deleted", async () => {
    // Verify all data exists before deletion
    const userBefore = await prisma.user.findUnique({
      where: { id: testUserId },
    });
    expect(userBefore).not.toBeNull();

    const learnerProfileBefore = await prisma.learnerProfile.findUnique({
      where: { id: learnerProfileId },
    });
    expect(learnerProfileBefore).not.toBeNull();

    const learnerMasteryBefore = await prisma.learnerMastery.findUnique({
      where: { id: learnerMasteryId },
    });
    expect(learnerMasteryBefore).not.toBeNull();

    const preferenceChangeBefore = await prisma.preferenceChange.findUnique({
      where: { id: preferenceChangeId },
    });
    expect(preferenceChangeBefore).not.toBeNull();

    // Delete the user (this should cascade)
    await prisma.user.delete({
      where: { id: testUserId },
    });

    // Verify user is deleted
    const userAfter = await prisma.user.findUnique({
      where: { id: testUserId },
    });
    expect(userAfter).toBeNull();

    // Verify learner profile is cascaded (deleted)
    const learnerProfileAfter = await prisma.learnerProfile.findUnique({
      where: { id: learnerProfileId },
    });
    expect(learnerProfileAfter).toBeNull();

    // Verify learner mastery is cascaded (deleted via learner profile)
    const learnerMasteryAfter = await prisma.learnerMastery.findUnique({
      where: { id: learnerMasteryId },
    });
    expect(learnerMasteryAfter).toBeNull();

    // Verify preference change is cascaded (deleted)
    const preferenceChangeAfter = await prisma.preferenceChange.findUnique({
      where: { id: preferenceChangeId },
    });
    expect(preferenceChangeAfter).toBeNull();
  });

  it("should handle cascade delete through multiple relationship levels", async () => {
    // This test verifies that cascade works through deeper relationships
    // Create a new user with nested data
    const nestedUser = await prisma.user.create({
      data: {
        id: "test-user-nested-cascade",
        email: "test-nested@example.com",
        tenantId: testTenantId,
        isActive: true,
        learnerProfile: {
          create: {
            id: "test-learner-profile-nested",
            gradeLevel: 10,
            learnerMastery: {
              create: [
                {
                  id: "test-mastery-nested-1",
                  conceptId: "concept-1",
                  masteryLevel: 0.7,
                  lastAssessedAt: new Date(),
                },
                {
                  id: "test-mastery-nested-2",
                  conceptId: "concept-2",
                  masteryLevel: 0.8,
                  lastAssessedAt: new Date(),
                },
              ],
            },
          },
        },
      },
    });

    // Verify nested data exists
    const masteryCountBefore = await prisma.learnerMastery.count({
      where: { learnerProfileId: "test-learner-profile-nested" },
    });
    expect(masteryCountBefore).toBe(2);

    // Delete user (should cascade through all levels)
    await prisma.user.delete({
      where: { id: "test-user-nested-cascade" },
    });

    // Verify all nested data is deleted
    const learnerProfileAfter = await prisma.learnerProfile.findUnique({
      where: { id: "test-learner-profile-nested" },
    });
    expect(learnerProfileAfter).toBeNull();

    const masteryCountAfter = await prisma.learnerMastery.count({
      where: { learnerProfileId: "test-learner-profile-nested" },
    });
    expect(masteryCountAfter).toBe(0);
  });
});
