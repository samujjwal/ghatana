/**
 * Canvas Change Management System
 * 
 * Provides change management capabilities for canvas-based applications:
 * - Architecture Decision Records (ADR)
 * - Release notes generation
 * - Change review board workflows
 * - Approval tracking
 * - Change calendar management
 * - Rollback planning
 * 
 * @module operations/changeManagement
 */

/**
 * ADR status
 */
export type ADRStatus =
  | 'proposed' // Initial proposal
  | 'accepted' // Decision approved
  | 'deprecated' // Being phased out
  | 'superseded'; // Replaced by another ADR

/**
 * Change approval status
 */
export type ApprovalStatus =
  | 'pending' // Awaiting review
  | 'approved' // Change approved
  | 'rejected' // Change rejected
  | 'conditional'; // Approved with conditions

/**
 * Change risk level
 */
export type RiskLevel = 'low' | 'medium' | 'high' | 'critical';

/**
 * Architecture Decision Record
 */
export interface ADR {
  id: string;
  number: number;
  title: string;
  status: ADRStatus;
  context: string;
  decision: string;
  consequences: {
    positive: string[];
    negative: string[];
    risks: string[];
  };
  alternatives: {
    title: string;
    description: string;
    rejectionReason: string;
  }[];
  supersededBy?: string;
  supersedes?: string[];
  relatedADRs: string[];
  tags: string[];
  author: string;
  createdAt: Date;
  updatedAt: Date;
}

/**
 * Change request
 */
export interface ChangeRequest {
  id: string;
  title: string;
  description: string;
  riskLevel: RiskLevel;
  impact: {
    services: string[];
    users: 'none' | 'some' | 'all';
    downtime: number; // milliseconds
  };
  implementationPlan: string;
  rollbackPlan: string;
  testing: {
    unit: boolean;
    integration: boolean;
    e2e: boolean;
    manual: boolean;
  };
  scheduledFor?: Date;
  approvalStatus: ApprovalStatus;
  approvers: ChangeApproval[];
  requester: string;
  assignee?: string;
  tags: string[];
  adrReferences: string[];
  createdAt: Date;
  updatedAt: Date;
}

/**
 * Change approval record
 */
export interface ChangeApproval {
  approver: string;
  status: ApprovalStatus;
  comments: string;
  approvedAt?: Date;
  conditions?: string[];
}

/**
 * Release note entry
 */
export interface ReleaseNote {
  id: string;
  version: string;
  releaseDate: Date;
  sections: {
    features: ReleaseNoteItem[];
    bugFixes: ReleaseNoteItem[];
    breaking: ReleaseNoteItem[];
    deprecations: ReleaseNoteItem[];
    improvements: ReleaseNoteItem[];
  };
  migration?: {
    title: string;
    steps: string[];
    rollbackSteps: string[];
  };
  contributors: string[];
  changeRequests: string[];
  createdAt: Date;
  updatedAt: Date;
}

/**
 * Release note item
 */
export interface ReleaseNoteItem {
  description: string;
  ticketId?: string;
  author?: string;
}

/**
 * Change calendar event
 */
export interface ChangeEvent {
  id: string;
  title: string;
  changeRequestId: string;
  scheduledTime: Date;
  estimatedDuration: number; // milliseconds
  actualStartTime?: Date;
  actualEndTime?: Date;
  status: 'scheduled' | 'in-progress' | 'completed' | 'cancelled' | 'rolled-back';
  maintenanceWindow?: {
    start: Date;
    end: Date;
  };
  notifyChannels: string[];
  createdAt: Date;
  updatedAt: Date;
}

/**
 * Configuration for change management system
 */
export interface ChangeManagementConfig {
  requireApprovalForRiskLevels?: RiskLevel[];
  minApprovers?: number;
  changeRetentionDays?: number;
  autoGenerateReleaseNotes?: boolean;
  maintenanceWindowBufferMinutes?: number;
}

/**
 * Change Management Manager
 * 
 * Manages the change management lifecycle including:
 * - ADR tracking and documentation
 * - Change request workflows
 * - Approval processes
 * - Release notes generation
 * - Change calendar management
 */
export class ChangeManagementManager {
  private adrs: Map<string, ADR> = new Map();
  private changes: Map<string, ChangeRequest> = new Map();
  private releaseNotes: Map<string, ReleaseNote> = new Map();
  private calendar: Map<string, ChangeEvent> = new Map();
  private config: Required<ChangeManagementConfig>;
  private adrCounter = 1;
  private changeCounter = 0;
  private eventCounter = 0;

