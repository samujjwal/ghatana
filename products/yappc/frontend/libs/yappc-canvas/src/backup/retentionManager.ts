/**
 * Retention Manager - Retention policies and lifecycle management
 * 
 * Implements retention policies with cold storage archival,
 * soft delete with recovery window, and automatic tier transitions.
 */

/**
 * Storage tier types
 */
export type StorageTier = 'hot' | 'warm' | 'cold' | 'archived';

/**
 * Snapshot lifecycle state
 */
export type LifecycleState = 'active' | 'soft_deleted' | 'archived' | 'permanently_deleted';

/**
 * Retention policy configuration
 */
export interface RetentionPolicy {
  /** Policy ID */
  id: string;
  /** Policy name */
  name: string;
  /** Hot storage retention in days */
  hotRetentionDays: number;
  /** Warm storage retention in days */
  warmRetentionDays: number;
  /** Cold storage retention in days */
  coldRetentionDays: number;
  /** Total retention in days before permanent deletion */
  totalRetentionDays: number;
  /** Soft delete recovery window in days */
  softDeleteRecoveryDays: number;
  /** Enable automatic tier transitions */
  enableAutoTransition: boolean;
  /** Minimum snapshots to retain */
  minSnapshots?: number;
  /** Tags this policy applies to */
  tags?: string[];
}

/**
 * Snapshot with retention metadata
 */
export interface RetentionSnapshot {
  /** Snapshot ID */
  id: string;
  /** Creation timestamp */
  createdAt: number;
  /** Current storage tier */
  tier: StorageTier;
  /** Lifecycle state */
  state: LifecycleState;
  /** Soft delete timestamp */
  softDeletedAt?: number;
  /** Archive timestamp */
  archivedAt?: number;
  /** Last accessed timestamp */
  lastAccessedAt?: number;
  /** Snapshot size in bytes */
  size: number;
  /** Tags for policy matching */
  tags?: string[];
  /** Snapshot type */
  type: 'full' | 'diff';
  /** Has dependents (for diff chains) */
  hasDependents?: boolean;
}

/**
 * Tier transition event
 */
export interface TierTransition {
  /** Transition ID */
  id: string;
  /** Snapshot ID */
  snapshotId: string;
  /** From tier */
  fromTier: StorageTier;
  /** To tier */
  toTier: StorageTier;
  /** Transition timestamp */
  timestamp: number;
  /** Reason for transition */
  reason: string;
}

/**
 * Storage tier statistics
 */
export interface TierStatistics {
  /** Tier name */
  tier: StorageTier;
  /** Snapshot count */
  count: number;
  /** Total size in bytes */
  totalSize: number;
  /** Oldest snapshot timestamp */
  oldestSnapshot?: number;
  /** Newest snapshot timestamp */
  newestSnapshot?: number;
}

/**
 * Retention statistics
 */
export interface RetentionStatistics {
  /** Total snapshots */
  totalSnapshots: number;
  /** Active snapshots */
  activeSnapshots: number;
  /** Soft deleted snapshots */
  softDeletedSnapshots: number;
  /** Archived snapshots */
  archivedSnapshots: number;
  /** By tier */
  byTier: TierStatistics[];
  /** Total storage used */
  totalStorage: number;
  /** Transitions in last 24h */
  recentTransitions: number;
  /** Pending deletions */
  pendingDeletions: number;
}

/**
 * Retention Manager
 */
export class RetentionManager {
  private policies: Map<string, RetentionPolicy> = new Map();
  private snapshots: Map<string, RetentionSnapshot> = new Map();
  private transitions: TierTransition[] = [];
  private defaultPolicy: RetentionPolicy;
  private autoTransitionTimer?: ReturnType<typeof setInterval>;

  /**
   *
   */
  constructor(defaultPolicy?: Partial<RetentionPolicy>) {
    this.defaultPolicy = {
      id: 'default',
      name: 'Default Retention Policy',
      hotRetentionDays: 7,
      warmRetentionDays: 30,
      coldRetentionDays: 90,
      totalRetentionDays: 365,
      softDeleteRecoveryDays: 7,
      enableAutoTransition: true,
      minSnapshots: 5,
      ...defaultPolicy,
    };

    this.policies.set(this.defaultPolicy.id, this.defaultPolicy);
  }

  /**
   * Add retention policy
   */
  addPolicy(policy: RetentionPolicy): void {
    this.policies.set(policy.id, policy);
  }

