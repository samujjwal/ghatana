/**
 * Awareness Payload Management
 *
 * Provides bounded awareness state management for collaborative canvas sessions.
 * Features:
 * - User presence tracking with automatic cleanup
 * - Payload size limits with truncation
 * - Editing indicators with throttling
 * - Presence counter with sub-second updates
 *
 * @module awarenessPayload
 */

/**
 * User presence state
 */
export interface PresenceState {
  /** Unique user identifier */
  userId: string;
  /** Display name */
  username: string;
  /** Avatar color (hex) */
  color: string;
  /** Current cursor position (canvas coordinates) */
  cursor?: { x: number; y: number };
  /** Currently selected node IDs */
  selection?: string[];
  /** Active editing target (node/edge ID being edited) */
  editing?: string;
  /** Viewport position and zoom */
  viewport?: { x: number; y: number; zoom: number };
  /** Last activity timestamp */
  lastActivity: number;
}

/**
 * Awareness payload (broadcasted to peers)
 */
export interface AwarenessPayload {
  /** User presence state */
  presence: PresenceState;
  /** Payload creation timestamp */
  timestamp: number;
  /** Payload size in bytes (estimated) */
  size: number;
  /** Whether payload was truncated due to size limit */
  truncated: boolean;
}

/**
 * Awareness update event
 */
export interface AwarenessUpdate {
  /** Added user IDs */
  added: string[];
  /** Updated user IDs */
  updated: string[];
  /** Removed user IDs */
  removed: string[];
  /** Current presence count */
  count: number;
}

/**
 * Awareness configuration
 */
export interface AwarenessConfig {
  /** Maximum payload size in bytes (default: 4KB) */
  maxPayloadSize: number;
  /** User inactivity timeout in ms (default: 30s) */
  inactivityTimeout: number;
  /** Editing indicator throttle in ms (default: 100ms) */
  editingThrottle: number;
  /** Presence update throttle in ms (default: 500ms) */
  presenceThrottle: number;
  /** Enable payload truncation warnings */
  warnOnTruncate: boolean;
}

/**
 * Default awareness configuration
 */
const DEFAULT_CONFIG: AwarenessConfig = {
  maxPayloadSize: 4096, // 4KB
  inactivityTimeout: 30000, // 30s
  editingThrottle: 100, // 100ms
  presenceThrottle: 500, // 500ms
  warnOnTruncate: true,
};

/**
 * Truncation warning
 */
export interface TruncationWarning {
  originalSize: number;
  truncatedSize: number;
  field: string;
  timestamp: number;
}

/**
 * Awareness manager
 *
 * Manages user presence, editing indicators, and payload size limits.
 */
export class AwarenessManager {
  private config: AwarenessConfig;
  private presenceMap = new Map<string, PresenceState>();
  private editingThrottles = new Map<string, number>();
  private presenceThrottles = new Map<string, number>();
  private listeners: Array<(update: AwarenessUpdate) => void> = [];
  private warnings: TruncationWarning[] = [];
  private cleanupInterval?: ReturnType<typeof setInterval>;

  /**
   *
   */
  constructor(config: Partial<AwarenessConfig> = {}) {
    this.config = { ...DEFAULT_CONFIG, ...config };
    this.startCleanupTimer();
  }

  /**
   * Set local user presence
   */
  setLocalPresence(presence: Partial<PresenceState> & { userId: string; username: string }): AwarenessPayload | null {
    const now = Date.now();
    const userId = presence.userId;

    // Check presence throttle
    const lastUpdate = this.presenceThrottles.get(userId) || 0;
    if (now - lastUpdate < this.config.presenceThrottle) {
      return null; // Throttled
    }

    // Get existing presence or create new
    const existing = this.presenceMap.get(userId);
    const newPresence: PresenceState = {
      ...existing,
      ...presence,
      lastActivity: now,
    } as PresenceState;

    const wasNew = !existing;
    this.presenceMap.set(userId, newPresence);
    this.presenceThrottles.set(userId, now);

    // Create payload
    const payload = this.createPayload(newPresence);

    // Emit update
    this.emitUpdate({
      added: wasNew ? [userId] : [],
      updated: wasNew ? [] : [userId],
      removed: [],
      count: this.presenceMap.size,
    });

    return payload;
  }

