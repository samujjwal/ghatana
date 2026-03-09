/**
 * Feature 2.26: Compliance Mapping
 *
 * Provides control tagging, coverage reporting, and audit bundle generation
 * for compliance frameworks (SOC2, ISO27001, HIPAA, PCI-DSS, etc.)
 *
 * @example
 * ```typescript
 * // Initialize compliance store
 * const store = createComplianceStore({
 *   frameworks: ['SOC2', 'ISO27001']
 * });
 *
 * // Tag node with controls
 * store.tagControl('node-1', {
 *   framework: 'SOC2',
 *   controlId: 'CC6.1',
 *   status: 'satisfied',
 *   evidence: ['doc1.pdf', 'test-results.json']
 * });
 *
 * // Generate coverage report
 * const report = store.getCoverageReport('SOC2');
 * console.log(`Coverage: ${report.satisfiedCount}/${report.totalCount}`);
 *
 * // Export audit bundle
 * const bundle = await store.exportAuditBundle({
 *   framework: 'SOC2',
 *   includeEvidence: true,
 *   format: 'pdf'
 * });
 * ```
 */

/**
 * Compliance framework identifiers
 */
export type ComplianceFramework =
  | 'SOC2'
  | 'ISO27001'
  | 'HIPAA'
  | 'PCI-DSS'
  | 'GDPR'
  | 'NIST-800-53'
  | 'FedRAMP'
  | 'CIS'
  | string;

/**
 * Control implementation status
 */
export type ControlStatus =
  | 'not-started' // Control not yet addressed
  | 'in-progress' // Control implementation underway
  | 'satisfied' // Control fully implemented
  | 'partial' // Control partially implemented
  | 'gap' // Control has identified gaps
  | 'not-applicable'; // Control doesn't apply to this system

/**
 * Severity level for control gaps
 */
export type GapSeverity = 'critical' | 'high' | 'medium' | 'low' | 'info';

/**
 * Control definition in a framework
 */
export interface ControlDefinition {
  readonly id: string;
  readonly framework: ComplianceFramework;
  readonly title: string;
  readonly description: string;
  readonly category: string;
  readonly requiredEvidence: readonly string[];
  readonly relatedControls: readonly string[]; // Cross-references to other controls
}

/**
 * Control tag applied to a canvas element
 */
export interface ControlTag {
  readonly id: string;
  readonly elementId: string;
  readonly framework: ComplianceFramework;
  readonly controlId: string;
  readonly status: ControlStatus;
  readonly implementationNotes?: string;
  readonly evidence: readonly string[]; // File paths or URLs
  readonly gaps: readonly ControlGap[];
  readonly assignedTo?: string;
  readonly dueDate?: Date;
  readonly lastReviewDate?: Date;
  readonly taggedAt: Date;
  readonly taggedBy: string;
  readonly updatedAt: Date;
}

/**
 * Identified gap in control implementation
 */
export interface ControlGap {
  readonly id: string;
  readonly description: string;
  readonly severity: GapSeverity;
  readonly remediation: string;
  readonly estimatedEffort?: string;
  readonly identifiedAt: Date;
  readonly resolvedAt?: Date;
}

/**
 * Coverage statistics for a framework
 */
export interface CoverageReport {
  readonly framework: ComplianceFramework;
  readonly totalCount: number;
  readonly satisfiedCount: number;
  readonly partialCount: number;
  readonly inProgressCount: number;
  readonly gapCount: number;
  readonly notStartedCount: number;
  readonly notApplicableCount: number;
  readonly coveragePercentage: number; // (satisfied + partial/2) / (total - notApplicable)
  readonly controlsByCategory: Record<
    string,
    {
      total: number;
      satisfied: number;
      gaps: number;
    }
  >;
  readonly criticalGaps: readonly ControlGap[];
  readonly upcomingDueDates: readonly {
    controlId: string;
    dueDate: Date;
    assignedTo?: string;
  }[];
  readonly generatedAt: Date;
}

/**
 * Audit bundle export options
 */
export interface AuditBundleOptions {
  readonly framework: ComplianceFramework;
  readonly includeEvidence: boolean;
  readonly includeDiagrams: boolean;
  readonly includeAuditLogs: boolean;
  readonly format: 'pdf' | 'csv' | 'json' | 'markdown';
  readonly dateRange?: {
    start: Date;
    end: Date;
  };
  readonly controlIds?: readonly string[]; // If specified, only export these controls
}

