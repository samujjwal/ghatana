/**
 * Canvas Incident Response System
 * 
 * Provides incident management capabilities for canvas-based applications:
 * - Incident tracking and lifecycle management
 * - Pager duty integration
 * - On-call rotation management
 * - Postmortem templates and workflows
 * - Escalation policies
 * - Runbook management for incident resolution
 * 
 * @module operations/incidentResponse
 */

/**
 * Incident severity levels
 */
export type IncidentSeverity =
  | 'sev1' // Critical - total outage
  | 'sev2' // High - major degradation
  | 'sev3' // Medium - partial impact
  | 'sev4'; // Low - minor issue

/**
 * Incident status
 */
export type IncidentStatus =
  | 'investigating' // Initial response
  | 'identified' // Root cause found
  | 'monitoring' // Fix applied, monitoring
  | 'resolved' // Incident closed
  | 'postmortem'; // Postmortem phase

/**
 * On-call schedule shift
 */
export interface OnCallShift {
  userId: string;
  startTime: Date;
  endTime: Date;
  isActive: boolean;
}

/**
 * Escalation policy
 */
export interface EscalationPolicy {
  id: string;
  name: string;
  description: string;
  levels: {
    level: number;
    escalationDelayMinutes: number;
    notifyUsers: string[];
    notifyChannels: string[];
  }[];
  createdAt: Date;
  updatedAt: Date;
}

/**
 * Incident record
 */
export interface Incident {
  id: string;
  title: string;
  description: string;
  severity: IncidentSeverity;
  status: IncidentStatus;
  affectedServices: string[];
  assignedTo: string[];
  startTime: Date;
  identifiedTime?: Date;
  resolvedTime?: Date;
  escalationPolicyId?: string;
  currentEscalationLevel: number;
  updates: IncidentUpdate[];
  tags: string[];
  postmortemUrl?: string;
  createdAt: Date;
  updatedAt: Date;
}

/**
 * Incident update (status update or communication)
 */
export interface IncidentUpdate {
  id: string;
  timestamp: Date;
  author: string;
  message: string;
  statusChange?: {
    from: IncidentStatus;
    to: IncidentStatus;
  };
  notifyChannels: string[];
}

/**
 * Postmortem template
 */
export interface Postmortem {
  id: string;
  incidentId: string;
  title: string;
  date: Date;
  participants: string[];
  summary: string;
  timeline: PostmortemTimelineEntry[];
  rootCause: string;
  impact: {
    usersAffected: number;
    duration: number;
    servicesAffected: string[];
  };
  resolution: string;
  actionItems: PostmortemActionItem[];
  lessonsLearned: string[];
  createdAt: Date;
  updatedAt: Date;
}

/**
 * Postmortem timeline entry
 */
export interface PostmortemTimelineEntry {
  time: Date;
  event: string;
  author: string;
}

/**
 * Postmortem action item
 */
export interface PostmortemActionItem {
  id: string;
  description: string;
  assignee: string;
  dueDate: Date;
  status: 'open' | 'in-progress' | 'completed';
  priority: 'low' | 'medium' | 'high' | 'critical';
}

/**
 * On-call rotation configuration
 */
export interface OnCallRotation {
  id: string;
  name: string;
  description: string;
  teamMembers: string[];
  shiftDuration: number; // milliseconds
  currentShift: OnCallShift;
  futureShifts: OnCallShift[];
  createdAt: Date;
  updatedAt: Date;
}

/**
 * Pager duty notification
 */
export interface PagerNotification {
  id: string;
  incidentId: string;
  userId: string;
  method: 'sms' | 'phone' | 'push' | 'email';
  sentAt: Date;
  acknowledgedAt?: Date;
  escalatedAt?: Date;
}

/**
 * Incident response runbook
 */
