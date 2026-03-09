/**
 * Component Renderer
 *
 * Renders React UI components from JSON schema definitions.
 * Supports all atomic design components (atoms, molecules, organisms).
 *
 * @packageDocumentation
 */

import { Box } from '@ghatana/ui';
import React from 'react';

// Import atoms
import { Alert } from '../components/Alert';
import { Avatar } from '../components/Avatar';
import { Badge } from '../components/Badge';
import { Button } from '../components/Button';
import { Card } from '../components/Card';
import { Checkbox } from '../components/Checkbox';
import { Chip } from '../components/Chip';
import { Form } from '../components/Form';
import { Grid } from '../components/Grid';
import { Input } from '../components/Input';
import { Radio } from '../components/Radio';
import { Select } from '../components/Select';
import { Stack } from '../components/Stack';
import { Switch } from '../components/Switch';
import { Tabs } from '../components/Tabs';
import { TextField } from '../components/TextField';
import { Tooltip } from '../components/Tooltip';

// Import molecules

// Import organisms

/**
 * Component schema definition
 */
export interface ComponentSchema {
  /**
   * Component type (matches component name)
   */
  type: string;

  /**
   * Component props
   */
  props?: Record<string, unknown>;

  /**
   * Child components or content
   */
  children?: ComponentSchema[] | string;

  /**
   * Unique identifier for this component instance
   */
  id?: string;

  /**
   * Conditional rendering expression
   */
  condition?: string;

  /**
   * Data binding configuration
   */
  dataBinding?: {
    source: string;
    path?: string;
    transform?: string;
  };

  /**
   * Event handlers
   */
  events?: Record<string, string>;
}

/**
 * Component registry mapping type names to React components
 */
const COMPONENT_REGISTRY: Record<string, React.ComponentType<unknown>> = {
  // Layout
  Box,
  Grid,
  Stack,

  // Atoms
  Button,
  Input,
  Checkbox,
  Radio,
  Switch,
  Badge,
  Chip,
  Avatar,
  Tooltip,

  // Molecules
  TextField,
  Select,
  Alert,

  // Organisms
  Card,
  Form,
  Tabs,
};

/**
 * Context for data binding and event handling
 */
export interface RendererContext {
  /**
   * Data available for binding
   */
  data?: Record<string, unknown>;

  /**
   * Event handlers
   */
  handlers?: Record<string, (...args: unknown[]) => void>;

  /**
   * Parent component context
   */
  parent?: RendererContext;
}

/**
 * Renderer options
 */
export interface RendererOptions {
  /**
   * Enable strict mode (throws on unknown components)
   */
  strict?: boolean;

  /**
   * Custom component registry
   */
  components?: Record<string, React.ComponentType<unknown>>;

  /**
   * Enable debugging output
   */
  debug?: boolean;
}

/**
 * Component Renderer Props
 */
export interface ComponentRendererProps {
  /**
   * JSON schema to render
   */
  schema: ComponentSchema | ComponentSchema[];

  /**
   * Context for data binding and events
   */
  context?: RendererContext;

  /**
   * Renderer options
   */
  options?: RendererOptions;

  /**
   * Callback when schema changes (for live editing)
   */
  onSchemaChange?: (schema: ComponentSchema | ComponentSchema[]) => void;

  /**
   * Error boundary fallback
   */
  errorFallback?: React.ReactNode;
}

/**
 * Evaluate a condition expression
 */
function evaluateCondition(condition: string, context: RendererContext): boolean {
  try {
    // Simple condition evaluation (can be enhanced with a proper expression parser)
    // For now, support simple property checks
    const match = condition.match(/^(\w+\.?\w*)$/);
    if (match) {
      const path = match[1].split('.');
      let value: unknown = context.data;
      for (const key of path) {
        value = (value as Record<string, unknown>)?.[key];
      }
      return Boolean(value);
    }
    return true;
  } catch (error) {
    console.warn('Failed to evaluate condition:', condition, error);
    return true;
  }
}

/**
 * Resolve data binding
 */
