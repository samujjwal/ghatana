/**
 * Feature 2.34: Audit Trail Hardening
 *
 * Provides tamper-evident audit logging with:
 * - Append-only ledger with hash chain (blockchain-style)
 * - Cryptographic signatures for integrity verification
 * - Automated retention policies with cold storage roll-off
 * - Export with full signature verification
 * - Tamper detection and integrity validation
 *
 * @module auditLedger
 */

/**
 * Audit event types
 */
export type AuditEventType =
  | 'create'
  | 'update'
  | 'delete'
  | 'export'
  | 'import'
  | 'share'
  | 'access'
  | 'permission_change'
  | 'policy_update'
  | 'backup'
  | 'restore';

/**
 * Severity levels for audit events
 */
export type AuditSeverity = 'info' | 'warning' | 'error' | 'critical';

/**
 * Storage tier for audit entries
 */
export type StorageTier = 'hot' | 'warm' | 'cold' | 'archived';

/**
 * Audit entry metadata
 */
export interface AuditMetadata {
  /** IP address of the actor */
  ipAddress?: string;
  /** User agent string */
  userAgent?: string;
  /** Session identifier */
  sessionId?: string;
  /** Geographic location */
  location?: string;
  /** Device identifier */
  deviceId?: string;
  /** Additional custom metadata */
  custom?: Record<string, unknown>;
}

/**
 * Core audit ledger entry
 */
export interface AuditLedgerEntry {
  /** Unique entry identifier */
  id: string;
  /** Sequence number in the ledger */
  sequence: number;
  /** Timestamp of the event */
  timestamp: number;
  /** Type of audit event */
  eventType: AuditEventType;
  /** User/actor who performed the action */
  actor: string;
  /** Resource or entity affected */
  resource: string;
  /** Action performed */
  action: string;
  /** Severity level */
  severity: AuditSeverity;
  /** Event details */
  details: Record<string, unknown>;
  /** Additional metadata */
  metadata?: AuditMetadata;
  /** Hash of the previous entry (for chain integrity) */
  previousHash: string;
  /** Hash of this entry's content */
  contentHash: string;
  /** Signature of this entry */
  signature: string;
  /** Current storage tier */
  storageTier: StorageTier;
  /** When this entry was moved to current tier */
  tierTransitionTime?: number;
}

/**
 * Retention policy configuration
 */
export interface RetentionPolicy {
  /** Name of the retention policy */
  name: string;
  /** Hot storage retention (days) */
  hotRetentionDays: number;
  /** Warm storage retention (days) */
  warmRetentionDays: number;
  /** Cold storage retention (days) */
  coldRetentionDays: number;
  /** Whether to archive after cold storage expires */
  archiveAfterCold: boolean;
  /** Event types this policy applies to (empty = all types) */
  applicableEventTypes?: AuditEventType[];
  /** Minimum severity for retention (lower severity may be pruned earlier) */
  minSeverity?: AuditSeverity;
}

/**
 * Audit ledger export options
 */
export interface AuditExportOptions {
  /** Start timestamp for export range */
  startTime?: number;
  /** End timestamp for export range */
  endTime?: number;
  /** Filter by event types */
  eventTypes?: AuditEventType[];
  /** Filter by actors */
  actors?: string[];
  /** Filter by resources */
  resources?: string[];
  /** Filter by severity */
  minSeverity?: AuditSeverity;
  /** Include verification signatures */
  includeSignatures: boolean;
  /** Include full metadata */
  includeMetadata: boolean;
  /** Export format */
  format: 'json' | 'csv' | 'pdf';
}

/**
 * Export bundle with integrity proof
 */
export interface AuditExportBundle {
  /** Exported entries */
  entries: AuditLedgerEntry[];
  /** Export metadata */
  exportInfo: {
    exportedAt: number;
    exportedBy: string;
    totalEntries: number;
    timeRange: { start: number; end: number };
    filters: Partial<AuditExportOptions>;
  };
  /** Integrity verification data */
  integrity: {
    firstEntryHash: string;
    lastEntryHash: string;
    chainValid: boolean;
    signature: string;
  };
  /** Version information */
  version: string;
}