  /**
   *
   */
  constructor(config: ChangeManagementConfig = {}) {
    this.config = {
      requireApprovalForRiskLevels: config.requireApprovalForRiskLevels || [
        'medium',
        'high',
        'critical',
      ],
      minApprovers: config.minApprovers || 2,
      changeRetentionDays: config.changeRetentionDays || 365,
      autoGenerateReleaseNotes: config.autoGenerateReleaseNotes ?? true,
      maintenanceWindowBufferMinutes: config.maintenanceWindowBufferMinutes || 30,
    };
  }

  /**
   * Create ADR
   */
  createADR(
    params: Omit<ADR, 'id' | 'number' | 'createdAt' | 'updatedAt'>
  ): ADR {
    const adr: ADR = {
      ...params,
      id: this.generateADRId(this.adrCounter),
      number: this.adrCounter++,
      createdAt: new Date(),
      updatedAt: new Date(),
    };

    this.adrs.set(adr.id, adr);
    return adr;
  }

  /**
   * Get ADR by ID
   */
  getADR(id: string): ADR | null {
    return this.adrs.get(id) ?? null;
  }

  /**
   * Get ADR by number
   */
  getADRByNumber(number: number): ADR | null {
    return Array.from(this.adrs.values()).find((a) => a.number === number) ?? null;
  }

  /**
   * Get all ADRs
   */
  getAllADRs(): ADR[] {
    return Array.from(this.adrs.values()).sort((a, b) => a.number - b.number);
  }

  /**
   * Get ADRs by status
   */
  getADRsByStatus(status: ADRStatus): ADR[] {
    return Array.from(this.adrs.values()).filter((a) => a.status === status);
  }

  /**
   * Update ADR status
   */
  updateADRStatus(id: string, status: ADRStatus, supersededBy?: string): void {
    const adr = this.adrs.get(id);
    if (!adr) {
      throw new Error(`ADR ${id} not found`);
    }

    adr.status = status;
    adr.updatedAt = new Date();

    if (status === 'superseded' && supersededBy) {
      adr.supersededBy = supersededBy;
    }
  }

  /**
   * Export ADR as Markdown
   */
  exportADRMarkdown(id: string): string {
    const adr = this.adrs.get(id);
    if (!adr) {
      throw new Error(`ADR ${id} not found`);
    }

    const lines: string[] = [];
    lines.push(`# ADR-${adr.number.toString().padStart(4, '0')}: ${adr.title}`);
    lines.push('');
    lines.push(`**Status:** ${adr.status}`);
    lines.push(`**Author:** ${adr.author}`);
    lines.push(`**Date:** ${adr.createdAt.toISOString().split('T')[0]}`);
    lines.push('');

    if (adr.supersededBy) {
      lines.push(`**Superseded by:** ADR-${adr.supersededBy}`);
      lines.push('');
    }

    if (adr.supersedes && adr.supersedes.length > 0) {
      lines.push(`**Supersedes:** ${adr.supersedes.join(', ')}`);
      lines.push('');
    }

    lines.push('## Context');
    lines.push(adr.context);
    lines.push('');

    lines.push('## Decision');
    lines.push(adr.decision);
    lines.push('');

    lines.push('## Consequences');
    lines.push('### Positive');
    adr.consequences.positive.forEach((p) => lines.push(`- ${p}`));
    lines.push('');
    lines.push('### Negative');
    adr.consequences.negative.forEach((n) => lines.push(`- ${n}`));
    lines.push('');
    lines.push('### Risks');
    adr.consequences.risks.forEach((r) => lines.push(`- ${r}`));
    lines.push('');

    if (adr.alternatives.length > 0) {
      lines.push('## Alternatives Considered');
      adr.alternatives.forEach((alt) => {
        lines.push(`### ${alt.title}`);
        lines.push(alt.description);
        lines.push(`**Rejected because:** ${alt.rejectionReason}`);
        lines.push('');
      });
    }

    return lines.join('\n');
  }

  /**
   * Create change request
   */
  createChangeRequest(
    params: Omit<ChangeRequest, 'id' | 'approvers' | 'createdAt' | 'updatedAt'>
  ): ChangeRequest {
    const change: ChangeRequest = {
      ...params,
      id: this.generateChangeId(params.riskLevel),
      approvers: [],
      createdAt: new Date(),
      updatedAt: new Date(),
    };

    this.changes.set(change.id, change);
    return change;
  }

  /**
   * Get change request by ID
   */
  getChangeRequest(id: string): ChangeRequest | null {
    return this.changes.get(id) ?? null;
  }

