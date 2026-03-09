import type { MetaField, MetaCollection } from '@/types/schema.types';

/**
 * Schema service for managing collection schemas.
 *
 * <p><b>Purpose</b><br>
 * Provides API integration for fetching and managing collection schemas.
 * Includes caching, validation, and field suggestion capabilities.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { schemaService } from '@/api/schema.service';
 *
 * // Fetch collection schema
 * const schema = await schemaService.getCollectionSchema('tenant-123', 'products');
 *
 * // Get field suggestions
 * const suggestions = schemaService.suggestFields('price', 'number');
 *
 * // Validate field mapping
 * const isValid = schemaService.validateFieldMapping(sourceField, targetField);
 * }</pre>
 *
 * <p><b>Features</b><br>
 * - Fetch collection schemas from backend
 * - Cache schema data
 * - Validate field mappings
 * - Generate field suggestions
 * - Type compatibility checking
 *
 * @doc.type service
 * @doc.purpose Schema management and field validation
 * @doc.layer frontend
 */

const API_BASE = '/api/v1';
const CACHE_TTL = 5 * 60 * 1000; // 5 minutes

interface CacheEntry<T> {
  data: T;
  timestamp: number;
}

class SchemaService {
  private schemaCache: Map<string, CacheEntry<MetaCollection>> = new Map();
  private fieldsCache: Map<string, CacheEntry<MetaField[]>> = new Map();

  /**
   * Gets collection schema from API or cache.
   *
   * <p>GIVEN: A tenant ID and collection name
   * WHEN: getCollectionSchema() is called
   * THEN: Returns schema from cache or fetches from API
   *
   * @param tenantId the tenant identifier
   * @param collectionName the collection name
   * @returns the collection schema
   * @throws Error if fetch fails
   */
  async getCollectionSchema(tenantId: string, collectionName: string): Promise<MetaCollection> {
    const cacheKey = `${tenantId}:${collectionName}`;

    // Check cache
    const cached = this.schemaCache.get(cacheKey);
    if (cached && Date.now() - cached.timestamp < CACHE_TTL) {
      console.debug('Schema cache hit:', cacheKey);
      return cached.data;
    }

    // Fetch from API
    try {
      const response = await fetch(
        `${API_BASE}/collections/${collectionName}?tenantId=${tenantId}`,
        {
          headers: {
            'X-Tenant-ID': tenantId,
            'Content-Type': 'application/json',
          },
        }
      );

      if (!response.ok) {
        throw new Error(`Failed to fetch schema: ${response.statusText}`);
      }

      const schema = (await response.json()) as MetaCollection;

      // Cache result
      this.schemaCache.set(cacheKey, {
        data: schema,
        timestamp: Date.now(),
      });

      // Also populate fields cache for quick access and accurate cache stats
      this.fieldsCache.set(`${cacheKey}:fields`, {
        data: schema.fields || [],
        timestamp: Date.now(),
      });

      console.debug('Schema fetched and cached:', cacheKey);
      return schema;
    } catch (error) {
      console.error('Error fetching schema:', error);
      throw new Error(`Failed to fetch collection schema: ${error instanceof Error ? error.message : 'Unknown error'}`);
    }
  }

  /**
   * Gets all fields for a collection.
   *
   * @param tenantId the tenant identifier
   * @param collectionName the collection name
   * @returns list of fields
   */
  async getCollectionFields(tenantId: string, collectionName: string): Promise<MetaField[]> {
    const cacheKey = `${tenantId}:${collectionName}:fields`;

    // Check cache
    const cached = this.fieldsCache.get(cacheKey);
    if (cached && Date.now() - cached.timestamp < CACHE_TTL) {
      return cached.data;
    }

    // Fetch schema and extract fields
    const schema = await this.getCollectionSchema(tenantId, collectionName);

    // Cache fields
    this.fieldsCache.set(cacheKey, {
      data: schema.fields || [],
      timestamp: Date.now(),
    });

    return schema.fields || [];
  }

