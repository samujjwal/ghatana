import { beforeEach, describe, expect, it, vi } from 'vitest';

const { mockApiClient } = vi.hoisted(() => ({
  mockApiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

vi.mock('../../lib/api/client', () => ({
  apiClient: mockApiClient,
}));

import { getRecentActivity, logActivity } from '../../lib/api/user-activity';

describe('user-activity api', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('parses recent activity responses', async () => {
    mockApiClient.get.mockResolvedValueOnce({
      activities: [
        {
          id: 'activity-1',
          action: 'navigate',
          target: 'Explore Data',
          timestamp: '2026-04-15T13:00:00Z',
          type: 'query',
          resourceType: 'navigation',
          resourceId: '/data',
        },
      ],
      continueWorking: [
        {
          id: 'workflow-1',
          name: 'Daily Sync',
          type: 'dashboard',
          lastAccessed: '2026-04-15T12:55:00Z',
          path: '/insights',
        },
      ],
    });

    const response = await getRecentActivity();

    expect(mockApiClient.get).toHaveBeenCalledWith('/user-activity/recent');
    expect(response.activities).toHaveLength(1);
    expect(response.continueWorking).toHaveLength(1);
    expect(response.continueWorking[0].type).toBe('insight');
  });

  it('validates log activity requests before posting', async () => {
    mockApiClient.post.mockResolvedValueOnce({ ok: true });

    await logActivity({
      action: 'navigate',
      target: 'Build Pipeline',
      type: 'create',
      resourceType: 'navigation',
      resourceId: '/pipelines/new',
    });

    expect(mockApiClient.post).toHaveBeenCalledWith('/user-activity/log', {
      action: 'navigate',
      target: 'Build Pipeline',
      type: 'create',
      resourceType: 'navigation',
      resourceId: '/pipelines/new',
    });
  });
});