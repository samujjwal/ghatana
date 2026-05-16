import { describe, expect, it } from 'vitest';

import { importFromSource } from '../ImportSourceWorkflow';

describe('import-source flow', () => {
  it('imports through governed async backend flow and preserves review status plus normalized residuals', async () => {
    const originalFetch = globalThis.fetch;
    const calls: Array<{ input: RequestInfo | URL; init?: RequestInit }> = [];

    const runningJob = {
      id: '9f3f8bcf-3bc1-4f5d-b8bf-562f9c9ef204',
      status: 'FETCHING_SOURCE',
      tenantId: 'tenant-1',
      workspaceId: 'workspace-1',
      projectId: 'proj-1',
      sourceType: 'tsx',
      source: 'https://example.com/RepoImport.tsx',
      percentComplete: 55,
      currentStep: 'fetch_source',
      steps: [
        { id: 'validate_scope', label: 'Validate scope', status: 'completed', percent: 20 },
        { id: 'fetch_source', label: 'Fetch source', status: 'running', percent: 55 },
      ],
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };

    const reviewJob = {
      ...runningJob,
      status: 'REVIEW_REQUIRED',
      percentComplete: 100,
      currentStep: 'audit',
      steps: runningJob.steps.map((step) => ({ ...step, status: 'completed', percent: 100 })),
      updatedAt: new Date().toISOString(),
    };

    const startResponse = {
      success: true,
      componentId: 'proj-1/RepoImport',
      files: [],
      warnings: [],
      errors: [],
      metadata: {
        sourceType: 'tsx',
        source: 'https://example.com/RepoImport.tsx',
        importedAt: new Date().toISOString(),
        componentName: 'RepoImport',
        dependencies: [],
        fileCount: 0,
        totalSize: 0,
      },
      residuals: [
        {
          id: 'e509ecce-3346-40ee-8fcb-4eb3f0b4f3f4',
          sourcePath: 'src/legacy.tsx',
          type: 'dynamic-pattern',
          confidence: 0.3,
          requiresReview: true,
          description: 'Dynamic branch requires manual review',
          lineRange: [14, 20],
        },
      ],
      job: runningJob,
    };

    globalThis.fetch = async (input, init) => {
      calls.push({ input, init });
      const body = input === '/api/v1/yappc/artifact/import-source'
        ? startResponse
        : { job: reviewJob };
      return new Response(JSON.stringify(body), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    };

    try {
      const result = await importFromSource({
        sourceType: 'tsx',
        source: 'https://example.com/RepoImport.tsx',
        projectId: 'proj-1',
        options: {
          requireServerImport: true,
          tenantId: 'tenant-1',
          workspaceId: 'workspace-1',
          jobPollIntervalMs: 0,
        },
      });

      expect(result.success).toBe(true);
      expect(result.job).toMatchObject({
        id: '9f3f8bcf-3bc1-4f5d-b8bf-562f9c9ef204',
        status: 'REVIEW_REQUIRED',
        currentStep: 'audit',
      });
      expect(result.residuals).toEqual([
        expect.objectContaining({
          id: 'e509ecce-3346-40ee-8fcb-4eb3f0b4f3f4',
          kind: 'code',
          reasonUnmodeled: 'dynamic-pattern',
          reviewRequired: true,
          sourceLocation: expect.objectContaining({
            filePath: 'src/legacy.tsx',
            startLine: 14,
            endLine: 20,
          }),
        }),
      ]);
      expect(calls.map((call) => call.input)).toEqual([
        '/api/v1/yappc/artifact/import-source',
        '/api/v1/yappc/artifact/import-source/9f3f8bcf-3bc1-4f5d-b8bf-562f9c9ef204',
      ]);
      expect(calls[1]?.init?.headers).toMatchObject({
        'X-Tenant-ID': 'tenant-1',
        'X-Workspace-ID': 'workspace-1',
        'X-Project-ID': 'proj-1',
      });
    } finally {
      globalThis.fetch = originalFetch;
    }
  });
});