/**
 * Audit bundle content
 */
export interface AuditBundle {
  readonly framework: ComplianceFramework;
  readonly generatedAt: Date;
  readonly generatedBy: string;
  readonly coverageReport: CoverageReport;
  readonly controlTags: readonly ControlTag[];
  readonly diagrams?: readonly {
    elementId: string;
    title: string;
    imageData: string; // Base64 or URL
  }[];
  readonly auditLogs?: readonly AuditLogEntry[];
  readonly evidence?: Record<string, string>; // controlId -> evidence content/path
  readonly metadata: {
    version: string;
    exportedControls: number;
    includesEvidence: boolean;
    includesDiagrams: boolean;
  };
}

/**
 * Audit log entry for compliance tracking
 */
export interface AuditLogEntry {
  readonly id: string;
  readonly timestamp: Date;
  readonly actor: string;
  readonly action:
    | 'control-tagged'
    | 'control-updated'
    | 'control-removed'
    | 'gap-identified'
    | 'gap-resolved'
    | 'evidence-added'
    | 'review-completed';
  readonly elementId: string;
  readonly controlId: string;
  readonly framework: ComplianceFramework;
  readonly details: Record<string, unknown>;
  readonly previousState?: Partial<ControlTag>;
  readonly newState?: Partial<ControlTag>;
}

/**
 * Configuration for compliance store
 */
export interface ComplianceStoreConfig {
  readonly frameworks: readonly ComplianceFramework[];
  readonly controlDefinitions?: readonly ControlDefinition[];
  readonly requireEvidence?: boolean; // Default: true
  readonly enableAuditLog?: boolean; // Default: true
  readonly autoGenerateIds?: boolean; // Default: true
}

/**
 * Compliance store state
 */
export interface ComplianceStore {
  readonly config: ComplianceStoreConfig;
  readonly controlTags: Record<string, ControlTag>; // tagId -> tag
  readonly controlDefinitions: Record<string, ControlDefinition>; // controlId -> definition
  readonly auditLog: readonly AuditLogEntry[];
  readonly tagsByElement: Record<string, readonly string[]>; // elementId -> tagIds
  readonly tagsByControl: Record<string, readonly string[]>; // controlId -> tagIds
  readonly tagsByFramework: Record<string, readonly string[]>; // framework -> tagIds
}

/**
 * Create a new compliance store
 */
export function createComplianceStore(
  config: ComplianceStoreConfig
): ComplianceStore {
  const controlDefs: Record<string, ControlDefinition> = {};

  // Index provided control definitions
  if (config.controlDefinitions) {
    for (const def of config.controlDefinitions) {
      controlDefs[`${def.framework}:${def.id}`] = def;
    }
  }

  return {
    config,
    controlTags: {},
    controlDefinitions: controlDefs,
    auditLog: [],
    tagsByElement: {},
    tagsByControl: {},
    tagsByFramework: {},
  };
}

/**
 * Tag a canvas element with a compliance control
 */
