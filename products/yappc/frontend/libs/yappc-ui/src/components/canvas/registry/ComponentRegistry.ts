/**
 * Component Registry
 *
 * Central registry for all components available in the visual composer
 */

import type { ComponentSchema } from '../adapters/ComponentNodeAdapter';

/**
 * Component metadata
 */
export interface ComponentMetadata {
  /**
   * Component type identifier
   */
  type: string;

  /**
   * Display name
   */
  displayName: string;

  /**
   * Category for organization
   */
  category: 'atoms' | 'molecules' | 'organisms' | 'templates' | 'custom';

  /**
   * Description
   */
  description?: string;

  /**
   * Icon name or component
   */
  icon?: string;

  /**
   * Tags for search
   */
  tags?: string[];

  /**
   * Default props
   */
  defaultProps?: Record<string, unknown>;

  /**
   * Prop definitions for property panel
   */
  propDefinitions?: PropDefinition[];

  /**
   * Whether component can have children
   */
  acceptsChildren?: boolean;

  /**
   * Preview component (for palette)
   */
  preview?: React.ComponentType<unknown>;
}

/**
 * Property definition for forms
 */
export interface PropDefinition {
  name: string;
  type: 'string' | 'number' | 'boolean' | 'select' | 'color' | 'token' | 'object' | 'array';
  label: string;
  description?: string;
  required?: boolean;
  defaultValue?: unknown;
  options?: Array<{ label: string; value: string | number | boolean }>; // For select type
  tokenCategory?: string; // For token type
  validation?: unknown[];
}

/**
 * Component Registry class
 */
export class ComponentRegistry {
  private static components = new Map<string, ComponentMetadata>();

  /**
   * Register a component
   */
  static register(metadata: ComponentMetadata): void {
    this.components.set(metadata.type, metadata);
  }

  /**
   * Register multiple components
   */
  static registerMany(metadata: ComponentMetadata[]): void {
    metadata.forEach((meta) => this.register(meta));
  }

  /**
   * Get component metadata
   */
  static get(type: string): ComponentMetadata | undefined {
    return this.components.get(type);
  }

  /**
   * Get all components
   */
  static getAll(): ComponentMetadata[] {
    return Array.from(this.components.values());
  }

  /**
   * Get components by category
   */
  static getByCategory(category: ComponentMetadata['category']): ComponentMetadata[] {
    return this.getAll().filter((comp) => comp.category === category);
  }

  /**
   * Search components
   */
  static search(query: string): ComponentMetadata[] {
    const lowerQuery = query.toLowerCase();
    return this.getAll().filter(
      (comp) =>
        comp.displayName.toLowerCase().includes(lowerQuery) ||
        comp.type.toLowerCase().includes(lowerQuery) ||
        comp.tags?.some((tag) => tag.toLowerCase().includes(lowerQuery))
    );
  }

  /**
   * Check if component exists
   */
  static has(type: string): boolean {
    return this.components.has(type);
  }

  /**
   * Unregister a component
   */
  static unregister(type: string): boolean {
    return this.components.delete(type);
  }

  /**
   * Clear all registrations
   */
  static clear(): void {
    this.components.clear();
  }

  /**
   * Get component count
   */
  static count(): number {
    return this.components.size;
  }

  /**
   * Create default schema for component
   */
  static createDefaultSchema(type: string): ComponentSchema | null {
    const metadata = this.get(type);
    if (!metadata) return null;

    return {
      type,
      props: metadata.defaultProps || {},
      metadata: {
        label: metadata.displayName,
        description: metadata.description,
        category: metadata.category,
      },
    };
  }
}

