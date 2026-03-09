import { describe, it, expect, beforeEach } from 'vitest';

import {
  createAuditLedger,
  appendAuditEntry,
  verifyChainIntegrity,
  applyRetentionPolicies,
  exportAuditLedger,
  getEntriesByActor,
  getEntriesByResource,
  getEntriesByTier,
  getAuditStatistics,
  searchAuditEntries,
  addRetentionPolicy,
  removeRetentionPolicy,
  type AuditLedger,
  type RetentionPolicy,
  type AuditExportOptions,
} from '../auditLedger';

describe.skip('Feature 2.34: Audit Trail Hardening', () => {
  let ledger: AuditLedger;
  const testSigningKey = 'test-signing-key-12345';

  beforeEach(() => {
    ledger = createAuditLedger(testSigningKey);
  });

  describe('Ledger Creation', () => {
    it('should create empty ledger with genesis hash', () => {
      expect(ledger.entries).toEqual([]);
      expect(ledger.currentSequence).toBe(0);
      expect(ledger.lastEntryHash).toMatch(/^0+$/); // Genesis hash is all zeros
      expect(ledger.signingKey).toBe(testSigningKey);
    });

    it('should initialize statistics', () => {
      expect(ledger.statistics.totalEntries).toBe(0);
      expect(ledger.statistics.byEventType.create).toBe(0);
      expect(ledger.statistics.bySeverity.info).toBe(0);
      expect(ledger.statistics.byTier.hot).toBe(0);
    });

    it('should initialize retention policies', () => {
      const policies: RetentionPolicy[] = [
        {
          name: 'Standard',
          hotRetentionDays: 30,
          warmRetentionDays: 60,
          coldRetentionDays: 365,
          archiveAfterCold: true,
        },
      ];

      const ledgerWithPolicies = createAuditLedger(testSigningKey, policies);
      expect(ledgerWithPolicies.retentionPolicies).toHaveLength(1);
      expect(ledgerWithPolicies.retentionPolicies[0].name).toBe('Standard');
    });
  });

  describe('Append Operations', () => {
    it('should append new audit entry', () => {
      const updated = appendAuditEntry(ledger, {
        eventType: 'create',
        actor: 'user-1',
        resource: 'document-1',
        action: 'created new document',
        severity: 'info',
        details: { title: 'Test Document' },
      });

      expect(updated.entries).toHaveLength(1);
      expect(updated.currentSequence).toBe(1);
      expect(updated.statistics.totalEntries).toBe(1);
      expect(updated.statistics.byEventType.create).toBe(1);
    });

    it('should create hash chain', () => {
      let updated = ledger;

      // Append first entry
      updated = appendAuditEntry(updated, {
        eventType: 'create',
        actor: 'user-1',
        resource: 'doc-1',
        action: 'create',
        severity: 'info',
        details: {},
      });

      const firstEntry = updated.entries[0];

      // Append second entry
      updated = appendAuditEntry(updated, {
        eventType: 'update',
        actor: 'user-1',
        resource: 'doc-1',
        action: 'update',
        severity: 'info',
        details: {},
      });

      const secondEntry = updated.entries[1];

      // Second entry should reference first entry's hash
      expect(secondEntry.previousHash).toBe(firstEntry.contentHash);
      expect(secondEntry.sequence).toBe(2);
    });

    it('should generate unique entry IDs', () => {
      let updated = ledger;

      updated = appendAuditEntry(updated, {
        eventType: 'create',
        actor: 'user-1',
        resource: 'doc-1',
        action: 'create',
        severity: 'info',
        details: {},
      });

      updated = appendAuditEntry(updated, {
        eventType: 'create',
        actor: 'user-1',
        resource: 'doc-2',
        action: 'create',
        severity: 'info',
        details: {},
      });

      expect(updated.entries[0].id).not.toBe(updated.entries[1].id);
    });

    it('should sign each entry', () => {
      const updated = appendAuditEntry(ledger, {
        eventType: 'delete',
        actor: 'user-1',
        resource: 'doc-1',
        action: 'delete',
        severity: 'warning',
        details: { reason: 'obsolete' },
      });

      const entry = updated.entries[0];
      expect(entry.signature).toBeDefined();
      expect(entry.signature).toContain('SIG_');
    });

    it('should store entry in hot tier initially', () => {
      const updated = appendAuditEntry(ledger, {
        eventType: 'access',
        actor: 'user-1',
        resource: 'doc-1',
        action: 'viewed',
        severity: 'info',
        details: {},
      });

      const entry = updated.entries[0];
      expect(entry.storageTier).toBe('hot');
      expect(updated.statistics.byTier.hot).toBe(1);
    });

    it('should include metadata when provided', () => {
      const updated = appendAuditEntry(ledger, {
        eventType: 'access',
        actor: 'user-1',
        resource: 'doc-1',
        action: 'viewed',
        severity: 'info',
        details: {},
        metadata: {
          ipAddress: '192.168.1.1',
          userAgent: 'Mozilla/5.0',
          sessionId: 'sess-123',
        },
      });

      const entry = updated.entries[0];
      expect(entry.metadata?.ipAddress).toBe('192.168.1.1');
      expect(entry.metadata?.userAgent).toBe('Mozilla/5.0');
    });
  });

  describe('Chain Integrity Verification', () => {
    it('should verify valid chain', () => {
      let updated = ledger;

      for (let i = 0; i < 5; i++) {
        updated = appendAuditEntry(updated, {
          eventType: 'create',
          actor: 'user-1',
          resource: `doc-${i}`,
          action: 'create',
          severity: 'info',
          details: {},
        });
      }

      const verification = verifyChainIntegrity(updated);
      expect(verification.valid).toBe(true);
      expect(verification.totalEntries).toBe(5);
      expect(verification.validEntries).toBe(5);
      expect(verification.issues).toHaveLength(0);
    });

    it('should detect hash chain tampering', () => {
      let updated = ledger;

      updated = appendAuditEntry(updated, {
        eventType: 'create',
        actor: 'user-1',
        resource: 'doc-1',
        action: 'create',
        severity: 'info',
        details: {},
      });

      updated = appendAuditEntry(updated, {
        eventType: 'update',
        actor: 'user-1',
        resource: 'doc-1',
        action: 'update',
        severity: 'info',
        details: {},
      });

      // Tamper with the second entry's previous hash
      const tamperedEntry = {
        ...updated.entries[1],
        previousHash: 'tampered-hash',
      };
      updated = {
        ...updated,
        entries: [updated.entries[0], tamperedEntry],
      };

      const verification = verifyChainIntegrity(updated);
      expect(verification.valid).toBe(false);
      expect(verification.issues.length).toBeGreaterThan(0);
      expect(verification.issues.some((i) => i.issueType === 'hash_mismatch')).toBe(true);
    });

    it('should detect content tampering', () => {
      let updated = appendAuditEntry(ledger, {
        eventType: 'create',
        actor: 'user-1',
        resource: 'doc-1',
        action: 'create',
        severity: 'info',
        details: { original: 'data' },
      });

      // Tamper with entry details
      const tamperedEntry = {
        ...updated.entries[0],
        details: { tampered: 'data' },
      };
      updated = {
        ...updated,
        entries: [tamperedEntry],
      };

      const verification = verifyChainIntegrity(updated);
      expect(verification.valid).toBe(false);
      expect(verification.issues.some((i) => i.issueType === 'hash_mismatch')).toBe(true);
    });

    it('should detect signature tampering', () => {
      let updated = appendAuditEntry(ledger, {
        eventType: 'create',
        actor: 'user-1',
        resource: 'doc-1',
        action: 'create',
        severity: 'info',
        details: {},
      });

      // Tamper with signature
      const tamperedEntry = {
        ...updated.entries[0],
        signature: 'INVALID_SIGNATURE',
      };
      updated = {
        ...updated,
        entries: [tamperedEntry],
      };

      const verification = verifyChainIntegrity(updated);
      expect(verification.valid).toBe(false);
      expect(verification.issues.some((i) => i.issueType === 'signature_invalid')).toBe(true);
    });

    it('should detect sequence gaps', () => {
      let updated = appendAuditEntry(ledger, {
        eventType: 'create',
        actor: 'user-1',
        resource: 'doc-1',
        action: 'create',
        severity: 'info',
        details: {},
      });

      updated = appendAuditEntry(updated, {
        eventType: 'update',
        actor: 'user-1',
        resource: 'doc-1',
        action: 'update',
        severity: 'info',
        details: {},
      });

      // Create gap by manipulating sequence
      const entryWithGap = {
        ...updated.entries[1],
        sequence: 5, // Should be 2
      };
      updated = {
        ...updated,
        entries: [updated.entries[0], entryWithGap],
      };

      const verification = verifyChainIntegrity(updated);
      expect(verification.valid).toBe(false);
      expect(verification.issues.some((i) => i.issueType === 'sequence_gap')).toBe(true);
    });

    it('should detect timestamp anomalies', () => {
      const now = Date.now();

      let updated = appendAuditEntry(ledger, {
        eventType: 'create',
        actor: 'user-1',
        resource: 'doc-1',
        action: 'create',
        severity: 'info',
        details: {},
      });

      // Manually create entry with earlier timestamp
      const earlierEntry = {
        ...updated.entries[0],
        id: 'early-entry',
        sequence: 2,
        timestamp: now - 10000, // Earlier than first entry
        previousHash: updated.entries[0].contentHash,
      };

      updated = {
        ...updated,
        entries: [updated.entries[0], earlierEntry],
        currentSequence: 2,
      };

      const verification = verifyChainIntegrity(updated);
      expect(verification.issues.some((i) => i.issueType === 'timestamp_anomaly')).toBe(true);
    });
  });

  describe('Retention Policies', () => {
    beforeEach(() => {
      const policy: RetentionPolicy = {
        name: 'Standard',
        hotRetentionDays: 1,
        warmRetentionDays: 2,
        coldRetentionDays: 3,
        archiveAfterCold: true,
      };
      ledger = addRetentionPolicy(ledger, policy);
    });

    it('should keep recent entries in hot tier', () => {
      const updated = appendAuditEntry(ledger, {
        eventType: 'create',
        actor: 'user-1',
        resource: 'doc-1',
        action: 'create',
        severity: 'info',
        details: {},
      });

      const afterRetention = applyRetentionPolicies(updated);
      expect(afterRetention.entries[0].storageTier).toBe('hot');
    });

    it('should transition old entries to warm tier', () => {
      // Create entry with old timestamp
      let updated = appendAuditEntry(ledger, {
        eventType: 'create',
        actor: 'user-1',
        resource: 'doc-1',
        action: 'create',
        severity: 'info',
        details: {},
      });

      // Manually set old timestamp (2 days ago)
      const oldEntry = {
        ...updated.entries[0],
        timestamp: Date.now() - 2 * 24 * 60 * 60 * 1000,
      };
      updated = {
        ...updated,
        entries: [oldEntry],
        entriesById: new Map([[oldEntry.id, oldEntry]]),
      };

      const afterRetention = applyRetentionPolicies(updated);
      const entry = afterRetention.entriesById.get(oldEntry.id);
      expect(entry?.storageTier).toBe('warm');
    });

    it('should transition very old entries to cold tier', () => {
      let updated = appendAuditEntry(ledger, {
        eventType: 'create',
        actor: 'user-1',
        resource: 'doc-1',
        action: 'create',
        severity: 'info',
        details: {},
      });

      // Manually set very old timestamp (4 days ago)
      const veryOldEntry = {
        ...updated.entries[0],
        timestamp: Date.now() - 4 * 24 * 60 * 60 * 1000,
      };
      updated = {
        ...updated,
        entries: [veryOldEntry],
        entriesById: new Map([[veryOldEntry.id, veryOldEntry]]),
      };

      const afterRetention = applyRetentionPolicies(updated);
      const entry = afterRetention.entriesById.get(veryOldEntry.id);
      expect(entry?.storageTier).toBe('cold');
    });

    it('should archive extremely old entries', () => {
      let updated = appendAuditEntry(ledger, {
        eventType: 'create',
        actor: 'user-1',
        resource: 'doc-1',
        action: 'create',
        severity: 'info',
        details: {},
      });

      // Manually set extremely old timestamp (7 days ago - beyond all tiers)
      const extremelyOldEntry = {
        ...updated.entries[0],
        timestamp: Date.now() - 7 * 24 * 60 * 60 * 1000,
      };
      updated = {
        ...updated,
        entries: [extremelyOldEntry],
        entriesById: new Map([[extremelyOldEntry.id, extremelyOldEntry]]),
      };

      const afterRetention = applyRetentionPolicies(updated);
      const entry = afterRetention.entriesById.get(extremelyOldEntry.id);
      expect(entry?.storageTier).toBe('archived');
    });

    it('should handle multiple policies', () => {
      const criticalPolicy: RetentionPolicy = {
        name: 'Critical',
        hotRetentionDays: 90,
        warmRetentionDays: 180,
        coldRetentionDays: 365,
        archiveAfterCold: false,
        applicableEventTypes: ['permission_change', 'policy_update'],
        minSeverity: 'critical',
      };

      const updated = addRetentionPolicy(ledger, criticalPolicy);
      expect(updated.retentionPolicies).toHaveLength(2);
    });

    it('should throw error for duplicate policy names', () => {
      const duplicatePolicy: RetentionPolicy = {
        name: 'Standard', // Same name as existing
        hotRetentionDays: 30,
        warmRetentionDays: 60,
        coldRetentionDays: 90,
        archiveAfterCold: true,
      };

      expect(() => addRetentionPolicy(ledger, duplicatePolicy)).toThrow(
        "Retention policy with name 'Standard' already exists"
      );
    });

    it('should remove retention policy', () => {
      const updated = removeRetentionPolicy(ledger, 'Standard');
      expect(updated.retentionPolicies).toHaveLength(0);
    });
  });

  describe('Export Operations', () => {
    beforeEach(() => {
      // Add several entries
      let updated = ledger;
      for (let i = 0; i < 5; i++) {
        updated = appendAuditEntry(updated, {
          eventType: i % 2 === 0 ? 'create' : 'update',
          actor: i % 2 === 0 ? 'user-1' : 'user-2',
          resource: `doc-${i}`,
          action: i % 2 === 0 ? 'created' : 'updated',
          severity: i === 4 ? 'error' : 'info',
          details: { index: i },
        });
      }
      ledger = updated;
    });

    it('should export all entries', () => {
      const options: AuditExportOptions = {
        includeSignatures: true,
        includeMetadata: true,
        format: 'json',
      };

      const bundle = exportAuditLedger(ledger, options, 'admin-1');
      expect(bundle.entries).toHaveLength(5);
      expect(bundle.exportInfo.totalEntries).toBe(5);
      expect(bundle.exportInfo.exportedBy).toBe('admin-1');
    });

    it('should filter by time range', () => {
      // Use explicit timestamps to ensure filtering works
      const now = Date.now();
      const earlier = now - 10000;

      // Manually create entries with controlled timestamps
      const entry1 = { ...ledger.entries[0], timestamp: earlier };
      const entry2 = { ...ledger.entries[1], timestamp: earlier + 1000 };
      const entry3 = { ...ledger.entries[2], timestamp: now };
      const entry4 = { ...ledger.entries[3], timestamp: now + 1000 };
      const entry5 = { ...ledger.entries[4], timestamp: now + 2000 };

      const modifiedLedger: AuditLedger = {
        ...ledger,
        entries: [entry1, entry2, entry3, entry4, entry5],
      };

      const midpoint = now; // Filter from 'now' onwards

      const options: AuditExportOptions = {
        startTime: midpoint,
        includeSignatures: true,
        includeMetadata: true,
        format: 'json',
      };

      const bundle = exportAuditLedger(modifiedLedger, options, 'admin-1');
      expect(bundle.entries.length).toBe(3); // entries 3, 4, 5
      expect(bundle.entries.every((e) => e.timestamp >= midpoint)).toBe(true);
    });

    it('should filter by event type', () => {
      const options: AuditExportOptions = {
        eventTypes: ['create'],
        includeSignatures: true,
        includeMetadata: true,
        format: 'json',
      };

      const bundle = exportAuditLedger(ledger, options, 'admin-1');
      expect(bundle.entries.every((e) => e.eventType === 'create')).toBe(true);
    });

    it('should filter by actor', () => {
      const options: AuditExportOptions = {
        actors: ['user-1'],
        includeSignatures: true,
        includeMetadata: true,
        format: 'json',
      };

      const bundle = exportAuditLedger(ledger, options, 'admin-1');
      expect(bundle.entries.every((e) => e.actor === 'user-1')).toBe(true);
    });

    it('should filter by severity', () => {
      const options: AuditExportOptions = {
        minSeverity: 'error',
        includeSignatures: true,
        includeMetadata: true,
        format: 'json',
      };

      const bundle = exportAuditLedger(ledger, options, 'admin-1');
      expect(bundle.entries.length).toBeGreaterThan(0);
      expect(bundle.entries.every((e) => e.severity === 'error' || e.severity === 'critical')).toBe(true);
    });

    it('should include integrity proof', () => {
      const options: AuditExportOptions = {
        includeSignatures: true,
        includeMetadata: true,
        format: 'json',
      };

      const bundle = exportAuditLedger(ledger, options, 'admin-1');
      expect(bundle.integrity).toBeDefined();
      expect(bundle.integrity.chainValid).toBe(true);
      expect(bundle.integrity.signature).toBeDefined();
      expect(bundle.integrity.firstEntryHash).toBe(ledger.entries[0].contentHash);
      expect(bundle.integrity.lastEntryHash).toBe(ledger.entries[ledger.entries.length - 1].contentHash);
    });

    it('should exclude signatures when requested', () => {
      const options: AuditExportOptions = {
        includeSignatures: false,
        includeMetadata: true,
        format: 'json',
      };

      const bundle = exportAuditLedger(ledger, options, 'admin-1');
      expect(bundle.entries.every((e) => e.signature === '')).toBe(true);
    });

    it('should exclude metadata when requested', () => {
      // Add entry with metadata
      const updated = appendAuditEntry(ledger, {
        eventType: 'access',
        actor: 'user-1',
        resource: 'doc-1',
        action: 'viewed',
        severity: 'info',
        details: {},
        metadata: { ipAddress: '192.168.1.1' },
      });

      const options: AuditExportOptions = {
        includeSignatures: true,
        includeMetadata: false,
        format: 'json',
      };

      const bundle = exportAuditLedger(updated, options, 'admin-1');
      expect(bundle.entries.every((e) => e.metadata === undefined)).toBe(true);
    });

    it('should include export metadata', () => {
      const options: AuditExportOptions = {
        eventTypes: ['create'],
        actors: ['user-1'],
        includeSignatures: true,
        includeMetadata: true,
        format: 'json',
      };

      const bundle = exportAuditLedger(ledger, options, 'admin-1');
      expect(bundle.exportInfo.filters.eventTypes).toEqual(['create']);
      expect(bundle.exportInfo.filters.actors).toEqual(['user-1']);
      expect(bundle.version).toBe('1.0.0');
    });
  });

  describe('Query Operations', () => {
    beforeEach(() => {
      // Add diverse entries
      let updated = ledger;

      updated = appendAuditEntry(updated, {
        eventType: 'create',
        actor: 'user-1',
        resource: 'doc-1',
        action: 'created',
        severity: 'info',
        details: {},
      });

      updated = appendAuditEntry(updated, {
        eventType: 'update',
        actor: 'user-2',
        resource: 'doc-1',
        action: 'updated',
        severity: 'warning',
        details: {},
      });

      updated = appendAuditEntry(updated, {
        eventType: 'delete',
        actor: 'user-1',
        resource: 'doc-2',
        action: 'deleted',
        severity: 'error',
        details: {},
      });

      ledger = updated;
    });

    it('should get entries by actor', () => {
      const entries = getEntriesByActor(ledger, 'user-1');
      expect(entries).toHaveLength(2);
      expect(entries.every((e) => e.actor === 'user-1')).toBe(true);
    });

    it('should get entries by resource', () => {
      const entries = getEntriesByResource(ledger, 'doc-1');
      expect(entries).toHaveLength(2);
      expect(entries.every((e) => e.resource === 'doc-1')).toBe(true);
    });

    it('should get entries by tier', () => {
      const entries = getEntriesByTier(ledger, 'hot');
      expect(entries).toHaveLength(3);
      expect(entries.every((e) => e.storageTier === 'hot')).toBe(true);
    });

    it('should get audit statistics', () => {
      const stats = getAuditStatistics(ledger);
      expect(stats.totalEntries).toBe(3);
      expect(stats.byEventType.create).toBe(1);
      expect(stats.byEventType.update).toBe(1);
      expect(stats.byEventType.delete).toBe(1);
      expect(stats.bySeverity.info).toBe(1);
      expect(stats.bySeverity.warning).toBe(1);
      expect(stats.bySeverity.error).toBe(1);
    });

    it('should search by event types', () => {
      const results = searchAuditEntries(ledger, {
        eventTypes: ['create', 'delete'],
      });
      expect(results).toHaveLength(2);
      expect(results.every((e) => e.eventType === 'create' || e.eventType === 'delete')).toBe(true);
    });

    it('should search by actors', () => {
      const results = searchAuditEntries(ledger, {
        actors: ['user-2'],
      });
      expect(results).toHaveLength(1);
      expect(results[0].actor).toBe('user-2');
    });

    it('should search by minimum severity', () => {
      const results = searchAuditEntries(ledger, {
        minSeverity: 'warning',
      });
      expect(results.length).toBeGreaterThan(0);
      expect(results.every((e) => ['warning', 'error', 'critical'].includes(e.severity))).toBe(true);
    });

    it('should search by time range', () => {
      const midpoint = ledger.entries[1].timestamp;

      const results = searchAuditEntries(ledger, {
        startTime: midpoint,
      });

      expect(results.every((e) => e.timestamp >= midpoint)).toBe(true);
    });

    it('should search by multiple criteria', () => {
      const results = searchAuditEntries(ledger, {
        actors: ['user-1'],
        eventTypes: ['create'],
        minSeverity: 'info',
      });

      expect(results).toHaveLength(1);
      expect(results[0].actor).toBe('user-1');
      expect(results[0].eventType).toBe('create');
    });
  });

  describe('Edge Cases', () => {
    it('should handle empty ledger verification', () => {
      const verification = verifyChainIntegrity(ledger);
      expect(verification.valid).toBe(true);
      expect(verification.totalEntries).toBe(0);
      expect(verification.issues).toHaveLength(0);
    });

    it('should handle export of empty ledger', () => {
      const options: AuditExportOptions = {
        includeSignatures: true,
        includeMetadata: true,
        format: 'json',
      };

      const bundle = exportAuditLedger(ledger, options, 'admin-1');
      expect(bundle.entries).toHaveLength(0);
      expect(bundle.exportInfo.totalEntries).toBe(0);
    });

    it('should handle retention policy with no matching entries', () => {
      const policy: RetentionPolicy = {
        name: 'Specific',
        hotRetentionDays: 30,
        warmRetentionDays: 60,
        coldRetentionDays: 90,
        archiveAfterCold: true,
        applicableEventTypes: ['backup'], // No backup events in ledger
      };

      const updated = addRetentionPolicy(ledger, policy);
      const afterRetention = applyRetentionPolicies(updated);
      expect(afterRetention).toEqual(updated); // No changes
    });

    it('should handle search with no results', () => {
      const updated = appendAuditEntry(ledger, {
        eventType: 'create',
        actor: 'user-1',
        resource: 'doc-1',
        action: 'created',
        severity: 'info',
        details: {},
      });

      const results = searchAuditEntries(updated, {
        actors: ['non-existent-user'],
      });

      expect(results).toHaveLength(0);
    });

    it('should handle queries for non-existent actor', () => {
      const entries = getEntriesByActor(ledger, 'non-existent');
      expect(entries).toHaveLength(0);
    });

    it('should handle queries for non-existent resource', () => {
      const entries = getEntriesByResource(ledger, 'non-existent');
      expect(entries).toHaveLength(0);
    });
  });
});
