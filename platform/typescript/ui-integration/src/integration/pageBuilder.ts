/**
 * Page Builder Integration for @ghatana/ui
 * 
 * Provides component palette, theme synchronization, and property mapping
 * for seamless integration with page builder systems.
 */

// import { tokens } from '@ghatana/tokens'; // TODO: Fix tokens import when available

/**
 * Component palette configuration for page builder
 */
export interface ComponentPalette {
  category: string;
  components: ComponentDefinition[];
}

export interface ComponentDefinition {
  id: string;
  name: string;
  description: string;
  icon?: string;
  category: string;
  properties: PropertyDefinition[];
  defaultProps: Record<string, unknown>;
  variants?: ComponentVariant[];
}

export interface PropertyDefinition {
  name: string;
  type: 'string' | 'number' | 'boolean' | 'select' | 'color' | 'size' | 'array' | 'object';
  label: string;
  description?: string;
  required?: boolean;
  defaultValue?: unknown;
  options?: Array<{ label: string; value: unknown }>;
  validation?: PropertyValidation;
}

export interface PropertyValidation {
  min?: number;
  max?: number;
  pattern?: string;
  message?: string;
}

export interface ComponentVariant {
  name: string;
  label: string;
  props: Record<string, unknown>;
}

/**
 * Complete component palette for page builder
 */
