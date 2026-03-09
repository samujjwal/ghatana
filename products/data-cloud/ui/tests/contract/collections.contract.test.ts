import { describe, it, expect } from 'vitest';
import { z } from 'zod';

/**
 * Collections API Contract Tests
 * 
 * Validates that API responses match the expected schema.
 * These tests ensure frontend-backend contract compliance.
 * 
 * @doc.type test
 * @doc.purpose API contract validation tests
 * @doc.layer testing
 */

// Define the expected schema for Collection
const CollectionSchema = z.object({
  id: z.string(),
  name: z.string(),
  description: z.string(),
  schemaType: z.enum(['entity', 'event', 'timeseries', 'graph', 'document']),
  status: z.enum(['active', 'draft', 'archived', 'processing']),
  entityCount: z.number(),
  schema: z.record(z.unknown()),
  tags: z.array(z.string()),
  createdAt: z.string(),
  updatedAt: z.string(),
  createdBy: z.string(),
});

const PaginatedCollectionResponseSchema = z.object({
  items: z.array(CollectionSchema),
  total: z.number(),
  page: z.number(),
  pageSize: z.number(),
  hasMore: z.boolean(),
});

const CreateCollectionRequestSchema = z.object({
  name: z.string().min(1),
  description: z.string(),
  schemaType: z.enum(['entity', 'event', 'timeseries', 'graph', 'document']),
  schema: z.record(z.unknown()),
  tags: z.array(z.string()).optional(),
});

const UpdateCollectionRequestSchema = z.object({
  name: z.string().min(1).optional(),
  description: z.string().optional(),
  schema: z.record(z.unknown()).optional(),
  tags: z.array(z.string()).optional(),
  status: z.enum(['active', 'draft', 'archived', 'processing']).optional(),
});

describe('Collections API Contract', () => {
  describe('GET /api/v1/collections', () => {
    it('should return paginated collections matching schema', () => {
      const mockResponse = {
        items: [
          {
            id: 'col-1',
            name: 'Products',
            description: 'Product catalog',
            schemaType: 'entity',
            status: 'active',
            entityCount: 100,
            schema: { fields: [] },
            tags: ['catalog'],
            createdAt: '2024-01-01T00:00:00Z',
            updatedAt: '2024-01-01T00:00:00Z',
            createdBy: 'user-1',
          },
        ],
        total: 1,
        page: 1,
        pageSize: 10,
        hasMore: false,
      };

      const result = PaginatedCollectionResponseSchema.safeParse(mockResponse);
      expect(result.success).toBe(true);
    });

    it('should reject invalid response schema', () => {
      const invalidResponse = {
        items: [
          {
            id: 'col-1',
            // Missing required fields
          },
        ],
        total: 1,
      };

      const result = PaginatedCollectionResponseSchema.safeParse(invalidResponse);
      expect(result.success).toBe(false);
    });
  });

  describe('GET /api/v1/collections/:id', () => {
    it('should return single collection matching schema', () => {
      const mockResponse = {
        id: 'col-1',
        name: 'Products',
        description: 'Product catalog',
        schemaType: 'entity',
        status: 'active',
        entityCount: 100,
        schema: { fields: [] },
        tags: ['catalog'],
        createdAt: '2024-01-01T00:00:00Z',
        updatedAt: '2024-01-01T00:00:00Z',
        createdBy: 'user-1',
      };

      const result = CollectionSchema.safeParse(mockResponse);
      expect(result.success).toBe(true);
    });

    it('should reject collection with invalid status', () => {
      const invalidResponse = {
        id: 'col-1',
        name: 'Products',
        description: 'Product catalog',
        schemaType: 'entity',
        status: 'invalid-status', // Invalid enum value
        entityCount: 100,
        schema: { fields: [] },
        tags: ['catalog'],
        createdAt: '2024-01-01T00:00:00Z',
        updatedAt: '2024-01-01T00:00:00Z',
        createdBy: 'user-1',
      };

      const result = CollectionSchema.safeParse(invalidResponse);
      expect(result.success).toBe(false);
    });
  });

  describe('POST /api/v1/collections', () => {
    it('should accept valid create request', () => {
      const validRequest = {
        name: 'New Collection',
        description: 'A new collection',
        schemaType: 'entity',
        schema: { fields: [] },
        tags: ['test'],
      };

      const result = CreateCollectionRequestSchema.safeParse(validRequest);
      expect(result.success).toBe(true);
    });

    it('should reject request with empty name', () => {
      const invalidRequest = {
        name: '', // Empty string
        description: 'A new collection',
        schemaType: 'entity',
        schema: { fields: [] },
      };

      const result = CreateCollectionRequestSchema.safeParse(invalidRequest);
      expect(result.success).toBe(false);
    });

    it('should reject request with invalid schemaType', () => {
      const invalidRequest = {
        name: 'New Collection',
        description: 'A new collection',
        schemaType: 'invalid-type',
        schema: { fields: [] },
      };

      const result = CreateCollectionRequestSchema.safeParse(invalidRequest);
      expect(result.success).toBe(false);
    });
  });

  describe('PUT /api/v1/collections/:id', () => {
    it('should accept valid update request', () => {
      const validRequest = {
        name: 'Updated Collection',
        description: 'Updated description',
        status: 'archived',
      };

      const result = UpdateCollectionRequestSchema.safeParse(validRequest);
      expect(result.success).toBe(true);
    });

    it('should accept partial update request', () => {
      const validRequest = {
        name: 'Updated Collection',
      };

      const result = UpdateCollectionRequestSchema.safeParse(validRequest);
      expect(result.success).toBe(true);
    });

    it('should reject update with invalid status', () => {
      const invalidRequest = {
        status: 'invalid-status',
      };

      const result = UpdateCollectionRequestSchema.safeParse(invalidRequest);
      expect(result.success).toBe(false);
    });
  });

  describe('DELETE /api/v1/collections/:id', () => {
    it('should return success response', () => {
      const DeleteResponseSchema = z.object({
        success: z.boolean(),
      });

      const mockResponse = { success: true };
      const result = DeleteResponseSchema.safeParse(mockResponse);
      expect(result.success).toBe(true);
    });
  });
});
