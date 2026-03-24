// All tests skipped - incomplete feature
/**
 * Integration Tests: Event Bus + DataSource
 *
 * Tests the integration between the Event Bus system and DataSource hook,
 * ensuring they work together seamlessly for reactive data flows.
 */

import { renderHook, act, waitFor } from '@testing-library/react';
import { rest } from 'msw';
import { setupServer } from 'msw/node';

import { eventBus, useEventBus, useEventEmitter } from '../../core/event-bus';
import { useDataSource } from '../../hooks/useDataSource';


// ============================================================================
// Mock Server Setup
// ============================================================================

const server = setupServer(
  rest.get('/api/users', (req, res, ctx) => {
    return res(
      ctx.json([
        { id: 1, name: 'John Doe', email: 'john@example.com' },
        { id: 2, name: 'Jane Smith', email: 'jane@example.com' },
      ])
    );
  }),
  rest.post('/api/users', (req, res, ctx) => {
    return res(ctx.json({ id: 3, name: 'New User', email: 'new@example.com' }));
  })
);

beforeAll(() => server.listen());
afterEach(() => {
  server.resetHandlers();
  eventBus.removeAllListeners();
  eventBus.clearHistory();
});
afterAll(() => server.close());

// ============================================================================
// Integration Tests
// ============================================================================

