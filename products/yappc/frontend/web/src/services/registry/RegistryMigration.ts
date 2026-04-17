/**
 * Registry Migration Utilities
 * Tools for migrating existing canvas implementations to use the unified registry
 */

import { UnifiedRegistry } from './UnifiedRegistry';

import type { ComponentDefinition } from './types';
import type { RegistryEntry } from './UnifiedRegistry';

// Namespace constants for different canvas types
export const REGISTRY_NAMESPACES = {
  DEVSECOPS: 'devsecops',
  PAGE_DESIGNER: 'page-designer',
  CANVAS_SCENE: 'canvas-scene',
  FLOW_DIAGRAM: 'flow-diagram',
  SHARED: 'shared',
} as const;

/**
 *
 */
export type RegistryNamespace =
  (typeof REGISTRY_NAMESPACES)[keyof typeof REGISTRY_NAMESPACES];

// Legacy component definition (from old registries)
/**
 *
 */
export interface LegacyComponentDefinition {
  id: string;
  name?: string;
  label?: string;
  description?: string;
  category?: string;
  icon?: string;
  component?: unknown;
  props?: unknown;
  defaultProps?: unknown;
  schema?: unknown;
  tags?: string[];
}

/**
 * Migration utilities for converting legacy registries
 */
export class RegistryMigration {
  /**
   * Migrate legacy component definitions to unified registry
   */
  static migrateLegacyComponents(
    registry: UnifiedRegistry<ComponentDefinition>,
    namespace: RegistryNamespace,
    legacyComponents: LegacyComponentDefinition[]
  ): void {
    legacyComponents.forEach((legacy) => {
      const componentDefinition: ComponentDefinition = {
        id: legacy.id,
        label: legacy.label || legacy.name || legacy.id,
        description: legacy.description || '',
        category: legacy.category || 'misc',
        type: 'component',
        version: '1.0.0',
        component: legacy.component,
        props: legacy.props || {},
        defaultProps: legacy.defaultProps || {},
        icon: legacy.icon || undefined,
        tags: legacy.tags || [],
        // Add migration metadata
        metadata: {
          migratedFrom: 'legacy',
          originalId: legacy.id,
          migrationDate: new Date().toISOString(),
        },
      };

      try {
        registry.register(namespace, componentDefinition);
      } catch (error) {
        console.warn(`Failed to migrate component ${legacy.id}:`, error);
      }
    });
  }

  /**
   * Create DevSecOps component registry
   */
  static createDevSecOpsRegistry(
    registry: UnifiedRegistry<ComponentDefinition>
  ): void {
    const devSecOpsComponents: ComponentDefinition[] = [
      {
        id: 'phase-card',
        label: 'Phase Card',
        description: 'DevSecOps phase workflow card',
        category: 'workflow',
        type: 'component',
        version: '1.0.0',
        component: null, // To be set during migration
        props: {
          phase: { type: 'string', required: true },
          status: {
            type: 'string',
            enum: ['todo', 'in-progress', 'done', 'blocked'],
          },
          assignees: { type: 'array', items: { type: 'string' } },
          dueDate: { type: 'string', format: 'date' },
        },
        defaultProps: {
          status: 'todo',
        },
        icon: 'WorkflowIcon',
        tags: ['devsecops', 'workflow', 'card'],
      },
      {
        id: 'security-gate',
        label: 'Security Gate',
        description: 'Security checkpoint in pipeline',
        category: 'security',
        type: 'component',
        version: '1.0.0',
        component: null,
        props: {
          gateName: { type: 'string', required: true },
          status: { type: 'string', enum: ['passed', 'failed', 'pending'] },
          findings: { type: 'array', items: { type: 'object' } },
        },
        defaultProps: {
          status: 'pending',
        },
        icon: 'SecurityIcon',
        tags: ['devsecops', 'security', 'gate'],
      },
      {
        id: 'pipeline-stage',
        label: 'Pipeline Stage',
        description: 'CI/CD pipeline stage',
        category: 'pipeline',
        type: 'component',
        version: '1.0.0',
        component: null,
        props: {
          stageName: { type: 'string', required: true },
          duration: { type: 'number' },
          status: {
            type: 'string',
            enum: ['running', 'success', 'failed', 'skipped'],
          },
        },
        defaultProps: {
          status: 'running',
        },
        icon: 'PipelineIcon',
        tags: ['devsecops', 'pipeline', 'stage'],
      },
    ];

    devSecOpsComponents.forEach((component) => {
      registry.register(REGISTRY_NAMESPACES.DEVSECOPS, component);
    });
  }