export interface IncidentRunbook {
  id: string;
  title: string;
  description: string;
  triggers: string[];
  steps: {
    order: number;
    description: string;
    commands?: string[];
    checkpoints: string[];
  }[];
  escalationCriteria: string[];
  relatedIncidents: string[];
  lastUpdated: Date;
  createdAt: Date;
}

/**
 * Configuration for incident response system
 */
export interface IncidentResponseConfig {
  defaultEscalationDelayMinutes?: number;
  postmortemRequiredForSeverities?: IncidentSeverity[];
  incidentRetentionDays?: number;
  autoEscalationEnabled?: boolean;
  notificationChannels?: string[];
}

/**
 * Incident Response Manager
 * 
 * Manages the complete incident response lifecycle including:
 * - Incident creation and tracking
 * - On-call rotation management
 * - Escalation policies
 * - Postmortem workflows
 * - Pager duty notifications
 */
export class IncidentResponseManager {
  private incidents: Map<string, Incident> = new Map();
  private escalationPolicies: Map<string, EscalationPolicy> = new Map();
  private rotations: Map<string, OnCallRotation> = new Map();
  private postmortems: Map<string, Postmortem> = new Map();
  private notifications: Map<string, PagerNotification> = new Map();
  private runbooks: Map<string, IncidentRunbook> = new Map();
  private config: Required<IncidentResponseConfig>;
  private incidentCounter = 0;
  private escalationCounter = 0;
  private rotationCounter = 0;
  private postmortemCounter = 0;
  private notificationCounter = 0;
  private runbookCounter = 0;

  /**
   *
   */
  constructor(config: IncidentResponseConfig = {}) {
    this.config = {
      defaultEscalationDelayMinutes: config.defaultEscalationDelayMinutes || 15,
      postmortemRequiredForSeverities:
        config.postmortemRequiredForSeverities || ['sev1', 'sev2'],
      incidentRetentionDays: config.incidentRetentionDays || 90,
      autoEscalationEnabled: config.autoEscalationEnabled ?? true,
      notificationChannels: config.notificationChannels || [
        'slack',
        'email',
        'pagerduty',
      ],
    };
  }

  /**
   * Create a new incident
   */
  createIncident(
    params: Omit<
      Incident,
      | 'id'
      | 'currentEscalationLevel'
      | 'updates'
      | 'createdAt'
      | 'updatedAt'
      | 'startTime'
    >
  ): Incident {
    const incident: Incident = {
      ...params,
      id: this.generateIncidentId(params.severity),
      currentEscalationLevel: 0,
      updates: [],
      startTime: new Date(),
      createdAt: new Date(),
      updatedAt: new Date(),
    };

    this.incidents.set(incident.id, incident);

    // Auto-notify on-call engineer for sev1/sev2
    if (params.severity === 'sev1' || params.severity === 'sev2') {
      this.notifyOnCall(incident.id);
    }

    return incident;
  }

  /**
   * Get incident by ID
   */
  getIncident(id: string): Incident | null {
    return this.incidents.get(id) ?? null;
  }

  /**
   * Get all incidents
   */
  getAllIncidents(): Incident[] {
    return Array.from(this.incidents.values());
  }

  /**
   * Get incidents by status
   */
  getIncidentsByStatus(status: IncidentStatus): Incident[] {
    return Array.from(this.incidents.values()).filter((i) => i.status === status);
  }

  /**
   * Get incidents by severity
   */
  getIncidentsBySeverity(severity: IncidentSeverity): Incident[] {
    return Array.from(this.incidents.values()).filter(
      (i) => i.severity === severity
    );
  }

  /**
   * Get active incidents (not resolved or in postmortem)
   */
  getActiveIncidents(): Incident[] {
    return Array.from(this.incidents.values()).filter(
      (i) => i.status !== 'resolved' && i.status !== 'postmortem'
    );
  }

