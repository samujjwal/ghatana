import { z } from 'zod';

/**
 * Product build manifest
 */
export interface ProductBuildManifest {
  schemaVersion: string;
  productId: string;
  buildNumber: string;
  version: string;
  gitCommit: string;
  gitBranch: string;
  timestamp: string;
  surfaces: BuildSurfaceManifest[];
  buildMetadata: BuildMetadata;
}

/**
 * Build surface manifest
 */
export interface BuildSurfaceManifest {
  surface: string;
  surfaceType: string;
  buildStatus: 'succeeded' | 'failed' | 'skipped';
  artifacts: BuildArtifact[];
  buildDurationMs: number;
  buildLogPath?: string;
}

/**
 * Build artifact
 */
export interface BuildArtifact {
  id: string;
  type: string;
  path: string;
  fingerprint: string;
  sizeBytes: number;
  producedBy: string;
}

/**
 * Build metadata
 */
export interface BuildMetadata {
  buildTool: string;
  buildToolVersion: string;
  environment: string;
  buildTrigger: string;
  triggeredBy: string;
  ciRunId?: string;
}

/**
 * Zod schema for product build manifest validation
 */
export const ProductBuildManifestSchema = z.object({
  schemaVersion: z.string().regex(/^\d+\.\d+\.\d+$/),
  productId: z.string().min(1),
  buildNumber: z.string().min(1),
  version: z.string().min(1),
  gitCommit: z.string().min(1),
  gitBranch: z.string().min(1),
  timestamp: z.string().datetime(),
  surfaces: z.array(
    z.object({
      surface: z.string().min(1),
      surfaceType: z.string().min(1),
      buildStatus: z.enum(['succeeded', 'failed', 'skipped']),
      artifacts: z.array(
        z.object({
          id: z.string().min(1),
          type: z.string().min(1),
          path: z.string().min(1),
          fingerprint: z.string().min(1),
          sizeBytes: z.number().int().nonnegative(),
          producedBy: z.string().min(1),
        }),
      ),
      buildDurationMs: z.number().int().nonnegative(),
      buildLogPath: z.string().optional(),
    }),
  ),
  buildMetadata: z.object({
    buildTool: z.string().min(1),
    buildToolVersion: z.string().min(1),
    environment: z.string().min(1),
    buildTrigger: z.string().min(1),
    triggeredBy: z.string().min(1),
    ciRunId: z.string().optional(),
  }),
});

export type ProductBuildManifestInput = z.infer<typeof ProductBuildManifestSchema>;
