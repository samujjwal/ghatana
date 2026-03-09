/**
 * Presence System
 *
 * @description Real-time user presence tracking for collaboration,
 * showing who's online and what they're working on.
 */

import { CollaborationManager, CollaborationUser } from './CollaborationManager';

// =============================================================================
// Types
// =============================================================================

export interface PresenceState {
  status: 'online' | 'away' | 'busy' | 'offline';
  activity?: string;
  location?: PresenceLocation;
  lastSeen: number;
}

export interface PresenceLocation {
  type: 'page' | 'file' | 'canvas' | 'document' | 'chat';
  path?: string;
  name?: string;
  id?: string;
}

export interface PresenceUser extends CollaborationUser {
  presence: PresenceState;
}

export type PresenceEventType = 'presence-change' | 'user-join' | 'user-leave';

export interface PresenceEvent {
  type: PresenceEventType;
  user?: PresenceUser;
  users?: PresenceUser[];
}

// =============================================================================
// Constants
// =============================================================================

const AWAY_TIMEOUT = 5 * 60 * 1000; // 5 minutes
const OFFLINE_TIMEOUT = 15 * 60 * 1000; // 15 minutes
const PRESENCE_UPDATE_INTERVAL = 30 * 1000; // 30 seconds

// =============================================================================
// Presence Manager Class
// =============================================================================

export class PresenceManager {
  private collab: CollaborationManager;
  private userId: string;
  private userName: string;
  private userColor: string;
  private presence: PresenceState;
  private users: Map<string, PresenceUser>;
  private listeners: Map<PresenceEventType, Set<(event: PresenceEvent) => void>>;
  private updateInterval: number | null = null;
  private activityTimeout: number | null = null;
  private lastActivity: number;

  constructor(
    collab: CollaborationManager,
    userId: string,
    userName: string,
    userColor: string
  ) {
    this.collab = collab;
    this.userId = userId;
    this.userName = userName;
    this.userColor = userColor;
    this.users = new Map();
    this.listeners = new Map();
    this.lastActivity = Date.now();

    this.presence = {
      status: 'online',
      lastSeen: Date.now(),
    };

    this.setupListeners();
    this.startPresenceUpdates();
    this.setupActivityTracking();
  }

  /**
   * Set up collaboration listeners
   */
  private setupListeners(): void {
    this.collab.on('awareness-change', ({ users }) => {
      this.handleAwarenessChange(users);
    });

    this.collab.on('connection-change', ({ connected }) => {
      if (connected) {
        this.setStatus('online');
      } else {
        this.setStatus('offline');
      }
    });
  }

  /**
   * Handle awareness changes
   */
  private handleAwarenessChange(users: CollaborationUser[]): void {
    const previousUsers = new Map(this.users);
    const currentUsers = new Map<string, PresenceUser>();

    users.forEach((user) => {
      const presenceUser: PresenceUser = {
        ...user,
        presence: (user as unknown).presence || {
          status: 'online',
          lastSeen: user.lastActive,
        },
      };
      currentUsers.set(user.id, presenceUser);
    });

    // Detect joins
    currentUsers.forEach((user, id) => {
      if (!previousUsers.has(id)) {
        this.emit('user-join', { user });
      }
    });

    // Detect leaves
    previousUsers.forEach((user, id) => {
      if (!currentUsers.has(id)) {
        this.emit('user-leave', { user });
      }
    });

    this.users = currentUsers;
    this.emit('presence-change', { users: this.getUsers() });
  }

  /**
   * Start periodic presence updates
   */
  private startPresenceUpdates(): void {
    this.updatePresence();
    this.updateInterval = window.setInterval(() => {
      this.updatePresence();
    }, PRESENCE_UPDATE_INTERVAL);
  }

  /**
   * Set up activity tracking
   */
  private setupActivityTracking(): void {
    const activityEvents = [
      'mousedown',
      'mousemove',
      'keydown',
      'scroll',
      'touchstart',
    ];

    const handleActivity = () => {
      this.recordActivity();
    };

    activityEvents.forEach((event) => {
      document.addEventListener(event, handleActivity, { passive: true });
    });
  }

  /**
   * Record user activity
   */
  recordActivity(): void {
    const now = Date.now();
    const wasAway = this.presence.status === 'away';

    this.lastActivity = now;

    // Return to online if was away
    if (wasAway) {
      this.setStatus('online');
    }

    // Reset away timeout
    if (this.activityTimeout) {
      clearTimeout(this.activityTimeout);
    }

    this.activityTimeout = window.setTimeout(() => {
      if (this.presence.status === 'online') {
        this.setStatus('away');
      }
    }, AWAY_TIMEOUT);
  }

  /**
   * Update presence in collaboration
   */
  private updatePresence(): void {
    // Check if should be marked away
    const timeSinceActivity = Date.now() - this.lastActivity;
    if (timeSinceActivity > AWAY_TIMEOUT && this.presence.status === 'online') {
      this.presence.status = 'away';
    }

    this.presence.lastSeen = Date.now();

    // Update in collaboration awareness
    // This would need to be integrated with the collaboration manager
  }

