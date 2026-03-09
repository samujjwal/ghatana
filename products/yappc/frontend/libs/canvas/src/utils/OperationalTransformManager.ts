import type {
  OperationalTransform,
  ConflictResolution,
} from '../hooks/useAdvancedCollaboration';
import type { Node, Edge } from '@xyflow/react';


/**
 *
 */
export interface TransformOperation {
  apply(target: unknown): unknown;
  inverse(): TransformOperation;
  compose(other: TransformOperation): TransformOperation;
  transform(other: TransformOperation, priority: boolean): TransformOperation;
}

/**
 *
 */
export class NodeInsertOperation implements TransformOperation {
  /**
   *
   */
  constructor(
    private node: Node,
    protected index: number
  ) { }

  /**
   *
   */
  apply(nodes: Node[]): Node[] {
    const newNodes = [...nodes];
    newNodes.splice(this.index, 0, this.node);
    return newNodes;
  }

  /**
   *
   */
  inverse(): TransformOperation {
    return new NodeDeleteOperation(this.node.id, this.index);
  }

  /**
   *
   */
  compose(other: TransformOperation): TransformOperation {
    // Composition logic for chaining operations
    return this;
  }

  /**
   *
   */
  transform(other: TransformOperation, priority: boolean): TransformOperation {
    if (other instanceof NodeInsertOperation) {
      // If inserting at same or earlier index, shift our index
      if ((other as unknown).index <= (this as unknown).index) {
        return new NodeInsertOperation(this.node, (this as unknown).index + 1);
      }
    }
    return this;
  }
}

/**
 *
 */
export class NodeDeleteOperation implements TransformOperation {
  /**
   *
   */
  constructor(
    private nodeId: string,
    protected index: number,
    private deletedNode?: Node
  ) { }

  /**
   *
   */
  apply(nodes: Node[]): Node[] {
    const nodeIndex = nodes.findIndex((n) => n.id === this.nodeId);
    if (nodeIndex >= 0) {
      const newNodes = [...nodes];
      newNodes.splice(nodeIndex, 1);
      return newNodes;
    }
    return nodes;
  }

  /**
   *
   */
  inverse(): TransformOperation {
    if (!this.deletedNode) {
      throw new Error('Cannot create inverse without deleted node data');
    }
    return new NodeInsertOperation(this.deletedNode, this.index);
  }

  /**
   *
   */
  compose(other: TransformOperation): TransformOperation {
    return this;
  }

  /**
   *
   */
  transform(other: TransformOperation, priority: boolean): TransformOperation {
    if (other instanceof NodeInsertOperation) {
      // If inserting before our delete position, shift index
      if ((other as unknown).index <= (this as unknown).index) {
        return new NodeDeleteOperation(
          this.nodeId,
          (this as unknown).index + 1,
          this.deletedNode
        );
      }
    } else if (other instanceof NodeDeleteOperation) {
      // If both deleting same node, one becomes no-op
      if ((other as unknown).nodeId === this.nodeId) {
        return new NoOpOperation();
      }
    }
    return this;
  }
}

/**
 *
 */
export class NodeUpdateOperation implements TransformOperation {
  /**
   *
   */
  constructor(
    private nodeId: string,
    private updates: Partial<Node>,
    private previousState?: Partial<Node>
  ) { }

  /**
   *
   */
  apply(nodes: Node[]): Node[] {
    return nodes.map((node) =>
      node.id === this.nodeId ? { ...node, ...this.updates } : node
    );
  }

  /**
   *
   */
  inverse(): TransformOperation {
    if (!this.previousState) {
      throw new Error('Cannot create inverse without previous state');
    }
    return new NodeUpdateOperation(
      this.nodeId,
      this.previousState,
      this.updates
    );
  }

  /**
   *
   */
  compose(other: TransformOperation): TransformOperation {
    if (other instanceof NodeUpdateOperation && other.nodeId === this.nodeId) {
      // Merge updates
      return new NodeUpdateOperation(
        this.nodeId,
        { ...this.updates, ...other.updates },
        this.previousState
      );
    }
    return this;
  }