  /**
   * Get all change requests
   */
  getAllChangeRequests(): ChangeRequest[] {
    return Array.from(this.changes.values());
  }

  /**
   * Get change requests by approval status
   */
  getChangeRequestsByStatus(status: ApprovalStatus): ChangeRequest[] {
    return Array.from(this.changes.values()).filter(
      (c) => c.approvalStatus === status
    );
  }

  /**
   * Get change requests by risk level
   */
  getChangeRequestsByRiskLevel(riskLevel: RiskLevel): ChangeRequest[] {
    return Array.from(this.changes.values()).filter(
      (c) => c.riskLevel === riskLevel
    );
  }

  /**
   * Add approval to change request
   */
  addApproval(
    changeId: string,
    approver: string,
    status: ApprovalStatus,
    comments: string,
    conditions?: string[]
  ): void {
    const change = this.changes.get(changeId);
    if (!change) {
      throw new Error(`Change request ${changeId} not found`);
    }

    const approval: ChangeApproval = {
      approver,
      status,
      comments,
      approvedAt: new Date(),
      conditions,
    };

    change.approvers.push(approval);
    change.updatedAt = new Date();

    // Update overall approval status
    this.updateChangeApprovalStatus(change);
  }

  /**
   * Schedule change
   */
  scheduleChange(changeId: string, scheduledTime: Date): ChangeEvent {
    const change = this.changes.get(changeId);
    if (!change) {
      throw new Error(`Change request ${changeId} not found`);
    }

    if (change.approvalStatus !== 'approved') {
      throw new Error(`Change ${changeId} is not approved`);
    }

    change.scheduledFor = scheduledTime;
    change.updatedAt = new Date();

    const event: ChangeEvent = {
      id: this.generateEventId(changeId),
      title: change.title,
      changeRequestId: changeId,
      scheduledTime,
      estimatedDuration: change.impact.downtime,
      status: 'scheduled',
      maintenanceWindow: {
        start: new Date(
          scheduledTime.getTime() -
            this.config.maintenanceWindowBufferMinutes * 60 * 1000
        ),
        end: new Date(
          scheduledTime.getTime() +
            change.impact.downtime +
            this.config.maintenanceWindowBufferMinutes * 60 * 1000
        ),
      },
      notifyChannels: [],
      createdAt: new Date(),
      updatedAt: new Date(),
    };

    this.calendar.set(event.id, event);
    return event;
  }

  /**
   * Start change execution
   */
  startChange(eventId: string): void {
    const event = this.calendar.get(eventId);
    if (!event) {
      throw new Error(`Event ${eventId} not found`);
    }

    event.status = 'in-progress';
    event.actualStartTime = new Date();
    event.updatedAt = new Date();
  }

  /**
   * Complete change execution
   */
  completeChange(eventId: string): void {
    const event = this.calendar.get(eventId);
    if (!event) {
      throw new Error(`Event ${eventId} not found`);
    }

    event.status = 'completed';
    event.actualEndTime = new Date();
    event.updatedAt = new Date();
  }

  /**
   * Cancel change
   */
  cancelChange(eventId: string): void {
    const event = this.calendar.get(eventId);
    if (!event) {
      throw new Error(`Event ${eventId} not found`);
    }

    event.status = 'cancelled';
    event.updatedAt = new Date();
  }

  /**
   * Rollback change
   */
  rollbackChange(eventId: string): void {
    const event = this.calendar.get(eventId);
    if (!event) {
      throw new Error(`Event ${eventId} not found`);
    }

    event.status = 'rolled-back';
    event.actualEndTime = new Date();
    event.updatedAt = new Date();
  }

  /**
   * Get upcoming changes
   */
  getUpcomingChanges(withinDays: number = 7): ChangeEvent[] {
    const now = Date.now();
    const futureLimit = now + withinDays * 24 * 60 * 60 * 1000;

    return Array.from(this.calendar.values())
      .filter(
        (e) =>
          e.scheduledTime.getTime() > now &&
          e.scheduledTime.getTime() < futureLimit &&
          e.status === 'scheduled'
      )
      .sort((a, b) => a.scheduledTime.getTime() - b.scheduledTime.getTime());
  }

  /**
   * Create release note
   */
  createReleaseNote(
    params: Omit<ReleaseNote, 'id' | 'createdAt' | 'updatedAt'>
  ): ReleaseNote {
    const release: ReleaseNote = {
      ...params,
      id: this.generateReleaseNoteId(params.version),
      createdAt: new Date(),
      updatedAt: new Date(),
    };

    this.releaseNotes.set(release.id, release);
    return release;
  }

