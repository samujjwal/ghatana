/**
 * Template Definition Types
 *
 * Types and interfaces for canvas templates.
 *
 * @module canvas/templates/TemplateDefinition
 */

import type { ComponentSchema } from '../types/ComponentSchema';

// ============================================================================
// Types
// ============================================================================

/**
 *
 */
export type TemplateCategory =
  | 'authentication'
  | 'dashboard'
  | 'form'
  | 'data-display'
  | 'navigation'
  | 'settings'
  | 'custom';

/**
 *
 */
export interface TemplateNode {
  id: string;
  componentType: string;
  schema: ComponentSchema;
  position: { x: number; y: number };
  children?: string[];
}

/**
 *
 */
export interface TemplateDefinition {
  /**
   * Template ID
   */
  id: string;

  /**
   * Template name
   */
  name: string;

  /**
   * Template description
   */
  description: string;

  /**
   * Template category
   */
  category: TemplateCategory;

  /**
   * Template tags for search
   */
  tags: string[];

  /**
   * Preview image URL
   */
  preview?: string;

  /**
   * Template nodes
   */
  nodes: TemplateNode[];

  /**
   * Data bindings between nodes
   */
  bindings?: Array<{
    sourceNodeId: string;
    targetNodeId: string;
    sourcePath: string;
    targetProp: string;
    mode: 'one-way' | 'two-way' | 'one-time' | 'expression';
  }>;

  /**
   * Event connections between nodes
   */
  events?: Array<{
    sourceNodeId: string;
    sourceEvent: string;
    targetNodeId: string;
    targetEvent: string;
    payload?: Record<string, unknown>;
  }>;

  /**
   * Template metadata
   */
  metadata: {
    author?: string;
    version: string;
    createdAt: Date;
    updatedAt: Date;
    usageCount?: number;
  };
}

/**
 *
 */
export interface TemplateInstantiationOptions {
  /**
   * Starting position for template
   */
  position?: { x: number; y: number };

  /**
   * Override props for specific nodes
   */
  nodeOverrides?: Record<string, Partial<ComponentSchema>>;

  /**
   * Theme to apply
   */
  theme?: 'base' | 'brand' | 'workspace' | 'app';

  /**
   * Generate unique IDs
   */
  generateIds?: boolean;
}

/**
 *
 */
export interface InstantiatedTemplate {
  nodes: TemplateNode[];
  bindings: TemplateDefinition['bindings'];
  events: TemplateDefinition['events'];
  rootNodeId: string;
}
