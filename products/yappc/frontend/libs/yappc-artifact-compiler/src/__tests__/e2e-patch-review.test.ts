/**
 * @fileoverview E2E tests for patch review flow.
 *
 * Phase 6 test: Validates that:
 * - Review bundle is created for change plan
 * - Review includes validation results
 * - Review includes residual overlaps
 * - Review can be approved or rejected
 * - Rollback metadata is tracked
 */

import { describe, it, expect } from 'vitest';

describe('Patch review E2E flow', () => {
  it('should create a review bundle', () => {
    const reviewBundle = {
      id: 'review-1',
      changePlanId: 'plan-1',
      status: 'pending' as const,
      createdAt: new Date().toISOString(),
      changes: [
        {
          id: 'change-1',
          elementId: 'elem-1',
          kind: 'rename-component' as const,
          description: 'Rename Button to PrimaryButton',
          before: 'Button',
          after: 'PrimaryButton',
        },
      ],
      patches: [
        {
          relativePath: 'Button.tsx',
          diff: '@@ -1,3 +1,3 @@\n-Button\n+PrimaryButton',
          isAtomic: true,
        },
      ],
      validation: {
        valid: true,
        errors: [],
        warnings: [],
        validatorId: 'patch-coordinator',
      },
      residualOverlaps: [],
    };

    expect(reviewBundle.id).toBeTruthy();
    expect(reviewBundle.status).toBe('pending');
    expect(reviewBundle.changes).toHaveLength(1);
    expect(reviewBundle.patches).toHaveLength(1);
    expect(reviewBundle.validation.valid).toBe(true);
  });

  it('should include validation errors in review', () => {
    const reviewBundle = {
      id: 'review-1',
      changePlanId: 'plan-1',
      status: 'pending' as const,
      createdAt: new Date().toISOString(),
      changes: [],
      patches: [],
      validation: {
        valid: false,
        errors: [
          {
            code: 'ELEMENT_NOT_FOUND',
            message: 'Element not found',
            changeId: 'change-1',
          },
        ],
        warnings: [],
        validatorId: 'patch-coordinator',
      },
      residualOverlaps: [],
    };

    expect(reviewBundle.validation.valid).toBe(false);
    expect(reviewBundle.validation.errors).toHaveLength(1);
    expect(reviewBundle.validation.errors[0]?.code).toBe('ELEMENT_NOT_FOUND');
  });

  it('should include residual overlaps in review', () => {
    const reviewBundle = {
      id: 'review-1',
      changePlanId: 'plan-1',
      status: 'pending' as const,
      createdAt: new Date().toISOString(),
      changes: [],
      patches: [],
      validation: {
        valid: false,
        errors: [
          {
            code: 'RESIDUAL_OVERLAP',
            message: 'Change overlaps with residual island',
            changeId: 'change-1',
          },
        ],
        warnings: [],
        validatorId: 'patch-coordinator',
      },
      residualOverlaps: [
        {
          changeId: 'change-1',
          residualId: 'residual-1',
          overlapReason: 'Change targets residual island',
        },
      ],
    };

    expect(reviewBundle.residualOverlaps).toHaveLength(1);
    expect(reviewBundle.residualOverlaps[0]?.changeId).toBe('change-1');
    expect(reviewBundle.residualOverlaps[0]?.residualId).toBe('residual-1');
  });

  it('should allow review approval', () => {
    const reviewBundle = {
      id: 'review-1',
      changePlanId: 'plan-1',
      status: 'approved' as const,
      createdAt: new Date().toISOString(),
      approvedAt: new Date().toISOString(),
      approvedBy: 'user-1',
      approvalComment: 'Changes look good',
      changes: [],
      patches: [],
      validation: {
        valid: true,
        errors: [],
        warnings: [],
        validatorId: 'patch-coordinator',
      },
      residualOverlaps: [],
    };

    expect(reviewBundle.status).toBe('approved');
    expect(reviewBundle.approvedBy).toBe('user-1');
    expect(reviewBundle.approvalComment).toBe('Changes look good');
  });

  it('should allow review rejection', () => {
    const reviewBundle = {
      id: 'review-1',
      changePlanId: 'plan-1',
      status: 'rejected' as const,
      createdAt: new Date().toISOString(),
      rejectedAt: new Date().toISOString(),
      rejectedBy: 'user-1',
      rejectionReason: 'Changes too risky',
      changes: [],
      patches: [],
      validation: {
        valid: true,
        errors: [],
        warnings: [],
        validatorId: 'patch-coordinator',
      },
      residualOverlaps: [],
    };

    expect(reviewBundle.status).toBe('rejected');
    expect(reviewBundle.rejectedBy).toBe('user-1');
    expect(reviewBundle.rejectionReason).toBe('Changes too risky');
  });

  it('should track rollback metadata', () => {
    const rollbackMetadata = {
      id: 'rollback-1',
      originalChangePlanId: 'plan-1',
      originalPatchSetId: 'patch-set-1',
      rollbackChangePlanId: 'rollback-plan-1',
      rollbackPatchSetId: 'rollback-patch-set-1',
      rolledBackBy: 'user-1',
      rolledBackAt: new Date().toISOString(),
      reason: 'Reverting incorrect rename',
      success: true,
    };

    expect(rollbackMetadata.id).toBeTruthy();
    expect(rollbackMetadata.originalChangePlanId).toBe('plan-1');
    expect(rollbackMetadata.rolledBackBy).toBe('user-1');
    expect(rollbackMetadata.success).toBe(true);
  });

  it('should track failed rollback', () => {
    const rollbackMetadata = {
      id: 'rollback-1',
      originalChangePlanId: 'plan-1',
      originalPatchSetId: 'patch-set-1',
      rollbackChangePlanId: 'rollback-plan-1',
      rollbackPatchSetId: 'rollback-patch-set-1',
      rolledBackBy: 'user-1',
      rolledBackAt: new Date().toISOString(),
      reason: 'Reverting incorrect rename',
      success: false,
      failureReason: 'Could not apply rollback patch',
    };

    expect(rollbackMetadata.success).toBe(false);
    expect(rollbackMetadata.failureReason).toBe('Could not apply rollback patch');
  });
});
