/**
 * @fileoverview Builder Action Manager
 *
 * Manages action execution, undo/redo stacks, and action validation.
 * Provides a centralized interface for all builder operations.
 *
 * @doc.type module
 * @doc.purpose Builder action management and undo/redo
 * @doc.layer platform
 */

import type {
  BuilderAction,
  ActionContext,
  ActionResult,
  ActionRegistration,
  ActionManagerConfig,
  HistoryEntry,
} from './types.js';
import type { BuilderDocument } from '../builder-document.js';
import {
  validateAddNodeAction,
  validateRemoveNodeAction,
  validateMoveNodeAction,
  validateUpdateNodePropsAction,
  validateBindComponentAction,
  validateUnbindComponentAction,
  validateUpdateLayoutAction,
  validateValidateDocumentAction,
  validateUndoAction,
  validateRedoAction,
} from './actions.js';
import {
  executeAddNodeAction,
  executeRemoveNodeAction,
  executeMoveNodeAction,
  executeUpdateNodePropsAction,
  executeBindComponentAction,
  executeUnbindComponentAction,
  executeUpdateLayoutAction,
  executeValidateDocumentAction,
  executeUndoAction,
  executeRedoAction,
} from './actions.js';

// ============================================================================
// ACTION MANAGER CLASS
// ============================================================================

/**
 * Manages builder actions with undo/redo support.
 */
export class ActionManager {
  private config: ActionManagerConfig;
  private undoStack: HistoryEntry[] = [];
  private redoStack: HistoryEntry[] = [];
  private currentDocument: BuilderDocument;
  private actionRegistrations: Map<string, ActionRegistration> = new Map();

  constructor(document: BuilderDocument, config: ActionManagerConfig = {}) {
    this.currentDocument = document;
    this.config = {
      maxHistorySize: 100,
      enableUndoRedo: true,
      autoValidate: true,
      trackChanges: true,
      ...config,
    };

    this.registerDefaultActions();
  }

  // ============================================================================
  // ACTION REGISTRATION
  // ============================================================================

  /** Register default actions. */
  private registerDefaultActions(): void {
    this.registerAction({
      type: 'add-node',
      validator: validateAddNodeAction,
      executor: executeAddNodeAction,
      description: 'Add a new component node',
      category: 'node',
    });

    this.registerAction({
      type: 'remove-node',
      validator: validateRemoveNodeAction,
      executor: executeRemoveNodeAction,
      description: 'Remove a component node',
      category: 'node',
    });

    this.registerAction({
      type: 'move-node',
      validator: validateMoveNodeAction,
      executor: executeMoveNodeAction,
      description: 'Move a node to a new position',
      category: 'node',
    });

    this.registerAction({
      type: 'update-node-props',
      validator: validateUpdateNodePropsAction,
      executor: executeUpdateNodePropsAction,
      description: 'Update node properties',
      category: 'node',
    });

    this.registerAction({
      type: 'bind-component',
      validator: validateBindComponentAction,
      executor: executeBindComponentAction,
      description: 'Create a data binding',
      category: 'binding',
    });

    this.registerAction({
      type: 'unbind-component',
      validator: validateUnbindComponentAction,
      executor: executeUnbindComponentAction,
      description: 'Remove a data binding',
      category: 'binding',
    });

    this.registerAction({
      type: 'update-layout',
      validator: validateUpdateLayoutAction,
      executor: executeUpdateLayoutAction,
      description: 'Update document layout',
      category: 'layout',
    });

    this.registerAction({
      type: 'validate-document',
      validator: validateValidateDocumentAction,
      executor: executeValidateDocumentAction,
      description: 'Validate document structure',
      category: 'document',
    });

    this.registerAction({
      type: 'undo',
      validator: validateUndoAction,
      executor: executeUndoAction,
      description: 'Undo last action',
      category: 'history',
    });

    this.registerAction({
      type: 'redo',
      validator: validateRedoAction,
      executor: executeRedoAction,
      description: 'Redo last undone action',
      category: 'history',
    });
  }

  /** Register a new action type. */
  registerAction<T extends BuilderAction>(registration: ActionRegistration<T>): void {
    this.actionRegistrations.set(registration.type, registration as unknown as ActionRegistration);
  }

  /** Get registered action types. */
  getRegisteredActionTypes(): readonly string[] {
    return Array.from(this.actionRegistrations.keys());
  }

  /** Get action registration by type. */
  getActionRegistration(type: string): ActionRegistration | undefined {
    return this.actionRegistrations.get(type);
  }

  // ============================================================================
  // ACTION EXECUTION
  // ============================================================================

  /** Execute an action. */
  executeAction(
    action: BuilderAction,
    contextOverrides: Partial<ActionContext> = {},
  ): ActionResult {
    const context: ActionContext = {
      document: this.currentDocument,
      userId: undefined,
      sessionId: undefined,
      dryRun: false,
      skipValidation: false,
      ...contextOverrides,
    };

    const registration = this.actionRegistrations.get(action.type);
    if (!registration) {
      return {
        success: false,
        document: context.document,
        errors: [`Unknown action type: ${action.type}`],
        warnings: [],
      };
    }

    // Validate action
    if (!context.skipValidation) {
      const validationErrors = registration.validator(action, context);
      if (validationErrors.length > 0) {
        return {
          success: false,
          document: context.document,
          errors: validationErrors,
          warnings: [],
        };
      }
    }

    // Execute action
    const result = registration.executor(action, context);

    // Update document and history if successful
    if (result.success && !context.dryRun) {
      this.currentDocument = result.document;
      
      if (this.config.enableUndoRedo && this.config.trackChanges) {
        this.addToHistory(action, result);
      }
    }

    return result;
  }

