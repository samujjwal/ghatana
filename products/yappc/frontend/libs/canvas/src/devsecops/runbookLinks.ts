/**
 * Runbook & Playbook Integration for SRE Operations
 *
 * Provides functionality to attach runbooks to canvas nodes, track incidents,
 * display SLO metrics, and manage on-call escalation paths.
 *
 * @module devsecops/runbookLinks
 */

/**
 * Runbook types
 */
export type RunbookType =
  | 'incident-response'
  | 'deployment'
  | 'maintenance'
  | 'troubleshooting'
  | 'rollback'
  | 'disaster-recovery'
  | 'security-incident'
  | 'performance'
  | 'custom';

/**
 * Incident severity levels
 */
export type IncidentSeverity = 'critical' | 'high' | 'medium' | 'low' | 'info';

/**
 * Incident status
 */
export type IncidentStatus =
  | 'detected'
  | 'investigating'
  | 'identified'
  | 'resolving'
  | 'resolved'
  | 'closed';

/**
 * SLO metric types
 */
export type MetricType = 'availability' | 'latency' | 'error-rate' | 'throughput' | 'custom';

/**
 * Runbook metadata
 */
export interface Runbook {
  id: string;
  title: string;
  type: RunbookType;
  description: string;
  url: string;
  version: string;
  author: string;
  lastUpdated: Date;
  tags: string[];
  estimatedDuration?: number; // minutes
  requiredSkills?: string[];
  dependencies?: string[]; // IDs of prerequisite runbooks
  metadata: Record<string, unknown>;
}

/**
 * Incident event in timeline
 */
export interface IncidentEvent {
  id: string;
  timestamp: Date;
  type: 'detected' | 'escalated' | 'acknowledged' | 'updated' | 'resolved' | 'closed';
  actor: string;
  description: string;
  severity?: IncidentSeverity;
  metadata: Record<string, unknown>;
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
  nodeIds: string[]; // Affected canvas nodes
  runbookId?: string;
  timeline: IncidentEvent[];
  startTime: Date;
  endTime?: Date;
  assignee?: string;
  oncallTeam?: string;
  escalationPath?: string[];
  rootCause?: string;
  resolution?: string;
  tags: string[];
  metadata: Record<string, unknown>;
}

/**
 * SLO (Service Level Objective) metric
 */
export interface SLOMetric {
  id: string;
  name: string;
  type: MetricType;
  target: number; // Target percentage (0-100) or value
  current: number; // Current percentage or value
  unit: string; // 'percent', 'ms', 'count', etc.
  timeWindow: string; // '30d', '7d', '1h', etc.
  threshold: {
    warning: number;
    critical: number;
  };
  trend: 'improving' | 'stable' | 'degrading';
  lastUpdated: Date;
  dataPoints?: { timestamp: Date; value: number }[];
  metadata: Record<string, unknown>;
}

/**
 * Playbook template
 */
export interface PlaybookTemplate {
  id: string;
  name: string;
  description: string;
  scenario: string;
  steps: PlaybookStep[];
  estimatedDuration: number; // minutes
  requiredRoles: string[];
  tags: string[];
  metadata: Record<string, unknown>;
}

/**
 * Playbook step
 */
export interface PlaybookStep {
  id: string;
  order: number;
  title: string;
  description: string;
  duration?: number; // minutes
  automatable: boolean;
  runbookUrl?: string;
  verificationCriteria?: string[];
  rollbackProcedure?: string;
  metadata: Record<string, unknown>;
}

/**
 * Runbook link (attaches runbook to canvas node)
 */
export interface RunbookLink {
  id: string;
  nodeId: string;
  runbookId: string;
  context?: string; // Why this runbook is relevant to this node
  priority: 'primary' | 'secondary' | 'reference';
  createdAt: Date;
  createdBy: string;
  metadata: Record<string, unknown>;
}

/**
 * On-call escalation level
 */