/**
 * Verification result for audit chain integrity
 */
export interface ChainVerificationResult {
  /** Whether the entire chain is valid */
  valid: boolean;
  /** Total entries verified */
  totalEntries: number;
  /** Number of entries with valid hashes */
  validEntries: number;
  /** Detected issues */
  issues: ChainIssue[];
  /** Verification timestamp */
  verifiedAt: number;
}

/**
 * Issue detected during chain verification
 */
export interface ChainIssue {
  /** Entry sequence number with issue */
  sequence: number;
  /** Entry ID */
  entryId: string;
  /** Type of issue */
  issueType: 'hash_mismatch' | 'signature_invalid' | 'sequence_gap' | 'timestamp_anomaly';
  /** Issue description */
  description: string;
  /** Severity of the issue */
  severity: 'minor' | 'major' | 'critical';
}

/**
 * Audit ledger state
 */
export interface AuditLedger {
  /** All audit entries (ordered by sequence) */
  entries: AuditLedgerEntry[];
  /** Index by entry ID */
  entriesById: Map<string, AuditLedgerEntry>;
  /** Index by actor */
  entriesByActor: Map<string, string[]>; // actor -> entry IDs
  /** Index by resource */
  entriesByResource: Map<string, string[]>; // resource -> entry IDs
  /** Index by storage tier */
  entriesByTier: Map<StorageTier, string[]>; // tier -> entry IDs
  /** Current sequence number */
  currentSequence: number;
  /** Hash of the last entry */
  lastEntryHash: string;
  /** Retention policies */
  retentionPolicies: RetentionPolicy[];
  /** Signing key for entries */
  signingKey: string;
  /** Statistics */
  statistics: {
    totalEntries: number;
    byEventType: Record<AuditEventType, number>;
    bySeverity: Record<AuditSeverity, number>;
    byTier: Record<StorageTier, number>;
  };
}

/**
 * Severity hierarchy (for filtering)
 */
const SEVERITY_HIERARCHY: AuditSeverity[] = ['info', 'warning', 'error', 'critical'];

/**
 * Initial hash for the chain (genesis block)
 */
const GENESIS_HASH = '0000000000000000000000000000000000000000000000000000000000000000';

/**
 * Creates a hash of content (simplified for demo; use crypto in production)
 */
function hashContent(content: string): string {
  let hash = 0;
  for (let i = 0; i < content.length; i++) {
    const char = content.charCodeAt(i);
    hash = (hash << 5) - hash + char;
    hash = hash & hash; // Convert to 32-bit integer
  }
  return Math.abs(hash).toString(16).padStart(64, '0');
}

/**
 * Signs an entry (simplified for demo; use crypto in production)
 */
function signEntry(entry: Omit<AuditLedgerEntry, 'signature'>, signingKey: string): string {
  const payload = JSON.stringify({ ...entry, key: signingKey });
  return `SIG_${hashContent(payload)}`;
}

/**
 * Verifies an entry signature
 */
function verifyEntrySignature(entry: AuditLedgerEntry, signingKey: string): boolean {
  const { signature, ...entryWithoutSig } = entry;
  const expectedSignature = signEntry(entryWithoutSig, signingKey);
  return signature === expectedSignature;
}

/**
 * Creates a new audit ledger
 */
export function createAuditLedger(signingKey: string, retentionPolicies?: RetentionPolicy[]): AuditLedger {
  return {
    entries: [],
    entriesById: new Map(),
    entriesByActor: new Map(),
    entriesByResource: new Map(),
    entriesByTier: new Map([
      ['hot', []],
      ['warm', []],
      ['cold', []],
      ['archived', []],
    ]),
    currentSequence: 0,
    lastEntryHash: GENESIS_HASH,
    retentionPolicies: retentionPolicies || [],
    signingKey,
    statistics: {
      totalEntries: 0,
      byEventType: {
        create: 0,
        update: 0,
        delete: 0,
        export: 0,
        import: 0,
        share: 0,
        access: 0,
        permission_change: 0,
        policy_update: 0,
        backup: 0,
        restore: 0,
      },
      bySeverity: {
        info: 0,
        warning: 0,
        error: 0,
        critical: 0,
      },
      byTier: {
        hot: 0,
        warm: 0,
        cold: 0,
        archived: 0,
      },
    },
  };
}

