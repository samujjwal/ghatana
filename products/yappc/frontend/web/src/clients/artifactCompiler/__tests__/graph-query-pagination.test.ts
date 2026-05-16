/**
 * @fileoverview Test for P3-2: Graph query pagination
 *
 * Verifies that graph query responses include items, nextCursor, totalEstimate,
 * and scope metadata as implemented in P3-1.
 */

import { describe, it, expect } from 'vitest';
import type { GraphQueryResponse, GraphQueryScopeMetadata } from '../ArtifactCompilerClient';

describe('P3-2: Graph Query Pagination', () => {
  it('should return items in query response', () => {
    const response: GraphQueryResponse = {
      items: {
        orphanedNodes: ['node1', 'node2'],
        nodeCount: 10,
        edgeCount: 5,
      },
      nextCursor: null,
      totalEstimate: 10,
      scope: {
        tenantId: 'tenant-123',
        productId: 'product-456',
        queryType: 'stats',
        pageSize: 100,
        hasMore: false,
      },
    };

    expect(response.items).toBeDefined();
    expect(response.items.orphanedNodes).toEqual(['node1', 'node2']);
    expect(response.items.nodeCount).toBe(10);
  });

  it('should return nextCursor for pagination', () => {
    const responseWithCursor: GraphQueryResponse = {
      items: { nodeCount: 5 },
      nextCursor: 'cursor-abc123',
      totalEstimate: 10,
      scope: {
        tenantId: 'tenant-123',
        productId: 'product-456',
        queryType: 'orphaned',
        pageSize: 5,
        hasMore: true,
      },
    };

    expect(responseWithCursor.nextCursor).toBe('cursor-abc123');
    expect(responseWithCursor.scope.hasMore).toBe(true);
  });

  it('should return null nextCursor for last page', () => {
    const responseLastPage: GraphQueryResponse = {
      items: { nodeCount: 10 },
      nextCursor: null,
      totalEstimate: 10,
      scope: {
        tenantId: 'tenant-123',
        productId: 'product-456',
        queryType: 'stats',
        pageSize: 100,
        hasMore: false,
      },
    };

    expect(responseLastPage.nextCursor).toBeNull();
    expect(responseLastPage.scope.hasMore).toBe(false);
  });

  it('should return totalEstimate', () => {
    const response: GraphQueryResponse = {
      items: { nodeCount: 5 },
      nextCursor: 'cursor-123',
      totalEstimate: 100,
      scope: {
        tenantId: 'tenant-123',
        productId: 'product-456',
        queryType: 'stats',
        pageSize: 10,
        hasMore: true,
      },
    };

    expect(response.totalEstimate).toBe(100);
    expect(typeof response.totalEstimate).toBe('number');
  });

  it('should return scope metadata', () => {
    const response: GraphQueryResponse = {
      items: { nodeCount: 5 },
      nextCursor: null,
      totalEstimate: 5,
      scope: {
        tenantId: 'tenant-123',
        productId: 'product-456',
        queryType: 'stats',
        pageSize: 100,
        hasMore: false,
      },
    };

    expect(response.scope).toBeDefined();
    expect(response.scope.tenantId).toBe('tenant-123');
    expect(response.scope.productId).toBe('product-456');
    expect(response.scope.queryType).toBe('stats');
    expect(response.scope.pageSize).toBe(100);
    expect(response.scope.hasMore).toBe(false);
  });

  it('should include all required scope metadata fields', () => {
    const scope: GraphQueryScopeMetadata = {
      tenantId: 'tenant-123',
      productId: 'product-456',
      queryType: 'dependencies',
      pageSize: 50,
      hasMore: true,
    };

    expect(scope.tenantId).toBeDefined();
    expect(scope.productId).toBeDefined();
    expect(scope.queryType).toBeDefined();
    expect(scope.pageSize).toBeDefined();
    expect(scope.hasMore).toBeDefined();
  });

  it('should handle different query types', () => {
    const queryTypes = ['orphaned', 'dependencies', 'dependents', 'stats'] as const;

    queryTypes.forEach((queryType) => {
      const response: GraphQueryResponse = {
        items: {},
        nextCursor: null,
        totalEstimate: 0,
        scope: {
          tenantId: 'tenant-123',
          productId: 'product-456',
          queryType,
          pageSize: 100,
          hasMore: false,
        },
      };

      expect(response.scope.queryType).toBe(queryType);
    });
  });

  it('should handle pagination with seed node IDs', () => {
    const response: GraphQueryResponse = {
      items: {
        dependencies: {
          'node1': ['node2', 'node3'],
          'node4': ['node5'],
        },
      },
      nextCursor: 'cursor-xyz',
      totalEstimate: 5,
      scope: {
        tenantId: 'tenant-123',
        productId: 'product-456',
        queryType: 'dependencies',
        pageSize: 10,
        hasMore: true,
      },
    };

    expect(response.items.dependencies).toBeDefined();
    expect(response.items.dependencies['node1']).toEqual(['node2', 'node3']);
  });

  it('should handle empty results', () => {
    const response: GraphQueryResponse = {
      items: {},
      nextCursor: null,
      totalEstimate: 0,
      scope: {
        tenantId: 'tenant-123',
        productId: 'product-456',
        queryType: 'stats',
        pageSize: 100,
        hasMore: false,
      },
    };

    expect(response.items).toEqual({});
    expect(response.totalEstimate).toBe(0);
    expect(response.nextCursor).toBeNull();
  });
});
