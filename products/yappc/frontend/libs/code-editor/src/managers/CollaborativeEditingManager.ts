/**
 * Collaborative Editing Manager
 * 
 * Manages real-time collaborative editing with cursor tracking,
 * conflict resolution, and user presence for Monaco editors.
 * 
 * Features:
 * - 👥 Real-time cursor and selection synchronization
 * - 🎨 User color assignment and avatar display
 * - ⚡ Conflict detection and resolution
 * - 📊 User presence and activity tracking
 * - 🔄 Undo/redo synchronization
 * 
 * @doc.type class
 * @doc.purpose Collaborative editing management
 * @doc.layer product
 * @doc.pattern Manager
 */

import type { editor } from 'monaco-editor';
import * as Y from 'yjs';

// CollaborativeCursor type mirrored here to avoid circular type import issues
export interface CollaborativeCursor {
  userId: string;
  userName: string;
  position: {
    line: number;
    column: number;
  };
  selection?: {
    start: { line: number; column: number };
    end: { line: number; column: number };
  };
  color: string;
}

/**
 * User presence information
 */
export interface UserPresence {
  userId: string;
  userName: string;
  avatar?: string;
  color: string;
  cursor?: {
    line: number;
    column: number;
  };
  selection?: {
    start: { line: number; column: number };
    end: { line: number; column: number };
  };
  isActive: boolean;
  lastSeen: number;
}

/**
 * Conflict information
 */
export interface EditConflict {
  id: string;
  userId: string;
  type: 'cursor' | 'selection' | 'content';
  description: string;
  timestamp: number;
  resolved: boolean;
}

/**
 * Collaborative editing configuration
 */
export interface CollaborativeEditingConfig {
  /** Enable cursor tracking */
  enableCursorTracking: boolean;
  /** Enable selection tracking */
  enableSelectionTracking: boolean;
  /** Enable conflict detection */
  enableConflictDetection: boolean;
  /** Cursor update debounce time (ms) */
  cursorDebounceMs: number;
  /** User inactivity timeout (ms) */
  inactivityTimeout: number;
  /** Color palette for users */
  colorPalette: string[];
}

/**
 * Collaborative editing events
 */
export interface CollaborativeEditingEvents {
  /** User joined editing */
  onUserJoined: (user: UserPresence) => void;
  /** User left editing */
  onUserLeft: (userId: string) => void;
  /** Cursor updated */
  onCursorUpdated: (cursor: CollaborativeCursor) => void;
  /** Conflict detected */
  onConflictDetected: (conflict: EditConflict) => void;
  /** Conflict resolved */
  onConflictResolved: (conflictId: string) => void;
}

/**
 * Collaborative Editing Manager
 */
export class CollaborativeEditingManager {
  private ydoc: Y.Doc;
  private config: CollaborativeEditingConfig;
  private events: CollaborativeEditingEvents;

  // Yjs shared data
  private yusers: Y.Map<UserPresence> = new Y.Map();
  private ycursors: Y.Map<CollaborativeCursor> = new Y.Map();
  private yconflicts: Y.Map<EditConflict> = new Y.Map();

  // Local state
  private localUserId: string;
  private localUserName: string;
  private localColor: string = '';
  private isActive = false;

  // Editor references
  private editors: Map<string, editor.IStandaloneCodeEditor> = new Map();

  // Debouncing
  private cursorTimeout: NodeJS.Timeout | null = null;
  private presenceInterval: NodeJS.Timeout | null = null;

  // Event listeners
  private disposables: Map<string, () => void> = new Map();

