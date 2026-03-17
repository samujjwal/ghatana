/**
 * Unified registry type definitions for YAPPC App Creator.
 *
 * <p><b>Purpose</b><br>
 * Provides canonical type definitions for all registry systems (ComponentRegistry,
 * SchemaRegistry, UnifiedRegistry, etc.). This is the single source of truth for
 * registry-related types to eliminate duplication across modules.
 *
 * <p><b>Architecture Role</b><br>
 * Part of the core type system that ensures type consistency across:
 * - Component registration and discovery
 * - Schema validation and versioning
 * - Template management
 * - Plugin registration
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { RegistryEntry, RegistryFilter, ValidationResult } from '@ghatana/yappc-types/registry';
 *
 * const entry: RegistryEntry<ComponentDefinition> = {
 *   key: 'namespace:component-id',
 *   value: componentDef,
 *   namespace: 'custom-components',
 *   registeredAt: new Date().toISOString(),
 *   updatedAt: new Date().toISOString()
 * };
 * }</pre>
 *
 * @doc.type module
 * @doc.purpose Unified registry type definitions
 * @doc.layer product
 * @doc.pattern Value Object
 */

import type { z } from 'zod';
import type React from 'react';

// ============================================================================
// CORE REGISTRY TYPES
// ============================================================================

/**
 * Represents an entry in any registry system.
 *
 * <p><b>Purpose</b><br>
 * Provides consistent metadata wrapper for registered items across all registry types.
 * Enables tracking, versioning, and auditing of registered entities.
 *
 * <p><b>Thread Safety</b><br>
 * Immutable value object - thread-safe for concurrent reads.
 *
 * @typeParam T - The type of the registered value
 *
 * @doc.type interface
 * @doc.purpose Generic registry entry wrapper
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface RegistryEntry<T> {
  /** Unique key for this entry (typically "namespace:id" format) */
  key: string;
  /** The registered value */
  value: T;
  /** Namespace for this entry (enables multi-tenancy and scoping) */
  namespace: string;
  /** ISO timestamp when entry was registered */
  registeredAt: string;
  /** ISO timestamp when entry was last updated */
  updatedAt: string;
  /** Optional metadata for extensions */
  metadata?: RegistryEntryMetadata;
}

/**
 * Standard metadata fields for registry entries.
 *
 * <p><b>Purpose</b><br>
 * Provides extensible metadata for registry entries including versioning,
 * deprecation status, and custom fields.
 *
 * @doc.type interface
 * @doc.purpose Registry entry metadata
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface RegistryEntryMetadata {
  /** Author of the registered item */
  author?: string;
  /** Version of the registered item */
  version?: string;
  /** Tags for categorization and search */
  tags?: string[];
  /** Whether this entry is deprecated */
  deprecated?: boolean;
  /** Path to migration guide if deprecated */
  migrationPath?: string;
  /** ID of the item this migrated from */
  migratedFrom?: string;
  /** Original ID before migration */
  originalId?: string;
  /** ISO timestamp of migration */
  migrationDate?: string;
  /** Custom metadata fields */
  [key: string]: unknown;
}

/**
 * Filter function for querying registry entries.
 *
 * <p><b>Purpose</b><br>
 * Enables type-safe filtering of registry entries with full type information.
 *
 * @typeParam T - The type of the registered value
 *
 * @doc.type type
 * @doc.purpose Registry filter predicate
 * @doc.layer product
 * @doc.pattern Strategy
 */
export type RegistryFilter<T> = (entry: RegistryEntry<T>) => boolean;

/**
 * Comparator function for sorting registry entries.
 *
 * <p><b>Purpose</b><br>
 * Enables type-safe sorting of registry entries.
 *
 * @typeParam T - The type of the registered value
 *
 * @doc.type type
 * @doc.purpose Registry comparator
 * @doc.layer product
 * @doc.pattern Strategy
 */
export type RegistryComparator<T> = (a: RegistryEntry<T>, b: RegistryEntry<T>) => number;

/**
 * Search query for registry lookups.
 *
 * <p><b>Purpose</b><br>
 * Provides structured query interface for searching across registry entries
 * with support for text search, filtering, and pagination.
 *
 * @doc.type interface
 * @doc.purpose Registry search query
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface RegistrySearchQuery {
  /** Free-text search string */
  text?: string;
  /** Filter by namespace */
  namespace?: string;
  /** Filter by category */
  category?: string;
  /** Filter by tags (OR semantics) */
  tags?: string[];
  /** Filter by type */
  type?: string;
  /** Maximum number of results to return */
  limit?: number;
  /** Offset for pagination */
  offset?: number;
  /** Whether to include deprecated entries */
  includeDeprecated?: boolean;
}

/**
 * Result of a validation operation.
 *
 * <p><b>Purpose</b><br>
 * Provides consistent validation result format across all registry systems
 * with support for errors and warnings.
 *
 * @doc.type interface
 * @doc.purpose Validation result
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface ValidationResult {
  /** Whether validation passed */
  valid: boolean;
  /** Validation errors (blocking) */
  errors?: string[];
  /** Validation warnings (non-blocking) */
  warnings?: string[];
}

/**
 * Hook for migrating data between versions.
 *
 * <p><b>Purpose</b><br>
 * Enables schema evolution and data migration with version tracking.
 *
 * @doc.type interface
 * @doc.purpose Migration hook
 * @doc.layer product
 * @doc.pattern Strategy
 */
export interface MigrationHook {
  /** Source version */
  fromVersion: string;
  /** Target version */
  toVersion: string;
  /** Migration function (should be pure and idempotent) */
  migrate: (data: unknown) => unknown;
  /** Optional description of changes */
  description?: string;
}

