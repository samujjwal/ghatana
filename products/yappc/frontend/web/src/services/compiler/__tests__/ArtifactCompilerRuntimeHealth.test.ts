import { describe, expect, it, vi } from 'vitest';

import {
  checkArtifactCompilerRuntimeHealth,
  formatArtifactCompilerRuntimeUnavailableMessage,
  getArtifactCompilerRuntimeRequirements,
} from '../ArtifactCompilerRuntimeHealth';

describe('ArtifactCompilerRuntimeHealth', () => {
  it('declares the required local artifact analyzer runtime and health endpoint', () => {
    const requirements = getArtifactCompilerRuntimeRequirements({
      artifactApiBaseUrl: 'http://localhost:8080/api/v1/yappc/artifact',
      analyzerHealthUrl: 'http://localhost:8080/health',
    });

    expect(requirements).toEqual([
      expect.objectContaining({
        id: 'artifact-analyzer-http',
        required: true,
        endpoint: 'http://localhost:8080/api/v1/yappc/artifact',
        healthUrl: 'http://localhost:8080/health',
      }),
    ]);
  });

  it('reports available when all runtime health checks pass', async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response(null, { status: 200 }));

    const health = await checkArtifactCompilerRuntimeHealth({
      analyzerHealthUrl: 'http://localhost:8080/health',
      fetcher,
    });

    expect(health.status).toBe('available');
    expect(health.unavailableRequirements).toEqual([]);
    expect(fetcher).toHaveBeenCalledWith(
      'http://localhost:8080/health',
      expect.objectContaining({ method: 'GET', credentials: 'omit' }),
    );
  });

  it('returns an actionable unavailable message when the runtime is down', async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response(null, { status: 503 }));

    const health = await checkArtifactCompilerRuntimeHealth({
      analyzerHealthUrl: 'http://localhost:8080/health',
      fetcher,
    });

    expect(health.status).toBe('unavailable');
    expect(health.message).toContain('Artifact compiler runtime unavailable');
    expect(health.message).toContain('http://localhost:8080/health');
  });

  it('formats unavailable requirements without hiding the required health URL', () => {
    const message = formatArtifactCompilerRuntimeUnavailableMessage([
      {
        id: 'artifact-analyzer-http',
        label: 'YAPPC artifact analyzer HTTP runtime',
        required: true,
        endpoint: 'http://localhost:8080/api/v1/yappc/artifact',
        healthUrl: 'http://localhost:8080/health',
        description: 'Required for governed source import.',
      },
    ]);

    expect(message).toContain('YAPPC artifact analyzer HTTP runtime');
    expect(message).toContain('http://localhost:8080/health');
  });
});
