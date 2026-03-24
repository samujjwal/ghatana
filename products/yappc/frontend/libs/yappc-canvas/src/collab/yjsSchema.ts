/**
 * CRDT Synchronization (Feature 3.1)
 * 
 * Provides Yjs document schema validation, path guards, snapshot reloading,
 * and attachment policies for collaborative canvas editing.
 * 
 * Features:
 * - Schema validation for Yjs document paths
 * - Type guards for canonical data structures
 * - Version mismatch detection and resync triggers
 * - Binary attachment policies (reference-only, not embedded)
 * - Invalid path rejection (client/server)
 * 
 * @module collab/yjsSchema
 */

import * as Y from 'yjs';

// ============================================================================
// Types & Interfaces
// ============================================================================

/**
 * Allowed Yjs document paths
 */
export type YjsPath =
  | 'nodes'
  | 'edges'
  | 'viewport'
  | 'selection'
  | 'metadata'
  | 'attachments';

/**
 * Node type definition
 */
export interface YjsNode {
  id: string;
  type: string;
  position: { x: number; y: number };
  data: Record<string, unknown>;
  style?: Record<string, unknown>;
  metadata?: Record<string, unknown>;
}

/**
 * Edge type definition
 */
export interface YjsEdge {
  id: string;
  source: string;
  target: string;
  type?: string;
  data?: Record<string, unknown>;
  style?: Record<string, unknown>;
}

/**
 * Viewport state
 */
export interface YjsViewport {
  x: number;
  y: number;
  zoom: number;
}

/**
 * Selection state
 */
export interface YjsSelection {
  nodes: string[];
  edges: string[];
}

/**
 * Attachment reference (not embedded binary)
 */
export interface YjsAttachment {
  id: string;
  name: string;
  type: string;
  size: number;
  url: string; // Reference to external storage
  uploadedBy: string;
  uploadedAt: number;
  checksum?: string;
}

/**
 * Schema validation result
 */
export interface ValidationResult {
  valid: boolean;
  errors: ValidationError[];
  warnings: ValidationWarning[];
}

/**
 * Validation error
 */
export interface ValidationError {
  path: string;
  type: 'invalid_path' | 'type_mismatch' | 'required_field' | 'constraint_violation' | 'binary_embedded';
  message: string;
  value?: unknown;
}

/**
 * Validation warning
 */
export interface ValidationWarning {
  path: string;
  type: 'deprecated_field' | 'large_payload' | 'missing_optional';
  message: string;
}

/**
 * Document snapshot
 */
export interface YjsSnapshot {
  version: number;
  timestamp: number;
  stateVector: Uint8Array;
  update: Uint8Array;
  checksum: string;
}

/**
 * Schema configuration
 */
export interface YjsSchemaConfig {
  allowedPaths: Set<YjsPath>;
  maxNodeSize: number; // bytes
  maxEdgeSize: number;
  maxAttachmentSize: number;
  maxAttachments: number;
  strictMode: boolean;
  allowBinaryEmbedding: boolean;
  schemaVersion: number;
}

/**
 * Document state
 */
export interface YjsDocumentState {
  nodes: Map<string, YjsNode>;
  edges: Map<string, YjsEdge>;
  viewport: YjsViewport;
  selection: YjsSelection;
  metadata: Record<string, unknown>;
  attachments: Map<string, YjsAttachment>;
}

// ============================================================================
// Schema Validator
// ============================================================================

/**
 *
 */
export class YjsSchemaValidator {
  private config: YjsSchemaConfig;
  
  /**
   *
   */
  constructor(config?: Partial<YjsSchemaConfig>) {
    this.config = this.createDefaultConfig(config);
  }
  
