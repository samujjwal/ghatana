import type { ReactElement } from 'react';
import { Badge } from '@ghatana/design-system';
import { useStudioLifecycleData } from '../data/StudioLifecycleDataContext';
import type { StudioTranslationKey } from '../i18n/studioTranslations';
import { useStudioTranslation } from '../i18n/studioTranslations';
import {
  describeLifecycleDataStatus,
  formatBytes,
  lifecycleDataBadgeTone,
} from './studioLifecycleRouteSupport';

type TranslateFn = (key: StudioTranslationKey) => string;

function artifactStatusLabel(found: boolean, t: TranslateFn): string {
  return found ? t('studio.route.artifacts.status.found') : t('studio.route.artifacts.status.missing');
}

export default function ArtifactsPage(): ReactElement {
  const lifecycleData = useStudioLifecycleData();
  const t = useStudioTranslation();
  const snapshot = lifecycleData.snapshot;
  const artifacts = snapshot.artifactManifest?.artifacts ?? [];
  const artifactManifestStatus = snapshot.manifestLoadState.artifactManifest.status;
  const artifactManifestMessage = snapshot.manifestLoadState.artifactManifest.message;
  const lifecycleResultRef = snapshot.selectedRun?.manifestRefs?.['lifecycle-result'];
  const artifactManifestRef = snapshot.selectedRun?.manifestRefs?.['artifact-manifest'];
  const verifyHealthRef = snapshot.selectedRun?.healthSnapshotRef;
  const intelligence = resolveArtifactIntelligence(snapshot.productUnit?.metadata);

  return (
    <section className="space-y-6" aria-labelledby="artifacts-title">
      <div className="space-y-2">
        <Badge tone={lifecycleDataBadgeTone(snapshot.status)} variant="soft">
          {describeLifecycleDataStatus(snapshot.status)}
        </Badge>
        <h2 id="artifacts-title" className="text-2xl font-semibold text-gray-950">
          {t('studio.route.artifacts.title')}
        </h2>
        <p className="max-w-3xl text-sm leading-6 text-gray-600">
          {t('studio.route.artifacts.description')}
        </p>
      </div>

      <article className="studio-card space-y-2" aria-label="artifact-manifest-state">
        <div className="flex items-center justify-between gap-3">
          <h3 className="text-base font-semibold text-gray-950">Manifest evidence state</h3>
          <Badge tone={artifactManifestStatus === 'loaded' ? 'success' : artifactManifestStatus === 'missing' ? 'warning' : 'danger'} variant="soft" className="text-xs">
            artifact-manifest: {artifactManifestStatus}
          </Badge>
        </div>
        {artifactManifestStatus !== 'loaded' && (
          <p className="text-sm text-amber-700">
            Manifest evidence is not fully available yet. Run lifecycle build/package and refresh this view.
          </p>
        )}
        {artifactManifestMessage !== undefined && (
          <p className="text-xs text-gray-600">{artifactManifestMessage}</p>
        )}
        <ul className="space-y-1 text-xs text-gray-600">
          <li className="font-mono">lifecycle-result: {lifecycleResultRef ?? 'not reported'}</li>
          <li className="font-mono">artifact-manifest: {artifactManifestRef ?? 'not reported'}</li>
          <li className="font-mono">verify-health: {verifyHealthRef ?? 'not reported'}</li>
        </ul>
      </article>

      <div className="grid gap-4 lg:grid-cols-2">
        {artifacts.length === 0 ? (
          <article className="studio-card space-y-2">
            <h3 className="text-base font-semibold text-gray-950">
              {t('studio.route.artifacts.emptyTitle')}
            </h3>
            <p className="text-sm leading-6 text-gray-600">
              {t('studio.route.artifacts.emptyDescription')}
            </p>
          </article>
        ) : null}
        {artifacts.map((artifact) => (
          <article key={artifact.id} className="studio-card space-y-3">
            <div className="flex items-center justify-between gap-3">
              <h3 className="text-base font-semibold text-gray-950">{artifact.id}</h3>
              <Badge tone={artifact.found ? 'success' : 'danger'} variant="soft">
                {artifactStatusLabel(artifact.found, t)}
              </Badge>
            </div>
            <dl className="grid gap-2 text-sm text-gray-600">
              <div className="flex justify-between gap-3">
                <dt className="font-medium text-gray-900">Type</dt>
                <dd>{artifact.metadata.type}</dd>
              </div>
              <div className="flex justify-between gap-3">
                <dt className="font-medium text-gray-900">Packaging</dt>
                <dd>{artifact.metadata.packaging}</dd>
              </div>
              <div className="flex justify-between gap-3">
                <dt className="font-medium text-gray-900">Fingerprint</dt>
                <dd>{`${artifact.fingerprint.algorithm}:${artifact.fingerprint.hash}`}</dd>
              </div>
              <div className="flex justify-between gap-3">
                <dt className="font-medium text-gray-900">Size</dt>
                <dd>{formatBytes(artifact.metadata.sizeBytes)}</dd>
              </div>
            </dl>
          </article>
        ))}
      </div>

      {(intelligence.residualIslands.length > 0 ||
        intelligence.riskHotspots.length > 0 ||
        intelligence.recommendations.length > 0 ||
        intelligence.evidenceRefs.length > 0) && (
        <article className="studio-card space-y-3" aria-labelledby="artifact-intelligence-title">
          <h3 id="artifact-intelligence-title" className="text-base font-semibold text-gray-950">
            {t('studio.route.artifacts.intelligenceTitle')}
          </h3>
          {intelligence.residualIslands.length > 0 && (
            <div>
              <h4 className="text-sm font-medium text-gray-900">{t('studio.route.artifacts.residualIslandsTitle')}</h4>
              <ul className="mt-1 space-y-1 text-sm text-gray-600">
                {intelligence.residualIslands.map((island) => (
                  <li key={island}>{island}</li>
                ))}
              </ul>
            </div>
          )}
          {intelligence.riskHotspots.length > 0 && (
            <div>
              <h4 className="text-sm font-medium text-gray-900">{t('studio.route.artifacts.riskHotspotsTitle')}</h4>
              <ul className="mt-1 space-y-1 text-sm text-gray-600">
                {intelligence.riskHotspots.map((hotspot) => (
                  <li key={hotspot}>{hotspot}</li>
                ))}
              </ul>
            </div>
          )}
          {intelligence.recommendations.length > 0 && (
            <div>
              <h4 className="text-sm font-medium text-gray-900">{t('studio.route.artifacts.recommendationsTitle')}</h4>
              <ul className="mt-1 space-y-1 text-sm text-gray-600">
                {intelligence.recommendations.map((recommendation) => (
                  <li key={recommendation}>{recommendation}</li>
                ))}
              </ul>
            </div>
          )}
          {intelligence.evidenceRefs.length > 0 && (
            <div>
              <h4 className="text-sm font-medium text-gray-900">{t('studio.route.artifacts.evidenceRefsTitle')}</h4>
              <ul className="mt-1 space-y-1 text-xs text-gray-600">
                {intelligence.evidenceRefs.map((evidenceRef) => (
                  <li key={evidenceRef} className="font-mono">{evidenceRef}</li>
                ))}
              </ul>
            </div>
          )}
        </article>
      )}
    </section>
  );
}

