/**
 * Collaboration Provider — React context for collaborative canvas editing.
 *
 * @doc.type module
 * @doc.purpose React context wrapping CanvasCollaborationAdapter for live collaboration
 * @doc.layer platform
 * @doc.pattern Provider/Hook
 *
 * Usage:
 * ```tsx
 * import { CollaborationProvider, useCollaboration } from "@ghatana/canvas/collaboration";
 *
 * // Wrap the canvas:
 * <CollaborationProvider adapter={myYjsAdapter} documentId="doc-123" localUser={...}>
 *   <HybridCanvas ... />
 * </CollaborationProvider>
 *
 * // Inside canvas components:
 * const { session, collaborators, updateCursor } = useCollaboration();
 * ```
 */

import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useReducer,
  useRef,
} from "react";

import {
  noopCollaborationAdapter,
  type CanvasCollaborationAdapter,
  type CollaborationSession,
  type CollaboratorPresence,
  type CollaborativeChange,
} from "./collab-types.js";

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

interface CollabState {
  session: CollaborationSession | null;
  collaborators: CollaboratorPresence[];
  isConnecting: boolean;
  error: string | null;
}

type CollabAction =
  | { type: "SESSION_CHANGE"; payload: CollaborationSession }
  | { type: "PRESENCE_CHANGE"; payload: CollaboratorPresence[] }
  | { type: "SET_CONNECTING"; payload: boolean }
  | { type: "SET_ERROR"; payload: string | null };

function reducer(state: CollabState, action: CollabAction): CollabState {
  switch (action.type) {
    case "SESSION_CHANGE":
      return { ...state, session: action.payload, isConnecting: action.payload.status === "connecting" };
    case "PRESENCE_CHANGE":
      return { ...state, collaborators: action.payload };
    case "SET_CONNECTING":
      return { ...state, isConnecting: action.payload };
    case "SET_ERROR":
      return { ...state, error: action.payload };
    default:
      return state;
  }
}

// ---------------------------------------------------------------------------
// Context value
// ---------------------------------------------------------------------------

export interface CollaborationContextValue {
  session: CollaborationSession | null;
  collaborators: CollaboratorPresence[];
  isConnecting: boolean;
  isConnected: boolean;
  error: string | null;
  /** Push a change event to collaborators */
  broadcastChange(change: Omit<CollaborativeChange, "seq" | "timestamp">): void;
  /** Update cursor position */
  updateCursor(pos: { x: number; y: number } | null): void;
  /** Update local selection */
  updateSelection(elementIds: string[]): void;
}

const CollabCtx = createContext<CollaborationContextValue | null>(null);

// ---------------------------------------------------------------------------
// Provider
// ---------------------------------------------------------------------------

export interface CollaborationProviderProps {
  adapter?: CanvasCollaborationAdapter;
  documentId?: string;
  localUser?: Omit<CollaboratorPresence, "isLocal" | "status">;
  children: React.ReactNode;
}

export function CollaborationProvider({
  adapter = noopCollaborationAdapter,
  documentId,
  localUser,
  children,
}: CollaborationProviderProps): React.ReactElement {
  const [state, dispatch] = useReducer(reducer, {
    session: null,
    collaborators: [],
    isConnecting: false,
    error: null,
  });

  const adapterRef = useRef(adapter);
  adapterRef.current = adapter;

  // Join session when documentId + localUser are provided
  useEffect(() => {
    if (!documentId || !localUser) return;

    const a = adapterRef.current;
    dispatch({ type: "SET_CONNECTING", payload: true });

    void a.join(documentId, localUser).catch((e: unknown) => {
      dispatch({
        type: "SET_ERROR",
        payload: e instanceof Error ? e.message : "Failed to join session",
      });
    });

    const unsubSession = a.onSessionChange((s) => {
      dispatch({ type: "SESSION_CHANGE", payload: s });
    });
    const unsubPresence = a.onPresenceChange((p) => {
      dispatch({ type: "PRESENCE_CHANGE", payload: p });
    });

    return () => {
      unsubSession();
      unsubPresence();
      void a.leave();
    };
  // localUser is spread as an object — use JSON-stable key to avoid infinite re-runs
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [documentId]);

  const broadcastChange = useCallback(
    (change: Omit<CollaborativeChange, "seq" | "timestamp">) => {
      adapterRef.current.broadcastChange(change);
    },
    [],
  );

  const updateCursor = useCallback((pos: { x: number; y: number } | null) => {
    adapterRef.current.updateCursor(pos);
  }, []);

  const updateSelection = useCallback((elementIds: string[]) => {
    adapterRef.current.updateSelection(elementIds);
  }, []);

  const value = useMemo<CollaborationContextValue>(
    () => ({
      session: state.session,
      collaborators: state.collaborators,
      isConnecting: state.isConnecting,
      isConnected: state.session?.status === "connected",
      error: state.error,
      broadcastChange,
      updateCursor,
      updateSelection,
    }),
    [state, broadcastChange, updateCursor, updateSelection],
  );

  return React.createElement(CollabCtx.Provider, { value }, children);
}

// ---------------------------------------------------------------------------
// Hooks
// ---------------------------------------------------------------------------

export function useCollaboration(): CollaborationContextValue {
  const ctx = useContext(CollabCtx);
  if (!ctx) throw new Error("useCollaboration must be used within CollaborationProvider");
  return ctx;
}

export function useCollaborators(): CollaboratorPresence[] {
  return useCollaboration().collaborators;
}

export function useRemoteCollaborators(): CollaboratorPresence[] {
  return useCollaboration().collaborators.filter((c) => !c.isLocal);
}
