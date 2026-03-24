/**
 * Snapshot Cadence - Backup scheduling system
 * 
 * Implements automated backup scheduling with full/diff backup support,
 * metadata indexing, and checksum verification.
 */

/**
 * Backup types
 */
export type BackupType = 'full' | 'diff';

/**
 * Snapshot schedule configuration
 */
export interface ScheduleConfig {
  /** Enable/disable scheduling */
  enabled: boolean;
  /** Full backup interval in milliseconds */
  fullBackupInterval: number;
  /** Differential backup interval in milliseconds */
  diffBackupInterval: number;
  /** Maximum backups to retain */
  maxBackups?: number;
  /** Retention period in milliseconds */
  retentionPeriod?: number;
  /** Enable checksum verification */
  enableChecksums: boolean;
  /** Enable compression */
  enableCompression: boolean;
}

/**
 * Snapshot metadata
 */
export interface SnapshotMetadata {
  /** Unique snapshot ID */
  id: string;
  /** Backup type */
  type: BackupType;
  /** Creation timestamp */
  timestamp: number;
  /** Canvas state checksum */
  checksum: string;
  /** Snapshot size in bytes */
  size: number;
  /** Is compressed */
  compressed: boolean;
  /** Parent snapshot ID (for diffs) */
  parentId?: string;
  /** Description/label */
  description?: string;
  /** Creator user ID */
  createdBy?: string;
  /** Tags for categorization */
  tags?: string[];
  /** Verification status */
  verified: boolean;
  /** Verification timestamp */
  verifiedAt?: number;
}

/**
 * Snapshot data structure
 */
export interface Snapshot {
  /** Metadata */
  metadata: SnapshotMetadata;
  /** Canvas data (stringified JSON) */
  data: string;
  /** Changes from parent (for diffs) */
  diff?: SnapshotDiff;
}

/**
 * Snapshot diff structure
 */
export interface SnapshotDiff {
  /** Added nodes */
  addedNodes: string[];
  /** Modified nodes */
  modifiedNodes: string[];
  /** Removed nodes */
  removedNodes: string[];
  /** Added edges */
  addedEdges: string[];
  /** Removed edges */
  removedEdges: string[];
  /** Metadata changes */
  metadataChanges: Record<string, unknown>;
}

/**
 * Backup schedule entry
 */
export interface ScheduleEntry {
  /** Schedule ID */
  id: string;
  /** Backup type */
  type: BackupType;
  /** Next scheduled time */
  nextRun: number;
  /** Last run time */
  lastRun?: number;
  /** Interval in milliseconds */
  interval: number;
  /** Is active */
  active: boolean;
}

/**
 * Backup statistics
 */
export interface BackupStatistics {
  /** Total snapshots */
  totalSnapshots: number;
  /** Full backups */
  fullBackups: number;
  /** Differential backups */
  diffBackups: number;
  /** Total size in bytes */
  totalSize: number;
  /** Average snapshot size */
  averageSize: number;
  /** Last backup time */
  lastBackupTime?: number;
  /** Next scheduled backup */
  nextBackupTime?: number;
  /** Verified snapshots */
  verifiedSnapshots: number;
  /** Failed verifications */
  failedVerifications: number;
}

/**
 * Snapshot scheduler
 */
export class SnapshotScheduler {
  private config: ScheduleConfig;
  private snapshots: Map<string, Snapshot> = new Map();
  private schedules: Map<string, ScheduleEntry> = new Map();
  private timers: Map<string, ReturnType<typeof setTimeout>> = new Map();
  private snapshotListeners: Array<(snapshot: Snapshot) => void> = [];
  private currentState: string = '';

  /**
   *
   */
  constructor(config?: Partial<ScheduleConfig>) {
    this.config = {
      enabled: true,
      fullBackupInterval: 24 * 60 * 60 * 1000, // 24 hours
      diffBackupInterval: 60 * 60 * 1000, // 1 hour
      maxBackups: 30,
      enableChecksums: true,
      enableCompression: false,
      ...config,
    };

    // Always initialize schedules, but only start if enabled
    this.initializeSchedules();

    if (this.config.enabled) {
      this.start();
    }
  }