  /**
   * Get policy by ID
   */
  getPolicy(id: string): RetentionPolicy | undefined {
    return this.policies.get(id);
  }

  /**
   * List all policies
   */
  listPolicies(): RetentionPolicy[] {
    return Array.from(this.policies.values());
  }

  /**
   * Update policy
   */
  updatePolicy(id: string, updates: Partial<RetentionPolicy>): boolean {
    const policy = this.policies.get(id);
    if (!policy) {
      return false;
    }

    Object.assign(policy, updates);
    return true;
  }

  /**
   * Delete policy
   */
  deletePolicy(id: string): boolean {
    if (id === 'default') {
      return false; // Cannot delete default policy
    }

    return this.policies.delete(id);
  }

  /**
   * Register snapshot for retention management
   */
  registerSnapshot(snapshot: Omit<RetentionSnapshot, 'tier' | 'state'>): RetentionSnapshot {
    const managedSnapshot: RetentionSnapshot = {
      ...snapshot,
      tier: 'hot',
      state: 'active',
      lastAccessedAt: Date.now(),
    };

    this.snapshots.set(snapshot.id, managedSnapshot);
    return managedSnapshot;
  }

  /**
   * Get snapshot
   */
  getSnapshot(id: string): RetentionSnapshot | undefined {
    const snapshot = this.snapshots.get(id);
    if (snapshot && snapshot.state === 'active') {
      // Update last accessed time
      snapshot.lastAccessedAt = Date.now();
    }
    return snapshot;
  }

  /**
   * List snapshots with optional filtering
   */
  listSnapshots(filter?: {
    tier?: StorageTier;
    state?: LifecycleState;
    tags?: string[];
    olderThan?: number;
    newerThan?: number;
  }): RetentionSnapshot[] {
    let snapshots = Array.from(this.snapshots.values());

    if (filter) {
      if (filter.tier) {
        snapshots = snapshots.filter(s => s.tier === filter.tier);
      }

      if (filter.state) {
        snapshots = snapshots.filter(s => s.state === filter.state);
      }

      if (filter.tags && filter.tags.length > 0) {
        snapshots = snapshots.filter(s =>
          s.tags?.some(tag => filter.tags!.includes(tag))
        );
      }

      if (filter.olderThan) {
        snapshots = snapshots.filter(s => s.createdAt < filter.olderThan!);
      }

      if (filter.newerThan) {
        snapshots = snapshots.filter(s => s.createdAt > filter.newerThan!);
      }
    }

    return snapshots.sort((a, b) => b.createdAt - a.createdAt);
  }

  /**
   * Soft delete snapshot
   */
  softDelete(id: string): boolean {
    const snapshot = this.snapshots.get(id);
    if (!snapshot || snapshot.state !== 'active') {
      return false;
    }

    // Check if snapshot has dependents
    if (snapshot.hasDependents) {
      return false; // Cannot delete snapshot with dependents
    }

    snapshot.state = 'soft_deleted';
    snapshot.softDeletedAt = Date.now();

    return true;
  }

  /**
   * Restore soft deleted snapshot
   */
  restore(id: string): boolean {
    const snapshot = this.snapshots.get(id);
    if (!snapshot || snapshot.state !== 'soft_deleted') {
      return false;
    }

    // Check if still within recovery window
    const policy = this.getPolicyForSnapshot(snapshot);
    const recoveryWindow = policy.softDeleteRecoveryDays * 24 * 60 * 60 * 1000;
    const deletedAge = Date.now() - (snapshot.softDeletedAt || 0);

    if (deletedAge > recoveryWindow) {
      return false; // Outside recovery window
    }

    snapshot.state = 'active';
    snapshot.softDeletedAt = undefined;

    return true;
  }

  /**
   * Permanently delete snapshot
   */
  permanentDelete(id: string): boolean {
    const snapshot = this.snapshots.get(id);
    if (!snapshot) {
      return false;
    }

    // Check if snapshot has dependents
    if (snapshot.hasDependents) {
      return false;
    }

    // Only allow permanent delete if soft deleted or archived
    if (snapshot.state !== 'soft_deleted' && snapshot.state !== 'archived') {
      return false;
    }

    snapshot.state = 'permanently_deleted';
    this.snapshots.delete(id);

    return true;
  }

