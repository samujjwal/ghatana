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
