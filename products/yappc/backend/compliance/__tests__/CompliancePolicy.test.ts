/**
 * Compliance Policy Manager Tests
 *
 * Tests for compliance policy management, versioning, and acknowledgments.
 * Located in: products/yappc/backend/compliance
 *
 * @jest-environment jsdom
 */

import { CompliancePolicyManager, CompliancePolicy, PolicyStatus } from '../CompliancePolicy';

describe('CompliancePolicyManager', () => {
  let manager: CompliancePolicyManager;

  beforeEach(() => {
    manager = new CompliancePolicyManager();
  });

  describe('Policy Management', () => {
    it('should create and store policy', () => {
      const policy: Omit<CompliancePolicy, 'id' | 'createdDate' | 'updatedDate'> = {
        name: 'Data Classification Policy',
        framework: 'ISO_27001',
        content: 'All data must be classified...',
        status: PolicyStatus.ACTIVE,
        version: '1.0',
        owner: 'security-team',
        reviewCycle: 365,
        acknowledgments: [],
      };

      const created = manager.createPolicy(policy);

      expect(created).toBeDefined();
      expect(created.name).toBe('Data Classification Policy');
      expect(created.status).toBe(PolicyStatus.ACTIVE);
      expect(created.version).toBe('1.0');
    });

    it('should retrieve policy by ID', () => {
      const policy: Omit<CompliancePolicy, 'id' | 'createdDate' | 'updatedDate'> = {
        name: 'Access Control Policy',
        framework: 'SOC2',
        content: 'Access controls...',
        status: PolicyStatus.ACTIVE,
        version: '1.0',
        owner: 'security-team',
        reviewCycle: 365,
        acknowledgments: [],
      };

      const created = manager.createPolicy(policy);
      const retrieved = manager.getPolicy(created.id);

      expect(retrieved).toEqual(created);
    });

    it('should list policies by status', () => {
      manager.createPolicy({
        name: 'Policy 1',
        framework: 'SOC2',
        content: 'Content 1',
        status: PolicyStatus.ACTIVE,
        version: '1.0',
        owner: 'team',
        reviewCycle: 365,
        acknowledgments: [],
      });

      manager.createPolicy({
        name: 'Policy 2',
        framework: 'ISO_27001',
        content: 'Content 2',
        status: PolicyStatus.DRAFT,
        version: '1.0',
        owner: 'team',
        reviewCycle: 365,
        acknowledgments: [],
      });

      const active = manager.getPoliciesByStatus(PolicyStatus.ACTIVE);
      const draft = manager.getPoliciesByStatus(PolicyStatus.DRAFT);

      expect(active).toHaveLength(1);
      expect(draft).toHaveLength(1);
    });
  });

  describe('Policy Updates', () => {
    it('should update policy and increment version', () => {
      const policy = manager.createPolicy({
        name: 'Original',
        framework: 'SOC2',
        content: 'Original content',
        status: PolicyStatus.ACTIVE,
        version: '1.0',
        owner: 'team',
        reviewCycle: 365,
        acknowledgments: [],
      });

      const updated = manager.updatePolicy(policy.id, {
        content: 'Updated content',
        status: PolicyStatus.ACTIVE,
      });

      expect(updated?.content).toBe('Updated content');
      expect(updated?.version).toBe('1.1');
    });

    it('should archive policy', () => {
      const policy = manager.createPolicy({
        name: 'To Archive',
        framework: 'SOC2',
        content: 'Content',
        status: PolicyStatus.ACTIVE,
        version: '1.0',
        owner: 'team',
        reviewCycle: 365,
        acknowledgments: [],
      });

      const archived = manager.archivePolicy(policy.id);

      expect(archived?.status).toBe(PolicyStatus.ARCHIVED);
    });
  });

  describe('Acknowledgments', () => {
    it('should record user acknowledgment', () => {
      const policy = manager.createPolicy({
        name: 'Policy to Acknowledge',
        framework: 'SOC2',
        content: 'Content',
        status: PolicyStatus.ACTIVE,
        version: '1.0',
        owner: 'team',
        reviewCycle: 365,
        acknowledgments: [],
      });

      manager.recordAcknowledgment(policy.id, 'user@example.com');

      const retrieved = manager.getPolicy(policy.id);
      expect(retrieved?.acknowledgments).toHaveLength(1);
      expect(retrieved?.acknowledgments[0].email).toBe('user@example.com');
    });

    it('should find policies requiring acknowledgment', () => {
      const policy = manager.createPolicy({
        name: 'Needs Acknowledgment',
        framework: 'SOC2',
        content: 'Content',
        status: PolicyStatus.ACTIVE,
        version: '1.0',
        owner: 'team',
        reviewCycle: 365,
        acknowledgments: [],
      });

      const needsAck = manager.getPoliciesNeedingAcknowledgment('user@example.com');

      expect(needsAck.length).toBeGreaterThan(0);
      expect(needsAck[0].id).toBe(policy.id);
    });
  });

  describe('Compliance', () => {
    it('should identify policies due for review', () => {
      const policy = manager.createPolicy({
        name: 'Review Needed',
        framework: 'SOC2',
        content: 'Content',
        status: PolicyStatus.ACTIVE,
        version: '1.0',
        owner: 'team',
        reviewCycle: 1, // 1 day
        acknowledgments: [],
      });

      // Wait for date to pass (in real scenario, would be time-based)
      const due = manager.getPoliciesDueForReview(90);

      expect(due.length).toBeGreaterThan(0);
    });
  });
});
