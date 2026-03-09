/**
 * Advisory Lock Manager
 *
 * Provides advisory locks for collaborative editing with automatic timeouts.
 * Features:
 * - Claim-based lock acquisition
 * - Admin override with notifications
 * - Automatic timeout and release
 * - Lock status tracking
 * - Event notifications
 *
 * @module lockManager
 */

/**
 * Lock status
 */
export type LockStatus = 'available' | 'locked' | 'pending_override';

/**
 * Lock holder information
 */
export interface LockHolder {
  /** User ID of lock holder */
  userId: string;
  /** Username for display */
  username: string;
  /** When the lock was acquired */
  acquiredAt: number;
  /** When the lock expires */
  expiresAt: number;
  /** Lock claim reason (optional) */
  reason?: string;
}

/**
 * Lock information
 */
export interface Lock {
  /** Resource identifier (node/edge ID) */
  resourceId: string;
  /** Lock status */
  status: LockStatus;
  /** Current lock holder */
  holder?: LockHolder;
  /** Override requestor (if override pending) */
  overrideRequest?: {
    userId: string;
    username: string;
    requestedAt: number;
    reason?: string;
  };
}

/**
 * Lock event types
 */
export type LockEventType =
  | 'acquired'
  | 'released'
  | 'timeout'
  | 'override_requested'
  | 'override_granted'
  | 'override_denied';

/**
 * Lock event
 */
export interface LockEvent {
  /** Event type */
  type: LockEventType;
  /** Resource ID */
  resourceId: string;
  /** User who triggered the event */
  userId: string;
  /** Username for display */
  username: string;
  /** Event timestamp */
  timestamp: number;
  /** Previous holder (for override events) */
  previousHolder?: LockHolder;
  /** Additional details */
  details?: Record<string, unknown>;
}

/**
 * Lock configuration
 */
export interface LockConfig {
  /** Default lock timeout in ms (default: 5 minutes) */
  defaultTimeout: number;
  /** Maximum lock timeout in ms (default: 30 minutes) */
  maxTimeout: number;
  /** Auto-release idle locks after this duration (default: 10 minutes) */
  idleTimeout: number;
  /** Enable admin override */
  enableAdminOverride: boolean;
  /** Admin user IDs */
  adminUserIds: Set<string>;
}

/**
 * Lock request options
 */
export interface LockRequest {
  /** User requesting the lock */
  userId: string;
  /** Username for display */
  username: string;
  /** Lock timeout in ms (optional, uses default) */
  timeout?: number;
  /** Reason for the lock (optional) */
  reason?: string;
}

/**
 * Lock result
 */
export interface LockResult {
  /** Whether the operation succeeded */
  success: boolean;
  /** Lock information (if successful) */
  lock?: Lock;
  /** Error message (if failed) */
  error?: string;
  /** Event generated (if successful) */
  event?: LockEvent;
}

/**
 * Default lock configuration
 */
const DEFAULT_CONFIG: LockConfig = {
  defaultTimeout: 5 * 60 * 1000, // 5 minutes
  maxTimeout: 30 * 60 * 1000, // 30 minutes
  idleTimeout: 10 * 60 * 1000, // 10 minutes
  enableAdminOverride: true,
  adminUserIds: new Set<string>(),
};

/**
 * Advisory lock manager
 *
 * Manages resource locks for collaborative editing with timeout and override support.
 */
export class LockManager {
  private config: LockConfig;
  private locks = new Map<string, Lock>();
  private listeners: Array<(event: LockEvent) => void> = [];
  private timeoutHandles = new Map<string, ReturnType<typeof setTimeout>>();
  private cleanupInterval?: ReturnType<typeof setInterval>;

  /**
   *
   */
  constructor(config: Partial<LockConfig> = {}) {
    this.config = {
      ...DEFAULT_CONFIG,
      ...config,
      adminUserIds: config.adminUserIds ? new Set(config.adminUserIds) : DEFAULT_CONFIG.adminUserIds,
    };
    this.startCleanupTimer();
  }

