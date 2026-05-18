import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  ArtifactCompilerClient,
  type ArtifactGraphIngestRequest,
  type GraphQueryRequest,
  type ResidualAnalyzeRequest,
} from '../ArtifactCompilerClient';
import { LegacyArtifactPatchBundleClient } from '../LegacyArtifactPatchBundleClient';
import { ArtifactGraphService } from '@/clients/generated/api';

type LegacyPatchMethodNames = 'approveBundle' | 'rejectBundle' | 'applyBundle';
type ArtifactClientPublicMethodNames = keyof ArtifactCompilerClient;
type LegacyMethodsOnMainClient = Extract<ArtifactClientPublicMethodNames, LegacyPatchMethodNames>;
const assertLegacyMethodsNotExposed: LegacyMethodsOnMainClient extends never ? true : never = true;

describe('ArtifactCompilerClient', () => {
  let client: ArtifactCompilerClient;

  beforeEach(() => {
    vi.restoreAllMocks();
    client = new ArtifactCompilerClient({
      baseUrl: 'http://localhost:3000',
      authToken: 'token',
      tenantId: 'tenant-123',
    });
    client.setScope({ workspaceId: 'workspace-123', projectId: 'project-123' });
  });

  it('calls generated ingest endpoint', async () => {
    const spy = vi.spyOn(ArtifactGraphService, 'ingestArtifactGraph').mockResolvedValue({
      success: true,
      message: 'ok',
    });

    const request: ArtifactGraphIngestRequest = {
      nodes: [],
      edges: [],
      unresolvedEdges: [],
      edgeResolutionRecords: [],
      residualIslands: [],
    };

    const result = await client.ingestGraph(request);

    expect(spy).toHaveBeenCalledWith('tenant-123', 'workspace-123', 'project-123', request);
    expect(result.success).toBe(true);
    expect(result.data?.message).toBe('ok');
  });

  it('calls generated query endpoint', async () => {
    const spy = vi.spyOn(ArtifactGraphService, 'queryArtifactGraph').mockResolvedValue({
      items: {},
      nextCursor: null,
      totalEstimate: 0,
      scope: {
        tenantId: 'tenant-123',
        workspaceId: 'workspace-123',
        projectId: 'project-123',
        queryType: 'stats',
        pageSize: 100,
        hasMore: false,
      },
    });

    const request: GraphQueryRequest = {
      queryType: 'stats',
    };

    const result = await client.queryGraph(request);

    expect(spy).toHaveBeenCalledWith('tenant-123', 'workspace-123', 'project-123', request);
    expect(result.success).toBe(true);
    expect(result.data?.nextCursor).toBeNull();
  });

  it('calls generated residual analysis endpoint', async () => {
    const spy = vi.spyOn(ArtifactGraphService, 'analyzeResidual').mockResolvedValue({
      success: true,
      message: 'analyzed',
    });

    const request: ResidualAnalyzeRequest = {
      projectId: 'project-123',
      tenantId: 'tenant-123',
      workspaceId: 'workspace-123',
      residualIslands: [],
    };

    const result = await client.analyzeResidual(request);

    expect(spy).toHaveBeenCalledWith('tenant-123', 'workspace-123', 'project-123', request);
    expect(result.success).toBe(true);
    expect(result.data?.message).toBe('analyzed');
  });

  it('does not expose legacy patch-bundle methods', () => {
    expect(assertLegacyMethodsNotExposed).toBe(true);
    expect('approveBundle' in client).toBe(false);
    expect('rejectBundle' in client).toBe(false);
    expect('applyBundle' in client).toBe(false);
  });

  it('uses patch compatibility endpoint via LegacyArtifactPatchBundleClient', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ success: true, bundleId: 'bundle-1', status: 'APPROVED', reviewedBy: 'u1' }),
    });

    vi.stubGlobal('fetch', fetchMock);

    const legacyClient = new LegacyArtifactPatchBundleClient({
      baseUrl: 'http://localhost:3000',
      authToken: 'token',
      tenantId: 'tenant-123',
    });
    legacyClient.setScope({ workspaceId: 'workspace-123', projectId: 'project-123' });

    const result = await legacyClient.approveBundle('bundle-1', { reviewer: 'u1' });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0]?.[0]).toContain('/api/v1/yappc/artifact/patch/bundles/bundle-1/approve');
    expect(result.success).toBe(true);
    expect(result.status).toBe('APPROVED');
  });
});