  /**
   * Update incident status
   */
  updateIncidentStatus(
    id: string,
    status: IncidentStatus,
    author: string,
    message: string
  ): void {
    const incident = this.incidents.get(id);
    if (!incident) {
      throw new Error(`Incident ${id} not found`);
    }

    const previousStatus = incident.status;
    incident.status = status;
    incident.updatedAt = new Date();

    // Track timing
    if (status === 'identified' && !incident.identifiedTime) {
      incident.identifiedTime = new Date();
    } else if (status === 'resolved' && !incident.resolvedTime) {
      incident.resolvedTime = new Date();
    }

    // Add update
    const update: IncidentUpdate = {
      id: this.generateUpdateId(id),
      timestamp: new Date(),
      author,
      message,
      statusChange: {
        from: previousStatus,
        to: status,
      },
      notifyChannels: this.config.notificationChannels,
    };

    incident.updates.push(update);
  }

  /**
   * Add incident update
   */
  addIncidentUpdate(
    incidentId: string,
    author: string,
    message: string,
    notifyChannels: string[] = []
  ): void {
    const incident = this.incidents.get(incidentId);
    if (!incident) {
      throw new Error(`Incident ${incidentId} not found`);
    }

    const update: IncidentUpdate = {
      id: this.generateUpdateId(incidentId),
      timestamp: new Date(),
      author,
      message,
      notifyChannels,
    };

    incident.updates.push(update);
    incident.updatedAt = new Date();
  }

  /**
   * Assign incident to user
   */
  assignIncident(incidentId: string, userId: string): void {
    const incident = this.incidents.get(incidentId);
    if (!incident) {
      throw new Error(`Incident ${incidentId} not found`);
    }

    if (!incident.assignedTo.includes(userId)) {
      incident.assignedTo.push(userId);
      incident.updatedAt = new Date();
    }
  }

  /**
   * Escalate incident to next level
   */
  escalateIncident(incidentId: string): void {
    const incident = this.incidents.get(incidentId);
    if (!incident) {
      throw new Error(`Incident ${incidentId} not found`);
    }

    if (!incident.escalationPolicyId) {
      throw new Error(`Incident ${incidentId} has no escalation policy`);
    }

    const policy = this.escalationPolicies.get(incident.escalationPolicyId);
    if (!policy) {
      throw new Error(`Escalation policy ${incident.escalationPolicyId} not found`);
    }

    incident.currentEscalationLevel++;
    incident.updatedAt = new Date();

    const level = policy.levels.find(
      (l) => l.level === incident.currentEscalationLevel
    );
    if (level) {
      // Notify users at this escalation level
      level.notifyUsers.forEach((userId) => {
        this.sendPagerNotification(incidentId, userId, 'push');
      });
    }
  }

  /**
   * Create escalation policy
   */
  createEscalationPolicy(
    params: Omit<EscalationPolicy, 'id' | 'createdAt' | 'updatedAt'>
  ): EscalationPolicy {
    const policy: EscalationPolicy = {
      ...params,
      id: this.generateEscalationPolicyId(params.name),
      createdAt: new Date(),
      updatedAt: new Date(),
    };

    this.escalationPolicies.set(policy.id, policy);
    return policy;
  }

  /**
   * Get escalation policy by ID
   */
  getEscalationPolicy(id: string): EscalationPolicy | null {
    return this.escalationPolicies.get(id) ?? null;
  }

  /**
   * Get all escalation policies
   */
  getAllEscalationPolicies(): EscalationPolicy[] {
    return Array.from(this.escalationPolicies.values());
  }

  /**
   * Create on-call rotation
   */
  createRotation(
    params: Omit<
      OnCallRotation,
      'id' | 'currentShift' | 'futureShifts' | 'createdAt' | 'updatedAt'
    >
  ): OnCallRotation {
    const now = new Date();
    const currentShift: OnCallShift = {
      userId: params.teamMembers[0],
      startTime: now,
      endTime: new Date(now.getTime() + params.shiftDuration),
      isActive: true,
    };

    const rotation: OnCallRotation = {
      ...params,
      id: this.generateRotationId(params.name),
      currentShift,
      futureShifts: [],
      createdAt: new Date(),
      updatedAt: new Date(),
    };

    // Generate future shifts
    this.generateFutureShifts(rotation);

    this.rotations.set(rotation.id, rotation);
    return rotation;
  }