  /**
   * Validate entire Yjs document
   */
  validateDocument(doc: Y.Doc): ValidationResult {
    const errors: ValidationError[] = [];
    const warnings: ValidationWarning[] = [];
    
    // Check for unknown top-level keys
    const docKeys: string[] = [];
    doc.share.forEach((value, key) => {
      // Only consider keys with actual content (non-empty maps)
      if (value instanceof Y.Map && value.size > 0) {
        docKeys.push(key);
      } else if (!(value instanceof Y.Map)) {
        docKeys.push(key);
      }
    });
    
    docKeys.forEach(key => {
      if (!this.config.allowedPaths.has(key as YjsPath)) {
        errors.push({
          path: key,
          type: 'invalid_path',
          message: `Invalid top-level path: ${key}`
        });
      }
    });
    
    // Validate nodes
    const nodesMap = doc.get('nodes') as Y.Map<unknown> | undefined;
    if (nodesMap && nodesMap instanceof Y.Map && nodesMap.size > 0) {
      const nodeErrors = this.validateNodes(nodesMap);
      errors.push(...nodeErrors);
    }
    
    // Validate edges
    const edgesMap = doc.get('edges') as Y.Map<unknown> | undefined;
    if (edgesMap && edgesMap instanceof Y.Map && edgesMap.size > 0) {
      const edgeErrors = this.validateEdges(edgesMap);
      errors.push(...edgeErrors);
    }
    
    // Validate viewport
    const viewportMap = doc.get('viewport') as Y.Map<unknown> | undefined;
    if (viewportMap && viewportMap instanceof Y.Map && viewportMap.size > 0) {
      const viewportErrors = this.validateViewport(viewportMap);
      errors.push(...viewportErrors);
    }
    
    // Validate attachments
    const attachmentsMap = doc.get('attachments') as Y.Map<unknown> | undefined;
    if (attachmentsMap && attachmentsMap instanceof Y.Map && attachmentsMap.size > 0) {
      const attachmentErrors = this.validateAttachments(attachmentsMap);
      errors.push(...attachmentErrors);
    }
    
    return {
      valid: errors.length === 0,
      errors,
      warnings
    };
  }
  
  /**
   * Validate path is allowed
   */
  validatePath(path: string): boolean {
    const topLevel = path.split('.')[0];
    return this.config.allowedPaths.has(topLevel as YjsPath);
  }
  
  /**
   * Validate node structure
   */
  validateNode(node: unknown): ValidationError[] {
    const errors: ValidationError[] = [];
    
    // Required fields
    if (!node.id || typeof node.id !== 'string') {
      errors.push({
        path: 'nodes.*.id',
        type: 'required_field',
        message: 'Node must have a string id',
        value: node.id
      });
    }
    
    if (!node.type || typeof node.type !== 'string') {
      errors.push({
        path: 'nodes.*.type',
        type: 'required_field',
        message: 'Node must have a string type',
        value: node.type
      });
    }
    
    if (!node.position || typeof node.position.x !== 'number' || typeof node.position.y !== 'number') {
      errors.push({
        path: 'nodes.*.position',
        type: 'required_field',
        message: 'Node must have position with x, y coordinates',
        value: node.position
      });
    }
    
    // Check for embedded binaries
    if (this.containsBinary(node)) {
      errors.push({
        path: `nodes.${node.id}`,
        type: 'binary_embedded',
        message: 'Node contains embedded binary data. Use attachment references instead.'
      });
    }
    
    // Size check
    const size = this.estimateSize(node);
    if (size > this.config.maxNodeSize) {
      errors.push({
        path: `nodes.${node.id}`,
        type: 'constraint_violation',
        message: `Node size ${size} exceeds maximum ${this.config.maxNodeSize}`
      });
    }
    
    return errors;
  }
  
  /**
   * Validate edge structure
   */
  validateEdge(edge: unknown): ValidationError[] {
    const errors: ValidationError[] = [];
    
    if (!edge.id || typeof edge.id !== 'string') {
      errors.push({
        path: 'edges.*.id',
        type: 'required_field',
        message: 'Edge must have a string id'
      });
    }
    
    if (!edge.source || typeof edge.source !== 'string') {
      errors.push({
        path: 'edges.*.source',
        type: 'required_field',
        message: 'Edge must have a string source'
      });
    }
    
    if (!edge.target || typeof edge.target !== 'string') {
      errors.push({
        path: 'edges.*.target',
        type: 'required_field',
        message: 'Edge must have a string target'
      });
    }
    
    const size = this.estimateSize(edge);
    if (size > this.config.maxEdgeSize) {
      errors.push({
        path: `edges.${edge.id}`,
        type: 'constraint_violation',
        message: `Edge size ${size} exceeds maximum ${this.config.maxEdgeSize}`
      });
    }
    
    return errors;
  }
  
  /**
   * Validate attachment (must be reference, not embedded)
   */
  validateAttachment(attachment: unknown): ValidationError[] {
    const errors: ValidationError[] = [];
    
    if (!attachment.id || typeof attachment.id !== 'string') {
      errors.push({
        path: 'attachments.*.id',
        type: 'required_field',
        message: 'Attachment must have a string id'
      });
    }
    
    if (!attachment.url || typeof attachment.url !== 'string') {
      errors.push({
        path: 'attachments.*.url',
        type: 'required_field',
        message: 'Attachment must have a URL reference'
      });
    }
    
    if (attachment.data || attachment.buffer || attachment.blob) {
      errors.push({
        path: `attachments.${attachment.id}`,
        type: 'binary_embedded',
        message: 'Binary data must not be embedded. Use URL reference only.'
      });
    }
    
    if (attachment.size > this.config.maxAttachmentSize) {
      errors.push({
        path: `attachments.${attachment.id}`,
        type: 'constraint_violation',
        message: `Attachment size ${attachment.size} exceeds maximum ${this.config.maxAttachmentSize}`
      });
    }
    
    return errors;
  }
  