/**
 * Appends a new entry to the audit ledger
 */
export function appendAuditEntry(
  ledger: AuditLedger,
  event: {
    eventType: AuditEventType;
    actor: string;
    resource: string;
    action: string;
    severity: AuditSeverity;
    details: Record<string, unknown>;
    metadata?: AuditMetadata;
  }
): AuditLedger {
  const entryId = `audit-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
  const sequence = ledger.currentSequence + 1;
  const timestamp = Date.now();

  // Create content hash
  const contentToHash = JSON.stringify({
    id: entryId,
    sequence,
    timestamp,
    eventType: event.eventType,
    actor: event.actor,
    resource: event.resource,
    action: event.action,
    severity: event.severity,
    details: event.details,
    previousHash: ledger.lastEntryHash,
  });
  const contentHash = hashContent(contentToHash);

  // Create entry without signature first
  const entryWithoutSig: Omit<AuditLedgerEntry, 'signature'> = {
    id: entryId,
    sequence,
    timestamp,
    eventType: event.eventType,
    actor: event.actor,
    resource: event.resource,
    action: event.action,
    severity: event.severity,
    details: event.details,
    metadata: event.metadata,
    previousHash: ledger.lastEntryHash,
    contentHash,
    storageTier: 'hot',
  };

  // Sign the entry
  const signature = signEntry(entryWithoutSig, ledger.signingKey);

  const newEntry: AuditLedgerEntry = {
    ...entryWithoutSig,
    signature,
  };

  // Update indices
  const newEntriesById = new Map(ledger.entriesById).set(entryId, newEntry);

  const actorEntries = ledger.entriesByActor.get(event.actor) || [];
  const newEntriesByActor = new Map(ledger.entriesByActor).set(event.actor, [...actorEntries, entryId]);

  const resourceEntries = ledger.entriesByResource.get(event.resource) || [];
  const newEntriesByResource = new Map(ledger.entriesByResource).set(event.resource, [...resourceEntries, entryId]);

  const tierEntries = ledger.entriesByTier.get('hot') || [];
  const newEntriesByTier = new Map(ledger.entriesByTier).set('hot', [...tierEntries, entryId]);

  return {
    ...ledger,
    entries: [...ledger.entries, newEntry],
    entriesById: newEntriesById,
    entriesByActor: newEntriesByActor,
    entriesByResource: newEntriesByResource,
    entriesByTier: newEntriesByTier,
    currentSequence: sequence,
    lastEntryHash: contentHash,
    statistics: {
      totalEntries: ledger.statistics.totalEntries + 1,
      byEventType: {
        ...ledger.statistics.byEventType,
        [event.eventType]: ledger.statistics.byEventType[event.eventType] + 1,
      },
      bySeverity: {
        ...ledger.statistics.bySeverity,
        [event.severity]: ledger.statistics.bySeverity[event.severity] + 1,
      },
      byTier: {
        ...ledger.statistics.byTier,
        hot: ledger.statistics.byTier.hot + 1,
      },
    },
  };
}

/**
 * Verifies the integrity of the entire audit chain
 */
export function verifyChainIntegrity(ledger: AuditLedger): ChainVerificationResult {
  const issues: ChainIssue[] = [];
  let validEntries = 0;
  let expectedPreviousHash = GENESIS_HASH;

  for (let i = 0; i < ledger.entries.length; i++) {
    const entry = ledger.entries[i];
    let entryValid = true;

    // Check sequence continuity
    const expectedSequence = i + 1;
    if (entry.sequence !== expectedSequence) {
      issues.push({
        sequence: entry.sequence,
        entryId: entry.id,
        issueType: 'sequence_gap',
        description: `Expected sequence ${expectedSequence}, got ${entry.sequence}`,
        severity: 'critical',
      });
      entryValid = false;
    }

    // Check hash chain
    if (entry.previousHash !== expectedPreviousHash) {
      issues.push({
        sequence: entry.sequence,
        entryId: entry.id,
        issueType: 'hash_mismatch',
        description: `Previous hash mismatch: expected ${expectedPreviousHash}, got ${entry.previousHash}`,
        severity: 'critical',
      });
      entryValid = false;
    }

    // Verify content hash
    const contentToHash = JSON.stringify({
      id: entry.id,
      sequence: entry.sequence,
      timestamp: entry.timestamp,
      eventType: entry.eventType,
      actor: entry.actor,
      resource: entry.resource,
      action: entry.action,
      severity: entry.severity,
      details: entry.details,
      previousHash: entry.previousHash,
    });
    const expectedContentHash = hashContent(contentToHash);
    if (entry.contentHash !== expectedContentHash) {
      issues.push({
        sequence: entry.sequence,
        entryId: entry.id,
        issueType: 'hash_mismatch',
        description: 'Content hash does not match computed hash',
        severity: 'critical',
      });
      entryValid = false;
    }

    // Verify signature
    if (!verifyEntrySignature(entry, ledger.signingKey)) {
      issues.push({
        sequence: entry.sequence,
        entryId: entry.id,
        issueType: 'signature_invalid',
        description: 'Entry signature verification failed',
        severity: 'critical',
      });
      entryValid = false;
    }

    // Check timestamp monotonicity (timestamps should generally increase)
    if (i > 0 && entry.timestamp < ledger.entries[i - 1].timestamp) {
      issues.push({
        sequence: entry.sequence,
        entryId: entry.id,
        issueType: 'timestamp_anomaly',
        description: 'Timestamp is earlier than previous entry',
        severity: 'minor',
      });
      // Don't mark as invalid for timestamp anomalies
    }

    if (entryValid) {
      validEntries++;
    }

    expectedPreviousHash = entry.contentHash;
  }

  return {
    valid: issues.filter((i) => i.severity === 'critical').length === 0,
    totalEntries: ledger.entries.length,
    validEntries,
    issues,
    verifiedAt: Date.now(),
  };
}

/**
 * Applies retention policies and transitions entries to appropriate storage tiers
 */
export function applyRetentionPolicies(ledger: AuditLedger): AuditLedger {
  if (ledger.retentionPolicies.length === 0) {
    return ledger; // No policies to apply
  }

  const now = Date.now();
  let updatedLedger = ledger;

  for (const entry of ledger.entries) {
    const entryAge = now - entry.timestamp;
    const entryAgeDays = entryAge / (24 * 60 * 60 * 1000);

    // Find applicable policy
    const policy = ledger.retentionPolicies.find((p) =>
      !p.applicableEventTypes || p.applicableEventTypes.includes(entry.eventType)
    );

    if (!policy) continue;

    // Determine target tier based on age
    let targetTier: StorageTier = entry.storageTier;

    if (entryAgeDays > policy.hotRetentionDays + policy.warmRetentionDays + policy.coldRetentionDays) {
      if (policy.archiveAfterCold) {
        targetTier = 'archived';
      }
    } else if (entryAgeDays > policy.hotRetentionDays + policy.warmRetentionDays) {
      targetTier = 'cold';
    } else if (entryAgeDays > policy.hotRetentionDays) {
      targetTier = 'warm';
    }

    // Transition if needed
    if (targetTier !== entry.storageTier) {
      updatedLedger = transitionEntryTier(updatedLedger, entry.id, targetTier);
    }
  }

  return updatedLedger;
}

/**
 * Transitions an entry to a different storage tier
 */
function transitionEntryTier(ledger: AuditLedger, entryId: string, newTier: StorageTier): AuditLedger {
  const entry = ledger.entriesById.get(entryId);
  if (!entry) {
    throw new Error(`Entry '${entryId}' not found`);
  }

  if (entry.storageTier === newTier) {
    return ledger; // Already in target tier
  }

  const oldTier = entry.storageTier;

  // Update entry
  const updatedEntry: AuditLedgerEntry = {
    ...entry,
    storageTier: newTier,
    tierTransitionTime: Date.now(),
  };

  // Update entries array
  const entryIndex = ledger.entries.findIndex((e) => e.id === entryId);
  const newEntries = [...ledger.entries];
  newEntries[entryIndex] = updatedEntry;

  // Update indices
  const newEntriesById = new Map(ledger.entriesById).set(entryId, updatedEntry);

  // Update tier index
  const oldTierEntries = (ledger.entriesByTier.get(oldTier) || []).filter((id) => id !== entryId);
  const newTierEntries = [...(ledger.entriesByTier.get(newTier) || []), entryId];

  const newEntriesByTier = new Map(ledger.entriesByTier);
  newEntriesByTier.set(oldTier, oldTierEntries);
  newEntriesByTier.set(newTier, newTierEntries);

  return {
    ...ledger,
    entries: newEntries,
    entriesById: newEntriesById,
    entriesByTier: newEntriesByTier,
    statistics: {
      ...ledger.statistics,
      byTier: {
        ...ledger.statistics.byTier,
        [oldTier]: ledger.statistics.byTier[oldTier] - 1,
        [newTier]: ledger.statistics.byTier[newTier] + 1,
      },
    },
  };
}

/**
 * Exports audit entries with integrity proof
 */
export function exportAuditLedger(
  ledger: AuditLedger,
  options: AuditExportOptions,
  exportedBy: string
): AuditExportBundle {
  // Filter entries based on options
  let filteredEntries = ledger.entries;

  if (options.startTime !== undefined) {
    filteredEntries = filteredEntries.filter((e) => e.timestamp >= options.startTime!);
  }

  if (options.endTime !== undefined) {
    filteredEntries = filteredEntries.filter((e) => e.timestamp <= options.endTime!);
  }

  if (options.eventTypes && options.eventTypes.length > 0) {
    filteredEntries = filteredEntries.filter((e) => options.eventTypes!.includes(e.eventType));
  }

  if (options.actors && options.actors.length > 0) {
    filteredEntries = filteredEntries.filter((e) => options.actors!.includes(e.actor));
  }

  if (options.resources && options.resources.length > 0) {
    filteredEntries = filteredEntries.filter((e) => options.resources!.includes(e.resource));
  }

  if (options.minSeverity) {
    const minSeverityIndex = SEVERITY_HIERARCHY.indexOf(options.minSeverity);
    filteredEntries = filteredEntries.filter(
      (e) => SEVERITY_HIERARCHY.indexOf(e.severity) >= minSeverityIndex
    );
  }

  // Strip metadata if not requested
  let entriesToExport = filteredEntries;
  if (!options.includeMetadata) {
    entriesToExport = filteredEntries.map((e) => {
      const { metadata, ...entryWithoutMetadata } = e;
      return entryWithoutMetadata as AuditLedgerEntry;
    });
  }

  // Strip signatures if not requested
  if (!options.includeSignatures) {
    entriesToExport = entriesToExport.map((e) => {
      const { signature, ...entryWithoutSignature } = e;
      return { ...entryWithoutSignature, signature: '' } as AuditLedgerEntry;
    });
  }

  // Calculate integrity data
  const chainValid = verifyChainIntegrity(ledger).valid;
  const firstEntry = filteredEntries[0];
  const lastEntry = filteredEntries[filteredEntries.length - 1];

  const integrityPayload = JSON.stringify({
    firstEntryHash: firstEntry?.contentHash || '',
    lastEntryHash: lastEntry?.contentHash || '',
    chainValid,
    totalEntries: entriesToExport.length,
  });
  const integritySignature = `EXPORT_SIG_${hashContent(integrityPayload)}_${exportedBy}`;

  return {
    entries: entriesToExport,
    exportInfo: {
      exportedAt: Date.now(),
      exportedBy,
      totalEntries: entriesToExport.length,
      timeRange: {
        start: firstEntry?.timestamp || 0,
        end: lastEntry?.timestamp || 0,
      },
      filters: {
        startTime: options.startTime,
        endTime: options.endTime,
        eventTypes: options.eventTypes,
        actors: options.actors,
        resources: options.resources,
        minSeverity: options.minSeverity,
      },
    },
    integrity: {
      firstEntryHash: firstEntry?.contentHash || '',
      lastEntryHash: lastEntry?.contentHash || '',
      chainValid,
      signature: integritySignature,
    },
    version: '1.0.0',
  };
}

/**
 * Gets entries by actor
 */
export function getEntriesByActor(ledger: AuditLedger, actor: string): AuditLedgerEntry[] {
  const entryIds = ledger.entriesByActor.get(actor) || [];
  return entryIds.map((id) => ledger.entriesById.get(id)!).filter(Boolean);
}

/**
 * Gets entries by resource
 */
export function getEntriesByResource(ledger: AuditLedger, resource: string): AuditLedgerEntry[] {
  const entryIds = ledger.entriesByResource.get(resource) || [];
  return entryIds.map((id) => ledger.entriesById.get(id)!).filter(Boolean);
}

/**
 * Gets entries by storage tier
 */
export function getEntriesByTier(ledger: AuditLedger, tier: StorageTier): AuditLedgerEntry[] {
  const entryIds = ledger.entriesByTier.get(tier) || [];
  return entryIds.map((id) => ledger.entriesById.get(id)!).filter(Boolean);
}

/**
 * Gets audit statistics
 */
export function getAuditStatistics(ledger: AuditLedger): AuditLedger['statistics'] {
  return ledger.statistics;
}

/**
 * Searches audit entries
 */
export function searchAuditEntries(
  ledger: AuditLedger,
  query: {
    eventTypes?: AuditEventType[];
    actors?: string[];
    resources?: string[];
    minSeverity?: AuditSeverity;
    startTime?: number;
    endTime?: number;
    tiers?: StorageTier[];
  }
): AuditLedgerEntry[] {
  let results = ledger.entries;

  if (query.eventTypes && query.eventTypes.length > 0) {
    results = results.filter((e) => query.eventTypes!.includes(e.eventType));
  }

  if (query.actors && query.actors.length > 0) {
    results = results.filter((e) => query.actors!.includes(e.actor));
  }

  if (query.resources && query.resources.length > 0) {
    results = results.filter((e) => query.resources!.includes(e.resource));
  }

  if (query.minSeverity) {
    const minIndex = SEVERITY_HIERARCHY.indexOf(query.minSeverity);
    results = results.filter((e) => SEVERITY_HIERARCHY.indexOf(e.severity) >= minIndex);
  }

  if (query.startTime !== undefined) {
    results = results.filter((e) => e.timestamp >= query.startTime!);
  }

  if (query.endTime !== undefined) {
    results = results.filter((e) => e.timestamp <= query.endTime!);
  }

  if (query.tiers && query.tiers.length > 0) {
    results = results.filter((e) => query.tiers!.includes(e.storageTier));
  }

  return results;
}

/**
 * Adds a retention policy to the ledger
 */
export function addRetentionPolicy(ledger: AuditLedger, policy: RetentionPolicy): AuditLedger {
  // Check for duplicate policy names
  if (ledger.retentionPolicies.some((p) => p.name === policy.name)) {
    throw new Error(`Retention policy with name '${policy.name}' already exists`);
  }

  return {
    ...ledger,
    retentionPolicies: [...ledger.retentionPolicies, policy],
  };
}

/**
 * Removes a retention policy
 */
export function removeRetentionPolicy(ledger: AuditLedger, policyName: string): AuditLedger {
  return {
    ...ledger,
    retentionPolicies: ledger.retentionPolicies.filter((p) => p.name !== policyName),
  };
}