  /**
   * Initialize backup schedules
   */
  private initializeSchedules(): void {
    // Full backup schedule
    const fullSchedule: ScheduleEntry = {
      id: 'full-backup',
      type: 'full',
      nextRun: Date.now() + this.config.fullBackupInterval,
      interval: this.config.fullBackupInterval,
      active: true,
    };
    this.schedules.set(fullSchedule.id, fullSchedule);

    // Diff backup schedule
    const diffSchedule: ScheduleEntry = {
      id: 'diff-backup',
      type: 'diff',
      nextRun: Date.now() + this.config.diffBackupInterval,
      interval: this.config.diffBackupInterval,
      active: true,
    };
    this.schedules.set(diffSchedule.id, diffSchedule);
  }

  /**
   * Start scheduling
   */
  start(): void {
    if (!this.config.enabled) {
      return;
    }

    for (const schedule of this.schedules.values()) {
      if (schedule.active) {
        this.scheduleNext(schedule);
      }
    }
  }

  /**
   * Stop scheduling
   */
  stop(): void {
    for (const timer of this.timers.values()) {
      clearTimeout(timer);
    }
    this.timers.clear();
  }

  /**
   * Schedule next backup
   */
  private scheduleNext(schedule: ScheduleEntry): void {
    // Clear existing timer
    const existingTimer = this.timers.get(schedule.id);
    if (existingTimer) {
      clearTimeout(existingTimer);
    }

    // Calculate delay
    const now = Date.now();
    const delay = Math.max(0, schedule.nextRun - now);

    // Schedule backup
    const timer = setTimeout(() => {
      this.executeBackup(schedule);
    }, delay);

    this.timers.set(schedule.id, timer);
  }

  /**
   * Execute scheduled backup
   */
  private executeBackup(schedule: ScheduleEntry): void {
    if (!this.currentState) {
      // No state to backup
      this.scheduleNext(schedule);
      return;
    }

    // Create snapshot
    const snapshot = this.createSnapshot(schedule.type);

    // Update schedule
    schedule.lastRun = Date.now();
    schedule.nextRun = schedule.lastRun + schedule.interval;

    // Notify listeners
    this.notifyListeners(snapshot);

    // Schedule next
    this.scheduleNext(schedule);
  }

  /**
   * Update current canvas state
   */
  updateState(state: string): void {
    this.currentState = state;
  }

  /**
   * Create snapshot manually
   */
  createSnapshot(
    type: BackupType,
    options?: {
      description?: string;
      createdBy?: string;
      tags?: string[];
    }
  ): Snapshot {
    const id = this.generateId();
    const timestamp = Date.now();

    // Get parent for diff
    let parentId: string | undefined;
    let diff: SnapshotDiff | undefined;

    if (type === 'diff') {
      const lastFull = this.getLastSnapshot('full');
      if (lastFull) {
        parentId = lastFull.metadata.id;
        diff = this.calculateDiff(lastFull.data, this.currentState);
      } else {
        // No full backup yet, create full instead
        type = 'full';
      }
    }

    // Calculate checksum
    const checksum = this.config.enableChecksums
      ? this.calculateChecksum(this.currentState)
      : '';

    // Calculate size
    const size = new Blob([this.currentState]).size;

    // Create metadata
    const metadata: SnapshotMetadata = {
      id,
      type,
      timestamp,
      checksum,
      size,
      compressed: this.config.enableCompression,
      parentId,
      description: options?.description,
      createdBy: options?.createdBy,
      tags: options?.tags,
      verified: false,
    };

    // Create snapshot
    const snapshot: Snapshot = {
      metadata,
      data: this.currentState,
      diff,
    };

    // Store snapshot
    this.snapshots.set(id, snapshot);

    // Cleanup old snapshots
    this.cleanupOldSnapshots();

    // Notify listeners
    this.notifyListeners(snapshot);

    return snapshot;
  }