  /**
   * Get on-call rotation by ID
   */
  getRotation(id: string): OnCallRotation | null {
    return this.rotations.get(id) ?? null;
  }

  /**
   * Get all rotations
   */
  getAllRotations(): OnCallRotation[] {
    return Array.from(this.rotations.values());
  }

  /**
   * Get current on-call user for a rotation
   */
  getCurrentOnCall(rotationId: string): string | null {
    const rotation = this.rotations.get(rotationId);
    if (!rotation) {
      return null;
    }

    return rotation.currentShift.userId;
  }

  /**
   * Rotate to next on-call shift
   */
  rotateShift(rotationId: string): void {
    const rotation = this.rotations.get(rotationId);
    if (!rotation) {
      throw new Error(`Rotation ${rotationId} not found`);
    }

    if (rotation.futureShifts.length === 0) {
      throw new Error(`No future shifts available for rotation ${rotationId}`);
    }

    // Move to next shift
    rotation.currentShift.isActive = false;
    rotation.currentShift = rotation.futureShifts.shift()!;
    rotation.currentShift.isActive = true;
    rotation.updatedAt = new Date();

    // Generate more future shifts if needed
    if (rotation.futureShifts.length < 3) {
      this.generateFutureShifts(rotation);
    }
  }

  /**
   * Send pager notification
   */
  sendPagerNotification(
    incidentId: string,
    userId: string,
    method: PagerNotification['method']
  ): PagerNotification {
    const notification: PagerNotification = {
      id: this.generateNotificationId(incidentId),
      incidentId,
      userId,
      method,
      sentAt: new Date(),
    };

    this.notifications.set(notification.id, notification);
    return notification;
  }

  /**
   * Acknowledge pager notification
   */
  acknowledgeNotification(notificationId: string): void {
    const notification = this.notifications.get(notificationId);
    if (!notification) {
      throw new Error(`Notification ${notificationId} not found`);
    }

    notification.acknowledgedAt = new Date();
  }

  /**
   * Get notifications for incident
   */
  getNotificationsByIncident(incidentId: string): PagerNotification[] {
    return Array.from(this.notifications.values()).filter(
      (n) => n.incidentId === incidentId
    );
  }

  /**
   * Create postmortem
   */
  createPostmortem(
    params: Omit<Postmortem, 'id' | 'createdAt' | 'updatedAt'>
  ): Postmortem {
    const postmortem: Postmortem = {
      ...params,
      id: this.generatePostmortemId(params.incidentId),
      createdAt: new Date(),
      updatedAt: new Date(),
    };

    this.postmortems.set(postmortem.id, postmortem);

    // Update incident with postmortem link
    const incident = this.incidents.get(params.incidentId);
    if (incident) {
      incident.postmortemUrl = postmortem.id;
      incident.status = 'postmortem';
      incident.updatedAt = new Date();
    }

    return postmortem;
  }

  /**
   * Get postmortem by ID
   */
  getPostmortem(id: string): Postmortem | null {
    return this.postmortems.get(id) ?? null;
  }

  /**
   * Get postmortem by incident ID
   */
  getPostmortemByIncident(incidentId: string): Postmortem | null {
    return (
      Array.from(this.postmortems.values()).find(
        (p) => p.incidentId === incidentId
      ) ?? null
    );
  }

  /**
   * Get all postmortems
   */
  getAllPostmortems(): Postmortem[] {
    return Array.from(this.postmortems.values());
  }

