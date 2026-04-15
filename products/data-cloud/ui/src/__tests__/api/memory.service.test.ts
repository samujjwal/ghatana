import { beforeEach, describe, expect, it, vi } from 'vitest';

const { mockApiClient } = vi.hoisted(() => ({
  mockApiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

vi.mock('../../lib/api/client', () => ({
  apiClient: mockApiClient,
}));

import { memoryService } from '../../api/memory.service';

describe('memoryService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('unwraps memory search responses from the launcher envelope', async () => {
    mockApiClient.post.mockResolvedValue({
      agentId: 'agent-1',
      tenantId: 'tenant-a',
      query: 'rollback',
      results: [
        {
          id: 'mem-1',
          tenantId: 'tenant-a',
          agentId: 'agent-1',
          type: 'EPISODIC',
          content: 'Rollback procedure',
          tags: ['ops'],
          salience: 0.7,
          createdAt: '2026-04-14T12:00:00Z',
          metadata: {},
        },
      ],
      count: 1,
      timestamp: '2026-04-14T12:01:00Z',
    });

    const items = await memoryService.searchMemory('agent-1', 'rollback', 'tenant-a');

    expect(mockApiClient.post).toHaveBeenCalledWith(
      '/memory/agent-1/search',
      { query: 'rollback' },
      { params: { tenantId: 'tenant-a' } },
    );
    expect(items).toHaveLength(1);
    expect(items[0]?.id).toBe('mem-1');
  });

  it('sends an explicit empty retain body so the launcher handler can parse it', async () => {
    mockApiClient.put.mockResolvedValue(undefined);

    await memoryService.retainMemoryItem('agent-1', 'mem-9');

    expect(mockApiClient.put).toHaveBeenCalledWith('/memory/agent-1/mem-9/retain', {});
  });

  it('returns root memory items from the list envelope', async () => {
    mockApiClient.get.mockResolvedValue({
      agentId: 'root',
      tenantId: 'tenant-a',
      type: 'all',
      query: '',
      items: [
        {
          id: 'mem-2',
          tenantId: 'tenant-a',
          agentId: 'agent-1',
          type: 'SEMANTIC',
          content: 'Known customer preference',
          tags: ['crm'],
          salience: 0.4,
          createdAt: '2026-04-14T12:05:00Z',
          metadata: {},
        },
      ],
      total: 1,
      timestamp: '2026-04-14T12:06:00Z',
    });

    const items = await memoryService.listMemoryItems({ tenantId: 'tenant-a' });

    expect(mockApiClient.get).toHaveBeenCalledWith('/memory', { params: { tenantId: 'tenant-a' } });
    expect(items[0]?.type).toBe('SEMANTIC');
  });

  it('unwraps agent summary counts from the canonical /memory/:agentId route', async () => {
    mockApiClient.get.mockResolvedValue({
      agentId: 'agent-9',
      tenantId: 'tenant-a',
      total: 7,
      items: [],
      contextWindowSize: 12,
      byType: {
        episodic: 2,
        semantic: 1,
        procedural: 0,
        preference: 4,
        other: 0,
      },
      timestamp: '2026-04-14T12:08:00Z',
    });

    const summary = await memoryService.getAgentMemorySummary('agent-9', 'tenant-a');

    expect(mockApiClient.get).toHaveBeenCalledWith('/memory/agent-9', { params: { tenantId: 'tenant-a' } });
    expect(summary.preference).toBe(4);
  });

  it('lists tier-specific memory items when agentId and type are both provided', async () => {
    mockApiClient.get.mockResolvedValue({
      agentId: 'agent-1',
      tenantId: 'tenant-a',
      tier: 'procedural',
      items: [
        {
          id: 'mem-3',
          tenantId: 'tenant-a',
          agentId: 'agent-1',
          type: 'PROCEDURAL',
          content: 'Rollback steps',
          tags: ['ops'],
          salience: 0.9,
          createdAt: '2026-04-14T12:10:00Z',
          metadata: {},
        },
      ],
      count: 1,
      offset: 0,
      limit: 25,
      timestamp: '2026-04-14T12:10:30Z',
    });

    const items = await memoryService.listMemoryItems({
      agentId: 'agent-1',
      type: 'PROCEDURAL',
      tenantId: 'tenant-a',
      limit: 25,
    });

    expect(mockApiClient.get).toHaveBeenCalledWith('/memory/agent-1/procedural', {
      params: { tenantId: 'tenant-a', limit: 25 },
    });
    expect(items[0]?.type).toBe('PROCEDURAL');
  });

  it('lists agent-root memory items when only agentId is provided', async () => {
    mockApiClient.get.mockResolvedValue({
      agentId: 'agent-2',
      tenantId: 'tenant-a',
      type: 'all',
      query: '',
      items: [
        {
          id: 'mem-4',
          tenantId: 'tenant-a',
          agentId: 'agent-2',
          type: 'EPISODIC',
          content: 'Escalation note',
          tags: ['support'],
          salience: 0.55,
          createdAt: '2026-04-14T12:11:00Z',
          metadata: {},
        },
      ],
      total: 1,
      timestamp: '2026-04-14T12:11:30Z',
    });

    const items = await memoryService.listMemoryItems({ agentId: 'agent-2', tenantId: 'tenant-a' });

    expect(mockApiClient.get).toHaveBeenCalledWith('/memory/agent-2', { params: { tenantId: 'tenant-a' } });
    expect(items[0]?.id).toBe('mem-4');
  });

  it('maps canonical learning status into the memory consolidation summary model', async () => {
    mockApiClient.get.mockResolvedValue({
      running: false,
      lastRunTime: '2026-04-14T12:00:00Z',
      nextScheduledRun: '2026-04-14T13:00:00Z',
      intervalMinutes: 60,
      pendingReviews: 2,
      lastResult: {
        status: 'COMPLETED',
        recordsAnalyzed: 24,
        patternsDiscovered: 3,
        patternsUpdated: 1,
        ranAt: '2026-04-14T12:00:00Z',
      },
      timestamp: '2026-04-14T12:01:00Z',
    });

    const status = await memoryService.getConsolidationStatus('tenant-a');

    expect(mockApiClient.get).toHaveBeenCalledWith('/learning/status', { params: { tenantId: 'tenant-a' } });
    expect(status).toEqual({
      lastRun: '2026-04-14T12:00:00Z',
      episodesProcessed: 24,
      policiesExtracted: 3,
      nextScheduled: '2026-04-14T13:00:00Z',
    });
  });

  it('maps not-started learning status into an empty consolidation schedule', async () => {
    mockApiClient.get.mockResolvedValue({
      running: false,
      lastRunTime: 'never',
      nextScheduledRun: 'not started',
      intervalMinutes: 60,
      pendingReviews: 0,
      lastResult: {
        status: 'NOT_STARTED',
        ranAt: '2026-04-14T14:00:00Z',
        recordsAnalyzed: 0,
        patternsDiscovered: 0,
      },
      timestamp: '2026-04-14T14:00:00Z',
    });

    const status = await memoryService.getConsolidationStatus();

    expect(mockApiClient.get).toHaveBeenCalledWith('/learning/status', { params: {} });
    expect(status).toEqual({
      lastRun: '2026-04-14T14:00:00Z',
      episodesProcessed: 0,
      policiesExtracted: 0,
      nextScheduled: undefined,
    });
  });
});