/**
 * Canvas Services Index
 * 
 * Centralized exports for all canvas service modules.
 * 
 * @doc.type module
 * @doc.purpose Canvas services barrel export
 * @doc.layer product
 * @doc.pattern Barrel Export
 */

// Canvas Editor
export { CanvasEditor, useCanvasEditor } from './CanvasEditor';
export type {
    SelectionState,
    EditorConfig,
    EditorOperations,
    KeyboardShortcuts,
} from './CanvasEditor';

// Canvas Persistence
export { CanvasPersistence, useCanvasPersistence } from './CanvasPersistence';
export type {
    CanvasSnapshot,
    VersionDiff,
    AutoSaveConfig,
    Command,
    CommandStack,
    PersistenceConfig,
} from './CanvasPersistence';

// Canvas Commands
export {
    AddElementCommand,
    RemoveElementCommand,
    MoveElementCommand,
    UpdateElementCommand,
    AddConnectionCommand,
    RemoveConnectionCommand,
    BatchCommand,
    CommandFactory,
} from './CanvasCommands';

// Lifecycle Services
export * from './lifecycle';

// Canvas Sync Service
export {
    CanvasSyncService,
    getCanvasSyncService,
    clearCanvasSyncService,
} from './CanvasSyncService';
export type {
    CanvasSyncState,
    SyncHistoryEntry,
    CanvasSyncSnapshot,
} from './CanvasSyncService';

// Canvas Sync Hook
export { useCanvasSync } from './useCanvasSync';
export type { UseCanvasSyncResult } from './useCanvasSync';

// Intent-first Canvas Generation
export {
  parseIntent,
  generateNodes,
  generateConnections,
  generatePreview,
} from './intent/IntentCanvasGenerator';
export type {
  IntentCanvasNode,
  IntentCanvasConnection,
  IntentCanvasPreview,
  IntentParseResult,
} from './intent/IntentCanvasGenerator';