function resolveDataBinding(
  binding: ComponentSchema['dataBinding'],
  context: RendererContext
): unknown {
  if (!binding) return undefined;

  try {
    const { source, path, transform } = binding;

    // Get data from context
    let value = context.data?.[source];

    // Navigate path if provided
    if (path && value) {
      const pathParts = path.split('.');
      for (const part of pathParts) {
        value = value[part];
      }
    }

    // Apply transform if provided (simple function names)
    if (transform && typeof value === 'string') {
      switch (transform) {
        case 'uppercase':
          value = value.toUpperCase();
          break;
        case 'lowercase':
          value = value.toLowerCase();
          break;
        case 'capitalize':
          value = value.charAt(0).toUpperCase() + value.slice(1);
          break;
      }
    }

    return value;
  } catch (error) {
    console.warn('Failed to resolve data binding:', binding, error);
    return undefined;
  }
}

/**
 * Sanitize props to prevent invalid HTML attributes from reaching DOM
 * - Converts boolean values to strings for HTML attributes that support boolean strings
 * - Removes or converts problematic attributes
 */
function sanitizeProps(props: Record<string, unknown>): Record<string, unknown> {
  const sanitized: Record<string, unknown> = {};

  for (const [key, value] of Object.entries(props)) {
    // If value is boolean, convert to string or skip depending on attribute type
    if (typeof value === 'boolean') {
      // For attributes like 'item', 'xs', 'sm', 'md', 'lg', 'xl' (Material-UI Grid/flex attributes)
      // convert boolean to string representation
      if (
        key === 'item' ||
        key === 'xs' ||
        key === 'sm' ||
        key === 'md' ||
        key === 'lg' ||
        key === 'xl' ||
        key === 'container'
      ) {
        sanitized[key] = value.toString();
      } else if (
        // For standard HTML boolean attributes, keep them as-is
        key === 'disabled' ||
        key === 'readOnly' ||
        key === 'required' ||
        key === 'autoplay' ||
        key === 'controls' ||
        key === 'loop' ||
        key === 'muted' ||
        key === 'checked' ||
        key === 'selected' ||
        key === 'defaultChecked' ||
        key === 'defaultSelected'
      ) {
        sanitized[key] = value;
      } else {
        // For other boolean props, skip if false, include if true
        if (value !== false) {
          sanitized[key] = value;
        }
      }
    } else {
      sanitized[key] = value;
    }
  }

  return sanitized;
}

/**
 * Render a single component from schema
 */
function renderComponent(
  schema: ComponentSchema,
  context: RendererContext,
  options: RendererOptions,
  key: string | number
): React.ReactNode {
  // Check condition
  if (schema.condition && !evaluateCondition(schema.condition, context)) {
    return null;
  }

  // Get component from registry
  const componentRegistry = {
    ...COMPONENT_REGISTRY,
    ...options.components,
  };

  const Component = componentRegistry[schema.type];

  if (!Component) {
    if (options.strict) {
      throw new Error(`Unknown component type: ${schema.type}`);
    }
    if (options.debug) {
      console.warn(`Unknown component type: ${schema.type}`);
    }
    return null;
  }

  // Build props
  let props: Record<string, unknown> = {
    ...schema.props,
  };

  // Apply data binding
  if (schema.dataBinding) {
    const boundValue = resolveDataBinding(schema.dataBinding, context);
    if (boundValue !== undefined) {
      props.value = boundValue;
    }
  }

  // Attach event handlers
  if (schema.events) {
    Object.entries(schema.events).forEach(([eventName, handlerName]) => {
      const handler = context.handlers?.[handlerName];
      if (handler) {
        props[eventName] = handler;
      } else if (options.debug) {
        console.warn(`Handler not found: ${handlerName}`);
      }
    });
  }

  // Sanitize props to prevent invalid HTML attributes
  props = sanitizeProps(props);

  // Render children
  let children: React.ReactNode = null;

  if (typeof schema.children === 'string') {
    // String content
    children = schema.children;
  } else if (Array.isArray(schema.children)) {
    // Nested components
    children = schema.children.map((child, index) =>
      renderComponent(child, context, options, `${key}-${index}`)
    );
  }

  return <Component key={key} {...props}>{children}</Component>;
}