  /**
   * Attempt to acquire a lock on a resource
   */
  acquire(resourceId: string, request: LockRequest): LockResult {
    const now = Date.now();
    const existing = this.locks.get(resourceId);

    // Validate timeout
    const timeout = request.timeout || this.config.defaultTimeout;
    if (timeout > this.config.maxTimeout) {
      return {
        success: false,
        error: `Timeout exceeds maximum allowed (${this.config.maxTimeout}ms)`,
      };
    }

    // Check if already locked
    if (existing && existing.status === 'locked') {
      // Check if lock expired
      if (existing.holder && existing.holder.expiresAt <= now) {
        // Lock expired, release it
        this.forceRelease(resourceId, 'timeout');
      } else {
        return {
          success: false,
          error: `Resource locked by ${existing.holder?.username} until ${new Date(existing.holder?.expiresAt || 0).toISOString()}`,
          lock: existing,
        };
      }
    }

    // Acquire lock
    const holder: LockHolder = {
      userId: request.userId,
      username: request.username,
      acquiredAt: now,
      expiresAt: now + timeout,
      reason: request.reason,
    };

    const lock: Lock = {
      resourceId,
      status: 'locked',
      holder,
    };

    this.locks.set(resourceId, lock);

    // Set timeout
    this.setLockTimeout(resourceId, timeout);

    // Emit event
    const event: LockEvent = {
      type: 'acquired',
      resourceId,
      userId: request.userId,
      username: request.username,
      timestamp: now,
      details: { reason: request.reason, timeout },
    };

    this.emitEvent(event);

    return {
      success: true,
      lock,
      event,
    };
  }

  /**
   * Release a lock
   */
  release(resourceId: string, userId: string): LockResult {
    const lock = this.locks.get(resourceId);

    if (!lock || lock.status !== 'locked') {
      return {
        success: false,
        error: 'Resource is not locked',
      };
    }

    if (lock.holder?.userId !== userId && !this.isAdmin(userId)) {
      return {
        success: false,
        error: 'Only the lock holder or admin can release the lock',
        lock,
      };
    }

    // Release lock
    this.locks.delete(resourceId);
    this.clearLockTimeout(resourceId);

    // Emit event
    const event: LockEvent = {
      type: 'released',
      resourceId,
      userId,
      username: lock.holder?.username || 'unknown',
      timestamp: Date.now(),
    };

    this.emitEvent(event);

    return {
      success: true,
      event,
    };
  }

  /**
   * Request admin override of a lock
   */
  requestOverride(resourceId: string, request: LockRequest): LockResult {
    if (!this.config.enableAdminOverride) {
      return {
        success: false,
        error: 'Admin override is not enabled',
      };
    }

    if (!this.isAdmin(request.userId)) {
      return {
        success: false,
        error: 'Only admins can override locks',
      };
    }

    const lock = this.locks.get(resourceId);

    if (!lock || lock.status !== 'locked') {
      return {
        success: false,
        error: 'Resource is not locked',
      };
    }

    // Set override request
    lock.status = 'pending_override';
    lock.overrideRequest = {
      userId: request.userId,
      username: request.username,
      requestedAt: Date.now(),
      reason: request.reason,
    };

    this.locks.set(resourceId, lock);

    // Emit event to notify previous holder
    const event: LockEvent = {
      type: 'override_requested',
      resourceId,
      userId: request.userId,
      username: request.username,
      timestamp: Date.now(),
      previousHolder: lock.holder,
      details: { reason: request.reason },
    };

    this.emitEvent(event);

    return {
      success: true,
      lock,
      event,
    };
  }

