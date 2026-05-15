import { describe, expect, it } from 'vitest';
import {
  ProductApprovalGateManager,
  ProductApprovalGateSchema,
  type ProductApprovalGate,
} from '../ProductApprovalGate.js';

describe('ProductApprovalGate', () => {
  const manager = new ProductApprovalGateManager();

  function createGate(overrides: Partial<ProductApprovalGate> = {}): ProductApprovalGate {
    return manager.createApprovalGate({
      approvalId: 'deploy-prod-approval',
      productId: 'digital-marketing',
      runId: 'run-1',
      correlationId: 'corr-1',
      environment: 'prod',
      action: 'deploy',
      riskLevel: 'high',
      requestedBy: 'release-manager',
      requestedAt: '2026-05-14T10:00:00.000Z',
      evidenceRefs: ['lifecycle-result:run-1'],
      approvers: ['alice', 'bob'],
      requiredApprovals: 2,
      ...overrides,
    });
  }

  it('creates a lifecycle-scoped approval gate', () => {
    const gate = createGate();
    const recreatedGate = manager.createApprovalGate(gate);

    expect(gate).toMatchObject({
      approvalId: 'deploy-prod-approval',
      runId: 'run-1',
      correlationId: 'corr-1',
      action: 'deploy',
      riskLevel: 'high',
      requestedBy: 'release-manager',
      status: 'pending',
      requiredApprovals: 2,
      evidenceRefs: ['lifecycle-result:run-1'],
    });
    expect(recreatedGate).toEqual(gate);
  });

  it('fills optional creation defaults and preserves approval comments', () => {
    const gate = manager.createApprovalGate({
      approvalId: 'package-approval',
      productId: 'digital-marketing',
      runId: 'run-2',
      correlationId: 'corr-2',
      environment: 'staging',
      action: 'package',
      riskLevel: 'low',
      requestedBy: 'release-manager',
      required: false,
      approvers: ['alice'],
    });
    const approvedGate = manager.approve(gate, 'alice', 'Package artifact is verified');

    expect(gate.requestedAt).toBeDefined();
    expect(gate.evidenceRefs).toEqual([]);
    expect(gate.requiredApprovals).toBe(1);
    expect(gate.required).toBe(false);
    expect(approvedGate.approvals[0].comment).toBe('Package artifact is verified');
  });

  it('approves only after enough unique approvals are recorded', () => {
    const firstDecision = manager.recordDecision(createGate(), true, {
      approver: 'alice',
      decidedAt: '2026-05-14T10:05:00.000Z',
      evidenceRefs: ['approval:alice'],
    });
    const secondDecision = manager.recordDecision(firstDecision, true, {
      approver: 'bob',
      comment: 'Release checks passed',
      decidedAt: '2026-05-14T10:06:00.000Z',
    });

    expect(firstDecision.status).toBe('pending');
    expect(secondDecision.status).toBe('approved');
    expect(secondDecision.decidedAt).toBe('2026-05-14T10:06:00.000Z');
    expect(secondDecision.approvals).toHaveLength(2);
  });

  it('requires a rejection reason and records rejected status', () => {
    expect(() =>
      manager.reject(createGate(), 'alice'),
    ).toThrow('Rejection reason is required');

    const rejectedGate = manager.reject(
      createGate(),
      'alice',
      'Deployment risk accepted by neither product nor platform owner',
    );

    expect(rejectedGate.status).toBe('rejected');
    expect(rejectedGate.decidedAt).toBeDefined();
    expect(rejectedGate.approvals[0]).toMatchObject({
      approver: 'alice',
      approved: false,
      comment: 'Deployment risk accepted by neither product nor platform owner',
    });
  });

  it('prevents duplicate approvals unless explicitly configured', () => {
    const gate = manager.approve(createGate(), 'alice');

    expect(() => manager.approve(gate, 'alice')).toThrow(
      'Duplicate approval decision for approver: alice',
    );

    const duplicatePolicyGate = {
      ...gate,
      approvalPolicy: {
        allowDuplicateApprovals: true,
      },
    };
    const duplicateAllowed = manager.recordDecision(duplicatePolicyGate, true, {
      approver: 'alice',
      decidedAt: '2026-05-14T10:07:00.000Z',
    });

    expect(duplicateAllowed.approvals).toHaveLength(2);
  });

  it('validates approval policy constraints', () => {
    const parsed = ProductApprovalGateSchema.safeParse({
      ...createGate({ requiredApprovals: 2 }),
      approvalPolicy: {
        minApprovals: 2,
        allowedApprovers: ['alice'],
        requireEvidenceForRisk: ['high'],
      },
      requiredApprovals: 1,
    });

    expect(parsed.success).toBe(false);
    if (!parsed.success) {
      expect(parsed.error.issues.some((issue) =>
        issue.message === 'Required approvals must satisfy approval policy minimum: 2',
      )).toBe(true);
    }
  });

  it('returns validation details for valid and invalid gates', () => {
    expect(manager.validateApprovalGate(createGate())).toEqual({
      valid: true,
      errors: [],
    });

    const invalidGate = {
      ...createGate(),
      requiredApprovals: 3,
    } as ProductApprovalGate;

    const validation = manager.validateApprovalGate(invalidGate);

    expect(validation.valid).toBe(false);
    expect(validation.errors).toContain('Required approvals cannot exceed number of approvers');
  });

  it('reports every schema-level approval policy violation', () => {
    const decidedApproval = {
      approvalId: 'different-approval',
      approver: 'mallory',
      approved: false,
      timestamp: '2026-05-14T10:08:00.000Z',
      decidedAt: '2026-05-14T10:08:00.000Z',
    };

    const parsed = ProductApprovalGateSchema.safeParse({
      ...createGate({
        approvalPolicy: {
          allowDuplicateApprovals: false,
          allowedApprovers: ['alice'],
          requireCommentOnRejection: true,
        },
      }),
      required: true,
      approvers: [],
      requiredApprovals: 2,
      evidenceRefs: [],
      status: 'rejected',
      approvals: [decidedApproval, { ...decidedApproval, approvalId: 'deploy-prod-approval' }],
    });

    expect(parsed.success).toBe(false);
    if (!parsed.success) {
      const messages = parsed.error.issues.map((issue) => issue.message);
      expect(messages).toContain('Approvers are required when gate is required');
      expect(messages).toContain('Required approvals cannot exceed number of approvers');
      expect(messages).toContain('Evidence refs are required for high risk approvals');
      expect(messages).toContain('Approval decision approvalId must match gate approvalId');
      expect(messages).toContain('Approval decision approver is not authorized: mallory');
      expect(messages).toContain('Approval policy does not allow approver: mallory');
      expect(messages).toContain('Duplicate approval decision for approver: mallory');
      expect(messages).toContain('Rejection reason is required');
      expect(messages).toContain('decidedAt is required when approval gate is decided');
    }
  });

  it('rejects unauthorized approvers and status drift', () => {
    expect(() => manager.approve(createGate(), 'mallory')).toThrow(
      'Approver is not authorized for approval gate: mallory',
    );

    const parsed = ProductApprovalGateSchema.safeParse({
      ...createGate(),
      status: 'approved',
    });

    expect(parsed.success).toBe(false);
    if (!parsed.success) {
      expect(parsed.error.issues.some((issue) => issue.message === 'Approval gate status must be pending')).toBe(true);
    }
  });
});