/**
 * Component Renderer
 *
 * Renders React UI from JSON schema definitions with data binding and event handling.
 *
 * ## Features
 * - ✅ Supports all atomic design components
 * - ✅ Data binding from context
 * - ✅ Event handler attachment
 * - ✅ Conditional rendering
 * - ✅ Nested component composition
 * - ✅ Custom component registry
 * - ✅ Error boundaries
 * - ✅ Debug mode
 *
 * @example Basic Usage
 * ```tsx
 * const schema = {
 *   type: 'Button',
 *   props: { variant: 'contained', color: 'primary' },
 *   children: 'Click Me'
 * };
 *
 * <ComponentRenderer schema={schema} />
 * ```
 *
 * @example With Data Binding
 * ```tsx
 * const schema = {
 *   type: 'TextField',
 *   props: { label: 'Username' },
 *   dataBinding: { source: 'user', path: 'name' }
 * };
 *
 * const context = {
 *   data: { user: { name: 'John Doe' } }
 * };
 *
 * <ComponentRenderer schema={schema} context={context} />
 * ```
 *
 * @example With Events
 * ```tsx
 * const schema = {
 *   type: 'Button',
 *   children: 'Submit',
 *   events: { onClick: 'handleSubmit' }
 * };
 *
 * const context = {
 *   handlers: {
 *     handleSubmit: () => console.log('Submitted!')
 *   }
 * };
 *
 * <ComponentRenderer schema={schema} context={context} />
 * ```
 *
 * @example Complex Form
 * ```tsx
 * const schema = {
 *   type: 'Form',
 *   props: { onSubmit: 'handleSubmit' },
 *   children: [
 *     {
 *       type: 'TextField',
 *       props: { label: 'Email', required: true },
 *       dataBinding: { source: 'formData', path: 'email' }
 *     },
 *     {
 *       type: 'Button',
 *       props: { type: 'submit', variant: 'contained' },
 *       children: 'Submit'
 *     }
 *   ]
 * };
 * ```
 */
export const ComponentRenderer: React.FC<ComponentRendererProps> = ({
  schema,
  context = {},
  options = {},
  errorFallback,
}) => {
  const defaultOptions: RendererOptions = {
    strict: false,
    debug: false,
    ...options,
  };

  try {
    if (Array.isArray(schema)) {
      return (
        <>
          {schema.map((item, index) =>
            renderComponent(item, context, defaultOptions, `root-${index}`)
          )}
        </>
      );
    }

    return <>{renderComponent(schema, context, defaultOptions, 'root')}</>;
  } catch (error) {
    console.error('ComponentRenderer error:', error);

    if (errorFallback) {
      return <>{errorFallback}</>;
    }

    return (
      <Alert severity="error">
        Failed to render component: {error instanceof Error ? error.message : 'Unknown error'}
      </Alert>
    );
  }
};

ComponentRenderer.displayName = 'ComponentRenderer';

export default ComponentRenderer;

/**
 * Hook for managing renderer state
 */
export function useRenderer(initialSchema: ComponentSchema | ComponentSchema[]) {
  const [schema, setSchema] = React.useState(initialSchema);
  const [context, setContext] = React.useState<RendererContext>({});

  const updateSchema = React.useCallback((newSchema: ComponentSchema | ComponentSchema[]) => {
    setSchema(newSchema);
  }, []);

  const updateContext = React.useCallback((newContext: Partial<RendererContext>) => {
    setContext((prev) => ({ ...prev, ...newContext }));
  }, []);

  const setData = React.useCallback((data: Record<string, unknown>) => {
    setContext((prev) => ({ ...prev, data }));
  }, []);

  const setHandlers = React.useCallback((handlers: Record<string, (...args: unknown[]) => void>) => {
    setContext((prev) => ({ ...prev, handlers }));
  }, []);

  return {
    schema,
    context,
    updateSchema,
    updateContext,
    setData,
    setHandlers,
  };
}