  /**
   * Get release note by ID
   */
  getReleaseNote(id: string): ReleaseNote | null {
    return this.releaseNotes.get(id) ?? null;
  }

  /**
   * Get release note by version
   */
  getReleaseNoteByVersion(version: string): ReleaseNote | null {
    return (
      Array.from(this.releaseNotes.values()).find((r) => r.version === version) ??
      null
    );
  }

  /**
   * Get all release notes
   */
  getAllReleaseNotes(): ReleaseNote[] {
    return Array.from(this.releaseNotes.values()).sort((a, b) =>
      b.releaseDate.getTime() - a.releaseDate.getTime()
    );
  }

  /**
   * Export release note as Markdown
   */
  exportReleaseNoteMarkdown(id: string): string {
    const release = this.releaseNotes.get(id);
    if (!release) {
      throw new Error(`Release note ${id} not found`);
    }

    const lines: string[] = [];
    lines.push(`# Release ${release.version}`);
    lines.push(`**Date:** ${release.releaseDate.toISOString().split('T')[0]}`);
    lines.push('');

    if (release.sections.features.length > 0) {
      lines.push('## ✨ Features');
      release.sections.features.forEach((item) => {
        lines.push(`- ${item.description}`);
      });
      lines.push('');
    }

    if (release.sections.improvements.length > 0) {
      lines.push('## 🚀 Improvements');
      release.sections.improvements.forEach((item) => {
        lines.push(`- ${item.description}`);
      });
      lines.push('');
    }

    if (release.sections.bugFixes.length > 0) {
      lines.push('## 🐛 Bug Fixes');
      release.sections.bugFixes.forEach((item) => {
        lines.push(`- ${item.description}`);
      });
      lines.push('');
    }

    if (release.sections.breaking.length > 0) {
      lines.push('## ⚠️  Breaking Changes');
      release.sections.breaking.forEach((item) => {
        lines.push(`- ${item.description}`);
      });
      lines.push('');
    }

    if (release.sections.deprecations.length > 0) {
      lines.push('## 🗑️  Deprecations');
      release.sections.deprecations.forEach((item) => {
        lines.push(`- ${item.description}`);
      });
      lines.push('');
    }

    if (release.migration) {
      lines.push('## 📦 Migration Guide');
      lines.push(release.migration.title);
      lines.push('');
      lines.push('### Steps');
      release.migration.steps.forEach((step, i) => {
        lines.push(`${i + 1}. ${step}`);
      });
      lines.push('');
      lines.push('### Rollback');
      release.migration.rollbackSteps.forEach((step, i) => {
        lines.push(`${i + 1}. ${step}`);
      });
      lines.push('');
    }

    if (release.contributors.length > 0) {
      lines.push('## 👏 Contributors');
      lines.push(release.contributors.join(', '));
      lines.push('');
    }

    return lines.join('\n');
  }

  /**
   * Clean up old changes
   */
  cleanupOldChanges(): number {
    const cutoffDate = new Date(
      Date.now() - this.config.changeRetentionDays * 24 * 60 * 60 * 1000
    );

    let removed = 0;
    for (const [id, change] of this.changes.entries()) {
      if (change.createdAt < cutoffDate) {
        this.changes.delete(id);
        removed++;
      }
    }

    return removed;
  }

  /**
   * Get change management statistics
   */
  getStats(): {
    totalADRs: number;
    adrsByStatus: Record<ADRStatus, number>;
    totalChanges: number;
    changesByStatus: Record<ApprovalStatus, number>;
    changesByRisk: Record<RiskLevel, number>;
    scheduledChanges: number;
    releaseNotes: number;
  } {
    const adrs = Array.from(this.adrs.values());
    const changes = Array.from(this.changes.values());

    return {
      totalADRs: adrs.length,
      adrsByStatus: {
        proposed: adrs.filter((a) => a.status === 'proposed').length,
        accepted: adrs.filter((a) => a.status === 'accepted').length,
        deprecated: adrs.filter((a) => a.status === 'deprecated').length,
        superseded: adrs.filter((a) => a.status === 'superseded').length,
      },
      totalChanges: changes.length,
      changesByStatus: {
        pending: changes.filter((c) => c.approvalStatus === 'pending').length,
        approved: changes.filter((c) => c.approvalStatus === 'approved').length,
        rejected: changes.filter((c) => c.approvalStatus === 'rejected').length,
        conditional: changes.filter((c) => c.approvalStatus === 'conditional')
          .length,
      },
      changesByRisk: {
        low: changes.filter((c) => c.riskLevel === 'low').length,
        medium: changes.filter((c) => c.riskLevel === 'medium').length,
        high: changes.filter((c) => c.riskLevel === 'high').length,
        critical: changes.filter((c) => c.riskLevel === 'critical').length,
      },
      scheduledChanges: Array.from(this.calendar.values()).filter(
        (e) => e.status === 'scheduled'
      ).length,
      releaseNotes: this.releaseNotes.size,
    };
  }

