import Fastify, { type FastifyInstance } from 'fastify';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const prismaMock = {
  project: {
    findUnique: vi.fn(),
  },
  lifecycleArtifact: {
    findMany: vi.fn(),
  },
};

vi.mock('../database/client.js', () => ({
  getPrismaClient: () => prismaMock,
}));

vi.mock('../middleware/rbac.middleware', () => ({
  requirePermission: () => async () => undefined,
  requireRole: () => async () => undefined,
}));

import lifecycleRoutes from '../routes/lifecycle';

describe('lifecycle phase preview routes', () => {
  let app: FastifyInstance;

  beforeEach(async () => {
    app = Fastify();
    await app.register(lifecycleRoutes, { prefix: '/api' });
    prismaMock.project.findUnique.mockReset();
    prismaMock.lifecycleArtifact.findMany.mockReset();
  });

  afterEach(async () => {
    await app.close();
  });

  it('returns next phase preview when project is ready to advance', async () => {
    prismaMock.project.findUnique.mockResolvedValue({ id: 'project-1' });
    prismaMock.lifecycleArtifact.findMany.mockResolvedValue([
      { type: 'Idea Brief' },
      { type: 'Problem Statement' },
      { type: 'Success Criteria' },
    ]);

    const response = await app.inject({
      method: 'GET',
      url: '/api/phases/INTENT/next?projectId=project-1',
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      projectId: 'project-1',
      currentPhase: 'INTENT',
      nextPhase: 'SHAPE',
      canAdvance: true,
      readiness: 100,
      estimatedReadyIn: 'Ready now',
      estimatedReadyInHours: 0,
      predictionConfidence: 0.95,
      blockers: [],
      requiredArtifacts: [
        'Idea Brief',
        'Problem Statement',
        'Success Criteria',
      ],
      completedArtifacts: [
        'Idea Brief',
        'Problem Statement',
        'Success Criteria',
      ],
    });
  });

  it('returns blockers when approved artifacts are missing', async () => {
    prismaMock.project.findUnique.mockResolvedValue({ id: 'project-2' });
    prismaMock.lifecycleArtifact.findMany.mockResolvedValue([
      { type: 'Idea Brief' },
    ]);

    const response = await app.inject({
      method: 'GET',
      url: '/api/phases/INTENT/next?projectId=project-2',
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      currentPhase: 'INTENT',
      nextPhase: 'SHAPE',
      canAdvance: false,
      readiness: 33,
      estimatedReadyIn: '~2 days',
      estimatedReadyInHours: 50,
      predictionConfidence: 0.5,
      blockers: [
        'Missing approved artifact: Problem Statement',
        'Missing approved artifact: Success Criteria',
        'At least 2 approved artifacts are required before advancing from INTENT to SHAPE.',
      ],
    });
  });

  it('rejects invalid phase values', async () => {
    const response = await app.inject({
      method: 'GET',
      url: '/api/phases/not-a-phase/next?projectId=project-3',
    });

    expect(response.statusCode).toBe(400);
    expect(response.json()).toMatchObject({
      error: 'Invalid phase',
      received: 'not-a-phase',
      validPhases: [
        'INTENT',
        'SHAPE',
        'VALIDATE',
        'GENERATE',
        'RUN',
        'OBSERVE',
        'IMPROVE',
      ],
    });
  });

  it('returns final-phase blocker when no next phase exists', async () => {
    prismaMock.project.findUnique.mockResolvedValue({ id: 'project-4' });
    prismaMock.lifecycleArtifact.findMany.mockResolvedValue([
      { type: 'Improvement Backlog' },
      { type: 'Metrics Analysis' },
      { type: 'Lessons Learned' },
    ]);

    const response = await app.inject({
      method: 'GET',
      url: '/api/phases/IMPROVE/next?projectId=project-4',
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      currentPhase: 'IMPROVE',
      nextPhase: null,
      canAdvance: false,
      estimatedReadyIn: null,
      estimatedReadyInHours: null,
      predictionConfidence: null,
      blockers: ['Project is already at the final lifecycle phase.'],
    });
  });
});
