/**
 * YAPPC platform-provider readiness contract.
 *
 * This file intentionally models the public handoff surface only. YAPPC can
 * produce creator-lifecycle evidence, but the Kernel consumes references and
 * decides whether Product Lifecycle execution is enabled.
 */

export type YappcProviderMode = 'platform-provider';
export type YappcCreatorLifecycleBoundary = 'creator-lifecycle-not-kernel-executor';

export type YappcEvidenceContract =
  | 'ProductUnitIntent'
  | 'ProductShapeEvidence'
  | 'DependencyGraphEvidence'
  | 'ArtifactGraphSummary'
  | 'ResidualIslandReport'
  | 'RiskHotspotReport'
  | 'GeneratedChangeSetSummary';

export interface YappcPlatformProviderReadiness {
  readonly productId: 'yappc';
  readonly providerMode: YappcProviderMode;
  readonly lifecycleBoundary: YappcCreatorLifecycleBoundary;
  readonly mutatesCanonicalProductRegistry: false;
  readonly mutatesGeneratedProductIncludes: false;
  readonly publishesEvidenceToDataCloud: true;
  readonly exposesPublicTruthOnly: true;
  readonly evidenceContracts: readonly YappcEvidenceContract[];
}

export const YAPPC_PLATFORM_PROVIDER_READINESS: YappcPlatformProviderReadiness = {
  productId: 'yappc',
  providerMode: 'platform-provider',
  lifecycleBoundary: 'creator-lifecycle-not-kernel-executor',
  mutatesCanonicalProductRegistry: false,
  mutatesGeneratedProductIncludes: false,
  publishesEvidenceToDataCloud: true,
  exposesPublicTruthOnly: true,
  evidenceContracts: [
    'ProductUnitIntent',
    'ProductShapeEvidence',
    'DependencyGraphEvidence',
    'ArtifactGraphSummary',
    'ResidualIslandReport',
    'RiskHotspotReport',
    'GeneratedChangeSetSummary',
  ],
} as const;

export function assertYappcPlatformProviderReadiness(
  readiness: YappcPlatformProviderReadiness,
): YappcPlatformProviderReadiness {
  if (readiness.providerMode !== 'platform-provider') {
    throw new Error('YAPPC readiness must stay in platform-provider mode.');
  }
  if (readiness.lifecycleBoundary !== 'creator-lifecycle-not-kernel-executor') {
    throw new Error('YAPPC creator lifecycle must remain distinct from Kernel Product Lifecycle execution.');
  }
  if (readiness.mutatesCanonicalProductRegistry || readiness.mutatesGeneratedProductIncludes) {
    throw new Error('YAPPC must not mutate canonical registry or generated product includes.');
  }
  if (!readiness.publishesEvidenceToDataCloud || !readiness.exposesPublicTruthOnly) {
    throw new Error('YAPPC provider evidence must flow through public runtime truth contracts.');
  }
  return readiness;
}
