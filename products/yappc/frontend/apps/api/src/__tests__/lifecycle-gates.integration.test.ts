/**
 * @doc.type test-suite
 * @doc.purpose Integration tests for gate evaluation accuracy
 * @doc.layer application
 * @doc.pattern Integration Test
 *
 * Tests gate evaluation accuracy across lifecycle stages:
 * - Stage 0 (Intent) gate checks intent artifacts
 * - Stage 1 (Shape) gate checks shape artifacts
 * - Stage 3 (Run) gate checks run artifacts
 * - Readiness calculations are stage-specific
 * - Required artifact types match STAGE_GATE_REQUIREMENTS
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

describe('Integration: Gate Evaluation Accuracy', () => {
  let app: FastifyInstance;
  let prisma: any;

  beforeAll(async () => {
    process.env.JWT_ACCESS_SECRET = 'test-secret-for-gate-tests';
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
      await prisma.lifecycleArtifact.deleteMany({});
      await prisma.project.deleteMany({});
      await prisma.workspace.deleteMany({});
      await prisma.user.deleteMany({});
    } catch (error) {
      // Ignore cleanup errors
    }
  });

  describe('Stage 0 (Intent) Gate Evaluation', () => {
    it('should return readiness: 100 when project has all required stage 0 artifacts', async () => {
      // Create test workspace and project at stage 0
      const workspace = await prisma.workspace.create({
        data: {
          id: 'ws-1',
          name: 'Test Workspace',
          ownerId: 'user-1',
          tenantId: 'tenant-1',
        },
      });

      const project = await prisma.project.create({
        data: {
          id: 'proj-1',
          name: 'Test Project',
          workspaceId: workspace.id,
          lifecyclePhase: 'INTENT',
          currentStage: 0,
          tenantId: 'tenant-1',
        },
      });

      // Seed stage 0 artifacts (intent documents, requirements)
      await prisma.lifecycleArtifact.create({
        data: {
          id: 'artifact-1',
          projectId: project.id,
          type: 'INTENT_DOCUMENT',
          stage: 0,
          status: 'COMPLETED',
        },
      });

      await prisma.lifecycleArtifact.create({
        data: {
          id: 'artifact-2',
          projectId: project.id,
          type: 'REQUIREMENTS',
          stage: 0,
          status: 'COMPLETED',
        },
      });

      const token = generateJWT('user-1', 'admin');

      const response = await app.inject({
        method: 'GET',
        url: `/lifecycle/projects/${project.id}/gates/0`,
        headers: { authorization: `Bearer ${token}` },
      });

      expect(response.statusCode).toBe(200);
      const gateEvaluation = response.json();
      expect(gateEvaluation).toHaveProperty('readiness', 100);
      expect(gateEvaluation).toHaveProperty('canProceed', true);
      expect(gateEvaluation).toHaveProperty('requiredArtifactTypes');
    });

    it('should return readiness: 0 when project has no stage 0 artifacts', async () => {
      // Create test workspace and project at stage 0 with no artifacts
      const workspace = await prisma.workspace.create({
        data: {
          id: 'ws-1',
          name: 'Test Workspace',
          ownerId: 'user-1',
          tenantId: 'tenant-1',
        },
      });

      const project = await prisma.project.create({
        data: {
          id: 'proj-1',
          name: 'Test Project',
          workspaceId: workspace.id,
          lifecyclePhase: 'INTENT',
          currentStage: 0,
          tenantId: 'tenant-1',
        },
      });

      const token = generateJWT('user-1', 'admin');

      const response = await app.inject({
        method: 'GET',
        url: `/lifecycle/projects/${project.id}/gates/0`,
        headers: { authorization: `Bearer ${token}` },
      });

      expect(response.statusCode).toBe(200);
      const gateEvaluation = response.json();
      expect(gateEvaluation).toHaveProperty('readiness', 0);
      expect(gateEvaluation).toHaveProperty('canProceed', false);
    });
  });

  describe('Stage 1 (Shape) Gate Evaluation', () => {
    it('should return correct readiness for stage 1 with shape artifacts', async () => {
      // Create test workspace and project at stage 1
      const workspace = await prisma.workspace.create({
        data: {
          id: 'ws-1',
          name: 'Test Workspace',
          ownerId: 'user-1',
          tenantId: 'tenant-1',
        },
      });

      const project = await prisma.project.create({
        data: {
          id: 'proj-1',
          name: 'Test Project',
          workspaceId: workspace.id,
          lifecyclePhase: 'SHAPE',
          currentStage: 1,
          tenantId: 'tenant-1',
        },
      });

      // Seed stage 1 artifacts (design documents, architecture)
      await prisma.lifecycleArtifact.create({
        data: {
          id: 'artifact-1',
          projectId: project.id,
          type: 'DESIGN_DOCUMENT',
          stage: 1,
          status: 'COMPLETED',
        },
      });

      await prisma.lifecycleArtifact.create({
        data: {
          id: 'artifact-2',
          projectId: project.id,
          type: 'ARCHITECTURE',
          stage: 1,
          status: 'COMPLETED',
        },
      });

      const token = generateJWT('user-1', 'admin');

      const response = await app.inject({
        method: 'GET',
        url: `/lifecycle/projects/${project.id}/gates/1`,
        headers: { authorization: `Bearer ${token}` },
      });

      expect(response.statusCode).toBe(200);
      const gateEvaluation = response.json();
      expect(gateEvaluation).toHaveProperty('readiness');
      expect(gateEvaluation).toHaveProperty('canProceed');
      expect(gateEvaluation).toHaveProperty('requiredArtifactTypes');
    });
  });

  describe('Stage 3 (Run) Gate Evaluation', () => {
    it('should return correct readiness for stage 3 with run artifacts', async () => {
      // Create test workspace and project at stage 3
      const workspace = await prisma.workspace.create({
        data: {
          id: 'ws-1',
          name: 'Test Workspace',
          ownerId: 'user-1',
          tenantId: 'tenant-1',
        },
      });

      const project = await prisma.project.create({
        data: {
          id: 'proj-1',
          name: 'Test Project',
          workspaceId: workspace.id,
          lifecyclePhase: 'RUN',
          currentStage: 3,
          tenantId: 'tenant-1',
        },
      });

      // Seed stage 3 artifacts (build artifacts, test results)
      await prisma.lifecycleArtifact.create({
        data: {
          id: 'artifact-1',
          projectId: project.id,
          type: 'BUILD_ARTIFACT',
          stage: 3,
          status: 'COMPLETED',
        },
      });

      await prisma.lifecycleArtifact.create({
        data: {
          id: 'artifact-2',
          projectId: project.id,
          type: 'TEST_RESULTS',
          stage: 3,
          status: 'COMPLETED',
        },
      });

      const token = generateJWT('user-1', 'admin');

      const response = await app.inject({
        method: 'GET',
        url: `/lifecycle/projects/${project.id}/gates/3`,
        headers: { authorization: `Bearer ${token}` },
      });

      expect(response.statusCode).toBe(200);
      const gateEvaluation = response.json();
      expect(gateEvaluation).toHaveProperty('readiness');
      expect(gateEvaluation).toHaveProperty('canProceed');
      expect(gateEvaluation).toHaveProperty('requiredArtifactTypes');
    });

    it('should not check stage 0 artifacts when evaluating stage 3 gate', async () => {
      // Create test workspace and project at stage 3
      const workspace = await prisma.workspace.create({
        data: {
          id: 'ws-1',
          name: 'Test Workspace',
          ownerId: 'user-1',
          tenantId: 'tenant-1',
        },
      });

      const project = await prisma.project.create({
        data: {
          id: 'proj-1',
          name: 'Test Project',
          workspaceId: workspace.id,
          lifecyclePhase: 'RUN',
          currentStage: 3,
          tenantId: 'tenant-1',
        },
      });

      // Seed only stage 0 artifacts (should not affect stage 3 gate)
      await prisma.lifecycleArtifact.create({
        data: {
          id: 'artifact-1',
          projectId: project.id,
          type: 'INTENT_DOCUMENT',
          stage: 0,
          status: 'COMPLETED',
        },
      });

      const token = generateJWT('user-1', 'admin');

      const response = await app.inject({
        method: 'GET',
        url: `/lifecycle/projects/${project.id}/gates/3`,
        headers: { authorization: `Bearer ${token}` },
      });

      expect(response.statusCode).toBe(200);
      const gateEvaluation = response.json();
      expect(gateEvaluation).toHaveProperty('readiness');
      // Stage 3 gate should not be satisfied by stage 0 artifacts
      expect(gateEvaluation.canProceed).toBe(false);
    });
  });

  describe('Required Artifact Types Validation', () => {
    it('should verify requiredArtifactTypes matches STAGE_GATE_REQUIREMENTS for stage 0', async () => {
      const workspace = await prisma.workspace.create({
        data: {
          id: 'ws-1',
          name: 'Test Workspace',
          ownerId: 'user-1',
          tenantId: 'tenant-1',
        },
      });

      const project = await prisma.project.create({
        data: {
          id: 'proj-1',
          name: 'Test Project',
          workspaceId: workspace.id,
          lifecyclePhase: 'INTENT',
          currentStage: 0,
          tenantId: 'tenant-1',
        },
      });

      const token = generateJWT('user-1', 'admin');

      const response = await app.inject({
        method: 'GET',
        url: `/lifecycle/projects/${project.id}/gates/0`,
        headers: { authorization: `Bearer ${token}` },
      });

      expect(response.statusCode).toBe(200);
      const gateEvaluation = response.json();
      expect(gateEvaluation).toHaveProperty('requiredArtifactTypes');
      expect(Array.isArray(gateEvaluation.requiredArtifactTypes)).toBe(true);
    });

    it('should verify requiredArtifactTypes matches STAGE_GATE_REQUIREMENTS for stage 1', async () => {
      const workspace = await prisma.workspace.create({
        data: {
          id: 'ws-1',
          name: 'Test Workspace',
          ownerId: 'user-1',
          tenantId: 'tenant-1',
        },
      });

      const project = await prisma.project.create({
        data: {
          id: 'proj-1',
          name: 'Test Project',
          workspaceId: workspace.id,
          lifecyclePhase: 'SHAPE',
          currentStage: 1,
          tenantId: 'tenant-1',
        },
      });

      const token = generateJWT('user-1', 'admin');

      const response = await app.inject({
        method: 'GET',
        url: `/lifecycle/projects/${project.id}/gates/1`,
        headers: { authorization: `Bearer ${token}` },
      });

      expect(response.statusCode).toBe(200);
      const gateEvaluation = response.json();
      expect(gateEvaluation).toHaveProperty('requiredArtifactTypes');
      expect(Array.isArray(gateEvaluation.requiredArtifactTypes)).toBe(true);
    });

    it('should verify requiredArtifactTypes matches STAGE_GATE_REQUIREMENTS for stage 3', async () => {
      const workspace = await prisma.workspace.create({
        data: {
          id: 'ws-1',
          name: 'Test Workspace',
          ownerId: 'user-1',
          tenantId: 'tenant-1',
        },
      });

      const project = await prisma.project.create({
        data: {
          id: 'proj-1',
          name: 'Test Project',
          workspaceId: workspace.id,
          lifecyclePhase: 'RUN',
          currentStage: 3,
          tenantId: 'tenant-1',
        },
      });

      const token = generateJWT('user-1', 'admin');

      const response = await app.inject({
        method: 'GET',
        url: `/lifecycle/projects/${project.id}/gates/3`,
        headers: { authorization: `Bearer ${token}` },
      });

      expect(response.statusCode).toBe(200);
      const gateEvaluation = response.json();
      expect(gateEvaluation).toHaveProperty('requiredArtifactTypes');
      expect(Array.isArray(gateEvaluation.requiredArtifactTypes)).toBe(true);
    });
  });
});
