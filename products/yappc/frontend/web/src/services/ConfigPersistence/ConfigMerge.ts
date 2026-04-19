/**
 * Config Merge
 *
 * Merge utility for combining PageConfig versions.
 *
 * @packageDocumentation
 */

import type { PageConfig } from '@yappc/config-schema';

/**
 * @doc.type service
 * @doc.purpose Merge utility for combining PageConfig versions
 * @doc.layer product
 * @doc.pattern Service
 */
export class ConfigMerge {
  /**
   * Merge two PageConfig versions.
   *
   * @param base - Base config
   * @param incoming - Incoming config to merge
   * @param strategy - Merge strategy ('theirs' | 'ours' | 'auto')
   * @returns Merged config
   */
  merge(base: PageConfig, incoming: PageConfig, strategy: 'theirs' | 'ours' | 'auto' = 'auto'): PageConfig {
    switch (strategy) {
      case 'theirs':
        return JSON.parse(JSON.stringify(incoming));
      case 'ours':
        return JSON.parse(JSON.stringify(base));
      case 'auto':
        return this.autoMerge(base, incoming);
    }
  }

  /**
   * Auto-merge two configs with conflict resolution.
   */
  private autoMerge(base: PageConfig, incoming: PageConfig): PageConfig {
    const merged: PageConfig = JSON.parse(JSON.stringify(base));

    // Merge basic fields (prefer incoming)
    merged.id = incoming.id || base.id;
    merged.title = incoming.title || base.title;
    merged.route = incoming.route || base.route;
    merged.layout = incoming.layout || base.layout;

    // Merge components
    merged.components = this.mergeComponents(base.components || [], incoming.components || []);

    // Merge connections
    merged.connections = this.mergeConnections(base.connections, incoming.connections);

    // Merge metadata
    merged.metadata = {
      ...base.metadata,
      ...incoming.metadata,
      mergedAt: new Date().toISOString(),
    };

    return merged;
  }

  /**
   * Merge component arrays.
   */
  private mergeComponents(baseComponents: PageConfig['components'], incomingComponents: PageConfig['components']): PageConfig['components'] {
    const merged = [...baseComponents];
    const componentMap = new Map(baseComponents.map((c) => [c.id, c]));

    incomingComponents.forEach((incomingComp) => {
      const existing = componentMap.get(incomingComp.id);
      if (existing) {
        // Update existing component
        const index = merged.findIndex((c) => c.id === incomingComp.id);
        if (index !== -1) {
          merged[index] = incomingComp;
        }
      } else {
        // Add new component
        merged.push(incomingComp);
      }
    });

    return merged;
  }

  /**
   * Merge connection objects.
   */
  private mergeConnections(
    base: PageConfig['connections'],
    incoming: PageConfig['connections']
  ): PageConfig['connections'] {
    if (!incoming) return base;
    if (!base) return incoming;

    return {
      events: this.mergeConnectionArrays(base.events || [], incoming.events || []),
      data: this.mergeConnectionArrays(base.data || [], incoming.data || []),
      navigation: this.mergeConnectionArrays(base.navigation || [], incoming.navigation || []),
    };
  }

  private mergeConnectionArrays(base: unknown[], incoming: unknown[]): unknown[] {
    const merged = [...base];
    const incomingMap = new Map();

    incoming.forEach((item: unknown) => {
      if (item && typeof item === 'object' && 'id' in item) {
        incomingMap.set((item as { id: string }).id, item);
      }
    });

    base.forEach((item: unknown) => {
      if (item && typeof item === 'object' && 'id' in item) {
        const id = (item as { id: string }).id;
        if (!incomingMap.has(id)) {
          incomingMap.set(id, item);
        }
      }
    });

    return Array.from(incomingMap.values());
  }
}
