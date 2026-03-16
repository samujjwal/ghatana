/**
 * CollaborationManager — real-time multi-user editing for Content Explorer.
 *
 * Architecture:
 *  - Transport: browser-native WebSocket (same pattern as platform/realtime)
 *  - Conflict Resolution: Last-Write-Wins per field (sufficient for content metadata)
 *  - Cursor sharing: each participant's selection broadcasts a lightweight
 *    `CursorEvent` so co-editors can see who is looking at what.
 *  - Reconnection: exponential back-off (1 s → 2 s → 4 s … max 30 s)
 *
 * @doc.type class
 * @doc.purpose Real-time multi-user content collaboration via WebSocket
 * @doc.layer product
 * @doc.pattern Service
 */

// ---------------------------------------------------------------------------
// Message protocol
// ---------------------------------------------------------------------------

export type CollabMessageType =
  | "join"
  | "leave"
  | "cursor"
  | "patch"
  | "presence"
  | "error"
  | "ack";

export interface CollabParticipant {
  userId: string;
  displayName: string;
  color: string;
  isOnline: boolean;
  /** ISO 8601 timestamp of last activity. */
  lastSeen: string;
}

export interface CursorEvent {
  type: "cursor";
  userId: string;
  contentPackageId: string;
  selection: string | null; // field name being edited, null = none
}

/** A field-level patch — LWW merge per `path`. */
export interface PatchEvent {
  type: "patch";
  userId: string;
  contentPackageId: string;
  path: string;   // dot-notation field path, e.g. "title" or "learningObjectives.0"
  value: unknown;
  timestamp: string; // ISO 8601, used for LWW
}

export interface PresenceEvent {
  type: "presence";
  participants: CollabParticipant[];
}

export interface JoinEvent {
  type: "join";
  contentPackageId: string;
  userId: string;
  displayName: string;
  color: string;
}

export interface LeaveEvent {
  type: "leave";
  contentPackageId: string;
  userId: string;
}

export interface AckEvent {
  type: "ack";
  sessionId: string;
}

export interface ErrorEvent {
  type: "error";
  code: string;
  message: string;
}

export type CollabMessage =
  | CursorEvent
  | PatchEvent
  | PresenceEvent
  | JoinEvent
  | LeaveEvent
  | AckEvent
  | ErrorEvent;

// ---------------------------------------------------------------------------
// Session
// ---------------------------------------------------------------------------

export interface CollaborationSession {
  sessionId: string;
  contentPackageId: string;
  participants: CollabParticipant[];
  /** Call to send a field-level patch to all participants. */
  sendPatch(path: string, value: unknown): void;
  /** Call to broadcast current cursor/selection state. */
  sendCursor(selection: string | null): void;
  /** Register a callback for incoming patches from other users. */
  onPatch(handler: (event: PatchEvent) => void): () => void;
  /** Register a callback for participant list changes. */
  onPresenceChange(handler: (participants: CollabParticipant[]) => void): () => void;
  /** Register a callback for cursor events from other users. */
  onCursor(handler: (event: CursorEvent) => void): () => void;
  /** Gracefully leave the session and close the WebSocket. */
  leave(): void;
}

// ---------------------------------------------------------------------------
// CollaborationManager
// ---------------------------------------------------------------------------

const RECONNECT_BASE_MS = 1_000;
const RECONNECT_MAX_MS = 30_000;
const PARTICIPANT_COLORS = [
  "#6366f1", "#f59e0b", "#10b981", "#ef4444",
  "#3b82f6", "#8b5cf6", "#ec4899", "#14b8a6",
];

export interface CollaborationManagerConfig {
  wsUrl?: string;
  userId: string;
  displayName: string;
}

/**
 * CollaborationManager — manages WebSocket lifecycle and exposes a typed
 * session API for content package collaboration.
 *
 * Usage:
 * ```ts
 * const mgr = new CollaborationManager({ userId: 'u1', displayName: 'Alice' });
 * const session = await mgr.joinSession('pkg-abc123');
 * session.onPatch(event => applyPatch(event.path, event.value));
 * session.sendPatch('title', 'New Title');
 * ```
 */
