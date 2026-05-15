import { z } from 'zod';

export const ApprovalRiskLevelSchema = z.enum(['low', 'medium', 'high', 'critical']);
export type ApprovalRiskLevel = z.infer<typeof ApprovalRiskLevelSchema>;

export const ProductApprovalStatusSchema = z.enum(['pending', 'approved', 'rejected']);
export type ProductApprovalStatus = z.infer<typeof ProductApprovalStatusSchema>;

export const ProductApprovalPolicySchema = z.object({
  minApprovals: z.number().int().min(0).optional(),
  allowDuplicateApprovals: z.boolean().optional(),
  allowedApprovers: z.array(z.string().trim().min(1)).optional(),
  requireEvidenceForRisk: z.array(ApprovalRiskLevelSchema).optional(),
  requireCommentOnRejection: z.boolean().optional(),
});
/* v8 ignore next -- Type-only export has no runtime branch to execute. */
export type ProductApprovalPolicy = z.infer<typeof ProductApprovalPolicySchema>;

export const ProductApprovalDecisionRecordSchema = z.object({
  approvalId: z.string().trim().min(1),
  approver: z.string().trim().min(1),
  approved: z.boolean(),
  timestamp: z.string().datetime(),
  decidedAt: z.string().datetime(),
  comment: z.string().trim().min(1).optional(),
  evidenceRefs: z.array(z.string().trim().min(1)).default([]),
});
export type ProductApprovalDecisionRecord = z.infer<typeof ProductApprovalDecisionRecordSchema>;

/**
 * Product approval gate schema.
 */
export const ProductApprovalGateSchema = z
  .object({
    approvalId: z.string().trim().min(1),
    productId: z.string().trim().min(1),
    runId: z.string().trim().min(1),
    correlationId: z.string().trim().min(1),
    environment: z.string().trim().min(1),
    gateName: z.string().trim().min(1),
    action: z.string().trim().min(1),
    riskLevel: ApprovalRiskLevelSchema,
    requestedBy: z.string().trim().min(1),
    requestedAt: z.string().datetime(),
    decidedAt: z.string().datetime().optional(),
    evidenceRefs: z.array(z.string().trim().min(1)).default([]),
    approvalPolicy: ProductApprovalPolicySchema.optional(),
    required: z.boolean(),
    approvers: z.array(z.string().trim().min(1)),
    approvals: z.array(ProductApprovalDecisionRecordSchema),
    status: ProductApprovalStatusSchema,
    requiredApprovals: z.number().int().min(0),
  })
  .superRefine((gate, ctx) => {
    const policy = resolveApprovalPolicy(gate);
    if (gate.required && gate.approvers.length === 0) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['approvers'],
        message: 'Approvers are required when gate is required',
      });
    }
    if (gate.requiredApprovals > gate.approvers.length) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['requiredApprovals'],
        message: 'Required approvals cannot exceed number of approvers',
      });
    }
    if (gate.requiredApprovals < policy.minApprovals) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['requiredApprovals'],
        message: `Required approvals must satisfy approval policy minimum: ${policy.minApprovals}`,
      });
    }
    if (policy.requireEvidenceForRisk.includes(gate.riskLevel) && gate.evidenceRefs.length === 0) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['evidenceRefs'],
        message: `Evidence refs are required for ${gate.riskLevel} risk approvals`,
      });
    }

    const seenApprovers = new Set<string>();
    for (const [index, approval] of gate.approvals.entries()) {
      if (approval.approvalId !== gate.approvalId) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['approvals', index, 'approvalId'],
          message: 'Approval decision approvalId must match gate approvalId',
        });
      }
      if (!gate.approvers.includes(approval.approver)) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['approvals', index, 'approver'],
          message: `Approval decision approver is not authorized: ${approval.approver}`,
        });
      }
      if (policy.allowedApprovers !== undefined && !policy.allowedApprovers.includes(approval.approver)) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['approvals', index, 'approver'],
          message: `Approval policy does not allow approver: ${approval.approver}`,
        });
      }
      if (!policy.allowDuplicateApprovals && seenApprovers.has(approval.approver)) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['approvals', index, 'approver'],
          message: `Duplicate approval decision for approver: ${approval.approver}`,
        });
      }
      seenApprovers.add(approval.approver);
      if (!approval.approved && policy.requireCommentOnRejection && approval.comment === undefined) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['approvals', index, 'comment'],
          message: 'Rejection reason is required',
        });
      }
    }

    const approvalStatus = resolveGateStatus(gate.approvals, gate.requiredApprovals);
    if (gate.status !== approvalStatus) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['status'],
        message: `Approval gate status must be ${approvalStatus}`,
      });
    }
    if (gate.status !== 'pending' && gate.decidedAt === undefined) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['decidedAt'],
        message: 'decidedAt is required when approval gate is decided',
      });
    }
  });

export type ProductApprovalGate = z.infer<typeof ProductApprovalGateSchema>;

export interface ProductApprovalGateCreateInput {
  readonly approvalId: string;
  readonly productId: string;
  readonly runId: string;
  readonly correlationId: string;
  readonly environment: string;
  readonly gateName?: string;
  readonly action: string;
  readonly riskLevel: ApprovalRiskLevel;
  readonly requestedBy: string;
  readonly requestedAt?: string;
  readonly evidenceRefs?: readonly string[];
  readonly approvalPolicy?: ProductApprovalPolicy;
  readonly required?: boolean;
  readonly approvers: readonly string[];
  readonly requiredApprovals?: number;
}