export interface EscalationLevel {
  level: number;
  name: string;
  contacts: string[];
  escalateAfter: number; // minutes
  notificationChannels: ('email' | 'sms' | 'slack' | 'pagerduty')[];
  metadata: Record<string, unknown>;
}

/**
 * Runbook manager state
 */
export interface RunbookManagerState {
  runbooks: Map<string, Runbook>;
  incidents: Map<string, Incident>;
  metrics: Map<string, SLOMetric>;
  links: Map<string, RunbookLink>;
  playbooks: Map<string, PlaybookTemplate>;
  escalationPaths: Map<string, EscalationLevel[]>;
}

/**
 * Create runbook manager state
 */
export function createRunbookManager(): RunbookManagerState {
  return {
    runbooks: new Map(),
    incidents: new Map(),
    metrics: new Map(),
    links: new Map(),
    playbooks: new Map(),
    escalationPaths: new Map(),
  };
}

/**
 * Add runbook
 */
export function addRunbook(state: RunbookManagerState, runbook: Runbook): RunbookManagerState {
  const newRunbooks = new Map(state.runbooks);
  newRunbooks.set(runbook.id, runbook);

  return {
    ...state,
    runbooks: newRunbooks,
  };
}

/**
 * Get runbook by ID
 */
export function getRunbook(state: RunbookManagerState, id: string): Runbook | undefined {
  return state.runbooks.get(id);
}

/**
 * Update runbook
 */
export function updateRunbook(
  state: RunbookManagerState,
  id: string,
  updates: Partial<Runbook>
): RunbookManagerState {
  const existing = state.runbooks.get(id);
  if (!existing) return state;

  const newRunbooks = new Map(state.runbooks);
  newRunbooks.set(id, {
    ...existing,
    ...updates,
    lastUpdated: new Date(),
  });

  return {
    ...state,
    runbooks: newRunbooks,
  };
}

/**
 * Remove runbook
 */
export function removeRunbook(state: RunbookManagerState, id: string): RunbookManagerState {
  const newRunbooks = new Map(state.runbooks);
  newRunbooks.delete(id);

  // Remove associated links
  const newLinks = new Map(state.links);
  for (const [linkId, link] of state.links) {
    if (link.runbookId === id) {
      newLinks.delete(linkId);
    }
  }

  return {
    ...state,
    runbooks: newRunbooks,
    links: newLinks,
  };
}

/**
 * Link runbook to node
 */
export function linkRunbookToNode(
  state: RunbookManagerState,
  link: RunbookLink
): RunbookManagerState {
  const newLinks = new Map(state.links);
  newLinks.set(link.id, link);

  return {
    ...state,
    links: newLinks,
  };
}

/**
 * Unlink runbook from node
 */
export function unlinkRunbookFromNode(state: RunbookManagerState, linkId: string): RunbookManagerState {
  const newLinks = new Map(state.links);
  newLinks.delete(linkId);

  return {
    ...state,
    links: newLinks,
  };
}

/**
 * Get runbooks for node
 */
export function getRunbooksForNode(state: RunbookManagerState, nodeId: string): Runbook[] {
  const links = Array.from(state.links.values()).filter(link => link.nodeId === nodeId);
  const runbooks = links
    .map(link => state.runbooks.get(link.runbookId))
    .filter((rb): rb is Runbook => rb !== undefined);

  // Sort by priority: primary > secondary > reference
  const priorityOrder = { primary: 0, secondary: 1, reference: 2 };
  const sortedLinks = links.sort(
    (a, b) => priorityOrder[a.priority] - priorityOrder[b.priority]
  );

  return sortedLinks
    .map(link => state.runbooks.get(link.runbookId))
    .filter((rb): rb is Runbook => rb !== undefined);
}

/**
 * Search runbooks
 */
