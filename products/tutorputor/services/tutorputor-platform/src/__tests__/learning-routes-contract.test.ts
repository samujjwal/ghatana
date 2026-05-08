/**
 * Real Contract Tests for Learning Routes
 *
 * These tests make actual HTTP calls to validate API contracts.
 * Unlike mocked integration tests, these validate real request/response behavior.
 *
 * @doc.type test-suite
 * @doc.purpose Real contract tests for learning routes HTTP API
 * @doc.layer product
 * @doc.pattern ContractTest
 */

import { describe, it, expect, beforeAll, afterAll, beforeEach } from "vitest";
import { createServer } from "../setup.js";
import type { FastifyInstance } from "fastify";
import type { PrismaClient } from "@tutorputor/core/db";

describe("Learning Routes - Real Contract Tests", () => {
  let app: FastifyInstance;
  let prisma: PrismaClient;

  beforeAll(async () => {
    const server = await createServer();
    app = server.app;
    prisma = server.prisma;
    await app.ready();
  });

  afterAll(async () => {
    await app.close();
  });

  beforeEach(async () => {
    // Clean up test data before each test
    await prisma.user.deleteMany({
      where: { email: { startsWith: "contract-test-" } },
    });
    await prisma.module.deleteMany({
      where: { title: { startsWith: "Contract Test" } },
    });
    await prisma.enrollment.deleteMany({
      where: { userId: { startsWith: "contract-user-" } },
    });
  });

  describe("GET /api/v1/learning/dashboard - Contract Validation", () => {
    it("returns canonical error envelope for unauthenticated requests", async () => {
      const response = await app.inject({
        method: "GET",
        url: "/api/v1/learning/dashboard",
      });

      expect(response.statusCode).toBe(401);
      const body = JSON.parse(response.body);
      expect(body).toHaveProperty("error");
      expect(body.error).toHaveProperty("code");
      expect(body.error).toHaveProperty("message");
      expect(body).toHaveProperty("traceId");
      expect(body).toHaveProperty("timestamp");
      expect(body).toHaveProperty("statusCode", 401);
    });

    it("returns dashboard structure for authenticated user", async () => {
      // Create test user
      const user = await prisma.user.create({
        data: {
          id: "contract-user-1",
          email: "contract-test-1@example.com",
          displayName: "Contract Test User",
          role: "STUDENT",
        },
      });

      // Create test module
      const module = await prisma.module.create({
        data: {
          id: "contract-module-1",
          tenantId: "contract-tenant-1",
          title: "Contract Test Module",
          description: "Test module for contract validation",
          domain: "TECH",
          status: "PUBLISHED",
        },
      });

      // Create enrollment
      await prisma.enrollment.create({
        data: {
          id: "contract-enrollment-1",
          tenantId: "contract-tenant-1",
          userId: user.id,
          moduleId: module.id,
          status: "IN_PROGRESS",
          progressPercent: 50,
        },
      });

      // Create JWT token for user
      const jwt = require("jsonwebtoken");
      const token = jwt.sign(
        { userId: user.id, tenantId: "contract-tenant-1", role: "STUDENT" },
        process.env.JWT_SECRET || "test-secret",
        { expiresIn: "1h" },
      );

      const response = await app.inject({
        method: "GET",
        url: "/api/v1/learning/dashboard",
        headers: {
          authorization: `Bearer ${token}`,
          "x-tenant-id": "contract-tenant-1",
          "x-user-id": user.id,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body).toHaveProperty("user");
      expect(body).toHaveProperty("currentEnrollments");
      expect(body).toHaveProperty("recommendedModules");
      expect(body.user).toHaveProperty("id");
      expect(body.user).toHaveProperty("email");
      expect(body.user).toHaveProperty("displayName");
      expect(Array.isArray(body.currentEnrollments)).toBe(true);
      expect(Array.isArray(body.recommendedModules)).toBe(true);
    });
  });

  describe("POST /api/v1/learning/enrollments - Contract Validation", () => {
    it("validates request schema - missing moduleId returns 400", async () => {
      const jwt = require("jsonwebtoken");
      const token = jwt.sign(
        { userId: "contract-user-1", tenantId: "contract-tenant-1", role: "STUDENT" },
        process.env.JWT_SECRET || "test-secret",
        { expiresIn: "1h" },
      );

      const response = await app.inject({
        method: "POST",
        url: "/api/v1/learning/enrollments",
        headers: {
          authorization: `Bearer ${token}`,
          "x-tenant-id": "contract-tenant-1",
          "x-user-id": "contract-user-1",
        },
        body: {}, // Missing moduleId
      });

      expect(response.statusCode).toBe(400);
      const body = JSON.parse(response.body);
      expect(body).toHaveProperty("error");
      expect(body.error).toHaveProperty("code");
    });

    it("creates enrollment and returns 201 on success", async () => {
      // Create test user and module
      const user = await prisma.user.create({
        data: {
          id: "contract-user-2",
          email: "contract-test-2@example.com",
          displayName: "Contract Test User 2",
          role: "STUDENT",
        },
      });

      const module = await prisma.module.create({
        data: {
          id: "contract-module-2",
          tenantId: "contract-tenant-1",
          title: "Contract Test Module 2",
          description: "Test module for contract validation",
          domain: "TECH",
          status: "PUBLISHED",
        },
      });

      const jwt = require("jsonwebtoken");
      const token = jwt.sign(
        { userId: user.id, tenantId: "contract-tenant-1", role: "STUDENT" },
        process.env.JWT_SECRET || "test-secret",
        { expiresIn: "1h" },
      );

      const response = await app.inject({
        method: "POST",
        url: "/api/v1/learning/enrollments",
        headers: {
          authorization: `Bearer ${token}`,
          "x-tenant-id": "contract-tenant-1",
          "x-user-id": user.id,
        },
        body: {
          moduleId: module.id,
        },
      });

      expect(response.statusCode).toBe(201);
      const body = JSON.parse(response.body);
      expect(body).toHaveProperty("id");
      expect(body).toHaveProperty("moduleId");
      expect(body).toHaveProperty("status");
      expect(body.moduleId).toBe(module.id);
    });
  });

  describe("POST /api/v1/learning/events - Contract Validation", () => {
    it("validates event schema and returns 204 on success", async () => {
      const jwt = require("jsonwebtoken");
      const token = jwt.sign(
        { userId: "contract-user-1", tenantId: "contract-tenant-1", role: "STUDENT" },
        process.env.JWT_SECRET || "test-secret",
        { expiresIn: "1h" },
      );

      const response = await app.inject({
        method: "POST",
        url: "/api/v1/learning/events",
        headers: {
          authorization: `Bearer ${token}`,
          "x-tenant-id": "contract-tenant-1",
          "x-user-id": "contract-user-1",
        },
        body: {
          event: {
            type: "module_viewed",
            userId: "contract-user-1",
            timestamp: new Date().toISOString(),
            schemaVersion: "1.0.0",
          },
        },
      });

      expect(response.statusCode).toBe(204);
    });

    it("rejects events with invalid schema version", async () => {
      const jwt = require("jsonwebtoken");
      const token = jwt.sign(
        { userId: "contract-user-1", tenantId: "contract-tenant-1", role: "STUDENT" },
        process.env.JWT_SECRET || "test-secret",
        { expiresIn: "1h" },
      );

      const response = await app.inject({
        method: "POST",
        url: "/api/v1/learning/events",
        headers: {
          authorization: `Bearer ${token}`,
          "x-tenant-id": "contract-tenant-1",
          "x-user-id": "contract-user-1",
        },
        body: {
          event: {
            type: "module_viewed",
            userId: "contract-user-1",
            timestamp: new Date().toISOString(),
            schemaVersion: "0.0.0", // Invalid version
          },
        },
      });

      expect(response.statusCode).toBe(400);
      const body = JSON.parse(response.body);
      expect(body).toHaveProperty("error");
    });
  });
});