  /**
   * Grant override and acquire lock
   */
  grantOverride(resourceId: string, adminUserId: string): LockResult {
    if (!this.isAdmin(adminUserId)) {
      return {
        success: false,
        error: 'Only admins can grant overrides',
      };
    }

    const lock = this.locks.get(resourceId);

    if (!lock || lock.status !== 'pending_override' || !lock.overrideRequest) {
      return {
        success: false,
        error: 'No pending override request for this resource',
      };
    }

    const previousHolder = lock.holder;
    const now = Date.now();
    const timeout = this.config.defaultTimeout;

    // Grant override
    const holder: LockHolder = {
      userId: lock.overrideRequest.userId,
      username: lock.overrideRequest.username,
      acquiredAt: now,
      expiresAt: now + timeout,
      reason: lock.overrideRequest.reason,
    };

    const updatedLock: Lock = {
      resourceId,
      status: 'locked',
      holder,
    };

    this.locks.set(resourceId, updatedLock);
    this.setLockTimeout(resourceId, timeout);

    // Emit event
    const event: LockEvent = {
      type: 'override_granted',
      resourceId,
      userId: holder.userId,
      username: holder.username,
      timestamp: now,
      previousHolder,
      details: { reason: holder.reason },
    };

    this.emitEvent(event);

    return {
      success: true,
      lock: updatedLock,
      event,
    };
  }

  /**
   * Deny override request
   */
  denyOverride(resourceId: string, adminUserId: string): LockResult {
    if (!this.isAdmin(adminUserId)) {
      return {
        success: false,
        error: 'Only admins can deny overrides',
      };
    }

    const lock = this.locks.get(resourceId);

    if (!lock || lock.status !== 'pending_override' || !lock.overrideRequest) {
      return {
        success: false,
        error: 'No pending override request for this resource',
      };
    }

    const overrideRequest = lock.overrideRequest;

    // Restore locked status
    lock.status = 'locked';
    delete lock.overrideRequest;

    this.locks.set(resourceId, lock);

    // Emit event
    const event: LockEvent = {
      type: 'override_denied',
      resourceId,
      userId: overrideRequest.userId,
      username: overrideRequest.username,
      timestamp: Date.now(),
      details: { deniedBy: adminUserId },
    };

    this.emitEvent(event);

    return {
      success: true,
      lock,
      event,
    };
  }

  /**
   * Get lock status for a resource
   */
  getLock(resourceId: string): Lock | undefined {
    return this.locks.get(resourceId);
  }

  /**
   * Check if a resource is locked
   */
  isLocked(resourceId: string): boolean {
    const lock = this.locks.get(resourceId);
    if (!lock || lock.status !== 'locked') {
      return false;
    }

    // Check if expired
    if (lock.holder && lock.holder.expiresAt <= Date.now()) {
      this.forceRelease(resourceId, 'timeout');
      return false;
    }

    return true;
  }

  /**
   * Get all locks
   */
  getAllLocks(): Lock[] {
    return Array.from(this.locks.values());
  }

  /**
   * Get locks held by a user
   */
  getLocksByUser(userId: string): Lock[] {
    return Array.from(this.locks.values()).filter((lock) => lock.holder?.userId === userId);
  }

  /**
   * Extend lock timeout
   */
  extend(resourceId: string, userId: string, additionalTime: number): LockResult {
    const lock = this.locks.get(resourceId);

    if (!lock || lock.status !== 'locked') {
      return {
        success: false,
        error: 'Resource is not locked',
      };
    }

    if (lock.holder?.userId !== userId && !this.isAdmin(userId)) {
      return {
        success: false,
        error: 'Only the lock holder or admin can extend the lock',
      };
    }

    const now = Date.now();
    const newExpiresAt = Math.min(lock.holder!.expiresAt + additionalTime, now + this.config.maxTimeout);

    lock.holder!.expiresAt = newExpiresAt;
    this.locks.set(resourceId, lock);

    // Update timeout
    this.clearLockTimeout(resourceId);
    this.setLockTimeout(resourceId, newExpiresAt - now);

    return {
      success: true,
      lock,
    };
  }

