/**
 * Tests for Yjs Schema Validator (Feature 3.1)
 * 
 * Tests CRDT synchronization with schema validation, path guards,
 * snapshot management, and attachment policies.
 */

import { describe, it, expect, beforeEach } from 'vitest';
import * as Y from 'yjs';

import {
  YjsSchemaValidator,
  createYjsValidator,
  createSnapshotManager,
  extractDocumentState,
  type YjsNode,
  type YjsEdge,
  type YjsAttachment
,
  YjsSnapshotManager} from '../yjsSchema';

// ============================================================================
// Test Helpers
// ============================================================================

function createTestDoc(): Y.Doc {
  return new Y.Doc();
}

function addTestNode(doc: Y.Doc, node: YjsNode): void {
  const nodesMap = doc.getMap('nodes');
  nodesMap.set(node.id, node);
}

function addTestEdge(doc: Y.Doc, edge: YjsEdge): void {
  const edgesMap = doc.getMap('edges');
  edgesMap.set(edge.id, edge);
}

// ============================================================================
// Schema Validator Tests
// ============================================================================

describe('Yjs Schema - Validator Basics', () => {
  let validator: YjsSchemaValidator;
  let doc: Y.Doc;
  
  beforeEach(() => {
    validator = createYjsValidator();
    doc = createTestDoc();
  });
  
  it('should create validator with default config', () => {
    expect(validator).toBeInstanceOf(YjsSchemaValidator);
  });
  
  it('should validate empty document', () => {
    const result = validator.validateDocument(doc);
    
    expect(result.valid).toBe(true);
    expect(result.errors).toHaveLength(0);
  });
  
  it('should validate allowed paths', () => {
    expect(validator.validatePath('nodes')).toBe(true);
    expect(validator.validatePath('edges')).toBe(true);
    expect(validator.validatePath('viewport')).toBe(true);
    expect(validator.validatePath('selection')).toBe(true);
    expect(validator.validatePath('metadata')).toBe(true);
    expect(validator.validatePath('attachments')).toBe(true);
  });
  
  it('should reject invalid paths', () => {
    expect(validator.validatePath('invalid')).toBe(false);
    expect(validator.validatePath('foo')).toBe(false);
  });
});

// ============================================================================
// Node Validation Tests
// ============================================================================

describe('Yjs Schema - Node Validation', () => {
  let validator: YjsSchemaValidator;
  
  beforeEach(() => {
    validator = createYjsValidator();
  });
  
  it('should validate valid node', () => {
    const node: YjsNode = {
      id: 'node1',
      type: 'default',
      position: { x: 100, y: 100 },
      data: { label: 'Test Node' }
    };
    
    const errors = validator.validateNode(node);
    expect(errors).toHaveLength(0);
  });
  
  it('should reject node without id', () => {
    const node: any = {
      type: 'default',
      position: { x: 100, y: 100 },
      data: {}
    };
    
    const errors = validator.validateNode(node);
    expect(errors.some(e => e.type === 'required_field' && e.path.includes('id'))).toBe(true);
  });
  
  it('should reject node without type', () => {
    const node: any = {
      id: 'node1',
      position: { x: 100, y: 100 },
      data: {}
    };
    
    const errors = validator.validateNode(node);
    expect(errors.some(e => e.type === 'required_field' && e.path.includes('type'))).toBe(true);
  });
  
  it('should reject node without position', () => {
    const node: any = {
      id: 'node1',
      type: 'default',
      data: {}
    };
    
    const errors = validator.validateNode(node);
    expect(errors.some(e => e.type === 'required_field' && e.path.includes('position'))).toBe(true);
  });
  
  it('should reject node with embedded binary', () => {
    const node: YjsNode = {
      id: 'node1',
      type: 'default',
      position: { x: 100, y: 100 },
      data: {
        label: 'Test',
        binary: new Uint8Array([1, 2, 3])
      }
    };
    
    const errors = validator.validateNode(node);
    expect(errors.some(e => e.type === 'binary_embedded')).toBe(true);
  });
  
  it('should reject oversized node', () => {
    const largeData = 'x'.repeat(200 * 1024); // 200KB
    const node: YjsNode = {
      id: 'node1',
      type: 'default',
      position: { x: 100, y: 100 },
      data: { large: largeData }
    };
    
    const errors = validator.validateNode(node);
    expect(errors.some(e => e.type === 'constraint_violation')).toBe(true);
  });
});

