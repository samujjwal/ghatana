import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
  optimizedPrismaClient,
  cacheQuery,
  batchQueries,
  selectMinimalFields,
  paginateWithCursor,
  optimizeIncludes,
  monitorQueryPerformance,
} from './optimization';

describe('optimizedPrismaClient', () => {
  it('should create client with optimized settings', () => {
    const client = optimizedPrismaClient({
      logQueries: true,
      connectionPoolSize: 10,
    });
    expect(client).toBeDefined();
  });
});

describe('cacheQuery', () => {
  it('should cache query results', async () => {
    const queryFn = vi.fn().mockResolvedValue({ data: 'test' });
    
    // First call - should execute query
    const result1 = await cacheQuery('test-key', queryFn, 60);
    expect(result1).toEqual({ data: 'test' });
    expect(queryFn).toHaveBeenCalledTimes(1);
    
    // Second call - should return cached result
    const result2 = await cacheQuery('test-key', queryFn, 60);
    expect(result2).toEqual({ data: 'test' });
    expect(queryFn).toHaveBeenCalledTimes(1); // Not called again
  });

  it('should handle query errors', async () => {
    const queryFn = vi.fn().mockRejectedValue(new Error('Query failed'));
    
    await expect(cacheQuery('error-key', queryFn)).rejects.toThrow('Query failed');
  });
});

describe('batchQueries', () => {
  it('should batch multiple queries', async () => {
    const queries = [
      { model: 'User', operation: 'findUnique', args: { where: { id: 1 } } },
      { model: 'Post', operation: 'findMany', args: { where: { authorId: 1 } } },
    ];
    
    const mockPrisma = {
      $transaction: vi.fn().mockResolvedValue([
        { id: 1, name: 'User 1' },
        [{ id: 1, title: 'Post 1' }],
      ]),
    };
    
    const results = await batchQueries(mockPrisma as any, queries);
    
    expect(results).toHaveLength(2);
    expect(mockPrisma.$transaction).toHaveBeenCalled();
  });
});

describe('selectMinimalFields', () => {
  it('should return minimal field selection', () => {
    const minimal = selectMinimalFields('User');
    expect(minimal).toBeDefined();
    expect(minimal.select).toBeDefined();
  });

  it('should work with custom fields', () => {
    const custom = selectMinimalFields('Post', ['id', 'title', 'published']);
    expect(custom.select).toBeDefined();
  });
});

describe('paginateWithCursor', () => {
  it('should create cursor-based pagination', () => {
    const pagination = paginateWithCursor(10, 'cursor-123');
    
    expect(pagination).toEqual({
      take: 10,
      skip: 1,
      cursor: { id: 'cursor-123' },
    });
  });

  it('should handle first page (no cursor)', () => {
    const pagination = paginateWithCursor(20);
    
    expect(pagination).toEqual({
      take: 20,
      skip: 0,
    });
    expect(pagination.cursor).toBeUndefined();
  });
});

describe('optimizeIncludes', () => {
  it('should optimize includes for query', () => {
    const optimized = optimizeIncludes(['posts', 'comments']);
    
    expect(optimized).toEqual({
      posts: {
        select: {
          id: true,
          title: true,
          createdAt: true,
        },
      },
      comments: {
        select: {
          id: true,
          content: true,
          createdAt: true,
        },
      },
    });
  });

  it('should handle empty includes', () => {
    const optimized = optimizeIncludes([]);
    expect(optimized).toEqual({});
  });
});

describe('monitorQueryPerformance', () => {
  it('should monitor query execution time', async () => {
    const mockPrisma = {
      $on: vi.fn(),
    };
    
    const monitor = monitorQueryPerformance(mockPrisma as any);
    
    expect(monitor).toBeDefined();
    expect(mockPrisma.$on).toHaveBeenCalledWith('query', expect.any(Function));
  });
});