  // Private validation methods
  
  /**
   *
   */
  private validateNodes(nodesMap: Y.Map<unknown>): ValidationError[] {
    const errors: ValidationError[] = [];
    
    nodesMap.forEach((node, nodeId) => {
      const nodeErrors = this.validateNode(node);
      errors.push(...nodeErrors);
    });
    
    return errors;
  }
  
  /**
   *
   */
  private validateEdges(edgesMap: Y.Map<unknown>): ValidationError[] {
    const errors: ValidationError[] = [];
    
    edgesMap.forEach((edge, edgeId) => {
      const edgeErrors = this.validateEdge(edge);
      errors.push(...edgeErrors);
    });
    
    return errors;
  }
  
  /**
   *
   */
  private validateViewport(viewportMap: Y.Map<unknown>): ValidationError[] {
    const errors: ValidationError[] = [];
    const viewport = viewportMap.toJSON();
    
    if (typeof viewport.x !== 'number') {
      errors.push({
        path: 'viewport.x',
        type: 'type_mismatch',
        message: 'Viewport x must be a number'
      });
    }
    
    if (typeof viewport.y !== 'number') {
      errors.push({
        path: 'viewport.y',
        type: 'type_mismatch',
        message: 'Viewport y must be a number'
      });
    }
    
    if (typeof viewport.zoom !== 'number' || viewport.zoom <= 0) {
      errors.push({
        path: 'viewport.zoom',
        type: 'constraint_violation',
        message: 'Viewport zoom must be a positive number'
      });
    }
    
    return errors;
  }
  
  /**
   *
   */
  private validateAttachments(attachmentsMap: Y.Map<unknown>): ValidationError[] {
    const errors: ValidationError[] = [];
    
    if (attachmentsMap.size > this.config.maxAttachments) {
      errors.push({
        path: 'attachments',
        type: 'constraint_violation',
        message: `Too many attachments: ${attachmentsMap.size} > ${this.config.maxAttachments}`
      });
    }
    
    attachmentsMap.forEach((attachment, attachmentId) => {
      const attachmentErrors = this.validateAttachment(attachment);
      errors.push(...attachmentErrors);
    });
    
    return errors;
  }
  
  /**
   *
   */
  private containsBinary(obj: unknown): boolean {
    if (obj instanceof Uint8Array || obj instanceof ArrayBuffer) {
      return true;
    }
    
    if (typeof obj === 'object' && obj !== null) {
      for (const key in obj) {
        if (this.containsBinary(obj[key])) {
          return true;
        }
      }
    }
    
    return false;
  }
  
  /**
   *
   */
  private estimateSize(obj: unknown): number {
    return JSON.stringify(obj).length;
  }
  
  /**
   *
   */
  private createDefaultConfig(overrides?: Partial<YjsSchemaConfig>): YjsSchemaConfig {
    return {
      allowedPaths: new Set<YjsPath>(['nodes', 'edges', 'viewport', 'selection', 'metadata', 'attachments']),
      maxNodeSize: 100 * 1024, // 100KB
      maxEdgeSize: 50 * 1024, // 50KB
      maxAttachmentSize: 10 * 1024 * 1024, // 10MB
      maxAttachments: 100,
      strictMode: true,
      allowBinaryEmbedding: false,
      schemaVersion: 1,
      ...overrides
    };
  }
}

// ============================================================================
// Snapshot Manager
// ============================================================================

/**
 *
 */
export class YjsSnapshotManager {
  private snapshots: Map<number, YjsSnapshot> = new Map();
  private maxSnapshots: number;
  
  /**
   *
   */
  constructor(maxSnapshots: number = 10) {
    this.maxSnapshots = maxSnapshots;
  }
  
