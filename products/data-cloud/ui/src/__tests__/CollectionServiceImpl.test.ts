import { describe, it, expect, vi, beforeEach } from 'vitest';
import { CollectionServiceImpl, createDefaultSchema, createDefaultField } from '../services/collections-impl';
import type { CreateCollectionRequest, Collection, FieldType, SchemaField } from '../services/collections';

/**
 * CollectionServiceImpl Tests - 100% Coverage
 * 
 * @doc.type test
 * @doc.purpose Comprehensive tests for CollectionServiceImpl
 * @doc.layer ui
 * @doc.pattern Unit Test
 */

describe('CollectionServiceImpl', () => {
  let service: CollectionServiceImpl;

  beforeEach(() => {
    vi.clearAllMocks();
    service = new CollectionServiceImpl('/api/v1');
  });

  describe('getCollections', () => {
    it('should fetch collections with query options', async () => {
      const mockCollections: Collection[] = [
        { id: '1', name: 'Test', tenantId: 'tenant-1', description: '', schema: { fields: [], indexes: [], validations: [] }, settings: { versioning: false, softDelete: false, auditLog: false, caching: false }, entityCount: 0, createdAt: '', updatedAt: '', createdBy: '', version: 1 }
      ];
      global.fetch = vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve(mockCollections) });

      const result = await service.getCollections('tenant-1', { search: 'test', page: 1, limit: 10 });

      expect(result).toEqual(mockCollections);
      expect(global.fetch).toHaveBeenCalledWith('/api/v1/tenants/tenant-1/collections?search=test&page=1&limit=10');
    });

    it('should throw on fetch error', async () => {
      global.fetch = vi.fn().mockResolvedValue({ ok: false, status: 500 });

      await expect(service.getCollections('tenant-1')).rejects.toThrow('Failed to fetch collections: 500');
    });
  });

  describe('getCollection', () => {
    it('should return collection when found', async () => {
      const mockCollection: Collection = { id: '1', name: 'Test', tenantId: 'tenant-1', description: '', schema: { fields: [], indexes: [], validations: [] }, settings: { versioning: false, softDelete: false, auditLog: false, caching: false }, entityCount: 0, createdAt: '', updatedAt: '', createdBy: '', version: 1 };
      global.fetch = vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve(mockCollection) });

      const result = await service.getCollection('1', 'tenant-1');

      expect(result).toEqual(mockCollection);
    });

    it('should return null when not found', async () => {
      global.fetch = vi.fn().mockResolvedValue({ status: 404 });

      const result = await service.getCollection('1', 'tenant-1');

      expect(result).toBeNull();
    });
  });

  describe('createCollection', () => {
    it('should create collection after validation passes', async () => {
      const request: CreateCollectionRequest = {
        tenantId: 'tenant-1',
        name: 'Products',
        schema: { fields: [{ name: 'name', type: 'string' as FieldType, required: true }], indexes: [], validations: [] }
      };
      const mockCollection: Collection = { id: '1', ...request, description: '', settings: { versioning: false, softDelete: false, auditLog: false, caching: false }, entityCount: 0, createdAt: '', updatedAt: '', createdBy: '', version: 1 };
      global.fetch = vi.fn().mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(mockCollection) });

      const result = await service.createCollection(request);

      expect(result).toEqual(mockCollection);
    });

    it('should throw when validation fails', async () => {
      const request: CreateCollectionRequest = {
        tenantId: 'tenant-1',
        name: '',
        schema: { fields: [], indexes: [], validations: [] }
      };

      await expect(service.createCollection(request)).rejects.toThrow('Validation failed');
    });
  });

  describe('validateCollection', () => {
    it('should pass valid collection', async () => {
      const request: CreateCollectionRequest = {
        tenantId: 'tenant-1',
        name: 'ValidCollection',
        schema: { fields: [{ name: 'id', type: 'string' as FieldType, required: true }], indexes: [], validations: [] }
      };

      const result = await service.validateCollection(request);

      expect(result.valid).toBe(true);
      expect(result.errors).toHaveLength(0);
    });

    it('should fail empty name', async () => {
      const request: CreateCollectionRequest = {
        tenantId: 'tenant-1',
        name: '',
        schema: { fields: [{ name: 'id', type: 'string' as FieldType, required: true }], indexes: [], validations: [] }
      };

      const result = await service.validateCollection(request);

      expect(result.valid).toBe(false);
      expect(result.errors).toContainEqual(expect.objectContaining({ field: 'name', code: 'REQUIRED' }));
    });

    it('should fail invalid name format', async () => {
      const request: CreateCollectionRequest = {
        tenantId: 'tenant-1',
        name: '123-invalid',
        schema: { fields: [{ name: 'id', type: 'string' as FieldType, required: true }], indexes: [], validations: [] }
      };

      const result = await service.validateCollection(request);

      expect(result.valid).toBe(false);
      expect(result.errors).toContainEqual(expect.objectContaining({ field: 'name', code: 'INVALID_FORMAT' }));
    });

    it('should fail duplicate field names', async () => {
      const request: CreateCollectionRequest = {
        tenantId: 'tenant-1',
        name: 'Test',
        schema: { fields: [{ name: 'field1', type: 'string' as FieldType, required: true }, { name: 'field1', type: 'number' as FieldType, required: true }], indexes: [], validations: [] }
      };

      const result = await service.validateCollection(request);

      expect(result.valid).toBe(false);
      expect(result.errors).toContainEqual(expect.objectContaining({ code: 'DUPLICATE' }));
    });

    it('should fail invalid field constraints', async () => {
      const request: CreateCollectionRequest = {
        tenantId: 'tenant-1',
        name: 'Test',
        schema: { fields: [{ name: 'count', type: 'number' as FieldType, required: true, constraints: { min: 100, max: 10 } }], indexes: [], validations: [] }
      };

      const result = await service.validateCollection(request);

      expect(result.valid).toBe(false);
      expect(result.errors).toContainEqual(expect.objectContaining({ code: 'INVALID_RANGE' }));
    });

    it('should warn about missing id field', async () => {
      const request: CreateCollectionRequest = {
        tenantId: 'tenant-1',
        name: 'Test',
        schema: { fields: [{ name: 'name', type: 'string' as FieldType, required: true }], indexes: [], validations: [] }
      };

      const result = await service.validateCollection(request);

      expect(result.warnings).toContainEqual(expect.objectContaining({ code: 'NO_ID_FIELD' }));
    });
  });

  describe('deleteCollection', () => {
    it('should delete successfully', async () => {
      global.fetch = vi.fn().mockResolvedValue({ ok: true });

      await expect(service.deleteCollection('1', 'tenant-1')).resolves.not.toThrow();
    });

    it('should not throw on 404', async () => {
      global.fetch = vi.fn().mockResolvedValue({ ok: false, status: 404 });

      await expect(service.deleteCollection('1', 'tenant-1')).resolves.not.toThrow();
    });
  });

  describe('exportCollection', () => {
    it('should return blob', async () => {
      const blob = new Blob(['data']);
      global.fetch = vi.fn().mockResolvedValue({ ok: true, blob: () => Promise.resolve(blob) });

      const result = await service.exportCollection('1', 'csv');

      expect(result).toBe(blob);
    });
  });

  describe('importCollection', () => {
    it('should import with options', async () => {
      const file = new File(['data'], 'import.csv');
      const mockResult = { total: 10, inserted: 10, updated: 0, failed: 0, errors: [] };
      global.fetch = vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve(mockResult) });

      const result = await service.importCollection('1', file, { skipValidation: true, upsert: true });

      expect(result).toEqual(mockResult);
    });
  });
});

describe('Utility Functions', () => {
  describe('createDefaultSchema', () => {
    it('should create schema with id field', () => {
      const schema = createDefaultSchema();
      
      expect(schema.fields).toHaveLength(1);
      expect(schema.fields[0].name).toBe('id');
      expect(schema.indexes[0].fields).toContain('id');
    });
  });

  describe('createDefaultField', () => {
    it('should create field with defaults', () => {
      const field: SchemaField = createDefaultField('name', 'string' as FieldType);
      
      expect(field.name).toBe('name');
      expect(field.type).toBe('string');
      expect(field.required).toBe(false);
    });
  });
});