  /**
   * Transition snapshot to different tier
   */
  transitionTier(id: string, toTier: StorageTier, reason: string = 'Manual transition'): boolean {
    const snapshot = this.snapshots.get(id);
    if (!snapshot || snapshot.state !== 'active') {
      return false;
    }

    const fromTier = snapshot.tier;
    if (fromTier === toTier) {
      return false; // Already in target tier
    }

    snapshot.tier = toTier;

    // Record transition
    this.recordTransition({
      id: this.generateId(),
      snapshotId: id,
      fromTier,
      toTier,
      timestamp: Date.now(),
      reason,
    });

    return true;
  }

  /**
   * Archive snapshot
   */
  archive(id: string): boolean {
    const snapshot = this.snapshots.get(id);
    if (!snapshot || snapshot.state !== 'active') {
      return false;
    }

    snapshot.state = 'archived';
    snapshot.archivedAt = Date.now();
    snapshot.tier = 'archived';

    this.recordTransition({
      id: this.generateId(),
      snapshotId: id,
      fromTier: snapshot.tier,
      toTier: 'archived',
      timestamp: Date.now(),
      reason: 'Archival',
    });

    return true;
  }

  /**
   * Run automatic tier transitions based on policies
   */
  runAutoTransitions(): {
    transitioned: string[];
    archived: string[];
    deleted: string[];
  } {
    const transitioned: string[] = [];
    const archived: string[] = [];
    const deleted: string[] = [];

    const now = Date.now();

    for (const snapshot of this.snapshots.values()) {
      const policy = this.getPolicyForSnapshot(snapshot);

      if (!policy.enableAutoTransition) {
        continue;
      }

      const age = now - snapshot.createdAt;
      const ageDays = age / (24 * 60 * 60 * 1000);

      // Handle soft deleted snapshots
      if (snapshot.state === 'soft_deleted') {
        const deletedAge = now - (snapshot.softDeletedAt || 0);
        const deletedAgeDays = deletedAge / (24 * 60 * 60 * 1000);

        if (deletedAgeDays > policy.softDeleteRecoveryDays) {
          if (this.permanentDelete(snapshot.id)) {
            deleted.push(snapshot.id);
          }
        }
        continue;
      }

      // Only transition active snapshots
      if (snapshot.state !== 'active') {
        continue;
      }

      // Check for archival (cold storage > retention threshold)
      if (ageDays > policy.coldRetentionDays && snapshot.tier === 'cold') {
        if (this.archive(snapshot.id)) {
          archived.push(snapshot.id);
        }
        continue;
      }

      // Check for permanent deletion (archived > total retention)
      if (snapshot.state === 'archived') {
        const archivedAge = now - (snapshot.archivedAt || 0);
        const archivedAgeDays = archivedAge / (24 * 60 * 60 * 1000);

        if (archivedAgeDays > (policy.totalRetentionDays - policy.coldRetentionDays)) {
          // Check minimum snapshots requirement
          const activeCount = this.listSnapshots({ state: 'active' }).length;
          if (!policy.minSnapshots || activeCount > policy.minSnapshots) {
            if (this.permanentDelete(snapshot.id)) {
              deleted.push(snapshot.id);
            }
          }
        }
        continue;
      }

      // Tier transitions based on age
      if (ageDays > policy.coldRetentionDays && snapshot.tier !== 'cold') {
        // Should be in cold tier
        if (this.transitionTier(snapshot.id, 'cold', 'Age-based transition')) {
          transitioned.push(snapshot.id);
        }
      } else if (ageDays > policy.warmRetentionDays && ageDays <= policy.coldRetentionDays && snapshot.tier !== 'warm') {
        // Should be in warm tier
        if (this.transitionTier(snapshot.id, 'warm', 'Age-based transition')) {
          transitioned.push(snapshot.id);
        }
      } else if (ageDays > policy.hotRetentionDays && ageDays <= policy.warmRetentionDays && snapshot.tier !== 'warm') {
        // Transition from hot to warm
        if (snapshot.tier === 'hot') {
          if (this.transitionTier(snapshot.id, 'warm', 'Age-based transition')) {
            transitioned.push(snapshot.id);
          }
        }
      }
    }

    return { transitioned, archived, deleted };
  }