  /**
   * Create snapshot of current document state
   */
  createSnapshot(doc: Y.Doc): YjsSnapshot {
    const stateVector = Y.encodeStateVector(doc);
    const update = Y.encodeStateAsUpdate(doc);
    const version = this.snapshots.size;
    
    const snapshot: YjsSnapshot = {
      version,
      timestamp: Date.now(),
      stateVector,
      update,
      checksum: this.calculateChecksum(update)
    };
    
    this.snapshots.set(version, snapshot);
    
    // Trim old snapshots
    if (this.snapshots.size > this.maxSnapshots) {
      const oldestVersion = Math.min(...this.snapshots.keys());
      this.snapshots.delete(oldestVersion);
    }
    
    return snapshot;
  }
  
  /**
   * Restore document from snapshot
   */
  restoreSnapshot(doc: Y.Doc, version: number): boolean {
    const snapshot = this.snapshots.get(version);
    if (!snapshot) {
      return false;
    }
    
    // Verify checksum
    const currentChecksum = this.calculateChecksum(snapshot.update);
    if (currentChecksum !== snapshot.checksum) {
      throw new Error('Snapshot checksum mismatch - data corruption detected');
    }
    
    // Apply snapshot
    Y.applyUpdate(doc, snapshot.update);
    return true;
  }
  
  /**
   * Check if version mismatch requires resync
   */
  requiresResync(doc: Y.Doc, remoteVersion: number): boolean {
    const localSnapshot = this.snapshots.get(remoteVersion);
    if (!localSnapshot) {
      return true;
    }
    
    const currentStateVector = Y.encodeStateVector(doc);
    const snapshotStateVector = localSnapshot.stateVector;
    
    // Compare state vectors
    return !this.areStateVectorsEqual(currentStateVector, snapshotStateVector);
  }
  
  /**
   * Get snapshot by version
   */
  getSnapshot(version: number): YjsSnapshot | undefined {
    return this.snapshots.get(version);
  }
  
  /**
   * Get all snapshots
   */
  getAllSnapshots(): YjsSnapshot[] {
    return Array.from(this.snapshots.values()).sort((a, b) => b.version - a.version);
  }
  
  /**
   * Clear all snapshots
   */
  clearSnapshots(): void {
    this.snapshots.clear();
  }
  
  /**
   *
   */
  private calculateChecksum(data: Uint8Array): string {
    // Simple hash for now - in production use crypto hash
    let hash = 0;
    for (let i = 0; i < data.length; i++) {
      hash = ((hash << 5) - hash) + data[i];
      hash = hash & hash; // Convert to 32bit integer
    }
    return hash.toString(36);
  }
  
  /**
   *
   */
  private areStateVectorsEqual(a: Uint8Array, b: Uint8Array): boolean {
    if (a.length !== b.length) return false;
    for (let i = 0; i < a.length; i++) {
      if (a[i] !== b[i]) return false;
    }
    return true;
  }
}

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Extract document state as plain objects
 */
export function extractDocumentState(doc: Y.Doc): YjsDocumentState {
  const nodesMap = doc.getMap('nodes');
  const edgesMap = doc.getMap('edges');
  const viewportMap = doc.getMap('viewport');
  const selectionMap = doc.getMap('selection');
  const metadataMap = doc.getMap('metadata');
  const attachmentsMap = doc.getMap('attachments');
  
  const state: YjsDocumentState = {
    nodes: new Map(),
    edges: new Map(),
    viewport: { x: 0, y: 0, zoom: 1 },
    selection: { nodes: [], edges: [] },
    metadata: {},
    attachments: new Map()
  };
  
  nodesMap?.forEach((node, id) => {
    state.nodes.set(id, node as YjsNode);
  });
  
  edgesMap?.forEach((edge, id) => {
    state.edges.set(id, edge as YjsEdge);
  });
  
  if (viewportMap && viewportMap.size > 0) {
    const viewportData = viewportMap.toJSON() as YjsViewport;
    state.viewport = viewportData;
  }
  
  if (selectionMap && selectionMap.size > 0) {
    const selectionData = selectionMap.toJSON() as YjsSelection;
    state.selection = selectionData;
  }
  
  if (metadataMap && metadataMap.size > 0) {
    state.metadata = metadataMap.toJSON();
  }
  
  attachmentsMap?.forEach((attachment, id) => {
    state.attachments.set(id, attachment as YjsAttachment);
  });
  
  return state;
}

/**
 * Create validator with default config
 */
export function createYjsValidator(config?: Partial<YjsSchemaConfig>): YjsSchemaValidator {
  return new YjsSchemaValidator(config);
}

/**
 * Create snapshot manager
 */
export function createSnapshotManager(maxSnapshots?: number): YjsSnapshotManager {
  return new YjsSnapshotManager(maxSnapshots);
}
