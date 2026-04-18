/**
 * @doc.type test-suite
 * @doc.purpose CRITICAL: YAPPC HTTP route validation, workspace CRUD, permissions
 * @doc.layer application
 * @doc.pattern Integration Test
 *
 * Phase 2B validates all HTTP API contracts using real Fastify app and database:
 * - Workspace CRUD (create, read, update, delete)
 * - Project management with ownership
 * - Permission enforcement at endpoint level
 * - Error responses and status codes
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
import { sign } from 'jsonwebtoken';

// JWT token generation helper
function generateJWT(
  userId: string,
  role: string = 'user',
  tenantId: string = 'tenant-1'
): string {
  return sign(
    { sub: userId, tenantId, role },
    process.env.JWT_ACCESS_SECRET || 'test-secret',
    { expiresIn: '1h' }
  );
}

describe('Phase 2B: YAPPC Critical HTTP Routes (Integration)', () => {
  let app: FastifyInstance;
  let prisma: any;

  beforeAll(async () => {
    process.env.JWT_ACCESS_SECRET = 'test-secret-for-integration-tests';
    process.env.DATABASE_URL = process.env.TEST_DATABASE_URL || 'postgresql://test:test@localhost:5432/yappc_test';
    app = await createApp();
    prisma = getPrismaClient();
  });

  afterAll(async () => {
    await app.close();
  });

  beforeEach(async () => {
    // Clean up test data
    try {
      await prisma.lifecycleActivityLog.deleteMany({});
      await prisma.lifecycleArtifact.deleteMany({});
      await prisma.project.deleteMany({});
      await prisma.workspace.deleteMany({});
      await prisma.user.deleteMany({});
    } catch (error) {
      // Ignore cleanup errors
    }
  });

  describe('Workspace CRUD Operations', () => {
    it('should create workspace with valid request', async () => {
      const user = await prisma.user.create({
        data: {
          id: 'user-1',
          email: 'user1@example.com',
          name: 'User 1',
        },
      });

      const token = generateJWT('user-1', 'admin');

      const response = await app.inject({
        method: 'POST',
        url: '/api/v1/workspaces',
        headers: { authorization: `Bearer ${token}` },
        payload: {
          name: 'My Workspace',
          description: 'Test workspace',
        },
      });

      expect(response.statusCode).toBe(201);
      expect(response.json()).toHaveProperty('id');
      expect(response.json().name).toBe('My Workspace');
    });

    it('should reject workspace creation with missing name', async () => {
      const user = await prisma.user.create({
        data: {
          id: 'user-1',
          email: 'user1@example.com',
          name: 'User 1',
        },
      });

      const token = generateJWT('user-1', 'admin');

      const response = await app.inject({
        method: 'POST',
        url: '/api/v1/workspaces',
        headers: { authorization: `Bearer ${token}` },
        payload: {
          description: 'No name provided',
        },
      });

      expect(response.statusCode).toBe(400);
      expect(response.json().error).toMatch(/name|required/i);
    });

    it('should list workspaces filtered by tenant', async () => {
      const user = await prisma.user.create({
        data: {
          id: 'user-1',
          email: 'user1@example.com',
          name: 'User 1',
        },
      });

      const ws1 = await prisma.workspace.create({
        data: {
          id: 'ws-1',
          name: 'Workspace 1',
          ownerId: user.id,
          tenantId: 'tenant-1',
        },
      });

      const ws2 = await prisma.workspace.create({
        data: {
          id: 'ws-2',
          name: 'Workspace 2',
          ownerId: user.id,
          tenantId: 'tenant-1',
        },
      });

      const token = generateJWT('user-1', 'admin');

      const response = await app.inject({
        method: 'GET',
        url: '/api/v1/workspaces',
        headers: { authorization: `Bearer ${token}` },
      });

      expect(response.statusCode).toBe(200);
      expect(response.json().data).toHaveLength(2);
    });

    it('should get single workspace by id', async () => {
      const user = await prisma.user.create({
        data: {
          id: 'user-1',
          email: 'user1@example.com',
          name: 'User 1',
        },
      });

      const workspace = await prisma.workspace.create({
        data: {
          id: 'ws-1',
          name: 'My Workspace',
          ownerId: user.id,
          tenantId: 'tenant-1',
          description: 'Workspace description',
        },
      });

      const token = generateJWT('user-1', 'admin');

      const response = await app.inject({
        method: 'GET',
        url: '/api/v1/workspaces/ws-1',
        headers: { authorization: `Bearer ${token}` },
      });

      expect(response.statusCode).toBe(200);
      expect(response.json()).toHaveProperty('name', 'My Workspace');
    });

    it('should return 404 for non-existent workspace', async () => {
      const user = await prisma.user.create({
        data: {
          id: 'user-1',
          email: 'user1@example.com',
          name: 'User 1',
        },
      });

      const token = generateJWT('user-1', 'admin');

      const response = await app.inject({
        method: 'GET',
        url: '/api/v1/workspaces/ws-nonexistent',
        headers: { authorization: `Bearer ${token}` },
      });

      expect(response.statusCode).toBe(404);
    });

    it('should update workspace with valid payload', async () => {
      const user = await prisma.user.create({
        data: {
          id: 'user-1',
          email: 'user1@example.com',
          name: 'User 1',
        },
      });

      const workspace = await prisma.workspace.create({
        data: {
          id: 'ws-1',
          name: 'My Workspace',
          ownerId: user.id,
          tenantId: 'tenant-1',
        },
      });

      const token = generateJWT('user-1', 'admin');

      const response = await app.inject({
        method: 'PATCH',
        url: '/api/v1/workspaces/ws-1',
        headers: { authorization: `Bearer ${token}` },
        payload: {
          name: 'Updated Workspace',
        },
      });

      expect(response.statusCode).toBe(200);
      expect(response.json().name).toBe('Updated Workspace');
    });

    it('should enforce owner-only access for workspace deletion', async () => {
      const user1 = await prisma.user.create({
        data: {
          id: 'user-1',
          email: 'user1@example.com',
          name: 'User 1',
        },
      });

      const user2 = await prisma.user.create({
        data: {
          id: 'user-2',
          email: 'user2@example.com',
          name: 'User 2',
        },
      });

      const workspace = await prisma.workspace.create({
        data: {
          id: 'ws-1',
          name: 'My Workspace',
          ownerId: user2.id, // Different owner
          tenantId: 'tenant-1',
        },
      });

      const token = generateJWT('user-1', 'admin');

      const response = await app.inject({
        method: 'DELETE',
        url: '/api/v1/workspaces/ws-1',
        headers: { authorization: `Bearer ${token}` },
      });

      expect(response.statusCode).toBe(403);
    });

    it('should delete workspace when owner', async () => {
      const user = await prisma.user.create({
        data: {
          id: 'user-1',
          email: 'user1@example.com',
          name: 'User 1',
        },
      });

      const workspace = await prisma.workspace.create({
        data: {
          id: 'ws-1',
          name: 'My Workspace',
          ownerId: user.id,
          tenantId: 'tenant-1',
        },
      });

      const token = generateJWT('user-1', 'admin');

      const response = await app.inject({
        method: 'DELETE',
        url: '/api/v1/workspaces/ws-1',
        headers: { authorization: `Bearer ${token}` },
      });

      expect(response.statusCode).toBe(204);
    });
  });

  describe('Project Management with Ownership', () => {
    it('should create project within workspace', async () => {
      const user = await prisma.user.create({
        data: {
          id: 'user-1',
          email: 'user1@example.com',
          name: 'User 1',
        },
      });

      const workspace = await prisma.workspace.create({
        data: {
          id: 'ws-1',
          name: 'Test Workspace',
          ownerId: user.id,
          tenantId: 'tenant-1',
        },
      });

      const token = generateJWT('user-1', 'admin');

      const response = await app.inject({
        method: 'POST',
        url: '/api/v1/workspaces/ws-1/projects',
        headers: { authorization: `Bearer ${token}` },
        payload: {
          name: 'My Project',
        },
      });

      expect(response.statusCode).toBe(201);
      expect(response.json()).toHaveProperty('workspaceId', 'ws-1');
    });

    it('should prevent project creation in non-member workspace', async () => {
      const user = await prisma.user.create({
        data: {
          id: 'user-1',
          email: 'user1@example.com',
          name: 'User 1',
        },
      });

      const token = generateJWT('user-1', 'admin');

      const response = await app.inject({
        method: 'POST',
        url: '/api/v1/workspaces/ws-nonexistent/projects',
        headers: { authorization: `Bearer ${token}` },
        payload: { name: 'Project' },
      });

      expect(response.statusCode).toBe(404);
    });

    it('should list projects in workspace', async () => {
      const user = await prisma.user.create({
        data: {
          id: 'user-1',
          email: 'user1@example.com',
          name: 'User 1',
        },
      });

      const workspace = await prisma.workspace.create({
        data: {
          id: 'ws-1',
          name: 'Test Workspace',
          ownerId: user.id,
          tenantId: 'tenant-1',
        },
      });

      await prisma.project.create({
        data: {
          id: 'proj-1',
          name: 'Project 1',
          workspaceId: workspace.id,
          tenantId: 'tenant-1',
          creatorId: user.id,
        },
      });

      await prisma.project.create({
        data: {
          id: 'proj-2',
          name: 'Project 2',
          workspaceId: workspace.id,
          tenantId: 'tenant-1',
          creatorId: user.id,
        },
      });

      const token = generateJWT('user-1', 'admin');

      const response = await app.inject({
        method: 'GET',
        url: '/api/v1/workspaces/ws-1/projects',
        headers: { authorization: `Bearer ${token}` },
      });

      expect(response.statusCode).toBe(200);
      expect(response.json().data).toHaveLength(2);
    });
  });

  describe('Permission Enforcement', () => {
    it('should enforce permission enforcement (403 for wrong role)', async () => {
      const user1 = await prisma.user.create({
        data: {
          id: 'user-1',
          email: 'user1@example.com',
          name: 'User 1',
        },
      });

      const user2 = await prisma.user.create({
        data: {
          id: 'user-2',
          email: 'user2@example.com',
          name: 'User 2',
        },
      });

      const workspace = await prisma.workspace.create({
        data: {
          id: 'ws-1',
          name: 'Test Workspace',
          ownerId: user1.id,
          tenantId: 'tenant-1',
        },
      });

      const project = await prisma.project.create({
        data: {
          id: 'proj-1',
          name: 'Test Project',
          workspaceId: workspace.id,
          tenantId: 'tenant-1',
          creatorId: user1.id,
        },
      });

      // User 2 (viewer) tries to delete project created by User 1 (owner)
      const viewerToken = generateJWT('user-2', 'viewer');

      const response = await app.inject({
        method: 'DELETE',
        url: '/api/v1/projects/proj-1',
        headers: { authorization: `Bearer ${viewerToken}` },
      });

      expect(response.statusCode).toBe(403);
    });
  });

  describe('Stage Transition Validation', () => {
    it('should validate fromStage matches project current stage', async () => {
      const user = await prisma.user.create({
        data: {
          id: 'user-1',
          email: 'user1@example.com',
          name: 'User 1',
        },
      });

      const workspace = await prisma.workspace.create({
        data: {
          id: 'ws-1',
          name: 'Test Workspace',
          ownerId: user.id,
          tenantId: 'tenant-1',
        },
      });

      const project = await prisma.project.create({
        data: {
          id: 'proj-1',
          name: 'Test Project',
          workspaceId: workspace.id,
          tenantId: 'tenant-1',
          lifecyclePhase: 'RUN', // Stage 3
          currentStage: 3,
          creatorId: user.id,
        },
      });

      const adminToken = generateJWT('user-1', 'admin');

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
    });
  });
});
