/**
 * Collaboration Library
 *
 * @description Real-time collaboration infrastructure using Yjs CRDT
 * for conflict-free synchronization across multiple users.
 *
 * Features:
 * - WebSocket-based real-time sync
 * - Offline support with IndexedDB persistence
 * - Canvas collaboration (nodes, edges, cursors)
 * - Document collaboration (text, comments, versions)
 * - User presence and activity tracking
 */

// Core managers
export {
  CollaborationManager,
  getCollaboration,
  destroyCollaboration,
} from './CollaborationManager';
export type {
  CollaborationConfig,
  CollaborationState,
  CollaborationUser,
  CollaborationEventType,
  CollaborationEvent,
} from './CollaborationManager';

// Canvas collaboration
export { CanvasCollaboration } from './CanvasCollaboration';
export type {
  CanvasNode,
  CanvasEdge,
  CanvasViewport,
  CanvasSelection,
  UserCursor,
  CanvasCollaborationState,
  CanvasChangeType,
  CanvasChangeEvent,
} from './CanvasCollaboration';

// Document collaboration
export { DocumentCollaboration } from './DocumentCollaboration';
export type {
  TextCursor,
  DocumentComment,
  DocumentCommentReply,
  DocumentVersion,
  DocumentChangeType,
  DocumentChangeEvent,
} from './DocumentCollaboration';

// Presence management
export {
  PresenceManager,
  getPresenceStatusColor,
  getPresenceStatusLabel,
  formatPresenceLocation,
} from './PresenceManager';
export type {
  PresenceState,
  PresenceLocation,
  PresenceUser,
  PresenceEventType,
  PresenceEvent,
} from './PresenceManager';

// React hooks
export {
  useCollaboration,
  useCanvasCollaboration,
  useDocumentCollaboration,
  usePresence,
  useCollaborationCursors,
} from './hooks';
export type {
  UseCollaborationOptions,
  UseCollaborationReturn,
  UseCanvasCollaborationOptions,
  UseCanvasCollaborationReturn,
  UseDocumentCollaborationOptions,
  UseDocumentCollaborationReturn,
  UsePresenceOptions,
  UsePresenceReturn,
  CollaborationCursor,
} from './hooks';

// Components
export {
  CollaborationCursors,
  PresenceAvatars,
  UserActivityIndicator,
  SelectionHighlight,
  TextCursorIndicator,
} from './components';
export type {
  CollaborationCursorsProps,
  PresenceAvatarsProps,
  UserActivityIndicatorProps,
  SelectionHighlightProps,
  SelectionRange,
  TextCursorIndicatorProps,
} from './components';