  /**
   * Validates if two fields are compatible for mapping.
   *
   * <p>GIVEN: A source field and target field
   * WHEN: validateFieldMapping() is called
   * THEN: Returns true if types are compatible
   *
   * @param sourceField the source field
   * @param targetField the target field
   * @returns true if fields are compatible
   */
  validateFieldMapping(sourceField: MetaField, targetField: MetaField): boolean {
    // Exact type match
    if (sourceField.type === targetField.type) {
      return true;
    }

    // Numeric types are compatible
    if (
      ['number', 'integer'].includes(sourceField.type) &&
      ['number', 'integer'].includes(targetField.type)
    ) {
      return true;
    }

    // String types are compatible
    if (
      ['string', 'text'].includes(sourceField.type) &&
      ['string', 'text'].includes(targetField.type)
    ) {
      return true;
    }

    // Date types are compatible
    if (
      ['date', 'datetime', 'timestamp'].includes(sourceField.type) &&
      ['date', 'datetime', 'timestamp'].includes(targetField.type)
    ) {
      return true;
    }

    return false;
  }

  /**
   * Gets field suggestions based on name and type.
   *
   * <p>GIVEN: A field name pattern and expected type
   * WHEN: suggestFields() is called
   * THEN: Returns matching fields sorted by relevance
   *
   * @param fields available fields to search
   * @param namePattern the field name pattern
   * @param expectedType the expected field type (optional)
   * @returns matching fields sorted by relevance
   */
  suggestFields(
    fields: MetaField[],
    namePattern: string,
    expectedType?: string
  ): MetaField[] {
    const pattern = namePattern.toLowerCase();

    // Filter by name pattern
    let suggestions = fields.filter((field) =>
      field.name.toLowerCase().includes(pattern)
    );

    // Sort by relevance
    suggestions.sort((a, b) => {
      // Exact match first
      if (a.name.toLowerCase() === pattern) return -1;
      if (b.name.toLowerCase() === pattern) return 1;

      // Starts with pattern
      const aStarts = a.name.toLowerCase().startsWith(pattern);
      const bStarts = b.name.toLowerCase().startsWith(pattern);
      if (aStarts && !bStarts) return -1;
      if (!aStarts && bStarts) return 1;

      // Type compatibility
      if (expectedType) {
        const aCompatible = this.areTypesCompatible(expectedType, a.type);
        const bCompatible = this.areTypesCompatible(expectedType, b.type);
        if (aCompatible && !bCompatible) return -1;
        if (!aCompatible && bCompatible) return 1;
      }

      // Alphabetical
      return a.name.localeCompare(b.name);
    });

    return suggestions.slice(0, 10); // Limit to 10 suggestions
  }

  /**
   * Checks if two types are compatible.
   *
   * @param sourceType the source type
   * @param targetType the target type
   * @returns true if types are compatible
   */
  private areTypesCompatible(sourceType: string, targetType: string): boolean {
    if (sourceType === targetType) return true;

    if (
      ['number', 'integer'].includes(sourceType) &&
      ['number', 'integer'].includes(targetType)
    ) {
      return true;
    }

    if (
      ['string', 'text'].includes(sourceType) &&
      ['string', 'text'].includes(targetType)
    ) {
      return true;
    }

    if (
      ['date', 'datetime', 'timestamp'].includes(sourceType) &&
      ['date', 'datetime', 'timestamp'].includes(targetType)
    ) {
      return true;
    }

    return false;
  }

  /**
   * Clears all caches.
   */
  clearCache(): void {
    this.schemaCache.clear();
    this.fieldsCache.clear();
    console.debug('Schema cache cleared');
  }

  /**
   * Gets cache statistics.
   *
   * @returns cache statistics
   */
  getCacheStats(): {
    schemaCount: number;
    fieldsCount: number;
    totalSize: number;
  } {
    return {
      schemaCount: this.schemaCache.size,
      fieldsCount: this.fieldsCache.size,
      totalSize: this.schemaCache.size + this.fieldsCache.size,
    };
  }
}

// Export singleton instance
export const schemaService = new SchemaService();

export default schemaService;