  /**
   * Reset manager state
   */
  reset(): void {
    this.adrs.clear();
    this.changes.clear();
    this.releaseNotes.clear();
    this.calendar.clear();
    this.adrCounter = 1;
    this.changeCounter = 0;
    this.eventCounter = 0;
  }

  /**
   * Update change approval status based on approvals
   */
  private updateChangeApprovalStatus(change: ChangeRequest): void {
    const approved = change.approvers.filter((a) => a.status === 'approved');
    const rejected = change.approvers.filter((a) => a.status === 'rejected');
    const conditional = change.approvers.filter(
      (a) => a.status === 'conditional'
    );

    if (rejected.length > 0) {
      change.approvalStatus = 'rejected';
    } else if (approved.length >= this.config.minApprovers) {
      change.approvalStatus = conditional.length > 0 ? 'conditional' : 'approved';
    } else {
      change.approvalStatus = 'pending';
    }
  }

  /**
   *
   */
  private generateADRId(number: number): string {
    return `adr-${number.toString().padStart(4, '0')}`;
  }

  /**
   *
   */
  private generateChangeId(riskLevel: RiskLevel): string {
    return `change-${riskLevel}-${Date.now()}-${this.changeCounter++}`;
  }

  /**
   *
   */
  private generateEventId(changeId: string): string {
    return `event-${changeId}-${Date.now()}-${this.eventCounter++}`;
  }

  /**
   *
   */
  private generateReleaseNoteId(version: string): string {
    return `release-${version.replace(/\./g, '-')}`;
  }
}

/**
 * Calculate change approval rate
 */
export function calculateApprovalRate(changes: ChangeRequest[]): number {
  if (changes.length === 0) return 0;

  const approved = changes.filter(
    (c) => c.approvalStatus === 'approved' || c.approvalStatus === 'conditional'
  ).length;

  return (approved / changes.length) * 100;
}

/**
 * Calculate average approval time
 */
export function calculateAvgApprovalTime(changes: ChangeRequest[]): number {
  const approvedChanges = changes.filter(
    (c) =>
      (c.approvalStatus === 'approved' || c.approvalStatus === 'conditional') &&
      c.approvers.length > 0
  );

  if (approvedChanges.length === 0) return 0;

  const totalTime = approvedChanges.reduce((sum, change) => {
    const lastApproval = change.approvers
      .filter((a) => a.approvedAt)
      .sort((a, b) => b.approvedAt!.getTime() - a.approvedAt!.getTime())[0];

    if (lastApproval?.approvedAt) {
      return sum + (lastApproval.approvedAt.getTime() - change.createdAt.getTime());
    }
    return sum;
  }, 0);

  return totalTime / approvedChanges.length;
}

/**
 * Format change duration
 */
export function formatChangeDuration(durationMs: number): string {
  const minutes = Math.floor(durationMs / (60 * 1000));
  const hours = Math.floor(minutes / 60);

  if (hours > 0) {
    return `${hours}h ${minutes % 60}m`;
  } else {
    return `${minutes}m`;
  }
}

/**
 * Validate change request
 */
export function validateChangeRequest(
  change: ChangeRequest
): { valid: boolean; errors: string[] } {
  const errors: string[] = [];

  if (!change.title || change.title.trim().length === 0) {
    errors.push('Change title is required');
  }

  if (!change.implementationPlan || change.implementationPlan.trim().length === 0) {
    errors.push('Implementation plan is required');
  }

  if (!change.rollbackPlan || change.rollbackPlan.trim().length === 0) {
    errors.push('Rollback plan is required');
  }

  if (change.riskLevel === 'high' || change.riskLevel === 'critical') {
    if (!change.testing.unit || !change.testing.integration || !change.testing.e2e) {
      errors.push('High/critical risk changes require all test types');
    }
  }

  if (change.impact.downtime > 4 * 60 * 60 * 1000) {
    // > 4 hours
    errors.push('Downtime exceeds 4 hours - consider breaking into smaller changes');
  }

  return {
    valid: errors.length === 0,
    errors,
  };
}
