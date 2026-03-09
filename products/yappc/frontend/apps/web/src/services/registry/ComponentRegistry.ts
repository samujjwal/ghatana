/**
 * Component Registry - Central registry for all canvas components
 */

import { RegistryComparator } from '@ghatana/yappc-types';

import type { ComponentDefinition, RegistryEntry, RegistryFilter } from '@ghatana/yappc-types';

/**
 *
 */
class ComponentRegistryClass {
  private components: Map<string, RegistryEntry<ComponentDefinition>> = new Map();
  private categoryIndex: Map<string, Set<string>> = new Map();
  private typeIndex: Map<string, string> = new Map();

  /**
   * Register a component definition
   */
  register(component: ComponentDefinition): void {
    const key = this.makeKey(component.type, component.version);

    if (this.components.has(key)) {
      throw new Error(`Component already registered: ${key}`);
    }

    const entry: RegistryEntry<ComponentDefinition> = {
      key,
      value: component,
      registeredAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };

    this.components.set(key, entry);
    this.typeIndex.set(component.type, key);

    // Update category index
    if (!this.categoryIndex.has(component.category)) {
      this.categoryIndex.set(component.category, new Set());
    }
    this.categoryIndex.get(component.category)!.add(key);
  }

  /**
   * Update an existing component definition
   */
  update(type: string, version: string, updates: Partial<ComponentDefinition>): void {
    const key = this.makeKey(type, version);
    const entry = this.components.get(key);

    if (!entry) {
      throw new Error(`Component not found: ${key}`);
    }

    entry.value = { ...entry.value, ...updates };
    entry.updatedAt = new Date().toISOString();
  }

  /**
   * Get component by type (latest version)
   */
  get(type: string): ComponentDefinition | null {
    const key = this.typeIndex.get(type);
    if (!key) return null;

    const entry = this.components.get(key);
    return entry ? entry.value : null;
  }

  /**
   * Get component by type and version
   */
  getVersion(type: string, version: string): ComponentDefinition | null {
    const key = this.makeKey(type, version);
    const entry = this.components.get(key);
    return entry ? entry.value : null;
  }

  /**
   * List all components
   */
  list(filter?: RegistryFilter<ComponentDefinition>): ComponentDefinition[] {
    const components = Array.from(this.components.values()).map((entry) => entry.value);

    if (filter) {
      return components.filter(filter);
    }

    return components;
  }

  /**
   * List components by category
   */
  listByCategory(category: string): ComponentDefinition[] {
    const keys = this.categoryIndex.get(category);
    if (!keys) return [];

    return Array.from(keys)
      .map((key) => this.components.get(key)?.value)
      .filter((comp): comp is ComponentDefinition => comp !== undefined);
  }

  /**
   * List all categories
   */
  listCategories(): string[] {
    return Array.from(this.categoryIndex.keys()).sort();
  }

  /**
   * Search components
   */
  search(query: string): ComponentDefinition[] {
    const lowerQuery = query.toLowerCase();

    return this.list((comp) => {
      return (
        comp.label.toLowerCase().includes(lowerQuery) ||
        comp.description.toLowerCase().includes(lowerQuery) ||
        comp.type.toLowerCase().includes(lowerQuery) ||
        comp.tags.some((tag) => tag.toLowerCase().includes(lowerQuery))
      );
    });
  }

  /**
   * Check if component exists
   */
  has(type: string, version?: string): boolean {
    if (version) {
      return this.components.has(this.makeKey(type, version));
    }
    return this.typeIndex.has(type);
  }

  /**
   * Remove component
   */
  remove(type: string, version: string): boolean {
    const key = this.makeKey(type, version);
    const entry = this.components.get(key);

    if (!entry) return false;

    // Remove from category index
    const category = entry.value.category;
    const categorySet = this.categoryIndex.get(category);
    if (categorySet) {
      categorySet.delete(key);
      if (categorySet.size === 0) {
        this.categoryIndex.delete(category);
      }
    }

    // Remove from type index if this is the current version
    if (this.typeIndex.get(type) === key) {
      this.typeIndex.delete(type);
    }

    return this.components.delete(key);
  }

  /**
   * Clear all components
   */
  clear(): void {
    this.components.clear();
    this.categoryIndex.clear();
    this.typeIndex.clear();
  }

  /**
   * Get registry statistics
   */
  stats(): {
    total: number;
    categories: number;
    deprecated: number;
  } {
    const components = this.list();
    return {
      total: components.length,
      categories: this.categoryIndex.size,
      deprecated: components.filter((c) => c.deprecated).length,
    };
  }

  /**
   *
   */
  private makeKey(type: string, version: string): string {
    return `${type}@${version}`;
  }
}

// Singleton instance
export const ComponentRegistry = new ComponentRegistryClass();
