/**
 * CRDT Engine for Flashit
 * Conflict-free Replicated Data Type for collaborative editing
 *
 * @doc.type service
 * @doc.purpose Real-time collaborative editing with CRDT
 * @doc.layer product
 * @doc.pattern CRDT
 */

// ============================================================================
// Types & Interfaces
// ============================================================================

export type OperationType = 'insert' | 'delete' | 'update';

export interface Position {
  index: number;
  siteId: string;
  clock: number;
}

export interface Character {
  id: string;
  value: string;
  position: Position;
  visible: boolean;
  tombstone: boolean;
}

export interface Operation {
  id: string;
  type: OperationType;
  siteId: string;
  clock: number;
  position?: Position;
  value?: string;
  length?: number;
  timestamp: number;
}

export interface CRDTState {
  characters: Character[];
  operations: Operation[];
  clock: number;
  siteId: string;
}

export interface CRDTDocument {
  id: string;
  state: CRDTState;
  version: number;
  lastModified: Date;
}

export interface SyncMessage {
  type: 'init' | 'operation' | 'ack' | 'sync_request' | 'sync_response';
  documentId: string;
  siteId: string;
  operation?: Operation;
  operations?: Operation[];
  state?: CRDTState;
}

// ============================================================================
// CRDT Position Utilities
// ============================================================================

/**
 * Compare two positions
 */
function comparePositions(p1: Position, p2: Position): number {
  if (p1.index !== p2.index) {
    return p1.index - p2.index;
  }
  if (p1.siteId !== p2.siteId) {
    return p1.siteId.localeCompare(p2.siteId);
  }
  return p1.clock - p2.clock;
}

/**
 * Generate position between two positions
 */
function generatePositionBetween(
  left: Position | null,
  right: Position | null,
  siteId: string,
  clock: number
): Position {
  const leftIndex = left ? left.index : 0;
  const rightIndex = right ? right.index : Number.MAX_SAFE_INTEGER;
  
  if (rightIndex - leftIndex > 1) {
    // Simple case: enough space between positions
    return {
      index: Math.floor((leftIndex + rightIndex) / 2),
      siteId,
      clock,
    };
  }
  
  // Complex case: need to use fractional positions
  // For simplicity, we increment the left position
  return {
    index: leftIndex + 1,
    siteId,
    clock,
  };
}

// ============================================================================
// CRDT Engine
// ============================================================================

/**
 * CRDTEngine implements a CRDT for text editing
 */
class CRDTEngine {
  private state: CRDTState;
  private listeners: Set<(operation: Operation) => void> = new Set();

  constructor(siteId: string, initialState?: CRDTState) {
    this.state = initialState || {
      characters: [],
      operations: [],
      clock: 0,
      siteId,
    };
  }

  /**
   * Get current state
   */
  getState(): CRDTState {
    return {
      ...this.state,
      characters: [...this.state.characters],
      operations: [...this.state.operations],
    };
  }

  /**
   * Get document text
   */
  getText(): string {
    return this.state.characters
      .filter((char) => char.visible && !char.tombstone)
      .map((char) => char.value)
      .join('');
  }

  /**
   * Get document length
   */
  getLength(): number {
    return this.state.characters.filter((char) => char.visible && !char.tombstone).length;
  }

  /**
   * Insert character at position
   */
  insert(index: number, value: string): Operation {
    this.state.clock++;

    // Find positions before and after insertion point
    const visibleChars = this.state.characters.filter(
      (char) => char.visible && !char.tombstone
    );
    const leftChar = index > 0 ? visibleChars[index - 1] : null;
    const rightChar = index < visibleChars.length ? visibleChars[index] : null;

    // Generate position
    const position = generatePositionBetween(
      leftChar?.position || null,
      rightChar?.position || null,
      this.state.siteId,
      this.state.clock
    );

    // Create character
    const character: Character = {
      id: `${this.state.siteId}-${this.state.clock}`,
      value,
      position,
      visible: true,
      tombstone: false,
    };

    // Insert character in sorted order
    this.insertCharacter(character);

    // Create operation
    const operation: Operation = {
      id: character.id,
      type: 'insert',
      siteId: this.state.siteId,
      clock: this.state.clock,
      position,
      value,
      timestamp: Date.now(),
    };

    this.state.operations.push(operation);
    this.notifyListeners(operation);

    return operation;
  }

  /**
   * Delete character at position
   */
  delete(index: number, length: number = 1): Operation {
    this.state.clock++;

    const visibleChars = this.state.characters.filter(
      (char) => char.visible && !char.tombstone
    );
    
    if (index < 0 || index >= visibleChars.length) {
      throw new Error('Invalid delete position');
    }

    const charToDelete = visibleChars[index];
    
    // Mark as tombstone
    const charIndex = this.state.characters.indexOf(charToDelete);
    this.state.characters[charIndex].tombstone = true;
    this.state.characters[charIndex].visible = false;

    // Create operation
    const operation: Operation = {
      id: `${this.state.siteId}-${this.state.clock}`,
      type: 'delete',
      siteId: this.state.siteId,
      clock: this.state.clock,
      position: charToDelete.position,
      length,
      timestamp: Date.now(),
    };

    this.state.operations.push(operation);
    this.notifyListeners(operation);

    return operation;
  }