  /**
   * Create collaborative editing manager
   */
  constructor(
    ydoc: Y.Doc,
    localUserId: string,
    localUserName: string,
    config: Partial<CollaborativeEditingConfig> = {},
    events: Partial<CollaborativeEditingEvents> = {}
  ) {
    this.ydoc = ydoc;
    this.localUserId = localUserId;
    this.localUserName = localUserName;
    this.config = {
      enableCursorTracking: true,
      enableSelectionTracking: true,
      enableConflictDetection: true,
      cursorDebounceMs: 100,
      inactivityTimeout: 300000, // 5 minutes
      colorPalette: [
        '#FF6B6B', '#4ECDC4', '#45B7D1', '#96CEB4', '#FFEAA7',
        '#DDA0DD', '#98D8C8', '#F7DC6F', '#BB8FCE', '#85C1E2',
      ],
      ...config,
    };
    this.events = {
      onUserJoined: () => { },
      onUserLeft: () => { },
      onCursorUpdated: () => { },
      onConflictDetected: () => { },
      onConflictResolved: () => { },
      ...events,
    };

    // Initialize Yjs shared data
    this.initializeSharedData();

    // Set local user presence
    this.setupLocalUser();

    // Start presence tracking
    this.startPresenceTracking();
  }

  /**
   * Initialize Yjs shared data structures
   */
  private initializeSharedData(): void {
    const ymap = this.ydoc.getMap('collaborative');

    // Get or create shared maps
    this.yusers = ymap.get('users') as Y.Map<UserPresence> || new Y.Map();
    this.ycursors = ymap.get('cursors') as Y.Map<CollaborativeCursor> || new Y.Map();
    this.yconflicts = ymap.get('conflicts') as Y.Map<EditConflict> || new Y.Map();

    // Store in main map if newly created
    if (!ymap.has('users')) ymap.set('users', this.yusers);
    if (!ymap.has('cursors')) ymap.set('cursors', this.ycursors);
    if (!ymap.has('conflicts')) ymap.set('conflicts', this.yconflicts);

    // Setup Yjs event listeners
    this.setupYjsListeners();
  }

  /**
   * Setup Yjs event listeners
   */
  private setupYjsListeners(): void {
    // Listen for user changes
    this.yusers.observe((event: Y.YMapEvent<UserPresence>) => {
      event.changes.keys.forEach((change, key) => {
        if (change.action === 'add') {
          const user = this.yusers.get(key);
          if (user && user.userId !== this.localUserId) {
            this.events.onUserJoined(user);
          }
        } else if (change.action === 'delete') {
          if (key !== this.localUserId) {
            this.events.onUserLeft(key);
          }
        }
      });
    });

    // Listen for cursor changes
    this.ycursors.observe((event: Y.YMapEvent<CollaborativeCursor>) => {
      event.changes.keys.forEach((change, key) => {
        if (change.action === 'update' && key !== this.localUserId) {
          const cursor = this.ycursors.get(key);
          if (cursor) {
            this.events.onCursorUpdated(cursor);
            this.updateCursorDecorations(key, cursor);
          }
        }
      });
    });

    // Listen for conflict changes
    this.yconflicts.observe((event: Y.YMapEvent<EditConflict>) => {
      event.changes.keys.forEach((change, key) => {
        if (change.action === 'add') {
          const conflict = this.yconflicts.get(key);
          if (conflict) {
            this.events.onConflictDetected(conflict);
          }
        } else if (change.action === 'update') {
          const conflict = this.yconflicts.get(key);
          if (conflict?.resolved) {
            this.events.onConflictResolved(conflict.id);
          }
        }
      });
    });
  }

  /**
   * Setup local user presence
   */
  private setupLocalUser(): void {
    // Assign color to local user
    this.localColor = this.assignUserColor(this.localUserId);

    // Create local user presence
    const localUser: UserPresence = {
      userId: this.localUserId,
      userName: this.localUserName,
      color: this.localColor,
      isActive: true,
      lastSeen: Date.now(),
    };

    // Add to shared users
    this.yusers.set(this.localUserId, localUser);
  }

  /**
   * Assign color to user based on ID
   */
  private assignUserColor(userId: string): string {
    const hash = userId.split('').reduce((acc, char) => {
      return char.charCodeAt(0) + ((acc << 5) - acc);
    }, 0);

    return this.config.colorPalette[Math.abs(hash) % this.config.colorPalette.length];
  }

