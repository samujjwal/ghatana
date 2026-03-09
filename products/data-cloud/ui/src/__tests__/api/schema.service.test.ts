import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import type { MetaField, MetaCollection } from '@/types/schema.types';
import { schemaService } from '@/api/schema.service';

/**
 * Tests for schema service.
 *
 * Tests validate:
 * - Schema fetching and caching
 * - Field retrieval
 * - Field mapping validation
 * - Field suggestions
 * - Type compatibility
 * - Error handling
 *
 * @see schema.service.ts
 */

describe('Schema Service', () => {
  const mockFields: MetaField[] = [
    {
      id: 'field-1',
      collectionId: 'collection-1',
      name: 'userId',
      type: 'string',
      description: 'User identifier',
      required: true,
    },
    {
      id: 'field-2',
      collectionId: 'collection-1',
      name: 'email',
      type: 'string',
      description: 'User email',
      required: true,
    },
    {
      id: 'field-3',
      collectionId: 'collection-1',
      name: 'age',
      type: 'integer',
      description: 'User age',
      required: false,
    },
    {
      id: 'field-4',
      collectionId: 'collection-1',
      name: 'price',
      type: 'number',
      description: 'Product price',
      required: false,
    },
    {
      id: 'field-5',
      collectionId: 'collection-1',
      name: 'createdAt',
      type: 'datetime',
      description: 'Creation timestamp',
      required: true,
    },
  ];

  const mockSchema: MetaCollection = {
    id: 'collection-1',
    tenantId: 'tenant-123',
    name: 'users',
    description: 'User collection',
    fields: mockFields,
    permission: {},
    applications: [],
  };

  beforeEach(() => {
    schemaService.clearCache();
    global.fetch = vi.fn();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  // ============ Schema Fetching Tests ============

  describe('Schema Fetching', () => {
    it('should fetch collection schema from API', async () => {
      // Given
      (global.fetch as any).mockResolvedValueOnce({
        ok: true,
        json: async () => mockSchema,
      });

      // When
      const schema = await schemaService.getCollectionSchema('tenant-123', 'users');

      // Then
      expect(schema).toEqual(mockSchema);
      expect(global.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/v1/collections/users'),
        expect.any(Object)
      );
    });

    it('should cache schema after fetching', async () => {
      // Given
      (global.fetch as any).mockResolvedValueOnce({
        ok: true,
        json: async () => mockSchema,
      });

      // When
      await schemaService.getCollectionSchema('tenant-123', 'users');
      await schemaService.getCollectionSchema('tenant-123', 'users');

      // Then
      expect(global.fetch).toHaveBeenCalledTimes(1); // Only called once due to cache
    });

    it('should throw error on fetch failure', async () => {
      // Given
      (global.fetch as any).mockResolvedValueOnce({
        ok: false,
        statusText: 'Not Found',
      });

      // When & Then
      await expect(
        schemaService.getCollectionSchema('tenant-123', 'users')
      ).rejects.toThrow('Failed to fetch schema');
    });

    it('should include tenant ID in request headers', async () => {
      // Given
      (global.fetch as any).mockResolvedValueOnce({
        ok: true,
        json: async () => mockSchema,
      });

      // When
      await schemaService.getCollectionSchema('tenant-123', 'users');

      // Then
      expect(global.fetch).toHaveBeenCalledWith(
        expect.any(String),
        expect.objectContaining({
          headers: expect.objectContaining({
            'X-Tenant-ID': 'tenant-123',
          }),
        })
      );
    });
  });

  // ============ Field Retrieval Tests ============

  describe('Field Retrieval', () => {
    it('should get collection fields', async () => {
      // Given
      (global.fetch as any).mockResolvedValueOnce({
        ok: true,
        json: async () => mockSchema,
      });

      // When
      const fields = await schemaService.getCollectionFields('tenant-123', 'users');

      // Then
      expect(fields).toEqual(mockFields);
      expect(fields).toHaveLength(5);
    });

    it('should cache fields after fetching', async () => {
      // Given
      (global.fetch as any).mockResolvedValueOnce({
        ok: true,
        json: async () => mockSchema,
      });

      // When
      await schemaService.getCollectionFields('tenant-123', 'users');
      await schemaService.getCollectionFields('tenant-123', 'users');

      // Then
      expect(global.fetch).toHaveBeenCalledTimes(1);
    });

    it('should return empty array if schema has no fields', async () => {
      // Given
      const schemaWithoutFields = { ...mockSchema, fields: undefined };
      (global.fetch as any).mockResolvedValueOnce({
        ok: true,
        json: async () => schemaWithoutFields,
      });

      // When
      const fields = await schemaService.getCollectionFields('tenant-123', 'users');

      // Then
      expect(fields).toEqual([]);
    });
  });

  // ============ Field Mapping Validation Tests ============

  describe('Field Mapping Validation', () => {
    it('should validate exact type match', () => {
      // Given
      const field1 = mockFields[0]; // string
      const field2 = mockFields[1]; // string

      // When
      const isValid = schemaService.validateFieldMapping(field1, field2);

      // Then
      expect(isValid).toBe(true);
    });

    it('should validate numeric type compatibility', () => {
      // Given
      const field1 = mockFields[2]; // integer
      const field2 = mockFields[3]; // number

      // When
      const isValid = schemaService.validateFieldMapping(field1, field2);

      // Then
      expect(isValid).toBe(true);
    });

    it('should validate string type compatibility', () => {
      // Given
      const field1 = mockFields[0]; // string
      const field2 = mockFields[1]; // string

      // When
      const isValid = schemaService.validateFieldMapping(field1, field2);

      // Then
      expect(isValid).toBe(true);
    });

    it('should reject incompatible types', () => {
      // Given
      const field1 = mockFields[0]; // string
      const field2 = mockFields[2]; // integer

      // When
      const isValid = schemaService.validateFieldMapping(field1, field2);

      // Then
      expect(isValid).toBe(false);
    });

    it('should validate date type compatibility', () => {
      // Given
      const dateField: MetaField = {
        id: 'field-6',
        collectionId: 'collection-1',
        name: 'updatedAt',
        type: 'datetime',
        required: false,
      };

      // When
      const isValid = schemaService.validateFieldMapping(mockFields[4], dateField);

      // Then
      expect(isValid).toBe(true);
    });
  });

  // ============ Field Suggestion Tests ============

  describe('Field Suggestions', () => {
    it('should suggest fields by name pattern', () => {
      // When
      const suggestions = schemaService.suggestFields(mockFields, 'user');

      // Then
      expect(suggestions).toContainEqual(mockFields[0]); // userId
      expect(suggestions.length).toBeGreaterThan(0);
    });

    it('should prioritize exact matches', () => {
      // When
      const suggestions = schemaService.suggestFields(mockFields, 'userId');

      // Then
      expect(suggestions[0]).toEqual(mockFields[0]); // userId should be first
    });

    it('should prioritize fields starting with pattern', () => {
      // When
      const suggestions = schemaService.suggestFields(mockFields, 'user');

      // Then
      expect(suggestions[0].name).toBe('userId');
    });

    it('should suggest fields by type', () => {
      // When
      const suggestions = schemaService.suggestFields(mockFields, 'id', 'string');

      // Then
      expect(suggestions.length).toBeGreaterThan(0);
      expect(suggestions[0].type).toBe('string');
    });

    it('should limit suggestions to 10', () => {
      // Given
      const manyFields = Array.from({ length: 20 }, (_, i) => ({
        id: `field-${i}`,
        collectionId: 'collection-1',
        name: `field${i}`,
        type: 'string',
        required: false,
      }));

      // When
      const suggestions = schemaService.suggestFields(manyFields, 'field');

      // Then
      expect(suggestions.length).toBeLessThanOrEqual(10);
    });

    it('should sort suggestions alphabetically as fallback', () => {
      // When
      const suggestions = schemaService.suggestFields(mockFields, 'e');

      // Then
      // Should include email and createdAt
      expect(suggestions.length).toBeGreaterThan(0);
    });
  });

  // ============ Type Compatibility Tests ============

  describe('Type Compatibility', () => {
    it('should recognize numeric type compatibility', () => {
      // When
      const suggestions = schemaService.suggestFields(mockFields, 'age', 'number');

      // Then
      // age (integer) should be suggested for number type
      expect(suggestions).toContainEqual(mockFields[2]);
    });

    it('should recognize string type compatibility', () => {
      // When
      const suggestions = schemaService.suggestFields(mockFields, 'user', 'string');

      // Then
      // userId should be suggested
      expect(suggestions).toContainEqual(mockFields[0]);
    });

    it('should prioritize compatible types in suggestions', () => {
      // When
      const suggestions = schemaService.suggestFields(mockFields, '', 'integer');

      // Then
      // age (integer) should be first
      expect(suggestions[0]).toEqual(mockFields[2]);
    });
  });

  // ============ Cache Management Tests ============

  describe('Cache Management', () => {
    it('should clear cache', async () => {
      // Given
      (global.fetch as any).mockResolvedValueOnce({
        ok: true,
        json: async () => mockSchema,
      });
      await schemaService.getCollectionSchema('tenant-123', 'users');

      // When
      schemaService.clearCache();
      (global.fetch as any).mockResolvedValueOnce({
        ok: true,
        json: async () => mockSchema,
      });
      await schemaService.getCollectionSchema('tenant-123', 'users');

      // Then
      expect(global.fetch).toHaveBeenCalledTimes(2); // Called again after cache clear
    });

    it('should provide cache statistics', async () => {
      // Given
      (global.fetch as any).mockResolvedValueOnce({
        ok: true,
        json: async () => mockSchema,
      });
      await schemaService.getCollectionSchema('tenant-123', 'users');

      // When
      const stats = schemaService.getCacheStats();

      // Then
      expect(stats.schemaCount).toBeGreaterThan(0);
      expect(stats.fieldsCount).toBeGreaterThan(0);
      expect(stats.totalSize).toBeGreaterThan(0);
    });

    it('should return zero stats for empty cache', () => {
      // When
      const stats = schemaService.getCacheStats();

      // Then
      expect(stats.schemaCount).toBe(0);
      expect(stats.fieldsCount).toBe(0);
      expect(stats.totalSize).toBe(0);
    });
  });

  // ============ Error Handling Tests ============

  describe('Error Handling', () => {
    it('should handle network errors', async () => {
      // Given
      (global.fetch as any).mockRejectedValueOnce(new Error('Network error'));

      // When & Then
      await expect(
        schemaService.getCollectionSchema('tenant-123', 'users')
      ).rejects.toThrow('Failed to fetch collection schema');
    });

    it('should handle invalid JSON response', async () => {
      // Given
      (global.fetch as any).mockResolvedValueOnce({
        ok: true,
        json: async () => {
          throw new Error('Invalid JSON');
        },
      });

      // When & Then
      await expect(
        schemaService.getCollectionSchema('tenant-123', 'users')
      ).rejects.toThrow();
    });
  });
});