  /**
   * Get snapshot by ID
   */
  getSnapshot(id: string): Snapshot | undefined {
    return this.snapshots.get(id);
  }

  /**
   * List snapshots with optional filtering
   */
  listSnapshots(filter?: {
    type?: BackupType;
    startDate?: number;
    endDate?: number;
    tags?: string[];
    createdBy?: string;
    verified?: boolean;
  }): Snapshot[] {
    let snapshots = Array.from(this.snapshots.values());

    if (filter) {
      if (filter.type) {
        snapshots = snapshots.filter(s => s.metadata.type === filter.type);
      }

      if (filter.startDate) {
        snapshots = snapshots.filter(s => s.metadata.timestamp >= filter.startDate!);
      }

      if (filter.endDate) {
        snapshots = snapshots.filter(s => s.metadata.timestamp <= filter.endDate!);
      }

      if (filter.tags && filter.tags.length > 0) {
        snapshots = snapshots.filter(s =>
          s.metadata.tags?.some(tag => filter.tags!.includes(tag))
        );
      }

      if (filter.createdBy) {
        snapshots = snapshots.filter(s => s.metadata.createdBy === filter.createdBy);
      }

      if (filter.verified !== undefined) {
        snapshots = snapshots.filter(s => s.metadata.verified === filter.verified);
      }
    }

    return snapshots.sort((a, b) => b.metadata.timestamp - a.metadata.timestamp);
  }

  /**
   * Delete snapshot
   */
  deleteSnapshot(id: string): boolean {
    // Check if snapshot has dependents (diffs)
    const dependents = Array.from(this.snapshots.values()).filter(
      s => s.metadata.parentId === id
    );

    if (dependents.length > 0) {
      return false; // Cannot delete snapshot with dependents
    }

    return this.snapshots.delete(id);
  }

  /**
   * Verify snapshot integrity
   */
  verifySnapshot(id: string): boolean {
    const snapshot = this.snapshots.get(id);
    if (!snapshot) {
      return false;
    }

    if (!this.config.enableChecksums) {
      snapshot.metadata.verified = true;
      snapshot.metadata.verifiedAt = Date.now();
      return true;
    }

    // Recalculate checksum
    const currentChecksum = this.calculateChecksum(snapshot.data);
    const isValid = currentChecksum === snapshot.metadata.checksum;

    snapshot.metadata.verified = isValid;
    snapshot.metadata.verifiedAt = Date.now();

    return isValid;
  }

  /**
   * Restore from snapshot
   */
  restore(id: string): string | null {
    const snapshot = this.snapshots.get(id);
    if (!snapshot) {
      return null;
    }

    // Verify before restore
    if (this.config.enableChecksums && !snapshot.metadata.verified) {
      if (!this.verifySnapshot(id)) {
        return null; // Verification failed
      }
    }

    // For diff snapshots, need to reconstruct from parent
    if (snapshot.metadata.type === 'diff' && snapshot.metadata.parentId) {
      const parentData = this.restore(snapshot.metadata.parentId);
      if (!parentData) {
        return null;
      }

      return this.applyDiff(parentData, snapshot.diff!);
    }

    return snapshot.data;
  }

  /**
   * Get schedule by ID
   */
  getSchedule(id: string): ScheduleEntry | undefined {
    return this.schedules.get(id);
  }

  /**
   * List all schedules
   */
  listSchedules(): ScheduleEntry[] {
    return Array.from(this.schedules.values());
  }

  /**
   * Update schedule
   */
  updateSchedule(id: string, updates: Partial<ScheduleEntry>): boolean {
    const schedule = this.schedules.get(id);
    if (!schedule) {
      return false;
    }

    Object.assign(schedule, updates);

    // Reschedule if active
    if (schedule.active) {
      this.scheduleNext(schedule);
    }

    return true;
  }

