/**
 * Config Diff
 *
 * Diff utility for comparing PageConfig versions.
 *
 * @packageDocumentation
 */

interface PageComponentConfig {
  id: string;
  [key: string]: unknown;
}

interface PageConnections {
  events?: unknown[];
  data?: unknown[];
  navigation?: unknown[];
}

interface PageConfig {
  id: string;
  title: string;
  route: string;
  layout?: string;
  components?: PageComponentConfig[];
  connections?: PageConnections;
}

/**
 * @doc.type service
 * @doc.purpose Diff utility for comparing PageConfig versions
 * @doc.layer product
 * @doc.pattern Service
 */
export interface ConfigChange {
  path: string;
  type: 'added' | 'removed' | 'modified';
  oldValue: unknown;
  newValue: unknown;
}

export class ConfigDiff {
  /**
   * Compare two PageConfig versions and return changes.
   *
   * @param base - Base config
   * @param target - Target config
   * @returns Array of changes
   */
  compare(base: PageConfig, target: PageConfig): ConfigChange[] {
    const changes: ConfigChange[] = [];

    // Compare basic fields
    this.compareField(base, target, 'id', changes);
    this.compareField(base, target, 'title', changes);
    this.compareField(base, target, 'route', changes);
    this.compareField(base, target, 'layout', changes);

    // Compare components
    this.compareComponents(base, target, changes);

    // Compare connections
    this.compareConnections(base, target, changes);

    return changes;
  }

  /**
   * Compare a single field.
   */
  private compareField(
    base: PageConfig,
    target: PageConfig,
    field: keyof PageConfig,
    changes: ConfigChange[]
  ): void {
    if (base[field] !== target[field]) {
      changes.push({
        path: field as string,
        type: 'modified',
        oldValue: base[field],
        newValue: target[field],
      });
    }
  }

  /**
   * Compare components.
   */
  private compareComponents(base: PageConfig, target: PageConfig, changes: ConfigChange[]): void {
    const baseComponents = base.components || [];
    const targetComponents = target.components || [];

    // Check for added components
    targetComponents.forEach((targetComp: PageComponentConfig) => {
      const baseComp = baseComponents.find((c: PageComponentConfig) => c.id === targetComp.id);
      if (!baseComp) {
        changes.push({
          path: `components.${targetComp.id}`,
          type: 'added',
          oldValue: null,
          newValue: targetComp,
        });
      }
    });

    // Check for removed components
    baseComponents.forEach((baseComp: PageComponentConfig) => {
      const targetComp = targetComponents.find((c: PageComponentConfig) => c.id === baseComp.id);
      if (!targetComp) {
        changes.push({
          path: `components.${baseComp.id}`,
          type: 'removed',
          oldValue: baseComp,
          newValue: null,
        });
      }
    });

    // Check for modified components
    targetComponents.forEach((targetComp: PageComponentConfig) => {
      const baseComp = baseComponents.find((c: PageComponentConfig) => c.id === targetComp.id);
      if (baseComp) {
        const baseStr = JSON.stringify(baseComp);
        const targetStr = JSON.stringify(targetComp);
        if (baseStr !== targetStr) {
          changes.push({
            path: `components.${targetComp.id}`,
            type: 'modified',
            oldValue: baseComp,
            newValue: targetComp,
          });
        }
      }
    });
  }

  /**
   * Compare connections.
   */
  private compareConnections(base: PageConfig, target: PageConfig, changes: ConfigChange[]): void {
    const baseConnections = base.connections || { events: [], data: [], navigation: [] };
    const targetConnections = target.connections || { events: [], data: [], navigation: [] };

    this.compareConnectionArray(baseConnections.events ?? [], targetConnections.events ?? [], 'connections.events', changes);
    this.compareConnectionArray(baseConnections.data ?? [], targetConnections.data ?? [], 'connections.data', changes);
    this.compareConnectionArray(baseConnections.navigation ?? [], targetConnections.navigation ?? [], 'connections.navigation', changes);
  }

  private compareConnectionArray(
    base: unknown[],
    target: unknown[],
    path: string,
    changes: ConfigChange[]
  ): void {
    const baseStr = JSON.stringify(base);
    const targetStr = JSON.stringify(target);

    if (baseStr !== targetStr) {
      changes.push({
        path,
        type: 'modified',
        oldValue: base,
        newValue: target,
      });
    }
  }
}