  /**
   *
   */
  transform(other: TransformOperation, priority: boolean): TransformOperation {
    if (other instanceof NodeUpdateOperation && other.nodeId === this.nodeId) {
      // Conflict resolution: merge non-conflicting properties
      const mergedUpdates = { ...this.updates };

      // Simple conflict resolution: priority wins for conflicting keys
      for (const [key, value] of Object.entries(other.updates)) {
        if (!(key in this.updates) || priority) {
          (mergedUpdates as unknown)[key] = value;
        }
      }

      return new NodeUpdateOperation(
        this.nodeId,
        mergedUpdates,
        this.previousState
      );
    }
    return this;
  }
}

/**
 *
 */
export class NoOpOperation implements TransformOperation {
  /**
   *
   */
  apply(target: unknown): unknown {
    return target;
  }

  /**
   *
   */
  inverse(): TransformOperation {
    return new NoOpOperation();
  }

  /**
   *
   */
  compose(other: TransformOperation): TransformOperation {
    return other;
  }

  /**
   *
   */
  transform(other: TransformOperation, priority: boolean): TransformOperation {
    return this;
  }
}

/**
 *
 */
export class OperationalTransformManager {
  private operationHistory: OperationalTransform[] = [];
  private undoStack: TransformOperation[] = [];
  private redoStack: TransformOperation[] = [];

  /**
   * Convert a generic OperationalTransform to a specific TransformOperation
   */
  private createTransformOperation(
    op: OperationalTransform
  ): TransformOperation {
    switch (op.target) {
      case 'node':
        switch (op.operation) {
          case 'insert':
            return new NodeInsertOperation(op.data, 0); // Index would come from data
          case 'delete':
            return new NodeDeleteOperation(op.targetId!, 0); // Index and node data from context
          case 'update':
            return new NodeUpdateOperation(op.targetId!, op.data);
          default:
            return new NoOpOperation();
        }
      case 'edge':
        // Similar edge operations would be implemented here
        return new NoOpOperation();
      default:
        return new NoOpOperation();
    }
  }

  /**
   * Apply operation and handle transformations
   */
  applyOperation(
    op: OperationalTransform,
    currentState: { nodes: Node[]; edges: Edge[] }
  ) {
    const transformOp = this.createTransformOperation(op);

    // Transform against concurrent operations
    const transformedOp = this.transformAgainstHistory(
      transformOp,
      op.timestamp
    );

    // Apply to state
    const newState = { ...currentState };
    if (op.target === 'node') {
      newState.nodes = transformedOp.apply(currentState.nodes);
    } else if (op.target === 'edge') {
      newState.edges = transformedOp.apply(currentState.edges);
    }

    // Add to history
    this.operationHistory.push(op);
    this.undoStack.push(transformedOp);
    this.redoStack = []; // Clear redo stack

    return newState;
  }

  /**
   * Transform operation against concurrent operations
   */
  private transformAgainstHistory(
    op: TransformOperation,
    timestamp: number
  ): TransformOperation {
    let transformedOp = op;

    // Find concurrent operations (simplified)
    const concurrentOps = this.operationHistory.filter(
      (histOp) => Math.abs(histOp.timestamp - timestamp) < 1000 // Within 1 second
    );

    // Transform against each concurrent operation
    for (const concurrentOp of concurrentOps) {
      const concurrentTransformOp = this.createTransformOperation(concurrentOp);
      transformedOp = transformedOp.transform(concurrentTransformOp, false);
    }

    return transformedOp;
  }

  /**
   * Detect conflicts between operations
   */
  detectConflicts(
    op1: OperationalTransform,
    op2: OperationalTransform
  ): ConflictResolution | null {
    // Same target and overlapping time
    if (
      op1.target === op2.target &&
      op1.targetId === op2.targetId &&
      Math.abs(op1.timestamp - op2.timestamp) < 1000
    ) {
      return {
        conflictId: `conflict-${Date.now()}`,
        type: 'concurrent_edit',
        operations: [op1, op2],
        resolution: 'manual',
        resolvedData: null,
        timestamp: Date.now(),
      };
    }

    // Version conflicts
    if (op1.version !== op2.version && op1.targetId === op2.targetId) {
      return {
        conflictId: `version-conflict-${Date.now()}`,
        type: 'version_mismatch',
        operations: [op1, op2],
        resolution: 'manual',
        resolvedData: null,
        timestamp: Date.now(),
      };
    }

    return null;
  }