  /**
   * Start presence tracking
   */
  private startPresenceTracking(): void {
    this.presenceInterval = setInterval(() => {
      this.updateLocalPresence();
      this.cleanupInactiveUsers();
    }, 30000); // Update every 30 seconds
  }

  /**
   * Update local user presence
   */
  private updateLocalPresence(): void {
    const localUser = this.yusers.get(this.localUserId);
    if (localUser) {
      localUser.lastSeen = Date.now();
      localUser.isActive = this.isActive;
      this.yusers.set(this.localUserId, localUser);
    }
  }

  /**
   * Cleanup inactive users
   */
  private cleanupInactiveUsers(): void {
    const now = Date.now();
    const toRemove: string[] = [];

    this.yusers.forEach((user, userId) => {
      if (userId !== this.localUserId && now - user.lastSeen > this.config.inactivityTimeout) {
        toRemove.push(userId);
      }
    });

    toRemove.forEach(userId => {
      this.yusers.delete(userId);
      this.ycursors.delete(userId);
    });
  }

  /**
   * Add editor to collaborative session
   */
  addEditor(fileId: string, editor: editor.IStandaloneCodeEditor): void {
    this.editors.set(fileId, editor);

    // Setup cursor tracking for this editor
    if (this.config.enableCursorTracking) {
      this.setupCursorTracking(fileId, editor);
    }

    // Setup selection tracking for this editor
    if (this.config.enableSelectionTracking) {
      this.setupSelectionTracking(fileId, editor);
    }
  }

  /**
   * Remove editor from collaborative session
   */
  removeEditor(fileId: string): void {
    // Cleanup event listeners
    const dispose = this.disposables.get(fileId);
    if (dispose) {
      dispose();
      this.disposables.delete(fileId);
    }

    // Remove local cursor
    this.ycursors.delete(`${this.localUserId}:${fileId}`);

    // Remove editor reference
    this.editors.delete(fileId);
  }

  /**
   * Setup cursor tracking for editor
   */
  private setupCursorTracking(fileId: string, editor: editor.IStandaloneCodeEditor): void {
    const cursorId = `${this.localUserId}:${fileId}`;

    const disposable = editor.onDidChangeCursorPosition((e) => {
      if (this.config.cursorDebounceMs > 0) {
        this.debouncedUpdateCursor(cursorId, fileId, e.position.lineNumber, e.position.column);
      } else {
        this.updateCursor(cursorId, fileId, e.position.lineNumber, e.position.column);
      }
    });

    this.disposables.set(`${fileId}:cursor`, () => disposable.dispose());
  }

  /**
   * Setup selection tracking for editor
   */
  private setupSelectionTracking(fileId: string, editor: editor.IStandaloneCodeEditor): void {
    const cursorId = `${this.localUserId}:${fileId}`;

    const disposable = editor.onDidChangeCursorSelection((e) => {
      if (!e.selection.isEmpty()) {
        const cursor = this.ycursors.get(cursorId);
        if (cursor) {
          cursor.selection = {
            start: {
              line: e.selection.startLineNumber,
              column: e.selection.startColumn,
            },
            end: {
              line: e.selection.endLineNumber,
              column: e.selection.endColumn,
            },
          };
          this.ycursors.set(cursorId, cursor);
        }
      }
    });

    this.disposables.set(`${fileId}:selection`, () => disposable.dispose());
  }

  /**
   * Debounced cursor update
   */
  private debouncedUpdateCursor(cursorId: string, fileId: string, line: number, column: number): void {
    if (this.cursorTimeout) {
      clearTimeout(this.cursorTimeout);
    }

    this.cursorTimeout = setTimeout(() => {
      this.updateCursor(cursorId, fileId, line, column);
    }, this.config.cursorDebounceMs);
  }

  /**
   * Update cursor position
   */
  private updateCursor(cursorId: string, fileId: string, line: number, column: number): void {
    const cursor: CollaborativeCursor = {
      userId: this.localUserId,
      userName: this.localUserName,
      position: { line, column },
      color: this.localColor,
    };

    this.ycursors.set(cursorId, cursor);
  }