  /**
   * Create Page Designer component registry
   */
  static createPageDesignerRegistry(
    registry: UnifiedRegistry<ComponentDefinition>
  ): void {
    const pageDesignerComponents: ComponentDefinition[] = [
      {
        id: 'button',
        label: 'Button',
        description: 'Material-UI Button component',
        category: 'input',
        type: 'component',
        version: '1.0.0',
        component: null, // To be set during migration
        props: {
          variant: { type: 'string', enum: ['text', 'outlined', 'contained'] },
          color: {
            type: 'string',
            enum: ['primary', 'secondary', 'success', 'error'],
          },
          size: { type: 'string', enum: ['small', 'medium', 'large'] },
          disabled: { type: 'boolean' },
        },
        defaultProps: {
          variant: 'contained',
          color: 'primary',
          size: 'medium',
          disabled: false,
        },
        icon: 'ButtonIcon',
        tags: ['mui', 'button', 'input'],
      },
      {
        id: 'text-field',
        label: 'Text Field',
        description: 'Material-UI TextField component',
        category: 'input',
        type: 'component',
        version: '1.0.0',
        component: null,
        props: {
          label: { type: 'string' },
          placeholder: { type: 'string' },
          variant: { type: 'string', enum: ['outlined', 'filled', 'standard'] },
          required: { type: 'boolean' },
          multiline: { type: 'boolean' },
          rows: { type: 'number' },
        },
        defaultProps: {
          variant: 'outlined',
          required: false,
          multiline: false,
        },
        icon: 'TextFieldIcon',
        tags: ['mui', 'input', 'form'],
      },
      {
        id: 'card',
        label: 'Card',
        description: 'Material-UI Card component',
        category: 'layout',
        type: 'component',
        version: '1.0.0',
        component: null,
        props: {
          elevation: { type: 'number', min: 0, max: 24 },
          variant: { type: 'string', enum: ['elevation', 'outlined'] },
        },
        defaultProps: {
          elevation: 1,
          variant: 'elevation',
        },
        icon: 'CardIcon',
        tags: ['mui', 'card', 'layout'],
      },
      {
        id: 'grid',
        label: 'Grid',
        description: 'Material-UI Grid component',
        category: 'layout',
        type: 'component',
        version: '1.0.0',
        component: null,
        props: {
          container: { type: 'boolean' },
          item: { type: 'boolean' },
          xs: { type: 'number', min: 1, max: 12 },
          sm: { type: 'number', min: 1, max: 12 },
          md: { type: 'number', min: 1, max: 12 },
          lg: { type: 'number', min: 1, max: 12 },
        },
        defaultProps: {
          container: false,
          item: false,
        },
        icon: 'GridIcon',
        tags: ['mui', 'grid', 'layout'],
      },
    ];

    pageDesignerComponents.forEach((component) => {
      registry.register(REGISTRY_NAMESPACES.PAGE_DESIGNER, component);
    });
  }

