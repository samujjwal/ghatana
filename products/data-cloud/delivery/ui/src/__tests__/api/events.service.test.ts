import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TEST_TENANT_ID } from '@/__tests__/test-utils/tenants';

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

import { eventsService } from '../../api/events.service';

describe('eventsService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('maps legacy event query params to the canonical events endpoint parameters', async () => {
    mockApiClient.get.mockResolvedValue({
      events: [],
      count: 0,
      fromOffset: 10,
      nextOffset: 10,
      tenantId: TEST_TENANT_ID,
      timestamp: '2026-04-14T12:00:00Z',
    });

    await eventsService.listEvents({
      tenantId: TEST_TENANT_ID,
      eventType: 'entity.created',
      from: '10',
      limit: 50,
      tier: 'HOT',
    });

    expect(mockApiClient.get).toHaveBeenCalledWith('/events', {
      params: {
        tenantId: TEST_TENANT_ID,
        type: 'entity.created',
        from: '10',
        limit: 50,
      },
    });
  });

  it('derives event stats from the canonical events list response', async () => {
    mockApiClient.get.mockResolvedValue({
      events: [
        { offset: 1, type: 'entity.created', payload: { tier: 'HOT' }, timestamp: '2026-04-14T12:00:00Z' },
        { offset: 2, type: 'entity.created', payload: { tier: 'HOT' }, timestamp: '2026-04-14T12:01:00Z' },
        { offset: 3, type: 'entity.deleted', payload: { tier: 'COLD' }, timestamp: '2026-04-14T12:02:00Z' },
      ],
      count: 3,
      fromOffset: 0,
      nextOffset: 3,
      tenantId: TEST_TENANT_ID,
      timestamp: '2026-04-14T12:03:00Z',
    });

    const stats = await eventsService.getStats(TEST_TENANT_ID);

    expect(mockApiClient.get).toHaveBeenCalledWith('/events', { params: { tenantId: TEST_TENANT_ID, limit: 1000 } });
    expect(stats.total).toBe(3);
    expect(stats.byTier.HOT).toBe(2);
    expect(stats.byTier.COLD).toBe(1);
    expect(stats.byType['entity.created']).toBe(2);
    expect(stats.eventsPerMinute).toBe(3);
  });

  it('adapts canonical event envelopes into the UI event model', async () => {
    mockApiClient.get.mockResolvedValue({
      events: [
        {
          offset: 42,
          type: 'pipeline.failed',
          payload: { pipelineId: 'pipe-1', tier: 'HOT' },
          timestamp: '2026-04-14T12:05:00Z',
          source: 'pipeline-engine',
          correlationId: 'corr-9',
          headers: { 'idempotency-key': 'idem-1' },
        },
      ],
      count: 1,
      fromOffset: 42,
      nextOffset: 43,
      tenantId: TEST_TENANT_ID,
      timestamp: '2026-04-14T12:05:01Z',
    });

    const response = await eventsService.listEvents({ tenantId: TEST_TENANT_ID, limit: 50 });

    expect(response.total).toBe(1);
    expect(response.hasMore).toBe(false);
    expect(response.events[0]).toMatchObject({
      id: 'event-42',
      tenantId: TEST_TENANT_ID,
      eventType: 'pipeline.failed',
      tier: 'HOT',
      source: 'pipeline-engine',
      correlationId: 'corr-9',
      idempotencyKey: 'idem-1',
    });
  });

  it('uses the canonical stream query parameter for event-type filtering', () => {
    const OriginalEventSource = globalThis.EventSource;
    const eventSourceMock = vi.fn();
    class FakeEventSource {
      constructor(url: string) {
        eventSourceMock(url);
      }

      close(): void {}
    }

    vi.stubGlobal('EventSource', FakeEventSource as unknown as typeof EventSource);

    eventsService.openStream({ tenantId: TEST_TENANT_ID, eventType: 'alert.triggered', tier: 'HOT' });

    expect(eventSourceMock).toHaveBeenCalledWith(`/api/v1/events/stream?tenantId=${TEST_TENANT_ID}&types=alert.triggered`);

    if (OriginalEventSource) {
      vi.stubGlobal('EventSource', OriginalEventSource);
    } else {
      vi.unstubAllGlobals();
    }
  });

  it('parses live event messages from the canonical stream payload', () => {
    const event = eventsService.parseLiveEvent(JSON.stringify({
      offset: 7,
      type: 'alert.triggered',
      payload: { severity: 'critical' },
      timestamp: '2026-04-14T12:15:00Z',
    }), 'tenant-live');

    expect(event.id).toBe('event-7');
    expect(event.tenantId).toBe('tenant-live');
    expect(event.eventType).toBe('alert.triggered');
    expect(event.metadata.offset).toBe(7);
  });
});