export const componentPalette: ComponentPalette[] = [
  {
    category: 'Layout',
    components: [
      {
        id: 'box',
        name: 'Box',
        description: 'Flexible container with spacing and styling options',
        category: 'Layout',
        properties: [
          {
            name: 'as',
            type: 'select',
            label: 'Element Type',
            options: [
              { label: 'div', value: 'div' },
              { label: 'section', value: 'section' },
              { label: 'article', value: 'article' },
              { label: 'main', value: 'main' },
            ],
            defaultValue: 'div',
          },
          {
            name: 'padding',
            type: 'size',
            label: 'Padding',
            defaultValue: 'md',
          },
          {
            name: 'margin',
            type: 'size',
            label: 'Margin',
            defaultValue: 'none',
          },
          {
            name: 'backgroundColor',
            type: 'color',
            label: 'Background Color',
            defaultValue: 'transparent',
          },
          {
            name: 'border',
            type: 'boolean',
            label: 'Show Border',
            defaultValue: false,
          },
        ],
        defaultProps: {
          as: 'div',
          padding: 'md',
          margin: 'none',
          backgroundColor: 'transparent',
          border: false,
        },
      },
      {
        id: 'stack',
        name: 'Stack',
        description: 'Flexible stack layout with spacing',
        category: 'Layout',
        properties: [
          {
            name: 'direction',
            type: 'select',
            label: 'Direction',
            options: [
              { label: 'Vertical', value: 'vertical' },
              { label: 'Horizontal', value: 'horizontal' },
            ],
            defaultValue: 'vertical',
          },
          {
            name: 'spacing',
            type: 'size',
            label: 'Spacing',
            defaultValue: 'md',
          },
          {
            name: 'align',
            type: 'select',
            label: 'Alignment',
            options: [
              { label: 'Start', value: 'start' },
              { label: 'Center', value: 'center' },
              { label: 'End', value: 'end' },
              { label: 'Stretch', value: 'stretch' },
            ],
            defaultValue: 'start',
          },
        ],
        defaultProps: {
          direction: 'vertical',
          spacing: 'md',
          align: 'start',
        },
      },
      {
        id: 'grid',
        name: 'Grid',
        description: '12-column responsive grid system',
        category: 'Layout',
        properties: [
          {
            name: 'columns',
            type: 'number',
            label: 'Columns',
            defaultValue: 12,
            validation: { min: 1, max: 12 },
          },
          {
            name: 'gap',
            type: 'size',
            label: 'Gap',
            defaultValue: 'md',
          },
          {
            name: 'responsive',
            type: 'boolean',
            label: 'Responsive',
            defaultValue: true,
          },
        ],
        defaultProps: {
          columns: 12,
          gap: 'md',
          responsive: true,
        },
      },
    ],
  },
  {
    category: 'Form',
    components: [
      {
        id: 'text-field',
        name: 'Text Field',
        description: 'Text input with validation and styling',
        category: 'Form',
        properties: [
          {
            name: 'label',
            type: 'string',
            label: 'Label',
            required: true,
          },
          {
            name: 'placeholder',
            type: 'string',
            label: 'Placeholder',
          },
          {
            name: 'type',
            type: 'select',
            label: 'Input Type',
            options: [
              { label: 'Text', value: 'text' },
              { label: 'Email', value: 'email' },
              { label: 'Password', value: 'password' },
              { label: 'Number', value: 'number' },
            ],
            defaultValue: 'text',
          },
          {
            name: 'size',
            type: 'select',
            label: 'Size',
            options: [
              { label: 'Small', value: 'sm' },
              { label: 'Medium', value: 'md' },
              { label: 'Large', value: 'lg' },
            ],
            defaultValue: 'md',
          },
          {
            name: 'required',
            type: 'boolean',
            label: 'Required',
            defaultValue: false,
          },
          {
            name: 'disabled',
            type: 'boolean',
            label: 'Disabled',
            defaultValue: false,
          },
        ],
        defaultProps: {
          label: 'Text Input',
          type: 'text',
          size: 'md',
          required: false,
          disabled: false,
        },
      },
      {
        id: 'button',
        name: 'Button',
        description: 'Interactive button with variants',
        category: 'Form',
        properties: [
          {
            name: 'children',
            type: 'string',
            label: 'Button Text',
            required: true,
          },
          {
            name: 'variant',
            type: 'select',
            label: 'Variant',
            options: [
              { label: 'Primary', value: 'primary' },
              { label: 'Secondary', value: 'secondary' },
              { label: 'Tertiary', value: 'tertiary' },
              { label: 'Danger', value: 'danger' },
            ],
            defaultValue: 'primary',
          },
          {
            name: 'size',
            type: 'select',
            label: 'Size',
            options: [
              { label: 'Small', value: 'sm' },
              { label: 'Medium', value: 'md' },
              { label: 'Large', value: 'lg' },
            ],
            defaultValue: 'md',
          },
          {
            name: 'fullWidth',
            type: 'boolean',
            label: 'Full Width',
            defaultValue: false,
          },
        ],
        defaultProps: {
          children: 'Button',
          variant: 'primary',
          size: 'md',
          fullWidth: false,
        },
      },
      {
        id: 'select',
        name: 'Select',
        description: 'Dropdown selection component',
        category: 'Form',
        properties: [
          {
            name: 'label',
            type: 'string',
            label: 'Label',
            required: true,
          },
          {
            name: 'placeholder',
            type: 'string',
            label: 'Placeholder',
            defaultValue: 'Select an option',
          },
          {
            name: 'options',
            type: 'array',
            label: 'Options',
            required: true,
          },
          {
            name: 'size',
            type: 'select',
            label: 'Size',
            options: [
              { label: 'Small', value: 'sm' },
              { label: 'Medium', value: 'md' },
              { label: 'Large', value: 'lg' },
            ],
            defaultValue: 'md',
          },
        ],
        defaultProps: {
          label: 'Select',
          placeholder: 'Select an option',
          options: [],
          size: 'md',
        },
      },
    ],
  },
  {
    category: 'Content',
    components: [
      {
        id: 'text',
        name: 'Text',
        description: 'Typography component with variants',
        category: 'Content',
        properties: [
          {
            name: 'children',
            type: 'string',
            label: 'Text Content',
            required: true,
          },
          {
            name: 'variant',
            type: 'select',
            label: 'Variant',
            options: [
              { label: 'Body 1', value: 'body1' },
              { label: 'Body 2', value: 'body2' },
              { label: 'Caption', value: 'caption' },
              { label: 'Overline', value: 'overline' },
            ],
            defaultValue: 'body1',
          },
          {
            name: 'size',
            type: 'select',
            label: 'Size',
            options: [
              { label: 'Extra Small', value: 'xs' },
              { label: 'Small', value: 'sm' },
              { label: 'Medium', value: 'md' },
              { label: 'Large', value: 'lg' },
              { label: 'Extra Large', value: 'xl' },
            ],
            defaultValue: 'md',
          },
          {
            name: 'weight',
            type: 'select',
            label: 'Font Weight',
            options: [
              { label: 'Normal', value: 'normal' },
              { label: 'Medium', value: 'medium' },
              { label: 'Semibold', value: 'semibold' },
              { label: 'Bold', value: 'bold' },
            ],
            defaultValue: 'normal',
          },
        ],
        defaultProps: {
          children: 'Text content',
          variant: 'body1',
          size: 'md',
          weight: 'normal',
        },
      },
      {
        id: 'heading',
        name: 'Heading',
        description: 'Semantic heading component',
        category: 'Content',
        properties: [
          {
            name: 'children',
            type: 'string',
            label: 'Heading Text',
            required: true,
          },
          {
            name: 'level',
            type: 'select',
            label: 'Heading Level',
            options: [
              { label: 'H1', value: 'h1' },
              { label: 'H2', value: 'h2' },
              { label: 'H3', value: 'h3' },
              { label: 'H4', value: 'h4' },
              { label: 'H5', value: 'h5' },
              { label: 'H6', value: 'h6' },
            ],
            defaultValue: 'h2',
          },
          {
            name: 'size',
            type: 'select',
            label: 'Display Size',
            options: [
              { label: 'Small', value: 'sm' },
              { label: 'Medium', value: 'md' },
              { label: 'Large', value: 'lg' },
              { label: 'Extra Large', value: 'xl' },
            ],
            defaultValue: 'lg',
          },
        ],
        defaultProps: {
          children: 'Heading',
          level: 'h2',
          size: 'lg',
        },
      },
      {
        id: 'card',
        name: 'Card',
        description: 'Content container with elevation',
        category: 'Content',
        properties: [
          {
            name: 'children',
            type: 'object',
            label: 'Card Content',
            required: true,
          },
          {
            name: 'variant',
            type: 'select',
            label: 'Variant',
            options: [
              { label: 'Default', value: 'default' },
              { label: 'Elevated', value: 'elevated' },
              { label: 'Outlined', value: 'outlined' },
            ],
            defaultValue: 'default',
          },
          {
            name: 'padding',
            type: 'select',
            label: 'Padding',
            options: [
              { label: 'None', value: 'none' },
              { label: 'Small', value: 'sm' },
              { label: 'Medium', value: 'md' },
              { label: 'Large', value: 'lg' },
            ],
            defaultValue: 'md',
          },
        ],
        defaultProps: {
          variant: 'default',
          padding: 'md',
        },
      },
    ],
  },
];