export function tagControl(
  store: ComplianceStore,
  elementId: string,
  tag: Omit<ControlTag, 'id' | 'taggedAt' | 'updatedAt'>
): ComplianceStore {
  const tagId = store.config.autoGenerateIds
    ? `tag-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`
    : `${tag.elementId  }-${  tag.controlId}`;

  const now = new Date();
  const fullTag: ControlTag = {
    ...tag,
    id: tagId,
    taggedAt: now,
    updatedAt: now,
  };

  // Validate evidence if required
  if (
    store.config.requireEvidence &&
    fullTag.status === 'satisfied' &&
    fullTag.evidence.length === 0
  ) {
    throw new Error(
      `Evidence required for satisfied control ${fullTag.controlId}`
    );
  }

  // Create audit log entry
  const auditEntry: AuditLogEntry = {
    id: `audit-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
    timestamp: now,
    actor: fullTag.taggedBy,
    action: 'control-tagged',
    elementId,
    controlId: fullTag.controlId,
    framework: fullTag.framework,
    details: {
      status: fullTag.status,
      evidenceCount: fullTag.evidence.length,
    },
    newState: fullTag,
  };

  return {
    ...store,
    controlTags: {
      ...store.controlTags,
      [tagId]: fullTag,
    },
    tagsByElement: {
      ...store.tagsByElement,
      [elementId]: [...(store.tagsByElement[elementId] || []), tagId],
    },
    tagsByControl: {
      ...store.tagsByControl,
      [fullTag.controlId]: [
        ...(store.tagsByControl[fullTag.controlId] || []),
        tagId,
      ],
    },
    tagsByFramework: {
      ...store.tagsByFramework,
      [fullTag.framework]: [
        ...(store.tagsByFramework[fullTag.framework] || []),
        tagId,
      ],
    },
    auditLog: store.config.enableAuditLog
      ? [...store.auditLog, auditEntry]
      : store.auditLog,
  };
}

/**
 * Update an existing control tag
 */
export function updateControlTag(
  store: ComplianceStore,
  tagId: string,
  updates: Partial<
    Omit<ControlTag, 'id' | 'elementId' | 'taggedAt' | 'taggedBy'>
  >,
  actor?: string
): ComplianceStore {
  const existingTag = store.controlTags[tagId];
  if (!existingTag) {
    throw new Error(`Control tag not found: ${tagId}`);
  }

  const now = new Date();
  const updatedTag: ControlTag = {
    ...existingTag,
    ...updates,
    updatedAt: now,
  };

  // Create audit log entry
  const auditEntry: AuditLogEntry = {
    id: `audit-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
    timestamp: now,
    actor: actor || existingTag.taggedBy,
    action: 'control-updated',
    elementId: existingTag.elementId,
    controlId: existingTag.controlId,
    framework: existingTag.framework,
    details: updates,
    previousState: existingTag,
    newState: updatedTag,
  };

  return {
    ...store,
    controlTags: {
      ...store.controlTags,
      [tagId]: updatedTag,
    },
    auditLog: store.config.enableAuditLog
      ? [...store.auditLog, auditEntry]
      : store.auditLog,
  };
}

/**
 * Remove a control tag
 */