interface ArtifactIntelligenceView {
  readonly residualIslands: readonly string[];
  readonly riskHotspots: readonly string[];
  readonly recommendations: readonly string[];
  readonly evidenceRefs: readonly string[];
}

function resolveArtifactIntelligence(metadata: unknown): ArtifactIntelligenceView {
  if (typeof metadata !== 'object' || metadata === null) {
    return {
      residualIslands: [],
      riskHotspots: [],
      recommendations: [],
      evidenceRefs: [],
    };
  }

  const record = metadata as {
    readonly artifactIntelligence?: {
      readonly residualIslands?: unknown;
      readonly riskHotspots?: unknown;
      readonly recommendations?: unknown;
      readonly evidenceRefs?: unknown;
    };
    readonly residualIslands?: unknown;
    readonly riskHotspots?: unknown;
    readonly recommendations?: unknown;
    readonly evidenceRefs?: unknown;
  };

  const source = record.artifactIntelligence;
  return {
    residualIslands: coerceStringArray(source?.residualIslands ?? record.residualIslands),
    riskHotspots: coerceStringArray(source?.riskHotspots ?? record.riskHotspots),
    recommendations: coerceStringArray(source?.recommendations ?? record.recommendations),
    evidenceRefs: coerceStringArray(source?.evidenceRefs ?? record.evidenceRefs),
  };
}

function coerceStringArray(value: unknown): readonly string[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.filter((entry): entry is string => typeof entry === 'string' && entry.trim().length > 0);
}
