import { z } from 'zod';

/**
 * Product approval gate schema
 */
export const ProductApprovalGateSchema = z.object({
  productId: z.string(),
  environment: z.string(),
  gateName: z.string(),
  required: z.boolean(),
  approvers: z.array(z.string()),
  approvals: z.array(z.object({
    approver: z.string(),
    approved: z.boolean(),
    timestamp: z.string(),
    comment: z.string().optional(),
  })),
  status: z.enum(['pending', 'approved', 'rejected']),
  requiredApprovals: z.number(),
});

export type ProductApprovalGate = z.infer<typeof ProductApprovalGateSchema>;

/**
 * Approval gate manager
 */
export class ProductApprovalGateManager {
  createApprovalGate(gate: ProductApprovalGate): ProductApprovalGate {
    ProductApprovalGateSchema.parse(gate);
    return gate;
  }

  approve(gate: ProductApprovalGate, approver: string, comment?: string): ProductApprovalGate {
    const approval = {
      approver,
      approved: true,
      timestamp: new Date().toISOString(),
      comment,
    };

    const updatedGate = {
      ...gate,
      approvals: [...gate.approvals, approval],
    };

    const approvedCount = updatedGate.approvals.filter((a: { approved: boolean }) => a.approved).length;
    if (approvedCount >= updatedGate.requiredApprovals) {
      updatedGate.status = 'approved';
    }

    return updatedGate;
  }

  reject(gate: ProductApprovalGate, approver: string, comment?: string): ProductApprovalGate {
    const approval = {
      approver,
      approved: false,
      timestamp: new Date().toISOString(),
      comment,
    };

    return {
      ...gate,
      approvals: [...gate.approvals, approval],
      status: 'rejected',
    };
  }

  validateApprovalGate(gate: ProductApprovalGate): { valid: boolean; errors: string[] } {
    const errors: string[] = [];

    if (gate.required && gate.approvers.length === 0) {
      errors.push('Approvers are required when gate is required');
    }
    if (gate.requiredApprovals > gate.approvers.length) {
      errors.push('Required approvals cannot exceed number of approvers');
    }

    return {
      valid: errors.length === 0,
      errors,
    };
  }
}
