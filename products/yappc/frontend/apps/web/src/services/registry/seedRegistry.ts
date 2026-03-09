/**
 * Seed registry with default components and schemas
 */

import { ComponentRegistry } from './ComponentRegistry';
import { SchemaRegistry } from './SchemaRegistry';
import {
  ButtonSchema,
  CardSchema,
  TextFieldSchema,
  TypographySchema,
  BoxSchema,
} from '../../components/canvas/page/schemas';

import type { ComponentDefinition } from './types';

/**
 * Seed component definitions
 */
export function seedComponents(): void {
  const components: ComponentDefinition[] = [
    // UI Components
    {
      id: 'ui.button',
      type: 'button',
      category: 'UI',
      label: 'Button',
      description: 'Interactive button component',
      icon: '🔘',
      version: '1.0.0',
      dataSchemaRef: 'ui.button@1.0.0',
      defaultData: {
        variant: 'contained',
        color: 'primary',
        size: 'medium',
        text: 'Button',
      },
      tags: ['ui', 'button', 'interactive'],
    },
    {
      id: 'ui.card',
      type: 'card',
      category: 'UI',
      label: 'Card',
      description: 'Material card container',
      icon: '🃏',
      version: '1.0.0',
      dataSchemaRef: 'ui.card@1.0.0',
      defaultData: {
        elevation: 2,
        title: 'Card Title',
      },
      tags: ['ui', 'card', 'container'],
    },
    {
      id: 'ui.textfield',
      type: 'textfield',
      category: 'UI',
      label: 'Text Field',
      description: 'Input text field',
      icon: '📝',
      version: '1.0.0',
      dataSchemaRef: 'ui.textfield@1.0.0',
      defaultData: {
        label: 'Text Field',
        variant: 'outlined',
        size: 'medium',
      },
      tags: ['ui', 'input', 'form'],
    },
    {
      id: 'ui.typography',
      type: 'typography',
      category: 'UI',
      label: 'Typography',
      description: 'Text display component',
      icon: '📄',
      version: '1.0.0',
      dataSchemaRef: 'ui.typography@1.0.0',
      defaultData: {
        variant: 'body1',
        text: 'Typography',
        align: 'left',
      },
      tags: ['ui', 'text', 'typography'],
    },
    {
      id: 'ui.box',
      type: 'box',
      category: 'UI',
      label: 'Container',
      description: 'Flexible box container',
      icon: '📦',
      version: '1.0.0',
      dataSchemaRef: 'ui.box@1.0.0',
      defaultData: {
        padding: 2,
        margin: 0,
        display: 'block',
      },
      tags: ['ui', 'container', 'layout'],
    },

    // Architecture Components
    {
      id: 'graph.service',
      type: 'api',
      category: 'Architecture',
      label: 'Service',
      description: 'Backend service or API',
      icon: '🔌',
      version: '1.0.0',
      defaultData: {
        label: 'Service',
        method: 'REST',
      },
      tags: ['architecture', 'service', 'api'],
    },
    {
      id: 'graph.database',
      type: 'data',
      category: 'Architecture',
      label: 'Database',
      description: 'Data storage system',
      icon: '🗃️',
      version: '1.0.0',
      defaultData: {
        label: 'Database',
        type: 'PostgreSQL',
      },
      tags: ['architecture', 'database', 'storage'],
    },
    {
      id: 'graph.component',
      type: 'component',
      category: 'Architecture',
      label: 'Component',
      description: 'Application component',
      icon: '🏗️',
      version: '1.0.0',
      defaultData: {
        label: 'Component',
        description: 'Application component',
      },
      tags: ['architecture', 'component'],
    },
  ];

  components.forEach((comp) => {
    try {
      ComponentRegistry.register(comp);
    } catch (error) {
      console.warn(`Failed to register component ${comp.id}:`, error);
    }
  });
}

/**
 * Seed schema definitions
 */
export function seedSchemas(): void {
  const schemas = [
    {
      id: 'ui.button@1.0.0',
      name: 'ui.button',
      version: '1.0.0',
      schema: ButtonSchema,
      description: 'Button component schema',
    },
    {
      id: 'ui.card@1.0.0',
      name: 'ui.card',
      version: '1.0.0',
      schema: CardSchema,
      description: 'Card component schema',
    },
    {
      id: 'ui.textfield@1.0.0',
      name: 'ui.textfield',
      version: '1.0.0',
      schema: TextFieldSchema,
      description: 'TextField component schema',
    },
    {
      id: 'ui.typography@1.0.0',
      name: 'ui.typography',
      version: '1.0.0',
      schema: TypographySchema,
      description: 'Typography component schema',
    },
    {
      id: 'ui.box@1.0.0',
      name: 'ui.box',
      version: '1.0.0',
      schema: BoxSchema,
      description: 'Box container schema',
    },
  ];

  schemas.forEach((schema) => {
    try {
      SchemaRegistry.register(schema);
    } catch (error) {
      console.warn(`Failed to register schema ${schema.id}:`, error);
    }
  });
}

/**
 * Initialize all registries
 */
export function initializeRegistries(): void {
  seedComponents();
  seedSchemas();
}
