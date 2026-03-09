/**
 * Integration tests for Flashit Web API
 * Tests the complete authentication and Moment capture flow
 */

import { describe, it, expect, beforeAll, afterAll } from "vitest";
import { buildServer } from "../server";
import { prisma } from "../lib/prisma";

describe("Flashit Web API Integration Tests", () => {
  const app = buildServer();

  beforeAll(async () => {
    await app.ready();
  });

  afterAll(async () => {
    await app.close();
  });

  describe("Health Check", () => {
    it("should return healthy status", async () => {
      const response = await app.inject({
        method: "GET",
        url: "/health",
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.status).toBe("ok");
      expect(body.service).toBe("flashit-web-api");
    });
  });

  describe("Authentication Flow", () => {
    const testUser = {
      email: `test-${Date.now()}@example.com`,
      password: "TestPassword123!",
      displayName: "Test User",
    };

    let authToken: string;
    let userId: string;

    it("should register a new user", async () => {
      const response = await app.inject({
        method: "POST",
        url: "/auth/register",
        payload: testUser,
      });

      expect(response.statusCode).toBe(201);
      const body = JSON.parse(response.body);
      expect(body.user.email).toBe(testUser.email);
      expect(body.user.displayName).toBe(testUser.displayName);
      expect(body.token).toBeDefined();

      authToken = body.token;
      userId = body.user.id;
    });

    it("should reject duplicate email registration", async () => {
      const response = await app.inject({
        method: "POST",
        url: "/auth/register",
        payload: testUser,
      });

      expect(response.statusCode).toBe(409);
      const body = JSON.parse(response.body);
      expect(body.error).toBe("User already exists");
    });

    it("should login with correct credentials", async () => {
      const response = await app.inject({
        method: "POST",
        url: "/auth/login",
        payload: {
          email: testUser.email,
          password: testUser.password,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.user.email).toBe(testUser.email);
      expect(body.token).toBeDefined();
    });

    it("should reject login with wrong password", async () => {
      const response = await app.inject({
        method: "POST",
        url: "/auth/login",
        payload: {
          email: testUser.email,
          password: "WrongPassword",
        },
      });

      expect(response.statusCode).toBe(401);
    });

    it("should get current user with valid token", async () => {
      const response = await app.inject({
        method: "GET",
        url: "/auth/me",
        headers: {
          authorization: `Bearer ${authToken}`,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.user.email).toBe(testUser.email);
    });

    it("should reject requests without token", async () => {
      const response = await app.inject({
        method: "GET",
        url: "/auth/me",
      });

      expect(response.statusCode).toBe(401);
    });

    // Cleanup
    afterAll(async () => {
      if (userId) {
        await prisma.user.delete({ where: { id: userId } });
      }
    });
  });

  describe("Spheres Management", () => {
    let authToken: string;
    let userId: string;
    let sphereId: string;

    beforeAll(async () => {
      // Create test user
      const response = await app.inject({
        method: "POST",
        url: "/auth/register",
        payload: {
          email: `sphere-test-${Date.now()}@example.com`,
          password: "TestPassword123!",
          displayName: "Sphere Test User",
        },
      });

      const body = JSON.parse(response.body);
      authToken = body.token;
      userId = body.user.id;
    });

    it("should create a new Sphere", async () => {
      const response = await app.inject({
        method: "POST",
        url: "/api/spheres",
        headers: {
          authorization: `Bearer ${authToken}`,
        },
        payload: {
          name: "Test Work Sphere",
          description: "Work-related test moments",
          type: "WORK",
          visibility: "PRIVATE",
        },
      });

      expect(response.statusCode).toBe(201);
      const body = JSON.parse(response.body);
      expect(body.sphere.name).toBe("Test Work Sphere");
      expect(body.sphere.type).toBe("WORK");
      sphereId = body.sphere.id;
    });

    it("should list user's Spheres", async () => {
      const response = await app.inject({
        method: "GET",
        url: "/api/spheres",
        headers: {
          authorization: `Bearer ${authToken}`,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.spheres).toBeInstanceOf(Array);
      expect(body.spheres.length).toBeGreaterThan(0);

      // Should have default "Personal" sphere + newly created
      const sphereNames = body.spheres.map((s: any) => s.name);
      expect(sphereNames).toContain("Personal");
      expect(sphereNames).toContain("Test Work Sphere");
    });

    it("should get Sphere by ID", async () => {
      const response = await app.inject({
        method: "GET",
        url: `/api/spheres/${sphereId}`,
        headers: {
          authorization: `Bearer ${authToken}`,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.sphere.id).toBe(sphereId);
      expect(body.sphere.userRole).toBe("OWNER");
    });

    it("should update Sphere", async () => {
      const response = await app.inject({
        method: "PATCH",
        url: `/api/spheres/${sphereId}`,
        headers: {
          authorization: `Bearer ${authToken}`,
        },
        payload: {
          name: "Updated Work Sphere",
          description: "Updated description",
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.sphere.name).toBe("Updated Work Sphere");
    });

    // Cleanup
    afterAll(async () => {
      if (userId) {
        await prisma.user.delete({ where: { id: userId } });
      }
    });
  });

  describe("Moments Capture and Retrieval", () => {
    let authToken: string;
    let userId: string;
    let sphereId: string;
    let momentId: string;

    beforeAll(async () => {
      // Create test user
      const authResponse = await app.inject({
        method: "POST",
        url: "/auth/register",
        payload: {
          email: `moment-test-${Date.now()}@example.com`,
          password: "TestPassword123!",
          displayName: "Moment Test User",
        },
      });

      const authBody = JSON.parse(authResponse.body);
      authToken = authBody.token;
      userId = authBody.user.id;

      // Get the default Personal sphere
      const spheresResponse = await app.inject({
        method: "GET",
        url: "/api/spheres",
        headers: {
          authorization: `Bearer ${authToken}`,
        },
      });

      const spheresBody = JSON.parse(spheresResponse.body);
      sphereId = spheresBody.spheres[0].id;
    });

    it("should create a text Moment", async () => {
      const response = await app.inject({
        method: "POST",
        url: "/api/moments",
        headers: {
          authorization: `Bearer ${authToken}`,
        },
        payload: {
          sphereId,
          content: {
            text: "Had a great meeting with the team today. We discussed the new feature roadmap.",
            type: "TEXT",
          },
          signals: {
            emotions: ["happy", "productive"],
            tags: ["work", "meeting", "planning"],
            importance: 4,
            sentimentScore: 0.8,
          },
          capturedAt: new Date().toISOString(),
        },
      });

      expect(response.statusCode).toBe(201);
      const body = JSON.parse(response.body);
      expect(body.moment.contentText).toContain("great meeting");
      expect(body.moment.emotions).toContain("happy");
      expect(body.moment.tags).toContain("work");
      momentId = body.moment.id;
    });

    it("should reject Moment creation without Sphere access", async () => {
      const fakeSphereId = "00000000-0000-0000-0000-000000000000";

      const response = await app.inject({
        method: "POST",
        url: "/api/moments",
        headers: {
          authorization: `Bearer ${authToken}`,
        },
        payload: {
          sphereId: fakeSphereId,
          content: {
            text: "This should fail",
            type: "TEXT",
          },
        },
      });

      expect(response.statusCode).toBe(403);
    });

    it("should get Moment by ID", async () => {
      const response = await app.inject({
        method: "GET",
        url: `/api/moments/${momentId}`,
        headers: {
          authorization: `Bearer ${authToken}`,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.moment.id).toBe(momentId);
      expect(body.canEdit).toBe(true);
    });

    it("should search Moments with filters", async () => {
      // Create a few more Moments
      await app.inject({
        method: "POST",
        url: "/api/moments",
        headers: { authorization: `Bearer ${authToken}` },
        payload: {
          sphereId,
          content: { text: "Feeling anxious about the presentation", type: "TEXT" },
          signals: { emotions: ["anxious"], tags: ["work", "presentation"] },
        },
      });

      await app.inject({
        method: "POST",
        url: "/api/moments",
        headers: { authorization: `Bearer ${authToken}` },
        payload: {
          sphereId,
          content: { text: "Family dinner was wonderful", type: "TEXT" },
          signals: { emotions: ["happy"], tags: ["family"] },
        },
      });

      // Search by tag
      const response = await app.inject({
        method: "GET",
        url: "/api/moments?tags=work",
        headers: {
          authorization: `Bearer ${authToken}`,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.moments.length).toBeGreaterThan(0);

      // All results should have "work" tag
      body.moments.forEach((m: any) => {
        expect(m.tags).toContain("work");
      });
    });

    it("should search Moments by text query", async () => {
      const response = await app.inject({
        method: "GET",
        url: "/api/moments?query=meeting",
        headers: {
          authorization: `Bearer ${authToken}`,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.moments.length).toBeGreaterThan(0);
      expect(body.moments[0].contentText).toContain("meeting");
    });

    it("should delete a Moment", async () => {
      const response = await app.inject({
        method: "DELETE",
        url: `/api/moments/${momentId}`,
        headers: {
          authorization: `Bearer ${authToken}`,
        },
      });

      expect(response.statusCode).toBe(204);

      // Verify soft delete
      const getResponse = await app.inject({
        method: "GET",
        url: `/api/moments/${momentId}`,
        headers: {
          authorization: `Bearer ${authToken}`,
        },
      });

      expect(getResponse.statusCode).toBe(404);
    });

    // Cleanup
    afterAll(async () => {
      if (userId) {
        await prisma.user.delete({ where: { id: userId } });
      }
    });
  });
});