// ============================================================================
// Edge Validation Tests
// ============================================================================

describe('Yjs Schema - Edge Validation', () => {
  let validator: YjsSchemaValidator;
  
  beforeEach(() => {
    validator = createYjsValidator();
  });
  
  it('should validate valid edge', () => {
    const edge: YjsEdge = {
      id: 'edge1',
      source: 'node1',
      target: 'node2'
    };
    
    const errors = validator.validateEdge(edge);
    expect(errors).toHaveLength(0);
  });
  
  it('should reject edge without id', () => {
    const edge: any = {
      source: 'node1',
      target: 'node2'
    };
    
    const errors = validator.validateEdge(edge);
    expect(errors.some(e => e.type === 'required_field' && e.path.includes('id'))).toBe(true);
  });
  
  it('should reject edge without source', () => {
    const edge: any = {
      id: 'edge1',
      target: 'node2'
    };
    
    const errors = validator.validateEdge(edge);
    expect(errors.some(e => e.type === 'required_field' && e.path.includes('source'))).toBe(true);
  });
  
  it('should reject edge without target', () => {
    const edge: any = {
      id: 'edge1',
      source: 'node1'
    };
    
    const errors = validator.validateEdge(edge);
    expect(errors.some(e => e.type === 'required_field' && e.path.includes('target'))).toBe(true);
  });
});

// ============================================================================
// Attachment Validation Tests
// ============================================================================

describe('Yjs Schema - Attachment Validation', () => {
  let validator: YjsSchemaValidator;
  
  beforeEach(() => {
    validator = createYjsValidator();
  });
  
  it('should validate valid attachment reference', () => {
    const attachment: YjsAttachment = {
      id: 'att1',
      name: 'document.pdf',
      type: 'application/pdf',
      size: 1024,
      url: 'https://storage.example.com/att1',
      uploadedBy: 'user1',
      uploadedAt: Date.now()
    };
    
    const errors = validator.validateAttachment(attachment);
    expect(errors).toHaveLength(0);
  });
  
  it('should reject attachment without id', () => {
    const attachment: any = {
      name: 'document.pdf',
      url: 'https://storage.example.com/att1',
      size: 1024
    };
    
    const errors = validator.validateAttachment(attachment);
    expect(errors.some(e => e.type === 'required_field' && e.path.includes('id'))).toBe(true);
  });
  
  it('should reject attachment without URL', () => {
    const attachment: any = {
      id: 'att1',
      name: 'document.pdf',
      size: 1024
    };
    
    const errors = validator.validateAttachment(attachment);
    expect(errors.some(e => e.type === 'required_field' && e.path.includes('url'))).toBe(true);
  });
  
  it('should reject embedded binary data', () => {
    const attachment: any = {
      id: 'att1',
      name: 'document.pdf',
      url: 'https://storage.example.com/att1',
      size: 1024,
      data: new Uint8Array([1, 2, 3])
    };
    
    const errors = validator.validateAttachment(attachment);
    expect(errors.some(e => e.type === 'binary_embedded')).toBe(true);
  });
  
  it('should reject oversized attachment', () => {
    const attachment: YjsAttachment = {
      id: 'att1',
      name: 'huge.bin',
      type: 'application/octet-stream',
      size: 20 * 1024 * 1024, // 20MB
      url: 'https://storage.example.com/att1',
      uploadedBy: 'user1',
      uploadedAt: Date.now()
    };
    
    const errors = validator.validateAttachment(attachment);
    expect(errors.some(e => e.type === 'constraint_violation')).toBe(true);
  });
});

