/**
 * Component Registry - Central registry for all canvas components
 */

import type { ComponentDefinition } from './types';

type ComponentPredicate = (component: ComponentDefinition) => boolean;

interface RegistryEntry {
  key: string;
  namespace: string;
  value: ComponentDefinition;
  registeredAt: string;
  updatedAt: string;
}

const COMPONENT_NAMESPACE = 'canvas-components';

/**
 *
 */
class ComponentRegistryClass {
  private components: Map<string, RegistryEntry> = new Map();
  private categoryIndex: Map<string, Set<string>> = new Map();
  private typeIndex: Map<string, string> = new Map();

  /**
   * Register a component definition
   */
  register(component: ComponentDefinition): void {
    const type = component.type ?? component.id;
    const version = component.version ?? 'latest';
    const category = component.category ?? 'uncategorized';
    const key = this.makeKey(type, version);

    if (this.components.has(key)) {
      throw new Error(`Component already registered: ${key}`);
    }

    const entry: RegistryEntry = {
      key,
      namespace: COMPONENT_NAMESPACE,
      value: component,
      registeredAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };

    this.components.set(key, entry);
    this.typeIndex.set(type, key);

    // Update category index
    if (!this.categoryIndex.has(category)) {
      this.categoryIndex.set(category, new Set());
    }
    this.categoryIndex.get(category)!.add(key);
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
  list(filter?: ComponentPredicate): ComponentDefinition[] {
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
        (comp.label ?? '').toLowerCase().includes(lowerQuery) ||
        (comp.description ?? '').toLowerCase().includes(lowerQuery) ||
        (comp.type ?? '').toLowerCase().includes(lowerQuery) ||
        (comp.tags ?? []).some((tag: string) => tag.toLowerCase().includes(lowerQuery))
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
    const categorySet = category ? this.categoryIndex.get(category) : undefined;
    if (categorySet) {
      categorySet.delete(key);
      if (categorySet.size === 0 && category) {
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
