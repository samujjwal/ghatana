/**
 * Unified Registry System
 * Consolidates ComponentRegistry and SchemaRegistry into a single, type-safe system
 *
 * <p><b>Architecture Role</b><br>
 * Provides centralized, namespace-aware registry for all canvas components, schemas,
 * and templates. Uses canonical types from @ghatana/yappc-types to ensure consistency.
 *
 * @doc.type class
 * @doc.purpose Unified registry implementation
 * @doc.layer product
 * @doc.pattern Registry
 */

import type {
  ComponentDefinition,
  RegistryEntry,
  RegistrySearchQuery,
} from '@ghatana/yappc-types';

/**
 * Unified Registry Implementation
 * Type-safe, namespace-aware registry for all canvas components
 *
 * <p><b>Thread Safety</b><br>
 * Not thread-safe - should be accessed from single thread or externally synchronized.
 *
 * @typeParam T - The type of items to be registered
 */
export class UnifiedRegistry<T extends { id: string; category?: string; type?: string; tags?: string[] }> {
  private entries: Map<string, RegistryEntry<T>> = new Map();
  private namespaceIndex: Map<string, Set<string>> = new Map();
  private categoryIndex: Map<string, Set<string>> = new Map();
  private typeIndex: Map<string, Set<string>> = new Map();
  private tagIndex: Map<string, Set<string>> = new Map();

  /**
   * Register an item in the specified namespace
   */
  register(namespace: string, item: T): void {
    const key = this.makeKey(namespace, item.id);

    if (this.entries.has(key)) {
      throw new Error(`Item already registered: ${key}`);
    }

    const entry: RegistryEntry<T> = {
      key,
      value: item,
      namespace,
      registeredAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };

    this.entries.set(key, entry);
    this.updateIndexes(entry);
  }

  /**
   * Update an existing item
   */
  update(namespace: string, id: string, updates: Partial<T>): void {
    const key = this.makeKey(namespace, id);
    const entry = this.entries.get(key);

    if (!entry) {
      throw new Error(`Item not found: ${key}`);
    }

    entry.value = { ...entry.value, ...updates };
    entry.updatedAt = new Date().toISOString();

    // Re-index with updated data
    this.clearIndexes(entry);
    this.updateIndexes(entry);
  }

  /**
   * Get an item by namespace and id
   */
  get(namespace: string, id: string): T | undefined {
    const key = this.makeKey(namespace, id);
    return this.entries.get(key)?.value;
  }

  /**
   * Get entry with metadata
   */
  getEntry(namespace: string, id: string): RegistryEntry<T> | undefined {
    const key = this.makeKey(namespace, id);
    return this.entries.get(key);
  }

  /**
   * List all items in a namespace
   */
  list(namespace: string): T[] {
    return Array.from(this.entries.values())
      .filter((entry) => entry.namespace === namespace)
      .map((entry) => entry.value);
  }

  /**
   * List all entries with metadata
   */
  listEntries(namespace?: string): RegistryEntry<T>[] {
    const entries = Array.from(this.entries.values());
    return namespace
      ? entries.filter((entry) => entry.namespace === namespace)
      : entries;
  }

  /**
   * Search items across namespaces
   */
  search(query: RegistrySearchQuery): RegistryEntry<T>[] {
    let results = Array.from(this.entries.values());

    // Filter by namespace
    if (query.namespace) {
      results = results.filter((entry) => entry.namespace === query.namespace);
    }

    // Filter by text search
    if (query.text) {
      const searchText = query.text.toLowerCase();
      results = results.filter((entry) => {
        const item = entry.value;
        return (
          item.id?.toLowerCase().includes(searchText) ||
          (('label' in item) && typeof item.label === 'string' && item.label.toLowerCase().includes(searchText)) ||
          (('description' in item) && typeof item.description === 'string' && item.description.toLowerCase().includes(searchText)) ||
          item.tags?.some((tag: string) =>
            tag.toLowerCase().includes(searchText)
          )
        );
      });
    }

    // Filter by category
    if (query.category) {
      results = results.filter(
        (entry) => entry.value.category === query.category
      );
    }

    // Filter by type
    if (query.type) {
      results = results.filter(
        (entry) => entry.value.type === query.type
      );
    }

    // Filter by tags
    if (query.tags && query.tags.length > 0) {
      results = results.filter((entry) => {
        const itemTags = entry.value.tags || [];
        return query.tags!.some((tag: string) => itemTags.includes(tag));
      });
    }

    // Apply limit
    if (query.limit && query.limit > 0) {
      results = results.slice(0, query.limit);
    }

    return results;
  }

  /**
   * List all items by category across namespaces
   */
  listByCategory(category: string): RegistryEntry<T>[] {
    const keys = this.categoryIndex.get(category) || new Set();
    return Array.from(keys)
      .map((key) => this.entries.get(key))
      .filter((entry): entry is RegistryEntry<T> => entry !== undefined);
  }

  /**
   * List all items by type across namespaces
   */
  listByType(type: string): RegistryEntry<T>[] {
    const keys = this.typeIndex.get(type) || new Set();
    return Array.from(keys)
      .map((key) => this.entries.get(key))
      .filter((entry): entry is RegistryEntry<T> => entry !== undefined);
  }