  /**
   * Set editing indicator
   */
  setEditing(userId: string, targetId: string | null): AwarenessPayload | null {
    const now = Date.now();

    // Check editing throttle
    const lastEdit = this.editingThrottles.get(userId) || 0;
    if (now - lastEdit < this.config.editingThrottle) {
      return null; // Throttled
    }

    const presence = this.presenceMap.get(userId);
    if (!presence) {
      return null; // User not present
    }

    // Update editing state
    presence.editing = targetId || undefined;
    presence.lastActivity = now;
    this.editingThrottles.set(userId, now);

    // Create payload
    const payload = this.createPayload(presence);

    // Emit update
    this.emitUpdate({
      added: [],
      updated: [userId],
      removed: [],
      count: this.presenceMap.size,
    });

    return payload;
  }

  /**
   * Apply remote awareness payload
   */
  applyRemotePayload(payload: AwarenessPayload): void {
    const userId = payload.presence.userId;
    const wasPresent = this.presenceMap.has(userId);

    this.presenceMap.set(userId, payload.presence);

    // Emit update
    this.emitUpdate({
      added: wasPresent ? [] : [userId],
      updated: wasPresent ? [userId] : [],
      removed: [],
      count: this.presenceMap.size,
    });
  }

  /**
   * Remove user presence
   */
  removePresence(userId: string): void {
    if (this.presenceMap.delete(userId)) {
      this.editingThrottles.delete(userId);
      this.presenceThrottles.delete(userId);

      // Emit update
      this.emitUpdate({
        added: [],
        updated: [],
        removed: [userId],
        count: this.presenceMap.size,
      });
    }
  }

  /**
   * Get all current presence states
   */
  getAllPresence(): PresenceState[] {
    return Array.from(this.presenceMap.values());
  }

  /**
   * Get presence for specific user
   */
  getPresence(userId: string): PresenceState | undefined {
    return this.presenceMap.get(userId);
  }

  /**
   * Get users currently editing
   */
  getEditingUsers(): Array<{ userId: string; targetId: string }> {
    const editing: Array<{ userId: string; targetId: string }> = [];
    for (const presence of this.presenceMap.values()) {
      if (presence.editing) {
        editing.push({ userId: presence.userId, targetId: presence.editing });
      }
    }
    return editing;
  }

  /**
   * Check if a target is being edited by any user
   */
  isBeingEdited(targetId: string): boolean {
    for (const presence of this.presenceMap.values()) {
      if (presence.editing === targetId) {
        return true;
      }
    }
    return false;
  }

  /**
   * Get presence count
   */
  getPresenceCount(): number {
    return this.presenceMap.size;
  }

  /**
   * Subscribe to awareness updates
   */
  subscribe(listener: (update: AwarenessUpdate) => void): () => void {
    this.listeners.push(listener);
    return () => {
      const index = this.listeners.indexOf(listener);
      if (index >= 0) {
        this.listeners.splice(index, 1);
      }
    };
  }

  /**
   * Get truncation warnings
   */
  getWarnings(): TruncationWarning[] {
    return [...this.warnings];
  }

  /**
   * Clear truncation warnings
   */
  clearWarnings(): void {
    this.warnings = [];
  }

  /**
   * Destroy manager and cleanup resources
   */
  destroy(): void {
    if (this.cleanupInterval) {
      clearInterval(this.cleanupInterval);
      this.cleanupInterval = undefined;
    }
    this.presenceMap.clear();
    this.editingThrottles.clear();
    this.presenceThrottles.clear();
    this.listeners = [];
    this.warnings = [];
  }

  /**
   * Create awareness payload from presence state
   */
  private createPayload(presence: PresenceState): AwarenessPayload {
    const timestamp = Date.now();
    let payload: AwarenessPayload = {
      presence,
      timestamp,
      size: 0,
      truncated: false,
    };

    // Estimate size
    const size = this.estimateSize(payload);
    payload.size = size;

    // Truncate if needed
    if (size > this.config.maxPayloadSize) {
      payload = this.truncatePayload(payload);
    }

    return payload;
  }

  /**
   * Estimate payload size in bytes
   */
  private estimateSize(payload: AwarenessPayload): number {
    // Simple JSON size estimation
    const json = JSON.stringify(payload);
    return new TextEncoder().encode(json).length;
  }

