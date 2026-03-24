/**
 * Component Registry for Node Renderer
 *
 * Maps component types to actual React components for rendering.
 * Enables dynamic component loading and rendering based on canvas node data.
 *
 * @module canvas/renderer/ComponentRegistry
 */

import React from 'react';

// ============================================================================
// Types
// ============================================================================

/**
 *
 */
export type ComponentType = React.ComponentType<unknown>;

/**
 *
 */
export interface RegisteredComponent {
  component: ComponentType;
  displayName: string;
  preloadData?: () => Promise<void>;
}

// ============================================================================
// Component Registry Implementation
// ============================================================================

/**
 *
 */
class ComponentRegistryImpl {
  private components = new Map<string, RegisteredComponent>();
  private preloadCache = new Set<string>();

  /**
   * Register a component
   */
  register(
    type: string,
    component: ComponentType,
    options: {
      displayName?: string;
      preloadData?: () => Promise<void>;
    } = {}
  ): void {
    this.components.set(type, {
      component,
      displayName: options.displayName || type,
      preloadData: options.preloadData,
    });
  }

  /**
   * Register multiple components at once
   */
  registerMany(
    components: Array<{
      type: string;
      component: ComponentType;
      displayName?: string;
      preloadData?: () => Promise<void>;
    }>
  ): void {
    for (const { type, component, displayName, preloadData } of components) {
      this.register(type, component, { displayName, preloadData });
    }
  }

  /**
   * Get a component by type
   */
  get(type: string): ComponentType | null {
    const registered = this.components.get(type);
    return registered?.component || null;
  }

  /**
   * Check if a component is registered
   */
  has(type: string): boolean {
    return this.components.has(type);
  }

  /**
   * Get all registered component types
   */
  getTypes(): string[] {
    return Array.from(this.components.keys());
  }

  /**
   * Unregister a component
   */
  unregister(type: string): boolean {
    return this.components.delete(type);
  }

  /**
   * Clear all components
   */
  clear(): void {
    this.components.clear();
    this.preloadCache.clear();
  }

  /**
   * Preload component data
   */
  async preload(type: string): Promise<void> {
    if (this.preloadCache.has(type)) {
      return;
    }

    const registered = this.components.get(type);
    if (registered?.preloadData) {
      await registered.preloadData();
      this.preloadCache.add(type);
    }
  }

  /**
   * Get component display name
   */
  getDisplayName(type: string): string {
    const registered = this.components.get(type);
    return registered?.displayName || type;
  }

  /**
   * Get registry size
   */
  get size(): number {
    return this.components.size;
  }
}

// ============================================================================
// Global Registry Instance
// ============================================================================

export const RendererComponentRegistry = new ComponentRegistryImpl();

// ============================================================================
// React Hook for Component Loading
// ============================================================================

/**
 * Hook to get a component from the registry
 */
export function useRegisteredComponent(type: string): ComponentType | null {
  const [component, setComponent] = React.useState<ComponentType | null>(() =>
    RendererComponentRegistry.get(type)
  );

  React.useEffect(() => {
    const loadComponent = async () => {
      if (!RendererComponentRegistry.has(type)) {
        console.warn(`Component type "${type}" not registered in RendererComponentRegistry`);
        setComponent(null);
        return;
      }

      // Preload if needed
      await RendererComponentRegistry.preload(type);

      const comp = RendererComponentRegistry.get(type);
      setComponent(comp);
    };

    loadComponent();
  }, [type]);

  return component;
}
