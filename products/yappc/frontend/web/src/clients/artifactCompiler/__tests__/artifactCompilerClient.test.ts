/**
 * @fileoverview Test for P2-1: Typed client artifactCompilerClient.ts
 *
 * Verifies that the typed client correctly handles import job, graph summary,
 * residual review, and patch review API calls.
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ArtifactCompilerClient } from '../ArtifactCompilerClient';

describe('P2-1: ArtifactCompilerClient Typed Client', () => {
  let client: ArtifactCompilerClient;

  beforeEach(() => {
    client = new ArtifactCompilerClient('http://localhost:3000/api/v1/yappc/artifact');
  });

  it('should initialize with base URL', () => {
    expect(client).toBeDefined();
  });

  it('should have import job method', () => {
    expect(typeof client.importSource).toBe('function');
  });

  it('should have graph summary method', () => {
    expect(typeof client.queryGraph).toBe('function');
  });

  it('should have residual review method', () => {
    expect(typeof client.getResidualIslands).toBe('function');
  });

  it('should have patch review method', () => {
    expect(typeof client.getPatchReview).toBe('function');
  });

  it('should construct correct import source endpoint', () => {
    const endpoint = client['buildEndpoint']('import-source');
    expect(endpoint).toContain('import-source');
  });

  it('should construct correct graph query endpoint', () => {
    const endpoint = client['buildEndpoint']('graph/query');
    expect(endpoint).toContain('graph/query');
  });

  it('should construct correct residual review endpoint', () => {
    const endpoint = client['buildEndpoint']('residual/review');
    expect(endpoint).toContain('residual/review');
  });

  it('should construct correct patch review endpoint', () => {
    const endpoint = client['buildEndpoint']('patch/review');
    expect(endpoint).toContain('patch/review');
  });

  it('should handle import source request', async () => {
    const mockResponse = {
      success: true,
      componentId: 'project/TestComponent',
      files: [],
      warnings: [],
      errors: [],
      metadata: {
        sourceType: 'github',
        source: 'org/repo',
        importedAt: new Date().toISOString(),
        componentName: 'TestComponent',
        dependencies: [],
        fileCount: 0,
        totalSize: 0,
      },
      job: {
        id: 'job-123',
        status: 'REVIEW_REQUIRED',
        percentComplete: 100,
      },
    };

    // Mock fetch implementation
    global.fetch = vi.fn(() =>
      Promise.resolve({
        ok: true,
        json: () => Promise.resolve(mockResponse),
      } as Response)
    );

    const result = await client.importSource({
      sourceType: 'github',
      source: 'org/repo',
      projectId: 'project-123',
    });

    expect(result.success).toBe(true);
  });

  it('should handle graph query request', async () => {
    const mockResponse: GraphQueryResponse = {
      items: {
        nodeCount: 10,
        edgeCount: 5,
      },
      nextCursor: null,
      totalEstimate: 10,
      scope: {
        tenantId: 'tenant-123',
        projectId: 'product-456',
        queryType: 'stats',
        pageSize: 100,
        hasMore: false,
      },
    };

    global.fetch = vi.fn(() =>
      Promise.resolve({
        ok: true,
        json: () => Promise.resolve(mockResponse),
      } as Response)
    );

    const result = await client.queryGraph('product-456', 'tenant-123', 'stats', null, null, 100);

    expect(result.items.nodeCount).toBe(10);
    expect(result.scope.hasMore).toBe(false);
  });

  it('should handle residual review request', async () => {
    const mockResponse = {
      residualIslands: [
        {
          id: 'residual-1',
          kind: 'code',
          originalSource: 'unparseable code',
          normalizedSummary: 'summary',
          reasonUnmodeled: 'unsupported syntax',
          reviewRequired: true,
        },
      ],
      total: 1,
    };

    global.fetch = vi.fn(() =>
      Promise.resolve({
        ok: true,
        json: () => Promise.resolve(mockResponse),
      } as Response)
    );

    const result = await client.getResidualIslands('product-456', 'tenant-123');

    expect(result.residualIslands).toHaveLength(1);
    expect(result.residualIslands[0].reviewRequired).toBe(true);
  });

  it('should handle patch review request', async () => {
    const mockResponse = {
      patchSet: {
        id: 'patch-123',
        patches: [],
        metadata: {},
      },
      validationResults: {
        valid: true,
        errors: [],
        warnings: [],
      },
    };

    global.fetch = vi.fn(() =>
      Promise.resolve({
        ok: true,
        json: () => Promise.resolve(mockResponse),
      } as Response)
    );

    const result = await client.getPatchReview('patch-123');

    expect(result.patchSet.id).toBe('patch-123');
    expect(result.validationResults.valid).toBe(true);
  });
});

interface GraphQueryResponse {
  items: Record<string, unknown>;
  nextCursor: string | null;
  totalEstimate: number;
  scope: {
    tenantId: string;
    projectId: string;
    queryType: string;
    pageSize: number;
    hasMore: boolean;
  };
}