// ============================================================================
// COMPONENT REGISTRY TYPES
// ============================================================================

/**
 * Definition for a registerable component.
 *
 * <p><b>Purpose</b><br>
 * Canonical structure for component definitions across page builder, canvas,
 * and design system. Ensures consistent component metadata and behavior.
 *
 * <p><b>Architecture Role</b><br>
 * Core domain entity for component-based systems. Used by:
 * - Component palette for drag-and-drop
 * - Code generation for exports
 * - Property panels for editing
 * - Type validation for schemas
 *
 * @doc.type interface
 * @doc.purpose Component definition
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface ComponentDefinition {
  /** Unique identifier (should be namespaced) */
  id: string;
  /** Component type (Button, Input, Card, etc.) */
  type: string;
  /** Category for grouping (Layout, Form, Data Display, etc.) */
  category: string;
  /** Display label */
  label: string;
  /** Human-readable description */
  description: string;
  /** Icon identifier or React element */
  icon?: string | React.ReactNode;
  /** Component version (semantic versioning recommended) */
  version?: string;
  /** Reference to Zod schema for validation */
  dataSchemaRef?: string;
  /** Default data values */
  defaultData?: Record<string, unknown>;
  /** Default props */
  defaultProps?: Record<string, unknown>;
  /** Component props definition */
  props?: Record<string, unknown>;
  /** React component implementation */
  component?: React.ComponentType<unknown>;
  /** Tags for search and categorization */
  tags?: string[];
  /** Whether this component is deprecated */
  deprecated?: boolean;
  /** Path to migration guide if deprecated */
  migrationPath?: string;
  /** Extended metadata */
  metadata?: RegistryEntryMetadata;
}

/**
 * Category for grouping components.
 *
 * <p><b>Purpose</b><br>
 * Standard component categories for consistent organization across UIs.
 *
 * @doc.type type
 * @doc.purpose Component category classification
 * @doc.layer product
 * @doc.pattern Value Object
 */
export type ComponentCategory =
  | 'Layout'
  | 'Typography'
  | 'Form'
  | 'Input'
  | 'Button'
  | 'Data Display'
  | 'Feedback'
  | 'Navigation'
  | 'Surface'
  | 'Media'
  | 'Chart'
  | 'Diagram'
  | 'Custom';

// ============================================================================
// SCHEMA REGISTRY TYPES
// ============================================================================

/**
 * Definition for a registerable Zod schema.
 *
 * <p><b>Purpose</b><br>
 * Enables schema versioning, validation, and migration for component data,
 * API contracts, and domain models.
 *
 * @doc.type interface
 * @doc.purpose Schema definition
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface SchemaDefinition {
  /** Unique schema identifier */
  id: string;
  /** Schema name */
  name: string;
  /** Schema version (semantic versioning) */
  version: string;
  /** Zod schema instance */
  schema: z.ZodSchema;
  /** Optional description */
  description?: string;
  /** Whether this schema is deprecated */
  deprecated?: boolean;
  /** Path to migration guide if deprecated */
  migrationPath?: string;
  /** Extended metadata */
  metadata?: RegistryEntryMetadata;
}

// ============================================================================
// TEMPLATE REGISTRY TYPES
// ============================================================================

/**
 * Definition for a registerable template.
 *
 * <p><b>Purpose</b><br>
 * Enables reusable page/component templates for rapid prototyping and
 * consistent design patterns.
 *
 * @doc.type interface
 * @doc.purpose Template definition
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface TemplateDefinition {
  /** Unique template identifier */
  id: string;
  /** Template name */
  name: string;
  /** Template description */
  description?: string;
  /** Template category */
  category: string;
  /** Preview image URL */
  previewUrl?: string;
  /** Template structure (component tree or page definition) */
  structure: unknown;
  /** Template tags */
  tags?: string[];
  /** Whether this template is deprecated */
  deprecated?: boolean;
  /** Extended metadata */
  metadata?: RegistryEntryMetadata;
}

// ============================================================================
// PLUGIN REGISTRY TYPES
// ============================================================================

/**
 * Definition for a registerable plugin.
 *
 * <p><b>Purpose</b><br>
 * Enables extensibility through plugin architecture with lifecycle management.
 *
 * @doc.type interface
 * @doc.purpose Plugin definition
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface PluginDefinition {
  /** Unique plugin identifier */
  id: string;
  /** Plugin name */
  name: string;
  /** Plugin description */
  description?: string;
  /** Plugin version */
  version: string;
  /** Plugin author */
  author?: string;
  /** Required dependencies */
  dependencies?: string[];
  /** Plugin initialization function */
  initialize?: () => void | Promise<void>;
  /** Plugin cleanup function */
  dispose?: () => void | Promise<void>;
  /** Extended metadata */
  metadata?: RegistryEntryMetadata;
}

// ============================================================================
// REGISTRY STATISTICS
// ============================================================================

/**
 * Statistics about registry contents.
 *
 * <p><b>Purpose</b><br>
 * Provides insights into registry size, distribution, and health.
 *
 * @doc.type interface
 * @doc.purpose Registry statistics
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface RegistryStatistics {
  /** Total number of registered entries */
  totalEntries: number;
  /** Number of unique namespaces */
  namespaces: number;
  /** Number of unique categories */
  categories: number;
  /** Number of unique types */
  types: number;
  /** Number of unique tags */
  tags: number;
  /** Number of deprecated entries */
  deprecated?: number;
  /** Last update timestamp */
  lastUpdated?: string;
}