  /**
   * Pause schedule
   */
  pauseSchedule(id: string): boolean {
    return this.updateSchedule(id, { active: false });
  }

  /**
   * Resume schedule
   */
  resumeSchedule(id: string): boolean {
    const schedule = this.schedules.get(id);
    if (!schedule) {
      return false;
    }

    schedule.active = true;
    schedule.nextRun = Date.now() + schedule.interval;
    this.scheduleNext(schedule);

    return true;
  }

  /**
   * Get backup statistics
   */
  getStatistics(): BackupStatistics {
    const snapshots = Array.from(this.snapshots.values());

    const fullBackups = snapshots.filter(s => s.metadata.type === 'full').length;
    const diffBackups = snapshots.filter(s => s.metadata.type === 'diff').length;
    const totalSize = snapshots.reduce((sum, s) => sum + s.metadata.size, 0);
    const verifiedSnapshots = snapshots.filter(s => s.metadata.verified).length;
    const failedVerifications = snapshots.filter(
      s => s.metadata.verifiedAt && !s.metadata.verified
    ).length;

    const lastBackup = snapshots.sort((a, b) => 
      b.metadata.timestamp - a.metadata.timestamp
    )[0];

    const nextBackup = Array.from(this.schedules.values())
      .filter(s => s.active)
      .sort((a, b) => a.nextRun - b.nextRun)[0];

    return {
      totalSnapshots: snapshots.length,
      fullBackups,
      diffBackups,
      totalSize,
      averageSize: snapshots.length > 0 ? totalSize / snapshots.length : 0,
      lastBackupTime: lastBackup?.metadata.timestamp,
      nextBackupTime: nextBackup?.nextRun,
      verifiedSnapshots,
      failedVerifications,
    };
  }

  /**
   * Get configuration
   */
  getConfig(): ScheduleConfig {
    return { ...this.config };
  }

  /**
   * Update configuration
   */
  updateConfig(updates: Partial<ScheduleConfig>): void {
    Object.assign(this.config, updates);

    // Update schedule intervals if changed
    if (updates.fullBackupInterval) {
      const fullSchedule = this.schedules.get('full-backup');
      if (fullSchedule) {
        fullSchedule.interval = updates.fullBackupInterval;
      }
    }

    if (updates.diffBackupInterval) {
      const diffSchedule = this.schedules.get('diff-backup');
      if (diffSchedule) {
        diffSchedule.interval = updates.diffBackupInterval;
      }
    }

    // Handle enable/disable
    if (updates.enabled !== undefined) {
      if (updates.enabled) {
        this.start();
      } else {
        this.stop();
      }
    }
  }

  /**
   * Add snapshot listener
   */
  onSnapshot(listener: (snapshot: Snapshot) => void): () => void {
    this.snapshotListeners.push(listener);

    // Return unsubscribe function
    return () => {
      const index = this.snapshotListeners.indexOf(listener);
      if (index >= 0) {
        this.snapshotListeners.splice(index, 1);
      }
    };
  }

  /**
   * Notify listeners
   */
  private notifyListeners(snapshot: Snapshot): void {
    for (const listener of this.snapshotListeners) {
      listener(snapshot);
    }
  }

  /**
   * Generate unique ID
   */
  private generateId(): string {
    return `snapshot_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`;
  }

  /**
   * Calculate checksum
   */
  private calculateChecksum(data: string): string {
    // Simple hash function (for production, use crypto.subtle.digest)
    let hash = 0;
    for (let i = 0; i < data.length; i++) {
      const char = data.charCodeAt(i);
      hash = ((hash << 5) - hash) + char;
      hash = hash & hash; // Convert to 32-bit integer
    }
    return hash.toString(16);
  }