export function removeControlTag(
  store: ComplianceStore,
  tagId: string,
  actor: string
): ComplianceStore {
  const tag = store.controlTags[tagId];
  if (!tag) {
    throw new Error(`Control tag not found: ${tagId}`);
  }

  // Create audit log entry
  const auditEntry: AuditLogEntry = {
    id: `audit-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
    timestamp: new Date(),
    actor,
    action: 'control-removed',
    elementId: tag.elementId,
    controlId: tag.controlId,
    framework: tag.framework,
    details: {},
    previousState: tag,
  };

  // Remove from all indices
  const { [tagId]: _, ...remainingTags } = store.controlTags;

  const newTagsByElement = { ...store.tagsByElement };
  if (newTagsByElement[tag.elementId]) {
    newTagsByElement[tag.elementId] = newTagsByElement[tag.elementId].filter(
      (id) => id !== tagId
    );
  }

  const newTagsByControl = { ...store.tagsByControl };
  if (newTagsByControl[tag.controlId]) {
    newTagsByControl[tag.controlId] = newTagsByControl[tag.controlId].filter(
      (id) => id !== tagId
    );
  }

  const newTagsByFramework = { ...store.tagsByFramework };
  if (newTagsByFramework[tag.framework]) {
    newTagsByFramework[tag.framework] = newTagsByFramework[
      tag.framework
    ].filter((id) => id !== tagId);
  }

  return {
    ...store,
    controlTags: remainingTags,
    tagsByElement: newTagsByElement,
    tagsByControl: newTagsByControl,
    tagsByFramework: newTagsByFramework,
    auditLog: store.config.enableAuditLog
      ? [...store.auditLog, auditEntry]
      : store.auditLog,
  };
}

/**
 * Get all control tags for an element
 */
export function getElementTags(
  store: ComplianceStore,
  elementId: string
): readonly ControlTag[] {
  const tagIds = store.tagsByElement[elementId] || [];
  return tagIds.map((id) => store.controlTags[id]).filter(Boolean);
}

/**
 * Get all tags for a specific control
 */
export function getControlTags(
  store: ComplianceStore,
  controlId: string
): readonly ControlTag[] {
  const tagIds = store.tagsByControl[controlId] || [];
  return tagIds.map((id) => store.controlTags[id]).filter(Boolean);
}

/**
 * Get all tags for a framework
 */
export function getFrameworkTags(
  store: ComplianceStore,
  framework: ComplianceFramework
): readonly ControlTag[] {
  const tagIds = store.tagsByFramework[framework] || [];
  return tagIds.map((id) => store.controlTags[id]).filter(Boolean);
}

/**
 * Generate coverage report for a framework
 */
export function getCoverageReport(
  store: ComplianceStore,
  framework: ComplianceFramework
): CoverageReport {
  const frameworkTags = getFrameworkTags(store, framework);
  const frameworkDefs = Object.values(store.controlDefinitions).filter(
    (def) => def.framework === framework
  );

  // Count controls by status
  const statusCounts = {
    'not-started': 0,
    'in-progress': 0,
    satisfied: 0,
    partial: 0,
    gap: 0,
    'not-applicable': 0,
  };

  const controlsByCategory: Record<
    string,
    { total: number; satisfied: number; gaps: number }
  > = {};
  const criticalGaps: ControlGap[] = [];
  const upcomingDueDates: {
    controlId: string;
    dueDate: Date;
    assignedTo?: string;
  }[] = [];

  // Get unique controls from tags
  const taggedControls = new Set(frameworkTags.map((t) => t.controlId));

  for (const tag of frameworkTags) {
    statusCounts[tag.status]++;

    // Extract critical gaps
    for (const gap of tag.gaps) {
      if (gap.severity === 'critical' && !gap.resolvedAt) {
        criticalGaps.push(gap);
      }
    }

    // Track upcoming due dates
    if (tag.dueDate && tag.status !== 'satisfied') {
      const daysUntilDue =
        (tag.dueDate.getTime() - Date.now()) / (1000 * 60 * 60 * 24);
      if (daysUntilDue >= 0 && daysUntilDue <= 30) {
        // Next 30 days
        upcomingDueDates.push({
          controlId: tag.controlId,
          dueDate: tag.dueDate,
          assignedTo: tag.assignedTo,
        });
      }
    }

    // Get control definition for category
    const def = store.controlDefinitions[`${framework}:${tag.controlId}`];
    if (def) {
      const category = def.category;
      if (!controlsByCategory[category]) {
        controlsByCategory[category] = { total: 0, satisfied: 0, gaps: 0 };
      }
      controlsByCategory[category].total++;
      if (tag.status === 'satisfied') {
        controlsByCategory[category].satisfied++;
      }
      if (tag.status === 'gap' || tag.gaps.length > 0) {
        controlsByCategory[category].gaps++;
      }
    }
  }

  // Calculate total controls (defined controls or tagged controls)
  const totalCount = Math.max(frameworkDefs.length, taggedControls.size);

  // Calculate coverage percentage
  // Formula: (satisfied + partial/2) / (total - notApplicable) * 100
  const applicable = totalCount - statusCounts['not-applicable'];
  const effectiveCovered =
    statusCounts.satisfied + statusCounts.partial * 0.5;
  const coveragePercentage =
    applicable > 0 ? (effectiveCovered / applicable) * 100 : 0;

  // Sort upcoming due dates
  upcomingDueDates.sort((a, b) => a.dueDate.getTime() - b.dueDate.getTime());

  return {
    framework,
    totalCount,
    satisfiedCount: statusCounts.satisfied,
    partialCount: statusCounts.partial,
    inProgressCount: statusCounts['in-progress'],
    gapCount: statusCounts.gap,
    notStartedCount: statusCounts['not-started'],
    notApplicableCount: statusCounts['not-applicable'],
    coveragePercentage: Math.round(coveragePercentage * 100) / 100,
    controlsByCategory,
    criticalGaps,
    upcomingDueDates,
    generatedAt: new Date(),
  };
}

/**
 * Register a control definition
 */
export function registerControlDefinition(
  store: ComplianceStore,
  definition: ControlDefinition
): ComplianceStore {
  const key = `${definition.framework}:${definition.id}`;
  return {
    ...store,
    controlDefinitions: {
      ...store.controlDefinitions,
      [key]: definition,
    },
  };
}

/**
 * Get control definition
 */
export function getControlDefinition(
  store: ComplianceStore,
  framework: ComplianceFramework,
  controlId: string
): ControlDefinition | undefined {
  return store.controlDefinitions[`${framework}:${controlId}`];
}

/**
 * Export audit bundle
 * Note: Actual format conversion (PDF/CSV) would be handled by external libraries
 */
export async function exportAuditBundle(
  store: ComplianceStore,
  options: AuditBundleOptions,
  actor: string
): Promise<AuditBundle> {
  const frameworkTags = getFrameworkTags(store, options.framework);
  const coverageReport = getCoverageReport(store, options.framework);

  // Filter by control IDs if specified
  const filteredTags = options.controlIds
    ? frameworkTags.filter((t) => options.controlIds!.includes(t.controlId))
    : frameworkTags;

  // Filter by date range if specified
  const dateFilteredTags = options.dateRange
    ? filteredTags.filter(
        (t) =>
          t.taggedAt >= options.dateRange!.start &&
          t.taggedAt <= options.dateRange!.end
      )
    : filteredTags;

  // Collect audit logs if requested
  let auditLogs: AuditLogEntry[] | undefined;
  if (options.includeAuditLogs) {
    auditLogs = store.auditLog.filter((log) => {
      if (log.framework !== options.framework) return false;
      if (
        options.controlIds &&
        !options.controlIds.includes(log.controlId)
      ) {
        return false;
      }
      if (options.dateRange) {
        return (
          log.timestamp >= options.dateRange.start &&
          log.timestamp <= options.dateRange.end
        );
      }
      return true;
    });
  }

  // Note: Diagram and evidence collection would require integration with
  // canvas rendering and file storage systems. These are placeholders.
  const diagrams = options.includeDiagrams ? [] : undefined;
  const evidence = options.includeEvidence ? {} : undefined;

  return {
    framework: options.framework,
    generatedAt: new Date(),
    generatedBy: actor,
    coverageReport,
    controlTags: dateFilteredTags,
    diagrams,
    auditLogs,
    evidence,
    metadata: {
      version: '1.0.0',
      exportedControls: dateFilteredTags.length,
      includesEvidence: options.includeEvidence,
      includesDiagrams: options.includeDiagrams,
    },
  };
}

/**
 * Search control tags
 */
export function searchControlTags(
  store: ComplianceStore,
  query: {
    framework?: ComplianceFramework;
    status?: ControlStatus;
    severity?: GapSeverity;
    assignedTo?: string;
    hasGaps?: boolean;
  }
): readonly ControlTag[] {
  let tags = Object.values(store.controlTags);

  if (query.framework) {
    tags = tags.filter((t) => t.framework === query.framework);
  }

  if (query.status) {
    tags = tags.filter((t) => t.status === query.status);
  }

  if (query.assignedTo) {
    tags = tags.filter((t) => t.assignedTo === query.assignedTo);
  }

  if (query.hasGaps !== undefined) {
    tags = tags.filter((t) => (t.gaps.length > 0) === query.hasGaps);
  }

  if (query.severity) {
    tags = tags.filter((t) =>
      t.gaps.some(
        (gap) => gap.severity === query.severity && !gap.resolvedAt
      )
    );
  }

  return tags;
}

/**
 * Get compliance statistics across all frameworks
 */
export function getComplianceStatistics(store: ComplianceStore): {
  frameworkCount: number;
  totalTags: number;
  totalControls: number;
  overallCoverage: number;
  criticalGaps: number;
  frameworkStats: Record<
    string,
    {
      coverage: number;
      tags: number;
      gaps: number;
    }
  >;
} {
  const frameworks = store.config.frameworks;
  const frameworkStats: Record<
    string,
    { coverage: number; tags: number; gaps: number }
  > = {};

  let totalCoverage = 0;
  let totalCriticalGaps = 0;

  for (const framework of frameworks) {
    const report = getCoverageReport(store, framework);
    frameworkStats[framework] = {
      coverage: report.coveragePercentage,
      tags: getFrameworkTags(store, framework).length,
      gaps: report.criticalGaps.length,
    };
    totalCoverage += report.coveragePercentage;
    totalCriticalGaps += report.criticalGaps.length;
  }

  const overallCoverage =
    frameworks.length > 0 ? totalCoverage / frameworks.length : 0;

  return {
    frameworkCount: frameworks.length,
    totalTags: Object.keys(store.controlTags).length,
    totalControls: Object.keys(store.controlDefinitions).length,
    overallCoverage: Math.round(overallCoverage * 100) / 100,
    criticalGaps: totalCriticalGaps,
    frameworkStats,
  };
}