  /**
   * Start automatic transition monitoring
   */
  startAutoTransitions(intervalMs: number = 60 * 60 * 1000): void {
    if (this.autoTransitionTimer) {
      return; // Already running
    }

    this.autoTransitionTimer = setInterval(() => {
      this.runAutoTransitions();
    }, intervalMs);
  }

  /**
   * Stop automatic transition monitoring
   */
  stopAutoTransitions(): void {
    if (this.autoTransitionTimer) {
      clearInterval(this.autoTransitionTimer);
      this.autoTransitionTimer = undefined;
    }
  }

  /**
   * Get transition history
   */
  getTransitionHistory(filter?: {
    snapshotId?: string;
    startDate?: number;
    endDate?: number;
  }): TierTransition[] {
    let transitions = [...this.transitions];

    if (filter) {
      if (filter.snapshotId) {
        transitions = transitions.filter(t => t.snapshotId === filter.snapshotId);
      }

      if (filter.startDate) {
        transitions = transitions.filter(t => t.timestamp >= filter.startDate!);
      }

      if (filter.endDate) {
        transitions = transitions.filter(t => t.timestamp <= filter.endDate!);
      }
    }

    return transitions.sort((a, b) => b.timestamp - a.timestamp);
  }

  /**
   * Get retention statistics
   */
  getStatistics(): RetentionStatistics {
    const snapshots = Array.from(this.snapshots.values());

    const activeSnapshots = snapshots.filter(s => s.state === 'active').length;
    const softDeletedSnapshots = snapshots.filter(s => s.state === 'soft_deleted').length;
    const archivedSnapshots = snapshots.filter(s => s.state === 'archived').length;

    // Calculate tier statistics
    const tiers: StorageTier[] = ['hot', 'warm', 'cold', 'archived'];
    const byTier: TierStatistics[] = tiers.map(tier => {
      const tierSnapshots = snapshots.filter(s => s.tier === tier);
      const totalSize = tierSnapshots.reduce((sum, s) => sum + s.size, 0);

      const timestamps = tierSnapshots.map(s => s.createdAt);
      const oldestSnapshot = timestamps.length > 0 ? Math.min(...timestamps) : undefined;
      const newestSnapshot = timestamps.length > 0 ? Math.max(...timestamps) : undefined;

      return {
        tier,
        count: tierSnapshots.length,
        totalSize,
        oldestSnapshot,
        newestSnapshot,
      };
    });

    const totalStorage = snapshots.reduce((sum, s) => sum + s.size, 0);

    // Recent transitions (last 24h)
    const twentyFourHoursAgo = Date.now() - (24 * 60 * 60 * 1000);
    const recentTransitions = this.transitions.filter(t => 
      t.timestamp >= twentyFourHoursAgo
    ).length;

    // Pending deletions (soft deleted past recovery window)
    const pendingDeletions = snapshots.filter(s => {
      if (s.state !== 'soft_deleted') {
        return false;
      }

      const policy = this.getPolicyForSnapshot(s);
      const recoveryWindow = policy.softDeleteRecoveryDays * 24 * 60 * 60 * 1000;
      const deletedAge = Date.now() - (s.softDeletedAt || 0);

      return deletedAge > recoveryWindow;
    }).length;

    return {
      totalSnapshots: snapshots.length,
      activeSnapshots,
      softDeletedSnapshots,
      archivedSnapshots,
      byTier,
      totalStorage,
      recentTransitions,
      pendingDeletions,
    };
  }

  /**
   * Get policy for snapshot
   */
  private getPolicyForSnapshot(snapshot: RetentionSnapshot): RetentionPolicy {
    // Find matching policy by tags
    if (snapshot.tags && snapshot.tags.length > 0) {
      for (const policy of this.policies.values()) {
        if (policy.tags && policy.tags.some(tag => snapshot.tags!.includes(tag))) {
          return policy;
        }
      }
    }

    // Return default policy
    return this.defaultPolicy;
  }

  /**
   * Record tier transition
   */
  private recordTransition(transition: TierTransition): void {
    this.transitions.push(transition);

    // Keep only last 1000 transitions
    if (this.transitions.length > 1000) {
      this.transitions = this.transitions.slice(-1000);
    }
  }

  /**
   * Generate unique ID
   */
  private generateId(): string {
    return `transition_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`;
  }
}

/**
 * Create retention manager
 */
export function createRetentionManager(defaultPolicy?: Partial<RetentionPolicy>): RetentionManager {
  return new RetentionManager(defaultPolicy);
}
