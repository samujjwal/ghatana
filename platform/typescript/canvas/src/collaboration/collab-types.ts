/**
 * Collaboration Types & Adapter Interface
 *
 * @doc.type module
 * @doc.purpose Platform-level contracts for real-time collaborative canvas editing
 * @doc.layer platform
 * @doc.pattern Types / Adapter
 *
 * The canvas platform itself does NOT implement a CRDT, Y.js, or any specific
 * real-time transport.  Products that need collaboration (YAPPC, Data Cloud)
 * implement `CanvasCollaborationAdapter` and pass it to `CollaborationProvider`.
 *
 * The adapter interface is intentionally transport-agnostic.  It works equally
 * well with:
 * - Y.js + WebSocket (most common)
 * - Automerge + WebRTC
 * - Custom operational-transform over WebSocket
 * - Server-Sent Events for read-only shared views
 */

// ---------------------------------------------------------------------------
// User presence
// ---------------------------------------------------------------------------

export interface CollaboratorPresence {
  /** Unique user identifier */
  userId: string;
  /** Display name */
  displayName: string;
  /** Avatar URL */
  avatarUrl?: string;
  /** Assigned color (hex) */
  color: string;
  /** Current cursor position in canvas space */
  cursor?: { x: number; y: number };
  /** IDs of elements the collaborator currently has selected */
  selectedElementIds?: string[];
  /** Currently active tool */
  activeTool?: string;
  /** Whether this is the local user */
  isLocal?: boolean;
  /** Online / idle / away */
  status: "online" | "idle" | "away";
  /** Last activity timestamp */
  lastActiveAt?: Date;
}

// ---------------------------------------------------------------------------
// Change events
// ---------------------------------------------------------------------------

export type CollaborativeChangeType =
  | "element-add"
  | "element-update"
  | "element-delete"
  | "node-add"
  | "node-update"
  | "node-delete"
  | "edge-add"
  | "edge-update"
  | "edge-delete"
  | "viewport-sync"
  | "presence-update"
  | "cursor-move"
  | "transaction-start"
  | "transaction-commit";

export interface CollaborativeChange {
  type: CollaborativeChangeType;
  /** User who made the change */
  userId: string;
  /** Logical clock / sequence number */
  seq: number;
  /** Opaque payload — interpreted by the adapter */
  payload: Record<string, unknown>;
  timestamp: Date;
}

// ---------------------------------------------------------------------------
// Session
// ---------------------------------------------------------------------------

export interface CollaborationSession {
  /** Unique identifier for this collaboration session */
  sessionId: string;
  /** Canvas document identifier */
  documentId: string;
  /** All currently connected collaborators */
  collaborators: CollaboratorPresence[];
  /** Connection status */
  status: "connecting" | "connected" | "disconnected" | "error";
  /** Error message when status = "error" */
  error?: string;
}

// ---------------------------------------------------------------------------
// Adapter interface
// ---------------------------------------------------------------------------

export type ChangeListener = (change: CollaborativeChange) => void;
export type PresenceListener = (collaborators: CollaboratorPresence[]) => void;
export type SessionListener = (session: CollaborationSession) => void;

/**
 * CanvasCollaborationAdapter
 *
 * Implement this interface in your product to enable real-time collaboration.
 */
export interface CanvasCollaborationAdapter {
  /** Join a collaborative editing session for a document */
  join(documentId: string, localUser: Omit<CollaboratorPresence, "isLocal" | "status">): Promise<CollaborationSession>;

  /** Leave the current session */
  leave(): Promise<void>;

  /** Broadcast a change to other collaborators */
  broadcastChange(change: Omit<CollaborativeChange, "seq" | "timestamp">): void;

  /** Update local user's cursor position */
  updateCursor(position: { x: number; y: number } | null): void;

  /** Update local user's selection */
  updateSelection(elementIds: string[]): void;

  /** Subscribe to remote changes */
  onRemoteChange(listener: ChangeListener): () => void;

  /** Subscribe to presence updates */
  onPresenceChange(listener: PresenceListener): () => void;

  /** Subscribe to session status changes */
  onSessionChange(listener: SessionListener): () => void;

  /** Get current session, if any */
  getSession(): CollaborationSession | null;

  /** Get all current collaborators (including local user) */
  getCollaborators(): CollaboratorPresence[];
}

// ---------------------------------------------------------------------------
// No-op adapter (platform default — no collaboration)
// ---------------------------------------------------------------------------

export const noopCollaborationAdapter: CanvasCollaborationAdapter = {
  async join(documentId, localUser) {
    return {
      sessionId: `local-${documentId}`,
      documentId,
      collaborators: [{ ...localUser, isLocal: true, status: "online" }],
      status: "connected",
    };
  },
  async leave() { /* noop */ },
  broadcastChange() { /* noop */ },
  updateCursor() { /* noop */ },
  updateSelection() { /* noop */ },
  onRemoteChange() { return () => undefined; },
  onPresenceChange() { return () => undefined; },
  onSessionChange() { return () => undefined; },
  getSession() { return null; },
  getCollaborators() { return []; },
};