export function searchRunbooks(
  state: RunbookManagerState,
  query: string,
  type?: RunbookType
): Runbook[] {
  const lowerQuery = query.toLowerCase();
  return Array.from(state.runbooks.values()).filter(runbook => {
    const matchesQuery =
      runbook.title.toLowerCase().includes(lowerQuery) ||
      runbook.description.toLowerCase().includes(lowerQuery) ||
      runbook.tags.some(tag => tag.toLowerCase().includes(lowerQuery));

    const matchesType = !type || runbook.type === type;

    return matchesQuery && matchesType;
  });
}

/**
 * Create incident
 */
export function createIncident(
  state: RunbookManagerState,
  incident: Incident
): RunbookManagerState {
  const newIncidents = new Map(state.incidents);
  newIncidents.set(incident.id, incident);

  return {
    ...state,
    incidents: newIncidents,
  };
}

/**
 * Get incident
 */
export function getIncident(state: RunbookManagerState, id: string): Incident | undefined {
  return state.incidents.get(id);
}

/**
 * Update incident status
 */
export function updateIncidentStatus(
  state: RunbookManagerState,
  incidentId: string,
  status: IncidentStatus,
  actor: string,
  notes?: string
): RunbookManagerState {
  const incident = state.incidents.get(incidentId);
  if (!incident) return state;

  const event: IncidentEvent = {
    id: `${incidentId}-event-${incident.timeline.length + 1}`,
    timestamp: new Date(),
    type: 'updated',
    actor,
    description: notes || `Status changed to ${status}`,
    metadata: { oldStatus: incident.status, newStatus: status },
  };

  const newIncidents = new Map(state.incidents);
  newIncidents.set(incidentId, {
    ...incident,
    status,
    timeline: [...incident.timeline, event],
    endTime: status === 'closed' || status === 'resolved' ? new Date() : incident.endTime,
  });

  return {
    ...state,
    incidents: newIncidents,
  };
}

/**
 * Add incident event
 */
export function addIncidentEvent(
  state: RunbookManagerState,
  incidentId: string,
  event: IncidentEvent
): RunbookManagerState {
  const incident = state.incidents.get(incidentId);
  if (!incident) return state;

  const newIncidents = new Map(state.incidents);
  newIncidents.set(incidentId, {
    ...incident,
    timeline: [...incident.timeline, event],
  });

  return {
    ...state,
    incidents: newIncidents,
  };
}

/**
 * Get incidents for node
 */
export function getIncidentsForNode(
  state: RunbookManagerState,
  nodeId: string,
  options?: {
    status?: IncidentStatus;
    severity?: IncidentSeverity;
    limit?: number;
  }
): Incident[] {
  let incidents = Array.from(state.incidents.values()).filter(incident =>
    incident.nodeIds.includes(nodeId)
  );

  if (options?.status) {
    incidents = incidents.filter(i => i.status === options.status);
  }

  if (options?.severity) {
    incidents = incidents.filter(i => i.severity === options.severity);
  }

  // Sort by start time (newest first)
  incidents.sort((a, b) => b.startTime.getTime() - a.startTime.getTime());

  if (options?.limit) {
    incidents = incidents.slice(0, options.limit);
  }

  return incidents;
}

/**
 * Get active incidents
 */
export function getActiveIncidents(state: RunbookManagerState): Incident[] {
  return Array.from(state.incidents.values()).filter(
    incident => incident.status !== 'closed' && incident.status !== 'resolved'
  );
}

/**
 * Add SLO metric
 */
export function addMetric(state: RunbookManagerState, metric: SLOMetric): RunbookManagerState {
  const newMetrics = new Map(state.metrics);
  newMetrics.set(metric.id, metric);

  return {
    ...state,
    metrics: newMetrics,
  };
}

/**
 * Update SLO metric
 */
export function updateMetric(
  state: RunbookManagerState,
  id: string,
  updates: Partial<SLOMetric>
): RunbookManagerState {
  const existing = state.metrics.get(id);
  if (!existing) return state;

  const newMetrics = new Map(state.metrics);
  newMetrics.set(id, {
    ...existing,
    ...updates,
    lastUpdated: new Date(),
  });

  return {
    ...state,
    metrics: newMetrics,
  };
}