  /**
   * Add postmortem action item
   */
  addActionItem(
    postmortemId: string,
    item: Omit<PostmortemActionItem, 'id'>
  ): void {
    const postmortem = this.postmortems.get(postmortemId);
    if (!postmortem) {
      throw new Error(`Postmortem ${postmortemId} not found`);
    }

    const actionItem: PostmortemActionItem = {
      ...item,
      id: this.generateActionItemId(postmortemId),
    };

    postmortem.actionItems.push(actionItem);
    postmortem.updatedAt = new Date();
  }

  /**
   * Update action item status
   */
  updateActionItemStatus(
    postmortemId: string,
    actionItemId: string,
    status: PostmortemActionItem['status']
  ): void {
    const postmortem = this.postmortems.get(postmortemId);
    if (!postmortem) {
      throw new Error(`Postmortem ${postmortemId} not found`);
    }

    const item = postmortem.actionItems.find((a) => a.id === actionItemId);
    if (!item) {
      throw new Error(`Action item ${actionItemId} not found`);
    }

    item.status = status;
    postmortem.updatedAt = new Date();
  }

  /**
   * Create incident runbook
   */
  createRunbook(
    params: Omit<IncidentRunbook, 'id' | 'createdAt' | 'lastUpdated'>
  ): IncidentRunbook {
    const runbook: IncidentRunbook = {
      ...params,
      id: this.generateRunbookId(params.title),
      lastUpdated: new Date(),
      createdAt: new Date(),
    };

    this.runbooks.set(runbook.id, runbook);
    return runbook;
  }

  /**
   * Get runbook by ID
   */
  getRunbook(id: string): IncidentRunbook | null {
    return this.runbooks.get(id) ?? null;
  }

  /**
   * Get all runbooks
   */
  getAllRunbooks(): IncidentRunbook[] {
    return Array.from(this.runbooks.values());
  }

  /**
   * Search runbooks by trigger
   */
  searchRunbooks(trigger: string): IncidentRunbook[] {
    const lowerTrigger = trigger.toLowerCase();
    return Array.from(this.runbooks.values()).filter((r) =>
      r.triggers.some((t) => t.toLowerCase().includes(lowerTrigger))
    );
  }

  /**
   * Clean up old resolved incidents
   */
  cleanupOldIncidents(): number {
    const cutoffDate = new Date(
      Date.now() - this.config.incidentRetentionDays * 24 * 60 * 60 * 1000
    );

    let removed = 0;
    for (const [id, incident] of this.incidents.entries()) {
      if (
        incident.status === 'resolved' &&
        incident.resolvedTime &&
        incident.resolvedTime < cutoffDate
      ) {
        this.incidents.delete(id);
        removed++;
      }
    }

    return removed;
  }

  /**
   * Get incident response statistics
   */
  getStats(): {
    totalIncidents: number;
    activeIncidents: number;
    bySeverity: Record<IncidentSeverity, number>;
    byStatus: Record<IncidentStatus, number>;
    avgTimeToIdentify: number;
    avgTimeToResolve: number;
    escalationPolicies: number;
    rotations: number;
    postmortems: number;
  } {
    const incidents = Array.from(this.incidents.values());

    const bySeverity = {
      sev1: incidents.filter((i) => i.severity === 'sev1').length,
      sev2: incidents.filter((i) => i.severity === 'sev2').length,
      sev3: incidents.filter((i) => i.severity === 'sev3').length,
      sev4: incidents.filter((i) => i.severity === 'sev4').length,
    };

    const byStatus = {
      investigating: incidents.filter((i) => i.status === 'investigating').length,
      identified: incidents.filter((i) => i.status === 'identified').length,
      monitoring: incidents.filter((i) => i.status === 'monitoring').length,
      resolved: incidents.filter((i) => i.status === 'resolved').length,
      postmortem: incidents.filter((i) => i.status === 'postmortem').length,
    };

    // Calculate average times
    const identifiedIncidents = incidents.filter(
      (i) => i.identifiedTime && i.startTime
    );
    const avgTimeToIdentify =
      identifiedIncidents.length > 0
        ? identifiedIncidents.reduce(
            (sum, i) =>
              sum + (i.identifiedTime!.getTime() - i.startTime.getTime()),
            0
          ) / identifiedIncidents.length
        : 0;

    const resolvedIncidents = incidents.filter(
      (i) => i.resolvedTime && i.startTime
    );
    const avgTimeToResolve =
      resolvedIncidents.length > 0
        ? resolvedIncidents.reduce(
            (sum, i) => sum + (i.resolvedTime!.getTime() - i.startTime.getTime()),
            0
          ) / resolvedIncidents.length
        : 0;

    return {
      totalIncidents: incidents.length,
      activeIncidents: this.getActiveIncidents().length,
      bySeverity,
      byStatus,
      avgTimeToIdentify,
      avgTimeToResolve,
      escalationPolicies: this.escalationPolicies.size,
      rotations: this.rotations.size,
      postmortems: this.postmortems.size,
    };
  }