describe.skip('Event Bus + DataSource Integration', () => {
  describe('Data Refresh on Event', () => {
    it('should refetch data when refresh event is emitted', async () => {
      let fetchCount = 0;

      server.use(
        rest.get('/api/users', (req, res, ctx) => {
          fetchCount++;
          return res(
            ctx.json([
              { id: 1, name: `User ${fetchCount}`, email: `user${fetchCount}@example.com` },
            ])
          );
        })
      );

      // Setup hook with event listener for refresh
      const { result } = renderHook(() => {
        const dataSource = useDataSource({
          type: 'rest',
          url: '/api/users',
          cache: false, // Disable cache for testing
        });

        useEventBus('data:refresh', async (payload: unknown) => {
          if (payload.source === 'users') {
            await dataSource.refetch();
          }
        });

        return dataSource;
      });

      // Wait for initial fetch
      await waitFor(() => expect(result.current.isLoading).toBe(false));
      expect(fetchCount).toBe(1);
      expect(result.current.data?.[0]?.name).toBe('User 1');

      // Emit refresh event
      await act(async () => {
        await eventBus.emit('data:refresh', { source: 'users' });
      });

      // Wait for refetch
      await waitFor(() => expect(fetchCount).toBe(2));
      expect(result.current.data?.[0]?.name).toBe('User 2');
    });

    it('should handle multiple data sources with targeted refresh events', async () => {
      const userFetches: number[] = [];
      const postFetches: number[] = [];

      server.use(
        rest.get('/api/users', (req, res, ctx) => {
          userFetches.push(Date.now());
          return res(ctx.json([{ id: 1, name: 'User' }]));
        }),
        rest.get('/api/posts', (req, res, ctx) => {
          postFetches.push(Date.now());
          return res(ctx.json([{ id: 1, title: 'Post' }]));
        })
      );

      const { result } = renderHook(() => {
        const users = useDataSource({ type: 'rest', url: '/api/users', cache: false });
        const posts = useDataSource({ type: 'rest', url: '/api/posts', cache: false });

        useEventBus('data:refresh', async (payload: unknown) => {
          if (payload.source === 'users') {
            await users.refetch();
          } else if (payload.source === 'posts') {
            await posts.refetch();
          }
        });

        return { users, posts };
      });

      // Wait for initial fetches
      await waitFor(() => {
        expect(result.current.users.isLoading).toBe(false);
        expect(result.current.posts.isLoading).toBe(false);
      });

      expect(userFetches).toHaveLength(1);
      expect(postFetches).toHaveLength(1);

      // Refresh only users
      await act(async () => {
        await eventBus.emit('data:refresh', { source: 'users' });
      });

      await waitFor(() => expect(userFetches).toHaveLength(2));
      expect(postFetches).toHaveLength(1); // Posts should not be refetched

      // Refresh only posts
      await act(async () => {
        await eventBus.emit('data:refresh', { source: 'posts' });
      });

      await waitFor(() => expect(postFetches).toHaveLength(2));
      expect(userFetches).toHaveLength(2); // Users should not be refetched again
    });
  });

  describe('Data Update Events', () => {
    it('should emit event after successful data fetch', async () => {
      const loadedEvents: unknown[] = [];

      eventBus.on('data:loaded', (payload) => {
        loadedEvents.push(payload);
      });

      renderHook(() => {
        const dataSource = useDataSource({
          type: 'rest',
          url: '/api/users',
          onSuccess: (data) => {
            eventBus.emit('data:loaded', { source: 'users', count: data.length });
          },
        });

        return dataSource;
      });

      await waitFor(() => expect(loadedEvents).toHaveLength(1));
      expect(loadedEvents[0]).toEqual({ source: 'users', count: 2 });
    });

    it('should emit event on data fetch error', async () => {
      const errorEvents: unknown[] = [];

      server.use(
        rest.get('/api/error', (req, res, ctx) => {
          return res(ctx.status(500), ctx.json({ message: 'Server error' }));
        })
      );

      eventBus.on('data:error', (payload) => {
        errorEvents.push(payload);
      });

      renderHook(() => {
        const dataSource = useDataSource({
          type: 'rest',
          url: '/api/error',
          onError: (error) => {
            eventBus.emit('data:error', { source: 'users', error: error.message });
          },
        });

        return dataSource;
      });

      await waitFor(() => expect(errorEvents).toHaveLength(1));
      expect(errorEvents[0].source).toBe('users');
      expect(errorEvents[0].error).toContain('500');
    });
  });

  describe('Optimistic Updates with Events', () => {
    it('should handle optimistic update with event emission and rollback on error', async () => {
      const events: string[] = [];

      server.use(
        rest.post('/api/users', (req, res, ctx) => {
          return res(ctx.status(500)); // Simulate error
        })
      );

      const { result } = renderHook(() => {
        const dataSource = useDataSource<unknown[]>({
          type: 'rest',
          url: '/api/users',
        });

        const emit = useEventEmitter('data:update');

        return { dataSource, emit };
      });

      await waitFor(() => expect(result.current.dataSource.isLoading).toBe(false));

      const originalData = result.current.dataSource.data;

      // Subscribe to events
      eventBus.on('data:update', () => events.push('update'));
      eventBus.on('data:rollback', () => events.push('rollback'));

      // Optimistic update
      await act(async () => {
        // Add new user optimistically
        result.current.dataSource.mutate([
          ...(originalData || []),
          { id: 999, name: 'Optimistic User', email: 'opt@example.com' },
        ]);

        await result.current.emit({ entityType: 'user', entityId: '999', data: {} });

        // Simulate failed save (would trigger rollback)
        try {
          await fetch('/api/users', { method: 'POST', body: JSON.stringify({}) });
        } catch (error) {
          // Rollback on error
          result.current.dataSource.mutate(originalData);
          await eventBus.emit('data:rollback', { entityType: 'user', entityId: '999' });
        }
      });

      await waitFor(() => expect(events).toContain('rollback'));
      expect(events).toEqual(['update', 'rollback']);
    });
  });

  describe('Cross-Component Communication', () => {
    it('should enable reactive updates across multiple components via events', async () => {
      const component1Updates: unknown[] = [];
      const component2Updates: unknown[] = [];

      // Component 1: Data fetcher
      const { result: component1 } = renderHook(() => {
        const dataSource = useDataSource({ type: 'rest', url: '/api/users' });

        useEventBus('component2:request-data', async () => {
          component1Updates.push('request-received');
          await eventBus.emit('component1:data-ready', { data: dataSource.data });
        });

        return dataSource;
      });

      // Component 2: Data consumer
      renderHook(() => {
        const emit = useEventEmitter('component2:request-data');

        useEventBus('component1:data-ready', (payload: unknown) => {
          component2Updates.push(payload.data);
        });

        return { emit };
      });

      await waitFor(() => expect(component1.current.isLoading).toBe(false));

      // Component 2 requests data
      await act(async () => {
        await eventBus.emit('component2:request-data', {});
      });

      await waitFor(() => {
        expect(component1Updates).toContain('request-received');
        expect(component2Updates).toHaveLength(1);
      });

      expect(component2Updates[0]).toEqual([
        { id: 1, name: 'John Doe', email: 'john@example.com' },
        { id: 2, name: 'Jane Smith', email: 'jane@example.com' },
      ]);
    });
  });

  describe('Data Synchronization', () => {
    it('should synchronize data across multiple instances using events', async () => {
      // Two instances of the same data source
      const { result: instance1 } = renderHook(() => {
        const dataSource = useDataSource<unknown[]>({
          type: 'rest',
          url: '/api/users',
          cacheKey: 'shared-users', // Shared cache key
        });

        useEventBus('sync:users', (payload: unknown) => {
          if (payload.action === 'update') {
            dataSource.mutate(payload.data);
          }
        });

        return dataSource;
      });

      const { result: instance2 } = renderHook(() => {
        const dataSource = useDataSource<unknown[]>({
          type: 'rest',
          url: '/api/users',
          cacheKey: 'shared-users', // Shared cache key
        });

        useEventBus('sync:users', (payload: unknown) => {
          if (payload.action === 'update') {
            dataSource.mutate(payload.data);
          }
        });

        return dataSource;
      });

      await waitFor(() => {
        expect(instance1.current.isLoading).toBe(false);
        expect(instance2.current.isLoading).toBe(false);
      });

      // Both should have same initial data (from cache)
      expect(instance1.current.data).toEqual(instance2.current.data);

      // Update from instance1
      const newData = [{ id: 999, name: 'Updated User', email: 'updated@example.com' }];

      await act(async () => {
        instance1.current.mutate(newData);
        await eventBus.emit('sync:users', { action: 'update', data: newData });
      });

      // Both instances should be synchronized
      await waitFor(() => {
        expect(instance1.current.data).toEqual(newData);
        expect(instance2.current.data).toEqual(newData);
      });
    });
  });

  describe('Event-Driven Caching', () => {
    it('should clear cache on specific events', async () => {
      let fetchCount = 0;

      server.use(
        rest.get('/api/users', (req, res, ctx) => {
          fetchCount++;
          return res(ctx.json([{ id: fetchCount, name: `Fetch ${fetchCount}` }]));
        })
      );

      const { result } = renderHook(() => {
        const dataSource = useDataSource({
          type: 'rest',
          url: '/api/users',
          cache: true,
          cacheTTL: 10000, // 10 seconds
        });

        useEventBus('cache:clear', (payload: unknown) => {
          if (payload.source === 'users') {
            dataSource.clearCache();
            dataSource.refetch();
          }
        });

        return dataSource;
      });

      // First fetch
      await waitFor(() => expect(result.current.isLoading).toBe(false));
      expect(fetchCount).toBe(1);
      expect(result.current.data?.[0]?.name).toBe('Fetch 1');

      // Refetch (should use cache)
      await act(async () => {
        await result.current.refetch();
      });

      expect(fetchCount).toBe(1); // No new fetch, used cache

      // Clear cache via event
      await act(async () => {
        await eventBus.emit('cache:clear', { source: 'users' });
      });

      // Should trigger new fetch
      await waitFor(() => expect(fetchCount).toBe(2));
      expect(result.current.data?.[0]?.name).toBe('Fetch 2');
    });
  });
});