  /**
   * Apply remote operation
   */
  applyOperation(operation: Operation): void {
    // Check if operation already applied
    if (this.state.operations.some((op) => op.id === operation.id)) {
      return;
    }

    switch (operation.type) {
      case 'insert':
        this.applyRemoteInsert(operation);
        break;
      case 'delete':
        this.applyRemoteDelete(operation);
        break;
      case 'update':
        this.applyRemoteUpdate(operation);
        break;
    }

    this.state.operations.push(operation);
    this.notifyListeners(operation);
  }

  /**
   * Apply multiple operations
   */
  applyOperations(operations: Operation[]): void {
    // Sort operations by timestamp
    const sorted = [...operations].sort((a, b) => a.timestamp - b.timestamp);
    
    for (const operation of sorted) {
      this.applyOperation(operation);
    }
  }

  /**
   * Merge with remote state
   */
  merge(remoteState: CRDTState): void {
    // Apply all remote operations that we don't have
    const remoteOps = remoteState.operations.filter(
      (op) => !this.state.operations.some((localOp) => localOp.id === op.id)
    );

    this.applyOperations(remoteOps);

    // Update clock to be max of local and remote
    this.state.clock = Math.max(this.state.clock, remoteState.clock);
  }

  /**
   * Subscribe to operations
   */
  subscribe(callback: (operation: Operation) => void): () => void {
    this.listeners.add(callback);
    return () => this.listeners.delete(callback);
  }

  /**
   * Get operations since a clock value
   */
  getOperationsSince(clock: number): Operation[] {
    return this.state.operations.filter((op) => op.clock > clock);
  }

  /**
   * Clear tombstones (garbage collection)
   */
  garbageCollect(): void {
    this.state.characters = this.state.characters.filter(
      (char) => !char.tombstone || char.visible
    );
  }

  // ============================================================================
  // Private Methods
  // ============================================================================

  private insertCharacter(character: Character): void {
    // Find insertion point using binary search
    let left = 0;
    let right = this.state.characters.length;

    while (left < right) {
      const mid = Math.floor((left + right) / 2);
      const comparison = comparePositions(
        character.position,
        this.state.characters[mid].position
      );

      if (comparison < 0) {
        right = mid;
      } else {
        left = mid + 1;
      }
    }

    this.state.characters.splice(left, 0, character);
  }

  private applyRemoteInsert(operation: Operation): void {
    if (!operation.position || !operation.value) {
      throw new Error('Invalid insert operation');
    }

    const character: Character = {
      id: operation.id,
      value: operation.value,
      position: operation.position,
      visible: true,
      tombstone: false,
    };

    this.insertCharacter(character);
  }

  private applyRemoteDelete(operation: Operation): void {
    if (!operation.position) {
      throw new Error('Invalid delete operation');
    }

    // Find character with matching position
    const charIndex = this.state.characters.findIndex(
      (char) =>
        comparePositions(char.position, operation.position!) === 0
    );

    if (charIndex !== -1) {
      this.state.characters[charIndex].tombstone = true;
      this.state.characters[charIndex].visible = false;
    }
  }

  private applyRemoteUpdate(operation: Operation): void {
    if (!operation.position || !operation.value) {
      throw new Error('Invalid update operation');
    }

    // Find character with matching position
    const charIndex = this.state.characters.findIndex(
      (char) =>
        comparePositions(char.position, operation.position!) === 0
    );

    if (charIndex !== -1) {
      this.state.characters[charIndex].value = operation.value;
    }
  }

  private notifyListeners(operation: Operation): void {
    this.listeners.forEach((listener) => {
      try {
        listener(operation);
      } catch (error) {
        console.error('Error in CRDT listener:', error);
      }
    });
  }
}

// ============================================================================
// CRDT Document Manager
// ============================================================================

/**
 * CRDTDocumentManager manages multiple CRDT documents
 */
export class CRDTDocumentManager {
  private documents: Map<string, CRDTEngine> = new Map();
  private siteId: string;

  constructor(siteId: string) {
    this.siteId = siteId;
  }

  /**
   * Create or get document
   */
  getDocument(documentId: string): CRDTEngine {
    if (!this.documents.has(documentId)) {
      const engine = new CRDTEngine(this.siteId);
      this.documents.set(documentId, engine);
    }
    return this.documents.get(documentId)!;
  }

  /**
   * Load document with initial state
   */
  loadDocument(documentId: string, state: CRDTState): void {
    const engine = new CRDTEngine(this.siteId, state);
    this.documents.set(documentId, engine);
  }

  /**
   * Delete document
   */
  deleteDocument(documentId: string): void {
    this.documents.delete(documentId);
  }

  /**
   * Get all document IDs
   */
  getDocumentIds(): string[] {
    return Array.from(this.documents.keys());
  }

  /**
   * Subscribe to document operations
   */
  subscribeToDocument(
    documentId: string,
    callback: (operation: Operation) => void
  ): () => void {
    const doc = this.getDocument(documentId);
    return doc.subscribe(callback);
  }
}

// ============================================================================
// Exports
// ============================================================================

/**
 * Create CRDT engine
 */
export function createCRDTEngine(siteId: string, initialState?: CRDTState): CRDTEngine {
  return new CRDTEngine(siteId, initialState);
}

/**
 * Create CRDT document manager
 */
export function createCRDTDocumentManager(siteId: string): CRDTDocumentManager {
  return new CRDTDocumentManager(siteId);
}

export default CRDTEngine;
