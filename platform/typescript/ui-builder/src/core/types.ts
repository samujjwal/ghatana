/**
 * @fileoverview Core UI Builder types - BuilderDocument, ComponentInstance, bindings.
 */

import type { ComponentContract } from '@ghatana/ds-schema';
import type {
  AIChangeDescriptor,
  CodeOwnership,
  DataClassification,
  PrivacyMetadata,
  ProvenanceRecord,
  SyncStatus,
  TrustLevel,
  VisibilityContract,
} from '@ghatana/platform-events';

// ============================================================================
// Core Identifiers
// ============================================================================

/** Branded type for node IDs. */
export type NodeId = string & { readonly __brand: unique symbol };

/** Branded type for document IDs. */
export type DocumentId = string & { readonly __brand: unique symbol };

/**
 * Create a new node ID.
 *
 * @param seed - Optional deterministic seed (for testing / migration).
 *   When provided, the seed is incorporated verbatim (must be a valid UUID or
 *   a short ASCII handle).  When omitted, a fresh UUID is generated.
 */
export function createNodeId(seed?: string): NodeId {
  return (seed ?? crypto.randomUUID()) as NodeId;
}

/** Create a new document ID. */
export function createDocumentId(): DocumentId {
  return crypto.randomUUID() as DocumentId;
}

// ============================================================================
// Layout Constraints
// ============================================================================

/** Axis-aligned layout constraints for a node. */
export interface LayoutConstraints {
  /** Minimum/maximum size bounds (CSS-compatible string or pixel number). */
  readonly minWidth?: number | string;
  readonly maxWidth?: number | string;
  readonly minHeight?: number | string;
  readonly maxHeight?: number | string;
  /** Aspect ratio lock (width / height). */
  readonly aspectRatio?: number;
  /** Whether the node can be resized by the editor. */
  readonly resizable: boolean;
  /** Whether the node can be repositioned independently. */
  readonly positionable: boolean;
  /** Overflow behaviour visible to the canvas editor. */
  readonly overflow?: 'visible' | 'hidden' | 'scroll' | 'auto';
}

// ============================================================================
// Responsive Variants
// ============================================================================

/** A named responsive breakpoint override for a node's props/position/size. */
export interface ResponsiveVariant {
  readonly breakpoint: string; // e.g. 'sm', 'md', 'lg', 'xl'
  readonly minWidth?: number;
  readonly maxWidth?: number;
  /** Partial overrides applied at this breakpoint. */
  readonly props?: Record<string, unknown>;
  readonly hidden?: boolean;
  readonly position?: { readonly x: number; readonly y: number };
  readonly size?: { readonly width: number; readonly height: number };
}

// ============================================================================
// State Variants
// ============================================================================

/** Interactive state variant (hover, focus, active, disabled, etc.). */
export interface StateVariant {
  readonly state: 'hover' | 'focus' | 'active' | 'disabled' | 'error' | 'loading' | 'selected';
  readonly props?: Record<string, unknown>;
}

// ============================================================================
// Action / Event Wiring
// ============================================================================

/** Action target kinds. */
export type ActionTargetKind =
  | 'navigate'
  | 'toggle-state'
  | 'emit-event'
  | 'call-api'
  | 'update-binding'
  | 'custom';

/** A single action definition attached to a component event. */
export interface ActionDefinition {
  readonly id: string;
  readonly label?: string;
  /** Component event that triggers this action (e.g. 'onClick', 'onChange'). */
  readonly triggerEvent: string;
  readonly targetKind: ActionTargetKind;
  /** Payload interpolation template or static value. */
  readonly payload?: Record<string, unknown>;
  /** Conditions under which action fires (expression string). */
  readonly condition?: string;
}

// ============================================================================
// Review & Approval
// ============================================================================

export type ReviewStatusKind = 'none' | 'pending' | 'approved' | 'rejected' | 'requires-manual';

export interface ReviewStatus {
  readonly status: ReviewStatusKind;
  readonly reviewedBy?: string;
  readonly reviewedAt?: string;
  readonly notes?: string;
}

// ============================================================================
// AI Change Lineage
// ============================================================================