export class CollaborationManager {
  private wsUrl: string;
  private userId: string;
  private displayName: string;
  private color: string;
  private ws: WebSocket | null = null;
  private reconnectAttempts = 0;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private currentSession: InternalSession | null = null;

  constructor(config: CollaborationManagerConfig) {
    this.wsUrl =
      config.wsUrl ||
      (typeof import.meta !== "undefined"
        ? (import.meta as { env?: Record<string, string> }).env?.["VITE_COLLAB_WS_URL"] ?? ""
        : "") ||
      inferWsUrl();
    this.userId = config.userId;
    this.displayName = config.displayName;
    // Deterministic color from userId hashCode
    const h = Math.abs(hashString(config.userId));
    this.color = PARTICIPANT_COLORS[h % PARTICIPANT_COLORS.length] ?? "#6366f1";
  }

  /**
   * Join a collaboration session for `contentPackageId`.
   *
   * Resolves once the server sends an `ack` message.  If the WebSocket URL is
   * not configured (dev / offline mode) the method still resolves but all
   * events are local-only — useful for testing without a server.
   */
  async joinSession(contentPackageId: string): Promise<CollaborationSession> {
    if (this.currentSession) {
      this.currentSession.leave();
    }

    const session = new InternalSession(
      contentPackageId,
      this.userId,
      this.displayName,
      this.color,
    );

    this.currentSession = session;

    if (!this.wsUrl) {
      // Offline/dev mode — resolve immediately with a stub session
      session.resolveAck(`offline-${crypto.randomUUID()}`);
    } else {
      this.connect(session);
    }

    return session.promise;
  }

  // ---------------------------------------------------------------------------
  // Private — WebSocket management
  // ---------------------------------------------------------------------------

  private connect(session: InternalSession): void {
    const url = `${this.wsUrl}/collab/${session.contentPackageId}?userId=${encodeURIComponent(this.userId)}`;

    try {
      this.ws = new WebSocket(url);
    } catch (err) {
      console.warn("[CollaborationManager] WebSocket construction failed:", err);
      session.resolveAck(`fallback-${crypto.randomUUID()}`);
      return;
    }

    this.ws.addEventListener("open", () => {
      this.reconnectAttempts = 0;
      const joinMsg: JoinEvent = {
        type: "join",
        contentPackageId: session.contentPackageId,
        userId: this.userId,
        displayName: this.displayName,
        color: this.color,
      };
      this.send(joinMsg);
    });

    this.ws.addEventListener("message", (event: MessageEvent<string>) => {
      try {
        const msg = JSON.parse(event.data) as CollabMessage;
        this.handleMessage(session, msg);
      } catch {
        // Malformed message — ignore
      }
    });

    this.ws.addEventListener("close", () => {
      this.scheduleReconnect(session);
    });

    this.ws.addEventListener("error", (err) => {
      console.warn("[CollaborationManager] WebSocket error:", err);
    });

    // Attach sender to session
    session.setSender((msg: CollabMessage) => this.send(msg));
  }

  private handleMessage(session: InternalSession, msg: CollabMessage): void {
    switch (msg.type) {
      case "ack":
        session.resolveAck(msg.sessionId);
        break;
      case "presence":
        session.updatePresence(msg.participants);
        break;
      case "patch":
        if (msg.userId !== this.userId) {
          session.dispatchPatch(msg);
        }
        break;
      case "cursor":
        if (msg.userId !== this.userId) {
          session.dispatchCursor(msg);
        }
        break;
      case "error":
        console.error("[CollaborationManager] Server error:", msg.code, msg.message);
        break;
      default:
        break;
    }
  }

  private send(msg: CollabMessage): void {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(msg));
    }
  }

  private scheduleReconnect(session: InternalSession): void {
    if (session.isLeft) return;
    const delay = Math.min(
      RECONNECT_BASE_MS * Math.pow(2, this.reconnectAttempts),
      RECONNECT_MAX_MS,
    );
    this.reconnectAttempts += 1;
    this.reconnectTimer = setTimeout(() => {
      this.connect(session);
    }, delay);
  }

  destroy(): void {
    if (this.reconnectTimer !== null) {
      clearTimeout(this.reconnectTimer);
    }
    this.ws?.close(1000, "CollaborationManager destroyed");
    this.ws = null;
    this.currentSession = null;
  }
}

