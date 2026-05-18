import fs from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';
import {
  YAPPC_PLATFORM_PROVIDER_READINESS,
  assertYappcPlatformProviderReadiness,
} from '../platform-provider-readiness';

const repoRoot = path.resolve(__dirname, '../../../../../..');

function readRepoFile(relativePath: string): string {
  return fs.readFileSync(path.join(repoRoot, relativePath), 'utf8');
}

describe('YAPPC platform-provider readiness', () => {
  it('keeps creator lifecycle evidence separate from Kernel Product Lifecycle execution', () => {
    const readiness = assertYappcPlatformProviderReadiness(YAPPC_PLATFORM_PROVIDER_READINESS);

    expect(readiness.lifecycleBoundary).toBe('creator-lifecycle-not-kernel-executor');
    expect(readiness.mutatesCanonicalProductRegistry).toBe(false);
    expect(readiness.mutatesGeneratedProductIncludes).toBe(false);
  });

  it('declares every public artifact-intelligence evidence contract expected by Kernel handoff', () => {
    expect(YAPPC_PLATFORM_PROVIDER_READINESS.evidenceContracts).toEqual([
      'ProductUnitIntent',
      'ProductShapeEvidence',
      'DependencyGraphEvidence',
      'ArtifactGraphSummary',
      'ResidualIslandReport',
      'RiskHotspotReport',
      'GeneratedChangeSetSummary',
    ]);
  });

  it('publishes platform-mode evidence through Data Cloud before Kernel handoff', () => {
    const exportService = readRepoFile(
      'products/yappc/frontend/web/src/services/canvas/commands/ProductUnitIntentExportService.ts',
    );

    expect(exportService).toContain("request.providerMode === 'platform'");
    expect(exportService).toContain('dataCloudEvidenceEndpoint');
    expect(exportService).toContain('parseDataCloudEvidencePersistenceResponse');
    expect(exportService).toContain('persistedEvidenceRefs');
  });
});