/**
 * Get metrics for node
 */
export function getMetricsForNode(state: RunbookManagerState, nodeId: string): SLOMetric[] {
  return Array.from(state.metrics.values()).filter(metric =>
    metric.id.startsWith(nodeId) || metric.metadata.nodeId === nodeId
  );
}

/**
 * Check if SLO is breached
 */
export function isSLOBreached(metric: SLOMetric): boolean {
  if (metric.type === 'latency' || metric.type === 'error-rate') {
    // For latency and error rate, current should be <= target
    return metric.current > metric.target;
  }
  // For availability and throughput, current should be >= target
  return metric.current < metric.target;
}

/**
 * Get SLO health status
 */
export function getSLOHealth(metric: SLOMetric): 'healthy' | 'warning' | 'critical' {
  const isLowerBetter = metric.type === 'latency' || metric.type === 'error-rate';

  if (isSLOBreached(metric)) {
    // For latency/error-rate: higher values are worse
    // For availability/throughput: lower values are worse
    if (isLowerBetter) {
      if (metric.current >= metric.threshold.critical) return 'critical';
      if (metric.current >= metric.threshold.warning) return 'warning';
    } else {
      if (metric.current <= metric.threshold.critical) return 'critical';
      if (metric.current <= metric.threshold.warning) return 'warning';
    }
  }
  return 'healthy';
}

/**
 * Add playbook template
 */
export function addPlaybook(
  state: RunbookManagerState,
  playbook: PlaybookTemplate
): RunbookManagerState {
  const newPlaybooks = new Map(state.playbooks);
  newPlaybooks.set(playbook.id, playbook);

  return {
    ...state,
    playbooks: newPlaybooks,
  };
}

/**
 * Get playbook template
 */
export function getPlaybook(
  state: RunbookManagerState,
  id: string
): PlaybookTemplate | undefined {
  return state.playbooks.get(id);
}

/**
 * Search playbook templates
 */
export function searchPlaybooks(state: RunbookManagerState, query: string): PlaybookTemplate[] {
  const lowerQuery = query.toLowerCase();
  return Array.from(state.playbooks.values()).filter(
    playbook =>
      playbook.name.toLowerCase().includes(lowerQuery) ||
      playbook.description.toLowerCase().includes(lowerQuery) ||
      playbook.scenario.toLowerCase().includes(lowerQuery) ||
      playbook.tags.some(tag => tag.toLowerCase().includes(lowerQuery))
  );
}

/**
 * Set escalation path
 */
export function setEscalationPath(
  state: RunbookManagerState,
  teamId: string,
  levels: EscalationLevel[]
): RunbookManagerState {
  const newPaths = new Map(state.escalationPaths);
  newPaths.set(teamId, levels);

  return {
    ...state,
    escalationPaths: newPaths,
  };
}

/**
 * Get escalation path
 */
export function getEscalationPath(
  state: RunbookManagerState,
  teamId: string
): EscalationLevel[] | undefined {
  return state.escalationPaths.get(teamId);
}

/**
 * Get current escalation level
 */
export function getCurrentEscalationLevel(
  incident: Incident,
  escalationPath: EscalationLevel[]
): EscalationLevel | undefined {
  if (!escalationPath.length) return undefined;

  const incidentDuration = Date.now() - incident.startTime.getTime();
  const incidentMinutes = incidentDuration / (1000 * 60);

  // Find highest escalation level that should have been reached
  let currentLevel = escalationPath[0];
  for (const level of escalationPath) {
    const cumulativeTime = escalationPath
      .filter(l => l.level <= level.level)
      .reduce((sum, l) => sum + l.escalateAfter, 0);

    if (incidentMinutes >= cumulativeTime) {
      currentLevel = level;
    } else {
      break;
    }
  }

  return currentLevel;
}

