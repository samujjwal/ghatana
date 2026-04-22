/**
 * @doc.type test-suite
 * @doc.purpose Integration tests for lifecycle gate evaluation using the real Fastify app and current Prisma schema
 * @doc.layer application
 * @doc.pattern Integration Test
 */

import {
  afterAll,
  beforeAll,
  beforeEach,
  describe,
  expect,
  it,
} from 'vitest';
import type { FastifyInstance } from 'fastify';
import type { PrismaClient } from '@prisma/client';
import { sign } from 'jsonwebtoken';

import { createApp } from '../index';
import { disconnectDatabase, getPrismaClient } from '../database/client';

const integrationDatabaseUrl = process.env.TEST_DATABASE_URL;

function generateJWT(
  userId: string,
  role: 'admin' | 'editor' | 'viewer' | 'owner' = 'admin',
): string {
  return sign(
    { sub: userId, tenantId: 'tenant-1', role },
    process.env.JWT_ACCESS_SECRET || 'test-secret-for-gate-tests',
    { expiresIn: '1h' },
  );
}

async function createOwnedProject(
  prisma: PrismaClient,
  params: {
    userId: string;
    workspaceId: string;
    projectId: string;
    phase:
      | 'INTENT'
      | 'CONTEXT'
      | 'PLAN'
      | 'EXECUTE'
      | 'VERIFY'
      | 'OBSERVE'
      | 'LEARN'
      | 'INSTITUTIONALIZE';
  },
) {
  const user = await prisma.user.create({
    data: {
      id: params.userId,
      email: `${params.userId}@example.com`,
      name: `User ${params.userId}`,
    },
  });

  const workspace = await prisma.workspace.create({
    data: {
      id: params.workspaceId,
      name: `Workspace ${params.workspaceId}`,
      ownerId: user.id,
    },
  });

  const project = await prisma.project.create({
    data: {
      id: params.projectId,
      name: `Project ${params.projectId}`,
      ownerWorkspaceId: workspace.id,
      createdById: user.id,
      type: 'FULL_STACK',
      lifecyclePhase: params.phase,
    },
  });

  return { user, workspace, project };
}

async function createApprovedArtifacts(
  prisma: PrismaClient,
  params: {
    projectId: string;
    phase:
      | 'INTENT'
      | 'CONTEXT'
      | 'PLAN'
      | 'EXECUTE'
      | 'VERIFY'
      | 'OBSERVE'
      | 'LEARN'
      | 'INSTITUTIONALIZE';
    stage: number;
    types: string[];
  },
) {
  for (const [index, type] of params.types.entries()) {
    await prisma.lifecycleArtifact.create({
      data: {
        id: `${params.projectId}-artifact-${index + 1}`,
        projectId: params.projectId,
        title: type,
        type,
        status: 'approved',
        phase: params.phase,
        flowStage: params.stage,
      },
    });
  }
}

