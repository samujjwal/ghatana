/**
 * KanbanBoard Component - Public Exports
 *
 * @module DevSecOps/KanbanBoard
 * @doc.type module
 * @doc.purpose KanbanBoard component exports with AI enhancements
 * @doc.layer product
 * @doc.pattern Barrel
 */

// Core KanbanBoard
export { KanbanBoard } from './KanbanBoard';
export type { KanbanBoardProps, KanbanColumn, DragEventData } from './types';

// AI-enhanced KanbanBoard
export { SmartKanbanBoard } from './SmartKanbanBoard';
export type {
    SmartKanbanBoardProps,
    KanbanAISuggestion,
    BoardInsights,
} from './SmartKanbanBoard';