// ============================================================================
// Document Validation Tests
// ============================================================================

describe('Yjs Schema - Document Validation', () => {
  let validator: YjsSchemaValidator;
  let doc: Y.Doc;
  
  beforeEach(() => {
    validator = createYjsValidator();
    doc = createTestDoc();
  });
  
  it('should validate document with valid nodes', () => {
    addTestNode(doc, {
      id: 'node1',
      type: 'default',
      position: { x: 100, y: 100 },
      data: {}
    });
    
    const result = validator.validateDocument(doc);
    expect(result.valid).toBe(true);
  });
  
  it('should reject document with invalid top-level path', () => {
    doc.getMap('invalid_path').set('key', 'value');
    
    const result = validator.validateDocument(doc);
    expect(result.valid).toBe(false);
    expect(result.errors.some(e => e.type === 'invalid_path')).toBe(true);
  });
  
  it('should validate document with multiple nodes and edges', () => {
    addTestNode(doc, {
      id: 'node1',
      type: 'default',
      position: { x: 100, y: 100 },
      data: {}
    });
    
    addTestNode(doc, {
      id: 'node2',
      type: 'default',
      position: { x: 200, y: 200 },
      data: {}
    });
    
    addTestEdge(doc, {
      id: 'edge1',
      source: 'node1',
      target: 'node2'
    });
    
    const result = validator.validateDocument(doc);
    expect(result.valid).toBe(true);
  });
});

// ============================================================================
// Snapshot Manager Tests
// ============================================================================

describe('Yjs Schema - Snapshot Manager', () => {
  let manager: YjsSnapshotManager;
  let doc: Y.Doc;
  
  beforeEach(() => {
    manager = createSnapshotManager();
    doc = createTestDoc();
  });
  
  it('should create snapshot of document', () => {
    addTestNode(doc, {
      id: 'node1',
      type: 'default',
      position: { x: 100, y: 100 },
      data: {}
    });
    
    const snapshot = manager.createSnapshot(doc);
    
    expect(snapshot.version).toBe(0);
    expect(snapshot.timestamp).toBeGreaterThan(0);
    expect(snapshot.stateVector).toBeInstanceOf(Uint8Array);
    expect(snapshot.update).toBeInstanceOf(Uint8Array);
    expect(snapshot.checksum).toBeDefined();
  });
  
  it('should restore document from snapshot', () => {
    addTestNode(doc, {
      id: 'node1',
      type: 'default',
      position: { x: 100, y: 100 },
      data: { label: 'Original' }
    });
    
    const snapshot = manager.createSnapshot(doc);
    
    // Modify document
    const nodesMap = doc.getMap('nodes');
    const node = nodesMap.get('node1') as YjsNode;
    node.data.label = 'Modified';
    nodesMap.set('node1', node);
    
    // Create new doc and restore
    const newDoc = createTestDoc();
    const restored = manager.restoreSnapshot(newDoc, snapshot.version);
    
    expect(restored).toBe(true);
    
    const state = extractDocumentState(newDoc);
    expect(state.nodes.get('node1')?.data.label).toBe('Original');
  });
  
  it('should track multiple snapshots', () => {
    for (let i = 0; i < 5; i++) {
      addTestNode(doc, {
        id: `node${i}`,
        type: 'default',
        position: { x: i * 100, y: i * 100 },
        data: {}
      });
      
      manager.createSnapshot(doc);
    }
    
    const snapshots = manager.getAllSnapshots();
    expect(snapshots.length).toBe(5);
  });
  
  it('should trim old snapshots when limit exceeded', () => {
    const smallManager = createSnapshotManager(3);
    
    for (let i = 0; i < 5; i++) {
      addTestNode(doc, {
        id: `node${i}`,
        type: 'default',
        position: { x: i * 100, y: i * 100 },
        data: {}
      });
      
      smallManager.createSnapshot(doc);
    }
    
    const snapshots = smallManager.getAllSnapshots();
    expect(snapshots.length).toBe(3);
  });
  
  it('should detect version mismatch requiring resync', () => {
    const snapshot = manager.createSnapshot(doc);
    
    // Modify document significantly
    addTestNode(doc, {
      id: 'node1',
      type: 'default',
      position: { x: 100, y: 100 },
      data: {}
    });
    
    const requiresResync = manager.requiresResync(doc, snapshot.version);
    expect(requiresResync).toBe(true);
  });
  
  it('should clear all snapshots', () => {
    manager.createSnapshot(doc);
    manager.createSnapshot(doc);
    
    manager.clearSnapshots();
    
    const snapshots = manager.getAllSnapshots();
    expect(snapshots.length).toBe(0);
  });
});

