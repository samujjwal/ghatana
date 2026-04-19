/**
 * PageConfig Schema
 *
 * Declarative configuration for a page/layout with components, data bindings,
 * actions, and connections.
 *
 * @packageDocumentation
 */

import { z } from 'zod';

// Re-use existing ComponentSchema from yappc-ui
// Import from ComponentRenderer to avoid duplication
// This is a Zod schema, so we need to define it here for validation

/**
 * ComponentInstance - represents a component instance in the page
 * This aligns with ComponentSchema from ComponentRenderer
 */
export const ComponentInstanceSchema = z.object({
  type: z.string(),
  props: z.record(z.string(), z.unknown()).optional(),
  children: z
    .lazy(() => z.array(z.any()))
    .or(z.string())
    .optional(),
  id: z.string().optional(),
  condition: z.string().optional(),
  dataBinding: z
    .object({
      source: z.string(),
      path: z.string().optional(),
      transform: z.string().optional(),
    })
    .optional(),
  events: z.record(z.string(), z.string()).optional(),
});

/**
 * ValidationRule schema
 */
export const ValidationRuleSchema = z.object({
  type: z.enum(['min', 'max', 'pattern', 'custom']),
  value: z.unknown().optional(),
  message: z.string(),
});

/**
 * PropertyDefinition schema
 */
export const PropertyDefinitionSchema = z.object({
  type: z.enum(['string', 'number', 'boolean', 'object', 'array']),
  description: z.string().optional(),
  required: z.boolean().optional(),
  validation: z.array(ValidationRuleSchema).optional(),
});

/**
 * InterfaceDefinition schema
 */
export const InterfaceDefinitionSchema = z.object({
  id: z.string(),
  name: z.string(),
  type: z.enum(['input', 'output', 'api', 'event']),
  schema: z.object({
    type: z.enum(['object', 'array', 'string', 'number', 'boolean']),
    properties: z.record(z.string(), PropertyDefinitionSchema).optional(),
    required: z.array(z.string()).optional(),
  }),
  validation: z.object({
    rules: z.array(ValidationRuleSchema),
  }),
  description: z.string(),
  examples: z.array(z.unknown()),
});

/**
 * EventConnection schema
 */
export const EventConnectionSchema = z.object({
  id: z.string(),
  sourceComponentId: z.string(),
  sourceEvent: z.string(),
  targetComponentId: z.string(),
  targetAction: z.string(),
  transform: z.string().optional(),
  condition: z.string().optional(),
});

/**
 * DataConnection schema
 */
export const DataConnectionSchema = z.object({
  id: z.string(),
  sourceId: z.string(),
  sourcePath: z.string(),
  targetComponentId: z.string(),
  targetProp: z.string(),
  transform: z.string().optional(),
  mode: z.enum(['one-way', 'two-way', 'one-time']),
});

/**
 * NavigationConnection schema
 */
export const NavigationConnectionSchema = z.object({
  id: z.string(),
  sourceComponentId: z.string(),
  sourceEvent: z.string(),
  targetPageId: z.string(),
  targetRoute: z.string(),
  params: z.record(z.string(), z.string()).optional(),
});

/**
 * ConnectionDefinition schema (union type)
 */
export const ConnectionDefinitionSchema = z.union([
  EventConnectionSchema,
  DataConnectionSchema,
  NavigationConnectionSchema,
]);

/**
 * DataSourceConfig schema
 */
export const DataSourceConfigSchema = z.object({
  id: z.string(),
  type: z.enum(['api', 'database', 'static', 'mock']),
  config: z.record(z.string(), z.unknown()),
});

/**
 * DataBindingConfig schema
 */
export const DataBindingConfigSchema = z.object({
  sourceId: z.string(),
  sourcePath: z.string(),
  targetComponentId: z.string(),
  targetProp: z.string(),
  transform: z.string().optional(),
  mode: z.enum(['one-way', 'two-way', 'one-time']),
});

/**
 * ResponsiveConfig schema
 */
export const ResponsiveConfigSchema = z.object({
  breakpoint: z.string(),
  config: z.record(z.string(), z.unknown()),
});

/**
 * PageConfig schema - main schema for declarative page configuration
 */
export const PageConfigSchema = z
  .object({
    id: z.string(),
    version: z.string(),

    // Intent linkage
    intentId: z.string().optional(),
    requirementIds: z.array(z.string()),

    // Page metadata
    title: z.string(),
    description: z.string(),
    route: z.string(),
    layout: z.enum(['canvas', 'grid', 'flex', 'sidebar']),

    // Layout configuration
    layoutConfig: z.object({
      template: z.string().optional(),
      responsiveBreakpoints: z.array(ResponsiveConfigSchema),
    }),

    // Components (from @ghatana/ui-builder ComponentInstance)
    components: z.array(ComponentInstanceSchema),

    // Data sources and bindings
    data: z.object({
      sources: z.array(DataSourceConfigSchema),
      bindings: z.array(DataBindingConfigSchema),
    }),

    // Actions (from @ghatana/ui-builder ActionDefinition)
    // For now, we use a placeholder - will integrate with @ghatana/ui-builder types
    actions: z.array(z.record(z.string(), z.unknown())),

    // Connections and wiring
    connections: z.object({
      events: z.array(EventConnectionSchema),
      data: z.array(DataConnectionSchema),
      navigation: z.array(NavigationConnectionSchema),
    }),

    // Contracts and interfaces
    contracts: z.object({
      inputs: z.array(InterfaceDefinitionSchema),
      outputs: z.array(InterfaceDefinitionSchema),
    }),

    // Permissions
    permissions: z.object({
      view: z.array(z.string()),
      edit: z.array(z.string()),
      delete: z.array(z.string()),
    }),

    // Localization
    i18n: z.object({
      defaultLocale: z.string(),
      supportedLocales: z.array(z.string()),
      translations: z.record(z.string(), z.record(z.string(), z.string())),
    }),

    // Metadata
    createdAt: z.string(),
    updatedAt: z.string(),
    author: z.string(),
    tags: z.array(z.string()),
  })
  .strict();

/**
 * Type inference from PageConfig schema
 */
export type PageConfig = z.infer<typeof PageConfigSchema>;

/**
 * Type inference from ComponentInstance schema
 */
export type ComponentInstance = z.infer<typeof ComponentInstanceSchema>;

/**
 * Type inference from InterfaceDefinition schema
 */
export type InterfaceDefinitionInferred = z.infer<
  typeof InterfaceDefinitionSchema
>;

/**
 * Type inference from EventConnection schema
 */
export type EventConnectionInferred = z.infer<typeof EventConnectionSchema>;

/**
 * Type inference from DataConnection schema
 */
export type DataConnectionInferred = z.infer<typeof DataConnectionSchema>;

/**
 * Type inference from NavigationConnection schema
 */
export type NavigationConnectionInferred = z.infer<
  typeof NavigationConnectionSchema
>;