/**
 * Theme synchronization utilities
 */
export interface ThemeConfig {
  colors: Record<string, string>;
  spacing: Record<string, string>;
  typography: Record<string, unknown>;
  borderRadius: Record<string, string>;
}

/**
 * Convert design tokens to page builder theme format
 */
export function tokensToPageBuilderTheme(): ThemeConfig {
  // TODO: Replace with actual tokens when @ghatana/tokens is available
  return {
    colors: {
      primary: '#3B82F6',
      'primary-hover': '#2563EB',
      'primary-light': '#E3F2FE',
      secondary: '#616161',
      'secondary-hover': '#424242',
      'secondary-light': '#EEEEEE',
      success: '#4CAF50',
      warning: '#FF9800',
      error: '#F44336',
      info: '#2196F3',
      background: '#FFFFFF',
      surface: '#FAFAFA',
      'surface-hover': '#F5F5F5',
      border: '#E0EOEO',
      text: '#212121',
      'text-secondary': '#7575',
      'text-disabled': '#B',
    },
    spacing: {
      xs: '0.25rem',
      sm: '0.5rem',
      md: '1rem',
      lg: '1.5rem',
      xl: '2rem',
      '2xl': '3rem',
      '3xl': '4rem',
    },
    typography: {
      fontFamily: 'Inter, system-ui, sans-serif',
      fontSize: {
        xs: '0.75rem',
        sm: '0.875rem',
        base: '1rem',
        lg: '1.125rem',
        xl: '1.25rem',
        '2xl': '1.5rem',
        '3xl': '1.875rem',
      },
      fontWeight: {
        normal: '400',
        medium: '500',
        semibold: '600',
        bold: '700',
      },
      lineHeight: {
        tight: '1.25',
        normal: '1.5',
        relaxed: '1.75',
      },
    },
    borderRadius: {
      none: '0',
      sm: '0.125rem',
      md: '0.375rem',
      lg: '0.5rem',
      full: '9999px',
    },
  };
}

/**
 * Style override system for page builder
 */
