import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import type { FastifyInstance } from 'fastify';
import { createApp } from '../index';

describe('AI routes', () => {
  let app: FastifyInstance;

  beforeAll(async () => {
    app = await createApp({ jwtSecret: 'test-secret-key-32-chars-minimum!!' });
  });

  afterAll(async () => {
    await app.close();
  });

  it('returns artifact suggestions from server-side endpoint', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/v1/ai/suggest-artifacts',
      payload: {
        context: {
          projectId: 'project-123',
          currentPhase: 'SHAPE',
          existingArtifacts: [{ kind: 'REQUIREMENTS', payload: {} }],
          projectDescription: 'Build a secure collaboration app',
        },
      },
    });

    expect(response.statusCode).toBe(200);
    const body = response.json() as {
      suggestions: Array<{
        kind: string;
        title: string;
        suggestedPayload: {
          defaultOwnerRole: string;
          defaultPriority: string;
          defaultTargetDays: number;
          defaultReasoning: string;
        };
      }>;
      correlationId?: string;
    };

    expect(body.suggestions.length).toBeGreaterThan(0);
    expect(body.suggestions.some((suggestion) => suggestion.kind === 'ADR')).toBe(true);
    const adrSuggestion = body.suggestions.find((suggestion) => suggestion.kind === 'ADR');
    expect(adrSuggestion).toBeDefined();
    expect(adrSuggestion?.suggestedPayload.defaultOwnerRole).toBe('Security Team');
    expect(adrSuggestion?.suggestedPayload.defaultPriority).toBe('high');
    expect(adrSuggestion?.suggestedPayload.defaultTargetDays).toBeLessThanOrEqual(3);
    expect(adrSuggestion?.suggestedPayload.defaultReasoning.length).toBeGreaterThan(0);
    expect(typeof body.correlationId).toBe('string');
    expect(response.headers['x-correlation-id']).toBeTruthy();
  });

  it('rejects invalid lifecycle phase payload', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/v1/ai/suggest-artifacts',
      payload: {
        context: {
          projectId: 'project-123',
          currentPhase: 'INVALID_PHASE',
          existingArtifacts: [],
        },
      },
    });

    expect(response.statusCode).toBe(400);
    const body = response.json() as { error: string; correlationId?: string };
    expect(body.error).toContain('currentPhase');
    expect(typeof body.correlationId).toBe('string');
  });
});
