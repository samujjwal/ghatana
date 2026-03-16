/**
 * React hook for collaborating on a content package in real-time.
 *
 * Manages WebSocket lifecycle, reconnection, and exposes a typed API for
 * sending patches and cursor updates.
 */

import { useState, useEffect, useRef, useCallback } from "react";
import {
  CollaborationManager,
  type CollaborationSession,
  type CollabParticipant,
  type PatchEvent,
  type CursorEvent,
} from "@/lib/collaboration";

export interface UseCollaborationOptions {
  contentPackageId: string | null;
  userId: string;
  displayName: string;
  /** Called when another user sends a patch. */
  onRemotePatch?: (event: PatchEvent) => void;
  /** Called when another user moves their cursor. */
  onRemoteCursor?: (event: CursorEvent) => void;
}

export interface UseCollaborationResult {
  session: CollaborationSession | null;
  participants: CollabParticipant[];
  isConnected: boolean;
  sendPatch: (path: string, value: unknown) => void;
  sendCursor: (selection: string | null) => void;
}

/**
 * Manage a live collaboration session for a content package.
 *
 * - Returns `null` session when `contentPackageId` is null (viewer mode).
 * - Cleans up WebSocket on unmount or when id changes.
 */
export function useCollaboration(
  opts: UseCollaborationOptions,
): UseCollaborationResult {
  const { contentPackageId, userId, displayName, onRemotePatch, onRemoteCursor } = opts;

  const managerRef = useRef<CollaborationManager | null>(null);
  const [session, setSession] = useState<CollaborationSession | null>(null);
  const [participants, setParticipants] = useState<CollabParticipant[]>([]);
  const [isConnected, setIsConnected] = useState(false);

  useEffect(() => {
    if (!contentPackageId) {
      setSession(null);
      setParticipants([]);
      setIsConnected(false);
      return;
    }

    const mgr = new CollaborationManager({ userId, displayName });
    managerRef.current = mgr;

    let cleanupPatch: (() => void) | null = null;
    let cleanupPresence: (() => void) | null = null;
    let cleanupCursor: (() => void) | null = null;
    let active = true;

    mgr.joinSession(contentPackageId).then((s) => {
      if (!active) {
        s.leave();
        return;
      }
      setSession(s);
      setIsConnected(true);

      cleanupPatch = s.onPatch((event) => {
        onRemotePatch?.(event);
      });
      cleanupPresence = s.onPresenceChange((p) => {
        setParticipants(p);
      });
      cleanupCursor = s.onCursor((event) => {
        onRemoteCursor?.(event);
      });
    });

    return () => {
      active = false;
      cleanupPatch?.();
      cleanupPresence?.();
      cleanupCursor?.();
      mgr.destroy();
      setSession(null);
      setIsConnected(false);
      setParticipants([]);
    };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [contentPackageId, userId, displayName]);

  const sendPatch = useCallback(
    (path: string, value: unknown) => {
      session?.sendPatch(path, value);
    },
    [session],
  );

  const sendCursor = useCallback(
    (selection: string | null) => {
      session?.sendCursor(selection);
    },
    [session],
  );

  return { session, participants, isConnected, sendPatch, sendCursor };
}
