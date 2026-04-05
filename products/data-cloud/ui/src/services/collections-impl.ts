/**
 * Collection Service Implementation
 * 
 * @doc.type class
 * @doc.purpose Concrete implementation of collection service with validation
 * @doc.layer ui
 * @doc.pattern Service Implementation
 */

import type { CollectionService, Collection, CreateCollectionRequest, UpdateCollectionRequest, CollectionSchema, ValidationResult, CollectionStats, ExportFormat, ImportOptions, ImportResult, CollectionQueryOptions, SchemaField, FieldType } from './collections';

export class CollectionServiceImpl implements CollectionService {
  private baseUrl: string;

  constructor(baseUrl = '/api/v1') {
    this.baseUrl = baseUrl;
  }

  async getCollections(tenantId: string, options?: CollectionQueryOptions): Promise<Collection[]> {
    const params = new URLSearchParams();
    if (options?.search) params.set('search', options.search);
    if (options?.sortBy) params.set('sortBy', options.sortBy);
    if (options?.sortOrder) params.set('sortOrder', options.sortOrder);
    if (options?.page) params.set('page', String(options.page));
    if (options?.limit) params.set('limit', String(options.limit));

    const response = await fetch(`${this.baseUrl}/tenants/${tenantId}/collections?${params}`);
    if (!response.ok) throw new Error(`Failed to fetch collections: ${response.status}`);
    return response.json();
  }

  async getCollection(collectionId: string, tenantId: string): Promise<Collection | null> {
    const response = await fetch(`${this.baseUrl}/tenants/${tenantId}/collections/${collectionId}`);
    if (response.status === 404) return null;
    if (!response.ok) throw new Error(`Failed to fetch collection: ${response.status}`);
    return response.json();
  }

  async createCollection(data: CreateCollectionRequest): Promise<Collection> {
    // Validate before sending
    const validation = await this.validateCollection(data);
    if (!validation.valid) {
      throw new Error(`Validation failed: ${validation.errors.map(e => e.message).join(', ')}`);
    }

    const response = await fetch(`${this.baseUrl}/tenants/${data.tenantId}/collections`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    });
    
    if (!response.ok) throw new Error(`Failed to create collection: ${response.status}`);
    return response.json();
  }

  async updateCollection(collectionId: string, data: UpdateCollectionRequest): Promise<Collection> {
    const response = await fetch(`${this.baseUrl}/collections/${collectionId}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    });
    
    if (!response.ok) throw new Error(`Failed to update collection: ${response.status}`);
    return response.json();
  }

  async deleteCollection(collectionId: string, tenantId: string): Promise<void> {
    const response = await fetch(`${this.baseUrl}/tenants/${tenantId}/collections/${collectionId}`, {
      method: 'DELETE'
    });
    
    if (!response.ok && response.status !== 404) {
      throw new Error(`Failed to delete collection: ${response.status}`);
    }
  }

  async getCollectionSchema(collectionId: string): Promise<CollectionSchema> {
    const response = await fetch(`${this.baseUrl}/collections/${collectionId}/schema`);
    if (!response.ok) throw new Error(`Failed to fetch schema: ${response.status}`);
    return response.json();
  }

  async validateCollection(data: CreateCollectionRequest): Promise<ValidationResult> {
    const errors: ValidationResult['errors'] = [];
    const warnings: ValidationResult['warnings'] = [];

    // Validate name
    if (!data.name || data.name.trim().length === 0) {
      errors.push({ field: 'name', code: 'REQUIRED', message: 'Collection name is required' });
    } else if (!/^[a-zA-Z_][a-zA-Z0-9_]*$/.test(data.name)) {
      errors.push({ field: 'name', code: 'INVALID_FORMAT', message: 'Name must start with letter/underscore and contain only alphanumeric characters' });
    }

    if (data.name && data.name.length > 64) {
      errors.push({ field: 'name', code: 'TOO_LONG', message: 'Name must be 64 characters or less' });
    }

    // Validate schema
    if (!data.schema || !data.schema.fields || data.schema.fields.length === 0) {
      errors.push({ field: 'schema', code: 'NO_FIELDS', message: 'Collection must have at least one field' });
    } else {
      // Check for duplicate field names
      const fieldNames = new Set<string>();
      for (const field of data.schema.fields) {
        if (fieldNames.has(field.name)) {
          errors.push({ field: `schema.fields.${field.name}`, code: 'DUPLICATE', message: `Duplicate field name: ${field.name}` });
        }
        fieldNames.add(field.name);

        // Validate field name
        if (!/^[a-zA-Z_][a-zA-Z0-9_]*$/.test(field.name)) {
          errors.push({ field: `schema.fields.${field.name}`, code: 'INVALID_NAME', message: `Invalid field name: ${field.name}` });
        }

        // Validate field constraints
        if (field.constraints) {
          if (field.constraints.min !== undefined && field.constraints.max !== undefined) {
            if (field.constraints.min > field.constraints.max) {
              errors.push({ field: `schema.fields.${field.name}.constraints`, code: 'INVALID_RANGE', message: 'Min cannot be greater than max' });
            }
          }
        }
      }

      // Check for ID field
      const hasIdField = data.schema.fields.some(f => f.name === 'id' || f.name === '_id');
      if (!hasIdField) {
        warnings.push({ field: 'schema', code: 'NO_ID_FIELD', message: 'Consider adding an ID field for better entity management' });
      }
    }

    return { valid: errors.length === 0, errors, warnings };
  }

  async getCollectionStats(collectionId: string): Promise<CollectionStats> {
    const response = await fetch(`${this.baseUrl}/collections/${collectionId}/stats`);
    if (!response.ok) throw new Error(`Failed to fetch stats: ${response.status}`);
    return response.json();
  }

  async exportCollection(collectionId: string, format: ExportFormat): Promise<Blob> {
    const response = await fetch(`${this.baseUrl}/collections/${collectionId}/export?format=${format}`);
    if (!response.ok) throw new Error(`Failed to export collection: ${response.status}`);
    return response.blob();
  }

  async importCollection(collectionId: string, file: File, options?: ImportOptions): Promise<ImportResult> {
    const formData = new FormData();
    formData.append('file', file);
    if (options?.skipValidation) formData.append('skipValidation', 'true');
    if (options?.upsert) formData.append('upsert', 'true');
    if (options?.batchSize) formData.append('batchSize', String(options.batchSize));
    if (options?.onError) formData.append('onError', options.onError);

    const response = await fetch(`${this.baseUrl}/collections/${collectionId}/import`, {
      method: 'POST',
      body: formData
    });
    
    if (!response.ok) throw new Error(`Failed to import collection: ${response.status}`);
    return response.json();
  }
}

// Utility functions for collection operations
export function createDefaultSchema(): CollectionSchema {
  return {
    fields: [
      { name: 'id', type: 'string' as FieldType, required: true, description: 'Unique identifier' }
    ],
    indexes: [{ name: 'idx_id', fields: ['id'], unique: true, type: 'btree' }],
    validations: []
  };
}

export function createDefaultField(name: string, type: FieldType): SchemaField {
  return {
    name,
    type,
    required: false,
    description: ''
  };
}