export interface StyleOverride {
  componentId: string;
  properties: Record<string, unknown>;
  breakpoints?: Record<string, Record<string, unknown>>;
}

/**
 * Apply style overrides to component properties
 */
export function applyStyleOverrides(
  componentId: string,
  baseProps: Record<string, unknown>,
  overrides: StyleOverride[]
): Record<string, unknown> {
  const override = overrides.find(o => o.componentId === componentId);
  if (!override) return baseProps;

  return {
    ...baseProps,
    ...override.properties,
    style: {
      ...(baseProps.style as Record<string, unknown> | undefined),
      ...(override.properties.style as Record<string, unknown> | undefined),
    },
  };
}

/**
 * Component property mapping for different page builder systems
 */
export interface PropertyMapping {
  sourceProperty: string;
  targetProperty: string;
  transform?: (value: unknown) => unknown;
}

export interface ComponentMapping {
  componentId: string;
  mappings: PropertyMapping[];
}

/**
 * Property mappings for common page builder systems
 */
export const propertyMappings: ComponentMapping[] = [
  {
    componentId: 'button',
    mappings: [
      { sourceProperty: 'text', targetProperty: 'children' },
      { sourceProperty: 'buttonType', targetProperty: 'variant' },
      { sourceProperty: 'buttonSize', targetProperty: 'size' },
    ],
  },
  {
    componentId: 'text-field',
    mappings: [
      { sourceProperty: 'fieldLabel', targetProperty: 'label' },
      { sourceProperty: 'inputType', targetProperty: 'type' },
      { sourceProperty: 'isRequired', targetProperty: 'required' },
      { sourceProperty: 'isDisabled', targetProperty: 'disabled' },
    ],
  },
  {
    componentId: 'card',
    mappings: [
      { sourceProperty: 'elevation', targetProperty: 'variant' },
      { sourceProperty: 'cardPadding', targetProperty: 'padding' },
    ],
  },
];

/**
 * Map properties from page builder format to component format
 */
export function mapComponentProperties(
  componentId: string,
  sourceProps: Record<string, unknown>
): Record<string, unknown> {
  const mapping = propertyMappings.find(m => m.componentId === componentId);
  if (!mapping) return sourceProps;

  const mappedProps: Record<string, unknown> = {};

  mapping.mappings.forEach(({ sourceProperty, targetProperty, transform }) => {
    const value = sourceProps[sourceProperty];
    if (value !== undefined) {
      mappedProps[targetProperty] = transform ? transform(value) : value;
    }
  });

  // Include any unmapped properties
  Object.keys(sourceProps).forEach(key => {
    const isMapped = mapping.mappings.some(m => m.sourceProperty === key);
    if (!isMapped && !mappedProps.hasOwnProperty(key)) {
      mappedProps[key] = sourceProps[key];
    }
  });

  return mappedProps;
}

/**
 * Page builder integration utilities
 */
export const PageBuilderIntegration = {
  /**
   * Get component palette for page builder
   */
  getPalette: () => componentPalette,

  /**
   * Get theme configuration for page builder
   */
  getTheme: () => tokensToPageBuilderTheme(),

  /**
   * Apply style overrides
   */
  applyOverrides: applyStyleOverrides,

  /**
   * Map component properties
   */
  mapProperties: mapComponentProperties,

  /**
   * Validate component configuration
   */
  validateComponent: (componentId: string, props: Record<string, unknown>) => {
    const component = componentPalette
      .flatMap(category => category.components)
      .find(c => c.id === componentId);

    if (!component) {
      return { valid: false, errors: [`Unknown component: ${componentId}`] };
    }

    const errors: string[] = [];

    component.properties.forEach(prop => {
      if (prop.required && props[prop.name] === undefined) {
        errors.push(`Required property '${prop.label}' is missing`);
      }

      if (prop.validation && props[prop.name] !== undefined) {
        const value = props[prop.name];
        if (prop.validation.min !== undefined && value < prop.validation.min) {
          errors.push(`Property '${prop.label}' must be at least ${prop.validation.min}`);
        }
        if (prop.validation.max !== undefined && value > prop.validation.max) {
          errors.push(`Property '${prop.label}' must be at most ${prop.validation.max}`);
        }
      }
    });

    return {
      valid: errors.length === 0,
      errors,
    };
  },
};

export default PageBuilderIntegration;
