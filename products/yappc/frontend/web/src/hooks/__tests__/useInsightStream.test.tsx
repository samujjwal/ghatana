import { act, renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { useInsightStream } from '../useInsightStream';

describe('useInsightStream', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('subscribes to project insights and merges incoming insight events', () => {
    const { result } = renderHook(() =>
      useInsightStream({
        projectId: 'project-1',
        minimumConfidence: 0.5,
      })
    );

    act(() => {
      window.dispatchEvent(
        new CustomEvent('yappc:ai-insight', {
          detail: {
            projectId: 'project-1',
            payload: {
              id: 'insight-1',
              projectId: 'project-1',
              title: 'High error budget burn',
              description: 'Rollback threshold is close to tripping.',
              severity: 'warning',
              category: 'deployment',
              confidence: 0.92,
              createdAt: '2026-04-06T12:00:00.000Z',
            },
          },
        })
      );
    });

    expect(result.current.unreadCount).toBe(1);
    expect(result.current.insights).toHaveLength(1);
    expect(result.current.insights[0]).toMatchObject({
      title: 'High error budget burn',
      category: 'deployment',
      severity: 'warning',
    });
  });

  it('filters low confidence and different project insights, and supports mark all read plus dismiss', () => {
    const { result } = renderHook(() =>
      useInsightStream({
        projectId: 'project-1',
        minimumConfidence: 0.6,
        initialInsights: [
          {
            id: 'seed-1',
            projectId: 'project-1',
            title: 'Seeded insight',
            description: 'Initial state',
            severity: 'info',
            category: 'code-quality',
            confidence: 0.8,
            createdAt: '2026-04-06T10:00:00.000Z',
            read: false,
          },
        ],
      })
    );

    act(() => {
      window.dispatchEvent(
        new CustomEvent('yappc:ai-insight', {
          detail: [
            {
              projectId: 'project-1',
              payload: {
                id: 'ignored-low-confidence',
                projectId: 'project-1',
                title: 'Low confidence',
                description: 'Should be ignored',
                severity: 'info',
                category: 'capacity',
                confidence: 0.2,
                createdAt: '2026-04-06T12:00:00.000Z',
              },
            },
            {
              projectId: 'project-2',
              payload: {
                id: 'ignored-other-project',
                projectId: 'project-2',
                title: 'Other project',
                description: 'Should be ignored',
                severity: 'info',
                category: 'capacity',
                confidence: 0.9,
                createdAt: '2026-04-06T12:00:00.000Z',
              },
            },
          ],
        })
      );
    });

    expect(result.current.insights).toHaveLength(1);
    expect(result.current.unreadCount).toBe(1);

    act(() => {
      result.current.markAllRead();
    });

    expect(result.current.unreadCount).toBe(0);

    act(() => {
      result.current.dismissInsight('seed-1');
    });

    expect(result.current.insights).toHaveLength(0);
  });
});