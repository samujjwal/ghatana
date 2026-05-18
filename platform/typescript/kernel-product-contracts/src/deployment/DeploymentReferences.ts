/**
 * DeploymentReferences - canonical deployment, environment, health, and rollback reference schemas.
 *
 * Provides typed reference contracts for deployment artifacts produced during lifecycle execution.
 *
 * @doc.type module
 * @doc.purpose Deployment, environment, health report, and rollback manifest reference contracts
 * @doc.layer kernel-product-contracts
 * @doc.pattern Contract
 */

import { z } from "zod";

// ---------------------------------------------------------------------------
// EnvironmentReference
// ---------------------------------------------------------------------------

export const EnvironmentReferenceSchema = z.object({
  environmentId: z.string().min(1),
  environmentType: z.enum(["local", "compose", "staging", "production"]),
  deploymentTarget: z.string().optional(),
});

export type EnvironmentReference = z.infer<typeof EnvironmentReferenceSchema>;

// ---------------------------------------------------------------------------
// DeploymentReference
// ---------------------------------------------------------------------------

export const DeploymentReferenceSchema = z.object({
  deploymentId: z.string().min(1),
  runId: z.string().min(1),
  correlationId: z.string().min(1),
  productId: z.string().min(1),
  environment: EnvironmentReferenceSchema,
  deployedAt: z.string().datetime(),
  adapterUsed: z.string().optional(),
});

export type DeploymentReference = z.infer<typeof DeploymentReferenceSchema>;

// ---------------------------------------------------------------------------
// DeploymentManifestReference
// ---------------------------------------------------------------------------

export const DeploymentManifestReferenceSchema = z.object({
  schemaVersion: z.literal("1.0.0"),
  runId: z.string().min(1),
  correlationId: z.string().min(1),
  createdAt: z.string().datetime(),
  productId: z.string().min(1),
  manifestPath: z.string().min(1),
  deployment: DeploymentReferenceSchema,
});

export type DeploymentManifestReference = z.infer<
  typeof DeploymentManifestReferenceSchema
>;

export function parseDeploymentManifestReference(
  input: unknown,
): DeploymentManifestReference {
  return DeploymentManifestReferenceSchema.parse(input);
}

// ---------------------------------------------------------------------------
// VerifyHealthReportReference
// ---------------------------------------------------------------------------

export const VerifyHealthReportReferenceSchema = z.object({
  schemaVersion: z.literal("1.0.0"),
  runId: z.string().min(1),
  correlationId: z.string().min(1),
  createdAt: z.string().datetime(),
  productId: z.string().min(1),
  reportPath: z.string().min(1),
  environment: EnvironmentReferenceSchema,
  overallStatus: z.enum(["healthy", "degraded", "unhealthy", "unknown"]),
  checks: z.array(
    z.object({
      checkId: z.string().min(1),
      status: z.enum(["passed", "failed", "skipped", "unknown"]),
      durationMs: z.number().int().nonnegative(),
      message: z.string().optional(),
    }),
  ),
});

export type VerifyHealthReportReference = z.infer<
  typeof VerifyHealthReportReferenceSchema
>;

export function parseVerifyHealthReportReference(
  input: unknown,
): VerifyHealthReportReference {
  return VerifyHealthReportReferenceSchema.parse(input);
}

// ---------------------------------------------------------------------------
// RollbackManifestReference
// ---------------------------------------------------------------------------

export const RollbackManifestReferenceSchema = z.object({
  schemaVersion: z.literal("1.0.0"),
  runId: z.string().min(1),
  correlationId: z.string().min(1),
  createdAt: z.string().datetime(),
  productId: z.string().min(1),
  manifestPath: z.string().min(1),
  environment: EnvironmentReferenceSchema,
  rollbackStrategyId: z.string(),
  rollbackTargetRef: z.string().optional(),
  approvalRequired: z.boolean(),
});

export type RollbackManifestReference = z.infer<
  typeof RollbackManifestReferenceSchema
>;

export function parseRollbackManifestReference(
  input: unknown,
): RollbackManifestReference {
  return RollbackManifestReferenceSchema.parse(input);
}
