/**
 * @doc.type test-suite
 * @doc.purpose Security tests for session ownership and RBAC bypass prevention
 * @doc.layer application
 * @doc.pattern Security Test
 *
 * Tests security-critical authorization scenarios:
 * - Copilot session ownership enforcement
 * - Role-based access control (RBAC) for force transitions
 * - Input validation for stage transitions
 * - FromStage validation to prevent state manipulation
 */

import {
  describe,
  it,
  expect,
  beforeAll,
  afterAll,
  beforeEach,
} from 'vitest';
import { createApp } from '../index';
import type { FastifyInstance } from 'fastify';
import { getPrismaClient } from '../database/client';

// JWT token generation helper
function generateJWT(
  userId: string,
  role: string = 'user',
  tenantId: string = 'tenant-1'
): string {
  const header = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64');
  const payload = Buffer.from(
    JSON.stringify({ sub: userId, tenantId, role })
  ).toString('base64');
  const signature = Buffer.from('test-signature').toString('base64');
  return `${header}.${payload}.${signature}`;
}

describe('Security: Session Ownership and RBAC', () => {
  let app: FastifyInstance;
  let prisma: any;

  beforeAll(async () => {
    process.env.JWT_ACCESS_SECRET = 'test-secret-for-security-tests';
    process.env.DATABASE_URL = 'postgresql://test:test@localhost:5432/test';
    app = await createApp();
    prisma = getPrismaClient();
  });

  afterAll(async () => {
    await app.close();
  });

  beforeEach(async () => {
    // Clean up test data
    try {
      await prisma.copilotSession.deleteMany({});
      await prisma.project.deleteMany({});
      await prisma.workspace.deleteMany({});
      await prisma.user.deleteMany({});
    } catch (error) {
      // Ignore cleanup errors
    }
  });

  describe('Copilot Session Ownership', () => {
    it('should allow User A to read their own copilot session', async () => {
      // Create test user
      const userA = await prisma.user.create({
        data: {
          id: 'user-a',
          email: 'user-a@example.com',
          name: 'User A',
        },
      });

      // Create copilot session for User A
      const session = await prisma.copilotSession.create({
        data: {
          id: 'session-1',
          userId: userA.id,
          agentName: 'test-agent',
          status: 'ACTIVE',
        },
      });

      const tokenA = generateJWT('user-a', 'user');

      const response = await app.inject({
        method: 'GET',
        url: `/copilot/session/${session.id}`,
        headers: { authorization: `Bearer ${tokenA}` },
      });

      expect(response.statusCode).toBe(200);
      expect(response.json()).toHaveProperty('id', session.id);
    });

    it('should prevent User B from reading User A copilot session (403 Forbidden)', async () => {
      // Create test users
      const userA = await prisma.user.create({
        data: {
          id: 'user-a',
          email: 'user-a@example.com',
          name: 'User A',
        },
      });

      const userB = await prisma.user.create({
        data: {
          id: 'user-b',
          email: 'user-b@example.com',
          name: 'User B',
        },
      });

      // Create copilot session for User A
      const session = await prisma.copilotSession.create({
        data: {
          id: 'session-1',
          userId: userA.id,
          agentName: 'test-agent',
          status: 'ACTIVE',
        },
      });

      // User B tries to access User A's session
      const tokenB = generateJWT('user-b', 'user');

      const response = await app.inject({
        method: 'GET',
        url: `/copilot/session/${session.id}`,
        headers: { authorization: `Bearer ${tokenB}` },
      });

      expect(response.statusCode).toBe(403);
      expect(response.json()).toHaveProperty('error');
    });
  });

  describe('RBAC: Force Stage Transitions', () => {
    it('should prevent EDITOR user from force transitioning stages (403 Forbidden)', async () => {
      // Create test workspace and project
      const workspace = await prisma.workspace.create({
        data: {
          id: 'ws-1',
          name: 'Test Workspace',
          ownerId: 'user-a',
          tenantId: 'tenant-1',
        },
      });

      const project = await prisma.project.create({
        data: {
          id: 'proj-1',
          name: 'Test Project',
          workspaceId: workspace.id,
          lifecyclePhase: 'INTENT',
          tenantId: 'tenant-1',
        },
      });

      // EDITOR user attempts force transition
      const editorToken = generateJWT('user-a', 'editor');

      const response = await app.inject({
        method: 'POST',
        url: `/lifecycle/projects/${project.id}/stages/transition`,
        headers: { authorization: `Bearer ${editorToken}` },
        payload: {
          fromStage: 0,
          toStage: 1,
          force: true,
        },
      });

      expect(response.statusCode).toBe(403);
      expect(response.json()).toHaveProperty('error');
    });

    it('should allow ADMIN user to force transition stages (200 OK)', async () => {
      // Create test workspace and project
      const workspace = await prisma.workspace.create({
        data: {
          id: 'ws-1',
          name: 'Test Workspace',
          ownerId: 'user-admin',
          tenantId: 'tenant-1',
        },
      });

      const project = await prisma.project.create({
        data: {
          id: 'proj-1',
          name: 'Test Project',
          workspaceId: workspace.id,
          lifecyclePhase: 'INTENT',
          tenantId: 'tenant-1',
        },
      });

      // ADMIN user attempts force transition
      const adminToken = generateJWT('user-admin', 'admin');

      const response = await app.inject({
        method: 'POST',
        url: `/lifecycle/projects/${project.id}/stages/transition`,
        headers: { authorization: `Bearer ${adminToken}` },
        payload: {
          fromStage: 0,
          toStage: 1,
          force: true,
        },
      });

      expect(response.statusCode).toBe(200);
      expect(response.json()).toHaveProperty('success', true);
    });

    it('should allow OWNER user to force transition stages (200 OK)', async () => {
      // Create test workspace and project with owner
      const workspace = await prisma.workspace.create({
        data: {
          id: 'ws-1',
          name: 'Test Workspace',
          ownerId: 'user-owner',
          tenantId: 'tenant-1',
        },
      });

      const project = await prisma.project.create({
        data: {
          id: 'proj-1',
          name: 'Test Project',
          workspaceId: workspace.id,
          lifecyclePhase: 'INTENT',
          tenantId: 'tenant-1',
        },
      });

      // OWNER user attempts force transition
      const ownerToken = generateJWT('user-owner', 'owner');

      const response = await app.inject({
        method: 'POST',
        url: `/lifecycle/projects/${project.id}/stages/transition`,
        headers: { authorization: `Bearer ${ownerToken}` },
        payload: {
          fromStage: 0,
          toStage: 1,
          force: true,
        },
      });

      expect(response.statusCode).toBe(200);
      expect(response.json()).toHaveProperty('success', true);
    });
  });

  describe('Input Validation', () => {
    it('should reject invalid stage number (400 Bad Request)', async () => {
      // Create test workspace and project
      const workspace = await prisma.workspace.create({
        data: {
          id: 'ws-1',
          name: 'Test Workspace',
          ownerId: 'user-a',
          tenantId: 'tenant-1',
        },
      });

      const project = await prisma.project.create({
        data: {
          id: 'proj-1',
          name: 'Test Project',
          workspaceId: workspace.id,
          lifecyclePhase: 'INTENT',
          tenantId: 'tenant-1',
        },
      });

      const token = generateJWT('user-a', 'admin');

      const response = await app.inject({
        method: 'GET',
        url: `/lifecycle/projects/${project.id}/gates/99`,
        headers: { authorization: `Bearer ${token}` },
      });

      expect(response.statusCode).toBe(400);
      expect(response.json()).toHaveProperty('error');
    });
  });

  describe('FromStage Validation', () => {
    it('should reject transition when fromStage does not match current stage (409 Conflict)', async () => {
      // Create test workspace and project at stage 3
      const workspace = await prisma.workspace.create({
        data: {
          id: 'ws-1',
          name: 'Test Workspace',
          ownerId: 'user-admin',
          tenantId: 'tenant-1',
        },
      });

      const project = await prisma.project.create({
        data: {
          id: 'proj-1',
          name: 'Test Project',
          workspaceId: workspace.id,
          lifecyclePhase: 'RUN', // Stage 3
          tenantId: 'tenant-1',
        },
      });

      const adminToken = generateJWT('user-admin', 'admin');

      // Try to transition from stage 0 when project is at stage 3
      const response = await app.inject({
        method: 'POST',
        url: `/lifecycle/projects/${project.id}/stages/transition`,
        headers: { authorization: `Bearer ${adminToken}` },
        payload: {
          fromStage: 0, // Incorrect - project is at stage 3
          toStage: 4,
        },
      });

      expect(response.statusCode).toBe(409);
      expect(response.json()).toHaveProperty('error');
    });

    it('should accept transition when fromStage matches current stage (200 OK)', async () => {
      // Create test workspace and project at stage 0
      const workspace = await prisma.workspace.create({
        data: {
          id: 'ws-1',
          name: 'Test Workspace',
          ownerId: 'user-admin',
          tenantId: 'tenant-1',
        },
      });

      const project = await prisma.project.create({
        data: {
          id: 'proj-1',
          name: 'Test Project',
          workspaceId: workspace.id,
          lifecyclePhase: 'INTENT', // Stage 0
          tenantId: 'tenant-1',
        },
      });

      const adminToken = generateJWT('user-admin', 'admin');

      // Transition from stage 0 to stage 1
      const response = await app.inject({
        method: 'POST',
        url: `/lifecycle/projects/${project.id}/stages/transition`,
        headers: { authorization: `Bearer ${adminToken}` },
        payload: {
          fromStage: 0, // Correct - project is at stage 0
          toStage: 1,
        },
      });

      expect(response.statusCode).toBe(200);
      expect(response.json()).toHaveProperty('success', true);
    });
  });
});