  /**
   * List all items with a specific tag
   */
  listByTag(tag: string): RegistryEntry<T>[] {
    const keys = this.tagIndex.get(tag) || new Set();
    return Array.from(keys)
      .map((key) => this.entries.get(key))
      .filter((entry): entry is RegistryEntry<T> => entry !== undefined);
  }

  /**
   * Get all unique categories
   */
  getCategories(): string[] {
    return Array.from(this.categoryIndex.keys()).sort();
  }

  /**
   * Get all unique types
   */
  getTypes(): string[] {
    return Array.from(this.typeIndex.keys()).sort();
  }

  /**
   * Get all unique tags
   */
  getTags(): string[] {
    return Array.from(this.tagIndex.keys()).sort();
  }

  /**
   * Get all namespaces
   */
  getNamespaces(): string[] {
    return Array.from(this.namespaceIndex.keys()).sort();
  }

  /**
   * Remove an item
   */
  remove(namespace: string, id: string): void {
    const key = this.makeKey(namespace, id);
    const entry = this.entries.get(key);

    if (entry) {
      this.clearIndexes(entry);
      this.entries.delete(key);
    }
  }

  /**
   * Clear all items in a namespace
   */
  clearNamespace(namespace: string): void {
    const entries = this.listEntries(namespace);
    entries.forEach((entry) => {
      this.clearIndexes(entry);
      this.entries.delete(entry.key);
    });
    this.namespaceIndex.delete(namespace);
  }

  /**
   * Clear all items
   */
  clear(): void {
    this.entries.clear();
    this.namespaceIndex.clear();
    this.categoryIndex.clear();
    this.typeIndex.clear();
    this.tagIndex.clear();
  }

  /**
   * Export all data
   */
  export(): RegistryEntry<T>[] {
    return Array.from(this.entries.values());
  }

  /**
   * Import data
   */
  import(entries: RegistryEntry<T>[]): void {
    this.clear();
    entries.forEach((entry) => {
      this.entries.set(entry.key, entry);
      this.updateIndexes(entry);
    });
  }

  /**
   * Get registry statistics
   */
  getStats() {
    return {
      totalEntries: this.entries.size,
      namespaces: this.namespaceIndex.size,
      categories: this.categoryIndex.size,
      types: this.typeIndex.size,
      tags: this.tagIndex.size,
    };
  }

  // Private helper methods
  /**
   * Make a unique key from namespace and id
   */
  private makeKey(namespace: string, id: string): string {
    return `${namespace}:${id}`;
  }

  /**
   * Update all indexes for an entry
   */
  private updateIndexes(entry: RegistryEntry<T>): void {
    const item = entry.value;
    
    // Update namespace index
    if (!this.namespaceIndex.has(entry.namespace)) {
      this.namespaceIndex.set(entry.namespace, new Set());
    }
    this.namespaceIndex.get(entry.namespace)!.add(entry.key);

    // Update category index
    if (item.category) {
      if (!this.categoryIndex.has(item.category)) {
        this.categoryIndex.set(item.category, new Set());
      }
      this.categoryIndex.get(item.category)!.add(entry.key);
    }

    // Update type index
    if (item.type) {
      if (!this.typeIndex.has(item.type)) {
        this.typeIndex.set(item.type, new Set());
      }
      this.typeIndex.get(item.type)!.add(entry.key);
    }

    // Update tag index
    if (item.tags && Array.isArray(item.tags)) {
      item.tags.forEach((tag: string) => {
        if (!this.tagIndex.has(tag)) {
          this.tagIndex.set(tag, new Set());
        }
        this.tagIndex.get(tag)!.add(entry.key);
      });
    }
  }

  /**
   *
   */
  private clearIndexes(entry: RegistryEntry<T>): void {
    // Remove from namespace index
    const namespaceKeys = this.namespaceIndex.get(entry.namespace);
    if (namespaceKeys) {
      namespaceKeys.delete(entry.key);
      if (namespaceKeys.size === 0) {
        this.namespaceIndex.delete(entry.namespace);
      }
    }

    // Clear from other indexes
    this.categoryIndex.forEach((keys) => keys.delete(entry.key));
    this.typeIndex.forEach((keys) => keys.delete(entry.key));
    this.tagIndex.forEach((keys) => keys.delete(entry.key));

    // Clean up empty index entries
    this.categoryIndex.forEach((keys, category) => {
      if (keys.size === 0) this.categoryIndex.delete(category);
    });
    this.typeIndex.forEach((keys, type) => {
      if (keys.size === 0) this.typeIndex.delete(type);
    });
    this.tagIndex.forEach((keys, tag) => {
      if (keys.size === 0) this.tagIndex.delete(tag);
    });
  }
}

// Create singleton instances for different data types
export const componentRegistry = new UnifiedRegistry<ComponentDefinition>();

// Schema registry - for Zod schemas with flexible structure
export const schemaRegistry = new UnifiedRegistry<{
  id: string;
  category?: string;
  type?: string;
  tags?: string[];
  [key: string]: unknown;
}>();

// Template registry - for page/component templates
export const templateRegistry = new UnifiedRegistry<{
  id: string;
  category?: string;
  type?: string;
  tags?: string[];
  [key: string]: unknown;
}>();