/** A lightweight record of an AI-initiated change on a node or document. */
export interface AIChangeRecord {
  readonly changeId: string;
  readonly timestamp: string;
  readonly descriptor: AIChangeDescriptor;
  readonly reviewStatus: ReviewStatus;
}

// ============================================================================
// Component Instance
// ============================================================================

/** A component instance within the builder document. */
export interface ComponentInstance {
  readonly id: NodeId;
  readonly contractName: string;
  readonly props: Record<string, unknown>;
  readonly slots: Record<string, NodeId[]>;
  readonly bindings: Binding[];
  readonly metadata: InstanceMetadata;
}

/** Instance-specific metadata. */
export interface InstanceMetadata {
  readonly name?: string;
  readonly position?: { readonly x: number; readonly y: number };
  readonly size?: { readonly width: number; readonly height: number };
  readonly locked?: boolean;
  readonly hidden?: boolean;
  readonly ownership?: Record<string, unknown>;
  /** Layout constraints for the canvas editor. */
  readonly layout?: LayoutConstraints;
  /** Per-breakpoint prop/size/position overrides. */
  readonly responsiveVariants?: ResponsiveVariant[];
  /** Interactive state overrides. */
  readonly stateVariants?: StateVariant[];
  /** Wired action definitions for component events. */
  readonly actions?: ActionDefinition[];
  /** Review / approval gate for this node. */
  readonly reviewStatus?: ReviewStatus;
  /** Pending changes not yet persisted or approved. */
  readonly pendingProps?: Record<string, unknown>;
  /** Privacy / data classification of props. */
  readonly privacyMetadata?: PrivacyMetadata;
  /** Data sensitivity classification. */
  readonly dataClassification?: Record<string, unknown>;
  /** AI-initiated changes affecting this node. */
  readonly aiLineage?: AIChangeRecord[];
  /** Collaboration session node identity (for CRDT merging). */
  readonly collaborationId?: string;
  /** Provenance record (who created / last modified this node). */
  readonly provenance?: ProvenanceRecord;
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

/**
 * BuilderDocument is now defined canonically in builder-document.ts with full Zod schema validation.
 * This file only exports supporting types. Import BuilderDocument from './builder-document' instead.
 *
 * @deprecated Import BuilderDocument from './builder-document' for the canonical schema-based definition.
 */

/** Document-level metadata. */
export interface DocumentMetadata {
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly author?: string;
  readonly description?: string;
  readonly tags?: readonly string[];
  /** Monotonically increasing change counter for optimistic concurrency. */
  readonly changeCount?: number;
  /** Collaboration session version (Yjs / CRDT clock). */
  readonly collaborationVersion?: number;
  /** Checkpoint ID for save / resume workflows. */
  readonly checkpointId?: string;
  /** Document-level privacy / data classification. */
  readonly dataClassification?: DataClassification;
  /** Document-level review / approval gate. */
  readonly reviewStatus?: ReviewStatus;
  /** Current sync status with remote persistence. */
  readonly syncStatus?: SyncStatus;
  /** Visibility contract for this document's authoring session. */
  readonly visibilityContract?: VisibilityContract;
  /** Trust level governing preview and execution. */
  readonly trustLevel?: TrustLevel;
}

// ============================================================================
// Code Projection
// ============================================================================

/**
 * Ownership marker for a region within a generated code file.
 * Distinct from {@link CodeOwnership} (platform string enum for node-level authorship).
 */
export interface CodeRegionOwnership {
  readonly region: string;
  readonly type: 'builder-generated' | 'user-authored' | 'protected';
  readonly lineStart: number;
  readonly lineEnd: number;
  readonly builderNodeIds: readonly NodeId[];
}

/** Code representation with ownership markers. */
export interface CodeProjection {
  readonly language: 'typescript' | 'javascript' | 'tsx' | 'jsx';
  readonly files: readonly CodeFile[];
  readonly ownership: readonly CodeRegionOwnership[];
  readonly roundTripFidelity: RoundTripFidelity;
}

/** A single code file. */
export interface CodeFile {
  readonly path: string;
  readonly content: string;
  readonly ownership: CodeRegionOwnership;
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
