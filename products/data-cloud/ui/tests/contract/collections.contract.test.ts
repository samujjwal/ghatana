import { describe, it, expect } from 'vitest';
import { z } from 'zod';
import {
  CollectionSchema,
  PaginatedCollectionResponseSchema,
  CreateCollectionRequestSchema,
  UpdateCollectionRequestSchema,
} from '../../src/contracts/schemas';

/**
 * Collections API Contract Tests
 * 
 * Validates that API responses match the expected schema.
 * These tests ensure frontend-backend contract compliance.
 * Schemas are imported from the shared contracts module.
 * 
 * @doc.type test
 * @doc.purpose API contract validation tests
 * @doc.layer testing
 */

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