  /**
   * Truncate oversized payload
   */
  private truncatePayload(payload: AwarenessPayload): AwarenessPayload {
    const truncated = { ...payload, truncated: true };
    const presence = { ...payload.presence };

    // Truncate selection array if present
    if (presence.selection && presence.selection.length > 10) {
      const originalLength = presence.selection.length;
      presence.selection = presence.selection.slice(0, 10);

      if (this.config.warnOnTruncate) {
        this.warnings.push({
          originalSize: originalLength,
          truncatedSize: 10,
          field: 'selection',
          timestamp: Date.now(),
        });
      }
    }

    // Remove viewport if still too large
    const withoutViewport = { ...truncated, presence };
    if (this.estimateSize(withoutViewport) > this.config.maxPayloadSize) {
      delete presence.viewport;

      if (this.config.warnOnTruncate) {
        this.warnings.push({
          originalSize: payload.size,
          truncatedSize: this.estimateSize(withoutViewport),
          field: 'viewport',
          timestamp: Date.now(),
        });
      }
    }

    truncated.presence = presence;
    truncated.size = this.estimateSize(truncated);

    return truncated;
  }

  /**
   * Emit awareness update to listeners
   */
  private emitUpdate(update: AwarenessUpdate): void {
    for (const listener of this.listeners) {
      try {
        listener(update);
      } catch (error) {
        console.error('Awareness listener error:', error);
      }
    }
  }

  /**
   * Start cleanup timer for inactive users
   */
  private startCleanupTimer(): void {
    this.cleanupInterval = setInterval(() => {
      this.cleanupInactiveUsers();
    }, 10000); // Check every 10s
  }

  /**
   * Remove inactive users
   */
  private cleanupInactiveUsers(): void {
    const now = Date.now();
    const removed: string[] = [];

    for (const [userId, presence] of this.presenceMap.entries()) {
      if (now - presence.lastActivity > this.config.inactivityTimeout) {
        this.presenceMap.delete(userId);
        this.editingThrottles.delete(userId);
        this.presenceThrottles.delete(userId);
        removed.push(userId);
      }
    }

    if (removed.length > 0) {
      this.emitUpdate({
        added: [],
        updated: [],
        removed,
        count: this.presenceMap.size,
      });
    }
  }
}

/**
 * Create awareness manager instance
 */
export function createAwarenessManager(config?: Partial<AwarenessConfig>): AwarenessManager {
  return new AwarenessManager(config);
}

/**
 * Validate presence state
 */
export function validatePresence(presence: unknown): presence is PresenceState {
  if (typeof presence !== 'object' || presence === null) {
    return false;
  }

  const p = presence as Partial<PresenceState>;

  // Required fields
  if (typeof p.userId !== 'string' || !p.userId) {
    return false;
  }
  if (typeof p.username !== 'string' || !p.username) {
    return false;
  }
  if (typeof p.color !== 'string' || !/^#[0-9A-Fa-f]{6}$/.test(p.color)) {
    return false;
  }
  if (typeof p.lastActivity !== 'number' || p.lastActivity <= 0) {
    return false;
  }

  // Optional cursor
  if (p.cursor !== undefined) {
    if (typeof p.cursor !== 'object' || p.cursor === null) {
      return false;
    }
    if (typeof p.cursor.x !== 'number' || typeof p.cursor.y !== 'number') {
      return false;
    }
  }

  // Optional selection
  if (p.selection !== undefined) {
    if (!Array.isArray(p.selection)) {
      return false;
    }
    if (!p.selection.every((id) => typeof id === 'string')) {
      return false;
    }
  }

  // Optional editing
  if (p.editing !== undefined && typeof p.editing !== 'string') {
    return false;
  }

  // Optional viewport
  if (p.viewport !== undefined) {
    if (typeof p.viewport !== 'object' || p.viewport === null) {
      return false;
    }
    if (
      typeof p.viewport.x !== 'number' ||
      typeof p.viewport.y !== 'number' ||
      typeof p.viewport.zoom !== 'number'
    ) {
      return false;
    }
  }

  return true;
}

/**
 * Validate awareness payload
 */
export function validatePayload(payload: unknown): payload is AwarenessPayload {
  if (typeof payload !== 'object' || payload === null) {
    return false;
  }

  const p = payload as Partial<AwarenessPayload>;

  if (!validatePresence(p.presence)) {
    return false;
  }
  if (typeof p.timestamp !== 'number' || p.timestamp <= 0) {
    return false;
  }
  if (typeof p.size !== 'number' || p.size < 0) {
    return false;
  }
  if (typeof p.truncated !== 'boolean') {
    return false;
  }

  return true;
}