if (!integrationDatabaseUrl) {
  describe.skip('Integration: Lifecycle gate evaluation (set TEST_DATABASE_URL to enable) [GH-90000]', () => {
    it('placeholder', () => {
      expect(integrationDatabaseUrl).toBeUndefined();
    });
  });
} else {
  describe('Integration: Lifecycle gate evaluation', () => {
    let app: FastifyInstance;
    let prisma: PrismaClient;

    beforeAll(async () => {
      process.env.JWT_ACCESS_SECRET = 'test-secret-for-gate-tests';
      process.env.DATABASE_URL = integrationDatabaseUrl;

      app = await createApp();
      prisma = getPrismaClient();
    });

    afterAll(async () => {
      if (app) {
        await app.close();
      }
      await disconnectDatabase();
    });

    beforeEach(async () => {
      await prisma.lifecycleActivityLog.deleteMany({});
      await prisma.lifecycleArtifact.deleteMany({});
      await prisma.project.deleteMany({});
      await prisma.workspace.deleteMany({});
      await prisma.user.deleteMany({});
    });

    it('returns readiness 100 when stage 0 has every required artifact', async () => {
      const { project, user } = await createOwnedProject(prisma, {
        userId: 'user-intent',
        workspaceId: 'ws-intent',
        projectId: 'proj-intent',
        phase: 'INTENT',
      });

      await createApprovedArtifacts(prisma, {
        projectId: project.id,
        phase: 'INTENT',
        stage: 0,
        types: ['Idea Brief', 'Problem Statement', 'Success Criteria'],
      });

      const response = await app.inject({
        method: 'GET',
        url: `/api/projects/${project.id}/gates/0`,
        headers: { authorization: `Bearer ${generateJWT(user.id)}` },
      });

      expect(response.statusCode).toBe(200);
      expect(response.json()).toMatchObject({
        stage: 0,
        readiness: 100,
        canProceed: true,
        requiredArtifactTypes: ['Idea Brief', 'Problem Statement', 'Success Criteria'],
      });
    });

    it('returns readiness 0 when stage 0 has no approved artifacts', async () => {
      const { project, user } = await createOwnedProject(prisma, {
        userId: 'user-empty',
        workspaceId: 'ws-empty',
        projectId: 'proj-empty',
        phase: 'INTENT',
      });

      const response = await app.inject({
        method: 'GET',
        url: `/api/projects/${project.id}/gates/0`,
        headers: { authorization: `Bearer ${generateJWT(user.id)}` },
      });

      expect(response.statusCode).toBe(200);
      expect(response.json()).toMatchObject({
        stage: 0,
        readiness: 0,
        canProceed: false,
        requiredArtifactTypes: ['Idea Brief', 'Problem Statement', 'Success Criteria'],
      });
    });

    it('uses canonical stage 1 requirements for context gates', async () => {
      const { project, user } = await createOwnedProject(prisma, {
        userId: 'user-context',
        workspaceId: 'ws-context',
        projectId: 'proj-context',
        phase: 'CONTEXT',
      });

      await createApprovedArtifacts(prisma, {
        projectId: project.id,
        phase: 'CONTEXT',
        stage: 1,
        types: ['Architecture Diagram', 'Tech Stack'],
      });

      const response = await app.inject({
        method: 'GET',
        url: `/api/projects/${project.id}/gates/1`,
        headers: { authorization: `Bearer ${generateJWT(user.id)}` },
      });

      expect(response.statusCode).toBe(200);
      expect(response.json()).toMatchObject({
        stage: 1,
        readiness: 67,
        canProceed: false,
        requiredArtifactTypes: ['Architecture Diagram', 'Tech Stack', 'API Design'],
      });
    });

    it('uses canonical stage 3 requirements for execute gates', async () => {
      const { project, user } = await createOwnedProject(prisma, {
        userId: 'user-execute',
        workspaceId: 'ws-execute',
        projectId: 'proj-execute',
        phase: 'EXECUTE',
      });

      await createApprovedArtifacts(prisma, {
        projectId: project.id,
        phase: 'EXECUTE',
        stage: 3,
        types: ['Source Code', 'Documentation', 'Build Artifacts'],
      });

      const response = await app.inject({
        method: 'GET',
        url: `/api/projects/${project.id}/gates/3`,
        headers: { authorization: `Bearer ${generateJWT(user.id)}` },
      });

      expect(response.statusCode).toBe(200);
      expect(response.json()).toMatchObject({
        stage: 3,
        readiness: 100,
        canProceed: true,
        requiredArtifactTypes: ['Source Code', 'Documentation', 'Build Artifacts'],
      });
    });

    it('ignores stage 0 artifacts when evaluating stage 3 gates', async () => {
      const { project, user } = await createOwnedProject(prisma, {
        userId: 'user-stage-mismatch',
        workspaceId: 'ws-stage-mismatch',
        projectId: 'proj-stage-mismatch',
        phase: 'EXECUTE',
      });

      await createApprovedArtifacts(prisma, {
        projectId: project.id,
        phase: 'INTENT',
        stage: 0,
        types: ['Idea Brief', 'Problem Statement', 'Success Criteria'],
      });

      const response = await app.inject({
        method: 'GET',
        url: `/api/projects/${project.id}/gates/3`,
        headers: { authorization: `Bearer ${generateJWT(user.id)}` },
      });

      expect(response.statusCode).toBe(200);
      expect(response.json()).toMatchObject({
        stage: 3,
        readiness: 0,
        canProceed: false,
        requiredArtifactTypes: ['Source Code', 'Documentation', 'Build Artifacts'],
      });
    });
  });
}