  /**
   * Reset manager state
   */
  reset(): void {
    this.incidents.clear();
    this.escalationPolicies.clear();
    this.rotations.clear();
    this.postmortems.clear();
    this.notifications.clear();
    this.runbooks.clear();
    this.incidentCounter = 0;
    this.escalationCounter = 0;
    this.rotationCounter = 0;
    this.postmortemCounter = 0;
    this.notificationCounter = 0;
    this.runbookCounter = 0;
  }

  /**
   * Notify on-call engineer
   */
  private notifyOnCall(incidentId: string): void {
    // Find first active rotation
    const rotation = Array.from(this.rotations.values()).find(
      (r) => r.currentShift.isActive
    );

    if (rotation) {
      this.sendPagerNotification(incidentId, rotation.currentShift.userId, 'push');
    }
  }

  /**
   * Generate future shifts for rotation
   */
  private generateFutureShifts(rotation: OnCallRotation): void {
    let lastShift =
      rotation.futureShifts.length > 0
        ? rotation.futureShifts[rotation.futureShifts.length - 1]
        : rotation.currentShift;

    // Generate 3 future shifts
    for (let i = 0; i < 3; i++) {
      const memberIndex =
        (rotation.teamMembers.indexOf(lastShift.userId) + 1) %
        rotation.teamMembers.length;
      const startTime = new Date(lastShift.endTime);
      const endTime = new Date(startTime.getTime() + rotation.shiftDuration);

      const shift: OnCallShift = {
        userId: rotation.teamMembers[memberIndex],
        startTime,
        endTime,
        isActive: false,
      };

      rotation.futureShifts.push(shift);
      lastShift = shift;
    }
  }

  /**
   * Generate unique incident ID
   */
  private generateIncidentId(severity: IncidentSeverity): string {
    return `inc-${severity}-${Date.now()}-${this.incidentCounter++}`;
  }

  /**
   *
   */
  private generateUpdateId(incidentId: string): string {
    return `update-${incidentId}-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
  }

  /**
   *
   */
  private generateEscalationPolicyId(name: string): string {
    return `policy-${name.toLowerCase().replace(/\s+/g, '-')}-${Date.now()}-${this.escalationCounter++}`;
  }

  /**
   *
   */
  private generateRotationId(name: string): string {
    return `rotation-${name.toLowerCase().replace(/\s+/g, '-')}-${Date.now()}-${this.rotationCounter++}`;
  }

  /**
   *
   */
  private generatePostmortemId(incidentId: string): string {
    return `pm-${incidentId}-${Date.now()}-${this.postmortemCounter++}`;
  }

  /**
   *
   */
  private generateNotificationId(incidentId: string): string {
    return `notif-${incidentId}-${Date.now()}-${this.notificationCounter++}`;
  }

  /**
   *
   */
  private generateRunbookId(title: string): string {
    return `runbook-${title.toLowerCase().replace(/\s+/g, '-')}-${Date.now()}-${this.runbookCounter++}`;
  }

  /**
   *
   */
  private generateActionItemId(postmortemId: string): string {
    return `action-${postmortemId}-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
  }
}