  /** Add action to history. */
  private addToHistory(action: BuilderAction, result: ActionResult): void {
    const entry: HistoryEntry = {
      action,
      result,
      timestamp: new Date().toISOString(),
    };

    this.undoStack.push(entry);
    this.redoStack = []; // Clear redo stack on new action

    // Enforce max history size
    if (this.config.maxHistorySize && this.undoStack.length > this.config.maxHistorySize) {
      this.undoStack.shift();
    }
  }

  // ============================================================================
  // UNDO/REDO
  // ============================================================================

  /** Undo the last action. */
  undo(): ActionResult {
    if (!this.config.enableUndoRedo) {
      return {
        success: false,
        document: this.currentDocument,
        errors: ['Undo/redo is disabled'],
        warnings: [],
      };
    }

    const entry = this.undoStack.pop();
    if (!entry) {
      return {
        success: false,
        document: this.currentDocument,
        errors: ['Nothing to undo'],
        warnings: [],
      };
    }

    // Restore previous document state
    this.currentDocument = entry.result.document;
    this.redoStack.push(entry);

    return {
      success: true,
      document: this.currentDocument,
      errors: [],
      warnings: [],
    };
  }

  /** Redo the last undone action. */
  redo(): ActionResult {
    if (!this.config.enableUndoRedo) {
      return {
        success: false,
        document: this.currentDocument,
        errors: ['Undo/redo is disabled'],
        warnings: [],
      };
    }

    const entry = this.redoStack.pop();
    if (!entry) {
      return {
        success: false,
        document: this.currentDocument,
        errors: ['Nothing to redo'],
        warnings: [],
      };
    }

    // Re-execute the action
    const result = this.executeAction(entry.action, { document: entry.result.document });
    if (result.success) {
      // Remove from redo stack since it's now in undo stack
      this.redoStack.pop();
    }

    return result;
  }

  /** Check if undo is available. */
  canUndo(): boolean {
    return !!this.config.enableUndoRedo && this.undoStack.length > 0;
  }

  /** Check if redo is available. */
  canRedo(): boolean {
    return !!this.config.enableUndoRedo && this.redoStack.length > 0;
  }

  /** Get undo history. */
  getUndoHistory(): readonly HistoryEntry[] {
    return [...this.undoStack];
  }

  /** Get redo history. */
  getRedoHistory(): readonly HistoryEntry[] {
    return [...this.redoStack];
  }

  /** Clear all history. */
  clearHistory(): void {
    this.undoStack = [];
    this.redoStack = [];
  }

  // ============================================================================
  // DOCUMENT MANAGEMENT
  // ============================================================================

  /** Get current document. */
  getCurrentDocument(): BuilderDocument {
    return this.currentDocument;
  }

  /** Set current document (resets history). */
  setCurrentDocument(document: BuilderDocument): void {
    this.currentDocument = document;
    this.clearHistory();
  }

  /** Update configuration. */
  updateConfig(config: Partial<ActionManagerConfig>): void {
    this.config = { ...this.config, ...config };
  }

  /** Get current configuration. */
  getConfig(): ActionManagerConfig {
    return { ...this.config };
  }

  // ============================================================================
  // BATCH OPERATIONS
  // ============================================================================

  /** Execute multiple actions as a batch. */
  executeBatch(actions: BuilderAction[]): ActionResult {
    let document = this.currentDocument;
    const allErrors: string[] = [];
    const allWarnings: string[] = [];
    let batchSuccess = true;

    // Disable history tracking during batch
    const originalTrackChanges = this.config.trackChanges;
    this.config.trackChanges = false;

    try {
      for (const action of actions) {
        const result = this.executeAction(action, { document });
        
        if (!result.success) {
          batchSuccess = false;
          allErrors.push(...result.errors);
        }
        
        allWarnings.push(...result.warnings);
        document = result.document;

        if (!batchSuccess) {
          break; // Stop on first error
        }
      }

      // Update final document if batch succeeded
      if (batchSuccess) {
        this.currentDocument = document;
        
        // Add batch to history as single entry
        if (this.config.enableUndoRedo && originalTrackChanges) {
          this.addToHistory(
            {
              id: `batch-${Date.now()}`,
              type: 'batch',
              timestamp: new Date().toISOString(),
              description: `Batch of ${actions.length} actions`,
              actionCount: actions.length,
            },
            {
              success: true,
              document: this.currentDocument,
              errors: [],
              warnings: allWarnings,
            },
          );
        }
      } else {
        // Restore original document on batch failure
        this.currentDocument = document;
      }
    } finally {
      this.config.trackChanges = originalTrackChanges;
    }

    return {
      success: batchSuccess,
      document: this.currentDocument,
      errors: allErrors,
      warnings: allWarnings,
    };
  }
}

// ============================================================================
// FACTORY FUNCTIONS
// ============================================================================

/** Create an action manager with default configuration. */
export function createActionManager(document: BuilderDocument): ActionManager {
  return new ActionManager(document);
}

/** Create an action manager with custom configuration. */
export function createActionManagerWithConfig(
  document: BuilderDocument,
  config: ActionManagerConfig,
): ActionManager {
  return new ActionManager(document, config);
}