// ---------------------------------------------------------------------------
// InternalSession — implements CollaborationSession
// ---------------------------------------------------------------------------

class InternalSession implements CollaborationSession {
  readonly contentPackageId: string;
  sessionId = "";
  participants: CollabParticipant[] = [];
  isLeft = false;

  private patchHandlers: Set<(e: PatchEvent) => void> = new Set();
  private presenceHandlers: Set<(p: CollabParticipant[]) => void> = new Set();
  private cursorHandlers: Set<(e: CursorEvent) => void> = new Set();
  private sender: ((msg: CollabMessage) => void) | null = null;

  private ackResolve!: (session: CollaborationSession) => void;
  readonly promise: Promise<CollaborationSession>;

  constructor(
    contentPackageId: string,
    private userId: string,
    private displayName: string,
    private color: string,
  ) {
    this.contentPackageId = contentPackageId;
    // Pre-populate with the local user so the UI shows "you" immediately
    this.participants = [{
      userId: this.userId,
      displayName: this.displayName,
      color: this.color,
      isOnline: true,
      lastSeen: new Date().toISOString(),
    }];
    this.promise = new Promise<CollaborationSession>((resolve) => {
      this.ackResolve = resolve;
    });
  }

  resolveAck(sessionId: string): void {
    this.sessionId = sessionId;
    this.ackResolve(this);
  }

  setSender(fn: (msg: CollabMessage) => void): void {
    this.sender = fn;
  }

  updatePresence(participants: CollabParticipant[]): void {
    this.participants = participants;
    this.presenceHandlers.forEach((h) => h(participants));
  }

  dispatchPatch(event: PatchEvent): void {
    this.patchHandlers.forEach((h) => h(event));
  }

  dispatchCursor(event: CursorEvent): void {
    this.cursorHandlers.forEach((h) => h(event));
  }

  // CollaborationSession interface
  sendPatch(path: string, value: unknown): void {
    if (this.isLeft) return;
    const event: PatchEvent = {
      type: "patch",
      userId: this.userId,
      contentPackageId: this.contentPackageId,
      path,
      value,
      timestamp: new Date().toISOString(),
    };
    this.sender?.(event);
  }

  sendCursor(selection: string | null): void {
    if (this.isLeft) return;
    const event: CursorEvent = {
      type: "cursor",
      userId: this.userId,
      contentPackageId: this.contentPackageId,
      selection,
    };
    this.sender?.(event);
  }

  onPatch(handler: (event: PatchEvent) => void): () => void {
    this.patchHandlers.add(handler);
    return () => this.patchHandlers.delete(handler);
  }

  onPresenceChange(handler: (participants: CollabParticipant[]) => void): () => void {
    this.presenceHandlers.add(handler);
    return () => this.presenceHandlers.delete(handler);
  }

  onCursor(handler: (event: CursorEvent) => void): () => void {
    this.cursorHandlers.add(handler);
    return () => this.cursorHandlers.delete(handler);
  }

  leave(): void {
    if (this.isLeft) return;
    this.isLeft = true;
    const event: LeaveEvent = {
      type: "leave",
      contentPackageId: this.contentPackageId,
      userId: this.userId,
    };
    this.sender?.(event);
    this.patchHandlers.clear();
    this.presenceHandlers.clear();
    this.cursorHandlers.clear();
  }
}

// ---------------------------------------------------------------------------
// Utilities
// ---------------------------------------------------------------------------

function hashString(s: string): number {
  let h = 0;
  for (let i = 0; i < s.length; i++) {
    h = (Math.imul(31, h) + s.charCodeAt(i)) | 0;
  }
  return h;
}

function inferWsUrl(): string {
  if (typeof window === "undefined") return "";
  const proto = window.location.protocol === "https:" ? "wss:" : "ws:";
  return `${proto}//${window.location.host}`;
}