  /**
   * Subscribe to lock events
   */
  subscribe(listener: (event: LockEvent) => void): () => void {
    this.listeners.push(listener);
    return () => {
      const index = this.listeners.indexOf(listener);
      if (index >= 0) {
        this.listeners.splice(index, 1);
      }
    };
  }

  /**
   * Get configuration
   */
  getConfig(): LockConfig {
    return { ...this.config };
  }

  /**
   * Add admin user
   */
  addAdmin(userId: string): void {
    this.config.adminUserIds.add(userId);
  }

  /**
   * Remove admin user
   */
  removeAdmin(userId: string): void {
    this.config.adminUserIds.delete(userId);
  }

  /**
   * Check if user is admin
   */
  isAdmin(userId: string): boolean {
    return this.config.adminUserIds.has(userId);
  }

  /**
   * Destroy manager and cleanup resources
   */
  destroy(): void {
    if (this.cleanupInterval) {
      clearInterval(this.cleanupInterval);
      this.cleanupInterval = undefined;
    }

    for (const handle of this.timeoutHandles.values()) {
      clearTimeout(handle);
    }

    this.timeoutHandles.clear();
    this.locks.clear();
    this.listeners = [];
  }

  /**
   * Force release a lock
   */
  private forceRelease(resourceId: string, reason: 'timeout' | 'cleanup'): void {
    const lock = this.locks.get(resourceId);
    if (!lock) {
      return;
    }

    this.locks.delete(resourceId);
    this.clearLockTimeout(resourceId);

    // Emit event
    if (lock.holder) {
      const event: LockEvent = {
        type: 'timeout',
        resourceId,
        userId: lock.holder.userId,
        username: lock.holder.username,
        timestamp: Date.now(),
        details: { reason },
      };

      this.emitEvent(event);
    }
  }

  /**
   * Set lock timeout
   */
  private setLockTimeout(resourceId: string, timeout: number): void {
    this.clearLockTimeout(resourceId);

    const handle = setTimeout(() => {
      this.forceRelease(resourceId, 'timeout');
    }, timeout);

    this.timeoutHandles.set(resourceId, handle);
  }

  /**
   * Clear lock timeout
   */
  private clearLockTimeout(resourceId: string): void {
    const handle = this.timeoutHandles.get(resourceId);
    if (handle) {
      clearTimeout(handle);
      this.timeoutHandles.delete(resourceId);
    }
  }

  /**
   * Emit lock event
   */
  private emitEvent(event: LockEvent): void {
    for (const listener of this.listeners) {
      try {
        listener(event);
      } catch (error) {
        console.error('Lock event listener error:', error);
      }
    }
  }

  /**
   * Start cleanup timer for expired locks
   */
  private startCleanupTimer(): void {
    this.cleanupInterval = setInterval(() => {
      this.cleanupExpiredLocks();
    }, 30000); // Check every 30s
  }

  /**
   * Cleanup expired locks
   */
  private cleanupExpiredLocks(): void {
    const now = Date.now();

    for (const [resourceId, lock] of this.locks.entries()) {
      if (lock.holder && lock.holder.expiresAt <= now) {
        this.forceRelease(resourceId, 'cleanup');
      }
    }
  }
}

/**
 * Create lock manager instance
 */
export function createLockManager(config?: Partial<LockConfig>): LockManager {
  return new LockManager(config);
}

/**
 * Validate lock request
 */
export function validateLockRequest(request: unknown): request is LockRequest {
  if (typeof request !== 'object' || request === null) {
    return false;
  }

  const req = request as Partial<LockRequest>;

  if (typeof req.userId !== 'string' || !req.userId) {
    return false;
  }
  if (typeof req.username !== 'string' || !req.username) {
    return false;
  }
  if (req.timeout !== undefined && (typeof req.timeout !== 'number' || req.timeout <= 0)) {
    return false;
  }
  if (req.reason !== undefined && typeof req.reason !== 'string') {
    return false;
  }

  return true;
}