/**
 * Calculate mean time to recovery (MTTR)
 */
export function calculateMTTR(incidents: Incident[]): number {
  const resolved = incidents.filter(
    (i) => i.resolvedTime && i.startTime
  );

  if (resolved.length === 0) return 0;

  const totalTime = resolved.reduce(
    (sum, i) => sum + (i.resolvedTime!.getTime() - i.startTime.getTime()),
    0
  );

  return totalTime / resolved.length;
}

/**
 * Calculate mean time to acknowledge (MTTA)
 */
export function calculateMTTA(
  incidents: Incident[],
  notifications: PagerNotification[]
): number {
  const acknowledged = notifications.filter((n) => n.acknowledgedAt);

  if (acknowledged.length === 0) return 0;

  const totalTime = acknowledged.reduce(
    (sum, n) => sum + (n.acknowledgedAt!.getTime() - n.sentAt.getTime()),
    0
  );

  return totalTime / acknowledged.length;
}

/**
 * Format incident duration
 */
export function formatIncidentDuration(durationMs: number): string {
  const seconds = Math.floor(durationMs / 1000);
  const minutes = Math.floor(seconds / 60);
  const hours = Math.floor(minutes / 60);
  const days = Math.floor(hours / 24);

  if (days > 0) {
    return `${days}d ${hours % 24}h`;
  } else if (hours > 0) {
    return `${hours}h ${minutes % 60}m`;
  } else if (minutes > 0) {
    return `${minutes}m ${seconds % 60}s`;
  } else {
    return `${seconds}s`;
  }
}

/**
 * Validate escalation policy
 */
export function validateEscalationPolicy(
  policy: EscalationPolicy
): { valid: boolean; errors: string[] } {
  const errors: string[] = [];

  if (policy.levels.length === 0) {
    errors.push('Policy must have at least one escalation level');
  }

  // Check level ordering
  const sortedLevels = [...policy.levels].sort((a, b) => a.level - b.level);
  if (JSON.stringify(sortedLevels) !== JSON.stringify(policy.levels)) {
    errors.push('Escalation levels must be in ascending order');
  }

  // Check for duplicate levels
  const levelNumbers = policy.levels.map((l) => l.level);
  if (new Set(levelNumbers).size !== levelNumbers.length) {
    errors.push('Duplicate escalation levels found');
  }

  // Check each level has contacts
  policy.levels.forEach((level, index) => {
    if (
      level.notifyUsers.length === 0 &&
      level.notifyChannels.length === 0
    ) {
      errors.push(`Level ${level.level} has no notification contacts`);
    }

    if (level.escalationDelayMinutes < 0) {
      errors.push(`Level ${level.level} has negative escalation delay`);
    }
  });

  return {
    valid: errors.length === 0,
    errors,
  };
}

/**
 * Calculate severity from impact
 */
export function calculateSeverity(impact: {
  usersAffected: number;
  servicesDown: number;
  revenueImpact: number;
}): IncidentSeverity {
  // SEV1: Critical - complete outage or major services down
  if (
    impact.servicesDown > 0 ||
    impact.usersAffected > 10000 ||
    impact.revenueImpact > 100000
  ) {
    return 'sev1';
  }

  // SEV2: High - significant degradation
  if (
    impact.usersAffected > 1000 ||
    impact.revenueImpact > 10000
  ) {
    return 'sev2';
  }

  // SEV3: Medium - partial impact
  if (impact.usersAffected > 100 || impact.revenueImpact > 1000) {
    return 'sev3';
  }

  // SEV4: Low - minor issue
  return 'sev4';
}
