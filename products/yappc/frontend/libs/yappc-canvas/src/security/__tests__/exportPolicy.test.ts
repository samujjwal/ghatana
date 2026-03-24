import { describe, it, expect, beforeEach } from 'vitest';

import {
  createExportPolicyStore,
  registerPolicy,
  setActivePolicy,
  getActivePolicy,
  applyRedaction,
  applyWatermark,
  signData,
  verifySignature,
  secureExport,
  getExportStatistics,
  getExportAuditTrail,
  getPoliciesForSensitivity,
  removePolicy,
  updatePolicy,
  pruneAuditLog,
  type ExportPolicy,
  type ExportContext,
  type ExportPolicyStore,
  type RedactionRule,
} from '../exportPolicy';

describe.skip('Feature 2.33: Policy-driven Export', () => {
  let store: ExportPolicyStore;

  beforeEach(() => {
    store = createExportPolicyStore();
  });

  describe('Store Creation', () => {
    it('should create store with empty state', () => {
      expect(store.policies.size).toBe(0);
      expect(store.activePolicies.size).toBe(0);
      expect(store.auditLog).toEqual([]);
      expect(store.statistics.totalExports).toBe(0);
    });

    it('should initialize statistics for all formats and sensitivity levels', () => {
      expect(store.statistics.byFormat).toEqual({
        json: 0,
        csv: 0,
        pdf: 0,
        markdown: 0,
      });
      expect(store.statistics.bySensitivity).toEqual({
        public: 0,
        internal: 0,
        confidential: 0,
        restricted: 0,
      });
    });
  });

  describe('Policy Registration', () => {
    it('should register a new policy', () => {
      const policy: ExportPolicy = {
        id: 'policy-1',
        name: 'Public Export Policy',
        sensitivity: 'public',
        redactionRules: [],
      };

      const updated = registerPolicy(store, policy);
      expect(updated.policies.size).toBe(1);
      expect(updated.policies.get('policy-1')).toEqual(policy);
    });

    it('should throw error for duplicate policy ID', () => {
      const policy: ExportPolicy = {
        id: 'policy-1',
        name: 'Test Policy',
        sensitivity: 'public',
        redactionRules: [],
      };

      const updated = registerPolicy(store, policy);
      expect(() => registerPolicy(updated, policy)).toThrow("Policy with ID 'policy-1' already exists");
    });

    it('should allow multiple policies with different IDs', () => {
      const policy1: ExportPolicy = {
        id: 'policy-1',
        name: 'Policy 1',
        sensitivity: 'public',
        redactionRules: [],
      };
      const policy2: ExportPolicy = {
        id: 'policy-2',
        name: 'Policy 2',
        sensitivity: 'internal',
        redactionRules: [],
      };

      let updated = registerPolicy(store, policy1);
      updated = registerPolicy(updated, policy2);

      expect(updated.policies.size).toBe(2);
    });
  });

  describe('Active Policy Management', () => {
    beforeEach(() => {
      const policy: ExportPolicy = {
        id: 'conf-policy',
        name: 'Confidential Policy',
        sensitivity: 'confidential',
        redactionRules: [],
      };
      store = registerPolicy(store, policy);
    });

    it('should set active policy for sensitivity level', () => {
      const updated = setActivePolicy(store, 'confidential', 'conf-policy');
      expect(updated.activePolicies.get('confidential')).toBe('conf-policy');
    });

    it('should throw error for non-existent policy', () => {
      expect(() => setActivePolicy(store, 'confidential', 'invalid')).toThrow("Policy 'invalid' not found");
    });

    it('should throw error for sensitivity mismatch', () => {
      expect(() => setActivePolicy(store, 'public', 'conf-policy')).toThrow(
        "Policy 'conf-policy' has sensitivity 'confidential' but trying to activate for 'public'"
      );
    });

    it('should retrieve active policy', () => {
      const updated = setActivePolicy(store, 'confidential', 'conf-policy');
      const active = getActivePolicy(updated, 'confidential');
      expect(active?.id).toBe('conf-policy');
    });

    it('should return undefined for inactive sensitivity level', () => {
      const active = getActivePolicy(store, 'public');
      expect(active).toBeUndefined();
    });
  });

  describe('Redaction Rules', () => {
    const sampleData = {
      name: 'John Doe',
      email: 'john@example.com',
      ssn: '123-45-6789',
      address: {
        street: '123 Main St',
        city: 'Anytown',
      },
      metadata: {
        created: '2024-01-01',
        secret: 'top-secret-value',
      },
    };

    it('should redact field with remove strategy', () => {
      const rule: RedactionRule = {
        fieldPath: 'ssn',
        minSensitivity: 'internal',
        strategy: 'remove',
      };

      const policy: ExportPolicy = {
        id: 'test-policy',
        name: 'Test',
        sensitivity: 'confidential',
        redactionRules: [rule],
      };

      const context: ExportContext = {
        userId: 'user-1',
        timestamp: Date.now(),
        format: 'json',
        sensitivity: 'confidential',
      };

      const result = applyRedaction(sampleData, policy, context);
      expect(result.hasRedactions).toBe(true);
      expect(result.redactedFields).toHaveLength(1);
      expect(result.redactedFields[0].fieldPath).toBe('ssn');
    });

    it('should redact field with mask strategy', () => {
      const rule: RedactionRule = {
        fieldPath: 'email',
        minSensitivity: 'internal',
        strategy: 'mask',
        placeholder: '***EMAIL***',
      };

      const policy: ExportPolicy = {
        id: 'test-policy',
        name: 'Test',
        sensitivity: 'confidential',
        redactionRules: [rule],
      };

      const context: ExportContext = {
        userId: 'user-1',
        timestamp: Date.now(),
        format: 'json',
        sensitivity: 'confidential',
      };

      const result = applyRedaction(sampleData, policy, context);
      expect((result.data as unknown).email).toBe('***EMAIL***');
    });

    it('should redact nested fields', () => {
      const rule: RedactionRule = {
        fieldPath: 'metadata.secret',
        minSensitivity: 'confidential',
        strategy: 'placeholder',
        placeholder: '[CLASSIFIED]',
      };

      const policy: ExportPolicy = {
        id: 'test-policy',
        name: 'Test',
        sensitivity: 'confidential',
        redactionRules: [rule],
      };

      const context: ExportContext = {
        userId: 'user-1',
        timestamp: Date.now(),
        format: 'json',
        sensitivity: 'confidential',
      };

      const result = applyRedaction(sampleData, policy, context);
      expect((result.data as unknown).metadata.secret).toBe('[CLASSIFIED]');
    });

    it('should apply hash strategy', () => {
      const rule: RedactionRule = {
        fieldPath: 'ssn',
        minSensitivity: 'internal',
        strategy: 'hash',
      };

      const policy: ExportPolicy = {
        id: 'test-policy',
        name: 'Test',
        sensitivity: 'confidential',
        redactionRules: [rule],
      };

      const context: ExportContext = {
        userId: 'user-1',
        timestamp: Date.now(),
        format: 'json',
        sensitivity: 'confidential',
      };

      const result = applyRedaction(sampleData, policy, context);
      expect((result.data as unknown).ssn).toMatch(/^\[HASH:\d+\]$/);
    });

    it('should skip redaction if sensitivity too low', () => {
      const rule: RedactionRule = {
        fieldPath: 'ssn',
        minSensitivity: 'restricted',
        strategy: 'remove',
      };

      const policy: ExportPolicy = {
        id: 'test-policy',
        name: 'Test',
        sensitivity: 'confidential',
        redactionRules: [rule],
      };

      const context: ExportContext = {
        userId: 'user-1',
        timestamp: Date.now(),
        format: 'json',
        sensitivity: 'confidential', // Not high enough for 'restricted'
      };

      const result = applyRedaction(sampleData, policy, context);
      expect(result.hasRedactions).toBe(false);
      expect((result.data as unknown).ssn).toBe('123-45-6789');
    });

    it('should apply multiple redaction rules', () => {
      const rules: RedactionRule[] = [
        {
          fieldPath: 'ssn',
          minSensitivity: 'internal',
          strategy: 'remove',
        },
        {
          fieldPath: 'email',
          minSensitivity: 'internal',
          strategy: 'mask',
        },
      ];

      const policy: ExportPolicy = {
        id: 'test-policy',
        name: 'Test',
        sensitivity: 'confidential',
        redactionRules: rules,
      };

      const context: ExportContext = {
        userId: 'user-1',
        timestamp: Date.now(),
        format: 'json',
        sensitivity: 'confidential',
      };

      const result = applyRedaction(sampleData, policy, context);
      expect(result.redactedFields).toHaveLength(2);
    });

    it('should handle non-existent fields gracefully', () => {
      const rule: RedactionRule = {
        fieldPath: 'nonexistent',
        minSensitivity: 'internal',
        strategy: 'remove',
      };

      const policy: ExportPolicy = {
        id: 'test-policy',
        name: 'Test',
        sensitivity: 'confidential',
        redactionRules: [rule],
      };

      const context: ExportContext = {
        userId: 'user-1',
        timestamp: Date.now(),
        format: 'json',
        sensitivity: 'confidential',
      };

      const result = applyRedaction(sampleData, policy, context);
      expect(result.hasRedactions).toBe(false);
    });
  });

  describe('Watermarking', () => {
    it('should apply watermark to data', () => {
      const config = {
        template: 'CONFIDENTIAL - Export {id} - {user} - {timestamp}',
        position: 'header' as const,
        opacity: 0.5,
      };

      const context: ExportContext = {
        userId: 'user-1',
        timestamp: Date.now(),
        format: 'pdf',
        sensitivity: 'confidential',
        exportId: 'EXP-001',
      };

      const data = { title: 'Test Document' };
      const result = applyWatermark(data, config, context);

      expect(result.watermarkText).toContain('CONFIDENTIAL');
      expect(result.watermarkText).toContain('EXP-001');
      expect(result.watermarkText).toContain('user-1');
      expect(result.position).toBe('header');
    });

    it('should embed watermark in structured data', () => {
      const config = {
        template: 'CLASSIFIED - {id}',
        position: 'footer' as const,
        opacity: 0.3,
      };

      const context: ExportContext = {
        userId: 'user-1',
        timestamp: Date.now(),
        format: 'json',
        sensitivity: 'restricted',
        exportId: 'EXP-002',
      };

      const data = { title: 'Secret Document' };
      const result = applyWatermark(data, config, context);

      expect((result.data as unknown).__watermark).toBeDefined();
      expect((result.data as unknown).__watermark.text).toContain('EXP-002');
      expect((result.data as unknown).__watermark.position).toBe('footer');
    });

    it('should handle missing export ID gracefully', () => {
      const config = {
        template: 'Export {id}',
        position: 'background' as const,
        opacity: 0.2,
      };

      const context: ExportContext = {
        userId: 'user-1',
        timestamp: Date.now(),
        format: 'pdf',
        sensitivity: 'internal',
        // No exportId
      };

      const data = { content: 'test' };
      const result = applyWatermark(data, config, context);

      expect(result.watermarkText).toContain('N/A');
    });
  });

  describe('Data Signing', () => {
    it('should sign data with signature', () => {
      const config = {
        algorithm: 'RS256' as const,
        key: 'test-private-key',
        keyId: 'key-001',
      };

      const context: ExportContext = {
        userId: 'user-1',
        timestamp: Date.now(),
        format: 'json',
        sensitivity: 'confidential',
      };

      const data = { content: 'sensitive data' };
      const result = signData(data, config, context);

      expect(result.signature).toBeDefined();
      expect(result.signature).toContain('SIG_');
      expect(result.algorithm).toBe('RS256');
      expect(result.keyId).toBe('key-001');
      expect(result.dataHash).toBeDefined();
    });

    it('should generate different signatures for different data', () => {
      const config = {
        algorithm: 'HS256' as const,
        key: 'secret',
      };

      const context: ExportContext = {
        userId: 'user-1',
        timestamp: Date.now(),
        format: 'json',
        sensitivity: 'internal',
      };

      const result1 = signData({ value: 'data1' }, config, context);
      const result2 = signData({ value: 'data2' }, config, context);

      expect(result1.signature).not.toBe(result2.signature);
      expect(result1.dataHash).not.toBe(result2.dataHash);
    });

    it('should verify valid signature', () => {
      const config = {
        algorithm: 'RS256' as const,
        key: 'test-key',
        keyId: 'key-001',
      };

      const context: ExportContext = {
        userId: 'user-1',
        timestamp: Date.now(),
        format: 'json',
        sensitivity: 'confidential',
        exportId: 'EXP-001',
      };

      const data = { content: 'test' };
      const signResult = signData(data, config, context);

      const bundle = {
        data,
        policy: { id: 'p1', name: 'Test', sensitivity: 'confidential' as const },
        context,
        signature: signResult,
        metadata: { exportedAt: Date.now(), format: 'json' as const, version: '1.0.0' },
      };

      expect(verifySignature(bundle, config)).toBe(true);
    });

    it('should reject tampered data', () => {
      const config = {
        algorithm: 'RS256' as const,
        key: 'test-key',
      };

      const context: ExportContext = {
        userId: 'user-1',
        timestamp: Date.now(),
        format: 'json',
        sensitivity: 'confidential',
      };

      const originalData = { content: 'original' };
      const signResult = signData(originalData, config, context);

      // Create bundle with tampered data
      const bundle = {
        data: { content: 'tampered' },
        policy: { id: 'p1', name: 'Test', sensitivity: 'confidential' as const },
        context,
        signature: signResult,
        metadata: { exportedAt: Date.now(), format: 'json' as const, version: '1.0.0' },
      };

      expect(verifySignature(bundle, config)).toBe(false);
    });

    it('should reject bundle without signature', () => {
      const config = {
        algorithm: 'RS256' as const,
        key: 'test-key',
      };

      const context: ExportContext = {
        userId: 'user-1',
        timestamp: Date.now(),
        format: 'json',
        sensitivity: 'internal',
      };

      const bundle = {
        data: { content: 'test' },
        policy: { id: 'p1', name: 'Test', sensitivity: 'internal' as const },
        context,
        metadata: { exportedAt: Date.now(), format: 'json' as const, version: '1.0.0' },
      };

      expect(verifySignature(bundle, config)).toBe(false);
    });
  });

  describe('Secure Export', () => {
    beforeEach(() => {
      const policy: ExportPolicy = {
        id: 'secure-policy',
        name: 'Secure Export Policy',
        sensitivity: 'confidential',
        redactionRules: [
          {
            fieldPath: 'password',
            minSensitivity: 'internal',
            strategy: 'remove',
            reason: 'Security',
          },
        ],
        watermark: {
          template: 'CONFIDENTIAL - {id}',
          position: 'header',
          opacity: 0.5,
        },
        signing: {
          algorithm: 'RS256',
          key: 'private-key',
          keyId: 'key-001',
        },
        includeAuditTrail: true,
      };
      store = registerPolicy(store, policy);
      store = setActivePolicy(store, 'confidential', 'secure-policy');
    });

    it('should perform complete secure export', () => {
      const data = {
        username: 'admin',
        password: 'secret123',
        content: 'sensitive document',
      };

      const context: ExportContext = {
        userId: 'user-1',
        timestamp: Date.now(),
        format: 'json',
        sensitivity: 'confidential',
        exportId: 'EXP-001',
      };

      const { store: updated, bundle } = secureExport(store, data, context);

      expect(bundle.data).toBeDefined();
      expect((bundle.data as unknown).password).toBeUndefined(); // Redacted
      expect(bundle.redactions?.count).toBe(1);
      expect(bundle.watermark).toBeDefined();
      expect(bundle.signature).toBeDefined();
      expect(bundle.auditTrail).toBeDefined();
      expect(bundle.auditTrail?.length).toBeGreaterThan(0);
      expect(updated.statistics.totalExports).toBe(1);
    });

    it('should update statistics correctly', () => {
      const context: ExportContext = {
        userId: 'user-1',
        timestamp: Date.now(),
        format: 'json',
        sensitivity: 'confidential',
      };

      const { store: updated } = secureExport(store, { data: 'test' }, context);

      expect(updated.statistics.totalExports).toBe(1);
      expect(updated.statistics.byFormat.json).toBe(1);
      expect(updated.statistics.bySensitivity.confidential).toBe(1);
    });

    it('should throw error for missing active policy', () => {
      const context: ExportContext = {
        userId: 'user-1',
        timestamp: Date.now(),
        format: 'json',
        sensitivity: 'public', // No policy registered for 'public'
      };

      expect(() => secureExport(store, {}, context)).toThrow("No active policy found for sensitivity level 'public'");
    });

    it('should respect format restrictions', () => {
      const limitedPolicy: ExportPolicy = {
        id: 'csv-only',
        name: 'CSV Only Policy',
        sensitivity: 'internal',
        redactionRules: [],
        applicableFormats: ['csv'],
      };

      let updated = registerPolicy(store, limitedPolicy);
      updated = setActivePolicy(updated, 'internal', 'csv-only');

      const context: ExportContext = {
        userId: 'user-1',
        timestamp: Date.now(),
        format: 'json', // Not allowed
        sensitivity: 'internal',
      };

      expect(() => secureExport(updated, {}, context)).toThrow("Policy 'csv-only' does not support format 'json'");
    });

    it('should create audit trail entries', () => {
      const data = { username: 'test', password: 'secret' };

      const context: ExportContext = {
        userId: 'user-1',
        timestamp: Date.now(),
        format: 'json',
        sensitivity: 'confidential',
        exportId: 'EXP-002',
      };

      const { store: updated } = secureExport(store, data, context);

      expect(updated.auditLog.length).toBeGreaterThan(0);
      const exportEntry = updated.auditLog.find((e) => e.action === 'export');
      expect(exportEntry).toBeDefined();
      expect(exportEntry?.userId).toBe('user-1');
    });

    it('should include policy metadata in bundle', () => {
      const policyWithMetadata: ExportPolicy = {
        id: 'meta-policy',
        name: 'Policy with Metadata',
        sensitivity: 'restricted',
        redactionRules: [],
        metadata: {
          department: 'Security',
          compliance: 'SOC2',
        },
      };

      let updated = registerPolicy(store, policyWithMetadata);
      updated = setActivePolicy(updated, 'restricted', 'meta-policy');

      const context: ExportContext = {
        userId: 'user-1',
        timestamp: Date.now(),
        format: 'json',
        sensitivity: 'restricted',
      };

      const { bundle } = secureExport(updated, { data: 'test' }, context);

      expect(bundle.metadata.department).toBe('Security');
      expect(bundle.metadata.compliance).toBe('SOC2');
    });
  });

  describe('Statistics and Queries', () => {
    beforeEach(() => {
      const policy1: ExportPolicy = {
        id: 'policy-1',
        name: 'Policy 1',
        sensitivity: 'internal',
        redactionRules: [],
      };
      const policy2: ExportPolicy = {
        id: 'policy-2',
        name: 'Policy 2',
        sensitivity: 'confidential',
        redactionRules: [],
      };

      store = registerPolicy(store, policy1);
      store = registerPolicy(store, policy2);
      store = setActivePolicy(store, 'internal', 'policy-1');
      store = setActivePolicy(store, 'confidential', 'policy-2');
    });

    it('should get export statistics', () => {
      const context1: ExportContext = {
        userId: 'user-1',
        timestamp: Date.now(),
        format: 'json',
        sensitivity: 'internal',
      };
      const context2: ExportContext = {
        userId: 'user-1',
        timestamp: Date.now(),
        format: 'csv',
        sensitivity: 'confidential',
      };

      let updated = secureExport(store, {}, context1).store;
      updated = secureExport(updated, {}, context2).store;

      const stats = getExportStatistics(updated);
      expect(stats.totalExports).toBe(2);
      expect(stats.byFormat.json).toBe(1);
      expect(stats.byFormat.csv).toBe(1);
      expect(stats.bySensitivity.internal).toBe(1);
      expect(stats.bySensitivity.confidential).toBe(1);
    });

    it('should get audit trail for specific export', () => {
      const context: ExportContext = {
        userId: 'user-1',
        timestamp: Date.now(),
        format: 'json',
        sensitivity: 'internal',
        exportId: 'EXP-001',
      };

      const { store: updated } = secureExport(store, {}, context);
      const trail = getExportAuditTrail(updated, 'EXP-001');

      expect(trail.length).toBeGreaterThan(0);
      expect(trail.every((entry) => entry.details.exportId === 'EXP-001')).toBe(true);
    });

    it('should get policies for sensitivity level', () => {
      const policies = getPoliciesForSensitivity(store, 'internal');
      expect(policies).toHaveLength(1);
      expect(policies[0].id).toBe('policy-1');
    });

    it('should return empty array for sensitivity with no policies', () => {
      const policies = getPoliciesForSensitivity(store, 'public');
      expect(policies).toHaveLength(0);
    });
  });

  describe('Policy Management', () => {
    beforeEach(() => {
      const policy: ExportPolicy = {
        id: 'test-policy',
        name: 'Test Policy',
        sensitivity: 'internal',
        redactionRules: [],
      };
      store = registerPolicy(store, policy);
    });

    it('should update policy', () => {
      const updated = updatePolicy(store, 'test-policy', {
        name: 'Updated Policy',
        description: 'New description',
      });

      const policy = updated.policies.get('test-policy');
      expect(policy?.name).toBe('Updated Policy');
      expect(policy?.description).toBe('New description');
      expect(policy?.id).toBe('test-policy'); // ID unchanged
    });

    it('should throw error when updating non-existent policy', () => {
      expect(() => updatePolicy(store, 'invalid', { name: 'Test' })).toThrow("Policy 'invalid' not found");
    });

    it('should prevent ID changes in updates', () => {
      expect(() => updatePolicy(store, 'test-policy', { id: 'new-id' })).toThrow('Cannot change policy ID');
    });

    it('should remove inactive policy', () => {
      const updated = removePolicy(store, 'test-policy');
      expect(updated.policies.has('test-policy')).toBe(false);
    });

    it('should throw error when removing active policy', () => {
      const updated = setActivePolicy(store, 'internal', 'test-policy');
      expect(() => removePolicy(updated, 'test-policy')).toThrow(
        "Cannot remove active policy 'test-policy'. Deactivate it first."
      );
    });

    it('should throw error when removing non-existent policy', () => {
      expect(() => removePolicy(store, 'invalid')).toThrow("Policy 'invalid' not found");
    });
  });

  describe('Audit Log Maintenance', () => {
    beforeEach(() => {
      const policy: ExportPolicy = {
        id: 'test-policy',
        name: 'Test',
        sensitivity: 'internal',
        redactionRules: [],
      };
      store = registerPolicy(store, policy);
      store = setActivePolicy(store, 'internal', 'test-policy');
    });

    it('should prune old audit log entries', () => {
      // Manually create audit entries with specific timestamps
      const oldTimestamp = Date.now() - 10 * 24 * 60 * 60 * 1000; // 10 days ago
      const recentTimestamp = Date.now();

      // Add old audit entry manually
      const storeWithOldEntry: ExportPolicyStore = {
        ...store,
        auditLog: [
          {
            timestamp: oldTimestamp,
            action: 'export',
            userId: 'user-1',
            details: { format: 'json', sensitivity: 'internal' },
            policyId: 'test-policy',
          },
        ],
      };

      // Add recent entry
      const context: ExportContext = {
        userId: 'user-1',
        timestamp: recentTimestamp,
        format: 'json',
        sensitivity: 'internal',
      };

      const { store: updated } = secureExport(storeWithOldEntry, {}, context);

      const originalCount = updated.auditLog.length;
      expect(originalCount).toBeGreaterThanOrEqual(2); // At least old entry + new export entries

      const pruned = pruneAuditLog(updated, 7); // Keep 7 days

      expect(pruned.auditLog.length).toBeLessThan(originalCount);
      // All remaining entries should be recent (within 7 days)
      const cutoffTime = Date.now() - 7 * 24 * 60 * 60 * 1000;
      expect(pruned.auditLog.every((entry) => entry.timestamp >= cutoffTime)).toBe(true);
    });

    it('should keep recent entries when pruning', () => {
      const context: ExportContext = {
        userId: 'user-1',
        timestamp: Date.now(),
        format: 'json',
        sensitivity: 'internal',
      };

      const { store: updated } = secureExport(store, {}, context);
      const pruned = pruneAuditLog(updated, 30);

      expect(pruned.auditLog.length).toBeGreaterThan(0);
    });
  });

  describe('Edge Cases', () => {
    it('should handle empty data', () => {
      const policy: ExportPolicy = {
        id: 'test',
        name: 'Test',
        sensitivity: 'public',
        redactionRules: [],
      };

      store = registerPolicy(store, policy);
      store = setActivePolicy(store, 'public', 'test');

      const context: ExportContext = {
        userId: 'user-1',
        timestamp: Date.now(),
        format: 'json',
        sensitivity: 'public',
      };

      const { bundle } = secureExport(store, {}, context);
      expect(bundle.data).toEqual({});
    });

    it('should handle array data', () => {
      const policy: ExportPolicy = {
        id: 'test',
        name: 'Test',
        sensitivity: 'internal',
        redactionRules: [],
      };

      store = registerPolicy(store, policy);
      store = setActivePolicy(store, 'internal', 'test');

      const context: ExportContext = {
        userId: 'user-1',
        timestamp: Date.now(),
        format: 'json',
        sensitivity: 'internal',
      };

      const arrayData = [1, 2, 3];
      const { bundle } = secureExport(store, arrayData, context);
      expect(Array.isArray(bundle.data)).toBe(true);
    });

    it('should handle null data', () => {
      const policy: ExportPolicy = {
        id: 'test',
        name: 'Test',
        sensitivity: 'public',
        redactionRules: [],
      };

      store = registerPolicy(store, policy);
      store = setActivePolicy(store, 'public', 'test');

      const context: ExportContext = {
        userId: 'user-1',
        timestamp: Date.now(),
        format: 'json',
        sensitivity: 'public',
      };

      const { bundle } = secureExport(store, null, context);
      expect(bundle.data).toBeNull();
    });

    it('should handle policy without watermark or signing', () => {
      const simplePolicy: ExportPolicy = {
        id: 'simple',
        name: 'Simple Policy',
        sensitivity: 'public',
        redactionRules: [],
        // No watermark or signing
      };

      store = registerPolicy(store, simplePolicy);
      store = setActivePolicy(store, 'public', 'simple');

      const context: ExportContext = {
        userId: 'user-1',
        timestamp: Date.now(),
        format: 'json',
        sensitivity: 'public',
      };

      const { bundle } = secureExport(store, { test: 'data' }, context);
      expect(bundle.watermark).toBeUndefined();
      expect(bundle.signature).toBeUndefined();
    });
  });
});