// Auto-register Phase 2 components
ComponentRegistry.registerMany([
  // Atoms
  {
    type: 'Button',
    displayName: 'Button',
    category: 'atoms',
    description: 'Interactive button component',
    icon: 'button',
    tags: ['button', 'action', 'click'],
    defaultProps: {
      label: 'Button',
      variant: 'primary',
      size: 'medium',
    },
    propDefinitions: [
      { name: 'label', type: 'string', label: 'Label', required: true },
      {
        name: 'variant',
        type: 'select',
        label: 'Variant',
        options: [
          { label: 'Primary', value: 'primary' },
          { label: 'Secondary', value: 'secondary' },
          { label: 'Tertiary', value: 'tertiary' },
        ],
      },
      {
        name: 'size',
        type: 'select',
        label: 'Size',
        options: [
          { label: 'Small', value: 'small' },
          { label: 'Medium', value: 'medium' },
          { label: 'Large', value: 'large' },
        ],
      },
      { name: 'disabled', type: 'boolean', label: 'Disabled' },
      { name: 'color', type: 'token', label: 'Color', tokenCategory: 'color' },
    ],
  },
  {
    type: 'TextField',
    displayName: 'Text Field',
    category: 'atoms',
    description: 'Text input field',
    icon: 'input',
    tags: ['input', 'text', 'form'],
    defaultProps: {
      name: 'textField',
      placeholder: 'Enter text...',
      type: 'text',
    },
    propDefinitions: [
      { name: 'name', type: 'string', label: 'Name', required: true },
      { name: 'placeholder', type: 'string', label: 'Placeholder' },
      { name: 'label', type: 'string', label: 'Label' },
      {
        name: 'type',
        type: 'select',
        label: 'Type',
        options: [
          { label: 'Text', value: 'text' },
          { label: 'Email', value: 'email' },
          { label: 'Password', value: 'password' },
          { label: 'Number', value: 'number' },
        ],
      },
      { name: 'required', type: 'boolean', label: 'Required' },
      { name: 'disabled', type: 'boolean', label: 'Disabled' },
    ],
  },
  {
    type: 'Text',
    displayName: 'Text',
    category: 'atoms',
    description: 'Text display component',
    icon: 'text',
    tags: ['text', 'label', 'typography'],
    defaultProps: {
      children: 'Text content',
    },
    propDefinitions: [
      { name: 'children', type: 'string', label: 'Content', required: true },
      { name: 'color', type: 'token', label: 'Color', tokenCategory: 'color' },
      { name: 'fontSize', type: 'token', label: 'Font Size', tokenCategory: 'typography' },
    ],
  },

  // Molecules
  {
    type: 'Card',
    displayName: 'Card',
    category: 'molecules',
    description: 'Card container component',
    icon: 'card',
    tags: ['card', 'container', 'box'],
    acceptsChildren: true,
    defaultProps: {
      title: 'Card Title',
      elevation: 1,
    },
    propDefinitions: [
      { name: 'title', type: 'string', label: 'Title' },
      {
        name: 'elevation',
        type: 'select',
        label: 'Elevation',
        options: [
          { label: 'None', value: 0 },
          { label: 'Low', value: 1 },
          { label: 'Medium', value: 2 },
          { label: 'High', value: 3 },
        ],
      },
      { name: 'backgroundColor', type: 'token', label: 'Background', tokenCategory: 'color' },
    ],
  },
  {
    type: 'Stack',
    displayName: 'Stack',
    category: 'molecules',
    description: 'Vertical or horizontal stack layout',
    icon: 'stack',
    tags: ['stack', 'layout', 'container'],
    acceptsChildren: true,
    defaultProps: {
      direction: 'vertical',
      spacing: 'md',
    },
    propDefinitions: [
      {
        name: 'direction',
        type: 'select',
        label: 'Direction',
        options: [
          { label: 'Vertical', value: 'vertical' },
          { label: 'Horizontal', value: 'horizontal' },
        ],
      },
      { name: 'spacing', type: 'token', label: 'Spacing', tokenCategory: 'spacing' },
    ],
  },

  // Organisms
  {
    type: 'Form',
    displayName: 'Form',
    category: 'organisms',
    description: 'Form container with validation',
    icon: 'form',
    tags: ['form', 'validation', 'submit'],
    acceptsChildren: true,
    defaultProps: {
      name: 'form',
    },
    propDefinitions: [
      { name: 'name', type: 'string', label: 'Name', required: true },
      { name: 'onSubmit', type: 'string', label: 'Submit Handler' },
    ],
  },
]);