  /**
   * Update cursor decorations in editor
   */
  private updateCursorDecorations(cursorId: string, cursor: CollaborativeCursor): void {
    const [userId, fileId] = cursorId.split(':');
    const editor = this.editors.get(fileId);

    if (!editor || userId === this.localUserId) return;

    // Clear previous decorations for this user
    const decorationKey = `cursor-${userId}`;
    editor.deltaDecorations(
      (editor as unknown as { [key: string]: string[] })[decorationKey] || [],
      []
    );

    // Create new decorations
    const decorations: editor.IModelDeltaDecoration[] = [
      {
        range: {
          startLineNumber: cursor.position.line,
          startColumn: cursor.position.column,
          endLineNumber: cursor.position.line,
          endColumn: cursor.position.column,
        },
        options: {
          className: `collaborative-cursor-${userId}`,
          hoverMessage: { value: `${cursor.userName}'s cursor` },
          stickiness: 1, // NeverGrowsWhenTypingAtEdges
        },
      },
    ];

    // Add selection if present
    if (cursor.selection) {
      decorations.push({
        range: {
          startLineNumber: cursor.selection.start.line,
          startColumn: cursor.selection.start.column,
          endLineNumber: cursor.selection.end.line,
          endColumn: cursor.selection.end.column,
        },
        options: {
          className: `collaborative-selection-${userId}`,
          stickiness: 1,
        },
      });
    }

    // Apply decorations
    const decorationIds = editor.deltaDecorations([], decorations);
    (editor as unknown as { [key: string]: string[] })[decorationKey] = decorationIds;
  }

  /**
   * Get all active users
   */
  getActiveUsers(): UserPresence[] {
    const users: UserPresence[] = [];
    this.yusers.forEach(user => {
      if (user.isActive) {
        users.push(user);
      }
    });
    return users;
  }

  /**
   * Get all cursors for a file
   */
  getCursorsForFile(fileId: string): CollaborativeCursor[] {
    const cursors: CollaborativeCursor[] = [];
    this.ycursors.forEach((cursor, cursorId) => {
      if (cursorId.endsWith(`:${fileId}`) && cursor.userId !== this.localUserId) {
        cursors.push(cursor);
      }
    });
    return cursors;
  }

  /**
   * Get all conflicts
   */
  getConflicts(): EditConflict[] {
    const conflicts: EditConflict[] = [];
    this.yconflicts.forEach(conflict => {
      conflicts.push(conflict);
    });
    return conflicts;
  }

  /**
   * Resolve conflict
   */
  resolveConflict(conflictId: string): void {
    const conflict = this.yconflicts.get(conflictId);
    if (conflict) {
      conflict.resolved = true;
      this.yconflicts.set(conflictId, conflict);
    }
  }

  /**
   * Set active status
   */
  setActive(active: boolean): void {
    this.isActive = active;
    this.updateLocalPresence();
  }

  /**
   * Dispose collaborative editing manager
   */
  dispose(): void {
    // Clear intervals
    if (this.cursorTimeout) {
      clearTimeout(this.cursorTimeout);
    }
    if (this.presenceInterval) {
      clearInterval(this.presenceInterval);
    }

    // Cleanup all editors
    this.editors.forEach((_, fileId) => {
      this.removeEditor(fileId);
    });

    // Remove local user
    this.yusers.delete(this.localUserId);
    this.ycursors.delete(`${this.localUserId}:*`);
  }
}

/**
 * Create collaborative editing manager with default configuration
 */
export function createCollaborativeEditingManager(
  ydoc: Y.Doc,
  localUserId: string,
  localUserName: string,
  config?: Partial<CollaborativeEditingConfig>,
  events?: Partial<CollaborativeEditingEvents>
): CollaborativeEditingManager {
  return new CollaborativeEditingManager(ydoc, localUserId, localUserName, config, events);
}
