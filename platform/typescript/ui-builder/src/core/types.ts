/**
 * @fileoverview Core UI Builder types - BuilderDocument, ComponentInstance, bindings.
 */

import type { ComponentContract } from '@ghatana/ds-schema';
import type { CodeOwnership } from '@ghatana/platform-events';

// ============================================================================
// Core Identifiers
// ============================================================================

/** Branded type for node IDs. */
export type NodeId = string & { readonly __brand: unique symbol };

/** Branded type for document IDs. */
export type DocumentId = string & { readonly __brand: unique symbol };

/** Create a new node ID. */
export function createNodeId(): NodeId {
  return crypto.randomUUID() as NodeId;
}

/** Create a new document ID. */
export function createDocumentId(): DocumentId {
  return crypto.randomUUID() as DocumentId;
}

// ============================================================================
// Component Instance
// ============================================================================

/** A component instance within the builder document. */
export interface ComponentInstance {
  readonly id: NodeId;
  readonly contractName: string;
  readonly props: Record<string, unknown>;
  readonly slots: Record<string, readonly NodeId[]>;
  readonly bindings: readonly Binding[];
  readonly metadata: InstanceMetadata;
}

/** Instance-specific metadata. */
export interface InstanceMetadata {
  readonly name?: string;
  readonly position?: { readonly x: number; readonly y: number };
  readonly size?: { readonly width: number; readonly height: number };
  readonly locked?: boolean;
  readonly hidden?: boolean;
  readonly ownership?: CodeOwnership;
}

// ============================================================================
// Data Bindings
// ============================================================================

/** Types of data bindings. */
export type BindingType = 'data' | 'event' | 'slot' | 'theme' | 'computed';

/** A data binding between a component property and a data source. */
export interface Binding {
  readonly id: string;
  readonly type: BindingType;
  readonly source: string; // e.g., 'dataSource.users', 'theme.colors.primary'
  readonly target: string; // component property path
  readonly transform?: string; // optional transformation expression
  readonly bidirectional?: boolean;
}

// ============================================================================
// Design System Model
// ============================================================================

/** Reference to a design system within the builder. */
export interface DesignSystemModel {
  readonly id: string;
  readonly name: string;
  readonly version: string;
  readonly tokenSetIds: readonly string[];
  readonly componentContracts: readonly ComponentContract[];
  readonly themeId: string;
}

// ============================================================================
// Builder Document
// ============================================================================

/** Root document type for the UI Builder. */
export interface BuilderDocument {
  readonly id: DocumentId;
  readonly version: string;
  readonly name: string;
  readonly designSystem: DesignSystemModel;
  readonly rootNodes: readonly NodeId[];
  readonly nodes: ReadonlyMap<NodeId, ComponentInstance>;
  readonly metadata: DocumentMetadata;
}

/** Document-level metadata. */
export interface DocumentMetadata {
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly author?: string;
  readonly description?: string;
  readonly tags?: readonly string[];
}

// ============================================================================
// Code Projection
// ============================================================================

/** Code representation with ownership markers. */
export interface CodeProjection {
  readonly language: 'typescript' | 'javascript' | 'tsx' | 'jsx';
  readonly files: readonly CodeFile[];
  readonly ownership: readonly CodeOwnership[];
  readonly roundTripFidelity: RoundTripFidelity;
}

/** A single code file. */
export interface CodeFile {
  readonly path: string;
  readonly content: string;
  readonly ownership: CodeOwnership;
}

/** Round-trip fidelity metadata. */
export interface RoundTripFidelity {
  readonly canRoundTrip: boolean;
  readonly lossPoints: readonly LossPoint[];
  readonly confidence: number; // 0-1
}

/** Point where information may be lost in round-trip. */
export interface LossPoint {
  readonly type: 'comment' | 'formatting' | 'import-order' | 'custom-code' | 'unsupported-pattern';
  readonly location?: string;
  readonly description: string;
}

// ============================================================================
// Preview
// ============================================================================

/** Preview host configuration. */
export interface PreviewConfig {
  readonly deviceType: 'desktop' | 'tablet' | 'mobile';
  readonly viewport: { readonly width: number; readonly height: number };
  readonly sandboxProfile: string;
  readonly themeMode: 'light' | 'dark' | 'system';
}

/** Preview host interface. */
export interface PreviewHost {
  readonly id: string;
  readonly url: string;
  readonly config: PreviewConfig;
  readonly status: 'connecting' | 'ready' | 'error';
}

// ============================================================================
// Validation
// ============================================================================

/** Validation result for a document. */
export interface ValidationResult {
  readonly valid: boolean;
  readonly errors: readonly ValidationError[];
  readonly warnings: readonly ValidationWarning[];
}

/** Validation error. */
export interface ValidationError {
  readonly code: string;
  readonly message: string;
  readonly nodeId?: NodeId;
  readonly path?: string;
}

/** Validation warning. */
export interface ValidationWarning {
  readonly code: string;
  readonly message: string;
  readonly nodeId?: NodeId;
  readonly path?: string;
}