  /**
   * Automatically resolve conflicts using operational transforms
   */
  resolveConflict(
    conflict: ConflictResolution,
    strategy: 'merge' | 'overwrite' | 'manual' = 'merge'
  ): ConflictResolution {
    const resolvedConflict = { ...conflict, resolution: strategy };

    switch (strategy) {
      case 'merge':
        // Use operational transforms to merge
        const [op1, op2] = conflict.operations;
        const transform1 = this.createTransformOperation(op1);
        const transform2 = this.createTransformOperation(op2);

        // Transform op1 against op2 (op2 has priority)
        const mergedTransform = transform1.transform(transform2, false);

        resolvedConflict.resolvedData = mergedTransform;
        break;

      case 'overwrite':
        // Last operation wins
        const lastOp = conflict.operations.reduce(
          (latest: OperationalTransform, op: OperationalTransform) =>
            op.timestamp > latest.timestamp ? op : latest
        );
        resolvedConflict.resolvedData = this.createTransformOperation(lastOp);
        break;

      case 'manual':
        // Leave for manual resolution
        break;
    }

    return resolvedConflict;
  }

  /**
   * Undo last operation
   */
  undo(): TransformOperation | null {
    const lastOp = this.undoStack.pop();
    if (lastOp) {
      const inverseOp = lastOp.inverse();
      this.redoStack.push(lastOp);
      return inverseOp;
    }
    return null;
  }

  /**
   * Redo last undone operation
   */
  redo(): TransformOperation | null {
    const redoOp = this.redoStack.pop();
    if (redoOp) {
      this.undoStack.push(redoOp);
      return redoOp;
    }
    return null;
  }

  /**
   * Create a diff between two states
   */
  createDiff(
    oldState: { nodes: Node[]; edges: Edge[] },
    newState: { nodes: Node[]; edges: Edge[] }
  ): OperationalTransform[] {
    const operations: OperationalTransform[] = [];

    // Compare nodes
    const oldNodeMap = new Map(oldState.nodes.map((n) => [n.id, n]));
    const newNodeMap = new Map(newState.nodes.map((n) => [n.id, n]));

    // Find deleted nodes
    for (const [id, node] of oldNodeMap) {
      if (!newNodeMap.has(id)) {
        operations.push({
          id: `delete-${id}-${Date.now()}`,
          operation: 'delete',
          target: 'node',
          targetId: id,
          data: node,
          author: 'system',
          timestamp: Date.now(),
          version: 0,
          dependencies: [],
        });
      }
    }

    // Find new and updated nodes
    for (const [id, node] of newNodeMap) {
      const oldNode = oldNodeMap.get(id);
      if (!oldNode) {
        // New node
        operations.push({
          id: `insert-${id}-${Date.now()}`,
          operation: 'insert',
          target: 'node',
          targetId: id,
          data: node,
          author: 'system',
          timestamp: Date.now(),
          version: 0,
          dependencies: [],
        });
      } else if (JSON.stringify(oldNode) !== JSON.stringify(node)) {
        // Updated node
        operations.push({
          id: `update-${id}-${Date.now()}`,
          operation: 'update',
          target: 'node',
          targetId: id,
          data: node,
          author: 'system',
          timestamp: Date.now(),
          version: 0,
          dependencies: [],
        });
      }
    }

    // Similar logic for edges...

    return operations;
  }

  /**
   * Get operation history
   */
  getHistory(): OperationalTransform[] {
    return [...this.operationHistory];
  }

  /**
   * Clear history (for testing or reset)
   */
  clearHistory(): void {
    this.operationHistory = [];
    this.undoStack = [];
    this.redoStack = [];
  }
}