// ============================================================================
// State Extraction Tests
// ============================================================================

describe('Yjs Schema - State Extraction', () => {
  let doc: Y.Doc;
  
  beforeEach(() => {
    doc = createTestDoc();
  });
  
  it('should extract empty document state', () => {
    const state = extractDocumentState(doc);
    
    expect(state.nodes.size).toBe(0);
    expect(state.edges.size).toBe(0);
    expect(state.viewport).toEqual({ x: 0, y: 0, zoom: 1 });
  });
  
  it('should extract nodes from document', () => {
    addTestNode(doc, {
      id: 'node1',
      type: 'default',
      position: { x: 100, y: 100 },
      data: { label: 'Test' }
    });
    
    const state = extractDocumentState(doc);
    
    expect(state.nodes.size).toBe(1);
    expect(state.nodes.get('node1')?.data.label).toBe('Test');
  });
  
  it('should extract edges from document', () => {
    addTestEdge(doc, {
      id: 'edge1',
      source: 'node1',
      target: 'node2'
    });
    
    const state = extractDocumentState(doc);
    
    expect(state.edges.size).toBe(1);
    expect(state.edges.get('edge1')?.source).toBe('node1');
  });
  
  it('should extract viewport from document', () => {
    const viewportMap = doc.getMap('viewport');
    viewportMap.set('x', 500);
    viewportMap.set('y', 300);
    viewportMap.set('zoom', 1.5);
    
    const state = extractDocumentState(doc);
    
    expect(state.viewport.x).toBe(500);
    expect(state.viewport.y).toBe(300);
    expect(state.viewport.zoom).toBe(1.5);
  });
  
  it('should extract attachments from document', () => {
    const attachmentsMap = doc.getMap('attachments');
    attachmentsMap.set('att1', {
      id: 'att1',
      name: 'document.pdf',
      type: 'application/pdf',
      size: 1024,
      url: 'https://storage.example.com/att1',
      uploadedBy: 'user1',
      uploadedAt: Date.now()
    });
    
    const state = extractDocumentState(doc);
    
    expect(state.attachments.size).toBe(1);
    expect(state.attachments.get('att1')?.name).toBe('document.pdf');
  });
});

// ============================================================================
// Configuration Tests
// ============================================================================

describe('Yjs Schema - Configuration', () => {
  it('should create validator with custom config', () => {
    const validator = createYjsValidator({
      maxNodeSize: 50 * 1024,
      maxAttachments: 50,
      strictMode: false
    });
    
    expect(validator).toBeInstanceOf(YjsSchemaValidator);
  });
  
  it('should respect custom size limits', () => {
    const validator = createYjsValidator({
      maxNodeSize: 1024 // 1KB
    });
    
    const largeNode: YjsNode = {
      id: 'node1',
      type: 'default',
      position: { x: 100, y: 100 },
      data: { large: 'x'.repeat(2048) }
    };
    
    const errors = validator.validateNode(largeNode);
    expect(errors.some(e => e.type === 'constraint_violation')).toBe(true);
  });
});