  /**
   * Calculate diff between two states
   */
  private calculateDiff(oldState: string, newState: string): SnapshotDiff {
    // Parse states
    let oldData: unknown;
    let newData: unknown;

    try {
      oldData = JSON.parse(oldState);
      newData = JSON.parse(newState);
    } catch {
      // If parsing fails, treat as simple string diff
      return {
        addedNodes: [],
        modifiedNodes: [],
        removedNodes: [],
        addedEdges: [],
        removedEdges: [],
        metadataChanges: {},
      };
    }

    // Calculate node changes
    const oldNodes = new Set(oldData.nodes?.map((n: unknown) => n.id) || []);
    const newNodes = new Set(newData.nodes?.map((n: unknown) => n.id) || []);

    const addedNodes = Array.from(newNodes).filter(id => !oldNodes.has(id)) as string[];
    const removedNodes = Array.from(oldNodes).filter(id => !newNodes.has(id)) as string[];
    const modifiedNodes: string[] = [];

    // Check for modifications
    for (const nodeId of newNodes) {
      if (oldNodes.has(nodeId)) {
        const oldNode = oldData.nodes.find((n: unknown) => n.id === nodeId);
        const newNode = newData.nodes.find((n: unknown) => n.id === nodeId);

        if (JSON.stringify(oldNode) !== JSON.stringify(newNode)) {
          modifiedNodes.push(nodeId as string);
        }
      }
    }

    // Calculate edge changes
    const oldEdges = new Set(oldData.edges?.map((e: unknown) => `${e.source}-${e.target}`) || []);
    const newEdges = new Set(newData.edges?.map((e: unknown) => `${e.source}-${e.target}`) || []);

    const addedEdges = Array.from(newEdges).filter(id => !oldEdges.has(id)) as string[];
    const removedEdges = Array.from(oldEdges).filter(id => !newEdges.has(id)) as string[];

    return {
      addedNodes,
      modifiedNodes,
      removedNodes,
      addedEdges,
      removedEdges,
      metadataChanges: {},
    };
  }

  /**
   * Apply diff to base state
   */
  private applyDiff(baseState: string, diff: SnapshotDiff): string {
    // For now, return base state
    // In production, would apply diff operations
    return baseState;
  }

  /**
   * Get last snapshot of type
   */
  private getLastSnapshot(type?: BackupType): Snapshot | undefined {
    const snapshots = type
      ? Array.from(this.snapshots.values()).filter(s => s.metadata.type === type)
      : Array.from(this.snapshots.values());

    return snapshots.sort((a, b) => 
      b.metadata.timestamp - a.metadata.timestamp
    )[0];
  }

  /**
   * Cleanup old snapshots based on retention policy
   */
  private cleanupOldSnapshots(): void {
    const snapshots = Array.from(this.snapshots.values())
      .sort((a, b) => b.metadata.timestamp - a.metadata.timestamp);

    // Remove by max count
    if (this.config.maxBackups && snapshots.length > this.config.maxBackups) {
      const toRemove = snapshots.slice(this.config.maxBackups);
      for (const snapshot of toRemove) {
        // Only remove if no dependents
        const dependents = Array.from(this.snapshots.values()).filter(
          s => s.metadata.parentId === snapshot.metadata.id
        );
        if (dependents.length === 0) {
          this.snapshots.delete(snapshot.metadata.id);
        }
      }
    }

    // Remove by retention period
    if (this.config.retentionPeriod) {
      const cutoff = Date.now() - this.config.retentionPeriod;
      for (const snapshot of snapshots) {
        if (snapshot.metadata.timestamp < cutoff) {
          // Only remove if no dependents
          const dependents = Array.from(this.snapshots.values()).filter(
            s => s.metadata.parentId === snapshot.metadata.id
          );
          if (dependents.length === 0) {
            this.snapshots.delete(snapshot.metadata.id);
          }
        }
      }
    }
  }
}

/**
 * Create snapshot scheduler
 */
export function createSnapshotScheduler(config?: Partial<ScheduleConfig>): SnapshotScheduler {
  return new SnapshotScheduler(config);
}