/**
 * Get incident statistics
 */
export function getIncidentStatistics(state: RunbookManagerState): {
  total: number;
  active: number;
  bySeverity: Record<IncidentSeverity, number>;
  byStatus: Record<IncidentStatus, number>;
  averageResolutionTime: number; // minutes
  mttr: number; // Mean Time To Resolution (minutes)
} {
  const incidents = Array.from(state.incidents.values());

  const bySeverity: Record<IncidentSeverity, number> = {
    critical: 0,
    high: 0,
    medium: 0,
    low: 0,
    info: 0,
  };

  const byStatus: Record<IncidentStatus, number> = {
    detected: 0,
    investigating: 0,
    identified: 0,
    resolving: 0,
    resolved: 0,
    closed: 0,
  };

  let totalResolutionTime = 0;
  let resolvedCount = 0;

  for (const incident of incidents) {
    bySeverity[incident.severity]++;
    byStatus[incident.status]++;

    if (incident.endTime) {
      const resolutionTime =
        (incident.endTime.getTime() - incident.startTime.getTime()) / (1000 * 60);
      totalResolutionTime += resolutionTime;
      resolvedCount++;
    }
  }

  return {
    total: incidents.length,
    active: getActiveIncidents(state).length,
    bySeverity,
    byStatus,
    averageResolutionTime: resolvedCount > 0 ? totalResolutionTime / resolvedCount : 0,
    mttr: resolvedCount > 0 ? totalResolutionTime / resolvedCount : 0,
  };
}

/**
 * Get runbook statistics
 */
export function getRunbookStatistics(state: RunbookManagerState): {
  total: number;
  byType: Record<RunbookType, number>;
  linkedNodes: number;
  averageUsage: number;
} {
  const runbooks = Array.from(state.runbooks.values());
  const links = Array.from(state.links.values());

  const byType: Record<RunbookType, number> = {
    'incident-response': 0,
    deployment: 0,
    maintenance: 0,
    troubleshooting: 0,
    rollback: 0,
    'disaster-recovery': 0,
    'security-incident': 0,
    performance: 0,
    custom: 0,
  };

  for (const runbook of runbooks) {
    byType[runbook.type]++;
  }

  const uniqueNodes = new Set(links.map(link => link.nodeId));

  return {
    total: runbooks.length,
    byType,
    linkedNodes: uniqueNodes.size,
    averageUsage: runbooks.length > 0 ? links.length / runbooks.length : 0,
  };
}

/**
 * Export runbook data
 */
export function exportRunbookData(state: RunbookManagerState): {
  runbooks: Runbook[];
  incidents: Incident[];
  metrics: SLOMetric[];
  links: RunbookLink[];
  playbooks: PlaybookTemplate[];
  escalationPaths: Record<string, EscalationLevel[]>;
  exportedAt: Date;
} {
  return {
    runbooks: Array.from(state.runbooks.values()),
    incidents: Array.from(state.incidents.values()),
    metrics: Array.from(state.metrics.values()),
    links: Array.from(state.links.values()),
    playbooks: Array.from(state.playbooks.values()),
    escalationPaths: Object.fromEntries(state.escalationPaths),
    exportedAt: new Date(),
  };
}

/**
 * Import runbook data
 */
export function importRunbookData(
  state: RunbookManagerState,
  data: ReturnType<typeof exportRunbookData>
): RunbookManagerState {
  return {
    runbooks: new Map(data.runbooks.map(rb => [rb.id, rb])),
    incidents: new Map(data.incidents.map(inc => [inc.id, inc])),
    metrics: new Map(data.metrics.map(m => [m.id, m])),
    links: new Map(data.links.map(l => [l.id, l])),
    playbooks: new Map(data.playbooks.map(pb => [pb.id, pb])),
    escalationPaths: new Map(Object.entries(data.escalationPaths)),
  };
}