  // ===========================================================================
  // Status API
  // ===========================================================================

  /**
   * Get current presence status
   */
  getStatus(): PresenceState['status'] {
    return this.presence.status;
  }

  /**
   * Set presence status
   */
  setStatus(status: PresenceState['status']): void {
    this.presence.status = status;
    this.presence.lastSeen = Date.now();
    this.updatePresence();
  }

  /**
   * Set activity description
   */
  setActivity(activity: string): void {
    this.presence.activity = activity;
    this.updatePresence();
  }

  /**
   * Clear activity description
   */
  clearActivity(): void {
    this.presence.activity = undefined;
    this.updatePresence();
  }

  /**
   * Set current location
   */
  setLocation(location: PresenceLocation): void {
    this.presence.location = location;
    this.updatePresence();
  }

  /**
   * Clear current location
   */
  clearLocation(): void {
    this.presence.location = undefined;
    this.updatePresence();
  }

  /**
   * Get full presence state
   */
  getPresence(): PresenceState {
    return { ...this.presence };
  }

  // ===========================================================================
  // Users API
  // ===========================================================================

  /**
   * Get all present users
   */
  getUsers(): PresenceUser[] {
    return Array.from(this.users.values());
  }

  /**
   * Get online users
   */
  getOnlineUsers(): PresenceUser[] {
    return this.getUsers().filter(
      (user) => user.presence.status === 'online' || user.presence.status === 'busy'
    );
  }

  /**
   * Get away users
   */
  getAwayUsers(): PresenceUser[] {
    return this.getUsers().filter((user) => user.presence.status === 'away');
  }

  /**
   * Get user by ID
   */
  getUser(userId: string): PresenceUser | null {
    return this.users.get(userId) || null;
  }

  /**
   * Get users at location
   */
  getUsersAtLocation(location: Partial<PresenceLocation>): PresenceUser[] {
    return this.getUsers().filter((user) => {
      if (!user.presence.location) return false;
      if (location.type && user.presence.location.type !== location.type) return false;
      if (location.path && user.presence.location.path !== location.path) return false;
      if (location.id && user.presence.location.id !== location.id) return false;
      return true;
    });
  }

  /**
   * Get user count
   */
  getUserCount(): number {
    return this.users.size;
  }

  /**
   * Get online user count
   */
  getOnlineUserCount(): number {
    return this.getOnlineUsers().length;
  }

  // ===========================================================================
  // Event Handling
  // ===========================================================================

  /**
   * Subscribe to presence events
   */
  on(
    type: PresenceEventType,
    callback: (event: PresenceEvent) => void
  ): () => void {
    if (!this.listeners.has(type)) {
      this.listeners.set(type, new Set());
    }
    this.listeners.get(type)!.add(callback);

    return () => {
      this.listeners.get(type)?.delete(callback);
    };
  }

  /**
   * Emit a presence event
   */
  private emit(type: PresenceEventType, data: Partial<PresenceEvent>): void {
    const event: PresenceEvent = { type, ...data };
    this.listeners.get(type)?.forEach((callback) => callback(event));
  }

  // ===========================================================================
  // Cleanup
  // ===========================================================================

  /**
   * Destroy the presence manager
   */
  destroy(): void {
    if (this.updateInterval) {
      clearInterval(this.updateInterval);
      this.updateInterval = null;
    }
    if (this.activityTimeout) {
      clearTimeout(this.activityTimeout);
      this.activityTimeout = null;
    }
    this.listeners.clear();
    this.users.clear();
  }
}

// =============================================================================
// Presence Utilities
// =============================================================================

/**
 * Get color for presence status
 */
export function getPresenceStatusColor(status: PresenceState['status']): string {
  switch (status) {
    case 'online':
      return '#22c55e'; // green-500
    case 'away':
      return '#f59e0b'; // amber-500
    case 'busy':
      return '#ef4444'; // red-500
    case 'offline':
      return '#71717a'; // zinc-500
    default:
      return '#71717a';
  }
}

/**
 * Get label for presence status
 */
export function getPresenceStatusLabel(status: PresenceState['status']): string {
  switch (status) {
    case 'online':
      return 'Online';
    case 'away':
      return 'Away';
    case 'busy':
      return 'Busy';
    case 'offline':
      return 'Offline';
    default:
      return 'Unknown';
  }
}

/**
 * Format location for display
 */
export function formatPresenceLocation(location: PresenceLocation): string {
  switch (location.type) {
    case 'page':
      return location.name || location.path || 'Unknown page';
    case 'file':
      return location.name || location.path || 'Unknown file';
    case 'canvas':
      return `Canvas: ${location.name || 'Untitled'}`;
    case 'document':
      return `Document: ${location.name || 'Untitled'}`;
    case 'chat':
      return location.name || 'Chat';
    default:
      return 'Unknown location';
  }
}

export default PresenceManager;