  /**
   * Create shared component registry
   */
  static createSharedRegistry(
    registry: UnifiedRegistry<ComponentDefinition>
  ): void {
    const sharedComponents: ComponentDefinition[] = [
      {
        id: 'text',
        label: 'Text',
        description: 'Basic text element',
        category: 'content',
        type: 'component',
        version: '1.0.0',
        component: null,
        props: {
          content: { type: 'string', required: true },
          variant: {
            type: 'string',
            enum: [
              'h1',
              'h2',
              'h3',
              'h4',
              'h5',
              'h6',
              'body1',
              'body2',
              'caption',
            ],
          },
          color: { type: 'string' },
          align: {
            type: 'string',
            enum: ['left', 'center', 'right', 'justify'],
          },
        },
        defaultProps: {
          variant: 'body1',
          align: 'left',
        },
        icon: 'TextIcon',
        tags: ['text', 'content', 'typography'],
      },
      {
        id: 'image',
        label: 'Image',
        description: 'Image display component',
        category: 'media',
        type: 'component',
        version: '1.0.0',
        component: null,
        props: {
          src: { type: 'string', required: true },
          alt: { type: 'string' },
          width: { type: 'number' },
          height: { type: 'number' },
          fit: {
            type: 'string',
            enum: ['cover', 'contain', 'fill', 'scale-down'],
          },
        },
        defaultProps: {
          fit: 'cover',
        },
        icon: 'ImageIcon',
        tags: ['image', 'media', 'content'],
      },
      {
        id: 'container',
        label: 'Container',
        description: 'Generic container component',
        category: 'layout',
        type: 'component',
        version: '1.0.0',
        component: null,
        props: {
          padding: { type: 'number' },
          margin: { type: 'number' },
          backgroundColor: { type: 'string' },
          borderRadius: { type: 'number' },
          minHeight: { type: 'number' },
        },
        defaultProps: {
          padding: 0,
          margin: 0,
        },
        icon: 'ContainerIcon',
        tags: ['container', 'layout', 'wrapper'],
      },
    ];

    sharedComponents.forEach((component) => {
      registry.register(REGISTRY_NAMESPACES.SHARED, component);
    });
  }

  /**
   * Validate registry integrity
   */
  static validateRegistry(registry: UnifiedRegistry<ComponentDefinition>): {
    isValid: boolean;
    errors: string[];
    warnings: string[];
  } {
    const errors: string[] = [];
    const warnings: string[] = [];
    const allEntries = registry.listEntries();

    // Check for duplicate IDs across namespaces
    const idMap = new Map<string, string[]>();
    allEntries.forEach((entry) => {
      const id = entry.value.id;
      if (!idMap.has(id)) {
        idMap.set(id, []);
      }
      idMap.get(id)!.push(entry.namespace);
    });

    idMap.forEach((namespaces, id) => {
      if (namespaces.length > 1) {
        warnings.push(
          `Component ID "${id}" exists in multiple namespaces: ${namespaces.join(', ')}`
        );
      }
    });

    // Check for missing required fields
    allEntries.forEach((entry) => {
      const component = entry.value;
      if (!component.id) {
        errors.push(
          `Component in namespace "${entry.namespace}" missing required id`
        );
      }
      if (!component.label) {
        warnings.push(`Component "${component.id}" missing label`);
      }
      if (!component.category) {
        warnings.push(`Component "${component.id}" missing category`);
      }
    });

    // Check for orphaned categories
    const categories = registry.getCategories();
    const usedCategories = new Set(
      allEntries.map((entry) => entry.value.category).filter(Boolean)
    );
    categories.forEach((category) => {
      if (!usedCategories.has(category)) {
        warnings.push(`Category "${category}" has no components`);
      }
    });

    return {
      isValid: errors.length === 0,
      errors,
      warnings,
    };
  }

  /**
   * Export registry data for backup
   */
  static exportRegistry(
    registry: UnifiedRegistry<ComponentDefinition>
  ): string {
    return JSON.stringify(registry.export(), null, 2);
  }

  /**
   * Import registry data from backup
   */
  static importRegistry(
    registry: UnifiedRegistry<ComponentDefinition>,
    data: string
  ): void {
    const entries = JSON.parse(data) as RegistryEntry<ComponentDefinition>[];
    registry.import(entries);
  }
}

// Initialize registries with default components
/**
 *
 */
export function initializeRegistries(): {
  componentRegistry: UnifiedRegistry<ComponentDefinition>;
  stats: unknown;
} {
  const registry = new UnifiedRegistry<ComponentDefinition>();

  // Create default components for each canvas type
  RegistryMigration.createDevSecOpsRegistry(registry);
  RegistryMigration.createPageDesignerRegistry(registry);
  RegistryMigration.createSharedRegistry(registry);

  const stats = registry.getStats();
  console.log('Registry initialized:', stats);

  // Validate integrity
  const validation = RegistryMigration.validateRegistry(registry);
  if (!validation.isValid) {
    console.error('Registry validation errors:', validation.errors);
  }
  if (validation.warnings.length > 0) {
    console.warn('Registry validation warnings:', validation.warnings);
  }

  return {
    componentRegistry: registry,
    stats,
  };
}