export interface ProductApprovalDecisionInput {
  readonly approver: string;
  readonly comment?: string;
  readonly decidedAt?: string;
  readonly evidenceRefs?: readonly string[];
  readonly allowDuplicateApproval?: boolean;
}

export interface ApprovalGateValidationResult {
  readonly valid: boolean;
  readonly errors: readonly string[];
}

/**
 * Approval gate manager.
 */
export class ProductApprovalGateManager {
  createApprovalGate(input: ProductApprovalGate | ProductApprovalGateCreateInput): ProductApprovalGate {
    if ('approvals' in input && 'status' in input) {
      return ProductApprovalGateSchema.parse(input);
    }

    const gate: ProductApprovalGate = {
      approvalId: input.approvalId,
      productId: input.productId,
      runId: input.runId,
      correlationId: input.correlationId,
      environment: input.environment,
      gateName: input.gateName ?? input.approvalId,
      action: input.action,
      riskLevel: input.riskLevel,
      requestedBy: input.requestedBy,
      requestedAt: input.requestedAt ?? new Date().toISOString(),
      evidenceRefs: [...(input.evidenceRefs ?? [])],
      ...(input.approvalPolicy !== undefined ? { approvalPolicy: input.approvalPolicy } : {}),
      required: input.required ?? true,
      approvers: [...input.approvers],
      approvals: [],
      status: 'pending',
      requiredApprovals: input.requiredApprovals ?? input.approvers.length,
    };

    return ProductApprovalGateSchema.parse(gate);
  }

  approve(gate: ProductApprovalGate, approver: string, comment?: string): ProductApprovalGate {
    return this.recordDecision(gate, true, {
      approver,
      ...(comment !== undefined ? { comment } : {}),
    });
  }

  reject(gate: ProductApprovalGate, approver: string, comment?: string): ProductApprovalGate {
    return this.recordDecision(gate, false, {
      approver,
      ...(comment !== undefined ? { comment } : {}),
    });
  }

  recordDecision(
    gate: ProductApprovalGate,
    approved: boolean,
    decision: ProductApprovalDecisionInput,
  ): ProductApprovalGate {
    const parsedGate = ProductApprovalGateSchema.parse(gate);
    const policy = resolveApprovalPolicy(parsedGate);
    const decidedAt = decision.decidedAt ?? new Date().toISOString();

    if (!parsedGate.approvers.includes(decision.approver)) {
      throw new Error(`Approver is not authorized for approval gate: ${decision.approver}`);
    }
    if (!approved && policy.requireCommentOnRejection && (decision.comment?.trim().length ?? 0) === 0) {
      throw new Error('Rejection reason is required');
    }
    if (!decision.allowDuplicateApproval && !policy.allowDuplicateApprovals) {
      const duplicate = parsedGate.approvals.some((approval) => approval.approver === decision.approver);
      if (duplicate) {
        throw new Error(`Duplicate approval decision for approver: ${decision.approver}`);
      }
    }

    const approval: ProductApprovalDecisionRecord = {
      approvalId: parsedGate.approvalId,
      approver: decision.approver,
      approved,
      timestamp: decidedAt,
      decidedAt,
      ...(decision.comment !== undefined ? { comment: decision.comment } : {}),
      evidenceRefs: [...(decision.evidenceRefs ?? [])],
    };
    const approvals = [...parsedGate.approvals, approval];
    const status = resolveGateStatus(approvals, parsedGate.requiredApprovals);
    const updatedGate: ProductApprovalGate = {
      ...parsedGate,
      approvals,
      status,
      ...(status !== 'pending' ? { decidedAt } : {}),
    };

    return ProductApprovalGateSchema.parse(updatedGate);
  }

  validateApprovalGate(gate: ProductApprovalGate): ApprovalGateValidationResult {
    const parsed = ProductApprovalGateSchema.safeParse(gate);
    if (!parsed.success) {
      return {
        valid: false,
        errors: parsed.error.issues.map((issue) => issue.message),
      };
    }

    return {
      valid: true,
      errors: [],
    };
  }
}

interface ResolvedApprovalPolicy {
  readonly minApprovals: number;
  readonly allowDuplicateApprovals: boolean;
  readonly allowedApprovers?: readonly string[];
  readonly requireEvidenceForRisk: readonly ApprovalRiskLevel[];
  readonly requireCommentOnRejection: boolean;
}

function resolveApprovalPolicy(gate: {
  readonly requiredApprovals: number;
  readonly approvalPolicy?: ProductApprovalPolicy;
}): ResolvedApprovalPolicy {
  return {
    minApprovals: gate.approvalPolicy?.minApprovals ?? Math.min(1, gate.requiredApprovals),
    allowDuplicateApprovals: gate.approvalPolicy?.allowDuplicateApprovals ?? false,
    ...(gate.approvalPolicy?.allowedApprovers !== undefined
      ? { allowedApprovers: gate.approvalPolicy.allowedApprovers }
      : {}),
    requireEvidenceForRisk: gate.approvalPolicy?.requireEvidenceForRisk ?? ['high', 'critical'],
    requireCommentOnRejection: gate.approvalPolicy?.requireCommentOnRejection ?? true,
  };
}

function resolveGateStatus(
  approvals: readonly ProductApprovalDecisionRecord[],
  requiredApprovals: number,
): ProductApprovalStatus {
  if (approvals.some((approval) => !approval.approved)) {
    return 'rejected';
  }
  const approvedPrincipals = new Set(
    approvals.filter((approval) => approval.approved).map((approval) => approval.approver),
  );
  return approvedPrincipals.size >= requiredApprovals ? 'approved' : 'pending';
}
