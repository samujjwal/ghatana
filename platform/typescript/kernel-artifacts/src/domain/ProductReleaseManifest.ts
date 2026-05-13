import { z } from 'zod';

/**
 * Product release manifest
 */
export interface ProductReleaseManifest {
  schemaVersion: string;
  productId: string;
  releaseId: string;
  version: string;
  releaseStatus: 'draft' | 'pending' | 'approved' | 'rejected' | 'released' | 'rolled-back';
  timestamp: string;
  surfaces: ReleaseSurfaceManifest[];
  releaseMetadata: ReleaseMetadata;
  approvalGates: ApprovalGate[];
}

/**
 * Release surface manifest
 */
export interface ReleaseSurfaceManifest {
  surface: string;
  surfaceType: string;
  artifactManifestPath: string;
  deploymentManifestPath: string;
  releaseStatus: 'pending' | 'released' | 'failed';
  releaseNotes?: string;
}

/**
 * Release metadata
 */
export interface ReleaseMetadata {
  sourceBuildId: string;
  sourceBuildNumber: string;
  gitCommit: string;
  gitBranch: string;
  releaseType: 'major' | 'minor' | 'patch' | 'hotfix';
  changelog: string;
  releaseManager: string;
  approvalRequired: boolean;
  approvers?: string[];
}

/**
 * Approval gate
 */
export interface ApprovalGate {
  gateId: string;
  gateName: string;
  status: 'pending' | 'passed' | 'failed';
  checkedAt: string;
  checkedBy: string;
  details?: string;
}

/**
 * Zod schema for product release manifest validation
 */
export const ProductReleaseManifestSchema = z.object({
  schemaVersion: z.string().regex(/^\d+\.\d+\.\d+$/),
  productId: z.string().min(1),
  releaseId: z.string().min(1),
  version: z.string().min(1),
  releaseStatus: z.enum(['draft', 'pending', 'approved', 'rejected', 'released', 'rolled-back']),
  timestamp: z.string().datetime(),
  surfaces: z.array(
    z.object({
      surface: z.string().min(1),
      surfaceType: z.string().min(1),
      artifactManifestPath: z.string().min(1),
      deploymentManifestPath: z.string().min(1),
      releaseStatus: z.enum(['pending', 'released', 'failed']),
      releaseNotes: z.string().optional(),
    }),
  ),
  releaseMetadata: z.object({
    sourceBuildId: z.string().min(1),
    sourceBuildNumber: z.string().min(1),
    gitCommit: z.string().min(1),
    gitBranch: z.string().min(1),
    releaseType: z.enum(['major', 'minor', 'patch', 'hotfix']),
    changelog: z.string().min(1),
    releaseManager: z.string().min(1),
    approvalRequired: z.boolean(),
    approvers: z.array(z.string()).optional(),
  }),
  approvalGates: z.array(
    z.object({
      gateId: z.string().min(1),
      gateName: z.string().min(1),
      status: z.enum(['pending', 'passed', 'failed']),
      checkedAt: z.string().datetime(),
      checkedBy: z.string().min(1),
      details: z.string().optional(),
    }),
  ),
});

export type ProductReleaseManifestInput = z.infer<typeof ProductReleaseManifestSchema>;
