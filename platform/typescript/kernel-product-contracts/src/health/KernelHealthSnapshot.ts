import { z } from "zod";

export const KernelHealthStatusSchema = z.enum([
  "healthy",
  "degraded",
  "unhealthy",
  "unknown",
]);

const EvidenceLinksSchema = z
  .object({
    lifecycleRunRef: z.string().trim().min(1).optional(),
    gateResultRef: z.string().trim().min(1).optional(),
    artifactManifestRef: z.string().trim().min(1).optional(),
    deploymentManifestRef: z.string().trim().min(1).optional(),
    verifyHealthReportRef: z.string().trim().min(1).optional(),
    agentActionRef: z.string().trim().min(1).optional(),
    semanticArtifactOutputRef: z.string().trim().min(1).optional(),
  })
  .strict();

const BaseSnapshotSchema = z
  .object({
    snapshotId: z.string().trim().min(1),
    tenantId: z.string().trim().min(1),
    workspaceId: z.string().trim().min(1),
    observedAt: z.string().datetime({ offset: true }),
    status: KernelHealthStatusSchema,
    reasonCode: z.string().trim().min(1).optional(),
    evidenceRefs: z.array(z.string().trim().min(1)).default([]),
    links: EvidenceLinksSchema.default({}),
  })
  .strict();

export const KernelLifecycleHealthSnapshotSchema = BaseSnapshotSchema.extend({
  kind: z.literal("lifecycle"),
  productUnitId: z.string().trim().min(1),
  runId: z.string().trim().min(1),
  phase: z.string().trim().min(1),
}).superRefine((snapshot, context) => {
  if (snapshot.status === "degraded" && snapshot.reasonCode === undefined) {
    context.addIssue({
      code: "custom",
      path: ["reasonCode"],
      message:
        "Degraded lifecycle health snapshots must include a reason code.",
    });
  }
});

export const KernelProductHealthSnapshotSchema = BaseSnapshotSchema.extend({
  kind: z.literal("product"),
  productUnitId: z.string().trim().min(1),
});

export const KernelProviderHealthSnapshotSchema = BaseSnapshotSchema.extend({
  kind: z.literal("provider"),
  providerId: z.string().trim().min(1),
  mode: z.enum(["bootstrap", "platform"]),
});

export const KernelGateHealthSnapshotSchema = BaseSnapshotSchema.extend({
  kind: z.literal("gate"),
  productUnitId: z.string().trim().min(1),
  runId: z.string().trim().min(1),
  gateId: z.string().trim().min(1),
});

export const KernelDeploymentHealthSnapshotSchema = BaseSnapshotSchema.extend({
  kind: z.literal("deployment"),
  productUnitId: z.string().trim().min(1),
  runId: z.string().trim().min(1),
  deploymentId: z.string().trim().min(1),
});

export const KernelHealthSnapshotSchema = z.discriminatedUnion("kind", [
  KernelLifecycleHealthSnapshotSchema,
  KernelProductHealthSnapshotSchema,
  KernelProviderHealthSnapshotSchema,
  KernelGateHealthSnapshotSchema,
  KernelDeploymentHealthSnapshotSchema,
]);

export type KernelHealthStatus = z.infer<typeof KernelHealthStatusSchema>;
export type KernelLifecycleHealthSnapshot = z.infer<
  typeof KernelLifecycleHealthSnapshotSchema
>;
export type KernelProductHealthSnapshot = z.infer<
  typeof KernelProductHealthSnapshotSchema
>;
export type KernelProviderHealthSnapshot = z.infer<
  typeof KernelProviderHealthSnapshotSchema
>;
export type KernelGateHealthSnapshot = z.infer<
  typeof KernelGateHealthSnapshotSchema
>;
export type KernelDeploymentHealthSnapshot = z.infer<
  typeof KernelDeploymentHealthSnapshotSchema
>;
export type KernelHealthSnapshot = z.infer<typeof KernelHealthSnapshotSchema>;
